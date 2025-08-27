package com.example.iface_offilne.helpers

import android.util.Log
import kotlin.math.*

/**
 * 🎯 HELPER PARA COMPARAÇÃO AVANÇADA DE EMBEDDINGS
 * 
 * Implementa múltiplas métricas de comparação para melhorar
 * a precisão do reconhecimento facial:
 * - Cosine Similarity
 * - Distância Euclidiana
 * - Distância Manhattan
 * - Correlação de Pearson
 * - Múltiplas imagens por pessoa
 */
class EmbeddingComparisonHelper {
    
    companion object {
        private const val TAG = "EmbeddingComparisonHelper"
        
        // ✅ THRESHOLDS CONFIGURÁVEIS - MAIS RIGOROSOS PARA SISTEMA DE PONTO
        var COSINE_SIMILARITY_THRESHOLD = 0.75f
        var EUCLIDEAN_DISTANCE_THRESHOLD = 1.0f
        var MANHATTAN_DISTANCE_THRESHOLD = 1.5f
        var PEARSON_CORRELATION_THRESHOLD = 0.70f
        
        // ✅ PESOS PARA COMBINAÇÃO DE MÉTRICAS
        var COSINE_WEIGHT = 0.4f
        var EUCLIDEAN_WEIGHT = 0.3f
        var MANHATTAN_WEIGHT = 0.2f
        var PEARSON_WEIGHT = 0.1f
    }
    
