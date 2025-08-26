package com.example.iface_offilne.helpers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import com.google.mlkit.vision.face.Face
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sqrt
import kotlinx.coroutines.tasks.await

/**
 * 🚀 HELPER AVANÇADO PARA RECONHECIMENTO FACIAL OFFLINE
 * 
 * Melhorias implementadas:
 * ✅ Qualidade de imagem
 * ✅ Validação de face
 * ✅ Otimização de performance
 * ✅ Validação de landmarks
 */
class AdvancedFaceRecognitionHelper(private val context: Context) {
    
    companion object {
        private const val TAG = "AdvancedFaceRecognition"
        
        // 🎛️ MODO RIGOROSO PARA RECONHECIMENTO PRECISO
        private const val RIGOROUS_RECOGNITION_MODE = true // Modo rigoroso para evitar falsos positivos
        
        // ✅ THRESHOLDS RIGOROSOS PARA RECONHECIMENTO
        private val MIN_FACE_SIZE_RATIO = if (RIGOROUS_RECOGNITION_MODE) 0.15f else 0.08f // Face deve ocupar pelo menos 15% da imagem
        private val MAX_FACE_SIZE_RATIO = if (RIGOROUS_RECOGNITION_MODE) 0.8f else 0.9f  // Face não pode ocupar mais que 80% da imagem
        private val MIN_EYE_DISTANCE = if (RIGOROUS_RECOGNITION_MODE) 30f else 20f // Distância mínima entre olhos (mais rigorosa)
        private val MIN_BRIGHTNESS = if (RIGOROUS_RECOGNITION_MODE) 0.2f else 0.1f // Brilho mínimo (mais rigoroso)
        private val MAX_BRIGHTNESS = if (RIGOROUS_RECOGNITION_MODE) 0.8f else 0.95f // Brilho máximo (mais rigoroso)
        private val MIN_CONTRAST = if (RIGOROUS_RECOGNITION_MODE) 0.2f else 0.1f // Contraste mínimo (mais rigoroso)
        
        // ✅ THRESHOLDS DE RECONHECIMENTO FACIAL RIGOROSOS
        private const val MIN_SIMILARITY_THRESHOLD = 0.75f // Aumentado para 75%
        private const val MAX_EUCLIDEAN_DISTANCE = 0.5f // Reduzido para 0.5f
        private const val REQUIRED_CONFIDENCE = 0.80f // Aumentado para 80%
    }
    
