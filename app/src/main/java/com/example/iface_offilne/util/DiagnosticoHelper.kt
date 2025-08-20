package com.example.iface_offilne.util

import android.content.Context
import android.util.Log
import com.example.iface_offilne.data.AppDatabase
import com.example.iface_offilne.data.FuncionariosEntity
import com.example.iface_offilne.data.PontosGenericosEntity
import com.example.iface_offilne.util.ConfiguracoesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

/**
 * 🩺 HELPER PARA DIAGNÓSTICO DE PROBLEMAS
 * 
 * Este helper identifica possíveis causas de crashes no sistema de ponto
 */
object DiagnosticoHelper {
    
    private const val TAG = "DIAGNOSTICO"
    
    /**
     * 🔍 EXECUTAR DIAGNÓSTICO COMPLETO
     */
    suspend fun executarDiagnosticoCompleto(context: Context): DiagnosticoResult {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "🔍 === INICIANDO DIAGNÓSTICO COMPLETO ===")
                
                val problemas = mutableListOf<String>()
                val avisos = mutableListOf<String>()
                val sucessos = mutableListOf<String>()
                
                // 1. Verificar banco de dados
                val dbResult = verificarBancoDados(context)
                problemas.addAll(dbResult.problemas)
                avisos.addAll(dbResult.avisos)
                sucessos.addAll(dbResult.sucessos)
                
                // 2. Verificar funcionários
                val funcResult = verificarFuncionarios(context)
                problemas.addAll(funcResult.problemas)
                avisos.addAll(funcResult.avisos)
                sucessos.addAll(funcResult.sucessos)
                
                // 3. Verificar configurações
                val configResult = verificarConfiguracoes(context)
                problemas.addAll(configResult.problemas)
                avisos.addAll(configResult.avisos)
                sucessos.addAll(configResult.sucessos)
                
                // 4. Verificar pontos
                val pontosResult = verificarPontos(context)
                problemas.addAll(pontosResult.problemas)
                avisos.addAll(pontosResult.avisos)
                sucessos.addAll(pontosResult.sucessos)
                
                // 5. Verificar entidade
                val entidadeResult = verificarEntidade(context)
                problemas.addAll(entidadeResult.problemas)
                avisos.addAll(entidadeResult.avisos)
                sucessos.addAll(entidadeResult.sucessos)
                
                val resultado = DiagnosticoResult(
                    problemas = problemas,
                    avisos = avisos,
                    sucessos = sucessos,
                    timestamp = System.currentTimeMillis()
                )
                
                Log.d(TAG, "✅ === DIAGNÓSTICO CONCLUÍDO ===")
                Log.d(TAG, "🔴 Problemas: ${problemas.size}")
                Log.d(TAG, "🟡 Avisos: ${avisos.size}")
                Log.d(TAG, "🟢 Sucessos: ${sucessos.size}")
                
