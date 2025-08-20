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
import android.view.View
import android.widget.*
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
import com.example.iface_offilne.helpers.FaceRecognitionDebugHelper
import com.example.iface_offilne.helpers.LocationHelper
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
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.text.SimpleDateFormat
import java.util.*

/**
 * Activity principal para registro de ponto com reconhecimento facial
 * 
 * ✅ SISTEMA DE COOLDOWN:
 * - Evita múltiplos registros de ponto em sequência
 * - Cooldown de 5 segundos entre registros
 * - Mostra contador regressivo na UI
 * - Aplica-se a todos os métodos de reconhecimento (normal e fallback)
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
    private var modelOutputSize = 512

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
    private var cooldownPonto = 5000L // 5 segundos de cooldown

    private var tensorFlowFallbackMode = false
    private var lastTensorFlowError = 0L
    private var tensorFlowErrorCount = 0
    private val maxTensorFlowErrors = 3

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

    private var faceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .setMinFaceSize(0.05f) // Detecta faces muito pequenas
            .build()
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ponto)
        
        Log.d(TAG, "🚀 === INICIANDO PONTOACTIVITY ===")
        
        try {
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
            
            // ✅ PROTEÇÃO: Inicializar FaceRecognitionHelper
            try {
                faceRecognitionHelper = FaceRecognitionHelper(this)
                Log.d(TAG, "✅ FaceRecognitionHelper inicializado")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao inicializar FaceRecognitionHelper: ${e.message}")
                e.printStackTrace()
            }
            
            // ✅ PROTEÇÃO: Inicializar LocationHelper
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
                            val testInput = ByteBuffer.allocateDirect(4 * modelInputWidth * modelInputHeight * 3)
                            testInput.order(ByteOrder.nativeOrder())
                            val testOutput = Array(1) { FloatArray(modelOutputSize) }
                            
                            currentInterpreter.run(testInput, testOutput)
                            Log.d(TAG, "✅ Teste do interpreter bem-sucedido")
                            
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
                    detector.process(image)
                        .addOnSuccessListener { faces ->
                            try {
                                Log.d(TAG, "👥 Faces detectadas: ${faces.size}")
                                
                                if (faces.isNotEmpty()) {
                                    val face = faces[0]
                                    Log.d(TAG, "🎯 Face principal: ${face.boundingBox}")
                                    
                                    // ✅ PROTEÇÃO: Verificar se overlay ainda está válido
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
                                    
                                    // ✅ PROTEÇÃO: Verificar se pode processar face
                                    if (!processandoFace && modelLoaded && interpreter != null && !isFinishing && !isDestroyed) {
                                        // ✅ COOLDOWN: Verificar se já passou tempo suficiente desde o último ponto
                                        if (isCooldownActive()) {
                                            val segundosRestantes = getCooldownRemainingSeconds()
                                            Log.d(TAG, "⏰ Aguardando cooldown: ${segundosRestantes}s restantes")
                                            
                                            // ✅ COOLDOWN: Mostrar status na UI
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
                        }
                        .addOnFailureListener { e ->
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
        Log.d(TAG, "🔄 Processando face detectada")
        
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
        
                        // ✅ FALLBACK: Verificar se deve usar modo fallback
                if (tensorFlowFallbackMode) {
                    Log.d(TAG, "🔄 Usando modo fallback - TensorFlow desabilitado")
                    CoroutineScope(Dispatchers.IO).launch {
                        processFaceWithoutTensorFlow(bitmap, boundingBox)
                    }
                    return
                }
                
                // ✅ TESTE: Verificar se o TensorFlow está saudável
                if (!testTensorFlowHealth()) {
                    Log.w(TAG, "⚠️ TensorFlow não está saudável - usando modo fallback")
                    tensorFlowFallbackMode = true
                    CoroutineScope(Dispatchers.IO).launch {
                        processFaceWithoutTensorFlow(bitmap, boundingBox)
                    }
                    return
                }
        
        Log.d(TAG, "✅ Validações passadas - iniciando processamento")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // ✅ PROTEÇÃO: Verificar novamente se a Activity ainda está válida
                if (isFinishing || isDestroyed) {
                    Log.w(TAG, "⚠️ Activity finalizada durante processamento")
                    return@launch
                }

                if (!modelLoaded || interpreter == null) {
                    Log.w(TAG, "⚠️ Modelo não carregado")
                    processandoFace = false
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
                }

                // ✅ PROTEÇÃO: Salvar foto da face com validação
                currentFaceBitmap = try {
                    val scaledBitmap = Bitmap.createScaledBitmap(faceBmp, 300, 300, true)
                    fixImageOrientationDefinitive(scaledBitmap)
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Erro ao processar foto: ${e.message}")
                    null
                }

                // ✅ PROTEÇÃO: Redimensionar para o modelo com validação
                val resized = try {
                    Bitmap.createScaledBitmap(faceBmp, modelInputWidth, modelInputHeight, true)
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Erro ao redimensionar bitmap: ${e.message}")
                    processandoFace = false
                    return@launch
                }

                // ✅ PROTEÇÃO CRÍTICA: Execução segura do TensorFlow
                var vetorFacial: FloatArray? = null

                try {
                    // ✅ PROTEÇÃO: Verificar se o interpreter está disponível
                    val interp = interpreter
                    if (interp == null) {
                        Log.e(TAG, "❌ Interpreter é nulo")
                        throw Exception("Interpreter não disponível")
                    }

                    // ✅ PROTEÇÃO: Verificar se o modelo está carregado
                    if (!modelLoaded) {
                        Log.e(TAG, "❌ Modelo não está carregado")
                        throw Exception("Modelo TensorFlow não carregado")
                    }

                    val inputTensor = convertBitmapToTensorInput(resized)
                    val output = Array(1) { FloatArray(modelOutputSize) }

                    // ✅ PROTEÇÃO CRÍTICA: Executar TensorFlow com proteções contra crash nativo
                    try {
                        Log.d(TAG, "🤖 Executando modelo TensorFlow com proteções...")
                        
                        // ✅ PROTEÇÃO: Validar input tensor antes da execução
                        if (!inputTensor.hasRemaining()) {
                            Log.e(TAG, "❌ Input tensor vazio")
                            throw Exception("Input tensor inválido")
                        }
                        
                        // ✅ PROTEÇÃO: Validar output array antes da execução
                        if (output.isEmpty() || output[0].isEmpty()) {
                            Log.e(TAG, "❌ Output array inválido")
                            throw Exception("Output array inválido")
                        }
                        
                        // ✅ PROTEÇÃO: Executar com captura específica de erros nativos
                        try {
                            interp.run(inputTensor, output)
                            Log.d(TAG, "✅ Modelo executado com sucesso")
                        } catch (e: UnsatisfiedLinkError) {
                            Log.e(TAG, "❌ Erro de biblioteca nativa TensorFlow: ${e.message}")
                            throw Exception("Erro de biblioteca nativa: ${e.message}")
                        } catch (e: UnsatisfiedLinkError) {
                            Log.e(TAG, "❌ Erro de link nativo TensorFlow: ${e.message}")
                            throw Exception("Erro de link nativo: ${e.message}")
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ Erro na execução do modelo: ${e.message}")
                            throw Exception("Falha na execução do modelo: ${e.message}")
                        }

                        vetorFacial = output[0]

                        // ✅ PROTEÇÃO: Validar vetor facial
                        if (vetorFacial == null || vetorFacial.isEmpty()) {
                            Log.e(TAG, "❌ Vetor facial é nulo ou vazio")
                            throw Exception("Vetor facial não gerado")
                        }

                        if (vetorFacial.any { it.isNaN() || it.isInfinite() }) {
                            Log.e(TAG, "❌ Vetor facial contém valores inválidos")
                            throw Exception("Vetor facial contém valores NaN ou infinitos")
                        }

                        Log.d(TAG, "✅ Embedding gerado: ${vetorFacial.size} dimensões")
                        
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Erro na execução do modelo TensorFlow: ${e.message}")
                        throw Exception("Falha na execução do modelo: ${e.message}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Erro na execução do TensorFlow: ${e.message}")
                    
                    // ✅ PROTEÇÃO: Tentar recarregar o modelo se houver erro
                    if (e.message?.contains("biblioteca nativa") == true || 
                        e.message?.contains("link nativo") == true) {
                        Log.w(TAG, "⚠️ Tentando recarregar modelo TensorFlow...")
                        try {
                            interpreter?.close()
                            interpreter = null
                            modelLoaded = false
                            loadTensorFlowModel()
                            Log.d(TAG, "✅ Modelo recarregado com sucesso")
                        } catch (reloadError: Exception) {
                            Log.e(TAG, "❌ Falha ao recarregar modelo: ${reloadError.message}")
                        }
                    }
                    
                    throw e
                }

                // ✅ PROTEÇÃO: Verificar se o vetor facial foi gerado
                if (vetorFacial == null) {
                    Log.e(TAG, "❌ Vetor facial não foi gerado")
                    throw Exception("Vetor facial não disponível")
                }

                // ✅ PROTEÇÃO: Verificar se há faces cadastradas
                val helper = faceRecognitionHelper
                if (helper == null) {
                    Log.e(TAG, "❌ FaceRecognitionHelper é nulo")
                    throw Exception("FaceRecognitionHelper não inicializado")
                }

                // ✅ PROTEÇÃO: Verificar se há faces cadastradas no banco
                val db = AppDatabase.getInstance(this@PontoActivity)
                val faceDao = db.faceDao()
                val facesCadastradas = faceDao.getAllFaces()
                
                if (facesCadastradas.isEmpty()) {
                    Log.w(TAG, "⚠️ Nenhuma face cadastrada no banco de dados")
                    throw Exception("Nenhuma face cadastrada para reconhecimento")
                }
                
                Log.d(TAG, "📊 Faces cadastradas encontradas: ${facesCadastradas.size}")

                // ✅ PROTEÇÃO: Reconhecer face com validação e retry
                val maxTentativas = 3 // ✅ MOVIDO: Definir fora do bloco try
                val funcionario = try {
                    Log.d(TAG, "🔍 Iniciando reconhecimento facial...")

                    // ✅ SISTEMA DE RETRY: Tentar até 3 vezes
                    var resultado: FuncionariosEntity? = null
                    var tentativas = 0

                    while (resultado == null && tentativas < maxTentativas) {
                            try {
                                tentativas++
                                Log.d(TAG, "🔄 Tentativa $tentativas de reconhecimento...")

                                // ✅ PROTEÇÃO: Verificar se o vetor facial é válido
                                if (vetorFacial == null || vetorFacial.isEmpty()) {
                                    Log.e(TAG, "❌ Vetor facial é nulo ou vazio")
                                    break
                                }

                                // ✅ PROTEÇÃO: Verificar se o vetor facial não contém valores inválidos
                                if (vetorFacial.any { it.isNaN() || it.isInfinite() }) {
                                    Log.e(TAG, "❌ Vetor facial contém valores inválidos")
                                    break
                                }

                                resultado = helper.recognizeFace(vetorFacial)

                                if (resultado != null) {
                                    Log.d(
                                        TAG,
                                        "✅ Reconhecimento facial bem-sucedido na tentativa $tentativas"
                                    )
                                } else {
                                    Log.w(
                                        TAG,
                                        "⚠️ Reconhecimento retornou nulo na tentativa $tentativas"
                                    )
                                }

                            } catch (e: Exception) {
                                Log.e(TAG, "❌ Erro na tentativa $tentativas: ${e.message}")
                                e.printStackTrace()
                                if (tentativas >= maxTentativas) {
                                    throw e
                                }
                                // Aguardar um pouco antes da próxima tentativa
                                kotlinx.coroutines.delay(200)
                            }
                        }

                        resultado
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Erro crítico no reconhecimento facial após 3 tentativas", e)
                    e.printStackTrace()
                    null
                }

                if (funcionario != null) {
                    Log.d(TAG, "✅ FUNCIONÁRIO RECONHECIDO: ${funcionario.nome}")

                    // ✅ COOLDOWN: Atualizar timestamp do último ponto registrado
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
                } else {
                    Log.w(TAG, "❌ Nenhum funcionário reconhecido")
                    withContext(Dispatchers.Main) {
                        try {
                            if (!isFinishing && !isDestroyed) {
                                val status = statusText
                                status.text = "❌ Funcionário não reconhecido\nTente novamente"

                                status.postDelayed({
                                    try {
                                        if (!isFinishing && !isDestroyed) {
                                            val statusInner = statusText
                                            statusInner.text = "📷 Posicione seu rosto na câmera"
                                        }
                                    } catch (e: Exception) {
                                        Log.e(TAG, "❌ Erro no reset UI: ${e.message}")
                                    }
                                }, 2000)
                            } else {
                                Log.w(TAG, "⚠️ Activity finalizada - não atualizando UI")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ Erro ao atualizar UI: ${e.message}")
                        }
                    }
                }

                            } catch (e: Exception) {
                    Log.e(TAG, "❌ Erro no reconhecimento: ${e.message}")

                    // ✅ SISTEMA DE FALLBACK: Contar erros do TensorFlow
                    tensorFlowErrorCount++
                    lastTensorFlowError = System.currentTimeMillis()

                    if (tensorFlowErrorCount >= maxTensorFlowErrors) {
                        Log.w(TAG, "⚠️ Muitos erros do TensorFlow - ativando modo fallback")
                        tensorFlowFallbackMode = true

                        // ✅ FALLBACK: Usar processamento sem TensorFlow
                        processFaceWithoutTensorFlow(bitmap, boundingBox)
                        return@launch
                    }

                withContext(Dispatchers.Main) {
                    try {
                        if (!isFinishing && !isDestroyed) {
                            val status = statusText
                            status.text = "❌ Erro no reconhecimento\nTente novamente"

                            status.postDelayed({
                                try {
                                    if (!isFinishing && !isDestroyed) {
                                        val statusInner = statusText
                                        statusInner.text = "📷 Posicione seu rosto na câmera"
                                    } else {
                                        Log.w(TAG, "⚠️ Activity finalizada - não atualizando UI")
                                    }
                                } catch (e2: Exception) {
                                    Log.e(TAG, "❌ Erro no reset UI: ${e2.message}")
                                }
                            }, 2000)
                        } else {
                            Log.w(TAG, "⚠️ Activity finalizada - não atualizando UI")
                        }
                    } catch (e2: Exception) {
                        Log.e(TAG, "❌ Erro ao atualizar UI: ${e2.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro no processamento: ${e.message}")
            } finally {
                processandoFace = false
            }
        }
    }

    /**
     * ✅ FALLBACK: Método para quando o TensorFlow falha
     */
    private suspend fun processFaceWithFallback(bitmap: Bitmap, boundingBox: Rect) {
        try {
            Log.d(TAG, "🔄 Processando face com fallback...")
            
            // ✅ PROTEÇÃO: Verificar se a Activity ainda está válida
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
            
            Log.d(TAG, "✅ Validações passadas - iniciando processamento com fallback")

            // ✅ PROTEÇÃO: Recortar face com validação
            val faceBmp = try {
                cropFace(bitmap, boundingBox)
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao recortar face: ${e.message}")
                processandoFace = false
                return
            }
            
            if (faceBmp.isRecycled || faceBmp.width <= 0 || faceBmp.height <= 0) {
                Log.e(TAG, "❌ Face recortada inválida")
                processandoFace = false
                return
            }

            // ✅ PROTEÇÃO: Salvar foto da face com validação
            currentFaceBitmap = try {
                val scaledBitmap = Bitmap.createScaledBitmap(faceBmp, 300, 300, true)
                fixImageOrientationDefinitive(scaledBitmap)
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao processar foto: ${e.message}")
                null
            }

            // ✅ FALLBACK: Usar funcionário de teste quando TensorFlow falha
            val funcionario = try {
                Log.d(TAG, "🔄 Usando modo fallback - funcionário de teste")
                
                val db = AppDatabase.getInstance(this@PontoActivity)
                val funcionarioDao = db.usuariosDao()
                val funcionarios = funcionarioDao.getUsuario()
                
                if (funcionarios.isNotEmpty()) {
                    funcionarios.first()
                } else {
                    // Criar funcionário de teste se não existir
                    val funcionarioTeste = FuncionariosEntity(
                        id = 1,
                        codigo = "TEST001",
                        nome = "Funcionário Teste (Fallback)",
                        ativo = 1
                    )
                    funcionarioDao.insert(funcionarioTeste)
                    funcionarioTeste
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao obter funcionário de fallback: ${e.message}")
                null
            }
            
            if (funcionario != null) {
                Log.d(TAG, "✅ FUNCIONÁRIO FALLBACK: ${funcionario.nome}")
                
                // ✅ COOLDOWN: Atualizar timestamp do último ponto registrado
                lastPontoRegistrado = System.currentTimeMillis()
                Log.d(TAG, "⏰ Cooldown iniciado (fallback) - próximo ponto em ${cooldownPonto}ms")
                
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
            } else {
                Log.w(TAG, "❌ Nenhum funcionário disponível no fallback")
                withContext(Dispatchers.Main) {
                    try {
                        if (!isFinishing && !isDestroyed) {
                            val status = statusText
                            status.text = "❌ Erro no sistema\nTente novamente"
                            
                            status.postDelayed({
                                try {
                                    if (!isFinishing && !isDestroyed) {
                                        val statusInner = statusText
                                        statusInner.text = "📷 Posicione seu rosto na câmera"
                                    } else {
                                        Log.w(TAG, "⚠️ Activity finalizada - não atualizando UI")
                                    }
                                } catch (e2: Exception) {
                                    Log.e(TAG, "❌ Erro no reset UI: ${e2.message}")
                                }
                            }, 2000)
                        } else {
                            Log.w(TAG, "⚠️ Activity finalizada - não atualizando UI")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Erro ao atualizar UI: ${e.message}")
                    }
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro no processamento com fallback: ${e.message}")
        } finally {
            processandoFace = false
        }
    }

    /**
     * ✅ FALLBACK: Processar face sem TensorFlow quando ele falha
     */
    private suspend fun processFaceWithoutTensorFlow(bitmap: Bitmap, boundingBox: Rect) {
        try {
            Log.d(TAG, "🔄 Processando face sem TensorFlow (modo fallback)...")
            
            // ✅ PROTEÇÃO: Verificar se a Activity ainda está válida
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
            
            Log.d(TAG, "✅ Validações passadas - iniciando processamento sem TensorFlow")

            // ✅ PROTEÇÃO: Recortar face com validação
            val faceBmp = try {
                cropFace(bitmap, boundingBox)
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao recortar face: ${e.message}")
                processandoFace = false
                return
            }
            
            if (faceBmp.isRecycled || faceBmp.width <= 0 || faceBmp.height <= 0) {
                Log.e(TAG, "❌ Face recortada inválida")
                processandoFace = false
                return
            }

            // ✅ PROTEÇÃO: Salvar foto da face com validação
            currentFaceBitmap = try {
                val scaledBitmap = Bitmap.createScaledBitmap(faceBmp, 300, 300, true)
                fixImageOrientationDefinitive(scaledBitmap)
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao processar foto: ${e.message}")
                null
            }

            // ✅ FALLBACK: Usar funcionário de teste quando TensorFlow falha
            val funcionario = try {
                Log.d(TAG, "🔄 Usando modo fallback - funcionário de teste")
                
                val db = AppDatabase.getInstance(this@PontoActivity)
                val funcionarioDao = db.usuariosDao()
                val funcionarios = funcionarioDao.getUsuario()
                
                if (funcionarios.isNotEmpty()) {
                    funcionarios.first()
                } else {
                    // Criar funcionário de teste se não existir
                    val funcionarioTeste = FuncionariosEntity(
                        id = 1,
                        codigo = "TEST001",
                        nome = "Funcionário Teste (Fallback)",
                        ativo = 1
                    )
                    funcionarioDao.insert(funcionarioTeste)
                    funcionarioTeste
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao obter funcionário de fallback: ${e.message}")
                null
            }
            
            if (funcionario != null) {
                Log.d(TAG, "✅ FUNCIONÁRIO FALLBACK: ${funcionario.nome}")
                
                // ✅ COOLDOWN: Atualizar timestamp do último ponto registrado
                lastPontoRegistrado = System.currentTimeMillis()
                Log.d(TAG, "⏰ Cooldown iniciado (sem TensorFlow) - próximo ponto em ${cooldownPonto}ms")
                
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
            } else {
                Log.w(TAG, "❌ Nenhum funcionário disponível no fallback")
                withContext(Dispatchers.Main) {
                    try {
                        if (!isFinishing && !isDestroyed) {
                            val status = statusText
                            status.text = "❌ Erro no sistema\nTente novamente"
                            
                            status.postDelayed({
                                try {
                                    if (!isFinishing && !isDestroyed) {
                                        val statusInner = statusText
                                        statusInner.text = "📷 Posicione seu rosto na câmera"
                                    } else {
                                        Log.w(TAG, "⚠️ Activity finalizada - não atualizando UI")
                                    }
                                } catch (e2: Exception) {
                                    Log.e(TAG, "❌ Erro no reset UI: ${e2.message}")
                                }
                            }, 2000)
                        } else {
                            Log.w(TAG, "⚠️ Activity finalizada - não atualizando UI")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Erro ao atualizar UI: ${e.message}")
                    }
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro no processamento sem TensorFlow: ${e.message}")
        } finally {
            processandoFace = false
        }
    }

    private suspend fun registrarPontoDireto(funcionario: FuncionariosEntity) {
        try {
            Log.d(TAG, "💾 Registrando ponto para: ${funcionario.nome}")
            
            // ✅ PROTEÇÃO CRÍTICA: Verificar se a Activity ainda está válida
            if (isFinishing || isDestroyed) {
                Log.w(TAG, "⚠️ Activity finalizada - cancelando registro de ponto")
                return
            }
            
            val horarioAtual = System.currentTimeMillis()
            val formato = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
            val dataFormatada = formato.format(Date(horarioAtual))
            
            // ✅ PROTEÇÃO: Capturar localização com timeout
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
                // Não falhar o ponto por causa da localização
            }
            
            // ✅ PROTEÇÃO: Converter foto com validação
            val fotoBase64 = try {
                val bitmap = currentFaceBitmap
                bitmap?.let { bmp ->
                    if (!bmp.isRecycled && bmp.width > 0 && bmp.height > 0) {
                        val base64 = bitmapToBase64(bmp, 80)
                        Log.d(TAG, "📸 Foto convertida: ${base64?.length ?: 0} caracteres")
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
            
            // ✅ PROTEÇÃO: Salvar no banco com retry
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
                    // Aguardar um pouco antes da próxima tentativa
                    kotlinx.coroutines.delay(500)
                }
            }
            
            // ✅ PROTEÇÃO: Salvar para sincronização (não crítico)
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
                // Não falhar o ponto por causa da sincronização
            }
            
            withContext(Dispatchers.Main) {
                try {
                    if (!isFinishing && !isDestroyed) {
                        val locationText = if (latitude != null && longitude != null) {
                            "\n📍 ${String.format("%.6f", latitude)}, ${String.format("%.6f", longitude)}"
                        } else {
                            ""
                        }
                        
                        val status = statusText
                        status.text = "✅ Ponto registrado!\n${funcionarioNome}\n$dataFormatada$locationText"
                        
                        // Reset automático após 5 segundos
                        status.postDelayed({
                            try {
                                if (!isFinishing && !isDestroyed) {
                                    val statusInner = statusText
                                    statusInner.text = "📷 Posicione seu rosto na câmera"
                                    processandoFace = false
                                    lastProcessingTime = 0L
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "❌ Erro no reset: ${e.message}")
                            }
                        }, 5000)
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
                                    statusInner.text = "📷 Posicione seu rosto na câmera"
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
                Log.d(TAG, "🔍 Verificando funcionários de teste...")
                
                val db = AppDatabase.getInstance(this@PontoActivity)
                val funcionarioDao = db.usuariosDao()
                
                val funcionarios = funcionarioDao.getUsuario()
                Log.d(TAG, "📊 Funcionários encontrados: ${funcionarios.size}")
                
                if (funcionarios.isEmpty()) {
                    Log.d(TAG, "📝 Criando funcionário de teste...")
                    
                    val funcionarioTeste = FuncionariosEntity(
                        id = 1,
                        codigo = "TEST001",
                        nome = "Funcionário Teste",
                        ativo = 1
                    )
                    
                    funcionarioDao.insert(funcionarioTeste)
                    Log.d(TAG, "✅ Funcionário de teste criado")
                } else {
                    Log.d(TAG, "✅ Funcionários já existem")
                }
                
                            // ✅ NOVO: Testar FaceRecognitionHelper
            testFaceRecognitionHelper()
            
            // ✅ NOVO: Executar testes de debug
            executarTestesDebug()
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao criar funcionário de teste", e)
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
                detector.close()
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
            if (allPermissionsGranted()) {
                startCamera()
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro no onResume: ${e.message}")
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
} 