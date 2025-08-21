package com.example.iface_offilne.helpers

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.util.Log
import kotlin.math.min

/**
 * 🚀 HELPER PARA DETECTAR CAPACIDADES DO DISPOSITIVO
 * 
 * Detecta automaticamente o desempenho do dispositivo e ajusta
 * os parâmetros do reconhecimento facial para otimizar performance
 * mantendo a precisão em dispositivos de baixo desempenho
 */
// 🎛️ NÍVEIS DE PERFORMANCE
enum class PerformanceLevel {
    LOW,      // Dispositivos antigos/fracos
    MEDIUM,   // Dispositivos intermediários
    HIGH      // Dispositivos modernos/potentes
}

class DeviceCapabilityHelper(private val context: Context) {
    
    companion object {
        private const val TAG = "DeviceCapability"
        
        // 📊 THRESHOLDS PARA CLASSIFICAÇÃO
        private const val LOW_MEMORY_THRESHOLD = 2 * 1024 * 1024 * 1024L // 2GB
        private const val MEDIUM_MEMORY_THRESHOLD = 4 * 1024 * 1024 * 1024L // 4GB
        private const val LOW_CPU_CORES = 4
        private const val MEDIUM_CPU_CORES = 6
        private const val LOW_ANDROID_VERSION = 21 // API 21 (Android 5.0)
        private const val MEDIUM_ANDROID_VERSION = 26 // API 26 (Android 8.0)
    }
    
    private var performanceLevel: PerformanceLevel? = null
    private var deviceScore: Float = 0f
    
    init {
        analyzeDeviceCapabilities()
    }
    
    /**
     * 🔍 ANALISAR CAPACIDADES DO DISPOSITIVO
     */
    private fun analyzeDeviceCapabilities() {
        try {
            Log.d(TAG, "🔍 === ANALISANDO CAPACIDADES DO DISPOSITIVO ===")
            
            // ✅ 1. MEMÓRIA RAM
            val memoryInfo = getMemoryInfo()
            Log.d(TAG, "💾 Memória total: ${memoryInfo.totalGB}GB, Disponível: ${memoryInfo.availableGB}GB")
            
            // ✅ 2. CPU
            val cpuInfo = getCpuInfo()
            Log.d(TAG, "🖥️ CPU: ${cpuInfo.cores} cores, ${cpuInfo.architecture}")
            
            // ✅ 3. VERSÃO ANDROID
            val androidVersion = Build.VERSION.SDK_INT
            Log.d(TAG, "🤖 Android API: $androidVersion (${Build.VERSION.RELEASE})")
            
            // ✅ 4. CALCULAR SCORE DO DISPOSITIVO
            deviceScore = calculateDeviceScore(memoryInfo, cpuInfo, androidVersion)
            Log.d(TAG, "📊 Score do dispositivo: ${String.format("%.2f", deviceScore)}/100")
            
            // ✅ 5. CLASSIFICAR NÍVEL DE PERFORMANCE
            performanceLevel = when {
                deviceScore < 30f -> PerformanceLevel.LOW
                deviceScore < 70f -> PerformanceLevel.MEDIUM
                else -> PerformanceLevel.HIGH
            }
            
            Log.d(TAG, "🎯 Nível de performance: ${performanceLevel}")
            Log.d(TAG, "✅ Análise de capacidades concluída")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao analisar capacidades do dispositivo", e)
            // ✅ FALLBACK: Assumir nível baixo em caso de erro
            performanceLevel = PerformanceLevel.LOW
            deviceScore = 20f
        }
    }
    
    /**
     * 📊 OBTER INFORMAÇÕES DE MEMÓRIA
     */
    private fun getMemoryInfo(): MemoryInfo {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        
        return MemoryInfo(
            total = memoryInfo.totalMem,
            available = memoryInfo.availMem,
            threshold = memoryInfo.threshold
        )
    }
    
    /**
     * 🖥️ OBTER INFORMAÇÕES DE CPU
     */
    private fun getCpuInfo(): CpuInfo {
        val cores = Runtime.getRuntime().availableProcessors()
        val architecture = System.getProperty("os.arch") ?: "unknown"
        
        return CpuInfo(
            cores = cores,
            architecture = architecture
        )
    }
    
