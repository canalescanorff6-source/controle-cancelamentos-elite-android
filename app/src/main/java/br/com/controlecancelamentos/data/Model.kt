package br.com.controlecancelamentos.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale
import java.util.UUID

val PT_BR: Locale = Locale.forLanguageTag("pt-BR")
val DATE_BR: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT)
val TIME_BR: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm").withResolverStyle(ResolverStyle.STRICT)
val REASONS = listOf("01 CNA (Cartão não autorizado)", "02 DC (Desistência)", "03 Erro de preço", "04 Erro operacional", "05 Teste", "Outro")
fun nowDate() = LocalDate.now().format(DATE_BR)
fun nowTime() = LocalTime.now().format(TIME_BR)
fun showMoney(cents: Long): String = NumberFormat.getCurrencyInstance(PT_BR).format(BigDecimal.valueOf(cents, 2))

// Converte 1.234,56 / R$ 1234,56 / 1234.56 / 1234 em centavos exatos.
fun parseMoney(text: String): Long? {
    return try {
    var s = text.replace("R$", "", ignoreCase = true).replace(" ", "").trim()
    if (!s.matches(Regex("[0-9.,]+"))) return null
    if (s.contains(',')) s = s.replace(".", "").replace(',', '.')
    else if (s.count { it == '.' } > 1 || Regex("^\\d{1,3}(\\.\\d{3})+$").matches(s)) s = s.replace(".", "")
    val dec = BigDecimal(s)
    if (dec.signum() < 0 || dec.scale() > 2 || dec > BigDecimal("9999999999.99")) return null
    dec.multiply(BigDecimal(100)).setScale(0, RoundingMode.UNNECESSARY).longValueExact()
    } catch (_: Exception) { null }
}

fun parseLocalDate(date: String): String? = try { LocalDate.parse(date.trim(), DATE_BR).toString() } catch (_: Exception) { null }
fun fromIsoDate(dateIso: String): String = try { LocalDate.parse(dateIso).format(DATE_BR) } catch (_: Exception) { dateIso }
fun validTime(value: String): Boolean = try { LocalTime.parse(value.trim(), TIME_BR); true } catch (_: Exception) { false }
fun id() = UUID.randomUUID().toString()

@Entity(tableName = "users", indices = [Index(value = ["email"], unique = true)])
data class UserEntity(
    @PrimaryKey val id: String = id(),
    val name: String,
    val email: String,
    val passwordHash: String,
    val salt: String,
    val role: String = "OPERADOR", // ADMIN / OPERADOR
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "stores")
data class StoreEntity(
    @PrimaryKey val id: String = id(),
    val name: String,
    val active: Boolean = true
)

@Entity(tableName = "cancellations", indices = [Index("dateIso"), Index("storeId"), Index("operatorName")])
data class CancellationEntity(
    @PrimaryKey val id: String = id(),
    val dateIso: String,
    val time: String,
    val amountCents: Long,
    val coupon: String,
    val reason: String,
    val reasonDetail: String,
    val operatorName: String,
    val fiscalName: String,
    val managerName: String,
    val lossPreventionName: String,
    val storeId: String?,
    val storeName: String,
    val pdv: String,
    val ccf: String,
    val notes: String,
    val photoPath: String?,
    val createdBy: String,
    val updatedBy: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null
)

@Entity(tableName = "audit", indices = [Index("subjectId")])
data class AuditEntity(
    @PrimaryKey val id: String = id(),
    val action: String,
    val subjectId: String,
    val actorId: String,
    val description: String,
    val at: Long = System.currentTimeMillis()
)

@Entity(tableName = "invites", indices = [Index(value = ["code"], unique = true)])
data class InviteEntity(
    @PrimaryKey val id: String = id(),
    val code: String,
    val role: String = "OPERADOR",
    val usedAt: Long? = null,
    val expiresAt: Long,
    val createdBy: String
)

@Dao
interface AppDao {
    @Query("SELECT * FROM users ORDER BY createdAt ASC") fun users(): Flow<List<UserEntity>>
    @Query("SELECT * FROM users ORDER BY createdAt ASC") suspend fun getUsers(): List<UserEntity>
    @Query("SELECT * FROM users WHERE LOWER(email)=LOWER(:email) LIMIT 1") suspend fun userByEmail(email: String): UserEntity?
    @Query("SELECT * FROM users WHERE id=:userId LIMIT 1") suspend fun userById(userId: String): UserEntity?
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertUser(user: UserEntity)
    @Query("UPDATE users SET enabled=:enabled WHERE id=:userId") suspend fun setUserEnabled(userId: String, enabled: Boolean)

