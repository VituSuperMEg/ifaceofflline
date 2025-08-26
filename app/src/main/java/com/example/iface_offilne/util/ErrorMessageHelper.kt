package com.example.iface_offilne.util

import android.content.Context
import android.widget.Toast
import retrofit2.HttpException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.ConnectException

object ErrorMessageHelper {
    
    /**
     * Get standardized error message based on exception type
     */
    fun getErrorMessage(throwable: Throwable): String {
        return when (throwable) {
            is HttpException -> {
                when (throwable.code()) {
                    400 -> "Erro de conexão com a api.rh247.com.br"
                    500 -> "Não foi possível estabelecer conexão com a internet. Verifique sua conexão ou entre em contato com seu provedor de serviços"
                    else -> "Erro de conexão com a api.rh247.com.br"
                }
            }
            is SocketTimeoutException -> "Não foi possível estabelecer conexão com a internet. Verifique sua conexão ou entre em contato com seu provedor de serviços"
            is UnknownHostException -> "Não foi possível estabelecer conexão com a internet. Verifique sua conexão ou entre em contato com seu provedor de serviços"
            is ConnectException -> "Não foi possível estabelecer conexão com a internet. Verifique sua conexão ou entre em contato com seu provedor de serviços"
            else -> "Não foi possível estabelecer conexão com a internet. Verifique sua conexão ou entre em contato com seu provedor de serviços"
        }
    }
    
    /**
     * Show standardized error message as Toast
     */
    fun showErrorMessage(context: Context, throwable: Throwable) {
        val message = getErrorMessage(throwable)
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }
    
    /**
     * Show standardized error message as Toast with custom message
     */
    fun showErrorMessage(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }
} 