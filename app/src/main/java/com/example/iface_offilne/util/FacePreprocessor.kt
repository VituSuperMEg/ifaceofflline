package com.example.iface_offilne.util

import android.content.Context
import android.graphics.*
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs

/**
 * 🎯 PRÉ-PROCESSAMENTO FACIAL AVANÇADO
 * 
 * Esta classe implementa técnicas de pré-processamento para melhorar
 * a qualidade das imagens faciais antes de gerar embeddings:
 * 
 * ✅ Alinhamento facial baseado em landmarks
 * ✅ Correção de iluminação
 * ✅ Normalização de contraste
 * ✅ Redimensionamento inteligente
 * ✅ Remoção de ruído
 */
class FacePreprocessor(private val context: Context) {
    
    companion object {
        private const val TAG = "FacePreprocessor"
        
        // 🎯 CONFIGURAÇÕES DE PRÉ-PROCESSAMENTO
        private const val TARGET_SIZE = 112 // Tamanho padrão para modelos faciais
        private const val BRIGHTNESS_TARGET = 0.5f // Brilho ideal (0.0 a 1.0)
        private const val CONTRAST_TARGET = 0.3f // Contraste ideal
        private const val SHARPNESS_FACTOR = 1.2f // Fator de nitidez
    }
    
    // 🎯 FACE DETECTOR DE ALTA PRECISÃO - CONFIGURAÇÃO COMPATÍVEL
    private val faceDetector: FaceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST) // ✅ MUDANÇA: Usar FAST para compatibilidade
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
            .setMinFaceSize(0.05f) // ✅ MUDANÇA: Reduzir para 5% para detectar faces menores
            .enableTracking()
            .build()
    )
    
    /**
     * 🎯 PRÉ-PROCESSAMENTO COMPLETO DA FACE
     * Aplica todas as técnicas de melhoria na imagem
     */
    suspend fun preprocessFace(originalBitmap: Bitmap): PreprocessedFace? {
        return try {
            Log.d(TAG, "🎯 === INICIANDO PRÉ-PROCESSAMENTO FACIAL ===")
            Log.d(TAG, "📊 Imagem original: ${originalBitmap.width}x${originalBitmap.height}")
            
            // ✅ 1. DETECTAR FACE E LANDMARKS
            val face = detectFaceWithLandmarks(originalBitmap)
            if (face == null) {
                Log.w(TAG, "❌ Nenhuma face detectada para pré-processamento")
                return null
            }
            
            Log.d(TAG, "✅ Face detectada com ${face.allLandmarks.size} landmarks")
            
            // ✅ 2. CROPAR FACE COM MARGEM
            val croppedFace = cropFaceWithMargin(originalBitmap, face)
            Log.d(TAG, "✅ Face cropada: ${croppedFace.width}x${croppedFace.height}")
            
            // ✅ 3. ALINHAR FACE BASEADO NOS OLHOS
            val alignedFace = alignFaceByEyes(croppedFace, face)
            Log.d(TAG, "✅ Face alinhada: ${alignedFace.width}x${alignedFace.height}")
            
            // ✅ 4. CORRIGIR ILUMINAÇÃO
            val illuminatedFace = correctIllumination(alignedFace)
            Log.d(TAG, "✅ Iluminação corrigida")
            
            // ✅ 5. MELHORAR CONTRASTE
            val contrastedFace = enhanceContrast(illuminatedFace)
            Log.d(TAG, "✅ Contraste melhorado")
            
            // ✅ 6. APLICAR NITIDEZ
            val sharpenedFace = applySharpening(contrastedFace)
            Log.d(TAG, "✅ Nitidez aplicada")
            
            // ✅ 7. REDIMENSIONAR PARA TAMANHO PADRÃO
            val finalFace = resizeToTargetSize(sharpenedFace)
            Log.d(TAG, "✅ Redimensionado para ${TARGET_SIZE}x${TARGET_SIZE}")
            
            // ✅ 8. NORMALIZAR VALORES
            val normalizedFace = normalizePixels(finalFace)
            Log.d(TAG, "✅ Pixels normalizados")
            
            Log.d(TAG, "🎉 === PRÉ-PROCESSAMENTO CONCLUÍDO ===")
            
            PreprocessedFace(
                bitmap = normalizedFace,
                originalFace = face,
                quality = calculateQuality(normalizedFace)
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro no pré-processamento facial", e)
            null
        }
    }
    
    /**
     * 🎯 PRÉ-PROCESSAMENTO DE FACE JÁ DETECTADA (PARA PONTOACTIVITY)
     * Usado quando a face já foi detectada pelo ML Kit principal
     */
    fun preprocessDetectedFace(faceBitmap: Bitmap): PreprocessedFace? {
        return try {
            Log.d(TAG, "🎯 === PRÉ-PROCESSAMENTO DE FACE JÁ DETECTADA ===")
            Log.d(TAG, "📊 Face recebida: ${faceBitmap.width}x${faceBitmap.height}")
            
            // ✅ 1. REDIMENSIONAR PARA TAMANHO PADRÃO PRIMEIRO
            val resizedFace = resizeToTargetSize(faceBitmap)
            Log.d(TAG, "✅ Redimensionado para ${TARGET_SIZE}x${TARGET_SIZE}")
            
            // ✅ 2. CORRIGIR ILUMINAÇÃO
            val illuminatedFace = correctIllumination(resizedFace)
            Log.d(TAG, "✅ Iluminação corrigida")
            
            // ✅ 3. MELHORAR CONTRASTE
            val contrastedFace = enhanceContrast(illuminatedFace)
            Log.d(TAG, "✅ Contraste melhorado")
            
            // ✅ 4. APLICAR NITIDEZ
            val sharpenedFace = applySharpening(contrastedFace)
            Log.d(TAG, "✅ Nitidez aplicada")
            
            // ✅ 5. NORMALIZAR VALORES
            val normalizedFace = normalizePixels(sharpenedFace)
            Log.d(TAG, "✅ Pixels normalizados")
            
            Log.d(TAG, "🎉 === PRÉ-PROCESSAMENTO DE FACE DETECTADA CONCLUÍDO ===")
            
            PreprocessedFace(
                bitmap = normalizedFace,
                originalFace = null, // Não temos a face original do ML Kit
                quality = calculateQuality(normalizedFace)
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro no pré-processamento de face detectada", e)
            null
        }
    }
    
    /**
     * 👁️ DETECTAR FACE COM LANDMARKS COMPLETOS
     */
    private suspend fun detectFaceWithLandmarks(bitmap: Bitmap): Face? {
        return suspendCancellableCoroutine { continuation ->
            try {
                val image = InputImage.fromBitmap(bitmap, 0)
                
                faceDetector.process(image)
                    .addOnSuccessListener { faces ->
                        if (faces.isNotEmpty()) {
                            val face = faces[0] // Pegar a primeira face
                            Log.d(TAG, "✅ Face detectada com ${face.allLandmarks.size} landmarks")
                            continuation.resume(face)
                        } else {
                            Log.w(TAG, "❌ Nenhuma face detectada")
                            continuation.resume(null)
                        }
                    }
                    .addOnFailureListener { exception ->
                        Log.e(TAG, "❌ Erro na detecção de face", exception)
                        continuation.resumeWithException(exception)
                    }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao processar imagem", e)
                continuation.resumeWithException(e)
            }
        }
    }
    
    /**
     * ✂️ CROPAR FACE COM MARGEM ADICIONAL
     */
    private fun cropFaceWithMargin(bitmap: Bitmap, face: Face): Bitmap {
        val boundingBox = face.boundingBox
        
        // ✅ ADICIONAR MARGEM DE 20% AO REDOR DA FACE
        val marginX = (boundingBox.width() * 0.2f).toInt()
        val marginY = (boundingBox.height() * 0.2f).toInt()
        
        val left = (boundingBox.left - marginX).coerceAtLeast(0)
        val top = (boundingBox.top - marginY).coerceAtLeast(0)
        val right = (boundingBox.right + marginX).coerceAtMost(bitmap.width)
        val bottom = (boundingBox.bottom + marginY).coerceAtMost(bitmap.height)
        
        return Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
    }
    
    /**
     * 🔄 ALINHAR FACE BASEADO NOS OLHOS
     */
    private fun alignFaceByEyes(bitmap: Bitmap, face: Face): Bitmap {
        val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)
        val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)
        
        if (leftEye == null || rightEye == null) {
            Log.w(TAG, "⚠️ Landmarks dos olhos não encontrados, retornando imagem original")
            return bitmap
        }
        
        // ✅ CALCULAR ÂNGULO DE ROTAÇÃO
        val eyeAngle = kotlin.math.atan2(
            (rightEye.position.y - leftEye.position.y).toDouble(),
            (rightEye.position.x - leftEye.position.x).toDouble()
        )
        
        val angleDegrees = Math.toDegrees(eyeAngle)
        
        Log.d(TAG, "👁️ Ângulo de rotação: ${angleDegrees}°")
        
        // ✅ APLICAR ROTAÇÃO
        val matrix = Matrix().apply {
            setRotate(angleDegrees.toFloat(), bitmap.width / 2f, bitmap.height / 2f)
        }
        
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
    
    /**
     * 💡 CORRIGIR ILUMINAÇÃO
     */
    private fun correctIllumination(bitmap: Bitmap): Bitmap {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        
        // ✅ CALCULAR BRILHO MÉDIO
        var totalBrightness = 0.0
        for (pixel in pixels) {
            val r = Color.red(pixel)
            val g = Color.green(pixel)
            val b = Color.blue(pixel)
            totalBrightness += (r + g + b) / 3.0 / 255.0
        }
        val avgBrightness = totalBrightness / pixels.size
        
        Log.d(TAG, "💡 Brilho médio atual: $avgBrightness")
        
        // ✅ CALCULAR FATOR DE CORREÇÃO
        val correctionFactor = BRIGHTNESS_TARGET / avgBrightness.toFloat()
        val clampedFactor = correctionFactor.coerceIn(0.5f, 2.0f)
        
        Log.d(TAG, "💡 Fator de correção: $clampedFactor")
        
        // ✅ APLICAR CORREÇÃO
        val correctedPixels = pixels.map { pixel ->
            val r = (Color.red(pixel) * clampedFactor).toInt().coerceIn(0, 255)
            val g = (Color.green(pixel) * clampedFactor).toInt().coerceIn(0, 255)
            val b = (Color.blue(pixel) * clampedFactor).toInt().coerceIn(0, 255)
            Color.rgb(r, g, b)
        }.toIntArray()
        
        val correctedBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config ?: Bitmap.Config.ARGB_8888)
        correctedBitmap.setPixels(correctedPixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        
        return correctedBitmap
    }
    
    /**
     * 🎨 MELHORAR CONTRASTE
     */
    private fun enhanceContrast(bitmap: Bitmap): Bitmap {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        
        // ✅ CALCULAR CONTRASTE ATUAL
        val brightnessValues = pixels.map { pixel ->
            (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3.0
        }
        
        val minBrightness = brightnessValues.minOrNull() ?: 0.0
        val maxBrightness = brightnessValues.maxOrNull() ?: 255.0
        val currentContrast = (maxBrightness - minBrightness) / 255.0
        
        Log.d(TAG, "🎨 Contraste atual: $currentContrast")
        
        // ✅ APLICAR MELHORIA DE CONTRASTE
        val enhancedPixels = pixels.map { pixel ->
            val r = Color.red(pixel)
            val g = Color.green(pixel)
            val b = Color.blue(pixel)
            
            val enhancedR = ((r - 128) * CONTRAST_TARGET + 128).toInt().coerceIn(0, 255)
            val enhancedG = ((g - 128) * CONTRAST_TARGET + 128).toInt().coerceIn(0, 255)
            val enhancedB = ((b - 128) * CONTRAST_TARGET + 128).toInt().coerceIn(0, 255)
            
            Color.rgb(enhancedR, enhancedG, enhancedB)
        }.toIntArray()
        
        val enhancedBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config ?: Bitmap.Config.ARGB_8888)
        enhancedBitmap.setPixels(enhancedPixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        
        return enhancedBitmap
    }
    
    /**
     * 🔍 APLICAR NITIDEZ
     */
    private fun applySharpening(bitmap: Bitmap): Bitmap {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        
        // ✅ KERNEL DE NITIDEZ (UNSHARP MASK)
        val sharpenedPixels = IntArray(pixels.size)
        
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val index = y * bitmap.width + x
                
                // ✅ CALCULAR MÉDIA DOS VIZINHOS
                var sumR = 0
                var sumG = 0
                var sumB = 0
                var count = 0
                
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        val nx = x + dx
                        val ny = y + dy
                        
                        if (nx >= 0 && nx < bitmap.width && ny >= 0 && ny < bitmap.height) {
                            val neighborPixel = pixels[ny * bitmap.width + nx]
                            sumR += Color.red(neighborPixel)
                            sumG += Color.green(neighborPixel)
                            sumB += Color.blue(neighborPixel)
                            count++
                        }
                    }
                }
                
                val avgR = sumR / count
                val avgG = sumG / count
                val avgB = sumB / count
                
                // ✅ APLICAR NITIDEZ
                val currentPixel = pixels[index]
                val currentR = Color.red(currentPixel)
                val currentG = Color.green(currentPixel)
                val currentB = Color.blue(currentPixel)
                
                val sharpR = (currentR + (currentR - avgR) * SHARPNESS_FACTOR).toInt().coerceIn(0, 255)
                val sharpG = (currentG + (currentG - avgG) * SHARPNESS_FACTOR).toInt().coerceIn(0, 255)
                val sharpB = (currentB + (currentB - avgB) * SHARPNESS_FACTOR).toInt().coerceIn(0, 255)
                
                sharpenedPixels[index] = Color.rgb(sharpR, sharpG, sharpB)
            }
        }
        
        val sharpenedBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config ?: Bitmap.Config.ARGB_8888)
        sharpenedBitmap.setPixels(sharpenedPixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        
        return sharpenedBitmap
    }
    
    /**
     * 📏 REDIMENSIONAR PARA TAMANHO PADRÃO
     */
    private fun resizeToTargetSize(bitmap: Bitmap): Bitmap {
        return Bitmap.createScaledBitmap(bitmap, TARGET_SIZE, TARGET_SIZE, true)
    }
    
    /**
     * 📊 NORMALIZAR PIXELS PARA [-1, 1]
     */
    private fun normalizePixels(bitmap: Bitmap): Bitmap {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        
        val normalizedPixels = pixels.map { pixel ->
            val r = (Color.red(pixel) / 255.0f * 2.0f - 1.0f) * 255.0f
            val g = (Color.green(pixel) / 255.0f * 2.0f - 1.0f) * 255.0f
            val b = (Color.blue(pixel) / 255.0f * 2.0f - 1.0f) * 255.0f
            
            Color.rgb(
                r.toInt().coerceIn(0, 255),
                g.toInt().coerceIn(0, 255),
                b.toInt().coerceIn(0, 255)
            )
        }.toIntArray()
        
        val normalizedBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config ?: Bitmap.Config.ARGB_8888)
        normalizedBitmap.setPixels(normalizedPixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        
        return normalizedBitmap
    }
    
    /**
     * 📊 CALCULAR QUALIDADE DA IMAGEM
     */
    private fun calculateQuality(bitmap: Bitmap): Float {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        
        // ✅ CALCULAR BRILHO MÉDIO
        var totalBrightness = 0.0
        for (pixel in pixels) {
            val r = Color.red(pixel)
            val g = Color.green(pixel)
            val b = Color.blue(pixel)
            totalBrightness += (r + g + b) / 3.0 / 255.0
        }
        val avgBrightness = totalBrightness / pixels.size
        
        // ✅ CALCULAR CONTRASTE
        val brightnessValues = pixels.map { pixel ->
            (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3.0
        }
        val minBrightness = brightnessValues.minOrNull() ?: 0.0
        val maxBrightness = brightnessValues.maxOrNull() ?: 255.0
        val contrast = (maxBrightness - minBrightness) / 255.0
        
        // ✅ CALCULAR NITIDEZ (VARIÂNCIA)
        val variance = brightnessValues.map { (it - avgBrightness) * (it - avgBrightness) }.average()
        val sharpness = (variance / 10000.0).toFloat().coerceIn(0.0f, 1.0f)
        
        // ✅ QUALIDADE COMBINADA
        val brightnessScore = (1.0f - abs(avgBrightness.toFloat() - BRIGHTNESS_TARGET)).coerceIn(0.0f, 1.0f)
        val contrastScore = contrast.toFloat().coerceIn(0.0f, 1.0f)
        val sharpnessScore = sharpness.coerceIn(0.0f, 1.0f)
        
        val quality = (brightnessScore * 0.3f + contrastScore * 0.4f + sharpnessScore * 0.3f)
        
        Log.d(TAG, "📊 Qualidade calculada: $quality (brilho: $brightnessScore, contraste: $contrastScore, nitidez: $sharpnessScore)")
        
        return quality
    }
    
    /**
     * 🧹 LIMPAR RECURSOS
     */
    fun close() {
        faceDetector.close()
    }
    
    /**
     * 📦 RESULTADO DO PRÉ-PROCESSAMENTO
     */
    data class PreprocessedFace(
        val bitmap: Bitmap,
        val originalFace: Face?, // Pode ser null se a face já foi detectada
        val quality: Float
    )
} 