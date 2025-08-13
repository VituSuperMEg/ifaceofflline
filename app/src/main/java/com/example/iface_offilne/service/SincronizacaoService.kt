package com.example.iface_offilne.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.iface_offilne.service.PontoSincronizacaoService
import com.example.iface_offilne.util.ConfiguracoesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*

class SincronizacaoService {
    
    companion object {
        private const val TAG = "SYNC_DEBUG"
        private const val ACTION_SINCRONIZAR = "com.example.iface_offilne.SINCRONIZAR"
        private const val REQUEST_CODE = 1001
    }

    class SincronizacaoReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_SINCRONIZAR) {
                Log.d(TAG, "🔄 Iniciando sincronização automática...")
                executarSincronizacao(context)
            }
        }

        private fun executarSincronizacao(context: Context?) {
            context?.let {
                // Verificar se a sincronização ainda está ativa
                CoroutineScope(Dispatchers.IO).launch {
                    val sincronizacaoAtiva = ConfiguracoesManager.isSincronizacaoAtiva(it)
                    if (sincronizacaoAtiva) {
                        Log.d(TAG, "✅ Sincronização ativa, executando...")
                        
                        // Executar sincronização usando PontoSincronizacaoService
                        try {
                            val pontoService = PontoSincronizacaoService()
                            pontoService.sincronizarPontosPendentesComHistorico(it, "Sincronização automática")
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ Erro na sincronização automática: ${e.message}")
                        }
                        
                    } else {
                        Log.d(TAG, "❌ Sincronização desativada, cancelando alarme...")
                        SincronizacaoService().cancelarAlarme(it)
                    }
                }
            }
        }
    }

    fun configurarAlarme(context: Context, hora: Int, minuto: Int, intervalo: Int = 24) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, SincronizacaoReceiver::class.java).apply {
                action = ACTION_SINCRONIZAR
            }
            
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Configurar horário
            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hora)
                set(Calendar.MINUTE, minuto)
                set(Calendar.SECOND, 0)
                
                // Se o horário já passou hoje, agendar para amanhã
                if (timeInMillis <= System.currentTimeMillis()) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }

            // Configurar alarme com intervalo personalizado
            val intervaloMillis = intervalo * 60 * 60 * 1000L // Converter horas para milissegundos
            
            alarmManager.setRepeating(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                intervaloMillis,
                pendingIntent
            )

            Log.d(TAG, "⏰ Alarme configurado para ${String.format("%02d:%02d", hora, minuto)}")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao configurar alarme: ${e.message}")
        }
    }

    fun cancelarAlarme(context: Context) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, SincronizacaoReceiver::class.java).apply {
                action = ACTION_SINCRONIZAR
            }
            
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )

            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
                Log.d(TAG, "❌ Alarme cancelado")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao cancelar alarme: ${e.message}")
        }
    }

    fun executarSincronizacaoManual(context: Context) {
        Log.d(TAG, "🔄 Executando sincronização manual...")
        
        // Debug - verificar configurações
        verificarStatusSincronizacao(context)
        
        // Executar sincronização usando PontoSincronizacaoService
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val pontoService = PontoSincronizacaoService()
                
                // Verificar quantos pontos pendentes existem
                val pontosPendentes = pontoService.getQuantidadePontosPendentes(context)
                Log.d(TAG, "📊 Pontos pendentes para sincronização: $pontosPendentes")
                
                if (pontosPendentes == 0) {
                    Log.w(TAG, "⚠️ Não há pontos para sincronizar!")
                }
                
                pontoService.sincronizarPontosPendentesComHistorico(context, "Sincronização manual")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro na sincronização manual: ${e.message}")
                e.printStackTrace()
            }
        }
    }
    
    // Método para debug - verificar status das configurações
    fun verificarStatusSincronizacao(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "🔍 === DEBUG SINCRONIZAÇÃO ===")
                
                // Verificar configurações
                val localizacaoId = ConfiguracoesManager.getLocalizacaoId(context)
                val codigoSincronizacao = ConfiguracoesManager.getCodigoSincronizacao(context)
                val sincronizacaoAtiva = ConfiguracoesManager.isSincronizacaoAtiva(context)
                val intervalo = ConfiguracoesManager.getIntervaloSincronizacao(context)
                
                Log.d(TAG, "📍 Localização ID: '$localizacaoId'")
                Log.d(TAG, "🔑 Código Sincronização: '$codigoSincronizacao'")
                Log.d(TAG, "🔄 Sincronização Ativa: $sincronizacaoAtiva")
                Log.d(TAG, "⏱️ Intervalo: $intervalo horas")
                
                // Verificar pontos pendentes
                val pontoService = PontoSincronizacaoService()
                val pontosPendentes = pontoService.getQuantidadePontosPendentes(context)
                Log.d(TAG, "📊 Pontos Pendentes: $pontosPendentes")
                
                // Verificar se configurações estão válidas
                val configuracoesValidas = localizacaoId.isNotEmpty() && codigoSincronizacao.isNotEmpty()
                Log.d(TAG, "✅ Configurações Válidas: $configuracoesValidas")
                
                if (!configuracoesValidas) {
                    Log.w(TAG, "⚠️ PROBLEMA: Configurações de localização/código não estão preenchidas!")
                }
                
                if (!sincronizacaoAtiva) {
                    Log.w(TAG, "⚠️ PROBLEMA: Sincronização automática está desativada!")
                }
                
                if (pontosPendentes == 0) {
                    Log.w(TAG, "⚠️ INFO: Não há pontos pendentes para sincronizar")
                }
                
                Log.d(TAG, "🔍 === FIM DEBUG ===")
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro no debug: ${e.message}")
            }
        }
    }
} 