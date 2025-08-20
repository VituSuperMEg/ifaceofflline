package com.example.iface_offilne.util

import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import java.lang.ref.WeakReference

/**
 * Utilitário para aplicar máscaras em campos de texto
 */
object MaskUtil {
    
    /**
     * Aplica máscara de CPF (999.999.999-99)
     */
    fun applyCpfMask(editText: EditText) {
        editText.addTextChangedListener(CpfMaskWatcher(editText))
    }
    
    /**
     * Aplica máscara para código de entidade (9 dígitos)
     */
    fun applyEntidadeMask(editText: EditText) {
        editText.addTextChangedListener(EntidadeMaskWatcher(editText))
    }
    
    /**
     * Remove máscaras e retorna apenas os números
     */
    fun unmask(text: String): String {
        return text.replace(Regex("[^\\d]"), "")
    }
    
    /**
     * Aplica máscara de CPF
     */
    fun maskCpf(text: String): String {
        val unmasked = unmask(text)
        return when {
            unmasked.length <= 3 -> unmasked
            unmasked.length <= 6 -> "${unmasked.substring(0, 3)}.${unmasked.substring(3)}"
            unmasked.length <= 9 -> "${unmasked.substring(0, 3)}.${unmasked.substring(3, 6)}.${unmasked.substring(6)}"
            else -> "${unmasked.substring(0, 3)}.${unmasked.substring(3, 6)}.${unmasked.substring(6, 9)}-${unmasked.substring(9, minOf(11, unmasked.length))}"
        }
    }
    
    /**
     * Aplica máscara para código de entidade (9 dígitos)
     */
    fun maskEntidade(text: String): String {
        val unmasked = unmask(text)
        return if (unmasked.length <= 9) unmasked else unmasked.substring(0, 9)
    }
    
    /**
     * TextWatcher para máscara de CPF
     */
    private class CpfMaskWatcher(editText: EditText) : TextWatcher {
        private val editTextWeakRef = WeakReference(editText)
        private var isUpdating = false
        
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        
        override fun afterTextChanged(s: Editable?) {
            if (isUpdating) return
            
            val editText = editTextWeakRef.get() ?: return
            isUpdating = true
            
            val masked = maskCpf(s.toString())
            if (masked != s.toString()) {
                editText.setText(masked)
                editText.setSelection(masked.length)
            }
            
            isUpdating = false
        }
    }
    
    /**
     * TextWatcher para máscara de código de entidade
     */
    private class EntidadeMaskWatcher(editText: EditText) : TextWatcher {
        private val editTextWeakRef = WeakReference(editText)
        private var isUpdating = false
        
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        
        override fun afterTextChanged(s: Editable?) {
            if (isUpdating) return
            
            val editText = editTextWeakRef.get() ?: return
            isUpdating = true
            
            val masked = maskEntidade(s.toString())
            if (masked != s.toString()) {
                editText.setText(masked)
                editText.setSelection(masked.length)
            }
            
            isUpdating = false
        }
    }
} 