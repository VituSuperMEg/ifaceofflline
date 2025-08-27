package com.example.iface_offilne.helpers

import android.graphics.*
import android.util.Log
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceLandmark
import kotlin.math.*

/**
 * 🎯 HELPER PARA PRÉ-PROCESSAMENTO AVANÇADO DE FACES
 * 
 * Implementa todas as técnicas de pré-processamento para melhorar
 * a precisão do reconhecimento facial:
 * - Redimensionamento para tamanho do modelo
 * - Normalização de pixels
 * - Alinhamento baseado em landmarks
 * - Equalização de iluminação
 * - Data augmentation
 */
class FacePreprocessingHelper {
    
    companion object {
        private const val TAG = "FacePreprocessingHelper"
        
        // ✅ CONFIGURAÇÕES DO MODELO MOBILEFACENET
        const val MODEL_INPUT_SIZE = 112
        const val MODEL_INPUT_CHANNELS = 3
        
        // ✅ THRESHOLDS CONFIGURÁVEIS
        var SIMILARITY_THRESHOLD = 0.85f
        var EUCLIDEAN_THRESHOLD = 1.2f
        var CONFIDENCE_THRESHOLD = 0.90f
        
        // ✅ CONFIGURAÇÕES DE PRÉ-PROCESSAMENTO
        const val EYE_ALIGNMENT_ANGLE = 0f // Ângulo para alinhar olhos
        const val BRIGHTNESS_TARGET = 128f // Brilho alvo para equalização
        const val CONTRAST_FACTOR = 1.2f // Fator de contraste
    }
    
    /**
     * 🎯 PRÉ-PROCESSAMENTO COMPLETO DA FACE
     * 
     * Aplica todas as técnicas de pré-processamento:
     * 1. Detecção de landmarks
     * 2. Alinhamento da face
     * 3. Equalização de iluminação
     * 4. Redimensionamento
     * 5. Normalização
     */
    fun preprocessFace(originalBitmap: Bitmap, face: Face? = null): Bitmap? {
        return try {
            Log.d(TAG, "🔄 Iniciando pré-processamento completo da face")
            
            // ✅ 1. VALIDAR BITMAP DE ENTRADA
            if (originalBitmap.isRecycled || originalBitmap.width <= 0 || originalBitmap.height <= 0) {
                Log.e(TAG, "❌ Bitmap inválido para pré-processamento")
                return null
            }
            
            Log.d(TAG, "📐 Bitmap original: ${originalBitmap.width}x${originalBitmap.height}")
            
            // ✅ 2. ALINHAR FACE SE LANDMARKS DISPONÍVEIS
            val alignedBitmap = if (face != null && hasValidLandmarks(face)) {
                alignFaceWithLandmarks(originalBitmap, face)
            } else {
                Log.d(TAG, "⚠️ Face sem landmarks válidos - usando bitmap original")
                originalBitmap
            }
            
            // ✅ 3. EQUALIZAR ILUMINAÇÃO
            val equalizedBitmap = equalizeIllumination(alignedBitmap)
            
            // ✅ 4. REDIMENSIONAR PARA TAMANHO DO MODELO
            val resizedBitmap = resizeToModelSize(equalizedBitmap)
            
            // ✅ 5. NORMALIZAR PIXELS
            val normalizedBitmap = normalizePixels(resizedBitmap)
            
            Log.d(TAG, "✅ Pré-processamento concluído: ${normalizedBitmap.width}x${normalizedBitmap.height}")
            
            normalizedBitmap
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro no pré-processamento: ${e.message}")
            null
        }
    }
    
    /**
     * 🎯 VERIFICAR SE A FACE TEM LANDMARKS VÁLIDOS
     */
    private fun hasValidLandmarks(face: Face): Boolean {
        return try {
            val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)
            val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)
            
