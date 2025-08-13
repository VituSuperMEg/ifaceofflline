package com.example.iface_offilne.util

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

class UpdateManager {
    
    companion object {
        private const val TAG = "UpdateManager"
        
        // URL do seu servidor para verificar atualizações (você pode alterar isso)
        private const val UPDATE_CHECK_URL = "https://api.seuservidor.com/updates/iface-offline"
        
        // Chave para armazenar a última verificação
        private const val PREF_LAST_UPDATE_CHECK = "last_update_check"
        private const val PREF_UPDATE_INFO = "update_info"
    }
        
    data class UpdateInfo(
        val versionName: String,
        val versionCode: Int,
        val downloadUrl: String,
        val releaseNotes: String,
        val isMandatory: Boolean,
        val releaseDate: String
    )
    
    data class AppVersion(
        val versionName: String,
        val versionCode: Int,
        val buildDate: String
    )
    
    /**
     * Obtém informações da versão atual do app
     */
    fun getCurrentAppVersion(context: Context): AppVersion {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_META_DATA
            )
            
            val buildDate = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
                .format(Date(packageInfo.lastUpdateTime))
            
            AppVersion(
                versionName = packageInfo.versionName ?: "Desconhecida",
                versionCode = packageInfo.versionCode,
                buildDate = buildDate
            )
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao obter versão atual: ${e.message}")
            AppVersion("Desconhecida", 0, "N/A")
        }
    }
    
    /**
     * Verifica se há atualizações disponíveis
     * Por enquanto simula a verificação, mas você pode implementar uma chamada real para seu servidor
     */
    suspend fun checkForUpdates(context: Context): UpdateCheckResult {
        return withContext(Dispatchers.IO) {
            try {
                // Simular delay de rede
                Thread.sleep(1000)
                
                // Por enquanto, vamos simular que não há atualização
                // Para testar, você pode alterar hasUpdate para true
                val hasUpdate = false
                
                if (hasUpdate) {
                    // Simular dados de atualização
                    val updateInfo = UpdateInfo(
                        versionName = "1.1",
                        versionCode = 2,
                        downloadUrl = "https://seuservidor.com/downloads/iface-offline-v1.1.apk",
                        releaseNotes = "• Correções de bugs\n• Melhorias na interface\n• Novos recursos de sincronização",
                        isMandatory = false,
                        releaseDate = "15/01/2024"
                    )
                    
                    // Salvar informações da atualização
                    saveUpdateInfo(context, updateInfo)
                    
                    UpdateCheckResult.UpdateAvailable(updateInfo)
                } else {
                    UpdateCheckResult.NoUpdateAvailable
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao verificar atualizações: ${e.message}")
                UpdateCheckResult.Error(e.message ?: "Erro desconhecido")
            }
        }
    }
    
    /**
     * Verifica atualizações reais via HTTP (implementação exemplo)
     * Você pode usar esta função quando tiver um servidor configurado
     */
    suspend fun checkForUpdatesFromServer(context: Context): UpdateCheckResult {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL(UPDATE_CHECK_URL)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("User-Agent", "iFace-Offline-Android")
                connection.setRequestProperty("Accept", "application/json")
                
                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream))
                    val response = StringBuilder()
                    var line: String?
                    
                    while (reader.readLine().also { line = it } != null) {
                        response.append(line)
                    }
                    reader.close()
                    
                    // Parse da resposta JSON
                    val jsonResponse = JSONObject(response.toString())
                    val currentVersion = getCurrentAppVersion(context)
                    
                    val serverVersionCode = jsonResponse.optInt("versionCode", 0)
                    
                    if (serverVersionCode > currentVersion.versionCode) {
                        val updateInfo = UpdateInfo(
                            versionName = jsonResponse.optString("versionName", ""),
                            versionCode = serverVersionCode,
                            downloadUrl = jsonResponse.optString("downloadUrl", ""),
                            releaseNotes = jsonResponse.optString("releaseNotes", ""),
                            isMandatory = jsonResponse.optBoolean("isMandatory", false),
                            releaseDate = jsonResponse.optString("releaseDate", "")
                        )
                        
                        saveUpdateInfo(context, updateInfo)
                        UpdateCheckResult.UpdateAvailable(updateInfo)
                    } else {
                        UpdateCheckResult.NoUpdateAvailable
                    }
                } else {
                    UpdateCheckResult.Error("Erro HTTP: $responseCode")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao verificar atualizações do servidor: ${e.message}")
                UpdateCheckResult.Error(e.message ?: "Erro de conexão")
            }
        }
    }
    
    /**
     * Salva informações da atualização no SharedPreferences
     */
    private fun saveUpdateInfo(context: Context, updateInfo: UpdateInfo) {
        val prefs = context.getSharedPreferences("UpdatePrefs", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        
        editor.putString(PREF_UPDATE_INFO, """
            {
                "versionName": "${updateInfo.versionName}",
                "versionCode": ${updateInfo.versionCode},
                "downloadUrl": "${updateInfo.downloadUrl}",
                "releaseNotes": "${updateInfo.releaseNotes}",
                "isMandatory": ${updateInfo.isMandatory},
                "releaseDate": "${updateInfo.releaseDate}"
            }
        """.trimIndent())
        
        editor.putLong(PREF_LAST_UPDATE_CHECK, System.currentTimeMillis())
        editor.apply()
    }
    
    /**
     * Obtém informações da última atualização salva
     */
    fun getLastUpdateInfo(context: Context): UpdateInfo? {
        val prefs = context.getSharedPreferences("UpdatePrefs", Context.MODE_PRIVATE)
        val updateInfoJson = prefs.getString(PREF_UPDATE_INFO, null)
        
        return try {
            if (updateInfoJson != null) {
                val json = JSONObject(updateInfoJson)
                UpdateInfo(
                    versionName = json.getString("versionName"),
                    versionCode = json.getInt("versionCode"),
                    downloadUrl = json.getString("downloadUrl"),
                    releaseNotes = json.getString("releaseNotes"),
                    isMandatory = json.getBoolean("isMandatory"),
                    releaseDate = json.getString("releaseDate")
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao parsear informações de atualização: ${e.message}")
            null
        }
    }
    
    /**
     * Verifica se já foi feita uma verificação recente (dentro de 1 hora)
     */
    fun shouldCheckForUpdates(context: Context): Boolean {
        val prefs = context.getSharedPreferences("UpdatePrefs", Context.MODE_PRIVATE)
        val lastCheck = prefs.getLong(PREF_LAST_UPDATE_CHECK, 0)
        val currentTime = System.currentTimeMillis()
        val oneHour = 60 * 60 * 1000 // 1 hora em millisegundos
        
        return (currentTime - lastCheck) > oneHour
    }
}

sealed class UpdateCheckResult {
    object NoUpdateAvailable : UpdateCheckResult()
    data class UpdateAvailable(val updateInfo: UpdateManager.UpdateInfo) : UpdateCheckResult()
    data class Error(val message: String) : UpdateCheckResult()
} 