package br.com.controlecancelamentos.ui

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class DateFilterUtilsTest {
    @Test fun mascaraInsereBarrasAoDigitar() {
        assertEquals("1", DateFilterUtils.formatInput("1"))
        assertEquals("10", DateFilterUtils.formatInput("10"))
        assertEquals("10/0", DateFilterUtils.formatInput("100"))
        assertEquals("10/08", DateFilterUtils.formatInput("1008"))
        assertEquals("10/08/2026", DateFilterUtils.formatInput("10082026"))
        assertEquals("10/08/2026", DateFilterUtils.formatInput("100820260000"))
    }

    @Test fun aceitaEntradaComEspacosESeparadores() {
        assertEquals("10/08/2026", DateFilterUtils.formatInput("10 8 2026"))
        assertEquals("01/08/2026", DateFilterUtils.formatInput("1/8/2026"))
        assertEquals("10/08/2026", DateFilterUtils.formatInput("10-8-2026"))
        assertEquals("10/08/2026", DateFilterUtils.formatInput("10.08.2026"))
    }

    @Test fun naoInventaDatasInvalidas() {
        assertNull(DateFilterUtils.parse("31/02/2026"))
        assertNull(DateFilterUtils.parse("1/8/2026"))
        assertEquals(LocalDate.of(2026, 8, 10), DateFilterUtils.parse("10/08/2026"))
    }

    @Test fun permiteApagarAoLadoDaBarra() {
        assertEquals("10", DateFilterUtils.formatEdit("10/0", "100"))
        assertEquals("", DateFilterUtils.formatEdit("1", ""))
    }

    @Test fun periodosSaoInclusivosENaoConfundemMeses() {
        val day = LocalDate.of(2026, 10, 16)
        assertEquals(DateFilterUtils.Period("16/10/2026", "16/10/2026"),
            DateFilterUtils.period(DateFilterUtils.Shortcut.TODAY, day))
        assertEquals(DateFilterUtils.Period("10/10/2026", "16/10/2026"),
            DateFilterUtils.period(DateFilterUtils.Shortcut.LAST_7_DAYS, day))
        assertEquals(DateFilterUtils.Period("01/10/2026", "16/10/2026"),
            DateFilterUtils.period(DateFilterUtils.Shortcut.THIS_MONTH, day))
        assertEquals(DateFilterUtils.Period("01/09/2026", "30/09/2026"),
            DateFilterUtils.period(DateFilterUtils.Shortcut.PREVIOUS_MONTH, day))
    }

    @Test fun intervaloIncluiViradaDeMes() {
        assertEquals(DateFilterUtils.Period("01/10/2026", "30/10/2026"),
            DateFilterUtils.period(DateFilterUtils.Shortcut.LAST_30_DAYS, LocalDate.of(2026, 10, 30)))
    }
}
