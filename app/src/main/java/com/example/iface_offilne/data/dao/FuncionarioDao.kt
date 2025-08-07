package com.example.iface_offilne.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import com.example.iface_offilne.data.FuncionariosEntity


@Dao
interface FuncionarioDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(funcionario: FuncionariosEntity)
}