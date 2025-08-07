package com.example.iface_offilne

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream

object TempImageStorage {
    private var tempFaceBitmap: Bitmap? = null
    
    fun storeFaceBitmap(bitmap: Bitmap) {
        // Limpar bitmap anterior se existir
        tempFaceBitmap?.recycle()
        
        // Compactar o bitmap para economizar memória
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
        val byteArray = stream.toByteArray()
        
        // Criar bitmap compactado
        tempFaceBitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
        
        // Limpar recursos
        stream.close()
    }
    
    fun getFaceBitmap(): Bitmap? {
        return tempFaceBitmap
    }
    
    fun clearFaceBitmap() {
        tempFaceBitmap?.recycle()
        tempFaceBitmap = null
    }
} 