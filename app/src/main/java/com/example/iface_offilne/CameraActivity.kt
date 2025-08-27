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
import com.example.iface_offilne.helpers.PreciseFaceRecognitionHelper
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

    private var modelInputWidth = 112 // ✅ MOBILE_FACE_NET: Dimensões padrão
    private var modelInputHeight = 112 // ✅ MOBILE_FACE_NET: Dimensões padrão
    private var modelOutputSize = 192 // ✅ MOBILE_FACE_NET: Embedding size
    
    // 🚀 NOVO: Helper preciso para reconhecimento facial
    private lateinit var preciseFaceHelper: PreciseFaceRecognitionHelper

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
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST) // ✅ OTIMIZAÇÃO: Modo rápido para melhor performance
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE) // ✅ OTIMIZAÇÃO: Desabilitar contours
            .setMinFaceSize(0.08f) // ✅ CORRIGIDO: Face menor para detectar qualquer rosto
            .build()
    )

    private var alreadySaved = false
    private var faceDetectionCount = 0
    private var currentFaceBitmap: Bitmap? = null
    
    // 🚀 NOVO: Sistema de múltiplas capturas
    private val capturedFaces = mutableListOf<Bitmap>()
    private var captureCount = 0
    private val requiredCaptures = com.example.iface_offilne.util.FaceRecognitionConfig.REQUIRED_FACE_CAPTURES
    private var isCapturing = false
    
    // ✅ SISTEMA DE ESTABILIZAÇÃO: Equilibrado para melhor experiência
    private var faceStableCount = 0 // Contador de frames estáveis
    private var lastFacePosition: Rect? = null // Última posição da face
    private var faceStableStartTime = 0L // Tempo de início da estabilização
    private var minStableFrames = 8 // Mínimo de frames estáveis (EQUILIBRADO)
    private var maxStableTime = 6000L // Tempo máximo de estabilização (6 segundos)
    private var positionTolerance = 60 // Tolerância de movimento (pixels) - EQUILIBRADO
    private var isProcessingFace = false // Evitar múltiplos processamentos
    
    // 🚀 MODO DE TESTE: Para facilitar debug
    private val TEST_MODE = true // Ativar modo de teste mais permissivo

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setupUI()
        Log.d(TAG, "🚀 === INICIANDO APLICAÇÃO ===")

        // 🚀 NOVO: Inicializar helper preciso
        preciseFaceHelper = PreciseFaceRecognitionHelper(this)
        
        // 🔍 TESTE: Verificar banco de dados
        testDatabaseConnection()
        
        // ✅ NOVO: Validar embeddings existentes (APENAS VERIFICAÇÃO, SEM REMOÇÃO)
        validateExistingEmbeddings()
        
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
            
            // ✅ INSTRUÇÕES DETALHADAS PARA POSICIONAMENTO
            Handler(Looper.getMainLooper()).postDelayed({
                showToast("📷 Posicione seu rosto no oval\nSistema capturará 3 imagens")
            }, 2000)
            
            // ✅ INSTRUÇÕES ADICIONAIS
            Handler(Looper.getMainLooper()).postDelayed({
                showToast("📷 Aguardando estabilização...\nMantenha o rosto no centro por 5 segundos")
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
            Log.d(TAG, "📂 === CARREGANDO MODELO TENSORFLOW LITE ===")
            listAssetsFiles()

            // ✅ VERIFICAR SE O ARQUIVO EXISTE (priorizar model.tflite - mobile_face_net)
            val modelFileName = if (checkModelExists("model.tflite")) {
                "model.tflite" // ✅ PRIORIDADE: mobile_face_net
            } else if (checkModelExists("facenet_model.tflite")) {
                "facenet_model.tflite" // Fallback
            } else {
                Log.e(TAG, "❌ MODELO NÃO ENCONTRADO!")
                Log.e(TAG, "📁 Arquivos disponíveis em assets:")
                listAssetsFiles()
                showToast("❌ Modelo model.tflite não encontrado!")
                return
            }
            
            Log.d(TAG, "📂 Usando modelo: $modelFileName")

            // ✅ VALIDAR O ARQUIVO
            if (!validateModelFile(modelFileName)) {
                Log.e(TAG, "❌ Arquivo $modelFileName é inválido!")
                showToast("❌ Modelo corrompido - usando modo de detecção apenas")
                return
            }

            // ✅ CARREGAR O ARQUIVO
            val buffer = loadModelFile(modelFileName)
            Log.d(TAG, "✅ Buffer carregado! Tamanho: ${buffer.capacity()} bytes")

            // ✅ CRIAR INTERPRETER COM CONFIGURAÇÕES OTIMIZADAS
            val options = Interpreter.Options().apply {
                setNumThreads(4) // Mais threads para melhor performance
                setUseNNAPI(false) // Desabilitar NNAPI para compatibilidade
                setAllowFp16PrecisionForFp32(false) // Usar precisão FP32
            }

            interpreter = Interpreter(buffer, options)
            interpreter?.allocateTensors()

            Log.d(TAG, "✅ Interpreter criado e tensores alocados!")

            // ✅ VERIFICAR DIMENSÕES DO MODELO
            if (checkAndExtractModelDimensions()) {
                modelLoaded = true
                Log.d(TAG, "🎯 === MODELO TENSORFLOW LITE CARREGADO COM SUCESSO ===")
                Log.d(TAG, "📊 Dimensões de entrada: ${modelInputWidth}x${modelInputHeight}")
                Log.d(TAG, "📊 Dimensões de saída: ${modelOutputSize}")
                Log.d(TAG, "🤖 Interpreter: ${interpreter != null}")
                showToast("✅ Modelo TensorFlow Lite carregado!")
            } else {
                Log.w(TAG, "⚠️ Dimensões do modelo não puderam ser extraídas - usando padrão")
                // Usar dimensões padrão se não conseguir extrair
                modelInputWidth = 160
                modelInputHeight = 160
                modelOutputSize = 192
                modelLoaded = true
                Log.d(TAG, "🔄 Usando dimensões padrão: 160x160 → 192")
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro crítico ao carregar modelo: ${e.javaClass.simpleName}", e)
            e.printStackTrace()

            when {
                e.message?.contains("flatbuffer") == true -> {
                    Log.e(TAG, "💡 DIAGNÓSTICO: Arquivo não é um modelo TFLite válido")
                    showToast("❌ Modelo inválido - usando detecção apenas")
                }
                e.message?.contains("not found") == true -> {
                    Log.e(TAG, "💡 DIAGNÓSTICO: Arquivo model.tflite não encontrado")
                    showToast("⚠️ Modelo não encontrado - usando detecção apenas")
                }
                else -> {
                    Log.e(TAG, "💡 DIAGNÓSTICO: Erro desconhecido no modelo")
                    showToast("❌ Erro no modelo: ${e.message}")
                }
            }

            // ✅ LIMPAR RECURSOS EM CASO DE ERRO
            interpreter?.close()
            interpreter = null
            modelLoaded = false

            Log.w(TAG, "🔄 Continuando apenas com detecção de faces...")
        }
    }

    private fun checkModelExists(fileName: String): Boolean {
        return try {
            val files = assets.list("") ?: emptyArray()
            files.contains(fileName)
        } catch (e: Exception) {
            false
        }
    }

    private fun validateModelFile(fileName: String): Boolean {
        return try {
            assets.open(fileName).use { inputStream ->
                val header = ByteArray(8)
                val bytesRead = inputStream.read(header)

                Log.d(TAG, "🔍 Validando arquivo $fileName...")
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
            Log.e(TAG, "❌ Erro ao validar arquivo $fileName", e)
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

            Log.d(TAG, "🔍 Procurando especificamente por modelos...")
            val hasModel = files.contains("model.tflite")
            val hasFaceNetModel = files.contains("facenet_model.tflite")
            Log.d(TAG, "   model.tflite presente: ${if (hasModel) "✅ SIM" else "❌ NÃO"}")
            Log.d(TAG, "   facenet_model.tflite presente: ${if (hasFaceNetModel) "✅ SIM" else "❌ NÃO"}")

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
                .setTargetResolution(android.util.Size(480, 360)) // ✅ OTIMIZAÇÃO: Resolução menor para processamento mais rápido
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

                        // ✅ SISTEMA DE ESTABILIZAÇÃO: Verificar se a face está estável
                        val isFaceStable = checkFaceStability(face.boundingBox)
                        
                        // ✅ Critérios de qualidade da face - EQUILIBRADOS
                        val faceArea = face.boundingBox.width() * face.boundingBox.height()
                        val screenArea = mediaImage.width * mediaImage.height
                        val faceRatio = faceArea.toFloat() / screenArea.toFloat()
                        
                        val isFaceBigEnough = faceRatio >= 0.008f // Face deve ocupar 0.8% da tela (EQUILIBRADO)
                        val isFaceInOval = overlay.isFaceInOval(face.boundingBox)
                        val isFaceNotTooBig = faceRatio <= 0.35f // Face não deve ocupar mais de 35% da tela (EQUILIBRADO)
                        
                        Log.d(TAG, "📏 Face ratio: $faceRatio, Estável: $isFaceStable, Frames estáveis: $faceStableCount")
                        Log.d(TAG, "🔍 DEBUG: BigEnough=$isFaceBigEnough, InOval=$isFaceInOval, Stable=$isFaceStable")
                        Log.d(TAG, "🔍 DEBUG: alreadySaved=$alreadySaved, isProcessingFace=$isProcessingFace")
                        
                        // ✅ PROCESSAR APENAS QUANDO ESTÁVEL E BEM POSICIONADA
                        val shouldCapture = if (TEST_MODE) {
                            // 🚀 MODO DE TESTE: Mais permissivo
                            !alreadySaved && !isProcessingFace && isFaceBigEnough && faceStableCount >= 5
                        } else {
                            // 🎯 MODO NORMAL: EQUILIBRADO
                            !alreadySaved && !isProcessingFace && isFaceBigEnough && isFaceInOval && isFaceStable && isFaceNotTooBig
                        }
                        
                        if (shouldCapture) {
                            Log.d(TAG, "✅ FACE ESTÁVEL E BEM POSICIONADA - INICIANDO CAPTURAS!")
                            
                            if (!isCapturing) {
                                isCapturing = true
                                capturedFaces.clear()
                                captureCount = 0
                                showToast("📸 Captura 1 de $requiredCaptures - Mantenha a posição!")
                            }
                            
                            // Capturar face quando estável
                            if (captureCount < requiredCaptures) {
                                captureFace(mediaImage, face.boundingBox)
                            }
                            
                        } else if (!alreadySaved && !isProcessingFace) {
                            // ✅ FEEDBACK DETALHADO PARA O USUÁRIO SE POSICIONAR - MAIS AMIGÁVEL
                            val feedbackMessage = if (TEST_MODE) {
                                when {
                                    !isFaceBigEnough -> "📷 Aproxime mais o rosto"
                                    faceStableCount < 5 -> "📷 Fique parado (${faceStableCount}/5)"
                                    else -> "📷 Posicione seu rosto no oval"
                                }
                            } else {
                                when {
                                    !isFaceBigEnough -> "📷 Aproxime mais o rosto (0.8% da tela)"
                                    isFaceNotTooBig -> "📷 Afaste um pouco o rosto"
                                    !isFaceInOval -> "📷 Centre o rosto no oval"
                                    !isFaceStable -> "📷 Fique parado (${faceStableCount}/${minStableFrames})"
                                    else -> "📷 Posicione seu rosto no oval"
                                }
                            }
                            
                            // Mostrar feedback a cada 3 frames para ser mais responsivo
                            if (faceDetectionCount % 3 == 0) {
                                showToast(feedbackMessage)
                            }
                        }
                    } else {
                        overlay.clear()
                        // Reset da estabilização quando perde a face
                        if (faceDetectionCount > 0) {
                            Log.d(TAG, "⚠️ Face perdida - resetando estabilização")
                            resetFaceStability()
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


    /**
     * 📸 CAPTURAR FACE PARA MÚLTIPLAS CAPTURAS
     */
    private fun captureFace(mediaImage: android.media.Image, boundingBox: Rect) {
        try {
            Log.d(TAG, "📸 === CAPTURANDO FACE ${captureCount + 1}/$requiredCaptures ===")
            Log.d(TAG, "📊 MediaImage: ${mediaImage.width}x${mediaImage.height}")
            Log.d(TAG, "📐 BoundingBox: ${boundingBox.left},${boundingBox.top} ${boundingBox.width()}x${boundingBox.height()}")
            
            // ✅ VERIFICAR SE O BOUNDINGBOX É VÁLIDO
            if (boundingBox.width() <= 0 || boundingBox.height() <= 0) {
                Log.e(TAG, "❌ BoundingBox inválido: ${boundingBox.width()}x${boundingBox.height()}")
                return
            }
            
            if (boundingBox.left < 0 || boundingBox.top < 0 || 
                boundingBox.right > mediaImage.width || boundingBox.bottom > mediaImage.height) {
                Log.e(TAG, "❌ BoundingBox fora dos limites da imagem")
                return
            }

            val bitmap = toBitmap(mediaImage)
            if (bitmap == null) {
                Log.e(TAG, "❌ Bitmap é nulo após conversão!")
                return
            }
            Log.d(TAG, "🖼️ Bitmap original: ${bitmap.width}x${bitmap.height}")
            
            val faceBmp = cropFace(bitmap, boundingBox)
            if (faceBmp == null) {
                Log.e(TAG, "❌ Face cropada é nula!")
                return
            }
            Log.d(TAG, "👤 Face cropada: ${faceBmp.width}x${faceBmp.height}")
            
            // ✅ VALIDAR QUALIDADE DA FACE CAPTURADA
            val quality = checkFaceQuality(faceBmp)
            Log.d(TAG, "📊 Qualidade da face: ${String.format("%.3f", quality)}")
            
            if (quality < 0.5f) { // ✅ MAIS PERMISSIVO
                Log.w(TAG, "⚠️ Qualidade da face baixa (${String.format("%.2f", quality)}) - aguardando melhor posição")
                return
            }
            
            // ✅ ADICIONAR À LISTA DE CAPTURAS
            capturedFaces.add(faceBmp)
            captureCount++
            
            Log.d(TAG, "✅ Face ${captureCount}/$requiredCaptures capturada com qualidade ${String.format("%.2f", quality)}")
            Log.d(TAG, "📊 Total de faces capturadas: ${capturedFaces.size}")
            
            // ✅ MOSTRAR PROGRESSO PARA O USUÁRIO
            if (captureCount < requiredCaptures) {
                showToast("📸 Captura ${captureCount + 1} de $requiredCaptures - Continue na posição!")
            } else {
                showToast("✅ Todas as capturas realizadas! Processando...")
                processMultipleCaptures()
            }
            
            // ✅ SALVAR IMAGEM PARA DEBUG (OPCIONAL)
            if (captureCount == requiredCaptures) {
                Log.d(TAG, "💾 Salvando imagem final para debug...")
                saveImage(faceBmp, "face_final")
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro na captura da face", e)
            e.printStackTrace()
            showToast("Erro na captura: ${e.message}")
        }
    }
    
    /**
     * 🔄 PROCESSAR MÚLTIPLAS CAPTURAS
     */
    private fun processMultipleCaptures() {
        try {
            Log.d(TAG, "🔄 === PROCESSANDO MÚLTIPLAS CAPTURAS ===")
            Log.d(TAG, "📊 Total de capturas: ${capturedFaces.size}")
            
            if (capturedFaces.size < requiredCaptures) {
                Log.w(TAG, "⚠️ Capturas insuficientes: ${capturedFaces.size}/$requiredCaptures")
                showToast("Capturas insuficientes. Tente novamente.")
                resetCaptures()
                return
            }
            
            // ✅ PROCESSAR COM HELPER PRECISO
            isProcessingFace = true
            processFacesWithPreciseHelper(capturedFaces)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro no processamento de múltiplas capturas", e)
            showToast("Erro no processamento: ${e.message}")
            resetCaptures()
        }
    }
    
    /**
     * 🔄 RESETAR CAPTURAS
     */
    private fun resetCaptures() {
        isCapturing = false
        captureCount = 0
        capturedFaces.clear()
        isProcessingFace = false
        alreadySaved = false
        Log.d(TAG, "🔄 Capturas resetadas")
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
     * 🎯 PROCESSAR MÚLTIPLAS FACES COM MODELO TENSORFLOW
     */
    private fun processFacesWithPreciseHelper(faceBitmaps: List<Bitmap>) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "🎯 === PROCESSANDO FACES COM MODELO TENSORFLOW ===")
                Log.d(TAG, "📊 Total de faces: ${faceBitmaps.size}")
                
                // ✅ VERIFICAR SE O MODELO ESTÁ CARREGADO
                if (!modelLoaded || interpreter == null) {
                    Log.e(TAG, "❌ Modelo TensorFlow não está carregado!")
                    withContext(Dispatchers.Main) {
                        showToast("❌ Modelo não carregado. Reinicie o app.")
                        resetCaptures()
                    }
                    return@launch
                }
                
                // ✅ GERAR EMBEDDING COM A MELHOR FACE
                val bestFace = selectBestFace(faceBitmaps)
                if (bestFace == null) {
                    Log.e(TAG, "❌ Nenhuma face válida encontrada!")
                    withContext(Dispatchers.Main) {
                        showToast("❌ Nenhuma face válida. Tente novamente.")
                        resetCaptures()
                    }
                    return@launch
                }
                
                Log.d(TAG, "✅ Melhor face selecionada: ${bestFace.width}x${bestFace.height}")
                
                // ✅ GERAR EMBEDDING COM TENSORFLOW
                val embedding = generateEmbeddingDirectly(bestFace)
                if (embedding == null || embedding.isEmpty()) {
                    Log.e(TAG, "❌ Falha ao gerar embedding!")
                    withContext(Dispatchers.Main) {
                        showToast("❌ Erro ao processar face. Tente novamente.")
                        resetCaptures()
                    }
                    return@launch
                }
                
                Log.d(TAG, "✅ Embedding gerado com sucesso!")
                Log.d(TAG, "📊 Tamanho do embedding: ${embedding.size}")
                Log.d(TAG, "📊 Primeiros 5 valores: ${embedding.take(5).joinToString(", ") { "%.6f".format(it) }}")
                
                // ✅ VALIDAR EMBEDDING
                if (!validateEmbedding(embedding)) {
                    Log.e(TAG, "❌ Embedding inválido gerado!")
                    withContext(Dispatchers.Main) {
                        showToast("❌ Face inválida. Tente novamente.")
                        resetCaptures()
                    }
                    return@launch
                }
                
                // ✅ SALVAR FOTO PARA EXIBIÇÃO
                val faceForDisplay = Bitmap.createScaledBitmap(bestFace, 300, 300, true)
                currentFaceBitmap = fixImageOrientationDefinitive(faceForDisplay)
                
                // ✅ SALVAR EMBEDDING NO BANCO
                saveFaceToDatabase(embedding)
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro crítico no processamento", e)
                withContext(Dispatchers.Main) {
                    showToast("Erro no processamento: ${e.message}")
                    resetCaptures()
                }
            }
        }
    }
    
    /**
     * ✅ SIMPLIFICAÇÃO: Processar face de forma direta
     */
    private fun processFaceWithHelper(faceBmp: Bitmap) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "🔄 === PROCESSANDO FACE COM TENSORFLOW LITE DIRETO ===")
                
                // ✅ USAR TENSORFLOW LITE DIRETAMENTE SEMPRE
                val embedding = generateEmbeddingDirectly(faceBmp)
                
                if (embedding != null && embedding.isNotEmpty()) {
                    Log.d(TAG, "✅ Embedding gerado com TensorFlow Lite!")
                    Log.d(TAG, "📊 Embedding tamanho: ${embedding.size}")
                    Log.d(TAG, "📊 Primeiros 5 valores: ${embedding.take(5).joinToString(", ")}")
                    
                    // Validar embedding antes de salvar
                    if (validateEmbedding(embedding)) {
                        // Salvar a foto do rosto para mostrar na tela de confirmação
                        val faceForDisplay = Bitmap.createScaledBitmap(faceBmp, 300, 300, true)
                        currentFaceBitmap = fixImageOrientationDefinitive(faceForDisplay)
                        
                        // Salvar embedding no banco
                        saveFaceToDatabase(embedding)
                    } else {
                        Log.e(TAG, "❌ Embedding inválido gerado")
                        withContext(Dispatchers.Main) {
                            showToast("Embedding inválido. Tente novamente.")
                            isProcessingFace = false
                            alreadySaved = false
                        }
                    }
                } else {
                    Log.e(TAG, "❌ Falha ao gerar embedding com TensorFlow Lite")
                    withContext(Dispatchers.Main) {
                        showToast("Falha no processamento. Verifique a iluminação.")
                        isProcessingFace = false
                        alreadySaved = false
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro crítico no processamento", e)
                withContext(Dispatchers.Main) {
                    showToast("Erro no processamento: ${e.message}")
                    isProcessingFace = false
                    alreadySaved = false
                }
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
    
    /**
     * ✅ SISTEMA DE ESTABILIZAÇÃO: Verificar se a face está estável
     */
    private fun checkFaceStability(currentPosition: Rect): Boolean {
        val currentTime = System.currentTimeMillis()
        
        // Se é a primeira detecção, inicializar
        if (lastFacePosition == null) {
            lastFacePosition = currentPosition
            faceStableStartTime = currentTime
            faceStableCount = 1
            Log.d(TAG, "🔄 Iniciando estabilização da face")
            return false
        }
        
        // Verificar se a posição mudou significativamente
        val positionChanged = kotlin.math.abs(currentPosition.centerX() - lastFacePosition!!.centerX()) > positionTolerance ||
                             kotlin.math.abs(currentPosition.centerY() - lastFacePosition!!.centerY()) > positionTolerance ||
                             kotlin.math.abs(currentPosition.width() - lastFacePosition!!.width()) > positionTolerance ||
                             kotlin.math.abs(currentPosition.height() - lastFacePosition!!.height()) > positionTolerance
        
        if (positionChanged) {
            // Posição mudou - resetar estabilização
            Log.d(TAG, "🔄 Face se moveu - resetando estabilização")
            lastFacePosition = currentPosition
            faceStableStartTime = currentTime
            faceStableCount = 1
            return false
        } else {
            // Posição estável - incrementar contador
            lastFacePosition = currentPosition
            faceStableCount++
            
            // Verificar se atingiu o tempo máximo
            val timeElapsed = currentTime - faceStableStartTime
            if (timeElapsed > maxStableTime) {
                Log.d(TAG, "⏰ Tempo máximo de estabilização atingido - resetando")
                resetFaceStability()
                return false
            }
            
            // Verificar se atingiu frames mínimos
            val isStable = faceStableCount >= minStableFrames
            if (isStable) {
                Log.d(TAG, "✅ Face estabilizada! Frames: $faceStableCount, Tempo: ${timeElapsed}ms")
            }
            
            return isStable
        }
    }
    
    /**
     * ✅ SISTEMA DE ESTABILIZAÇÃO: Resetar estabilização
     */
    private fun resetFaceStability() {
        faceStableCount = 0
        lastFacePosition = null
        faceStableStartTime = 0L
        faceDetectionCount = 0
        Log.d(TAG, "🔄 Estabilização resetada")
    }
    
    /**
     * ✅ GERAR EMBEDDING DIRETAMENTE COM TENSORFLOW LITE
     */
    private fun generateEmbeddingDirectly(faceBmp: Bitmap): FloatArray? {
        return try {
            Log.d(TAG, "🤖 === GERANDO EMBEDDING COM TENSORFLOW LITE ===")
            
            if (interpreter == null) {
                Log.e(TAG, "❌ Interpreter TensorFlow é nulo!")
                return null
            }
            
            if (!modelLoaded) {
                Log.e(TAG, "❌ Modelo não foi carregado corretamente!")
                return null
            }
            
            Log.d(TAG, "✅ Modelo TensorFlow carregado e pronto")
            Log.d(TAG, "📊 Dimensões do modelo: ${modelInputWidth}x${modelInputHeight} → ${modelOutputSize}")
            
            val resizedBitmap = Bitmap.createScaledBitmap(faceBmp, modelInputWidth, modelInputHeight, true)
            Log.d(TAG, "📐 Face redimensionada: ${faceBmp.width}x${faceBmp.height} → ${resizedBitmap.width}x${resizedBitmap.height}")
            
            // ✅ CONVERTER PARA TENSOR DE ENTRADA
            val inputTensor = convertBitmapToTensorInput(resizedBitmap)
            Log.d(TAG, "📊 Tensor de entrada criado: ${inputTensor.capacity()} bytes")
            
            // ✅ CRIAR ARRAY DE SAÍDA COM TAMANHO CORRETO
            val output = Array(1) { FloatArray(modelOutputSize) }
            Log.d(TAG, "📊 Array de saída criado: 1x${modelOutputSize}")
            
            // ✅ EXECUTAR O MODELO TENSORFLOW LITE
            Log.d(TAG, "🚀 Executando modelo TensorFlow Lite...")
            interpreter?.run(inputTensor, output)
            
            val embedding = output[0]
            Log.d(TAG, "✅ Modelo executado com sucesso!")
            
            // ✅ VERIFICAÇÃO IMEDIATA DO EMBEDDING
            Log.d(TAG, "🔍 === VERIFICAÇÃO DO EMBEDDING GERADO ===")
            Log.d(TAG, "📊 Tamanho do embedding: ${embedding.size} (esperado: $modelOutputSize)")
            Log.d(TAG, "📊 Primeiros 5 valores: ${embedding.take(5).joinToString(", ") { "%.6f".format(it) }}")
            Log.d(TAG, "📊 Últimos 5 valores: ${embedding.takeLast(5).joinToString(", ") { "%.6f".format(it) }}")
            
            // ✅ VERIFICAR SE NÃO SÃO TODOS ZEROS
            val allZeros = embedding.all { it == 0f }
            if (allZeros) {
                Log.e(TAG, "❌ CRÍTICO: Embedding contém apenas zeros!")
                return null
            }
            
            // ✅ VERIFICAR SE NÃO SÃO TODOS IGUAIS
            val allSame = embedding.all { it == embedding[0] }
            if (allSame) {
                Log.e(TAG, "❌ CRÍTICO: Embedding contém valores idênticos!")
                return null
            }
            
            // ✅ CALCULAR ESTATÍSTICAS BÁSICAS
            val min = embedding.minOrNull() ?: 0f
            val max = embedding.maxOrNull() ?: 0f
            val mean = embedding.average().toFloat()
            val variance = embedding.map { (it - mean) * (it - mean) }.average().toFloat()
            
            Log.d(TAG, "📊 Estatísticas do embedding:")
            Log.d(TAG, "   Mínimo: $min")
            Log.d(TAG, "   Máximo: $max")
            Log.d(TAG, "   Média: $mean")
            Log.d(TAG, "   Variância: $variance")
            
            // ✅ VERIFICAR SE OS VALORES FAZEM SENTIDO
            if (variance < 0.0001f) {
                Log.e(TAG, "❌ CRÍTICO: Variância muito baixa - embedding inválido!")
                return null
            }
            
            Log.d(TAG, "✅ Embedding válido e pronto para salvar!")
            
            // Limpar bitmap temporário
            if (resizedBitmap != faceBmp) {
                resizedBitmap.recycle()
            }
            
            embedding
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro crítico ao gerar embedding: ${e.message}", e)
            e.printStackTrace()
            null
        }
    }
    
    /**
     * 🎯 SELECIONAR A MELHOR FACE DAS CAPTURAS
     */
    private fun selectBestFace(faceBitmaps: List<Bitmap>): Bitmap? {
        try {
            Log.d(TAG, "🎯 === SELECIONANDO MELHOR FACE ===")
            Log.d(TAG, "📊 Total de faces para seleção: ${faceBitmaps.size}")
            
            if (faceBitmaps.isEmpty()) {
                Log.e(TAG, "❌ Lista de faces vazia")
                return null
            }
            
            var bestFace: Bitmap? = null
            var bestQuality = 0f
            
            for ((index, face) in faceBitmaps.withIndex()) {
                val quality = checkFaceQuality(face)
                Log.d(TAG, "📊 Face $index: qualidade ${String.format("%.3f", quality)}")
                
                if (quality > bestQuality) {
                    bestQuality = quality
                    bestFace = face
                }
            }
            
            if (bestFace != null) {
                Log.d(TAG, "✅ Melhor face selecionada com qualidade: ${String.format("%.3f", bestQuality)}")
                Log.d(TAG, "📐 Dimensões: ${bestFace.width}x${bestFace.height}")
            } else {
                Log.e(TAG, "❌ Nenhuma face válida encontrada")
            }
            
            return bestFace
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao selecionar melhor face: ${e.message}", e)
            // Retornar a primeira face como fallback
            return faceBitmaps.firstOrNull()
        }
    }
    
    /**
     * ✅ VALIDAR EMBEDDING GERADO
     */
    private fun validateEmbedding(embedding: FloatArray): Boolean {
        try {
            Log.d(TAG, "🔍 === VALIDANDO EMBEDDING ===")
            
            // Verificar se não está vazio
            if (embedding.isEmpty()) {
                Log.e(TAG, "❌ Embedding vazio")
                return false
            }
            
            // Verificar se tem o tamanho esperado
            if (embedding.size != modelOutputSize) {
                Log.e(TAG, "❌ Tamanho incorreto: ${embedding.size} (esperado: $modelOutputSize)")
                return false
            }
            
            // Verificar se não tem valores inválidos
            val hasNaN = embedding.any { it.isNaN() }
            val hasInf = embedding.any { it.isInfinite() }
            
            if (hasNaN) {
                Log.e(TAG, "❌ Embedding contém valores NaN")
                return false
            }
            
            if (hasInf) {
                Log.e(TAG, "❌ Embedding contém valores infinitos")
                return false
            }
            
            // Verificar se não são todos zeros
            val allZeros = embedding.all { it == 0f }
            if (allZeros) {
                Log.e(TAG, "❌ Embedding contém apenas zeros")
                return false
            }
            
            // Verificar variância mínima
            val mean = embedding.average().toFloat()
            val variance = embedding.map { (it - mean) * (it - mean) }.average().toFloat()
            
            if (variance < 0.001f) {
                Log.e(TAG, "❌ Embedding tem variância muito baixa: $variance")
                return false
            }
            
            // Calcular magnitude
            val magnitude = kotlin.math.sqrt(embedding.map { it * it }.sum())
            
            if (magnitude < 0.1f) {
                Log.e(TAG, "❌ Embedding tem magnitude muito baixa: $magnitude")
                return false
            }
            
            Log.d(TAG, "✅ Embedding válido!")
            Log.d(TAG, "📊 Variância: $variance")
            Log.d(TAG, "📊 Magnitude: $magnitude")
            Log.d(TAG, "📊 Média: $mean")
            
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro na validação: ${e.message}", e)
            return false
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
            Log.d(TAG, "📊 Primeiros 3 valores: ${embedding.take(3).joinToString(", ") { "%.6f".format(it) }}")
            
            // Validar embedding antes de salvar
            if (embedding.isEmpty()) {
                Log.e(TAG, "❌ Embedding vazio!")
                showToast("Erro: embedding facial inválido")
                return
            }
            
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val dao = AppDatabase.getInstance(applicationContext).faceDao()
                    
                    // ✅ VERIFICAR SE JÁ EXISTE FACE PARA ESTE FUNCIONÁRIO
                    val existingFace = dao.getByFuncionarioId(usuario.codigo)
                    if (existingFace != null) {
                        Log.d(TAG, "🔄 Face existente encontrada para ${usuario.nome} (${usuario.codigo}) - substituindo...")
                        dao.deleteByFuncionarioId(usuario.codigo)
                        Log.d(TAG, "🗑️ Face antiga removida")
                    } else {
                        Log.d(TAG, "✨ Primeira face para o funcionário ${usuario.nome} (${usuario.codigo})")
                    }
                    
                    // ✅ CONVERTER EMBEDDING PARA STRING
                    val embeddingString = embedding.joinToString(",")
                    Log.d(TAG, "📝 === SALVANDO EMBEDDING NO BANCO ===")
                    Log.d(TAG, "📝 Embedding string tamanho: ${embeddingString.length} caracteres")
                    Log.d(TAG, "📝 Embedding array tamanho: ${embedding.size}")
                    Log.d(TAG, "📝 Embedding primeiros 3: ${embedding.take(3).joinToString(", ") { "%.6f".format(it) }}")
                    Log.d(TAG, "📝 Embedding últimos 3: ${embedding.takeLast(3).joinToString(", ") { "%.6f".format(it) }}")
                    
                    // ✅ CRIAR NOVA FACE
                    val faceEntity = FaceEntity(
                        id = 0, // Deixar o Room gerar o ID
                        funcionarioId = usuario.codigo,
                        embedding = embeddingString,
                        synced = true
                    )
                    
                    // ✅ INSERIR NOVA FACE
                    dao.insert(faceEntity)
                    
                    // ✅ VERIFICAR SE FOI SALVO CORRETAMENTE
                    val savedFace = dao.getByFuncionarioId(usuario.codigo)
                    if (savedFace != null) {
                        Log.d(TAG, "✅ Face salva com sucesso!")
                        Log.d(TAG, "   ID: ${savedFace.id}")
                        Log.d(TAG, "   Funcionário: ${savedFace.funcionarioId}")
                        Log.d(TAG, "   Embedding tamanho: ${savedFace.embedding.split(",").size}")
                        Log.d(TAG, "   Sincronizado: ${savedFace.synced}")
                        
                        // ✅ MOSTRAR TELA DE CONFIRMAÇÃO
                        withContext(Dispatchers.Main) {
                            showSuccessScreen()
                        }
                    } else {
                        Log.e(TAG, "❌ Face não foi encontrada após salvar!")
                        withContext(Dispatchers.Main) {
                            showToast("❌ Erro: face não foi salva corretamente")
                            resetCaptures()
                        }
                    }
                    
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Erro ao salvar face: ${e.message}", e)
                    withContext(Dispatchers.Main) {
                        showToast("❌ Erro ao salvar face: ${e.message}")
                        resetCaptures()
                    }
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro geral ao salvar face", e)
            showToast("❌ Erro ao salvar face: ${e.message}")
            resetCaptures()
        }
    }


    private fun convertBitmapToTensorInput(bitmap: Bitmap): ByteBuffer {
        try {
            val inputSize = modelInputWidth // ✅ MOBILE_FACE_NET: Usar dimensões do modelo
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
                // ✅ MOBILE_FACE_NET: Normalização [-1, 1]
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

    /**
     * 💾 SALVAR IMAGEM COM SUPORTE A DIFERENTES VERSÕES DO ANDROID
     */
    private fun saveImage(bitmap: Bitmap, prefix: String) {
        try {
            Log.d(TAG, "💾 === TENTANDO SALVAR IMAGEM ===")
            Log.d(TAG, "📊 Bitmap: ${bitmap.width}x${bitmap.height}, Config: ${bitmap.config}")
            
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val filename = "${prefix}_${timestamp}.jpg"
            
            Log.d(TAG, "📝 Nome do arquivo: $filename")
            
            // ✅ TENTAR DIFERENTES LOCAIS DE ARMAZENAMENTO
            val savedPaths = mutableListOf<String>()
            
            // 1. TENTAR ARMAZENAMENTO EXTERNO PÚBLICO (Android < 13)
            try {
                if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
                    val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                    val appDir = File(picturesDir, "FaceApp")
                    if (appDir.mkdirs() || appDir.exists()) {
                        val file = File(appDir, filename)
                        
                        FileOutputStream(file).use { out ->
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                        }
                        
                        savedPaths.add(file.absolutePath)
                        Log.d(TAG, "✅ Salvo em armazenamento externo: ${file.absolutePath}")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Falha ao salvar em armazenamento externo: ${e.message}")
            }
            
            try {
                val internalDir = File(filesDir, "face_captures")
                if (internalDir.mkdirs() || internalDir.exists()) {
                    val file = File(internalDir, filename)
                    
                    FileOutputStream(file).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                    }
                    
                    savedPaths.add(file.absolutePath)
                    Log.d(TAG, "✅ Salvo em armazenamento interno: ${file.absolutePath}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Falha ao salvar em armazenamento interno: ${e.message}")
            }
            
            // 3. TENTAR CACHE DO APP
            try {
                val cacheDir = File(cacheDir, "face_captures")
                if (cacheDir.mkdirs() || cacheDir.exists()) {
                    val file = File(cacheDir, filename)
                    
                    FileOutputStream(file).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                    }
                    
                    savedPaths.add(file.absolutePath)
                    Log.d(TAG, "✅ Salvo em cache: ${file.absolutePath}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Falha ao salvar em cache: ${e.message}")
            }
            
            // ✅ RESULTADO FINAL
            if (savedPaths.isNotEmpty()) {
                Log.d(TAG, "🎉 Imagem salva com sucesso em ${savedPaths.size} local(is):")
                savedPaths.forEach { path ->
                    Log.d(TAG, "   📁 $path")
                }
                
                // Mostrar toast de sucesso
                showToast("📸 Imagem salva: $filename")
            } else {
                Log.e(TAG, "❌ Falha ao salvar imagem em qualquer local")
                showToast("⚠️ Erro ao salvar imagem")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro crítico ao salvar imagem", e)
            e.printStackTrace()
            showToast("❌ Erro ao salvar: ${e.message}")
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
     * ✅ NOVA FUNÇÃO: Validar embeddings existentes
     */
    private fun validateExistingEmbeddings() {
        Log.d(TAG, "🔍 === VALIDANDO EMBEDDINGS EXISTENTES ===")
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val validator = com.example.iface_offilne.helpers.EmbeddingValidator(this@CameraActivity)
                val report = validator.validateAllEmbeddings()
                
                Log.d(TAG, "📊 === RELATÓRIO DE VALIDAÇÃO ===")
                Log.d(TAG, "✅ Faces válidas: ${report.validFaces}")
                Log.d(TAG, "❌ Faces inválidas: ${report.invalidFaces}")
                
                if (report.invalidFaces > 0) {
                    Log.w(TAG, "⚠️ ENCONTRADAS FACES INVÁLIDAS!")
                    Log.w(TAG, "🔧 Problemas encontrados:")
                    report.problems.forEach { problem ->
                        Log.w(TAG, "   - $problem")
                    }
                    
                    // ✅ SEGURANÇA: NÃO REMOVER AUTOMATICAMENTE - APENAS LOGAR
                    Log.w(TAG, "🛡️ SEGURANÇA: Faces inválidas detectadas mas NÃO removidas automaticamente")
                    Log.w(TAG, "🛡️ Use a função de limpeza manual se necessário")
                    
                    withContext(Dispatchers.Main) {
                        showToast("⚠️ ${report.invalidFaces} faces com problemas detectadas")
                    }
                } else {
                    Log.d(TAG, "✅ Todos os embeddings estão válidos!")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro na validação: ${e.message}", e)
            }
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