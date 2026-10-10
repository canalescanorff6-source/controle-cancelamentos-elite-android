package br.com.controlecancelamentos.ocr

import br.com.controlecancelamentos.data.CancellationForm
import br.com.controlecancelamentos.data.parseLocalDate
import br.com.controlecancelamentos.data.validTime
import br.com.controlecancelamentos.security.EncryptedArchive
import org.junit.Assert.*
import org.junit.Test
import javax.crypto.AEADBadTagException

class DomainValidationV3Test {
    @Test fun `data invalida nao pode ser corrigida silenciosamente`() {
        assertNull(parseLocalDate("31/02/2026"))
        assertNull(parseLocalDate("29/02/2025"))
        assertEquals("2024-02-29", parseLocalDate("29/02/2024"))
        assertFalse(validTime("25:15"))
        assertTrue(validTime("13:45"))
    }

    @Test fun `cadastro rejeita campos que ultrapassam o formato do backup`() {
        val valid = CancellationForm(date = "10/10/2026", time = "14:20", amount = "9,99",
            coupon = "00123", operator = "Operador", fiscal = "Fiscal",
            manager = "Gerente", lossPrevention = "Prevenção")
        assertNull(valid.validate())
        assertNotNull(valid.copy(date = "31/02/2026").validate())
        assertNotNull(valid.copy(pdv = "X".repeat(51)).validate())
        assertNotNull(valid.copy(reasonDetail = "X".repeat(1001)).validate())
    }

    @Test(expected = AEADBadTagException::class)
    fun `alterar um byte no backup criptografado invalida a autenticacao`() {
        val password = "senha-backup-alta-seguranca".toCharArray()
        val bytes = EncryptedArchive.encrypt("registro verificado".toByteArray(), password)
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        EncryptedArchive.decrypt(bytes, password)
    }
}
