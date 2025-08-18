package com.example.iface_offilne.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
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
                Log.d(TAG, "🚀 === EXECUTANDO SINCRONIZAÇÃO AUTOMÁTICA ===")
                
                // Verificar se a sincronização ainda está ativa
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                    val sincronizacaoAtiva = ConfiguracoesManager.isSincronizacaoAtiva(it)
                        val intervalo = ConfiguracoesManager.getIntervaloSincronizacao(it)
                        
                        Log.d(TAG, "🔍 Status da sincronização: ativa=$sincronizacaoAtiva, intervalo=${intervalo}h")
                        
                    if (sincronizacaoAtiva) {
                        Log.d(TAG, "✅ Sincronização ativa, executando...")
                        
                        // Executar sincronização usando PontoSincronizacaoService
                        try {
                            val pontoService = PontoSincronizacaoService()
                                
                                // ✅ NOVO: Verificar se há pontos para sincronizar
                                val pontosPendentes = pontoService.getQuantidadePontosPendentes(it)
                                Log.d(TAG, "📊 Pontos pendentes: $pontosPendentes")
                                
                                if (pontosPendentes > 0) {
                                    val resultado = pontoService.sincronizarPontosPendentesComHistorico(it, "Sincronização automática")
                                    Log.d(TAG, "📤 Resultado da sincronização: $resultado")
                                } else {
                                    Log.d(TAG, "📭 Nenhum ponto para sincronizar")
                                }
                                
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ Erro na sincronização automática: ${e.message}")
                                e.printStackTrace()
                            }
                            
                            // ✅ CORREÇÃO CRÍTICA: Reagendar próximo alarme para Android 6+
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                try {
                                    Log.d(TAG, "🔄 Reagendando próximo alarme (Android 6+)...")
                                    val service = SincronizacaoService()
                                    service.configurarAlarme(it, 0, 0, intervalo) // Usar hora atual + intervalo
                                } catch (e: Exception) {
                                    Log.e(TAG, "❌ Erro ao reagendar alarme: ${e.message}")
                                }
                        }
                        
                    } else {
                        Log.d(TAG, "❌ Sincronização desativada, cancelando alarme...")
                        SincronizacaoService().cancelarAlarme(it)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Erro crítico na sincronização: ${e.message}")
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    fun configurarAlarme(context: Context, hora: Int, minuto: Int, intervalo: Int = 24) {
        try {
            Log.d(TAG, "🔧 === CONFIGURANDO ALARME DE SINCRONIZAÇÃO ===")
            Log.d(TAG, "⏰ Horário: ${String.format("%02d:%02d", hora, minuto)}")
            Log.d(TAG, "🔁 Intervalo: $intervalo horas")
            
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            
            // ✅ CORREÇÃO: Cancelar alarme anterior primeiro
            cancelarAlarme(context)
            
            val intent = Intent(context, SincronizacaoReceiver::class.java).apply {
                action = ACTION_SINCRONIZAR
            }
            
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // ✅ CORREÇÃO: Usar intervalo baseado no momento atual, não no horário específico
            val intervaloMillis = intervalo * 60 * 60 * 1000L // Converter horas para milissegundos
            val proximaExecucao = System.currentTimeMillis() + intervaloMillis
            
            Log.d(TAG, "🕐 Primeira execução em: ${java.text.SimpleDateFormat("dd/MM HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(proximaExecucao))}")
            
            // ✅ CORREÇÃO: Usar setInexactRepeating para economia de bateria
            // Android otimiza automaticamente os alarmes inexatos
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    // Para Android 6+, usar setExactAndAllowWhileIdle com reagendamento manual
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        proximaExecucao,
                        pendingIntent
                    )
                    Log.d(TAG, "✅ Alarme configurado com setExactAndAllowWhileIdle (Android 6+)")
                } else {
                    // Para versões mais antigas
            alarmManager.setRepeating(
                AlarmManager.RTC_WAKEUP,
                        proximaExecucao,
                        intervaloMillis,
                        pendingIntent
                    )
                    Log.d(TAG, "✅ Alarme configurado com setRepeating (Android < 6)")
                }
            } catch (e: SecurityException) {
                Log.w(TAG, "⚠️ Permissão de alarme exato negada, usando inexact repeating")
                // Fallback para alarme inexato
                alarmManager.setInexactRepeating(
                    AlarmManager.RTC_WAKEUP,
                    proximaExecucao,
                intervaloMillis,
                pendingIntent
            )
                Log.d(TAG, "✅ Alarme configurado com setInexactRepeating (fallback)")
            }

            Log.d(TAG, "✅ Alarme configurado com sucesso!")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao configurar alarme: ${e.message}")
            e.printStackTrace()
        }
    }

    fun cancelarAlarme(context: Context) {
        try {
            Log.d(TAG, "🛑 === CANCELANDO ALARME DE SINCRONIZAÇÃO ===")
            
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
                Log.d(TAG, "✅ Alarme cancelado com sucesso")
            } else {
                Log.d(TAG, "ℹ️ Nenhum alarme ativo para cancelar")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao cancelar alarme: ${e.message}")
            e.printStackTrace()
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
                Log.d(TAG, "🔍 === DEBUG SINCRONIZAÇÃO COMPLETO ===")
                
                // Verificar configurações
                val localizacaoId = ConfiguracoesManager.getLocalizacaoId(context)
                val codigoSincronizacao = ConfiguracoesManager.getCodigoSincronizacao(context)
                val sincronizacaoAtiva = ConfiguracoesManager.isSincronizacaoAtiva(context)
                val intervalo = ConfiguracoesManager.getIntervaloSincronizacao(context)
                
                Log.d(TAG, "📍 Localização ID: '$localizacaoId'")
                Log.d(TAG, "🔑 Código Sincronização: '$codigoSincronizacao'")
                Log.d(TAG, "🔄 Sincronização Ativa: $sincronizacaoAtiva")
                Log.d(TAG, "⏱️ Intervalo: $intervalo horas")
                
                // ✅ NOVO: Verificar status do alarme
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
                    
                    val alarmeAtivo = pendingIntent != null
                    Log.d(TAG, "⏰ Alarme Ativo: $alarmeAtivo")
                    
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val canScheduleExactAlarms = alarmManager.canScheduleExactAlarms()
                        Log.d(TAG, "🔐 Permissão Alarme Exato: $canScheduleExactAlarms")
                    }
                    
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Erro ao verificar alarme: ${e.message}")
                }
                
                // Verificar pontos pendentes
                val pontoService = PontoSincronizacaoService()
                val pontosPendentes = pontoService.getQuantidadePontosPendentes(context)
                Log.d(TAG, "📊 Pontos Pendentes: $pontosPendentes")
                
                // ✅ NOVO: Verificar entidade configurada
                val entidade = com.example.iface_offilne.util.SessionManager.entidade
                Log.d(TAG, "🏢 Entidade: ${entidade?.name ?: "NÃO CONFIGURADA"}")
                
                // Verificar se configurações estão válidas
                val configuracoesValidas = localizacaoId.isNotEmpty() && codigoSincronizacao.isNotEmpty() && entidade != null
                Log.d(TAG, "✅ Configurações Válidas: $configuracoesValidas")
                
                // ✅ DIAGNÓSTICO DE PROBLEMAS
                val problemas = mutableListOf<String>()
                
                if (localizacaoId.isEmpty()) problemas.add("Localização ID não preenchida")
                if (codigoSincronizacao.isEmpty()) problemas.add("Código de sincronização não preenchido")
                if (entidade == null) problemas.add("Entidade não configurada")
                if (!sincronizacaoAtiva) problemas.add("Sincronização automática desativada")
                if (pontosPendentes == 0) problemas.add("Nenhum ponto para sincronizar")
                
                if (problemas.isNotEmpty()) {
                    Log.w(TAG, "⚠️ PROBLEMAS ENCONTRADOS:")
                    problemas.forEach { problema ->
                        Log.w(TAG, "   ❌ $problema")
                    }
                } else {
                    Log.d(TAG, "✅ TUDO CONFIGURADO CORRETAMENTE!")
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
    
    // ✅ NOVA FUNÇÃO: Testar alarme imediatamente (para debug)
    fun testarAlarmeImediato(context: Context) {
        Log.d(TAG, "🧪 === TESTE DE ALARME IMEDIATO ===")
        
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, SincronizacaoReceiver::class.java).apply {
                action = ACTION_SINCRONIZAR
            }
            
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE + 1, // Usar ID diferente para teste
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Disparar em 10 segundos
            val proximaExecucao = System.currentTimeMillis() + 10000L
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    proximaExecucao,
                    pendingIntent
                )
            } else {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    proximaExecucao,
                    pendingIntent
                )
            }
            
            Log.d(TAG, "✅ Alarme de teste configurado para 10 segundos")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao configurar alarme de teste: ${e.message}")
        }
    }
} 