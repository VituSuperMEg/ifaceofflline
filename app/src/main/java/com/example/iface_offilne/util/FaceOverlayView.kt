package com.example.iface_offilne.util

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

class FaceOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var boundingBox: Rect? = null

    fun setBoundingBox(rect: Rect, imageWidth: Int, imageHeight: Int) {
        boundingBox = rect
        invalidate()
    }

    fun clear() {
        boundingBox = null
        invalidate()
    }

    /** Verifica se o centro do rosto está dentro do oval central com tolerância máxima */
    fun isFaceInOval(faceRect: Rect): Boolean {
        val centerX = width / 2f
        val centerY = height / 2f

        // ✅ SIMPLIFICAÇÃO: Oval ainda maior para máxima tolerância
        val radiusX = (width * 0.8f) / 2f   // 80% da largura
        val radiusY = (height * 0.9f) / 2f  // 90% da altura

        val faceCenterX = faceRect.exactCenterX()
        val faceCenterY = faceRect.exactCenterY()

        // ✅ SIMPLIFICAÇÃO: Tolerância máxima de 50% para funcionar em qualquer aparelho
        val tolerance = 0.5f
        val normX = (faceCenterX - centerX) / radiusX
        val normY = (faceCenterY - centerY) / radiusY

        return (normX * normX + normY * normY) <= (1 + tolerance)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val centerX = width / 2f
        val centerY = height / 2f

        // ✅ SIMPLIFICAÇÃO: Oval ainda maior para máxima tolerância
        val ovalWidth = width * 0.8f   // 80% da largura da tela
        val ovalHeight = height * 0.9f  // 90% da altura da tela

        val paintOval = Paint().apply {
            color = Color.RED
            style = Paint.Style.STROKE
            strokeWidth = 8f
            isAntiAlias = true
        }

        // Criar o RectF para o oval
        val ovalRect = RectF(
            centerX - ovalWidth / 2,
            centerY - ovalHeight / 2,
            centerX + ovalWidth / 2,
            centerY + ovalHeight / 2
        )

        // Desenhar o oval vermelho
        canvas.drawOval(ovalRect, paintOval)
    }

}
