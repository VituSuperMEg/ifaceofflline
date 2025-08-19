package com.example.iface_offilne.helpers

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.example.iface_offilne.data.api.ApiService
import com.example.iface_offilne.data.api.PermissaoRequest
import com.example.iface_offilne.data.api.PermissaoResponse
import com.example.iface_offilne.data.api.RetrofitClient
import com.example.iface_offilne.util.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PermissaoHelper(private val context: Context) {
    
    companion object {
        private const val TAG = "PermissaoHelper"
        
        // ✅ CONSTANTES PARA MENUS
        const val MENU_CONFIGURACOES = "configuracoes"
        const val MENU_FUNCIONARIOS = "funcionarios"
        const val MENU_PONTOS = "pontos"
        const val MENU_SINCRONIZACAO = "sincronizacao"
        const val MENU_CADASTRO_FACE = "cadastro_face"
        const val MENU_RELATORIOS = "relatorios"
        const val MENU_ADMIN = "admin"
        const val MENU_HOME = "home"
    }
    
    private val apiService = RetrofitClient.instance
    
    /**
     * ✅ VERIFICAR PERMISSÃO PARA UM MENU ESPECÍFICO
     * @param menu Nome do menu (ex: "configuracoes", "funcionarios")
     * @param onSuccess Callback executado se tem permissão
     * @param onError Callback executado se não tem permissão ou erro
     */
    fun verificarPermissao(
        menu: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        // ✅ NOVO: Usar método utilitário do SessionManager
        if (!SessionManager.isEntidadeConfigurada()) {
            Log.e(TAG, "❌ === ERRO CRÍTICO: ENTIDADE NÃO CONFIGURADA ===")
            Log.e(TAG, "  🔴 ${SessionManager.getEntidadeInfo()}")
            Log.e(TAG, "  📍 Menu solicitado: $menu")
            Log.e(TAG, "  💡 SOLUÇÃO: Usuário deve ir em configurações e selecionar uma entidade")
            onError("Entidade não configurada. Vá em Configurações e selecione uma entidade.")
            return
        }
        
        val entidade = SessionManager.getEntidadeId()
        
        Log.d(TAG, "🔐 Verificando permissão para menu: $menu")
        Log.d(TAG, "🏢 Entidade: $entidade")
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val request = PermissaoRequest(
                    entidade = entidade,
                    menu = menu
                )
                
                Log.d(TAG, "🌐 Fazendo requisição para API...")
                val response = apiService.getPermissao(entidade, request)
                
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        val permissaoResponse = response.body()
                        
                        if (permissaoResponse != null && permissaoResponse.success) {
                            if (permissaoResponse.permissao) {
                                Log.d(TAG, "✅ Permissão concedida para menu: $menu")
                                onSuccess()
                            } else {
                                Log.w(TAG, "❌ Permissão negada para menu: $menu")
                                val mensagem = permissaoResponse.message.ifEmpty { 
                                    "Você não tem permissão para acessar este menu" 
                                }
                                onError(mensagem)
                            }
                        } else {
                            Log.e(TAG, "❌ Resposta inválida da API")
                            Log.e(TAG, "  📝 Response: $permissaoResponse")
                            onError("Erro na verificação de permissão")
                        }
                    } else {
                        Log.e(TAG, "❌ Erro na API: ${response.code()}")
                        Log.e(TAG, "  📝 Error Body: ${response.errorBody()?.string()}")
                        Log.e(TAG, "  📝 Error Message: ${response.message()}")
                        onError("Erro de conexão (${response.code()})")
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao verificar permissão: ${e.message}")
                Log.e(TAG, "  🔴 Tipo: ${e.javaClass.simpleName}")
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    onError("Erro de conexão: ${e.message}")
                }
            }
        }
    }
    
    /**
     * ✅ VERIFICAR PERMISSÃO COM TOAST AUTOMÁTICO
     * @param menu Nome do menu
     * @param onSuccess Callback executado se tem permissão
     */
    fun verificarPermissaoComToast(
        menu: String,
        onSuccess: () -> Unit
    ) {
        verificarPermissao(
            menu = menu,
            onSuccess = onSuccess,
            onError = { mensagem ->
                Log.w(TAG, "❌ Permissão negada: $mensagem")
                try {
                    Toast.makeText(context, "❌ $mensagem", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Erro ao mostrar Toast: ${e.message}")
                }
            }
        )
    }
    
    /**
     * ✅ VERIFICAR PERMISSÃO PARA CONFIGURAÇÕES
     */
    fun verificarPermissaoConfiguracoes(onSuccess: () -> Unit) {
        verificarPermissaoComToast(MENU_CONFIGURACOES, onSuccess)
    }
    
    /**
     * ✅ VERIFICAR PERMISSÃO PARA FUNCIONÁRIOS
     */
    fun verificarPermissaoFuncionarios(onSuccess: () -> Unit) {
        verificarPermissaoComToast(MENU_FUNCIONARIOS, onSuccess)
    }
    
    /**
     * ✅ VERIFICAR PERMISSÃO PARA PONTOS
     */
    fun verificarPermissaoPontos(onSuccess: () -> Unit) {
        verificarPermissaoComToast(MENU_PONTOS, onSuccess)
    }
    
    /**
     * ✅ VERIFICAR PERMISSÃO PARA SINCRONIZAÇÃO
     */
    fun verificarPermissaoSincronizacao(onSuccess: () -> Unit) {
        verificarPermissaoComToast(MENU_SINCRONIZACAO, onSuccess)
    }
    
    /**
     * ✅ VERIFICAR PERMISSÃO PARA CADASTRO DE FACE
     */
    fun verificarPermissaoCadastroFace(onSuccess: () -> Unit) {
        verificarPermissaoComToast(MENU_CADASTRO_FACE, onSuccess)
    }
    
    /**
     * ✅ VERIFICAR PERMISSÃO PARA RELATÓRIOS
     */
    fun verificarPermissaoRelatorios(onSuccess: () -> Unit) {
        verificarPermissaoComToast(MENU_RELATORIOS, onSuccess)
    }
    
    /**
     * ✅ VERIFICAR PERMISSÃO PARA ADMIN
     */
    fun verificarPermissaoAdmin(onSuccess: () -> Unit) {
        verificarPermissaoComToast(MENU_ADMIN, onSuccess)
    }
} 