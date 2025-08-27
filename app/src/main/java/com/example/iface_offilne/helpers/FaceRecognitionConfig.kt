package com.example.iface_offilne.helpers

/**
 * 🎛️ CONFIGURAÇÕES CENTRALIZADAS PARA RECONHECIMENTO FACIAL
 * 
 * Este arquivo centraliza todas as configurações relacionadas ao reconhecimento facial,
 * permitindo fácil ajuste dos thresholds e parâmetros sem modificar o código principal.
 */
object FaceRecognitionConfig {
    
    // ========== CONFIGURAÇÕES DO MOBILEFACENET ==========
    
    /** Tamanho de entrada do modelo MobileFaceNet */
    const val MODEL_INPUT_SIZE = 112
    
    /** Tamanho de saída do modelo MobileFaceNet (dimensões do embedding) */
    const val MODEL_OUTPUT_SIZE = 192
    
    // ========== THRESHOLDS DE QUALIDADE DE IMAGEM ==========
    
    /** Face deve ocupar pelo menos 5% da área da câmera */
    const val MIN_FACE_SIZE_RATIO = 0.05f
    
    /** Face não pode ocupar mais que 95% da área da câmera */
    const val MAX_FACE_SIZE_RATIO = 0.95f
    
    /** Distância mínima entre os olhos (em pixels) */
    const val MIN_EYE_DISTANCE = 15f
    
    /** Brilho mínimo da imagem (0.0 = preto, 1.0 = branco) */
    const val MIN_BRIGHTNESS = 0.1f
    
    /** Brilho máximo da imagem (0.0 = preto, 1.0 = branco) */
    const val MAX_BRIGHTNESS = 0.9f
    
    /** Contraste mínimo da imagem */
    const val MIN_CONTRAST = 0.2f
    
    /** Nitidez mínima (Laplacian variance) */
    const val MIN_SHARPNESS = 100f
    
    // ========== THRESHOLDS DE ÂNGULO DO ROSTO ==========
    
    /** Ângulo horizontal máximo (yaw) em graus */
    const val MAX_YAW_ANGLE = 45f
    
    /** Ângulo vertical máximo (pitch) em graus */
    const val MAX_PITCH_ANGLE = 45f
    
    /** Ângulo de rotação máximo (roll) em graus */
    const val MAX_ROLL_ANGLE = 45f
    
    // ========== THRESHOLDS DE RECONHECIMENTO ==========
    
    /** Similaridade cosseno mínima para reconhecimento (0.0 = diferente, 1.0 = idêntico) */
    const val DEFAULT_SIMILARITY_THRESHOLD = 0.9f
    
    /** Distância euclidiana máxima para reconhecimento */
    const val MAX_EUCLIDEAN_DISTANCE = 0.5f
    
    /** Confiança mínima combinada (similaridade + distância) */
    const val MIN_CONFIDENCE = 0.85f
    
    // ========== CONFIGURAÇÕES DE CAPTURA ==========
    
    /** Número de amostras necessárias para cadastro */
    const val REQUIRED_SAMPLES = 5
    
    /** Tipos de amostras necessárias */
    val SAMPLE_TYPES = listOf(
        "frente_neutra",
        "frente_sorrindo", 
        "esquerda",
        "direita",
        "centro"
    )
    
    /** Tempo de estabilização necessário antes da captura (em milissegundos) */
    const val STABILIZATION_TIME_MS = 2000L
    
    /** Número de frames estáveis necessários */
    const val REQUIRED_STABLE_FRAMES = 30
    
    // ========== CONFIGURAÇÕES DE PERFORMANCE ==========
    
    /** Número de threads para processamento TensorFlow */
    const val TENSORFLOW_THREADS = 2 // ✅ Reduzido para melhor compatibilidade
    
    /** Usar GPU delegate para aceleração */
    const val USE_GPU_DELEGATE = false // ✅ SEMPRE FALSE para evitar crashes
    
