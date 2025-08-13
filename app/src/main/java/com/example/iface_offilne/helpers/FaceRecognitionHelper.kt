package com.example.iface_offilne.helpers

import android.content.Context
import android.util.Log
import com.example.iface_offilne.data.AppDatabase
import com.example.iface_offilne.data.FaceEntity
import com.example.iface_offilne.data.FuncionariosEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

class FaceRecognitionHelper(private val context: Context) {
    
    private val database = AppDatabase.getInstance(context)
    private val faceDao = database.faceDao()
    private val funcionarioDao = database.usuariosDao()
    
    // ❌ REMOVIDO: Cache problemático que estava causando confusão
    // private var cachedFuncionarios: List<FuncionariosEntity>? = null
    // private var cacheTimestamp = 0L
    // private val cacheExpirationMs = 30000L // 30 segundos
    
    companion object {
        private const val TAG = "FaceRecognitionHelper"
        private const val DEBUG_MODE = true
        
        // ✅ SIMPLES: Thresholds mais permissivos para funcionar facilmente
        private const val COSINE_THRESHOLD = 0.50f // Bem mais baixo
        private const val FALLBACK_THRESHOLD = 0.45f // Bem mais baixo
        private const val MIN_SCORE_DIFFERENCE = 0.10f // Bem mais baixo
        private const val HIGH_CONFIDENCE_THRESHOLD = 0.60f // Bem mais baixo
        private const val ULTRA_HIGH_CONFIDENCE_THRESHOLD = 0.65f // Bem mais baixo
        
        // ✅ SIMPLES: Processar mais funcionários para ter mais chances
        private const val MAX_FUNCIONARIOS_PROCESSAR = 100 // Aumentado
    }

    // ❌ REMOVIDO: Tracker problemático que mantinha estado
    // private val matchTracker = FaceMatchTracker()
    
    /**
     * Compara um vetor facial com todos os rostos cadastrados no banco
     * e retorna o funcionário correspondente se houver match
     */
    suspend fun recognizeFace(faceEmbedding: FloatArray): FuncionariosEntity? {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "🔍 === INICIANDO RECONHECIMENTO FACIAL PROTEGIDO ===")
                
                // ✅ CORREÇÃO: Verificar se o vetor é válido (aceitar 192 ou 512)
                if (faceEmbedding.isEmpty() || (faceEmbedding.size != 192 && faceEmbedding.size != 512)) {
                    Log.e(TAG, "❌ Vetor facial inválido: tamanho=${faceEmbedding.size} (esperado: 192 ou 512)")
                    return@withContext null
                }
                
                // ✅ NOVA: Verificar se há valores válidos no vetor
                val validValues = faceEmbedding.count { !it.isNaN() && !it.isInfinite() }
                if (validValues < faceEmbedding.size / 2) {
                    Log.e(TAG, "❌ Muitos valores inválidos no vetor: $validValues/${faceEmbedding.size} válidos")
                    return@withContext null
                }
                
                // ✅ CORREÇÃO: Verificar se o contexto ainda é válido
                if (context is android.app.Activity) {
                    val activity = context as android.app.Activity
                    if (activity.isFinishing || activity.isDestroyed) {
                        Log.w(TAG, "⚠️ Activity finalizada, cancelando reconhecimento")
                        return@withContext null
                    }
                }
                
                // ✅ CORREÇÃO: Tratar valores inválidos em vez de falhar
                var embeddingParaUso = faceEmbedding
                if (faceEmbedding.any { it.isNaN() || it.isInfinite() }) {
                    Log.w(TAG, "⚠️ Vetor facial contém valores inválidos - tentando corrigir...")
                    
                    val embeddingCorrigido = FloatArray(faceEmbedding.size) { index ->
                        val valor = faceEmbedding[index]
                        if (valor.isNaN() || valor.isInfinite()) {
                            0.0f // Substituir por 0
                        } else {
                            valor
                        }
                    }
                    
                    Log.d(TAG, "🔧 Vetor corrigido com sucesso")
                    embeddingParaUso = embeddingCorrigido
                }
                
                if (DEBUG_MODE) {
                    Log.d(TAG, "🔍 === INICIANDO RECONHECIMENTO FACIAL ===")
                    Log.d(TAG, "🔍 Vetor de entrada: tamanho=${embeddingParaUso.size}")
                    Log.d(TAG, "🔍 Primeiros 5 valores: [${embeddingParaUso.take(5).joinToString(", ")}...]")
                }
                
