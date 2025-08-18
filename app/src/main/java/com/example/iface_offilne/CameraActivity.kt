package com.example.iface_offilne

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.*
import android.os.Bundle
import android.os.Environment
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
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .build()
    )

    private var alreadySaved = false
    private var faceDetectionCount = 0
    private var currentFaceBitmap: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setupUI()
        Log.d(TAG, "🚀 === INICIANDO APLICAÇÃO ===")

        // Carrega o modelo
        loadTensorFlowModel()

        // Solicita permissões
        Log.d(TAG, "🔐 Verificando permissões...")
        Log.d(TAG, "📱 Versão Android: ${android.os.Build.VERSION.SDK_INT}")
        Log.d(TAG, "📋 Permissões necessárias: ${REQUIRED_PERMISSIONS.joinToString(", ")}")
        
        if (allPermissionsGranted()) {
            Log.d(TAG, "✅ Todas as permissões já concedidas")
            startCamera()
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
                .setTargetResolution(android.util.Size(640, 480)) // Resolução otimizada para velocidade
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

                        // ✅ Só salva se estiver no centro
                        if (!alreadySaved && overlay.isFaceInOval(face.boundingBox)) {
                            processDetectedFace(mediaImage, face.boundingBox)
                            alreadySaved = true
                            showToast("✅ Rosto centralizado e salvo com sucesso!")
                        }
                    } else {
                        overlay.clear()
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
            Log.d(TAG, "🔄 === PROCESSANDO FACE ===")

            val bitmap = toBitmap(mediaImage)
            saveImage(bitmap, "original")

            val faceBmp = cropFace(bitmap, boundingBox)
            saveImage(faceBmp, "face_cropped")

            val resized = Bitmap.createScaledBitmap(faceBmp, modelInputWidth, modelInputHeight, true)
            saveImage(resized, "face_${modelInputWidth}x${modelInputHeight}")

            // Manter orientação natural da imagem
            val correctedFace = faceBmp

            // Salvar a foto do rosto para mostrar na tela de confirmação (tamanho otimizado para melhor qualidade)
            val faceForDisplay = Bitmap.createScaledBitmap(correctedFace, 300, 300, true)
            currentFaceBitmap = fixImageOrientationDefinitive(faceForDisplay) // Corrigir orientação
            
            Log.d(TAG, "💾 Imagens salvas com correção de orientação!")

            if (modelLoaded && interpreter != null) {
                executeInference(resized)
            } else {
                Log.d(TAG, "⚠️  Sem modelo - apenas detecção")
                // Mostrar tela de sucesso mesmo sem modelo
                showSuccessScreen()
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro no processamento", e)
            showToast("Erro: ${e.message}")
        }
    }

    private fun executeInference(bitmap: Bitmap) {
        try {
            Log.d(TAG, "🧠 === EXECUTANDO INFERÊNCIA ===")

            // Usar o mesmo método de conversão que a PontoActivity
            val inputTensor = convertBitmapToTensorInput(bitmap)
            val output = Array(1) { FloatArray(modelOutputSize) }

            interpreter?.run(inputTensor, output)

            val vetorFacial = output[0]
            val usuario = intent.getSerializableExtra("usuario") as? FuncionariosLocalModel

            if (usuario != null) {
                // Primeiro deletar face antiga se existir
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val dao = AppDatabase.getInstance(applicationContext).faceDao()
                        
                        // Deletar face antiga
                        dao.deleteByFuncionarioId(usuario.codigo)
                        Log.d(TAG, "🗑️ Face antiga deletada para funcionário ${usuario.codigo}")
                        
                        // Criar nova face
                        val faces = FaceEntity(
                            id = 0, // Deixar o Room gerar o ID
                            funcionarioId = usuario.codigo,
                            embedding = vetorFacial.joinToString(","),
                            synced = true
                        )
                        
                        // Inserir nova face
                        dao.insert(faces)
                        Log.d(TAG, "✅ Nova face salva para funcionário ${usuario.codigo}")
                        
                        // Mostrar tela de confirmação na thread principal
                        withContext(Dispatchers.Main) {
                            showSuccessScreen()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Erro ao salvar face: ${e.message}")
                        withContext(Dispatchers.Main) {
                            showToast("Erro ao salvar face: ${e.message}")
                        }
                    }
                }


            } else {
                Log.e(TAG, "❌ Usuario nulo - não foi possível salvar o vetor facial.")
                showToast("Erro: usuário não encontrado.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro na inferência", e)
            showToast("Erro na inferência: ${e.message}")
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