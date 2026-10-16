package br.com.controlecancelamentos.security

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.controlecancelamentos.data.*
import java.io.File
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CompleteBackupInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun row(id: String, photo: File?) = CancellationEntity(
        id = id, dateIso = "2026-10-16", time = "09:45", amountCents = 6434L,
        coupon = "313511", reason = REASONS.first(), reasonDetail = "",
        operatorName = "Teste", fiscalName = "Fiscal", managerName = "Gerente",
        lossPreventionName = "Prevenção", storeId = null, storeName = "10 Santa Inês",
        pdv = "100", ccf = "184240", notes = "",
        photoPath = photo?.absolutePath, createdBy = "tester", updatedBy = "tester"
    )

    @Test fun roundTripWithPhoto() {
        val id = UUID.randomUUID().toString()
        val dir = File(context.filesDir, "cupons").apply { mkdirs() }
        val image = File(dir, "$id.img")
        val original = ByteArray(16000) { index -> (index % 223).toByte() }
        image.writeBytes(original)
        val output = File(context.cacheDir, "test-" + UUID.randomUUID() + ".ccf3")
        val password = "senha-de-teste-123".toCharArray()
        try {
            assertEquals(1, CompleteBackup.export(context, Uri.fromFile(output), password,
                emptyList(), listOf(row(id, image)), BrandingSettings()))
            assertTrue(output.length() > 100)
            val prepared = CompleteBackup.prepare(context, Uri.fromFile(output), password, "tester")
            try {
                assertEquals("313511", prepared.snapshot.records.single().coupon)
                assertArrayEquals(original, prepared.images.getValue(id).readBytes())
                assertNull(prepared.snapshot.records.single().photoPath)
            } finally { prepared.clean() }
        } finally {
            image.delete()
            output.delete()
            password.fill('\u0000')
        }
    }

    @Test fun wrongPasswordIsRejected() {
        val id = UUID.randomUUID().toString()
        val output = File(context.cacheDir, "test-" + UUID.randomUUID() + ".ccf3")
        try {
            CompleteBackup.export(context, Uri.fromFile(output),
                "senha-de-teste-123".toCharArray(), emptyList(),
                listOf(row(id, null)), BrandingSettings())
            var rejected = false
            try {
                CompleteBackup.prepare(context, Uri.fromFile(output),
                    "senha-incorreta-987".toCharArray(), "tester")
            } catch (_: Exception) { rejected = true }
            assertTrue("Senha incorreta precisa ser rejeitada", rejected)
        } finally { output.delete() }
    }
}
