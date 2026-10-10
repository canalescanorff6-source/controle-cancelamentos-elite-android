package br.com.controlecancelamentos.data

import android.content.Context

/** Preferência exclusiva deste aparelho: 0 = sistema, 1 = claro, 2 = escuro. */
object AppearanceStore {
    const val SYSTEM = 0
    const val LIGHT = 1
    const val DARK = 2
    fun read(context: Context): Int = context.getSharedPreferences("appearance_v3", Context.MODE_PRIVATE)
        .getInt("mode", SYSTEM).takeIf { it in SYSTEM..DARK } ?: SYSTEM
    fun save(context: Context, value: Int) {
        require(value in SYSTEM..DARK)
        context.getSharedPreferences("appearance_v3", Context.MODE_PRIVATE).edit().putInt("mode", value).apply()
    }
}
