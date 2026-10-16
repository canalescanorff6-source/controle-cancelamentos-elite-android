package br.com.controlecancelamentos.data

import org.junit.Assert.*
import org.junit.Test

class StoreCatalogTest {
    @Test fun catalogoTemQuinzeLojasExatas() {
        assertEquals(15, StoreCatalog.defaults.size)
        assertEquals(15, StoreCatalog.defaults.map { it.code }.distinct().size)
        assertEquals("01 Centro", StoreCatalog.defaults.first().displayName)
        assertEquals("10 Santa Inês", StoreCatalog.defaults.first { it.code == "10" }.displayName)
        assertEquals("23 Araçagy", StoreCatalog.defaults.last().displayName)
    }

    @Test fun cadastrarNoBancoVazioNaoDuplica() {
        val first = StoreCatalog.missing(emptyList())
        assertEquals(15, first.size)
        val inserted = first.map { StoreEntity(name = it.displayName) }
        assertTrue(StoreCatalog.missing(inserted).isEmpty())
    }

    @Test fun codigoEquivalenteJaExistenteNaoEhRecriado() {
        val current = listOf(StoreEntity(name = "Loja: 0010 - Santa Inês"))
        val missing = StoreCatalog.missing(current)
        assertEquals(14, missing.size)
        assertFalse(missing.any { it.code == "10" })
    }

    @Test fun nomeJaExistenteSemNumeroEhPreservado() {
        val current = listOf(StoreEntity(name = "Santa Inês"))
        assertFalse(StoreCatalog.missing(current).any { it.code == "10" })
    }

    @Test fun reconheceLJComZerosELojaNaNota() {
        val known = StoreCatalog.defaults.map { StoreEntity(name = it.displayName) }
        val target = StoreCatalog.matchCouponCode(
            "CANTINHO DOCE - LOJA 10\nLJ:0010 PDV:100\nTOTAL R$ 64,34", known
        )
        assertEquals("10 Santa Inês", target?.name)
    }

    @Test fun numeroAmbiguoNaoEscolheLoja() {
        val known = StoreCatalog.defaults.map { StoreEntity(name = it.displayName) }
        assertNull(StoreCatalog.matchCouponCode("LOJA 10\nLJ 0009", known))
    }

    @Test fun recusaCodigoRepetido() {
        val known = listOf(StoreEntity(name="10 Santa Inês"), StoreEntity(name="Loja 0010 duplicada"))
        assertNull(StoreCatalog.matchCouponCode("LJ:0010", known))
    }

    @Test fun reconhecePadroesDeCodigos() {
        assertEquals("10", StoreCatalog.codeFromName("0010"))
        assertEquals("10", StoreCatalog.codeFromName("Loja: 0010 - Santa Inês"))
        assertEquals("2", StoreCatalog.codeFromName("02 Cohama"))
        assertNull(StoreCatalog.codeFromName("Cohama"))
    }

    @Test fun backupReconheceLojasEquivalentes() {
        val original = StoreEntity(name = "Loja 0010 - Santa Inês")
        val atual = StoreEntity(name = "10 Santa Inês")
        assertTrue(StoreCatalog.sameStore(original, atual))
        assertTrue(StoreCatalog.sameStore(StoreEntity(name = "São Cristóvão"), StoreEntity(name = "sao cristovao")))
        assertFalse(StoreCatalog.sameStore(StoreEntity(name = "02 Cohama"), StoreEntity(name = "03 Maiobão")))
    }
}