            leftEye != null && rightEye != null
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Erro ao verificar landmarks: ${e.message}")
            false
        }
    }
    
    /**
     * 🎯 ALINHAR FACE COM BASE NOS LANDMARKS
     * 
     * Alinha a face horizontalmente baseado na posição dos olhos
     * para melhorar a precisão do reconhecimento
     */
    private fun alignFaceWithLandmarks(bitmap: Bitmap, face: Face): Bitmap {
        return try {
            val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)
            val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)
            
            if (leftEye == null || rightEye == null) {
                Log.w(TAG, "⚠️ Landmarks dos olhos não disponíveis")
                return bitmap
            }
            
            // ✅ CALCULAR ÂNGULO DE ROTAÇÃO
            val eyeAngle = calculateEyeAngle(leftEye.position, rightEye.position)
            
            if (abs(eyeAngle) < 2f) {
                Log.d(TAG, "✅ Face já está alinhada (ângulo: ${String.format("%.1f", eyeAngle)}°)")
                return bitmap
            }
            
            Log.d(TAG, "🔄 Alinhando face: ${String.format("%.1f", eyeAngle)}°")
            
            // ✅ APLICAR ROTAÇÃO
            val matrix = Matrix().apply {
                setRotate(-eyeAngle, bitmap.width / 2f, bitmap.height / 2f)
            }
            
            val alignedBitmap = Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
            )
            
            Log.d(TAG, "✅ Face alinhada com sucesso")
            alignedBitmap
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao alinhar face: ${e.message}")
            bitmap
        }
    }
    
    /**
     * 🎯 CALCULAR ÂNGULO DOS OLHOS
     */
    private fun calculateEyeAngle(leftEye: android.graphics.PointF, rightEye: android.graphics.PointF): Float {
        val deltaY = rightEye.y - leftEye.y
        val deltaX = rightEye.x - leftEye.x
        return atan2(deltaY, deltaX) * (180f / PI.toFloat())
    }
    
    /**
     * 🎯 EQUALIZAR ILUMINAÇÃO
     * 
     * Aplica equalização de histograma e ajuste de contraste
     * para normalizar a iluminação da face
     */
    private fun equalizeIllumination(bitmap: Bitmap): Bitmap {
        return try {
            Log.d(TAG, "🔄 Equalizando iluminação")
            
            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            
            // ✅ CALCULAR HISTOGRAMA
            val histogram = IntArray(256) { 0 }
            for (pixel in pixels) {
                val gray = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3
                histogram[gray]++
            }
            
            // ✅ CALCULAR FUNÇÃO DE TRANSFORMAÇÃO
            val cdf = IntArray(256) { 0 }
            var sum = 0
            for (i in 0 until 256) {
                sum += histogram[i]
                cdf[i] = sum
            }
            
            // ✅ APLICAR EQUALIZAÇÃO
            val equalizedPixels = IntArray(pixels.size)
            val totalPixels = pixels.size
            
            for (i in pixels.indices) {
                val pixel = pixels[i]
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                
                val gray = (r + g + b) / 3
                val equalizedGray = (cdf[gray] * 255) / totalPixels
                
                // ✅ AJUSTAR CONTRASTE
                val adjustedGray = ((equalizedGray - 128) * CONTRAST_FACTOR + 128).toInt().coerceIn(0, 255)
                
                equalizedPixels[i] = Color.rgb(adjustedGray, adjustedGray, adjustedGray)
            }
            
            val equalizedBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            equalizedBitmap.setPixels(equalizedPixels, 0, width, 0, 0, width, height)
            
            Log.d(TAG, "✅ Iluminação equalizada")
            equalizedBitmap
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro na equalização: ${e.message}")
            bitmap
        }
    }
    
    /**
     * 🎯 REDIMENSIONAR PARA TAMANHO DO MODELO
     */
    private fun resizeToModelSize(bitmap: Bitmap): Bitmap {
        return try {
            if (bitmap.width == MODEL_INPUT_SIZE && bitmap.height == MODEL_INPUT_SIZE) {
                Log.d(TAG, "✅ Bitmap já tem tamanho correto")
                return bitmap
            }
            
            Log.d(TAG, "🔄 Redimensionando para ${MODEL_INPUT_SIZE}x${MODEL_INPUT_SIZE}")
            
            val resizedBitmap = Bitmap.createScaledBitmap(
                bitmap, MODEL_INPUT_SIZE, MODEL_INPUT_SIZE, true
            )
            
            Log.d(TAG, "✅ Redimensionamento concluído")
            resizedBitmap
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro no redimensionamento: ${e.message}")
            bitmap
        }
    }
    
    /**
     * 🎯 NORMALIZAR PIXELS PARA [0, 1]
     */
    private fun normalizePixels(bitmap: Bitmap): Bitmap {
        return try {
            Log.d(TAG, "🔄 Normalizando pixels")
            
            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            
            val normalizedPixels = IntArray(pixels.size)
            
            for (i in pixels.indices) {
                val pixel = pixels[i]
                val r = Color.red(pixel) / 255f
                val g = Color.green(pixel) / 255f
                val b = Color.blue(pixel) / 255f
                
                // ✅ CONVERTER DE VOLTA PARA [0, 255]
                val normalizedR = (r * 255).toInt().coerceIn(0, 255)
                val normalizedG = (g * 255).toInt().coerceIn(0, 255)
                val normalizedB = (b * 255).toInt().coerceIn(0, 255)
                
                normalizedPixels[i] = Color.rgb(normalizedR, normalizedG, normalizedB)
            }
            
            val normalizedBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            normalizedBitmap.setPixels(normalizedPixels, 0, width, 0, 0, width, height)
            
            Log.d(TAG, "✅ Pixels normalizados")
            normalizedBitmap
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro na normalização: ${e.message}")
            bitmap
        }
    }
    
    /**
     * 🎯 DATA AUGMENTATION
     * 
     * Aplica técnicas de data augmentation para melhorar
     * a robustez do reconhecimento
     */
    fun applyDataAugmentation(bitmap: Bitmap): List<Bitmap> {
        val augmentedBitmaps = mutableListOf<Bitmap>()
        
        try {
            Log.d(TAG, "🔄 Aplicando data augmentation")
            
            // ✅ 1. ROTAÇÃO PEQUENA
            val rotatedBitmap = applyRotation(bitmap, 5f)
            if (rotatedBitmap != null) augmentedBitmaps.add(rotatedBitmap)
            
            // ✅ 2. AJUSTE DE BRILHO
            val brightBitmap = adjustBrightness(bitmap, 1.1f)
            if (brightBitmap != null) augmentedBitmaps.add(brightBitmap)
            
            val darkBitmap = adjustBrightness(bitmap, 0.9f)
            if (darkBitmap != null) augmentedBitmaps.add(darkBitmap)
            
            // ✅ 3. AJUSTE DE CONTRASTE
            val contrastBitmap = adjustContrast(bitmap, 1.1f)
            if (contrastBitmap != null) augmentedBitmaps.add(contrastBitmap)
            
            // ✅ 4. RUÍDO GAUSSIANO
            val noisyBitmap = addGaussianNoise(bitmap, 5f)
            if (noisyBitmap != null) augmentedBitmaps.add(noisyBitmap)
            
            Log.d(TAG, "✅ Data augmentation concluído: ${augmentedBitmaps.size} variações")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro no data augmentation: ${e.message}")
        }
        
        return augmentedBitmaps
    }
    
    /**
     * 🎯 APLICAR ROTAÇÃO
     */
    private fun applyRotation(bitmap: Bitmap, angle: Float): Bitmap? {
        return try {
            val matrix = Matrix().apply {
                setRotate(angle, bitmap.width / 2f, bitmap.height / 2f)
            }
            
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro na rotação: ${e.message}")
            null
        }
    }
    
    /**
     * 🎯 AJUSTAR BRILHO
     */
    private fun adjustBrightness(bitmap: Bitmap, factor: Float): Bitmap? {
        return try {
            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            
            val adjustedPixels = IntArray(pixels.size)
            
            for (i in pixels.indices) {
                val pixel = pixels[i]
                val r = (Color.red(pixel) * factor).toInt().coerceIn(0, 255)
                val g = (Color.green(pixel) * factor).toInt().coerceIn(0, 255)
                val b = (Color.blue(pixel) * factor).toInt().coerceIn(0, 255)
                
                adjustedPixels[i] = Color.rgb(r, g, b)
            }
            
            val adjustedBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            adjustedBitmap.setPixels(adjustedPixels, 0, width, 0, 0, width, height)
            
            adjustedBitmap
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro no ajuste de brilho: ${e.message}")
            null
        }
    }
    
    /**
     * 🎯 AJUSTAR CONTRASTE
     */
    private fun adjustContrast(bitmap: Bitmap, factor: Float): Bitmap? {
        return try {
            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            
            val adjustedPixels = IntArray(pixels.size)
            
            for (i in pixels.indices) {
                val pixel = pixels[i]
                val r = ((Color.red(pixel) - 128) * factor + 128).toInt().coerceIn(0, 255)
                val g = ((Color.green(pixel) - 128) * factor + 128).toInt().coerceIn(0, 255)
                val b = ((Color.blue(pixel) - 128) * factor + 128).toInt().coerceIn(0, 255)
                
                adjustedPixels[i] = Color.rgb(r, g, b)
            }
            
            val adjustedBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            adjustedBitmap.setPixels(adjustedPixels, 0, width, 0, 0, width, height)
            
            adjustedBitmap
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro no ajuste de contraste: ${e.message}")
            null
        }
    }
    
    /**
     * 🎯 ADICIONAR RUÍDO GAUSSIANO
     */
    private fun addGaussianNoise(bitmap: Bitmap, intensity: Float): Bitmap? {
        return try {
            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            
            val noisyPixels = IntArray(pixels.size)
            val random = java.util.Random()
            
            for (i in pixels.indices) {
                val pixel = pixels[i]
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                
                // ✅ GERAR RUÍDO GAUSSIANO
                val noiseR = (random.nextGaussian() * intensity).toInt()
                val noiseG = (random.nextGaussian() * intensity).toInt()
                val noiseB = (random.nextGaussian() * intensity).toInt()
                
                val noisyR = (r + noiseR).toInt().coerceIn(0, 255)
                val noisyG = (g + noiseG).toInt().coerceIn(0, 255)
                val noisyB = (b + noiseB).toInt().coerceIn(0, 255)
                
                noisyPixels[i] = Color.rgb(noisyR, noisyG, noisyB)
            }
            
            val noisyBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            noisyBitmap.setPixels(noisyPixels, 0, width, 0, 0, width, height)
            
            noisyBitmap
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro no ruído gaussiano: ${e.message}")
            null
        }
    }
} 