    /** Usar precisão FP16 para melhor performance */
    const val USE_FP16_PRECISION = false // ✅ FALSE para melhor compatibilidade
    
    // ========== CONFIGURAÇÕES DE SEGURANÇA ==========
    
    /** Habilitar detecção de liveness básica */
    const val ENABLE_LIVENESS_DETECTION = true
    
    /** Rejeitar embeddings com valores suspeitos */
    const val REJECT_SUSPICIOUS_EMBEDDINGS = true
    
    /** Threshold para detectar embeddings suspeitos */
    const val SUSPICIOUS_EMBEDDING_THRESHOLD = 0.1f
    
    // ========== CONFIGURAÇÕES DE DEBUG ==========
    
    /** Habilitar logs detalhados */
    const val ENABLE_DETAILED_LOGS = true
    
    /** Salvar imagens de debug */
    const val SAVE_DEBUG_IMAGES = false
    
    /** Mostrar métricas de performance */
    const val SHOW_PERFORMANCE_METRICS = true
    
    // ========== MÉTODOS AUXILIARES ==========
    
    /**
     * 🔧 Obter configuração baseada no modo de operação
     */
    fun getConfigForMode(mode: RecognitionMode): RecognitionConfig {
        return when (mode) {
            RecognitionMode.STRICT -> RecognitionConfig(
                similarityThreshold = 0.95f,
                euclideanDistance = 0.3f,
                minConfidence = 0.9f,
                minFaceSizeRatio = 0.7f,
                maxFaceSizeRatio = 0.75f,
                stabilizationTime = 3000L
            )
            RecognitionMode.BALANCED -> RecognitionConfig(
                similarityThreshold = 0.9f,
                euclideanDistance = 0.5f,
                minConfidence = 0.85f,
                minFaceSizeRatio = 0.6f,
                maxFaceSizeRatio = 0.8f,
                stabilizationTime = 2000L
            )
            RecognitionMode.LENIENT -> RecognitionConfig(
                similarityThreshold = 0.8f,
                euclideanDistance = 0.7f,
                minConfidence = 0.75f,
                minFaceSizeRatio = 0.5f,
                maxFaceSizeRatio = 0.85f,
                stabilizationTime = 1000L
            )
        }
    }
    
    /**
     * 📊 Validar configurações
     */
    fun validateConfig(): List<String> {
        val errors = mutableListOf<String>()
        
        if (MIN_FACE_SIZE_RATIO >= MAX_FACE_SIZE_RATIO) {
            errors.add("MIN_FACE_SIZE_RATIO deve ser menor que MAX_FACE_SIZE_RATIO")
        }
        
        if (MIN_BRIGHTNESS >= MAX_BRIGHTNESS) {
            errors.add("MIN_BRIGHTNESS deve ser menor que MAX_BRIGHTNESS")
        }
        
        if (DEFAULT_SIMILARITY_THRESHOLD < 0f || DEFAULT_SIMILARITY_THRESHOLD > 1f) {
            errors.add("DEFAULT_SIMILARITY_THRESHOLD deve estar entre 0 e 1")
        }
        
        if (MAX_EUCLIDEAN_DISTANCE < 0f) {
            errors.add("MAX_EUCLIDEAN_DISTANCE deve ser positivo")
        }
        
        return errors
    }
}

/**
 * 🎯 Modos de reconhecimento
 */
enum class RecognitionMode {
    STRICT,     // Muito rigoroso - menor chance de falsos positivos
    BALANCED,   // Equilibrado - boa precisão e recall
    LENIENT     // Menos rigoroso - maior chance de reconhecimento
}

/**
 * ⚙️ Configuração de reconhecimento
 */
data class RecognitionConfig(
    val similarityThreshold: Float,
    val euclideanDistance: Float,
    val minConfidence: Float,
    val minFaceSizeRatio: Float,
    val maxFaceSizeRatio: Float,
    val stabilizationTime: Long
) 