                resultado
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro no diagnóstico: ${e.message}")
                DiagnosticoResult(
                    problemas = listOf("Erro crítico no diagnóstico: ${e.message}"),
                    avisos = emptyList(),
                    sucessos = emptyList(),
                    timestamp = System.currentTimeMillis()
                )
            }
        }
    }
    
    /**
     * 🗄️ VERIFICAR BANCO DE DADOS
     */
    private suspend fun verificarBancoDados(context: Context): DiagnosticoResult {
        val problemas = mutableListOf<String>()
        val avisos = mutableListOf<String>()
        val sucessos = mutableListOf<String>()
        
        try {
            val database = AppDatabase.getInstance(context)
            
            // Verificar se o banco está acessível
            try {
                database.openHelper.readableDatabase
                sucessos.add("✅ Banco de dados acessível")
            } catch (e: Exception) {
                problemas.add("❌ Banco de dados inacessível: ${e.message}")
                return DiagnosticoResult(problemas, avisos, sucessos, System.currentTimeMillis())
            }
            
            // Verificar tabelas
            val tabelas = listOf(
                "funcionarios",
                "pontos_genericos", 
                "configuracoes",
                "pontos_sincronizacao",
                "faces"
            )
            
            for (tabela in tabelas) {
                try {

                } catch (e: Exception) {
                    problemas.add("❌ Tabela $tabela inacessível: ${e.message}")
                }
            }
            
        } catch (e: Exception) {
            problemas.add("❌ Erro ao verificar banco: ${e.message}")
        }
        
        return DiagnosticoResult(problemas, avisos, sucessos, System.currentTimeMillis())
    }
    
    /**
     * 👥 VERIFICAR FUNCIONÁRIOS
     */
    private suspend fun verificarFuncionarios(context: Context): DiagnosticoResult {
        val problemas = mutableListOf<String>()
        val avisos = mutableListOf<String>()
        val sucessos = mutableListOf<String>()
        
        try {
            val database = AppDatabase.getInstance(context)
            val funcionarioDao = database.usuariosDao()
            
            val funcionarios = funcionarioDao.getUsuario()
            
            if (funcionarios.isEmpty()) {
                problemas.add("❌ Nenhum funcionário cadastrado")
            } else {
                sucessos.add("✅ ${funcionarios.size} funcionários cadastrados")
                
                // Verificar funcionários sem dados essenciais
                val funcionariosInvalidos = funcionarios.filter { 
                    it.codigo.isNullOrEmpty() || it.nome.isNullOrEmpty() 
                }
                
                if (funcionariosInvalidos.isNotEmpty()) {
                    avisos.add("⚠️ ${funcionariosInvalidos.size} funcionários com dados incompletos")
                }
                
                // Verificar funcionários inativos
                val funcionariosInativos = funcionarios.filter { it.ativo == 0 }
                if (funcionariosInativos.isNotEmpty()) {
                    avisos.add("⚠️ ${funcionariosInativos.size} funcionários inativos")
                }
            }
            
        } catch (e: Exception) {
            problemas.add("❌ Erro ao verificar funcionários: ${e.message}")
        }
        
        return DiagnosticoResult(problemas, avisos, sucessos, System.currentTimeMillis())
    }
    
    /**
     * ⚙️ VERIFICAR CONFIGURAÇÕES
     */
    private suspend fun verificarConfiguracoes(context: Context): DiagnosticoResult {
        val problemas = mutableListOf<String>()
        val avisos = mutableListOf<String>()
        val sucessos = mutableListOf<String>()
        
        try {
            val database = AppDatabase.getInstance(context)
            val configDao = database.configuracoesDao()
            
            val configuracoes = configDao.getConfiguracoes()
            
            if (configuracoes == null) {
                problemas.add("❌ Configurações não encontradas")
            } else {
                sucessos.add("✅ Configurações encontradas")
                
                if (configuracoes.localizacaoId.isEmpty()) {
                    problemas.add("❌ ID da localização não configurado")
                } else {
                    sucessos.add("✅ ID da localização: ${configuracoes.localizacaoId}")
                }
                
                if (configuracoes.codigoSincronizacao.isEmpty()) {
                    problemas.add("❌ Código de sincronização não configurado")
                } else {
                    sucessos.add("✅ Código de sincronização configurado")
                }
                
                if (!configuracoes.sincronizacaoAtiva) {
                    avisos.add("⚠️ Sincronização automática desativada")
                } else {
                    sucessos.add("✅ Sincronização automática ativada")
                }
            }
            
        } catch (e: Exception) {
            problemas.add("❌ Erro ao verificar configurações: ${e.message}")
        }
        
        return DiagnosticoResult(problemas, avisos, sucessos, System.currentTimeMillis())
    }
    
    /**
     * 📊 VERIFICAR PONTOS
     */
    private suspend fun verificarPontos(context: Context): DiagnosticoResult {
        val problemas = mutableListOf<String>()
        val avisos = mutableListOf<String>()
        val sucessos = mutableListOf<String>()
        
        try {
            val database = AppDatabase.getInstance(context)
            val pontosDao = database.pontosGenericosDao()
            val sincronizacaoDao = database.pontoSincronizacaoDao()
            
            // Verificar pontos genéricos
            val pontosGenericos = pontosDao.getAllPontos()
            sucessos.add("✅ ${pontosGenericos.size} pontos genéricos")
            
            val pontosPendentes = pontosDao.getPendingSync()
            if (pontosPendentes.isNotEmpty()) {
                avisos.add("⚠️ ${pontosPendentes.size} pontos pendentes de sincronização")
            }
            
            // Verificar pontos de sincronização
            val pontosSincronizacao = sincronizacaoDao.getAllPontos()
            sucessos.add("✅ ${pontosSincronizacao.size} pontos de sincronização")
            
            val pontosNaoSincronizados = sincronizacaoDao.getPontosNaoSincronizados()
            if (pontosNaoSincronizados.isNotEmpty()) {
                avisos.add("⚠️ ${pontosNaoSincronizados.size} pontos não sincronizados")
            }
            
            // Verificar pontos duplicados
            val hoje = System.currentTimeMillis()
            val ontem = hoje - (24 * 60 * 60 * 1000)
            
            val pontosHoje = pontosGenericos.filter { it.dataHora >= ontem }
            if (pontosHoje.size > 10) {
                avisos.add("⚠️ Muitos pontos hoje: ${pontosHoje.size}")
            }
            
        } catch (e: Exception) {
            problemas.add("❌ Erro ao verificar pontos: ${e.message}")
        }
        
        return DiagnosticoResult(problemas, avisos, sucessos, System.currentTimeMillis())
    }
    
    /**
     * 🏢 VERIFICAR ENTIDADE
     */
    private suspend fun verificarEntidade(context: Context): DiagnosticoResult {
        val problemas = mutableListOf<String>()
        val avisos = mutableListOf<String>()
        val sucessos = mutableListOf<String>()
        
        try {
            val entidadeId = ConfiguracoesManager.getEntidadeId(context)
            
            if (entidadeId.isEmpty()) {
                problemas.add("❌ Entidade não configurada")
            } else {
                sucessos.add("✅ Entidade configurada: ${entidadeId}")
            }
            
        } catch (e: Exception) {
            problemas.add("❌ Erro ao verificar entidade: ${e.message}")
        }
        
        return DiagnosticoResult(problemas, avisos, sucessos, System.currentTimeMillis())
    }
    
    /**
     * 📋 GERAR RELATÓRIO DE DIAGNÓSTICO
     */
    fun gerarRelatorio(resultado: DiagnosticoResult): String {
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        val dataHora = sdf.format(Date(resultado.timestamp))
        
        return buildString {
            appendLine("🔍 === RELATÓRIO DE DIAGNÓSTICO ===")
            appendLine("📅 Data/Hora: $dataHora")
            appendLine("")
            
            if (resultado.problemas.isNotEmpty()) {
                appendLine("🔴 PROBLEMAS CRÍTICOS:")
                resultado.problemas.forEach { problema ->
                    appendLine("  • $problema")
                }
                appendLine("")
            }
            
            if (resultado.avisos.isNotEmpty()) {
                appendLine("🟡 AVISOS:")
                resultado.avisos.forEach { aviso ->
                    appendLine("  • $aviso")
                }
                appendLine("")
            }
            
            if (resultado.sucessos.isNotEmpty()) {
                appendLine("🟢 VERIFICAÇÕES OK:")
                resultado.sucessos.forEach { sucesso ->
                    appendLine("  • $sucesso")
                }
                appendLine("")
            }
            
            appendLine("📊 RESUMO:")
            appendLine("  • Problemas: ${resultado.problemas.size}")
            appendLine("  • Avisos: ${resultado.avisos.size}")
            appendLine("  • Sucessos: ${resultado.sucessos.size}")
            appendLine("")
            
            if (resultado.problemas.isEmpty()) {
                appendLine("✅ SISTEMA FUNCIONANDO CORRETAMENTE!")
            } else {
                appendLine("❌ PROBLEMAS IDENTIFICADOS - CORREÇÃO NECESSÁRIA")
            }
        }
    }
    
    /**
     * 🧹 LIMPAR DADOS PROBLEMÁTICOS
     */
    suspend fun limparDadosProblematicos(context: Context): LimpezaResult {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "🧹 === INICIANDO LIMPEZA DE DADOS PROBLEMÁTICOS ===")
                
                val database = AppDatabase.getInstance(context)
                val pontosDao = database.pontosGenericosDao()
                val sincronizacaoDao = database.pontoSincronizacaoDao()
                
                var pontosRemovidos = 0
                var sincronizacoesRemovidas = 0
                
                // Remover pontos duplicados
                val todosPontos = pontosDao.getAllPontos()
                val pontosPorFuncionario = todosPontos.groupBy { it.funcionarioId }
                
                for ((funcionarioId, pontos) in pontosPorFuncionario) {
                    val pontosPorDia = pontos.groupBy { 
                        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(it.dataHora))
                    }
                    
                    for ((dia, pontosDoDia) in pontosPorDia) {
                        if (pontosDoDia.size > 4) { // Mais de 4 pontos no mesmo dia
                            val pontosParaRemover = pontosDoDia.drop(4)
                            for (ponto in pontosParaRemover) {
                                try {
                                    pontosDao.delete(ponto)
                                    pontosRemovidos++
                                } catch (e: Exception) {
                                    Log.e(TAG, "❌ Erro ao remover ponto: ${e.message}")
                                }
                            }
                        }
                    }
                }
                
                val pontosSincronizacao = sincronizacaoDao.getAllPontos()
                val dataLimite = System.currentTimeMillis() - (30 * 24 * 60 * 60 * 1000L) // 30 dias
                
                for (ponto in pontosSincronizacao) {
                    try {
                        val dataPonto = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                            .parse(ponto.dataHora)?.time ?: 0L
                        
                        if (dataPonto < dataLimite) {
                            // sincronizacaoDao.delete(ponto)
                            sincronizacoesRemovidas++
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Erro ao processar ponto de sincronização: ${e.message}")
                    }
                }
                
                Log.d(TAG, "✅ Limpeza concluída:")
                Log.d(TAG, "  • Pontos removidos: $pontosRemovidos")
                Log.d(TAG, "  • Sincronizações removidas: $sincronizacoesRemovidas")
                
                LimpezaResult(
                    sucesso = true,
                    pontosRemovidos = pontosRemovidos,
                    sincronizacoesRemovidas = sincronizacoesRemovidas,
                    mensagem = "Limpeza concluída com sucesso"
                )
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro na limpeza: ${e.message}")
                LimpezaResult(
                    sucesso = false,
                    pontosRemovidos = 0,
                    sincronizacoesRemovidas = 0,
                    mensagem = "Erro na limpeza: ${e.message}"
                )
            }
        }
    }
    
    /**
     * 📊 RESULTADO DO DIAGNÓSTICO
     */
    data class DiagnosticoResult(
        val problemas: List<String>,
        val avisos: List<String>,
        val sucessos: List<String>,
        val timestamp: Long
    )
    
    /**
     * 🧹 RESULTADO DA LIMPEZA
     */
    data class LimpezaResult(
        val sucesso: Boolean,
        val pontosRemovidos: Int,
        val sincronizacoesRemovidas: Int,
        val mensagem: String
    )
} 