    /**
     * 📊 CALCULAR SCORE DO DISPOSITIVO (0-100)
     */
    private fun calculateDeviceScore(memoryInfo: MemoryInfo, cpuInfo: CpuInfo, androidVersion: Int): Float {
        var score = 0f
        
        // ✅ MEMÓRIA (40% do score)
        val memoryScore = when {
            memoryInfo.total >= MEDIUM_MEMORY_THRESHOLD -> 40f
            memoryInfo.total >= LOW_MEMORY_THRESHOLD -> 25f
            else -> 10f
        }
        score += memoryScore
        
        // ✅ CPU (35% do score)
        val cpuScore = when {
            cpuInfo.cores >= MEDIUM_CPU_CORES -> 35f
            cpuInfo.cores >= LOW_CPU_CORES -> 20f
            else -> 10f
        }
        score += cpuScore
        
        // ✅ VERSÃO ANDROID (25% do score)
        val androidScore = when {
            androidVersion >= MEDIUM_ANDROID_VERSION -> 25f
            androidVersion >= LOW_ANDROID_VERSION -> 15f
            else -> 5f
        }
        score += androidScore
        
        return min(score, 100f)
    }
    
    /**
     * 🎛️ OBTER CONFIGURAÇÕES ADAPTATIVAS PARA RECONHECIMENTO FACIAL
     */
    fun getAdaptiveFaceRecognitionConfig(): AdaptiveConfig {
        val level = performanceLevel ?: PerformanceLevel.LOW
        
        return when (level) {
            PerformanceLevel.LOW -> {
                Log.d(TAG, "🎛️ Configurando para dispositivo de BAIXO desempenho")
                AdaptiveConfig(
                    // ✅ THRESHOLDS ULTRA PERMISSIVOS para dispositivos fracos
                    minSimilarityThreshold = 0.2f,      // Reduzido para 20%
                    maxEuclideanDistance = 1.0f,          // Aumentado para 1.0f
                    requiredConfidence = 0.3f,            // Reduzido para 30%
                    
                    // ✅ QUALIDADE DE IMAGEM REDUZIDA
                    imageQuality = ImageQuality.LOW,
                    maxImageSize = 160,                   // Reduzido de 300
                    compressionQuality = 60,              // Reduzido de 80
                    
                    // ✅ PROCESSAMENTO OTIMIZADO
                    useTensorFlowOptimizations = true,
                    enableFallbackMode = true,
                    maxProcessingTime = 3000L,            // 3 segundos
                    
                    // ✅ VALIDAÇÕES ULTRA PERMISSIVAS
                    minFaceSizeRatio = 0.05f,              // Reduzido para 0.05f
                    maxFaceSizeRatio = 0.98f,              // Aumentado para 0.98f
                    minEyeDistance = 10f,                 // Reduzido para 10f
                    minBrightness = 0.05f,                // Reduzido para 0.05f
                    maxBrightness = 0.95f,                // Aumentado para 0.95f
                    minContrast = 0.05f                   // Reduzido para 0.05f
                )
            }
            
            PerformanceLevel.MEDIUM -> {
                Log.d(TAG, "🎛️ Configurando para dispositivo de MÉDIO desempenho")
                AdaptiveConfig(
                    // ✅ THRESHOLDS ULTRA PERMISSIVOS para dispositivos médios
                    minSimilarityThreshold = 0.25f,      // Reduzido para 25%
                    maxEuclideanDistance = 1.0f,          // Aumentado para 1.0f
                    requiredConfidence = 0.35f,            // Reduzido para 35%
                    
                    // ✅ QUALIDADE DE IMAGEM MÉDIA
                    imageQuality = ImageQuality.MEDIUM,
                    maxImageSize = 240,                   // Reduzido de 300
                    compressionQuality = 70,              // Reduzido de 80
                    
                    // ✅ PROCESSAMENTO OTIMIZADO
                    useTensorFlowOptimizations = true,
                    enableFallbackMode = true,
                    maxProcessingTime = 2500L,            // 2.5 segundos
                    
                    // ✅ VALIDAÇÕES ULTRA PERMISSIVAS
                    minFaceSizeRatio = 0.08f,              // Reduzido para 0.08f
                    maxFaceSizeRatio = 0.95f,              // Aumentado para 0.95f
                    minEyeDistance = 12f,                 // Reduzido para 12f
                    minBrightness = 0.08f,                // Reduzido para 0.08f
                    maxBrightness = 0.92f,                // Aumentado para 0.92f
                    minContrast = 0.08f                   // Reduzido para 0.08f
                )
            }
            
            PerformanceLevel.HIGH -> {
                Log.d(TAG, "🎛️ Configurando para dispositivo de ALTO desempenho")
                AdaptiveConfig(
                    // ✅ THRESHOLDS ULTRA PERMISSIVOS para dispositivos potentes
                    minSimilarityThreshold = 0.3f,        // Reduzido para 30%
                    maxEuclideanDistance = 1.0f,          // Aumentado para 1.0f
                    requiredConfidence = 0.4f,            // Reduzido para 40%
                    
                    // ✅ QUALIDADE DE IMAGEM ALTA
                    imageQuality = ImageQuality.HIGH,
                    maxImageSize = 300,                   // Mantido em 300
                    compressionQuality = 80,              // Mantido em 80
                    
                    // ✅ PROCESSAMENTO OTIMIZADO
                    useTensorFlowOptimizations = true,
                    enableFallbackMode = false,           // Desabilitado para dispositivos potentes
                    maxProcessingTime = 2000L,            // 2 segundos
                    
                    // ✅ VALIDAÇÕES ULTRA PERMISSIVAS
                    minFaceSizeRatio = 0.1f,             // Reduzido para 0.1f
                    maxFaceSizeRatio = 0.9f,             // Aumentado para 0.9f
                    minEyeDistance = 15f,                 // Reduzido para 15f
                    minBrightness = 0.1f,                // Reduzido para 0.1f
                    maxBrightness = 0.9f,                // Aumentado para 0.9f
                    minContrast = 0.1f                   // Reduzido para 0.1f
                )
            }
        }
    }
    
