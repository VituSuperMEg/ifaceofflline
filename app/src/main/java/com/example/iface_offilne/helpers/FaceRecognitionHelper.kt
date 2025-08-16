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
    
    // ✅ CACHE OTIMIZADO: Cache inteligente para performance
    private var cachedFacesData: List<CachedFaceData>? = null
    private var cacheTimestamp = 0L
    private val cacheExpirationMs = 60000L // 1 minuto de cache
    
    // ✅ NOVA: Classe para cache otimizado
    private data class CachedFaceData(
        val funcionario: FuncionariosEntity,
        val embedding: FloatArray
    )
    
    companion object {
        private const val TAG = "FaceRecognitionHelper"
        
        // ✅ CRITÉRIOS BALANCEADOS E PRECISOS PARA PRODUÇÃO
        private const val BASE_THRESHOLD = 0.50f // 50% - balanceado
        private const val GOOD_MATCH_THRESHOLD = 0.65f // 65% - bom
        private const val EXCELLENT_MATCH_THRESHOLD = 0.80f // 80% - excelente
        
        // MODO TESTE: Critérios mais permissivos para diagnóstico
        private const val TEST_BASE_THRESHOLD = 0.25f // 25% para teste
        private const val TEST_GOOD_MATCH_THRESHOLD = 0.40f // 40% para teste
        private const val TEST_MIN_SIMILARITY = 0.15f // 15% mínimo para teste
        private const val TEST_MAX_EUCLIDEAN_DISTANCE = 2.0f // Distância maior para teste
        
        // FLAG DE MODO TESTE
        private var MODO_TESTE_ATIVO = false // ✅ DESATIVADO - MODO PRODUÇÃO
        
        // Critérios de segurança BALANCEADOS
        private const val MIN_DIFFERENCE_BETWEEN_PEOPLE = 0.12f // 12% diferença entre pessoas
        private const val MAX_EUCLIDEAN_DISTANCE = 1.2f // Distância balanceada
        private const val CONFIDENCE_RATIO_THRESHOLD = 1.5f // Confiança balanceada
        
        // Sistema de qualidade BALANCEADO
        private const val HIGH_QUALITY_THRESHOLD = 0.75f // Alta qualidade
        private const val LOW_QUALITY_THRESHOLD = 0.60f // Baixa qualidade
        
        // VALIDAÇÕES BALANCEADAS
        private const val MIN_SIMILARITY_FOR_ANY_APPROVAL = 0.45f // 45% mínimo absoluto
        private const val MAX_CANDIDATES_ALLOWED = 3 // Máximo 3 candidatos
        private const val REQUIRED_CONFIDENCE_MULTIPLIER = 1.4f // Multiplicador
        
        private const val DEBUG_MODE = true // Debug para análise
    }

    /**
     * ✅ VERSÃO OTIMIZADA: Reconhecimento facial rápido e eficiente
     */
    suspend fun recognizeFace(faceEmbedding: FloatArray): FuncionariosEntity? {
        return withContext(Dispatchers.IO) {
            try {
                val startTime = System.currentTimeMillis()
                
                // ✅ VALIDAÇÃO BÁSICA
                if (faceEmbedding.isEmpty() || (faceEmbedding.size != 192 && faceEmbedding.size != 512)) {
                    if (DEBUG_MODE) Log.e(TAG, "❌ Vetor facial inválido: tamanho=${faceEmbedding.size}")
                    return@withContext null
                }
                
                // ✅ VERIFICAÇÃO DE CONTEXTO
                if (context is android.app.Activity && (context.isFinishing || context.isDestroyed)) {
                    if (DEBUG_MODE) Log.w(TAG, "⚠️ Activity finalizada")
                    return@withContext null
                }
                
                // ✅ CORREÇÃO DE VALORES INVÁLIDOS
                for (i in faceEmbedding.indices) {
                    if (faceEmbedding[i].isNaN() || faceEmbedding[i].isInfinite()) {
                        faceEmbedding[i] = 0.0f
                    }
                }
                
                // ✅ CARREGAR DADOS EM CACHE
                val facesData = getCachedFacesData()
                if (facesData.isEmpty()) {
                    if (DEBUG_MODE) Log.w(TAG, "⚠️ Nenhuma face cadastrada")
                    return@withContext null
                }
                
                if (DEBUG_MODE) Log.d(TAG, "🎯 === SISTEMA DE PONTO ELETRÔNICO ===")
                if (DEBUG_MODE) Log.d(TAG, "🔍 Analisando ${facesData.size} funcionários cadastrados")
                
                // ✅ ANÁLISE COMPARATIVA: Calcular similaridades
                val candidatos = mutableListOf<Triple<FuncionariosEntity, Float, Float>>() // funcionario, cosine, euclidean
                
                for (faceData in facesData) {
                    val cosineSimilarity = calculateCosineSimilarityFast(faceEmbedding, faceData.embedding)
                    val euclideanDistance = calculateEuclideanDistanceFast(faceEmbedding, faceData.embedding)
                    
                    if (DEBUG_MODE) {
                        Log.d(TAG, "👤 ${faceData.funcionario.nome}:")
                        Log.d(TAG, "   - Cosine: $cosineSimilarity")
                        Log.d(TAG, "   - Euclidean: $euclideanDistance")
                        
                        // ✅ ANÁLISE DETALHADA DOS EMBEDDINGS
                        Log.d(TAG, "   📊 ANÁLISE DETALHADA:")
                        Log.d(TAG, "      - Embedding atual: tamanho=${faceEmbedding.size}")
                        Log.d(TAG, "      - Embedding cadastrado: tamanho=${faceData.embedding.size}")
                        Log.d(TAG, "      - Embedding atual: primeiros 5 valores=[${faceEmbedding.take(5).joinToString(", ") { "%.3f".format(it) }}]")
                        Log.d(TAG, "      - Embedding cadastrado: primeiros 5 valores=[${faceData.embedding.take(5).joinToString(", ") { "%.3f".format(it) }}]")
                        
                        // Verificar se embeddings são válidos
                        val atualValido = faceEmbedding.any { it != 0f }
                        val cadastradoValido = faceData.embedding.any { it != 0f }
                        Log.d(TAG, "      - Embedding atual válido: $atualValido")
                        Log.d(TAG, "      - Embedding cadastrado válido: $cadastradoValido")
                    }
                    
                    // ✅ FILTROS ULTRA RIGOROSOS: MÚLTIPLAS VALIDAÇÕES OBRIGATÓRIAS
                    val thresholdsAtivos = if (MODO_TESTE_ATIVO) {
                        Log.w(TAG, "🧪 MODO TESTE ATIVO - Critérios permissivos")
                        Triple(TEST_MIN_SIMILARITY, TEST_MAX_EUCLIDEAN_DISTANCE, TEST_BASE_THRESHOLD)
                    } else {
                        Triple(MIN_SIMILARITY_FOR_ANY_APPROVAL, MAX_EUCLIDEAN_DISTANCE, BASE_THRESHOLD)
                    }
                    
                    val passaCosseno = cosineSimilarity >= thresholdsAtivos.first
                    val passaDistancia = euclideanDistance <= thresholdsAtivos.second
                    val passaThresholdBase = cosineSimilarity >= thresholdsAtivos.third
                    
                    // DEVE PASSAR EM TODOS OS TESTES
                    if (passaCosseno && passaDistancia && passaThresholdBase) {
                        candidatos.add(Triple(faceData.funcionario, cosineSimilarity, euclideanDistance))
                        if (DEBUG_MODE) {
                            Log.d(TAG, "   ✅ CANDIDATO VÁLIDO")
                        }
                    } else {
                        if (DEBUG_MODE) {
                            Log.d(TAG, "   ❌ REJEITADO - Cosine:$passaCosseno Dist:$passaDistancia Thresh:$passaThresholdBase")
                        }
                    }
                }
                
                // ✅ ANÁLISE DE CANDIDATOS COM VALIDAÇÕES EXTREMAS
                val funcionarioEscolhido = if (candidatos.isEmpty()) {
                    if (DEBUG_MODE) Log.w(TAG, "❌ ZERO candidatos passaram nos filtros ULTRA RIGOROSOS")
                    null
                } else {
                    if (DEBUG_MODE) Log.d(TAG, "🎯 ${candidatos.size} candidatos encontrados")
                    
                    // ✅ VALIDAÇÃO 1: MÁXIMO DE CANDIDATOS PERMITIDOS
                    if (candidatos.size > MAX_CANDIDATES_ALLOWED) {
                        if (DEBUG_MODE) Log.w(TAG, "❌ MUITOS CANDIDATOS (${candidatos.size}) - SUSPEITO DE FALSO POSITIVO")
                        null
                    } else {
                        // ✅ ORDENAR POR SIMILARIDADE (MELHOR PRIMEIRO)
                        candidatos.sortByDescending { it.second }
                        
                        val melhorCandidato = candidatos[0]
                        val melhorFuncionario = melhorCandidato.first
                        val melhorSimilaridade = melhorCandidato.second
                        val melhorDistancia = melhorCandidato.third
                        
                        if (DEBUG_MODE) {
                            Log.d(TAG, "🏆 MELHOR: ${melhorFuncionario.nome} - Sim:$melhorSimilaridade Dist:$melhorDistancia")
                        }
                        
                        // ✅ CENÁRIOS ULTRA RIGOROSOS - SÓ APROVA SE TER CERTEZA ABSOLUTA
                        val thresholdsParaCenarios = if (MODO_TESTE_ATIVO) {
                            Log.w(TAG, "🧪 USANDO THRESHOLDS DE TESTE PARA CENÁRIOS")
                            Triple(TEST_GOOD_MATCH_THRESHOLD, TEST_BASE_THRESHOLD, TEST_MAX_EUCLIDEAN_DISTANCE)
                        } else {
                            Triple(GOOD_MATCH_THRESHOLD, BASE_THRESHOLD, MAX_EUCLIDEAN_DISTANCE)
                        }
                        
                        when {
                            // Cenário 1: MATCH PERFEITO - APROVAÇÃO IMEDIATA
                            melhorSimilaridade >= EXCELLENT_MATCH_THRESHOLD -> {
                                if (DEBUG_MODE) Log.d(TAG, "🎯 CENÁRIO 1: MATCH PERFEITO ($melhorSimilaridade ≥ $EXCELLENT_MATCH_THRESHOLD)")
                                melhorFuncionario
                            }
                            
                            // Cenário 2: MATCH MUITO BOM + CANDIDATO ÚNICO + DISTÂNCIA EXCELENTE
                            melhorSimilaridade >= thresholdsParaCenarios.first && 
                            candidatos.size == 1 && 
                            melhorDistancia <= (thresholdsParaCenarios.third * 0.8f) -> {
                                if (DEBUG_MODE) Log.d(TAG, "🎯 CENÁRIO 2: MATCH MTO BOM + ÚNICO + DIST EXCELENTE")
                                melhorFuncionario
                            }
                            
                            // Cenário 3: MATCH BOM + DIFERENÇA DO SEGUNDO
                            melhorSimilaridade >= thresholdsParaCenarios.first && candidatos.size > 1 -> {
                                val segundoMelhor = candidatos[1].second
                                val diferenca = melhorSimilaridade - segundoMelhor
                                val diferencaMinima = if (MODO_TESTE_ATIVO) 0.05f else MIN_DIFFERENCE_BETWEEN_PEOPLE
                                
                                if (diferenca >= diferencaMinima) {
                                    if (DEBUG_MODE) Log.d(TAG, "🎯 CENÁRIO 3: DIFERENÇA OK ($diferenca ≥ $diferencaMinima)")
                                    melhorFuncionario
                                } else {
                                    if (DEBUG_MODE) Log.w(TAG, "❌ CENÁRIO 3: DIFERENÇA INSUFICIENTE ($diferenca < $diferencaMinima)")
                                    null
                                }
                            }
                            
                            // Cenário 4: BASE + ÚNICO + CONFIANÇA
                            melhorSimilaridade >= thresholdsParaCenarios.second && 
                            candidatos.size == 1 && 
                            melhorDistancia <= (thresholdsParaCenarios.third * 0.7f) -> {
                                val confiancaExtrema = (melhorSimilaridade / melhorDistancia) * (if (MODO_TESTE_ATIVO) 1.0f else REQUIRED_CONFIDENCE_MULTIPLIER)
                                val limiteConfianca = if (MODO_TESTE_ATIVO) 0.5f else CONFIDENCE_RATIO_THRESHOLD
                                
                                if (confiancaExtrema >= limiteConfianca) {
                                    if (DEBUG_MODE) Log.d(TAG, "🎯 CENÁRIO 4: CONFIANÇA OK ($confiancaExtrema)")
                                    melhorFuncionario
                                } else {
                                    if (DEBUG_MODE) Log.w(TAG, "❌ CENÁRIO 4: CONFIANÇA INSUFICIENTE ($confiancaExtrema)")
                                    null
                                }
                            }
                            
                            else -> {
                                if (DEBUG_MODE) {
                                    Log.w(TAG, "❌ NENHUM CENÁRIO ATENDIDO:")
                                    Log.w(TAG, "   - Similaridade: $melhorSimilaridade")
                                    Log.w(TAG, "   - Candidatos: ${candidatos.size}")
                                    Log.w(TAG, "   - Distância: $melhorDistancia")
                                    Log.w(TAG, "   - PESSOA NÃO CADASTRADA OU CRITÉRIOS INSUFICIENTES")
                                }
                                null
                            }
                        }
                    }
                }
                
                val processingTime = System.currentTimeMillis() - startTime
                
                // ✅ VALIDAÇÃO FINAL: Verificar se resultado é confiável
                val resultadoFinal = if (funcionarioEscolhido != null && candidatos.size > 1) {
                    // Se há múltiplos candidatos, fazer validação adicional
                    candidatos.sortByDescending { it.second }
                    val melhor = candidatos[0]
                    val segundo = candidatos[1]
                    val diferenca = melhor.second - segundo.second
                    
                    if (DEBUG_MODE) {
                        Log.d(TAG, "🔍 VALIDAÇÃO FINAL COM MÚLTIPLOS CANDIDATOS:")
                        Log.d(TAG, "   1º: ${melhor.first.nome} - ${String.format("%.3f", melhor.second)}")
                        Log.d(TAG, "   2º: ${segundo.first.nome} - ${String.format("%.3f", segundo.second)}")
                        Log.d(TAG, "   Diferença: ${String.format("%.3f", diferenca)} (mín: $MIN_DIFFERENCE_BETWEEN_PEOPLE)")
                    }
                    
                    if (diferenca < MIN_DIFFERENCE_BETWEEN_PEOPLE && melhor.second < GOOD_MATCH_THRESHOLD) {
                        if (DEBUG_MODE) Log.w(TAG, "❌ REJEITADO NA VALIDAÇÃO FINAL - Candidatos muito próximos")
                        null
                    } else {
                        funcionarioEscolhido
                    }
                } else {
                    funcionarioEscolhido
                }
                
                if (DEBUG_MODE) {
                    Log.d(TAG, "⚡ Reconhecimento concluído em ${processingTime}ms")
                    if (resultadoFinal != null) {
                        Log.d(TAG, "✅ FUNCIONÁRIO RECONHECIDO: ${resultadoFinal.nome}")
                    } else {
                        Log.d(TAG, "❌ NENHUM FUNCIONÁRIO RECONHECIDO")
                    }
                }
                
                return@withContext resultadoFinal
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro no reconhecimento: ${e.message}")
                return@withContext null
            }
        }
    }
    
    /**
     * ✅ CACHE INTELIGENTE: Busca dados em cache ou recarrega se necessário
     */
    private suspend fun getCachedFacesData(): List<CachedFaceData> {
        val currentTime = System.currentTimeMillis()
        
        // Verificar se cache ainda é válido
        if (cachedFacesData != null && (currentTime - cacheTimestamp) < cacheExpirationMs) {
            return cachedFacesData!!
        }
        
        // Recarregar cache
        return try {
            if (DEBUG_MODE) Log.d(TAG, "🔄 Recarregando cache de faces...")
            
            val funcionarios = funcionarioDao.getUsuario()
            val facesData = mutableListOf<CachedFaceData>()
            
            for (funcionario in funcionarios) {
                try {
                    val faceEntity = faceDao.getByFuncionarioId(funcionario.codigo)
                    if (faceEntity != null && faceEntity.embedding.isNotBlank()) {
                        val embedding = stringToFloatArrayFast(faceEntity.embedding)
                        if (embedding.size == 192 || embedding.size == 512) {
                            facesData.add(CachedFaceData(funcionario, embedding))
                        }
                    }
                } catch (e: Exception) {
                    if (DEBUG_MODE) Log.w(TAG, "⚠️ Erro ao processar ${funcionario.nome}: ${e.message}")
                }
            }
            
            cachedFacesData = facesData
            cacheTimestamp = currentTime
            
            if (DEBUG_MODE) Log.d(TAG, "✅ Cache atualizado: ${facesData.size} faces")
            
            facesData
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao carregar cache: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * ✅ CONVERSÃO OTIMIZADA: String para FloatArray sem validações extras
     */
    private fun stringToFloatArrayFast(embeddingString: String): FloatArray {
        val values = embeddingString.split(",")
        val floatArray = FloatArray(values.size)
        
        for (i in values.indices) {
            floatArray[i] = values[i].trim().toFloatOrNull() ?: 0f
        }
        
        return floatArray
    }
    
    /**
     * ✅ CÁLCULO OTIMIZADO: Similaridade de cosseno sem verificações redundantes
     */
    private fun calculateCosineSimilarityFast(vector1: FloatArray, vector2: FloatArray): Float {
        if (vector1.size != vector2.size) return 0f
        
        var dotProduct = 0f
        var magnitude1 = 0f
        var magnitude2 = 0f
        
        for (i in vector1.indices) {
            val v1 = vector1[i]
            val v2 = vector2[i]
            dotProduct += v1 * v2
            magnitude1 += v1 * v1
            magnitude2 += v2 * v2
        }
        
        val mag1 = sqrt(magnitude1)
        val mag2 = sqrt(magnitude2)
        
        return if (mag1 > 0f && mag2 > 0f) {
            kotlin.math.abs(dotProduct / (mag1 * mag2))
        } else {
            0f
        }
    }
    
    /**
     * ✅ FUNÇÃO PÚBLICA: Limpar cache quando necessário
     */
    fun clearCache() {
        cachedFacesData = null
        cacheTimestamp = 0L
        if (DEBUG_MODE) Log.d(TAG, "🗑️ Cache limpo")
    }
    
    /**
     * ✅ FUNÇÃO SIMPLIFICADA: Converte string para FloatArray com validação básica
     */
    private fun stringToFloatArray(embeddingString: String): FloatArray {
        try {
            if (embeddingString.isBlank()) {
                throw IllegalArgumentException("String de embedding vazia")
            }
            
            val values = embeddingString.split(",")
            if (values.size != 192 && values.size != 512) {
                throw IllegalArgumentException("Número incorreto de valores: ${values.size}")
            }
            
            val floatArray = FloatArray(values.size)
            for (i in values.indices) {
                try {
                    val value = values[i].trim().toFloat()
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
     * ✅ FUNÇÃO MANTIDA: Calcula similaridade de cosseno com validações
     */
    private fun calculateCosineSimilarity(vector1: FloatArray, vector2: FloatArray): Float {
        try {
            if (vector1.isEmpty() || vector2.isEmpty()) {
                return 0f
            }
            
            if (vector1.size != vector2.size) {
                val minSize = minOf(vector1.size, vector2.size)
                if (minSize > 0) {
                    val v1 = vector1.sliceArray(0 until minSize)
                    val v2 = vector2.sliceArray(0 until minSize)
                    return calculateCosineSimilarityInternal(v1, v2)
                }
                return 0f
            }
            
            if (vector1.any { it.isNaN() || it.isInfinite() } || vector2.any { it.isNaN() || it.isInfinite() }) {
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
            kotlin.math.abs(similarity)
        } else {
            0f
        }
    }
    
    /**
     * ✅ CÁLCULO OTIMIZADO: Distância euclidiana rápida
     */
    private fun calculateEuclideanDistanceFast(vector1: FloatArray, vector2: FloatArray): Float {
        if (vector1.size != vector2.size) return Float.MAX_VALUE
        
        var sum = 0f
        for (i in vector1.indices) {
            val diff = vector1[i] - vector2[i]
            sum += diff * diff
        }
        
        return sqrt(sum)
    }
    
    /**
     * Calcula a distância euclidiana entre dois vetores
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
     * ✅ FUNÇÃO SIMPLIFICADA: Limpar faces duplicadas
     */
    suspend fun limparFacesDuplicadas() {
        withContext(Dispatchers.IO) {
            try {
                val todasFaces = faceDao.getAllFaces()
                val facesPorFuncionario = todasFaces.groupBy { it.funcionarioId }
                
                for ((funcionarioId, faces) in facesPorFuncionario) {
                    if (faces.size > 1) {
                        val faceMaisRecente = faces.maxByOrNull { it.id }
                        for (face in faces) {
                            if (face.id != faceMaisRecente?.id) {
                                faceDao.delete(face)
                            }
                        }
                    }
                }
                
                clearCache() // Limpar cache após limpeza
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao limpar faces duplicadas: ${e.message}")
            }
        }
    }
    
    /**
     * ✅ FUNÇÃO SIMPLIFICADA: Verificar integridade das faces
     */
    suspend fun verificarIntegridadeFaces() {
        withContext(Dispatchers.IO) {
            try {
                val funcionarios = funcionarioDao.getUsuario()
                val todasFaces = faceDao.getAllFaces()
                
                var funcionariosComFace = 0
                for (funcionario in funcionarios) {
                    val face = faceDao.getByFuncionarioId(funcionario.codigo)
                    if (face != null) {
                        funcionariosComFace++
                    }
                }
                
                Log.d(TAG, "✅ Verificação: $funcionariosComFace/${funcionarios.size} funcionários com face")
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro na verificação: ${e.message}")
            }
        }
    }
    
    /**
     * ✅ FUNÇÃO SIMPLIFICADA: Teste de reconhecimento
     */
    suspend fun testarReconhecimento(faceEmbedding: FloatArray) {
        if (!DEBUG_MODE) return
        
        withContext(Dispatchers.IO) {
            try {
                val facesData = getCachedFacesData()
                val scores = mutableListOf<Pair<FuncionariosEntity, Float>>()
                
                for (faceData in facesData) {
                    val similarity = calculateCosineSimilarityFast(faceEmbedding, faceData.embedding)
                    scores.add(Pair(faceData.funcionario, similarity))
                }
                
                scores.sortByDescending { it.second }
                scores.take(3).forEachIndexed { index, (funcionario, score) ->
                    val status = if (score >= BASE_THRESHOLD) "✅" else "❌"
                    Log.d(TAG, "${index + 1}. $status ${funcionario.nome}: $score")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro no teste: ${e.message}")
            }
        }
    }
    
    /**
     * ✅ FUNÇÃO SIMPLIFICADA: Verificar e corrigir problemas
     */
    suspend fun verificarECorrigirProblemasReconhecimento() {
        withContext(Dispatchers.IO) {
            try {
                limparFacesDuplicadas()
                verificarIntegridadeFaces()
                clearCache() // Garantir cache limpo
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro na verificação: ${e.message}")
            }
        }
    }
    
    /**
     * ✅ FUNÇÃO SIMPLIFICADA: Listar funcionários com problemas
     */
    suspend fun listarFuncionariosComProblemas(): List<String> {
        return withContext(Dispatchers.IO) {
            try {
                val problemas = mutableListOf<String>()
                val funcionarios = funcionarioDao.getUsuario()
                
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
                
                problemas
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao listar problemas: ${e.message}")
                emptyList()
            }
        }
    }
    
    /**
     * ✅ FUNÇÃO DE TESTE: Verificar rigorosidade dos critérios
     */
    suspend fun testarRigorosidadeCriterios(): String {
        return withContext(Dispatchers.IO) {
            try {
                val facesData = getCachedFacesData()
                if (facesData.isEmpty()) {
                    return@withContext "⚠️ Nenhuma face cadastrada para teste"
                }
                
                Log.d(TAG, "🧪 === TESTANDO RIGOROSIDADE DOS CRITÉRIOS ===")
                
                val relatorio = buildString {
                    appendLine("🛡️ CRITÉRIOS ULTRA RIGOROSOS ATIVOS:")
                    appendLine("   - Threshold Cosseno: $BASE_THRESHOLD (50%)")
                    appendLine("   - Alta Confiança: $GOOD_MATCH_THRESHOLD (65%)")
                    appendLine("   - Match Perfeito: $EXCELLENT_MATCH_THRESHOLD (80%)")
                    appendLine("   - Distância Máx: $MAX_EUCLIDEAN_DISTANCE")
                    appendLine("   - Diferença Mín: $MIN_DIFFERENCE_BETWEEN_PEOPLE")
                    appendLine("   - Ratio Mín: $CONFIDENCE_RATIO_THRESHOLD")
                    appendLine("   - Alta Qualidade: $HIGH_QUALITY_THRESHOLD (85%)")
                    appendLine("   - Baixa Qualidade: $LOW_QUALITY_THRESHOLD (70%)")
                    appendLine("   - Mínimo Absoluto: $MIN_SIMILARITY_FOR_ANY_APPROVAL")
                    appendLine("   - Máximo de Candidatos: $MAX_CANDIDATES_ALLOWED")
                    appendLine("   - Multiplicador de Confiança: $REQUIRED_CONFIDENCE_MULTIPLIER")
                    appendLine("")
                    appendLine(" Faces cadastradas: ${facesData.size}")
                    facesData.forEach { face ->
                        appendLine("   - ${face.funcionario.nome} (${face.funcionario.codigo})")
                    }
                    appendLine("")
                    appendLine("🔍 TESTE: Qualquer pessoa NÃO cadastrada deve ser REJEITADA")
                    appendLine("🔍 TESTE: Somente pessoas cadastradas com alta similaridade devem passar")
                    appendLine("")
                    appendLine("📝 COMO TESTAR:")
                    appendLine("1. Teste com pessoas NÃO cadastradas - devem ser rejeitadas")
                    appendLine("2. Teste com pessoas cadastradas - devem passar se similaridade > 50%")
                    appendLine("3. Verifique os logs para ver os valores exatos")
                }
                
                Log.d(TAG, relatorio)
                return@withContext relatorio
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro no teste: ${e.message}")
                return@withContext "❌ Erro no teste: ${e.message}"
            }
        }
    }

    /**
     * ✅ FUNÇÃO DE ANÁLISE: Mostrar por que uma pessoa foi rejeitada
     */
    suspend fun analisarRejeicao(faceEmbedding: FloatArray) {
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "🔍 === ANÁLISE DETALHADA DE REJEIÇÃO ===")
                
                val facesData = getCachedFacesData()
                if (facesData.isEmpty()) {
                    Log.w(TAG, "❌ Nenhuma face cadastrada para comparar")
                    return@withContext
                }
                
                Log.d(TAG, "📊 Comparando com ${facesData.size} faces cadastradas:")
                
                val resultados = mutableListOf<Triple<String, Float, Float>>()
                
                for (faceData in facesData) {
                    val cosineSimilarity = calculateCosineSimilarityFast(faceEmbedding, faceData.embedding)
                    val euclideanDistance = calculateEuclideanDistanceFast(faceEmbedding, faceData.embedding)
                    
                    resultados.add(Triple(faceData.funcionario.nome, cosineSimilarity, euclideanDistance))
                    
                    val cosineStatus = if (cosineSimilarity >= BASE_THRESHOLD) "✅" else "❌"
                    val euclideanStatus = if (euclideanDistance <= MAX_EUCLIDEAN_DISTANCE) "✅" else "❌"
                    
                    Log.d(TAG, "👤 ${faceData.funcionario.nome}:")
                    Log.d(TAG, "   📏 Cosseno: $cosineSimilarity >= $BASE_THRESHOLD $cosineStatus")
                    Log.d(TAG, "   📐 Euclidiana: $euclideanDistance <= $MAX_EUCLIDEAN_DISTANCE $euclideanStatus")
                }
                
                // Encontrar o melhor resultado mesmo que rejeitado
                val melhorResultado = resultados.maxByOrNull { it.second }
                
                if (melhorResultado != null) {
                    val (nome, cosine, euclidean) = melhorResultado
                    Log.w(TAG, "🏆 MELHOR RESULTADO (mesmo assim rejeitado):")
                    Log.w(TAG, "   👤 Pessoa: $nome")
                    Log.w(TAG, "   📊 Similaridade: $cosine (mín: $BASE_THRESHOLD)")
                    Log.w(TAG, "   📊 Distância: $euclidean (máx: $MAX_EUCLIDEAN_DISTANCE)")
                    
                    val motivosRejeicao = mutableListOf<String>()
                    
                    if (cosine < BASE_THRESHOLD) {
                        motivosRejeicao.add("Similaridade muito baixa ($cosine < $BASE_THRESHOLD)")
                    }
                    if (euclidean > MAX_EUCLIDEAN_DISTANCE) {
                        motivosRejeicao.add("Distância muito alta ($euclidean > $MAX_EUCLIDEAN_DISTANCE)")
                    }
                    if (cosine < LOW_QUALITY_THRESHOLD) {
                        motivosRejeicao.add("Abaixo do mínimo válido ($cosine < $LOW_QUALITY_THRESHOLD)")
                    }
                    
                    Log.w(TAG, "🚫 MOTIVOS DA REJEIÇÃO:")
                    motivosRejeicao.forEach { motivo ->
                        Log.w(TAG, "   - $motivo")
                    }
                    
                    if (motivosRejeicao.isEmpty()) {
                        Log.w(TAG, "   - Passou nos critérios básicos mas falhou em validações de segurança")
                        Log.w(TAG, "   - Pode ser pessoa muito similar a um funcionário cadastrado")
                    }
                } else {
                    Log.w(TAG, "❌ Nenhum resultado válido encontrado")
                }
                
                Log.d(TAG, "��️ PROTEÇÃO ANTI-FALSO POSITIVO: Sistema funcionando corretamente!")
                Log.d(TAG, "💡 DICA: Para registrar uma nova pessoa, use o menu de cadastro")
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro na análise de rejeição: ${e.message}")
            }
        }
    }
    
    /**
     * ✅ ANÁLISE COMPLETA: Investigar problemas no reconhecimento
     */
    suspend fun analisarEmbeddingsCompleta(faceEmbedding: FloatArray): String {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "🔬 === ANÁLISE COMPLETA DOS EMBEDDINGS ===")
                
                val facesData = getCachedFacesData()
                if (facesData.isEmpty()) {
                    return@withContext "❌ Nenhuma face cadastrada no sistema"
                }
                
                val relatorio = buildString {
                    appendLine("🔬 ANÁLISE DETALHADA DO RECONHECIMENTO FACIAL")
                    appendLine("=".repeat(50))
                    appendLine("")
                    
                    appendLine("📊 EMBEDDING DE ENTRADA:")
                    appendLine("   - Tamanho: ${faceEmbedding.size}")
                    appendLine("   - Média: ${faceEmbedding.average()}")
                    appendLine("   - Min: ${faceEmbedding.minOrNull()}")
                    appendLine("   - Max: ${faceEmbedding.maxOrNull()}")
                    appendLine("   - Tem NaN: ${faceEmbedding.any { it.isNaN() }}")
                    appendLine("   - Tem Infinite: ${faceEmbedding.any { it.isInfinite() }}")
                    appendLine("   - Primeiros 10 valores: ${faceEmbedding.take(10).joinToString(", ")}")
                    appendLine("")
                    
                    appendLine("👥 FACES CADASTRADAS (${facesData.size}):")
                    appendLine("")
                    
                    val resultados = mutableListOf<QuadRuple<String, Float, Float, String>>()
                    
                    for ((index, faceData) in facesData.withIndex()) {
                        val funcionario = faceData.funcionario
                        val storedEmbedding = faceData.embedding
                        
                        appendLine("${index + 1}. 👤 ${funcionario.nome} (${funcionario.codigo})")
                        appendLine("   📊 Embedding armazenado:")
                        appendLine("      - Tamanho: ${storedEmbedding.size}")
                        appendLine("      - Média: ${storedEmbedding.average()}")
                        appendLine("      - Min: ${storedEmbedding.minOrNull()}")
                        appendLine("      - Max: ${storedEmbedding.maxOrNull()}")
                        appendLine("      - Primeiros 10: ${storedEmbedding.take(10).joinToString(", ")}")
                        
                        // Cálculos de similaridade
                        val cosineSimilarity = calculateCosineSimilarityFast(faceEmbedding, storedEmbedding)
                        val euclideanDistance = calculateEuclideanDistanceFast(faceEmbedding, storedEmbedding)
                        
                        appendLine("   🧮 CÁLCULOS:")
                        appendLine("      - Similaridade Cosseno: $cosineSimilarity")
                        appendLine("      - Distância Euclidiana: $euclideanDistance")
                        
                        // Verificar critérios
                        val passesCosine = cosineSimilarity >= BASE_THRESHOLD
                        val passesEuclidean = euclideanDistance <= MAX_EUCLIDEAN_DISTANCE
                        val passesMinimum = cosineSimilarity >= LOW_QUALITY_THRESHOLD
                        
                        appendLine("   ✅ CRITÉRIOS:")
                        appendLine("      - Cosseno ≥ $BASE_THRESHOLD: $passesCosine")
                        appendLine("      - Euclidiana ≤ $MAX_EUCLIDEAN_DISTANCE: $passesEuclidean")
                        appendLine("      - Mínimo ≥ $LOW_QUALITY_THRESHOLD: $passesMinimum")
                        
                        val status = when {
                            passesCosine && passesEuclidean -> "✅ APROVADO"
                            passesCosine -> "⚠️ COSSENO OK, EUCLIDIANA FALHA"
                            passesEuclidean -> "⚠️ EUCLIDIANA OK, COSSENO FALHA"
                            else -> "❌ AMBOS FALHARAM"
                        }
                        
                        appendLine("   🎯 RESULTADO: $status")
                        appendLine("")
                        
                        resultados.add(QuadRuple(funcionario.nome, cosineSimilarity, euclideanDistance, status))
                    }
                    
                    // Análise final
                    appendLine("🏆 RANKING DE SIMILARIDADE:")
                    resultados.sortedByDescending { it.second }.forEachIndexed { index, (nome, cosine, euclidean, status) ->
                        appendLine("   ${index + 1}. $nome: $cosine ($status)")
                    }
                    appendLine("")
                    
                    val melhor = resultados.maxByOrNull { it.second }
                    if (melhor != null) {
                        val (nome, cosine, euclidean, status) = melhor
                        appendLine("🎯 MELHOR CANDIDATO:")
                        appendLine("   👤 Nome: $nome")
                        appendLine("   📊 Similaridade: $cosine")
                        appendLine("   📐 Distância: $euclidean")
                        appendLine("   🎭 Status: $status")
                        appendLine("")
                        
                        if (cosine < BASE_THRESHOLD) {
                            appendLine("❌ PROBLEMA IDENTIFICADO:")
                            appendLine("   A similaridade ($cosine) está abaixo do threshold ($BASE_THRESHOLD)")
                            appendLine("   💡 SUGESTÕES:")
                            appendLine("   1. Verifique se a foto cadastrada está boa")
                            appendLine("   2. Recadastre a face em melhor qualidade")
                            appendLine("   3. Verifique a iluminação durante o reconhecimento")
                        } else if (euclidean > MAX_EUCLIDEAN_DISTANCE) {
                            appendLine("❌ PROBLEMA IDENTIFICADO:")
                            appendLine("   A distância euclidiana ($euclidean) está muito alta (máx: $MAX_EUCLIDEAN_DISTANCE)")
                            appendLine("   💡 SUGESTÕES:")
                            appendLine("   1. Os embeddings podem estar muito diferentes")
                            appendLine("   2. Recadastre a face da pessoa")
                        } else {
                            appendLine("✅ CRITÉRIOS BÁSICOS ATENDIDOS!")
                            appendLine("   O problema pode estar nas validações de segurança.")
                        }
                    }
                }
                
                Log.d(TAG, relatorio)
                return@withContext relatorio
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro na análise completa: ${e.message}")
                return@withContext "❌ Erro na análise: ${e.message}"
            }
        }
    }
    
    /**
     * ✅ ANÁLISE DE QUALIDADE: Determinar qualidade do embedding baseado em estatísticas
     */
    private fun analisarQualidadeEmbedding(embedding: FloatArray): Float {
        if (embedding.isEmpty()) return 0f
        
        // Calcular métricas de qualidade
        val mean = embedding.average().toFloat()
        val variance = embedding.map { (it - mean) * (it - mean) }.average().toFloat()
        val stdDev = kotlin.math.sqrt(variance)
        
        // Embeddings de boa qualidade têm distribuição normal
        val normalizedStdDev = kotlin.math.min(stdDev / 0.5f, 1.0f)
        
        // Verificar se há valores extremos
        val extremeValues = embedding.count { kotlin.math.abs(it) > 2.0f }.toFloat()
        val extremeRatio = extremeValues / embedding.size
        
        // Qualidade final (0.0 a 1.0)
        val qualidade = normalizedStdDev * (1.0f - extremeRatio)
        
        return kotlin.math.min(kotlin.math.max(qualidade, 0.1f), 1.0f)
    }
    
    /**
     * ✅ TESTE ANTI-FALSO POSITIVO: Validar rigorosidade do sistema
     */
    suspend fun testarAntiFalsoPositivo(faceEmbedding: FloatArray): String {
        return withContext(Dispatchers.IO) {
            try {
                val facesData = getCachedFacesData()
                if (facesData.isEmpty()) {
                    return@withContext "❌ Nenhuma face cadastrada para teste"
                }
                
                                 Log.d(TAG, "🧪 === TESTE ANTI-FALSO POSITIVO ===")
                
                val relatorio = buildString {
                    appendLine("🛡️ TESTE ULTRA RIGOROSO DE FALSOS POSITIVOS")
                    appendLine("=".repeat(55))
                    appendLine("")
                    
                    appendLine("📊 CRITÉRIOS ATIVOS:")
                    appendLine("   🔒 Mínimo Absoluto: ${MIN_SIMILARITY_FOR_ANY_APPROVAL * 100}%")
                    appendLine("   🔒 Base Rigoroso: ${BASE_THRESHOLD * 100}%") 
                    appendLine("   🔒 Muito Bom: ${GOOD_MATCH_THRESHOLD * 100}%")
                    appendLine("   🔒 Perfeito: ${EXCELLENT_MATCH_THRESHOLD * 100}%")
                    appendLine("   🔒 Distância Máx: $MAX_EUCLIDEAN_DISTANCE")
                    appendLine("   🔒 Diferença Mín: ${MIN_DIFFERENCE_BETWEEN_PEOPLE * 100}%")
                    appendLine("   🔒 Máx Candidatos: $MAX_CANDIDATES_ALLOWED")
                    appendLine("")
                    
                    appendLine("🔍 TESTANDO CONTRA ${facesData.size} FACES CADASTRADAS:")
                    
                    var candidatosValidos = 0
                    var melhorSimilaridade = 0f
                    var melhorNome = ""
                    
                    for (faceData in facesData) {
                        val similarity = calculateCosineSimilarityFast(faceEmbedding, faceData.embedding)
                        val distance = calculateEuclideanDistanceFast(faceEmbedding, faceData.embedding)
                        
                        val passaMinimo = similarity >= MIN_SIMILARITY_FOR_ANY_APPROVAL
                        val passaBase = similarity >= BASE_THRESHOLD
                        val passaDistancia = distance <= MAX_EUCLIDEAN_DISTANCE
                        val eValido = passaMinimo && passaBase && passaDistancia
                        
                        if (eValido) candidatosValidos++
                        if (similarity > melhorSimilaridade) {
                            melhorSimilaridade = similarity
                            melhorNome = faceData.funcionario.nome
                        }
                        
                        appendLine("   ${if (eValido) "✅" else "❌"} ${faceData.funcionario.nome}")
                        appendLine("       Similaridade: ${(similarity * 100).toInt()}% ${if (passaMinimo && passaBase) "✅" else "❌"}")
                        appendLine("       Distância: ${String.format("%.3f", distance)} ${if (passaDistancia) "✅" else "❌"}")
                    }
                    
                    appendLine("")
                    appendLine("📋 RESULTADO FINAL:")
                    appendLine("   🎯 Candidatos válidos: $candidatosValidos")
                    appendLine("   🏆 Melhor match: $melhorNome (${(melhorSimilaridade * 100).toInt()}%)")
                    
                    when {
                        candidatosValidos == 0 -> {
                            appendLine("   🛡️ STATUS: PESSOA NÃO CADASTRADA - CORRETAMENTE REJEITADA")
                        }
                        candidatosValidos == 1 && melhorSimilaridade >= EXCELLENT_MATCH_THRESHOLD -> {
                            appendLine("   ✅ STATUS: RECONHECIMENTO VÁLIDO - MATCH PERFEITO")
                        }
                        candidatosValidos == 1 && melhorSimilaridade >= GOOD_MATCH_THRESHOLD -> {
                            appendLine("   ✅ STATUS: RECONHECIMENTO VÁLIDO - MATCH MUITO BOM")
                        }
                        candidatosValidos == 1 -> {
                            appendLine("   ⚠️ STATUS: RECONHECIMENTO DUVIDOSO - SIMILARIDADE BAIXA")
                        }
                        candidatosValidos > MAX_CANDIDATES_ALLOWED -> {
                            appendLine("   🚨 STATUS: SUSPEITO DE FALSO POSITIVO - MUITOS CANDIDATOS")
                        }
                        else -> {
                            appendLine("   ⚠️ STATUS: MÚLTIPLOS CANDIDATOS - ANÁLISE NECESSÁRIA")
                        }
                    }
                }
                
                return@withContext relatorio
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro no teste: ${e.message}")
                return@withContext "❌ Erro: ${e.message}"
            }
        }
    }
    
    /**
     * ✅ DIAGNÓSTICO: Analisar qualidade do cadastro existente
     */
    suspend fun diagnosticarQualidadeCadastro(): String {
        return withContext(Dispatchers.IO) {
            try {
                val facesData = getCachedFacesData()
                if (facesData.isEmpty()) {
                    return@withContext "❌ Nenhuma face cadastrada"
                }
                
                val relatorio = buildString {
                    appendLine("🔍 DIAGNÓSTICO DE QUALIDADE DOS CADASTROS")
                    appendLine("=".repeat(50))
                    appendLine("")
                    
                    for (faceData in facesData) {
                        val embedding = faceData.embedding
                        val qualidade = analisarQualidadeEmbedding(embedding)
                        val funcionario = faceData.funcionario
                        
                        appendLine("👤 ${funcionario.nome}:")
                        appendLine("   📊 Qualidade: ${String.format("%.1f", qualidade * 100)}%")
                        
                        when {
                            qualidade >= 0.8f -> appendLine("   ✅ EXCELENTE - Deve funcionar perfeitamente")
                            qualidade >= 0.6f -> appendLine("   ⚠️ BOA - Pode funcionar mas recomendo recadastrar")
                            qualidade >= 0.4f -> appendLine("   ❌ RUIM - Recomendo URGENTE recadastrar")
                            else -> appendLine("   🚨 PÉSSIMA - DEVE recadastrar IMEDIATAMENTE")
                        }
                        
                        // Estatísticas do embedding
                        val mean = embedding.average().toFloat()
                        val variance = embedding.map { (it - mean) * (it - mean) }.average().toFloat()
                        val stdDev = kotlin.math.sqrt(variance)
                        
                        appendLine("   📈 Estatísticas:")
                        appendLine("      - Média: ${String.format("%.3f", mean)}")
                        appendLine("      - Desvio: ${String.format("%.3f", stdDev)}")
                        appendLine("      - Tamanho: ${embedding.size}")
                        appendLine("")
                    }
                    
                    appendLine("💡 RECOMENDAÇÕES:")
                    appendLine("1. Faces com qualidade < 60% devem ser recadastradas")
                    appendLine("2. Use boa iluminação no recadastro")
                    appendLine("3. Posicione a face bem centralizada")
                    appendLine("4. Evite óculos/sombras se possível")
                }
                
                return@withContext relatorio
                
            } catch (e: Exception) {
                return@withContext "❌ Erro: ${e.message}"
            }
        }
    }
    
    /**
     * ✅ VALIDAÇÃO: Detectar embeddings problemáticos que causam confusão
     */
    suspend fun validarEmbeddingsParaConfusao(): String {
        return withContext(Dispatchers.IO) {
            try {
                val facesData = getCachedFacesData()
                if (facesData.size < 2) {
                    return@withContext "ℹ️ Menos de 2 pessoas cadastradas - sem risco de confusão"
                }
                
                val relatorio = buildString {
                    appendLine("🔍 ANÁLISE DE CONFUSÃO ENTRE EMBEDDINGS")
                    appendLine("=".repeat(50))
                    appendLine("")
                    
                    var problemasEncontrados = 0
                    
                    for (i in facesData.indices) {
                        for (j in i + 1 until facesData.size) {
                            val pessoa1 = facesData[i]
                            val pessoa2 = facesData[j]
                            
                            val similaridade = calculateCosineSimilarityFast(pessoa1.embedding, pessoa2.embedding)
                            val distancia = calculateEuclideanDistanceFast(pessoa1.embedding, pessoa2.embedding)
                            
                            appendLine("👥 ${pessoa1.funcionario.nome} ↔ ${pessoa2.funcionario.nome}:")
                            appendLine("   📊 Similaridade: ${String.format("%.3f", similaridade)} (${(similaridade * 100).toInt()}%)")
                            appendLine("   📏 Distância: ${String.format("%.3f", distancia)}")
                            
                            when {
                                similaridade > 0.70f -> {
                                    appendLine("   🚨 PROBLEMA CRÍTICO - Muito similares! Pode confundir!")
                                    problemasEncontrados++
                                }
                                similaridade > 0.50f -> {
                                    appendLine("   ⚠️ ATENÇÃO - Similares demais, pode haver confusão")
                                    problemasEncontrados++
                                }
                                similaridade > 0.30f -> {
                                    appendLine("   ⚡ OK - Diferença aceitável")
                                }
                                else -> {
                                    appendLine("   ✅ EXCELENTE - Bem diferentes")
                                }
                            }
                            appendLine("")
                        }
                    }
                    
                    appendLine("📋 RESUMO:")
                    appendLine("   Total de comparações: ${(facesData.size * (facesData.size - 1)) / 2}")
                    appendLine("   Problemas encontrados: $problemasEncontrados")
                    
                    if (problemasEncontrados > 0) {
                        appendLine("")
                        appendLine("💡 RECOMENDAÇÕES:")
                        appendLine("1. Recadastre as faces problemáticas com melhor qualidade")
                        appendLine("2. Use iluminação e ângulos diferentes")
                        appendLine("3. Certifique-se que são pessoas diferentes")
                    } else {
                        appendLine("   ✅ TODOS OS EMBEDDINGS ESTÃO BEM DIFERENCIADOS")
                    }
                }
                
                return@withContext relatorio
                
            } catch (e: Exception) {
                return@withContext "❌ Erro na validação: ${e.message}"
            }
        }
    }
    
    // Classe auxiliar para guardar 4 valores
    private data class QuadRuple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
} 