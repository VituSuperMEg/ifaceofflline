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
        // ✅ THRESHOLDS MUITO MAIS RIGOROSOS PARA EVITAR RECONHECIMENTOS ERRADOS
        private const val COSINE_THRESHOLD = 0.65f // Era 0.30f - MUITO MAIS RIGOROSO
        private const val FALLBACK_THRESHOLD = 0.55f // Era 0.20f - MUITO MAIS RIGOROSO
        private const val MIN_SCORE_DIFFERENCE = 0.15f // Era 0.05f - Diferença maior entre candidatos
        private const val HIGH_CONFIDENCE_THRESHOLD = 0.75f // Era 0.40f - MUITO MAIS RIGOROSO
        private const val DEBUG_MODE = true // Ativado para debug do problema
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
                // ✅ CORREÇÃO: Verificar se o vetor é válido (aceitar 192 ou 512)
                if (faceEmbedding.isEmpty() || (faceEmbedding.size != 192 && faceEmbedding.size != 512)) {
                    Log.e(TAG, "❌ Vetor facial inválido: tamanho=${faceEmbedding.size} (esperado: 192 ou 512)")
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
                    return@withContext recognizeFace(embeddingCorrigido) // Recursão com vetor corrigido
                }
                
                if (DEBUG_MODE) {
                    Log.d(TAG, "🔍 === INICIANDO RECONHECIMENTO FACIAL ===")
                    Log.d(TAG, "🔍 Vetor de entrada: tamanho=${faceEmbedding.size}")
                }
                
                // ✅ CORREÇÃO: Sempre buscar funcionários do banco (sem cache)
                val funcionarios = try {
                    funcionarioDao.getUsuario()
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Erro ao buscar funcionários: ${e.message}")
                    return@withContext null
                }
                if (DEBUG_MODE) Log.d(TAG, "👥 Total de funcionários: ${funcionarios.size}")
                
                if (funcionarios.isEmpty()) {
                    if (DEBUG_MODE) Log.w(TAG, "⚠️  Nenhum funcionário cadastrado!")
                    return@withContext null
                }
                
                var bestMatch: FuncionariosEntity? = null
                var bestSimilarity = 0f
                var secondBestMatch: FuncionariosEntity? = null
                var secondBestSimilarity = 0f
                var thirdBestMatch: FuncionariosEntity? = null
                var thirdBestSimilarity = 0f
                
                // ✅ CORREÇÃO: Processamento sem cache
                for (funcionario in funcionarios) {
                    try {
                        // Buscar o rosto do funcionário
                        val faceEntity = try {
                            faceDao.getByFuncionarioId(funcionario.codigo)
                        } catch (e: Exception) {
                            if (DEBUG_MODE) Log.w(TAG, "⚠️ Erro ao buscar face de ${funcionario.nome}: ${e.message}")
                            continue
                        }
                        
                        if (faceEntity != null) {
                            // ✅ CORREÇÃO: Verificar se o embedding é válido
                            if (faceEntity.embedding.isBlank()) {
                                if (DEBUG_MODE) Log.w(TAG, "⚠️ Embedding vazio para ${funcionario.nome}")
                                continue
                            }
                            
                            // Converter o embedding string para FloatArray
                            val storedEmbedding = try {
                                stringToFloatArray(faceEntity.embedding)
                            } catch (e: Exception) {
                                if (DEBUG_MODE) Log.w(TAG, "⚠️ Erro ao converter embedding de ${funcionario.nome}: ${e.message}")
                                continue
                            }
                            
                            // ✅ CORREÇÃO: Verificar se o embedding convertido é válido (aceitar 192 ou 512)
                            if (storedEmbedding.size != 192 && storedEmbedding.size != 512) {
                                if (DEBUG_MODE) Log.w(TAG, "⚠️ Embedding inválido para ${funcionario.nome}: tamanho=${storedEmbedding.size}")
                                continue
                            }
                            
                            // ✅ OTIMIZAÇÃO: Calcular apenas similaridade de cosseno
                            val cosineSimilarity = try {
                                calculateCosineSimilarity(faceEmbedding, storedEmbedding)
                            } catch (e: Exception) {
                                if (DEBUG_MODE) Log.w(TAG, "⚠️ Erro ao calcular similaridade para ${funcionario.nome}: ${e.message}")
                                continue
                            }
                            
                            if (DEBUG_MODE) {
                                val euclideanDistance = calculateEuclideanDistance(faceEmbedding, storedEmbedding)
                                Log.d(TAG, "📊 Funcionário ${funcionario.nome}:")
                                Log.d(TAG, "   - Similaridade cosseno: $cosineSimilarity (limite: $COSINE_THRESHOLD)")
                                Log.d(TAG, "   - Distância euclidiana: $euclideanDistance")
                            }
                            
                            // ✅ THRESHOLD OTIMIZADO: Usar novo valor mais rigoroso
                            if (cosineSimilarity >= COSINE_THRESHOLD) {
                                if (cosineSimilarity > bestSimilarity) {
                                    // Mover o anterior melhor para segundo lugar
                                    thirdBestMatch = secondBestMatch
                                    thirdBestSimilarity = secondBestSimilarity
                                    secondBestMatch = bestMatch
                                    secondBestSimilarity = bestSimilarity
                                    
                                    bestSimilarity = cosineSimilarity
                                    bestMatch = funcionario
                                    if (DEBUG_MODE) Log.d(TAG, "⭐ Novo melhor match: ${funcionario.nome} (similaridade: $cosineSimilarity)")
                                } else if (cosineSimilarity > secondBestSimilarity) {
                                    // Atualizar segundo melhor
                                    thirdBestMatch = secondBestMatch
                                    thirdBestSimilarity = secondBestSimilarity
                                    secondBestSimilarity = cosineSimilarity
                                    secondBestMatch = funcionario
                                    if (DEBUG_MODE) Log.d(TAG, "🥈 Segundo melhor match: ${funcionario.nome} (similaridade: $cosineSimilarity)")
                                } else if (cosineSimilarity > thirdBestSimilarity) {
                                    // Atualizar terceiro melhor
                                    thirdBestSimilarity = cosineSimilarity
                                    thirdBestMatch = funcionario
                                    if (DEBUG_MODE) Log.d(TAG, "🥉 Terceiro melhor match: ${funcionario.nome} (similaridade: $cosineSimilarity)")
                                }
                            }
                        }
                        
                    } catch (e: Exception) {
                        if (DEBUG_MODE) Log.w(TAG, "Erro ao processar funcionário ${funcionario.nome}: ${e.message}")
                        // Continua para o próximo funcionário
                    }
                }
                
                var candidateMatch: FuncionariosEntity? = null
                var matchSimilarity = 0f

                if (bestMatch != null) {
                    Log.d(TAG, "✅ Match encontrado: ${bestMatch.nome} (similaridade: $bestSimilarity)")
                    
                    // ✅ VERIFICAÇÃO OTIMIZADA: Análise mais rigorosa para evitar confusões
                    if (secondBestMatch != null) {
                        val scoreDifference = bestSimilarity - secondBestSimilarity
                        
                        if (DEBUG_MODE) {
                            Log.d(TAG, "📊 Análise de diferenças:")
                            Log.d(TAG, "   - Melhor: ${bestMatch.nome} (similaridade: $bestSimilarity)")
                            Log.d(TAG, "   - Segundo: ${secondBestMatch.nome} (similaridade: $secondBestSimilarity)")
                            Log.d(TAG, "   - Diferença: $scoreDifference (mínima: $MIN_SCORE_DIFFERENCE)")
                            if (thirdBestMatch != null) {
                                Log.d(TAG, "   - Terceiro: ${thirdBestMatch.nome} (similaridade: $thirdBestSimilarity)")
                            }
                        }
                        
                        // ✅ NOVA LÓGICA: Verificação muito mais rigorosa para evitar confusões
                        if (bestSimilarity >= HIGH_CONFIDENCE_THRESHOLD) {
                            // Match de alta confiança - aceitar apenas se for MUITO claro
                            if (scoreDifference >= MIN_SCORE_DIFFERENCE) {
                                candidateMatch = bestMatch
                                matchSimilarity = bestSimilarity
                                if (DEBUG_MODE) Log.d(TAG, "🚀 Match de alta confiança aceito: ${bestMatch.nome}")
                            } else {
                                if (DEBUG_MODE) Log.w(TAG, "⚠️ Alta similaridade mas diferença insuficiente - REJEITADO")
                                candidateMatch = null
                            }
                        } else if (scoreDifference >= MIN_SCORE_DIFFERENCE && bestSimilarity >= COSINE_THRESHOLD) {
                            // Diferença suficiente E similaridade boa - aceitar
                            candidateMatch = bestMatch
                            matchSimilarity = bestSimilarity
                            if (DEBUG_MODE) Log.d(TAG, "✅ Match aceito com diferença suficiente")
                        } else {
                            // Qualquer dúvida - rejeitar para evitar erro
                            if (DEBUG_MODE) {
                                Log.w(TAG, "🚫 MATCH REJEITADO - Critérios não atendidos:")
                                Log.w(TAG, "   - Similaridade: $bestSimilarity (mín: $COSINE_THRESHOLD)")
                                Log.w(TAG, "   - Diferença: $scoreDifference (mín: $MIN_SCORE_DIFFERENCE)")
                                Log.w(TAG, "   - REJEITANDO para evitar reconhecimento errado")
                            }
                            candidateMatch = null
                        }
                    } else {
                        // Apenas um match - aceitar APENAS se for muito confiável
                        if (bestSimilarity >= HIGH_CONFIDENCE_THRESHOLD) {
                            candidateMatch = bestMatch
                            matchSimilarity = bestSimilarity
                            if (DEBUG_MODE) Log.d(TAG, "✅ Match único aceito com alta confiança: ${bestMatch.nome}")
                        } else {
                            if (DEBUG_MODE) {
                                Log.w(TAG, "🚫 Match único REJEITADO - Similaridade baixa:")
                                Log.w(TAG, "   - Similaridade: $bestSimilarity (mín: $HIGH_CONFIDENCE_THRESHOLD)")
                                Log.w(TAG, "   - REJEITANDO para evitar reconhecimento errado")
                            }
                            candidateMatch = null
                        }
                    }
                } else {
                    if (DEBUG_MODE) Log.d(TAG, "❌ Nenhum match encontrado")
                    
                    // ✅ FALLBACK OTIMIZADO: Threshold mais rigoroso
                    if (DEBUG_MODE) Log.d(TAG, "🔄 Tentando fallback com threshold mais rigoroso...")
                    
                    for (funcionario in funcionarios) {
                        try {
                            val faceEntity = faceDao.getByFuncionarioId(funcionario.codigo)
                            if (faceEntity != null) {
                                val storedEmbedding = stringToFloatArray(faceEntity.embedding)
                                val cosineSimilarity = calculateCosineSimilarity(faceEmbedding, storedEmbedding)
                                
                                if (cosineSimilarity >= FALLBACK_THRESHOLD) {
                                    Log.d(TAG, "🆘 Fallback: Match encontrado com threshold $FALLBACK_THRESHOLD")
                                    Log.d(TAG, "✅ Funcionário: ${funcionario.nome} (similaridade: $cosineSimilarity)")
                                    candidateMatch = funcionario
                                    matchSimilarity = cosineSimilarity
                                    break
                                }
                            }
                        } catch (e: Exception) {
                            if (DEBUG_MODE) Log.w(TAG, "Erro no fallback para ${funcionario.nome}: ${e.message}")
                        }
                    }
                }

                // ✅ CORREÇÃO: Retornar diretamente o match (sem tracker)
                if (candidateMatch != null) {
                    Log.d(TAG, "🎯 Match final confirmado: ${candidateMatch.nome} (similaridade: $matchSimilarity)")
                } else if (DEBUG_MODE) {
                    Log.d(TAG, "❌ Nenhum match encontrado ou confirmado")
                }
                
                return@withContext candidateMatch
                
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
     * ✅ OTIMIZADA: Calcula a similaridade de cosseno entre dois vetores
     */
    private fun calculateCosineSimilarity(vector1: FloatArray, vector2: FloatArray): Float {
        try {
            // ✅ CORREÇÃO: Verificar se os vetores são válidos
            if (vector1.isEmpty() || vector2.isEmpty()) {
                Log.w(TAG, "⚠️ Vetores vazios")
                return 0f
            }
            
            if (vector1.size != vector2.size) {
                if (DEBUG_MODE) Log.w(TAG, "⚠️  Vetores têm tamanhos diferentes: ${vector1.size} vs ${vector2.size}")
                
                // ✅ CORREÇÃO: Aceitar vetores de 192 ou 512 dimensões
                if ((vector1.size != 192 && vector1.size != 512) || (vector2.size != 192 && vector2.size != 512)) {
                    Log.w(TAG, "⚠️ Vetores não têm tamanho válido: ${vector1.size} vs ${vector2.size}")
                    return 0f
                }
                
                // ✅ CORREÇÃO: Se tamanhos diferentes, usar o menor
                val minSize = minOf(vector1.size, vector2.size)
                if (minSize > 0) {
                    if (DEBUG_MODE) Log.d(TAG, "🔧 Usando tamanho mínimo: $minSize")
                    val v1 = vector1.sliceArray(0 until minSize)
                    val v2 = vector2.sliceArray(0 until minSize)
                    return calculateCosineSimilarityInternal(v1, v2)
                }
                return 0f
            }
            
            // ✅ CORREÇÃO: Verificar se há valores inválidos
            if (vector1.any { it.isNaN() || it.isInfinite() } || vector2.any { it.isNaN() || it.isInfinite() }) {
                Log.w(TAG, "⚠️ Vetores contêm valores inválidos")
                return 0f
            }
            
            return calculateCosineSimilarityInternal(vector1, vector2)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao calcular similaridade: ${e.message}")
            return 0f
        }
    }
    
    private fun calculateCosineSimilarityInternal(vector1: FloatArray, vector2: FloatArray): Float {
        
        var dotProduct = 0f
        var magnitude1 = 0f
        var magnitude2 = 0f
        
        for (i in vector1.indices) {
            dotProduct += vector1[i] * vector2[i]
            magnitude1 += vector1[i] * vector1[i]
            magnitude2 += vector2[i] * vector2[i]
        }
        
        magnitude1 = sqrt(magnitude1)
        magnitude2 = sqrt(magnitude2)
        
        return if (magnitude1 != 0f && magnitude2 != 0f) {
            val similarity = dotProduct / (magnitude1 * magnitude2)
            // Converter para similaridade absoluta para facilitar comparação
            kotlin.math.abs(similarity)
        } else {
            0f
        }
    }
    
    /**
     * Calcula a distância euclidiana entre dois vetores (método alternativo)
     */
    private fun calculateEuclideanDistance(vector1: FloatArray, vector2: FloatArray): Float {
        if (vector1.size != vector2.size) return Float.MAX_VALUE
        
        var sum = 0f
        for (i in vector1.indices) {
            val diff = vector1[i] - vector2[i]
            sum += diff * diff
        }
        
        return sqrt(sum)
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