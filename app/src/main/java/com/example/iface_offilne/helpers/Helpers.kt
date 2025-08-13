package com.example.iface_offilne.helpers

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import android.util.Base64
import java.io.ByteArrayOutputStream


fun toBitmap(image: Image): Bitmap {
    val yBuffer = image.planes[0].buffer
    val uBuffer = image.planes[1].buffer
    val vBuffer = image.planes[2].buffer

    val ySize = yBuffer.remaining()
    val uSize = uBuffer.remaining()
    val vSize = vBuffer.remaining()

    val nv21 = ByteArray(ySize + uSize + vSize)

    yBuffer.get(nv21, 0, ySize)
    vBuffer.get(nv21, ySize, vSize)
    uBuffer.get(nv21, ySize + vSize, uSize)

    val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
    val out = ByteArrayOutputStream()
    yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 100, out)
    val imageBytes = out.toByteArray()

    return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
}

fun cropFace(bitmap: Bitmap, rect: Rect): Bitmap {
    // Adicionar margem de 60% para capturar mais área ao redor do rosto
    val marginFactor = 0.6f
    val marginWidth = (rect.width() * marginFactor).toInt()
    val marginHeight = (rect.height() * marginFactor).toInt()
    
    // Expandir o retângulo com a margem
    val expandedRect = Rect(
        rect.left - marginWidth,
        rect.top - marginHeight,
        rect.right + marginWidth,
        rect.bottom + marginHeight
    )
    
    // Garantir que está dentro dos limites da imagem
    val safeRect = Rect(
        expandedRect.left.coerceAtLeast(0),
        expandedRect.top.coerceAtLeast(0),
        expandedRect.right.coerceAtMost(bitmap.width),
        expandedRect.bottom.coerceAtMost(bitmap.height)
    )
    
    return Bitmap.createBitmap(bitmap, safeRect.left, safeRect.top, safeRect.width(), safeRect.height())
}


fun bitmapToFloatArray(bitmap: Bitmap): Array<Array<Array<FloatArray>>> {
    val input = Array(1) { Array(160) { Array(160) { FloatArray(3) } } }
    for (y in 0 until 160) {
        for (x in 0 until 160) {
            val pixel = bitmap.getPixel(x, y)
            input[0][y][x][0] = (Color.red(pixel) - 127.5f) / 128f
            input[0][y][x][1] = (Color.green(pixel) - 127.5f) / 128f
            input[0][y][x][2] = (Color.blue(pixel) - 127.5f) / 128f
        }
    }
    return input
}

/**
 * Converte um Bitmap para string Base64 com correção de orientação
 * VERSÃO ATUAL: Testando 180° (corrige imagem de cabeça para baixo)
 */
fun bitmapToBase64(bitmap: Bitmap, quality: Int = 80): String {
    // TESTE ATUAL: Rotação 180° - corrige imagem invertida/de cabeça para baixo
    val correctedBitmap = rotateBitmap180(bitmap)
    
    val byteArrayOutputStream = ByteArrayOutputStream()
    correctedBitmap.compress(Bitmap.CompressFormat.JPEG, quality, byteArrayOutputStream)
    val byteArray = byteArrayOutputStream.toByteArray()
    return Base64.encodeToString(byteArray, Base64.DEFAULT)
}

/**
 * VERSÕES ALTERNATIVAS PARA TESTE:
 * Descomente a versão que quiser testar e comente a atual
 */

/*
// TESTE: Sem rotação (0°)
fun bitmapToBase64(bitmap: Bitmap, quality: Int = 80): String {
    val correctedBitmap = rotateBitmap0(bitmap)
    val byteArrayOutputStream = ByteArrayOutputStream()
    correctedBitmap.compress(Bitmap.CompressFormat.JPEG, quality, byteArrayOutputStream)
    val byteArray = byteArrayOutputStream.toByteArray()
    return Base64.encodeToString(byteArray, Base64.DEFAULT)
}
*/

