package com.example.iface_offilne

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.*
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.iface_offilne.data.AppDatabase
import com.example.iface_offilne.data.FuncionariosEntity
import com.example.iface_offilne.data.PontosGenericosEntity
import com.example.iface_offilne.service.PontoSincronizacaoService
import com.example.iface_offilne.helpers.FaceRecognitionHelper
import com.example.iface_offilne.helpers.LocationHelper
import com.example.iface_offilne.helpers.DeviceCapabilityHelper
import com.example.iface_offilne.helpers.AdaptiveFaceRecognitionHelper
import com.example.iface_offilne.helpers.AdvancedFaceRecognitionHelper
import com.example.iface_offilne.helpers.FaceRecognitionDebugHelper
import com.example.iface_offilne.helpers.PerformanceLevel
import com.example.iface_offilne.helpers.bitmapToBase64
import com.example.iface_offilne.helpers.cropFace
import com.example.iface_offilne.helpers.fixImageOrientationDefinitive
import com.example.iface_offilne.helpers.toBitmap
import com.example.iface_offilne.util.FaceOverlayView
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.*

/**
 * Activity principal para registro de ponto com reconhecimento facial
 * 
 * ✅ VERSÃO SIMPLIFICADA E ORGANIZADA:
 * - Sistema de reconhecimento facial simplificado
 * - Cooldown de 5 segundos entre registros
 * - Interface limpa e funcional
 * - Tratamento de erros robusto
 */
class PontoActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var imageAnalyzer: ImageAnalysis
    private lateinit var overlay: FaceOverlayView
    private lateinit var statusText: TextView
    private lateinit var funcionarioInfo: LinearLayout
    private lateinit var funcionarioNome: TextView
    private lateinit var ultimoPonto: TextView
    private lateinit var tipoPontoRadioGroup: RadioGroup

    private var interpreter: Interpreter? = null
    private var modelLoaded = false
    private var modelInputWidth = 160
    private var modelInputHeight = 160
    private var modelOutputSize = 192 // ✅ CORRIGIDO: Usar 192 como na CameraActivity

    private var faceRecognitionHelper: FaceRecognitionHelper? = null
    private var locationHelper: LocationHelper? = null
    private var funcionarioReconhecido: FuncionariosEntity? = null
    private var processandoFace = false
    private var currentFaceBitmap: Bitmap? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var lastProcessingTime = 0L
    private var processingTimeout = 10000L
    
    // ✅ COOLDOWN: Sistema para evitar múltiplos registros
    private var lastPontoRegistrado = 0L
    private var cooldownPonto = 8000L // 8 segundos de cooldown
    
    // ✅ NOVO: Helpers adaptativos para reconhecimento facial
    private var deviceCapabilityHelper: DeviceCapabilityHelper? = null
    private var adaptiveFaceRecognitionHelper: AdaptiveFaceRecognitionHelper? = null
    private var advancedFaceRecognitionHelper: AdvancedFaceRecognitionHelper? = null
    
    // ✅ FALLBACK: Sistema de fallback para TensorFlow
    private var tensorFlowFallbackMode = false
    private var tensorFlowErrorCount = 0
    private var lastTensorFlowError = 0L
    private var maxTensorFlowErrors = 3

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private const val REQUEST_CODE_LOCATION_PERMISSIONS = 20
        private val REQUIRED_PERMISSIONS = when {
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.POST_NOTIFICATIONS
                )
            }
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU -> {
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.READ_MEDIA_IMAGES
                )
            }
            else -> {
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                )
            }
        }
        private val LOCATION_PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        private const val TAG = "PontoActivity"
    }

    private var faceDetector: com.google.mlkit.vision.face.FaceDetector? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ponto)
        
        Log.d(TAG, "🚀 === INICIANDO PONTOACTIVITY ===")
        
        try {
            // ✅ VERIFICAR SE VEM DA TELA DE SUCESSO
            val fromSuccessScreen = intent.getBooleanExtra("FROM_SUCCESS_SCREEN", false)
            if (fromSuccessScreen) {
                Log.d(TAG, "🔄 Reinicialização completa após tela de sucesso")
                // ✅ FORÇAR LIMPEZA COMPLETA DOS RECURSOS
                forceCleanup()
            }
            
            setupUI()
            Log.d(TAG, "✅ UI configurada")
            
            initializeHelpers()
            Log.d(TAG, "✅ Helpers inicializados")
            
            loadTensorFlowModel()
            Log.d(TAG, "✅ Modelo TensorFlow iniciado")
            
            createTestEmployeeIfNeeded()
            Log.d(TAG, "✅ Funcionário de teste verificado")
            
            if (allPermissionsGranted()) {
                Log.d(TAG, "✅ Permissões concedidas")
                if (!allLocationPermissionsGranted()) {
                    Log.d(TAG, "📍 Solicitando permissões de localização")
                    ActivityCompat.requestPermissions(this, LOCATION_PERMISSIONS, REQUEST_CODE_LOCATION_PERMISSIONS)
                } else {
                    Log.d(TAG, "📍 Permissões de localização já concedidas")
                }
                startCamera()
            } else {
                Log.d(TAG, "🔐 Solicitando permissões")
                ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
            }
            
            Log.d(TAG, "✅ PontoActivity inicializada com sucesso")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro crítico na inicialização", e)
            e.printStackTrace()
            Toast.makeText(this, "❌ Erro na inicialização: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupUI() {
        statusText = findViewById(R.id.statusText)
        previewView = findViewById(R.id.previewView)
        overlay = findViewById(R.id.overlay)
        funcionarioInfo = findViewById(R.id.funcionarioInfo)
        funcionarioNome = findViewById(R.id.funcionarioNome)
        ultimoPonto = findViewById(R.id.ultimoPonto)
        tipoPontoRadioGroup = findViewById(R.id.tipoPontoRadioGroup)
        
        // Mensagem da mensagem de sucesso
        statusText.text = ""
        
        findViewById<Button>(R.id.btnVoltar).setOnClickListener {
            val intent = Intent(this, ConfiguracoesActivity::class.java)
            startActivity(intent)
        }
        
        findViewById<Button>(R.id.btnSair).setOnClickListener {
            onBackPressed()
        }
    }

    private fun initializeHelpers() {
        try {
            Log.d(TAG, "🔧 Inicializando helpers...")
            
            // ✅ INICIALIZAR FACE DETECTOR
            try {
                faceDetector = FaceDetection.getClient(
                    FaceDetectorOptions.Builder()
                        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                        .setMinFaceSize(0.05f) // Detecta faces muito pequenas
                        .build()
                )
                Log.d(TAG, "✅ FaceDetector inicializado")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao inicializar FaceDetector: ${e.message}")
                e.printStackTrace()
            }
            
            // ✅ NOVO: Inicializar DeviceCapabilityHelper primeiro
            try {
                deviceCapabilityHelper = DeviceCapabilityHelper(this)
                Log.d(TAG, "✅ DeviceCapabilityHelper inicializado")
                
                val isSupported = deviceCapabilityHelper?.isFaceRecognitionSupported() ?: false
                Log.d(TAG, "🔍 Suporte ao reconhecimento facial: $isSupported")
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao inicializar DeviceCapabilityHelper: ${e.message}")
                e.printStackTrace()
            }
            
            try {
                adaptiveFaceRecognitionHelper = AdaptiveFaceRecognitionHelper(this)
                Log.d(TAG, "✅ AdaptiveFaceRecognitionHelper inicializado")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao inicializar AdaptiveFaceRecognitionHelper: ${e.message}")
                e.printStackTrace()
            }
            
            try {
                faceRecognitionHelper = FaceRecognitionHelper(this)
                Log.d(TAG, "✅ FaceRecognitionHelper inicializado")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao inicializar FaceRecognitionHelper: ${e.message}")
                e.printStackTrace()
            }
            
            try {
                advancedFaceRecognitionHelper = AdvancedFaceRecognitionHelper(this)
                Log.d(TAG, "✅ AdvancedFaceRecognitionHelper inicializado")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao inicializar AdvancedFaceRecognitionHelper: ${e.message}")
                e.printStackTrace()
            }
            
            try {
                locationHelper = LocationHelper(this)
                Log.d(TAG, "✅ LocationHelper inicializado")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao inicializar LocationHelper: ${e.message}")
                e.printStackTrace()
            }
            
            Log.d(TAG, "✅ Helpers inicializados com sucesso")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro crítico ao inicializar helpers", e)
            e.printStackTrace()
        }
    }

    private fun loadTensorFlowModel() {
        Log.d(TAG, "🤖 Carregando modelo TensorFlow...")
        
        // ✅ PROTEÇÃO: Verificar se já está carregando
        if (modelLoaded && interpreter != null) {
            Log.d(TAG, "✅ Modelo já carregado - pulando carregamento")
            return
        }
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // ✅ PROTEÇÃO: Verificar se a Activity ainda está válida
                if (isFinishing || isDestroyed) {
                    Log.w(TAG, "⚠️ Activity finalizada - cancelando carregamento do modelo")
                    return@launch
                }
                
                // ✅ PROTEÇÃO: Limpar interpreter anterior
                try {
                    interpreter?.close()
                    interpreter = null
                    modelLoaded = false
                    Log.d(TAG, "🧹 Interpreter anterior limpo")
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ Erro ao limpar interpreter anterior: ${e.message}")
                }
                
                val modelFile = try {
                    Log.d(TAG, "📁 Tentando abrir modelo do assets...")
                    assets.open("facenet_model.tflite")
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ Modelo não encontrado em assets, tentando raw resources...")
                    try {
                        resources.openRawResource(R.raw.mobilefacenet)
                    } catch (e2: Exception) {
                        Log.e(TAG, "❌ Erro ao abrir modelo: ${e2.message}")
                        throw e2
                    }
                }
                
                Log.d(TAG, "📄 Modelo encontrado, lendo bytes...")
                val modelBuffer = modelFile.use { input ->
                    val available = input.available()
                    if (available <= 0 || available > 100 * 1024 * 1024) { // Máximo 100MB
                        throw Exception("Tamanho de modelo inválido: $available bytes")
                    }
                    
                    val bytes = ByteArray(available)
                    val bytesRead = input.read(bytes)
                    if (bytesRead != available) {
                        throw Exception("Erro na leitura do modelo: lidos $bytesRead de $available bytes")
                    }
                    
                    Log.d(TAG, "📊 Bytes lidos: ${bytes.size}")
                    
                    try {
                        ByteBuffer.allocateDirect(bytes.size).apply {
                            order(ByteOrder.nativeOrder())
                            put(bytes)
                            rewind()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Erro ao alocar buffer do modelo: ${e.message}")
                        throw e
                    }
                }
                
                Log.d(TAG, "⚙️ Configurando opções do Interpreter...")
                val options = try {
                    Interpreter.Options().apply {
                        setNumThreads(1)
                        setUseNNAPI(false)
                        setAllowFp16PrecisionForFp32(false) // Desabilitar FP16 para evitar problemas
                        setAllowBufferHandleOutput(false) // Desabilitar buffer handle
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Erro ao configurar opções: ${e.message}")
                    throw e
                }
                
                Log.d(TAG, "🔧 Criando Interpreter...")
                interpreter = try {
                    // ✅ PROTEÇÃO: Verificar se o modelo não está corrompido
                    if (modelBuffer.capacity() < 1000) {
                        throw Exception("Modelo muito pequeno, possivelmente corrompido")
                    }
                    
                    Interpreter(modelBuffer, options)
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Erro ao criar Interpreter: ${e.message}")
                    throw e
                }
                
                // ✅ PROTEÇÃO: Verificar se o interpreter foi criado corretamente
                if (interpreter != null) {
                                    // ✅ PROTEÇÃO: Testar o interpreter com dados dummy
                try {
                    val currentInterpreter = interpreter
                    if (currentInterpreter != null) {
                        // ✅ DETECTAR DIMENSÕES DO MODELO AUTOMATICAMENTE
                        detectModelDimensions(currentInterpreter)
                        
                        val testInput = ByteBuffer.allocateDirect(4 * modelInputWidth * modelInputHeight * 3)
                        testInput.order(ByteOrder.nativeOrder())
                        val testOutput = Array(1) { FloatArray(modelOutputSize) }
                        
                        currentInterpreter.run(testInput, testOutput)
                        Log.d(TAG, "✅ Teste do interpreter bem-sucedido")
                        Log.d(TAG, "📊 Dimensões detectadas: ${modelInputWidth}x${modelInputHeight} → ${modelOutputSize}")
                        
                        modelLoaded = true
                        Log.d(TAG, "✅ Modelo TensorFlow carregado com sucesso")
                    } else {
                        Log.e(TAG, "❌ Interpreter é nulo durante teste")
                        modelLoaded = false
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Erro no teste do interpreter: ${e.message}")
                    interpreter?.close()
                    interpreter = null
                    modelLoaded = false
                    throw e
                }
                } else {
                    Log.e(TAG, "❌ Interpreter criado mas é nulo")
                    modelLoaded = false
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao carregar modelo TensorFlow", e)
                e.printStackTrace()
                modelLoaded = false
                
                // ✅ PROTEÇÃO: Limpar recursos em caso de erro
                try {
                    interpreter?.close()
                    interpreter = null
                } catch (closeException: Exception) {
                    Log.e(TAG, "❌ Erro ao fechar interpreter: ${closeException.message}")
                }
            }
        }
    }

    private fun startCamera() {
        Log.d(TAG, "📷 Iniciando câmera")
        
        // ✅ PROTEÇÃO: Verificar se a Activity ainda está válida
        if (isFinishing || isDestroyed) {
            Log.w(TAG, "⚠️ Activity finalizada - cancelando inicialização da câmera")
            return
        }
        
        // ✅ PROTEÇÃO: Verificar se a câmera já está ativa
        if (cameraProvider != null) {
            Log.d(TAG, "✅ Câmera já está ativa - pulando inicialização")
            return
        }
        
        try {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
            cameraProviderFuture.addListener({
                try {
                    // ✅ PROTEÇÃO: Verificar novamente se a Activity ainda está válida
                    if (isFinishing || isDestroyed) {
                        Log.w(TAG, "⚠️ Activity finalizada durante inicialização da câmera")
                        return@addListener
                    }
                    
                    cameraProvider = cameraProviderFuture.get()
                    Log.d(TAG, "✅ CameraProvider obtido")

                    // ✅ PROTEÇÃO: Verificar se previewView está inicializado
                    if (!::previewView.isInitialized) {
                        Log.e(TAG, "❌ PreviewView não inicializado")
                        return@addListener
                    }

                    val preview = Preview.Builder()
                        .setTargetResolution(android.util.Size(800, 600))
                        .build().also {
                            try {
                                val preview = previewView
                                it.setSurfaceProvider(preview.surfaceProvider)
                                Log.d(TAG, "✅ Preview configurado")
                            } catch (e: Exception) {
                                Log.e(TAG, "❌ Erro ao configurar preview: ${e.message}")
                                return@addListener
                            }
                        }

                    imageAnalyzer = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setTargetResolution(android.util.Size(640, 480))
                        .build()
                        .also {
                            try {
                                it.setAnalyzer(ContextCompat.getMainExecutor(this)) { imageProxy ->
                                    processImage(imageProxy)
                                }
                                Log.d(TAG, "✅ ImageAnalyzer configurado")
                            } catch (e: Exception) {
                                Log.e(TAG, "❌ Erro ao configurar ImageAnalyzer: ${e.message}")
                                return@addListener
                            }
                        }

                    val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
                    Log.d(TAG, "✅ CameraSelector configurado")

                    try {
                        val provider = cameraProvider
                        provider?.unbindAll()
                        Log.d(TAG, "✅ Câmeras desvinculadas")
                    } catch (e: Exception) {
                        Log.w(TAG, "⚠️ Erro ao desvincular câmeras: ${e.message}")
                    }

                    try {
                        val provider = cameraProvider
                        provider?.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer)
                        Log.d(TAG, "✅ Câmera vinculada ao lifecycle")
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Erro ao vincular câmera: ${e.message}")
                        return@addListener
                    }
                    
                    Log.d(TAG, "✅ Câmera iniciada com sucesso")
                    
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Erro ao iniciar câmera", e)
                    e.printStackTrace()
                }
            }, ContextCompat.getMainExecutor(this))
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro crítico ao obter CameraProvider", e)
            e.printStackTrace()
        }
    }

    private fun processImage(imageProxy: ImageProxy) {
        try {
            // ✅ PROTEÇÃO CRÍTICA: Verificar se a Activity ainda está válida
            if (isFinishing || isDestroyed) {
                Log.w(TAG, "⚠️ Activity finalizada - fechando imageProxy sem processar")
                try {
                    imageProxy.close()
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ Erro ao fechar imageProxy: ${e.message}")
                }
                return
            }

            // ✅ RESET: Verificar se deve resetar modo fallback
            resetFallbackMode()

            // ✅ PROTEÇÃO: Verificar se o faceDetector ainda está válido
            if (faceDetector == null) {
                Log.w(TAG, "⚠️ FaceDetector nulo - fechando imageProxy")
                try {
                    imageProxy.close()
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ Erro ao fechar imageProxy: ${e.message}")
                }
                return
            }

            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                Log.d(TAG, "📸 Processando imagem: ${mediaImage.width}x${mediaImage.height}")
                
                try {
                    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                    Log.d(TAG, "🔄 Imagem convertida para InputImage")

                    val detector = faceDetector
                    detector?.process(image)?.addOnSuccessListener { faces ->
                            try {
                                Log.d(TAG, "👥 Faces detectadas: ${faces.size}")
                                
                                if (faces.isNotEmpty()) {
                                    val face = faces[0]
                                    Log.d(TAG, "🎯 Face principal: ${face.boundingBox}")
                                    
                                    try {
                                        if (!isFinishing && !isDestroyed && ::overlay.isInitialized) {
                                            val overlayView = overlay
                                            overlayView.setBoundingBox(face.boundingBox, mediaImage.width, mediaImage.height)
                                        } else {
                                            Log.w(TAG, "⚠️ Overlay não disponível para atualização")
                                        }
                                    } catch (e: Exception) {
                                        Log.w(TAG, "⚠️ Erro ao atualizar overlay: ${e.message}")
                                    }

                                    val faceArea = face.boundingBox.width() * face.boundingBox.height()
                                    val screenArea = mediaImage.width * mediaImage.height
                                    val faceRatio = faceArea.toFloat() / screenArea.toFloat()
                                    
                                    Log.d(TAG, "📊 Face ratio: $faceRatio")
                                    
                                    if (!processandoFace && modelLoaded && interpreter != null && !isFinishing && !isDestroyed) {
                                        if (isCooldownActive()) {
                                            val segundosRestantes = getCooldownRemainingSeconds()
                                            Log.d(TAG, "⏰ Aguardando cooldown: ${segundosRestantes}s restantes")
                                            
                                            try {
                                                if (!isFinishing && !isDestroyed && ::statusText.isInitialized) {
                                                    val status = statusText
                                                    status.text = "⏰ Aguarde ${segundosRestantes}s\npara próximo ponto"
                                                }
                                            } catch (e: Exception) {
                                                Log.w(TAG, "⚠️ Erro ao atualizar status do cooldown: ${e.message}")
                                            }
                                            
                                            try {
                                                imageProxy.close()
                                            } catch (e: Exception) {
                                                Log.w(TAG, "⚠️ Erro ao fechar imageProxy: ${e.message}")
                                            }
                                            return@addOnSuccessListener
                                        }
                                        
                                        Log.d(TAG, "✅ INICIANDO RECONHECIMENTO - QUALQUER FACE!")
                                        
                                        processandoFace = true
                                        lastProcessingTime = System.currentTimeMillis()
                                        
                                        // ✅ PROTEÇÃO: Atualizar status com verificação
                                        try {
                                            if (!isFinishing && !isDestroyed && ::statusText.isInitialized) {
                                                val status = statusText
                                                status.text = "🔍 Reconhecendo..."
                                            } else {
                                                Log.w(TAG, "⚠️ StatusText não disponível para atualização")
                                            }
                                        } catch (e: Exception) {
                                            Log.w(TAG, "⚠️ Erro ao atualizar status: ${e.message}")
                                        }
                                        
                                        // ✅ PROTEÇÃO: Converter bitmap com verificação
                                        try {
                                            val bitmap = toBitmap(mediaImage)
                                            Log.d(TAG, "🖼️ Bitmap convertido: ${bitmap.width}x${bitmap.height}")
                                            
                                            // ✅ PROTEÇÃO: Fechar imageProxy antes de processar
                                            try {
                                                imageProxy.close()
                                            } catch (e: Exception) {
                                                Log.w(TAG, "⚠️ Erro ao fechar imageProxy: ${e.message}")
                                            }
                                            
                                            processDetectedFace(bitmap, face.boundingBox)
                                        } catch (e: Exception) {
                                            Log.e(TAG, "❌ Erro ao converter bitmap", e)
                                            processandoFace = false
                                            try {
                                                imageProxy.close()
                                            } catch (closeException: Exception) {
                                                Log.w(TAG, "⚠️ Erro ao fechar imageProxy: ${closeException.message}")
                                            }
                                        }
                                    } else {
                                        Log.d(TAG, "⏸️ Não processando face - processandoFace: $processandoFace, modelLoaded: $modelLoaded, interpreter: ${interpreter != null}")
                                        try {
                                            imageProxy.close()
                                        } catch (e: Exception) {
                                            Log.w(TAG, "⚠️ Erro ao fechar imageProxy: ${e.message}")
                                        }
                                    }
                                } else {
                                    Log.d(TAG, "👤 Nenhuma face detectada")
                                    try {
                                        if (!isFinishing && !isDestroyed && ::overlay.isInitialized) {
                                            val overlayView = overlay
                                            overlayView.clear()
                                        }
                                    } catch (e: Exception) {
                                        Log.w(TAG, "⚠️ Erro ao limpar overlay: ${e.message}")
                                    }
                                    try {
                                        imageProxy.close()
                                    } catch (e: Exception) {
                                        Log.w(TAG, "⚠️ Erro ao fechar imageProxy: ${e.message}")
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "❌ Erro no processamento de faces", e)
                                processandoFace = false
                                try {
                                    imageProxy.close()
                                } catch (closeException: Exception) {
                                    Log.w(TAG, "⚠️ Erro ao fechar imageProxy: ${closeException.message}")
                                }
                            }
                        }?.addOnFailureListener { e ->
                            Log.e(TAG, "❌ Erro na detecção de faces", e)
                            processandoFace = false
                            try {
                                imageProxy.close()
                            } catch (closeException: Exception) {
                                Log.w(TAG, "⚠️ Erro ao fechar imageProxy: ${closeException.message}")
                            }
                        }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Erro ao criar InputImage", e)
                    try {
                        imageProxy.close()
                    } catch (closeException: Exception) {
                        Log.w(TAG, "⚠️ Erro ao fechar imageProxy: ${closeException.message}")
                    }
                }
            } else {
                Log.w(TAG, "⚠️ MediaImage nulo")
                try {
                    imageProxy.close()
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ Erro ao fechar imageProxy: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro crítico no processImage", e)
            e.printStackTrace()
            processandoFace = false
            try {
                imageProxy.close()
            } catch (closeException: Exception) {
                Log.w(TAG, "⚠️ Erro ao fechar imageProxy: ${closeException.message}")
            }
        }
    }

    private fun processDetectedFace(bitmap: Bitmap, boundingBox: Rect) {
        Log.d(TAG, "🔄 Processando face detectada - MODO DIRETO SIMPLIFICADO")
        
        // ✅ PROTEÇÃO CRÍTICA: Verificar se a Activity ainda está válida
        if (isFinishing || isDestroyed) {
            Log.w(TAG, "⚠️ Activity finalizada - cancelando processamento de face")
            processandoFace = false
            return
        }
        
        // ✅ PROTEÇÃO: Verificar se o bitmap é válido
        if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) {
            Log.e(TAG, "❌ Bitmap inválido - reciclado: ${bitmap.isRecycled}, dimensões: ${bitmap.width}x${bitmap.height}")
            processandoFace = false
            return
        }
        
        // ✅ PROTEÇÃO: Verificar se o boundingBox é válido
        if (boundingBox.isEmpty || boundingBox.width() <= 0 || boundingBox.height() <= 0) {
            Log.e(TAG, "❌ BoundingBox inválido: $boundingBox")
            processandoFace = false
            return
        }

        Log.d(TAG, "✅ Validações passadas - PROCESSAMENTO DIRETO")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // ✅ PROTEÇÃO: Verificar novamente se a Activity ainda está válida
                if (isFinishing || isDestroyed) {
                    Log.w(TAG, "⚠️ Activity finalizada durante processamento")
                    return@launch
                }

                // ✅ PROTEÇÃO: Recortar face com validação
                val faceBmp = try {
                    cropFace(bitmap, boundingBox)
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Erro ao recortar face: ${e.message}")
                    processandoFace = false
                    return@launch
                }

                if (faceBmp.isRecycled || faceBmp.width <= 0 || faceBmp.height <= 0) {
                    Log.e(TAG, "❌ Face recortada inválida")
                    processandoFace = false
                    return@launch
                } else {
                    Log.d(TAG, "✅ Face recortada válida: ${faceBmp.width}x${faceBmp.height}")
                }

                Log.d(TAG, "📸 Face recortada: ${faceBmp.width}x${faceBmp.height}")

                // ✅ PROTEÇÃO: Salvar foto da face com validação
                currentFaceBitmap = try {
                    val scaledBitmap = Bitmap.createScaledBitmap(faceBmp, 300, 300, true)
                    fixImageOrientationDefinitive(scaledBitmap)
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Erro ao processar foto: ${e.message}")
                    null
                }
                
                Log.d(TAG, "🎯 EXECUTANDO RECONHECIMENTO DIRETO")
                
                // ✅ RECONHECIMENTO DIRETO - SEM HELPER COMPLEXO
                val recognitionResult = performDirectRecognition(faceBmp)
                
                when (recognitionResult) {
                    is RecognitionResult.Success -> {
                        val funcionario = recognitionResult.funcionario
                        val similarity = recognitionResult.similarity
                        
                        Log.d(TAG, "✅ FUNCIONÁRIO RECONHECIDO DIRETAMENTE!")
                        Log.d(TAG, "👤 Funcionário: ${funcionario.nome}")
                        Log.d(TAG, "📊 Similaridade: ${String.format("%.3f", similarity)}")

                        lastPontoRegistrado = System.currentTimeMillis()
                        Log.d(TAG, "⏰ Cooldown iniciado - próximo ponto em ${cooldownPonto}ms")

                        // ✅ PROTEÇÃO: Registrar ponto com verificação de contexto
                        withContext(Dispatchers.Main) {
                            if (!isFinishing && !isDestroyed) {
                                CoroutineScope(Dispatchers.IO).launch {
                                    try {
                                        registrarPontoDireto(funcionario)
                                    } catch (e: Exception) {
                                        Log.e(TAG, "❌ Erro crítico no registro de ponto: ${e.message}")
                                        processandoFace = false
                                    }
                                }
                            } else {
                                Log.w(TAG, "⚠️ Activity finalizada antes do registro de ponto")
                                processandoFace = false
                            }
                        }
                    }
                    
                    is RecognitionResult.Failure -> {
                        Log.w(TAG, "❌ Reconhecimento direto falhou: ${recognitionResult.reason}")
                        
                        withContext(Dispatchers.Main) {
                            try {
                                if (!isFinishing && !isDestroyed) {
                                    val status = statusText
                                    status.text = ""

                                    status.postDelayed({
                                        try {
                                            if (!isFinishing && !isDestroyed) {
                                                val statusInner = statusText
                                                statusInner.text = ""
                                            }
                                        } catch (e: Exception) {
                                            Log.e(TAG, "❌ Erro no reset UI: ${e.message}")
                                        }
                                    }, 3000)
                                } else {
                                    Log.w(TAG, "⚠️ Activity finalizada - não atualizando UI")
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "❌ Erro ao atualizar UI: ${e.message}")
                            }
                        }
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro no processamento direto: ${e.message}")
                e.printStackTrace()

                withContext(Dispatchers.Main) {
                    try {
                        if (!isFinishing && !isDestroyed) {
                            val status = statusText
                            status.text = "❌ Erro no reconhecimento\nTente novamente"

                            status.postDelayed({
                                try {
                                    if (!isFinishing && !isDestroyed) {
                                        val statusInner = statusText
                                        statusInner.text = ""
                                    }
                                } catch (e2: Exception) {
                                    Log.e(TAG, "❌ Erro no reset UI: ${e2.message}")
                                }
                            }, 3000)
                        } else {
                            Log.w(TAG, "⚠️ Activity finalizada - não atualizando UI")
                        }
                    } catch (e2: Exception) {
                        Log.e(TAG, "❌ Erro ao atualizar UI: ${e2.message}")
                    }
                }
            } finally {
                processandoFace = false
            }
        }
    }
    
    /**
     * 🎯 RECONHECIMENTO DIRETO SIMPLIFICADO
     */
    private suspend fun performDirectRecognition(faceBmp: Bitmap): RecognitionResult {
        return try {
            Log.d(TAG, "🔍 === RECONHECIMENTO DIRETO ===")
            
            // ✅ VERIFICAR SE O TENSORFLOW ESTÁ DISPONÍVEL
            if (!modelLoaded || interpreter == null) {
                Log.e(TAG, "❌ TensorFlow não está disponível")
                return RecognitionResult.Failure("Sistema de reconhecimento não disponível")
            } else {
                Log.d(TAG, "✅ TensorFlow disponível para reconhecimento")
            }
            
            // ✅ VALIDAR QUALIDADE DA FACE ANTES DO RECONHECIMENTO
            val faceQuality = validateFaceQuality(faceBmp)
            if (!faceQuality.isValid) {
                Log.w(TAG, "❌ Face de baixa qualidade rejeitada: ${faceQuality.reason}")
                return RecognitionResult.Failure("")
            }
            
            Log.d(TAG, "✅ Face aprovada na validação de qualidade")
            
            // ✅ GERAR EMBEDDING
            val embedding = try {
                generateEmbeddingDirect(faceBmp)
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao gerar embedding: ${e.message}")
                return RecognitionResult.Failure("Erro ao processar face: ${e.message}")
            }
            
            if (embedding.isEmpty()) {
                Log.e(TAG, "❌ Embedding vazio")
                return RecognitionResult.Failure("Falha ao processar face")
            }
            
            // ✅ VALIDAR QUALIDADE DO EMBEDDING GERADO
            val embeddingQuality = validateEmbeddingQuality(embedding)
            if (!embeddingQuality.isValid) {
                Log.w(TAG, "❌ Embedding de baixa qualidade: ${embeddingQuality.reason}")
                return RecognitionResult.Failure("Processamento facial instável: ${embeddingQuality.reason}")
            }
            
            Log.d(TAG, "✅ Embedding gerado e validado: ${embedding.size} dimensões")
            
            // ✅ COMPARAR COM BANCO DE DADOS
            val db = AppDatabase.getInstance(this@PontoActivity)
            val faceDao = db.faceDao()
            val funcionarioDao = db.usuariosDao()
            
            val faces = faceDao.getAllFaces()
            val funcionarios = funcionarioDao.getUsuario()
            
            Log.d(TAG, "📊 Faces cadastradas: ${faces.size}")
            Log.d(TAG, "👥 Funcionários: ${funcionarios.size}")
            
            if (faces.isEmpty()) {
                Log.w(TAG, "⚠️ Nenhuma face cadastrada no banco")
                return RecognitionResult.Failure("Nenhuma face cadastrada no sistema")
            }
            
            // ✅ COMPARAR COM CADA FACE CADASTRADA
            var melhorSimilaridade = 0f
            var funcionarioReconhecido: FuncionariosEntity? = null
            var melhorFace: com.example.iface_offilne.data.FaceEntity? = null
            
            for (face in faces) {
                try {
                    val embeddingCadastrado = face.embedding.split(",").map { it.toFloat() }.toFloatArray()
                    
                    if (embeddingCadastrado.size != embedding.size) {
                        Log.w(TAG, "⚠️ Tamanho de embedding diferente: ${embeddingCadastrado.size} vs ${embedding.size}")
                        continue
                    }
                    
                    // ✅ VALIDAR QUALIDADE DO EMBEDDING CADASTRADO
                    if (embeddingCadastrado.all { it == 0f } || embeddingCadastrado.any { it.isNaN() || it.isInfinite() }) {
                        Log.w(TAG, "⚠️ Embedding cadastrado inválido para ${face.funcionarioId}")
                        continue
                    }
                    
                    val similaridade = calculateSimilarity(embedding, embeddingCadastrado)
                    // Log.d(TAG, "📊 Similaridade com ${face.funcionarioId}: ${String.format("%.3f", similaridade)}")
                    
                    if (similaridade > melhorSimilaridade) {
                        melhorSimilaridade = similaridade
                        funcionarioReconhecido = funcionarios.find { it.codigo == face.funcionarioId }
                        melhorFace = face
                    }
                    
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Erro ao comparar com face ${face.funcionarioId}: ${e.message}")
                }
            }
            
            // PROCESSOS DE SIMILIRADE MINIA
            val thresholdMinimo = 0.90f // 80% de similaridade mínima - MUITO RIGOROSO
            val thresholdIdeal = 0.95f // 90% para confiança alta - EXTREMAMENTE RIGOROSO
            
            if (funcionarioReconhecido != null && melhorSimilaridade >= thresholdMinimo) {
                return RecognitionResult.Success(funcionarioReconhecido, melhorSimilaridade)
            } else {
                if (melhorSimilaridade > 0.5f) {
                    return RecognitionResult.Failure("")
                } else {
                    return RecognitionResult.Failure("")
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro crítico no reconhecimento direto: ${e.message}")
            e.printStackTrace()
            return RecognitionResult.Failure("Erro interno: ${e.message}")
        }
    }
    
    /**
     * 🤖 GERAR EMBEDDING DIRETAMENTE
     */
    private fun generateEmbeddingDirect(bitmap: Bitmap): FloatArray {
        return try {
            // ✅ REDIMENSIONAR PARA O TAMANHO DO MODELO
            val resizedBitmap = if (bitmap.width != 160 || bitmap.height != 160) {
                Bitmap.createScaledBitmap(bitmap, 160, 160, true)
            } else {
                bitmap
            }
            
            // ✅ CONVERTER PARA TENSOR
            val inputTensor = convertBitmapToTensorInput(resizedBitmap)
            val output = Array(1) { FloatArray(modelOutputSize) }
            
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
     * 📊 CALCULAR SIMILARIDADE ENTRE EMBEDDINGS COM MÚLTIPLAS MÉTRICAS
     */
    private fun calculateSimilarity(embedding1: FloatArray, embedding2: FloatArray): Float {
        return try {
            if (embedding1.size != embedding2.size) {
                Log.w(TAG, "⚠️ Embeddings de tamanhos diferentes: ${embedding1.size} vs ${embedding2.size}")
                return 0f
            }
            
            // ✅ 1. CALCULAR COSSENO SIMILARITY
            var dotProduct = 0f
            var norm1 = 0f
            var norm2 = 0f
            
            for (i in embedding1.indices) {
                dotProduct += embedding1[i] * embedding2[i]
                norm1 += embedding1[i] * embedding1[i]
                norm2 += embedding2[i] * embedding2[i]
            }
            
            val cosineSimilarity = dotProduct / (kotlin.math.sqrt(norm1) * kotlin.math.sqrt(norm2))
            
            // ✅ 2. CALCULAR DISTÂNCIA EUCLIDIANA
            var euclideanDistance = 0f
            for (i in embedding1.indices) {
                val diff = embedding1[i] - embedding2[i]
                euclideanDistance += diff * diff
            }
            euclideanDistance = kotlin.math.sqrt(euclideanDistance)
            
            // ✅ 3. CALCULAR DISTÂNCIA MANHATTAN
            var manhattanDistance = 0f
            for (i in embedding1.indices) {
                manhattanDistance += kotlin.math.abs(embedding1[i] - embedding2[i])
            }
            
            // ✅ 4. NORMALIZAR E COMBINAR MÉTRICAS
            val normalizedCosine = (cosineSimilarity + 1) / 2 // [0, 1]
            val normalizedEuclidean = 1f / (1f + euclideanDistance) // [0, 1] - quanto menor a distância, maior a similaridade
            val normalizedManhattan = 1f / (1f + manhattanDistance / embedding1.size) // [0, 1] - normalizado pelo tamanho
            
            // ✅ 5. PESO DAS MÉTRICAS (Cosseno tem mais peso por ser mais confiável)
            val finalSimilarity = (normalizedCosine * 0.6f + normalizedEuclidean * 0.3f + normalizedManhattan * 0.1f)
            
            Log.d(TAG, "📊 Métricas calculadas:")
            Log.d(TAG, "   Cosseno: ${String.format("%.3f", normalizedCosine)}")
            Log.d(TAG, "   Euclidiana: ${String.format("%.3f", normalizedEuclidean)}")
            Log.d(TAG, "   Manhattan: ${String.format("%.3f", normalizedManhattan)}")
            Log.d(TAG, "   Final: ${String.format("%.3f", finalSimilarity)}")
            
            finalSimilarity
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao calcular similaridade: ${e.message}")
            0f
        }
    }
    
    /**
     * ✅ VALIDAR QUALIDADE DA FACE
     */
    private fun validateFaceQuality(bitmap: Bitmap): QualityResult {
        return try {
            // ✅ 1. VERIFICAR TAMANHO MÍNIMO
            if (bitmap.width < 80 || bitmap.height < 80) {
                return QualityResult(false, "Face muito pequena (${bitmap.width}x${bitmap.height})")
            }
            
            // ✅ 2. VERIFICAR LUMINOSIDADE
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            
            var totalBrightness = 0f
            
            for (pixel in pixels) {
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                
                val brightness = (r + g + b) / 3f / 255f
                totalBrightness += brightness
            }
            
            val avgBrightness = totalBrightness / pixels.size
            
            // ✅ 3. VERIFICAR SE NÃO ESTÁ MUITO ESCURO OU MUITO CLARO
            if (avgBrightness < 0.15f) {
                return QualityResult(false, "Imagem muito escura (${String.format("%.1f", avgBrightness * 100)}%)")
            }
            
            if (avgBrightness > 0.85f) {
                return QualityResult(false, "Imagem muito clara (${String.format("%.1f", avgBrightness * 100)}%)")
            }
            
            // ✅ 4. VERIFICAR VARIAÇÃO DE PIXELS (CONTRASTE)
            var variance = 0f
            for (pixel in pixels) {
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                
                val brightness = (r + g + b) / 3f / 255f
                val diff = brightness - avgBrightness
                variance += diff * diff
            }
            variance /= pixels.size
            
            if (variance < 0.01f) {
                return QualityResult(false, "Imagem sem contraste suficiente")
            }
            
            Log.d(TAG, "✅ Qualidade da face aprovada: luminosidade=${String.format("%.2f", avgBrightness)}, contraste=${String.format("%.3f", variance)}")
            return QualityResult(true, "Qualidade aprovada")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao validar qualidade da face: ${e.message}")
            return QualityResult(false, "Erro na validação: ${e.message}")
        }
    }
    
    /**
     * ✅ VALIDAR QUALIDADE DO EMBEDDING
     */
    private fun validateEmbeddingQuality(embedding: FloatArray): QualityResult {
        return try {
            // ✅ 1. VERIFICAR SE NÃO É TUDO ZERO
            if (embedding.all { it == 0f }) {
                return QualityResult(false, "Embedding zerado")
            }
            
            // ✅ 2. VERIFICAR SE HÁ VALORES INVÁLIDOS
            if (embedding.any { it.isNaN() || it.isInfinite() }) {
                return QualityResult(false, "Embedding com valores inválidos")
            }
            
            // ✅ 3. VERIFICAR VARIÂNCIA DO EMBEDDING
            val mean = embedding.average().toFloat()
            var variance = 0f
            for (value in embedding) {
                val diff = value - mean
                variance += diff * diff
            }
            variance /= embedding.size
            
            if (variance < 0.001f) {
                return QualityResult(false, "Embedding sem variação suficiente (variância: ${String.format("%.6f", variance)})")
            }
            
            // ✅ 4. VERIFICAR MAGNITUDE DO EMBEDDING
            var magnitude = 0f
            for (value in embedding) {
                magnitude += value * value
            }
            magnitude = kotlin.math.sqrt(magnitude)
            
            if (magnitude < 0.1f) {
                return QualityResult(false, "Embedding com magnitude muito baixa (${String.format("%.3f", magnitude)})")
            }
            
            Log.d(TAG, "✅ Qualidade do embedding aprovada: variância=${String.format("%.6f", variance)}, magnitude=${String.format("%.3f", magnitude)}")
            return QualityResult(true, "Embedding de qualidade")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao validar qualidade do embedding: ${e.message}")
            return QualityResult(false, "Erro na validação: ${e.message}")
        }
    }
    
    /**
     * 📊 RESULTADO DE QUALIDADE
     */
    data class QualityResult(val isValid: Boolean, val reason: String)
    
    /**
     * 📊 RESULTADO DO RECONHECIMENTO
     */
    sealed class RecognitionResult {
        data class Success(val funcionario: FuncionariosEntity, val similarity: Float) : RecognitionResult()
        data class Failure(val reason: String) : RecognitionResult()
    }

    private suspend fun registrarPontoDireto(funcionario: FuncionariosEntity) {
        try {
            Log.d(TAG, "💾 Registrando ponto para: ${funcionario.nome}")
            
            if (isFinishing || isDestroyed) {
                Log.w(TAG, "⚠️ Activity finalizada - cancelando registro de ponto")
                return
            }
            
            val horarioAtual = System.currentTimeMillis()
            val formato = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
            val dataFormatada = formato.format(Date(horarioAtual))
            
            var latitude: Double? = null
            var longitude: Double? = null
            
            try {
                val helper = locationHelper
                val locationData = helper?.getCurrentLocationForPoint()
                if (locationData != null) {
                    latitude = locationData.latitude
                    longitude = locationData.longitude
                    Log.d(TAG, "📍 Localização capturada: $latitude, $longitude")
                } else {
                    Log.w(TAG, "⚠️ Localização não disponível")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro na localização: ${e.message}")
            }
            
            // ✅ PROTEÇÃO: Converter foto com validação
            val fotoBase64 = try {
                val bitmap = currentFaceBitmap
                bitmap?.let { bmp ->
                    if (!bmp.isRecycled && bmp.width > 0 && bmp.height > 0) {
                        val base64 = bitmapToBase64(bmp, 80)
                        base64
                    } else {
                        Log.w(TAG, "⚠️ Bitmap inválido para conversão")
                        null
                    }
                } ?: null
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao converter foto: ${e.message}")
                null
            }
            
            // ✅ PROTEÇÃO: Validar dados do funcionário
            val funcionarioId = funcionario.codigo ?: "FUNCIONARIO_${System.currentTimeMillis()}"
            val funcionarioNome = funcionario.nome ?: "Funcionário"
            
            if (funcionarioId.isEmpty()) {
                Log.e(TAG, "❌ ID do funcionário vazio")
                throw Exception("ID do funcionário inválido")
            }
            
            // ✅ PROTEÇÃO: Criar ponto com validação
            val ponto = try {
                PontosGenericosEntity(
                    funcionarioId = funcionarioId,
                    funcionarioNome = funcionarioNome,
                    funcionarioMatricula = funcionario.matricula ?: "",
                    funcionarioCpf = funcionario.cpf ?: "",
                    funcionarioCargo = funcionario.cargo ?: "",
                    funcionarioSecretaria = funcionario.secretaria ?: "",
                    funcionarioLotacao = funcionario.lotacao ?: "",
                    tipoPonto = "PONTO",
                    dataHora = horarioAtual,
                    latitude = latitude,
                    longitude = longitude,
                    fotoBase64 = fotoBase64
                )
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao criar entidade de ponto: ${e.message}")
                throw Exception("Erro ao criar ponto: ${e.message}")
            }
            
            var pontoSalvo = false
            var tentativas = 0
            val maxTentativas = 3
            
            while (!pontoSalvo && tentativas < maxTentativas) {
                try {
                    tentativas++
                    Log.d(TAG, "💾 Tentativa $tentativas de salvar ponto...")
                    
                    AppDatabase.getInstance(this@PontoActivity).pontosGenericosDao().insert(ponto)
                    pontoSalvo = true
                    Log.d(TAG, "✅ Ponto salvo com sucesso na tentativa $tentativas")
                    
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Erro na tentativa $tentativas: ${e.message}")
                    if (tentativas >= maxTentativas) {
                        throw Exception("Falha ao salvar ponto após $maxTentativas tentativas: ${e.message}")
                    }
                    kotlinx.coroutines.delay(500)
                }
            }
            
            try {
                val pontoService = PontoSincronizacaoService()
                pontoService.salvarPontoParaSincronizacao(
                    this@PontoActivity,
                    funcionarioId,
                    funcionarioNome,
                    "ponto",
                    fotoBase64,
                    latitude,
                    longitude
                )
                Log.d(TAG, "✅ Ponto salvo para sincronização")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro na sincronização (não crítico): ${e.message}")
            }
            
            withContext(Dispatchers.Main) {
                try {
                    if (!isFinishing && !isDestroyed) {
                        showConfirmationUI(funcionario, fotoBase64)
                    } else {
                        Log.w(TAG, "⚠️ Activity finalizada durante atualização da UI")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Erro ao mostrar sucesso: ${e.message}")
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro crítico ao registrar ponto: ${e.message}")
            e.printStackTrace()
            
            // ✅ PROTEÇÃO: Mostrar erro com verificações de contexto
            withContext(Dispatchers.Main) {
                try {
                    if (!isFinishing && !isDestroyed) {
                        val status = statusText
                        status.text = "❌ Erro ao registrar ponto\nTente novamente"
                        
                        status.postDelayed({
                            try {
                                if (!isFinishing && !isDestroyed) {
                                    val statusInner = statusText
                                    statusInner.text = ""
                                } else {
                                    Log.w(TAG, "⚠️ Activity finalizada - não atualizando UI")
                                }
                            } catch (e2: Exception) {
                                Log.e(TAG, "❌ Erro no reset: ${e2.message}")
                            }
                        }, 3000)
                    } else {
                        Log.w(TAG, "⚠️ Activity finalizada - não atualizando UI")
                    }
                } catch (e2: Exception) {
                    Log.e(TAG, "❌ Erro ao mostrar erro: ${e2.message}")
                }
            }
        } finally {
            // ✅ PROTEÇÃO: Sempre resetar flags
            processandoFace = false
            lastProcessingTime = 0L
        }
    }

    private fun convertBitmapToTensorInput(bitmap: Bitmap): ByteBuffer {
        try {
            Log.d(TAG, "🔄 Convertendo bitmap para tensor - entrada: ${bitmap.width}x${bitmap.height}")
            
            val inputSize = modelInputWidth
            
            // ✅ PROTEÇÃO CRÍTICA: Validar bitmap de entrada
            if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) {
                Log.e(TAG, "❌ Bitmap inválido para conversão - reciclado: ${bitmap.isRecycled}, dimensões: ${bitmap.width}x${bitmap.height}")
                throw IllegalArgumentException("Bitmap inválido")
            }

            // ✅ PROTEÇÃO: Verificar se o bitmap não é muito grande
            if (bitmap.width > 2048 || bitmap.height > 2048) {
                Log.w(TAG, "⚠️ Bitmap muito grande: ${bitmap.width}x${bitmap.height} - redimensionando")
            }

            // ✅ PROTEÇÃO: Alocar ByteBuffer com validação
            val bufferSize = 4 * inputSize * inputSize * 3
            if (bufferSize <= 0 || bufferSize > 100 * 1024 * 1024) { // Máximo 100MB
                Log.e(TAG, "❌ Tamanho de buffer inválido: $bufferSize bytes")
                throw IllegalArgumentException("Tamanho de buffer inválido")
            }
            
            val byteBuffer = try {
                ByteBuffer.allocateDirect(bufferSize)
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao alocar ByteBuffer: ${e.message}")
                throw e
            }
            
            byteBuffer.order(ByteOrder.nativeOrder())

            // ✅ PROTEÇÃO: Redimensionar bitmap com validação
            val resizedBitmap = if (bitmap.width != inputSize || bitmap.height != inputSize) {
                try {
                    Log.d(TAG, "📏 Redimensionando bitmap de ${bitmap.width}x${bitmap.height} para ${inputSize}x${inputSize}")
                    val scaled = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
                    if (scaled == null || scaled.isRecycled) {
                        throw Exception("Falha ao redimensionar bitmap")
                    }
                    scaled
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Erro ao redimensionar bitmap: ${e.message}")
                    throw e
                }
            } else {
                Log.d(TAG, "✅ Bitmap já tem o tamanho correto")
                bitmap
            }
            
            // ✅ PROTEÇÃO: Alocar array de pixels com validação
            val pixelCount = inputSize * inputSize
            if (pixelCount <= 0 || pixelCount > 10 * 1024 * 1024) { // Máximo 10M pixels
                Log.e(TAG, "❌ Número de pixels inválido: $pixelCount")
                throw IllegalArgumentException("Número de pixels inválido")
            }
            
            val intValues = try {
                IntArray(pixelCount)
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao alocar array de pixels: ${e.message}")
                throw e
            }
            
            // ✅ PROTEÇÃO: Obter pixels com validação
            try {
                resizedBitmap.getPixels(intValues, 0, inputSize, 0, 0, inputSize, inputSize)
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao obter pixels do bitmap: ${e.message}")
                throw e
            }

            // ✅ PROTEÇÃO: Processar pixels com validação
            try {
                for (pixel in intValues) {
                    val r = ((pixel shr 16) and 0xFF) / 127.5f - 1.0f
                    val g = ((pixel shr 8) and 0xFF) / 127.5f - 1.0f
                    val b = (pixel and 0xFF) / 127.5f - 1.0f
                    
                    val rFinal = if (r.isNaN() || r.isInfinite()) 0.0f else r
                    val gFinal = if (g.isNaN() || g.isInfinite()) 0.0f else g
                    val bFinal = if (b.isNaN() || b.isInfinite()) 0.0f else b

                    byteBuffer.putFloat(rFinal)
                    byteBuffer.putFloat(gFinal)
                    byteBuffer.putFloat(bFinal)
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao processar pixels: ${e.message}")
                throw e
            }
            
            // ✅ PROTEÇÃO: Limpar bitmap temporário
            if (resizedBitmap != bitmap) {
                try {
                    if (!resizedBitmap.isRecycled) {
                        resizedBitmap.recycle()
                        Log.d(TAG, "✅ Bitmap temporário reciclado")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ Erro ao reciclar bitmap: ${e.message}")
                }
            }

            Log.d(TAG, "✅ Conversão para tensor concluída: ${byteBuffer.capacity()} bytes")
            return byteBuffer
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro crítico na conversão do bitmap: ${e.message}")
            throw e
        }
    }

    private fun createTestEmployeeIfNeeded() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "🔍 === VERIFICANDO E CRIANDO DADOS DE TESTE ===")
                
                val db = AppDatabase.getInstance(this@PontoActivity)
                val funcionarioDao = db.usuariosDao()
                val faceDao = db.faceDao()
                
                val funcionarios = funcionarioDao.getUsuario()
                val faces = faceDao.getAllFaces()
                
                Log.d(TAG, "📊 Estado atual: ${funcionarios.size} funcionários, ${faces.size} faces")
                
                // ✅ CRIAR FUNCIONÁRIO DE TESTE SE NECESSÁRIO
                var funcionarioTeste: FuncionariosEntity? = null
                if (funcionarios.isEmpty()) {
                    Log.d(TAG, "📝 Criando funcionário de teste...")
                    
                    funcionarioTeste = FuncionariosEntity(
                        id = 1,
                        codigo = "TEST001",
                        nome = "Funcionário Teste",
                        matricula = "001",
                        cpf = "00000000000",
                        cargo = "Teste",
                        secretaria = "TI",
                        lotacao = "Desenvolvimento",
                        ativo = 1
                    )
                    
                    funcionarioDao.insert(funcionarioTeste)
                    Log.d(TAG, "✅ Funcionário de teste criado: ${funcionarioTeste.nome}")
                } else {
                    funcionarioTeste = funcionarios.first()
                    Log.d(TAG, "✅ Funcionário já existe: ${funcionarioTeste.nome}")
                }
                
                // ✅ CRIAR FACE DE TESTE SE NECESSÁRIO
                if (faces.isEmpty()) {
                    Log.d(TAG, "📝 Criando face de teste com embedding realista...")
                    
                    try {
                        // ✅ GERAR EMBEDDING USANDO O PRÓPRIO TENSORFLOW
                        val testEmbedding = generateTestEmbedding()
                        if (testEmbedding != null) {
                            val embeddingString = testEmbedding.joinToString(",")
                            
                            val faceTeste = com.example.iface_offilne.data.FaceEntity(
                                id = 0,
                                funcionarioId = funcionarioTeste.codigo ?: "TEST001",
                                embedding = embeddingString,
                                synced = true
                            )
                            
                            kotlinx.coroutines.runBlocking { faceDao.insert(faceTeste) }
                            Log.d(TAG, "✅ Face de teste criada com embedding TensorFlow (${testEmbedding.size} dimensões)")
                            
                            // ✅ VERIFICAR SE FOI SALVA CORRETAMENTE
                            val savedFace = faceDao.getByFuncionarioId(funcionarioTeste.codigo ?: "TEST001")
                            if (savedFace != null) {
                                Log.d(TAG, "✅ Face de teste salva com sucesso - ID: ${savedFace.id}")
                                Log.d(TAG, "📐 Embedding: ${savedFace.embedding.length} caracteres")
                            } else {
                                Log.e(TAG, "❌ Erro: Face de teste não foi salva")
                            }
                        } else {
                            Log.w(TAG, "⚠️ Falha ao gerar embedding - criando embedding aleatório")
                            createRandomTestFace(funcionarioTeste, faceDao)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Erro ao gerar face com TensorFlow - usando método alternativo: ${e.message}")
                        createRandomTestFace(funcionarioTeste, faceDao)
                    }
                } else {
                    Log.d(TAG, "✅ Faces já cadastradas:")
                    faces.forEach { face ->
                        Log.d(TAG, "   - ID: ${face.funcionarioId}, Embedding: ${face.embedding.length} chars")
                    }
                }
                
                // ✅ VERIFICAR ESTADO FINAL
                val finalFuncionarios = funcionarioDao.getUsuario()
                val finalFaces = faceDao.getAllFaces()
                Log.d(TAG, "📊 Estado final: ${finalFuncionarios.size} funcionários, ${finalFaces.size} faces")
                
                if (finalFuncionarios.isNotEmpty() && finalFaces.isNotEmpty()) {
                    Log.d(TAG, "✅ === DADOS DE TESTE PRONTOS ===")
                } else {
                    Log.e(TAG, "❌ === FALHA NA CRIAÇÃO DOS DADOS DE TESTE ===")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro crítico ao criar funcionário/face de teste", e)
                e.printStackTrace()
            }
        }
    }
    
    /**
     * 🔍 DETECTAR DIMENSÕES DO MODELO AUTOMATICAMENTE
     */
    private fun detectModelDimensions(interpreter: Interpreter) {
        try {
            Log.d(TAG, "🔍 === DETECTANDO DIMENSÕES DO MODELO ===")
            
            val inputTensor = interpreter.getInputTensor(0)
            val outputTensor = interpreter.getOutputTensor(0)
            
            val inputShape = inputTensor.shape()
            val outputShape = outputTensor.shape()
            
            Log.d(TAG, "📊 Input shape: ${inputShape.contentToString()}")
            Log.d(TAG, "📊 Output shape: ${outputShape.contentToString()}")
            
            // Extrair dimensões de entrada
            if (inputShape.size >= 4) {
                modelInputHeight = inputShape[1]
                modelInputWidth = inputShape[2]
                val channels = inputShape[3]
                Log.d(TAG, "📐 Entrada detectada: ${modelInputWidth}x${modelInputHeight}x${channels}")
            } else if (inputShape.size >= 3) {
                modelInputHeight = inputShape[1]
                modelInputWidth = inputShape[2]
                Log.d(TAG, "📐 Entrada detectada: ${modelInputWidth}x${modelInputHeight}")
            }
            
            // Extrair dimensão de saída
            if (outputShape.size >= 2) {
                modelOutputSize = outputShape[1]
                Log.d(TAG, "📐 Saída detectada: ${modelOutputSize} dimensões")
            }
            
            Log.d(TAG, "✅ Dimensões detectadas com sucesso!")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao detectar dimensões: ${e.message}")
            // Manter valores padrão em caso de erro
            Log.d(TAG, "🔄 Usando dimensões padrão: 160x160 → 192")
        }
    }
    
    /**
     * 🤖 GERAR EMBEDDING DE TESTE USANDO TENSORFLOW
     */
    private fun generateTestEmbedding(): FloatArray? {
        return try {
            if (!modelLoaded || interpreter == null) {
                Log.w(TAG, "⚠️ TensorFlow não está carregado para gerar embedding de teste")
                return null
            }
            
            Log.d(TAG, "🤖 Gerando embedding de teste com TensorFlow...")
            
            // ✅ CRIAR IMAGEM DE TESTE (PATTERN SIMPLES)
            val testBitmap = createTestFaceBitmap()
            val inputTensor = convertBitmapToTensorInput(testBitmap)
            val output = Array(1) { FloatArray(modelOutputSize) }
            
            // ✅ EXECUTAR MODELO
            interpreter?.run(inputTensor, output)
            val embedding = output[0]
            
            // ✅ VALIDAR EMBEDDING
            if (embedding.isEmpty() || embedding.all { it == 0f } || embedding.any { it.isNaN() || it.isInfinite() }) {
                Log.w(TAG, "⚠️ Embedding gerado é inválido")
                return null
            }
            
            Log.d(TAG, "✅ Embedding de teste gerado: ${embedding.size} dimensões")
            Log.d(TAG, "📊 Amostra: [${embedding.take(5).joinToString(", ") { String.format("%.3f", it) }}...]")
            
            return embedding
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao gerar embedding de teste: ${e.message}")
            null
        }
    }
    
    /**
     * 🖼️ CRIAR BITMAP DE TESTE PARA FACE
     */
    private fun createTestFaceBitmap(): Bitmap {
        val size = 160
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        val canvas = Canvas(bitmap)
        
        // ✅ CRIAR PATTERN SIMPLES DE FACE
        val paint = Paint().apply {
            isAntiAlias = true
        }
        
        // Fundo claro
        paint.color = android.graphics.Color.rgb(220, 220, 220)
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
        
        // "Rosto" (círculo)
        paint.color = android.graphics.Color.rgb(200, 180, 160)
        canvas.drawCircle(size / 2f, size / 2f, size / 3f, paint)
        
        // "Olhos"
        paint.color = android.graphics.Color.rgb(50, 50, 50)
        canvas.drawCircle(size / 2f - 20, size / 2f - 10, 8f, paint)
        canvas.drawCircle(size / 2f + 20, size / 2f - 10, 8f, paint)
        
        // "Boca"
        paint.color = android.graphics.Color.rgb(100, 50, 50)
        canvas.drawCircle(size / 2f, size / 2f + 20, 15f, paint)
        
        return bitmap
    }
    
    /**
     * 🎲 CRIAR FACE DE TESTE COM EMBEDDING ALEATÓRIO (FALLBACK)
     */
    private suspend fun createRandomTestFace(funcionario: FuncionariosEntity, faceDao: com.example.iface_offilne.data.FaceDao) {
        try {
            Log.d(TAG, "🎲 Criando face com embedding aleatório...")
            
            // ✅ GERAR EMBEDDING ALEATÓRIO MAS REALISTA
            val random = java.util.Random()
            val testEmbedding = FloatArray(192) { 
                (random.nextGaussian() * 0.1).toFloat() // Distribuição gaussiana centrada em 0
            }
            
            val embeddingString = testEmbedding.joinToString(",")
            
            val faceTeste = com.example.iface_offilne.data.FaceEntity(
                id = 0,
                funcionarioId = funcionario.codigo ?: "TEST001",
                embedding = embeddingString,
                synced = true
            )
            
            faceDao.insert(faceTeste)
            Log.d(TAG, "✅ Face aleatória criada: ${testEmbedding.size} dimensões")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao criar face aleatória: ${e.message}")
        }
    }
    
    /**
     * ✅ NOVO: Verificar se há faces cadastradas e criar face de teste se necessário
     */
    private fun checkAndCreateTestFace() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "🔍 Verificando faces cadastradas...")
                
                val db = AppDatabase.getInstance(this@PontoActivity)
                val faceDao = db.faceDao()
                val funcionarioDao = db.usuariosDao()
                
                val faces = faceDao.getAllFaces()
                Log.d(TAG, "📸 Faces encontradas: ${faces.size}")
                
                if (faces.isEmpty()) {
                    Log.d(TAG, "📝 Nenhuma face cadastrada - criando face de teste...")
                    
                    // Buscar funcionário de teste
                    val funcionarios = funcionarioDao.getUsuario()
                    if (funcionarios.isNotEmpty()) {
                        val funcionarioTeste = funcionarios.first()
                        
                        // ✅ CRIAR FACE DE TESTE: Gerar embedding de teste
                        val testEmbedding = FloatArray(192) { 0.1f } // Embedding de teste simples
                        val embeddingString = testEmbedding.joinToString(",")
                        
                        val faceTeste = com.example.iface_offilne.data.FaceEntity(
                            id = 0,
                            funcionarioId = funcionarioTeste.codigo,
                            embedding = embeddingString,
                            synced = true
                        )
                        
                        faceDao.insert(faceTeste)
                        Log.d(TAG, "✅ Face de teste criada para: ${funcionarioTeste.nome}")
                        
                        // Verificar se foi salva
                        val savedFace = faceDao.getByFuncionarioId(funcionarioTeste.codigo)
                        if (savedFace != null) {
                            Log.d(TAG, "✅ Face de teste salva com sucesso - ID: ${savedFace.id}")
                        } else {
                            Log.e(TAG, "❌ Erro: Face de teste não foi salva")
                        }
                    } else {
                        Log.e(TAG, "❌ Nenhum funcionário encontrado para criar face de teste")
                    }
                } else {
                    Log.d(TAG, "✅ Faces já cadastradas:")
                    faces.forEach { face ->
                        Log.d(TAG, "   - ${face.funcionarioId}: ${face.embedding.length} chars")
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao verificar/criar faces: ${e.message}")
                e.printStackTrace()
            }
        }
    }
    
    /**
     * ✅ NOVO: Executar testes de debug para diagnosticar problemas
     */
    private fun executarTestesDebug() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "🔧 Executando testes de debug...")
                
                val debugHelper = FaceRecognitionDebugHelper(this@PontoActivity)
                val relatorio = debugHelper.executarTodosTestes()
                
                Log.d(TAG, "📊 RELATÓRIO DE DEBUG:")
                Log.d(TAG, relatorio)
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro nos testes de debug: ${e.message}")
                e.printStackTrace()
            }
        }
    }
    
    /**
     * ✅ NOVO: Testar se o FaceRecognitionHelper está funcionando
     */
    private fun testFaceRecognitionHelper() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "🧪 Testando FaceRecognitionHelper...")
                
                if (faceRecognitionHelper != null) {
                    Log.d(TAG, "✅ FaceRecognitionHelper está inicializado")
                    
                    // Testar se consegue acessar o banco de dados
                    val db = AppDatabase.getInstance(this@PontoActivity)
                    val funcionarioDao = db.usuariosDao()
                    val funcionarios = funcionarioDao.getUsuario()
                    
                    Log.d(TAG, "📊 Funcionários no banco: ${funcionarios.size}")
                    funcionarios.forEach { funcionario ->
                        Log.d(TAG, "👤 Funcionário: ${funcionario.nome} (${funcionario.codigo})")
                    }
                    
                    // ✅ NOVO: Testar se consegue acessar as faces
                    try {
                        val faceDao = db.faceDao()
                        val faces = faceDao.getAllFaces()
                        Log.d(TAG, "📸 Faces cadastradas: ${faces.size}")
                        
                        faces.forEach { face ->
                            Log.d(TAG, "🖼️ Face: ${face.funcionarioId} - embedding: ${face.embedding.length} chars")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Erro ao acessar faces: ${e.message}")
                        e.printStackTrace()
                    }
                    
                } else {
                    Log.e(TAG, "❌ FaceRecognitionHelper é nulo")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao testar FaceRecognitionHelper", e)
                e.printStackTrace()
            }
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }
    
    private fun allLocationPermissionsGranted() = LOCATION_PERMISSIONS.any {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_CODE_PERMISSIONS -> {
                if (allPermissionsGranted()) {
                    if (!allLocationPermissionsGranted()) {
                        ActivityCompat.requestPermissions(this, LOCATION_PERMISSIONS, REQUEST_CODE_LOCATION_PERMISSIONS)
                    }
                    startCamera()
                } else {
                    Toast.makeText(this, "❌ Permissões necessárias foram negadas!", Toast.LENGTH_LONG).show()
                    finish()
                }
            }
            REQUEST_CODE_LOCATION_PERMISSIONS -> {
                if (allLocationPermissionsGranted()) {
                    Log.d(TAG, "✅ Permissões de localização concedidas")
                } else {
                    Log.w(TAG, "⚠️ Permissões de localização negadas")
                }
            }
        }
    }

    override fun onBackPressed() {
        AlertDialog.Builder(this)
            .setTitle("Sair da Tela de Ponto")
            .setMessage("Tem certeza que deseja sair da tela de registro de ponto?")
            .setPositiveButton("Sim, Sair") { dialog, _ ->
                dialog.dismiss()
                try {
                    stopCamera()
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Erro ao parar câmera: ${e.message}")
                }
                Handler(Looper.getMainLooper()).postDelayed({
                    try {
                        super.onBackPressed()
                    } catch (e: Exception) {
                        finish()
                    }
                }, 200)
            }
            .setNegativeButton("Cancelar") { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(true)
            .show()
    }

    private fun stopCamera() {
        Log.d(TAG, "🛑 Parando câmera")
        try {
            // ✅ PROTEÇÃO: Limpar ImageAnalyzer
            try {
                if (::imageAnalyzer.isInitialized) {
                    val analyzer = imageAnalyzer
                    analyzer.clearAnalyzer()
                    Log.d(TAG, "✅ ImageAnalyzer limpo")
                }
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Erro ao limpar ImageAnalyzer: ${e.message}")
            }
            
            // ✅ PROTEÇÃO: Desvincular câmeras
            try {
                val provider = cameraProvider
                provider?.unbindAll()
                Log.d(TAG, "✅ Câmeras desvinculadas")
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Erro ao desvincular câmeras: ${e.message}")
            }
            
            // ✅ PROTEÇÃO: Limpar cameraProvider
            try {
                cameraProvider = null
                Log.d(TAG, "✅ CameraProvider limpo")
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Erro ao limpar CameraProvider: ${e.message}")
            }
            
            // ✅ PROTEÇÃO: Limpar overlay
            try {
                if (::overlay.isInitialized) {
                    val overlayView = overlay
                    overlayView.clear()
                    Log.d(TAG, "✅ Overlay limpo")
                }
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Erro ao limpar overlay: ${e.message}")
            }
            
            // ✅ PROTEÇÃO: Resetar flags
            try {
                processandoFace = false
                lastProcessingTime = 0L
                modelLoaded = false
                funcionarioReconhecido = null
                
                // ✅ PROTEÇÃO: Limpar recursos do fallback
                tensorFlowFallbackMode = false
                tensorFlowErrorCount = 0
                lastTensorFlowError = 0L
                
                Log.d(TAG, "✅ Flags resetados")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao resetar flags: ${e.message}")
            }
            
            Log.d(TAG, "✅ Câmera parada com sucesso")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro crítico ao parar câmera", e)
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            Log.d(TAG, "🧹 Iniciando limpeza de recursos...")
            
            // ✅ PROTEÇÃO: Parar câmera com segurança
            try {
                stopCamera()
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao parar câmera: ${e.message}")
            }
            
            // ✅ PROTEÇÃO: Limpar bitmap com validação
            try {
                val bitmap = currentFaceBitmap
                bitmap?.let { bmp ->
                    if (!bmp.isRecycled) {
                        bmp.recycle()
                        Log.d(TAG, "✅ Bitmap reciclado")
                    }
                }
                currentFaceBitmap = null
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao reciclar bitmap: ${e.message}")
            }
            
            // ✅ PROTEÇÃO: Fechar interpreter com validação
            try {
                interpreter?.let { interp ->
                    interp.close()
                    Log.d(TAG, "✅ Interpreter fechado")
                }
                interpreter = null
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao fechar interpreter: ${e.message}")
            }
            
            // ✅ PROTEÇÃO: Fechar face detector com validação
            try {
                val detector = faceDetector
                detector?.close()
                Log.d(TAG, "✅ Face detector fechado")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao fechar face detector: ${e.message}")
            }
            
            // ✅ PROTEÇÃO: Limpar cache do face recognition helper
            try {
                val helper = faceRecognitionHelper
                helper?.clearCache()
                faceRecognitionHelper = null
                Log.d(TAG, "✅ Face recognition helper limpo")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao limpar face recognition helper: ${e.message}")
            }
            
            // ✅ PROTEÇÃO: Limpar helpers adaptativos
            try {
                adaptiveFaceRecognitionHelper = null
                deviceCapabilityHelper = null
                advancedFaceRecognitionHelper = null
                Log.d(TAG, "✅ Helpers adaptativos limpos")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao limpar helpers adaptativos: ${e.message}")
            }
            
            // ✅ PROTEÇÃO: Limpar location helper
            try {
                locationHelper = null
                Log.d(TAG, "✅ Location helper limpo")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao limpar location helper: ${e.message}")
            }
            
            // ✅ PROTEÇÃO: Resetar flags
            try {
                processandoFace = false
                lastProcessingTime = 0L
                modelLoaded = false
                funcionarioReconhecido = null
                
                // ✅ PROTEÇÃO: Limpar recursos do fallback
                tensorFlowFallbackMode = false
                tensorFlowErrorCount = 0
                lastTensorFlowError = 0L
                
                Log.d(TAG, "✅ Flags resetados")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao resetar flags: ${e.message}")
            }
            
            Log.d(TAG, "✅ Limpeza de recursos concluída")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro crítico na limpeza de recursos: ${e.message}")
        }
    }
    
    override fun onPause() {
        super.onPause()
        try {
            stopCamera()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro no onPause: ${e.message}")
        }
    }
    
    override fun onResume() {
        super.onResume()
        try {
            Log.d(TAG, "🔄 === RESUMINDO PONTOACTIVITY ===")
            
            // ✅ REINICIALIZAR MODELO TENSORFLOW SE NECESSÁRIO
            if (!modelLoaded || interpreter == null) {
                Log.d(TAG, "🤖 Modelo TensorFlow não carregado - recarregando...")
                loadTensorFlowModel()
            } else {
                Log.d(TAG, "✅ Modelo TensorFlow já carregado - verificando saúde...")
                reinitializeTensorFlowIfNeeded()
            }
            
            // ✅ REINICIALIZAR CAMERA
            if (allPermissionsGranted()) {
                Log.d(TAG, "📷 Reiniciando câmera...")
                startCamera()
            } else {
                Log.w(TAG, "⚠️ Permissões não concedidas - não iniciando câmera")
            }
            
            // ✅ RESETAR FLAGS DE PROCESSAMENTO
            processandoFace = false
            lastProcessingTime = 0L
            funcionarioReconhecido = null
            
            Log.d(TAG, "✅ PontoActivity resumida com sucesso")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro no onResume: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * ✅ RESET: Resetar modo fallback após timeout
     */
    private fun resetFallbackMode() {
        val currentTime = System.currentTimeMillis()
        val fallbackTimeout = 5 * 60 * 1000L // 5 minutos
        
        if (tensorFlowFallbackMode && (currentTime - lastTensorFlowError) > fallbackTimeout) {
            Log.d(TAG, "🔄 Resetando modo fallback após timeout")
            tensorFlowFallbackMode = false
            tensorFlowErrorCount = 0
            lastTensorFlowError = 0L
            
            // Tentar recarregar o modelo TensorFlow
            loadTensorFlowModel()
        }
    }
    
    /**
     * ✅ COOLDOWN: Resetar cooldown do ponto (para testes ou emergências)
     */
    private fun resetPontoCooldown() {
        Log.d(TAG, "🔄 Resetando cooldown do ponto")
        lastPontoRegistrado = 0L
    }
    
    /**
     * ✅ COOLDOWN: Verificar se o cooldown está ativo
     */
    private fun isCooldownActive(): Boolean {
        val tempoAtual = System.currentTimeMillis()
        val tempoDesdeUltimoPonto = tempoAtual - lastPontoRegistrado
        return tempoDesdeUltimoPonto < cooldownPonto
    }
    
    /**
     * ✅ COOLDOWN: Obter tempo restante do cooldown em segundos
     */
    private fun getCooldownRemainingSeconds(): Int {
        val tempoAtual = System.currentTimeMillis()
        val tempoDesdeUltimoPonto = tempoAtual - lastPontoRegistrado
        val tempoRestante = cooldownPonto - tempoDesdeUltimoPonto
        return if (tempoRestante > 0) (tempoRestante / 1000).toInt() else 0
    }

    /**
     * ✅ TESTE: Verificar se o TensorFlow está funcionando corretamente
     */
    private fun testTensorFlowHealth(): Boolean {
        return try {
            val interp = interpreter
            if (interp == null || !modelLoaded) {
                Log.w(TAG, "⚠️ TensorFlow não está carregado")
                return false
            }

            // ✅ TESTE: Criar dados de teste
            val testInput = ByteBuffer.allocateDirect(4 * modelInputWidth * modelInputHeight * 3)
            testInput.order(ByteOrder.nativeOrder())
            
            // Preencher com dados de teste
            for (i in 0 until modelInputWidth * modelInputHeight * 3) {
                testInput.putFloat(0.1f)
            }
            testInput.rewind()
            
            val testOutput = Array(1) { FloatArray(modelOutputSize) }
            
            // ✅ TESTE: Executar modelo com dados de teste
            try {
                interp.run(testInput, testOutput)
                
                // ✅ VERIFICAÇÃO: Verificar se o output é válido
                val output = testOutput[0]
                if (output.isEmpty() || output.any { it.isNaN() || it.isInfinite() }) {
                    Log.w(TAG, "⚠️ TensorFlow retornou output inválido")
                    return false
                }
                
                Log.d(TAG, "✅ TensorFlow está funcionando corretamente")
                return true
                
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "❌ TensorFlow com erro de biblioteca nativa: ${e.message}")
                return false
            } catch (e: Exception) {
                Log.e(TAG, "❌ TensorFlow com erro: ${e.message}")
                return false
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao testar TensorFlow: ${e.message}")
            return false
        }
    }
    
    /**
     * 🔄 REINICIALIZAR MODELO TENSORFLOW SE NECESSÁRIO
     */
    private fun reinitializeTensorFlowIfNeeded() {
        try {
            Log.d(TAG, "🔄 Verificando necessidade de reinicialização do TensorFlow...")
            
            // ✅ VERIFICAR SE O MODELO ESTÁ FUNCIONANDO
            if (!testTensorFlowHealth()) {
                Log.w(TAG, "⚠️ TensorFlow não está funcionando - reinicializando...")
                
                // ✅ LIMPAR RECURSOS ATUAIS
                try {
                    interpreter?.close()
                    interpreter = null
                    modelLoaded = false
                    Log.d(TAG, "🧹 Recursos do TensorFlow limpos")
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ Erro ao limpar recursos: ${e.message}")
                }
                
                // ✅ RECARREGAR MODELO
                loadTensorFlowModel()
                
                // ✅ AGUARDAR UM POUCO E TESTAR NOVAMENTE
                Handler(Looper.getMainLooper()).postDelayed({
                    try {
                        if (testTensorFlowHealth()) {
                            Log.d(TAG, "✅ TensorFlow reinicializado com sucesso")
                        } else {
                            Log.e(TAG, "❌ Falha na reinicialização do TensorFlow")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Erro ao testar reinicialização: ${e.message}")
                    }
                }, 2000)
                
            } else {
                Log.d(TAG, "✅ TensorFlow está funcionando corretamente")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao verificar TensorFlow: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * 🧹 FORÇAR LIMPEZA COMPLETA DOS RECURSOS
     */
    private fun forceCleanup() {
        try {
            Log.d(TAG, "🧹 === FORÇANDO LIMPEZA COMPLETA ===")
            
            // ✅ PARAR CÂMERA
            try {
                stopCamera()
                Log.d(TAG, "✅ Câmera parada")
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Erro ao parar câmera: ${e.message}")
            }
            
            // ✅ LIMPAR TENSORFLOW
            try {
                interpreter?.close()
                interpreter = null
                modelLoaded = false
                Log.d(TAG, "✅ TensorFlow limpo")
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Erro ao limpar TensorFlow: ${e.message}")
            }
            
            // ✅ LIMPAR FACE DETECTOR
            try {
                faceDetector?.close()
                faceDetector = null
                Log.d(TAG, "✅ Face detector limpo")
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Erro ao limpar face detector: ${e.message}")
            }
            
            // ✅ RESETAR FLAGS
            processandoFace = false
            lastProcessingTime = 0L
            funcionarioReconhecido = null
            lastPontoRegistrado = 0L
            tensorFlowFallbackMode = false
            tensorFlowErrorCount = 0
            lastTensorFlowError = 0L
            
            // ✅ LIMPAR BITMAP
            try {
                currentFaceBitmap?.recycle()
                currentFaceBitmap = null
                Log.d(TAG, "✅ Bitmap limpo")
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Erro ao limpar bitmap: ${e.message}")
            }
            
            // ✅ LIMPAR OVERLAY
            try {
                if (::overlay.isInitialized) {
                    overlay.clear()
                    Log.d(TAG, "✅ Overlay limpo")
                }
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Erro ao limpar overlay: ${e.message}")
            }
            
            Log.d(TAG, "✅ Limpeza completa finalizada")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro na limpeza forçada: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * 🎉 NAVEGAR PARA ACTIVITY DE SUCESSO
     */
    private fun showConfirmationUI(funcionario: FuncionariosEntity, fotoBase64: String?) {
        try {
            Log.d(TAG, "🎉 Navegando para Activity de sucesso para: ${funcionario.nome}")
            
            // ✅ OBTER DADOS ATUAIS
            val horarioAtual = System.currentTimeMillis()
            
            // ✅ USAR COROUTINE PARA OBTER LOCALIZAÇÃO
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    var latitude: Double? = null
                    var longitude: Double? = null
                    
                    try {
                        val helper = locationHelper
                        val locationData = helper?.getCurrentLocationForPoint()
                        if (locationData != null) {
                            latitude = locationData.latitude
                            longitude = locationData.longitude
                            Log.d(TAG, "📍 Localização para sucesso: $latitude, $longitude")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Erro ao obter localização para sucesso: ${e.message}")
                    }
                    
                    // ✅ NAVEGAR PARA ACTIVITY DE SUCESSO NA THREAD PRINCIPAL
                    withContext(Dispatchers.Main) {
                        try {
                            if (!isFinishing && !isDestroyed) {
                                // ✅ CRIAR INTENT PARA ACTIVITY DE SUCESSO
                                val intent = Intent(this@PontoActivity, PontoSucessoActivity::class.java).apply {
                                    putExtra(PontoSucessoActivity.EXTRA_FUNCIONARIO_NOME, funcionario.nome)
                                    putExtra(PontoSucessoActivity.EXTRA_DATA_HORA, horarioAtual)
                                    
                                    if (latitude != null && longitude != null) {
                                        putExtra(PontoSucessoActivity.EXTRA_LATITUDE, latitude)
                                        putExtra(PontoSucessoActivity.EXTRA_LONGITUDE, longitude)
                                    }
                                }
                                
                                // ✅ NAVEGAR PARA ACTIVITY DE SUCESSO
                                startActivity(intent)
                                
                                Log.d(TAG, "✅ Navegação para Activity de sucesso iniciada")
                            } else {
                                Log.w(TAG, "⚠️ Activity finalizada - cancelando navegação")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ Erro ao navegar para Activity de sucesso: ${e.message}")
                            e.printStackTrace()
                            
                            // ✅ FALLBACK: Mostrar mensagem simples
                            if (::statusText.isInitialized) {
                                val status = statusText
                                status.text = "✅ Ponto registrado!\n${funcionario.nome}"
                                
                                status.postDelayed({
                                    try {
                                        if (!isFinishing && !isDestroyed) {
                                            val statusInner = statusText
                                            statusInner.text = ""
                                            processandoFace = false
                                            lastProcessingTime = 0L
                                        }
                                    } catch (e2: Exception) {
                                        Log.e(TAG, "❌ Erro no reset: ${e2.message}")
                                    }
                                }, 3000)
                            }
                        }
                    }
                    
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Erro crítico na coroutine de sucesso: ${e.message}")
                    e.printStackTrace()
                    
                    // ✅ FALLBACK: Mostrar mensagem simples na thread principal
                    withContext(Dispatchers.Main) {
                        try {
                            if (!isFinishing && !isDestroyed && ::statusText.isInitialized) {
                                val status = statusText
                                status.text = "✅ Ponto registrado!\n${funcionario.nome}"
                                
                                status.postDelayed({
                                    try {
                                        if (!isFinishing && !isDestroyed) {
                                            val statusInner = statusText
                                            statusInner.text = ""
                                            processandoFace = false
                                            lastProcessingTime = 0L
                                        }
                                    } catch (e2: Exception) {
                                        Log.e(TAG, "❌ Erro no reset: ${e2.message}")
                                    }
                                }, 3000)
                            }
                        } catch (e2: Exception) {
                            Log.e(TAG, "❌ Erro no fallback: ${e2.message}")
                        }
                    }
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro crítico ao iniciar navegação: ${e.message}")
            e.printStackTrace()
            
            // ✅ FALLBACK FINAL: Mostrar mensagem simples
            if (::statusText.isInitialized) {
                val status = statusText
                status.text = "✅ Ponto registrado!\n${funcionario.nome}"
                
                status.postDelayed({
                    try {
                        if (!isFinishing && !isDestroyed) {
                            val statusInner = statusText
                            statusInner.text = ""
                            processandoFace = false
                            lastProcessingTime = 0L
                        }
                    } catch (e2: Exception) {
                        Log.e(TAG, "❌ Erro no reset: ${e2.message}")
                    }
                }, 3000)
            }
        }
    }
} 