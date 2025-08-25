package com.example.iface_offilne.helpers

import android.content.Context
import android.util.Log
import com.example.iface_offilne.data.AppDatabase
import com.example.iface_offilne.data.FaceEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ✅ VALIDADOR DE EMBEDDINGS
 * Verifica se todos os embeddings no banco estão consistentes
 */
class EmbeddingValidator(private val context: Context) {
    
    companion object {
        private const val TAG = "EmbeddingValidator"
        private const val EXPECTED_EMBEDDING_SIZE = 192 // ✅ Tamanho padrão do modelo
    }
    
    /**
     * 🔍 VERIFICAR TODOS OS EMBEDDINGS NO BANCO
     */
    suspend fun validateAllEmbeddings(): ValidationReport {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "🔍 === VALIDANDO TODOS OS EMBEDDINGS ===")
                
                val db = AppDatabase.getInstance(context)
                val faceDao = db.faceDao()
                val faces = faceDao.getAllFaces()
                
                Log.d(TAG, "📊 Total de faces no banco: ${faces.size}")
                
                val report = ValidationReport()
                
                for (face in faces) {
                    val faceValidation = validateSingleEmbedding(face)
                    report.addFaceValidation(faceValidation)
                }
                
                Log.d(TAG, "✅ Validação concluída!")
                Log.d(TAG, "📊 Resultados:")
                Log.d(TAG, "   ✅ Válidos: ${report.validFaces}")
                Log.d(TAG, "   ❌ Inválidos: ${report.invalidFaces}")
                Log.d(TAG, "   ⚠️ Problemas: ${report.problems.size}")
                