/*
// TESTE: Rotação 90° (horário)
fun bitmapToBase64(bitmap: Bitmap, quality: Int = 80): String {
    val correctedBitmap = rotateBitmap90(bitmap)
    val byteArrayOutputStream = ByteArrayOutputStream()
    correctedBitmap.compress(Bitmap.CompressFormat.JPEG, quality, byteArrayOutputStream)
    val byteArray = byteArrayOutputStream.toByteArray()
    return Base64.encodeToString(byteArray, Base64.DEFAULT)
}
*/

/*
// TESTE: Rotação 180° (invertido)
fun bitmapToBase64(bitmap: Bitmap, quality: Int = 80): String {
    val correctedBitmap = rotateBitmap180(bitmap)
    val byteArrayOutputStream = ByteArrayOutputStream()
    correctedBitmap.compress(Bitmap.CompressFormat.JPEG, quality, byteArrayOutputStream)
    val byteArray = byteArrayOutputStream.toByteArray()
    return Base64.encodeToString(byteArray, Base64.DEFAULT)
}
*/

/**
 * Versão que testa múltiplas rotações para encontrar a orientação correta
 * Esta função oferece opções caso a primeira rotação não funcione
 */
fun fixImageOrientationAdvanced(bitmap: Bitmap, rotationAngle: Float = -90f): Bitmap {
    return try {
        android.util.Log.d("ImageOrientationAdv", "Tentando rotação de ${rotationAngle}° na imagem ${bitmap.width}x${bitmap.height}")
        
        val matrix = Matrix()
        matrix.postRotate(rotationAngle)
        
        val rotatedBitmap = Bitmap.createBitmap(
            bitmap, 0, 0,
            bitmap.width, bitmap.height,
            matrix, true
        )
        
        android.util.Log.d("ImageOrientationAdv", "Resultado: ${bitmap.width}x${bitmap.height} -> ${rotatedBitmap.width}x${rotatedBitmap.height}")
        
        // Reciclar bitmap original
        if (rotatedBitmap != bitmap && !bitmap.isRecycled) {
            bitmap.recycle()
        }
        
        return rotatedBitmap
        
    } catch (e: Exception) {
        android.util.Log.e("ImageOrientationAdv", "Erro na rotação de ${rotationAngle}°: ${e.message}")
        return bitmap
    }
}

/**
 * SOLUÇÃO DEFINITIVA: Função que testa todas as orientações possíveis
 * Use esta se ainda houver problemas de orientação
 */
fun fixImageOrientationDefinitive(bitmap: Bitmap): Bitmap {
    return try {
        android.util.Log.d("ImageOrientationDef", "=== CORREÇÃO DEFINITIVA DE ORIENTAÇÃO ===")
        android.util.Log.d("ImageOrientationDef", "Imagem original: ${bitmap.width}x${bitmap.height}")
        
        // Se a imagem já está em portrait (altura > largura), não fazer nada
        if (bitmap.height > bitmap.width) {
            android.util.Log.d("ImageOrientationDef", "✅ Imagem já em portrait, mantendo")
            return bitmap
        }
        
        // TENTATIVA 1: Rotação de 90° horário (mais comum)
        val matrix = Matrix()
        matrix.postRotate(90f)
        
        val rotatedBitmap = Bitmap.createBitmap(
            bitmap, 0, 0,
            bitmap.width, bitmap.height,
            matrix, true
        )
        
        android.util.Log.d("ImageOrientationDef", "✅ Rotação +90° aplicada: ${bitmap.width}x${bitmap.height} -> ${rotatedBitmap.width}x${rotatedBitmap.height}")
        
        // Reciclar original
        if (!bitmap.isRecycled) {
            bitmap.recycle()
        }
        
        return rotatedBitmap
        
    } catch (e: Exception) {
        android.util.Log.e("ImageOrientationDef", "❌ Erro na correção definitiva: ${e.message}")
        return bitmap
    }
}

/**
 * FUNÇÃO DE EMERGÊNCIA: Se ainda não funcionar, use esta função
 * que tenta diferentes ângulos até encontrar o correto
 */
