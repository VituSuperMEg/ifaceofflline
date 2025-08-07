package com.example.iface_offilne.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.iface_offilne.data.OperadorEntity

@Dao
interface OperadorDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun  insert(operador: OperadorEntity)

    @Query("SELECT *  from operador where cpf = :cpf and senha = :senha limit 1")
    suspend fun getOperador(cpf: String, senha: String): OperadorEntity?
}