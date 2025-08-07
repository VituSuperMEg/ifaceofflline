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

    /** Verifica se o centro do rosto está dentro do oval central */
    fun isFaceInOval(faceRect: Rect): Boolean {
        val centerX = width / 2f
        val centerY = height / 2f

        // Usar as mesmas proporções do oval vermelho
        val radiusX = (width * 0.6f) / 2f   // metade da largura do oval
        val radiusY = (height * 0.8f) / 2f  // metade da altura do oval

        val faceCenterX = faceRect.exactCenterX()
        val faceCenterY = faceRect.exactCenterY()

        // Usa a equação do elipse para verificar se o ponto está dentro
        val normX = (faceCenterX - centerX) / radiusX
        val normY = (faceCenterY - centerY) / radiusY

        return (normX * normX + normY * normY) <= 1
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val centerX = width / 2f
        val centerY = height / 2f

        // Definir o tamanho do oval - ajuste conforme necessário
        val ovalWidth = width * 0.6f   // 60% da largura da tela
        val ovalHeight = height * 0.8f // 80% da altura da tela

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
