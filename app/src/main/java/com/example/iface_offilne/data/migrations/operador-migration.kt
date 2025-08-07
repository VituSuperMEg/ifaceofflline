package com.example.iface_offilne.data.migrations

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.example.iface_offilne.data.AppDatabase
import com.example.iface_offilne.data.OperadorEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

val operadorMigration = object : RoomDatabase.Callback() {
    @Volatile private var INSTANCE: AppDatabase? = null


    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)

        CoroutineScope(Dispatchers.IO).launch {
            INSTANCE?.let {
                database ->
                val operador = OperadorEntity(
                    cpf = "999999999",
                    nome = "root",
                    senha = "00331520"
                )
                database.operadorDao().insert(operador)
            }
        }
    }
}
