#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
cat > "$TMP/RoomStubs.kt" <<'KOTLIN'
package androidx.room
import kotlin.reflect.KClass
annotation class Entity(val tableName: String = "", val indices: Array<Index> = [])
annotation class Index(vararg val value: String, val unique: Boolean = false)
annotation class PrimaryKey
annotation class Dao
annotation class Query(val value: String)
annotation class Insert(val onConflict: Int = 0)
annotation class Update
annotation class Database(val entities: Array<KClass<*>>, val version: Int, val exportSchema: Boolean = false)
object OnConflictStrategy { const val ABORT = 1; const val IGNORE = 2 }
open class RoomDatabase
KOTLIN
cat > "$TMP/FlowStubs.kt" <<'KOTLIN'
package kotlinx.coroutines.flow
interface Flow<out T>
KOTLIN
cat > "$TMP/Test.kt" <<'KOTLIN'
import br.com.controlecancelamentos.data.*
import br.com.controlecancelamentos.ocr.ReceiptParser
import br.com.controlecancelamentos.security.EncryptedArchive
import javax.crypto.AEADBadTagException

fun main() {
    var assertions = 0
    fun checkIt(value: Boolean, name: String) { check(value) { name }; assertions++ }
    checkIt(parseLocalDate("31/02/2026") == null, "strict data February 31")
    checkIt(parseLocalDate("29/02/2025") == null, "strict non leap")
    checkIt(parseLocalDate("29/02/2024") == "2024-02-29", "strict leap")
    checkIt(!validTime("25:45") && validTime("14:30"), "strict time")
    checkIt(parseMoney("R$ 1.234,56") == 123456L, "currency")
    checkIt(parseMoney("10,999") == null, "fraction digits")
    val form = CancellationForm(date="10/10/2026",time="14:20",amount="9,99",coupon="00123",operator="Op",fiscal="Fiscal",manager="Gerente",lossPrevention="Equipe")
    checkIt(form.validate() == null, "valid cancellation")
    checkIt(form.copy(date="31/02/2026").validate() != null, "invalid date")
    checkIt(form.copy(pdv="X".repeat(51)).validate() != null, "oversized pdv")
    checkIt(form.copy(reasonDetail="X".repeat(1001)).validate() != null, "oversized reason")
    val receipt = ReceiptParser.parse("CUPOM Nº 005921\nPDV: 004 CCF: 10874\nDATA 05/10/2026 14:33:10\nSUBTOTAL 49,90\nVALOR TOTAL R$ 44,90")
    checkIt(receipt.coupon == "005921" && receipt.amountCents == 4490L && receipt.pdv == "004", "OCR parsed candidates")
    checkIt(ReceiptParser.parse("TOTAL DE TRIBUTOS 15,00").amountCents == null, "reject taxes")
    val pw="senha-forte-de-backup-123".toCharArray()
    val raw="Teste de seguranca".toByteArray()
    val archive=EncryptedArchive.encrypt(raw,pw)
    checkIt(EncryptedArchive.decrypt(archive,pw).contentEquals(raw), "restore ciphertext")
    archive[archive.lastIndex]=(archive.last().toInt() xor 1).toByte()
    var invalid = false
    try { EncryptedArchive.decrypt(archive,pw) } catch (_:AEADBadTagException) { invalid = true }
    checkIt(invalid, "detect ciphertext tampering")
    println("PASS: $assertions assertions from real Kotlin core files (stub Android Room/Flow only)")
}
KOTLIN
kotlinc \
    "$TMP/RoomStubs.kt" "$TMP/FlowStubs.kt" "$TMP/Test.kt" \
    "$ROOT/app/src/main/java/br/com/controlecancelamentos/data/Model.kt" \
    "$ROOT/app/src/main/java/br/com/controlecancelamentos/ocr/ReceiptParser.kt" \
    "$ROOT/app/src/main/java/br/com/controlecancelamentos/security/EncryptedArchive.kt" \
    -include-runtime -d "$TMP/core.jar"
java -jar "$TMP/core.jar"
