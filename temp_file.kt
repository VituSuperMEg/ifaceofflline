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
                processFaceWithFallback(bitmap, boundingBox)
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
                    val inputTensor = convertBitmapToTensorInput(resized)
                    val output = Array(1) { FloatArray(modelOutputSize) }
                    
                    val interp = interpreter
                    if (interp != null) {
                        // ✅ PROTEÇÃO: Executar com timeout e validação
                        try {
                            Log.d(TAG, "🤖 Executando modelo TensorFlow...")
                            interp.run(inputTensor, output)
                            Log.d(TAG, "✅ Modelo executado com sucesso")
                            
                            vetorFacial = output[0]
                            
                            // ✅ PROTEÇÃO: Validar vetor facial
                            if (vetorFacial.isNotEmpty() && vetorFacial.all { !it.isNaN() && !it.isInfinite() }) {
                                Log.d(TAG, "✅ Embedding gerado: ${vetorFacial.size} dimensões")
                            } else {
                                Log.e(TAG, "❌ Vetor facial inválido - valores NaN ou infinitos")
                                throw Exception("Vetor facial inválido")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ Erro na execução do modelo TensorFlow: ${e.message}")
                            throw Exception("Falha na execução do modelo: ${e.message}")
                        }
                    } else {
                        Log.e(TAG, "❌ Interpreter é nulo")
                        throw Exception("Interpreter não disponível")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Erro na execução do TensorFlow: ${e.message}")
                    throw e
                }
                
                // ✅ PROTEÇÃO: Verificar se o vetor facial foi gerado
                if (vetorFacial == null) {
                    Log.e(TAG, "❌ Vetor facial não foi gerado")
                    throw Exception("Vetor facial não disponível")
                }
                
                // ✅ PROTEÇÃO: Reconhecer face com validação e retry
                val funcionario = try {
                    val helper = faceRecognitionHelper
                    if (helper != null) {
                        Log.d(TAG, "🔍 Iniciando reconhecimento facial...")
                        
                        // ✅ SISTEMA DE RETRY: Tentar até 3 vezes
                        var resultado: FuncionariosEntity? = null
                        var tentativas = 0
                        val maxTentativas = 3
                        
                        while (resultado == null && tentativas < maxTentativas) {
                            try {
                                tentativas++
                                Log.d(TAG, "🔄 Tentativa $tentativas de reconhecimento...")
                                
                                resultado = helper.recognizeFace(vetorFacial!!)
                                
                                if (resultado != null) {
                                    Log.d(TAG, "✅ Reconhecimento facial bem-sucedido na tentativa $tentativas")
                                } else {
                                    Log.w(TAG, "⚠️ Reconhecimento retornou nulo na tentativa $tentativas")
                                }
                                
                            } catch (e: Exception) {
                                Log.e(TAG, "❌ Erro na tentativa $tentativas: ${e.message}")
                                if (tentativas >= 3) {
                                    throw e
                                }
                                // Aguardar um pouco antes da próxima tentativa
                                kotlinx.coroutines.delay(100)
                            }
                        }
                        
                        resultado
                    } else {
                        Log.e(TAG, "❌ FaceRecognitionHelper é nulo")
                        null
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Erro no reconhecimento facial após 3 tentativas", e)
                    e.printStackTrace()
                    null
                }
                    
                    if (funcionario != null) {
                        Log.d(TAG, "✅ FUNCIONÁRIO RECONHECIDO: ${funcionario.nome}")
                        
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
                        
                        // ✅ FALLBACK: Usar processamento básico
                        processFaceWithFallback(bitmap, boundingBox)
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
                }
                