                // ✅ CORREÇÃO: Sempre buscar funcionários do banco (sem cache)
                val funcionarios = try {
                    Log.d(TAG, "📋 Buscando funcionários do banco...")
                    val allFuncionarios = funcionarioDao.getUsuario()
                    
                    // ✅ OTIMIZAÇÃO: Limitar número de funcionários para velocidade
                    val limitedFuncionarios = if (allFuncionarios.size > MAX_FUNCIONARIOS_PROCESSAR) {
                        Log.d(TAG, "⚡ Limitando para $MAX_FUNCIONARIOS_PROCESSAR funcionários (de ${allFuncionarios.size}) para velocidade")
                        allFuncionarios.take(MAX_FUNCIONARIOS_PROCESSAR)
                    } else {
                        allFuncionarios
                    }
                    
                    limitedFuncionarios
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Erro ao buscar funcionários: ${e.message}")
                    return@withContext null
                }
                if (DEBUG_MODE) Log.d(TAG, "👥 Total de funcionários: ${funcionarios.size}")
                
                if (funcionarios.isEmpty()) {
                    if (DEBUG_MODE) Log.w(TAG, "⚠️  Nenhum funcionário cadastrado!")
                    return@withContext null
                }

                // ✅ OTIMIZAÇÃO: Reconhecimento mais rápido e eficiente
                val candidatos = mutableListOf<Pair<FuncionariosEntity, Float>>()
                
                // ✅ OTIMIZAÇÃO: Processar apenas funcionários com faces cadastradas
                val funcionariosComFace = funcionarios.filter { funcionario ->
                    try {
                        val faceEntity = faceDao.getByFuncionarioId(funcionario.codigo)
                        faceEntity != null
                    } catch (e: Exception) {
                        false
                    }
                }
                
                Log.d(TAG, "🎯 Funcionários com face cadastrada: ${funcionariosComFace.size}")
                
                if (funcionariosComFace.isEmpty()) {
                    if (DEBUG_MODE) Log.w(TAG, "⚠️ Nenhum funcionário com face cadastrada!")
                    return@withContext null
                }

                // ✅ OTIMIZAÇÃO: Calcular similaridades apenas para funcionários relevantes
                for (funcionario in funcionariosComFace) {
                    try {
                        val faceEntity = faceDao.getByFuncionarioId(funcionario.codigo)
                        if (faceEntity != null) {
                            val storedEmbedding = stringToFloatArray(faceEntity.embedding)
                            
                            // ✅ OTIMIZAÇÃO: Verificação rápida de compatibilidade
                            if (storedEmbedding.size != embeddingParaUso.size) {
                                if (DEBUG_MODE) Log.w(TAG, "⚠️ Tamanhos diferentes para ${funcionario.nome}: ${storedEmbedding.size} vs ${embeddingParaUso.size}")
                                continue
                            }
                            
                            // ✅ OTIMIZAÇÃO: Calcular apenas similaridade de cosseno (mais rápido)
                            val cosineSimilarity = try {
                                calculateCosineSimilarity(embeddingParaUso, storedEmbedding)
                            } catch (e: Exception) {
                                if (DEBUG_MODE) Log.w(TAG, "⚠️ Erro ao calcular similaridade para ${funcionario.nome}: ${e.message}")
                                continue
                            }
                            
                            // ✅ OTIMIZAÇÃO: Adicionar apenas candidatos viáveis
                            if (cosineSimilarity >= COSINE_THRESHOLD) {
                                candidatos.add(Pair(funcionario, cosineSimilarity))
                                if (DEBUG_MODE) Log.d(TAG, "🎯 Candidato: ${funcionario.nome} - Similaridade: $cosineSimilarity")
                            }
                        }
                    } catch (e: Exception) {
                        if (DEBUG_MODE) Log.w(TAG, "⚠️ Erro ao processar ${funcionario.nome}: ${e.message}")
                        continue
                    }
                }

                // ✅ OTIMIZAÇÃO: Processamento rápido de candidatos
                if (candidatos.isEmpty()) {
                    if (DEBUG_MODE) Log.w(TAG, "❌ Nenhum candidato viável encontrado")
                    return@withContext null
                }

                // ✅ OTIMIZAÇÃO: Ordenar por similaridade (mais alto primeiro)
                candidatos.sortByDescending { it.second }
                
                val melhorCandidato = candidatos.first()
                val melhorSimilaridade = melhorCandidato.second
                val funcionarioEscolhido = melhorCandidato.first
                
