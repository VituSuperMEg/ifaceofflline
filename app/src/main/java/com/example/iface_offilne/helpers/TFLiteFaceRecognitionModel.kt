package com.example.iface_offilne.helpers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import kotlin.Pair
import org.tensorflow.lite.Interpreter
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*
import kotlin.math.sqrt
import kotlin.math.pow

/**
 * 🧠 MODELO DE RECONHECIMENTO FACIAL COM TENSORFLOW LITE
 * Baseado na implementação de SimilarityClassifier
 */
class TFLiteFaceRecognitionModel(private val context: Context) {
    
    companion object {
        private const val TAG = "TFLiteFaceRecognition"
        
        // ✅ CONFIGURAÇÕES DO MODELO
        private const val MODEL_INPUT_SIZE = 112 // mobile_face_net.tflite
        private const val OUTPUT_SIZE = 192 // Dimensões do embedding
        private const val FLOAT_SIZE = 4
        private const val PIXEL_SIZE = 3
        private const val MEAN = 127.5f
        private const val STD = 127.5f
    }
    
    // ✅ COMPONENTES DO MODELO
    private var interpreter: Interpreter? = null
    private var modelLoaded = false
    
    // ✅ BUFFER PARA EMBEDDINGS
    private var embeddings: Array<FloatArray>? = null
    
    // ✅ DATASET DE FACES CADASTRADAS
    private val registered = HashMap<String, Recognition>()
    
    // ✅ CONFIGURAÇÕES
    private val similarityThreshold = 0.7f // 70% de similaridade
    private val maxDistance = 1.0f // Distância máxima para reconhecimento
    
    /**
     * 🚀 INICIALIZAR O MODELO
     */
    fun initialize(): Boolean {
        return try {
            Log.d(TAG, "🚀 === INICIALIZANDO MODELO TFLite ===")
            
            // ✅ CARREGAR MODELO
            val modelFile = findModelFile()
            if (modelFile == null) {
                Log.e(TAG, "❌ Nenhum modelo encontrado!")
                return false
            }
            
            Log.d(TAG, "📂 Carregando modelo: $modelFile")
            
            // ✅ CRIAR INTERPRETER
            val options = Interpreter.Options().apply {
                setNumThreads(4)
                setUseNNAPI(true)
            }
            
            interpreter = Interpreter(File(modelFile), options)
            interpreter?.allocateTensors()
            
            // ✅ VERIFICAR DIMENSÕES
            val inputTensor = interpreter?.getInputTensor(0)
            val outputTensor = interpreter?.getOutputTensor(0)
            
            Log.d(TAG, "📊 Input tensor: ${inputTensor?.shape()?.contentToString()}")
            Log.d(TAG, "📊 Output tensor: ${outputTensor?.shape()?.contentToString()}")
            
            modelLoaded = true
            Log.d(TAG, "✅ Modelo TFLite carregado com sucesso!")
            
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao inicializar modelo", e)
            modelLoaded = false
            false
        }
    }
    
