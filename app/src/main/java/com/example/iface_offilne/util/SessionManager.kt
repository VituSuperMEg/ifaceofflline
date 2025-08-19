package com.example.iface_offilne.util

import android.util.Log
import com.example.iface_offilne.data.Entidade

object SessionManager {
    private const val TAG = "SessionManager"
    
    var entidade: Entidade? = null
    var estadoBr: String? = null
    var municipio: String? = null
    
    /**
     * ✅ NOVO: Verifica se a entidade está configurada
     * @return true se a entidade estiver configurada, false caso contrário
     */
    fun isEntidadeConfigurada(): Boolean {
        val isConfigurada = entidade != null && entidade?.id?.isNotEmpty() == true
        
        if (!isConfigurada) {
            Log.w(TAG, "⚠️ Entidade não configurada:")
            Log.w(TAG, "  🔴 entidade: $entidade")
            Log.w(TAG, "  🔴 entidade?.id: ${entidade?.id}")
            Log.w(TAG, "  🔴 entidade?.name: ${entidade?.name}")
        } else {
            Log.d(TAG, "✅ Entidade configurada: ${entidade?.name} (${entidade?.id})")
        }
        
        return isConfigurada
    }
    
    /**
     * ✅ NOVO: Obtém o ID da entidade de forma segura
     * @return ID da entidade ou string vazia se não configurada
     */
    fun getEntidadeId(): String {
        return entidade?.id ?: ""
    }
    
    /**
     * ✅ NOVO: Obtém o nome da entidade de forma segura
     * @return Nome da entidade ou string vazia se não configurada
     */
    fun getEntidadeName(): String {
        return entidade?.name ?: ""
    }
    
    /**
     * ✅ NOVO: Obtém informações completas da entidade para debug
     * @return String com informações da entidade
     */
    fun getEntidadeInfo(): String {
        return when {
            entidade == null -> "Entidade: null"
            entidade?.id?.isEmpty() == true -> "Entidade: ID vazio"
            entidade?.name?.isEmpty() == true -> "Entidade: ${entidade?.id} (sem nome)"
            else -> "Entidade: ${entidade?.name} (${entidade?.id})"
        }
    }
    
    /**
     * ✅ NOVO: Limpa todos os dados da sessão
     */
    fun clearSession() {
        Log.d(TAG, "🧹 Limpando dados da sessão")
        entidade = null
        estadoBr = null
        municipio = null
        Log.d(TAG, "✅ Sessão limpa")
    }
}