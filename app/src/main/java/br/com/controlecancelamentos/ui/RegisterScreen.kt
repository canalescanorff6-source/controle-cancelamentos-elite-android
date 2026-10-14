package br.com.controlecancelamentos.ui

import android.net.Uri
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.KeyboardType
import androidx.core.content.FileProvider
import br.com.controlecancelamentos.data.*
import br.com.controlecancelamentos.ocr.ReceiptReader
import br.com.controlecancelamentos.ocr.ReceiptCaptureContract
import br.com.controlecancelamentos.ocr.InvalidReceiptImageException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

@Composable
fun RegisterScreen(vm: AppViewModel, editing: CancellationEntity?, busy: Boolean,
                   onDirtyChange: (Boolean) -> Unit, onDone: () -> Unit) {
    key(editing?.id) {
        val initialForm = remember(editing?.id) { editing?.toForm() ?: CancellationForm() }
        var form by remember { mutableStateOf(initialForm) }
        var photoUri by remember { mutableStateOf<Uri?>(null) }
        var cameraUriText by rememberSaveable { mutableStateOf<String?>(null) }
        val pendingCamera = cameraUriText?.let(Uri::parse)
        var cameraFilePath by rememberSaveable { mutableStateOf<String?>(null) }
        val cameraFileState = remember { mutableStateOf(cameraFilePath?.let(::File)) }
        // A limpeza não acontece no onDispose, pois a Activity pode recriar enquanto a câmera estiver aberta.
        var scanning by remember { mutableStateOf(false) }
        var scanMessage by remember { mutableStateOf<String?>(null) }
        var scanNeedsConfirmation by remember { mutableStateOf(false) }
        var scanReviewed by remember { mutableStateOf(false) }
        val stores by vm.stores.collectAsState()
        val context = LocalContext.current
        LaunchedEffect(Unit) {
            withContext(Dispatchers.IO) {
                File(context.cacheDir, "capturas").listFiles()?.forEach { file ->
                    if (System.currentTimeMillis() - file.lastModified() > 172800000L) file.delete()
                }
            }
        }
        val scope = rememberCoroutineScope()
        LaunchedEffect(form, photoUri) { onDirtyChange(form != initialForm || photoUri != null) }

        fun processPhoto(uri: Uri) {
            photoUri = uri
            scanning = true
            scanMessage = null
            scanNeedsConfirmation = false
            scanReviewed = false
            scope.launch {
                try {
                    val candidate = ReceiptReader.recognize(context, uri)
                    form = form.copy(
                        coupon = candidate.coupon ?: form.coupon,
                        amount = candidate.amountCents?.let { (it / 100).toString() + "," + (it % 100).toString().padStart(2, '0') } ?: form.amount,
                        date = candidate.date ?: form.date,
                        time = candidate.time ?: form.time,
                        pdv = candidate.pdv ?: form.pdv,
                        ccf = candidate.ccf ?: form.ccf
                    )
                    scanMessage = candidate.notes.joinToString("\n")
                    scanNeedsConfirmation = true
                } catch (error: Exception) {
                    scanMessage = "Leitura não concluída: ${error.message ?: "imagem incompatível"}. Você pode digitar os dados manualmente."
                    if (error is InvalidReceiptImageException) {
                        // Somente anexos perigosos/corrompidos são descartados.
                        photoUri = null
                        scanNeedsConfirmation = false
                        if (pendingCamera == uri) {
                            cameraFileState.value?.delete()
                            cameraFileState.value = null
                            cameraFilePath = null
                            cameraUriText = null
                        }
                    } else {
                        // Falha de OCR não impede a foto válida e o cadastro manual.
                        scanNeedsConfirmation = true
                    }
                } finally { scanning = false }
            }
        }

        val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            uri?.let {
                cameraFileState.value?.delete()
                cameraFileState.value = null
                cameraFilePath = null
                cameraUriText = null
                processPhoto(it)
            }
        }
        val files = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                cameraFileState.value?.delete()
                cameraFileState.value = null
                cameraFilePath = null
                cameraUriText = null
                processPhoto(it)
            }
        }
        val camera = rememberLauncherForActivityResult(ReceiptCaptureContract()) { success ->
            val captured = cameraUriText?.let(Uri::parse)
            if (success && captured != null) processPhoto(captured)
            else {
                cameraFileState.value?.delete()
                cameraFileState.value = null
                cameraFilePath = null
                cameraUriText = null
                scanMessage = "Captura cancelada ou a câmera não salvou a foto. Tente novamente ou use Arquivos."
            }
        }
        fun openCamera() {
            runCatching {
                val dir = File(context.cacheDir, "capturas").apply { mkdirs() }
                val file = File.createTempFile("cupom-", ".jpg", dir)
                cameraFileState.value?.delete()
                cameraFileState.value = file
                cameraFilePath = file.absolutePath
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                cameraUriText = uri.toString()
                camera.launch(uri)
            }.onFailure { error ->
                cameraFileState.value?.delete()
                cameraFileState.value = null
                cameraFilePath = null
                cameraUriText = null
                scanMessage = "Não foi possível abrir a câmera: ${error.message ?: "indisponível"}. Use Galeria ou Arquivos."
            }
        }
        fun openGallery() {
            runCatching {
                gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }.onFailure { error ->
                scanMessage = "Falha ao abrir galeria: ${error.message ?: "seletor indisponível"}. Tente Arquivos."
            }
        }
        fun openFiles() {
            runCatching { files.launch(arrayOf("image/*")) }
                .onFailure { error -> scanMessage = "Falha ao abrir Arquivos: ${error.message ?: "seletor indisponível"}." }
        }
        val storedPhoto = remember(editing?.photoPath) {
            editing?.photoPath?.let { path ->
                runCatching { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(path)) }.getOrNull()
            }
        }
        val previewUri = photoUri ?: storedPhoto
        val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, previewUri) {
            value = if (previewUri == null) null else withContext(Dispatchers.IO) {
                runCatching { decodePreview(context, previewUri) }.getOrNull()
            }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Hero(if (editing == null) "Novo cancelamento" else "Editar cancelamento", "Registro rápido e conferência dos dados do cupom")
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                PremiumPanel {
                    SectionTitle("01 · Cupom fiscal")
                    Text("Tire uma foto ou selecione uma imagem. A leitura ajuda a preencher os campos, mas exige conferência.",
                        style = MaterialTheme.typography.bodySmall, color = Grey)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = { openCamera() }, modifier = Modifier.weight(1f), enabled = !scanning) {
                            Icon(Icons.Default.PhotoCamera, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("Câmera")
                        }
                        OutlinedButton(onClick = { openGallery() }, modifier = Modifier.weight(1f), enabled = !scanning) {
                            Icon(Icons.Default.PhotoLibrary, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("Galeria")
                        }
                    }
                    OutlinedButton(onClick = { openFiles() }, modifier = Modifier.fillMaxWidth(), enabled = !scanning) {
                        Icon(Icons.Default.FolderOpen, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Selecionar em Arquivos")
                    }
                    if (scanning) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Analisando cupom no dispositivo…", color = Grey) }
                    bitmap?.let { img ->
                        Image(bitmap = img.asImageBitmap(), contentDescription = "Imagem do cupom",
                            modifier = Modifier.fillMaxWidth().height(160.dp), contentScale = ContentScale.Fit)
                    }
                    scanMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
                    if (scanNeedsConfirmation) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = scanReviewed, onCheckedChange = { scanReviewed = it })
                            Text("Conferi no cupom original os campos que serão salvos",
                                style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        }
                    }
                }
                PremiumPanel {
                    SectionTitle("02 · Informações do cancelamento")
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        AppTextField(form.date, { form = form.copy(date = it) }, "Data • dd/MM/aaaa", Modifier.weight(1f), keyboardType = KeyboardType.Text)
                        AppTextField(form.time, { form = form.copy(time = it) }, "Hora • HH:mm", Modifier.weight(0.72f), keyboardType = KeyboardType.Text)
                    }
                    AppTextField(form.amount, { form = form.copy(amount = it) }, "Valor do cupom (R$) *", keyboardType = KeyboardType.Decimal)
                    AppTextField(form.coupon, { form = form.copy(coupon = it) }, "Número do cupom *", keyboardType = KeyboardType.Text)
                    SimpleSelector("Motivo *", form.reason, REASONS, { form = form.copy(reason = it) })
                    if (form.reason == "Outro") AppTextField(form.reasonDetail, { form = form.copy(reasonDetail = it) }, "Descreva o motivo *")
                    else AppTextField(form.reasonDetail, { form = form.copy(reasonDetail = it) }, "Detalhes adicionais do motivo (opcional)")
                    SimpleSelector("Loja (opcional)", stores.firstOrNull { it.id == form.storeId }?.name ?: "Não selecionada",
                        listOf("Não selecionada") + stores.filter { it.active || it.id == form.storeId }.map { it.name },
                        { name -> form = form.copy(storeId = stores.firstOrNull { it.name == name }?.id) })
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        AppTextField(form.pdv, { form = form.copy(pdv = it) }, "PDV / Caixa", Modifier.weight(1f))
                        AppTextField(form.ccf, { form = form.copy(ccf = it) }, "CCF", Modifier.weight(1f))
                    }
                }
                PremiumPanel {
                    SectionTitle("03 · Responsáveis")
                    AppTextField(form.operator, { form = form.copy(operator = it) }, "Operador *")
                    AppTextField(form.fiscal, { form = form.copy(fiscal = it) }, "Fiscal de caixa *")
                    AppTextField(form.manager, { form = form.copy(manager = it) }, "Gerente *")
                    AppTextField(form.lossPrevention, { form = form.copy(lossPrevention = it) }, "Prevenção de Perdas *")
                    AppTextField(form.notes, { form = form.copy(notes = it) }, "Observações", singleLine = false, minLines = 3)
                }
                Button(enabled = !busy && !scanning && (!scanNeedsConfirmation || scanReviewed),
                    onClick = {
                        vm.save(form, photoUri) {
                            cameraFileState.value?.delete()
                            cameraFileState.value = null
                            cameraFilePath = null
                            cameraUriText = null
                            onDone()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp)) {
                    Icon(Icons.Default.CheckCircle, null)
                    Spacer(Modifier.width(9.dp))
                    Text(if (editing == null) "Salvar cancelamento" else "Salvar alterações")
                }
                Text("* Campos obrigatórios. Confira valores extraídos por foto.",
                    color = Grey, style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun SimpleSelector(label: String, selected: String, options: List<String>, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Grey)
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                Text(selected, modifier = Modifier.weight(1f), maxLines = 2)
                Icon(Icons.Default.ArrowDropDown, null)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(text = { Text(option) }, onClick = { expanded = false; onSelected(option) })
                }
            }
        }
    }
}

/** Miniatura reduzida para não decodificar fotografias de 20–100 megapixels na UI. */
private fun decodePreview(context: android.content.Context, uri: Uri): android.graphics.Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var factor = 1
    while ((bounds.outWidth / factor) > 1500 || (bounds.outHeight / factor) > 1500) factor *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = factor }
    return context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
}
