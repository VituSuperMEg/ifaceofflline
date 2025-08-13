package com.example.iface_offilne.data.dao

import androidx.room.*
import com.example.iface_offilne.data.ConfiguracoesEntity

@Dao
interface ConfiguracoesDao {
    
    @Query("SELECT * FROM configuracoes WHERE id = 1")
    suspend fun getConfiguracoes(): ConfiguracoesEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConfiguracoes(configuracoes: ConfiguracoesEntity)
    
    @Update
    suspend fun updateConfiguracoes(configuracoes: ConfiguracoesEntity)
    
    @Query("DELETE FROM configuracoes WHERE id = 1")
    suspend fun deleteConfiguracoes()
    
    @Query("SELECT COUNT(*) FROM configuracoes WHERE id = 1")
    suspend fun hasConfiguracoes(): Int
} 