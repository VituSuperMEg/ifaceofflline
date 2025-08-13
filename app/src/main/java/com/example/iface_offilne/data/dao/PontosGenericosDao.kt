package com.example.iface_offilne.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.iface_offilne.data.PontosGenericosEntity

@Dao
interface PontosGenericosDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(ponto: PontosGenericosEntity)
    
    @Query("SELECT * FROM pontos_genericos WHERE funcionarioId = :funcionarioId ORDER BY dataHora DESC")
    suspend fun getPontosByFuncionario(funcionarioId: String): List<PontosGenericosEntity>
    
    @Query("SELECT * FROM pontos_genericos WHERE funcionarioId = :funcionarioId AND dataHora >= :dataInicio AND dataHora <= :dataFim ORDER BY dataHora DESC")
    suspend fun getPontosByFuncionarioAndPeriodo(funcionarioId: String, dataInicio: Long, dataFim: Long): List<PontosGenericosEntity>
    
    @Query("SELECT * FROM pontos_genericos WHERE synced = 0")
    suspend fun getPendingSync(): List<PontosGenericosEntity>
    
    @Query("UPDATE pontos_genericos SET synced = 1 WHERE id = :id")
    suspend fun markAsSynced(id: Int)
    
    @Query("SELECT * FROM pontos_genericos ORDER BY dataHora DESC LIMIT 100")
    suspend fun getAllPontos(): List<PontosGenericosEntity>
    
    @Query("SELECT * FROM pontos_genericos WHERE funcionarioId = :funcionarioId ORDER BY dataHora DESC LIMIT 1")
    suspend fun getUltimoPonto(funcionarioId: String): PontosGenericosEntity?
    
    @Delete
    suspend fun delete(ponto: PontosGenericosEntity)
    
    // Novos métodos para tratar batidas duplicadas
    @Query("""
        SELECT * FROM pontos_genericos 
        WHERE funcionarioId = :funcionarioId 
        AND tipoPonto = :tipoPonto 
        AND ABS(dataHora - :dataHora) <= :toleranciaMs 
        AND synced = 1
        LIMIT 1
    """)
    suspend fun findDuplicateSync(funcionarioId: String, tipoPonto: String, dataHora: Long, toleranciaMs: Long = 300000): PontosGenericosEntity?
    
    @Query("""
        UPDATE pontos_genericos 
        SET synced = 1 
        WHERE funcionarioId = :funcionarioId 
        AND tipoPonto = :tipoPonto 
        AND ABS(dataHora - :dataHora) <= :toleranciaMs 
        AND synced = 0
    """)
    suspend fun markDuplicatesAsSynced(funcionarioId: String, tipoPonto: String, dataHora: Long, toleranciaMs: Long = 300000): Int
    
    @Query("UPDATE pontos_genericos SET synced = 1 WHERE synced = 0 AND id IN (:ids)")
    suspend fun markMultipleAsSynced(ids: List<Int>): Int
    
    @Query("SELECT COUNT(*) FROM pontos_genericos WHERE synced = 0")
    suspend fun countPendingSync(): Int
} 