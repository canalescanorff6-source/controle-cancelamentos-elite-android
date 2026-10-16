package br.com.controlecancelamentos.ui

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

/** Funções puras para datas nos filtros (sem depender da interface Android). */
object DateFilterUtils {
    private val br = DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT)

    /**
     * Insere separadores conforme o usuário digita: 10082026 -> 10/08/2026.
     * Também aceita colar "10 8 2026", "10-8-2026" ou "10/8/2026".
     * Não inventa mês/dia a partir de números incompletos; validação ocorre no filtro.
     */
    fun formatInput(input: String): String {
        val trimmed = input.take(32)
        val segments = trimmed.trim().split(Regex("""[\s/.\-]+""")).filter { it.isNotEmpty() }
        if (segments.size == 3 && segments.all { it.all(Char::isDigit) } &&
            segments[0].length in 1..2 && segments[1].length in 1..2 &&
            segments[2].length == 4
        ) {
            return "${segments[0].padStart(2, '0')}/${segments[1].padStart(2, '0')}/${segments[2]}"
        }
        val digits = trimmed.filter(Char::isDigit).take(8)
        return buildString {
            digits.forEachIndexed { index, c ->
                if (index == 2 || index == 4) append('/')
                append(c)
            }
        }
    }

    fun formatEdit(previous: String, entered: String): String {
        if (entered.length == previous.length - 1 &&
            previous.filter(Char::isDigit) == entered.filter(Char::isDigit)
        ) {
            val at = previous.indices.firstOrNull { it >= entered.length || previous[it] != entered[it] }
            if (at != null && previous[at] == '/') {
                // Backspace sobre separador: remove o número anterior também.
                val previousDigitIndex = previous.substring(0, at).count(Char::isDigit) - 1
                val digits = entered.filter(Char::isDigit)
                if (previousDigitIndex >= 0 && previousDigitIndex < digits.length) {
                    return formatInput(digits.removeRange(previousDigitIndex, previousDigitIndex + 1))
                }
            }
        }
        return formatInput(entered)
    }

    fun parse(value: String): LocalDate? = runCatching {
        LocalDate.parse(value, br)
    }.getOrNull()

    fun show(date: LocalDate): String = date.format(br)

    enum class Shortcut { TODAY, LAST_7_DAYS, LAST_30_DAYS, THIS_MONTH, PREVIOUS_MONTH }
    data class Period(val from: String, val to: String)

    fun period(shortcut: Shortcut, today: LocalDate = LocalDate.now()): Period {
        val start = when (shortcut) {
            Shortcut.TODAY -> today
            Shortcut.LAST_7_DAYS -> today.minusDays(6)
            Shortcut.LAST_30_DAYS -> today.minusDays(29)
            Shortcut.THIS_MONTH -> today.withDayOfMonth(1)
            Shortcut.PREVIOUS_MONTH -> YearMonth.from(today).minusMonths(1).atDay(1)
        }
        val end = if (shortcut == Shortcut.PREVIOUS_MONTH)
            YearMonth.from(today).minusMonths(1).atEndOfMonth() else today
        return Period(show(start), show(end))
    }
}
