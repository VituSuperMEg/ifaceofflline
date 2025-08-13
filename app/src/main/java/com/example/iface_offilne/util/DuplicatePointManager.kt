package com.example.iface_offilne.util

import android.content.Context
import android.util.Log
import com.example.iface_offilne.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date

/**
 * Utilitário para gerenciar batidas duplicadas e pendentes
 */
object DuplicatePointManager {
    
    private const val TAG = "DuplicatePointManager"
    
    /**
     * Força a verificação e marcação de todas as batidas duplicadas como sincronizadas
     */
    suspend fun forcarMarcacaoBatidasDuplicadas(context: Context): DuplicateCheckResult {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "🔄 Iniciando verificação FORÇADA de batidas duplicadas...")
                
                val pontosGenericosDao = AppDatabase.getInstance(context).pontosGenericosDao()
                
                // Buscar todas as batidas pendentes
                val pontosPendentes = pontosGenericosDao.getPendingSync()
                Log.d(TAG, "📊 Total de batidas pendentes: ${pontosPendentes.size}")
                
                if (pontosPendentes.isEmpty()) {
                    return@withContext DuplicateCheckResult(
                        success = true,
                        totalPendentes = 0,
                        marcadasComoSincronizadas = 0,
                        message = "Nenhuma batida pendente encontrada"
                    )
                }
                
                var marcadasComoSincronizadas = 0
                val batidas = mutableListOf<String>()
                
                // Para cada batida pendente, verificar se já existe uma similar que foi sincronizada
                for (pontoPendente in pontosPendentes) {
                    val duplicata = pontosGenericosDao.findDuplicateSync(
                        funcionarioId = pontoPendente.funcionarioId,
                        tipoPonto = pontoPendente.tipoPonto,
                        dataHora = pontoPendente.dataHora,
                        toleranciaMs = 300000 // 5 minutos de tolerância
                    )
                    
                    if (duplicata != null) {
                        Log.d(TAG, "🔄 Batida duplicada encontrada:")
                        Log.d(TAG, "  📍 Funcionário: ${pontoPendente.funcionarioNome}")
                        Log.d(TAG, "  📍 Tipo: ${pontoPendente.tipoPonto}")
                        Log.d(TAG, "  📍 Data pendente: ${Date(pontoPendente.dataHora)}")
                        Log.d(TAG, "  📍 Data sincronizada: ${Date(duplicata.dataHora)}")
                        
                        // Marcar a batida pendente como sincronizada
                        pontosGenericosDao.markAsSynced(pontoPendente.id)
                        marcadasComoSincronizadas++
                        
                        batidas.add("${pontoPendente.funcionarioNome} - ${pontoPendente.tipoPonto} - ${Date(pontoPendente.dataHora)}")
                        
                        Log.d(TAG, "✅ Batida marcada como sincronizada (ID: ${pontoPendente.id})")
                    }
                }
                
                val message = if (marcadasComoSincronizadas > 0) {
                    "✅ $marcadasComoSincronizadas batidas foram marcadas como sincronizadas"
                } else {
                    "ℹ️ Nenhuma batida duplicada encontrada"
                }
                
                Log.d(TAG, message)
                
                DuplicateCheckResult(
                    success = true,
                    totalPendentes = pontosPendentes.size,
                    marcadasComoSincronizadas = marcadasComoSincronizadas,
                    message = message,
                    batidasMarcadas = batidas
                )
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao verificar batidas duplicadas: ${e.message}")
                e.printStackTrace()
                
                DuplicateCheckResult(
                    success = false,
                    totalPendentes = 0,
                    marcadasComoSincronizadas = 0,
                    message = "Erro: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Força a marcação de TODAS as batidas pendentes como sincronizadas
     * USE COM CUIDADO - Só use se tiver certeza que todas já foram subidas
     */
    suspend fun forcarMarcacaoTodasBatidas(context: Context): DuplicateCheckResult {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "⚠️ FORÇANDO marcação de TODAS as batidas como sincronizadas...")
                
                val pontosGenericosDao = AppDatabase.getInstance(context).pontosGenericosDao()
                
                // Buscar todas as batidas pendentes
                val pontosPendentes = pontosGenericosDao.getPendingSync()
                Log.d(TAG, "📊 Total de batidas que serão marcadas: ${pontosPendentes.size}")
                
                if (pontosPendentes.isEmpty()) {
                    return@withContext DuplicateCheckResult(
                        success = true,
                        totalPendentes = 0,
                        marcadasComoSincronizadas = 0,
                        message = "Nenhuma batida pendente encontrada"
                    )
                }
                
                val batidas = mutableListOf<String>()
                
                // Marcar todas como sincronizadas
                for (pontoPendente in pontosPendentes) {
                    pontosGenericosDao.markAsSynced(pontoPendente.id)
                    batidas.add("${pontoPendente.funcionarioNome} - ${pontoPendente.tipoPonto} - ${Date(pontoPendente.dataHora)}")
                    Log.d(TAG, "✅ Batida marcada como sincronizada: ${pontoPendente.funcionarioNome} - ${pontoPendente.tipoPonto}")
                }
                
                val message = "⚠️ TODAS as ${pontosPendentes.size} batidas foram FORÇADAMENTE marcadas como sincronizadas"
                Log.d(TAG, message)
                
                DuplicateCheckResult(
                    success = true,
                    totalPendentes = pontosPendentes.size,
                    marcadasComoSincronizadas = pontosPendentes.size,
                    message = message,
                    batidasMarcadas = batidas
                )
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao forçar marcação: ${e.message}")
                e.printStackTrace()
                
                DuplicateCheckResult(
                    success = false,
                    totalPendentes = 0,
                    marcadasComoSincronizadas = 0,
                    message = "Erro: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Executa a verificação na thread principal com callback
     */
    fun forcarMarcacaoBatidasDuplicadasAsync(context: Context, callback: (DuplicateCheckResult) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            val result = forcarMarcacaoBatidasDuplicadas(context)
            withContext(Dispatchers.Main) {
                callback(result)
            }
        }
    }
    
    /**
     * Executa a marcação forçada na thread principal com callback
     */
    fun forcarMarcacaoTodasBatidasAsync(context: Context, callback: (DuplicateCheckResult) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            val result = forcarMarcacaoTodasBatidas(context)
            withContext(Dispatchers.Main) {
                callback(result)
            }
        }
    }
}

/**
 * Resultado da verificação de batidas duplicadas
 */
data class DuplicateCheckResult(
    val success: Boolean,
    val totalPendentes: Int,
    val marcadasComoSincronizadas: Int,
    val message: String,
    val batidasMarcadas: List<String> = emptyList()
) 