package br.com.controlecancelamentos.ocr

import br.com.controlecancelamentos.data.parseMoney
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// Heurísticas conservadoras: devolve somente candidatos encontrados, nunca inventa informações.
data class ReceiptCandidate(
    val coupon: String? = null,
    val amountCents: Long? = null,
    val date: String? = null,
    val time: String? = null,
    val pdv: String? = null,
    val ccf: String? = null,
    val operatorName: String? = null,
    val fiscalName: String? = null,
    val managerName: String? = null,
    val lossPreventionName: String? = null,
    val headerLines: List<String> = emptyList(),
    val notes: List<String> = emptyList()
)

object ReceiptParser {
    private val amountPattern = Regex("(?:R\\$\\s*)?([0-9]{1,3}(?:\\.[0-9]{3})*,[0-9]{2}|[0-9]+[.,][0-9]{2})")
    private val datePattern = Regex("\\b(\\d{2})[/.-](\\d{2})[/.-](\\d{4})\\b")
    private val timePattern = Regex("\\b([01]\\d|2[0-3]):([0-5]\\d)(?::[0-5]\\d)?\\b")
    private val couponPattern = Regex("(?:CUPOM(?:\\s+(?:N[º°O.]|NUMERO|FISCAL))?|COO|EXTRATO\\s+N[º°O.]?)\\s*[:#-]?\\s*(\\d{2,12})", RegexOption.IGNORE_CASE)
    private val pdvPattern = Regex("(?:PDV|CAIXA|ECF)\\s*[:#-]?\\s*(\\d{1,8})", RegexOption.IGNORE_CASE)
    private val ccfPattern = Regex("CCF\\s*[:#-]?\\s*(\\d{1,12})", RegexOption.IGNORE_CASE)
    private val totalLabel = Regex("^(?:(?:VALOR\\s+)?TOTAL(?:\\s+(?:A\\s+PAGAR|DA\\s+VENDA|DO\\s+CUPOM|PAGO|LIQUIDO|CANCELADO))?|VALOR\\s+(?:A\\s+PAGAR|FINAL|LIQUIDO|DO\\s+CUPOM|CANCELADO|DO\\s+CANCELAMENTO)|VLR\\.?\\s*TOTAL|TOTAL\\s+(?:A\\s+PAGAR|PAGO|CANCELADO|DO\\s+CANCELAMENTO))\\b", RegexOption.IGNORE_CASE)

    fun parse(raw: String): ReceiptCandidate {
        val lines = raw.lines().map { it.trim().replace(Regex("\\s+"), " ") }.filter { it.isNotBlank() }
        val all = lines.joinToString("\n")
        // Distingue montante pago de total de itens, impostos, descontos e tributos.
        val excluded = Regex("^TOTAL\\s+(?:(?:DE|DOS|DO)\\s+)?(?:ITENS|PRODUTOS|TRIBUTOS|IMPOSTOS|DESCONTOS|TAXAS)", RegexOption.IGNORE_CASE)
        val stronglyLabeled = Regex("^(?:VALOR\\s+(?:TOTAL|A\\s+PAGAR|CANCELADO|DO\\s+CANCELAMENTO|FINAL|LIQUIDO)|TOTAL\\s+(?:A\\s+PAGAR|PAGO|CANCELADO|DO\\s+CANCELAMENTO)|VLR\\.?\\s*TOTAL)", RegexOption.IGNORE_CASE)
        val labelled = lines.withIndex().filter { totalLabel.containsMatchIn(it.value) && !excluded.containsMatchIn(it.value) }
        val prioritized = labelled.filter { stronglyLabeled.containsMatchIn(it.value) }.ifEmpty { labelled }
        val amount = prioritized.asReversed().firstNotNullOfOrNull { indexed ->
            amountPattern.findAll(indexed.value).mapNotNull { parseMoney(it.groupValues[1]) }.lastOrNull()
                ?: lines.getOrNull(indexed.index + 1)?.takeIf {
                    it.matches(Regex("^(?:R\\$\\s*)?[0-9.,]+$"))
                }?.let { parseMoney(it) }
        }
        val date = datePattern.find(all)?.let { match ->
            val iso = "${match.groupValues[3]}-${match.groupValues[2]}-${match.groupValues[1]}"
            try { LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("dd/MM/uuuu")) } catch (_: Exception) { null }
        }
        fun person(label: String): String? = lines.firstNotNullOfOrNull { line ->
            val marker = Regex("^(?:$label)\\s*[:#-]\\s*(.+)$", RegexOption.IGNORE_CASE).find(line)
            val value = marker?.groupValues?.getOrNull(1)?.trim()
                ?.replace(Regex("^\\d{1,10}\\s*[-:]\\s*"), "")?.trim()
            value?.takeIf { it.matches(Regex("[\\p{L}][\\p{L} .'-]{1,78}")) &&
                !it.equals("nao informado", ignoreCase = true) }
        }
        val found = ReceiptCandidate(
            coupon = couponPattern.find(all)?.groupValues?.get(1),
            amountCents = amount,
            date = date,
            time = timePattern.find(all)?.value?.take(5),
            pdv = pdvPattern.find(all)?.groupValues?.get(1),
            ccf = ccfPattern.find(all)?.groupValues?.get(1),
            operatorName = person("OPERADOR|OPER\\.?|ATENDENTE"),
            fiscalName = person("FISCAL DE CAIXA"),
            managerName = person("GERENTE"),
            lossPreventionName = person("PREVENÇÃO DE PERDAS|PREVENCAO DE PERDAS"),
            headerLines = lines.take(12)
        )
        val cautions = buildList {
            if (found.coupon == null) add("Número do cupom não identificado")
            if (found.amountCents == null) add("Valor total não identificado com segurança")
            if (found.date == null) add("Data não identificada")
            add("Confira todos os dados com o cupom original antes de salvar")
        }
        return found.copy(notes = cautions)
    }
    /** Seleciona loja somente quando o nome cadastrado aparece inequivocamente no cabeçalho. */
    fun matchStore(candidate: ReceiptCandidate, registeredNames: List<String>): String? {
        fun normalize(value: String): String = java.text.Normalizer
            .normalize(value, java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .uppercase().replace(Regex("\\s+"), " ").trim()
        val header = candidate.headerLines.map(::normalize)
        return registeredNames.filter { name ->
            val expected = normalize(name)
            expected.length >= 4 && header.any { line ->
                line == expected || line == "LOJA: $expected" || line == "FILIAL: $expected" ||
                    (expected.length >= 6 && (line.startsWith("$expected ") || line.endsWith(" $expected")))
            }
        }.singleOrNull()
    }

}
