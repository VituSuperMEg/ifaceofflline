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
    private var funcionarioReconhecido: FuncionariosEntity? = null
    private var processandoFace = false
    private var currentFaceBitmap: Bitmap? = null // Para armazenar a foto da face
    private var lastProcessingTime = 0L // ✅ NOVA: Controle de timeout
    private var processingTimeout = 10000L // ✅ NOVA: 10 segundos de timeout
    private var pontoJaRegistrado = false // ✅ NOVA: Controle para evitar registros duplicados
    private var ultimoFuncionarioReconhecido: String? = null // ✅ NOVA: Controle do último funcionário

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
        private const val TAG = "PontoActivity"
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
        Log.d(TAG, "🚀 === INICIANDO SISTEMA DE PONTO ===")
        Log.d(TAG, "📊 Estado inicial: processandoFace = $processandoFace, lastProcessingTime = $lastProcessingTime")

        // Inicializar helper de reconhecimento facial
        faceRecognitionHelper = com.example.iface_offilne.helpers.FaceRecognitionHelper(this)

        // Carregar modelo TensorFlow
        loadTensorFlowModel()
        
        // Criar funcionário de teste se não existir
        createTestEmployeeIfNeeded()
        
        // Remover backgroundTint do botão voltar
        binding.btnVoltar.backgroundTintList = null

        // Solicitar permissões
        if (allPermissionsGranted()) {
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
        
        // ✅ NOVA FUNÇÃO: Monitor de estado para evitar travamento
        startStateMonitor()
    }
    
    /**
     * ✅ NOVA FUNÇÃO: Monitor de estado para evitar travamento
     */
    private fun startStateMonitor() {
        Handler(Looper.getMainLooper()).postDelayed(object : Runnable {
            override fun run() {
                try {
                    // Verificar se o estado está travado
                    if (processandoFace) {
                        Log.d(TAG, "🔍 Monitor: processandoFace = true, verificando se não travou...")
                        
                        val currentTime = System.currentTimeMillis()
                        val timeSinceStart = currentTime - lastProcessingTime
                        
                        // Se estiver processando há muito tempo sem resultado, resetar
                        if (timeSinceStart > processingTimeout) {
                            Log.w(TAG, "⚠️ Monitor: Estado travado há ${timeSinceStart}ms, resetando...")
                            forcarResetEstado()
                        } else if (funcionarioReconhecido == null && timeSinceStart > 5000) {
                            Log.w(TAG, "⚠️ Monitor: Processando há ${timeSinceStart}ms sem resultado, resetando...")
                            forcarResetEstado()
                        }
                    }
                    
                    // Verificar se o modelo está carregado
                    if (!modelLoaded) {
                        Log.w(TAG, "⚠️ Monitor: Modelo não carregado, tentando recarregar...")
                        loadTensorFlowModel()
                    }
                    
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Erro no monitor de estado: ${e.message}")
                }
                
                // Agendar próxima verificação em 3 segundos (mais frequente)
                if (!isFinishing && !isDestroyed) {
                    Handler(Looper.getMainLooper()).postDelayed(this, 3000)
                }
            }
        }, 3000) // Primeira verificação em 3 segundos
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
                // ✅ CORREÇÃO: Reset manual do processandoFace
                Log.d(TAG, "🔄 Reset manual do processandoFace")
                forcarResetEstado()
                
                // Redirecionar para configurações
                val intent = Intent(this, ConfiguracoesActivity::class.java)
                startActivity(intent)
            }
            
            // ✅ NOVA: Botão de reset de emergência (long press no botão voltar)
            binding.btnVoltar.setOnLongClickListener {
                Log.d(TAG, "🚨 Reset de emergência ativado")
                forcarResetEstado()
                Toast.makeText(this, "🔄 Sistema resetado", Toast.LENGTH_SHORT).show()
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
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(ContextCompat.getMainExecutor(this)) { imageProxy ->
                        // ✅ CORREÇÃO: SEMPRE processar imagem, deixar a lógica interna decidir
                        processImage(imageProxy)
                    }
                }

            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer)
                Log.d(TAG, "✅ Câmera iniciada com sucesso")
            } catch (exc: Exception) {
                Log.e(TAG, "❌ Falha ao iniciar câmera", exc)
            }

        }, ContextCompat.getMainExecutor(this))
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
                                
                                // ✅ CORREÇÃO: Atualizar status baseado na posição da face
                                if (!processandoFace) {
                                    if (faceRatio < 0.08f) {
                                        statusText.text = "📷 Aproxime mais seu rosto"
                                    } else if (!overlay.isFaceInOval(face.boundingBox)) {
                                        statusText.text = "📷 Centralize seu rosto no oval"
                                    } else {
                                        statusText.text = "🔍 Pronto para reconhecer..."
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
        // ✅ SIMPLES: Verificação básica
        if (processandoFace) {
            return
        }
        
        if (isFinishing || isDestroyed) {
            return
        }
        
        processandoFace = true
        lastProcessingTime = System.currentTimeMillis()
        
        Log.d(TAG, "🔄 INICIANDO PROCESSAMENTO SIMPLES")
        
        // ✅ SIMPLES: Reset automático em 5 segundos
        Handler(Looper.getMainLooper()).postDelayed({
            if (processandoFace) {
                Log.w(TAG, "⚠️ RESET POR TIMEOUT")
                processandoFace = false
                lastProcessingTime = 0L
                try {
                    if (::statusText.isInitialized && !isFinishing) {
                        statusText.text = "📷 Posicione seu rosto na câmera"
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Erro no reset: ${e.message}")
                }
            }
        }, 5000)
        
        // ✅ SIMPLES: Processar em background
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Status simples
                withContext(Dispatchers.Main) {
                    try {
                        if (::statusText.isInitialized && !isFinishing) {
                            statusText.text = "🔍 Processando..."
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Erro no status: ${e.message}")
                    }
                }

                // Verificações básicas
                if (!modelLoaded || interpreter == null || faceRecognitionHelper == null) {
                    throw Exception("Sistema não carregado")
                }

                // Processar face
                val faceBmp = cropFace(bitmap, boundingBox)
                val resized = Bitmap.createScaledBitmap(faceBmp, modelInputWidth, modelInputHeight, true)
                
                // Converter para tensor
                val inputTensor = convertBitmapToTensorInput(resized)
                val output = Array(1) { FloatArray(modelOutputSize) }
                
                // ✅ SIMPLES: Executar modelo diretamente
                interpreter?.run(inputTensor, output)
                val vetorFacial = output[0]
                
                Log.d(TAG, "✅ Vetor gerado: ${vetorFacial.size}")

                // ✅ SIMPLES: Reconhecer funcionário
                val funcionario = faceRecognitionHelper?.recognizeFace(vetorFacial)
                
                Log.d(TAG, "🔍 Resultado: ${funcionario?.nome ?: "não reconhecido"}")
                
                // ✅ SIMPLES: Processar resultado
                withContext(Dispatchers.Main) {
                    try {
                        if (funcionario != null) {
                            Log.d(TAG, "✅ RECONHECIDO: ${funcionario.nome}")
                            
                            // Verificar duplicata simples
                            if (pontoJaRegistrado) {
                                Log.w(TAG, "⚠️ Já registrado, ignorando")
                                processandoFace = false
                                return@withContext
                            }
                            
                            pontoJaRegistrado = true
                            
                            // ✅ SIMPLES: Registrar ponto diretamente
                            CoroutineScope(Dispatchers.IO).launch {
                                try {
                                    val horario = System.currentTimeMillis()
                                    val formato = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
                                    val dataFormatada = formato.format(Date(horario))
                                    
                                    val ponto = PontosGenericosEntity(
                                        funcionarioId = funcionario.codigo,
                                        funcionarioNome = funcionario.nome ?: "Funcionário",
                                        funcionarioMatricula = funcionario.matricula ?: "",
                                        funcionarioCpf = funcionario.cpf ?: "",
                                        funcionarioCargo = funcionario.cargo ?: "",
                                        funcionarioSecretaria = funcionario.secretaria ?: "",
                                        funcionarioLotacao = funcionario.lotacao ?: "",
                                        tipoPonto = "PONTO",
                                        dataHora = horario,
                                        fotoBase64 = null
                                    )
                                    
                                    AppDatabase.getInstance(this@PontoActivity).pontosGenericosDao().insert(ponto)
                                    
                                    withContext(Dispatchers.Main) {
                                        try {
                                            Toast.makeText(this@PontoActivity, 
                                                "✅ Ponto registrado!\n${funcionario.nome}\n$dataFormatada", 
                                                Toast.LENGTH_LONG).show()
                                            
                                            // Reset em 15 segundos
                                            Handler(Looper.getMainLooper()).postDelayed({
                                                pontoJaRegistrado = false
                                            }, 15000)
                                            
                                            // Fechar em 3 segundos
                                            Handler(Looper.getMainLooper()).postDelayed({
                                                if (!isFinishing) {
                                                    finish()
                                                }
                                            }, 3000)
                                            
                                        } catch (e: Exception) {
                                            Log.e(TAG, "Erro no toast: ${e.message}")
                                            if (!isFinishing) {
                                                finish()
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Erro ao salvar: ${e.message}")
                                    pontoJaRegistrado = false
                                    
                                    withContext(Dispatchers.Main) {
                                        try {
                                            Toast.makeText(this@PontoActivity, "❌ Erro ao registrar", Toast.LENGTH_SHORT).show()
                                            statusText.text = "📷 Posicione seu rosto na câmera"
                                        } catch (e2: Exception) {
                                            Log.e(TAG, "Erro no fallback: ${e2.message}")
                                        }
                                    }
                                } finally {
                                    processandoFace = false
                                    lastProcessingTime = 0L
                                }
                            }
                        } else {
                            Log.d(TAG, "❌ NÃO RECONHECIDO")
                            statusText.text = "❌ Não reconhecido"
                            
                            // Reset em 2 segundos
                            Handler(Looper.getMainLooper()).postDelayed({
                                try {
                                    if (::statusText.isInitialized && !isFinishing) {
                                        statusText.text = "📷 Posicione seu rosto na câmera"
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Erro no reset status: ${e.message}")
                                }
                            }, 2000)
                            
                            processandoFace = false
                            lastProcessingTime = 0L
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Erro na UI: ${e.message}")
                        processandoFace = false
                        lastProcessingTime = 0L
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ ERRO GERAL: ${e.message}")
                
                withContext(Dispatchers.Main) {
                    try {
                        if (::statusText.isInitialized && !isFinishing) {
                            statusText.text = "❌ Erro - Tente novamente"
                            
                            Handler(Looper.getMainLooper()).postDelayed({
                                try {
                                    if (::statusText.isInitialized && !isFinishing) {
                                        statusText.text = "📷 Posicione seu rosto na câmera"
                                    }
                                } catch (e2: Exception) {
                                    Log.e(TAG, "Erro no reset final: ${e2.message}")
                                }
                            }, 3000)
                        }
                    } catch (e2: Exception) {
                        Log.e(TAG, "Erro na UI final: ${e2.message}")
                    }
                }
                
                processandoFace = false
                lastProcessingTime = 0L
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
                    fotoBase64
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
            
            // ✅ ABORDAGEM MAIS SIMPLES: Usar AlertDialog que é mais estável
            try {
                val builder = AlertDialog.Builder(this)
                builder.setTitle("✅ Ponto Registrado!")
                builder.setMessage("👤 ${funcionario.nome}\n🕐 $horario\n\n✅ Ponto salvo com sucesso!")
                builder.setIcon(android.R.drawable.ic_dialog_info)
                builder.setCancelable(false)
                
                builder.setPositiveButton("OK") { dialog, _ ->
                    try {
                        Log.d(TAG, "👆 Confirmação OK pressionada - fechando app")
                        dialog.dismiss()
                        // ✅ CORREÇÃO: Fechar imediatamente sem delay
                        try {
                            if (!isFinishing && !isDestroyed) {
                                Log.d(TAG, "🔚 Finalizando activity após confirmação")
                                finish()
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ Erro ao fechar activity: ${e.message}")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Erro no botão OK: ${e.message}")
                        try {
                            if (!isFinishing && !isDestroyed) {
                                finish()
                            }
                        } catch (e2: Exception) {
                            Log.e(TAG, "❌ Erro crítico ao fechar: ${e2.message}")
                        }
                    }
                }
                
                // ✅ CORREÇÃO: Verificar novamente antes de criar o dialog
                if (!isFinishing && !isDestroyed) {
                    val dialog = builder.create()
                    dialog.show()
                    
                    // Auto-fechar após 5 segundos como backup
                    Handler(Looper.getMainLooper()).postDelayed({
                        try {
                            if (dialog.isShowing && !isFinishing && !isDestroyed) {
                                Log.d(TAG, "⏰ Auto-fechando dialog e app após 5 segundos")
                                dialog.dismiss()
                                // ✅ CORREÇÃO: Fechar imediatamente sem delay adicional
                                try {
                                    if (!isFinishing && !isDestroyed) {
                                        Log.d(TAG, "🔚 Finalizando activity automaticamente")
                                        finish()
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "❌ Erro no auto-close finish: ${e.message}")
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ Erro no auto-close dialog: ${e.message}")
                        }
                    }, 5000)
                    
                    Log.d(TAG, "✅ Dialog de confirmação exibido com sucesso")
                } else {
                    Log.w(TAG, "⚠️ Activity finalizada antes de mostrar dialog")
                    throw IllegalStateException("Activity finalizada")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao criar dialog: ${e.message}")
                throw e
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao mostrar confirmação", e)
            
            // ✅ FALLBACK: Toast simples e fechar imediatamente
            try {
                if (!isFinishing && !isDestroyed) {
                    Toast.makeText(this, "✅ Ponto registrado!\n${funcionario.nome}\n$horario", Toast.LENGTH_SHORT).show()
                    
                    // ✅ CORREÇÃO: Fechar imediatamente após toast
                    Log.d(TAG, "🔚 Finalizando activity via fallback")
                    finish()
                } else {
                    Log.w(TAG, "⚠️ Activity finalizada, não exibindo fallback")
                }
            } catch (e2: Exception) {
                Log.e(TAG, "❌ Erro no fallback: ${e2.message}")
                // Último recurso: apenas fechar
                try {
                    if (!isFinishing && !isDestroyed) {
                        finish()
                    }
                } catch (e3: Exception) {
                    Log.e(TAG, "❌ Erro crítico final: ${e3.message}")
                }
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
            // ✅ OTIMIZAÇÃO: Verificações mínimas e rápidas
            if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) {
                throw IllegalStateException("Bitmap inválido")
            }
            
            val inputSize = modelInputWidth // 112
            val bufferSize = 4 * inputSize * inputSize * 3
            val byteBuffer = ByteBuffer.allocateDirect(bufferSize)
            byteBuffer.order(ByteOrder.nativeOrder())

            // ✅ OTIMIZAÇÃO: Redimensionamento mais eficiente
            val resizedBitmap = if (bitmap.width != inputSize || bitmap.height != inputSize) {
                Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, false) // false = mais rápido
            } else {
                bitmap
            }
            
            // ✅ OTIMIZAÇÃO: Verificação simples
            if (resizedBitmap.isRecycled) {
                throw IllegalStateException("Redimensionamento falhou")
            }
            
            val intValues = IntArray(inputSize * inputSize)
            resizedBitmap.getPixels(intValues, 0, inputSize, 0, 0, inputSize, inputSize)

            // ✅ OTIMIZAÇÃO: Processamento de pixels mais rápido
            for (pixel in intValues) {
                // Normalizar para [-1, 1] de forma mais eficiente
                val r = ((pixel shr 16) and 0xFF) / 127.5f - 1.0f
                val g = ((pixel shr 8) and 0xFF) / 127.5f - 1.0f
                val b = (pixel and 0xFF) / 127.5f - 1.0f

                byteBuffer.putFloat(r)
                byteBuffer.putFloat(g)
                byteBuffer.putFloat(b)
            }
            
            // ✅ OTIMIZAÇÃO: Limpeza eficiente
            if (resizedBitmap != bitmap && !resizedBitmap.isRecycled) {
                resizedBitmap.recycle()
            }

            return byteBuffer
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro na conversão: ${e.message}")
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
    private fun forcarResetEstado() {
        try {
            Log.d(TAG, "🚨 === FORÇANDO RESET DO ESTADO ===")
            processandoFace = false
            lastProcessingTime = 0L // ✅ NOVA: Resetar tempo de processamento
            pontoJaRegistrado = false // ✅ NOVA: Permitir novos registros
            ultimoFuncionarioReconhecido = null // ✅ NOVA: Limpar último funcionário
            funcionarioReconhecido = null
            currentFaceBitmap = null
            
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
                Toast.makeText(this, "Permissões de câmera necessárias", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            interpreter?.close()
            interpreter = null
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao fechar interpreter: ${e.message}")
        }
    }
    
    override fun onPause() {
        super.onPause()
        // ✅ CORREÇÃO: NÃO pausar processamento para evitar interrupção
        Log.d(TAG, "📱 onPause - mantendo processamento ativo")
    }
    
    override fun onResume() {
        super.onResume()
        // ✅ CORREÇÃO: Resetar estado completo quando activity volta
        Log.d(TAG, "📱 onResume - resetando estado completo")
        
        // ✅ CORREÇÃO: Reset imediato se processandoFace está travado
        if (processandoFace) {
            Log.w(TAG, "⚠️ processandoFace travado no onResume, resetando imediatamente")
            forcarResetEstado()
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
                Log.e(TAG, "❌ Erro no onResume: ${e.message}")
            }
        }, 500) // Aguardar 500ms
    }
} 