package br.com.controlecancelamentos.ocr

import br.com.controlecancelamentos.data.parseMoney
import org.junit.Assert.*
import org.junit.Test

class ReceiptParserTest {
    @Test fun `extrai campos claros de cupom`() {
        val candidate = ReceiptParser.parse("""
            SUPERMERCADO EXEMPLO
            CUPOM Nº 005921
            PDV: 004 CCF: 10874
            DATA 05/10/2026 14:33:10
            SUBTOTAL R$ 49,90
            DESCONTO R$ 5,00
            VALOR TOTAL R$ 44,90
        """.trimIndent())
        assertEquals("005921", candidate.coupon)
        assertEquals("004", candidate.pdv)
        assertEquals("10874", candidate.ccf)
        assertEquals("05/10/2026", candidate.date)
        assertEquals("14:33", candidate.time)
        assertEquals(4490L, candidate.amountCents)
    }

    @Test fun `nao confunde subtotal com total`() {
        val result = ReceiptParser.parse("SUBTOTAL 120,00\nOUTROS 5,00")
        assertNull(result.amountCents)
    }

    @Test fun `nao cria valor quando nao ha certeza`() {
        val result = ReceiptParser.parse("CNPJ 12.345.678/0001-90\nCUPOM 1234")
        assertEquals("1234", result.coupon)
        assertNull(result.amountCents)
    }

    @Test fun `total a pagar nao e troco nem desconto`() {
        val result = ReceiptParser.parse("""TOTAL DE ITENS 05
SUBTOTAL 55,00
DESCONTO 10,00
VALOR A PAGAR 45,00
TROCO 5,00""")
        assertEquals(4500L, result.amountCents)
    }

    @Test fun `total de tributos nao substitui valor pago`() {
        val result = ReceiptParser.parse("VALOR TOTAL R$ 100,00\nTOTAL DE TRIBUTOS 15,00")
        assertEquals(10000L, result.amountCents)
    }

    @Test fun `data invalida nao e inventada`() {
        val result = ReceiptParser.parse("DATA 31/02/2026\nCUPOM 00122")
        assertNull(result.date)
        assertEquals("00122", result.coupon)
    }

    @Test fun `money usa centavos e respeita ptBR`() {
        assertEquals(123456L, parseMoney("R$ 1.234,56"))
        assertEquals(1090L, parseMoney("10,90"))
        assertEquals(1090L, parseMoney("10.90"))
        assertNull(parseMoney("abc"))
        assertNull(parseMoney("10,999"))
    }
    @Test fun `extrai equipe declarada no cupom sem adivinhar`() {
        val result = ReceiptParser.parse("""
            MERCADO MODELO
            OPERADOR: 0007 - Maria Silva
            FISCAL DE CAIXA: Joana Lima
            GERENTE: Carlos Alves
            PREVENCAO DE PERDAS: Paulo Lima
            CUPOM 000321
            TOTAL CANCELADO 79,90
        """.trimIndent())
        assertEquals("Maria Silva", result.operatorName)
        assertEquals("Joana Lima", result.fiscalName)
        assertEquals("Carlos Alves", result.managerName)
        assertEquals("Paulo Lima", result.lossPreventionName)
        assertEquals(7990L, result.amountCents)
        assertEquals("MERCADO MODELO", ReceiptParser.matchStore(result, listOf("MERCADO MODELO", "MERCADO 2")))
    }

    @Test fun `loja ambigua nao e selecionada`() {
        val result = ReceiptParser.parse("LOJA: MERCADO MODELO\nTOTAL A PAGAR 10,25")
        assertNull(ReceiptParser.matchStore(result, listOf("Mercado Modelo", "MERCADO MODELO")))
        assertEquals(1025L, result.amountCents)
    }
}
