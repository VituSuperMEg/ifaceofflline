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
    
    /**
     * ✅ NOVO: Restaura entidade do SharedPreferences
     * @param context Contexto da aplicação
     * @return true se a entidade foi restaurada com sucesso, false caso contrário
     */
    fun restoreEntidadeFromPreferences(context: android.content.Context): Boolean {
        try {
            val sharedPreferences = context.getSharedPreferences("LoginPrefs", android.content.Context.MODE_PRIVATE)
            val savedEntidadeId = sharedPreferences.getString("saved_entidade_id", "")
            val savedEntidadeName = sharedPreferences.getString("saved_entidade_name", "")
            val savedEstado = sharedPreferences.getString("saved_estado", "")
            val savedMunicipio = sharedPreferences.getString("saved_municipio", "")
            
            if (!savedEntidadeId.isNullOrEmpty() && !savedEntidadeName.isNullOrEmpty()) {
                val entidade = com.example.iface_offilne.data.Entidade(
                    id = savedEntidadeId,
                    name = savedEntidadeName
                )
                
                if (entidade.id.isNotEmpty() && entidade.name.isNotEmpty()) {
                    SessionManager.entidade = entidade
                    SessionManager.estadoBr = savedEstado
                    SessionManager.municipio = savedMunicipio
                    
                    Log.d(TAG, "✅ Entidade restaurada: ${entidade.name} (${entidade.id})")
                    return true
                } else {
                    Log.w(TAG, "⚠️ Entidade salva é inválida")
                    return false
                }
            } else {
                Log.w(TAG, "⚠️ Nenhuma entidade salva encontrada")
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao restaurar entidade: ${e.message}")
            return false
        }
    }
    
    /**
     * ✅ NOVO: Salva entidade no SharedPreferences
     * @param context Contexto da aplicação
     * @return true se a entidade foi salva com sucesso, false caso contrário
     */
    fun saveEntidadeToPreferences(context: android.content.Context): Boolean {
        try {
            if (!isEntidadeConfigurada()) {
                Log.w(TAG, "⚠️ Tentativa de salvar entidade não configurada")
                return false
            }
            
            val sharedPreferences = context.getSharedPreferences("LoginPrefs", android.content.Context.MODE_PRIVATE)
            val editor = sharedPreferences.edit()
            editor.putString("saved_entidade_id", entidade?.id ?: "")
            editor.putString("saved_entidade_name", entidade?.name ?: "")
            editor.putString("saved_estado", estadoBr ?: "")
            editor.putString("saved_municipio", municipio ?: "")
            editor.apply()
            
            Log.d(TAG, "💾 Entidade salva: ${entidade?.name} (${entidade?.id})")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao salvar entidade: ${e.message}")
            return false
        }
    }
    
    /**
     * ✅ NOVO: Verifica se a entidade está configurada e válida
     * @return true se a entidade estiver configurada e válida, false caso contrário
     */
    fun isEntidadeValida(): Boolean {
        val isValida = entidade != null && 
                      entidade?.id?.isNotEmpty() == true && 
                      entidade?.name?.isNotEmpty() == true
        
        if (!isValida) {
            Log.w(TAG, "⚠️ Entidade não é válida:")
            Log.w(TAG, "  🔴 entidade: $entidade")
            Log.w(TAG, "  🔴 entidade?.id: ${entidade?.id}")
            Log.w(TAG, "  🔴 entidade?.name: ${entidade?.name}")
        } else {
            Log.d(TAG, "✅ Entidade válida: ${entidade?.name} (${entidade?.id})")
        }
        
        return isValida
    }
}