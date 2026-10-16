package br.com.controlecancelamentos.data

import java.text.Normalizer
import java.util.Locale

/** Catálogo inicial; não altera códigos, nomes nem registros já cadastrados. */
object StoreCatalog {
    data class Location(val code: String, val name: String) {
        val displayName: String get() = "$code $name"
    }

    val defaults = listOf(
        Location("01", "Centro"),
        Location("02", "Cohama"),
        Location("03", "Maiobão"),
        Location("05", "Renascença"),
        Location("07", "Cohab"),
        Location("08", "São Cristóvão"),
        Location("09", "Africanos"),
        Location("10", "Santa Inês"),
        Location("12", "Dirceu"),
        Location("15", "Bacabal"),
        Location("16", "Homero"),
        Location("18", "Vila Isabel"),
        Location("19", "Caxias"),
        Location("22", "Parque Piauí"),
        Location("23", "Araçagy")
    )

    private fun plain(value: String): String = Normalizer.normalize(
        value.trim(), Normalizer.Form.NFD
    ).replace(Regex("""\p{M}+"""), "").lowercase(Locale.ROOT)
        .replace(Regex("""\s+"""), " ").trim()

    /** Aceita "10 Santa Inês", "Loja: 10 - Santa Inês", "LJ 0010" ou "0010". */
    fun codeFromName(name: String): String? {
        val m = Regex("""^(?:(?:loja|lj|filial)\s*[:#.-]?\s*)?0*(\d{1,3})(?=$|[\s:.\-–])""", RegexOption.IGNORE_CASE)
            .find(name.trim()) ?: return null
        return m.groupValues[1].toIntOrNull()?.toString()
    }

    fun missing(existing: List<StoreEntity>): List<Location> {
        val knownCodes = existing.mapNotNull { codeFromName(it.name) }.toSet()
        val knownNames = existing.map { plain(it.name) }.toSet()
        return defaults.filter { item ->
            item.code.toInt().toString() !in knownCodes &&
                plain(item.displayName) !in knownNames &&
                plain(item.name) !in knownNames
        }
    }

    fun sameStore(a: StoreEntity, b: StoreEntity): Boolean {
        if (a.id == b.id) return true
        val aCode = codeFromName(a.name)
        val bCode = codeFromName(b.name)
        return (aCode != null && aCode == bCode) ||
            plain(a.name) == plain(b.name)
    }

    /** Faz o match apenas se o cupom e a lista de lojas apontarem uma única loja. */
    fun matchCouponCode(receiptText: String, existing: List<StoreEntity>): StoreEntity? {
        val m = Regex("""(?i)(?<![\p{L}\d])(?:LJ|LOJA|FILIAL)\s*[:#-]?\s*0*(\d{1,3})\b""")
            .findAll(receiptText).mapNotNull { it.groupValues[1].toIntOrNull()?.toString() }
            .distinct().toList()
        if (m.size != 1) return null
        return existing.filter { codeFromName(it.name) == m.single() && it.active }.singleOrNull()
    }
}
