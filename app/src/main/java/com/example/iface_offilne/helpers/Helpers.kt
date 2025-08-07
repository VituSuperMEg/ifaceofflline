package com.example.iface_offilne.helpers

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
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
    val safeRect = Rect(
        rect.left.coerceAtLeast(0),
        rect.top.coerceAtLeast(0),
        rect.right.coerceAtMost(bitmap.width),
        rect.bottom.coerceAtMost(bitmap.height)
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



class Helpers {




}