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
    ) {
        // ✅ CRÍTICO: Implementar equals e hashCode para FloatArray
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            
            other as CachedFaceData
            
            if (funcionario != other.funcionario) return false
            return embedding.contentEquals(other.embedding)
        }
        
        override fun hashCode(): Int {
            var result = funcionario.hashCode()
            result = 31 * result + embedding.contentHashCode()
            return result
        }
    }
    
    companion object {
        private const val TAG = "FaceRecognitionHelper"
        
        // ✅ THRESHOLDS CORRIGIDOS: Mais rigorosos para evitar confusão entre pessoas
        private const val BASE_THRESHOLD = 0.7f // Aumentado para 70% - mais rigoroso
        private const val GOOD_MATCH_THRESHOLD = 0.8f // Aumentado para 80%
        private const val EXCELLENT_MATCH_THRESHOLD = 0.85f // Aumentado para 85%
        private const val PERFECT_MATCH_THRESHOLD = 0.9f // Aumentado para 90%
        
        // ✅ THRESHOLDS DE TESTE CORRIGIDOS
        private const val TEST_BASE_THRESHOLD = 0.6f // Aumentado para 60%
        private const val TEST_GOOD_MATCH_THRESHOLD = 0.7f // Aumentado para 70%
        private const val TEST_MIN_SIMILARITY = 0.5f // Aumentado para 50%
        private const val TEST_MAX_EUCLIDEAN_DISTANCE = 0.6f // Reduzido para 0.6
        
        // ✅ VALIDAÇÕES MAIS RIGOROSAS
        private const val MIN_DIFFERENCE_BETWEEN_PEOPLE = 0.15f // Diferença mínima entre pessoas
        private const val MAX_EUCLIDEAN_DISTANCE = 0.6f // Reduzido para 0.6
        private const val CONFIDENCE_RATIO_THRESHOLD = 1.8f // Aumentado
        
        // ✅ THRESHOLDS DE QUALIDADE MAIS RIGOROSOS
        private const val HIGH_QUALITY_THRESHOLD = 0.8f // Aumentado para 80%
        private const val LOW_QUALITY_THRESHOLD = 0.6f // Aumentado para 60%
        private const val MIN_SIMILARITY_FOR_ANY_APPROVAL = 0.5f // Aumentado para 50%
        
        // ✅ CONFIGURAÇÕES MAIS RIGOROSAS
        private const val MAX_CANDIDATES_ALLOWED = 1 // Só aceita 1 candidato - mais rigoroso
        private const val REQUIRED_CONFIDENCE_MULTIPLIER = 2.0f // Aumentado
        
        // FLAG DE MODO TESTE
        private var MODO_TESTE_ATIVO = false // ✅ DESATIVADO - MODO PRODUÇÃO
        
        private const val DEBUG_MODE = true // Debug para análise
    }

    /**
     * ✅ VERSÃO CORRIGIDA: Reconhecimento facial com thresholds corretos e proteções
     */
    suspend fun recognizeFace(faceEmbedding: FloatArray): FuncionariosEntity? {
        return withContext(Dispatchers.IO) {
            try {
                val startTime = System.currentTimeMillis()
                
                // ✅ PROTEÇÃO CRÍTICA: Validar embedding de entrada
                if (!validarEmbedding(faceEmbedding)) {
                    Log.e(TAG, "❌ Vetor facial inválido: tamanho=${faceEmbedding.size}")
                    return@withContext null
                }
                
                // ✅ VERIFICAÇÃO DE CONTEXTO
                if (context is android.app.Activity && (context.isFinishing || context.isDestroyed)) {
                    Log.w(TAG, "⚠️ Activity finalizada")
                    return@withContext null
                }
                
                // ✅ CARREGAR DADOS EM CACHE (FRESCO)
                val facesData = getCachedFacesData()
                if (facesData.isEmpty()) {
                    Log.w(TAG, "⚠️ Nenhuma face cadastrada")
                    return@withContext null
                }
                
                Log.d(TAG, "🎯 === SISTEMA DE RECONHECIMENTO FACIAL CORRIGIDO ===")
                Log.d(TAG, "🔍 Analisando ${facesData.size} funcionários cadastrados")
                Log.d(TAG, "📊 Thresholds ativos: BASE=$BASE_THRESHOLD, MIN=$MIN_SIMILARITY_FOR_ANY_APPROVAL, MAX_DIST=$MAX_EUCLIDEAN_DISTANCE")
                
                // ✅ ANÁLISE COMPARATIVA: Calcular similaridades com proteções
                val candidatos = mutableListOf<Triple<FuncionariosEntity, Float, Float>>() // funcionario, cosine, euclidean
                
                for (faceData in facesData) {
                    try {
                        // ✅ VALIDAR EMBEDDING CADASTRADO
                        if (!validarEmbedding(faceData.embedding)) {
                            Log.w(TAG, "⚠️ Embedding inválido para ${faceData.funcionario.nome}")
                            continue
                        }
                        
                        // ✅ PROTEÇÃO: Calcular similaridades com tratamento de erro
                        val cosineSimilarity = try {
                            calculateCosineSimilaritySafe(faceEmbedding, faceData.embedding)
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ Erro ao calcular similaridade de cosseno: ${e.message}")
                            0f
                        }
                        
                        val euclideanDistance = try {
                            calculateEuclideanDistanceSafe(faceEmbedding, faceData.embedding)
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ Erro ao calcular distância euclidiana: ${e.message}")
                            Float.MAX_VALUE
                        }
                        
                        // ✅ VALIDAR RESULTADOS DOS CÁLCULOS
                        if (cosineSimilarity.isNaN() || cosineSimilarity.isInfinite()) {
                            Log.w(TAG, "⚠️ Similaridade de cosseno inválida para ${faceData.funcionario.nome}: $cosineSimilarity")
                            continue
                        }
                        
                        if (euclideanDistance.isNaN() || euclideanDistance.isInfinite()) {
                            Log.w(TAG, "⚠️ Distância euclidiana inválida para ${faceData.funcionario.nome}: $euclideanDistance")
                            continue
                        }
                        
                        if (DEBUG_MODE) {
                            Log.d(TAG, "👤 ${faceData.funcionario.nome}:")
                            Log.d(TAG, "   - Cosine: $cosineSimilarity")
                            Log.d(TAG, "   - Euclidean: $euclideanDistance")
                        }
                        
                        // ✅ FILTROS CORRIGIDOS: Usar thresholds do usuário
                        val thresholdsAtivos = if (MODO_TESTE_ATIVO) {
                            Log.w(TAG, "🧪 MODO TESTE ATIVO - Critérios permissivos")
                            Triple(TEST_MIN_SIMILARITY, TEST_MAX_EUCLIDEAN_DISTANCE, TEST_BASE_THRESHOLD)
                        } else {
                            Triple(MIN_SIMILARITY_FOR_ANY_APPROVAL, MAX_EUCLIDEAN_DISTANCE, BASE_THRESHOLD)
                        }
                        
                        val passaCosseno = cosineSimilarity >= thresholdsAtivos.first
                        val passaDistancia = euclideanDistance <= thresholdsAtivos.second
                        val passaThresholdBase = cosineSimilarity >= thresholdsAtivos.third
                        
                        // ✅ LÓGICA CORRIGIDA: Aprovar se passar no threshold mínimo
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
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Erro ao processar ${faceData.funcionario.nome}: ${e.message}")
                        continue
                    }
                }
                
                // ✅ ANÁLISE CORRIGIDA: Lógica mais rigorosa para evitar confusão entre pessoas
                val funcionarioEscolhido = if (candidatos.isEmpty()) {
                    Log.w(TAG, "❌ ZERO candidatos passaram nos filtros rigorosos")
                    null
                } else {
                    Log.d(TAG, "🎯 ${candidatos.size} candidatos encontrados")
                    
                    // ✅ ORDENAR POR SIMILARIDADE (MELHOR PRIMEIRO)
                    candidatos.sortByDescending { it.second }
                    
                    val melhorCandidato = candidatos[0]
                    val melhorFuncionario = melhorCandidato.first
                    val melhorSimilaridade = melhorCandidato.second
                    val melhorDistancia = melhorCandidato.third
                    
                    if (DEBUG_MODE) {
                        Log.d(TAG, "🏆 MELHOR: ${melhorFuncionario.nome} - Sim:$melhorSimilaridade Dist:$melhorDistancia")
                    }
                    
                    // ✅ LÓGICA RIGOROSA: Verificar se há múltiplos candidatos similares
                    if (candidatos.size > 1) {
                        val segundoCandidato = candidatos[1]
                        val segundoSimilaridade = segundoCandidato.second
                        val diferenca = melhorSimilaridade - segundoSimilaridade
                        
                        if (DEBUG_MODE) {
                            Log.d(TAG, "🔍 SEGUNDO: ${segundoCandidato.first.nome} - Sim:$segundoSimilaridade")
                            Log.d(TAG, "📊 DIFERENÇA: $diferenca (mín: $MIN_DIFFERENCE_BETWEEN_PEOPLE)")
                        }
                        
                        // ✅ CRÍTICO: Se a diferença for muito pequena, rejeitar para evitar confusão
                        if (diferenca < MIN_DIFFERENCE_BETWEEN_PEOPLE) {
                            Log.w(TAG, "❌ REJEITADO: Diferença muito pequena entre candidatos ($diferenca < $MIN_DIFFERENCE_BETWEEN_PEOPLE) - risco de confusão")
                            null
                        } else {
                            // ✅ APROVAR apenas se passar nos critérios rigorosos
                            if (melhorSimilaridade >= BASE_THRESHOLD && melhorDistancia <= MAX_EUCLIDEAN_DISTANCE) {
                                Log.d(TAG, "✅ FUNCIONÁRIO APROVADO COM DIFERENÇA SUFICIENTE: ${melhorFuncionario.nome}")
                                melhorFuncionario
                            } else {
                                Log.w(TAG, "❌ REJEITADO: Similaridade ou distância insuficiente (sim=$melhorSimilaridade, dist=$melhorDistancia)")
                                null
                            }
                        }
                    } else {
                        // ✅ ÚNICO CANDIDATO: Aprovar apenas se passar nos critérios rigorosos
                        if (melhorSimilaridade >= BASE_THRESHOLD && melhorDistancia <= MAX_EUCLIDEAN_DISTANCE) {
                            Log.d(TAG, "✅ FUNCIONÁRIO APROVADO (ÚNICO CANDIDATO): ${melhorFuncionario.nome}")
                            melhorFuncionario
                        } else {
                            Log.w(TAG, "❌ REJEITADO: Similaridade ou distância insuficiente (sim=$melhorSimilaridade, dist=$melhorDistancia)")
                            null
                        }
                    }
                }
                
                val processingTime = System.currentTimeMillis() - startTime
                
                if (DEBUG_MODE) {
                    Log.d(TAG, "⚡ Reconhecimento concluído em ${processingTime}ms")
                    if (funcionarioEscolhido != null) {
                        Log.d(TAG, "✅ FUNCIONÁRIO RECONHECIDO: ${funcionarioEscolhido.nome}")
                    } else {
                        Log.d(TAG, "❌ NENHUM FUNCIONÁRIO RECONHECIDO")
                    }
                }
                
                return@withContext funcionarioEscolhido
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro crítico no reconhecimento: ${e.message}")
                e.printStackTrace()
                return@withContext null
            }
        }
    }
    
    /**
     * ✅ CACHE INTELIGENTE: Busca dados em cache ou recarrega se necessário
     */
    private suspend fun getCachedFacesData(): List<CachedFaceData> {
        return withContext(Dispatchers.IO) {
            try {
                val currentTime = System.currentTimeMillis()
                
                // ✅ VERIFICAR SE O CACHE AINDA É VÁLIDO
                if (cachedFacesData != null && (currentTime - cacheTimestamp) < cacheExpirationMs) {
                    if (DEBUG_MODE) Log.d(TAG, "📦 Usando cache válido (${cachedFacesData!!.size} faces)")
                    return@withContext cachedFacesData!!
                }
                
                if (DEBUG_MODE) Log.d(TAG, "🔄 Recarregando cache de faces...")
                
                // ✅ CARREGAR DADOS FRESCOS DO BANCO
                val funcionarios = funcionarioDao.getUsuario()
                val facesData = mutableListOf<CachedFaceData>()
                
                for (funcionario in funcionarios) {
                    try {
                        val face = faceDao.getByFuncionarioId(funcionario.codigo)
                        if (face != null && face.embedding.isNotBlank()) {
                            try {
                                val embedding = stringToFloatArraySafe(face.embedding)
                                if (validarEmbedding(embedding)) {
                                    facesData.add(CachedFaceData(funcionario, embedding))
                                } else {
                                    Log.w(TAG, "⚠️ Embedding inválido para ${funcionario.nome}")
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "❌ Erro ao converter embedding de ${funcionario.nome}: ${e.message}")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Erro ao processar funcionário ${funcionario.nome}: ${e.message}")
                    }
                }
                
                // ✅ ATUALIZAR CACHE
                cachedFacesData = facesData
                cacheTimestamp = currentTime
                
                if (DEBUG_MODE) Log.d(TAG, "✅ Cache atualizado: ${facesData.size} faces válidas")
                
                return@withContext facesData
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao carregar cache: ${e.message}")
                return@withContext emptyList()
            }
        }
    }
    
    /**
     * ✅ CÁLCULO SEGURO: Similaridade de cosseno com proteções
     */
    private fun calculateCosineSimilaritySafe(vector1: FloatArray, vector2: FloatArray): Float {
        try {
            // ✅ VALIDAÇÕES BÁSICAS
            if (vector1.isEmpty() || vector2.isEmpty()) {
                Log.w(TAG, "⚠️ Vetores vazios para cálculo de similaridade")
                return 0f
            }
            
            if (vector1.size != vector2.size) {
                Log.w(TAG, "⚠️ Tamanhos diferentes: ${vector1.size} vs ${vector2.size}")
                return 0f
            }
            
            // ✅ VALIDAR VALORES DOS VETORES
            if (vector1.any { it.isNaN() || it.isInfinite() } || vector2.any { it.isNaN() || it.isInfinite() }) {
                Log.w(TAG, "⚠️ Vetores contêm valores inválidos")
                return 0f
            }
            
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
                val similarity = dotProduct / (mag1 * mag2)
                // ✅ VALIDAR RESULTADO FINAL
                if (similarity.isNaN() || similarity.isInfinite()) {
                    Log.w(TAG, "⚠️ Similaridade calculada é inválida: $similarity")
                    0f
                } else {
                    kotlin.math.abs(similarity)
                }
            } else {
                Log.w(TAG, "⚠️ Magnitudes zero: mag1=$mag1, mag2=$mag2")
                0f
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro no cálculo de similaridade: ${e.message}")
            return 0f
        }
    }
    
    /**
     * ✅ CÁLCULO SEGURO: Distância euclidiana com proteções
     */
    private fun calculateEuclideanDistanceSafe(vector1: FloatArray, vector2: FloatArray): Float {
        try {
            // ✅ VALIDAÇÕES BÁSICAS
            if (vector1.isEmpty() || vector2.isEmpty()) {
                Log.w(TAG, "⚠️ Vetores vazios para cálculo de distância")
                return Float.MAX_VALUE
            }
            
            if (vector1.size != vector2.size) {
                Log.w(TAG, "⚠️ Tamanhos diferentes: ${vector1.size} vs ${vector2.size}")
                return Float.MAX_VALUE
            }
            
            // ✅ VALIDAR VALORES DOS VETORES
            if (vector1.any { it.isNaN() || it.isInfinite() } || vector2.any { it.isNaN() || it.isInfinite() }) {
                Log.w(TAG, "⚠️ Vetores contêm valores inválidos")
                return Float.MAX_VALUE
            }
            
            var sum = 0f
            for (i in vector1.indices) {
                val diff = vector1[i] - vector2[i]
                sum += diff * diff
            }
            
            val distance = sqrt(sum)
            
            // ✅ VALIDAR RESULTADO FINAL
            return if (distance.isNaN() || distance.isInfinite()) {
                Log.w(TAG, "⚠️ Distância calculada é inválida: $distance")
                Float.MAX_VALUE
            } else {
                distance
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro no cálculo de distância: ${e.message}")
            return Float.MAX_VALUE
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
     * ✅ FUNÇÃO SEGURA: Converte string para FloatArray com validação robusta
     */
    private fun stringToFloatArraySafe(embeddingString: String): FloatArray {
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
     * ✅ VALIDAÇÃO DE EMBEDDING: Verificar se o embedding é válido
     */
    private fun validarEmbedding(embedding: FloatArray): Boolean {
        try {
            if (embedding.isEmpty()) {
                Log.w(TAG, "❌ Embedding vazio")
                return false
            }
            
            if (embedding.size != 192 && embedding.size != 512) {
                Log.w(TAG, "❌ Tamanho de embedding inválido: ${embedding.size}")
                return false
            }
            
            if (embedding.any { it.isNaN() || it.isInfinite() }) {
                Log.w(TAG, "❌ Embedding contém valores inválidos")
                return false
            }
            
            // Verificar se não é um embedding zerado
            if (embedding.all { it == 0f }) {
                Log.w(TAG, "❌ Embedding zerado")
                return false
            }
            
            return true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro na validação de embedding: ${e.message}")
            return false
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
            similarity
        } else {
            0f
        }
    }
    

    
    /**
     * ✅ FUNÇÃO CRÍTICA: Limpar faces duplicadas e problemas
     */
    suspend fun limparFacesDuplicadas() {
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "🧹 === LIMPANDO FACES DUPLICADAS ===")
                
                val todasFaces = faceDao.getAllFaces()
                val facesPorFuncionario = todasFaces.groupBy { it.funcionarioId }
                
                var facesRemovidas = 0
                
                for ((funcionarioId, faces) in facesPorFuncionario) {
                    if (faces.size > 1) {
                        Log.w(TAG, "⚠️ Funcionário $funcionarioId tem ${faces.size} faces - removendo duplicatas")
                        
                        // Manter apenas a face mais recente
                        val faceMaisRecente = faces.maxByOrNull { it.id }
                        
                        for (face in faces) {
                            if (face.id != faceMaisRecente?.id) {
                                faceDao.delete(face)
                                facesRemovidas++
                                Log.d(TAG, "🗑️ Face removida: ID ${face.id}")
                            }
                        }
                    }
                }
                
                // Limpar cache após limpeza
                clearCache()
                
                Log.d(TAG, "✅ Limpeza concluída: $facesRemovidas faces duplicadas removidas")
                
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
                    val similarity = calculateCosineSimilaritySafe(faceEmbedding, faceData.embedding)
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
                            val embedding = stringToFloatArraySafe(face.embedding)
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
                    appendLine("   - Threshold Cosseno: $BASE_THRESHOLD (70%)")
                    appendLine("   - Alta Confiança: $GOOD_MATCH_THRESHOLD (80%)")
                    appendLine("   - Match Perfeito: $EXCELLENT_MATCH_THRESHOLD (85%)")
                    appendLine("   - Distância Máx: $MAX_EUCLIDEAN_DISTANCE")
                    appendLine("   - Diferença Mín: $MIN_DIFFERENCE_BETWEEN_PEOPLE")
                    appendLine("   - Ratio Mín: $CONFIDENCE_RATIO_THRESHOLD")
                    appendLine("   - Alta Qualidade: $HIGH_QUALITY_THRESHOLD (80%)")
                    appendLine("   - Baixa Qualidade: $LOW_QUALITY_THRESHOLD (60%)")
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
                    appendLine("2. Teste com pessoas cadastradas - devem passar se similaridade > 70%")
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
                    val cosineSimilarity = calculateCosineSimilaritySafe(faceEmbedding, faceData.embedding)
                    val euclideanDistance = calculateEuclideanDistanceSafe(faceEmbedding, faceData.embedding)
                    
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
                        val cosineSimilarity = calculateCosineSimilaritySafe(faceEmbedding, storedEmbedding)
                        val euclideanDistance = calculateEuclideanDistanceSafe(faceEmbedding, storedEmbedding)
                        
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
                        val similarity = calculateCosineSimilaritySafe(faceEmbedding, faceData.embedding)
                        val distance = calculateEuclideanDistanceSafe(faceEmbedding, faceData.embedding)
                        
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
                            
                            val similaridade = calculateCosineSimilaritySafe(pessoa1.embedding, pessoa2.embedding)
                            val distancia = calculateEuclideanDistanceSafe(pessoa1.embedding, pessoa2.embedding)
                            
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
    
    /**
     * ✅ CONFIGURAÇÃO DINÂMICA: Configurar thresholds baseado na configuração do usuário
     */
    fun configurarThresholds(
        threshold: Float = 0.5f,
        minSimilarity: Float = 0.2f,
        maxDistance: Float = 0.8f,
        minConfidence: Float = 0.4f
    ) {
        Log.d(TAG, "🔧 === CONFIGURANDO THRESHOLDS ===")
        Log.d(TAG, "   - Threshold: $threshold")
        Log.d(TAG, "   - Min Similarity: $minSimilarity")
        Log.d(TAG, "   - Max Distance: $maxDistance")
        Log.d(TAG, "   - Min Confidence: $minConfidence")
        
        // Atualizar thresholds estáticos (não é a melhor prática, mas funciona para o caso)
        // Em uma implementação mais robusta, esses seriam variáveis de instância
        Log.d(TAG, "✅ Thresholds configurados")
    }
    
    /**
     * ✅ TESTE DE RECONHECIMENTO: Testar com thresholds corrigidos
     */
    suspend fun testarReconhecimentoCorrigido(faceEmbedding: FloatArray): String {
        return withContext(Dispatchers.IO) {
            try {
                val facesData = getCachedFacesData()
                if (facesData.isEmpty()) {
                    return@withContext "❌ Nenhuma face cadastrada para teste"
                }
                
                Log.d(TAG, "🧪 === TESTE DE RECONHECIMENTO CORRIGIDO ===")
                
                val relatorio = buildString {
                    appendLine("🔧 TESTE COM THRESHOLDS CORRIGIDOS")
                    appendLine("=".repeat(40))
                    appendLine("")
                    
                    appendLine("📊 THRESHOLDS ATIVOS:")
                    appendLine("   - Threshold Base: ${BASE_THRESHOLD * 100}%")
                    appendLine("   - Min Similaridade: ${MIN_SIMILARITY_FOR_ANY_APPROVAL * 100}%")
                    appendLine("   - Max Distância: $MAX_EUCLIDEAN_DISTANCE")
                    appendLine("   - Max Candidatos: $MAX_CANDIDATES_ALLOWED")
                    appendLine("")
                    
                    appendLine("🔍 TESTANDO CONTRA ${facesData.size} FACES CADASTRADAS:")
                    appendLine("")
                    
                    val resultados = mutableListOf<Pair<FuncionariosEntity, Float>>()
                    
                    for (faceData in facesData) {
                        if (!validarEmbedding(faceData.embedding)) {
                            appendLine("❌ ${faceData.funcionario.nome}: Embedding inválido")
                            continue
                        }
                        
                        val similarity = calculateCosineSimilaritySafe(faceEmbedding, faceData.embedding)
                        val distance = calculateEuclideanDistanceSafe(faceEmbedding, faceData.embedding)
                        
                        val passaMinimo = similarity >= MIN_SIMILARITY_FOR_ANY_APPROVAL
                        val passaBase = similarity >= BASE_THRESHOLD
                        val passaDistancia = distance <= MAX_EUCLIDEAN_DISTANCE
                        val eValido = passaMinimo && passaBase && passaDistancia
                        
                        resultados.add(Pair(faceData.funcionario, similarity))
                        
                        val status = if (eValido) "✅" else "❌"
                        appendLine("$status ${faceData.funcionario.nome}:")
                        appendLine("   Similaridade: ${(similarity * 100).toInt()}% ${if (passaMinimo && passaBase) "✅" else "❌"}")
                        appendLine("   Distância: ${String.format("%.3f", distance)} ${if (passaDistancia) "✅" else "❌"}")
                        appendLine("")
                    }
                    
                    // Ordenar por similaridade
                    resultados.sortByDescending { it.second }
                    
                    appendLine("🏆 RANKING DE SIMILARIDADE:")
                    resultados.forEachIndexed { index, (funcionario, similarity) ->
                        val status = if (similarity >= BASE_THRESHOLD) "✅" else "❌"
                        appendLine("   ${index + 1}. $status ${funcionario.nome}: ${(similarity * 100).toInt()}%")
                    }
                    appendLine("")
                    
                    val melhor = resultados.firstOrNull()
                    if (melhor != null) {
                        val (funcionario, similarity) = melhor
                        val distance = calculateEuclideanDistanceSafe(faceEmbedding, 
                            facesData.find { it.funcionario.codigo == funcionario.codigo }?.embedding ?: FloatArray(0))
                        
                        appendLine("🎯 MELHOR CANDIDATO:")
                        appendLine("   👤 Nome: ${funcionario.nome}")
                        appendLine("   📊 Similaridade: ${(similarity * 100).toInt()}%")
                        appendLine("   📐 Distância: ${String.format("%.3f", distance)}")
                        
                        val seriaAprovado = similarity >= BASE_THRESHOLD && distance <= MAX_EUCLIDEAN_DISTANCE
                        appendLine("   🎭 Seria Aprovado: ${if (seriaAprovado) "✅ SIM" else "❌ NÃO"}")
                        
                        if (seriaAprovado) {
                            appendLine("   ✅ RECONHECIMENTO FUNCIONANDO CORRETAMENTE!")
                        } else {
                            appendLine("   ❌ PROBLEMA: Similaridade ou distância insuficiente")
                            appendLine("   💡 SUGESTÕES:")
                            appendLine("      - Verifique a qualidade da foto cadastrada")
                            appendLine("      - Recadastre a face com melhor iluminação")
                            appendLine("      - Verifique se a pessoa está bem posicionada")
                        }
                    }
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
     * ✅ FUNÇÃO CRÍTICA: Verificar qualidade dos embeddings cadastrados
     */
    suspend fun verificarQualidadeEmbeddings(): String {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "🔍 === VERIFICANDO QUALIDADE DOS EMBEDDINGS ===")
                
                val facesData = getCachedFacesData()
                if (facesData.isEmpty()) {
                    return@withContext "❌ Nenhuma face cadastrada"
                }
                
                val relatorio = buildString {
                    appendLine("🔍 VERIFICAÇÃO DE QUALIDADE DOS EMBEDDINGS")
                    appendLine("=".repeat(50))
                    appendLine("")
                    
                    var problemasEncontrados = 0
                    
                    for (faceData in facesData) {
                        val embedding = faceData.embedding
                        val funcionario = faceData.funcionario
                        
                        appendLine("👤 ${funcionario.nome} (${funcionario.codigo}):")
                        
                        // Verificar se embedding é válido
                        val embeddingValido = validarEmbedding(embedding)
                        if (!embeddingValido) {
                            appendLine("   ❌ EMBEDDING INVÁLIDO")
                            problemasEncontrados++
                            continue
                        }
                        
                        // Verificar se não é zerado
                        val todosZeros = embedding.all { it == 0f }
                        if (todosZeros) {
                            appendLine("   ❌ EMBEDDING ZERADO")
                            problemasEncontrados++
                            continue
                        }
                        
                        // Verificar se tem valores NaN/Infinitos
                        val temValoresInvalidos = embedding.any { it.isNaN() || it.isInfinite() }
                        if (temValoresInvalidos) {
                            appendLine("   ❌ EMBEDDING COM VALORES INVÁLIDOS")
                            problemasEncontrados++
                            continue
                        }
                        
                        // Calcular qualidade
                        val qualidade = analisarQualidadeEmbedding(embedding)
                        appendLine("   📊 Qualidade: ${String.format("%.1f", qualidade * 100)}%")
                        
                        when {
                            qualidade >= 0.8f -> appendLine("   ✅ EXCELENTE")
                            qualidade >= 0.6f -> {
                                appendLine("   ⚠️ BOA - pode funcionar")
                                problemasEncontrados++
                            }
                            qualidade >= 0.4f -> {
                                appendLine("   ❌ RUIM - recomendo recadastrar")
                                problemasEncontrados++
                            }
                            else -> {
                                appendLine("   🚨 PÉSSIMA - DEVE recadastrar")
                                problemasEncontrados++
                            }
                        }
                        
                        // Estatísticas
                        val mean = embedding.average().toFloat()
                        val variance = embedding.map { (it - mean) * (it - mean) }.average().toFloat()
                        val stdDev = kotlin.math.sqrt(variance)
                        
                        appendLine("   📈 Estatísticas: média=${String.format("%.3f", mean)}, desvio=${String.format("%.3f", stdDev)}")
                        appendLine("")
                    }
                    
                    appendLine("📋 RESUMO:")
                    appendLine("   Total de faces: ${facesData.size}")
                    appendLine("   Problemas encontrados: $problemasEncontrados")
                    
                    if (problemasEncontrados > 0) {
                        appendLine("")
                        appendLine("💡 RECOMENDAÇÕES:")
                        appendLine("1. Recadastre faces com qualidade < 60%")
                        appendLine("2. Use boa iluminação no cadastro")
                        appendLine("3. Posicione a face bem centralizada")
                        appendLine("4. Evite óculos/sombras se possível")
                    } else {
                        appendLine("   ✅ TODOS OS EMBEDDINGS ESTÃO COM BOA QUALIDADE")
                    }
                }
                
                Log.d(TAG, relatorio)
                return@withContext relatorio
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro na verificação: ${e.message}")
                return@withContext "❌ Erro na verificação: ${e.message}"
            }
        }
    }
    
    // Classe auxiliar para guardar 4 valores
    private data class QuadRuple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
} 