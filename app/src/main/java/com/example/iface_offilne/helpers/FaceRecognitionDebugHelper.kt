package com.example.iface_offilne.helpers

import android.content.Context
import android.util.Log
import com.example.iface_offilne.data.AppDatabase
import com.example.iface_offilne.data.FaceEntity
import com.example.iface_offilne.data.FuncionariosEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 🔧 HELPER DE DEBUG PARA RECONHECIMENTO FACIAL
 * 
 * Este helper é usado para diagnosticar problemas no reconhecimento facial
 * e verificar a integridade dos dados no banco.
 */
class FaceRecognitionDebugHelper(private val context: Context) {
    
    companion object {
        private const val TAG = "FaceRecognitionDebug"
    }
    
    /**
     * 🔍 VERIFICAR INTEGRIDADE DO BANCO DE DADOS
     */
    suspend fun verificarIntegridadeBanco(): String {
        return withContext(Dispatchers.IO) {
            try {
                val db = AppDatabase.getInstance(context)
                val funcionarioDao = db.usuariosDao()
                val faceDao = db.faceDao()
                
                val funcionarios = funcionarioDao.getUsuario()
                val faces = faceDao.getAllFaces()
                
                val relatorio = buildString {
                    appendLine("🔍 === VERIFICAÇÃO DE INTEGRIDADE DO BANCO ===")
                    appendLine("")
                    
                    appendLine("📊 ESTATÍSTICAS:")
                    appendLine("   - Funcionários cadastrados: ${funcionarios.size}")
                    appendLine("   - Faces cadastradas: ${faces.size}")
                    appendLine("")
                    
                    appendLine("👥 FUNCIONÁRIOS:")
                    funcionarios.forEachIndexed { index, funcionario ->
                        appendLine("   ${index + 1}. ${funcionario.nome} (${funcionario.codigo})")
                    }
                    appendLine("")
                    
                    appendLine("📸 FACES:")
                    faces.forEachIndexed { index, face ->
                        appendLine("   ${index + 1}. Funcionário: ${face.funcionarioId}")
                        appendLine("      - Embedding: ${face.embedding.length} caracteres")
                        appendLine("      - Válido: ${validarEmbeddingString(face.embedding)}")
                    }
                    appendLine("")
                    
                    // Verificar correspondência
                    val funcionariosComFace = funcionarios.count { funcionario ->
                        faces.any { it.funcionarioId == funcionario.codigo }
                    }
                    
                    appendLine("✅ CORRESPONDÊNCIA:")
                    appendLine("   - Funcionários com face: $funcionariosComFace/${funcionarios.size}")
                    appendLine("   - Faces sem funcionário: ${faces.count { face ->
                        funcionarios.none { it.codigo == face.funcionarioId }
                    }}")
                }
                
                Log.d(TAG, relatorio)
                return@withContext relatorio
                
            } catch (e: Exception) {
                val erro = "❌ Erro na verificação: ${e.message}"
                Log.e(TAG, erro, e)
                return@withContext erro
            }
        }
    }
    
    /**
     * 🧪 TESTAR RECONHECIMENTO COM DADOS DUMMY
     */
    suspend fun testarReconhecimentoComDummy(): String {
        return withContext(Dispatchers.IO) {
            try {
                val helper = FaceRecognitionHelper(context)
                
                // Criar vetor dummy
                val dummyEmbedding = FloatArray(192) { 0.1f }
                
                val relatorio = buildString {
                    appendLine("🧪 === TESTE DE RECONHECIMENTO COM DADOS DUMMY ===")
                    appendLine("")
                    
                    appendLine("📊 VETOR DUMMY:")
                    appendLine("   - Tamanho: ${dummyEmbedding.size}")
                    appendLine("   - Valores válidos: ${dummyEmbedding.all { !it.isNaN() && !it.isInfinite() }}")
                    appendLine("")
                    
                    try {
                        val resultado = helper.recognizeFace(dummyEmbedding)
                        
                        appendLine("🎯 RESULTADO:")
                        if (resultado != null) {
                            appendLine("   ✅ Funcionário reconhecido: ${resultado.nome}")
                        } else {
                            appendLine("   ❌ Nenhum funcionário reconhecido")
                        }
                    } catch (e: Exception) {
                        appendLine("   ❌ Erro no reconhecimento: ${e.message}")
                    }
                }
                
                Log.d(TAG, relatorio)
                return@withContext relatorio
                
            } catch (e: Exception) {
                val erro = "❌ Erro no teste: ${e.message}"
                Log.e(TAG, erro, e)
                return@withContext erro
            }
        }
    }
    
    /**
     * 🔧 VERIFICAR CONFIGURAÇÃO DO FACE RECOGNITION HELPER
     */
    suspend fun verificarConfiguracaoHelper(): String {
        return withContext(Dispatchers.IO) {
            try {
                val helper = FaceRecognitionHelper(context)
                
                val relatorio = buildString {
                    appendLine("🔧 === VERIFICAÇÃO DE CONFIGURAÇÃO ===")
                    appendLine("")
                    
                    appendLine("📊 THRESHOLDS:")
                    appendLine("   - BASE_THRESHOLD: 0.7")
                    appendLine("   - MIN_SIMILARITY_FOR_ANY_APPROVAL: 0.5")
                    appendLine("   - MAX_EUCLIDEAN_DISTANCE: 0.6")
                    appendLine("   - MIN_DIFFERENCE_BETWEEN_PEOPLE: 0.15")
                    appendLine("")
                    
                    appendLine("🎛️ CONFIGURAÇÕES:")
                    appendLine("   - MODO_TESTE_ATIVO: false")
                    appendLine("   - DEBUG_MODE: true")
                    appendLine("   - MAX_CANDIDATES_ALLOWED: 1")
                    appendLine("")
                    
                    // Testar cache
                    try {
                        helper.clearCache()
                        appendLine("✅ Cache limpo com sucesso")
                    } catch (e: Exception) {
                        appendLine("❌ Erro ao limpar cache: ${e.message}")
                    }
                }
                
                Log.d(TAG, relatorio)
                return@withContext relatorio
                
            } catch (e: Exception) {
                val erro = "❌ Erro na verificação: ${e.message}"
                Log.e(TAG, erro, e)
                return@withContext erro
            }
        }
    }
    
    /**
     * ✅ VALIDAR STRING DE EMBEDDING
     */
    private fun validarEmbeddingString(embeddingString: String): String {
        return try {
            if (embeddingString.isBlank()) {
                "❌ Vazio"
            } else {
                val values = embeddingString.split(",")
                if (values.size != 192 && values.size != 512) {
                    "❌ Tamanho inválido (${values.size})"
                } else {
                    val hasInvalidValues = values.any { value ->
                        try {
                            val floatValue = value.trim().toFloat()
                            floatValue.isNaN() || floatValue.isInfinite()
                        } catch (e: NumberFormatException) {
                            true
                        }
                    }
                    
                    if (hasInvalidValues) {
                        "❌ Valores inválidos"
                    } else {
                        "✅ Válido"
                    }
                }
            }
        } catch (e: Exception) {
            "❌ Erro: ${e.message}"
        }
    }
    
    /**
     * 🚀 EXECUTAR TODOS OS TESTES
     */
    suspend fun executarTodosTestes(): String {
        return withContext(Dispatchers.IO) {
            try {
                val relatorio = buildString {
                    appendLine("🚀 === EXECUTANDO TODOS OS TESTES DE DEBUG ===")
                    appendLine("")
                    
                    appendLine("1️⃣ VERIFICAÇÃO DE INTEGRIDADE:")
                    appendLine(verificarIntegridadeBanco())
                    appendLine("")
                    
                    appendLine("2️⃣ TESTE DE RECONHECIMENTO:")
                    appendLine(testarReconhecimentoComDummy())
                    appendLine("")
                    
                    appendLine("3️⃣ VERIFICAÇÃO DE CONFIGURAÇÃO:")
                    appendLine(verificarConfiguracaoHelper())
                    appendLine("")
                    
                    appendLine("✅ TODOS OS TESTES CONCLUÍDOS")
                }
                
                Log.d(TAG, relatorio)
                return@withContext relatorio
                
            } catch (e: Exception) {
                val erro = "❌ Erro na execução dos testes: ${e.message}"
                Log.e(TAG, erro, e)
                return@withContext erro
            }
        }
    }
} 