    /**
     * 🎯 COMPARAÇÃO COMPLETA DE EMBEDDINGS
     * 
     * Combina múltiplas métricas para obter o melhor resultado
     */
    fun compareEmbeddings(embedding1: FloatArray, embedding2: FloatArray): ComparisonResult {
        return try {
            // ✅ VALIDAR EMBEDDINGS
            if (!validateEmbeddings(embedding1, embedding2)) {
                return ComparisonResult.Invalid("Embeddings inválidos")
            }
            
            // ✅ CALCULAR TODAS AS MÉTRICAS
            val cosineSimilarity = calculateCosineSimilarity(embedding1, embedding2)
            val euclideanDistance = calculateEuclideanDistance(embedding1, embedding2)
            val manhattanDistance = calculateManhattanDistance(embedding1, embedding2)
            val pearsonCorrelation = calculatePearsonCorrelation(embedding1, embedding2)
            
            // ✅ CALCULAR SCORE COMBINADO
            val combinedScore = calculateCombinedScore(
                cosineSimilarity, euclideanDistance, manhattanDistance, pearsonCorrelation
            )
            
            // ✅ VERIFICAR SE PASSA NOS THRESHOLDS
            val isMatch = checkThresholds(
                cosineSimilarity, euclideanDistance, manhattanDistance, pearsonCorrelation
            )
            
            ComparisonResult.Valid(
                cosineSimilarity = cosineSimilarity,
                euclideanDistance = euclideanDistance,
                manhattanDistance = manhattanDistance,
                pearsonCorrelation = pearsonCorrelation,
                combinedScore = combinedScore,
                isMatch = isMatch
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro na comparação: ${e.message}")
            ComparisonResult.Invalid("Erro na comparação: ${e.message}")
        }
    }
    
    /**
     * 🎯 VALIDAR EMBEDDINGS
     */
    private fun validateEmbeddings(embedding1: FloatArray, embedding2: FloatArray): Boolean {
        if (embedding1.isEmpty() || embedding2.isEmpty()) {
            Log.e(TAG, "❌ Embeddings vazios")
            return false
        }
        
        if (embedding1.size != embedding2.size) {
            Log.e(TAG, "❌ Tamanhos diferentes: ${embedding1.size} vs ${embedding2.size}")
            return false
        }
        
        if (embedding1.any { it.isNaN() || it.isInfinite() } || 
            embedding2.any { it.isNaN() || it.isInfinite() }) {
            Log.e(TAG, "❌ Embeddings com valores inválidos")
            return false
        }
        
        return true
    }
    
    /**
     * 🎯 CALCULAR COSINE SIMILARITY
     * 
     * Mede a similaridade cosseno entre dois vetores
     * Retorna valor entre -1 e 1, onde 1 = idênticos
     */
    fun calculateCosineSimilarity(embedding1: FloatArray, embedding2: FloatArray): Float {
        return try {
            var dotProduct = 0f
            var norm1 = 0f
            var norm2 = 0f
            
            for (i in embedding1.indices) {
                dotProduct += embedding1[i] * embedding2[i]
                norm1 += embedding1[i] * embedding1[i]
                norm2 += embedding2[i] * embedding2[i]
            }
            
            val cosineSimilarity = dotProduct / (sqrt(norm1) * sqrt(norm2))
            
            // ✅ CONVERTER PARA ESCALA [0, 1]
            val normalizedSimilarity = (cosineSimilarity + 1) / 2
            
            Log.d(TAG, "📊 Cosine Similarity: ${String.format("%.3f", normalizedSimilarity)}")
            normalizedSimilarity
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro no cosine similarity: ${e.message}")
            0f
        }
    }
    
    /**
     * 🎯 CALCULAR DISTÂNCIA EUCLIDIANA
     * 
     * Mede a distância euclidiana entre dois vetores
     * Quanto menor, mais similares
     */
    fun calculateEuclideanDistance(embedding1: FloatArray, embedding2: FloatArray): Float {
        return try {
            var sumSquaredDiff = 0f
            
            for (i in embedding1.indices) {
                val diff = embedding1[i] - embedding2[i]
                sumSquaredDiff += diff * diff
            }
            
            val euclideanDistance = sqrt(sumSquaredDiff)
            
            Log.d(TAG, "📊 Euclidean Distance: ${String.format("%.3f", euclideanDistance)}")
            euclideanDistance
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro na distância euclidiana: ${e.message}")
            Float.MAX_VALUE
        }
    }
    
    /**
     * 🎯 CALCULAR DISTÂNCIA MANHATTAN
     * 
     * Mede a distância Manhattan (L1) entre dois vetores
     * Quanto menor, mais similares
     */
    fun calculateManhattanDistance(embedding1: FloatArray, embedding2: FloatArray): Float {
        return try {
            var sumAbsDiff = 0f
            
            for (i in embedding1.indices) {
                sumAbsDiff += abs(embedding1[i] - embedding2[i])
            }
            
            Log.d(TAG, "📊 Manhattan Distance: ${String.format("%.3f", sumAbsDiff)}")
            sumAbsDiff
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro na distância Manhattan: ${e.message}")
            Float.MAX_VALUE
        }
    }
    
    /**
     * 🎯 CALCULAR CORRELAÇÃO DE PEARSON
     * 
     * Mede a correlação linear entre dois vetores
     * Retorna valor entre -1 e 1, onde 1 = correlação perfeita
     */
    fun calculatePearsonCorrelation(embedding1: FloatArray, embedding2: FloatArray): Float {
        return try {
            val n = embedding1.size
            
            // ✅ CALCULAR MÉDIAS
            val mean1 = embedding1.average().toFloat()
            val mean2 = embedding2.average().toFloat()
            
            // ✅ CALCULAR NUMERADOR E DENOMINADORES
            var numerator = 0f
            var sumSquaredDiff1 = 0f
            var sumSquaredDiff2 = 0f
            
            for (i in embedding1.indices) {
                val diff1 = embedding1[i] - mean1
                val diff2 = embedding2[i] - mean2
                
                numerator += diff1 * diff2
                sumSquaredDiff1 += diff1 * diff1
                sumSquaredDiff2 += diff2 * diff2
            }
            
            val denominator = sqrt(sumSquaredDiff1 * sumSquaredDiff2)
            
            val pearsonCorrelation = if (denominator > 0) numerator / denominator else 0f
            
            // ✅ CONVERTER PARA ESCALA [0, 1]
            val normalizedCorrelation = (pearsonCorrelation + 1) / 2
            
            Log.d(TAG, "📊 Pearson Correlation: ${String.format("%.3f", normalizedCorrelation)}")
            normalizedCorrelation
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro na correlação de Pearson: ${e.message}")
            0f
        }
    }
    
    /**
     * 🎯 CALCULAR SCORE COMBINADO
     * 
     * Combina todas as métricas usando pesos configuráveis
     */
    private fun calculateCombinedScore(
        cosineSimilarity: Float,
        euclideanDistance: Float,
        manhattanDistance: Float,
        pearsonCorrelation: Float
    ): Float {
        return try {
            // ✅ NORMALIZAR DISTÂNCIAS (inverter para que maior = melhor)
            val normalizedEuclidean = 1f / (1f + euclideanDistance)
            val normalizedManhattan = 1f / (1f + manhattanDistance)
            
            // ✅ CALCULAR SCORE PONDERADO
            val combinedScore = 
                cosineSimilarity * COSINE_WEIGHT +
                normalizedEuclidean * EUCLIDEAN_WEIGHT +
                normalizedManhattan * MANHATTAN_WEIGHT +
                pearsonCorrelation * PEARSON_WEIGHT
            
            Log.d(TAG, "📊 Combined Score: ${String.format("%.3f", combinedScore)}")
            combinedScore
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro no score combinado: ${e.message}")
            0f
        }
    }
    
    /**
     * 🎯 VERIFICAR THRESHOLDS
     * 
     * Verifica se todas as métricas passam nos thresholds configurados
     */
    private fun checkThresholds(
        cosineSimilarity: Float,
        euclideanDistance: Float,
        manhattanDistance: Float,
        pearsonCorrelation: Float
    ): Boolean {
        val cosinePass = cosineSimilarity >= COSINE_SIMILARITY_THRESHOLD
        val euclideanPass = euclideanDistance <= EUCLIDEAN_DISTANCE_THRESHOLD
        val manhattanPass = manhattanDistance <= MANHATTAN_DISTANCE_THRESHOLD
        val pearsonPass = pearsonCorrelation >= PEARSON_CORRELATION_THRESHOLD
        
        Log.d(TAG, "📊 Thresholds:")
        Log.d(TAG, "   Cosine: ${String.format("%.3f", cosineSimilarity)} >= ${COSINE_SIMILARITY_THRESHOLD} -> ${if (cosinePass) "✅" else "❌"}")
        Log.d(TAG, "   Euclidean: ${String.format("%.3f", euclideanDistance)} <= ${EUCLIDEAN_DISTANCE_THRESHOLD} -> ${if (euclideanPass) "✅" else "❌"}")
        Log.d(TAG, "   Manhattan: ${String.format("%.3f", manhattanDistance)} <= ${MANHATTAN_DISTANCE_THRESHOLD} -> ${if (manhattanPass) "✅" else "❌"}")
        Log.d(TAG, "   Pearson: ${String.format("%.3f", pearsonCorrelation)} >= ${PEARSON_CORRELATION_THRESHOLD} -> ${if (pearsonPass) "✅" else "❌"}")
        
        return cosinePass && euclideanPass && manhattanPass && pearsonPass
    }
    
    /**
     * 🎯 COMPARAR COM MÚLTIPLAS IMAGENS
     * 
     * Compara um embedding com múltiplos embeddings de uma pessoa
     * e retorna o melhor resultado
     */
    fun compareWithMultipleImages(
        targetEmbedding: FloatArray,
        referenceEmbeddings: List<FloatArray>
    ): MultiImageComparisonResult {
        return try {
            if (referenceEmbeddings.isEmpty()) {
                return MultiImageComparisonResult.Invalid("Nenhum embedding de referência")
            }
            
            val results = mutableListOf<ComparisonResult>()
            
            // ✅ COMPARAR COM CADA EMBEDDING DE REFERÊNCIA
            for (i in referenceEmbeddings.indices) {
                val result = compareEmbeddings(targetEmbedding, referenceEmbeddings[i])
                results.add(result)
                Log.d(TAG, "📊 Comparação $i: ${result}")
            }
            
            // ✅ ENCONTRAR O MELHOR RESULTADO
            val validResults = results.filterIsInstance<ComparisonResult.Valid>()
            
            if (validResults.isEmpty()) {
                return MultiImageComparisonResult.Invalid("Nenhum resultado válido")
            }
            
            val bestResult = validResults.maxByOrNull { it.combinedScore }!!
            
            // ✅ CALCULAR ESTATÍSTICAS
            val avgCombinedScore = validResults.map { it.combinedScore }.average().toFloat()
            val maxCombinedScore = validResults.map { it.combinedScore }.maxOrNull()!!
            val minCombinedScore = validResults.map { it.combinedScore }.minOrNull()!!
            
            // ✅ VERIFICAR CONSISTÊNCIA
            val consistentResults = validResults.filter { it.isMatch }
            val consistency = consistentResults.size.toFloat() / validResults.size
            
            MultiImageComparisonResult.Valid(
                bestResult = bestResult,
                averageScore = avgCombinedScore,
                maxScore = maxCombinedScore,
                minScore = minCombinedScore,
                consistency = consistency,
                totalComparisons = validResults.size,
                matchingComparisons = consistentResults.size
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro na comparação múltipla: ${e.message}")
            MultiImageComparisonResult.Invalid("Erro na comparação múltipla: ${e.message}")
        }
    }
    
    /**
     * 🎯 CALCULAR EMBEDDING MÉDIO
     * 
     * Calcula o embedding médio de múltiplas imagens de uma pessoa
     */
    fun calculateAverageEmbedding(embeddings: List<FloatArray>): FloatArray? {
        return try {
            if (embeddings.isEmpty()) {
                Log.e(TAG, "❌ Lista de embeddings vazia")
                return null
            }
            
            val size = embeddings[0].size
            val averageEmbedding = FloatArray(size) { 0f }
            
            // ✅ CALCULAR MÉDIA DE CADA DIMENSÃO
            for (i in 0 until size) {
                var sum = 0f
                for (embedding in embeddings) {
                    if (embedding.size != size) {
                        Log.e(TAG, "❌ Tamanhos de embedding inconsistentes")
                        return null
                    }
                    sum += embedding[i]
                }
                averageEmbedding[i] = sum / embeddings.size
            }
            
            Log.d(TAG, "✅ Embedding médio calculado: ${averageEmbedding.size} dimensões")
            averageEmbedding
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro no cálculo do embedding médio: ${e.message}")
            null
        }
    }
    
    /**
     * 🎯 RESULTADO DE COMPARAÇÃO
     */
    sealed class ComparisonResult {
        data class Valid(
            val cosineSimilarity: Float,
            val euclideanDistance: Float,
            val manhattanDistance: Float,
            val pearsonCorrelation: Float,
            val combinedScore: Float,
            val isMatch: Boolean
        ) : ComparisonResult()
        
        data class Invalid(val reason: String) : ComparisonResult()
    }
    
    /**
     * 🎯 RESULTADO DE COMPARAÇÃO MÚLTIPLA
     */
    sealed class MultiImageComparisonResult {
        data class Valid(
            val bestResult: ComparisonResult.Valid,
            val averageScore: Float,
            val maxScore: Float,
            val minScore: Float,
            val consistency: Float,
            val totalComparisons: Int,
            val matchingComparisons: Int
        ) : MultiImageComparisonResult()
        
        data class Invalid(val reason: String) : MultiImageComparisonResult()
    }
} 