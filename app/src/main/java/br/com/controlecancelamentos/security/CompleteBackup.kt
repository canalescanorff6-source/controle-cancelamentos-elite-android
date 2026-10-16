package br.com.controlecancelamentos.security

import android.content.Context
import android.net.Uri
import br.com.controlecancelamentos.data.*
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.SecureRandom
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Backup integral com fotos em ZIP criptografado de ponta a ponta (AES-256-GCM).
 * CCF3 + salt[16] + IV[12] + AES-GCM(ZIP(metadata.json, photos/UUID.img)).
 * Mantém compatibilidade com o formato legado .ccel, sem alterá-lo.
 */
object CompleteBackup {
    private val magic = byteArrayOf('C'.code.toByte(), 'C'.code.toByte(), 'F'.code.toByte(), '3'.code.toByte())
    private const val SALT = 16
    private const val IV = 12
    private const val MAX_IMAGE = 12L * 1024 * 1024
    private const val MAX_ALL = 512L * 1024 * 1024
    private const val MAX_METADATA = 20L * 1024 * 1024
    private val uuid = Regex("""^[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}$""")

    data class Prepared(
        val snapshot: PortableBackup.Snapshot,
        val images: Map<String, File>,
        val directory: File
    ) {
        fun clean() { directory.deleteRecursively() }
    }

    private fun cipher(password: CharArray, salt: ByteArray, iv: ByteArray, mode: Int): Cipher {
        require(password.size >= 10) { "Senha do backup: mínimo de 10 caracteres." }
        val spec = PBEKeySpec(password, salt, 210_000, 256)
        val secret = try {
            SecretKeySpec(
                SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded,
                "AES"
            )
        } finally { spec.clearPassword() }
        return Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode, secret, GCMParameterSpec(128, iv))
        }
    }

    /** Não mantém imagens desprotegidas no armazenamento compartilhado. */
    fun export(
        context: Context, uri: Uri, password: CharArray,
        stores: List<StoreEntity>, records: List<CancellationEntity>, brand: BrandingSettings
    ): Int {
        val metadata = PortableBackup.encode(stores, records, brand)
        require(metadata.size <= MAX_METADATA) { "Histórico excede o limite deste formato de backup." }
        val referenced = records.mapNotNull { row -> row.photoPath?.let { row.id to File(it) } }
        val privateDir = File(context.filesDir, "cupons").canonicalFile
        var totalBytes = 0L
        referenced.forEach { (id, file) ->
            require(uuid.matches(id)) { "Identificação de foto inválida." }
            val canonical = file.canonicalFile
            require(canonical.parentFile == privateDir && canonical.isFile) {
                "Existe foto ausente ou fora do armazenamento. Confira os anexos antes do backup."
            }
            require(canonical.length() in 1..MAX_IMAGE) { "Foto vazia ou maior que 12 MB." }
            totalBytes += canonical.length()
            require(totalBytes <= MAX_ALL) { "Fotos ultrapassam 512 MB por backup." }
        }
        val salt = ByteArray(SALT).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(IV).also { SecureRandom().nextBytes(it) }
        val encryption = cipher(password, salt, iv, Cipher.ENCRYPT_MODE)
        val target = context.contentResolver.openOutputStream(uri, "w")
            ?: error("Não foi possível criar o backup.")
        BufferedOutputStream(target).use { output ->
            output.write(magic)
            output.write(salt)
            output.write(iv)
            CipherOutputStream(output, encryption).use { encrypted ->
                ZipOutputStream(encrypted).use { zip ->
                    zip.putNextEntry(ZipEntry("metadata.json"))
                    zip.write(metadata)
                    zip.closeEntry()
                    referenced.forEach { (id, file) ->
                        zip.putNextEntry(ZipEntry("photos/$id.img"))
                        FileInputStream(file).use { it.copyTo(zip, 32768) }
                        zip.closeEntry()
                    }
                }
            }
        }
        return referenced.size
    }

    /** Senha incorreta e arquivos incompletos não modificam o banco. */
    fun prepare(context: Context, uri: Uri, password: CharArray, actorId: String): Prepared {
        val stage = File(context.cacheDir, "full-backup-${UUID.randomUUID()}").apply { mkdirs() }
        val tempZip = File(stage, "verified.zip")
        try {
            val input = context.contentResolver.openInputStream(uri)
                ?: error("Não foi possível abrir o backup.")
            BufferedInputStream(input).use { source ->
                val header = ByteArray(4 + SALT + IV)
                DataInputStream(source).readFully(header)
                require(header.copyOfRange(0, 4).contentEquals(magic)) {
                    "Formato incompatível: selecione um backup completo .ccf3."
                }
                val decrypt = cipher(password, header.copyOfRange(4, 4 + SALT),
                    header.copyOfRange(4 + SALT, header.size), Cipher.DECRYPT_MODE)
                var bytes = 0L
                CipherInputStream(source, decrypt).use { decrypted ->
                    FileOutputStream(tempZip).use { sink ->
                        val buffer = ByteArray(32768)
                        while (true) {
                            val count = decrypted.read(buffer)
                            if (count < 0) break
                            bytes += count
                            require(bytes <= MAX_ALL + MAX_METADATA + 10L * 1024 * 1024) {
                                "Backup excede o limite seguro."
                            }
                            sink.write(buffer, 0, count)
                        }
                    }
                } // Tag GCM verificado ANTES da extração.
            }
            var metadata: ByteArray? = null
            val photos = linkedMapOf<String, File>()
            var uncompressed = 0L
            ZipInputStream(BufferedInputStream(FileInputStream(tempZip))).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    require(!entry.isDirectory) { "Entrada ZIP inesperada." }
                    val name = entry.name
                    val limit = if (name == "metadata.json") MAX_METADATA else MAX_IMAGE
                    val photoId = if (name.startsWith("photos/") && name.endsWith(".img"))
                        name.removePrefix("photos/").removeSuffix(".img") else null
                    require(name == "metadata.json" || (photoId != null && uuid.matches(photoId))) {
                        "Nome de anexo não permitido."
                    }
                    require(name != "metadata.json" || metadata == null) { "Metadados duplicados." }
                    require(photoId == null || photoId !in photos) { "Foto duplicada." }
                    val destination = if (photoId != null) File(stage, "$photoId.img") else File(stage, "meta.json")
                    var count = 0L
                    destination.outputStream().use { sink ->
                        val buffer = ByteArray(32768)
                        while (true) {
                            val size = zip.read(buffer)
                            if (size < 0) break
                            count += size
                            uncompressed += size
                            require(count <= limit && uncompressed <= MAX_ALL + MAX_METADATA) {
                                "Arquivo excede limite seguro de extração."
                            }
                            sink.write(buffer, 0, size)
                        }
                    }
                    if (photoId != null) {
                        require(count in 1..MAX_IMAGE) { "Foto vazia no backup." }
                        photos[photoId] = destination
                    } else {
                        metadata = destination.readBytes()
                        destination.delete()
                    }
                    zip.closeEntry()
                }
            }
            tempZip.delete()
            val decoded = PortableBackup.decode(
                metadata ?: error("Backup sem metadados."), actorId
            )
            val ids = decoded.records.map { it.id }.toSet()
            require(photos.keys.all { it in ids }) { "Existem fotos sem cancelamento correspondente." }
            return Prepared(decoded, photos, stage)
        } catch (error: Exception) {
            stage.deleteRecursively()
            throw error
        }
    }
}
