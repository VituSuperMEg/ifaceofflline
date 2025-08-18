package com.example.iface_offilne.helpers

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.util.Log
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Helper para captura de geolocalização de forma simples e eficiente
 */
class LocationHelper(private val context: Context) {
    
    companion object {
        private const val TAG = "LocationHelper"
        private const val LOCATION_TIMEOUT = 10000L // 10 segundos timeout
    }
    
    data class LocationData(
        val latitude: Double,
        val longitude: Double,
        val accuracy: Float? = null,
        val timestamp: Long = System.currentTimeMillis()
    )
    
    /**
     * Verifica se as permissões de localização estão concedidas
     */
    fun hasLocationPermissions(): Boolean {
        return (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)
    }
    
    /**
     * Verifica se o GPS está habilitado
     */
    fun isLocationEnabled(): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || 
               locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }
    
    /**
     * Obtém a última localização conhecida de forma rápida
     */
    suspend fun getLastKnownLocation(): LocationData? {
        if (!hasLocationPermissions()) {
            Log.w(TAG, "⚠️ Permissões de localização não concedidas")
            return null
        }
        
        if (!isLocationEnabled()) {
            Log.w(TAG, "⚠️ Serviços de localização desabilitados")
            return null
        }
        
        return try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            
            // Tentar GPS primeiro, depois Network
            val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            
            for (provider in providers) {
                try {
                    if (locationManager.isProviderEnabled(provider)) {
                        val location = locationManager.getLastKnownLocation(provider)
                        if (location != null) {
                            Log.d(TAG, "📍 Localização obtida via $provider: ${location.latitude}, ${location.longitude}")
                            return LocationData(
                                latitude = location.latitude,
                                longitude = location.longitude,
                                accuracy = location.accuracy,
                                timestamp = location.time
                            )
                        }
                    }
                } catch (e: SecurityException) {
                    Log.e(TAG, "❌ Erro de segurança ao acessar $provider: ${e.message}")
                }
            }
            
            Log.w(TAG, "⚠️ Nenhuma localização disponível nos provedores")
            null
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao obter localização: ${e.message}")
            null
        }
    }
    
    /**
     * Método simplificado para uso no registro de ponto
     * Retorna rapidamente sem bloquear o processo
     */
    suspend fun getCurrentLocationForPoint(): LocationData? {
        return try {
            Log.d(TAG, "🌍 Tentando obter localização para registro de ponto...")
            
            // Usar apenas a última localização conhecida para ser rápido
            val location = getLastKnownLocation()
            
            if (location != null) {
                Log.d(TAG, "✅ Localização capturada: ${location.latitude}, ${location.longitude}")
                location
            } else {
                Log.w(TAG, "⚠️ Localização não disponível - ponto será registrado sem coordenadas")
                null
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao capturar localização: ${e.message}")
            null
        }
    }
    
    /**
     * Formata a localização para exibição
     */
    fun formatLocation(locationData: LocationData): String {
        return "📍 ${String.format("%.6f", locationData.latitude)}, ${String.format("%.6f", locationData.longitude)}"
    }
    
    /**
     * Verifica se a localização é válida
     */
    fun isValidLocation(latitude: Double?, longitude: Double?): Boolean {
        return latitude != null && longitude != null &&
               latitude >= -90.0 && latitude <= 90.0 &&
               longitude >= -180.0 && longitude <= 180.0 &&
               latitude != 0.0 && longitude != 0.0
    }
} 