    @Query("SELECT * FROM stores ORDER BY name COLLATE NOCASE ASC") fun stores(): Flow<List<StoreEntity>>
    @Query("SELECT * FROM stores ORDER BY name COLLATE NOCASE ASC") suspend fun snapshotStores(): List<StoreEntity>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun importStore(store: StoreEntity): Long
    @Insert suspend fun insertStore(store: StoreEntity)
    @Query("UPDATE stores SET active=:active WHERE id=:storeId") suspend fun setStoreActive(storeId: String, active: Boolean)

    @Query("SELECT * FROM cancellations WHERE deletedAt IS NULL ORDER BY dateIso DESC, time DESC, createdAt DESC")
    fun cancellations(): Flow<List<CancellationEntity>>
    @Query("SELECT * FROM cancellations WHERE deletedAt IS NULL ORDER BY dateIso DESC")
    suspend fun snapshotCancellations(): List<CancellationEntity>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun importCancellation(item: CancellationEntity): Long
    @Query("""SELECT COUNT(*) FROM cancellations WHERE deletedAt IS NULL
        AND dateIso=:dateIso AND coupon=:coupon
        AND COALESCE(storeId,'')=COALESCE(:storeId,'') AND TRIM(pdv)=TRIM(:pdv)
        AND id != :excludeId""")
    suspend fun countDuplicate(dateIso: String, coupon: String, storeId: String?, pdv: String, excludeId: String): Int
    @Insert suspend fun insertCancellation(item: CancellationEntity)
    @Update suspend fun updateCancellation(item: CancellationEntity)
    @Query("UPDATE cancellations SET deletedAt=:whenDeleted,updatedAt=:whenDeleted,updatedBy=:actor WHERE id=:entryId AND deletedAt IS NULL")
    suspend fun softDelete(entryId: String, actor: String, whenDeleted: Long): Int
    @Insert suspend fun log(entry: AuditEntity)
    @Query("SELECT * FROM audit ORDER BY at DESC LIMIT 100") fun audit(): Flow<List<AuditEntity>>

    @Query("SELECT * FROM invites WHERE code=:code AND usedAt IS NULL AND expiresAt>:now LIMIT 1")
    suspend fun validInvite(code: String, now: Long): InviteEntity?
    @Insert suspend fun addInvite(invite: InviteEntity)
    @Query("UPDATE invites SET usedAt=:now WHERE id=:inviteId AND usedAt IS NULL") suspend fun consumeInvite(inviteId: String, now: Long): Int
}

@Database(entities = [UserEntity::class, StoreEntity::class, CancellationEntity::class, AuditEntity::class, InviteEntity::class], version = 1, exportSchema = true)
abstract class AppDatabase: RoomDatabase() {
    abstract fun dao(): AppDao
}

data class CancellationForm(
    val id: String? = null,
    val date: String = nowDate(),
    val time: String = nowTime(),
    val amount: String = "",
    val coupon: String = "",
    val reason: String = REASONS.first(),
    val reasonDetail: String = "",
    val operator: String = "",
    val fiscal: String = "",
    val manager: String = "",
    val lossPrevention: String = "",
    val storeId: String? = null,
    val pdv: String = "",
    val ccf: String = "",
    val notes: String = "",
    val photoPath: String? = null
) {
    fun validate(): String? {
        if (parseLocalDate(date) == null) return "Informe uma data válida (dd/MM/aaaa)."
        if (!validTime(time)) return "Informe um horário válido (HH:mm)."
        if (parseMoney(amount) == null) return "Informe um valor válido em reais."
        if (coupon.isBlank()) return "Informe o número do cupom."
        if (coupon != coupon.trim()) return "Remova espaços extras do número do cupom."
        if (amount.isBlank() || parseMoney(amount) == null) return "Informe um valor válido em reais."
        if (reasonDetail.length > 1000 || pdv.length > 50 || ccf.length > 50) return "Detalhes, PDV ou CCF excedem o limite permitido."
        if (coupon.length > 40) return "O número do cupom é muito longo."
        if (operator.length > 120 || fiscal.length > 120 || manager.length > 120 || lossPrevention.length > 120 || notes.length > 2000) return "Há um campo com texto maior que o permitido."
        if (reason !in REASONS) return "Selecione um motivo válido."
        if (operator.isBlank()) return "Informe o operador."
        if (fiscal.isBlank()) return "Informe o fiscal de caixa."
        if (manager.isBlank()) return "Informe o gerente."
        if (lossPrevention.isBlank()) return "Informe a Prevenção de Perdas."
        if (reason == "Outro" && reasonDetail.isBlank()) return "Descreva o motivo do cancelamento."
        return null
    }
}

fun CancellationEntity.toForm() = CancellationForm(
    id, fromIsoDate(dateIso), time, (amountCents / 100).toString() + "," + (amountCents % 100).toString().padStart(2, '0'),
    coupon, reason, reasonDetail, operatorName, fiscalName, managerName, lossPreventionName,
    storeId, pdv, ccf, notes, photoPath
)