                if (DEBUG_MODE) {
                    Log.d(TAG, "🏆 MELHOR CANDIDATO:")
                    Log.d(TAG, "   - Nome: ${funcionarioEscolhido.nome}")
                    Log.d(TAG, "   - Similaridade: $melhorSimilaridade")
                    Log.d(TAG, "   - Total de candidatos: ${candidatos.size}")
                }

                // ✅ SIMPLES: Lógica de decisão mais direta
                when {
                    // Se só tem um candidato e passa no threshold básico
                    candidatos.size == 1 && melhorSimilaridade >= COSINE_THRESHOLD -> {
                        if (DEBUG_MODE) Log.d(TAG, "✅ ACEITO - Único candidato: $melhorSimilaridade")
                        return@withContext funcionarioEscolhido
                    }
                    
                    // Se tem múltiplos candidatos, aceitar o melhor se for razoável
                    candidatos.size > 1 -> {
                        val segundoMelhor = candidatos[1].second
                        val diferenca = melhorSimilaridade - segundoMelhor
                        
                        if (DEBUG_MODE) {
                            Log.d(TAG, "📊 MÚLTIPLOS CANDIDATOS:")
                            Log.d(TAG, "   - Melhor: $melhorSimilaridade")
                            Log.d(TAG, "   - Segundo: $segundoMelhor")
                            Log.d(TAG, "   - Diferença: $diferenca")
                        }
                        
                        // ✅ SIMPLES: Aceitar se o melhor for significativamente melhor
                        if (melhorSimilaridade >= HIGH_CONFIDENCE_THRESHOLD || diferenca >= MIN_SCORE_DIFFERENCE) {
                            if (DEBUG_MODE) Log.d(TAG, "🎉 FUNCIONÁRIO RECONHECIDO: ${funcionarioEscolhido.nome}")
                            return@withContext funcionarioEscolhido
                        } else {
                            if (DEBUG_MODE) Log.d(TAG, "❌ REJEITADO - Diferença insuficiente")
                            return@withContext null
                        }
                    }
                    
                    else -> {
                        if (DEBUG_MODE) Log.d(TAG, "❌ REJEITADO - Sem candidatos suficientes")
                        return@withContext null
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Erro no reconhecimento facial: ${e.message}", e)
                null
            }
        }
    }
    
    /**
     * ✅ CORREÇÃO: Função simplificada sem cache
     */
    private suspend fun getCachedFuncionarios(): List<FuncionariosEntity> {
        // Sempre buscar do banco para evitar problemas de cache
        return funcionarioDao.getUsuario()
    }
    
    /**
     * Converte uma string de embedding para FloatArray
     */
    private fun stringToFloatArray(embeddingString: String): FloatArray {
        try {
            // ✅ CORREÇÃO: Verificar se a string é válida
            if (embeddingString.isBlank()) {
                throw IllegalArgumentException("String de embedding vazia")
            }
            
            val values = embeddingString.split(",")
            
            // ✅ CORREÇÃO: Verificar se há valores suficientes (aceitar 192 ou 512)
            if (values.size != 192 && values.size != 512) {
                throw IllegalArgumentException("Número incorreto de valores: ${values.size} (esperado: 192 ou 512)")
            }
            
            // ✅ CORREÇÃO: Criar array do tamanho correto baseado nos valores
            val floatArray = FloatArray(values.size)
            for (i in values.indices) {
                try {
                    val value = values[i].trim().toFloat()
                    // ✅ CORREÇÃO: Verificar se o valor é válido
                    if (value.isNaN() || value.isInfinite()) {
                        throw IllegalArgumentException("Valor inválido na posição $i: $value")
                    }
                    floatArray[i] = value
                } catch (e: NumberFormatException) {
                    throw IllegalArgumentException("Erro ao converter valor na posição $i: '${values[i]}'")
                }
            }
            
            return floatArray
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao converter string para FloatArray: ${e.message}")
            throw e
        }
    }
    
    /**
     * Calcula a similaridade de cosseno entre dois vetores
     */
    private fun calculateCosineSimilarity(vector1: FloatArray, vector2: FloatArray): Float {
        try {
            // ✅ NOVA: Verificação robusta dos vetores
            if (vector1.isEmpty() || vector2.isEmpty()) {
                Log.w(TAG, "⚠️ Vetor vazio detectado")
                return 0.0f
            }
            
            if (vector1.size != vector2.size) {
                Log.w(TAG, "⚠️ Vetores com tamanhos diferentes: ${vector1.size} vs ${vector2.size}")
                return 0.0f
            }
            
            // ✅ NOVA: Verificar se há valores válidos
            val validCount1 = vector1.count { !it.isNaN() && !it.isInfinite() }
            val validCount2 = vector2.count { !it.isNaN() && !it.isInfinite() }
            
            if (validCount1 < vector1.size / 2 || validCount2 < vector2.size / 2) {
                Log.w(TAG, "⚠️ Muitos valores inválidos nos vetores")
                return 0.0f
            }
            
            var dotProduct = 0.0
            var normA = 0.0
            var normB = 0.0
            
            for (i in vector1.indices) {
                val a = vector1[i]
                val b = vector2[i]
                
                // ✅ NOVA: Tratar valores inválidos
                val aClean = if (a.isNaN() || a.isInfinite()) 0.0f else a
                val bClean = if (b.isNaN() || b.isInfinite()) 0.0f else b
                
                dotProduct += (aClean * bClean).toDouble()
                normA += (aClean * aClean).toDouble()
                normB += (bClean * bClean).toDouble()
            }
            
            // ✅ NOVA: Verificar divisão por zero
            if (normA == 0.0 || normB == 0.0) {
                Log.w(TAG, "⚠️ Norma zero detectada")
                return 0.0f
            }
            
            val similarity = (dotProduct / (sqrt(normA) * sqrt(normB))).toFloat()
            
            // ✅ NOVA: Verificar resultado válido
            return if (similarity.isNaN() || similarity.isInfinite()) {
                Log.w(TAG, "⚠️ Similaridade inválida calculada")
                0.0f
            } else {
                similarity.coerceIn(-1.0f, 1.0f) // Garantir que está no range correto
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao calcular similaridade de cosseno: ${e.message}")
            return 0.0f
        }
    }
    
    /**
     * Calcula a distância euclidiana entre dois vetores
     */
    private fun calculateEuclideanDistance(vector1: FloatArray, vector2: FloatArray): Float {
        try {
            // ✅ NOVA: Verificação robusta dos vetores
            if (vector1.isEmpty() || vector2.isEmpty() || vector1.size != vector2.size) {
                Log.w(TAG, "⚠️ Vetores inválidos para distância euclidiana")
                return Float.MAX_VALUE
            }
            
            var sum = 0.0
            for (i in vector1.indices) {
                val a = if (vector1[i].isNaN() || vector1[i].isInfinite()) 0.0f else vector1[i]
                val b = if (vector2[i].isNaN() || vector2[i].isInfinite()) 0.0f else vector2[i]
                val diff = (a - b).toDouble()
                sum += diff * diff
            }
            
            val distance = sqrt(sum).toFloat()
            return if (distance.isNaN() || distance.isInfinite()) {
                Log.w(TAG, "⚠️ Distância euclidiana inválida")
                Float.MAX_VALUE
            } else {
                distance
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao calcular distância euclidiana: ${e.message}")
            return Float.MAX_VALUE
        }
    }
    
    /**
     * Limpa faces duplicadas ou incorretas do banco de dados
     */
    suspend fun limparFacesDuplicadas() {
        withContext(Dispatchers.IO) {
            try {
                if (DEBUG_MODE) Log.d(TAG, "🧹 === LIMPANDO FACES DUPLICADAS ===")
                
                val todasFaces = faceDao.getAllFaces()
                if (DEBUG_MODE) Log.d(TAG, "📊 Total de faces no banco: ${todasFaces.size}")
                
                val facesPorFuncionario = todasFaces.groupBy { it.funcionarioId }
                
                for ((funcionarioId, faces) in facesPorFuncionario) {
                    if (faces.size > 1) {
                        if (DEBUG_MODE) Log.w(TAG, "⚠️  Funcionário $funcionarioId tem ${faces.size} faces cadastradas!")
                        
                        // Manter apenas a face mais recente (maior ID)
                        val faceMaisRecente = faces.maxByOrNull { it.id }
                        
                        // Deletar as outras faces
                        for (face in faces) {
                            if (face.id != faceMaisRecente?.id) {
                                faceDao.delete(face)
                                if (DEBUG_MODE) Log.d(TAG, "🗑️  Deletada face duplicada ID: ${face.id}")
                            }
                        }
                    }
                }
                
                // ✅ OTIMIZAÇÃO: Limpar cache após limpeza
                // cachedFuncionarios = null // REMOVIDO: Sem cache
                
                if (DEBUG_MODE) Log.d(TAG, "✅ Limpeza de faces duplicadas concluída")
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao limpar faces duplicadas: ${e.message}", e)
            }
        }
    }
    
    /**
     * Verifica a integridade dos dados de faces
     */
    suspend fun verificarIntegridadeFaces() {
        withContext(Dispatchers.IO) {
            try {
                if (DEBUG_MODE) Log.d(TAG, "🔍 === VERIFICANDO INTEGRIDADE DAS FACES ===")
                
                val funcionarios = getCachedFuncionarios()
                val todasFaces = faceDao.getAllFaces()
                
                if (DEBUG_MODE) {
                    Log.d(TAG, "📊 Total de funcionários: ${funcionarios.size}")
                    Log.d(TAG, "📊 Total de faces: ${todasFaces.size}")
                }
                
                var funcionariosComFace = 0
                for (funcionario in funcionarios) {
                    val face = faceDao.getByFuncionarioId(funcionario.codigo)
                    if (face == null) {
                        if (DEBUG_MODE) Log.w(TAG, "⚠️  Funcionário ${funcionario.nome} (${funcionario.codigo}) não possui face cadastrada")
                    } else {
                        funcionariosComFace++
                        if (DEBUG_MODE) Log.d(TAG, "✅ Funcionário ${funcionario.nome} (${funcionario.codigo}) possui face cadastrada")
                    }
                }
                
                Log.d(TAG, "✅ Verificação concluída: $funcionariosComFace/${funcionarios.size} funcionários com face cadastrada")
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao verificar integridade: ${e.message}", e)
            }
        }
    }

    /**
     * ✅ OTIMIZADA: Testa o reconhecimento - versão simplificada
     */
    suspend fun testarReconhecimento(faceEmbedding: FloatArray) {
        if (!DEBUG_MODE) return // Só executa em modo debug
        
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "🧪 === TESTE DE RECONHECIMENTO ===")
                
                val funcionarios = getCachedFuncionarios()
                Log.d(TAG, "👥 Total de funcionários: ${funcionarios.size}")
                
                val scores = mutableListOf<Pair<FuncionariosEntity, Float>>()
                
                for (funcionario in funcionarios) {
                    try {
                        val faceEntity = faceDao.getByFuncionarioId(funcionario.codigo)
                        if (faceEntity != null) {
                            val storedEmbedding = stringToFloatArray(faceEntity.embedding)
                            val cosineSimilarity = calculateCosineSimilarity(faceEmbedding, storedEmbedding)
                            
                            scores.add(Pair(funcionario, cosineSimilarity))
                            
                            Log.d(TAG, "📊 ${funcionario.nome}: $cosineSimilarity (threshold: $COSINE_THRESHOLD)")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Erro ao testar ${funcionario.nome}: ${e.message}")
                    }
                }
                
                // Ordenar por score
                scores.sortByDescending { it.second }
                
                Log.d(TAG, "🏆 TOP 3 MATCHES:")
                scores.take(3).forEachIndexed { index, (funcionario, score) ->
                    val status = if (score >= COSINE_THRESHOLD) "✅" else "❌"
                    Log.d(TAG, "${index + 1}. $status ${funcionario.nome}: $score")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro no teste de reconhecimento: ${e.message}", e)
            }
        }
    }

    /**
     * ✅ NOVA FUNÇÃO: Verifica e corrige problemas de reconhecimento
     */
    suspend fun verificarECorrigirProblemasReconhecimento() {
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "🔧 === VERIFICANDO E CORRIGINDO PROBLEMAS DE RECONHECIMENTO ===")
                
                // 1. Limpar faces duplicadas
                limparFacesDuplicadas()
                
                // 2. Verificar integridade
                verificarIntegridadeFaces()
                
                // 3. Verificar embeddings válidos
                verificarEmbeddingsValidos()
                
                // 4. Limpar cache
                // cachedFuncionarios = null // REMOVIDO: Sem cache
                
                Log.d(TAG, "✅ Verificação e correção concluída")
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro na verificação: ${e.message}", e)
            }
        }
    }
    
    /**
     * ✅ NOVA FUNÇÃO: Verifica se os embeddings são válidos
     */
    private suspend fun verificarEmbeddingsValidos() {
        try {
            if (DEBUG_MODE) Log.d(TAG, "🔍 === VERIFICANDO EMBEDDINGS VÁLIDOS ===")
            
            val todasFaces = faceDao.getAllFaces()
            var embeddingsInvalidos = 0
            var embeddingsCorrigidos = 0
            
            for (face in todasFaces) {
                try {
                    val embedding = stringToFloatArray(face.embedding)
                    
                    // Verificar se o embedding tem o tamanho correto (aceitar 192 ou 512)
                    if (embedding.size != 192 && embedding.size != 512) {
                        if (DEBUG_MODE) Log.w(TAG, "⚠️ Embedding inválido para funcionário ${face.funcionarioId}: tamanho=${embedding.size}")
                        embeddingsInvalidos++
                        
                        // Tentar corrigir se possível
                        if (embedding.size > 0) {
                            val targetSize = if (embedding.size > 512) 512 else 192
                            val embeddingCorrigido = if (embedding.size > targetSize) {
                                embedding.sliceArray(0 until targetSize)
                            } else {
                                // Preencher com zeros se for menor
                                FloatArray(targetSize) { if (it < embedding.size) embedding[it] else 0f }
                            }
                            
                            val faceCorrigida = face.copy(embedding = embeddingCorrigido.joinToString(","))
                            faceDao.update(faceCorrigida)
                            embeddingsCorrigidos++
                            
                            if (DEBUG_MODE) Log.d(TAG, "✅ Embedding corrigido para funcionário ${face.funcionarioId}")
                        }
                    } else {
                        // Verificar se todos os valores são números válidos
                        val temValoresInvalidos = embedding.any { it.isNaN() || it.isInfinite() }
                        if (temValoresInvalidos) {
                            if (DEBUG_MODE) Log.w(TAG, "⚠️ Embedding com valores inválidos para funcionário ${face.funcionarioId}")
                            embeddingsInvalidos++
                        }
                    }
                    
                } catch (e: Exception) {
                    if (DEBUG_MODE) Log.e(TAG, "❌ Erro ao verificar embedding do funcionário ${face.funcionarioId}: ${e.message}")
                    embeddingsInvalidos++
                }
            }
            
            Log.d(TAG, "📊 Resultado da verificação de embeddings:")
            Log.d(TAG, "   - Total de faces: ${todasFaces.size}")
            Log.d(TAG, "   - Embeddings inválidos: $embeddingsInvalidos")
            Log.d(TAG, "   - Embeddings corrigidos: $embeddingsCorrigidos")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao verificar embeddings: ${e.message}", e)
        }
    }
    
    /**
     * ✅ NOVA FUNÇÃO: Força recadastramento de face para um funcionário
     */
    suspend fun forcarRecadastramento(funcionarioId: String) {
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "🔄 === FORÇANDO RECADASTRAMENTO PARA FUNCIONÁRIO $funcionarioId ===")
                
                // Deletar face atual
                faceDao.deleteByFuncionarioId(funcionarioId)
                
                // Limpar cache
                // cachedFuncionarios = null // REMOVIDO: Sem cache
                
                Log.d(TAG, "✅ Face deletada. Funcionário deve recadastrar sua face.")
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao forçar recadastramento: ${e.message}", e)
            }
        }
    }
    
    /**
     * ✅ NOVA FUNÇÃO: Lista funcionários com problemas de reconhecimento
     */
    suspend fun listarFuncionariosComProblemas(): List<String> {
        return withContext(Dispatchers.IO) {
            try {
                val problemas = mutableListOf<String>()
                val funcionarios = getCachedFuncionarios()
                
                for (funcionario in funcionarios) {
                    val face = faceDao.getByFuncionarioId(funcionario.codigo)
                    if (face == null) {
                        problemas.add("❌ ${funcionario.nome}: Sem face cadastrada")
                    } else {
                        try {
                            val embedding = stringToFloatArray(face.embedding)
                            if (embedding.size != 192 && embedding.size != 512) {
                                problemas.add("⚠️ ${funcionario.nome}: Embedding inválido (tamanho: ${embedding.size})")
                            }
                        } catch (e: Exception) {
                            problemas.add("❌ ${funcionario.nome}: Embedding corrompido")
                        }
                    }
                }
                
                if (DEBUG_MODE) {
                    Log.d(TAG, "📋 Funcionários com problemas:")
                    problemas.forEach { Log.d(TAG, "   $it") }
                }
                
                problemas
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao listar problemas: ${e.message}", e)
                emptyList()
            }
        }
    }
} 