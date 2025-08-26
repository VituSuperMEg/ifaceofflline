package com.example.iface_offilne.data.api

import android.content.Context
import android.util.Log
import com.example.iface_offilne.data.AppDatabase
import com.example.iface_offilne.data.PontosGenericosEntity
import com.example.iface_offilne.util.ConfiguracoesManager
import com.example.iface_offilne.util.ErrorMessageHelper

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.*

class SyncService(private val context: Context) {

    companion object {
        private const val TAG = "SyncService"
    }

    suspend fun sincronizarPontosComServidor(): SyncResult {
        return try {
            withContext(Dispatchers.IO) {
                Log.d(TAG, "🔄 Iniciando sincronização de pontos...")

                // 🔄 NOVO: Verificar e marcar batidas duplicadas primeiro
                Log.d(TAG, "🔍 Verificando batidas duplicadas que já foram subidas...")
                verificarEMarcarBatidasDuplicadas()

                // Buscar pontos não sincronizados
                val pontosNaoSincronizados = AppDatabase.getInstance(context)
                    .pontosGenericosDao()
                    .getPendingSync()

                if (pontosNaoSincronizados.isEmpty()) {
                    Log.d(TAG, "✅ Nenhum ponto para sincronizar")
                    return@withContext SyncResult.Success(0, "Todos os pontos já estão sincronizados")
                }

                Log.d(TAG, "📊 Encontrados ${pontosNaoSincronizados.size} pontos para sincronizar")

                // ✅ CORREÇÃO: Obter configurações para incluir no request
                val configuracoes = AppDatabase.getInstance(context)
                    .configuracoesDao()
                    .getConfiguracoes()
                
                if (configuracoes == null) {
                    Log.e(TAG, "❌ Configurações não encontradas")
                    return@withContext SyncResult.Error("Configurações não encontradas")
                }
                
                // Converter pontos para formato do servidor
                val pontosParaEnviar = pontosNaoSincronizados.map { ponto ->
                    val formato = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
                    PontoSyncRequest(
                        funcionarioId = ponto.funcionarioId,
                        funcionarioNome = ponto.funcionarioNome,
                        dataHora = formato.format(Date(ponto.dataHora)),
                        tipoPonto = ponto.tipoPonto,
                        latitude = ponto.latitude,
                        longitude = ponto.longitude,
                        observacao = ponto.observacao,
                        fotoBase64 = ponto.fotoBase64 // ✅ NOVA: Incluir foto
                    )
                }

                // ✅ NOVO: Criar request completo com configurações
                val requestCompleto = PontoSyncCompleteRequest(
                    localizacao_id = configuracoes.localizacaoId,
                    cod_sincroniza = configuracoes.codigoSincronizacao,
                    pontos = pontosParaEnviar
                )
                
                Log.d(TAG, "📤 Enviando ${pontosParaEnviar.size} pontos para o servidor")
                Log.d(TAG, "📤 Configurações:")
                Log.d(TAG, "📤   - localizacao_id: ${requestCompleto.localizacao_id}")
                Log.d(TAG, "📤   - cod_sincroniza: ${requestCompleto.cod_sincroniza}")
                
                // Log do JSON que será enviado
                val gson = com.google.gson.Gson()
                val jsonEnviado = gson.toJson(requestCompleto)
                Log.d(TAG, "📤 JSON sendo enviado para o servidor:")
                Log.d(TAG, "📤 $jsonEnviado")
                
                // Log individual de cada ponto
                pontosParaEnviar.forEachIndexed { index, ponto ->
                    Log.d(TAG, "📤 Ponto ${index + 1}:")
                    Log.d(TAG, "📤   - funcionarioId: ${ponto.funcionarioId}")
                    Log.d(TAG, "📤   - funcionarioNome: ${ponto.funcionarioNome}")
                    Log.d(TAG, "📤   - dataHora: ${ponto.dataHora}")
                    Log.d(TAG, "📤   - tipoPonto: ${ponto.tipoPonto}")
                    Log.d(TAG, "📤   - latitude: ${ponto.latitude}")
                    Log.d(TAG, "📤   - longitude: ${ponto.longitude}")
                    Log.d(TAG, "📤   - observacao: ${ponto.observacao}")
                }

                // Obter entidade atual das configurações
                val entidade = ConfiguracoesManager.getEntidadeId(context)
                if (entidade.isEmpty()) {
                    Log.e(TAG, "❌ Entidade não configurada")
                    return@withContext SyncResult.Error("Entidade não configurada")
                }

                Log.d(TAG, "🏢 Usando entidade: $entidade")

                // Enviar para o servidor
                val apiService = RetrofitClient.instance
                
                // ✅ NOVO: Primeiro tentar com formato completo e resposta vazia
                try {
                    Log.d(TAG, "🔄 Tentando sincronização com formato completo e resposta vazia...")
                    val responseVazio: Response<Unit> = apiService.sincronizarPontosCompletoVazio(entidade, requestCompleto)
                    
                    Log.d(TAG, "📡 Resposta vazia - Código: ${responseVazio.code()}")
                    Log.d(TAG, "📡 Resposta vazia - Sucesso: ${responseVazio.isSuccessful}")
                    
                    if (responseVazio.isSuccessful) {
                        Log.d(TAG, "✅ Sincronização com formato completo e resposta vazia bem-sucedida")
                        
                        // Marcar pontos como sincronizados
                        pontosNaoSincronizados.forEach { ponto ->
                            AppDatabase.getInstance(context)
                                .pontosGenericosDao()
                                .markAsSynced(ponto.id)
                        }
                        
                        return@withContext SyncResult.Success(
                            pontosNaoSincronizados.size,
                            "Pontos sincronizados com sucesso (formato completo)"
                        )
                    } else {
                        Log.d(TAG, "⚠️ Resposta vazia falhou, tentando com resposta completa...")
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "⚠️ Erro com formato completo: ${e.message}, tentando formato antigo...")
                }
                
                // ✅ NOVO: Se falhou, tentar com formato completo e resposta completa
                val response: Response<PontoSyncResponse> = apiService.sincronizarPontosCompleto(entidade, requestCompleto)

                Log.d(TAG, "📡 Resposta do servidor - Código: ${response.code()}")
                Log.d(TAG, "📡 Resposta do servidor - Mensagem: ${response.message()}")
                Log.d(TAG, "📡 Resposta do servidor - Sucesso: ${response.isSuccessful}")

                if (response.isSuccessful) {
                    val syncResponse = response.body()
                    Log.d(TAG, "📡 Corpo da resposta: $syncResponse")
                    
                    if (syncResponse != null) {
                        if (syncResponse.success) {
                            // Marcar pontos como sincronizados
                            pontosNaoSincronizados.forEach { ponto ->
                                AppDatabase.getInstance(context)
                                    .pontosGenericosDao()
                                    .markAsSynced(ponto.id)
                            }

                            Log.d(TAG, "✅ Sincronização concluída: ${syncResponse.pontosSincronizados} pontos")
                            SyncResult.Success(
                                syncResponse.pontosSincronizados,
                                syncResponse.message
                            )
                        } else {
                            Log.e(TAG, "❌ Erro na sincronização: ${syncResponse.message}")
                            SyncResult.Error(syncResponse.message)
                        }
                    } else {
                        // Tentar ler o corpo da resposta como string para debug
                        val responseBody = response.body()?.toString() ?: "null"
                        val rawResponse = response.raw().toString()
                        
                        Log.e(TAG, "❌ Resposta vazia do servidor")
                        Log.e(TAG, "❌ Response body: $responseBody")
                        Log.e(TAG, "❌ Raw response: $rawResponse")
                        
                        // Se a resposta for vazia mas o código HTTP for 200, considerar sucesso
                        if (response.code() == 200) {
                            Log.d(TAG, "✅ Código 200 - considerando sucesso mesmo com resposta vazia")
                            
                            // Marcar pontos como sincronizados mesmo com resposta vazia
                            pontosNaoSincronizados.forEach { ponto ->
                                AppDatabase.getInstance(context)
                                    .pontosGenericosDao()
                                    .markAsSynced(ponto.id)
                            }
                            
                            SyncResult.Success(
                                pontosNaoSincronizados.size,
                                "Pontos sincronizados (resposta vazia do servidor)"
                            )
                        } else {
                            SyncResult.Error("Resposta vazia do servidor")
                        }
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    Log.e(TAG, "❌ Erro HTTP: ${response.code()} - ${response.message()}")
                    Log.e(TAG, "❌ Corpo do erro: $errorBody")
                    
                    // 🔄 NOVO: Detectar e tratar erros de duplicata
                    if (response.code() == 400 && errorBody != null) {
                        val duplicataResolvida = tratarErroDuplicata(errorBody, pontosNaoSincronizados)
                        if (duplicataResolvida) {
                            return@withContext SyncResult.Success(
                                pontosNaoSincronizados.size,
                                "Batidas duplicadas resolvidas automaticamente"
                            )
                        }
                    }
                    
                    SyncResult.Error(ErrorMessageHelper.getErrorMessage(Exception("HTTP ${response.code()}")))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro na sincronização", e)
            Log.e(TAG, "❌ Tipo do erro: ${e.javaClass.simpleName}")
            Log.e(TAG, "❌ Mensagem do erro: ${e.message}")
            e.printStackTrace()
            SyncResult.Error(ErrorMessageHelper.getErrorMessage(e))
        }
    }

    suspend fun testarConexaoComServidor(): SyncResult {
        return try {
            withContext(Dispatchers.IO) {
                Log.d(TAG, "🧪 Testando conexão com o servidor...")

                val entidade = ConfiguracoesManager.getEntidadeId(context)
                if (entidade.isEmpty()) {
                    Log.e(TAG, "❌ Entidade não configurada")
                    return@withContext SyncResult.Error("Entidade não configurada")
                }

                Log.d(TAG, "🏢 Testando entidade: $entidade")

                val apiService = RetrofitClient.instance
                val response: Response<SimpleResponse> = apiService.testConnection(entidade)

                Log.d(TAG, "📡 Teste - Código: ${response.code()}")
                Log.d(TAG, "📡 Teste - Mensagem: ${response.message()}")
                Log.d(TAG, "📡 Teste - Sucesso: ${response.isSuccessful}")

                if (response.isSuccessful) {
                    val testResponse = response.body()
                    Log.d(TAG, "📡 Teste - Corpo: $testResponse")
                    
                    if (testResponse != null) {
                        Log.d(TAG, "✅ Conexão com servidor OK")
                        SyncResult.Success(0, "Conexão com servidor OK")
                    } else {
                        Log.e(TAG, "❌ Resposta vazia do teste")
                        SyncResult.Error("Resposta vazia do teste")
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    Log.e(TAG, "❌ Erro no teste: ${response.code()} - ${response.message()}")
                    Log.e(TAG, "❌ Corpo do erro: $errorBody")
                    SyncResult.Error(ErrorMessageHelper.getErrorMessage(Exception("HTTP ${response.code()}")))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro no teste", e)
            Log.e(TAG, "❌ Tipo do erro: ${e.javaClass.simpleName}")
            Log.e(TAG, "❌ Mensagem do erro: ${e.message}")
            e.printStackTrace()
            SyncResult.Error(ErrorMessageHelper.getErrorMessage(e))
        }
    }
    
    /**
     * Verifica e marca como sincronizadas as batidas que podem ter sido subidas anteriormente
     * mas não foram marcadas devido a problemas de sincronização
     */
    private suspend fun verificarEMarcarBatidasDuplicadas() {
        try {
            Log.d(TAG, "🔍 Iniciando verificação de batidas duplicadas...")
            
            val pontosGenericosDao = AppDatabase.getInstance(context).pontosGenericosDao()
            
            // Buscar todas as batidas pendentes
            val pontosPendentes = pontosGenericosDao.getPendingSync()
            Log.d(TAG, "📊 Total de batidas pendentes para verificar: ${pontosPendentes.size}")
            
            var marcadasComoSincronizadas = 0
            
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
                    Log.d(TAG, "  📍 Data pendente: ${java.util.Date(pontoPendente.dataHora)}")
                    Log.d(TAG, "  📍 Data sincronizada: ${java.util.Date(duplicata.dataHora)}")
                    
                    // Marcar a batida pendente como sincronizada
                    pontosGenericosDao.markAsSynced(pontoPendente.id)
                    marcadasComoSincronizadas++
                    
                    Log.d(TAG, "✅ Batida marcada como sincronizada (ID: ${pontoPendente.id})")
                }
            }
            
            if (marcadasComoSincronizadas > 0) {
                Log.d(TAG, "✅ Total de batidas marcadas como sincronizadas: $marcadasComoSincronizadas")
            } else {
                Log.d(TAG, "ℹ️ Nenhuma batida duplicada encontrada")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao verificar batidas duplicadas: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * Trata erros de duplicata do servidor e marca as batidas como sincronizadas
     * @param errorBody Corpo da resposta de erro da API
     * @param pontosNaoSincronizados Lista de pontos que não foram sincronizados
     * @return true se conseguiu tratar o erro, false caso contrário
     */
    private suspend fun tratarErroDuplicata(
        errorBody: String,
        pontosNaoSincronizados: List<PontosGenericosEntity>
    ): Boolean {
        return try {
            Log.d(TAG, "🔍 === ANALISANDO ERRO DE DUPLICATA (SyncService) ===")
            
            // Verificar se é erro de chave duplicada
            val isDuplicateError = errorBody.contains("duplicate key value violates unique constraint") ||
                                 errorBody.contains("Unique violation") ||
                                 errorBody.contains("already exists") ||
                                 errorBody.contains("SQLSTATE[23505]")
            
            if (isDuplicateError) {
                Log.d(TAG, "🎯 DETECTADO: Erro de batida duplicada no servidor!")
                Log.d(TAG, "🔄 Marcando batidas como sincronizadas automaticamente...")
                
                val pontosGenericosDao = AppDatabase.getInstance(context).pontosGenericosDao()
                var batidasMarcadas = 0
                
                // Extrair informações da batida duplicada do erro (se possível)
                val regex = """\(data_ponto, funcionario_vinculo_id\)=\(([^,]+), (\d+)\)""".toRegex()
                val matchResult = regex.find(errorBody)
                
                if (matchResult != null) {
                    val dataHoraDuplicada = matchResult.groupValues[1]
                    val funcionarioId = matchResult.groupValues[2]
                    
                    Log.d(TAG, "📊 Batida duplicada identificada:")
                    Log.d(TAG, "  📅 Data/Hora: $dataHoraDuplicada")
                    Log.d(TAG, "  👤 Funcionário ID: $funcionarioId")
                    
                    // Marcar especificamente esta batida como sincronizada
                    val formato = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                    try {
                        val dataHoraTimestamp = formato.parse(dataHoraDuplicada)?.time ?: 0L
                        
                        pontosNaoSincronizados.forEach { ponto ->
                            if (ponto.funcionarioId == funcionarioId && 
                                Math.abs(ponto.dataHora - dataHoraTimestamp) <= 60000) { // 1 minuto de tolerância
                                pontosGenericosDao.markAsSynced(ponto.id)
                                batidasMarcadas++
                                Log.d(TAG, "✅ Batida marcada: ${ponto.funcionarioNome} - ${java.util.Date(ponto.dataHora)}")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Erro ao converter data: ${e.message}")
                    }
                }
                
                // Se não conseguiu identificar especificamente, marcar todas as batidas enviadas
                if (batidasMarcadas == 0) {
                    Log.d(TAG, "⚠️ Não foi possível identificar batida específica, marcando todas as enviadas...")
                    
                    pontosNaoSincronizados.forEach { ponto ->
                        pontosGenericosDao.markAsSynced(ponto.id)
                        batidasMarcadas++
                        Log.d(TAG, "✅ Batida marcada (fallback): ${ponto.funcionarioNome}")
                    }
                }
                
                if (batidasMarcadas > 0) {
                    Log.d(TAG, "🎉 === DUPLICATAS RESOLVIDAS AUTOMATICAMENTE ===")
                    Log.d(TAG, "✅ $batidasMarcadas batidas foram marcadas como sincronizadas")
                    Log.d(TAG, "💡 Problema: Essas batidas já existiam no servidor")
                    Log.d(TAG, "💡 Solução: Marcadas localmente para evitar reenvio")
                    true
                } else {
                    Log.e(TAG, "❌ Não foi possível identificar batidas duplicadas")
                    false
                }
            } else {
                Log.e(TAG, "❌ Erro não é de duplicata, mantendo como falha")
                false
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao tratar duplicata: ${e.message}")
            e.printStackTrace()
            false
        }
    }
}

sealed class SyncResult {
    data class Success(val pontosSincronizados: Int, val message: String) : SyncResult()
    data class Error(val message: String) : SyncResult()
} 