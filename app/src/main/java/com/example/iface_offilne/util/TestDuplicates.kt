package com.example.iface_offilne.util

import android.content.Context
import android.util.Log
import com.example.iface_offilne.data.AppDatabase
import kotlinx.coroutines.runBlocking

/**
 * Classe utilitária para testar funcionalidades de duplicatas via terminal
 */
object TestDuplicates {
    
    private const val TAG = "TestDuplicates"
    
    /**
     * Função simples para testar via terminal ou logs
     */
    fun testDuplicateCheck(context: Context) {
        Log.d(TAG, "🔄 Iniciando teste de verificação de duplicatas...")
        
        runBlocking {
            try {
                val result = DuplicatePointManager.forcarMarcacaoBatidasDuplicadas(context)
                
                Log.d(TAG, "✅ Teste concluído:")
                Log.d(TAG, "  📊 Sucesso: ${result.success}")
                Log.d(TAG, "  📊 Total pendentes: ${result.totalPendentes}")
                Log.d(TAG, "  📊 Marcadas como sincronizadas: ${result.marcadasComoSincronizadas}")
                Log.d(TAG, "  📊 Mensagem: ${result.message}")
                
                if (result.batidasMarcadas.isNotEmpty()) {
                    Log.d(TAG, "  📊 Batidas marcadas:")
                    result.batidasMarcadas.forEach { batida ->
                        Log.d(TAG, "    - $batida")
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro no teste: ${e.message}")
                e.printStackTrace()
            }
        }
    }
    
    /**
     * Função para listar estatísticas das batidas
     */
    fun logDuplicateStats(context: Context) {
        Log.d(TAG, "📊 Verificando estatísticas de batidas...")
        
        runBlocking {
            try {
                val dao = AppDatabase.getInstance(context).pontosGenericosDao()
                
                val totalBatidas = dao.getAllPontos().size
                val batidasPendentes = dao.getPendingSync().size
                val batidasSincronizadas = totalBatidas - batidasPendentes
                
                Log.d(TAG, "📊 Estatísticas:")
                Log.d(TAG, "  📊 Total de batidas: $totalBatidas")
                Log.d(TAG, "  📊 Batidas sincronizadas: $batidasSincronizadas")
                Log.d(TAG, "  📊 Batidas pendentes: $batidasPendentes")
                
                // Listar algumas batidas pendentes
                if (batidasPendentes > 0) {
                    Log.d(TAG, "  📊 Algumas batidas pendentes:")
                    dao.getPendingSync().take(5).forEach { batida ->
                        Log.d(TAG, "    - ${batida.funcionarioNome} - ${batida.tipoPonto} - ${java.util.Date(batida.dataHora)}")
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao obter estatísticas: ${e.message}")
                e.printStackTrace()
            }
        }
    }
} 