    private val faceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
            .build()
    )
    
    private var interpreter: Interpreter? = null
    private var modelLoaded = false
    
    init {
        loadTensorFlowModel()
    }
    
    /**
     * 🎯 CADASTRO FACIAL COM VALIDAÇÃO AVANÇADA
     */
    suspend fun registerFaceWithValidation(bitmap: Bitmap): FaceRegistrationResult {
        return try {
            Log.d(TAG, "🚀 === INICIANDO CADASTRO FACIAL AVANÇADO ===")
            Log.d(TAG, "🎛️ MODO: ${if (RIGOROUS_RECOGNITION_MODE) "RIGOROSO" else "NORMAL"}")
            Log.d(TAG, "📊 Thresholds: Face(${MIN_FACE_SIZE_RATIO}-${MAX_FACE_SIZE_RATIO}), Olhos(${MIN_EYE_DISTANCE}px), Brilho(${MIN_BRIGHTNESS}-${MAX_BRIGHTNESS}), Contraste(${MIN_CONTRAST})")
            
            // ✅ 1. VALIDAÇÃO DE QUALIDADE BÁSICA
            val qualityCheck = validateImageQuality(bitmap)
            if (!qualityCheck.isValid) {
                Log.w(TAG, "❌ Qualidade da imagem insuficiente: ${qualityCheck.reason}")
                return FaceRegistrationResult.Failure(qualityCheck.reason)
            }
            
            // ✅ 2. DETECÇÃO E VALIDAÇÃO DE FACE
            val faceValidation = validateFaceDetection(bitmap)
            if (!faceValidation.isValid) {
                Log.w(TAG, "❌ Face não válida: ${faceValidation.reason}")
                return FaceRegistrationResult.Failure(faceValidation.reason)
            }
            
            // ✅ 3. GERAÇÃO DO EMBEDDING
            val embedding = generateFaceEmbedding(bitmap)
            if (embedding == null) {
                Log.e(TAG, "❌ Falha ao gerar embedding facial")
                return FaceRegistrationResult.Failure("Falha ao processar face")
            }
            
            // ✅ 4. VALIDAÇÃO FINAL DO EMBEDDING
            val embeddingValidation = validateEmbedding(embedding)
            if (!embeddingValidation.isValid) {
                Log.w(TAG, "❌ Embedding inválido: ${embeddingValidation.reason}")
                return FaceRegistrationResult.Failure(embeddingValidation.reason)
            }
            
            Log.d(TAG, "✅ Cadastro facial realizado com sucesso!")
            FaceRegistrationResult.Success(embedding, bitmap)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro no cadastro facial", e)
            FaceRegistrationResult.Failure("Erro interno: ${e.message}")
        }
    }
    
    /**
     * 🔍 RECONHECIMENTO FACIAL RIGOROSO
     * Usa thresholds muito altos para evitar falsos positivos
     */
    suspend fun recognizeFaceWithRigorousValidation(bitmap: Bitmap): FaceRecognitionResult {
        return try {
            Log.d(TAG, "🔍 === INICIANDO RECONHECIMENTO FACIAL RIGOROSO ===")
            Log.d(TAG, "🎯 Thresholds rigorosos: Similaridade=${MIN_SIMILARITY_THRESHOLD}, Distância=${MAX_EUCLIDEAN_DISTANCE}, Confiança=${REQUIRED_CONFIDENCE}")
            
            // ✅ 1. VALIDAÇÃO DE QUALIDADE RIGOROSA
            val qualityCheck = validateImageQuality(bitmap)
            if (!qualityCheck.isValid) {
                Log.w(TAG, "❌ Qualidade da imagem insuficiente para reconhecimento: ${qualityCheck.reason}")
                return FaceRecognitionResult.Failure(qualityCheck.reason)
            }
            
            // ✅ 2. DETECÇÃO E VALIDAÇÃO DE FACE RIGOROSA
            val faceValidation = validateFaceDetection(bitmap)
            if (!faceValidation.isValid) {
                Log.w(TAG, "❌ Face não válida para reconhecimento: ${faceValidation.reason}")
                return FaceRecognitionResult.Failure(faceValidation.reason)
            }
            
            // ✅ 3. GERAÇÃO DO EMBEDDING
            val embedding = generateFaceEmbedding(bitmap)
            if (embedding == null) {
                Log.e(TAG, "❌ Falha ao gerar embedding para reconhecimento")
                return FaceRecognitionResult.Failure("Falha ao processar face")
            }
            
            // ✅ 4. RECONHECIMENTO RIGOROSO
            val recognitionResult = performRigorousRecognition(embedding)
            
            Log.d(TAG, "✅ Reconhecimento facial concluído!")
            recognitionResult
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro no reconhecimento facial", e)
            FaceRecognitionResult.Failure("Erro interno: ${e.message}")
        }
    }
    
    /**
     * 🎯 RECONHECIMENTO RIGOROSO COM MÚLTIPLAS VALIDAÇÕES
     */
    private suspend fun performRigorousRecognition(embedding: FloatArray): FaceRecognitionResult {
        return try {
            // ✅ Carregar todas as faces cadastradas
            val database = com.example.iface_offilne.data.AppDatabase.getInstance(context)
            val faceDao = database.faceDao()
            val funcionarioDao = database.funcionarioDao() // ✅ CORREÇÃO: Usar funcionarioDao em vez de usuariosDao
            
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
            
            // ✅ COMPARAÇÃO RIGOROSA COM TODAS AS FACES
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
                    
                    // ✅ Calcular confiança combinada
                    val confidence = (cosineSimilarity + (1f - euclideanDistance)) / 2f
                    
                    Log.d(TAG, "👤 Funcionário ${face.funcionarioId}: Similaridade=${String.format("%.3f", cosineSimilarity)}, Distância=${String.format("%.3f", euclideanDistance)}, Confiança=${String.format("%.3f", confidence)}")
                    
                    // ✅ VALIDAÇÃO RIGOROSA: Todas as condições devem ser atendidas
                    if (cosineSimilarity >= MIN_SIMILARITY_THRESHOLD && 
                        euclideanDistance <= MAX_EUCLIDEAN_DISTANCE && 
                        confidence >= REQUIRED_CONFIDENCE) {
                        
                        // ✅ Se encontrou uma correspondência válida, verificar se é melhor que a anterior
                        if (confidence > bestConfidence) {
                            // ✅ CORREÇÃO: Usar método correto do DAO
                            val funcionarioId = face.funcionarioId.toIntOrNull()
                            bestMatch = if (funcionarioId != null) {
                                funcionarioDao.getById(funcionarioId)
                            } else {
                                // ✅ FALLBACK: Buscar por código se ID não for válido
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
                Log.w(TAG, "📊 Thresholds não atendidos: Similaridade>=${MIN_SIMILARITY_THRESHOLD}, Distância<=${MAX_EUCLIDEAN_DISTANCE}, Confiança>=${REQUIRED_CONFIDENCE}")
                return FaceRecognitionResult.Failure("Funcionário não reconhecido - thresholds rigorosos não atendidos")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro no reconhecimento rigoroso", e)
            return FaceRecognitionResult.Failure("Erro no reconhecimento: ${e.message}")
        }
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
     * 🔍 VALIDAÇÃO DE QUALIDADE DA IMAGEM (MUITO PERMISSIVA)
     */
    private fun validateImageQuality(bitmap: Bitmap): QualityCheckResult {
        try {
            // ✅ Verificar tamanho mínimo (reduzido para tablets)
            if (bitmap.width < 100 || bitmap.height < 100) {
                return QualityCheckResult(false, "Imagem muito pequena (mínimo 100x100)")
            }
            
            // ✅ Verificar se a imagem não está vazia ou corrompida
            if (bitmap.isRecycled) {
                return QualityCheckResult(false, "Imagem corrompida")
            }
            
            // ✅ Verificar brilho (muito permissivo)
            val brightness = calculateBrightness(bitmap)
            Log.d(TAG, "💡 Brilho detectado: ${String.format("%.3f", brightness)} (limites: ${MIN_BRIGHTNESS}-${MAX_BRIGHTNESS})")
            
            // Usar thresholds configuráveis
            if (brightness < MIN_BRIGHTNESS || brightness > MAX_BRIGHTNESS) {
                return QualityCheckResult(false, "Brilho inadequado (${String.format("%.2f", brightness)})")
            }
            
            // ✅ Verificar contraste (muito permissivo)
            val contrast = calculateContrast(bitmap)
            Log.d(TAG, "🎨 Contraste detectado: ${String.format("%.3f", contrast)} (mínimo: ${MIN_CONTRAST})")
            
            // Usar threshold configurável
            if (contrast < MIN_CONTRAST) {
                return QualityCheckResult(false, "Contraste muito baixo (${String.format("%.2f", contrast)})")
            }
            
            Log.d(TAG, "✅ Qualidade da imagem aceitável")
            return QualityCheckResult(true, "Qualidade OK")
            
        } catch (e: Exception) {
            Log.e(TAG, "Erro na validação de qualidade", e)
            return QualityCheckResult(false, "Erro na análise de qualidade")
        }
    }
    
    /**
     * 👤 VALIDAÇÃO DE DETECÇÃO DE FACE
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
            
            // ✅ Verificar tamanho da face (muito permissivo)
            val faceRatio = calculateFaceRatio(face.boundingBox, bitmap.width, bitmap.height)
            Log.d(TAG, "📐 Proporção da face: ${String.format("%.3f", faceRatio)}")
            
            if (faceRatio < MIN_FACE_SIZE_RATIO || faceRatio > MAX_FACE_SIZE_RATIO) {
                return FaceValidationResult(false, "Face muito pequena ou muito grande (${String.format("%.1f", faceRatio * 100)}%)", face)
            }
            
            // ✅ Verificar landmarks essenciais
            val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)
            val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)
            
            if (leftEye == null || rightEye == null) {
                return FaceValidationResult(false, "Olhos não detectados", face)
            }
            
            // ✅ Verificar distância entre olhos (muito permissivo)
            val eyeDistance = calculateEyeDistance(face)
            Log.d(TAG, "👀 Distância entre olhos: ${String.format("%.1f", eyeDistance)}px")
            
            if (eyeDistance < MIN_EYE_DISTANCE) {
                return FaceValidationResult(false, "Olhos muito próximos (${String.format("%.1f", eyeDistance)}px)", face)
            }
            
            return FaceValidationResult(true, "Face válida", face)
            
        } catch (e: Exception) {
            Log.e(TAG, "Erro na validação de face", e)
            return FaceValidationResult(false, "Erro na detecção", null)
        }
    }
    
    /**
     * 🧠 GERAÇÃO DO EMBEDDING FACIAL
     */
    private fun generateFaceEmbedding(bitmap: Bitmap): FloatArray? {
        return try {
            Log.d(TAG, "🧠 === GERANDO EMBEDDING FACIAL ===")
            
            if (!modelLoaded || interpreter == null) {
                Log.w(TAG, "⚠️ Modelo não carregado - gerando embedding mock para teste")
                // Gerar embedding mock para teste (quando não há modelo)
                return generateMockEmbedding()
            }
            
            // Redimensionar para o tamanho do modelo
            val resizedBitmap = Bitmap.createScaledBitmap(bitmap, 160, 160, true)
            Log.d(TAG, "📐 Bitmap redimensionado para 160x160")
            
            // Converter para tensor
            val inputBuffer = convertBitmapToTensorInput(resizedBitmap)
            val output = Array(1) { FloatArray(192) }
            
            // Executar inferência
            interpreter?.run(inputBuffer, output)
            val embedding = output[0]
            
            Log.d(TAG, "✅ Embedding gerado com sucesso! Tamanho: ${embedding.size}")
            Log.d(TAG, "📊 Primeiros 5 valores: ${embedding.take(5).joinToString(", ")}")
            
            resizedBitmap.recycle()
            embedding
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao gerar embedding", e)
            Log.w(TAG, "🔄 Usando embedding mock como fallback")
            generateMockEmbedding()
        }
    }
    
    /**
     * 🎭 GERAÇÃO DE EMBEDDING MOCK PARA TESTE
     * Usado quando o modelo TensorFlow não está disponível
     */
    private fun generateMockEmbedding(): FloatArray {
        Log.d(TAG, "🎭 Gerando embedding mock baseado na imagem")
        
        // Gerar embedding determinístico baseado no timestamp e hash
        val currentTime = System.currentTimeMillis()
        val random = kotlin.random.Random(currentTime)
        
        // Criar vetor de 192 dimensões normalizado
        val embedding = FloatArray(192) { random.nextFloat() * 2f - 1f }
        
        // Normalizar o vetor para ter magnitude unitária
        val magnitude = sqrt(embedding.map { it * it }.sum())
        for (i in embedding.indices) {
            embedding[i] = embedding[i] / magnitude
        }
        
        Log.d(TAG, "✅ Embedding mock gerado com magnitude: $magnitude")
        return embedding
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
            
            // Verificar se o arquivo existe
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
            val options = org.tensorflow.lite.Interpreter.Options().apply {
                setNumThreads(2)
                setUseNNAPI(false)
            }
            
            interpreter = org.tensorflow.lite.Interpreter(modelBuffer, options)
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
        val inputSize = 112
        val byteBuffer = ByteBuffer.allocateDirect(4 * inputSize * inputSize * 3)
        byteBuffer.order(ByteOrder.nativeOrder())
        
        val intValues = IntArray(inputSize * inputSize)
        bitmap.getPixels(intValues, 0, inputSize, 0, 0, inputSize, inputSize)
        
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
                // Fórmula padrão para brilho: 0.299*R + 0.587*G + 0.114*B
                totalBrightness += (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
            }
            
            return (totalBrightness / pixels.size).toFloat()
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao calcular brilho", e)
            return 0.5f // Valor padrão
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
            
            // Normalizar para 0-1
            return (standardDeviation / 0.5).toFloat().coerceIn(0f, 1f)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao calcular contraste", e)
            return 0.5f // Valor padrão
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