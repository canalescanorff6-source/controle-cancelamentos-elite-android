package br.com.controlecancelamentos.ui

import android.net.Uri
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.KeyboardType
import androidx.core.content.FileProvider
import br.com.controlecancelamentos.data.*
import br.com.controlecancelamentos.ocr.ReceiptParser
import br.com.controlecancelamentos.ocr.ReceiptReader
import br.com.controlecancelamentos.smart.TeamSuggestions
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
        var pendingCamera by remember { mutableStateOf<Uri?>(null) }
        // Keep the mutable state object in the disposal callback, not its initial null value.
        // Otherwise temporary camera photos can remain in cache when a form is closed.
        val cameraFileState = remember { mutableStateOf<File?>(null) }
        var pendingCameraFile by cameraFileState
        DisposableEffect(Unit) {
            onDispose { cameraFileState.value?.delete() }
        }
        var scanning by remember { mutableStateOf(false) }
        var scanMessage by remember { mutableStateOf<String?>(null) }
        var scanNeedsConfirmation by remember { mutableStateOf(false) }
        var scanReviewed by remember { mutableStateOf(false) }
        val stores by vm.stores.collectAsState()
        val previousRecords by vm.cancellations.collectAsState()
        var initialStoreResolved by remember { mutableStateOf(editing != null) }
        LaunchedEffect(stores) {
            if (!initialStoreResolved && stores.isNotEmpty()) {
                initialStoreResolved = true
                stores.filter { it.active }.singleOrNull()?.let { active ->
                    if (form.storeId == null) form = form.copy(storeId = active.id)
                }
            }
        }
        val previousTeam = remember(previousRecords, form.storeId, form.pdv, editing?.id) {
            TeamSuggestions.suggest(previousRecords.filterNot { it.id == editing?.id }, form.storeId, form.pdv)
        }
        val context = LocalContext.current
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
                    val nameFromReceipt = ReceiptParser.matchStore(
                        candidate, stores.filter { it.active || it.id == form.storeId }.map { it.name }
                    )
                    val matchedStore = stores.singleOrNull { it.name == nameFromReceipt }
                    val extracted = buildList {
                        if (candidate.coupon != null) add("cupom")
                        if (candidate.amountCents != null) add("valor")
                        if (candidate.date != null) add("data")
                        if (candidate.time != null) add("hora")
                        if (candidate.pdv != null) add("PDV")
                        if (candidate.ccf != null) add("CCF")
                        if (matchedStore != null) add("loja")
                        if (candidate.operatorName != null && form.operator.isBlank()) add("operador")
                        if (candidate.fiscalName != null && form.fiscal.isBlank()) add("fiscal")
                        if (candidate.managerName != null && form.manager.isBlank()) add("gerente")
                        if (candidate.lossPreventionName != null && form.lossPrevention.isBlank()) add("Prevenção de Perdas")
                    }
                    form = form.copy(
                        coupon = candidate.coupon ?: form.coupon,
                        amount = candidate.amountCents?.let { (it / 100).toString() + "," + (it % 100).toString().padStart(2, '0') } ?: form.amount,
                        date = candidate.date ?: form.date,
                        time = candidate.time ?: form.time,
                        pdv = candidate.pdv ?: form.pdv,
                        ccf = candidate.ccf ?: form.ccf,
                        storeId = matchedStore?.id ?: form.storeId,
                        operator = form.operator.ifBlank { candidate.operatorName.orEmpty() },
                        fiscal = form.fiscal.ifBlank { candidate.fiscalName.orEmpty() },
                        manager = form.manager.ifBlank { candidate.managerName.orEmpty() },
                        lossPrevention = form.lossPrevention.ifBlank { candidate.lossPreventionName.orEmpty() }
                    )
                    scanMessage = buildString {
                        if (extracted.isNotEmpty()) append("Preenchido pela foto: ${extracted.joinToString(", ")}.\n")
                        append(candidate.notes.joinToString("\n"))
                        if (candidate.amountCents == null) append("\nValor não confirmado: confira e preencha manualmente.")
                    }
                    scanNeedsConfirmation = true
                } catch (error: Exception) {
                    scanMessage = "Leitura não concluída: ${error.message ?: "imagem incompatível"}. Você pode digitar os dados manualmente."
                    if (error is InvalidReceiptImageException) {
                        // Somente anexos perigosos/corrompidos são descartados.
                        photoUri = null
                        scanNeedsConfirmation = false
                        if (pendingCamera == uri) {
                            pendingCameraFile?.delete()
                            pendingCameraFile = null
                            pendingCamera = null
                        }
                    } else {
                        // Falha de OCR não impede a foto válida e o cadastro manual.
                        scanNeedsConfirmation = true
                    }
                } finally { scanning = false }
            }
        }

        val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let { processPhoto(it) }
        }
        val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success) pendingCamera?.let { processPhoto(it) }
            else {
                pendingCameraFile?.delete()
                pendingCameraFile = null
                pendingCamera = null
            }
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
                        OutlinedButton(onClick = {
                            val file = File(context.cacheDir, "capturas/${System.currentTimeMillis()}.jpg")
                            file.parentFile?.mkdirs()
                            pendingCameraFile?.delete()
                            pendingCameraFile = file
                            pendingCamera = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                            camera.launch(pendingCamera!!)
                        }, modifier = Modifier.weight(1f), enabled = !scanning) {
                            Icon(Icons.Default.PhotoCamera, null, Modifier.size(18.dp)); Spacer(Modifier.width(5.dp)); Text("Câmera")
                        }
                        OutlinedButton(onClick = { gallery.launch("image/*") }, modifier = Modifier.weight(1f), enabled = !scanning) {
                            Icon(Icons.Default.PhotoLibrary, null, Modifier.size(18.dp)); Spacer(Modifier.width(5.dp)); Text("Galeria")
                        }
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
                    if (previousTeam != null) {
                        Text(
                            "Equipe anterior da loja${if (previousTeam.samePdv) " e do mesmo PDV" else ""}: " +
                                "${previousTeam.people.joinToString(", ")} (registro de ${previousTeam.previousDate}). " +
                                "Confira quem está de turno.",
                            style = MaterialTheme.typography.bodySmall, color = Grey
                        )
                        OutlinedButton(
                            onClick = { form = TeamSuggestions.applyMissing(form, previousTeam) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.PersonAdd, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Usar equipe anterior")
                        }
                    }
                    AppTextField(form.operator, { form = form.copy(operator = it) }, "Operador *")
                    AppTextField(form.fiscal, { form = form.copy(fiscal = it) }, "Fiscal de caixa *")
                    AppTextField(form.manager, { form = form.copy(manager = it) }, "Gerente *")
                    AppTextField(form.lossPrevention, { form = form.copy(lossPrevention = it) }, "Prevenção de Perdas *")
                    AppTextField(form.notes, { form = form.copy(notes = it) }, "Observações", singleLine = false, minLines = 3)
                }
                Button(enabled = !busy && !scanning && (!scanNeedsConfirmation || scanReviewed),
                    onClick = { vm.save(form, photoUri, onDone) },
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