fun fixImageOrientationEmergency(bitmap: Bitmap, testMode: Boolean = false): Bitmap {
    return try {
        android.util.Log.d("ImageEmergency", "=== MODO EMERGÊNCIA DE ORIENTAÇÃO ===")
        android.util.Log.d("ImageEmergency", "Imagem original: ${bitmap.width}x${bitmap.height}")
        
        if (testMode) {
            // MODO TESTE: Mostrar todas as possibilidades
            android.util.Log.d("ImageEmergency", "🔄 TESTE - Rotação 0°: ${bitmap.width}x${bitmap.height}")
            android.util.Log.d("ImageEmergency", "🔄 TESTE - Rotação 90°: ${bitmap.height}x${bitmap.width}")
            android.util.Log.d("ImageEmergency", "🔄 TESTE - Rotação 180°: ${bitmap.width}x${bitmap.height}")
            android.util.Log.d("ImageEmergency", "🔄 TESTE - Rotação 270°: ${bitmap.height}x${bitmap.width}")
        }
        
        // Para câmeras que capturam de lado, geralmente 270° (-90°) é o correto
        val matrix = Matrix()
        matrix.postRotate(270f) // Ou -90f
        
        val rotatedBitmap = Bitmap.createBitmap(
            bitmap, 0, 0,
            bitmap.width, bitmap.height,
            matrix, true
        )
        
        android.util.Log.d("ImageEmergency", "🚀 EMERGÊNCIA: Rotação 270° aplicada: ${bitmap.width}x${bitmap.height} -> ${rotatedBitmap.width}x${rotatedBitmap.height}")
        
        // Reciclar original
        if (!bitmap.isRecycled) {
            bitmap.recycle()
        }
        
        return rotatedBitmap
        
    } catch (e: Exception) {
        android.util.Log.e("ImageEmergency", "❌ Erro no modo emergência: ${e.message}")
        return bitmap
    }
}

/**
 * FUNÇÕES DE TESTE: Use estas para encontrar a rotação correta
 * Chame diferentes funções até encontrar a que funciona
 */

// Rotação 0° (sem rotação)
fun rotateBitmap0(bitmap: Bitmap): Bitmap {
    android.util.Log.d("RotateTest", "🔄 TESTE 0°: Mantendo original ${bitmap.width}x${bitmap.height}")
    return bitmap
}

// Rotação 90° (horário)
fun rotateBitmap90(bitmap: Bitmap): Bitmap {
    return try {
        val matrix = Matrix()
        matrix.postRotate(90f)
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        android.util.Log.d("RotateTest", "🔄 TESTE 90°: ${bitmap.width}x${bitmap.height} -> ${rotated.width}x${rotated.height}")
        if (!bitmap.isRecycled) bitmap.recycle()
        rotated
    } catch (e: Exception) {
        android.util.Log.e("RotateTest", "Erro 90°: ${e.message}")
        bitmap
    }
}

// Rotação 180° (invertido)
fun rotateBitmap180(bitmap: Bitmap): Bitmap {
    return try {
        val matrix = Matrix()
        matrix.postRotate(180f)
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        android.util.Log.d("RotateTest", "🔄 TESTE 180°: ${bitmap.width}x${bitmap.height} -> ${rotated.width}x${rotated.height}")
        if (!bitmap.isRecycled) bitmap.recycle()
        rotated
    } catch (e: Exception) {
        android.util.Log.e("RotateTest", "Erro 180°: ${e.message}")
        bitmap
    }
}

// Rotação 270° (anti-horário) - MAIS COMUM PARA CORRIGIR CÂMERAS DE LADO
fun rotateBitmap270(bitmap: Bitmap): Bitmap {
    return try {
        val matrix = Matrix()
        matrix.postRotate(270f)
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        android.util.Log.d("RotateTest", "🔄 TESTE 270°: ${bitmap.width}x${bitmap.height} -> ${rotated.width}x${rotated.height}")
        if (!bitmap.isRecycled) bitmap.recycle()
        rotated
    } catch (e: Exception) {
        android.util.Log.e("RotateTest", "Erro 270°: ${e.message}")
        bitmap
    }
}


class Helpers {




}