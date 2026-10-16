package br.com.controlecancelamentos.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import br.com.controlecancelamentos.update.AvailableUpdate
import br.com.controlecancelamentos.update.ReleaseUpdater
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun UpdatePanel() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var searching by remember { mutableStateOf(false) }
    var downloading by remember { mutableStateOf(false) }
    var update by remember { mutableStateOf<AvailableUpdate?>(null) }
    var apk by remember { mutableStateOf<File?>(null) }
    var status by remember { mutableStateOf("Toque em Verificar para consultar o GitHub Releases oficial.") }
    @Suppress("DEPRECATION")
    val installedVersion = remember {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }
    PremiumPanel {
        Icon(Icons.Default.SystemUpdate, null, tint = Gold, modifier = Modifier.size(25.dp))
        SectionTitle("Atualizações do aplicativo")
        Text("Versão instalada: $installedVersion", fontWeight = FontWeight.SemiBold)
        Text("Consulta versões publicadas no GitHub. A atualização é baixada dentro do app, conferida e instalada pelo Android com sua confirmação.",
            color = Grey, style = MaterialTheme.typography.bodySmall)
        Button(
            enabled = !searching && !downloading,
            onClick = {
                searching = true
                apk = null
                update = null
                status = "Consultando versão oficial…"
                scope.launch {
                    try {
                        update = ReleaseUpdater.check(context)
                        status = if (update == null) "Nenhuma versão release mais recente publicada."
                            else "Versão ${update!!.version} disponível. Confirme para baixar o APK."
                    } catch (ex: Exception) {
                        status = ex.message ?: "Não foi possível consultar as atualizações."
                    } finally { searching = false }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Verificar atualizações")
        }
        if (searching || downloading) LinearProgressIndicator(Modifier.fillMaxWidth())
        update?.let { available ->
            if (apk == null) {
                OutlinedButton(
                    onClick = {
                        downloading = true
                        status = "Baixando e verificando integridade, assinatura e versão…"
                        scope.launch {
                            try {
                                apk = ReleaseUpdater.downloadAndVerify(context, available)
                                status = "APK validado. Toque em Instalar para abrir o instalador seguro do Android."
                            } catch (ex: Exception) {
                                status = ex.message ?: "Falha na atualização. Nenhuma instalação foi iniciada."
                            } finally { downloading = false }
                        }
                    },
                    enabled = !downloading && !searching,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Download, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Baixar atualização ${available.version}")
                }
            }
        }
        apk?.let { verified ->
            Button(
                onClick = {
                    try {
                        val opened = ReleaseUpdater.openInstaller(context, verified)
                        status = if (opened) "Confirme a instalação no Android."
                        else "Autorize a instalação para este aplicativo e depois toque em Instalar novamente."
                    } catch (ex: Exception) {
                        status = ex.message ?: "Não foi possível iniciar a instalação."
                    }
                },
                enabled = !searching && !downloading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.InstallMobile, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Instalar atualização")
            }
        }
        Text(status, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
        Text("As atualizações exigem um APK release assinado pela mesma chave do app atual. Versões debug ou com outro identificador não substituem a instalação existente.",
            color = Grey, style = MaterialTheme.typography.labelSmall)
    }
}
