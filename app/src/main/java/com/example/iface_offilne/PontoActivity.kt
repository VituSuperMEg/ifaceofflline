package com.example.iface_offilne

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.*
import android.os.Bundle
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
import com.example.iface_offilne.helpers.FaceRecognitionHelper
import com.example.iface_offilne.helpers.bitmapToFloatArray
import com.example.iface_offilne.helpers.cropFace
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
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.text.SimpleDateFormat
import java.util.*

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
    private var modelInputWidth = 112
    private var modelInputHeight = 112
    private var modelOutputSize = 192

    private var faceRecognitionHelper: com.example.iface_offilne.helpers.FaceRecognitionHelper? = null
    private var funcionarioReconhecido: FuncionariosEntity? = null
    private var processandoFace = false

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
        
        // Resetar variáveis
        funcionarioReconhecido = null
        processandoFace = false
        
        // Configurar para uso completo da tela
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = ContextCompat.getColor(this, android.R.color.transparent)
            window.navigationBarColor = ContextCompat.getColor(this, android.R.color.transparent)
        }
        
        setupUI()
        Log.d(TAG, "🚀 === INICIANDO SISTEMA DE PONTO ===")

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
            binding.btnVoltar.setOnClickListener { finish() }
            
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
            
            // Tentar carregar primeiro dos assets
            val modelFile = try {
                assets.open("facenet_model.tflite")
            } catch (e: Exception) {
                Log.w(TAG, "facenet_model.tflite não encontrado em assets, tentando mobilefacenet...")
                // Fallback para o modelo raw
                resources.openRawResource(R.raw.mobilefacenet)
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
            
            val options = Interpreter.Options().apply {
                setNumThreads(2)
                setUseNNAPI(false)
            }
            
            interpreter = Interpreter(modelBuffer, options)
            interpreter?.allocateTensors()
            
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
                        if (!processandoFace) {
                            processImage(imageProxy)
                        } else {
                            imageProxy.close()
                        }
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
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

                        faceDetector.process(image)
                .addOnSuccessListener { faces ->
                    try {
                        if (faces.isNotEmpty()) {
                            val face = faces[0]
                            overlay.setBoundingBox(face.boundingBox, mediaImage.width, mediaImage.height)

                            // Só processa se a face estiver centralizada e não estiver processando
                            if (overlay.isFaceInOval(face.boundingBox) && !processandoFace) {
                                processandoFace = true
                                Log.d(TAG, "👤 Face detectada e centralizada - iniciando reconhecimento")
                                
                                // Converter para bitmap antes de fechar o proxy
                                val bitmap = toBitmap(mediaImage)
                                imageProxy.close()
                                
                                // Processar o bitmap diretamente
                                processDetectedFace(bitmap, face.boundingBox)
                            } else if (!processandoFace) {
                                statusText.text = "📷 Centralize seu rosto no oval"
                                imageProxy.close()
                            } else {
                                imageProxy.close()
                            }
                        } else {
                            overlay.clear()
                            imageProxy.close()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Erro no processamento de faces", e)
                        imageProxy.close()
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "❌ Erro na detecção", e)
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }

    private fun processDetectedFace(bitmap: Bitmap, boundingBox: Rect) {
        // Proteção contra múltiplos processamentos
        if (funcionarioReconhecido != null) {
            Log.d(TAG, "⚠️ Funcionário já foi reconhecido, ignorando processamento...")
            return
        }
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                withContext(Dispatchers.Main) {
                    statusText.text = "🔍 Reconhecendo funcionário..."
                }

                Log.d(TAG, "🔄 === PROCESSANDO FACE PARA RECONHECIMENTO ===")

                // Bitmap já está disponível
                Log.d(TAG, "✅ Bitmap recebido: ${bitmap.width}x${bitmap.height}")

                // Recortar face
                Log.d(TAG, "✂️ Recortando face com boundingBox: $boundingBox")
                val faceBmp = cropFace(bitmap, boundingBox)
                Log.d(TAG, "✅ Face recortada: ${faceBmp.width}x${faceBmp.height}")

                // Redimensionar
                Log.d(TAG, "🔧 Redimensionando para ${modelInputWidth}x${modelInputHeight}...")
                val resized = Bitmap.createScaledBitmap(faceBmp, modelInputWidth, modelInputHeight, true)
                Log.d(TAG, "✅ Face redimensionada: ${resized.width}x${resized.height}")

                if (modelLoaded && interpreter != null) {
                    Log.d(TAG, "🧠 Processando com modelo de IA...")
                    
                    // Gerar vetor facial usando o formato correto
                    Log.d(TAG, "🔄 Convertendo para formato TensorFlow...")
                    val inputTensor = convertBitmapToTensorInput(resized)
                    Log.d(TAG, "✅ Tensor criado com ${inputTensor.capacity()} bytes")
                    
                    // Verificar se o tensor tem o tamanho correto
                    val expectedSize = 4 * modelInputWidth * modelInputHeight * 3
                    if (inputTensor.capacity() != expectedSize) {
                        throw IllegalStateException("Tensor tem tamanho incorreto: ${inputTensor.capacity()} vs esperado: $expectedSize")
                    }
                    
                    val output = Array(1) { FloatArray(modelOutputSize) }
                    Log.d(TAG, "🔄 Executando inferência do modelo...")
                    interpreter?.run(inputTensor, output)
                    val vetorFacial = output[0]

                    Log.d(TAG, "✅ Vetor facial gerado: tamanho=${vetorFacial.size}")
                    Log.d(TAG, "📊 Primeiros valores: [${vetorFacial.take(5).joinToString(", ")}...]")

                    // Reconhecer funcionário
                    Log.d(TAG, "🔍 Iniciando reconhecimento facial...")
                    val funcionario = faceRecognitionHelper?.recognizeFace(vetorFacial)

                    withContext(Dispatchers.Main) {
                        if (funcionario != null) {
                            mostrarFuncionarioReconhecido(funcionario)
                        } else {
                            mostrarFuncionarioNaoReconhecido()
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        statusText.text = "❌ Modelo não carregado"
                        processandoFace = false
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro no processamento", e)
                e.printStackTrace() // Log completo do erro
                
                withContext(Dispatchers.Main) {
                    val errorMsg = when {
                        e.message?.contains("toBitmap") == true -> "❌ Erro na conversão da imagem"
                        e.message?.contains("cropFace") == true -> "❌ Erro no recorte da face"
                        e.message?.contains("model") == true -> "❌ Erro no modelo de IA"
                        e.message?.contains("ByteBuffer") == true -> "❌ Erro no formato de entrada"
                        else -> "❌ Erro no reconhecimento: ${e.message?.take(50) ?: "Desconhecido"}"
                    }
                    statusText.text = errorMsg
                    processandoFace = false
                    
                    // Resetar após 5 segundos
                    statusText.postDelayed({
                        statusText.text = "📷 Posicione seu rosto na câmera"
                    }, 5000)
                }
            }
        }
    }

    private suspend fun mostrarFuncionarioReconhecido(funcionario: FuncionariosEntity) {
        // Proteção contra múltiplos registros
        if (funcionarioReconhecido != null) {
            Log.d(TAG, "⚠️ Funcionário já foi reconhecido, ignorando...")
            return
        }
        
        Log.d(TAG, "✅ Registrando ponto para: ${funcionario.nome}")
        funcionarioReconhecido = funcionario
        
        // Registrar ponto automaticamente
        val horarioAtual = System.currentTimeMillis()
        val formato = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        val dataFormatada = formato.format(Date(horarioAtual))
        
        // Buscar último ponto para referência
        val ultimoPontoEntity = AppDatabase.getInstance(this)
            .pontosGenericosDao()
            .getUltimoPonto(funcionario.codigo)

        // Determinar tipo de ponto baseado no último registro
        val tipoPonto = if (ultimoPontoEntity?.tipoPonto == "ENTRADA") "SAIDA" else "ENTRADA"
        
        // Registrar o ponto
        val ponto = PontosGenericosEntity(
            funcionarioId = funcionario.codigo,
            funcionarioNome = funcionario.nome,
            tipoPonto = tipoPonto,
            dataHora = horarioAtual
        )
        
        AppDatabase.getInstance(this)
            .pontosGenericosDao()
            .insert(ponto)
        
        Log.d(TAG, "💾 Ponto registrado: ${funcionario.nome} - $tipoPonto - $dataFormatada")
        
        // Mostrar tela de confirmação
        withContext(Dispatchers.Main) {
            mostrarConfirmacaoPonto(funcionario, tipoPonto, dataFormatada)
        }


        
        processandoFace = false
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
            
            // Limpar interface anterior
            funcionarioInfo.visibility = View.GONE
            statusText.visibility = View.GONE
            
            // Criar layout de confirmação simples
            val confirmacaoLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                )
                setPadding(32, 32, 32, 32)
                setBackgroundColor(Color.parseColor("#FFFFFF"))
                gravity = android.view.Gravity.CENTER
            }
            
            // Ícone de sucesso
            val iconeSucesso = TextView(this).apply {
                text = "✅"
                textSize = 48f
                gravity = android.view.Gravity.CENTER
                setPadding(0, 16, 0, 16)
            }
            
            // Título
            val titulo = TextView(this).apply {
                text = "Ponto Registrado!"
                textSize = 24f
                setTextColor(Color.parseColor("#4CAF50"))
                gravity = android.view.Gravity.CENTER
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(0, 16, 0, 8)
            }
            
            // Nome do funcionário
            val nomeFuncionario = TextView(this).apply {
                text = "👤 ${funcionario.nome}"
                textSize = 18f
                setTextColor(Color.parseColor("#333333"))
                gravity = android.view.Gravity.CENTER
                setPadding(0, 8, 0, 8)
            }
            

            
            // Horário
            val horarioText = TextView(this).apply {
                text = "🕐 $horario"
                textSize = 16f
                setTextColor(Color.parseColor("#666666"))
                gravity = android.view.Gravity.CENTER
                setPadding(0, 8, 0, 16)
            }
            
            // Botão OK
            val btnOk = Button(this).apply {
                text = "OK"
                textSize = 16f
                setBackgroundColor(Color.parseColor("#4CAF50"))
                setTextColor(Color.WHITE)
                setPadding(48, 16, 48, 16)
                setOnClickListener {
                    Log.d(TAG, "👆 Botão OK pressionado")
                    finish()
                }
            }
            
            // Adicionar elementos ao layout
            confirmacaoLayout.addView(iconeSucesso)
            confirmacaoLayout.addView(titulo)
            confirmacaoLayout.addView(nomeFuncionario)
            confirmacaoLayout.addView(horarioText)
            confirmacaoLayout.addView(btnOk)
            
            // Substituir o layout principal de forma segura
            val mainLayout = findViewById<LinearLayout>(R.id.mainLayout)
            if (mainLayout != null) {
                mainLayout.removeAllViews()
                mainLayout.addView(confirmacaoLayout)
                Log.d(TAG, "✅ Layout de confirmação aplicado com sucesso")
            } else {
                Log.e(TAG, "❌ mainLayout não encontrado")
                // Fallback: usar setContentView
                setContentView(confirmacaoLayout)
            }
            
            // Auto-fechar após 5 segundos
            confirmacaoLayout.postDelayed({
                Log.d(TAG, "⏰ Auto-fechando após 5 segundos")
                finish()
            }, 5000)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao mostrar confirmação", e)
            // Fallback simples
            Toast.makeText(this, "✅ Ponto registrado para ${funcionario.nome} às $horario", Toast.LENGTH_LONG).show()
            finish()
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
        interpreter?.close()
    }
} 