package br.com.controlecancelamentos.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object Passwords {
    fun salt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return Base64.getEncoder().encodeToString(bytes)
    }
    fun hash(password: String, salt: String): String {
        val spec = PBEKeySpec(password.toCharArray(), Base64.getDecoder().decode(salt), 210_000, 256)
        return try {
            val encoded = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            Base64.getEncoder().encodeToString(encoded)
        } finally { spec.clearPassword() }
    }
    fun verify(password: String, salt: String, expectedHash: String): Boolean = try {
        MessageDigest.isEqual(
            Base64.getDecoder().decode(hash(password, salt)),
            Base64.getDecoder().decode(expectedHash)
        )
    } catch (_: Exception) { false }
}
