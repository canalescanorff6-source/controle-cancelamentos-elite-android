package br.com.controlecancelamentos.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** Pacote portátil: apenas lojas, registros ativos e identidade visual.
 * Exclui senhas, usuários e fotografias para não transferir credenciais locais.
 */
object PortableBackup {
    const val FORMAT = 2
    fun encode(stores: List<StoreEntity>, rows: List<CancellationEntity>, brand: BrandingSettings): ByteArray {
        val json = JSONObject().put("format", FORMAT)
            .put("createdAt", System.currentTimeMillis())
            .put("branding", JSONObject()
                .put("appName", brand.appName).put("developerCredit", brand.developerCredit).put("ideaCredit", brand.ideaCredit))
        val shopList = JSONArray()
        stores.forEach { shop ->
            shopList.put(JSONObject().put("id", shop.id).put("name", shop.name).put("active", shop.active))
        }
        val rowsArray = JSONArray()
        rows.forEach { r ->
            rowsArray.put(JSONObject()
                .put("id",r.id).put("dateIso",r.dateIso).put("time",r.time).put("amountCents",r.amountCents)
                .put("coupon",r.coupon).put("reason",r.reason).put("reasonDetail",r.reasonDetail)
                .put("operatorName",r.operatorName).put("fiscalName",r.fiscalName)
                .put("managerName",r.managerName).put("lossPreventionName",r.lossPreventionName)
                .put("storeId",r.storeId ?: JSONObject.NULL).put("storeName",r.storeName)
                .put("pdv",r.pdv).put("ccf",r.ccf).put("notes",r.notes)
                .put("createdAt",r.createdAt).put("updatedAt",r.updatedAt))
        }
        json.put("stores", shopList).put("cancellations", rowsArray)
        return json.toString().toByteArray(Charsets.UTF_8)
    }

    data class Snapshot(val stores: List<StoreEntity>, val records: List<CancellationEntity>, val brand: BrandingSettings)

    fun decode(data: ByteArray, actorId: String): Snapshot {
        require(data.size <= 20 * 1024 * 1024) { "Arquivo excede o tamanho permitido." }
        val root = JSONObject(String(data, Charsets.UTF_8))
        require(root.getInt("format") == FORMAT) { "Formato de backup não suportado." }
        val storeArray = root.getJSONArray("stores")
        val recordsArray = root.getJSONArray("cancellations")
        require(storeArray.length() <= 500 && recordsArray.length() <= 50_000) { "Backup com registros demais." }
        // IDs gerados pelo aplicativo são UUIDs; recusar qualquer componente de caminho malicioso.
        val uuid = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
        val stores = (0 until storeArray.length()).map { i ->
            val v = storeArray.getJSONObject(i)
            val id = v.getString("id")
            val name = v.getString("name")
            require(uuid.matches(id) && name.length in 2..120) { "Identificação de loja inválida." }
            StoreEntity(id, name, v.getBoolean("active"))
        }
        val records = (0 until recordsArray.length()).map { i ->
            val v = recordsArray.getJSONObject(i)
            val date = v.getString("dateIso")
            val amount = v.getLong("amountCents")
            val coupon = v.getString("coupon")
            val reason = v.getString("reason")
            val time = v.getString("time")
            require(runCatching { LocalDate.parse(date) }.isSuccess &&
                amount in 0..999_999_999_999L && coupon.length in 1..40 &&
                reason in REASONS && validTime(time)) { "Registro de backup inválido." }
            val rowId = v.getString("id")
            require(uuid.matches(rowId)) { "Identificador de registro inválido." }
            CancellationEntity(
                id=rowId, dateIso=date, time=time, amountCents=amount, coupon=coupon, reason=reason,
                reasonDetail=v.getString("reasonDetail").take(1000),
                operatorName=v.getString("operatorName").take(120),
                fiscalName=v.getString("fiscalName").take(120),
                managerName=v.getString("managerName").take(120),
                lossPreventionName=v.getString("lossPreventionName").take(120),
                storeId=if (v.isNull("storeId")) null else v.getString("storeId"),
                storeName=v.getString("storeName").take(120),
                pdv=v.getString("pdv").take(50), ccf=v.getString("ccf").take(50), notes=v.getString("notes").take(2000),
                photoPath=null, createdBy=actorId, updatedBy=actorId,
                createdAt=v.getLong("createdAt"), updatedAt=v.getLong("updatedAt"))
        }
        val storeIds = stores.map { it.id }.toSet()
        require(records.all { it.storeId == null || it.storeId in storeIds }) { "Backup contém uma loja desconhecida." }
        val b = root.optJSONObject("branding") ?: JSONObject()
        val default = BrandingSettings()
        val title = b.optString("appName", default.appName).take(50)
        val credit = b.optString("developerCredit", default.developerCredit).take(80)
        val idea = b.optString("ideaCredit", "").take(80)
        return Snapshot(stores, records, BrandingSettings(title.ifBlank { default.appName },credit.ifBlank { default.developerCredit },idea))
    }
}
