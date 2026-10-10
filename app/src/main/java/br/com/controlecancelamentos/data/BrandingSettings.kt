package br.com.controlecancelamentos.data

import android.content.Context

data class BrandingSettings(
    val appName: String = "Controle de Cancelamentos Elite",
    val developerCredit: String = "Edição Marques • Desenvolvimento",
    val ideaCredit: String = ""
)

/** Identidade de marca local, editável pelo administrador. Não confere titularidade intelectual. */
object BrandingStore {
    private const val FILE = "identidade_local_v2"
    fun read(context: Context): BrandingSettings {
        val pref = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val default = BrandingSettings()
        return BrandingSettings(
            pref.getString("appName", default.appName).orEmpty().ifBlank { default.appName },
            pref.getString("developerCredit", default.developerCredit).orEmpty().ifBlank { default.developerCredit },
            pref.getString("ideaCredit", "").orEmpty()
        )
    }
    fun save(context: Context, input: BrandingSettings) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString("appName", input.appName.trim())
            .putString("developerCredit", input.developerCredit.trim())
            .putString("ideaCredit", input.ideaCredit.trim())
            .apply()
    }
}
