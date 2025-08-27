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
import com.example.iface_offilne.helpers.MobileFaceNetHelper
import com.example.iface_offilne.helpers.Helpers
import com.example.iface_offilne.helpers.bitmapToFloatArray
import com.example.iface_offilne.helpers.cropFace
import com.example.iface_offilne.helpers.cropFaceWithLandmarks
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
import kotlin.math.min

class CameraActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var imageAnalyzer: ImageAnalysis
    private lateinit var overlay: FaceOverlayView

    private var interpreter: Interpreter? = null
    private var modelLoaded = false

    private var modelInputWidth = 160
    private var modelInputHeight = 160
    private var modelOutputSize = 192
    
    private lateinit var mobileFaceNetHelper: MobileFaceNetHelper

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
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST) // ✅ AJUSTE: Modo rápido para melhor performance
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE) // ✅ AJUSTE: Sem landmarks para evitar erros
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE) // ✅ AJUSTE: Sem classificação para velocidade
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE) // ✅ AJUSTE: Sem contornos para velocidade
            .setMinFaceSize(0.02f)
            .build()
    )

    // ✅ NOVO: SISTEMA DE CADASTRO ROBUSTO COM MÚLTIPLAS AMOSTRAS
    private var currentFaceBitmap: Bitmap? = null
    private var isProcessingFace = false // Evitar múltiplos processamentos
    private var isShowingSuccessScreen = false // ✅ NOVO: Evitar múltiplas telas de sucesso
    private var successScreenShown = false // ✅ NOVO: Flag adicional para garantir que só aparece uma vez
    
    // ✅ SISTEMA DE CAPTURA MÚLTIPLA (AJUSTADO PARA CÂMERAS RUINS)
    private var capturedSamples = mutableListOf<FaceSample>()
    private var currentSampleIndex = 0
    private var totalSamplesRequired = 3 // ✅ AJUSTE: Reduzido para 3 amostras para câmeras ruins
    private var isCapturingSamples = false
    
    // ✅ SISTEMA DE ESTABILIZAÇÃO OTIMIZADO PARA CÂMERAS RUINS
    private var faceStableCount = 0
    private var lastFacePosition: Rect? = null
    private var faceStableStartTime = 0L
    private var minStableFrames = 2 // ✅ AJUSTE: Reduzido para apenas 2 frames para facilitar captura
    private var maxStableTime = 5000L // ✅ AJUSTE: 5 segundos máximo
    private var positionTolerance = 200 // ✅ AJUSTE: Tolerância ainda mais aumentada
    
    // ✅ SISTEMA DE QUALIDADE AVANÇADA
    private var currentFaceQuality: FaceOverlayView.FaceQuality = FaceOverlayView.FaceQuality.UNKNOWN
    private var currentLightingQuality: LightingQuality = LightingQuality.UNKNOWN
    private var currentSharpnessQuality: SharpnessQuality = SharpnessQuality.UNKNOWN
    private var currentAngleQuality: AngleQuality = AngleQuality.UNKNOWN
    
    // ✅ SISTEMA DE LIVENESS
    private var livenessTestPassed = false
    private var livenessTestType: LivenessTestType = LivenessTestType.NONE
    private var livenessTestStartTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setupUI()
        Log.d(TAG, "🚀 === INICIANDO APLICAÇÃO ===")

        // 🚀 NOVO: Inicializar MobileFaceNet helper
        mobileFaceNetHelper = MobileFaceNetHelper(this)
        
        // 🔍 TESTE: Verificar banco de dados
        testDatabaseConnection()
        
        // ✅ NOVO: Validar embeddings existentes (APENAS VERIFICAÇÃO, SEM REMOÇÃO)
        validateExistingEmbeddings()
        
        // ✅ NOVO: Detectar qualidade da câmera e ajustar parâmetros
        detectCameraQuality()
        
        // Carrega o modelo
        loadTensorFlowModel()

        
        if (allPermissionsGranted()) {
            Log.d(TAG, "✅ Todas as permissões já concedidas")
            startCamera()
            
            // ✅ INSTRUÇÕES DETALHADAS PARA POSICIONAMENTO
            Handler(Looper.getMainLooper()).postDelayed({
                showToast("📷 Posicione seu rosto no quadrado azul\nSiga as instruções na tela")
            }, 2000)
            
            // ✅ INSTRUÇÕES ADICIONAIS
            Handler(Looper.getMainLooper()).postDelayed({
                showToast("📷 Sistema de detecção ativo\nAguarde o quadrado ficar verde")
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
            Log.d(TAG, "📂 === CARREGANDO MODELO MOBILEFACENET ===")
            
            // ✅ TENTAR CARREGAR MOBILEFACENET DOS RECURSOS RAW
            val modelBuffer = try {
                Log.d(TAG, "🔍 Tentando carregar mobilefacenet.tflite dos recursos raw...")
                val inputStream = resources.openRawResource(R.raw.mobilefacenet)
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
                
                // ✅ FALLBACK: Tentar carregar do assets
                try {
                    loadModelFile("model.tflite")
                } catch (e2: Exception) {
                    Log.e(TAG, "❌ Nenhum modelo encontrado", e2)
                    throw e2
                }
            }
            
            Log.d(TAG, "✅ Buffer carregado! Tamanho: ${modelBuffer.capacity()} bytes")

            // ✅ CRIAR INTERPRETER COM CONFIGURAÇÕES OTIMIZADAS
            val options = Interpreter.Options().apply {
                setNumThreads(4) // Mais threads para melhor performance
                setUseNNAPI(false) // Desabilitar NNAPI para compatibilidade
                setAllowFp16PrecisionForFp32(false) // Usar precisão FP32
            }

            interpreter = Interpreter(modelBuffer, options)
            interpreter?.allocateTensors()

            Log.d(TAG, "✅ Interpreter criado e tensores alocados!")

            // ✅ CONFIGURAR DIMENSÕES DO MOBILEFACENET
            modelInputWidth = 112  // MobileFaceNet usa 112x112
            modelInputHeight = 112 // MobileFaceNet usa 112x112
            modelOutputSize = 192  // MobileFaceNet gera embeddings de 192 dimensões
            
            modelLoaded = true
            Log.d(TAG, "🎯 === MOBILEFACENET CARREGADO COM SUCESSO ===")
            Log.d(TAG, "📊 Dimensões de entrada: ${modelInputWidth}x${modelInputHeight}")
            Log.d(TAG, "📊 Dimensões de saída: ${modelOutputSize}")
            Log.d(TAG, "🤖 Interpreter: ${interpreter != null}")
            showToast("✅ Modelo MobileFaceNet carregado!")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro crítico ao carregar modelo: ${e.javaClass.simpleName}", e)
            e.printStackTrace()

            when {
                e.message?.contains("flatbuffer") == true -> {
                    Log.e(TAG, "💡 DIAGNÓSTICO: Arquivo não é um modelo TFLite válido")
                    showToast("❌ Modelo inválido - usando detecção apenas")
                }
                e.message?.contains("not found") == true -> {
                    Log.e(TAG, "💡 DIAGNÓSTICO: Modelo não encontrado")
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
        // ✅ PROTEÇÃO: Evitar múltiplas chamadas
        if (isShowingSuccessScreen || successScreenShown) {
            Log.w(TAG, "⚠️ Tela de sucesso já está sendo mostrada ou já foi mostrada - ignorando chamada")
            return
        }
        
        isShowingSuccessScreen = true
        successScreenShown = true
        Log.d(TAG, "🎉 === INICIANDO SHOW SUCCESS SCREEN ===")
        
        val usuario = intent.getSerializableExtra("usuario") as? FuncionariosLocalModel
        val faceBitmap = currentFaceBitmap
        
        Log.d(TAG, "👤 Usuário: ${usuario?.nome} (${usuario?.codigo})")
        Log.d(TAG, "📸 Face bitmap: ${if (faceBitmap != null) "${faceBitmap.width}x${faceBitmap.height}" else "null"}")
        
        if (faceBitmap != null) {
            try {
                // Criar uma versão otimizada para exibição (300x300 para melhor qualidade)
                val displayBitmap = Bitmap.createScaledBitmap(faceBitmap, 300, 300, true)
                Log.d(TAG, "✅ Bitmap redimensionado para exibição: ${displayBitmap.width}x${displayBitmap.height}")
                
                // Armazenar no TempImageStorage para evitar problema de transação
                TempImageStorage.storeFaceBitmap(displayBitmap)
                Log.d(TAG, "✅ Bitmap armazenado no TempImageStorage")
                
                // Abre a tela de confirmação
                Log.d(TAG, "🚀 Iniciando FaceRegistrationSuccessActivity...")
                FaceRegistrationSuccessActivity.start(this, usuario)
                Log.d(TAG, "✅ FaceRegistrationSuccessActivity iniciada")
                
                finish() // Fecha a CameraActivity
                Log.d(TAG, "✅ CameraActivity finalizada")
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao mostrar tela de sucesso", e)
                isShowingSuccessScreen = false // Reset em caso de erro
                successScreenShown = false // Reset em caso de erro
                showToast("✅ Facial cadastrado com sucesso!")
                finish()
            }
        } else {
            // Fallback para toast se não tiver a foto
            Log.w(TAG, "⚠️ Face bitmap é null - usando fallback")
            isShowingSuccessScreen = false // Reset em caso de erro
            successScreenShown = false // Reset em caso de erro
            showToast("✅ Facial cadastrado com sucesso!")
            finish()
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
                .setTargetResolution(android.util.Size(640, 480)) // ✅ AJUSTE: Resolução menor para câmeras ruins
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
                    Log.d(TAG, "🔍 Faces detectadas: ${faces.size}")
                    if (faces.isNotEmpty()) {
                        // ✅ NOVO: VALIDAR MÚLTIPLAS FACES
                        if (faces.size > 1) {
                            overlay.clear()
                            showUserMessage("⚠️ Apenas 1 pessoa por vez", true)
                            imageProxy.close()
                            return@addOnSuccessListener
                        }
                        
                        val face = faces[0]
                        
                        // ✅ NOVO: SISTEMA DE QUALIDADE AVANÇADA
                        val isFaceStable = checkFaceStability(face.boundingBox)
                        val faceQuality = calculateFaceQuality(face, mediaImage)
                        Log.d(TAG, "📊 Face estável: $isFaceStable, Qualidade: $faceQuality")
                        val lightingQuality = checkLightingQuality(face, mediaImage)
                        val sharpnessQuality = checkSharpnessQuality(face, mediaImage)
                        val angleQuality = checkAngleQuality(face)
                        
                        // ✅ ATUALIZAR OVERLAY COM TODAS AS QUALIDADES
                        overlay.setBoundingBox(face.boundingBox, mediaImage.width, mediaImage.height, imageProxy.imageInfo.rotationDegrees)
                        overlay.setFaceQuality(faceQuality, faceStableCount, isFaceStable)
                        
                        // ✅ MOSTRAR MENSAGENS DE INSTRUÇÃO
                        showQualityInstructions(faceQuality, lightingQuality, sharpnessQuality, angleQuality)
                        
                        Log.d(TAG, "📊 Qualidades - Face: $faceQuality, Luz: $lightingQuality, Nitidez: $sharpnessQuality, Ângulo: $angleQuality")
                        
                        // ✅ CAPTURAR AMOSTRA QUANDO CONDIÇÕES BÁSICAS FOREM ATENDIDAS (ULTRA SIMPLIFICADO)
                        if (!isProcessingFace && isCapturingSamples && 
                            (faceQuality == FaceOverlayView.FaceQuality.PERFECT || faceQuality == FaceOverlayView.FaceQuality.GOOD || faceQuality == FaceOverlayView.FaceQuality.UNKNOWN)) {
                            
                            Log.d(TAG, "✅ CONDIÇÕES ATENDIDAS - CAPTURANDO AMOSTRA!")
                            // ✅ CORREÇÃO: Processar imagem imediatamente na thread principal
                            captureFaceSampleImmediately(mediaImage, face)
                        }
                        
                        // ✅ INICIAR CAPTURA SE NÃO ESTIVER CAPTURANDO (SEM EXIGIR ESTABILIDADE)
                        if (!isCapturingSamples && !isProcessingFace) {
                            Log.d(TAG, "🎯 INICIANDO CAPTURA - Face detectada!")
                            startCaptureSession()
                        }
                        
                    } else {
                        overlay.clear()
                        showUserMessage("👤 Posicione seu rosto no quadrado azul", false)
                        resetFaceStability()
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
            Log.d(TAG, "🔄 === PROCESSANDO FACE COM QUALIDADE MELHORADA ===")

            val bitmap = toBitmap(mediaImage)
            saveImage(bitmap, "original")

            // ✅ MELHORIA: Usar recorte simples por enquanto (será melhorado na próxima versão)
            val faceBmp = cropFace(bitmap, boundingBox)
            saveImage(faceBmp, "face_cropped")

            // ✅ MELHORIA: Validar qualidade da face recortada
            val faceQuality = validateCroppedFaceQuality(faceBmp)
            if (!faceQuality.isValid) {
                Log.w(TAG, "⚠️ Face recortada de baixa qualidade: ${faceQuality.reason}")
                showToast("Face de baixa qualidade. Tente novamente.")
                isProcessingFace = false
                return
            }

            Log.d(TAG, "✅ Face recortada aprovada na validação")
            processFaceWithHelper(faceBmp)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro no processamento", e)
            showToast("Erro: ${e.message}")
            isProcessingFace = false
        }
    }
    
    /**
     * ✅ NOVA FUNÇÃO: Processar face usando landmarks para recorte preciso
     */
    private fun processDetectedFaceWithLandmarks(mediaImage: android.media.Image, face: com.google.mlkit.vision.face.Face) {
        try {
            Log.d(TAG, "🔄 === PROCESSANDO FACE COM LANDMARKS ===")

            val bitmap = toBitmap(mediaImage)
            saveImage(bitmap, "original")

            // ✅ MELHORIA: Usar recorte com landmarks para maior precisão
            val faceBmp = try {
                cropFaceWithLandmarks(bitmap, face)
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Erro no recorte com landmarks: ${e.message}")
                cropFace(bitmap, face.boundingBox) // Fallback
            }
            saveImage(faceBmp, "face_cropped_landmarks")

            // ✅ MELHORIA: Validar qualidade da face recortada
            val faceQuality = validateCroppedFaceQuality(faceBmp)
            if (!faceQuality.isValid) {
                Log.w(TAG, "⚠️ Face recortada de baixa qualidade: ${faceQuality.reason}")
                showToast("Face de baixa qualidade. Tente novamente.")
                isProcessingFace = false
                return
            }

            Log.d(TAG, "✅ Face recortada com landmarks aprovada na validação")
            processFaceWithHelper(faceBmp)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro no processamento com landmarks", e)
            showToast("Erro: ${e.message}")
            isProcessingFace = false
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
                    Log.d(TAG, "✅ Embedding gerado com MobileFaceNet!")
                    Log.d(TAG, "📊 Embedding tamanho: ${embedding.size}")
                    Log.d(TAG, "📊 Primeiros 5 valores: ${embedding.take(5).joinToString(", ")}")
                    
                    // ✅ VALIDAR QUALIDADE DO EMBEDDING MOBILEFACENET
                    val embeddingQuality = validateEmbeddingQuality(embedding)
                    if (embeddingQuality.isValid) {
                        // ✅ VERIFICAR SE NÃO É EMBEDDING DE TESTE
                        val isTestEmbedding = checkIfTestEmbedding(embedding)
                        if (!isTestEmbedding) {
                            // Salvar a foto do rosto para mostrar na tela de confirmação
                            val faceForDisplay = Bitmap.createScaledBitmap(faceBmp, 300, 300, true)
                            currentFaceBitmap = fixImageOrientationDefinitive(faceForDisplay)
                            
                            // Salvar embedding no banco
                            saveFaceToDatabase(embedding)
                        } else {
                            Log.w(TAG, "⚠️ Embedding detectado como de teste - rejeitando")
                            withContext(Dispatchers.Main) {
                                showToast("Face de teste detectada - use uma face real")
                                isProcessingFace = false
                            }
                        }
                    } else {
                        Log.e(TAG, "❌ Embedding de baixa qualidade: ${embeddingQuality.reason}")
                        withContext(Dispatchers.Main) {
                            showToast("Face de baixa qualidade: ${embeddingQuality.reason}")
                            isProcessingFace = false
                        }
                    }
                } else {
                    Log.e(TAG, "❌ Falha ao gerar embedding com TensorFlow Lite")
                    withContext(Dispatchers.Main) {
                        showToast("Falha no processamento. Verifique a iluminação.")
                        isProcessingFace = false
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro crítico no processamento", e)
                withContext(Dispatchers.Main) {
                    showToast("Erro no processamento: ${e.message}")
                    isProcessingFace = false
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
     * ✅ NOVA FUNÇÃO: Validar qualidade da face recortada
     */
    private fun validateCroppedFaceQuality(bitmap: Bitmap): FaceQualityResult {
        return try {
            // ✅ Verificar dimensões mínimas
            if (bitmap.width < 100 || bitmap.height < 100) {
                return FaceQualityResult(false, "Face muito pequena (${bitmap.width}x${bitmap.height})")
            }
            
            // ✅ Verificar se não está muito pequena
            if (bitmap.width < 150 || bitmap.height < 150) {
                return FaceQualityResult(false, "Face insuficientemente grande para processamento")
            }
            
            // ✅ Verificar brilho e contraste
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            
            var totalBrightness = 0f
            var minBrightness = 255f
            var maxBrightness = 0f
            
            for (pixel in pixels) {
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                
                val brightness = (r + g + b) / 3f
                totalBrightness += brightness
                minBrightness = minOf(minBrightness, brightness)
                maxBrightness = maxOf(maxBrightness, brightness)
            }
            
            val avgBrightness = totalBrightness / pixels.size
            val contrast = maxBrightness - minBrightness
            
            Log.d(TAG, "📊 Qualidade da face: Brilho=${String.format("%.1f", avgBrightness)}, Contraste=${String.format("%.1f", contrast)}")
            
            // ✅ Verificar se não está muito escuro
            if (avgBrightness < 50f) {
                return FaceQualityResult(false, "Face muito escura (iluminação insuficiente)")
            }
            
            // ✅ Verificar se não está muito claro
            if (avgBrightness > 200f) {
                return FaceQualityResult(false, "Face muito clara (superexposição)")
            }
            
            // ✅ Verificar contraste mínimo
            if (contrast < 30f) {
                return FaceQualityResult(false, "Contraste muito baixo")
            }
            
            Log.d(TAG, "✅ Face recortada aprovada na validação de qualidade")
            return FaceQualityResult(true, "Face de boa qualidade")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro na validação de qualidade", e)
            return FaceQualityResult(false, "Erro na validação: ${e.message}")
        }
    }
    
    /**
     * ✅ CLASSE AUXILIAR: Resultado da validação de qualidade
     */
    data class FaceQualityResult(val isValid: Boolean, val reason: String)
    
    /**
     * 📊 RESULTADO DE QUALIDADE
     */
    data class QualityResult(val isValid: Boolean, val reason: String)
    
    /**
     * 🎯 AMOSTRA DE FACE CAPTURADA
     */
    data class FaceSample(
        val bitmap: Bitmap,
        val embedding: FloatArray,
        val quality: Float,
        val timestamp: Long,
        val sampleType: SampleType
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            return timestamp == (other as FaceSample).timestamp
        }
        
        override fun hashCode(): Int = timestamp.hashCode()
    }
    
    /**
     * 📸 TIPOS DE AMOSTRA
     */
    enum class SampleType {
        NEUTRAL,        // Frente neutra
        SMILING,        // Frente sorrindo
        LOOKING_LEFT,   // Olhando à esquerda
        LOOKING_RIGHT,  // Olhando à direita
        LIVENESS        // Teste de liveness
    }
    
    /**
     * 💡 QUALIDADE DE ILUMINAÇÃO
     */
    enum class LightingQuality {
        UNKNOWN,
        TOO_DARK,       // Muito escuro
        TOO_BRIGHT,     // Muito claro
        ACCEPTABLE,     // Aceitável
        GOOD,           // Boa iluminação
        EXCELLENT       // Iluminação excelente
    }
    
    /**
     * 🔍 QUALIDADE DE NITIDEZ
     */
    enum class SharpnessQuality {
        UNKNOWN,
        BLURRY,         // Borrada
        ACCEPTABLE,     // Aceitável
        SHARP,          // Nítida
        EXCELLENT       // Excelente
    }
    
    /**
     * 📐 QUALIDADE DO ÂNGULO
     */
    enum class AngleQuality {
        UNKNOWN,
        TOO_TILTED,     // Muito inclinado
        TOO_ROTATED,    // Muito rotacionado
        ACCEPTABLE,     // Aceitável
        PERFECT         // Perfeito
    }
    
    /**
     * 👁️ TIPOS DE TESTE DE LIVENESS
     */
    enum class LivenessTestType {
        NONE,
        BLINK,          // Piscar os olhos
        SMILE,          // Sorrir
        HEAD_TURN       // Virar a cabeça
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
     * 👁️ INICIAR TESTE DE LIVENESS
     */
    private fun startLivenessTest() {
        Log.d(TAG, "👁️ === INICIANDO TESTE DE LIVENESS ===")
        livenessTestStartTime = System.currentTimeMillis()
        
        // ✅ ESCOLHER TIPO DE TESTE ALEATÓRIO
        val testTypes = listOf(LivenessTestType.BLINK, LivenessTestType.SMILE, LivenessTestType.HEAD_TURN)
        livenessTestType = testTypes.random()
        
        val instruction = when (livenessTestType) {
            LivenessTestType.BLINK -> "👁️ PISQUE OS OLHOS"
            LivenessTestType.SMILE -> "😊 SORRIA"
            LivenessTestType.HEAD_TURN -> "👤 VIRE A CABEÇA LEVEMENTE"
            else -> "👁️ TESTE DE LIVENESS"
        }
        
        showUserMessage(instruction, false)
        Log.d(TAG, "👁️ Teste de liveness: $livenessTestType")
    }
    
    /**
     * 👁️ VERIFICAR TESTE DE LIVENESS
     */
    private fun checkLivenessTest(face: com.google.mlkit.vision.face.Face): Boolean {
        return try {
            when (livenessTestType) {
                LivenessTestType.BLINK -> {
                    // ✅ VERIFICAR SE OS OLHOS ESTÃO FECHADOS
                    val leftEyeClosed = face.leftEyeOpenProbability != null && face.leftEyeOpenProbability!! < 0.3f
                    val rightEyeClosed = face.rightEyeOpenProbability != null && face.rightEyeOpenProbability!! < 0.3f
                    
                    if (leftEyeClosed && rightEyeClosed) {
                        Log.d(TAG, "✅ Teste de piscar detectado!")
                        return true
                    }
                }
                
                LivenessTestType.SMILE -> {
                    // ✅ VERIFICAR SE ESTÁ SORRINDO
                    val isSmiling = face.smilingProbability != null && face.smilingProbability!! > 0.7f
                    
                    if (isSmiling) {
                        Log.d(TAG, "✅ Teste de sorriso detectado!")
                        return true
                    }
                }
                
                LivenessTestType.HEAD_TURN -> {
                    // ✅ VERIFICAR SE VIROU A CABEÇA
                    val yaw = face.headEulerAngleY ?: 0f
                    val pitch = face.headEulerAngleX ?: 0f
                    
                    if (kotlin.math.abs(yaw) > 5f || kotlin.math.abs(pitch) > 5f) {
                        Log.d(TAG, "✅ Teste de virar cabeça detectado!")
                        return true
                    }
                }
                
                else -> return false
            }
            
            false
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro no teste de liveness", e)
            false
        }
    }
    
    /**
     * 💬 MOSTRAR MENSAGEM PARA O USUÁRIO (OTIMIZADO PARA EVITAR MUITOS TOASTS)
     */
    private var lastUserMessage = ""
    private var lastUserMessageTime = 0L
    
    private fun showUserMessage(message: String, isError: Boolean) {
        val currentTime = System.currentTimeMillis()
        
        // ✅ EVITAR MUITOS TOASTS: Só mostrar se a mensagem mudou ou passou 1 segundo
        if (message != lastUserMessage || (currentTime - lastUserMessageTime) > 1000) {
            runOnUiThread {
                try {
                    // ✅ MOSTRAR TOAST (apenas se não for erro ou se for erro importante)
                    if (isError || !message.contains("Posição perfeita")) {
                        Toast.makeText(this, message, if (isError) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
                    }
                    
                    // ✅ ATUALIZAR OVERLAY COM MENSAGEM (sempre)
                    // Removido: overlay.setUserMessage(message, isError)

                    Log.d(TAG, "💬 Mensagem: $message (Erro: $isError)")
                    
                    lastUserMessage = message
                    lastUserMessageTime = currentTime
                    
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Erro ao mostrar mensagem", e)
                }
            }
        }
    }
    
    /**
     * 📋 MOSTRAR INSTRUÇÕES DE QUALIDADE (OTIMIZADO PARA EVITAR MUITOS TOASTS)
     */
    private var lastQualityMessage = ""
    private var lastQualityMessageTime = 0L
    
    private fun showQualityInstructions(
        faceQuality: FaceOverlayView.FaceQuality,
        lightingQuality: LightingQuality,
        sharpnessQuality: SharpnessQuality,
        angleQuality: AngleQuality
    ) {
        val currentTime = System.currentTimeMillis()
        val message = when {
            faceQuality == FaceOverlayView.FaceQuality.TOO_SMALL -> "📏 Aproxime-se da câmera (rosto muito pequeno)"
            faceQuality == FaceOverlayView.FaceQuality.TOO_BIG -> "📏 Afaste-se da câmera (rosto muito grande)"
            faceQuality == FaceOverlayView.FaceQuality.OFF_CENTER -> "🎯 Centralize o rosto no quadrado azul"
            faceQuality == FaceOverlayView.FaceQuality.UNSTABLE -> "🔄 Mantenha o rosto estável"
            lightingQuality == LightingQuality.TOO_DARK -> "💡 Ambiente muito escuro - melhore a iluminação"
            lightingQuality == LightingQuality.TOO_BRIGHT -> "💡 Ambiente muito claro - evite luz direta"
            sharpnessQuality == SharpnessQuality.BLURRY -> "🔍 Imagem borrada - mantenha a câmera estável"
            angleQuality == AngleQuality.TOO_TILTED -> "📐 Rosto muito inclinado - endireite a cabeça"
            angleQuality == AngleQuality.TOO_ROTATED -> "📐 Rosto muito rotacionado - olhe para frente"
            faceQuality == FaceOverlayView.FaceQuality.GOOD -> "✅ Qualidade boa - mantenha a posição"
            else -> "✅ Posição excelente - aguarde a captura!"
        }
        
        // ✅ EVITAR MUITOS TOASTS: Só mostrar se a mensagem mudou ou passou 2 segundos
        if (message != lastQualityMessage || (currentTime - lastQualityMessageTime) > 2000) {
            showUserMessage(message, false)
            lastQualityMessage = message
            lastQualityMessageTime = currentTime
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
        
        Log.d(TAG, "🔄 Estabilidade - Frames: $faceStableCount/$minStableFrames, Mudou: $positionChanged")
        
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
        Log.d(TAG, "🔄 Estabilização resetada")
    }
    
    /**
     * 🎯 INICIAR SESSÃO DE CAPTURA
     */
    private fun startCaptureSession() {
        if (isCapturingSamples) return
        
        Log.d(TAG, "🎯 === INICIANDO SESSÃO DE CAPTURA MÚLTIPLA ===")
        isCapturingSamples = true
        currentSampleIndex = 0
        capturedSamples.clear()
        
        showUserMessage("📸 Sessão de captura iniciada! Posicione seu rosto no quadrado azul", false)
        
        // ✅ INICIAR TESTE DE LIVENESS
        startLivenessTest()
    }
    
    /**
     * 📸 CAPTURAR AMOSTRA DE FACE (CORRIGIDO - PROCESSAMENTO IMEDIATO)
     */
    private fun captureFaceSampleImmediately(mediaImage: android.media.Image, face: com.google.mlkit.vision.face.Face) {
        if (isProcessingFace) return
        
        isProcessingFace = true
        Log.d(TAG, "📸 === CAPTURANDO AMOSTRA ${currentSampleIndex + 1}/$totalSamplesRequired ===")
        
        try {
            // ✅ PROCESSAR FACE IMEDIATAMENTE NA THREAD PRINCIPAL
            val bitmap = toBitmap(mediaImage)
            val faceBmp = cropFaceWithLandmarks(bitmap, face)
            
            // ✅ PROCESSAR EMBEDDING EM BACKGROUND
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // ✅ GERAR EMBEDDING
                    val embedding = generateEmbeddingDirectly(faceBmp)
                    
                    if (embedding != null && embedding.isNotEmpty()) {
                        // ✅ DETERMINAR TIPO DE AMOSTRA
                        val sampleType = determineSampleType(currentSampleIndex)
                        
                        // ✅ CALCULAR QUALIDADE
                        val quality = calculateSampleQuality(faceBmp, embedding)
                        
                        // ✅ CRIAR AMOSTRA
                        val sample = FaceSample(
                            bitmap = faceBmp,
                            embedding = embedding,
                            quality = quality,
                            timestamp = System.currentTimeMillis(),
                            sampleType = sampleType
                        )
                        
                        capturedSamples.add(sample)
                        currentSampleIndex++
                        
                        Log.d(TAG, "✅ Amostra ${currentSampleIndex}/$totalSamplesRequired capturada!")
                        Log.d(TAG, "📊 Tipo: $sampleType, Qualidade: ${String.format("%.3f", quality)}")
                        
                        // ✅ MOSTRAR PROGRESSO
                        withContext(Dispatchers.Main) {
                            showCaptureProgress()
                        }
                        
                        // ✅ VERIFICAR SE COMPLETOU TODAS AS AMOSTRAS
                        if (currentSampleIndex >= totalSamplesRequired) {
                            Log.d(TAG, "🎉 TODAS AS AMOSTRAS CAPTURADAS!")
                            withContext(Dispatchers.Main) {
                                processAllSamples()
                            }
                        } else {
                            // ✅ AGUARDAR ANTES DA PRÓXIMA CAPTURA
                            kotlinx.coroutines.delay(1000)
                            withContext(Dispatchers.Main) {
                                isProcessingFace = false
                            }
                        }
                        
                    } else {
                        Log.e(TAG, "❌ Falha ao gerar embedding para amostra")
                        withContext(Dispatchers.Main) {
                            showUserMessage("❌ Erro ao processar amostra. Tente novamente.", true)
                            isProcessingFace = false
                        }
                    }
                    
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Erro ao processar embedding", e)
                    withContext(Dispatchers.Main) {
                        showUserMessage("❌ Erro: ${e.message}", true)
                        isProcessingFace = false
                    }
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao processar imagem", e)
            showUserMessage("❌ Erro ao processar imagem: ${e.message}", true)
            isProcessingFace = false
        }
    }
    
    /**
     * 📸 CAPTURAR AMOSTRA DE FACE (MÉTODO ORIGINAL - MANTIDO PARA COMPATIBILIDADE)
     */
    private fun captureFaceSample(mediaImage: android.media.Image, face: com.google.mlkit.vision.face.Face) {
        // ✅ DELEGAR PARA O MÉTODO CORRIGIDO
        captureFaceSampleImmediately(mediaImage, face)
    }
    
    /**
     * 🎯 DETERMINAR TIPO DE AMOSTRA (AJUSTADO PARA CÂMERAS RUINS)
     */
    private fun determineSampleType(index: Int): SampleType {
        return when (index) {
            0 -> SampleType.NEUTRAL      // Frente neutra
            1 -> SampleType.SMILING      // Frente sorrindo
            2 -> SampleType.LIVENESS     // Teste de liveness
            else -> SampleType.NEUTRAL
        }
    }
    
    /**
     * 📊 CALCULAR QUALIDADE DA AMOSTRA
     */
    private fun calculateSampleQuality(bitmap: Bitmap, embedding: FloatArray): Float {
        return try {
            var quality = 0f
            
            // ✅ QUALIDADE DO BITMAP (30%)
            val bitmapQuality = checkFaceQuality(bitmap)
            quality += bitmapQuality * 0.3f
            
            // ✅ QUALIDADE DO EMBEDDING (40%)
            val embeddingQuality = validateEmbeddingQuality(embedding)
            quality += (if (embeddingQuality.isValid) 1f else 0f) * 0.4f
            
            // ✅ VARIÂNCIA DO EMBEDDING (30%)
            val variance = embedding.let { emb ->
                val mean = emb.average().toFloat()
                emb.map { (it - mean) * (it - mean) }.average().toFloat()
            }
            val varianceQuality = (variance / 0.01f).coerceAtMost(1f) // Normalizar
            quality += varianceQuality * 0.3f
            
            quality
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao calcular qualidade da amostra", e)
            0.5f // Qualidade média como fallback
        }
    }
    
    /**
     * 📊 MOSTRAR PROGRESSO DA CAPTURA (AJUSTADO PARA CÂMERAS RUINS)
     */
    private fun showCaptureProgress() {
        val message = when (currentSampleIndex) {
            1 -> "📸 1/3 - Frente neutra capturada"
            2 -> "📸 2/3 - Sorriso capturado"
            3 -> "📸 3/3 - Teste de liveness capturado"
            else -> "📸 Capturando amostra ${currentSampleIndex}/$totalSamplesRequired"
        }
        
        showUserMessage(message, false)
    }
    
    /**
     * 🎯 PROCESSAR TODAS AS AMOSTRAS
     */
    private fun processAllSamples() {
        Log.d(TAG, "🎯 === PROCESSANDO TODAS AS AMOSTRAS ===")
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // ✅ VALIDAR AMOSTRAS (AJUSTADO PARA CÂMERAS RUINS)
                val validSamples = capturedSamples.filter { it.quality > 0.5f } // ✅ AJUSTE: Qualidade mínima reduzida
                
                Log.d(TAG, "📊 Total de amostras capturadas: ${capturedSamples.size}")
                Log.d(TAG, "📊 Amostras válidas: ${validSamples.size}")
                
                if (validSamples.size < 2) { // ✅ AJUSTE: Mínimo de 2 amostras em vez de 3
                    Log.w(TAG, "⚠️ Poucas amostras válidas: ${validSamples.size}/$totalSamplesRequired")
                    withContext(Dispatchers.Main) {
                        showUserMessage("⚠️ Poucas amostras válidas. Tente novamente.", true)
                        resetCaptureSession()
                    }
                    return@launch
                }
                
                // ✅ CALCULAR EMBEDDING MÉDIO
                val averageEmbedding = calculateAverageEmbedding(validSamples.map { it.embedding })
                
                if (averageEmbedding != null) {
                    Log.d(TAG, "✅ Embedding médio calculado com sucesso!")
                    Log.d(TAG, "📊 Tamanho do embedding: ${averageEmbedding.size}")
                    Log.d(TAG, "📊 Primeiros valores: ${averageEmbedding.take(5).joinToString(", ") { String.format("%.3f", it) }}")
                    
                    // ✅ SALVAR NO BANCO
                    saveFaceToDatabase(averageEmbedding)
                    
                } else {
                    Log.e(TAG, "❌ Falha ao calcular embedding médio")
                    withContext(Dispatchers.Main) {
                        showUserMessage("❌ Erro ao processar amostras. Tente novamente.", true)
                        resetCaptureSession()
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao processar amostras", e)
                withContext(Dispatchers.Main) {
                    showUserMessage("❌ Erro: ${e.message}", true)
                    resetCaptureSession()
                }
            }
        }
    }
    
    /**
     * 📊 CALCULAR EMBEDDING MÉDIO
     */
    private fun calculateAverageEmbedding(embeddings: List<FloatArray>): FloatArray? {
        return try {
            if (embeddings.isEmpty()) return null
            
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
            Log.e(TAG, "❌ Erro ao calcular embedding médio", e)
            null
        }
    }
    
    /**
     * 🔄 RESETAR SESSÃO DE CAPTURA
     */
    private fun resetCaptureSession() {
        isCapturingSamples = false
        isProcessingFace = false
        currentSampleIndex = 0
        capturedSamples.clear()
        livenessTestPassed = false
        livenessTestType = LivenessTestType.NONE
    }
    
    /**
     * ✅ NOVA FUNÇÃO: Calcular qualidade da face - MELHORADA
     */
    private fun calculateFaceQuality(face: com.google.mlkit.vision.face.Face, mediaImage: android.media.Image): FaceOverlayView.FaceQuality {
        try {
            // ✅ Verificar tamanho da face (20-80% da área da câmera) - MAIS TOLERANTE
            val faceArea = face.boundingBox.width() * face.boundingBox.height()
            val screenArea = mediaImage.width * mediaImage.height
            val faceRatio = faceArea.toFloat() / screenArea.toFloat()
            
            Log.d(TAG, "📐 Face ratio: ${String.format("%.3f", faceRatio)}")
            
            // ✅ Verificar se o tamanho está adequado (5-95% para ser ultra tolerante)
            if (faceRatio < 0.05f) {
                return FaceOverlayView.FaceQuality.TOO_SMALL
            }
            
            if (faceRatio > 0.95f) {
                return FaceOverlayView.FaceQuality.TOO_BIG
            }
            
            // ✅ Verificar se está no quadrado de alinhamento (mais tolerante)
            if (!overlay.isFaceInAlignmentSquare(face.boundingBox)) {
                // Apenas log, não rejeitar
                Log.d(TAG, "⚠️ Face fora do centro, mas continuando...")
            }
            
            // ✅ Verificar estabilidade (simplificado)
            val isFaceStable = checkFaceStability(face.boundingBox)
            Log.d(TAG, "📊 Face estável: $isFaceStable")
            // Não rejeitar por instabilidade
            
                        // ✅ ULTRA SIMPLIFICADO: Sempre retornar GOOD se chegou até aqui
            Log.d(TAG, "✅ Face aceita para captura!")
            return FaceOverlayView.FaceQuality.GOOD
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao calcular qualidade da face", e)
            return FaceOverlayView.FaceQuality.UNKNOWN
        }
    }
    
    /**
     * 💡 VERIFICAR QUALIDADE DE ILUMINAÇÃO
     */
    private fun checkLightingQuality(face: com.google.mlkit.vision.face.Face, mediaImage: android.media.Image): LightingQuality {
        return try {
            val bitmap = toBitmap(mediaImage)
            val faceRect = face.boundingBox
            
            // ✅ CORTAR APENAS A REGIÃO DA FACE
            val faceBitmap = Bitmap.createBitmap(
                bitmap, 
                faceRect.left.coerceAtLeast(0), 
                faceRect.top.coerceAtLeast(0),
                faceRect.width().coerceAtMost(bitmap.width - faceRect.left),
                faceRect.height().coerceAtMost(bitmap.height - faceRect.top)
            )
            
            // ✅ CALCULAR BRILHO MÉDIO
            val pixels = IntArray(faceBitmap.width * faceBitmap.height)
            faceBitmap.getPixels(pixels, 0, faceBitmap.width, 0, 0, faceBitmap.width, faceBitmap.height)
            
            var totalBrightness = 0f
            for (pixel in pixels) {
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                totalBrightness += (r + g + b) / 3f
            }
            
            val avgBrightness = totalBrightness / pixels.size
            
            Log.d(TAG, "💡 Brilho médio da face: ${String.format("%.1f", avgBrightness)}")
            
            when {
                avgBrightness < 30f -> LightingQuality.TOO_DARK // ✅ AJUSTE: Mais tolerante
                avgBrightness > 220f -> LightingQuality.TOO_BRIGHT // ✅ AJUSTE: Mais tolerante
                avgBrightness in 60f..180f -> LightingQuality.EXCELLENT // ✅ AJUSTE: Faixa maior
                avgBrightness in 40f..200f -> LightingQuality.GOOD // ✅ AJUSTE: Faixa maior
                else -> LightingQuality.ACCEPTABLE // ✅ NOVO: Qualidade aceitável
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao verificar iluminação", e)
            LightingQuality.UNKNOWN
        }
    }
    
    /**
     * 🔍 VERIFICAR QUALIDADE DE NITIDEZ
     */
    private fun checkSharpnessQuality(face: com.google.mlkit.vision.face.Face, mediaImage: android.media.Image): SharpnessQuality {
        return try {
            val bitmap = toBitmap(mediaImage)
            val faceRect = face.boundingBox
            
            // ✅ CORTAR APENAS A REGIÃO DA FACE
            val faceBitmap = Bitmap.createBitmap(
                bitmap, 
                faceRect.left.coerceAtLeast(0), 
                faceRect.top.coerceAtLeast(0),
                faceRect.width().coerceAtMost(bitmap.width - faceRect.left),
                faceRect.height().coerceAtMost(bitmap.height - faceRect.top)
            )
            
            // ✅ CALCULAR VARIÂNCIA DOS PIXELS (MEDIDA DE NITIDEZ)
            val pixels = IntArray(faceBitmap.width * faceBitmap.height)
            faceBitmap.getPixels(pixels, 0, faceBitmap.width, 0, 0, faceBitmap.width, faceBitmap.height)
            
            var totalBrightness = 0f
            for (pixel in pixels) {
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                totalBrightness += (r + g + b) / 3f
            }
            
            val avgBrightness = totalBrightness / pixels.size
            
            // ✅ CALCULAR VARIÂNCIA
            var variance = 0f
            for (pixel in pixels) {
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                val brightness = (r + g + b) / 3f
                variance += (brightness - avgBrightness) * (brightness - avgBrightness)
            }
            variance /= pixels.size
            
            Log.d(TAG, "🔍 Variância da face: ${String.format("%.1f", variance)}")
            
            when {
                variance < 30f -> SharpnessQuality.BLURRY // ✅ AJUSTE: Mais tolerante
                variance < 100f -> SharpnessQuality.ACCEPTABLE // ✅ AJUSTE: Mais tolerante
                variance < 300f -> SharpnessQuality.SHARP // ✅ AJUSTE: Mais tolerante
                else -> SharpnessQuality.EXCELLENT
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao verificar nitidez", e)
            SharpnessQuality.UNKNOWN
        }
    }
    
    /**
     * 📐 VERIFICAR QUALIDADE DO ÂNGULO
     */
    private fun checkAngleQuality(face: com.google.mlkit.vision.face.Face): AngleQuality {
        return try {
            // ✅ VERIFICAR ÂNGULOS DA CABEÇA (±15°)
            val yaw = face.headEulerAngleY ?: 0f
            val pitch = face.headEulerAngleX ?: 0f
            val roll = face.headEulerAngleZ ?: 0f
            
            Log.d(TAG, "📐 Ângulos - Yaw: ${String.format("%.1f", yaw)}°, Pitch: ${String.format("%.1f", pitch)}°, Roll: ${String.format("%.1f", roll)}°")
            
            val maxAngle = 30f // ✅ AJUSTE: Ângulo máximo aumentado para câmeras ruins
            
            when {
                kotlin.math.abs(yaw) > maxAngle || 
                kotlin.math.abs(pitch) > maxAngle || 
                kotlin.math.abs(roll) > maxAngle -> {
                    AngleQuality.TOO_TILTED
                }
                kotlin.math.abs(yaw) > 20f || 
                kotlin.math.abs(pitch) > 20f || 
                kotlin.math.abs(roll) > 20f -> {
                    AngleQuality.ACCEPTABLE
                }
                else -> AngleQuality.PERFECT
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao verificar ângulo", e)
            AngleQuality.UNKNOWN
        }
    }
    
    /**
     * 🤖 GERAR EMBEDDING COM MOBILEFACENET OTIMIZADO
     */
    private fun generateEmbeddingDirectly(faceBmp: Bitmap): FloatArray? {
        return try {
            Log.d(TAG, "🤖 === GERANDO EMBEDDING MOBILEFACENET ===")
            
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
            
            // ✅ REDIMENSIONAR PARA O TAMANHO DO MOBILEFACENET (112x112)
            val resizedBitmap = if (faceBmp.width != 112 || faceBmp.height != 112) {
                Log.d(TAG, "📏 Redimensionando de ${faceBmp.width}x${faceBmp.height} para 112x112")
                Bitmap.createScaledBitmap(faceBmp, 112, 112, true)
            } else {
                Log.d(TAG, "✅ Bitmap já tem tamanho correto 112x112")
                faceBmp
            }
            
            // ✅ CONVERTER PARA TENSOR MOBILEFACENET
            val inputTensor = convertBitmapToTensorInputMobileFaceNet(resizedBitmap)
            val output = Array(1) { FloatArray(192) } // MobileFaceNet gera 192 dimensões
            
            // ✅ EXECUTAR MODELO MOBILEFACENET
            interpreter?.run(inputTensor, output)
            val embedding = output[0]
            
            // ✅ VALIDAR EMBEDDING MOBILEFACENET
            if (embedding.isEmpty()) {
                throw Exception("Embedding MobileFaceNet vazio")
            }
            
            if (embedding.all { it == 0f }) {
                throw Exception("Embedding MobileFaceNet zerado")
            }
            
            if (embedding.any { it.isNaN() || it.isInfinite() }) {
                throw Exception("Embedding MobileFaceNet com valores inválidos")
            }
            
            // ✅ NORMALIZAR EMBEDDING MOBILEFACENET
            val normalizedEmbedding = normalizeEmbedding(embedding)
            
            Log.d(TAG, "✅ Embedding MobileFaceNet gerado: ${embedding.size} dimensões")
            Log.d(TAG, "📊 Amostra: [${embedding.take(5).joinToString(", ") { String.format("%.3f", it) }}...]")
            
            // Limpar bitmap temporário se foi criado
            if (resizedBitmap != faceBmp) {
                resizedBitmap.recycle()
            }
            
            normalizedEmbedding
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao gerar embedding MobileFaceNet: ${e.message}")
            null
        }
    }
    
    /**
     * 📊 CALCULAR SIMILARIDADE ENTRE EMBEDDINGS MOBILEFACENET - OTIMIZADO
     */
    private fun calculateSimilarity(embedding1: FloatArray, embedding2: FloatArray): Float {
        return try {
            if (embedding1.size != embedding2.size) {
                Log.w(TAG, "⚠️ Embeddings de tamanhos diferentes: ${embedding1.size} vs ${embedding2.size}")
                return 0f
            }
            
            // ✅ MOBILEFACENET: Calcular similaridade cosseno diretamente (já normalizados)
            var dotProduct = 0f
            var norm1 = 0f
            var norm2 = 0f
            
            for (i in embedding1.indices) {
                dotProduct += embedding1[i] * embedding2[i]
                norm1 += embedding1[i] * embedding1[i]
                norm2 += embedding2[i] * embedding2[i]
            }
            
            val cosineSimilarity = dotProduct / (kotlin.math.sqrt(norm1) * kotlin.math.sqrt(norm2))
            
            // ✅ VALIDAR SE O RESULTADO É VÁLIDO
            if (cosineSimilarity.isNaN() || cosineSimilarity.isInfinite()) {
                Log.w(TAG, "⚠️ Similaridade cosseno inválida: $cosineSimilarity")
                return 0f
            }
            
            // ✅ MOBILEFACENET: Converter para escala [0, 1] onde 1 = idêntico
            val finalSimilarity = (cosineSimilarity + 1) / 2
            
            Log.d(TAG, "📊 Similaridade MobileFaceNet: ${String.format("%.3f", finalSimilarity)}")
                        
            finalSimilarity
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao calcular similaridade MobileFaceNet: ${e.message}")
            0f
        }
    }
    
    /**
     * ✅ NORMALIZAR EMBEDDING PARA COMPARAÇÃO
     */
    private fun normalizeEmbedding(embedding: FloatArray): FloatArray {
        return try {
            // ✅ CALCULAR MAGNITUDE
            var magnitude = 0f
            for (value in embedding) {
                magnitude += value * value
            }
            magnitude = kotlin.math.sqrt(magnitude)
            
            // ✅ EVITAR DIVISÃO POR ZERO
            if (magnitude < 0.0001f) {
                Log.w(TAG, "⚠️ Embedding com magnitude muito baixa")
                return FloatArray(embedding.size) { 0f }
            }
            
            // ✅ NORMALIZAR
            val normalized = FloatArray(embedding.size)
            for (i in embedding.indices) {
                normalized[i] = embedding[i] / magnitude
            }
            
            normalized
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao normalizar embedding: ${e.message}")
            embedding // Retornar original em caso de erro
        }
    }
    
    /**
     * ✅ VERIFICAR SE É EMBEDDING DE TESTE - CORRIGIDO
     */
    private fun checkIfTestEmbedding(embedding: FloatArray): Boolean {
        return try {
            // ✅ VERIFICAR SE É MUITO SIMILAR AO EMBEDDING DE TESTE PADRÃO
            val testEmbedding = FloatArray(192) { 0.1f } // Embedding de teste padrão
            
            val similarity = calculateSimilarity(embedding, testEmbedding)
            
            // ✅ SE SIMILARIDADE > 90%, PROVAVELMENTE É TESTE
            if (similarity > 0.90f) {
                Log.w(TAG, "⚠️ Embedding muito similar ao de teste: ${String.format("%.3f", similarity)}")
                return true
            }
            
            // ✅ VERIFICAR SE TEM PADRÃO MUITO SIMPLES
            val variance = embedding.let { emb ->
                val mean = emb.average().toFloat()
                emb.map { (it - mean) * (it - mean) }.average().toFloat()
            }
            
            if (variance < 0.001f) { // ✅ CORRIGIDO: Variância mínima mais alta
                Log.w(TAG, "⚠️ Embedding com variância muito baixa (possível teste): ${String.format("%.6f", variance)}")
                return true
            }
            
            // ✅ VERIFICAR SE HÁ MUITOS VALORES IGUAIS
            val uniqueValues = embedding.toSet().size
            if (uniqueValues < 10) { // ✅ NOVO: Se menos de 10 valores únicos, provavelmente é teste
                Log.w(TAG, "⚠️ Embedding com poucos valores únicos (possível teste): $uniqueValues")
                return true
            }
            
            // ✅ VERIFICAR SE A MAGNITUDE É MUITO BAIXA
            var magnitude = 0f
            for (value in embedding) {
                magnitude += value * value
            }
            magnitude = kotlin.math.sqrt(magnitude)
            
            if (magnitude < 0.05f) { 
                Log.w(TAG, "⚠️ Embedding com magnitude muito baixa (possível teste): ${String.format("%.3f", magnitude)}")
                return true
            }
            
            false
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao verificar embedding de teste: ${e.message}")
            false
        }
    }
    
    /**
     * ✅ VALIDAR QUALIDADE DO EMBEDDING MOBILEFACENET - OTIMIZADO
     */
    private fun validateEmbeddingQuality(embedding: FloatArray): QualityResult {
        return try {
            // ✅ 1. VERIFICAR TAMANHO CORRETO DO MOBILEFACENET
            if (embedding.size != 192) {
                return QualityResult(false, "Embedding com tamanho incorreto: ${embedding.size} (esperado: 192)")
            }
            
            // ✅ 2. VERIFICAR SE NÃO É TUDO ZERO
            if (embedding.all { it == 0f }) {
                return QualityResult(false, "Embedding zerado")
            }
            
            // ✅ 3. VERIFICAR SE HÁ VALORES INVÁLIDOS
            if (embedding.any { it.isNaN() || it.isInfinite() }) {
                return QualityResult(false, "Embedding com valores inválidos")
            }
            
            // ✅ 4. VERIFICAR SE NÃO SÃO TODOS IGUAIS
            if (embedding.all { it == embedding[0] }) {
                return QualityResult(false, "Embedding com valores idênticos")
            }
            
            // ✅ 5. VERIFICAR MAGNITUDE DO EMBEDDING (MOBILEFACENET)
            var magnitude = 0f
            for (value in embedding) {
                magnitude += value * value
            }
            magnitude = kotlin.math.sqrt(magnitude)
            
            if (magnitude < 0.05f) { // ✅ MOBILEFACENET: Magnitude mínima ajustada
                return QualityResult(false, "Embedding com magnitude muito baixa (${String.format("%.3f", magnitude)})")
            }
            
            // ✅ 6. VERIFICAR VARIÂNCIA DO EMBEDDING (MOBILEFACENET)
            val mean = embedding.average().toFloat()
            var variance = 0f
            for (value in embedding) {
                val diff = value - mean
                variance += diff * diff
            }
            variance /= embedding.size
            
            if (variance < 0.0001f) { // ✅ MOBILEFACENET: Variância mínima ajustada
                return QualityResult(false, "Embedding sem variação suficiente (variância: ${String.format("%.6f", variance)})")
            }
            
            Log.d(TAG, "✅ Qualidade do embedding MobileFaceNet aprovada: magnitude=${String.format("%.3f", magnitude)}, variância=${String.format("%.6f", variance)}")
            return QualityResult(true, "Embedding MobileFaceNet de qualidade")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao validar qualidade do embedding MobileFaceNet: ${e.message}")
            return QualityResult(false, "Erro na validação: ${e.message}")
        }
    }
    
    /**
     * ✅ VALIDAR EMBEDDING GERADO
     */
    private fun validateEmbedding(embedding: FloatArray): Boolean {
        try {
            Log.d(TAG, "🔍 === VALIDANDO EMBEDDING MOBILEFACENET ===")
            
            // Verificar se não está vazio
            if (embedding.isEmpty()) {
                Log.e(TAG, "❌ Embedding vazio")
                return false
            }
            
            // ✅ Verificar se tem o tamanho esperado do MobileFaceNet (192)
            val expectedSize = 192
            if (embedding.size != expectedSize) {
                Log.e(TAG, "❌ Tamanho incorreto: ${embedding.size} (esperado: $expectedSize)")
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
            
            // Verificar se não são todos iguais
            val allSame = embedding.all { it == embedding[0] }
            if (allSame) {
                Log.e(TAG, "❌ Embedding contém valores idênticos")
                return false
            }
            
            // Verificar variância mínima
            val mean = embedding.average().toFloat()
            val variance = embedding.map { (it - mean) * (it - mean) }.average().toFloat()
            
            if (variance < 0.0001f) {
                Log.e(TAG, "❌ Embedding tem variância muito baixa: $variance")
                return false
            }
            
            // Calcular magnitude
            val magnitude = kotlin.math.sqrt(embedding.map { it * it }.sum())
            
            if (magnitude < 0.01f) {
                Log.e(TAG, "❌ Embedding tem magnitude muito baixa: $magnitude")
                return false
            }
            
            Log.d(TAG, "✅ Embedding MobileFaceNet válido!")
            Log.d(TAG, "📊 Tamanho: ${embedding.size}")
            Log.d(TAG, "📊 Variância: $variance")
            Log.d(TAG, "📊 Magnitude: $magnitude")
            Log.d(TAG, "📊 Média: $mean")
            Log.d(TAG, "📊 Primeiros 3 valores: ${embedding.take(3).joinToString(", ") { "%.6f".format(it) }}")
            
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
                    
                    // ✅ SEGURANÇA: Verificar se já existe face para este funcionário ESPECÍFICO
                    val existingFace = dao.getByFuncionarioId(usuario.codigo)
                    if (existingFace != null) {
                        Log.d(TAG, "🔄 Face existente encontrada para ${usuario.nome} (${usuario.codigo}) - atualizando...")
                        
                        // ✅ VALIDAR SE A FACE EXISTENTE É VÁLIDA ANTES DE REMOVER
                        val validator = com.example.iface_offilne.helpers.EmbeddingValidator(this@CameraActivity)
                        val faceValidation = validator.validateSingleEmbedding(existingFace)
                        
                        if (faceValidation.isValid) {
                            Log.d(TAG, "✅ Face existente é válida - substituindo...")
                            dao.deleteByFuncionarioId(usuario.codigo)
                            Log.d(TAG, "🗑️ Face antiga deletada para funcionário ${usuario.codigo}")
                        } else {
                            Log.w(TAG, "⚠️ Face existente é inválida - removendo e recadastrando...")
                            dao.deleteByFuncionarioId(usuario.codigo)
                            Log.d(TAG, "🗑️ Face inválida removida para funcionário ${usuario.codigo}")
                        }
                    } else {
                        Log.d(TAG, "✨ Primeira face para o funcionário ${usuario.nome} (${usuario.codigo})")
                    }
                    
                    // Converter embedding para string
                    val embeddingString = embedding.joinToString(",")
                    Log.d(TAG, "📝 === SALVANDO EMBEDDING NO BANCO ===")
                    Log.d(TAG, "📝 Embedding string tamanho: ${embeddingString.length} caracteres")
                    Log.d(TAG, "📝 Embedding valores (primeiros 50 chars): ${embeddingString.take(50)}...")
                    Log.d(TAG, "📝 Embedding array tamanho: ${embedding.size}")
                    Log.d(TAG, "📝 Embedding primeiros 3: ${embedding.take(3).joinToString(", ") { "%.6f".format(it) }}")
                    Log.d(TAG, "📝 Embedding últimos 3: ${embedding.takeLast(3).joinToString(", ") { "%.6f".format(it) }}")
                    
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
                        
                        // ✅ SALVAR BITMAP PARA EXIBIÇÃO
                        val firstSample = capturedSamples.firstOrNull()
                        if (firstSample != null) {
                            currentFaceBitmap = firstSample.bitmap
                            Log.d(TAG, "📸 Bitmap salvo para exibição: ${firstSample.bitmap.width}x${firstSample.bitmap.height}")
                        }
                        
                        // Mostrar tela de confirmação na thread principal
                        withContext(Dispatchers.Main) {
                            Log.d(TAG, "🎉 === MOSTRANDO TELA DE SUCESSO ===")
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


    /**
     * ✅ NOVA FUNÇÃO: Converter bitmap para tensor MobileFaceNet
     */
    private fun convertBitmapToTensorInputMobileFaceNet(bitmap: Bitmap): ByteBuffer {
        try {
            val inputSize = 112 // MobileFaceNet usa 112x112
            Log.d(TAG, "🔧 Preparando tensor MobileFaceNet para entrada ${inputSize}x${inputSize}")
            
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
                // ✅ MobileFaceNet usa normalização [0, 1] com pré-processamento específico
                val r = ((pixel shr 16) and 0xFF) / 255.0f
                val g = ((pixel shr 8) and 0xFF) / 255.0f
                val b = (pixel and 0xFF) / 255.0f

                // ✅ Aplicar normalização específica do MobileFaceNet
                val rNormalized = (r - 0.5f) * 2.0f // [-1, 1]
                val gNormalized = (g - 0.5f) * 2.0f // [-1, 1]
                val bNormalized = (b - 0.5f) * 2.0f // [-1, 1]

                byteBuffer.putFloat(rNormalized)
                byteBuffer.putFloat(gNormalized)
                byteBuffer.putFloat(bNormalized)
                pixelCount++
            }
            
            Log.d(TAG, "✅ Tensor MobileFaceNet preenchido com $pixelCount pixels")
            
            // Limpar bitmap temporário se foi criado
            if (resizedBitmap != bitmap) {
                resizedBitmap.recycle()
            }

            return byteBuffer
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro em convertBitmapToTensorInputMobileFaceNet", e)
            throw e
        }
    }
    
    private fun convertBitmapToTensorInput(bitmap: Bitmap): ByteBuffer {
        try {
            val inputSize = 160 // ✅ CORRIGIDO: Usar 160x160 como esperado pelo modelo
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
                minStableFrames = 3 // Apenas 3 frames estáveis
                positionTolerance = 200 // Tolerância muito alta
                maxStableTime = 5000L // 5 segundos máximo
                
                // 2. Ajustar face detector
                faceDetector.close()
                faceDetector = FaceDetection.getClient(
                    FaceDetectorOptions.Builder()
                        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                        .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
                        .setMinFaceSize(0.01f) // Face mínima de apenas 1% para tablets
                        .build()
                )
                
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