                return@withContext report
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro na validação: ${e.message}", e)
                return@withContext ValidationReport().apply {
                    addError("Erro geral: ${e.message}")
                }
            }
        }
    }
    
    /**
     * 🔍 VALIDAR EMBEDDING INDIVIDUAL
     */
    suspend fun validateSingleEmbedding(face: FaceEntity): FaceValidationResult {
        return try {
            Log.d(TAG, "🔍 Validando face ID: ${face.id}, Funcionário: ${face.funcionarioId}")
            
            // Verificar se embedding não está vazio
            if (face.embedding.isBlank()) {
                return FaceValidationResult(
                    faceId = face.id,
                    funcionarioId = face.funcionarioId,
                    isValid = false,
                    problem = "Embedding vazio"
                )
            }
            
            // Converter string para array
            val embeddingArray = try {
                face.embedding.split(",").map { it.toFloat() }.toFloatArray()
            } catch (e: Exception) {
                return FaceValidationResult(
                    faceId = face.id,
                    funcionarioId = face.funcionarioId,
                    isValid = false,
                    problem = "Erro ao converter embedding: ${e.message}"
                )
            }
            
            // Verificar tamanho
            if (embeddingArray.size != EXPECTED_EMBEDDING_SIZE) {
                return FaceValidationResult(
                    faceId = face.id,
                    funcionarioId = face.funcionarioId,
                    isValid = false,
                    problem = "Tamanho incorreto: ${embeddingArray.size} (esperado: $EXPECTED_EMBEDDING_SIZE)"
                )
            }
            
            // Verificar valores inválidos
            val hasNaN = embeddingArray.any { it.isNaN() }
            val hasInf = embeddingArray.any { it.isInfinite() }
            val allZeros = embeddingArray.all { it == 0f }
            val allSame = embeddingArray.all { it == embeddingArray[0] }
            
            when {
                hasNaN -> {
                    return FaceValidationResult(
                        faceId = face.id,
                        funcionarioId = face.funcionarioId,
                        isValid = false,
                        problem = "Contém valores NaN"
                    )
                }
                hasInf -> {
                    return FaceValidationResult(
                        faceId = face.id,
                        funcionarioId = face.funcionarioId,
                        isValid = false,
                        problem = "Contém valores infinitos"
                    )
                }
                allZeros -> {
                    return FaceValidationResult(
                        faceId = face.id,
                        funcionarioId = face.funcionarioId,
                        isValid = false,
                        problem = "Todos os valores são zero"
                    )
                }
                allSame -> {
                    return FaceValidationResult(
                        faceId = face.id,
                        funcionarioId = face.funcionarioId,
                        isValid = false,
                        problem = "Todos os valores são idênticos"
                    )
                }
            }
            
            // Calcular estatísticas
            val mean = embeddingArray.average().toFloat()
            val variance = embeddingArray.map { (it - mean) * (it - mean) }.average().toFloat()
            val magnitude = kotlin.math.sqrt(embeddingArray.map { it * it }.sum())
            
            Log.d(TAG, "✅ Face ${face.funcionarioId} válida:")
            Log.d(TAG, "   📊 Tamanho: ${embeddingArray.size}")
            Log.d(TAG, "   📊 Média: $mean")
            Log.d(TAG, "   📊 Variância: $variance")
            Log.d(TAG, "   📊 Magnitude: $magnitude")
            
            return FaceValidationResult(
                faceId = face.id,
                funcionarioId = face.funcionarioId,
                isValid = true,
                problem = null,
                embeddingSize = embeddingArray.size,
                mean = mean,
                variance = variance,
                magnitude = magnitude
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao validar face ${face.id}: ${e.message}")
            return FaceValidationResult(
                faceId = face.id,
                funcionarioId = face.funcionarioId,
                isValid = false,
                problem = "Erro: ${e.message}"
            )
        }
    }
    
    /**
     * 🧹 LIMPAR EMBEDDINGS INVÁLIDOS
     */
    suspend fun cleanInvalidEmbeddings(): CleanupReport {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "🧹 === LIMPANDO EMBEDDINGS INVÁLIDOS ===")
                
                val validationReport = validateAllEmbeddings()
                val db = AppDatabase.getInstance(context)
                val faceDao = db.faceDao()
                
                val cleanupReport = CleanupReport()
                
                for (faceValidation in validationReport.faceValidations) {
                    if (!faceValidation.isValid) {
                        try {
                            faceDao.deleteByFuncionarioId(faceValidation.funcionarioId)
                            cleanupReport.addDeletedFace(faceValidation)
                            Log.d(TAG, "🗑️ Face deletada: ${faceValidation.funcionarioId} - ${faceValidation.problem}")
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ Erro ao deletar face ${faceValidation.funcionarioId}: ${e.message}")
                            cleanupReport.addError("Erro ao deletar ${faceValidation.funcionarioId}: ${e.message}")
                        }
                    }
                }
                
                Log.d(TAG, "✅ Limpeza concluída!")
                Log.d(TAG, "📊 Faces deletadas: ${cleanupReport.deletedFaces}")
                
                return@withContext cleanupReport
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro na limpeza: ${e.message}", e)
                return@withContext CleanupReport().apply {
                    addError("Erro geral: ${e.message}")
                }
            }
        }
    }
    
    /**
     * 🛡️ LIMPEZA MANUAL SEGURA: Remover apenas faces inválidas específicas
     */
    suspend fun cleanSpecificInvalidEmbeddings(funcionarioIds: List<String>): CleanupReport {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "🛡️ === LIMPEZA MANUAL SEGURA DE EMBEDDINGS ===")
                Log.d(TAG, "📋 Funcionários para verificar: ${funcionarioIds.joinToString(", ")}")
                
                val db = AppDatabase.getInstance(context)
                val faceDao = db.faceDao()
                
                val cleanupReport = CleanupReport()
                
                for (funcionarioId in funcionarioIds) {
                    try {
                        val face = faceDao.getByFuncionarioId(funcionarioId)
                        if (face != null) {
                            val faceValidation = validateSingleEmbedding(face)
                            
                            if (!faceValidation.isValid) {
                                Log.w(TAG, "⚠️ Face inválida encontrada para $funcionarioId: ${faceValidation.problem}")
                                
                                // Confirmar antes de remover
                                faceDao.deleteByFuncionarioId(funcionarioId)
                                cleanupReport.addDeletedFace(faceValidation)
                                Log.d(TAG, "🗑️ Face inválida removida: $funcionarioId")
                            } else {
                                Log.d(TAG, "✅ Face válida para $funcionarioId - mantida")
                            }
                        } else {
                            Log.d(TAG, "ℹ️ Nenhuma face encontrada para $funcionarioId")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Erro ao processar funcionário $funcionarioId: ${e.message}")
                        cleanupReport.addError("Erro ao processar $funcionarioId: ${e.message}")
                    }
                }
                
                Log.d(TAG, "✅ Limpeza manual concluída!")
                Log.d(TAG, "📊 Faces removidas: ${cleanupReport.deletedFaces.size}")
                
                return@withContext cleanupReport
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro na limpeza manual: ${e.message}", e)
                return@withContext CleanupReport().apply {
                    addError("Erro geral: ${e.message}")
                }
            }
        }
    }
    
    /**
     * 📊 RELATÓRIO DE VALIDAÇÃO
     */
    data class ValidationReport(
        val faceValidations: MutableList<FaceValidationResult> = mutableListOf(),
        val errors: MutableList<String> = mutableListOf()
    ) {
        val validFaces: Int get() = faceValidations.count { it.isValid }
        val invalidFaces: Int get() = faceValidations.count { !it.isValid }
        val problems: List<String> get() = faceValidations.filter { !it.isValid }.map { it.problem ?: "Problema desconhecido" }
        
        fun addFaceValidation(validation: FaceValidationResult) {
            faceValidations.add(validation)
        }
        
        fun addError(error: String) {
            errors.add(error)
        }
    }
    
    /**
     * 📊 RESULTADO DE VALIDAÇÃO INDIVIDUAL
     */
    data class FaceValidationResult(
        val faceId: Int,
        val funcionarioId: String,
        val isValid: Boolean,
        val problem: String?,
        val embeddingSize: Int? = null,
        val mean: Float? = null,
        val variance: Float? = null,
        val magnitude: Float? = null
    )
    
    /**
     * 📊 RELATÓRIO DE LIMPEZA
     */
    data class CleanupReport(
        val deletedFaces: MutableList<FaceValidationResult> = mutableListOf(),
        val errors: MutableList<String> = mutableListOf()
    ) {
        fun addDeletedFace(face: FaceValidationResult) {
            deletedFaces.add(face)
        }
        
        fun addError(error: String) {
            errors.add(error)
        }
    }
} 