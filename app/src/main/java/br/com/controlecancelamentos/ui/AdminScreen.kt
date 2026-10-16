package br.com.controlecancelamentos.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.text.input.PasswordVisualTransformation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AdminScreen(vm: AppViewModel, onLogout: () -> Unit) {
    val identity by vm.branding.collectAsState()
    val restorePreview by vm.restorePreview.collectAsState()
    val busy by vm.busy.collectAsState()
    val appearance by vm.appearance.collectAsState()
    var appName by remember(identity) { mutableStateOf(identity.appName) }
    var devCredit by remember(identity) { mutableStateOf(identity.developerCredit) }
    var ideaCredit by remember(identity) { mutableStateOf(identity.ideaCredit) }
    val stores by vm.stores.collectAsState()
    val users by vm.users.collectAsState()
    val audit by vm.audit.collectAsState()
    val actor by vm.session.collectAsState()
    var storeName by remember { mutableStateOf("") }
    var inviteCode by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var backupPassword by remember { mutableStateOf("") }
    var pendingBackup by remember { mutableStateOf<ByteArray?>(null) }
    // Backup completo usa SAF e streaming para não manter imagens em memória.
    var completePassword by remember { mutableStateOf("") }
    val saveCompleteBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val password = completePassword
        completePassword = ""
        if (uri != null && password.isNotBlank()) vm.exportCompleteBackup(uri, password)
    }
    val pickCompleteBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        val password = completePassword
        completePassword = ""
        if (uri != null && password.isNotBlank()) vm.prepareCompleteRestore(uri, password)
    }

    val saveBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val bytes = pendingBackup
        pendingBackup = null
        if (uri != null && bytes != null) scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                        ?: error("Não foi possível gravar.")
                }
                vm.notify("Backup criptografado exportado. Guarde a senha em local seguro.")
            } catch (_: Exception) { vm.notify("Erro ao salvar o arquivo de backup.") }
        }
    }
    val selectBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try {
                val archive = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            val length = input.read(buffer)
                            if (length < 0) break
                            check(output.size() + length <= 21 * 1024 * 1024) { "Arquivo excessivamente grande." }
                            output.write(buffer, 0, length)
                        }
                        output.toByteArray()
                    } ?: error("Não foi possível abrir o arquivo.")
                }
                vm.prepareRestore(archive, backupPassword)
                backupPassword = ""
            } catch (_: Exception) { vm.notify("Arquivo de backup não pôde ser lido.") }
        }
    }
    restorePreview?.let { preview ->
        AlertDialog(
            onDismissRequest = { if (!busy) vm.dismissRestore() },
            title = { Text("Confirmar restauração de backup") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Arquivo autenticado com sucesso.")
                Text("${preview.stores} loja(s), ${preview.records} cancelamento(s) e ${preview.photos} foto(s) disponíveis.")
                Text(if (preview.isComplete) "Backup completo autenticado. Serão restaurados registros novos e suas fotos. Contas de usuários e senhas não são transferidas." else "Backup antigo: serão importados registros e lojas, mas não fotos ou usuários. Os dados existentes serão preservados.")
            } },
            confirmButton = { TextButton(onClick = { vm.confirmRestore() }, enabled = !busy) { Text("Confirmar importação") } },
            dismissButton = { TextButton(onClick = { vm.dismissRestore() }, enabled = !busy) { Text("Cancelar") } }
        )
    }
    val format = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("pt-BR")) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Hero("Administração", "Lojas, acessos, convites e auditoria")
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            PremiumPanel {
                Icon(Icons.Default.AccountCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                SectionTitle(actor?.name ?: "Administrador")
                Text(actor?.email ?: "", color = Grey)
                Text("Perfil: ${actor?.role ?: ""}", color = MaterialTheme.colorScheme.primary)
                OutlinedButton(onClick = onLogout) { Icon(Icons.Default.Logout, null); Spacer(Modifier.width(8.dp)); Text("Sair da conta") }
            }
            PremiumPanel {
                Icon(Icons.Default.Palette, null, tint = Gold)
                SectionTitle("Identidade do seu aplicativo")
                Text("Paleta exclusiva: grafite-azulado, safira e dourado. Personalize o nome e os créditos exibidos nas telas.",
                    color = Grey, style = MaterialTheme.typography.bodySmall)
                AppTextField(appName, { appName = it }, "Nome do aplicativo (3–50 caracteres)")
                AppTextField(devCredit, { devCredit = it }, "Sua assinatura / personalização")
                AppTextField(ideaCredit, { ideaCredit = it }, "Crédito de idealização (opcional)")
                Button(onClick = { vm.saveBranding(appName, devCredit, ideaCredit) }) {
                    Icon(Icons.Default.Save, null); Spacer(Modifier.width(8.dp)); Text("Salvar identidade")
                }
                Text("Alteração local de identidade visual, não substitui registro de marca ou direitos de autoria.", color = Grey,
                    style = MaterialTheme.typography.labelSmall)
            }
            PremiumPanel {
                Icon(Icons.Default.DarkMode, contentDescription = null, tint = Gold)
                SectionTitle("Aparência premium")
                Text("Escolha o visual deste aparelho. O modo Automático acompanha a configuração do Android.",
                    color = Grey, style = MaterialTheme.typography.bodySmall)
                SimpleSelector("Tema", when (appearance) {
                    1 -> "Claro"
                    2 -> "Escuro"
                    else -> "Automático (Android)"
                }, listOf("Automático (Android)", "Claro", "Escuro")) { selected ->
                    vm.setAppearance(when (selected) { "Claro" -> 1; "Escuro" -> 2; else -> 0 })
                }
            }
            UpdatePanel()
            PremiumPanel {
                Icon(Icons.Default.Backup, null, tint = MaterialTheme.colorScheme.primary)
                SectionTitle("Backup completo e restauração")
                Text("RECOMENDADO • Backup com registros, lojas e fotos dos cupons. Protegido por senha e criptografia AES-256-GCM.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary)
                OutlinedTextField(
                    backupPassword, { backupPassword = it }, modifier = Modifier.fillMaxWidth(),
                    label = { Text("Senha do backup (mínimo 10 caracteres)") },
                    singleLine = true, visualTransformation = PasswordVisualTransformation()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            completePassword = backupPassword
                            backupPassword = ""
                            saveCompleteBackup.launch("cancelamentos-elite-completo.ccf3")
                        },
                        enabled = !busy && backupPassword.length >= 10,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 6.dp)
                    ) {
                        Icon(Icons.Default.Backup, null, Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Backup + fotos")
                    }
                    OutlinedButton(
                        onClick = {
                            completePassword = backupPassword
                            backupPassword = ""
                            pickCompleteBackup.launch(arrayOf("*/*"))
                        },
                        enabled = !busy && backupPassword.length >= 10,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 6.dp)
                    ) {
                        Icon(Icons.Default.Restore, null, Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Restaurar completo")
                    }
                }
                Text("Antes de reinstalar ou trocar de celular: exporte o .ccf3, confira que foi salvo, guarde a senha e teste a restauração. O formato completo inclui fotos, mas não inclui contas nem senhas de acesso.",
                    style = MaterialTheme.typography.bodySmall, color = Grey)
                HorizontalDivider()
                Text("Backup antigo (.ccel) — sem fotos", style = MaterialTheme.typography.titleSmall)

                Text("Compatibilidade com backups anteriores: lojas, cancelamentos e identidade. Este formato não inclui fotos.",
                    style = MaterialTheme.typography.bodySmall, color = Grey)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        vm.exportEncryptedBackup(backupPassword) { bytes ->
                            pendingBackup = bytes
                            backupPassword = ""
                            saveBackup.launch("cancelamentos-elite-backup.ccel")
                        }
                    }, enabled = !busy && backupPassword.length >= 10, modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 6.dp)) {
                        Icon(Icons.Default.FileUpload, null, Modifier.size(17.dp)); Spacer(Modifier.width(5.dp)); Text("Exportar")
                    }
                    OutlinedButton(onClick = { selectBackup.launch(arrayOf("*/*")) },
                        enabled = !busy && backupPassword.isNotBlank(), modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 6.dp)) {
                        Icon(Icons.Default.FileDownload, null, Modifier.size(17.dp)); Spacer(Modifier.width(5.dp)); Text("Restaurar")
                    }
                }
                Text("Para restaurar, digite a senha do arquivo e selecione o .ccel. Confira o resumo e confirme a importação; registros existentes são preservados.",
                    color = Grey, style = MaterialTheme.typography.bodySmall)
                Text("A senha não pode ser recuperada. Faça backup antes de reinstalar ou trocar de aparelho e confira se o arquivo foi salvo.",
                    style = MaterialTheme.typography.labelSmall, color = Grey)
            }
            PremiumPanel {
                SectionTitle("Lojas cadastradas")
                Text("Catálogo de 15 unidades com código fixo. O aplicativo adiciona as faltantes sem apagar lojas e registros existentes.",
                    color = Grey, style = MaterialTheme.typography.bodySmall)
                OutlinedButton(
                    onClick = { vm.restoreStoreCatalog() },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Store, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(7.dp))
                    Text("Verificar e completar 15 lojas")
                }
                AppTextField(storeName, { storeName = it }, "Nome da nova loja")
                Button(onClick = { vm.addStore(storeName); storeName = "" }, enabled = storeName.isNotBlank()) {
                    Icon(Icons.Default.AddBusiness, null); Spacer(Modifier.width(7.dp)); Text("Adicionar loja")
                }
                if (stores.isEmpty()) Text("Nenhuma loja cadastrada.", color = Grey)
                stores.forEach { store ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text(store.name, fontWeight = FontWeight.Medium)
                            Text(if (store.active) "Ativa" else "Inativa", color = if (store.active) Teal else Grey,
                                style = MaterialTheme.typography.labelSmall)
                        }
                        Switch(checked = store.active, onCheckedChange = { vm.toggleStore(store) })
                    }
                }
            }
            PremiumPanel {
                SectionTitle("Usuários e permissões")
                if (users.isEmpty()) Text("Nenhum usuário.", color = Grey)
                users.forEach { u ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(u.name, fontWeight = FontWeight.Medium)
                            Text("${u.email} • ${u.role}", color = Grey, style = MaterialTheme.typography.labelSmall)
                        }
                        if (actor?.id != u.id) Switch(checked = u.enabled, onCheckedChange = { vm.toggleUser(u) })
                    }
                }
                HorizontalDivider()
                Text("Convide um operador para criar uma conta neste aparelho.", style = MaterialTheme.typography.bodySmall, color = Grey)
                Button(onClick = { vm.createInvite { inviteCode = it } }) {
                    Icon(Icons.Default.PersonAdd, null); Spacer(Modifier.width(7.dp)); Text("Gerar convite (7 dias)")
                }
                inviteCode?.let { code ->
                    Text("Código: $code", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                    Text("Este código funciona apenas na base de dados deste aparelho. Para conectar aparelhos diferentes, será necessário configurar um backend seguro.",
                        color = Grey, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = {
                        val share = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "Convite Controle de Cancelamentos: $code\nUse 'Tenho um convite' na tela de acesso do mesmo aparelho.")
                        }
                        context.startActivity(Intent.createChooser(share, "Compartilhar convite"))
                    }) { Icon(Icons.Default.Share, null); Spacer(Modifier.width(7.dp)); Text("Compartilhar código") }
                }
            }
            PremiumPanel {
                SectionTitle("Atividade recente")
                if (audit.isEmpty()) Text("Nenhuma alteração registrada.", color = Grey)
                audit.take(30).forEach { event ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("${event.action} • ${event.description}", style = MaterialTheme.typography.bodyMedium)
                        Text("${format.format(Date(event.at))} • ${users.firstOrNull { it.id == event.actorId }?.name ?: "Usuário"}",
                            color = Grey, style = MaterialTheme.typography.labelSmall)
                    }
                    HorizontalDivider(color = CanvasColor)
                }
            }
            PremiumPanel {
                SectionTitle("Privacidade")
                Text("Os dados e fotos ficam neste Android, sem sincronização automática. A desinstalação remove o banco local. Para equipes em aparelhos diferentes, será necessário um servidor seguro.",
                    style = MaterialTheme.typography.bodySmall, color = Grey)
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}
