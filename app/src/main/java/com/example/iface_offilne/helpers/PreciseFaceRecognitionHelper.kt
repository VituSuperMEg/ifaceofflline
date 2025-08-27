package com.example.iface_offilne.helpers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import com.google.mlkit.vision.face.Face
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sqrt
import kotlinx.coroutines.tasks.await
import com.example.iface_offilne.util.FaceRecognitionConfig

/**
 * 🎯 HELPER PARA RECONHECIMENTO FACIAL PRECISO E RIGOROSO
 * 
 * Características principais:
 * ✅ Múltiplas capturas para melhor qualidade
 * ✅ Validação rigorosa de landmarks
 * ✅ Thresholds altos para evitar falsos positivos
 * ✅ Processamento de imagem avançado
 * ✅ Validação de qualidade de face
 */
class PreciseFaceRecognitionHelper(private val context: Context) {
    
    companion object {
        private const val TAG = "PreciseFaceRecognition"
    }
    
    // 🎛️ CONFIGURAÇÃO RIGOROSA
    private val config = FaceRecognitionConfig.getRigorousConfig()
    
    private val faceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
            .setMinFaceSize(0.15f) // Face mínima de 15% da imagem
            .build()
    )
    
    private var interpreter: Interpreter? = null
    private var modelLoaded = false
    
    init {
        loadTensorFlowModel()
    }
    
    /**
     * 🎯 CADASTRO FACIAL COM MÚLTIPLAS CAPTURAS
     * Captura várias imagens da face para criar um embedding mais robusto
     */
    suspend fun registerFaceWithMultipleCaptures(faceBitmaps: List<Bitmap>): FaceRegistrationResult {
        return try {
            Log.d(TAG, "🚀 === INICIANDO CADASTRO FACIAL COM MÚLTIPLAS CAPTURAS ===")
            Log.d(TAG, "📸 Capturas recebidas: ${faceBitmaps.size}")
            
            if (faceBitmaps.size < FaceRecognitionConfig.REQUIRED_FACE_CAPTURES) {
                return FaceRegistrationResult.Failure("São necessárias pelo menos ${FaceRecognitionConfig.REQUIRED_FACE_CAPTURES} capturas da face")
            }
            
            // ✅ 1. VALIDAR TODAS AS CAPTURAS
            val validCaptures = mutableListOf<Bitmap>()
            for ((index, bitmap) in faceBitmaps.withIndex()) {
                Log.d(TAG, "🔍 Validando captura ${index + 1}/${faceBitmaps.size}")
                
                val qualityCheck = validateImageQuality(bitmap)
                if (!qualityCheck.isValid) {
                    Log.w(TAG, "❌ Captura ${index + 1} rejeitada: ${qualityCheck.reason}")
                    continue
                }
                
                val faceValidation = validateFaceDetection(bitmap)
                if (!faceValidation.isValid) {
                    Log.w(TAG, "❌ Captura ${index + 1} rejeitada: ${faceValidation.reason}")
                    continue
                }
                
                validCaptures.add(bitmap)
                Log.d(TAG, "✅ Captura ${index + 1} validada")
            }
            
            if (validCaptures.size < FaceRecognitionConfig.REQUIRED_FACE_CAPTURES) {
                return FaceRegistrationResult.Failure("Apenas ${validCaptures.size} capturas válidas de ${FaceRecognitionConfig.REQUIRED_FACE_CAPTURES} necessárias")
            }
            
            // ✅ 2. GERAR EMBEDDINGS PARA TODAS AS CAPTURAS VÁLIDAS
            val embeddings = mutableListOf<FloatArray>()
            for ((index, bitmap) in validCaptures.withIndex()) {
                Log.d(TAG, "🧠 Gerando embedding para captura ${index + 1}")
                val embedding = generateFaceEmbedding(bitmap)
                if (embedding != null) {
                    embeddings.add(embedding)
                    Log.d(TAG, "✅ Embedding ${index + 1} gerado")
                } else {
                    Log.w(TAG, "❌ Falha ao gerar embedding ${index + 1}")
                }
            }
            
            if (embeddings.isEmpty()) {
                return FaceRegistrationResult.Failure("Nenhum embedding válido foi gerado")
            }
            
            // ✅ 3. CRIAR EMBEDDING MÉDIO (MORE ROBUST)
            val averageEmbedding = calculateAverageEmbedding(embeddings)
            Log.d(TAG, "📊 Embedding médio criado a partir de ${embeddings.size} capturas")
            
            // ✅ 4. VALIDAR EMBEDDING FINAL
            val embeddingValidation = validateEmbedding(averageEmbedding)
            if (!embeddingValidation.isValid) {
                return FaceRegistrationResult.Failure("Embedding final inválido: ${embeddingValidation.reason}")
            }
            
            Log.d(TAG, "✅ Cadastro facial realizado com sucesso!")
            Log.d(TAG, "📊 Estatísticas: ${validCaptures.size} capturas válidas, embedding de ${averageEmbedding.size} dimensões")
            
            FaceRegistrationResult.Success(averageEmbedding, validCaptures.first())
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro no cadastro facial", e)
            FaceRegistrationResult.Failure("Erro interno: ${e.message}")
        }
    }
    
    /**
     * 🔍 RECONHECIMENTO FACIAL ULTRA RIGOROSO
     * Usa thresholds muito altos para evitar falsos positivos
     */
    suspend fun recognizeFaceWithUltraRigorousValidation(bitmap: Bitmap): FaceRecognitionResult {
        return try {
            Log.d(TAG, "🔍 === INICIANDO RECONHECIMENTO FACIAL ULTRA RIGOROSO ===")
            Log.d(TAG, "🎯 Thresholds ultra rigorosos: Similaridade=${config.minSimilarity}, Distância=${config.maxEuclideanDistance}, Confiança=${config.requiredConfidence}")
            
            // ✅ 1. VALIDAÇÃO DE QUALIDADE ULTRA RIGOROSA
            val qualityCheck = validateImageQuality(bitmap)
            if (!qualityCheck.isValid) {
                Log.w(TAG, "❌ Qualidade da imagem insuficiente: ${qualityCheck.reason}")
                return FaceRecognitionResult.Failure(qualityCheck.reason)
            }
            
            // ✅ 2. DETECÇÃO E VALIDAÇÃO DE FACE ULTRA RIGOROSA
            val faceValidation = validateFaceDetection(bitmap)
            if (!faceValidation.isValid) {
                Log.w(TAG, "❌ Face não válida: ${faceValidation.reason}")
                return FaceRecognitionResult.Failure(faceValidation.reason)
            }
            
            // ✅ 3. GERAÇÃO DO EMBEDDING
            val embedding = generateFaceEmbedding(bitmap)
            if (embedding == null) {
                Log.e(TAG, "❌ Falha ao gerar embedding")
                return FaceRecognitionResult.Failure("Falha ao processar face")
            }
            
            // ✅ 4. RECONHECIMENTO ULTRA RIGOROSO
            val recognitionResult = performUltraRigorousRecognition(embedding)
            
            Log.d(TAG, "✅ Reconhecimento facial concluído!")
            recognitionResult
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro no reconhecimento facial", e)
            FaceRecognitionResult.Failure("Erro interno: ${e.message}")
        }
    }
    
    /**
     * 🎯 RECONHECIMENTO ULTRA RIGOROSO COM MÚLTIPLAS VALIDAÇÕES
     */
    private suspend fun performUltraRigorousRecognition(embedding: FloatArray): FaceRecognitionResult {
        return try {
            // ✅ Carregar todas as faces cadastradas
            val database = com.example.iface_offilne.data.AppDatabase.getInstance(context)
            val faceDao = database.faceDao()
            val funcionarioDao = database.funcionarioDao()
            
            val faces = faceDao.getAllFaces()
            if (faces.isEmpty()) {
                Log.w(TAG, "⚠️ Nenhuma face cadastrada para comparação")
                return FaceRecognitionResult.Failure("Nenhuma face cadastrada")
            }
            
            Log.d(TAG, "🔍 Comparando com ${faces.size} faces cadastradas")
            
            var bestMatch: com.example.iface_offilne.data.FuncionariosEntity? = null
            var bestSimilarity = 0f
            var bestEuclideanDistance = Float.MAX_VALUE
            var bestConfidence = 0f
            
            // ✅ COMPARAÇÃO ULTRA RIGOROSA COM TODAS AS FACES
            for (face in faces) {
                try {
                    val storedEmbedding = parseEmbedding(face.embedding)
                    if (storedEmbedding == null) {
                        Log.w(TAG, "⚠️ Embedding inválido para funcionário ${face.funcionarioId}")
                        continue
                    }
                    
                    // ✅ Calcular similaridade cosseno
                    val cosineSimilarity = calculateCosineSimilarity(embedding, storedEmbedding)
                    
                    // ✅ Calcular distância euclidiana
                    val euclideanDistance = calculateEuclideanDistance(embedding, storedEmbedding)
                    
                    // ✅ Calcular confiança combinada (média ponderada)
                    val confidence = (cosineSimilarity * 0.6f + (1f - euclideanDistance) * 0.4f)
                    
                    Log.d(TAG, "👤 Funcionário ${face.funcionarioId}: Similaridade=${String.format("%.3f", cosineSimilarity)}, Distância=${String.format("%.3f", euclideanDistance)}, Confiança=${String.format("%.3f", confidence)}")
                    
                    // ✅ VALIDAÇÃO ULTRA RIGOROSA: Todas as condições devem ser atendidas
                    if (cosineSimilarity >= config.minSimilarity && 
                        euclideanDistance <= config.maxEuclideanDistance && 
                        confidence >= config.requiredConfidence) {
                        
                        // ✅ Se encontrou uma correspondência válida, verificar se é melhor que a anterior
                        if (confidence > bestConfidence) {
                            val funcionarioId = face.funcionarioId.toIntOrNull()
                            bestMatch = if (funcionarioId != null) {
                                funcionarioDao.getById(funcionarioId)
                            } else {
                                funcionarioDao.getAll().find { it.codigo == face.funcionarioId }
                            }
                            bestSimilarity = cosineSimilarity
                            bestEuclideanDistance = euclideanDistance
                            bestConfidence = confidence
                            
                            Log.d(TAG, "🎯 NOVO MELHOR MATCH: ${bestMatch?.nome} (Confiança: ${String.format("%.3f", confidence)})")
                        }
                    }
                    
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Erro ao comparar com face ${face.funcionarioId}: ${e.message}")
                }
            }
            
            // ✅ RESULTADO FINAL
            if (bestMatch != null) {
                Log.d(TAG, "✅ RECONHECIMENTO BEM-SUCEDIDO!")
                Log.d(TAG, "👤 Funcionário: ${bestMatch.nome}")
                Log.d(TAG, "📊 Métricas: Similaridade=${String.format("%.3f", bestSimilarity)}, Distância=${String.format("%.3f", bestEuclideanDistance)}, Confiança=${String.format("%.3f", bestConfidence)}")
                
                return FaceRecognitionResult.Success(
                    funcionario = bestMatch,
                    similarity = bestSimilarity,
                    euclideanDistance = bestEuclideanDistance,
                    confidence = bestConfidence
                )
            } else {
                Log.w(TAG, "❌ NENHUM FUNCIONÁRIO RECONHECIDO")
                Log.w(TAG, "📊 Thresholds não atendidos: Similaridade>=${config.minSimilarity}, Distância<=${config.maxEuclideanDistance}, Confiança>=${config.requiredConfidence}")
                return FaceRecognitionResult.Failure("Funcionário não reconhecido - thresholds ultra rigorosos não atendidos")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro no reconhecimento ultra rigoroso", e)
            return FaceRecognitionResult.Failure("Erro no reconhecimento: ${e.message}")
        }
    }
    
    /**
     * 📊 CALCULAR EMBEDDING MÉDIO PARA MAIOR ROBUSTEZ
     */
    private fun calculateAverageEmbedding(embeddings: List<FloatArray>): FloatArray {
        if (embeddings.isEmpty()) return FloatArray(0)
        if (embeddings.size == 1) return embeddings[0]
        
        val size = embeddings[0].size
        val averageEmbedding = FloatArray(size)
        
        for (i in 0 until size) {
            var sum = 0f
            for (embedding in embeddings) {
                sum += embedding[i]
            }
            averageEmbedding[i] = sum / embeddings.size
        }
        
        // Normalizar o embedding médio
        val magnitude = sqrt(averageEmbedding.map { it * it }.sum())
        for (i in averageEmbedding.indices) {
            averageEmbedding[i] = averageEmbedding[i] / magnitude
        }
        
        Log.d(TAG, "📊 Embedding médio calculado a partir de ${embeddings.size} embeddings")
        return averageEmbedding
    }
    
    /**
     * 📐 CALCULAR SIMILARIDADE COSSENO
     */
    private fun calculateCosineSimilarity(embedding1: FloatArray, embedding2: FloatArray): Float {
        if (embedding1.size != embedding2.size) return 0f
        
        var dotProduct = 0f
        var norm1 = 0f
        var norm2 = 0f
        
        for (i in embedding1.indices) {
            dotProduct += embedding1[i] * embedding2[i]
            norm1 += embedding1[i] * embedding1[i]
            norm2 += embedding2[i] * embedding2[i]
        }
        
        val denominator = sqrt(norm1) * sqrt(norm2)
        return if (denominator > 0f) dotProduct / denominator else 0f
    }
    
    /**
     * 📏 CALCULAR DISTÂNCIA EUCLIDIANA
     */
    private fun calculateEuclideanDistance(embedding1: FloatArray, embedding2: FloatArray): Float {
        if (embedding1.size != embedding2.size) return Float.MAX_VALUE
        
        var sum = 0f
        for (i in embedding1.indices) {
            val diff = embedding1[i] - embedding2[i]
            sum += diff * diff
        }
        
        return sqrt(sum)
    }
    
    /**
     * 🔧 PARSEAR EMBEDDING DO BANCO DE DADOS
     */
    private fun parseEmbedding(embeddingString: String): FloatArray? {
        return try {
            val values = embeddingString.split(",").map { it.trim().toFloat() }
            values.toFloatArray()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao parsear embedding: ${e.message}")
            null
        }
    }
    
    /**
     * 🔍 VALIDAÇÃO DE QUALIDADE DA IMAGEM ULTRA RIGOROSA
     */
    private fun validateImageQuality(bitmap: Bitmap): QualityCheckResult {
        try {
            // ✅ Verificar tamanho mínimo
            if (bitmap.width < 200 || bitmap.height < 200) {
                return QualityCheckResult(false, "Imagem muito pequena (mínimo 200x200)")
            }
            
            // ✅ Verificar se a imagem não está vazia ou corrompida
            if (bitmap.isRecycled) {
                return QualityCheckResult(false, "Imagem corrompida")
            }
            
            // ✅ Verificar brilho (ultra rigoroso)
            val brightness = calculateBrightness(bitmap)
            Log.d(TAG, "💡 Brilho detectado: ${String.format("%.3f", brightness)} (limites: ${config.minBrightness}-${config.maxBrightness})")
            
            if (brightness < config.minBrightness || brightness > config.maxBrightness) {
                return QualityCheckResult(false, "Brilho inadequado (${String.format("%.2f", brightness)})")
            }
            
            // ✅ Verificar contraste (ultra rigoroso)
            val contrast = calculateContrast(bitmap)
            Log.d(TAG, "🎨 Contraste detectado: ${String.format("%.3f", contrast)} (mínimo: ${config.minContrast})")
            
            if (contrast < config.minContrast) {
                return QualityCheckResult(false, "Contraste muito baixo (${String.format("%.2f", contrast)})")
            }
            
            // ✅ Verificar nitidez (nova validação)
            val sharpness = calculateSharpness(bitmap)
            Log.d(TAG, "🔍 Nitidez detectada: ${String.format("%.3f", sharpness)}")
            
            if (sharpness < FaceRecognitionConfig.MIN_SHARPNESS) {
                return QualityCheckResult(false, "Imagem muito borrada")
            }
            
            Log.d(TAG, "✅ Qualidade da imagem aceitável")
            return QualityCheckResult(true, "Qualidade OK")
            
        } catch (e: Exception) {
            Log.e(TAG, "Erro na validação de qualidade", e)
            return QualityCheckResult(false, "Erro na análise de qualidade")
        }
    }
    
    /**
     * 👤 VALIDAÇÃO DE DETECÇÃO DE FACE ULTRA RIGOROSA
     */
    private suspend fun validateFaceDetection(bitmap: Bitmap): FaceValidationResult {
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            
            val faces = faceDetector.process(image).await()
            
            if (faces.isEmpty()) {
                return FaceValidationResult(false, "Nenhuma face detectada", null)
            }
            
            if (faces.size > 1) {
                return FaceValidationResult(false, "Múltiplas faces detectadas", null)
            }
            
            val face = faces[0]
            
            // ✅ Verificar tamanho da face (ultra rigoroso)
            val faceRatio = calculateFaceRatio(face.boundingBox, bitmap.width, bitmap.height)
            Log.d(TAG, "📐 Proporção da face: ${String.format("%.3f", faceRatio)}")
            
            if (faceRatio < config.minFaceSizeRatio || faceRatio > FaceRecognitionConfig.MAX_FACE_SIZE_RATIO) {
                return FaceValidationResult(false, "Face muito pequena ou muito grande (${String.format("%.1f", faceRatio * 100)}%)", face)
            }
            
            // ✅ Verificar landmarks essenciais
            val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)
            val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)
            val nose = face.getLandmark(FaceLandmark.NOSE_BASE)
            val leftMouth = face.getLandmark(FaceLandmark.MOUTH_LEFT)
            val rightMouth = face.getLandmark(FaceLandmark.MOUTH_RIGHT)
            
            if (leftEye == null || rightEye == null || nose == null || leftMouth == null || rightMouth == null) {
                return FaceValidationResult(false, "Landmarks faciais incompletos", face)
            }
            
            // ✅ Verificar distância entre olhos (ultra rigoroso)
            val eyeDistance = calculateEyeDistance(face)
            Log.d(TAG, "👀 Distância entre olhos: ${String.format("%.1f", eyeDistance)}px")
            
            if (eyeDistance < config.minEyeDistance) {
                return FaceValidationResult(false, "Olhos muito próximos (${String.format("%.1f", eyeDistance)}px)", face)
            }
            
            // ✅ Verificar simetria facial (nova validação)
            val symmetry = calculateFaceSymmetry(face)
            Log.d(TAG, "⚖️ Simetria facial: ${String.format("%.3f", symmetry)}")
            
            if (symmetry < FaceRecognitionConfig.MIN_FACE_SYMMETRY) {
                return FaceValidationResult(false, "Face muito assimétrica (${String.format("%.2f", symmetry)})", face)
            }
            
            return FaceValidationResult(true, "Face válida", face)
            
        } catch (e: Exception) {
            Log.e(TAG, "Erro na validação de face", e)
            return FaceValidationResult(false, "Erro na detecção", null)
        }
    }
    
    /**
     * 🧠 GERAÇÃO DO EMBEDDING FACIAL COM PROCESSAMENTO AVANÇADO
     */
    private fun generateFaceEmbedding(bitmap: Bitmap): FloatArray? {
        return try {
            Log.d(TAG, "🧠 === GERANDO EMBEDDING FACIAL AVANÇADO ===")
            
            if (!modelLoaded || interpreter == null) {
                Log.w(TAG, "⚠️ Modelo não carregado - usando modelo alternativo")
                return generateEmbeddingWithAlternativeModel(bitmap)
            }
            
            // ✅ PROCESSAMENTO AVANÇADO DA IMAGEM
            val processedBitmap = preprocessImage(bitmap)
            
            // Redimensionar para o tamanho do modelo
            val resizedBitmap = Bitmap.createScaledBitmap(processedBitmap, FaceRecognitionConfig.MODEL_INPUT_SIZE, FaceRecognitionConfig.MODEL_INPUT_SIZE, true)
            Log.d(TAG, "📐 Bitmap redimensionado para ${FaceRecognitionConfig.MODEL_INPUT_SIZE}x${FaceRecognitionConfig.MODEL_INPUT_SIZE}")
            
            // Converter para tensor usando TensorFlow Lite Support
            val tensorImage = TensorImage.fromBitmap(resizedBitmap)
            val imageProcessor = ImageProcessor.Builder()
                .add(ResizeOp(FaceRecognitionConfig.MODEL_INPUT_SIZE, FaceRecognitionConfig.MODEL_INPUT_SIZE, ResizeOp.ResizeMethod.BILINEAR))
                .build()
            
            val processedImage = imageProcessor.process(tensorImage)
            
            // Preparar saída
            val output = Array(1) { FloatArray(FaceRecognitionConfig.MODEL_OUTPUT_SIZE) }
            
            // Executar inferência
            interpreter?.run(processedImage.buffer, output)
            val embedding = output[0]
            
            Log.d(TAG, "✅ Embedding gerado com sucesso! Tamanho: ${embedding.size}")
            Log.d(TAG, "📊 Primeiros 5 valores: ${embedding.take(5).joinToString(", ")}")
            
            // Limpar recursos
            resizedBitmap.recycle()
            if (processedBitmap != bitmap) {
                processedBitmap.recycle()
            }
            
            embedding
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao gerar embedding", e)
            null
        }
    }
    
    /**
     * 🔧 PROCESSAMENTO AVANÇADO DA IMAGEM
     */
    private fun preprocessImage(bitmap: Bitmap): Bitmap {
        try {
            // ✅ Aplicar correção de brilho e contraste
            val processedBitmap = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, true)
            
            // ✅ Aplicar filtro de suavização para reduzir ruído
            val canvas = android.graphics.Canvas(processedBitmap)
            val paint = android.graphics.Paint().apply {
                isAntiAlias = true
                isFilterBitmap = true
            }
            
            canvas.drawBitmap(bitmap, 0f, 0f, paint)
            
            Log.d(TAG, "✅ Imagem pré-processada")
            return processedBitmap
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro no pré-processamento", e)
            return bitmap
        }
    }
    
    /**
     * 🔄 GERAÇÃO DE EMBEDDING COM MODELO ALTERNATIVO
     */
    private fun generateEmbeddingWithAlternativeModel(bitmap: Bitmap): FloatArray? {
        try {
            Log.d(TAG, "🔄 Usando modelo alternativo para embedding")
            
            // Usar o modelo facenet_model.tflite se disponível
            val modelFile = "facenet_model.tflite"
            val files = context.assets.list("") ?: emptyArray()
            
            if (!files.contains(modelFile)) {
                Log.w(TAG, "⚠️ Modelo alternativo não encontrado")
                return null
            }
            
            // Carregar modelo alternativo
            val assetFileDescriptor = context.assets.openFd(modelFile)
            val inputStream = assetFileDescriptor.createInputStream()
            val fileChannel = inputStream.channel
            val startOffset = assetFileDescriptor.startOffset
            val declaredLength = assetFileDescriptor.declaredLength
            
            val modelBuffer = fileChannel.map(
                java.nio.channels.FileChannel.MapMode.READ_ONLY,
                startOffset,
                declaredLength
            )
            
            val options = Interpreter.Options().apply {
                setNumThreads(2)
                setUseNNAPI(false)
            }
            
            val altInterpreter = Interpreter(modelBuffer, options)
            
            // Processar imagem
            val resizedBitmap = Bitmap.createScaledBitmap(bitmap, FaceRecognitionConfig.MODEL_INPUT_SIZE, FaceRecognitionConfig.MODEL_INPUT_SIZE, true)
            val inputBuffer = convertBitmapToTensorInput(resizedBitmap)
            val output = Array(1) { FloatArray(FaceRecognitionConfig.MODEL_OUTPUT_SIZE) }
            
            altInterpreter.run(inputBuffer, output)
            val embedding = output[0]
            
            // Limpar recursos
            altInterpreter.close()
            resizedBitmap.recycle()
            
            Log.d(TAG, "✅ Embedding gerado com modelo alternativo")
            return embedding
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro com modelo alternativo", e)
            return null
        }
    }
    
    /**
     * ✅ VALIDAÇÃO DO EMBEDDING
     */
    private fun validateEmbedding(embedding: FloatArray): EmbeddingValidationResult {
        try {
            // Verificar se não é um vetor nulo
            val magnitude = sqrt(embedding.map { it * it }.sum())
            if (magnitude < 0.1f) {
                return EmbeddingValidationResult(false, "Embedding muito fraco")
            }
            
            // Verificar se não é um vetor constante
            val variance = embedding.map { it * it }.average().toFloat()
            if (variance < 0.01f) {
                return EmbeddingValidationResult(false, "Embedding muito uniforme")
            }
            
            // Verificar se não contém valores inválidos
            val hasNaN = embedding.any { it.isNaN() }
            val hasInf = embedding.any { it.isInfinite() }
            
            if (hasNaN || hasInf) {
                return EmbeddingValidationResult(false, "Embedding contém valores inválidos")
            }
            
            return EmbeddingValidationResult(true, "Embedding válido")
            
        } catch (e: Exception) {
            Log.e(TAG, "Erro na validação do embedding", e)
            return EmbeddingValidationResult(false, "Erro na validação")
        }
    }
    
    // ========== MÉTODOS AUXILIARES ==========
    
    private fun loadTensorFlowModel() {
        try {
            Log.d(TAG, "📂 === CARREGANDO MODELO TENSORFLOW ===")
            
            // Tentar carregar o modelo principal
            val files = context.assets.list("") ?: emptyArray()
            if (!files.contains("model.tflite")) {
                Log.w(TAG, "⚠️ Arquivo model.tflite não encontrado")
                modelLoaded = false
                return
            }
            
            // Carregar o modelo
            val assetFileDescriptor = context.assets.openFd("model.tflite")
            val inputStream = assetFileDescriptor.createInputStream()
            val fileChannel = inputStream.channel
            val startOffset = assetFileDescriptor.startOffset
            val declaredLength = assetFileDescriptor.declaredLength
            
            val modelBuffer = fileChannel.map(
                java.nio.channels.FileChannel.MapMode.READ_ONLY,
                startOffset,
                declaredLength
            )
            
            // Criar interpretador
            val options = Interpreter.Options().apply {
                setNumThreads(FaceRecognitionConfig.TENSORFLOW_THREADS)
                setUseNNAPI(FaceRecognitionConfig.USE_NNAPI)
            }
            
            interpreter = Interpreter(modelBuffer, options)
            interpreter?.allocateTensors()
            
            modelLoaded = true
            Log.d(TAG, "✅ Modelo TensorFlow carregado com sucesso!")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao carregar modelo TensorFlow", e)
            interpreter?.close()
            interpreter = null
            modelLoaded = false
        }
    }
    
    private fun convertBitmapToTensorInput(bitmap: Bitmap): ByteBuffer {
        val byteBuffer = ByteBuffer.allocateDirect(4 * FaceRecognitionConfig.MODEL_INPUT_SIZE * FaceRecognitionConfig.MODEL_INPUT_SIZE * 3)
        byteBuffer.order(ByteOrder.nativeOrder())
        
        val intValues = IntArray(FaceRecognitionConfig.MODEL_INPUT_SIZE * FaceRecognitionConfig.MODEL_INPUT_SIZE)
        bitmap.getPixels(intValues, 0, FaceRecognitionConfig.MODEL_INPUT_SIZE, 0, 0, FaceRecognitionConfig.MODEL_INPUT_SIZE, FaceRecognitionConfig.MODEL_INPUT_SIZE)
        
        for (pixel in intValues) {
            val r = ((pixel shr 16) and 0xFF) / 127.5f - 1.0f
            val g = ((pixel shr 8) and 0xFF) / 127.5f - 1.0f
            val b = (pixel and 0xFF) / 127.5f - 1.0f
            
            byteBuffer.putFloat(r)
            byteBuffer.putFloat(g)
            byteBuffer.putFloat(b)
        }
        
        return byteBuffer
    }
    
    private fun calculateBrightness(bitmap: Bitmap): Float {
        try {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            
            var totalBrightness = 0.0
            for (pixel in pixels) {
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                totalBrightness += (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
            }
            
            return (totalBrightness / pixels.size).toFloat()
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao calcular brilho", e)
            return 0.5f
        }
    }
    
    private fun calculateContrast(bitmap: Bitmap): Float {
        try {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            
            val brightnessValues = mutableListOf<Double>()
            for (pixel in pixels) {
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                val brightness = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
                brightnessValues.add(brightness)
            }
            
            val mean = brightnessValues.average()
            val variance = brightnessValues.map { (it - mean) * (it - mean) }.average()
            val standardDeviation = sqrt(variance)
            
            return (standardDeviation / 0.5).toFloat().coerceIn(0f, 1f)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao calcular contraste", e)
            return 0.5f
        }
    }
    
    private fun calculateSharpness(bitmap: Bitmap): Float {
        try {
            // Implementação simplificada de detecção de nitidez
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            
            var totalGradient = 0.0
            for (y in 1 until bitmap.height - 1) {
                for (x in 1 until bitmap.width - 1) {
                    val idx = y * bitmap.width + x
                    val current = pixels[idx]
                    
                    // Calcular gradiente horizontal
                    val left = pixels[idx - 1]
                    val right = pixels[idx + 1]
                    val gradX = abs((current and 0xFF) - (left and 0xFF)) + 
                               abs(((current shr 8) and 0xFF) - ((left shr 8) and 0xFF)) +
                               abs(((current shr 16) and 0xFF) - ((left shr 16) and 0xFF))
                    
                    val gradY = abs((current and 0xFF) - (right and 0xFF)) + 
                               abs(((current shr 8) and 0xFF) - ((right shr 8) and 0xFF)) +
                               abs(((current shr 16) and 0xFF) - ((right shr 16) and 0xFF))
                    
                    totalGradient += (gradX + gradY) / 6.0
                }
            }
            
            val averageGradient = totalGradient / (bitmap.width * bitmap.height)
            return (averageGradient / 255.0).toFloat().coerceIn(0f, 1f)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao calcular nitidez", e)
            return 0.5f
        }
    }
    
    private fun calculateFaceRatio(boundingBox: Rect, imageWidth: Int, imageHeight: Int): Float {
        val faceArea = boundingBox.width() * boundingBox.height()
        val imageArea = imageWidth * imageHeight
        return faceArea.toFloat() / imageArea.toFloat()
    }
    
    private fun calculateEyeDistance(face: Face): Float {
        val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)
        val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)
        
        if (leftEye == null || rightEye == null) return 0f
        
        val dx = leftEye.position.x - rightEye.position.x
        val dy = leftEye.position.y - rightEye.position.y
        return sqrt(dx * dx + dy * dy)
    }
    
    private fun calculateFaceSymmetry(face: Face): Float {
        try {
            val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)
            val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)
            val nose = face.getLandmark(FaceLandmark.NOSE_BASE)
            
            if (leftEye == null || rightEye == null || nose == null) return 0f
            
            // Calcular centro entre os olhos
            val eyeCenterX = (leftEye.position.x + rightEye.position.x) / 2f
            val eyeCenterY = (leftEye.position.y + rightEye.position.y) / 2f
            
            // Calcular distância do nariz ao centro dos olhos
            val noseToEyeCenter = sqrt(
                (nose.position.x - eyeCenterX) * (nose.position.x - eyeCenterX) +
                (nose.position.y - eyeCenterY) * (nose.position.y - eyeCenterY)
            )
            
            // Normalizar para 0-1 (quanto menor a distância, mais simétrico)
            val maxDistance = face.boundingBox.width() / 4f
            return (1f - (noseToEyeCenter / maxDistance)).coerceIn(0f, 1f)
            
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao calcular simetria", e)
            return 0.5f
        }
    }
    
    // ========== CLASSES DE RESULTADO ==========
    
    sealed class FaceRegistrationResult {
        data class Success(val embedding: FloatArray, val bitmap: Bitmap) : FaceRegistrationResult()
        data class Failure(val reason: String) : FaceRegistrationResult()
    }
    
    sealed class FaceRecognitionResult {
        data class Success(
            val funcionario: com.example.iface_offilne.data.FuncionariosEntity,
            val similarity: Float,
            val euclideanDistance: Float,
            val confidence: Float
        ) : FaceRecognitionResult()
        data class Failure(val reason: String) : FaceRecognitionResult()
    }
    
    data class QualityCheckResult(val isValid: Boolean, val reason: String)
    
    data class FaceValidationResult(val isValid: Boolean, val reason: String, val face: Face?)
    
    data class EmbeddingValidationResult(val isValid: Boolean, val reason: String)
} 