package com.example.iface_offilne.util

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.camera.core.CameraSelector
import java.util.*

/**
 * 🎯 OVERLAY PROFISSIONAL PARA RECONHECIMENTO FACIAL
 * Baseado no GraphicOverlay do ML Kit Demonstrator
 * 
 * Características:
 * ✅ Sistema de coordenadas preciso
 * ✅ Suporte a câmera frontal/traseira
 * ✅ Transformação automática de coordenadas
 * ✅ Múltiplos gráficos simultâneos
 * ✅ Thread-safe
 */
class FaceOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    companion object {
        private const val TAG = "FaceOverlayView"
    }

    // ✅ THREAD-SAFE GRAPHICS
    private val lock = Object()
    private val graphics = ArrayList<Graphic>()

    // ✅ PROPRIEDADES DA PREVIEW
    var previewWidth: Int = 0
    var previewHeight: Int = 0
    var isLensFacingFront: Boolean = false

    // ✅ CONFIGURAÇÕES DO OVAL
    private val ovalPaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 6f
        isAntiAlias = true
        alpha = 180
    }

    private val ovalFillPaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.FILL
        alpha = 30
    }

    private val faceBoxPaint = Paint().apply {
        color = Color.BLUE
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 32f
        isAntiAlias = true
        setShadowLayer(2f, 1f, 1f, Color.BLACK)
    }

    init {
        addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            postInvalidate()
        }
    }

    /**
     * 🎯 CONFIGURAR PROPRIEDADES DA PREVIEW
     */
    fun setPreviewProperties(previewWidth: Int, previewHeight: Int, lensFacing: Int) {
        this.previewWidth = previewWidth
        this.previewHeight = previewHeight
        this.isLensFacingFront = CameraSelector.LENS_FACING_FRONT == lensFacing
        postInvalidate()
    }

    /**
     * 🎯 ADICIONAR FACE DETECTADA
     */
    fun addFace(faceRect: Rect, confidence: Float = 1.0f, label: String? = null) {
        synchronized(lock) {
            graphics.add(FaceGraphic(this, faceRect, confidence, label))
        }
        postInvalidate()
    }

    /**
     * 🎯 ADICIONAR OVAL DE POSICIONAMENTO
     */
    fun addPositioningOval() {
        synchronized(lock) {
            graphics.add(PositioningOvalGraphic(this))
        }
        postInvalidate()
    }

    /**
     * 🎯 ADICIONAR INDICADOR DE ESTABILIDADE
     */
    fun addStabilityIndicator(isStable: Boolean, frames: Int) {
        synchronized(lock) {
            graphics.add(StabilityIndicatorGraphic(this, isStable, frames))
        }
        postInvalidate()
    }

    /**
     * 🎯 ADICIONAR INDICADOR DE CAPTURA
     */
    fun addCaptureIndicator(captureNumber: Int, totalCaptures: Int) {
        synchronized(lock) {
            graphics.add(CaptureIndicatorGraphic(this, captureNumber, totalCaptures))
        }
        postInvalidate()
    }

    /**
     * 🧹 LIMPAR TODOS OS GRÁFICOS
     */
    fun clear() {
        synchronized(lock) {
            graphics.clear()
        }
        postInvalidate()
    }

    /**
     * 🧹 REMOVER GRÁFICOS ESPECÍFICOS
     */
    fun removeFaces() {
        synchronized(lock) {
            graphics.removeAll { graphic -> graphic is FaceGraphic }
        }
        postInvalidate()
    }

    /**
     * 🎨 DESENHAR OVERLAY
     */
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        synchronized(lock) {
            for (graphic in graphics) {
                graphic.draw(canvas)
            }
        }
    }

    /**
     * 🎯 CLASSE BASE PARA GRÁFICOS
     */
    abstract class Graphic(
        protected val overlay: FaceOverlayView,
        protected val imageWidth: Int,
        protected val imageHeight: Int
    ) {
        abstract fun draw(canvas: Canvas)

        /**
         * 🔄 TRANSFORMAR COORDENADAS DA IMAGEM PARA VIEW
         */
        fun transform(rect: Rect): RectF {
            val scaleX = overlay.previewWidth / imageWidth.toFloat()
            val scaleY = overlay.previewHeight / imageHeight.toFloat()

            // ✅ CORREÇÃO PARA CÂMERA FRONTAL
            val flippedLeft = if (overlay.isLensFacingFront) {
                imageWidth - rect.right
            } else {
                rect.left
            }

            val flippedRight = if (overlay.isLensFacingFront) {
                imageWidth - rect.left
            } else {
                rect.right
            }

            // ✅ ESCALAR COORDENADAS
            val scaledLeft = scaleX * flippedLeft
            val scaledTop = scaleY * rect.top
            val scaledRight = scaleX * flippedRight
            val scaledBottom = scaleY * rect.bottom

            return RectF(scaledLeft, scaledTop, scaledRight, scaledBottom)
        }

        /**
         * 🔄 TRANSFORMAR COORDENADA X
         */
        fun translateX(x: Float): Float {
            val scaleX = overlay.previewWidth / imageWidth.toFloat()
            val flippedX = if (overlay.isLensFacingFront) {
                imageWidth - x
            } else {
                x
            }
            return flippedX * scaleX
        }

        /**
         * 🔄 TRANSFORMAR COORDENADA Y
         */
        fun translateY(y: Float): Float {
            val scaleY = overlay.previewHeight / imageHeight.toFloat()
            return y * scaleY
        }
    }

    /**
     * 👤 GRÁFICO DA FACE DETECTADA
     */
    inner class FaceGraphic(
        overlay: FaceOverlayView,
        private val faceRect: Rect,
        private val confidence: Float,
        private val label: String?
    ) : Graphic(overlay, faceRect.width(), faceRect.height()) {

        override fun draw(canvas: Canvas) {
            val transformedRect = transform(faceRect)

            // ✅ DESENHAR BORDA DA FACE
            val paint = Paint().apply {
                color = if (confidence > 0.8f) Color.GREEN else Color.YELLOW
                style = Paint.Style.STROKE
                strokeWidth = 4f
                isAntiAlias = true
            }

            canvas.drawRect(transformedRect, paint)

            // ✅ DESENHAR LABEL
            label?.let {
                val textPaint = Paint().apply {
                    color = Color.WHITE
                    textSize = 28f
                    isAntiAlias = true
                    setShadowLayer(2f, 1f, 1f, Color.BLACK)
                }

                            val textBounds = Rect()
            textPaint.getTextBounds(it, 0, it.length, textBounds)

            val textX = transformedRect.left
            val textY = transformedRect.top - 10f

            canvas.drawText(it, textX, textY, textPaint)
            }

            // ✅ DESENHAR CONFIDENCE
            val confidenceText = "${(confidence * 100).toInt()}%"
            val confidencePaint = Paint().apply {
                color = Color.CYAN
                textSize = 24f
                isAntiAlias = true
                setShadowLayer(1f, 1f, 1f, Color.BLACK)
            }

            val confidenceBounds = Rect()
            confidencePaint.getTextBounds(confidenceText, 0, confidenceText.length, confidenceBounds)

            val confX = transformedRect.right - confidenceBounds.width() - 10f
            val confY = transformedRect.bottom + confidenceBounds.height() + 10f

            canvas.drawText(confidenceText, confX, confY, confidencePaint)
        }
    }

    /**
     * 🟢 OVAL DE POSICIONAMENTO
     */
    inner class PositioningOvalGraphic(overlay: FaceOverlayView) : Graphic(overlay, width, height) {

        override fun draw(canvas: Canvas) {
            val centerX = width / 2f
            val centerY = height / 2f

            // ✅ OVAL MAIOR E MAIS PERMISSIVO
            val ovalWidth = width * 0.85f   // 85% da largura
            val ovalHeight = height * 0.95f // 95% da altura

            val ovalRect = RectF(
                centerX - ovalWidth / 2,
                centerY - ovalHeight / 2,
                centerX + ovalWidth / 2,
                centerY + ovalHeight / 2
            )

            // ✅ DESENHAR OVAL COM PREENCHIMENTO
            canvas.drawOval(ovalRect, ovalFillPaint)
            canvas.drawOval(ovalRect, ovalPaint)

            // ✅ DESENHAR TEXTO DE INSTRUÇÃO
            val instructionText = "Posicione seu rosto aqui"
            val instructionPaint = Paint().apply {
                color = Color.WHITE
                textSize = 36f
                isAntiAlias = true
                setShadowLayer(3f, 2f, 2f, Color.BLACK)
                textAlign = Paint.Align.CENTER
            }

            canvas.drawText(instructionText, centerX, centerY + 100f, instructionPaint)
        }
    }

    /**
     * 📊 INDICADOR DE ESTABILIDADE
     */
    inner class StabilityIndicatorGraphic(
        overlay: FaceOverlayView,
        private val isStable: Boolean,
        private val frames: Int
    ) : Graphic(overlay, width, height) {

        override fun draw(canvas: Canvas) {
            val centerX = width / 2f
            val topY = 100f

            val indicatorColor = if (isStable) Color.GREEN else Color.YELLOW
            val indicatorText = if (isStable) "✅ Estável" else "⏳ Estabilizando..."

            val paint = Paint().apply {
                color = indicatorColor
                textSize = 40f
                isAntiAlias = true
                setShadowLayer(3f, 2f, 2f, Color.BLACK)
                textAlign = Paint.Align.CENTER
            }

            canvas.drawText(indicatorText, centerX, topY, paint)

            // ✅ DESENHAR CONTADOR DE FRAMES
            val frameText = "Frames: $frames"
            val framePaint = Paint().apply {
                color = Color.WHITE
                textSize = 28f
                isAntiAlias = true
                setShadowLayer(2f, 1f, 1f, Color.BLACK)
                textAlign = Paint.Align.CENTER
            }

            canvas.drawText(frameText, centerX, topY + 50f, framePaint)
        }
    }

    /**
     * 📸 INDICADOR DE CAPTURA
     */
    inner class CaptureIndicatorGraphic(
        overlay: FaceOverlayView,
        private val captureNumber: Int,
        private val totalCaptures: Int
    ) : Graphic(overlay, width, height) {

        override fun draw(canvas: Canvas) {
            val centerX = width / 2f
            val bottomY = height - 100f

            val captureText = "📸 Captura $captureNumber/$totalCaptures"
            val paint = Paint().apply {
                color = Color.CYAN
                textSize = 44f
                isAntiAlias = true
                setShadowLayer(3f, 2f, 2f, Color.BLACK)
                textAlign = Paint.Align.CENTER
            }

            canvas.drawText(captureText, centerX, bottomY, paint)

            // ✅ DESENHAR BARRA DE PROGRESSO
            val progress = captureNumber.toFloat() / totalCaptures
            val barWidth = width * 0.8f
            val barHeight = 20f
            val barLeft = (width - barWidth) / 2f
            val barTop = bottomY + 20f

            // ✅ BARRA DE FUNDO
            val backgroundPaint = Paint().apply {
                color = Color.GRAY
                alpha = 100
            }
            canvas.drawRect(barLeft, barTop, barLeft + barWidth, barTop + barHeight, backgroundPaint)

            // ✅ BARRA DE PROGRESSO
            val progressPaint = Paint().apply {
                color = Color.GREEN
            }
            canvas.drawRect(barLeft, barTop, barLeft + (barWidth * progress), barTop + barHeight, progressPaint)
        }
    }

    /**
     * ✅ VERIFICAR SE FACE ESTÁ NO OVAL
     */
    fun isFaceInOval(faceRect: Rect): Boolean {
        val centerX = width / 2f
        val centerY = height / 2f

        // ✅ OVAL MAIS PERMISSIVO
        val radiusX = (width * 0.85f) / 2f   // 85% da largura
        val radiusY = (height * 0.95f) / 2f  // 95% da altura

        val faceCenterX = faceRect.exactCenterX()
        val faceCenterY = faceRect.exactCenterY()

        // ✅ TOLERÂNCIA MÁXIMA
        val tolerance = 0.6f
        val normX = (faceCenterX - centerX) / radiusX
        val normY = (faceCenterY - centerY) / radiusY

        return (normX * normX + normY * normY) <= (1 + tolerance)
    }
}