    /**
     * 📊 OBTER INFORMAÇÕES DO DISPOSITIVO
     */
    fun getDeviceInfo(): DeviceInfo {
        return DeviceInfo(
            performanceLevel = performanceLevel ?: PerformanceLevel.LOW,
            deviceScore = deviceScore,
            memoryInfo = getMemoryInfo(),
            cpuInfo = getCpuInfo(),
            androidVersion = Build.VERSION.SDK_INT,
            androidRelease = Build.VERSION.RELEASE
        )
    }
    
    /**
     * 🔧 VERIFICAR SE O DISPOSITIVO SUPORTA RECONHECIMENTO FACIAL
     */
    fun isFaceRecognitionSupported(): Boolean {
        val level = performanceLevel ?: PerformanceLevel.LOW
        val memoryInfo = getMemoryInfo()
        
        // ✅ VERIFICAÇÕES MÍNIMAS
        val hasMinimumMemory = memoryInfo.total >= 1024 * 1024 * 1024L // 1GB mínimo
        val hasMinimumAndroid = Build.VERSION.SDK_INT >= 21 // API 21 mínimo
        val hasMinimumCores = Runtime.getRuntime().availableProcessors() >= 2 // 2 cores mínimo
        
        val isSupported = hasMinimumMemory && hasMinimumAndroid && hasMinimumCores
        
        Log.d(TAG, "🔍 Suporte ao reconhecimento facial: $isSupported")
        Log.d(TAG, "📊 Memória mínima: $hasMinimumMemory, Android mínimo: $hasMinimumAndroid, Cores mínimos: $hasMinimumCores")
        
        return isSupported
    }
    
    // ========== CLASSES DE DADOS ==========
    
    data class MemoryInfo(
        val total: Long,
        val available: Long,
        val threshold: Long
    ) {
        val totalGB: Float get() = total / (1024f * 1024f * 1024f)
        val availableGB: Float get() = available / (1024f * 1024f * 1024f)
    }
    
    data class CpuInfo(
        val cores: Int,
        val architecture: String
    )
    
    data class DeviceInfo(
        val performanceLevel: PerformanceLevel,
        val deviceScore: Float,
        val memoryInfo: MemoryInfo,
        val cpuInfo: CpuInfo,
        val androidVersion: Int,
        val androidRelease: String
    )
    
    data class AdaptiveConfig(
        val minSimilarityThreshold: Float,
        val maxEuclideanDistance: Float,
        val requiredConfidence: Float,
        val imageQuality: ImageQuality,
        val maxImageSize: Int,
        val compressionQuality: Int,
        val useTensorFlowOptimizations: Boolean,
        val enableFallbackMode: Boolean,
        val maxProcessingTime: Long,
        val minFaceSizeRatio: Float,
        val maxFaceSizeRatio: Float,
        val minEyeDistance: Float,
        val minBrightness: Float,
        val maxBrightness: Float,
        val minContrast: Float
    )
    
    enum class ImageQuality {
        LOW,    // Para dispositivos fracos
        MEDIUM, // Para dispositivos intermediários
        HIGH    // Para dispositivos potentes
    }
} 