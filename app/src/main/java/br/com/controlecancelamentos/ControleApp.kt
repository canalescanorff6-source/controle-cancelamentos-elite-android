package br.com.controlecancelamentos

import android.app.Application
import androidx.room.Room
import br.com.controlecancelamentos.data.AppDatabase

class ControleApp: Application() {
    val db: AppDatabase by lazy {
        Room.databaseBuilder(applicationContext, AppDatabase::class.java, "cancelamentos-v1.db")
            .build()
    }
}
