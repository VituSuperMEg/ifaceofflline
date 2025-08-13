package com.example.iface_offilne.data.dao

import androidx.room.*
import com.example.iface_offilne.data.HistoricoSincronizacaoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoricoSincronizacaoDao {
    
    @Query("SELECT * FROM historico_sincronizacao ORDER BY dataHora DESC LIMIT 50")
    fun getUltimasSincronizacoes(): Flow<List<HistoricoSincronizacaoEntity>>
    
    @Query("SELECT * FROM historico_sincronizacao ORDER BY dataHora DESC")
    suspend fun getAllSincronizacoes(): List<HistoricoSincronizacaoEntity>
    
    @Insert
    suspend fun insertSincronizacao(sincronizacao: HistoricoSincronizacaoEntity)
    
    @Update
    suspend fun updateSincronizacao(sincronizacao: HistoricoSincronizacaoEntity)
    
    @Delete
    suspend fun deleteSincronizacao(sincronizacao: HistoricoSincronizacaoEntity)
    
    @Query("DELETE FROM historico_sincronizacao WHERE dataHora < :dataLimite")
    suspend fun deleteSincronizacoesAntigas(dataLimite: java.util.Date)
} 