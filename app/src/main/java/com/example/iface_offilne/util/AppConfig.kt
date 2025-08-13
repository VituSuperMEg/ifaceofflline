package com.example.iface_offilne.util

/**
 * Configurações centralizadas do aplicativo
 * ✅ Facilita ativar/desativar logs e ajustar parâmetros
 */
object AppConfig {
    
    // ✅ MODO DEBUG - Ative para logs detalhados em desenvolvimento
    const val DEBUG_MODE = false // Mude para true quando precisar debugar
    
    // ✅ CONFIGURAÇÕES DE RECONHECIMENTO FACIAL OTIMIZADAS
    object FaceRecognition {
        // Thresholds mais realistas
        const val COSINE_THRESHOLD = 0.50f // Era 0.70f
        const val FALLBACK_THRESHOLD = 0.40f // Era 0.60f
        const val MIN_SCORE_DIFFERENCE = 0.08f // Era 0.15f
        const val HIGH_CONFIDENCE_THRESHOLD = 0.65f
        
        // Configurações de detecção
        const val FACE_RATIO_THRESHOLD = 0.08f // Era 0.15f
        const val REQUIRED_MATCHES = 2 // Era 3
        const val MATCH_TIMEOUT_MS = 3000L // Era 2000L
        
        // Cache
        const val CACHE_EXPIRATION_MS = 30000L // 30 segundos
    }
    
    // ✅ CONFIGURAÇÕES DE PERFORMANCE
    object Performance {
        val ENABLE_DETAILED_LOGS = DEBUG_MODE
        val ENABLE_PERFORMANCE_METRICS = DEBUG_MODE
        const val MAX_PARALLEL_FACE_PROCESSING = 1
        // Usar valor numérico em vez de referência à classe
        const val CAMERA_ANALYSIS_STRATEGY = 1 // STRATEGY_KEEP_ONLY_LATEST
    }
    
    // ✅ CONFIGURAÇÕES DE UI
    object UI {
        val SHOW_DEBUG_INFO = DEBUG_MODE
        const val VIBRATE_ON_MATCH = true
        const val AUTO_HIDE_KEYBOARD = true
        const val STATUS_MESSAGE_TIMEOUT_MS = 5000L
    }
    
    // ✅ CONFIGURAÇÕES DE CÂMERA (usar valores numéricos)
    object Camera {
        const val PERFORMANCE_MODE = 1 // PERFORMANCE_MODE_FAST
        const val LANDMARK_MODE = 2 // LANDMARK_MODE_NONE
        const val CLASSIFICATION_MODE = 1 // CLASSIFICATION_MODE_NONE
    }
    
    // ✅ VERSÃO E BUILD INFO
    const val APP_VERSION = "1.0.0"
    val BUILD_TYPE = if (DEBUG_MODE) "DEBUG" else "RELEASE"
} 