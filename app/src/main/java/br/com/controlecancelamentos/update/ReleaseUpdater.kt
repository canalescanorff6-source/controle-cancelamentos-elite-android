package br.com.controlecancelamentos.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.content.pm.PackageManager
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URL
import java.security.MessageDigest
import javax.net.ssl.HttpsURLConnection

data class AvailableUpdate(
    val version: String,
    val url: String,
    val sha256: String,
    val assetName: String
)

/**
 * Atualiza somente a partir de Releases públicos do repositório oficial.
 * A checagem de assinatura é feita ANTES de abrir o instalador.
 * Sem instalação silenciosa e sem exigir desinstalação do app anterior.
 */
object ReleaseUpdater {
    private const val ENDPOINT =
        "https://api.github.com/repos/canalescanorff6-source/controle-cancelamentos-elite-android/releases/latest"
    private const val MAX_APK_BYTES = 220L * 1024 * 1024

    internal fun versionParts(value: String): List<Int>? {
        val m = Regex("""(?i)^v?(\d{1,4})\.(\d{1,4})\.(\d{1,4})(?:[-+].*)?$""")
            .matchEntire(value.trim()) ?: return null
        return (1..3).map { m.groupValues[it].toInt() }
    }

    internal fun isNewer(remote: String, installed: String): Boolean {
        val next = versionParts(remote) ?: return false
        val now = versionParts(installed) ?: return false
        for (i in 0 until 3) {
            if (next[i] > now[i]) return true
            if (next[i] < now[i]) return false
        }
        return false
    }

    suspend fun check(context: Context): AvailableUpdate? = withContext(Dispatchers.IO) {
        val conn = URL(ENDPOINT).openConnection() as HttpsURLConnection
        try {
            conn.connectTimeout = 12000
            conn.readTimeout = 15000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "Controle-Cancelamentos-Elite-Android")
            if (conn.responseCode == 404) return@withContext null
            require(conn.responseCode == 200) { "GitHub indisponível (HTTP ${conn.responseCode})." }
            val bytes = limitedRead(conn.inputStream, 512 * 1024L)
            val release = JSONObject(String(bytes, Charsets.UTF_8))
            if (release.optBoolean("draft") || release.optBoolean("prerelease")) return@withContext null
            val version = release.optString("tag_name").trim().removePrefix("v")
            val localVersion = context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
            if (!isNewer(version, localVersion)) return@withContext null
            val assets = release.getJSONArray("assets")
            val matches = (0 until assets.length()).map { assets.getJSONObject(it) }.filter {
                val name = it.optString("name")
                name.endsWith(".apk", true) && name.contains("Elite", true) &&
                    !name.contains("debug", true) && name.length in 10..160
            }
            val asset = matches.singleOrNull()
                ?: throw IllegalStateException("A versão $version ainda não possui um único APK release oficial.")
            val uri = Uri.parse(asset.getString("browser_download_url"))
            require(uri.scheme == "https" && uri.host == "github.com" &&
                uri.path.orEmpty().startsWith("/canalescanorff6-source/controle-cancelamentos-elite-android/releases/download/")) {
                "Endereço do APK não é o Release oficial."
            }
            val sha = asset.optString("digest").removePrefix("sha256:")
            require(sha.matches(Regex("""[0-9a-fA-F]{64}"""))) {
                "Release sem SHA-256 oficial. Atualização bloqueada por segurança."
            }
            AvailableUpdate(version, uri.toString(), sha.lowercase(), asset.getString("name"))
        } finally { conn.disconnect() }
    }

    suspend fun downloadAndVerify(context: Context, update: AvailableUpdate): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "atualizacoes").apply { mkdirs() }
        val temp = File(dir, "elite-update-pending.apk")
        val apk = File(dir, "elite-update.apk")
        temp.delete()
        try {
            require(update.url.startsWith(
                "https://github.com/canalescanorff6-source/controle-cancelamentos-elite-android/releases/download/"
            )) { "Origem do APK não permitida." }
            val conn = URL(update.url).openConnection() as HttpsURLConnection
            try {
                conn.connectTimeout = 15000
                conn.readTimeout = 30000
                require(conn.responseCode == 200) { "Falha ao baixar a atualização (${conn.responseCode})." }
                val digest = MessageDigest.getInstance("SHA-256")
                var total = 0L
                conn.inputStream.use { source ->
                    temp.outputStream().use { sink ->
                        val buffer = ByteArray(32768)
                        while (true) {
                            val size = source.read(buffer)
                            if (size < 0) break
                            total += size
                            require(total in 1..MAX_APK_BYTES) { "APK vazio ou maior que o limite seguro." }
                            digest.update(buffer, 0, size)
                            sink.write(buffer, 0, size)
                        }
                    }
                }
                val actual = digest.digest().joinToString("") { "%02x".format(it) }
                require(actual.equals(update.sha256, true)) { "Integridade SHA-256 inválida. APK descartado." }
            } finally { conn.disconnect() }
            require(isCompatible(context, temp)) {
                "A atualização não corresponde ao pacote, à assinatura ou à versão instalada."
            }
            if (apk.exists()) apk.delete()
            check(temp.renameTo(apk)) { "Não foi possível preparar o instalador." }
            apk
        } catch (error: Exception) {
            temp.delete()
            throw error
        }
    }

    @Suppress("DEPRECATION")
    private fun isCompatible(context: Context, file: File): Boolean {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28)
            PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val actual = pm.getPackageArchiveInfo(file.absolutePath, flags) ?: return false
        val installed = pm.getPackageInfo(context.packageName, flags)
        if (actual.packageName != context.packageName) return false
        val newVersion = if (Build.VERSION.SDK_INT >= 28) actual.longVersionCode else actual.versionCode.toLong()
        val oldVersion = if (Build.VERSION.SDK_INT >= 28) installed.longVersionCode else installed.versionCode.toLong()
        if (newVersion <= oldVersion) return false
        fun fingerprints(info: android.content.pm.PackageInfo): Set<String> {
            val signatures = if (Build.VERSION.SDK_INT >= 28) {
                info.signingInfo?.apkContentsSigners?.toList().orEmpty()
            } else info.signatures?.toList().orEmpty()
            return signatures.map {
                MessageDigest.getInstance("SHA-256").digest(it.toByteArray())
                    .joinToString("") { byte -> "%02x".format(byte) }
            }.toSet()
        }
        val oldSignatures = fingerprints(installed)
        val newSignatures = fingerprints(actual)
        return oldSignatures.isNotEmpty() && oldSignatures == newSignatures
    }

    /** false: o usuário ainda precisa liberar a instalação de APKs para este aplicativo. */
    fun openInstaller(context: Context, apk: File): Boolean {
        require(apk.isFile && apk.parentFile?.name == "atualizacoes") { "Arquivo de atualização ausente." }
        if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")))
            return false
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return true
    }

    private fun limitedRead(input: java.io.InputStream, max: Long): ByteArray = input.use {
        val buffer = ByteArray(8192)
        val out = ByteArrayOutputStream()
        while (true) {
            val read = it.read(buffer)
            if (read < 0) break
            require(out.size().toLong() + read <= max) { "Resposta do servidor excede limite seguro." }
            out.write(buffer, 0, read)
        }
        out.toByteArray()
    }
}
