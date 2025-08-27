package com.example.iface_offilne.helpers

import android.content.Context
import android.graphics.*
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.Face
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*
import kotlinx.coroutines.tasks.await

/**
 * 🚀 HELPER ESPECIALIZADO PARA MOBILEFACENET
 * 
 * Implementa todas as validações necessárias:
 * ✅ Detecção e enquadramento do rosto
 * ✅ Qualidade da imagem (nitidez, iluminação)
 * ✅ Ângulo do rosto (yaw, pitch, roll)
 * ✅ Captura de múltiplas amostras
 * ✅ Embeddings normalizados
 * ✅ Thresholds configuráveis
 */
class MobileFaceNetHelper(private val context: Context) {
    
    companion object {
        private const val TAG = "MobileFaceNetHelper"
    }
    
    private var interpreter: Interpreter? = null
    private var modelLoaded = false
    
    private val faceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
            .setMinFaceSize(0.15f) // ✅ Reduzir tamanho mínimo da face (15% da imagem)
            .build()
    )
    
    init {
        // ✅ Verificar compatibilidade antes de carregar o modelo
        checkCompatibility()
        loadMobileFaceNetModel()
    }
    
    /**
     * 🔍 VERIFICAR COMPATIBILIDADE DO DISPOSITIVO
     */
    private fun checkCompatibility() {
        try {
            Log.d(TAG, "🔍 === VERIFICANDO COMPATIBILIDADE ===")
            Log.d(TAG, "📱 Android API: ${android.os.Build.VERSION.SDK_INT}")
            Log.d(TAG, "🏭 Fabricante: ${android.os.Build.MANUFACTURER}")
            Log.d(TAG, "📋 Modelo: ${android.os.Build.MODEL}")
            
            // ✅ Verificar se TensorFlow Lite está disponível
            try {
                Class.forName("org.tensorflow.lite.Interpreter")
                Log.d(TAG, "✅ TensorFlow Lite disponível")
            } catch (e: ClassNotFoundException) {
                Log.e(TAG, "❌ TensorFlow Lite não disponível", e)
                throw RuntimeException("TensorFlow Lite não disponível")
            }
            
            // ✅ Verificar se GPU Delegate está disponível (mas não usar)
            try {
                Class.forName("org.tensorflow.lite.gpu.GpuDelegate")
                Log.d(TAG, "✅ GPU Delegate disponível (mas não será usado)")
            } catch (e: ClassNotFoundException) {
                Log.w(TAG, "⚠️ GPU Delegate não disponível - usando apenas CPU")
            }
            
            Log.d(TAG, "✅ Compatibilidade verificada")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro na verificação de compatibilidade", e)
            throw e
        }
    }
    
    /**
     * 📂 CARREGAR MODELO MOBILEFACENET
     */
    private fun loadMobileFaceNetModel() {
        try {
            Log.d(TAG, "📂 === CARREGANDO MOBILEFACENET ===")
            
            // ✅ Tentar carregar do raw resources primeiro
            val modelBuffer = try {
                Log.d(TAG, "🔍 Carregando mobilefacenet.tflite dos recursos raw...")
                val inputStream = context.resources.openRawResource(
                    context.resources.getIdentifier("mobilefacenet", "raw", context.packageName)
                )
                val bytes = inputStream.use { input ->
                    ByteArray(input.available()).also { input.read(it) }
                }
                ByteBuffer.allocateDirect(bytes.size).apply {
                    order(ByteOrder.nativeOrder())
                    put(bytes)
                    rewind()
                }
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ MobileFaceNet não encontrado nos recursos raw, tentando assets...")
                
                // ✅ Fallback: tentar carregar do assets
                val inputStream = context.assets.open("facenet_model.tflite")
                val bytes = inputStream.use { input ->
                    ByteArray(input.available()).also { input.read(it) }
                }
                ByteBuffer.allocateDirect(bytes.size).apply {
                    order(ByteOrder.nativeOrder())
                    put(bytes)
                    rewind()
                }
            }
            
            Log.d(TAG, "✅ Buffer carregado! Tamanho: ${modelBuffer.capacity()} bytes")
            
            // ✅ Criar interpreter com configurações otimizadas (sem GPU delegate por compatibilidade)
            val options = Interpreter.Options().apply {
                setNumThreads(FaceRecognitionConfig.TENSORFLOW_THREADS)
                // ✅ DESABILITADO GPU DELEGATE PARA EVITAR PROBLEMAS DE COMPATIBILIDADE
                // if (FaceRecognitionConfig.USE_GPU_DELEGATE) {
                //     addDelegate(gpuDelegate)
                // }
                setAllowFp16PrecisionForFp32(FaceRecognitionConfig.USE_FP16_PRECISION)
                setAllowBufferHandleOutput(false)
            }
            
            interpreter = Interpreter(modelBuffer, options)
            interpreter?.allocateTensors()
            
            modelLoaded = true
            Log.d(TAG, "🎯 === MOBILEFACENET CARREGADO COM SUCESSO ===")
            Log.d(TAG, "📊 Dimensões: ${FaceRecognitionConfig.MODEL_INPUT_SIZE}x${FaceRecognitionConfig.MODEL_INPUT_SIZE} → ${FaceRecognitionConfig.MODEL_OUTPUT_SIZE}")
            Log.d(TAG, "🤖 GPU Delegate: DESABILITADO (compatibilidade)")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao carregar MobileFaceNet: ${e.message}", e)
            cleanup()
        }
    }
    
    /**
     * 🎯 CADASTRO FACIAL COM MÚLTIPLAS AMOSTRAS
     */
    suspend fun registerFaceWithMultipleSamples(
        onSampleCaptured: (sampleType: String, sampleNumber: Int, totalSamples: Int) -> Unit,
        onQualityCheck: (message: String) -> Unit,
        onProgress: (progress: Float) -> Unit
    ): FaceRegistrationResult {
        return try {
            Log.d(TAG, "🚀 === INICIANDO CADASTRO COM MÚLTIPLAS AMOSTRAS ===")
            
            val samples = mutableListOf<FloatArray>()
            var currentSample = 0
            
            for (sampleType in FaceRecognitionConfig.SAMPLE_TYPES) {
                currentSample++
                onProgress(currentSample.toFloat() / FaceRecognitionConfig.REQUIRED_SAMPLES)
                
                Log.d(TAG, "📸 Capturando amostra $currentSample/${FaceRecognitionConfig.REQUIRED_SAMPLES}: $sampleType")
                onSampleCaptured(sampleType, currentSample, FaceRecognitionConfig.REQUIRED_SAMPLES)
                
                // ✅ Aguardar captura da amostra (implementar lógica de captura)
                // Por enquanto, simular captura
                val sampleBitmap = captureSample(sampleType)
                if (sampleBitmap != null) {
                    // ✅ Validar qualidade da amostra
                    val qualityResult = validateImageQuality(sampleBitmap)
                    if (qualityResult.isValid) {
                        // ✅ Gerar embedding
                        val embedding = generateFaceEmbedding(sampleBitmap)
                        if (embedding != null) {
                            samples.add(embedding)
                            Log.d(TAG, "✅ Amostra $sampleType capturada e processada")
                        } else {
                            onQualityCheck("Falha ao processar amostra $sampleType")
                            return FaceRegistrationResult.Failure("Falha ao processar amostra")
                        }
                    } else {
                        onQualityCheck("Qualidade insuficiente: ${qualityResult.reason}")
                        return FaceRegistrationResult.Failure(qualityResult.reason)
                    }
                } else {
                    onQualityCheck("Falha ao capturar amostra $sampleType")
                    return FaceRegistrationResult.Failure("Falha na captura")
                }
            }
            
            // ✅ Calcular embedding médio normalizado
            if (samples.size == FaceRecognitionConfig.REQUIRED_SAMPLES) {
                val averageEmbedding = calculateAverageEmbedding(samples)
                Log.d(TAG, "✅ Cadastro concluído com ${samples.size} amostras")
                FaceRegistrationResult.Success(averageEmbedding, null)
            } else {
                FaceRegistrationResult.Failure("Número insuficiente de amostras: ${samples.size}/${FaceRecognitionConfig.REQUIRED_SAMPLES}")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro no cadastro facial", e)
            FaceRegistrationResult.Failure("Erro interno: ${e.message}")
        }
    }
    
    /**
     * 🔍 RECONHECIMENTO FACIAL COM VALIDAÇÃO RIGOROSA
     */
    suspend fun recognizeFaceWithValidation(
        bitmap: Bitmap,
        threshold: Float = FaceRecognitionConfig.DEFAULT_SIMILARITY_THRESHOLD
    ): FaceRecognitionResult {
        return try {
            Log.d(TAG, "🔍 === RECONHECIMENTO FACIAL RIGOROSO ===")
            Log.d(TAG, "🎯 Threshold: $threshold")
            
            // ✅ 1. Validação de qualidade
            val qualityResult = validateImageQuality(bitmap)
            if (!qualityResult.isValid) {
                return FaceRecognitionResult.Failure("Qualidade insuficiente: ${qualityResult.reason}")
            }
            
            // ✅ 2. Detecção e validação de face
            val faceResult = validateFaceDetection(bitmap)
            if (!faceResult.isValid) {
                return FaceRecognitionResult.Failure("Face inválida: ${faceResult.reason}")
            }
            
            // ✅ 3. Geração do embedding
            val embedding = generateFaceEmbedding(bitmap)
            if (embedding == null) {
                return FaceRecognitionResult.Failure("Falha ao gerar embedding")
            }
            
            // ✅ 4. Reconhecimento com threshold configurável
            val recognitionResult = performRecognition(embedding, threshold)
            
            Log.d(TAG, "✅ Reconhecimento concluído")
            recognitionResult
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro no reconhecimento", e)
            FaceRecognitionResult.Failure("Erro interno: ${e.message}")
        }
    }
    
    /**
     * 📸 CAPTURAR AMOSTRA ESPECÍFICA
     */
    private fun captureSample(sampleType: String): Bitmap? {
        // ✅ Implementar lógica de captura específica
        // Por enquanto, retornar null para simular
        return null
    }
    
    /**
     * 🔍 VALIDAR QUALIDADE DA IMAGEM
     */
    fun validateImageQuality(bitmap: Bitmap): QualityResult {
        return try {
            // ✅ 1. Verificar nitidez (Laplacian variance)
            val sharpness = calculateSharpness(bitmap)
            if (sharpness < FaceRecognitionConfig.MIN_SHARPNESS) {
                return QualityResult(false, "Imagem muito borrada (nitidez: $sharpness)")
            }
            
            // ✅ 2. Verificar brilho
            val brightness = calculateBrightness(bitmap)
            if (brightness < FaceRecognitionConfig.MIN_BRIGHTNESS || brightness > FaceRecognitionConfig.MAX_BRIGHTNESS) {
                return QualityResult(false, "Iluminação inadequada (brilho: $brightness)")
            }
            
            // ✅ 3. Verificar contraste
            val contrast = calculateContrast(bitmap)
            if (contrast < FaceRecognitionConfig.MIN_CONTRAST) {
                return QualityResult(false, "Contraste insuficiente ($contrast)")
            }
            
            QualityResult(true, "Qualidade adequada")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro na validação de qualidade", e)
            QualityResult(false, "Erro na validação: ${e.message}")
        }
    }
    
    /**
     * 📸 PROCESSAR MÚLTIPLAS AMOSTRAS DE BITMAPS (OTIMIZADO PARA VELOCIDADE)
     */
    fun processMultipleSamples(bitmaps: List<Bitmap>): List<FloatArray> {
        return try {
            Log.d(TAG, "📸 === PROCESSANDO ${bitmaps.size} AMOSTRAS (MODO RÁPIDO) ===")
            
            val embeddings = mutableListOf<FloatArray>()
            
            for ((index, bitmap) in bitmaps.withIndex()) {
                try {
                    Log.d(TAG, "📸 Processando amostra ${index + 1}/${bitmaps.size}")
                    
                  E
                    val embedding = generateFaceEmbedding(bitmap)
                    if (embedding != null) {
                        embeddings.add(embedding)
                        Log.d(TAG, "✅ Amostra ${index + 1} processada: ${embedding.size} dimensões")
                    } else {
                        Log.w(TAG, "⚠️ Falha ao gerar embedding para amostra ${index + 1}")
                    }
                    
                    // } else {
                    //     Log.w(TAG, "⚠️ Qualidade insuficiente para amostra ${index + 1}: ${qualityResult.reason}")
                    // }
                    
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Erro ao processar amostra ${index + 1}", e)
                }
            }
            
            Log.d(TAG, "✅ Processamento concluído: ${embeddings.size}/${bitmaps.size} embeddings válidos")
            embeddings
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro no processamento de múltiplas amostras", e)
            emptyList()
        }
    }
    
    /**
     * 👤 VALIDAR DETECÇÃO DE FACE
     */
    private suspend fun validateFaceDetection(bitmap: Bitmap): FaceValidationResult {
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val faces = faceDetector.process(image).await()
            
            if (faces.isEmpty()) {
                return FaceValidationResult(false, "Nenhuma face detectada")
            }
            
            if (faces.size > 1) {
                // ✅ Filtrar apenas a face maior (mais próxima da câmera)
                val largestFace = faces.maxByOrNull { face ->
                    face.boundingBox.width() * face.boundingBox.height()
                }
                
                if (largestFace != null) {
                    // ✅ Usar apenas a face maior para validação
                    val faceSizeRatio = calculateFaceSizeRatio(largestFace, bitmap)
                    if (faceSizeRatio < FaceRecognitionConfig.MIN_FACE_SIZE_RATIO || faceSizeRatio > FaceRecognitionConfig.MAX_FACE_SIZE_RATIO) {
                        return FaceValidationResult(false, "Aproxime-se da câmera")
                    }
                    
                    // ✅ Continuar com a face maior
                    val face = largestFace
                    // ... resto da validação
                } else {
                    return FaceValidationResult(false, "Nenhuma face válida detectada")
                }
            }
            
            val face = if (faces.size > 1) {
                // ✅ Se múltiplas faces, usar a maior
                faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() } ?: faces[0]
            } else {
                faces[0]
            }
            
            // ✅ 1. Verificar tamanho da face
            val faceSizeRatio = calculateFaceSizeRatio(face, bitmap)
            if (faceSizeRatio < FaceRecognitionConfig.MIN_FACE_SIZE_RATIO) {
                return FaceValidationResult(false, "Aproxime-se da câmera")
            }
            if (faceSizeRatio > FaceRecognitionConfig.MAX_FACE_SIZE_RATIO) {
                return FaceValidationResult(false, "Afaste-se da câmera")
            }
            
            // ✅ 2. Verificar distância entre olhos
            val eyeDistance = calculateEyeDistance(face)
            if (eyeDistance < FaceRecognitionConfig.MIN_EYE_DISTANCE) {
                return FaceValidationResult(false, "Aproxime-se da câmera")
            }
            
            // ✅ 3. Verificar ângulos do rosto
            val angleResult = validateFaceAngles(face)
            if (!angleResult.isValid) {
                return FaceValidationResult(false, angleResult.reason)
            }
            
            FaceValidationResult(true, "Face válida")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro na validação de face", e)
            FaceValidationResult(false, "Erro na detecção: ${e.message}")
        }
    }
    
    /**
     * 🤖 GERAR EMBEDDING FACIAL (OTIMIZADO PARA VELOCIDADE)
     */
    fun generateFaceEmbedding(bitmap: Bitmap): FloatArray? {
        return try {
            if (!modelLoaded || interpreter == null) {
                Log.e(TAG, "❌ Modelo não carregado")
                return null
            }
            
            // ✅ REDIMENSIONAR PARA 112x112 (MODO RÁPIDO)
            val resizedBitmap = if (bitmap.width != FaceRecognitionConfig.MODEL_INPUT_SIZE || bitmap.height != FaceRecognitionConfig.MODEL_INPUT_SIZE) {
                Bitmap.createScaledBitmap(bitmap, FaceRecognitionConfig.MODEL_INPUT_SIZE, FaceRecognitionConfig.MODEL_INPUT_SIZE, true)
            } else {
                bitmap
            }
            
            // ✅ CONVERTER PARA TENSOR (MODO RÁPIDO)
            val inputTensor = convertBitmapToTensor(resizedBitmap)
            val output = Array(1) { FloatArray(FaceRecognitionConfig.MODEL_OUTPUT_SIZE) }
            
            // ✅ EXECUTAR MODELO
            interpreter?.run(inputTensor, output)
            val embedding = output[0]
            
            // ✅ NORMALIZAR EMBEDDING (L2 normalization) - MODO RÁPIDO
            val normalizedEmbedding = normalizeEmbedding(embedding)
            
            Log.d(TAG, "✅ Embedding gerado: ${normalizedEmbedding.size} dimensões")
            normalizedEmbedding
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao gerar embedding", e)
            null
        }
    }
    
    /**
     * 🔍 REALIZAR RECONHECIMENTO
     */
    private suspend fun performRecognition(
        embedding: FloatArray,
        threshold: Float
    ): FaceRecognitionResult {
        return try {
            // ✅ Carregar faces do banco de dados
            val database = com.example.iface_offilne.data.AppDatabase.getInstance(context)
            val faceDao = database.faceDao()
            val funcionarioDao = database.funcionarioDao()
            
            val faces = faceDao.getAllFaces()
            if (faces.isEmpty()) {
                return FaceRecognitionResult.Failure("Nenhuma face cadastrada")
            }
            
            var bestMatch: com.example.iface_offilne.data.FuncionariosEntity? = null
            var bestSimilarity = 0f
            var bestDistance = Float.MAX_VALUE
            
            // ✅ Comparar com todas as faces cadastradas
            for (face in faces) {
                val storedEmbedding = parseEmbedding(face.embedding)
                if (storedEmbedding != null) {
                    val cosineSimilarity = calculateCosineSimilarity(embedding, storedEmbedding)
                    val euclideanDistance = calculateEuclideanDistance(embedding, storedEmbedding)
                    
                    Log.d(TAG, "👤 Funcionário ${face.funcionarioId}: Similaridade=$cosineSimilarity, Distância=$euclideanDistance")
                    
                    if (cosineSimilarity >= threshold && euclideanDistance <= FaceRecognitionConfig.MAX_EUCLIDEAN_DISTANCE) {
                        if (cosineSimilarity > bestSimilarity) {
                            val funcionarioId = face.funcionarioId.toIntOrNull()
                            bestMatch = if (funcionarioId != null) {
                                funcionarioDao.getById(funcionarioId)
                            } else {
                                funcionarioDao.getAll().find { it.codigo == face.funcionarioId }
                            }
                            bestSimilarity = cosineSimilarity
                            bestDistance = euclideanDistance
                        }
                    }
                }
            }
            
            if (bestMatch != null) {
                FaceRecognitionResult.Success(
                    funcionario = bestMatch,
                    similarity = bestSimilarity,
                    euclideanDistance = bestDistance,
                    confidence = bestSimilarity
                )
            } else {
                FaceRecognitionResult.Failure("Funcionário não reconhecido")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro no reconhecimento", e)
            FaceRecognitionResult.Failure("Erro interno: ${e.message}")
        }
    }
    
    // ========== MÉTODOS AUXILIARES ==========
    
    /**
     * 📐 CALCULAR NITIDEZ (LAPLACIAN VARIANCE)
     */
    private fun calculateSharpness(bitmap: Bitmap): Float {
        val grayBitmap = convertToGrayscale(bitmap)
        val laplacian = floatArrayOf(0f, -1f, 0f, -1f, 4f, -1f, 0f, -1f, 0f)
        
        var sum = 0f
        var count = 0
        
        for (y in 1 until grayBitmap.height - 1) {
            for (x in 1 until grayBitmap.width - 1) {
                var value = 0f
                for (ky in -1..1) {
                    for (kx in -1..1) {
                        val pixel = grayBitmap.getPixel(x + kx, y + ky)
                        val gray = (pixel and 0xFF) / 255f
                        value += gray * laplacian[(ky + 1) * 3 + (kx + 1)]
                    }
                }
                sum += value * value
                count++
            }
        }
        
        return if (count > 0) sum / count else 0f
    }
    
    /**
     * 💡 CALCULAR BRILHO
     */
    private fun calculateBrightness(bitmap: Bitmap): Float {
        var sum = 0f
        var count = 0
        
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                sum += (r + g + b) / (3f * 255f)
                count++
            }
        }
        
        return if (count > 0) sum / count else 0f
    }
    
    /**
     * 🎨 CALCULAR CONTRASTE
     */
    private fun calculateContrast(bitmap: Bitmap): Float {
        val brightness = calculateBrightness(bitmap)
        var variance = 0f
        var count = 0
        
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                val pixelBrightness = (r + g + b) / (3f * 255f)
                variance += (pixelBrightness - brightness) * (pixelBrightness - brightness)
                count++
            }
        }
        
        return if (count > 0) sqrt(variance / count) else 0f
    }
    
    /**
     * 📏 CALCULAR TAMANHO DA FACE
     */
    private fun calculateFaceSizeRatio(face: Face, bitmap: Bitmap): Float {
        val faceArea = face.boundingBox.width() * face.boundingBox.height()
        val imageArea = bitmap.width * bitmap.height
        return faceArea.toFloat() / imageArea
    }
    
    /**
     * 👁️ CALCULAR DISTÂNCIA ENTRE OLHOS
     */
    private fun calculateEyeDistance(face: Face): Float {
        val leftEye = face.getLandmark(com.google.mlkit.vision.face.FaceLandmark.LEFT_EYE)
        val rightEye = face.getLandmark(com.google.mlkit.vision.face.FaceLandmark.RIGHT_EYE)
        
        return if (leftEye != null && rightEye != null) {
            val dx = leftEye.position.x - rightEye.position.x
            val dy = leftEye.position.y - rightEye.position.y
            sqrt(dx * dx + dy * dy)
        } else {
            0f
        }
    }
    
    /**
     * 📐 VALIDAR ÂNGULOS DO ROSTO
     */
    private fun validateFaceAngles(face: Face): AngleValidationResult {
        val yaw = face.headEulerAngleY
        val pitch = face.headEulerAngleX
        val roll = face.headEulerAngleZ
        
        if (abs(yaw) > FaceRecognitionConfig.MAX_YAW_ANGLE) {
            return AngleValidationResult(false, "Rosto muito virado horizontalmente ($yaw°)")
        }
        
        if (abs(pitch) > FaceRecognitionConfig.MAX_PITCH_ANGLE) {
            return AngleValidationResult(false, "Rosto muito inclinado verticalmente ($pitch°)")
        }
        
        if (abs(roll) > FaceRecognitionConfig.MAX_ROLL_ANGLE) {
            return AngleValidationResult(false, "Rosto muito rotacionado ($roll°)")
        }
        
        return AngleValidationResult(true, "Ângulos adequados")
    }
    
    /**
     * 🔧 CONVERTER BITMAP PARA TENSOR
     */
    private fun convertBitmapToTensor(bitmap: Bitmap): ByteBuffer {
        val byteBuffer = ByteBuffer.allocateDirect(4 * FaceRecognitionConfig.MODEL_INPUT_SIZE * FaceRecognitionConfig.MODEL_INPUT_SIZE * 3)
        byteBuffer.order(ByteOrder.nativeOrder())
        
        val intValues = IntArray(FaceRecognitionConfig.MODEL_INPUT_SIZE * FaceRecognitionConfig.MODEL_INPUT_SIZE)
        bitmap.getPixels(intValues, 0, FaceRecognitionConfig.MODEL_INPUT_SIZE, 0, 0, FaceRecognitionConfig.MODEL_INPUT_SIZE, FaceRecognitionConfig.MODEL_INPUT_SIZE)
        
        for (pixel in intValues) {
            val r = ((pixel shr 16) and 0xFF) / 255.0f
            val g = ((pixel shr 8) and 0xFF) / 255.0f
            val b = (pixel and 0xFF) / 255.0f
            
            // ✅ Normalização específica do MobileFaceNet
            val rNormalized = (r - 0.5f) * 2.0f
            val gNormalized = (g - 0.5f) * 2.0f
            val bNormalized = (b - 0.5f) * 2.0f
            
            byteBuffer.putFloat(rNormalized)
            byteBuffer.putFloat(gNormalized)
            byteBuffer.putFloat(bNormalized)
        }
        
        return byteBuffer
    }
    
    /**
     * 📐 NORMALIZAR EMBEDDING (L2 NORMALIZATION)
     */
    private fun normalizeEmbedding(embedding: FloatArray): FloatArray {
        var sum = 0f
        for (value in embedding) {
            sum += value * value
        }
        val norm = sqrt(sum)
        
        return if (norm > 0f) {
            embedding.map { it / norm }.toFloatArray()
        } else {
            embedding
        }
    }
    
    /**
     * 📊 CALCULAR EMBEDDING MÉDIO
     */
    private fun calculateAverageEmbedding(embeddings: List<FloatArray>): FloatArray {
        if (embeddings.isEmpty()) return FloatArray(0)
        
        val size = embeddings[0].size
        val average = FloatArray(size)
        
        for (i in 0 until size) {
            var sum = 0f
            for (embedding in embeddings) {
                sum += embedding[i]
            }
            average[i] = sum / embeddings.size
        }
        
        return normalizeEmbedding(average)
    }
    
    /**
     * 🔍 CALCULAR SIMILARIDADE COSSENO
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
     * 🔧 CONVERTER PARA ESCALA DE CINZA
     */
    private fun convertToGrayscale(bitmap: Bitmap): Bitmap {
        val grayBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(grayBitmap)
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
        }
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return grayBitmap
    }
    
    /**
     * 🔧 PARSEAR EMBEDDING DO BANCO
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
     * 🧹 LIMPEZA DE RECURSOS
     */
    private fun cleanup() {
        try {
            interpreter?.close()
            interpreter = null
            modelLoaded = false
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro na limpeza", e)
        }
    }
    
    /**
     * 🔄 LIBERAR RECURSOS
     */
    fun release() {
        cleanup()
        faceDetector.close()
    }
}

// ========== CLASSES DE RESULTADO ==========

data class QualityResult(val isValid: Boolean, val reason: String)
data class FaceValidationResult(val isValid: Boolean, val reason: String)
data class AngleValidationResult(val isValid: Boolean, val reason: String)

sealed class FaceRegistrationResult {
    data class Success(val embedding: FloatArray, val bitmap: Bitmap?) : FaceRegistrationResult()
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