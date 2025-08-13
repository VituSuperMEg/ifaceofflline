package com.example.iface_offilne.data.dao

import androidx.room.*
import com.example.iface_offilne.data.PontoSincronizacaoEntity

@Dao
interface PontoSincronizacaoDao {
    
    @Query("SELECT * FROM pontos_sincronizacao WHERE sincronizado = 0 ORDER BY dataHora ASC")
    suspend fun getPontosNaoSincronizados(): List<PontoSincronizacaoEntity>
    
    @Query("SELECT * FROM pontos_sincronizacao ORDER BY dataHora DESC")
    suspend fun getAllPontos(): List<PontoSincronizacaoEntity>
    
    @Query("SELECT * FROM pontos_sincronizacao WHERE funcionarioId = :funcionarioId ORDER BY dataHora DESC")
    suspend fun getPontosByFuncionario(funcionarioId: String): List<PontoSincronizacaoEntity>
    
    @Insert
    suspend fun insertPonto(ponto: PontoSincronizacaoEntity)
    
    @Update
    suspend fun updatePonto(ponto: PontoSincronizacaoEntity)
    
    @Query("UPDATE pontos_sincronizacao SET sincronizado = 1, dataSincronizacao = :dataSincronizacao WHERE id = :pontoId")
    suspend fun marcarComoSincronizado(pontoId: Long, dataSincronizacao: String)
    
    @Query("UPDATE pontos_sincronizacao SET sincronizado = 1, dataSincronizacao = :dataSincronizacao WHERE id IN (:pontoIds)")
    suspend fun marcarMultiplosComoSincronizados(pontoIds: List<Long>, dataSincronizacao: String)
    
    @Query("DELETE FROM pontos_sincronizacao WHERE sincronizado = 1 AND dataSincronizacao < :dataLimite")
    suspend fun limparPontosSincronizadosAntigos(dataLimite: String): Int
    
    @Query("SELECT COUNT(*) FROM pontos_sincronizacao WHERE sincronizado = 0")
    suspend fun getQuantidadePontosNaoSincronizados(): Int
} 