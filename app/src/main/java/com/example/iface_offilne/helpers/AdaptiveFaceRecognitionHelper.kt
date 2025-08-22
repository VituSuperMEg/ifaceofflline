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
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sqrt
import kotlinx.coroutines.tasks.await
import com.example.iface_offilne.helpers.PerformanceLevel


/**
 * 🚀 HELPER ADAPTATIVO PARA RECONHECIMENTO FACIAL
 * 
 * Sistema inteligente que ajusta automaticamente os parâmetros
 * de reconhecimento facial baseado nas capacidades do dispositivo
 * 
 * ✅ Funciona em dispositivos de baixo desempenho
 * ✅ Mantém precisão em dispositivos potentes
 * ✅ Otimização automática de performance
 */
class AdaptiveFaceRecognitionHelper(private val context: Context) {
    
    companion object {
        private const val TAG = "AdaptiveFaceRecognition"
    }
    
    private val deviceCapabilityHelper = DeviceCapabilityHelper(context)
    private val adaptiveConfig = deviceCapabilityHelper.getAdaptiveFaceRecognitionConfig()
    private val deviceInfo = deviceCapabilityHelper.getDeviceInfo()
    
    private val faceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(
                when (adaptiveConfig.imageQuality) {
                    DeviceCapabilityHelper.ImageQuality.LOW -> FaceDetectorOptions.PERFORMANCE_MODE_FAST
                    DeviceCapabilityHelper.ImageQuality.MEDIUM -> FaceDetectorOptions.PERFORMANCE_MODE_FAST
                    DeviceCapabilityHelper.ImageQuality.HIGH -> FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE
                }
            )
            .setLandmarkMode(
                when (adaptiveConfig.imageQuality) {
                    DeviceCapabilityHelper.ImageQuality.LOW -> FaceDetectorOptions.LANDMARK_MODE_NONE
                    DeviceCapabilityHelper.ImageQuality.MEDIUM -> FaceDetectorOptions.LANDMARK_MODE_ALL
                    DeviceCapabilityHelper.ImageQuality.HIGH -> FaceDetectorOptions.LANDMARK_MODE_ALL
                }
            )
            .setClassificationMode(
                when (adaptiveConfig.imageQuality) {
                    DeviceCapabilityHelper.ImageQuality.LOW -> FaceDetectorOptions.CLASSIFICATION_MODE_NONE
                    DeviceCapabilityHelper.ImageQuality.MEDIUM -> FaceDetectorOptions.CLASSIFICATION_MODE_ALL
                    DeviceCapabilityHelper.ImageQuality.HIGH -> FaceDetectorOptions.CLASSIFICATION_MODE_ALL
                }
            )
            .setMinFaceSize(
                when (adaptiveConfig.imageQuality) {
                    DeviceCapabilityHelper.ImageQuality.LOW -> 0.05f
                    DeviceCapabilityHelper.ImageQuality.MEDIUM -> 0.1f
                    DeviceCapabilityHelper.ImageQuality.HIGH -> 0.15f
                }
            )
            .build()
    )
    
    private var interpreter: Interpreter? = null
    private var modelLoaded = false
    
    init {
        Log.d(TAG, "🚀 === INICIANDO HELPER ADAPTATIVO ===")
        Log.d(TAG, "📊 Dispositivo: ${deviceInfo.performanceLevel} (Score: ${String.format("%.1f", deviceInfo.deviceScore)}/100)")
        Log.d(TAG, "💾 Memória: ${String.format("%.1f", deviceInfo.memoryInfo.totalGB)}GB")
        Log.d(TAG, "🖥️ CPU: ${deviceInfo.cpuInfo.cores} cores")
        Log.d(TAG, "🤖 Android: ${deviceInfo.androidRelease} (API ${deviceInfo.androidVersion})")
        
        loadTensorFlowModel()
    }
    
    /**
     * 🔍 RECONHECIMENTO FACIAL ADAPTATIVO
     * Ajusta automaticamente a qualidade baseado no dispositivo
     */
    suspend fun recognizeFaceAdaptive(faceBitmap: Bitmap): FaceRecognitionResult {
        return try {
            Log.d(TAG, "🔍 === RECONHECIMENTO FACIAL ADAPTATIVO ===")
            
            // ✅ OBTER CONFIGURAÇÃO ADAPTATIVA
            val config = deviceCapabilityHelper.getAdaptiveFaceRecognitionConfig()
            Log.d(TAG, "🎛️ Configuração: ${deviceCapabilityHelper.getDeviceInfo()?.performanceLevel}")
            Log.d(TAG, "📊 Thresholds: Similaridade>=${config.minSimilarityThreshold}, Distância<=${config.maxEuclideanDistance}, Confiança>=${config.requiredConfidence}")
            
            // ✅ VALIDAR BITMAP DE ENTRADA
            if (faceBitmap.isRecycled || faceBitmap.width <= 0 || faceBitmap.height <= 0) {
                Log.e(TAG, "❌ Bitmap inválido: reciclado=${faceBitmap.isRecycled}, dimensões=${faceBitmap.width}x${faceBitmap.height}")
                return FaceRecognitionResult.Failure("Bitmap inválido")
            }
            
            Log.d(TAG, "📸 Processando face: ${faceBitmap.width}x${faceBitmap.height}")
            
            // ✅ CALCULAR QUALIDADE DA IMAGEM
            val quality = calculateImageQuality(faceBitmap)
            Log.d(TAG, "📊 Qualidade: Brilho=${String.format("%.3f", quality.brightness)}, Contraste=${String.format("%.3f", quality.contrast)}")
            
            // ✅ ACEITAR FACE JÁ DETECTADA - NÃO TENTAR DETECTAR NOVAMENTE
            Log.d(TAG, "✅ Face já detectada e recortada - processando diretamente")
            
            // ✅ GERAR EMBEDDING
            val embedding = try {
                generateEmbedding(faceBitmap)
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao gerar embedding: ${e.message}")
                return FaceRecognitionResult.Failure("Erro ao gerar embedding: ${e.message}")
            }
            
            if (embedding.isEmpty()) {
                Log.e(TAG, "❌ Embedding vazio")
                return FaceRecognitionResult.Failure("Embedding vazio")
            }
            
            Log.d(TAG, "✅ Embedding gerado: ${embedding.size} dimensões")
            
            // ✅ EXECUTAR RECONHECIMENTO
            val recognitionResult = performAdaptiveRecognition(embedding)
            
            when (recognitionResult) {
                is FaceRecognitionResult.Success -> {
                    Log.d(TAG, "✅ RECONHECIMENTO BEM-SUCEDIDO!")
                    Log.d(TAG, "👤 Funcionário: ${recognitionResult.funcionario.nome}")
                    Log.d(TAG, "📊 Métricas: Similaridade=${String.format("%.3f", recognitionResult.similarity)}, Distância=${String.format("%.3f", recognitionResult.euclideanDistance)}, Confiança=${String.format("%.3f", recognitionResult.confidence)}")
                }
                is FaceRecognitionResult.Failure -> {
                    Log.w(TAG, "❌ RECONHECIMENTO FALHOU: ${recognitionResult.reason}")
                }
            }
            
            recognitionResult
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro crítico no reconhecimento adaptativo: ${e.message}")
            e.printStackTrace()
            FaceRecognitionResult.Failure("Erro interno: ${e.message}")
        }
    }
    
    /**
     * 🎯 RECONHECIMENTO ADAPTATIVO COM CONFIGURAÇÕES DINÂMICAS
     */
    private suspend fun performAdaptiveRecognition(embedding: FloatArray): FaceRecognitionResult {
        return try {
            Log.d(TAG, "🔍 === INICIANDO RECONHECIMENTO ADAPTATIVO ===")
            
            // ✅ Carregar dados do banco
            val db = com.example.iface_offilne.data.AppDatabase.getInstance(context)
            val faceDao = db.faceDao()
            val funcionarioDao = db.usuariosDao()
            
            val faces = faceDao.getAllFaces()
            val funcionarios = funcionarioDao.getUsuario()
            
            Log.d(TAG, "📊 Faces cadastradas no banco: ${faces.size}")
            Log.d(TAG, "👥 Funcionários cadastrados: ${funcionarios.size}")
            
            if (faces.isEmpty()) {
                Log.e(TAG, "❌ NENHUMA FACE CADASTRADA NO BANCO!")
                return FaceRecognitionResult.Failure("Nenhuma face cadastrada no sistema")
            }
            
            if (funcionarios.isEmpty()) {
                Log.e(TAG, "❌ NENHUM FUNCIONÁRIO CADASTRADO NO BANCO!")
                return FaceRecognitionResult.Failure("Nenhum funcionário cadastrado no sistema")
            }
            
            Log.d(TAG, "📐 Embedding de entrada: ${embedding.size} dimensões")
            Log.d(TAG, "🎯 Thresholds: Similaridade≥${adaptiveConfig.minSimilarityThreshold}, Distância≤${adaptiveConfig.maxEuclideanDistance}, Confiança≥${adaptiveConfig.requiredConfidence}")
            
            // ✅ VARIÁVEIS PARA MELHOR MATCH
            var bestMatch: com.example.iface_offilne.data.FuncionariosEntity? = null
            var bestSimilarity = 0f
            var bestEuclideanDistance = Float.MAX_VALUE
            var bestConfidence = 0f
            var allResults = mutableListOf<String>()
            
            // ✅ COMPARAÇÃO ADAPTATIVA
            for ((index, face) in faces.withIndex()) {
                try {
                    Log.d(TAG, "🔄 Processando face ${index + 1}/${faces.size}: ID=${face.funcionarioId}")
                    
                    val storedEmbedding = parseEmbedding(face.embedding)
                    if (storedEmbedding == null) {
                        Log.w(TAG, "⚠️ Embedding inválido para funcionário ${face.funcionarioId}")
                        allResults.add("Face ${face.funcionarioId}: EMBEDDING INVÁLIDO")
                        continue
                    }
                    
                    Log.d(TAG, "📐 Embedding armazenado: ${storedEmbedding.size} dimensões")
                    
                    // ✅ Calcular métricas
                    val cosineSimilarity = calculateCosineSimilarity(embedding, storedEmbedding)
                    val euclideanDistance = calculateEuclideanDistance(embedding, storedEmbedding)
                    val confidence = (cosineSimilarity + (1f - (euclideanDistance / 2f))) / 2f // Normalizar distância
                    
                    val resultado = "Face ${face.funcionarioId}: Sim=${String.format("%.3f", cosineSimilarity)}, Dist=${String.format("%.3f", euclideanDistance)}, Conf=${String.format("%.3f", confidence)}"
                    allResults.add(resultado)
                    Log.d(TAG, "📊 $resultado")
                    
                    // ✅ VALIDAÇÃO ADAPTATIVA: Usar thresholds baseados no dispositivo
                    val similarityOk = cosineSimilarity >= adaptiveConfig.minSimilarityThreshold
                    val distanceOk = euclideanDistance <= adaptiveConfig.maxEuclideanDistance
                    val confidenceOk = confidence >= adaptiveConfig.requiredConfidence
                    
                    Log.d(TAG, "✅ Validações: Sim=${similarityOk}, Dist=${distanceOk}, Conf=${confidenceOk}")
                    
                    if (similarityOk && distanceOk && confidenceOk) {
                        Log.d(TAG, "🎯 CANDIDATO VÁLIDO encontrado!")
                        
                        // ✅ Se encontrou uma correspondência válida, verificar se é melhor
                        if (confidence > bestConfidence) {
                            val funcionarioId = face.funcionarioId.toIntOrNull()
                            val funcionarioEncontrado = if (funcionarioId != null) {
                                funcionarios.find { it.id == funcionarioId }
                            } else {
                                funcionarios.find { it.codigo == face.funcionarioId }
                            }
                            
                            if (funcionarioEncontrado != null) {
                                bestMatch = funcionarioEncontrado
                                bestSimilarity = cosineSimilarity
                                bestEuclideanDistance = euclideanDistance
                                bestConfidence = confidence
                                
                                Log.d(TAG, "🎯 NOVO MELHOR MATCH: ${bestMatch.nome} (Confiança: ${String.format("%.3f", confidence)})")
                            } else {
                                Log.w(TAG, "⚠️ Funcionário não encontrado para ID: ${face.funcionarioId}")
                            }
                        }
                    } else {
                        Log.d(TAG, "❌ Candidato rejeitado pelos thresholds")
                    }
                    
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Erro ao comparar com face ${face.funcionarioId}: ${e.message}")
                    allResults.add("Face ${face.funcionarioId}: ERRO - ${e.message}")
                }
            }
            
            // ✅ LOG DETALHADO DE TODOS OS RESULTADOS
            Log.d(TAG, "📋 === RESUMO DE TODAS AS COMPARAÇÕES ===")
            allResults.forEach { resultado ->
                Log.d(TAG, "📊 $resultado")
            }
            
            // ✅ RESULTADO FINAL
            if (bestMatch != null) {
                Log.d(TAG, "✅ === RECONHECIMENTO BEM-SUCEDIDO ===")
                Log.d(TAG, "👤 Funcionário: ${bestMatch.nome}")
                Log.d(TAG, "🆔 ID: ${bestMatch.id}, Código: ${bestMatch.codigo}")
                Log.d(TAG, "📊 Métricas Finais: Similaridade=${String.format("%.3f", bestSimilarity)}, Distância=${String.format("%.3f", bestEuclideanDistance)}, Confiança=${String.format("%.3f", bestConfidence)}")
                
                return FaceRecognitionResult.Success(
                    funcionario = bestMatch,
                    similarity = bestSimilarity,
                    euclideanDistance = bestEuclideanDistance,
                    confidence = bestConfidence
                )
            } else {
                Log.w(TAG, "❌ === NENHUM FUNCIONÁRIO RECONHECIDO ===")
                Log.w(TAG, "📊 Thresholds: Similaridade≥${adaptiveConfig.minSimilarityThreshold}, Distância≤${adaptiveConfig.maxEuclideanDistance}, Confiança≥${adaptiveConfig.requiredConfidence}")
                Log.w(TAG, "📋 Motivo: Nenhuma face atendeu aos critérios mínimos")
                
                return FaceRecognitionResult.Failure("Face não reconhecida - nenhuma correspondência encontrada nos ${faces.size} registros")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro crítico no reconhecimento adaptativo", e)
            e.printStackTrace()
            return FaceRecognitionResult.Failure("Erro interno do sistema: ${e.message}")
        }
    }
    
    /**
     * 🖼️ PROCESSAMENTO DE IMAGEM ADAPTATIVO
     */
    private fun processImageAdaptive(bitmap: Bitmap): Bitmap? {
        return try {
            Log.d(TAG, "🖼️ Processando imagem adaptativamente...")
            
            // ✅ Redimensionar baseado na qualidade do dispositivo
            val targetSize = adaptiveConfig.maxImageSize
            val processedBitmap = if (bitmap.width != targetSize || bitmap.height != targetSize) {
                Log.d(TAG, "📏 Redimensionando de ${bitmap.width}x${bitmap.height} para ${targetSize}x${targetSize}")
                Bitmap.createScaledBitmap(bitmap, targetSize, targetSize, true)
            } else {
                bitmap
            }
            
            Log.d(TAG, "✅ Imagem processada: ${processedBitmap.width}x${processedBitmap.height}")
            processedBitmap
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro no processamento de imagem: ${e.message}")
            null
        }
    }
    
    /**
     * 📐 VALIDAÇÃO DE QUALIDADE ADAPTATIVA
     */
    private suspend fun validateImageQualityAdaptive(bitmap: Bitmap): QualityCheckResult {
        return try {
            // ✅ Verificar tamanho mínimo
            if (bitmap.width < 100 || bitmap.height < 100) {
                return QualityCheckResult(false, "Imagem muito pequena (${bitmap.width}x${bitmap.height})")
            }
            
            // ✅ Verificar se não está reciclada
            if (bitmap.isRecycled) {
                return QualityCheckResult(false, "Imagem reciclada")
            }
            
            // ✅ Verificar brilho e contraste (mais permissivo para dispositivos fracos)
            val brightness = calculateBrightness(bitmap)
            val contrast = calculateContrast(bitmap)
            
            Log.d(TAG, "📊 Qualidade: Brilho=${String.format("%.3f", brightness)}, Contraste=${String.format("%.3f", contrast)}")
            
            if (brightness < adaptiveConfig.minBrightness || brightness > adaptiveConfig.maxBrightness) {
                return QualityCheckResult(false, "Brilho inadequado (${String.format("%.3f", brightness)})")
            }
            
            if (contrast < adaptiveConfig.minContrast) {
                return QualityCheckResult(false, "Contraste baixo (${String.format("%.3f", contrast)})")
            }
            
            QualityCheckResult(true, "Qualidade adequada")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro na validação de qualidade: ${e.message}")
            QualityCheckResult(false, "Erro na validação: ${e.message}")
        }
    }
    
    /**
     * 👤 VALIDAÇÃO DE DETECÇÃO DE FACE ADAPTATIVA
     */
    private suspend fun validateFaceDetectionAdaptive(bitmap: Bitmap): FaceValidationResult {
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
            
            // ✅ Verificar tamanho da face (adaptativo)
            val faceRatio = calculateFaceRatio(face.boundingBox, bitmap.width, bitmap.height)
            Log.d(TAG, "📐 Proporção da face: ${String.format("%.3f", faceRatio)}")
            
            if (faceRatio < adaptiveConfig.minFaceSizeRatio || faceRatio > adaptiveConfig.maxFaceSizeRatio) {
                return FaceValidationResult(false, "Face muito pequena ou muito grande (${String.format("%.1f", faceRatio * 100)}%)", face)
            }
            
            // ✅ Verificar landmarks (mais permissivo para dispositivos fracos)
            if (adaptiveConfig.imageQuality != DeviceCapabilityHelper.ImageQuality.LOW) {
                val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)
                val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)
                
                if (leftEye == null || rightEye == null) {
                    return FaceValidationResult(false, "Olhos não detectados", face)
                }
                
                val eyeDistance = calculateEyeDistance(face)
                Log.d(TAG, "👀 Distância entre olhos: ${String.format("%.1f", eyeDistance)}px")
                
                if (eyeDistance < adaptiveConfig.minEyeDistance) {
                    return FaceValidationResult(false, "Olhos muito próximos (${String.format("%.1f", eyeDistance)}px)", face)
                }
            }
            
            return FaceValidationResult(true, "Face válida", face)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro na validação de face: ${e.message}")
            return FaceValidationResult(false, "Erro na detecção: ${e.message}", null)
        }
    }
    
    /**
     * 🤖 CARREGAR MODELO TENSORFLOW ADAPTATIVO
     */
    private fun loadTensorFlowModel() {
        Log.d(TAG, "🤖 Carregando modelo TensorFlow adaptativo...")
        
        try {
            // ✅ Usar configurações otimizadas para o dispositivo
            val options = Interpreter.Options().apply {
                setNumThreads(
                    when (deviceInfo.performanceLevel) {
                        PerformanceLevel.LOW -> 1
                        PerformanceLevel.MEDIUM -> 2
                        PerformanceLevel.HIGH -> 4
                        else -> 2 // Fallback para casos inesperados
                    }
                )
                setUseNNAPI(adaptiveConfig.useTensorFlowOptimizations)
                setAllowFp16PrecisionForFp32(adaptiveConfig.useTensorFlowOptimizations)
                setAllowBufferHandleOutput(false)
            }
            
            // ✅ Carregar modelo
            val modelFile = try {
                context.assets.open("facenet_model.tflite")
            } catch (e: Exception) {
                context.resources.openRawResource(com.example.iface_offilne.R.raw.mobilefacenet)
            }
            
            val modelBuffer = modelFile.use { input ->
                val bytes = ByteArray(input.available())
                input.read(bytes)
                ByteBuffer.allocateDirect(bytes.size).apply {
                    order(ByteOrder.nativeOrder())
                    put(bytes)
                    rewind()
                }
            }
            
            interpreter = Interpreter(modelBuffer, options)
            modelLoaded = true
            
            Log.d(TAG, "✅ Modelo TensorFlow carregado com configurações adaptativas")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao carregar modelo TensorFlow: ${e.message}")
            modelLoaded = false
        }
    }
    
    /**
     * 🔢 GERAR EMBEDDING FACIAL
     */
    private fun generateFaceEmbedding(bitmap: Bitmap): FloatArray? {
        return try {
            if (!modelLoaded || interpreter == null) {
                Log.e(TAG, "❌ Modelo não carregado")
                return null
            }
            
            val inputTensor = convertBitmapToTensorInput(bitmap)
            val output = Array(1) { FloatArray(512) } // Tamanho padrão do embedding
            
            interpreter?.run(inputTensor, output)
            
            val embedding = output[0]
            Log.d(TAG, "✅ Embedding gerado: ${embedding.size} dimensões")
            
            embedding
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao gerar embedding: ${e.message}")
            null
        }
    }
    
    /**
     * 📊 CALCULAR QUALIDADE DA IMAGEM
     */
    private fun calculateImageQuality(bitmap: Bitmap): ImageQuality {
        return try {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            
            var totalBrightness = 0f
            var totalContrast = 0f
            
            for (pixel in pixels) {
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                
                val brightness = (r + g + b) / 3f / 255f
                totalBrightness += brightness
            }
            
            val avgBrightness = totalBrightness / pixels.size
            
            // Calcular contraste simples
            val contrast = 0.5f // Valor padrão para simplicidade
            
            ImageQuality(avgBrightness, contrast)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao calcular qualidade: ${e.message}")
            ImageQuality(0.5f, 0.5f) // Valores padrão
        }
    }
    
    /**
     * 🤖 GERAR EMBEDDING DA FACE
     */
    private fun generateEmbedding(bitmap: Bitmap): FloatArray {
        return try {
            // ✅ REDIMENSIONAR PARA O TAMANHO DO MODELO
            val resizedBitmap = if (bitmap.width != 160 || bitmap.height != 160) {
                Bitmap.createScaledBitmap(bitmap, 160, 160, true)
            } else {
                bitmap
            }
            
            // ✅ CONVERTER PARA TENSOR
            val inputTensor = convertBitmapToTensorInput(resizedBitmap)
            val output = Array(1) { FloatArray(512) }
            
            // ✅ EXECUTAR MODELO
            interpreter?.run(inputTensor, output)
            val embedding = output[0]
            
            // ✅ VALIDAR EMBEDDING
            if (embedding.isEmpty() || embedding.all { it == 0f } || embedding.any { it.isNaN() || it.isInfinite() }) {
                throw Exception("Embedding inválido gerado")
            }
            
            Log.d(TAG, "✅ Embedding gerado com sucesso: ${embedding.size} dimensões")
            embedding
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao gerar embedding: ${e.message}")
            throw e
        }
    }
    
    /**
     * 🔧 CONVERTER BITMAP PARA TENSOR
     */
    private fun convertBitmapToTensorInput(bitmap: Bitmap): ByteBuffer {
        val inputSize = 160
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
    
    // ========== MÉTODOS AUXILIARES ==========
    
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
    
    private fun calculateEuclideanDistance(embedding1: FloatArray, embedding2: FloatArray): Float {
        if (embedding1.size != embedding2.size) return Float.MAX_VALUE
        
        var sum = 0f
        for (i in embedding1.indices) {
            val diff = embedding1[i] - embedding2[i]
            sum += diff * diff
        }
        
        return sqrt(sum)
    }
    
    private fun parseEmbedding(embeddingString: String): FloatArray? {
        return try {
            embeddingString.split(",").map { it.trim().toFloat() }.toFloatArray()
        } catch (e: Exception) {
            null
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
    
    private fun calculateBrightness(bitmap: Bitmap): Float {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        
        var totalBrightness = 0f
        for (pixel in pixels) {
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            totalBrightness += (r + g + b) / 3f / 255f
        }
        
        return totalBrightness / pixels.size
    }
    
    private fun calculateContrast(bitmap: Bitmap): Float {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        
        var totalBrightness = 0f
        for (pixel in pixels) {
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            totalBrightness += (r + g + b) / 3f / 255f
        }
        
        val averageBrightness = totalBrightness / pixels.size
        
        var variance = 0f
        for (pixel in pixels) {
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            val brightness = (r + g + b) / 3f / 255f
            variance += (brightness - averageBrightness) * (brightness - averageBrightness)
        }
        
        return sqrt(variance / pixels.size)
    }
    
    // ========== CLASSES DE RESULTADO ==========
    
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
    
    /**
     * 📊 CLASSE PARA QUALIDADE DA IMAGEM
     */
    data class ImageQuality(
        val brightness: Float,
        val contrast: Float
    )
} 