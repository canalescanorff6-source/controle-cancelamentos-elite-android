package br.com.controlecancelamentos.ocr

import br.com.controlecancelamentos.security.EncryptedArchive
import org.junit.Assert.*
import org.junit.Test

class EncryptedArchiveTest {
    @Test fun `backup cifrado pode ser recuperado com senha correta`() {
        val data = "Cupom 0002; R$ 124,50; operador".toByteArray()
        val password = "senha-segura-123".toCharArray()
        val encrypted = EncryptedArchive.encrypt(data, password)
        assertFalse(encrypted.contentEquals(data))
        assertArrayEquals(data, EncryptedArchive.decrypt(encrypted, password))
    }

    @Test(expected = javax.crypto.AEADBadTagException::class)
    fun `senha errada nao abre o backup`() {
        val encrypted = EncryptedArchive.encrypt("registro teste".toByteArray(), "senha-original-2026".toCharArray())
        EncryptedArchive.decrypt(encrypted, "senha-invalida-2026".toCharArray())
    }
}
