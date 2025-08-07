package com.example.iface_offilne

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

class FaceRecognitionHelper(context: Context) {

    private val interpreter: Interpreter

    init {
        val resources = context.resources
        val inputStream = resources.openRawResource(R.raw.mobilefacenet)
        val modelBuffer = inputStream.use { input ->
            val bytes = ByteArray(input.available())
            input.read(bytes)
            ByteBuffer.allocateDirect(bytes.size).apply {
                order(ByteOrder.nativeOrder())
                put(bytes)
                rewind()
            }
        }
        val options = Interpreter.Options()
        interpreter = Interpreter(modelBuffer, options)
    }


    fun getEmbedding(bitmap: Bitmap): FloatArray {
        val inputBuffer = convertBitmapToBuffer(bitmap)
        val output = Array(1) { FloatArray(192) } // vetor de saída do modelo
        interpreter.run(inputBuffer, output)
        return output[0]
    }

    private fun convertBitmapToBuffer(bitmap: Bitmap): ByteBuffer {
        val inputSize = 112
        val byteBuffer = ByteBuffer.allocateDirect(4 * inputSize * inputSize * 3)
        byteBuffer.order(ByteOrder.nativeOrder())

        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)

        val intValues = IntArray(inputSize * inputSize)
        resizedBitmap.getPixels(intValues, 0, inputSize, 0, 0, inputSize, inputSize)

        for (pixel in intValues) {
            // Extrair canais R, G, B e normalizar [0..1]
            val r = ((pixel shr 16) and 0xFF) / 255.0f
            val g = ((pixel shr 8) and 0xFF) / 255.0f
            val b = (pixel and 0xFF) / 255.0f

            byteBuffer.putFloat(r)
            byteBuffer.putFloat(g)
            byteBuffer.putFloat(b)
        }

        return byteBuffer
    }
}
