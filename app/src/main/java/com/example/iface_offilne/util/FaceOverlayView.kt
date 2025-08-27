package com.example.iface_offilne.util

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.min
import kotlin.math.max

class FaceOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var boundingBox: Rect? = null
    private var faceQuality: FaceQuality = FaceQuality.UNKNOWN
    private var isFaceStable: Boolean = false
    private var imageWidth: Int = 0
    private var imageHeight: Int = 0
    private var imageRotation: Int = 0 // ✅ NOVO: Adicionar rotação da imagem
    
    // 🎨 Cores para feedback visual
    private val colorRed = Color.parseColor("#FF4444")      // Vermelho - muito pequeno/grande
    private val colorYellow = Color.parseColor("#FFAA00")   // Amarelo - fora do centro/instável
    private val colorGreen = Color.parseColor("#44FF44")    // Verde - perfeito
    private val colorBlue = Color.parseColor("#4488FF")     // Azul - aguardando
    
    // 📐 Dimensões do quadrado de alinhamento - ULTRA PRECISO
    private val alignmentSquareSize = 0.70f // 70% da tela - PRECISO para melhor alinhamento
    private val minFaceSizeRatio = 0.15f // Face deve ocupar pelo menos 15% da área - PRECISO para qualidade
    private val maxFaceSizeRatio = 0.60f // Face não pode ocupar mais que 60% da área - PRECISO para evitar distorção
    
    // 🎨 Paint para desenho
    private val paint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }
    
    // ✍️ Paint para texto sutil
    private val textPaint = Paint().apply {
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
        color = Color.WHITE
        alpha = 180 // Transparência sutil
        textSize = 40f // Tamanho pequeno
    }
    
    enum class FaceQuality {
        UNKNOWN,    // Azul - aguardando
        TOO_SMALL,  // Vermelho - muito pequena
        TOO_BIG,    // Vermelho - muito grande
        OFF_CENTER, // Amarelo - fora do centro
        UNSTABLE,   // Amarelo - instável
        GOOD,       // Verde claro - boa qualidade
        PERFECT     // Verde - perfeita
    }

    fun setBoundingBox(rect: Rect, imageWidth: Int, imageHeight: Int, rotation: Int = 0) {
        boundingBox = rect
        this.imageWidth = imageWidth
        this.imageHeight = imageHeight
        this.imageRotation = rotation // ✅ NOVO: Armazenar rotação
        invalidate()
    }
    
    fun setFaceQuality(quality: FaceQuality, stableCount: Int = 0, isStable: Boolean = false) {
        this.faceQuality = quality
        this.isFaceStable = isStable
        invalidate()
    }
    
    fun setCaptureMode(enabled: Boolean) {
        // Não fazer nada - apenas o quadrado
        invalidate()
    }
    
    fun setCaptureProgress(progress: Float) {
        // Não fazer nada - apenas o quadrado
        invalidate()
    }

    fun clear() {
        boundingBox = null
        faceQuality = FaceQuality.UNKNOWN
        isFaceStable = false
        invalidate()
    }

    /** Verifica se a face está bem posicionada no quadrado de alinhamento */
    fun isFaceInAlignmentSquare(faceRect: Rect): Boolean {
        // ✅ PRECISO: Verificar se a face está dentro do quadrado de alinhamento
        if (boundingBox == null || imageWidth <= 0 || imageHeight <= 0) {
            return false
        }
        
        // ✅ Converter coordenadas da imagem para coordenadas da tela com correção de rotação
        val (scaleX, scaleY) = getCorrectedScales()
        val (faceCenterX, faceCenterY) = getCorrectedFacePosition(faceRect, scaleX, scaleY)
        
        // ✅ Calcular posição do quadrado de alinhamento
        val centerX = width / 2f
        val centerY = height / 2f
        val squareSize = min(width, height) * alignmentSquareSize
        val squareLeft = centerX - squareSize / 2
        val squareTop = centerY - squareSize / 2
        val squareRight = centerX + squareSize / 2
        val squareBottom = centerY + squareSize / 2
        
        // ✅ Calcular posição da face no quadrado
        val faceWidth = faceRect.width() * scaleX
        val faceHeight = faceRect.height() * scaleY
        val faceLeft = faceCenterX - faceWidth / 2
        val faceTop = faceCenterY - faceHeight / 2
        val faceRight = faceCenterX + faceWidth / 2
        val faceBottom = faceCenterY + faceHeight / 2
        
        // ✅ Verificar se a face está dentro do quadrado com tolerância
        val tolerance = 20f // Tolerância de 20 pixels
        return faceLeft >= (squareLeft - tolerance) &&
               faceTop >= (squareTop - tolerance) &&
               faceRight <= (squareRight + tolerance) &&
               faceBottom <= (squareBottom + tolerance)
    }
    
    /** Verifica se o tamanho da face está adequado */
    fun isFaceSizeAdequate(faceRect: Rect): Boolean {
        val faceArea = faceRect.width() * faceRect.height()
        val screenArea = width * height
        val faceRatio = faceArea.toFloat() / screenArea.toFloat()
        
        return faceRatio >= minFaceSizeRatio && faceRatio <= maxFaceSizeRatio
    }
    
    /** Calcula a qualidade da face baseada em posição e tamanho */
    fun calculateFaceQuality(faceRect: Rect): FaceQuality {
        if (!isFaceSizeAdequate(faceRect)) {
            val faceArea = faceRect.width() * faceRect.height()
            val screenArea = width * height
            val faceRatio = faceArea.toFloat() / screenArea.toFloat()
            
            return if (faceRatio < minFaceSizeRatio) {
                FaceQuality.TOO_SMALL
            } else {
                FaceQuality.TOO_BIG
            }
        }
        
        if (!isFaceInAlignmentSquare(faceRect)) {
            return FaceQuality.OFF_CENTER
        }
        
        if (!isFaceStable) {
            return FaceQuality.UNSTABLE
        }
        
        return FaceQuality.PERFECT
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // 🎯 Desenhar quadrado que acompanha o rosto
        if (boundingBox != null && imageWidth > 0 && imageHeight > 0) {
            drawDynamicAlignmentSquare(canvas)
        } else {
            // Fallback: quadrado central estático quando não há face detectada
        val centerX = width / 2f
        val centerY = height / 2f
            drawStaticAlignmentSquare(canvas, centerX, centerY)
        }
        
        // ✍️ Desenhar mensagem sutil quando necessário
        val centerX = width / 2f
        val centerY = height / 2f
        drawSubtleMessage(canvas, centerX, centerY)
    }
    
    /**
     * 🎯 Desenhar quadrado estático central (quando não há face detectada)
     */
    private fun drawStaticAlignmentSquare(canvas: Canvas, centerX: Float, centerY: Float) {
        val squareSize = min(width, height) * alignmentSquareSize
        val left = centerX - squareSize / 2
        val top = centerY - squareSize / 2
        val right = centerX + squareSize / 2
        val bottom = centerY + squareSize / 2
        
        paint.color = colorBlue // Azul - aguardando face
        paint.strokeWidth = 6f
        
        val rect = RectF(left, top, right, bottom)
        canvas.drawRoundRect(rect, 20f, 20f, paint)
    }
    
    /**
     * 🎯 Desenhar quadrado dinâmico que acompanha o rosto
     */
    private fun drawDynamicAlignmentSquare(canvas: Canvas) {
        val faceRect = boundingBox!!
        
        // ✅ Converter coordenadas da imagem para coordenadas da tela com correção de rotação
        val (scaleX, scaleY) = getCorrectedScales()
        val (faceCenterX, faceCenterY) = getCorrectedFacePosition(faceRect, scaleX, scaleY)
        
        // ✅ Tamanho do quadrado baseado no tamanho do rosto (com margem)
        val faceWidth = faceRect.width() * scaleX
        val faceHeight = faceRect.height() * scaleY
        val squareSize = max(faceWidth, faceHeight) * 1.2f // 20% maior que o rosto - PRECISO
        
        val left = faceCenterX - squareSize / 2
        val top = faceCenterY - squareSize / 2
        val right = faceCenterX + squareSize / 2
        val bottom = faceCenterY + squareSize / 2
        
        // ✅ Cor baseada na qualidade da face
        val color = when (faceQuality) {
            FaceQuality.PERFECT -> colorGreen      // Verde - perfeito
            FaceQuality.GOOD -> colorGreen         // Verde - bom
            FaceQuality.UNSTABLE -> colorYellow    // Amarelo - instável
            FaceQuality.OFF_CENTER -> colorYellow  // Amarelo - fora do centro
            FaceQuality.TOO_SMALL, FaceQuality.TOO_BIG -> colorRed  // Vermelho - muito pequeno/grande
            else -> colorBlue  // Azul - aguardando
        }
        
        paint.color = color
        paint.strokeWidth = 6f
        
        // ✅ Desenhar quadrado com cantos arredondados
        val rect = RectF(left, top, right, bottom)
        canvas.drawRoundRect(rect, 20f, 20f, paint)
    }
    
    /**
     * ✅ NOVA FUNÇÃO: Obter escalas corrigidas baseadas na rotação
     */
    private fun getCorrectedScales(): Pair<Float, Float> {
        return when (imageRotation) {
            90, 270 -> {
                // Para rotações de 90° e 270°, trocar width e height
                val scaleX = width.toFloat() / imageHeight.toFloat()
                val scaleY = height.toFloat() / imageWidth.toFloat()
                Pair(scaleX, scaleY)
            }
            else -> {
                // Para outras rotações, usar normalmente
                val scaleX = width.toFloat() / imageWidth.toFloat()
                val scaleY = height.toFloat() / imageHeight.toFloat()
                Pair(scaleX, scaleY)
            }
        }
    }
    
    /**
     * ✅ NOVA FUNÇÃO: Obter posição da face corrigida para rotação
     */
    private fun getCorrectedFacePosition(faceRect: Rect, scaleX: Float, scaleY: Float): Pair<Float, Float> {
        return when (imageRotation) {
            90 -> {
                // Rotação 90°: (x, y) -> (y, width-x)
                val faceCenterX = faceRect.exactCenterY() * scaleX
                val faceCenterY = (imageWidth - faceRect.exactCenterX()) * scaleY
                Pair(faceCenterX, faceCenterY)
            }
            180 -> {
                // Rotação 180°: (x, y) -> (width-x, height-y)
                val faceCenterX = (imageWidth - faceRect.exactCenterX()) * scaleX
                val faceCenterY = (imageHeight - faceRect.exactCenterY()) * scaleY
                Pair(faceCenterX, faceCenterY)
            }
            270 -> {
                // Rotação 270°: (x, y) -> (height-y, x)
                val faceCenterX = (imageHeight - faceRect.exactCenterY()) * scaleX
                val faceCenterY = faceRect.exactCenterX() * scaleY
                Pair(faceCenterX, faceCenterY)
            }
            else -> {
                // Sem rotação ou rotação 0°
                val faceCenterX = faceRect.exactCenterX() * scaleX
                val faceCenterY = faceRect.exactCenterY() * scaleY
                Pair(faceCenterX, faceCenterY)
            }
        }
    }
    
    /**
     * ✍️ Desenhar mensagem sutil e elegante
     */
    private fun drawSubtleMessage(canvas: Canvas, centerX: Float, centerY: Float) {
        val message = when (faceQuality) {
            FaceQuality.TOO_SMALL -> "Aproxime o rosto"
            FaceQuality.TOO_BIG -> "Afaste um pouco"
            FaceQuality.OFF_CENTER -> "Centralize o rosto"
            FaceQuality.UNSTABLE -> "Mantenha estável"
            FaceQuality.GOOD -> "Posição boa"
            FaceQuality.PERFECT -> "Perfeito!"
            else -> "Posicione o rosto no quadrado"
        }
        
        // ✅ Posicionar mensagem abaixo do quadrado
        val squareSize = min(width, height) * alignmentSquareSize
        val textY = centerY + squareSize / 2 + 80f // 80px abaixo do quadrado
        
        // ✅ Ajustar transparência baseada na qualidade
        val alphaValue = when (faceQuality) {
            FaceQuality.TOO_SMALL -> 200 // Mais visível quando precisa aproximar
            FaceQuality.TOO_BIG -> 180
            FaceQuality.OFF_CENTER -> 180
            FaceQuality.UNSTABLE -> 160
            FaceQuality.GOOD -> 140
            FaceQuality.PERFECT -> 120 // Menos visível quando está perfeito
            else -> 150
        }
        
        // ✅ Criar paint para texto principal com alpha ajustado
        val mainTextPaint = Paint().apply {
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            color = Color.WHITE
            alpha = alphaValue
            textSize = 40f
        }
        
        // ✅ Criar paint para sombra
        val shadowPaint = Paint().apply {
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            color = Color.BLACK
            alpha = 100
            textSize = 40f
            maskFilter = BlurMaskFilter(3f, BlurMaskFilter.Blur.NORMAL)
        }
        
        // Sombra
        canvas.drawText(message, centerX + 1f, textY + 1f, shadowPaint)
        // Texto principal
        canvas.drawText(message, centerX, textY, mainTextPaint)
    }
}
