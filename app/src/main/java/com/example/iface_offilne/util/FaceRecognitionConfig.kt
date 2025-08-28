package com.example.iface_offilne.util

/**
 * 🎛️ CONFIGURAÇÕES PARA RECONHECIMENTO FACIAL PRECISO
 * 
 * Este arquivo centraliza todas as configurações de thresholds
 * para facilitar ajustes e manutenção
 */
object FaceRecognitionConfig {
    
    // ========== CONFIGURAÇÕES DE CAPTURA ==========
    
    /**
     * Número de capturas necessárias para cadastro
     */
    const val REQUIRED_FACE_CAPTURES = 3
    
    /**
     * Intervalo entre capturas (em milissegundos)
     */
    const val CAPTURE_INTERVAL_MS = 1000L
    
    // ========== THRESHOLDS DE QUALIDADE DE IMAGEM ==========
    
    /**
     * Tamanho mínimo da face em relação à imagem (0.0 - 1.0)
     */
    const val MIN_FACE_SIZE_RATIO = 0.05f // Reduzido de 0.12f para 0.05f (mais permissivo)
    
    /**
     * Tamanho máximo da face em relação à imagem (0.0 - 1.0)
     */
    const val MAX_FACE_SIZE_RATIO = 0.85f // Aumentado de 0.75f para 0.85f (mais permissivo)
    
    /**
     * Distância mínima entre olhos (em pixels)
     */
    const val MIN_EYE_DISTANCE = 35f
    
    /**
     * Brilho mínimo da imagem (0.0 - 1.0)
     */
    const val MIN_BRIGHTNESS = 0.25f
    
    /**
     * Brilho máximo da imagem (0.0 - 1.0)
     */
    const val MAX_BRIGHTNESS = 0.75f
    
    /**
     * Contraste mínimo da imagem (0.0 - 1.0)
     */
    const val MIN_CONTRAST = 0.25f
    
    /**
     * Nitidez mínima da imagem (0.0 - 1.0)
     */
    const val MIN_SHARPNESS = 0.005f // Reduzido de 0.1f para 0.005f (mais permissivo)
    
    // ========== THRESHOLDS DE RECONHECIMENTO ==========
    
    /**
     * Similaridade cosseno mínima para reconhecimento (0.0 - 1.0)
     * Quanto maior, mais rigoroso
     */
    const val MIN_SIMILARITY_THRESHOLD = 0.85f
    
    /**
     * Distância euclidiana máxima para reconhecimento
     * Quanto menor, mais rigoroso
     */
    const val MAX_EUCLIDEAN_DISTANCE = 0.35f
    
    /**
     * Confiança mínima para reconhecimento (0.0 - 1.0)
     * Quanto maior, mais rigoroso
     */
    const val REQUIRED_CONFIDENCE = 0.90f
    
    // ========== CONFIGURAÇÕES DE ESTABILIZAÇÃO ==========
    
    /**
     * Número mínimo de frames estáveis
     */
    const val MIN_STABLE_FRAMES = 15 // Reduzido de 40 para 15 (mais permissivo)
    
    /**
     * Tempo máximo para estabilização (em milissegundos)
     */
    const val MAX_STABLE_TIME_MS = 10000L // Aumentado de 8000 para 10000
    
    /**
     * Tolerância de posição (em pixels)
     */
    const val POSITION_TOLERANCE = 150 // Aumentado de 90 para 150 (mais permissivo)
    
    // ========== CONFIGURAÇÕES DE SIMETRIA ==========
    
    /**
     * Simetria facial mínima (0.0 - 1.0)
     */
    const val MIN_FACE_SYMMETRY = 0.7f
    
    // ========== CONFIGURAÇÕES DE VALIDAÇÃO DE EMBEDDING ==========
    
    /**
     * Magnitude mínima do embedding
     */
    const val MIN_EMBEDDING_MAGNITUDE = 0.1f
    
    /**
     * Variância mínima do embedding
     */
    const val MIN_EMBEDDING_VARIANCE = 0.01f
    
    // ========== CONFIGURAÇÕES DE PERFORMANCE ==========
    
    /**
     * Número de threads para TensorFlow Lite
     */
    const val TENSORFLOW_THREADS = 2
    
    /**
     * Usar NNAPI (Neural Network API)
     */
    const val USE_NNAPI = false
    
    /**
     * Usar GPU para TensorFlow Lite
     */
    const val USE_GPU = false
    
    // ========== CONFIGURAÇÕES DE DIMENSÕES ==========
    
    /**
     * Tamanho de entrada do modelo (mobile_face_net.tflite)
     */
    const val MODEL_INPUT_SIZE = 112
    
    /**
     * Tamanho de saída do modelo (dimensões do embedding - mobile_face_net.tflite)
     */
    const val MODEL_OUTPUT_SIZE = 192
    
    // ========== MÉTODOS DE CONFIGURAÇÃO ==========
    
    /**
     * Configuração para modo rigoroso (evitar falsos positivos)
     */
    fun getRigorousConfig(): RigorousConfig {
        return RigorousConfig(
            minSimilarity = 0.90f,
            maxEuclideanDistance = 0.25f,
            requiredConfidence = 0.95f,
            minFaceSizeRatio = 0.15f,
            minEyeDistance = 40f,
            minBrightness = 0.30f,
            maxBrightness = 0.70f,
            minContrast = 0.30f
        )
    }
    
    /**
     * Configuração para modo equilibrado
     */
    fun getBalancedConfig(): RigorousConfig {
        return RigorousConfig(
            minSimilarity = 0.85f,
            maxEuclideanDistance = 0.35f,
            requiredConfidence = 0.90f,
            minFaceSizeRatio = 0.12f,
            minEyeDistance = 35f,
            minBrightness = 0.25f,
            maxBrightness = 0.75f,
            minContrast = 0.25f
        )
    }
    
    /**
     * Configuração para modo permissivo (para testes)
     */
    fun getPermissiveConfig(): RigorousConfig {
        return RigorousConfig(
            minSimilarity = 0.75f,
            maxEuclideanDistance = 0.50f,
            requiredConfidence = 0.80f,
            minFaceSizeRatio = 0.08f,
            minEyeDistance = 25f,
            minBrightness = 0.15f,
            maxBrightness = 0.85f,
            minContrast = 0.15f
        )
    }
    
    /**
     * Classe para configurações rigorosas
     */
    data class RigorousConfig(
        val minSimilarity: Float,
        val maxEuclideanDistance: Float,
        val requiredConfidence: Float,
        val minFaceSizeRatio: Float,
        val minEyeDistance: Float,
        val minBrightness: Float,
        val maxBrightness: Float,
        val minContrast: Float
    )
} 