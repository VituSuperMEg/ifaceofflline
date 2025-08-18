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
import com.example.iface_offilne.helpers.bitmapToFloatArray
import com.example.iface_offilne.helpers.cropFace
import com.example.iface_offilne.helpers.fixImageOrientationDefinitive
import com.example.iface_offilne.helpers.toBitmap
import com.example.iface_offilne.databinding.ActivityPontoBinding
import com.example.iface_offilne.util.FaceOverlayView
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.TimeoutCancellationException

class PontoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPontoBinding
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
    private var modelOutputSize = 512 // ✅ CORREÇÃO: Ajustar para o tamanho real do modelo

    private var faceRecognitionHelper: com.example.iface_offilne.helpers.FaceRecognitionHelper? = null
    private var locationHelper: LocationHelper? = null
    private var funcionarioReconhecido: FuncionariosEntity? = null
    private var processandoFace = false
    private var currentFaceBitmap: Bitmap? = null // Para armazenar a foto da face
    private var cameraProvider: ProcessCameraProvider? = null // ✅ NOVO: Referência para limpar camera
    private var lastProcessingTime = 0L // ✅ NOVA: Controle de timeout
    private var processingTimeout = 5000L // ✅ OTIMIZAÇÃO: 5 segundos de timeout
    private var pontoJaRegistrado = false // ✅ NOVA: Controle para evitar registros duplicados
    private var ultimoFuncionarioReconhecido: String? = null // ✅ NOVA: Controle do último funcionário
    
    // ✅ SISTEMA DE TIMEOUT MELHORADO: Mais estável e robusto
    private var lastFaceDetectionTime = 0L // Última vez que detectou uma face
    private var noFaceTimeout = 300000L // ✅ CORREÇÃO: 5 minutos sem detectar face (aumentado)
    private var activityStartTime = 0L // Tempo de início da activity
    private var maxActivityTime = 600000L // ✅ CORREÇÃO: 10 minutos máximo na tela (aumentado)
    private var timeoutPausado = false // Para pausar timeout durante processamento importante
    private var monitorHandler: Handler? = null // ✅ NOVA: Handler dedicado para controle

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private const val REQUEST_CODE_LOCATION_PERMISSIONS = 20
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
        private val LOCATION_PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        private const val TAG = "PontoActivity"
        
        // ✅ NOVA: Função para monitorar memória
        private fun logMemoryUsage(context: String) {
            try {
                val runtime = Runtime.getRuntime()
                val usedMemory = runtime.totalMemory() - runtime.freeMemory()
                val maxMemory = runtime.maxMemory()
                val percentUsed = (usedMemory * 100) / maxMemory
                
                Log.d(TAG, "🧠 Memória [$context]: ${usedMemory/1024/1024}MB/${maxMemory/1024/1024}MB (${percentUsed}%)")
                
                if (percentUsed > 80) {
                    Log.w(TAG, "⚠️ ATENÇÃO: Uso de memória alto (${percentUsed}%)")
                    System.gc() // Forçar garbage collection
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao verificar memória: ${e.message}")
            }
        }
    }

    private var faceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .build()
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Inicializar binding
        binding = ActivityPontoBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Configurar para uso completo da tela
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = ContextCompat.getColor(this, android.R.color.transparent)
            window.navigationBarColor = ContextCompat.getColor(this, android.R.color.transparent)
        }
        
        setupUI()
        
        // ✅ CORREÇÃO: Resetar estado APÓS setupUI
        resetarEstadoReconhecimento()
        
        // ✅ CORREÇÃO: Garantir que processandoFace começa como false
        processandoFace = false
        lastProcessingTime = 0L // ✅ NOVA: Garantir que o tempo também começa zerado
        pontoJaRegistrado = false // ✅ NOVA: Garantir que não há registro pendente
        ultimoFuncionarioReconhecido = null // ✅ NOVA: Limpar último funcionário
        
        // ✅ NOVO: Inicializar tempos de timeout
        activityStartTime = System.currentTimeMillis()
        lastFaceDetectionTime = System.currentTimeMillis()
        
        Log.d(TAG, "🚀 === INICIANDO SISTEMA DE PONTO ===")
        Log.d(TAG, "📊 Estado inicial: processandoFace = $processandoFace, lastProcessingTime = $lastProcessingTime")
        Log.d(TAG, "⏱️ Timeout configurado: ${noFaceTimeout/1000}s sem face, ${maxActivityTime/1000}s máximo total")
        Log.d(TAG, "📸 Sistema de captura de foto RESTAURADO - fotos serão enviadas com os pontos")
        
        // ✅ OTIMIZAÇÃO: Monitorar memória inicial
        logMemoryUsage("Inicialização")

        // ✅ OTIMIZAÇÃO: Inicializar helper de reconhecimento facial com tratamento de erro
        try {
            faceRecognitionHelper = com.example.iface_offilne.helpers.FaceRecognitionHelper(this)
            Log.d(TAG, "✅ Face recognition helper inicializado")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao inicializar face recognition helper: ${e.message}")
            // Tentar inicializar novamente após um delay
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    faceRecognitionHelper = com.example.iface_offilne.helpers.FaceRecognitionHelper(this)
                    Log.d(TAG, "✅ Face recognition helper inicializado na segunda tentativa")
                } catch (e2: Exception) {
                    Log.e(TAG, "❌ Falha na segunda tentativa: ${e2.message}")
                }
            }, 2000)
        }
        
        // ✅ OTIMIZAÇÃO: Inicializar helper de localização com tratamento de erro
        try {
            locationHelper = LocationHelper(this)
            Log.d(TAG, "✅ Location helper inicializado")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao inicializar location helper: ${e.message}")
            // Tentar inicializar novamente após um delay
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    locationHelper = LocationHelper(this)
                    Log.d(TAG, "✅ Location helper inicializado na segunda tentativa")
                } catch (e2: Exception) {
                    Log.e(TAG, "❌ Falha na segunda tentativa: ${e2.message}")
                }
            }, 2000)
        }
        
        // ✅ OTIMIZAÇÃO: Limpar cache inicial para garantir dados atualizados
        faceRecognitionHelper?.clearCache()

        // ✅ OTIMIZAÇÃO: Carregar modelo TensorFlow com tratamento de erro
        CoroutineScope(Dispatchers.IO).launch {
            try {
                loadTensorFlowModel()
                Log.d(TAG, "✅ Modelo TensorFlow carregado com sucesso")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao carregar modelo TensorFlow: ${e.message}")
                // Tentar carregar novamente após um delay
                delay(3000)
                try {
                    loadTensorFlowModel()
                    Log.d(TAG, "✅ Modelo TensorFlow carregado na segunda tentativa")
                } catch (e2: Exception) {
                    Log.e(TAG, "❌ Falha na segunda tentativa: ${e2.message}")
                }
            }
        }
        
        // Criar funcionário de teste se não existir
        createTestEmployeeIfNeeded()
        
        // Remover backgroundTint do botão voltar
        binding.btnVoltar.backgroundTintList = null

        // Solicitar permissões
        if (allPermissionsGranted()) {
            // ✅ NOVA: Verificar permissões de localização também
            if (!allLocationPermissionsGranted()) {
                ActivityCompat.requestPermissions(this, LOCATION_PERMISSIONS, REQUEST_CODE_LOCATION_PERMISSIONS)
            }
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }
        
        // Verificar integridade das faces e limpar duplicatas
        CoroutineScope(Dispatchers.IO).launch {
            try {
                verificarIntegridadeFaces()
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao verificar integridade: ${e.message}")
            }
        }
        
        // ✅ NOVA FUNÇÃO: Verificar e corrigir problemas de reconhecimento
        CoroutineScope(Dispatchers.IO).launch {
            try {
                verificarECorrigirProblemasReconhecimento()
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao verificar problemas de reconhecimento: ${e.message}")
            }
        }
        
        // ✅ NOVA: Testar rigorosidade dos critérios de reconhecimento
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val relatorio = faceRecognitionHelper?.testarRigorosidadeCriterios()
                Log.d(TAG, "📋 Relatório de critérios: $relatorio")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao testar critérios: ${e.message}")
            }
        }
        
        // ✅ NOVA FUNÇÃO: Monitor de estado para evitar travamento
        startStateMonitor()
    }
    
    /**
     * ✅ MONITOR DE ESTADO OTIMIZADO: Menos agressivo e mais estável
     */
    private fun startStateMonitor() {
        try {
            // ✅ CORREÇÃO: Parar monitor anterior se existir
            stopStateMonitor()
            
            // ✅ CORREÇÃO: Criar handler dedicado
            monitorHandler = Handler(Looper.getMainLooper())
            
            val monitorRunnable = object : Runnable {
                override fun run() {
                    try {
                        // ✅ CORREÇÃO: Verificar se activity ainda é válida
                        if (isFinishing || isDestroyed) {
                            Log.d(TAG, "🔄 Activity finalizada - parando monitor")
                            return
                        }
                        
                        val currentTime = System.currentTimeMillis()
                        
                        // ✅ OTIMIZAÇÃO: Verificações menos frequentes e mais suaves
                        val timeSinceLastFace = currentTime - lastFaceDetectionTime
                        val totalActivityTime = currentTime - activityStartTime
                        
                        // ✅ OTIMIZAÇÃO: Timeout mais longo e menos agressivo
                        if (!timeoutPausado && !processandoFace) {
                            if (totalActivityTime > maxActivityTime) {
                                Log.w(TAG, "⏱️ Tempo máximo da activity atingido (${totalActivityTime/1000}s) - mantendo na tela de ponto")
                                // Apenas informar e manter na tela
                                statusText.text = "⏱️ Sessão longa. Continue posicionando o rosto."
                            }
                            // Verificar timeout de face e apenas resetar dicas, sem navegar
                            if (timeSinceLastFace > noFaceTimeout) {
                                Log.w(TAG, "⏱️ Timeout sem detectar face (${timeSinceLastFace/1000}s) - permanecendo na tela")
                                statusText.text = "📷 Não detectei rosto há um tempo. Posicione seu rosto na câmera."
                            }
                        }
                        
                        // ✅ OTIMIZAÇÃO: Verificar travamento com timeout maior e menos agressivo
                        if (processandoFace) {
                            val timeSinceStart = currentTime - lastProcessingTime
                            if (timeSinceStart > processingTimeout * 3) { // Triplicar o timeout para ser menos agressivo
                                Log.w(TAG, "⚠️ Processamento travado há ${timeSinceStart}ms - resetando suavemente")
                                runOnUiThread {
                                    try {
                                        forcarResetEstado()
                                    } catch (e: Exception) {
                                        Log.e(TAG, "❌ Erro ao resetar estado: ${e.message}")
                                    }
                                }
                            }
                        }
                        
                        // ✅ OTIMIZAÇÃO: Verificar modelo com menos frequência
                        if (!modelLoaded && (currentTime % 60000 < 10000)) { // A cada 60s por 10s
                            CoroutineScope(Dispatchers.IO).launch {
                                try {
                                    loadTensorFlowModel()
                                } catch (e: Exception) {
                                    Log.e(TAG, "❌ Erro ao recarregar modelo: ${e.message}")
                                }
                            }
                        }
                        
                        // ✅ NOVA: Verificar uso de memória periodicamente
                        if (currentTime % 30000 < 5000) { // A cada 30s
                            logMemoryUsage("Monitor")
                        }
                        
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Erro no monitor (ignorando): ${e.message}")
                    }
                    
                    // ✅ OTIMIZAÇÃO: Reagendar apenas se activity ainda for válida
                    try {
                        if (!isFinishing && !isDestroyed && monitorHandler != null) {
                            monitorHandler?.postDelayed(this, 10000) // ✅ OTIMIZAÇÃO: 10 segundos (menos frequente)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Erro ao reagendar monitor: ${e.message}")
                    }
                }
            }
            
            // ✅ OTIMIZAÇÃO: Iniciar monitor com delay inicial
            monitorHandler?.postDelayed(monitorRunnable, 10000)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao iniciar monitor: ${e.message}")
        }
    }
    
    /**
     * ✅ NOVA: Parar monitor de estado para evitar memory leaks
     */
    private fun stopStateMonitor() {
        try {
            monitorHandler?.removeCallbacksAndMessages(null)
            monitorHandler = null
            Log.d(TAG, "✅ Monitor de estado parado")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao parar monitor: ${e.message}")
        }
    }

    private fun setupUI() {
        try {
            Log.d(TAG, "🔧 Configurando UI...")
            
            // Configurar padding para status bar
            binding.mainLayout.setPadding(0, getStatusBarHeight(), 0, 0)
            
            // Referenciar views usando binding
            statusText = binding.statusText
            previewView = binding.previewView
            overlay = binding.overlay
            funcionarioInfo = binding.funcionarioInfo
            funcionarioNome = binding.funcionarioNome
            ultimoPonto = binding.ultimoPonto
            tipoPontoRadioGroup = binding.tipoPontoRadioGroup
            
            // Configurar botão voltar
            binding.btnVoltar.setOnClickListener { 
                Log.d(TAG, "🔄 Usuário clicou em voltar")
                voltarParaTelaInicial("Botão voltar pressionado")
            }
            
            // Removido: long press que abria AlertDialogs de análise para manter a tela limpa
            binding.btnVoltar.setOnLongClickListener {
                Log.d(TAG, "🔕 Long press desativado nesta tela")
                true
            }
            
            Log.d(TAG, "✅ UI configurada com sucesso")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao configurar UI", e)
            // Fallback simples
            val errorLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER
                setBackgroundColor(Color.WHITE)
            }
            
            val errorText = TextView(this).apply {
                text = "❌ Erro ao carregar interface\nTente novamente"
                textSize = 18f
                gravity = android.view.Gravity.CENTER
                setTextColor(Color.RED)
                setPadding(32, 32, 32, 32)
            }
            
            errorLayout.addView(errorText)
            setContentView(errorLayout)
        }
    }

    private fun loadTensorFlowModel() {
        try {
            Log.d(TAG, "🔄 Carregando modelo TensorFlow...")
            
            // ✅ CORREÇÃO: Verificar se o contexto ainda é válido
            if (isFinishing || isDestroyed) {
                Log.w(TAG, "⚠️ Activity finalizada, cancelando carregamento do modelo")
                return
            }
            
            // Tentar carregar primeiro dos assets
            val modelFile = try {
                assets.open("facenet_model.tflite")
            } catch (e: Exception) {
                Log.w(TAG, "facenet_model.tflite não encontrado em assets, tentando mobilefacenet...")
                // Fallback para o modelo raw
                try {
                    resources.openRawResource(R.raw.mobilefacenet)
                } catch (e2: Exception) {
                    Log.e(TAG, "❌ Nenhum modelo encontrado: ${e2.message}")
                    throw e2
                }
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
            
            // ✅ CORREÇÃO: Verificar se o buffer é válido
            if (modelBuffer.capacity() <= 0) {
                throw IllegalStateException("Modelo vazio ou inválido")
            }
            
            val options = Interpreter.Options().apply {
                setNumThreads(1) // ✅ CORREÇÃO: Usar apenas 1 thread para evitar conflitos
                setUseNNAPI(false)
                setAllowFp16PrecisionForFp32(false) // ✅ CORREÇÃO: Forçar precisão FP32
            }
            
            // ✅ CORREÇÃO: Criar interpreter com proteção
            interpreter = try {
                Interpreter(modelBuffer, options)
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao criar interpreter: ${e.message}")
                null
            }
            
            // ✅ CORREÇÃO: Verificar se o interpreter foi criado corretamente
            interpreter?.let { interp ->
                try {
                    interp.allocateTensors()
                    modelLoaded = true
                    Log.d(TAG, "✅ Modelo TensorFlow carregado com sucesso")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Erro ao alocar tensores: ${e.message}")
                    interpreter = null
                    modelLoaded = false
                    throw e
                }
            } ?: run {
                Log.e(TAG, "❌ Falha ao criar interpreter")
                modelLoaded = false
                throw IllegalStateException("Interpreter não foi criado")
            }
            
            // Verificar dimensões do modelo
            val inputTensor = interpreter?.getInputTensor(0)
            val outputTensor = interpreter?.getOutputTensor(0)
            
            if (inputTensor != null && outputTensor != null) {
                val inputShape = inputTensor.shape()
                val outputShape = outputTensor.shape()
                
                Log.d(TAG, "📊 Dimensões do modelo:")
                Log.d(TAG, "   Input: ${inputShape.contentToString()}")
                Log.d(TAG, "   Output: ${outputShape.contentToString()}")
                
                // Atualizar dimensões baseado no modelo real
                if (inputShape.size >= 4) {
                    modelInputHeight = inputShape[1]
                    modelInputWidth = inputShape[2]
                    Log.d(TAG, "✅ Dimensões atualizadas: ${modelInputWidth}x${modelInputHeight}")
                }
                
                if (outputShape.size >= 2) {
                    modelOutputSize = outputShape[1]
                    Log.d(TAG, "✅ Tamanho de saída atualizado: $modelOutputSize")
                }
            }
            
            modelLoaded = true
            Log.d(TAG, "✅ Modelo TensorFlow carregado com sucesso")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao carregar modelo: ${e.message}", e)
            modelLoaded = false
        }
    }

    private fun startCamera() {
        Log.d(TAG, "📷 === INICIANDO CÂMERA ===")
        
        // ✅ CORREÇÃO: Limpar camera anterior se existir
        stopCamera()
        
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            try {
                // ✅ CORREÇÃO: Verificar se activity ainda é válida
                if (isFinishing || isDestroyed) {
                    Log.w(TAG, "⚠️ Activity finalizada, cancelando inicialização da câmera")
                    return@addListener
                }
                
                cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder()
                    .setTargetResolution(android.util.Size(800, 600)) // ✅ CORREÇÃO: Resolução menor para economizar memória
                    .build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                imageAnalyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setTargetResolution(android.util.Size(640, 480)) // ✅ CORREÇÃO: Resolução menor para análise
                    .build()
                    .also {
                        it.setAnalyzer(ContextCompat.getMainExecutor(this)) { imageProxy ->
                            // ✅ CORREÇÃO: Verificar se activity ainda é válida antes de processar
                            if (!isFinishing && !isDestroyed) {
                                processImage(imageProxy)
                            } else {
                                imageProxy.close()
                            }
                        }
                    }

                val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

                try {
                    cameraProvider?.unbindAll()
                    cameraProvider?.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer)
                    Log.d(TAG, "✅ Câmera iniciada com sucesso")
                } catch (exc: Exception) {
                    Log.e(TAG, "❌ Falha ao iniciar câmera", exc)
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro crítico ao iniciar câmera: ${e.message}")
                e.printStackTrace()
            }

        }, ContextCompat.getMainExecutor(this))
    }
    
    // ✅ NOVA FUNÇÃO: Parar câmera de forma segura
    private fun stopCamera() {
        try {
            cameraProvider?.unbindAll()
            cameraProvider = null
            Log.d(TAG, "📷 Câmera parada com sucesso")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao parar câmera: ${e.message}")
        }
    }

    private fun processImage(imageProxy: ImageProxy) {
        try {
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                // ✅ CORREÇÃO: Verificar se a imagem é válida
                if (mediaImage.width <= 0 || mediaImage.height <= 0) {
                    Log.w(TAG, "⚠️ Imagem inválida: ${mediaImage.width}x${mediaImage.height}")
                    imageProxy.close()
                    return
                }
                
                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

                faceDetector.process(image)
                    .addOnSuccessListener { faces ->
                        try {
                            if (faces.isNotEmpty()) {
                                val face = faces[0]
                                
                                // ✅ NOVO: Atualizar timestamp da última detecção de face
                                lastFaceDetectionTime = System.currentTimeMillis()
                                
                                // ✅ CORREÇÃO: Verificar se o boundingBox é válido
                                if (face.boundingBox.width() <= 0 || face.boundingBox.height() <= 0) {
                                    Log.w(TAG, "⚠️ BoundingBox inválido: ${face.boundingBox}")
                                    overlay.clear()
                                    imageProxy.close()
                                    return@addOnSuccessListener
                                }
                                
                                overlay.setBoundingBox(face.boundingBox, mediaImage.width, mediaImage.height)

                                // Verificar distância da face
                                val faceArea = face.boundingBox.width() * face.boundingBox.height()
                                val screenArea = mediaImage.width * mediaImage.height
                                val faceRatio = faceArea.toFloat() / screenArea.toFloat()
                                
                                Log.d(TAG, "📏 Face ratio: $faceRatio (área face: $faceArea, área tela: $screenArea)")
                                
                                // ✅ CORREÇÃO: Verificar se há timeout apenas se estiver processando
                                if (processandoFace) {
                                    val currentTime = System.currentTimeMillis()
                                    if (currentTime - lastProcessingTime > processingTimeout) {
                                        Log.w(TAG, "⚠️ Timeout detectado, resetando processandoFace")
                                        processandoFace = false
                                        lastProcessingTime = 0L
                                        try {
                                            if (::statusText.isInitialized && !isFinishing && !isDestroyed) {
                                                statusText.text = "📷 Posicione seu rosto na câmera"
                                            }
                                        } catch (e: Exception) {
                                            Log.e(TAG, "❌ Erro ao resetar status: ${e.message}")
                                        }
                                    }
                                }
                                
                                // ✅ OTIMIZAÇÃO: Atualizar status baseado na posição da face
                                if (!processandoFace) {
                                    if (faceRatio < 0.08f) {
                                        statusText.text = "📷 Aproxime seu rosto"
                                    } else if (!overlay.isFaceInOval(face.boundingBox)) {
                                        statusText.text = "📷 Centre seu rosto no oval"
                                    } else {
                                        statusText.text = "🔍 Pronto..."
                                    }
                                }
                                
                                // ✅ CORREÇÃO: Iniciar reconhecimento apenas quando face estiver bem posicionada
                                if (overlay.isFaceInOval(face.boundingBox) && faceRatio >= 0.08f && !processandoFace) {
                                    // ✅ CORREÇÃO: Verificar se o modelo está carregado antes de processar
                                    if (!modelLoaded || interpreter == null) {
                                        Log.w(TAG, "⚠️ Modelo não carregado, aguardando...")
                                        statusText.text = "⏳ Carregando modelo..."
                                        imageProxy.close()
                                        return@addOnSuccessListener
                                    }
                                    
                                                        processandoFace = true
                    lastProcessingTime = System.currentTimeMillis()
                    Log.d(TAG, "👤 === INICIANDO RECONHECIMENTO FACIAL ===")
                    
                    // ✅ NOVA: Monitorar memória antes do processamento
                    logMemoryUsage("Antes reconhecimento")
                    
                    statusText.text = "🔍 Reconhecendo..."
                                    
                                    try {
                                        // Converter para bitmap antes de fechar o proxy
                                        val bitmap = toBitmap(mediaImage)
                                        
                                        // ✅ CORREÇÃO: Verificar se o bitmap foi criado corretamente
                                        if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) {
                                            Log.e(TAG, "❌ Bitmap inválido criado: ${bitmap.width}x${bitmap.height}")
                                            processandoFace = false
                                            lastProcessingTime = 0L
                                            imageProxy.close()
                                            return@addOnSuccessListener
                                        }
                                        
                                        imageProxy.close()
                                        
                                        // Processar o bitmap diretamente
                                        processDetectedFace(bitmap, face.boundingBox)
                                    } catch (e: Exception) {
                                        Log.e(TAG, "❌ Erro ao converter bitmap", e)
                                        processandoFace = false
                                        lastProcessingTime = 0L
                                        imageProxy.close()
                                    }
                                } else {
                                    // ✅ CORREÇÃO: Fechar proxy se não iniciou processamento
                                    imageProxy.close()
                                }
                            } else {
                                overlay.clear()
                                // ✅ NOVA: Resetar estado se não há faces detectadas
                                if (processandoFace) {
                                    Log.w(TAG, "⚠️ Nenhuma face detectada, resetando estado de processamento")
                                    processandoFace = false
                                    lastProcessingTime = 0L
                                    try {
                                        if (::statusText.isInitialized && !isFinishing && !isDestroyed) {
                                            statusText.text = "📷 Posicione seu rosto na câmera"
                                        }
                                    } catch (e: Exception) {
                                        Log.e(TAG, "❌ Erro ao resetar status: ${e.message}")
                                    }
                                }
                                imageProxy.close()
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ Erro no processamento de faces", e)
                            processandoFace = false
                            imageProxy.close()
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "❌ Erro na detecção", e)
                        processandoFace = false
                        imageProxy.close()
                    }
            } else {
                Log.w(TAG, "⚠️ MediaImage é nulo")
                imageProxy.close()
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro crítico no processImage", e)
            processandoFace = false
            try {
                imageProxy.close()
            } catch (closeException: Exception) {
                Log.e(TAG, "❌ Erro ao fechar imageProxy", closeException)
            }
        }
    }

    private fun processDetectedFace(bitmap: Bitmap, boundingBox: Rect) {
        // ✅ CORREÇÃO: Resetar estado a cada nova detecção
        Log.d(TAG, "🔄 === PROCESSANDO FACE DETECTADA ===")
        funcionarioReconhecido = null
        
        // ✅ OTIMIZAÇÃO: Pausar timeout apenas durante processamento crítico
        pausarTimeout("Processando face detectada")
        
        // ✅ OTIMIZAÇÃO: Auto-reset reduzido para 8 segundos
        Handler(Looper.getMainLooper()).postDelayed({
            if (processandoFace) {
                Log.w(TAG, "⚠️ Auto-reset do processandoFace após 8 segundos")
                processandoFace = false
                lastProcessingTime = 0L
                pontoJaRegistrado = false
                // ✅ CORREÇÃO: NÃO limpar ultimoFuncionarioReconhecido no auto-reset
                // Só limpar no reset manual ou nova pessoa
                try {
                    if (::statusText.isInitialized && !isFinishing && !isDestroyed) {
                        statusText.text = "📷 Posicione seu rosto na câmera"
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Erro ao resetar status: ${e.message}")
                }
                retomarTimeout("Auto-reset timeout")
            }
        }, 8000)
        
        // ✅ OTIMIZAÇÃO: Verificações básicas apenas
        if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) {
            Log.e(TAG, "❌ Bitmap inválido")
            processandoFace = false
            lastProcessingTime = 0L
            retomarTimeout("Bitmap inválido")
            return
        }
        
        if (boundingBox.width() <= 0 || boundingBox.height() <= 0) {
            Log.e(TAG, "❌ BoundingBox inválido")
            processandoFace = false
            lastProcessingTime = 0L
            retomarTimeout("BoundingBox inválido")
            return
        }
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // ✅ OTIMIZAÇÃO: Verificação simples de activity
                if (isFinishing || isDestroyed) {
                    Log.w(TAG, "⚠️ Activity finalizada, cancelando")
                    return@launch
                }
                
                // ✅ OTIMIZAÇÃO: Verificação rápida do modelo
                if (!modelLoaded || interpreter == null) {
                    Log.w(TAG, "⚠️ Modelo não carregado")
                                            withContext(Dispatchers.Main) {
                            try {
                                if (::statusText.isInitialized && !isFinishing && !isDestroyed) {
                                    statusText.text = "❌ Carregando modelo..."
                                } else {
                                    // Activity finalizada, não fazer nada
                                }
                            } catch (e: Exception) {
                            Log.e(TAG, "❌ Erro ao atualizar status: ${e.message}")
                        }
                    }
                    processandoFace = false
                    lastProcessingTime = 0L
                    retomarTimeout("Modelo não carregado")
                    return@launch
                }
                
                // ✅ OTIMIZAÇÃO: Verificação rápida do helper
                if (faceRecognitionHelper == null) {
                    Log.w(TAG, "⚠️ Helper não disponível")
                    processandoFace = false
                    lastProcessingTime = 0L
                    retomarTimeout("Helper não disponível")
                    return@launch
                }
                
                try {
                    withContext(Dispatchers.Main) {
                        if (::statusText.isInitialized && !isFinishing) {
                            statusText.text = "🔍 Reconhecendo..."
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ Erro ao atualizar status: ${e.message}")
                }

                Log.d(TAG, "🔄 Iniciando processamento de face...")

                // ✅ OTIMIZAÇÃO: Verificação simplificada de bitmap
                if (bitmap.isRecycled) {
                    throw IllegalStateException("Bitmap reciclado")
                }

                // Recortar face
                val faceBmp = cropFace(bitmap, boundingBox)
                if (faceBmp.isRecycled || faceBmp.width <= 0 || faceBmp.height <= 0) {
                    throw IllegalStateException("Face recortada inválida")
                }
                
                // ✅ CORREÇÃO: Limpar bitmap anterior para evitar memory leak
                currentFaceBitmap?.let { oldBitmap ->
                    if (!oldBitmap.isRecycled) {
                        oldBitmap.recycle()
                    }
                }
                
                // Salvar foto da face para registro do ponto
                val faceForPoint = try {
                    Bitmap.createScaledBitmap(faceBmp, 300, 300, true)
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Erro ao criar faceForPoint: ${e.message}")
                    null
                }
                
                currentFaceBitmap = faceForPoint?.let { bitmap ->
                    try {
                        val fixedBitmap = fixImageOrientationDefinitive(bitmap)
                        // ✅ CORREÇÃO: Reciclar bitmap original se diferente do corrigido
                        if (fixedBitmap != bitmap && !bitmap.isRecycled) {
                            bitmap.recycle()
                        }
                        fixedBitmap
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Erro ao corrigir orientação: ${e.message}")
                        bitmap
                    }
                }
                
                if (currentFaceBitmap != null) {
                    Log.d(TAG, "📸 Foto da face capturada com sucesso: ${currentFaceBitmap!!.width}x${currentFaceBitmap!!.height}")
                } else {
                    Log.w(TAG, "⚠️ Falha ao capturar foto da face")
                }

                // Redimensionar para o modelo
                val resized = try {
                    Bitmap.createScaledBitmap(faceBmp, modelInputWidth, modelInputHeight, true)
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Erro ao redimensionar: ${e.message}")
                    // ✅ CORREÇÃO: Limpar bitmap de face antes de lançar exceção
                    if (!faceBmp.isRecycled) {
                        faceBmp.recycle()
                    }
                    throw e
                }
                
                if (resized.isRecycled || resized.width != modelInputWidth || resized.height != modelInputHeight) {
                    // ✅ CORREÇÃO: Limpar bitmaps antes de lançar exceção
                    if (!faceBmp.isRecycled) {
                        faceBmp.recycle()
                    }
                    if (!resized.isRecycled) {
                        resized.recycle()
                    }
                    throw IllegalStateException("Redimensionamento falhou")
                }

                if (modelLoaded && interpreter != null) {
                    // ✅ OTIMIZAÇÃO: Processamento direto sem logs excessivos
                    val inputTensor = convertBitmapToTensorInput(resized)
                    // Rebobinar buffer para garantir posição no início
                    inputTensor.rewind()
                    val expectedSize = 4 * modelInputWidth * modelInputHeight * 3
                    if (inputTensor.capacity() != expectedSize) {
                        throw IllegalStateException("Tensor tem tamanho incorreto")
                    }
                    
                    // ✅ PROTEÇÃO CRÍTICA: Validar input antes da inferência
                    if (inputTensor.capacity() <= 0) {
                        throw IllegalStateException("Tensor vazio")
                    }
                    
                    // ✅ PROTEÇÃO: Verificar se todos os valores são zero (indica problema)
                    val position = inputTensor.position()
                    inputTensor.rewind()
                    var allZeros = true
                    for (i in 0 until minOf(100, inputTensor.capacity() / 4)) { // Verificar primeiros 100 floats
                        if (inputTensor.getFloat() != 0.0f) {
                            allZeros = false
                            break
                        }
                    }
                    inputTensor.position(position) // Restaurar posição
                    
                    if (allZeros) {
                        throw IllegalStateException("Tensor contém apenas zeros - problema na normalização")
                    }
                    
                    					// ✅ PROTEÇÃO CRÍTICA: Verificar alinhamento de memória
					if (inputTensor.capacity() % 4 != 0) {
						throw IllegalStateException("Tensor não está alinhado corretamente")
					}
					
					// ✅ PROTEÇÃO: Verificar se há valores NaN/Infinitos no input
					inputTensor.rewind()
					var hasInvalidValues = false
					for (i in 0 until minOf(50, inputTensor.capacity() / 4)) {
						val value = inputTensor.getFloat()
						if (value.isNaN() || value.isInfinite()) {
							hasInvalidValues = true
							break
						}
					}
					inputTensor.position(position)
					
					if (hasInvalidValues) {
						throw IllegalStateException("Tensor contém valores inválidos (NaN/Infinito)")
					}
					
					// ✅ PROTEÇÃO CRÍTICA: Verificar se o buffer está acessível
					if (!inputTensor.hasRemaining()) {
						throw IllegalStateException("Tensor não tem dados restantes")
					}
					
					// ✅ PROTEÇÃO: Verificar se o tamanho está correto (usando variável já declarada)
					if (inputTensor.capacity() != expectedSize) {
						throw IllegalStateException("Tensor com tamanho incorreto: ${inputTensor.capacity()} vs $expectedSize")
					}
                    
                    val output = Array(1) { FloatArray(modelOutputSize) }
                    
                    // ✅ OTIMIZAÇÃO: Inferência com timeout reduzido
                    val vetorFacial = try {
                        interpreter?.let { interp ->
                            if (inputTensor.capacity() <= 0) {
                                throw IllegalStateException("Tensor vazio")
                            }
                            
                            if (isFinishing || isDestroyed) {
                                throw IllegalStateException("Activity finalizada durante inferência")
                            }
                            
                            // ✅ OTIMIZAÇÃO: Timeout reduzido para 5 segundos
                            try {
                                withTimeout(5000L) { // Reduzido de 10 para 5 segundos
                                    // ✅ PROTEÇÃO CRÍTICA: Capturar crashes nativos do TFLite
                                    try {
                                        // Verificar se o interpreter ainda é válido
                                        if (interp == null) {
                                            throw IllegalStateException("Interpreter é null")
                                        }
                                        
                                        // ✅ PROTEÇÃO: Verificar se o modelo está carregado corretamente
                                        try {
                                            interp.allocateTensors()
                                        } catch (e: Exception) {
                                            throw IllegalStateException("Falha ao alocar tensores: ${e.message}")
                                        }
                                        
                                        // Executar inferência com proteção nativa
                                        interp.run(inputTensor, output)
                                        
                                        // ✅ VALIDAÇÃO: Verificar se o output é válido
                                        if (output[0].all { it == 0.0f || it.isNaN() || it.isInfinite() }) {
                                            throw IllegalStateException("Output inválido - todos zeros ou valores inválidos")
                                        }
                                        
                                        // ✅ VALIDAÇÃO ADICIONAL: Verificar se há pelo menos alguns valores não-zero
                                        val nonZeroCount = output[0].count { it != 0.0f && !it.isNaN() && !it.isInfinite() }
                                        if (nonZeroCount < 10) {
                                            throw IllegalStateException("Output insuficiente - apenas $nonZeroCount valores válidos")
                                        }
                                        
                                    } catch (e: UnsatisfiedLinkError) {
                                        Log.e(TAG, "❌ Erro nativo do TFLite: ${e.message}")
                                        throw IllegalStateException("Erro nativo do TensorFlow Lite")
                                    } catch (e: UnsatisfiedLinkError) {
                                        Log.e(TAG, "❌ Erro de link nativo: ${e.message}")
                                        throw IllegalStateException("Erro de biblioteca nativa")
                                    } catch (e: Exception) {
                                        Log.e(TAG, "❌ Erro durante inferência: ${e.message}")
                                        throw e
                                    }
                                    
                                    output[0]
                                }
							} catch (e: TimeoutCancellationException) {
                                Log.e(TAG, "❌ Erro na inferência: ${e.message}")
                                throw IllegalStateException("Falha na execução do modelo")
                            }
                            
                            if (output[0].isEmpty()) {
                                throw IllegalStateException("Saída vazia do modelo")
                            }
                            
                            output[0]
                        } ?: throw IllegalStateException("Interpreter não disponível")
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Erro na inferência: ${e.message}")
                        throw e
                    }

                    // ✅ OTIMIZAÇÃO: Correção rápida de NaN
                    var vetorFacialFinal = vetorFacial
                    if (vetorFacial.any { it.isNaN() || it.isInfinite() }) {
                        Log.w(TAG, "⚠️ Corrigindo valores inválidos...")
                        vetorFacialFinal = FloatArray(vetorFacial.size) { index ->
                            val valor = vetorFacial[index]
                            if (valor.isNaN() || valor.isInfinite()) 0.0f else valor
                        }
                    }

                    // ✅ CORREÇÃO: Limpar bitmap redimensionado após uso do modelo
                    try {
                        if (!resized.isRecycled) {
                            resized.recycle()
                        }
                        if (!faceBmp.isRecycled) {
                            faceBmp.recycle()
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "⚠️ Erro ao limpar bitmaps temporários: ${e.message}")
                    }

                    // ✅ OTIMIZAÇÃO: Reconhecimento facial otimizado
                    val funcionario = try {
                        faceRecognitionHelper?.recognizeFace(vetorFacialFinal)
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Erro no reconhecimento: ${e.message}")
                        // ✅ CORREÇÃO: Não deixar crashar, apenas retornar null
                        null
                    }

                    // ✅ OTIMIZAÇÃO: Processamento de resultado com tratamento de erro
                    try {
                        withContext(Dispatchers.Main) {
                            if (isFinishing || isDestroyed) {
                                Log.w(TAG, "⚠️ Activity finalizada durante resultado")
                                return@withContext
                            }
                            
                            if (funcionario != null) {
                                Log.d(TAG, "✅ Funcionário reconhecido: ${funcionario.nome}")
                                
                                // ✅ CORREÇÃO CRÍTICA: Verificação de duplicatas APENAS para a MESMA pessoa em pouco tempo
                                val agora = System.currentTimeMillis()
                                val tempoLimiteParaDuplicata = 10000L // 10 segundos para considerar duplicata
                                val ehMesmaPessoa = ultimoFuncionarioReconhecido == funcionario.codigo
                                val dentrodoTempoLimite = (agora - lastProcessingTime) < tempoLimiteParaDuplicata
                                
                                if (pontoJaRegistrado && ehMesmaPessoa && dentrodoTempoLimite) {
                                    Log.w(TAG, "⚠️ Ponto duplicado para ${funcionario.nome} em ${agora - lastProcessingTime}ms")
                                    processandoFace = false
                                    lastProcessingTime = 0L
                                    retomarTimeout("Ponto duplicado")
                                    return@withContext
                                }
                                
                                // ✅ CORREÇÃO: Sempre permitir pessoa DIFERENTE
                                if (!ehMesmaPessoa) {
                                    Log.d(TAG, "🔄 Nova pessoa detectada: ${funcionario.nome} (anterior: $ultimoFuncionarioReconhecido)")
                                    pontoJaRegistrado = false // Reset para nova pessoa
                                }
                                
                                // Marcar como processado
                                pontoJaRegistrado = true
                                ultimoFuncionarioReconhecido = funcionario.codigo
                                lastProcessingTime = agora
                                
                                // ✅ OTIMIZAÇÃO: Registro de ponto direto e rápido
                                CoroutineScope(Dispatchers.IO).launch {
                                    try {
                                        if (isFinishing || isDestroyed) {
                                            Log.w(TAG, "⚠️ Activity finalizada antes do registro")
                                            return@launch
                                        }
                                        
                                        // ✅ CORREÇÃO: Capturar exceções no registro de ponto
                                        try {
                                            registrarPonto(funcionario)
                                        } catch (e: Exception) {
                                            Log.e(TAG, "❌ Erro ao registrar ponto: ${e.message}")
                                            // ✅ CORREÇÃO: Não deixar crashar, apenas logar o erro
                                            withContext(Dispatchers.Main) {
                                                if (!isFinishing && !isDestroyed) {
                                                    Toast.makeText(this@PontoActivity, "Erro ao registrar ponto. Tente novamente.", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Log.e(TAG, "❌ Erro crítico no registro: ${e.message}")
                                        // ✅ CORREÇÃO: Não deixar crashar
                                    }
                                }
                                
                            } else {
                                // ✅ CORREÇÃO: Quando não reconhece, apenas continuar sem crashar
                                Log.w(TAG, "❌ Nenhum funcionário reconhecido")
                                processandoFace = false
                                retomarTimeout("Nenhum funcionário reconhecido")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Erro no processamento do resultado: ${e.message}")
                        // ✅ CORREÇÃO: Não deixar crashar, apenas resetar estado
                        processandoFace = false
                        retomarTimeout("Erro no processamento")
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        statusText.text = "❌ Modelo não carregado"
                        processandoFace = false
                        lastProcessingTime = 0L
                        retomarTimeout("Modelo não carregado")
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro no processamento: ${e.message}")
                
                // ✅ OTIMIZAÇÃO: Tratamento de erro simplificado
                try {
                    withContext(Dispatchers.Main) {
                        if (!isFinishing && !isDestroyed && ::statusText.isInitialized) {
                            val errorMsg = when {
                                e.message?.contains("toBitmap") == true -> "❌ Erro na imagem"
                                e.message?.contains("cropFace") == true -> "❌ Erro no recorte"
                                e.message?.contains("model") == true -> "❌ Erro no modelo"
                                e.message?.contains("reciclado") == true -> "❌ Imagem inválida"
                                else -> "❌ Erro no reconhecimento"
                            }
                            
                            try {
                                statusText.text = errorMsg
                                
                                // Reset após 3 segundos
                                statusText.postDelayed({
                                    try {
                                        if (!isFinishing && !isDestroyed && ::statusText.isInitialized) {
                                            statusText.text = "📷 Posicione seu rosto na câmera"
                                        }
                                    } catch (e2: Exception) {
                                        Log.e(TAG, "❌ Erro no reset UI: ${e2.message}")
                                    }
                                }, 3000)
                            } catch (e2: Exception) {
                                Log.e(TAG, "❌ Erro ao atualizar UI: ${e2.message}")
                            }
                        }
                    }
                } catch (e2: Exception) {
                    Log.e(TAG, "❌ Erro no tratamento de erro: ${e2.message}")
                }
            } finally {
                // ✅ OTIMIZAÇÃO: Always reset processandoFace
                try {
                    processandoFace = false
                    lastProcessingTime = 0L
                    retomarTimeout("Finally block")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Erro ao resetar no finally: ${e.message}")
                }
            }
        }
    }

    private suspend fun mostrarFuncionarioReconhecido(funcionario: FuncionariosEntity) {
        try {
            Log.d(TAG, "✅ Registrando ponto para: ${funcionario.nome}")
            
            // ✅ CORREÇÃO: Verificar se o funcionário é válido
            if (funcionario.codigo.isBlank()) {
                throw IllegalArgumentException("Código do funcionário está vazio")
            }
            
            funcionarioReconhecido = funcionario
            
            // Registrar ponto automaticamente
            val horarioAtual = System.currentTimeMillis()
            val formato = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
            val dataFormatada = formato.format(Date(horarioAtual))
            
            // Registrar ponto simples sem classificação ENTRADA/SAÍDA
            val tipoPonto = "PONTO"
            
            // ✅ CORREÇÃO: NÃO salvar foto para evitar problemas
            val fotoBase64 = null // Não salvar foto por enquanto
            
            // ✅ CORREÇÃO: Criar ponto com proteção
            val ponto = try {
                PontosGenericosEntity(
                    funcionarioId = funcionario.codigo,
                    funcionarioNome = funcionario.nome ?: "Funcionário",
                    funcionarioMatricula = funcionario.matricula ?: "",
                    funcionarioCpf = funcionario.cpf ?: "",
                    funcionarioCargo = funcionario.cargo ?: "",
                    funcionarioSecretaria = funcionario.secretaria ?: "",
                    funcionarioLotacao = funcionario.lotacao ?: "",
                    tipoPonto = tipoPonto,
                    dataHora = horarioAtual,
                    fotoBase64 = fotoBase64
                )
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao criar entidade de ponto: ${e.message}")
                throw e
            }
            
            // ✅ CORREÇÃO: Inserir no banco com proteção
            try {
                AppDatabase.getInstance(this)
                    .pontosGenericosDao()
                    .insert(ponto)
                Log.d(TAG, "💾 Ponto registrado: ${funcionario.nome} - $dataFormatada")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao inserir ponto no banco: ${e.message}")
                throw e
            }
            
            // ✅ CORREÇÃO: Salvar ponto para sincronização com proteção
            try {
                val pontoService = PontoSincronizacaoService()
                pontoService.salvarPontoParaSincronizacao(
                    this,
                    funcionario.codigo,
                    funcionario.nome ?: "Funcionário",
                    tipoPonto.lowercase(),
                    fotoBase64,
                    null, // ✅ NOVA: Sem latitude nesta função (legacy)
                    null  // ✅ NOVA: Sem longitude nesta função (legacy)
                )
                Log.d(TAG, "✅ Ponto salvo para sincronização")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao salvar ponto para sincronização: ${e.message}")
                // Não rethrow - sincronização não é crítica
            }
            
            // ✅ CORREÇÃO: Mostrar confirmação simples e fechar IMEDIATAMENTE
            withContext(Dispatchers.Main) {
                try {
                    // ✅ SOLUÇÃO DEFINITIVA: Toast simples + fechamento imediato
                    Log.d(TAG, "✅ Ponto salvo com sucesso para ${funcionario.nome}")
                    Toast.makeText(this@PontoActivity, 
                        "✅ Ponto registrado!\n${funcionario.nome}\n$dataFormatada", 
                        Toast.LENGTH_LONG).show()
                    
                    // ✅ CORREÇÃO: Fechar IMEDIATAMENTE após 2 segundos
                    Handler(Looper.getMainLooper()).postDelayed({
                        try {
                            Log.d(TAG, "🔚 Fechando activity após ponto registrado")
                            if (!isFinishing && !isDestroyed) {
                                finish()
                            } else {
                                Log.w(TAG, "⚠️ Activity já finalizada, não fechando")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ Erro ao fechar: ${e.message}")
                            // Força saída do processo se necessário
                            android.os.Process.killProcess(android.os.Process.myPid())
                        }
                    }, 2000)
                    
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Erro ao mostrar toast: ${e.message}")
                    // Fechar imediatamente se der erro
                    try {
                        if (!isFinishing && !isDestroyed) {
                            finish()
                        } else {
                            Log.w(TAG, "⚠️ Activity já finalizada, não fechando")
                        }
                    } catch (e2: Exception) {
                        Log.e(TAG, "❌ Erro crítico: ${e2.message}")
                        android.os.Process.killProcess(android.os.Process.myPid())
                    }
                }
            }
            
            // ✅ CORREÇÃO: NÃO chamar resetarAposPonto aqui - dialog vai gerenciar o fechamento
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro crítico ao registrar ponto: ${e.message}", e)
            
            // ✅ CORREÇÃO: Fallback para evitar crash
            withContext(Dispatchers.Main) {
                try {
                    Toast.makeText(this@PontoActivity, 
                        "❌ Erro ao registrar ponto\nTente novamente", 
                        Toast.LENGTH_LONG).show()
                    statusText.text = "📷 Posicione seu rosto na câmera"
                } catch (e2: Exception) {
                    Log.e(TAG, "❌ Erro no fallback: ${e2.message}")
                }
            }
        } finally {
            processandoFace = false
        }
    }

    private fun mostrarFuncionarioNaoReconhecido() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getInstance(this@PontoActivity)
                val funcionarios = db.usuariosDao().getUsuario()
                
                // Verificar se há pelo menos uma face cadastrada
                var temFacesCadastradas = false
                for (funcionario in funcionarios) {
                    val face = db.faceDao().getByFuncionarioId(funcionario.id.toString())
                    if (face != null) {
                        temFacesCadastradas = true
                        break
                    }
                }
                
                withContext(Dispatchers.Main) {
                    statusText.text = if (funcionarios.isEmpty()) {
                        "❌ Nenhum funcionário cadastrado\nRegistre funcionários primeiro"
                    } else if (!temFacesCadastradas) {
                        "❌ Nenhum rosto cadastrado\nRegistre rostos via menu principal"
                    } else {
                        "❌ Funcionário não reconhecido\nTente novamente ou cadastre novo rosto"
                    }
                    
                    funcionarioInfo.visibility = View.GONE
                    
                    // Resetar após 5 segundos para dar tempo de ler
                    statusText.postDelayed({
                        statusText.text = "📷 Posicione seu rosto na câmera"
                        processandoFace = false
                    }, 5000)
                }
                
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    statusText.text = "❌ Erro no sistema\nTente novamente"
                    funcionarioInfo.visibility = View.GONE
                    
                    statusText.postDelayed({
                        statusText.text = "📷 Posicione seu rosto na câmera"
                        processandoFace = false
                    }, 3000)
                }
            }
        }
    }



    private fun mostrarConfirmacaoPonto(funcionario: FuncionariosEntity, tipoPonto: String, horario: String) {
        try {
            Log.d(TAG, "🎉 Mostrando confirmação de ponto para ${funcionario.nome}")
            
            // ✅ CORREÇÃO: Verificar se a activity ainda é válida
            if (isFinishing || isDestroyed) {
                Log.w(TAG, "⚠️ Activity finalizada, cancelando confirmação")
                return
            }
            
            // ✅ NOVA ABORDAGEM: Toast e manter tela ativa
            Toast.makeText(this, "✅ Ponto registrado!\n${funcionario.nome}\n$horario", Toast.LENGTH_LONG).show()
            
            // ✅ RESETAR ESTADO E MANTER TELA ATIVA
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    Log.d(TAG, "🔄 Resetando estado após registro de ponto")
                    
                    // ✅ CORREÇÃO: Limpar estado sem fechar activity
                    forcarResetEstado()
                    
                    // ✅ MANTER TELA ATIVA - NÃO FECHAR
                    Log.d(TAG, "✅ Tela mantida ativa para novos registros")
                    
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Erro ao resetar estado: ${e.message}")
                }
            }, 2000) // Aguardar 2 segundos
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao mostrar confirmação", e)
            
            // ✅ FALLBACK: Resetar estado mesmo com erro
            try {
                forcarResetEstado()
            } catch (e2: Exception) {
                Log.e(TAG, "❌ Erro crítico ao resetar: ${e2.message}")
            }
        }
    }
    
    private fun cancelarPonto() {
        funcionarioReconhecido = null
        funcionarioInfo.visibility = View.GONE
        statusText.text = "📷 Posicione seu rosto na câmera"
        processandoFace = false
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
    
    private fun getStatusBarHeight(): Int {
        var result = 0
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            result = resources.getDimensionPixelSize(resourceId)
        }
        return result
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
                // ✅ CORREÇÃO: Usar EXATAMENTE a mesma normalização do cadastro
                // Normalizar para [-1, 1] como no CameraActivity (cadastro)
                val r = ((pixel shr 16) and 0xFF) / 127.5f - 1.0f
                val g = ((pixel shr 8) and 0xFF) / 127.5f - 1.0f
                val b = (pixel and 0xFF) / 127.5f - 1.0f
                
                // ✅ VALIDAÇÃO: Garantir que não são NaN ou infinitos
                val rFinal = if (r.isNaN() || r.isInfinite()) 0.0f else r
                val gFinal = if (g.isNaN() || g.isInfinite()) 0.0f else g
                val bFinal = if (b.isNaN() || b.isInfinite()) 0.0f else b

                byteBuffer.putFloat(rFinal)
                byteBuffer.putFloat(gFinal)
                byteBuffer.putFloat(bFinal)
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
    

    
    private fun createTestEmployeeIfNeeded() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getInstance(this@PontoActivity)
                val funcionarioDao = db.usuariosDao()
                
                // Verificar se há funcionários cadastrados
                val funcionarios = funcionarioDao.getUsuario()
                
                if (funcionarios.isEmpty()) {
                    Log.d(TAG, "📝 Criando funcionário de teste...")
                    
                    // Criar funcionário de teste
                    val funcionarioTeste = FuncionariosEntity(
                        id = 1,
                        codigo = "TEST001",
                        nome = "Funcionário Teste",
                        ativo = 1
                    )
                    
                    funcionarioDao.insert(funcionarioTeste)
                    
                    Log.d(TAG, "✅ Funcionário de teste criado: ${funcionarioTeste.nome}")
                    
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@PontoActivity, 
                            "👤 Funcionário de teste criado!\nPrimeiro registre seu rosto via menu principal", 
                            Toast.LENGTH_LONG).show()
                    }
                } else {
                    Log.d(TAG, "👥 ${funcionarios.size} funcionário(s) já cadastrado(s)")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao criar funcionário de teste: ${e.message}")
            }
        }
    }

    private suspend fun verificarIntegridadeFaces() {
        try {
            Log.d(TAG, "🔍 Verificando integridade das faces...")
            
            // Verificar integridade
            faceRecognitionHelper?.verificarIntegridadeFaces()
            
            // Limpar faces duplicadas
            faceRecognitionHelper?.limparFacesDuplicadas()
            
            Log.d(TAG, "✅ Verificação de integridade concluída")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro na verificação de integridade: ${e.message}", e)
        }
    }
    
    /**
     * ✅ NOVA FUNÇÃO: Verifica e corrige problemas de reconhecimento
     */
    private suspend fun verificarECorrigirProblemasReconhecimento() {
        try {
            Log.d(TAG, "🔧 Verificando e corrigindo problemas de reconhecimento...")
            
            // Executar verificação completa
            faceRecognitionHelper?.verificarECorrigirProblemasReconhecimento()
            
            // Listar problemas encontrados
            val problemas = faceRecognitionHelper?.listarFuncionariosComProblemas()
            if (!problemas.isNullOrEmpty()) {
                Log.w(TAG, "⚠️ Problemas encontrados:")
                problemas.forEach { problema ->
                    Log.w(TAG, "   $problema")
                }
            } else {
                Log.d(TAG, "✅ Nenhum problema encontrado")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao verificar problemas: ${e.message}")
        }
    }
    
    /**
     * ✅ NOVA FUNÇÃO: Resetar estado de reconhecimento
     */
    private fun resetarEstadoReconhecimento() {
        try {
            Log.d(TAG, "🔄 === RESETANDO ESTADO DE RECONHECIMENTO ===")
            
            // ✅ CORREÇÃO: Verificar se activity ainda é válida
            if (isFinishing || isDestroyed) {
                Log.w(TAG, "⚠️ Activity finalizada, cancelando reset")
                return
            }
            
            funcionarioReconhecido = null
            processandoFace = false
            lastProcessingTime = 0L // ✅ NOVA: Resetar tempo de processamento
            pontoJaRegistrado = false // ✅ NOVA: Permitir novos registros
            ultimoFuncionarioReconhecido = null // ✅ NOVA: Limpar último funcionário
            currentFaceBitmap = null
            
            // ✅ CORREÇÃO: Verificar se as views estão inicializadas E activity válida
            try {
                if (::statusText.isInitialized && !isFinishing && !isDestroyed) {
                    statusText.text = "📷 Posicione seu rosto na câmera"
                } else {
                    Log.w(TAG, "⚠️ statusText não disponível para reset")
                }
                
                if (::funcionarioInfo.isInitialized && !isFinishing && !isDestroyed) {
                    funcionarioInfo.visibility = View.GONE
                } else {
                    Log.w(TAG, "⚠️ funcionarioInfo não disponível para reset")
                }
                
                // ✅ CORREÇÃO: Limpar overlay também
                if (::overlay.isInitialized && !isFinishing && !isDestroyed) {
                    overlay.clear()
                }
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Erro ao resetar views: ${e.message}")
            }
            
            Log.d(TAG, "✅ Estado resetado com sucesso")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro crítico no reset: ${e.message}", e)
        }
    }
    
    /**
     * ✅ NOVA FUNÇÃO: Forçar reset do estado (para casos de emergência)
     */
    /**
     * ✅ NOVA FUNÇÃO: Pausar timeout durante processamentos importantes
     */
    private fun pausarTimeout(motivo: String) {
        timeoutPausado = true
        Log.d(TAG, "⏸️ Timeout pausado: $motivo")
    }
    
    /**
     * ✅ NOVA FUNÇÃO: Retomar timeout
     */
    private fun retomarTimeout(motivo: String) {
        timeoutPausado = false
        lastFaceDetectionTime = System.currentTimeMillis() // Reset do timer de face
        Log.d(TAG, "▶️ Timeout retomado: $motivo")
    }

    /**
     * ✅ NOVA FUNÇÃO: Voltar para tela inicial de forma segura
     */
    private fun voltarParaTelaInicial(motivo: String) {
        try {
            Log.w(TAG, "🏠 === VOLTANDO PARA TELA INICIAL ===")
            Log.w(TAG, "📋 Motivo: $motivo")
            
            // Mostrar mensagem para o usuário
            runOnUiThread {
                try {
                    if (!isFinishing && !isDestroyed) {
                        Toast.makeText(this, "Retornando à tela inicial: $motivo", Toast.LENGTH_LONG).show()
                        
                        // Limpar estado
                        forcarResetEstado()
                        
                        // Voltar para HomeActivity de forma mais segura
                        val intent = Intent(this, HomeActivity::class.java)
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        startActivity(intent)
                        finish()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Erro ao voltar para tela inicial: ${e.message}")
                    // Em caso de erro, tentar finalizar a activity
                    try {
                        finish()
                    } catch (e2: Exception) {
                        Log.e(TAG, "❌ Erro crítico ao finalizar activity: ${e2.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro crítico ao voltar para tela inicial: ${e.message}")
        }
    }

    private fun forcarResetEstado() {
        try {
            Log.d(TAG, "🚨 === FORÇANDO RESET DO ESTADO ===")
            processandoFace = false
            lastProcessingTime = 0L // ✅ NOVA: Resetar tempo de processamento
            pontoJaRegistrado = false // ✅ NOVA: Permitir novos registros
            ultimoFuncionarioReconhecido = null // ✅ NOVA: Limpar último funcionário
            funcionarioReconhecido = null
            
            // ✅ CORREÇÃO: Limpar bitmap de forma segura
            currentFaceBitmap?.let { bitmap ->
                try {
                    if (!bitmap.isRecycled) {
                        bitmap.recycle()
                    } else {
                        // Bitmap já foi reciclado
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ Erro ao reciclar bitmap: ${e.message}")
                }
            }
            currentFaceBitmap = null
            
            // ✅ NOVO: Resetar tempos de timeout e retomar
            lastFaceDetectionTime = System.currentTimeMillis()
            retomarTimeout("Reset de estado")
            
            // Resetar UI
            try {
                if (::statusText.isInitialized && !isFinishing && !isDestroyed) {
                    statusText.text = "📷 Posicione seu rosto na câmera"
                }
                if (::funcionarioInfo.isInitialized && !isFinishing && !isDestroyed) {
                    funcionarioInfo.visibility = View.GONE
                }
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Erro ao resetar UI: ${e.message}")
            }
            
            Log.d(TAG, "✅ Reset forçado concluído")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro no reset forçado: ${e.message}")
        }
    }
    
    /**
     * ✅ FUNÇÃO REMOVIDA: resetarAposPonto causava conflito
     * Agora o dialog gerencia o fechamento da activity diretamente
     */

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }
    
    // ✅ NOVA: Verificar permissões de localização
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
                    // ✅ NOVA: Verificar permissões de localização também
                    if (!allLocationPermissionsGranted()) {
                        ActivityCompat.requestPermissions(this, LOCATION_PERMISSIONS, REQUEST_CODE_LOCATION_PERMISSIONS)
                    }
                    startCamera()
                } else {
                    Toast.makeText(this, "Permissões de câmera necessárias", Toast.LENGTH_LONG).show()
                    finish()
                }
            }
            REQUEST_CODE_LOCATION_PERMISSIONS -> {
                if (allLocationPermissionsGranted()) {
                    Log.d(TAG, "✅ Permissões de localização concedidas")
                    Toast.makeText(this, "✅ Localização habilitada para pontos", Toast.LENGTH_SHORT).show()
                } else {
                    Log.w(TAG, "⚠️ Permissões de localização negadas - pontos serão registrados sem coordenadas")
                    Toast.makeText(this, "⚠️ Localização negada - pontos sem coordenadas", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            Log.d(TAG, "🗑️ === LIMPANDO RECURSOS NO onDestroy ===")
            
            // ✅ OTIMIZAÇÃO: Parar monitor de estado para evitar memory leaks
            stopStateMonitor()
            
            // ✅ OTIMIZAÇÃO: Parar câmera para evitar memory leaks
            stopCamera()
            
            // ✅ OTIMIZAÇÃO: Limpar bitmap atual de forma mais segura
            currentFaceBitmap?.let { bitmap ->
                try {
                    if (!bitmap.isRecycled) {
                        bitmap.recycle()
                        Log.d(TAG, "🖼️ Bitmap reciclado")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ Erro ao reciclar bitmap no onDestroy: ${e.message}")
                }
            }
            currentFaceBitmap = null
            
            // ✅ OTIMIZAÇÃO: Limpar cache do helper para liberar memória
            try {
                faceRecognitionHelper?.clearCache()
                faceRecognitionHelper = null
                Log.d(TAG, "🧠 Cache do face recognition limpo")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao limpar cache: ${e.message}")
            }
            
            // ✅ OTIMIZAÇÃO: Limpar helper de localização
            locationHelper = null
            
            // ✅ OTIMIZAÇÃO: Limpar interpreter de forma mais segura
            try {
                if (interpreter != null) {
                    interpreter?.close()
                    interpreter = null
                    Log.d(TAG, "🤖 TensorFlow interpreter fechado")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao fechar interpreter: ${e.message}")
            }
            
            // ✅ OTIMIZAÇÃO: Limpar face detector de forma mais segura
            try {
                faceDetector.close()
                Log.d(TAG, "👤 Face detector fechado")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao fechar face detector: ${e.message}")
            }
            
            // ✅ OTIMIZAÇÃO: Forçar garbage collection e monitorar memória final
            logMemoryUsage("Antes limpeza final")
            System.gc()
            
            // Aguardar um pouco e verificar novamente
            Handler(Looper.getMainLooper()).postDelayed({
                logMemoryUsage("Após limpeza final")
            }, 100)
            
            Log.d(TAG, "✅ Todos os recursos liberados no onDestroy")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao fechar recursos: ${e.message}")
            e.printStackTrace()
        }
    }
    
    override fun onPause() {
        super.onPause()
        Log.d(TAG, "📱 onPause - pausando câmera para economizar recursos")
        
        try {
            // ✅ CORREÇÃO: Parar câmera para economizar memória e recursos
            stopCamera()
            
            // ✅ NOVA: Pausar timeout para evitar fechamento durante pausa
            pausarTimeout("Activity pausada")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro no onPause: ${e.message}")
        }
    }
    
    override fun onResume() {
        super.onResume()
        Log.d(TAG, "📱 onResume - retomando atividade")
        
        try {
            // ✅ CORREÇÃO: Reset imediato se processandoFace está travado
            if (processandoFace) {
                Log.w(TAG, "⚠️ processandoFace travado no onResume, resetando imediatamente")
                forcarResetEstado()
            }
            
            // ✅ NOVA: Retomar timeout
            retomarTimeout("Activity retomada")
            
            // ✅ CORREÇÃO: Reiniciar câmera se permissões estão ok
            if (allPermissionsGranted()) {
                startCamera()
            }
            
            // ✅ CORREÇÃO: Aguardar um pouco antes de resetar para evitar conflitos
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    forcarResetEstado()
                    Log.d(TAG, "📊 Estado no onResume: processandoFace = $processandoFace")
                    
                    // ✅ CORREÇÃO: Verificar se o modelo está carregado
                    if (!modelLoaded) {
                        Log.w(TAG, "⚠️ Modelo não carregado no onResume, recarregando...")
                        loadTensorFlowModel()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Erro no reset no onResume: ${e.message}")
                }
            }, 500) // Aguardar 500ms
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro no onResume: ${e.message}")
        }
    }

    /**
     * ✅ FUNÇÃO SIMPLIFICADA: Registrar ponto com tratamento de erro
     */
    private suspend fun registrarPonto(funcionario: FuncionariosEntity) {
        try {
            val horarioAtual = System.currentTimeMillis()
            val formato = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
            val dataFormatada = formato.format(Date(horarioAtual))
            
            // ✅ Capturar geolocalização
            var latitude: Double? = null
            var longitude: Double? = null
            
            try {
                val locationData = locationHelper?.getCurrentLocationForPoint()
                if (locationData != null) {
                    latitude = locationData.latitude
                    longitude = locationData.longitude
                    Log.d(TAG, "🌍 Localização capturada: $latitude, $longitude")
                } else {
                    Log.w(TAG, "⚠️ Localização não disponível para este ponto")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao capturar localização: ${e.message}")
            }
            
            // ✅ Converter foto para base64
            val fotoBase64 = try {
                currentFaceBitmap?.let { bitmap ->
                    if (!bitmap.isRecycled) {
                        bitmapToBase64(bitmap, 80)
                    } else {
                        Log.w(TAG, "⚠️ Bitmap da face foi reciclado")
                        null
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao converter foto para base64: ${e.message}")
                null
            }
            
            if (fotoBase64 != null) {
                Log.d(TAG, "📸 Foto convertida para base64: ${fotoBase64.length} caracteres")
            } else {
                Log.w(TAG, "⚠️ Nenhuma foto será enviada com o ponto")
            }
            
            // Criar ponto
            val ponto = PontosGenericosEntity(
                funcionarioId = funcionario.codigo,
                funcionarioNome = funcionario.nome ?: "Funcionário",
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
            
            // Salvar no banco
            AppDatabase.getInstance(this@PontoActivity).pontosGenericosDao().insert(ponto)
            Log.d(TAG, "💾 Ponto registrado: ${funcionario.nome}")
            
            // Salvar para sincronização
            try {
                val pontoService = PontoSincronizacaoService()
                pontoService.salvarPontoParaSincronizacao(
                    this@PontoActivity,
                    funcionario.codigo,
                    funcionario.nome ?: "Funcionário",
                    "ponto",
                    fotoBase64,
                    latitude,
                    longitude
                )
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro na sincronização: ${e.message}")
            }
            
            // ✅ Mostrar sucesso
            withContext(Dispatchers.Main) {
                try {
                    val toastLocationText = if (latitude != null && longitude != null) {
                        "\n📍 ${String.format("%.4f", latitude)}, ${String.format("%.4f", longitude)}"
                    } else {
                        ""
                    }
                    
                    Toast.makeText(this@PontoActivity, 
                        "✅ Ponto registrado!\n${funcionario.nome}\n$dataFormatada$toastLocationText", 
                        Toast.LENGTH_LONG).show()
                    
                    // Reset automático após 20 segundos
                    Handler(Looper.getMainLooper()).postDelayed({
                        pontoJaRegistrado = false
                        ultimoFuncionarioReconhecido = null
                    }, 20000)
                    
                    val locationText = if (latitude != null && longitude != null) {
                        "\n📍 ${String.format("%.6f", latitude)}, ${String.format("%.6f", longitude)}"
                    } else {
                        "\n⚠️ Sem localização"
                    }
                    
                    statusText.text = "✅ Ponto registrado!\n${funcionario.nome}\n$dataFormatada$locationText\n\nClique 'Voltar' para sair"
                    
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Erro ao mostrar sucesso: ${e.message}")
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao registrar ponto: ${e.message}")
            throw e
        } finally {
            processandoFace = false
            lastProcessingTime = 0L
            retomarTimeout("Processamento de ponto concluído")
        }
    }
} 