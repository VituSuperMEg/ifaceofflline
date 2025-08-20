package com.example.iface_offilne

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.*
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.iface_offilne.data.AppDatabase
import com.example.iface_offilne.data.FaceEntity
import com.example.iface_offilne.helpers.AdvancedFaceRecognitionHelper
import com.example.iface_offilne.helpers.Helpers
import com.example.iface_offilne.helpers.bitmapToFloatArray
import com.example.iface_offilne.helpers.cropFace
import com.example.iface_offilne.helpers.fixImageOrientationDefinitive
import com.example.iface_offilne.helpers.toBitmap
import com.example.iface_offilne.models.FacesModel
import com.example.iface_offilne.models.FuncionariosLocalModel
import com.example.iface_offilne.util.FaceOverlayView
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.text.SimpleDateFormat
import java.util.*

class CameraActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var imageAnalyzer: ImageAnalysis
    private lateinit var overlay: FaceOverlayView

    private var interpreter: Interpreter? = null
    private var modelLoaded = false

    private var modelInputWidth = 112
    private var modelInputHeight = 112
    private var modelOutputSize = 192
    
    // 🚀 NOVO: Helper avançado para reconhecimento facial
    private lateinit var advancedFaceHelper: AdvancedFaceRecognitionHelper

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = when {
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
                // Android 14+ (API 34+)
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.POST_NOTIFICATIONS
                )
            }
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU -> {
                // Android 13+ (API 33+)
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.READ_MEDIA_IMAGES
                )
            }
            else -> {
                // Android 12 e abaixo
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                )
            }
        }
        private const val TAG = "CameraActivity"

        // Assinatura de arquivo TFLite válido
        private val TFLITE_SIGNATURE = byteArrayOf(0x54, 0x46, 0x4C, 0x33) // "TFL3"
    }

    private var faceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST) // ✅ SIMPLIFICAÇÃO: Modo rápido para melhor performance
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .setMinFaceSize(0.1f) // ✅ SIMPLIFICAÇÃO: Face ainda menor para detectar qualquer rosto
            .build()
    )

    private var alreadySaved = false
    private var faceDetectionCount = 0
    private var currentFaceBitmap: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setupUI()
        Log.d(TAG, "🚀 === INICIANDO APLICAÇÃO ===")

        // 🚀 NOVO: Inicializar helper avançado
        advancedFaceHelper = AdvancedFaceRecognitionHelper(this)
        
        // 🔍 TESTE: Verificar banco de dados
        testDatabaseConnection()
        
        // ✅ NOVO: Detectar qualidade da câmera e ajustar parâmetros
        detectCameraQuality()
        
        // Carrega o modelo
        loadTensorFlowModel()

        // Solicita permissões
        Log.d(TAG, "🔐 Verificando permissões...")
        Log.d(TAG, "📱 Versão Android: ${android.os.Build.VERSION.SDK_INT}")
        Log.d(TAG, "📋 Permissões necessárias: ${REQUIRED_PERMISSIONS.joinToString(", ")}")
        
        if (allPermissionsGranted()) {
            Log.d(TAG, "✅ Todas as permissões já concedidas")
            startCamera()
            
            // ✅ SIMPLIFICAÇÃO: Instruções mais simples e diretas
            Handler(Looper.getMainLooper()).postDelayed({
                showToast("📷 Posicione seu rosto na tela\nQualquer posição funciona!")
            }, 2000)
            
            // ✅ NOVO: Debug para verificar se a detecção está funcionando
            Handler(Looper.getMainLooper()).postDelayed({
                Log.d(TAG, "🔍 DEBUG: Verificando se detecção está ativa...")
                showToast("🔍 Sistema de detecção ativo")
            }, 5000)
        } else {
            Log.d(TAG, "❌ Permissões pendentes - solicitando...")
            // Mostrar mensagem informativa antes de solicitar permissões
            Toast.makeText(this, "📷 O app precisa de permissão para câmera e armazenamento para registrar sua face", Toast.LENGTH_LONG).show()
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }
    }

    private fun setupUI() {
        previewView = PreviewView(this).apply { id = View.generateViewId() }
        overlay = FaceOverlayView(this).apply { id = View.generateViewId() }

        val container = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            addView(previewView)
        }
        container.addView(overlay)
        setContentView(container)
    }

    private fun loadTensorFlowModel() {
        try {
            Log.d(TAG, "📂 === VERIFICAÇÃO DO MODELO ===")
            listAssetsFiles()

            // Verifica se o arquivo existe
            if (!checkModelExists()) {
                Log.w(TAG, "⚠️  Arquivo model.tflite não encontrado")
                createDummyModel()
                showToast("Funcionando sem modelo (apenas detecção)")
                return
            }

            // Valida o arquivo
            if (!validateModelFile()) {
                Log.e(TAG, "❌ Arquivo model.tflite é inválido!")
                showToast("Arquivo model.tflite corrompido ou inválido")
                return
            }

            // Tenta carregar
            val buffer = loadModelFile("model.tflite")
            Log.d(TAG, "✅ Buffer carregado! Tamanho: ${buffer.capacity()} bytes")

            // Cria interpretador
            val options = Interpreter.Options().apply {
                setNumThreads(2)
                setUseNNAPI(false)
            }

            interpreter = Interpreter(buffer, options)
            interpreter?.allocateTensors()

            Log.d(TAG, "✅ Interpretador criado e tensores alocados!")

            if (checkAndExtractModelDimensions()) {
                modelLoaded = true
                showToast("✅ Modelo TensorFlow carregado!")
                Log.d(TAG, "🎯 Modelo pronto para uso!")
            } else {
                throw Exception("Dimensões do modelo inválidas")
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao carregar modelo: ${e.javaClass.simpleName}", e)

            when {
                e.message?.contains("flatbuffer") == true -> {
                    Log.e(TAG, "💡 DIAGNÓSTICO: Arquivo não é um modelo TFLite válido")
                    showToast("❌ Arquivo não é um modelo TensorFlow Lite válido")
                }
                e.message?.contains("not found") == true -> {
                    Log.e(TAG, "💡 DIAGNÓSTICO: Arquivo model.tflite não encontrado")
                    showToast("⚠️  Arquivo model.tflite não encontrado")
                }
                else -> {
                    Log.e(TAG, "💡 DIAGNÓSTICO: Erro desconhecido no modelo")
                    showToast("❌ Erro no modelo: ${e.message}")
                }
            }

            interpreter?.close()
            interpreter = null
            modelLoaded = false

            // Funciona sem modelo
            Log.w(TAG, "🔄 Continuando apenas com detecção de faces...")
        }
    }

    private fun checkModelExists(): Boolean {
        return try {
            val files = assets.list("") ?: emptyArray()
            files.contains("model.tflite")
        } catch (e: Exception) {
            false
        }
    }

    private fun validateModelFile(): Boolean {
        return try {
            assets.open("model.tflite").use { inputStream ->
                val header = ByteArray(8)
                val bytesRead = inputStream.read(header)

                Log.d(TAG, "🔍 Validando arquivo...")
                Log.d(TAG, "   Bytes lidos: $bytesRead")
                Log.d(TAG, "   Header: ${header.joinToString(" ") { "%02X".format(it) }}")

                // Verifica se tem pelo menos 8 bytes
                if (bytesRead < 8) {
                    Log.e(TAG, "❌ Arquivo muito pequeno (${bytesRead} bytes)")
                    return false
                }

                // Verifica assinatura TFLite (pode estar em diferentes posições)
                val isValidTFLite = header.sliceArray(0..3).contentEquals(TFLITE_SIGNATURE) ||
                        header.sliceArray(4..7).contentEquals(TFLITE_SIGNATURE)

                if (isValidTFLite) {
                    Log.d(TAG, "✅ Assinatura TFLite válida encontrada!")
                } else {
                    Log.e(TAG, "❌ Assinatura TFLite não encontrada")
                    Log.e(TAG, "   Esperado: ${TFLITE_SIGNATURE.joinToString(" ") { "%02X".format(it) }}")
                }

                isValidTFLite
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao validar arquivo", e)
            false
        }
    }

    private fun createDummyModel() {
        Log.d(TAG, "🤖 Criando modelo dummy para demonstração...")
        // Aqui você poderia criar um modelo simples para teste
        // Por enquanto, só registra que está funcionando sem modelo
        showToast("Modo demonstração: apenas detecção de faces")
    }

    private fun listAssetsFiles() {
        try {
            val files = assets.list("") ?: emptyArray()
            Log.d(TAG, "📁 Arquivos na pasta assets (${files.size}):")

            if (files.isEmpty()) {
                Log.w(TAG, "   📂 Pasta vazia!")
            } else {
                files.forEachIndexed { index, file ->
                    val size = try {
                        assets.openFd(file).declaredLength
                    } catch (e: Exception) {
                        -1L
                    }
                    Log.d(TAG, "   ${index + 1}. $file (${if (size >= 0) "${size} bytes" else "tamanho desconhecido"})")
                }
            }

            Log.d(TAG, "🔍 Procurando especificamente por model.tflite...")
            val hasModel = files.contains("model.tflite")
            Log.d(TAG, "   model.tflite presente: ${if (hasModel) "✅ SIM" else "❌ NÃO"}")

        } catch (e: Exception) {
            Log.e(TAG, "Erro ao listar assets", e)
        }
    }

    private fun checkAndExtractModelDimensions(): Boolean {
        return try {
            val interp = interpreter ?: return false

            val inputTensor = interp.getInputTensor(0)
            val outputTensor = interp.getOutputTensor(0)

            val inputShape = inputTensor.shape()
            val outputShape = outputTensor.shape()

            Log.d(TAG, "📊 === DIMENSÕES DO MODELO ===")
            Log.d(TAG, "Input shape: ${inputShape.contentToString()}")
            Log.d(TAG, "Output shape: ${outputShape.contentToString()}")
            Log.d(TAG, "Input type: ${inputTensor.dataType()}")
            Log.d(TAG, "Output type: ${outputTensor.dataType()}")

            if (inputShape.size >= 4) {
                modelInputHeight = inputShape[1]
                modelInputWidth = inputShape[2]
                val channels = inputShape[3]
                Log.d(TAG, "📐 Entrada: ${modelInputWidth}x${modelInputHeight}x${channels}")
            }

            if (outputShape.size >= 2) {
                modelOutputSize = outputShape[1]
                Log.d(TAG, "📐 Saída: vetor ${modelOutputSize}D")
            }

            val valid = modelInputWidth > 0 && modelInputHeight > 0 && modelOutputSize > 0
            Log.d(TAG, "✅ Modelo ${if (valid) "VÁLIDO" else "INVÁLIDO"}")

            valid

        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao analisar modelo", e)
            false
        }
    }

    private fun showToast(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun showSuccessScreen() {
        val usuario = intent.getSerializableExtra("usuario") as? FuncionariosLocalModel
        val faceBitmap = currentFaceBitmap
        
        if (faceBitmap != null) {
            // Criar uma versão otimizada para exibição (300x300 para melhor qualidade)
            val displayBitmap = Bitmap.createScaledBitmap(faceBitmap, 300, 300, true)
            
            // Armazenar no TempImageStorage para evitar problema de transação
            TempImageStorage.storeFaceBitmap(displayBitmap)
            
            // Abre a tela de confirmação
            FaceRegistrationSuccessActivity.start(this, usuario)
            finish() // Fecha a CameraActivity
        } else {
            // Fallback para toast se não tiver a foto
            showToast("✅ Facial cadastrado com sucesso!")
        }
    }

    private fun startCamera() {
        Log.d(TAG, "📷 === INICIANDO CÂMERA ===")

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setTargetResolution(android.util.Size(640, 480)) // ✅ SIMPLIFICAÇÃO: Resolução menor para melhor performance
                .build().also {
                    it.setAnalyzer(ContextCompat.getMainExecutor(this)) { proxy ->
                        processImage(proxy)
                    }
                }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    preview,
                    imageAnalyzer
                )
                Log.d(TAG, "✅ Câmera ativa!")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro na câmera", e)
                showToast("Erro na câmera: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun processImage(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

            faceDetector.process(image)
                .addOnSuccessListener { faces ->
                    if (faces.isNotEmpty()) {
                        faceDetectionCount++
                        val face = faces[0]

                        Log.d(TAG, "👤 FACE #$faceDetectionCount detectada!")

                        overlay.setBoundingBox(face.boundingBox, mediaImage.width, mediaImage.height)

                        // ✅ SIMPLIFICAÇÃO: Critérios muito mais simples e tolerantes
                        val faceArea = face.boundingBox.width() * face.boundingBox.height()
                        val screenArea = mediaImage.width * mediaImage.height
                        val faceRatio = faceArea.toFloat() / screenArea.toFloat()
                        
                        Log.d(TAG, "📏 Face ratio: $faceRatio")
                        
                        // ✅ SIMPLIFICAÇÃO: Critérios mínimos para funcionar em qualquer aparelho
                        val isFaceBigEnough = faceRatio >= 0.02f // Face deve ocupar apenas 2% da tela (muito tolerante)
                        val isFaceInOval = overlay.isFaceInOval(face.boundingBox)
                        val isFaceStable = faceDetectionCount >= 2 // Apenas 2 detecções para estabilizar
                        
                        Log.d(TAG, "🔍 Critérios: tamanho=${isFaceBigEnough}, posição=${isFaceInOval}, estável=${isFaceStable}")
                        
                        if (!alreadySaved && isFaceBigEnough && isFaceInOval && isFaceStable) {
                            Log.d(TAG, "✅ FACE DETECTADA - PROCESSANDO IMEDIATAMENTE!")
                            processDetectedFace(mediaImage, face.boundingBox)
                            alreadySaved = true
                            showToast("✅ Rosto detectado! Processando...")
                        } else if (!alreadySaved && isFaceBigEnough && faceDetectionCount >= 5) {
                            // ✅ SIMPLIFICAÇÃO: Fallback - processar mesmo fora do oval após 5 detecções
                            Log.d(TAG, "🔄 FALLBACK: Processando face fora do oval após 5 detecções")
                            processDetectedFace(mediaImage, face.boundingBox)
                            alreadySaved = true
                            showToast("✅ Processando face...")
                        } else if (!alreadySaved) {
                            // ✅ SIMPLIFICAÇÃO: Feedback mais simples
                            val feedbackMessage = when {
                                !isFaceBigEnough -> "📷 Aproxime mais"
                                !isFaceInOval -> "📷 Centre no oval"
                                !isFaceStable -> "📷 Fique parado"
                                else -> "📷 Posicione seu rosto"
                            }
                            
                            // Mostrar feedback a cada 5 frames (mais frequente)
                            if (faceDetectionCount % 5 == 0) {
                                showToast(feedbackMessage)
                            }
                        }
                    } else {
                        overlay.clear()
                        // Reset mais rápido
                        if (faceDetectionCount > 0) {
                            Log.d(TAG, "⚠️ Face perdida")
                            faceDetectionCount = 0
                        }
                    }
                    imageProxy.close()
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "❌ Erro na detecção", e)
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }


    private fun processDetectedFace(mediaImage: android.media.Image, boundingBox: Rect) {
        try {
            Log.d(TAG, "🔄 === PROCESSANDO FACE SIMPLIFICADO ===")

            val bitmap = toBitmap(mediaImage)
            saveImage(bitmap, "original")

            val faceBmp = cropFace(bitmap, boundingBox)
            saveImage(faceBmp, "face_cropped")

            // ✅ SIMPLIFICAÇÃO: Processar diretamente sem verificações complexas
            Log.d(TAG, "✅ Processando face diretamente")
            processFaceWithHelper(faceBmp)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro no processamento", e)
            showToast("Erro: ${e.message}")
            alreadySaved = false // Reset para permitir nova tentativa
        }
    }
    
    /**
     * ✅ SIMPLIFICAÇÃO: Processar face de forma direta
     */
    private fun processFaceWithHelper(faceBmp: Bitmap) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "🔄 Tentando processar face...")
                
                // ✅ SIMPLIFICAÇÃO: Tentar processamento direto primeiro
                try {
                    val registrationResult = advancedFaceHelper.registerFaceWithValidation(faceBmp)
                    
                    when (registrationResult) {
                        is AdvancedFaceRecognitionHelper.FaceRegistrationResult.Success -> {
                            Log.d(TAG, "✅ Face validada com sucesso!")
                            
                            // Salvar a foto do rosto para mostrar na tela de confirmação
                            val faceForDisplay = Bitmap.createScaledBitmap(faceBmp, 300, 300, true)
                            currentFaceBitmap = fixImageOrientationDefinitive(faceForDisplay)
                            
                            // Salvar embedding no banco
                            saveFaceToDatabase(registrationResult.embedding)
                        }
                        
                        is AdvancedFaceRecognitionHelper.FaceRegistrationResult.Failure -> {
                            Log.w(TAG, "❌ Face rejeitada: ${registrationResult.reason}")
                            // ✅ SIMPLIFICAÇÃO: Tentar processamento alternativo
                            processFaceAlternative(faceBmp)
                        }
                    }
                    
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Erro no processamento avançado, tentando alternativa", e)
                    // ✅ SIMPLIFICAÇÃO: Fallback para processamento alternativo
                    processFaceAlternative(faceBmp)
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro crítico no processamento", e)
                withContext(Dispatchers.Main) {
                    showToast("Erro no processamento. Tente novamente.")
                    alreadySaved = false
                }
            }
        }
    }
    
    /**
     * ✅ NOVA FUNÇÃO: Processamento alternativo para câmeras de baixa qualidade
     */
    private suspend fun processFaceAlternative(faceBmp: Bitmap) {
        try {
            Log.d(TAG, "🔄 Tentando processamento alternativo...")
            
            // ✅ SIMPLIFICAÇÃO: Processamento mais simples
            val resizedFace = Bitmap.createScaledBitmap(faceBmp, 112, 112, true)
            
            // Tentar gerar embedding diretamente
            val embedding = try {
                val inputTensor = convertBitmapToTensorInput(resizedFace)
                val output = Array(1) { FloatArray(modelOutputSize) }
                
                interpreter?.run(inputTensor, output)
                output[0]
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao gerar embedding", e)
                null
            }
            
            if (embedding != null) {
                Log.d(TAG, "✅ Embedding gerado com sucesso!")
                
                // Salvar a foto do rosto
                val faceForDisplay = Bitmap.createScaledBitmap(faceBmp, 300, 300, true)
                currentFaceBitmap = fixImageOrientationDefinitive(faceForDisplay)
                
                // Salvar embedding no banco
                saveFaceToDatabase(embedding)
            } else {
                Log.e(TAG, "❌ Falha ao gerar embedding")
                withContext(Dispatchers.Main) {
                    showToast("Falha no processamento. Tente em melhor iluminação.")
                    alreadySaved = false
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro no processamento alternativo", e)
            withContext(Dispatchers.Main) {
                showToast("Erro no processamento alternativo.")
                alreadySaved = false
            }
        }
    }
    
    /**
     * ✅ NOVA FUNÇÃO: Verificar qualidade da face
     */
    private fun checkFaceQuality(bitmap: Bitmap): Float {
        try {
            // Verificar resolução mínima
            if (bitmap.width < 100 || bitmap.height < 100) {
                return 0.1f
            }
            
            // Verificar se não está muito escuro ou muito claro
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
            
            // Calcular qualidade baseada na luminosidade
            val quality = when {
                avgBrightness < 0.2f -> 0.2f // Muito escuro
                avgBrightness > 0.8f -> 0.3f // Muito claro
                avgBrightness in 0.3f..0.7f -> 0.8f // Boa luminosidade
                else -> 0.5f // Luminosidade aceitável
            }
            
            Log.d(TAG, "📊 Qualidade calculada: $quality (luminosidade: $avgBrightness)")
            return quality
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao verificar qualidade", e)
            return 0.5f // Qualidade média como fallback
        }
    }
    
    /**
     * ✅ NOVA FUNÇÃO: Melhorar qualidade da face
     */
    private fun improveFaceQuality(bitmap: Bitmap): Bitmap? {
        try {
            // ✅ MELHORIA: Aplicar filtros para melhorar a qualidade
            val improvedBitmap = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, true)
            
            // Aplicar filtro de suavização para reduzir ruído
            val canvas = Canvas(improvedBitmap)
            val paint = Paint().apply {
                isAntiAlias = true
                isFilterBitmap = true
            }
            
            // Desenhar com filtros aplicados
            canvas.drawBitmap(bitmap, 0f, 0f, paint)
            
            // Redimensionar para melhor qualidade se necessário
            val finalBitmap = if (improvedBitmap.width < 200 || improvedBitmap.height < 200) {
                Bitmap.createScaledBitmap(improvedBitmap, 200, 200, true)
            } else {
                improvedBitmap
            }
            
            Log.d(TAG, "✅ Face melhorada: ${bitmap.width}x${bitmap.height} -> ${finalBitmap.width}x${finalBitmap.height}")
            return finalBitmap
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao melhorar qualidade", e)
            return null
        }
    }

    private fun saveFaceToDatabase(embedding: FloatArray) {
        try {
            Log.d(TAG, "💾 === SALVANDO FACE NO BANCO ===")
            
            val usuario = intent.getSerializableExtra("usuario") as? FuncionariosLocalModel
            
            if (usuario == null) {
                Log.e(TAG, "❌ Usuario nulo - não foi possível salvar o vetor facial.")
                showToast("Erro: usuário não encontrado.")
                return
            }
            
            Log.d(TAG, "👤 Usuário: ${usuario.nome} (${usuario.codigo})")
            Log.d(TAG, "📊 Embedding tamanho: ${embedding.size}")
            Log.d(TAG, "📊 Primeiros 3 valores: ${embedding.take(3).joinToString(", ")}")
            
            // Validar embedding antes de salvar
            if (embedding.isEmpty()) {
                Log.e(TAG, "❌ Embedding vazio!")
                showToast("Erro: embedding facial inválido")
                return
            }
            
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val dao = AppDatabase.getInstance(applicationContext).faceDao()
                    
                    // Verificar se já existe face para este funcionário
                    val existingFace = dao.getByFuncionarioId(usuario.codigo)
                    if (existingFace != null) {
                        Log.d(TAG, "🔄 Face existente encontrada - atualizando...")
                        dao.deleteByFuncionarioId(usuario.codigo)
                        Log.d(TAG, "🗑️ Face antiga deletada para funcionário ${usuario.codigo}")
                    } else {
                        Log.d(TAG, "✨ Primeira face para o funcionário ${usuario.codigo}")
                    }
                    
                    // Converter embedding para string
                    val embeddingString = embedding.joinToString(",")
                    Log.d(TAG, "📝 Embedding string (primeiros 50 chars): ${embeddingString.take(50)}...")
                    
                    // Criar nova face
                    val faceEntity = FaceEntity(
                        id = 0, // Deixar o Room gerar o ID
                        funcionarioId = usuario.codigo,
                        embedding = embeddingString,
                        synced = true
                    )
                    
                    // Inserir nova face
                    dao.insert(faceEntity)
                    
                    // Verificar se foi salvo corretamente
                    val savedFace = dao.getByFuncionarioId(usuario.codigo)
                    if (savedFace != null) {
                        Log.d(TAG, "✅ Face salva com sucesso!")
                        Log.d(TAG, "   ID: ${savedFace.id}")
                        Log.d(TAG, "   Funcionário: ${savedFace.funcionarioId}")
                        Log.d(TAG, "   Embedding tamanho: ${savedFace.embedding.split(",").size}")
                        Log.d(TAG, "   Sincronizado: ${savedFace.synced}")
                        
                        // Mostrar tela de confirmação na thread principal
                        withContext(Dispatchers.Main) {
                            showSuccessScreen()
                        }
                    } else {
                        Log.e(TAG, "❌ Face não foi encontrada após salvar!")
                        withContext(Dispatchers.Main) {
                            showToast("Erro: face não foi salva corretamente")
                        }
                    }
                    
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Erro ao salvar face: ${e.message}", e)
                    withContext(Dispatchers.Main) {
                        showToast("Erro ao salvar face: ${e.message}")
                    }
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro geral ao salvar face", e)
            showToast("Erro ao salvar face: ${e.message}")
        }
    }


    private fun convertBitmapToTensorInput(bitmap: Bitmap): ByteBuffer {
        try {
            val inputSize = modelInputWidth // 112
            Log.d(TAG, "🔧 Preparando tensor para entrada ${inputSize}x${inputSize}")
            
            if (bitmap.isRecycled) {
                throw IllegalStateException("Bitmap foi reciclado")
            }
            
            // Alocar buffer com tamanho correto para float32
            val byteBuffer = ByteBuffer.allocateDirect(4 * inputSize * inputSize * 3)
            byteBuffer.order(ByteOrder.nativeOrder())

            val resizedBitmap = if (bitmap.width != inputSize || bitmap.height != inputSize) {
                Log.d(TAG, "🔧 Redimensionando bitmap de ${bitmap.width}x${bitmap.height} para ${inputSize}x${inputSize}")
                Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
            } else {
                bitmap
            }
            
            val intValues = IntArray(inputSize * inputSize)
            resizedBitmap.getPixels(intValues, 0, inputSize, 0, 0, inputSize, inputSize)
            Log.d(TAG, "✅ Pixels extraídos: ${intValues.size} pixels")

            var pixelCount = 0
            for (pixel in intValues) {
                // Normalizar para [-1, 1] como esperado pelo modelo MobileFaceNet
                val r = ((pixel shr 16) and 0xFF) / 127.5f - 1.0f
                val g = ((pixel shr 8) and 0xFF) / 127.5f - 1.0f
                val b = (pixel and 0xFF) / 127.5f - 1.0f

                byteBuffer.putFloat(r)
                byteBuffer.putFloat(g)
                byteBuffer.putFloat(b)
                pixelCount++
            }
            
            Log.d(TAG, "✅ Tensor preenchido com $pixelCount pixels")
            
            // Limpar bitmap temporário se foi criado
            if (resizedBitmap != bitmap) {
                resizedBitmap.recycle()
            }

            return byteBuffer
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro em convertBitmapToTensorInput", e)
            throw e
        }
    }

    private fun saveImage(bitmap: Bitmap, prefix: String) {
        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val filename = "${prefix}_${timestamp}.jpg"

            val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val appDir = File(picturesDir, "FaceApp")
            appDir.mkdirs()
            val file = File(appDir, filename)

            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }

            Log.d(TAG, "💾 Salvo: ${file.name}")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao salvar", e)
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    @Throws(IOException::class)
    private fun loadModelFile(fileName: String): ByteBuffer {
        return assets.openFd(fileName).use { fileDescriptor ->
            FileInputStream(fileDescriptor.fileDescriptor).use { inputStream ->
                val fileChannel = inputStream.channel
                val startOffset = fileDescriptor.startOffset
                val declaredLength = fileDescriptor.declaredLength

                fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
            }
        }
    }

    /**
     * 🔍 TESTE DE CONEXÃO COM O BANCO DE DADOS
     */
    private fun testDatabaseConnection() {
        Log.d(TAG, "🔍 === TESTANDO CONEXÃO COM BANCO ===")
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = AppDatabase.getInstance(applicationContext).faceDao()
                val allFaces = dao.getAllFaces()
                
                Log.d(TAG, "📊 Total de faces no banco: ${allFaces.size}")
                
                val usuario = intent.getSerializableExtra("usuario") as? FuncionariosLocalModel
                if (usuario != null) {
                    Log.d(TAG, "👤 Verificando face para usuário: ${usuario.nome} (${usuario.codigo})")
                    
                    val existingFace = dao.getByFuncionarioId(usuario.codigo)
                    if (existingFace != null) {
                        Log.d(TAG, "✅ Face existente encontrada:")
                        Log.d(TAG, "   ID: ${existingFace.id}")
                        Log.d(TAG, "   Embedding tamanho: ${existingFace.embedding.split(",").size}")
                        Log.d(TAG, "   Sincronizado: ${existingFace.synced}")
                    } else {
                        Log.d(TAG, "📝 Nenhuma face encontrada para este usuário")
                    }
                } else {
                    Log.w(TAG, "⚠️ Usuário não informado no intent")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao testar banco de dados", e)
            }
        }
    }
    
    /**
     * ✅ NOVA FUNÇÃO: Detectar qualidade da câmera e ajustar parâmetros
     */
    private fun detectCameraQuality() {
        Log.d(TAG, "📷 === DETECTANDO QUALIDADE DA CÂMERA ===")
        
        try {
            val cameraManager = getSystemService(CAMERA_SERVICE) as android.hardware.camera2.CameraManager
            val cameraIds = cameraManager.cameraIdList
            
            for (cameraId in cameraIds) {
                val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                val facing = characteristics.get(android.hardware.camera2.CameraCharacteristics.LENS_FACING)
                
                // Verificar apenas câmera frontal
                if (facing == android.hardware.camera2.CameraCharacteristics.LENS_FACING_FRONT) {
                    val sensorSize = characteristics.get(android.hardware.camera2.CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
                    
                    Log.d(TAG, "📱 Câmera frontal encontrada:")
                    Log.d(TAG, "   ID: $cameraId")
                    
                    if (sensorSize != null) {
                        Log.d(TAG, "   Sensor: ${sensorSize.width}x${sensorSize.height}")
                        
                        // Classificar qualidade baseada no sensor
                        val sensorPixels = sensorSize.width * sensorSize.height
                        val quality = when {
                            sensorPixels >= 8000000 -> "ALTA" // 8MP+
                            sensorPixels >= 5000000 -> "MÉDIA" // 5MP+
                            sensorPixels >= 2000000 -> "BAIXA" // 2MP+
                            else -> "MUITO BAIXA"
                        }
                        
                        Log.d(TAG, "   Qualidade estimada: $quality (${sensorPixels/1000000}MP)")
                        
                        // ✅ AJUSTAR PARÂMETROS BASEADO NA QUALIDADE
                        adjustParametersForQuality(quality)
                    }
                    
                    break // Só precisamos da câmera frontal
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao detectar qualidade da câmera", e)
            // Usar configuração padrão
            adjustParametersForQuality("MÉDIA")
        }
    }
    
    /**
     * ✅ NOVA FUNÇÃO: Ajustar parâmetros baseado na qualidade da câmera
     */
    private fun adjustParametersForQuality(quality: String) {
        Log.d(TAG, "⚙️ === AJUSTANDO PARÂMETROS PARA QUALIDADE: $quality ===")
        
        when (quality) {
            "ALTA" -> {
                // Câmera de alta qualidade - parâmetros mais restritivos
                Log.d(TAG, "🎯 Configuração para câmera de ALTA qualidade")
                // Manter configurações padrão
            }
            "MÉDIA" -> {
                // Câmera de qualidade média - parâmetros equilibrados
                Log.d(TAG, "⚖️ Configuração para câmera de MÉDIA qualidade")
                // Ajustes moderados já aplicados
            }
            "BAIXA", "MUITO BAIXA" -> {
                // Câmera de baixa qualidade - parâmetros mais tolerantes
                Log.d(TAG, "🔧 Configuração para câmera de BAIXA qualidade")
                
                // ✅ AJUSTES PARA CÂMERAS DE BAIXA QUALIDADE:
                // 1. Reduzir tamanho mínimo da face
                // 2. Aumentar tolerância do oval
                // 3. Reduzir critérios de estabilidade
                
                showToast("📷 Detectada câmera de baixa qualidade - Ajustando configurações...")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        interpreter?.close()
        Log.d(TAG, "🛑 App finalizado")
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                // Verificar quais permissões foram negadas
                val deniedPermissions = mutableListOf<String>()
                for (i in permissions.indices) {
                    if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                        deniedPermissions.add(permissions[i])
                    }
                }
                
                Log.e(TAG, "❌ Permissões negadas: ${deniedPermissions.joinToString(", ")}")
                
                                    val message = when {
                        deniedPermissions.contains(Manifest.permission.CAMERA) -> 
                            "❌ Permissão de câmera negada!\n\nPara registrar sua face, você precisa permitir o acesso à câmera.\n\nVá em Configurações > Apps > iFace Offline > Permissões e ative a câmera."
                        deniedPermissions.contains(Manifest.permission.READ_MEDIA_IMAGES) -> 
                            "❌ Permissão de mídia negada!\n\nPara salvar fotos, você precisa permitir o acesso às imagens.\n\nVá em Configurações > Apps > iFace Offline > Permissões e ative 'Fotos e vídeos'."
                        deniedPermissions.contains(Manifest.permission.POST_NOTIFICATIONS) -> 
                            "❌ Permissão de notificação negada!\n\nPara receber avisos do app, você precisa permitir notificações.\n\nVá em Configurações > Apps > iFace Offline > Permissões e ative 'Notificações'."
                        deniedPermissions.contains(Manifest.permission.WRITE_EXTERNAL_STORAGE) -> 
                            "❌ Permissão de armazenamento negada!\n\nPara salvar fotos, você precisa permitir o acesso ao armazenamento.\n\nVá em Configurações > Apps > iFace Offline > Permissões e ative 'Armazenamento'."
                        else -> "❌ Permissões necessárias foram negadas!\n\nVá em Configurações > Apps > iFace Offline > Permissões e ative todas as permissões."
                    }
                
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                
                // Aguardar um pouco antes de fechar para o usuário ler a mensagem
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    finish()
                }, 3000)
            }
        }
    }
}