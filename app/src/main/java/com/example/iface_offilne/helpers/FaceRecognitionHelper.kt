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
    
    companion object {
        private const val TAG = "FaceRecognitionHelper"
        private const val SIMILARITY_THRESHOLD = 0.5f // Limiar de similaridade reduzido para facilitar reconhecimento
    }
    
    /**
     * Compara um vetor facial com todos os rostos cadastrados no banco
     * e retorna o funcionário correspondente se houver match
     */
    suspend fun recognizeFace(faceEmbedding: FloatArray): FuncionariosEntity? {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "🔍 === INICIANDO RECONHECIMENTO FACIAL ===")
                Log.d(TAG, "🔍 Vetor de entrada: tamanho=${faceEmbedding.size}")
                
                // Buscar todos os funcionários
                val funcionarios = funcionarioDao.getUsuario()
                Log.d(TAG, "👥 Total de funcionários: ${funcionarios.size}")
                
                if (funcionarios.isEmpty()) {
                    Log.w(TAG, "⚠️  Nenhum funcionário cadastrado!")
                    return@withContext null
                }
                
                var bestMatch: FuncionariosEntity? = null
                var bestSimilarity = 0f
                
                for (funcionario in funcionarios) {
                    try {
                        // Buscar o rosto do funcionário
                        val faceEntity = faceDao.getByFuncionarioId(funcionario.codigo)
                        
                        if (faceEntity != null) {
                            // Converter o embedding string para FloatArray
                            val storedEmbedding = stringToFloatArray(faceEntity.embedding)
                            Log.d(TAG, "🔍 Funcionário ${funcionario.nome}: vetor armazenado tamanho=${storedEmbedding.size}")
                            
                            // Calcular similaridade
                            val similarity = calculateCosineSimilarity(faceEmbedding, storedEmbedding)
                            Log.d(TAG, "📊 Funcionário ${funcionario.nome}: similaridade = $similarity (limite: $SIMILARITY_THRESHOLD)")
                            
                            // Verificar se é o melhor match até agora
                            if (similarity > bestSimilarity && similarity >= SIMILARITY_THRESHOLD) {
                                bestSimilarity = similarity
                                bestMatch = funcionario
                                Log.d(TAG, "⭐ Novo melhor match: ${funcionario.nome} ($similarity)")
                            }
                        } else {
                            Log.w(TAG, "⚠️  Funcionário ${funcionario.nome} não possui rosto cadastrado")
                        }
                        
                    } catch (e: Exception) {
                        Log.w(TAG, "Erro ao processar funcionário ${funcionario.nome}: ${e.message}")
                        // Continua para o próximo funcionário
                    }
                }
                
                if (bestMatch != null) {
                    Log.d(TAG, "✅ Match encontrado: ${bestMatch.nome} (similaridade: $bestSimilarity)")
                } else {
                    Log.d(TAG, "❌ Nenhum match encontrado")
                }
                
                bestMatch
                
            } catch (e: Exception) {
                Log.e(TAG, "Erro no reconhecimento facial: ${e.message}", e)
                null
            }
        }
    }
    
    /**
     * Converte uma string de embedding para FloatArray
     */
    private fun stringToFloatArray(embeddingString: String): FloatArray {
        return embeddingString.split(",").map { it.trim().toFloat() }.toFloatArray()
    }
    
    /**
     * Calcula a similaridade de cosseno entre dois vetores
     */
    private fun calculateCosineSimilarity(vector1: FloatArray, vector2: FloatArray): Float {
        if (vector1.size != vector2.size) {
            Log.w(TAG, "⚠️  Vetores têm tamanhos diferentes: ${vector1.size} vs ${vector2.size}")
            
            // Tentar normalizar para o menor tamanho
            val minSize = minOf(vector1.size, vector2.size)
            if (minSize > 0) {
                Log.d(TAG, "🔧 Tentando normalizar para tamanho $minSize")
                val v1 = vector1.sliceArray(0 until minSize)
                val v2 = vector2.sliceArray(0 until minSize)
                return calculateCosineSimilarityInternal(v1, v2)
            }
            return 0f
        }
        
        return calculateCosineSimilarityInternal(vector1, vector2)
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
} 