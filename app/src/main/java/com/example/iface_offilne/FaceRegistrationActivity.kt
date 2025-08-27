package com.example.iface_offilne

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.BitmapFactory
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
import androidx.lifecycle.lifecycleScope
import com.example.iface_offilne.data.AppDatabase
import com.example.iface_offilne.data.FaceEntity
import com.example.iface_offilne.helpers.MobileFaceNetHelper
import com.example.iface_offilne.helpers.FaceRecognitionConfig
import com.example.iface_offilne.util.FaceOverlayView
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.*
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.math.max
import kotlin.math.min

/**
 * 🚀 ACTIVITY DE CADASTRO FACIAL AVANÇADO
 * 
 * Implementa:
 * ✅ Captura de múltiplas amostras
 * ✅ Validação de qualidade em tempo real
 * ✅ Feedback visual para o usuário
 * ✅ Bounding box dinâmico
 * ✅ Validação de ângulos
 */
class FaceRegistrationActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "FaceRegistrationActivity"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
    
    // UI Components
    private lateinit var previewView: PreviewView
    private lateinit var overlay: FaceOverlayView
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var instructionText: TextView
    private lateinit var confirmButton: Button
    private lateinit var cancelButton: Button
    
    // Camera
    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null
    private var imageAnalyzer: ImageAnalysis? = null
    
    // Face Detection
    private val faceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .build()
    )
    
    // MobileFaceNet Helper
    private lateinit var mobileFaceNetHelper: MobileFaceNetHelper
    
    // State
    private var currentSample = 0
    private val totalSamples = 5
    private val sampleTypes = listOf("Frente Neutra", "Frente Sorrindo", "Olhando à Esquerda", "Olhando à Direita", "Centro")
    private val capturedSamples = mutableListOf<Bitmap>()
    private var isProcessing = false
    private var currentFaceBitmap: Bitmap? = null
    private var currentUsuario: com.example.iface_offilne.models.FuncionariosLocalModel? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_face_registration)
        
        // ✅ Carregar usuário passado como parâmetro
        currentUsuario = intent.getSerializableExtra("usuario") as? com.example.iface_offilne.models.FuncionariosLocalModel
        
        if (currentUsuario == null) {
            Log.e(TAG, "❌ Usuário não fornecido")
            showError("Usuário não fornecido")
            finish()
            return
        }
        
        // ✅ Inicializar componentes
        initializeViews()
        initializeMobileFaceNet()
        
        // ✅ Executar testes de compatibilidade (apenas em debug)
        try {
            runCompatibilityTests()
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Testes de compatibilidade não executados: ${e.message}")
        }
        
        // ✅ Verificar permissões
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }
        
        // ✅ Configurar listeners
        setupListeners()
        
        cameraExecutor = Executors.newSingleThreadExecutor()
        
        Log.d(TAG, "✅ FaceRegistrationActivity inicializada com sucesso")
    }
    
    /**
     * 🎨 INICIALIZAR COMPONENTES DA UI
     */
    private fun initializeViews() {
        previewView = findViewById(R.id.preview_view)
        overlay = findViewById(R.id.face_overlay)
        progressBar = findViewById(R.id.progress_bar)
        statusText = findViewById(R.id.status_text)
        instructionText = findViewById(R.id.instruction_text)
        confirmButton = findViewById(R.id.confirm_button)
        cancelButton = findViewById(R.id.cancel_button)
        
        // ✅ OCULTAR TODOS OS ELEMENTOS DE TEXTO
        progressBar.visibility = View.GONE
        statusText.visibility = View.GONE
        instructionText.visibility = View.GONE
        confirmButton.visibility = View.GONE
        cancelButton.visibility = View.GONE
        
        // ✅ APENAS O OVERLAY FICA VISÍVEL (QUADRADO AMARELO)
        overlay.visibility = View.VISIBLE
    }
    
    /**
     * 🤖 INICIALIZAR MOBILEFACENET
     */
    private fun initializeMobileFaceNet() {
        try {
            Log.d(TAG, "🤖 Inicializando MobileFaceNet...")
            mobileFaceNetHelper = MobileFaceNetHelper(this)
            Log.d(TAG, "✅ MobileFaceNet inicializado com sucesso")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao inicializar MobileFaceNet", e)
            showError("Erro ao inicializar reconhecimento facial: ${e.message}")
            
            // ✅ Tentar continuar sem MobileFaceNet (modo de fallback)
            Log.w(TAG, "⚠️ Continuando em modo de fallback (sem MobileFaceNet)")
            // ✅ REMOVIDO: updateStatus("Modo de fallback ativo")
        }
    }
    
    /**
     * 📷 INICIAR CÂMERA
     */
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            
            val preview = Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .build()
                .also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
            
            imageAnalyzer = ImageAnalysis.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        analyzeImage(imageProxy)
                    }
                }
            
            try {
                cameraProvider.unbindAll()
                
                val camera = cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    preview,
                    imageAnalyzer
                )
                
                Log.d(TAG, "✅ Câmera iniciada com sucesso")
                // ✅ REMOVIDO: updateStatus("Câmera pronta")
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao iniciar câmera", e)
                showError("Erro ao iniciar câmera")
            }
            
        }, ContextCompat.getMainExecutor(this))
    }
    
    /**
     * 🔍 ANALISAR IMAGEM
     */
    private fun analyzeImage(imageProxy: ImageProxy) {
        try {
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                
                // ✅ Converter para bitmap para análise
                val bitmap = try {
                    mediaImage.toBitmap()
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Erro ao converter MediaImage para Bitmap", e)
                    imageProxy.close()
                    return
                }
                
                faceDetector.process(image)
                    .addOnSuccessListener { faces ->
                        try {
                            // ✅ Atualizar bitmap da face se detectada
                            if (faces.isNotEmpty()) {
                                val face = if (faces.size > 1) {
                                    faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() } ?: faces[0]
                                } else {
                                    faces[0]
                                }
                                
                                // ✅ Extrair face do bitmap
                                currentFaceBitmap = extractFaceFromBitmap(bitmap, face.boundingBox)
                                Log.d(TAG, "✅ Face bitmap atualizado: ${currentFaceBitmap?.width}x${currentFaceBitmap?.height}")
                            }
                            
                            handleFaceDetection(faces)
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ Erro ao processar faces detectadas", e)
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "❌ Erro na detecção de faces", e)
                    }
                    .addOnCompleteListener {
                        imageProxy.close()
                    }
            } else {
                imageProxy.close()
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao analisar imagem", e)
            imageProxy.close()
        }
    }
    
    /**
     * 👤 TRATAR DETECÇÃO DE FACE
     */
    private fun handleFaceDetection(faces: List<com.google.mlkit.vision.face.Face>) {
        runOnUiThread {
            Log.d(TAG, "🔍 Faces detectadas: ${faces.size}")
            
            when {
                faces.isEmpty() -> {
                    // ✅ REMOVIDO: updateStatus("Nenhuma face detectada")
                    // ✅ REMOVIDO: updateInstruction("Posicione seu rosto no centro da tela")
                    overlay.setCaptureMode(false)
                }
                faces.size > 1 -> {
                    // ✅ Se múltiplas faces, usar a maior (mais próxima)
                    val largestFace = faces.maxByOrNull { face ->
                        face.boundingBox.width() * face.boundingBox.height()
                    }
                    
                    if (largestFace != null) {
                        Log.d(TAG, "✅ Usando face maior: ${largestFace.boundingBox}")
                        validateFaceQuality(largestFace)
                    } else {
                        // ✅ REMOVIDO: updateStatus("Múltiplas faces detectadas")
                        // ✅ REMOVIDO: updateInstruction("Aproxime-se da câmera")
                        overlay.setCaptureMode(false)
                    }
                }
                else -> {
                    val face = faces[0]
                    Log.d(TAG, "✅ Face única detectada: ${face.boundingBox}")
                    validateFaceQuality(face)
                }
            }
        }
    }
    
    /**
     * ✅ VALIDAR QUALIDADE DA FACE
     */
    private fun validateFaceQuality(face: com.google.mlkit.vision.face.Face) {
        // ✅ Verificar tamanho da face
        val faceSizeRatio = calculateFaceSizeRatio(face)
        Log.d(TAG, "📏 Face size ratio: $faceSizeRatio (min: ${FaceRecognitionConfig.MIN_FACE_SIZE_RATIO}, max: ${FaceRecognitionConfig.MAX_FACE_SIZE_RATIO})")
        
        if (faceSizeRatio < FaceRecognitionConfig.MIN_FACE_SIZE_RATIO || faceSizeRatio > FaceRecognitionConfig.MAX_FACE_SIZE_RATIO) {
            if (faceSizeRatio < FaceRecognitionConfig.MIN_FACE_SIZE_RATIO) {
                // ✅ REMOVIDO: updateStatus("Aproxime-se da câmera")
                // ✅ REMOVIDO: updateInstruction("Seu rosto deve ocupar pelo menos 25% da tela")
            } else {
                // ✅ REMOVIDO: updateStatus("Afaste-se da câmera")
                // ✅ REMOVIDO: updateInstruction("Seu rosto está muito próximo")
            }
            overlay.setCaptureMode(false)
            return
        }
        
        // ✅ Verificar ângulos (mais tolerante)
        val yaw = face.headEulerAngleY
        val pitch = face.headEulerAngleX
        val roll = face.headEulerAngleZ
        
        when {
            abs(yaw) > 25f -> {
                // ✅ REMOVIDO: updateStatus("Centralize o rosto")
                // ✅ REMOVIDO: updateInstruction("Olhe para o centro da tela")
                overlay.setCaptureMode(false)
                return
            }
            abs(pitch) > 25f -> {
                // ✅ REMOVIDO: updateStatus("Ajuste a inclinação")
                // ✅ REMOVIDO: updateInstruction("Mantenha o rosto nivelado")
                overlay.setCaptureMode(false)
                return
            }
            abs(roll) > 25f -> {
                // ✅ REMOVIDO: updateStatus("Endireite o rosto")
                // ✅ REMOVIDO: updateInstruction("Não incline a cabeça")
                overlay.setCaptureMode(false)
                return
            }
        }
        
        // ✅ Verificar iluminação (mais tolerante)
        if (currentFaceBitmap != null) {
            val brightness = calculateBrightness(currentFaceBitmap!!)
            if (brightness < 0.1f || brightness > 0.9f) {
                // ✅ REMOVIDO: updateStatus("Ajuste a iluminação")
                // ✅ REMOVIDO: updateInstruction("Evite sombras ou luz muito forte")
                overlay.setCaptureMode(false)
                return
            }
        }
        
        // ✅ Verificar se currentSample está dentro dos limites
        if (currentSample >= sampleTypes.size) {
            Log.e(TAG, "❌ currentSample ($currentSample) fora dos limites do array sampleTypes (${sampleTypes.size})")
            // ✅ REMOVIDO: updateStatus("Todas as amostras capturadas!")
            // ✅ REMOVIDO: updateInstruction("Processando e salvando...")
            confirmButton.isEnabled = true
            overlay.setCaptureMode(false)
            // ✅ SALVAR AUTOMATICAMENTE
            processRegistration()
            return
        }
        
        // ✅ Face válida - permitir captura
        // ✅ REMOVIDO: updateStatus("Face válida - ${sampleTypes[currentSample]}")
        // ✅ REMOVIDO: updateInstruction("Mantenha a posição e aguarde a captura automática")
        overlay.setCaptureMode(true)
        
        Log.d(TAG, "✅ Face válida detectada! Iniciando captura automática...")
        
        // ✅ Captura automática após 500ms de face válida (muito mais rápido)
        if (!isProcessing && currentSample < totalSamples) {
            lifecycleScope.launch {
                delay(500) // Reduzido para 500ms para captura mais rápida
                if (!isProcessing && currentSample < totalSamples) {
                    Log.d(TAG, "📸 Iniciando captura da amostra ${currentSample + 1}")
                    captureCurrentSample()
                }
            }
        }
    }
    
    /**
     * 📸 CAPTURAR AMOSTRA ATUAL
     */
    private fun captureCurrentSample() {
        Log.d(TAG, "📸 captureCurrentSample chamado - isProcessing: $isProcessing, currentFaceBitmap: ${currentFaceBitmap != null}")
        
        if (isProcessing || currentFaceBitmap == null) {
            Log.d(TAG, "❌ Captura cancelada - isProcessing: $isProcessing, bitmap: ${currentFaceBitmap != null}")
            return
        }
        
        // ✅ Verificar se currentSample está dentro dos limites
        if (currentSample >= totalSamples) {
            Log.e(TAG, "❌ currentSample ($currentSample) >= totalSamples ($totalSamples)")
            return
        }
        
        isProcessing = true
        // ✅ REMOVIDO: updateStatus("Capturando amostra ${currentSample + 1}/$totalSamples")
        Log.d(TAG, "✅ Iniciando captura da amostra ${currentSample + 1}/$totalSamples")
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // ✅ Validar qualidade com MobileFaceNet
                // val qualityResult = mobileFaceNetHelper.validateImageQuality(currentFaceBitmap!!)
                // if (!qualityResult.isValid) {
                //     withContext(Dispatchers.Main) {
                //         updateStatus("Qualidade insuficiente")
                //         updateInstruction(qualityResult.reason)
                //         isProcessing = false
                //     }
                //     return@launch
                // }
                
                // ✅ Adicionar à lista de amostras
                val sampleCopy = currentFaceBitmap!!.copy(currentFaceBitmap!!.config ?: Bitmap.Config.ARGB_8888, true)
                capturedSamples.add(sampleCopy)
                
                withContext(Dispatchers.Main) {
                    // ✅ Verificar se não ultrapassou o limite antes de incrementar
                    if (currentSample >= totalSamples) {
                        Log.d(TAG, "✅ Todas as amostras já foram capturadas")
                        // ✅ REMOVIDO: updateStatus("Todas as amostras capturadas!")
                        // ✅ REMOVIDO: updateInstruction("Processando e salvando...")
                        confirmButton.isEnabled = true
                        overlay.setCaptureMode(false)
                        isProcessing = false
                        // ✅ SALVAR AUTOMATICAMENTE
                        processRegistration()
                        return@withContext
                    }
                    
                    currentSample++
                    // ✅ REMOVIDO: updateProgress(currentSample)
                    
                    if (currentSample >= totalSamples) {
                        // ✅ Todas as amostras capturadas - SALVAR AUTOMATICAMENTE
                        // ✅ REMOVIDO: updateStatus("Todas as amostras capturadas!")
                        // ✅ REMOVIDO: updateInstruction("Processando e salvando...")
                        confirmButton.isEnabled = true
                        overlay.setCaptureMode(false)
                        isProcessing = false
                        // ✅ SALVAR AUTOMATICAMENTE
                        processRegistration()
                        return@withContext
                    }
                    
                    // ✅ Verificar se currentSample está dentro dos limites antes de acessar sampleTypes
                    if (currentSample < sampleTypes.size) {
                        // ✅ REMOVIDO: updateStatus("Amostra ${currentSample} capturada")
                        // ✅ REMOVIDO: updateInstruction("Agora: ${sampleTypes[currentSample]}")
                    } else {
                        // ✅ Caso de segurança - todas as amostras foram capturadas
                        Log.d(TAG, "✅ Todas as amostras capturadas (currentSample: $currentSample)")
                        // ✅ REMOVIDO: updateStatus("Todas as amostras capturadas!")
                        // ✅ REMOVIDO: updateInstruction("Processando e salvando...")
                        confirmButton.isEnabled = true
                        overlay.setCaptureMode(false)
                        // ✅ SALVAR AUTOMATICAMENTE
                        processRegistration()
                    }
                    
                    isProcessing = false
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao capturar amostra", e)
                withContext(Dispatchers.Main) {
                    // ✅ REMOVIDO: updateStatus("Erro na captura")
                    // ✅ REMOVIDO: updateInstruction("Tente novamente")
                    isProcessing = false
                }
            }
        }
    }
    
    /**
     * 🎯 CONFIGURAR LISTENERS
     */
    private fun setupListeners() {
        confirmButton.setOnClickListener {
            // ✅ Fallback: se por algum motivo o salvamento automático não funcionou
            if (capturedSamples.size == totalSamples && !isProcessing) {
                Log.d(TAG, "🔄 Salvamento manual iniciado via botão Confirmar")
                processRegistration()
            } else {
                Log.d(TAG, "⚠️ Botão Confirmar pressionado mas não há amostras suficientes")
                showError("Capture todas as amostras primeiro")
            }
        }
        
        cancelButton.setOnClickListener {
            finish()
        }
    }
    
    /**
     * 📊 PROCESSAR CADASTRO (OTIMIZADO PARA VELOCIDADE)
     */
    private fun processRegistration() {
        confirmButton.isEnabled = false
        // ✅ REMOVIDO: updateStatus("Processando...")
        // ✅ REMOVIDO: updateInstruction("Gerando embeddings...")
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // ✅ Verificar se temos amostras suficientes
                if (capturedSamples.size != totalSamples) {
                    Log.e(TAG, "❌ Número insuficiente de amostras: ${capturedSamples.size}/$totalSamples")
                    withContext(Dispatchers.Main) {
                        // ✅ REMOVIDO: updateStatus("Número insuficiente de amostras")
                        // ✅ REMOVIDO: updateInstruction("Capture todas as amostras necessárias")
                        confirmButton.isEnabled = true
                    }
                    return@launch
                }
                
                // ✅ Verificar se o usuário está definido
                if (currentUsuario == null) {
                    Log.e(TAG, "❌ Usuário não definido")
                    withContext(Dispatchers.Main) {
                        confirmButton.isEnabled = true
                    }
                    return@launch
                }
                
                withContext(Dispatchers.Main) {
                    
                }
                
                val embeddings = mobileFaceNetHelper.processMultipleSamples(capturedSamples)
                
                withContext(Dispatchers.Main) {
                   
                }
                
                if (embeddings.isNotEmpty()) {
                    val averageEmbedding = calculateAverageEmbedding(embeddings)
                    
                    val database = AppDatabase.getInstance(this@FaceRegistrationActivity)
                    val faceDao = database.faceDao()
                    
                    val existingFace = faceDao.getByFuncionarioId(getCurrentFuncionarioCodigo())
                    if (existingFace != null) {
                        Log.d(TAG, "🔄 Face existente encontrada - atualizando...")
                        faceDao.deleteByFuncionarioId(getCurrentFuncionarioCodigo())
                    }
                    
                    val faceEntity = FaceEntity(
                        id = 0,
                        funcionarioId = getCurrentFuncionarioCodigo(),
                        embedding = averageEmbedding.joinToString(","),
                        synced = true
                    )
                    
                    faceDao.insert(faceEntity)
                    
                    
                    withContext(Dispatchers.Main) {
                       
                        saveFacePhotoForDisplay()
                        
                        FaceRegistrationSuccessActivity.start(this@FaceRegistrationActivity, currentUsuario)
                        finish() 
                    }
                } else {
                    Log.e(TAG, "❌ Nenhum embedding válido gerado")
                    withContext(Dispatchers.Main) {
                        confirmButton.isEnabled = true
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro no cadastro", e)
                withContext(Dispatchers.Main) {
                    confirmButton.isEnabled = true
                }
            }
        }
    }
    
    // ========== MÉTODOS AUXILIARES ==========
    
    /**
     * ❌ MOSTRAR ERRO
     */
    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
    
    /**
     * ✅ MOSTRAR SUCESSO
     */
    private fun showSuccess(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
    
    /**
     * 📏 CALCULAR TAMANHO DA FACE
     */
    private fun calculateFaceSizeRatio(face: com.google.mlkit.vision.face.Face): Float {
        val faceArea = face.boundingBox.width() * face.boundingBox.height()
        val imageArea = previewView.width * previewView.height
        return faceArea.toFloat() / imageArea
    }
    
    /**
     * 💡 CALCULAR BRILHO
     */
    private fun calculateBrightness(bitmap: Bitmap): Float {
        var sum = 0f
        var count = 0
        
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                sum += (r + g + b) / (3f * 255f)
                count++
            }
        }
        
        return if (count > 0) sum / count else 0f
    }
    
    /**
     * 📊 CALCULAR EMBEDDING MÉDIO (OTIMIZADO PARA VELOCIDADE)
     */
    private fun calculateAverageEmbedding(embeddings: List<FloatArray>): FloatArray {
        if (embeddings.isEmpty()) return FloatArray(0)
        
        val size = embeddings[0].size
        val average = FloatArray(size)
        
        // ✅ CALCULAR MÉDIA DE FORMA OTIMIZADA
        for (i in 0 until size) {
            var sum = 0f
            for (embedding in embeddings) {
                sum += embedding[i]
            }
            average[i] = sum / embeddings.size
        }
        
        // ✅ NORMALIZAR DE FORMA OTIMIZADA
        return normalizeEmbedding(average)
    }
    
    /**
     * 📐 NORMALIZAR EMBEDDING (OTIMIZADO PARA VELOCIDADE)
     */
    private fun normalizeEmbedding(embedding: FloatArray): FloatArray {
        var sum = 0f
        // ✅ CALCULAR SOMA DOS QUADRADOS DE FORMA OTIMIZADA
        for (value in embedding) {
            sum += value * value
        }
        val norm = sqrt(sum)
        
        return if (norm > 0f) {
            // ✅ NORMALIZAR DE FORMA OTIMIZADA
            embedding.map { it / norm }.toFloatArray()
        } else {
            embedding
        }
    }
    
    /**
     * 👤 OBTER ID DO FUNCIONÁRIO ATUAL
     */
    private fun getCurrentFuncionarioId(): Int {
        return currentUsuario?.id ?: 1
    }
    
    /**
     * 👤 OBTER CÓDIGO DO FUNCIONÁRIO ATUAL
     */
    private fun getCurrentFuncionarioCodigo(): String {
        return currentUsuario?.codigo ?: "1"
    }
    
    /**
     * 🧪 EXECUTAR TESTES DE COMPATIBILIDADE
     */
    private fun runCompatibilityTests() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val testHelper = com.example.iface_offilne.helpers.FaceRegistrationTestHelper(this@FaceRegistrationActivity)
                val result = testHelper.runCompatibilityTests()
                
                withContext(Dispatchers.Main) {
                    if (result.isSuccess) {
                        Log.d(TAG, "✅ Testes de compatibilidade passaram")
                    } else {
                        Log.w(TAG, "⚠️ Testes de compatibilidade falharam: ${result.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro nos testes de compatibilidade", e)
            }
        }
    }
    
    /**
     * ✅ VERIFICAR PERMISSÕES
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
                showError("Permissões necessárias não concedidas")
                finish()
            }
        }
    }
    
    /**
     * 🖼️ CONVERTER MEDIAIMAGE PARA BITMAP
     */
    private fun android.media.Image.toBitmap(): Bitmap {
        val yBuffer = planes[0].buffer
        val uBuffer = planes[1].buffer
        val vBuffer = planes[2].buffer
        
        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()
        
        val nv21 = ByteArray(ySize + uSize + vSize)
        
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)
        
        val yuvImage = android.graphics.YuvImage(nv21, android.graphics.ImageFormat.NV21, width, height, null)
        val out = java.io.ByteArrayOutputStream()
        yuvImage.compressToJpeg(android.graphics.Rect(0, 0, width, height), 100, out)
        val imageBytes = out.toByteArray()
        return android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }
    
    /**
     * ✂️ EXTRAIR FACE DO BITMAP
     */
    private fun extractFaceFromBitmap(bitmap: Bitmap, boundingBox: android.graphics.Rect): Bitmap {
        return try {
            val left = max(0, boundingBox.left)
            val top = max(0, boundingBox.top)
            val right = min(bitmap.width, boundingBox.right)
            val bottom = min(bitmap.height, boundingBox.bottom)
            
            // ✅ Verificar se as dimensões são válidas
            if (right <= left || bottom <= top) {
                Log.w(TAG, "⚠️ Bounding box inválido: left=$left, top=$top, right=$right, bottom=$bottom")
                return bitmap
            }
            
            // ✅ Verificar se o tamanho é muito pequeno
            val width = right - left
            val height = bottom - top
            if (width < 10 || height < 10) {
                Log.w(TAG, "⚠️ Face muito pequena: ${width}x${height}")
                return bitmap
            }
            
            Bitmap.createBitmap(bitmap, left, top, width, height)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao extrair face do bitmap", e)
            bitmap
        }
    }
    
    /**
     * ✅ SALVAR FOTO DA FACE PARA EXIBIÇÃO
     */
    private fun saveFacePhotoForDisplay() {
        try {
            // ✅ USAR A PRIMEIRA FACE CAPTURADA
            val faceBitmap = capturedSamples.firstOrNull() ?: currentFaceBitmap
            
            if (faceBitmap != null) {
                Log.d(TAG, "📸 Salvando foto da face para exibição...")
                
                // ✅ CRIAR VERSÃO OTIMIZADA PARA EXIBIÇÃO
                val displayBitmap = Bitmap.createScaledBitmap(faceBitmap, 300, 300, true)
                
                // ✅ SALVAR NO ARMAZENAMENTO INTERNO
                val photoFile = getFacePhotoFile(getCurrentFuncionarioCodigo())
                val outputStream = java.io.FileOutputStream(photoFile)
                
                // ✅ COMPRIMIR E SALVAR
                displayBitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
                outputStream.close()
                
                Log.d(TAG, "✅ Foto salva: ${photoFile.absolutePath}")
                
                // ✅ LIMPAR BITMAP TEMPORÁRIO
                if (displayBitmap != faceBitmap) {
                    displayBitmap.recycle()
                }
            } else {
                Log.w(TAG, "⚠️ Nenhuma face capturada para salvar")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao salvar foto da face", e)
        }
    }
    
    /**
     * ✅ OBTER ARQUIVO DA FOTO DO FUNCIONÁRIO
     */
    private fun getFacePhotoFile(funcionarioId: String): java.io.File {
        val photosDir = java.io.File(filesDir, "face_photos")
        if (!photosDir.exists()) {
            photosDir.mkdirs()
        }
        return java.io.File(photosDir, "face_${funcionarioId}.jpg")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        try {
            cameraExecutor.shutdown()
            mobileFaceNetHelper.release()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao limpar recursos", e)
        }
    }
} 
