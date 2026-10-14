package br.com.controlecancelamentos.ocr

import org.junit.Assert.*
import org.junit.Test

/**
 * Cenários derivados da fotografia enviada: loja 0010, PDV 100,
 * CCF 184240, COO 313511, total R$ 64,34.
 * Não pressupõe que a foto tenha sido reconhecida pelo ML Kit no aparelho.
 */
class ReceiptRealCouponTest {
    @Test fun cupomRealComTotalECoos() {
        val c = ReceiptParser.parse("""
            CANTINHO DOCE - LOJA 10
            SANTA INES - MA
            10/10/2026 17:45:39    CCF:184240   COO:313511
            TOTAL R$           64,34
            CUPOM CANCELADO
            LJ:0010  PDV:100  10/10/2026 17:45:39
        """.trimIndent())
        assertEquals("313511", c.coupon)
        assertEquals(6434L, c.amountCents)
        assertEquals("184240", c.ccf)
        assertEquals("100", c.pdv)
        assertEquals("10/10/2026", c.date)
        assertEquals("17:45", c.time)
    }

    @Test fun cooComZerosESeparacaoPorBlocos() {
        val c = ReceiptParser.parse("C00 : 313511\nTOTAL R$\n64,34\nCCF:184240")
        assertEquals("313511", c.coupon)
        assertEquals(6434L, c.amountCents)
        assertEquals("184240", c.ccf)
    }

    @Test fun valorQuandoSimboloENumeroEmTresLinhas() {
        val c = ReceiptParser.parse("TOTAL\nR$\n64,34\nCUPOM CANCELADO")
        assertEquals(6434L, c.amountCents)
    }

    @Test fun posicaoRecuperaValorEIdentificadorEmColunas() {
        val parsed = ReceiptParser.parse("TOTAL R$\nCOO:\nCCF:184240")
        assertNull(parsed.amountCents)
        assertNull(parsed.coupon)
        val extracted = ReceiptSpatialMatcher.augment(parsed, listOf(
            ReceiptSegment("TOTAL R$", 60, 875, 300, 900),
            ReceiptSegment("64,34", 546, 845, 690, 874),
            ReceiptSegment("COO:", 560, 813, 612, 838),
            ReceiptSegment("313511", 619, 810, 708, 836),
            ReceiptSegment("CCF:184240", 360, 807, 525, 838)
        ))
        assertEquals(6434L, extracted.amountCents)
        assertEquals("313511", extracted.coupon)
        assertEquals("184240", extracted.ccf)
    }

    @Test fun naoConfundeCcfComCupom() {
        val parsed = ReceiptParser.parse("CCF:184240\nPDV:100\nTOTAL R$\n64,34")
        assertNull(parsed.coupon)
        assertEquals(184240.toString(), parsed.ccf)
    }

    @Test fun recusaDoisValoresEspacialmenteAmbiguos() {
        val parsed = ReceiptParser.parse("TOTAL R$")
        val extracted = ReceiptSpatialMatcher.augment(parsed, listOf(
            ReceiptSegment("TOTAL R$", 60, 800, 260, 825),
            ReceiptSegment("64,34", 320, 803, 390, 825),
            ReceiptSegment("69,34", 420, 805, 490, 827)
        ))
        assertNull(extracted.amountCents)
    }

    @Test fun naoEscolheDoisTotaisConflitantes() {
        val parsed = ReceiptParser.parse("VALOR TOTAL 64,34\nVALOR A PAGAR 73,99")
        assertNull(parsed.amountCents)
    }
}
