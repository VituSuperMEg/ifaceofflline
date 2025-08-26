package com.example.iface_offilne.service

import android.content.Context
import android.util.Log
import com.example.iface_offilne.data.AppDatabase
import com.example.iface_offilne.data.HistoricoSincronizacaoEntity
import com.example.iface_offilne.data.PontoSincronizacaoEntity
import com.example.iface_offilne.data.dao.PontosGenericosDao
import com.example.iface_offilne.data.api.RetrofitClient
import com.example.iface_offilne.util.ConfiguracoesManager
import com.example.iface_offilne.util.ErrorMessageHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class PontoSincronizacaoService {
    
    companion object {
        private const val TAG = "SYNC_DEBUG"
    }

    // Salvar ponto no Room para sincronização posterior
    suspend fun salvarPontoParaSincronizacao(
        context: Context,
        funcionarioId: String,
        funcionarioNome: String,
        tipo: String, // "entrada" ou "saida"
        fotoBase64: String? = null, // 🆕 Foto da batida em base64
        latitude: Double? = null, // ✅ NOVA: Latitude
        longitude: Double? = null // ✅ NOVA: Longitude
    ) {
        withContext(Dispatchers.IO) {
            try {
                // ✅ PROTEÇÃO: Validar parâmetros
                if (funcionarioId.isEmpty()) {
                    Log.e(TAG, "❌ ID do funcionário vazio")
                    return@withContext
                }
                
                if (funcionarioNome.isEmpty()) {
                    Log.e(TAG, "❌ Nome do funcionário vazio")
                    return@withContext
                }
                
                val database = AppDatabase.getInstance(context)
                val pontoDao = database.pontoSincronizacaoDao()
                val funcionarioDao = database.funcionarioDao()
                
                // ✅ PROTEÇÃO: Obter configurações com fallback
                val localizacaoId = try {
                    ConfiguracoesManager.getLocalizacaoId(context)
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ Erro ao obter localização ID: ${e.message}")
                    ""
                }
                
                val codigoSincronizacao = try {
                    ConfiguracoesManager.getCodigoSincronizacao(context)
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ Erro ao obter código de sincronização: ${e.message}")
                    ""
                }
                
                // ✅ PROTEÇÃO: Buscar informações completas do funcionário
                val funcionario = try {
                    funcionarioDao.getById(funcionarioId.toIntOrNull() ?: 0)
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ Erro ao buscar funcionário: ${e.message}")
                    null
                }
                
                // Formatar data/hora atual
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                val dataHora = sdf.format(Date())
                
                // ✅ PROTEÇÃO: Criar entidade do ponto com informações completas
                val ponto = try {
                    PontoSincronizacaoEntity(
                        funcionarioId = funcionarioId,
                        funcionarioNome = funcionarioNome,
                        funcionarioMatricula = funcionario?.matricula ?: "",
                        funcionarioCpf = funcionario?.cpf ?: "",
                        funcionarioCargo = funcionario?.cargo ?: "",
                        funcionarioSecretaria = funcionario?.secretaria ?: "",
                        funcionarioLotacao = funcionario?.lotacao ?: "",
                        dataHora = dataHora,
                        tipo = tipo,
                        sincronizado = false,
                        localizacaoId = localizacaoId,
                        codigoSincronizacao = codigoSincronizacao,
                        fotoBase64 = fotoBase64, // 🆕 Incluir foto
                        latitude = latitude, // ✅ NOVA: Incluir latitude
                        longitude = longitude // ✅ NOVA: Incluir longitude
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Erro ao criar entidade de ponto: ${e.message}")
                    return@withContext
                }
                
                // ✅ PROTEÇÃO: Salvar no Room com retry
                var pontoSalvo = false
                var tentativas = 0
                val maxTentativas = 3
                
                while (!pontoSalvo && tentativas < maxTentativas) {
                    try {
                        tentativas++
                        Log.d(TAG, "💾 Tentativa $tentativas de salvar ponto para sincronização...")
                        
                        pontoDao.insertPonto(ponto)
                        pontoSalvo = true
                        Log.d(TAG, "✅ Ponto salvo para sincronização na tentativa $tentativas: $funcionarioId - $tipo - $dataHora")
                        
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Erro na tentativa $tentativas: ${e.message}")
                        if (tentativas >= maxTentativas) {
                            Log.e(TAG, "❌ Falha ao salvar ponto para sincronização após $maxTentativas tentativas")
                            return@withContext
                        }
                        // Aguardar um pouco antes da próxima tentativa
                        kotlinx.coroutines.delay(500)
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao salvar ponto para sincronização: ${e.message}")
                // ✅ PROTEÇÃO: Não fazer throw para evitar crash
                // Apenas logar o erro e continuar
            }
        }
    }

    // Sincronizar pontos pendentes
    suspend fun sincronizarPontosPendentes(context: Context): SincronizacaoResult {
        return withContext(Dispatchers.IO) {
            val tempoInicio = System.currentTimeMillis()
            
            try {
                Log.d(TAG, "🚀 === INICIANDO SINCRONIZAÇÃO REAL ===")
                
                // ✅ VERIFICAÇÃO PRIORITÁRIA: Entidade configurada
                val entidadeId = ConfiguracoesManager.getEntidadeId(context)
                if (entidadeId.isEmpty()) {
                    Log.e(TAG, "❌ === ERRO CRÍTICO: ENTIDADE NÃO CONFIGURADA ===")
                    Log.e(TAG, "  🔴 Entidade ID das configurações: '$entidadeId'")
                    Log.e(TAG, "  💡 SOLUÇÃO: Usuário deve ir em configurações e preencher o código da entidade")
                    Log.e(TAG, "  📍 Sem entidade, a API retornará erro 400 'Cliente não configurado'")
                    return@withContext SincronizacaoResult(false, 0, 0, "❌ Entidade não configurada. Vá em configurações e preencha o código da entidade.")
                }
                
                Log.d(TAG, "✅ Entidade configurada:")
                Log.d(TAG, "  🆔 ID: '$entidadeId'")
                
                val database = AppDatabase.getInstance(context)
                val pontoDao = database.pontoSincronizacaoDao()
                val pontosGenericosDao = database.pontosGenericosDao()
                val configuracoesDao = database.configuracoesDao()
                
                // Verificar se há configurações
                Log.d(TAG, "🔍 Verificando configurações...")
                val configuracoes = configuracoesDao.getConfiguracoes()
                if (configuracoes == null) {
                    Log.w(TAG, "⚠️ Configurações não encontradas")
                    return@withContext SincronizacaoResult(false, 0, 0, "Configurações não encontradas")
                }
                
                Log.d(TAG, "✅ Configurações encontradas:")
                Log.d(TAG, "  📍 Localização ID: '${configuracoes.localizacaoId}'")
                Log.d(TAG, "  🔑 Código: '${configuracoes.codigoSincronizacao}'")
                
                // Verificar se as configurações estão válidas
                if (configuracoes.localizacaoId.isEmpty() || configuracoes.codigoSincronizacao.isEmpty()) {
                    Log.e(TAG, "❌ Configurações inválidas!")
                    return@withContext SincronizacaoResult(false, 0, 0, "Configurações de localização/código não preenchidas")
                }
                
                // ✅ CORRIGIDO: Buscar pontos de AMBAS as tabelas
                Log.d(TAG, "🔍 Buscando pontos de ambas as tabelas...")
                
                // 🔄 NOVO: Verificar e marcar batidas duplicadas primeiro
                Log.d(TAG, "🔍 Verificando batidas duplicadas que já foram subidas...")
                verificarEMarcarBatidasDuplicadas(pontosGenericosDao)
                
                // 1. Pontos da tabela de sincronização específica
                val pontosSincronizacao = pontoDao.getPontosNaoSincronizados()
                Log.d(TAG, "📊 Pontos da tabela sincronização: ${pontosSincronizacao.size}")
                
                // 2. Pontos da tabela genérica (reconhecimento facial) que não foram sincronizados
                val pontosGenericos = pontosGenericosDao.getPendingSync()
                Log.d(TAG, "📊 Pontos da tabela genérica (após verificação de duplicatas): ${pontosGenericos.size}")
                
                // 3. Converter pontos genéricos para formato de sincronização
                val pontosGenericosConvertidos = pontosGenericos.map { pontoGenerico ->
                    PontoSincronizacaoEntity(
                        funcionarioId = pontoGenerico.funcionarioId,
                        funcionarioNome = pontoGenerico.funcionarioNome,
                        funcionarioMatricula = pontoGenerico.funcionarioMatricula,
                        funcionarioCpf = pontoGenerico.funcionarioCpf,
                        funcionarioCargo = pontoGenerico.funcionarioCargo,
                        funcionarioSecretaria = pontoGenerico.funcionarioSecretaria,
                        funcionarioLotacao = pontoGenerico.funcionarioLotacao,
                        dataHora = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(pontoGenerico.dataHora)),
                        tipo = pontoGenerico.tipoPonto.lowercase(), // "PONTO" -> "ponto"
                        sincronizado = false,
                        localizacaoId = configuracoes.localizacaoId,
                        codigoSincronizacao = configuracoes.codigoSincronizacao,
                        fotoBase64 = pontoGenerico.fotoBase64 // 🆕 Incluir foto
                    )
                }
                
                // 4. Unir todos os pontos para sincronizar
                val todosPontos = pontosSincronizacao + pontosGenericosConvertidos
                
                if (todosPontos.isEmpty()) {
                    Log.d(TAG, "ℹ️ Nenhum ponto pendente para sincronização")
                    return@withContext SincronizacaoResult(true, 0, 0, "Nenhum ponto pendente para sincronização")
                }
                
                Log.d(TAG, "📊 Total de pontos para sincronizar: ${todosPontos.size}")
                Log.d(TAG, "  🔹 Da tabela sincronização: ${pontosSincronizacao.size}")
                Log.d(TAG, "  🔹 Da tabela genérica: ${pontosGenericosConvertidos.size}")
                
                todosPontos.forEachIndexed { index, ponto ->
                    Log.d(TAG, "  🔹 [$index] ${ponto.funcionarioNome} - ${ponto.tipo} - ${ponto.dataHora}")
                }
                
                Log.d(TAG, "🔄 Iniciando sincronização de ${todosPontos.size} pontos...")
                
                // ✅ IMPLEMENTAÇÃO REAL DA API
                val sucesso = enviarPontosParaAPI(context, todosPontos, configuracoes)
                
                val duracaoSegundos = (System.currentTimeMillis() - tempoInicio) / 1000
                Log.d(TAG, "⏱️ Duração da sincronização: ${duracaoSegundos}s")
                
                if (sucesso) {
                    // Marcar pontos como sincronizados em AMBAS as tabelas
                    Log.d(TAG, "✅ Marcando pontos como sincronizados...")
                    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    val dataSincronizacao = sdf.format(Date())
                    
                    // Marcar pontos da tabela de sincronização
                    if (pontosSincronizacao.isNotEmpty()) {
                        val idsPontosSincronizacao = pontosSincronizacao.map { it.id }
                        Log.d(TAG, "🔄 Marcando ${idsPontosSincronizacao.size} pontos da tabela sincronização")
                        pontoDao.marcarMultiplosComoSincronizados(idsPontosSincronizacao, dataSincronizacao)
                    }
                    
                    // Marcar pontos da tabela genérica
                    if (pontosGenericos.isNotEmpty()) {
                        Log.d(TAG, "🔄 Marcando ${pontosGenericos.size} pontos da tabela genérica")
                        for (pontoGenerico in pontosGenericos) {
                            pontosGenericosDao.markAsSynced(pontoGenerico.id)
                        }
                    }
                    
                    Log.d(TAG, "✅ ${todosPontos.size} pontos sincronizados com sucesso")
                    Log.d(TAG, "🚀 === SINCRONIZAÇÃO CONCLUÍDA COM SUCESSO ===")
                    SincronizacaoResult(true, todosPontos.size, duracaoSegundos, "${todosPontos.size} pontos sincronizados com sucesso")
                } else {
                    Log.e(TAG, "❌ Falha na sincronização dos pontos")
                    Log.d(TAG, "🚀 === SINCRONIZAÇÃO FALHOU ===")
                    SincronizacaoResult(false, todosPontos.size, duracaoSegundos, "Falha na sincronização com a API")
                }
                
            } catch (e: Exception) {
                val duracaoSegundos = (System.currentTimeMillis() - tempoInicio) / 1000
                Log.e(TAG, "❌ Erro na sincronização: ${e.message}")
                e.printStackTrace()
                Log.d(TAG, "🚀 === SINCRONIZAÇÃO COM ERRO ===")
                SincronizacaoResult(false, 0, duracaoSegundos, "Erro na sincronização: ${e.message}")
            }
        }
    }

    /**
     * ✅ NOVA IMPLEMENTAÇÃO: Envio real para a API
     * Baseado no formato JSON fornecido pelo usuário
     */
    private suspend fun enviarPontosParaAPI(
        context: Context,
        pontos: List<PontoSincronizacaoEntity>,
        configuracoes: com.example.iface_offilne.data.ConfiguracoesEntity
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "📤 === ENVIANDO PARA API REAL ===")
                
                // Preparar dados no formato que a API espera
                val pontosFormatados = pontos.map { ponto ->
                    mapOf(
                        "id" to ponto.id.toString(),
                        "funcionario_id" to ponto.funcionarioId,
                        "funcionario_nome" to ponto.funcionarioNome,
                        "funcionario_matricula" to ponto.funcionarioMatricula,
                        "funcionario_cpf" to ponto.funcionarioCpf,
                        "funcionario_cargo" to ponto.funcionarioCargo,
                        "funcionario_secretaria" to ponto.funcionarioSecretaria,
                        "funcionario_lotacao" to ponto.funcionarioLotacao,
                        "data_hora" to ponto.dataHora
                    )
                }
                
                // Montar payload no formato esperado pela API
                val payload = mapOf(
                    "localizacao_id" to configuracoes.localizacaoId,
                    "codigo_sincronizacao" to configuracoes.codigoSincronizacao,
                    "data_sincronizacao" to SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()),
                    "pontos" to pontosFormatados
                )
                
                Log.d(TAG, "📦 === PAYLOAD COMPLETO ===")
                Log.d(TAG, "  📍 Localização ID: '${payload["localizacao_id"]}'")
                Log.d(TAG, "  🔑 Código: '${payload["codigo_sincronizacao"]}'")
                Log.d(TAG, "  📊 Quantidade de pontos: ${pontosFormatados.size}")
                Log.d(TAG, "  📅 Data sincronização: '${payload["data_sincronizacao"]}'")
                
                // ✅ NOVO: Mostrar JSON completo formatado
                Log.d(TAG, "📋 === JSON COMPLETO DA REQUEST ===")
                val jsonString = buildString {
                    appendLine("{")
                    appendLine("  \"localizacao_id\": \"${payload["localizacao_id"]}\",")
                    appendLine("  \"codigo_sincronizacao\": \"${payload["codigo_sincronizacao"]}\",")
                    appendLine("  \"data_sincronizacao\": \"${payload["data_sincronizacao"]}\",")
                    appendLine("  \"pontos\": [")
                    
                    pontosFormatados.forEachIndexed { index, ponto ->
                        appendLine("    {")
                        appendLine("      \"id\": \"${ponto["id"]}\",")
                        appendLine("      \"funcionario_id\": \"${ponto["funcionario_id"]}\",")
                        appendLine("      \"funcionario_nome\": \"${ponto["funcionario_nome"]}\",")
                        appendLine("      \"funcionario_matricula\": \"${ponto["funcionario_matricula"]}\",")
                        appendLine("      \"funcionario_cpf\": \"${ponto["funcionario_cpf"]}\",")
                        appendLine("      \"funcionario_cargo\": \"${ponto["funcionario_cargo"]}\",")
                        appendLine("      \"funcionario_secretaria\": \"${ponto["funcionario_secretaria"]}\",")
                        appendLine("      \"funcionario_lotacao\": \"${ponto["funcionario_lotacao"]}\",")
                        appendLine("      \"data_hora\": \"${ponto["data_hora"]}\"")
                        if (index < pontosFormatados.size - 1) {
                            appendLine("    },")
                        } else {
                            appendLine("    }")
                        }
                    }
                    
                    appendLine("  ]")
                    appendLine("}")
                }
                
                Log.d(TAG, jsonString)
                Log.d(TAG, "📋 === FIM DO JSON ===")
                
                // ✅ NOVO: Mostrar detalhes de cada ponto individualmente
                Log.d(TAG, "🔍 === DETALHES DE CADA PONTO ===")
                pontosFormatados.forEachIndexed { index, ponto ->
                    Log.d(TAG, "Ponto #${index + 1}:")
                    ponto.forEach { (chave, valor) ->
                        Log.d(TAG, "  $chave: '$valor'")
                    }
                    Log.d(TAG, "  ---")
                }
                
                // Fazer chamada para a API
                Log.d(TAG, "🌐 === FAZENDO CHAMADA HTTP ===")
                
                try {
                    // ✅ CORRIGIDO: Obter entidade das configurações
                    val entidade = ConfiguracoesManager.getEntidadeId(context)
                    
                    if (entidade.isEmpty()) {
                        Log.e(TAG, "❌ === ERRO: ENTIDADE NÃO CONFIGURADA ===")
                        Log.e(TAG, "  🔴 Entidade ID das configurações é vazio")
                        Log.e(TAG, "  💡 Usuário precisa preencher o código da entidade primeiro")
                        Log.e(TAG, "  📍 Vá em configurações e preencha o código da entidade")
                        return@withContext false
                    }
                    
                    Log.d(TAG, "🔗 URL da API: /$entidade/services/util/sincronizar-ponto-table")
                    Log.d(TAG, "🔗 Entidade das configurações: '$entidade'")
                    
                                    // ✅ CORREÇÃO: Converter pontos para o formato da API com geolocalização
                val pontosParaAPI = pontos.map { ponto ->
                    com.example.iface_offilne.data.api.PontoSyncRequest(
                        funcionarioId = ponto.funcionarioId,
                        funcionarioNome = ponto.funcionarioNome,
                        dataHora = ponto.dataHora,
                        tipoPonto = ponto.tipo.uppercase(), // "PONTO"
                        latitude = ponto.latitude, // ✅ NOVA: Incluir latitude
                        longitude = ponto.longitude, // ✅ NOVA: Incluir longitude
                        fotoBase64 = ponto.fotoBase64 // 🆕 Incluir foto
                    )
                }
                    
                    // ✅ NOVO: Criar request completo com configurações no nível raiz
                    val requestCompleto = com.example.iface_offilne.data.api.PontoSyncCompleteRequest(
                        localizacao_id = configuracoes.localizacaoId,
                        cod_sincroniza = configuracoes.codigoSincronizacao,
                        pontos = pontosParaAPI
                    )
                    
                    // ✅ NOVO: Mostrar formato completo para API
                    Log.d(TAG, "📋 === FORMATO COMPLETO PARA API ===")
                    Log.d(TAG, "  localizacao_id: '${requestCompleto.localizacao_id}'")
                    Log.d(TAG, "  cod_sincroniza: '${requestCompleto.cod_sincroniza}'")
                    Log.d(TAG, "  pontos: ${requestCompleto.pontos.size} pontos")
                    
                    requestCompleto.pontos.forEachIndexed { index, pontoAPI ->
                        Log.d(TAG, "Ponto API #${index + 1}:")
                        Log.d(TAG, "  funcionarioId: '${pontoAPI.funcionarioId}'")
                        Log.d(TAG, "  funcionarioNome: '${pontoAPI.funcionarioNome}'")
                        Log.d(TAG, "  dataHora: '${pontoAPI.dataHora}'")
                        Log.d(TAG, "  tipoPonto: '${pontoAPI.tipoPonto}'")
                        Log.d(TAG, "  latitude: ${pontoAPI.latitude}")
                        Log.d(TAG, "  longitude: ${pontoAPI.longitude}")
                        Log.d(TAG, "  observacao: '${pontoAPI.observacao}'")
                        Log.d(TAG, "  fotoBase64: ${if (pontoAPI.fotoBase64?.isNotEmpty() == true) "✅ Presente (${pontoAPI.fotoBase64.length} chars)" else "❌ Ausente"}")
                        Log.d(TAG, "  ---")
                    }
                    
                    val apiService = RetrofitClient.instance
                    Log.d(TAG, "🔄 Executando chamada HTTP com formato completo...")
                    
                    // ✅ NOVO: Usar o novo endpoint com formato completo
                    val response = apiService.sincronizarPontosCompleto(entidade, requestCompleto)
                    
                    Log.d(TAG, "📡 === RESPOSTA DA API ===")
                    Log.d(TAG, "  📈 Status Code: ${response.code()}")
                    Log.d(TAG, "  ✅ Sucesso: ${response.isSuccessful}")
                    
                    if (response.isSuccessful) {
                        val responseBody = response.body()
                        Log.d(TAG, "  📝 Response Body: $responseBody")
                        
                        if (responseBody != null) {
                            Log.d(TAG, "  🎯 Success: ${responseBody.success}")
                            Log.d(TAG, "  💬 Message: '${responseBody.message}'")
                            Log.d(TAG, "  📊 Pontos Sincronizados: ${responseBody.pontosSincronizados}")
                        }
                        
                        Log.d(TAG, "✅ API respondeu com sucesso!")
                        true
                    } else {
                        val errorBody = response.errorBody()?.string()
                        Log.e(TAG, "❌ API retornou erro: ${response.code()}")
                        Log.e(TAG, "  📝 Error Body: $errorBody")
                        Log.e(TAG, "  📝 Error Message: ${response.message()}")
                        
                        // 🔄 NOVO: Detectar erros de duplicata e marcar como sincronizadas
                        if (response.code() == 400 && errorBody != null) {
                            return@withContext tratarErroDuplicata(context, errorBody, pontos, pontosParaAPI)
                        }
                        
                        // ✅ ANÁLISE específica do erro 400 (outros casos)
                        if (response.code() == 400 && errorBody?.contains("Cliente não configurado") == true) {
                            Log.e(TAG, "  🔴 ERRO ESPECÍFICO: Cliente não configurado")
                            Log.e(TAG, "  💡 SOLUÇÃO: Verificar se a entidade '$entidade' está correta")
                            Log.e(TAG, "  💡 DICA: Vá em configurações e selecione novamente a entidade")
                        }
                        
                        false
                    }
                    
                } catch (networkException: Exception) {
                    Log.e(TAG, "❌ === ERRO DE REDE ===")
                    Log.e(TAG, "  🔴 Tipo: ${networkException.javaClass.simpleName}")
                    Log.e(TAG, "  💬 Mensagem: ${networkException.message}")
                    Log.e(TAG, "  📍 Stack Trace:")
                    networkException.printStackTrace()
                    
                    // ✅ FALLBACK: Tentar sincronização alternativa se a API principal falhar
                    Log.d(TAG, "🔄 === DADOS QUE SERIAM ENVIADOS (FALLBACK) ===")
                    Log.d(TAG, "📋 JSON que seria enviado:")
                    Log.d(TAG, jsonString)
                    
                    // ✅ NOVO: Usar mensagem padronizada
                    Log.e(TAG, "  💬 Mensagem padronizada: ${ErrorMessageHelper.getErrorMessage(networkException)}")
                    
                    false
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ === ERRO GERAL ===")
                Log.e(TAG, "  🔴 Tipo: ${e.javaClass.simpleName}")
                Log.e(TAG, "  💬 Mensagem: ${e.message}")
                Log.e(TAG, "  📍 Stack Trace:")
                e.printStackTrace()
                
                // ✅ NOVO: Usar mensagem padronizada
                Log.e(TAG, "  💬 Mensagem padronizada: ${ErrorMessageHelper.getErrorMessage(e)}")
                
                false
            }
        }
    }

    // Preparar dados para sincronização (método legado - mantido para compatibilidade)
    private fun prepararDadosSincronizacao(
        pontos: List<PontoSincronizacaoEntity>,
        configuracoes: com.example.iface_offilne.data.ConfiguracoesEntity
    ): Map<String, Any> {
        
        Log.d(TAG, "🔧 === PREPARANDO DADOS PARA SINCRONIZAÇÃO ===")
        Log.d(TAG, "📊 Quantidade de pontos: ${pontos.size}")
        Log.d(TAG, "⚙️ Configurações:")
        Log.d(TAG, "  📍 Localização ID: '${configuracoes.localizacaoId}'")
        Log.d(TAG, "  🔑 Código Sincronização: '${configuracoes.codigoSincronizacao}'")
        
        val pontosData = pontos.mapIndexed { index, ponto ->
            Log.d(TAG, "🔹 Ponto [$index]:")
            Log.d(TAG, "  👤 Funcionário: ${ponto.funcionarioNome} (ID: ${ponto.funcionarioId})")
            Log.d(TAG, "  📋 Matrícula: ${ponto.funcionarioMatricula}")
            Log.d(TAG, "  📄 CPF: ${ponto.funcionarioCpf}")
            Log.d(TAG, "  💼 Cargo: ${ponto.funcionarioCargo}")
            Log.d(TAG, "  🏢 Secretaria: ${ponto.funcionarioSecretaria}")
            Log.d(TAG, "  📍 Lotação: ${ponto.funcionarioLotacao}")
            Log.d(TAG, "  📅 Data/Hora: ${ponto.dataHora}")
            Log.d(TAG, "  🔄 Tipo: ${ponto.tipo}")
            Log.d(TAG, "  🔗 Sincronizado: ${ponto.sincronizado}")
            
            val pontoMap = mapOf(
                "id" to ponto.id,
                "funcionario_id" to ponto.funcionarioId,
                "funcionario_nome" to ponto.funcionarioNome,
                "funcionario_matricula" to ponto.funcionarioMatricula,
                "funcionario_cpf" to ponto.funcionarioCpf,
                "funcionario_cargo" to ponto.funcionarioCargo,
                "funcionario_secretaria" to ponto.funcionarioSecretaria,
                "funcionario_lotacao" to ponto.funcionarioLotacao,
                "data_hora" to ponto.dataHora,
                "tipo" to ponto.tipo,
                "localizacao_id" to ponto.localizacaoId,
                "codigo_sincronizacao" to ponto.codigoSincronizacao
            )
            
            Log.d(TAG, "  ✅ Ponto mapeado: $pontoMap")
            pontoMap
        }
        
        val dadosFinais = mapOf(
            "localizacao_id" to configuracoes.localizacaoId,
            "codigo_sincronizacao" to configuracoes.codigoSincronizacao,
            "pontos" to pontosData,
            "data_sincronizacao" to SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        )
        
        Log.d(TAG, "🔧 === DADOS PREPARADOS COM SUCESSO ===")
        return dadosFinais
    }

    // Enviar dados para API (método legado - removido pois foi substituído)
    // ... código removido ...

    // Obter quantidade de pontos pendentes
    suspend fun getQuantidadePontosPendentes(context: Context): Int {
        return withContext(Dispatchers.IO) {
            try {
                val database = AppDatabase.getInstance(context)
                val pontoDao = database.pontoSincronizacaoDao()
                val pontosGenericosDao = database.pontosGenericosDao()
                
                // ✅ CORRIGIDO: Contar pontos de AMBAS as tabelas
                val pontosSincronizacao = pontoDao.getQuantidadePontosNaoSincronizados()
                val pontosGenericos = pontosGenericosDao.getPendingSync().size
                
                val total = pontosSincronizacao + pontosGenericos
                
                Log.d(TAG, "📊 Pontos pendentes:")
                Log.d(TAG, "  🔹 Tabela sincronização: $pontosSincronizacao")
                Log.d(TAG, "  🔹 Tabela genérica: $pontosGenericos")
                Log.d(TAG, "  🔹 Total: $total")
                
                total
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao obter quantidade de pontos pendentes: ${e.message}")
                0
            }
        }
    }

    // Limpar pontos sincronizados antigos (manutenção)
    suspend fun limparPontosAntigos(context: Context, diasParaManter: Int = 30) {
        withContext(Dispatchers.IO) {
            try {
                val database = AppDatabase.getInstance(context)
                val pontoDao = database.pontoSincronizacaoDao()
                
                // Calcular data limite
                val calendar = Calendar.getInstance()
                calendar.add(Calendar.DAY_OF_YEAR, -diasParaManter)
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                val dataLimite = sdf.format(calendar.time)
                
                // Limpar pontos antigos
                val pontosRemovidos = pontoDao.limparPontosSincronizadosAntigos(dataLimite)
                
                Log.d(TAG, "🗑️ Removidos $pontosRemovidos pontos antigos (anteriores a $dataLimite)")
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao limpar pontos antigos: ${e.message}")
            }
        }
    }

    // Sincronizar pontos pendentes com registro no histórico
    suspend fun sincronizarPontosPendentesComHistorico(context: Context, tipoSincronizacao: String) {
        withContext(Dispatchers.IO) {
            try {
                // Registrar início da sincronização
                registrarHistorico(context, "EM_ANDAMENTO", "$tipoSincronizacao iniciada", 0, 0)
                
                // Executar sincronização
                val resultado = sincronizarPontosPendentes(context)
                
                // Registrar resultado no histórico
                if (resultado.sucesso) {
                    registrarHistorico(context, "SUCESSO", resultado.mensagem, resultado.quantidadePontos, resultado.duracaoSegundos)
                } else {
                    registrarHistorico(context, "ERRO", resultado.mensagem, resultado.quantidadePontos, resultado.duracaoSegundos)
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro na sincronização: ${e.message}")
                registrarHistorico(context, "ERRO", "Erro na $tipoSincronizacao: ${e.message}", 0, 0)
            }
        }
    }

    // Registrar no histórico
    private fun registrarHistorico(context: Context, status: String, mensagem: String, quantidadePontos: Int, duracaoSegundos: Long) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val database = AppDatabase.getInstance(context)
                val historicoDao = database.historicoSincronizacaoDao()
                
                val historico = HistoricoSincronizacaoEntity(
                    dataHora = Date(),
                    status = status,
                    mensagem = mensagem,
                    quantidadePontos = quantidadePontos,
                    duracaoSegundos = duracaoSegundos
                )
                
                historicoDao.insertSincronizacao(historico)
                Log.d(TAG, "📝 Histórico registrado: $status - $mensagem")
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao registrar histórico: ${e.message}")
            }
        }
    }

    // Método para criar pontos de teste (apenas para debug)
    suspend fun criarPontoTeste(context: Context) {
        withContext(Dispatchers.IO) {
            try {
                val database = AppDatabase.getInstance(context)
                val pontoDao = database.pontoSincronizacaoDao()
                
                // Formatar data/hora atual
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                val dataHora = sdf.format(Date())
                
                // Criar ponto de teste
                val pontoTeste = PontoSincronizacaoEntity(
                    funcionarioId = "00905076303", // Usando o CPF do exemplo
                    funcionarioNome = "ADAMS ANTONIO GIRAO MENESES", // Nome do exemplo
                    funcionarioMatricula = "100001", // Matrícula do exemplo
                    funcionarioCpf = "00905076303",
                    funcionarioCargo = "Analista",
                    funcionarioSecretaria = "SEAD",
                    funcionarioLotacao = "Diretoria",
                    dataHora = dataHora,
                    tipo = "ponto",
                    sincronizado = false,
                    localizacaoId = ConfiguracoesManager.getLocalizacaoId(context),
                    codigoSincronizacao = ConfiguracoesManager.getCodigoSincronizacao(context)
                )
                
                pontoDao.insertPonto(pontoTeste)
                Log.d(TAG, "✅ Ponto de teste criado: $dataHora")
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao criar ponto de teste: ${e.message}")
            }
        }
    }
    
    /**
     * Verifica e marca como sincronizadas as batidas que podem ter sido subidas anteriormente
     * mas não foram marcadas devido a problemas de sincronização
     */
    private suspend fun verificarEMarcarBatidasDuplicadas(pontosGenericosDao: PontosGenericosDao) {
        try {
            Log.d(TAG, "🔍 Iniciando verificação de batidas duplicadas...")
            
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
                    Log.d(TAG, "  📍 Data pendente: ${Date(pontoPendente.dataHora)}")
                    Log.d(TAG, "  📍 Data sincronizada: ${Date(duplicata.dataHora)}")
                    
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
     * @param context Contexto da aplicação
     * @param errorBody Corpo da resposta de erro da API
     * @param pontosOriginais Lista de pontos que foram enviados
     * @param pontosParaAPI Lista de pontos no formato da API
     * @return true se conseguiu tratar o erro, false caso contrário
     */
    private suspend fun tratarErroDuplicata(
        context: Context,
        errorBody: String,
        pontosOriginais: List<PontoSincronizacaoEntity>,
        pontosParaAPI: List<com.example.iface_offilne.data.api.PontoSyncRequest>
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🔍 === ANALISANDO ERRO DE DUPLICATA ===")
            
            // Verificar se é erro de chave duplicada
            val isDuplicateError = errorBody.contains("duplicate key value violates unique constraint") ||
                                 errorBody.contains("Unique violation") ||
                                 errorBody.contains("already exists") ||
                                 errorBody.contains("SQLSTATE[23505]")
            
            if (isDuplicateError) {
                Log.d(TAG, "🎯 DETECTADO: Erro de batida duplicada no servidor!")
                Log.d(TAG, "🔄 Marcando batidas como sincronizadas automaticamente...")
                
                val pontosGenericosDao = AppDatabase.getInstance(context).pontosGenericosDao()
                val pontoDao = AppDatabase.getInstance(context).pontoSincronizacaoDao()
                
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
                    val formato = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    try {
                        val dataHoraTimestamp = formato.parse(dataHoraDuplicada)?.time ?: 0L
                        
                        // Buscar na tabela genérica
                        val pontosGenericos = pontosGenericosDao.getPendingSync()
                        pontosGenericos.forEach { ponto ->
                            if (ponto.funcionarioId == funcionarioId && 
                                Math.abs(ponto.dataHora - dataHoraTimestamp) <= 60000) { // 1 minuto de tolerância
                                pontosGenericosDao.markAsSynced(ponto.id)
                                batidasMarcadas++
                                Log.d(TAG, "✅ Batida genérica marcada: ${ponto.funcionarioNome} - ${Date(ponto.dataHora)}")
                            }
                        }
                        
                        // Buscar na tabela de sincronização
                        val pontosSincronizacao = pontoDao.getPontosNaoSincronizados()
                        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                        pontosSincronizacao.forEach { ponto ->
                            try {
                                val pontoTimestamp = sdf.parse(ponto.dataHora)?.time ?: 0L
                                if (ponto.funcionarioId == funcionarioId && 
                                    Math.abs(pontoTimestamp - dataHoraTimestamp) <= 60000) {
                                    pontoDao.marcarComoSincronizado(ponto.id, sdf.format(Date()))
                                    batidasMarcadas++
                                    Log.d(TAG, "✅ Batida sincronização marcada: ${ponto.funcionarioNome} - ${ponto.dataHora}")
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "❌ Erro ao processar ponto sincronização: ${e.message}")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Erro ao converter data: ${e.message}")
                    }
                }
                
                // Se não conseguiu identificar especificamente, marcar todas as batidas enviadas
                if (batidasMarcadas == 0) {
                    Log.d(TAG, "⚠️ Não foi possível identificar batida específica, marcando todas as enviadas...")
                    
                    // Marcar pontos genéricos enviados
                    pontosOriginais.forEach { pontoOriginal ->
                        try {
                            // Converter data do formato da sincronização para timestamp
                            val formato = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                            val timestamp = formato.parse(pontoOriginal.dataHora)?.time ?: 0L
                            
                            val pontosGenericos = pontosGenericosDao.getPendingSync()
                            pontosGenericos.forEach { pontoGenerico ->
                                if (pontoGenerico.funcionarioId == pontoOriginal.funcionarioId &&
                                    Math.abs(pontoGenerico.dataHora - timestamp) <= 300000) { // 5 minutos
                                    pontosGenericosDao.markAsSynced(pontoGenerico.id)
                                    batidasMarcadas++
                                    Log.d(TAG, "✅ Batida marcada (fallback): ${pontoGenerico.funcionarioNome}")
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ Erro ao processar ponto: ${e.message}")
                        }
                    }
                    
                    // Marcar pontos de sincronização enviados
                    val pontosSincronizacao = pontoDao.getPontosNaoSincronizados()
                    pontosOriginais.forEach { pontoOriginal ->
                        pontosSincronizacao.forEach { pontoSync ->
                            if (pontoSync.funcionarioId == pontoOriginal.funcionarioId &&
                                pontoSync.dataHora == pontoOriginal.dataHora) {
                                pontoDao.marcarComoSincronizado(pontoSync.id, SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))
                                batidasMarcadas++
                                Log.d(TAG, "✅ Ponto sincronização marcado (fallback): ${pontoSync.funcionarioNome}")
                            }
                        }
                    }
                }
                
                if (batidasMarcadas > 0) {
                    Log.d(TAG, "🎉 === DUPLICATAS RESOLVIDAS AUTOMATICAMENTE ===")
                    Log.d(TAG, "✅ $batidasMarcadas batidas foram marcadas como sincronizadas")
                    Log.d(TAG, "💡 Problema: Essas batidas já existiam no servidor")
                    Log.d(TAG, "💡 Solução: Marcadas localmente para evitar reenvio")
                    return@withContext true
                } else {
                    Log.e(TAG, "❌ Não foi possível identificar batidas duplicadas")
                    return@withContext false
                }
            } else {
                Log.e(TAG, "❌ Erro não é de duplicata, mantendo como falha")
                return@withContext false
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao tratar duplicata: ${e.message}")
            e.printStackTrace()
            return@withContext false
        }
    }
} 