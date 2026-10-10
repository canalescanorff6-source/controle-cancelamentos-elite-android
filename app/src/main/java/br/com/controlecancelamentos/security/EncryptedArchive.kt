package br.com.controlecancelamentos.security

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Backup local AES-256-GCM; formato CCEL + sal de 16 + IV de 12 + texto cifrado.
 * A chave é derivada somente da senha fornecida. Não depende da conta cadastrada.
 */
object EncryptedArchive {
    private val magic = byteArrayOf(0x43, 0x43, 0x45, 0x4c) // CCEL
    private const val SALT_SIZE = 16
    private const val IV_SIZE = 12
    private const val HEADER_SIZE = 4 + SALT_SIZE + IV_SIZE
    private const val MAX_BYTES = 20 * 1024 * 1024

    private fun key(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, 210_000, 256)
        return try {
            SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES")
        } finally { spec.clearPassword() }
    }

    fun encrypt(plain: ByteArray, password: CharArray): ByteArray {
        require(password.size >= 10) { "Use uma senha de backup com pelo menos 10 caracteres." }
        require(plain.size in 1..MAX_BYTES) { "O backup excedeu o tamanho suportado." }
        val salt = ByteArray(SALT_SIZE).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(IV_SIZE).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv))
        val output = cipher.doFinal(plain)
        return magic + salt + iv + output
    }

    fun decrypt(archive: ByteArray, password: CharArray): ByteArray {
        require(archive.size in (HEADER_SIZE + 17)..(MAX_BYTES + HEADER_SIZE + 16)) { "Arquivo de backup inválido ou grande demais." }
        require(archive.copyOfRange(0, 4).contentEquals(magic)) { "Arquivo não reconhecido como backup deste aplicativo." }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE,
            key(password, archive.copyOfRange(4, 4 + SALT_SIZE)),
            GCMParameterSpec(128, archive.copyOfRange(4 + SALT_SIZE, HEADER_SIZE)))
        return cipher.doFinal(archive.copyOfRange(HEADER_SIZE, archive.size))
    }
}
