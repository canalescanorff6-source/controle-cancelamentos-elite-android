package br.com.controlecancelamentos.report

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import br.com.controlecancelamentos.data.CancellationEntity
import br.com.controlecancelamentos.data.BrandingSettings
import br.com.controlecancelamentos.data.fromIsoDate
import br.com.controlecancelamentos.data.showMoney
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.math.BigDecimal

object Reports {
    private fun csvField(text: String): String {
        // CSV exportado pode ser aberto em Excel/Sheets: bloquear fórmulas injetadas.
        val safe = if (text.trimStart().startsWith('=') || text.trimStart().startsWith('+') ||
            text.trimStart().startsWith('-') || text.trimStart().startsWith('@') ||
            text.startsWith('\t') || text.startsWith('\r') || text.startsWith('\n')) "'" + text else text
        return "\"" + safe.replace("\"", "\"\"") + "\""
    }
    fun csv(entries: List<CancellationEntity>): ByteArray {
        val header = listOf("Data", "Hora", "Loja", "Cupom", "Valor (R$)", "Motivo", "Detalhes", "Operador", "Fiscal", "Gerente", "Prevenção de Perdas", "PDV", "CCF", "Observação")
        val lines = entries.map { e ->
            listOf(fromIsoDate(e.dateIso), e.time, e.storeName, e.coupon, BigDecimal.valueOf(e.amountCents, 2).toPlainString().replace('.', ','),
                e.reason, e.reasonDetail, e.operatorName, e.fiscalName, e.managerName, e.lossPreventionName, e.pdv, e.ccf, e.notes).joinToString(";") { csvField(it) }
        }
        return ("\uFEFF" + (listOf(header.joinToString(";") { csvField(it) }) + lines).joinToString("\r\n")).toByteArray(Charsets.UTF_8)
    }

    fun pdf(entries: List<CancellationEntity>, brand: BrandingSettings = BrandingSettings()): ByteArray {
        val pdf = PdfDocument()
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(30, 41, 59); textSize = 9f }
        val tiny = Paint(body).apply { textSize = 8f }
        val heading = Paint(body).apply { textSize = 18f; isFakeBoldText = true }
        val muted = Paint(body).apply { color = Color.DKGRAY; textSize = 10f }
        val teal = Paint().apply { color = Color.rgb(53, 95, 232); style = Paint.Style.FILL }
        val pageWidth = 595; val pageHeight = 842 // A4 a 72 dpi
        var pageNumber = 0
        var y = 0f
        var page: PdfDocument.Page? = null
        fun nextPage() {
            page?.let { pdf.finishPage(it) }
            pageNumber++
            page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
            val c = page!!.canvas
            c.drawRect(0f, 0f, pageWidth.toFloat(), 8f, teal)
            val header = brand.appName.uppercase().take(40)
            c.drawText(header, 36f, 48f, heading)
            c.drawText("Relatório de cancelamentos • " + SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")).format(Date()), 36f, 67f, muted)
            c.drawText("Página $pageNumber", 508f, 67f, muted)
            val credit = if (brand.ideaCredit.isBlank()) brand.developerCredit
                else "${brand.developerCredit} | Ideia: ${brand.ideaCredit}"
            c.drawText(credit.take(92), 36f, 79f, tiny)
            y = 94f
        }
        fun txt(label: String, x: Float, yy: Float, max: Int, paint: Paint = body) {
            val allowed = label.take(max).replace('\n', ' ').replace('\r', ' ')
            val availableWidth = (pageWidth - 36f - x).coerceAtLeast(8f)
            val visible = if (paint.measureText(allowed) <= availableWidth) allowed else {
                val dots = "…"
                val length = paint.breakText(allowed, true,
                    (availableWidth - paint.measureText(dots)).coerceAtLeast(0f), null)
                allowed.take(length) + dots
            }
            page!!.canvas.drawText(visible, x, yy, paint)
        }
        nextPage()
        txt("Registros: ${entries.size}     Total: ${showMoney(entries.sumOf { it.amountCents })}", 36f, y, 90, muted); y += 30
        if (entries.isEmpty()) { txt("Nenhum registro para os filtros selecionados.", 36f, y, 80) }
        entries.forEachIndexed { idx, e ->
            if (y > 742f) nextPage()
            if (idx % 2 == 0) { val p = Paint().apply { color = Color.rgb(243, 247, 249) }; page!!.canvas.drawRect(32f, y - 14f, 563f, y + 40f, p) }
            txt("${fromIsoDate(e.dateIso)} ${e.time}", 38f, y, 24)
            txt("Cupom ${e.coupon}", 154f, y, 28)
            txt(showMoney(e.amountCents), 415f, y, 22)
            y += 14f
            txt("Loja: ${e.storeName.ifBlank { "Não informada" }} | PDV: ${e.pdv} | CCF: ${e.ccf}", 38f, y, 105, tiny)
            y += 12f
            txt("${e.reason} ${e.reasonDetail} | Operador: ${e.operatorName}", 38f, y, 112, tiny)
            y += 12f
            txt("Fiscal: ${e.fiscalName} | Gerente: ${e.managerName} | PP: ${e.lossPreventionName}", 38f, y, 120, tiny)
            y += 25f
        }
        page?.let { pdf.finishPage(it) }
        return try {
            ByteArrayOutputStream().use { stream -> pdf.writeTo(stream); stream.toByteArray() }
        } finally { pdf.close() }
    }

    fun print(context: Context, data: ByteArray) {
        val manager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        manager.print("Cancelamentos", object : PrintDocumentAdapter() {
            override fun onLayout(oldAttributes: PrintAttributes?, newAttributes: PrintAttributes?, cancellationSignal: CancellationSignal?, callback: LayoutResultCallback, extras: android.os.Bundle?) {
                if (cancellationSignal?.isCanceled == true) callback.onLayoutCancelled()
                else callback.onLayoutFinished(PrintDocumentInfo.Builder("cancelamentos.pdf")
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN).build(), true)
            }
            override fun onWrite(pages: Array<out PageRange>, destination: ParcelFileDescriptor, cancellationSignal: CancellationSignal?, callback: WriteResultCallback) {
                if (cancellationSignal?.isCanceled == true) { callback.onWriteCancelled(); return }
                try {
                    FileOutputStream(destination.fileDescriptor).use { it.write(data) }
                    callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (ex: Exception) { callback.onWriteFailed(ex.message) }
            }
        }, PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build())
    }
}
