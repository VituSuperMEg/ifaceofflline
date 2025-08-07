package com.example.iface_offilne.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.iface_offilne.data.dao.FuncionarioDao
import com.example.iface_offilne.data.dao.OperadorDao
import com.example.iface_offilne.data.dao.PontosGenericosDao
import com.example.iface_offilne.data.dao.UsuarioDao
import com.example.iface_offilne.data.migrations.operadorMigration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(entities = [FaceEntity::class, OperadorEntity::class, FuncionariosEntity::class, PontosGenericosEntity::class], version = 2)
abstract class AppDatabase : RoomDatabase() {
    abstract fun faceDao(): FaceDao
    abstract fun operadorDao(): OperadorDao

    abstract fun funcionarioDao(): FuncionarioDao

    abstract fun usuariosDao(): UsuarioDao
    
    abstract fun pontosGenericosDao(): PontosGenericosDao


    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "faces.db"
                )
                    .fallbackToDestructiveMigration() // Para desenvolvimento - remove em produção
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            insertDefaultOperator(context)
                        }
                        
                        override fun onOpen(db: SupportSQLiteDatabase) {
                            super.onOpen(db)
                            // Sempre verificar e inserir o operador ROOT se não existir
                            insertDefaultOperator(context)
                        }
                    })
                    .build()
                INSTANCE = instance
                instance
            }
        }
        
        private fun insertDefaultOperator(context: Context) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val database = getInstance(context)
                    val operadorDao = database.operadorDao()
                    
                    // Verificar se o operador ROOT já existe
                    val existingOperator = operadorDao.getOperador("99999999", "00331520")
                    
                    if (existingOperator == null) {
                        // Inserir operador ROOT se não existir
                        operadorDao.insert(
                            OperadorEntity(
                                cpf = "99999999",
                                nome = "ROOT",
                                senha = "00331520"
                            )
                        )
                        android.util.Log.d("AppDatabase", "✅ Operador ROOT criado com sucesso")
                    } else {
                        android.util.Log.d("AppDatabase", "✅ Operador ROOT já existe")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("AppDatabase", "❌ Erro ao criar operador ROOT: ${e.message}")
                }
            }
        }
    }
}
