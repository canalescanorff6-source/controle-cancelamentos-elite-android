package br.com.controlecancelamentos.ocr

import br.com.controlecancelamentos.data.parseMoney
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Textos de impressoras fiscais podem ser entregues em blocos distintos pelo ML Kit.
 * Relaciona um rótulo ao número isolado à direita sem misturar subtotal, CCF e COO.
 * Não completa dígitos ilegíveis e não escolhe números ambíguos.
 */
data class ReceiptSegment(
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    val centerY: Int get() = (top + bottom) / 2
    val height: Int get() = max(1, bottom - top)
}

object ReceiptSpatialMatcher {
    private val total = Regex(
        """^(?:(?:VALOR\s+)?TOTAL(?:\s+(?:A\s+PAGAR|PAGO|CANCELADO|DA\s+VENDA))?|VALOR\s+(?:A\s+PAGAR|FINAL|CANCELADO|DO\s+CANCELAMENTO)|VLR\.?\s*TOTAL)\b""",
        RegexOption.IGNORE_CASE
    )
    private val forbidden = Regex("""^TOTAL\s+(?:DE\s+)?(?:ITENS|PRODUTOS|TRIBUTOS|IMPOSTOS|DESCONTOS|TAXAS)""", RegexOption.IGNORE_CASE)
    private val standaloneAmount = Regex("""^(?:R\s*\$\s*)?(\d{1,8}(?:\.\d{3})*,\s*\d{2}|\d{1,9}[.,]\s*\d{2})$""", RegexOption.IGNORE_CASE)
    private val standaloneCoo = Regex("""^C\s*[O0Q]\s*[O0Q]\s*[:#=.-]?$""", RegexOption.IGNORE_CASE)
    private val standaloneInteger = Regex("""^\d{2,12}$""")

    fun augment(parsed: ReceiptCandidate, parts: List<ReceiptSegment>): ReceiptCandidate {
        val amount = parsed.amountCents ?: findNear(
            parts.filter { total.containsMatchIn(it.text.trim()) && !forbidden.containsMatchIn(it.text.trim()) },
            parts.mapNotNull { part ->
                val money = standaloneAmount.matchEntire(part.text.trim())?.groupValues?.get(1)
                    ?.replace(Regex("""\s+"""), "")
                    ?.let(::parseMoney)
                money?.let { part to it }
            }
        )

        val coupon = parsed.coupon ?: findNear(
            parts.filter { standaloneCoo.matches(it.text.trim()) },
            parts.mapNotNull { part ->
                standaloneInteger.matchEntire(part.text.trim())?.value?.let { part to it }
            }
        )

        return parsed.copy(
            coupon = coupon,
            amountCents = amount,
            notes = buildList {
                addAll(parsed.notes.filterNot {
                    (coupon != null && it.startsWith("Número do cupom não identificado")) ||
                    (amount != null && it.startsWith("Valor total não identificado"))
                })
                if (parsed.coupon == null && coupon != null) add("COO associado pela posição no cupom; confira o número.")
                if (parsed.amountCents == null && amount != null) add("Valor associado pela posição no cupom; confira o total.")
            }
        )
    }

    private fun <T> findNear(
        labels: List<ReceiptSegment>,
        values: List<Pair<ReceiptSegment, T>>
    ): T? {
        if (labels.isEmpty()) return null
        val matches = labels.mapNotNull { label ->
            val candidates = values.mapNotNull { (valueBox, value) ->
                val separation = abs(label.centerY - valueBox.centerY)
                val toleratedY = min(65, max(24, max(label.height, valueBox.height) * 2))
                val toRight = valueBox.left >= label.right - 8
                if (!toRight || separation > toleratedY) null
                else Triple(valueBox, value, separation)
            }.sortedWith(compareBy<Triple<ReceiptSegment, T, Int>> { it.third }
                .thenBy { it.first.left - label.right })
            val chosen = candidates.firstOrNull() ?: return@mapNotNull null
            val rival = candidates.getOrNull(1)
            // Se dois números diferentes estão praticamente na mesma posição, não adivinhar.
            if (rival != null && rival.second != chosen.second && rival.third - chosen.third <= 6) null
            else chosen.second
        }.distinct()
        return matches.singleOrNull()
    }
}
