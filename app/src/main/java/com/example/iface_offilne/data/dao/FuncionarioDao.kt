package com.example.iface_offilne.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.iface_offilne.data.FuncionariosEntity


@Dao
interface FuncionarioDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(funcionario: FuncionariosEntity)
    
    @Update
    suspend fun update(funcionario: FuncionariosEntity)
    
    @Query("SELECT * FROM funcionarios WHERE id = :id")
    suspend fun getById(id: Int): FuncionariosEntity?
    
    @Query("SELECT * FROM funcionarios")
    suspend fun getAll(): List<FuncionariosEntity>
    
    @Delete
    suspend fun delete(funcionario: FuncionariosEntity)
}