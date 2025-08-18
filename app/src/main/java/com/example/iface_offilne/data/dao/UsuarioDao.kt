package com.example.iface_offilne.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.iface_offilne.data.FuncionariosEntity

@Dao
interface UsuarioDao {

    @Query("SELECT * FROM funcionarios ORDER BY nome ASC")
    suspend fun getUsuario(): List<FuncionariosEntity>

    @Insert
    suspend fun insert(funcionario: FuncionariosEntity)
}