    /**
     * 📂 ENCONTRAR ARQUIVO DO MODELO
     */
    private fun findModelFile(): String? {
        return try {
            Log.d(TAG, "📂 === PROCURANDO MODELO TFLite ===")
            
            // ✅ 1. TENTAR NOS ASSETS PRIMEIRO (PRIORIDADE)
            val assetModels = listOf(
                "mobile_face_net.tflite", // ✅ PRIORIDADE: mobile_face_net
                "model.tflite",           // Fallback
                "facenet_model.tflite"    // Fallback
            )
            
            for (modelName in assetModels) {
                try {
                    val inputStream = context.assets.open(modelName)
                    val modelFile = File(context.filesDir, modelName)
                    
                    if (!modelFile.exists()) {
                        Log.d(TAG, "📝 Copiando modelo dos assets: $modelName")
                        inputStream.use { input ->
                            modelFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                    
                    if (modelFile.exists() && modelFile.length() > 0) {
                        Log.d(TAG, "✅ Modelo encontrado: ${modelFile.absolutePath}")
                        Log.d(TAG, "📊 Tamanho: ${modelFile.length()} bytes")
                        return modelFile.absolutePath
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ Modelo $modelName não encontrado nos assets: ${e.message}")
                }
            }
            
            // ✅ 2. TENTAR NO DIRETÓRIO DE MODELOS
            val modelDir = File(context.filesDir, "models")
            if (modelDir.exists()) {
                val modelFiles = modelDir.listFiles { file -> 
                    file.name.endsWith(".tflite") && file.length() > 0 
                }
                
                if (modelFiles != null && modelFiles.isNotEmpty()) {
                    val modelFile = modelFiles.first()
                    Log.d(TAG, "✅ Modelo encontrado no diretório models: ${modelFile.absolutePath}")
                    return modelFile.absolutePath
                }
            }
            
            // ✅ 3. TENTAR NO DIRETÓRIO RAIZ
            val rootFiles = context.filesDir.listFiles { file -> 
                file.name.endsWith(".tflite") && file.length() > 0 
            }
            
            if (rootFiles != null && rootFiles.isNotEmpty()) {
                val modelFile = rootFiles.first()
                Log.d(TAG, "✅ Modelo encontrado no diretório raiz: ${modelFile.absolutePath}")
                return modelFile.absolutePath
            }
            
            Log.e(TAG, "❌ Nenhum modelo TFLite encontrado!")
            Log.e(TAG, "📁 Diretórios verificados:")
            Log.e(TAG, "   - Assets: ${assetModels.joinToString(", ")}")
            Log.e(TAG, "   - Models: ${modelDir.absolutePath}")
            Log.e(TAG, "   - Raiz: ${context.filesDir.absolutePath}")
            
            null
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao procurar modelo", e)
            null
        }
    }
    
    /**
     * 📝 CADASTRAR FACE
     */
    fun registerFace(name: String, bitmap: Bitmap): Boolean {
        return try {
            Log.d(TAG, "📝 === CADASTRANDO FACE: $name ===")
            
            if (!modelLoaded) {
                Log.e(TAG, "❌ Modelo não carregado!")
                return false
            }
            
            // ✅ GERAR EMBEDDING
            val embedding = generateEmbedding(bitmap)
            if (embedding == null) {
                Log.e(TAG, "❌ Falha ao gerar embedding!")
                return false
            }
            
            // ✅ CRIAR RECOGNITION
            val recognition = Recognition(
                id = name,
                label = name,
                confidence = 1.0f,
                location = RectF(),
                extra = arrayOf(embedding)
            )
            
            // ✅ SALVAR NO DATASET
            registered[name] = recognition
            
            Log.d(TAG, "✅ Face cadastrada com sucesso: $name")
            Log.d(TAG, "📊 Total de faces cadastradas: ${registered.size}")
            
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao cadastrar face", e)
            false
        }
    }
    
    /**
     * 🧠 GERAR EMBEDDING PARA RETORNO
     */
    fun generateEmbeddingForReturn(bitmap: Bitmap): FloatArray? {
        return generateEmbedding(bitmap)
    }
    
    /**
     * 🔍 RECONHECER FACE
     */
    fun recognizeFace(bitmap: Bitmap): Recognition? {
        return try {
            Log.d(TAG, "🔍 === RECONHECENDO FACE ===")
            
            if (!modelLoaded) {
                Log.e(TAG, "❌ Modelo não carregado!")
                return null
            }
            
            if (registered.isEmpty()) {
                Log.w(TAG, "⚠️ Nenhuma face cadastrada!")
                return null
            }
            
            // ✅ GERAR EMBEDDING
            val embedding = generateEmbedding(bitmap)
            if (embedding == null) {
                Log.e(TAG, "❌ Falha ao gerar embedding!")
                return null
            }
            
            // ✅ ENCONTRAR MAIS PRÓXIMO
            val nearest = findNearest(embedding)
            if (nearest == null) {
                Log.w(TAG, "⚠️ Nenhuma face similar encontrada!")
                return null
            }
            
            val name = nearest.first
            val distance = nearest.second
            
            // ✅ VERIFICAR THRESHOLD
            val similarity = 1.0f - (distance / maxDistance)
            Log.d(TAG, "📊 Similaridade: ${String.format("%.3f", similarity)} (threshold: $similarityThreshold)")
            
            if (similarity >= similarityThreshold) {
                Log.d(TAG, "✅ FACE RECONHECIDA: $name (similaridade: ${String.format("%.1f", similarity * 100)}%)")
                
                return Recognition(
                    id = name,
                    label = name,
                    confidence = similarity,
                    location = RectF(),
                    extra = arrayOf(embedding)
                )
            } else {
                Log.w(TAG, "⚠️ Similaridade insuficiente: ${String.format("%.1f", similarity * 100)}%")
                return null
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao reconhecer face", e)
            null
        }
    }
    
    /**
     * 🧠 GERAR EMBEDDING
     */
    private fun generateEmbedding(bitmap: Bitmap): FloatArray? {
        return try {
            Log.d(TAG, "🧠 === GERANDO EMBEDDING ===")
            
            if (!modelLoaded || interpreter == null) {
                Log.e(TAG, "❌ Modelo não carregado!")
                return null
            }
            
            // ✅ REDIMENSIONAR IMAGEM
            val resizedBitmap = Bitmap.createScaledBitmap(bitmap, MODEL_INPUT_SIZE, MODEL_INPUT_SIZE, true)
            Log.d(TAG, "📐 Imagem redimensionada: ${MODEL_INPUT_SIZE}x${MODEL_INPUT_SIZE}")
            
            // ✅ PREPARAR INPUT
            val inputBuffer = preprocessImage(resizedBitmap)
            Log.d(TAG, "📊 Buffer de entrada criado: ${inputBuffer.capacity()} bytes")
            
            // ✅ PREPARAR OUTPUT - CORREÇÃO: Usar Array simples em vez de HashMap
            val output = Array(1) { FloatArray(OUTPUT_SIZE) }
            
            // ✅ EXECUTAR MODELO - CORREÇÃO: Usar run() em vez de runForMultipleInputsOutputs()
            Log.d(TAG, "🚀 Executando modelo TFLite...")
            interpreter?.run(inputBuffer, output)
            
            val embedding = output[0]
            
            // ✅ VERIFICAR VALIDADE
            val allZeros = embedding.all { it == 0f }
            val allSame = embedding.all { it == embedding[0] }
            
            if (allZeros) {
                Log.e(TAG, "❌ CRÍTICO: Embedding contém apenas zeros!")
                return null
            }
            
            if (allSame) {
                Log.e(TAG, "❌ CRÍTICO: Embedding contém valores idênticos!")
                return null
            }
            
            // ✅ VERIFICAÇÃO ADICIONAL: Verificar se há valores NaN ou infinitos
            val hasNaN = embedding.any { it.isNaN() }
            val hasInf = embedding.any { it.isInfinite() }
            
            if (hasNaN) {
                Log.e(TAG, "❌ CRÍTICO: Embedding contém valores NaN!")
                return null
            }
            
            if (hasInf) {
                Log.e(TAG, "❌ CRÍTICO: Embedding contém valores infinitos!")
                return null
            }
            
            // ✅ CALCULAR ESTATÍSTICAS PARA VALIDAÇÃO
            val min = embedding.minOrNull() ?: 0f
            val max = embedding.maxOrNull() ?: 0f
            val mean = embedding.average().toFloat()
            val variance = embedding.map { (it - mean) * (it - mean) }.average().toFloat()
            
            Log.d(TAG, "✅ Embedding gerado com sucesso!")
            Log.d(TAG, "📊 Tamanho: ${embedding.size}")
            Log.d(TAG, "📊 Primeiros 5 valores: ${embedding.take(5).joinToString(", ") { "%.6f".format(it) }}")
            Log.d(TAG, "📊 Estatísticas: min=$min, max=$max, mean=$mean, variance=$variance")
            
            // Limpar recursos
            resizedBitmap.recycle()
            
            embedding
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao gerar embedding", e)
            e.printStackTrace()
            null
        }
    }
    
    /**
     * 🔧 PRÉ-PROCESSAR IMAGEM
     */
    private fun preprocessImage(bitmap: Bitmap): ByteBuffer {
        val byteBuffer = ByteBuffer.allocateDirect(MODEL_INPUT_SIZE * MODEL_INPUT_SIZE * PIXEL_SIZE * FLOAT_SIZE)
        byteBuffer.order(ByteOrder.nativeOrder())
        
        val intValues = IntArray(MODEL_INPUT_SIZE * MODEL_INPUT_SIZE)
        bitmap.getPixels(intValues, 0, MODEL_INPUT_SIZE, 0, 0, MODEL_INPUT_SIZE, MODEL_INPUT_SIZE)
        
        for (pixel in intValues) {
            val r = ((pixel shr 16) and 0xFF)
            val g = ((pixel shr 8) and 0xFF)
            val b = (pixel and 0xFF)
            
            // ✅ NORMALIZAÇÃO [-1, 1]
            val normalizedR = (r - MEAN) / STD
            val normalizedG = (g - MEAN) / STD
            val normalizedB = (b - MEAN) / STD
            
            byteBuffer.putFloat(normalizedR)
            byteBuffer.putFloat(normalizedG)
            byteBuffer.putFloat(normalizedB)
        }
        
        return byteBuffer
    }
    
    /**
     * 🔍 ENCONTRAR FACE MAIS PRÓXIMA
     */
    private fun findNearest(embedding: FloatArray): Pair<String, Float>? {
        var nearest: Pair<String, Float>? = null
        
        for ((name, recognition) in registered) {
            val knownEmbedding = (recognition.extra as Array<FloatArray>)[0]
            
            // ✅ CALCULAR DISTÂNCIA EUCLIDIANA (L2 norm)
            var distance = 0.0f
            for (i in embedding.indices) {
                val diff = embedding[i] - knownEmbedding[i]
                distance += diff * diff
            }
            distance = sqrt(distance.toDouble()).toFloat()
            
            Log.d(TAG, "📊 Distância para $name: ${String.format("%.6f", distance)}")
            
            if (nearest == null || distance < nearest.second) {
                nearest = Pair(name, distance)
            }
        }
        
        return nearest
    }
    
    /**
     * 📊 OBTER FACES CADASTRADAS
     */
    fun getRegisteredFaces(): List<String> {
        return registered.keys.toList()
    }
    
    /**
     * 🗑️ REMOVER FACE CADASTRADA
     */
    fun removeFace(name: String): Boolean {
        return if (registered.containsKey(name)) {
            registered.remove(name)
            Log.d(TAG, "🗑️ Face removida: $name")
            true
        } else {
            Log.w(TAG, "⚠️ Face não encontrada: $name")
            false
        }
    }
    
    /**
     * 🧹 LIMPAR TODAS AS FACES
     */
    fun clearAllFaces() {
        registered.clear()
        Log.d(TAG, "🧹 Todas as faces removidas")
    }
    
    /**
     * 📊 OBTER ESTATÍSTICAS
     */
    fun getStats(): String {
        return "Faces cadastradas: ${registered.size}, Modelo carregado: $modelLoaded"
    }
    
    /**
     * 🔄 LIBERAR RECURSOS
     */
    fun close() {
        interpreter?.close()
        interpreter = null
        modelLoaded = false
        Log.d(TAG, "🔄 Recursos liberados")
    }
    
    /**
     * 📋 CLASSE RECOGNITION
     */
    data class Recognition(
        val id: String,
        val label: String,
        val confidence: Float,
        val location: RectF,
        val extra: Any? = null
    )
} 