package com.example.iface_offilne

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.iface_offilne.databinding.ActivityHomeBinding
import com.example.iface_offilne.helpers.PermissaoHelper
import com.example.iface_offilne.service.SincronizacaoService
import com.example.iface_offilne.util.ConfiguracoesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private val TAG = "HomeActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // ✅ NOVO: Configurar alarme de sincronização automaticamente
        configurarAlarmeSincronizacaoAutomatico()

        // ✅ NOVO: Sistema de permissões implementado com tratamento robusto
        val permissaoHelper = PermissaoHelper(this)
        
        // Card Usuários
        binding.cardUsuarios.setOnClickListener {
                permissaoHelper.verificarPermissaoFuncionarios {
                    val screen = Intent(this@HomeActivity, UsuariosActivity::class.java)
                    startActivity(screen)
            }
        }

        // Card Funcionários
        binding.cardFuncionarios.setOnClickListener {
                permissaoHelper.verificarPermissaoFuncionarios {
                    val screen = Intent(this@HomeActivity, FuncionariosActivity::class.java)
                    startActivity(screen)
            }
        }

        // Card Registrar Ponto
        binding.cardRegistrarPonto.setOnClickListener {
                permissaoHelper.verificarPermissaoPontos {
                    val screen = Intent(this@HomeActivity, FaceRecognitionActivity::class.java)
                    startActivity(screen)
            }
        }

        // Card Visualizar Pontos (Relatórios)
        binding.cardVisualizarPontos.setOnClickListener {
                permissaoHelper.verificarPermissaoRelatorios {
                    val screen = Intent(this@HomeActivity, VisualizarPontosActivity::class.java)
                    startActivity(screen)
            }
        }

        // Card Configurações
        binding.cardConfiguracoes.setOnClickListener {
                permissaoHelper.verificarPermissaoConfiguracoes {
                    val screen = Intent(this@HomeActivity, ConfiguracoesActivity::class.java)
                    startActivity(screen)
            }
        }
    }

    /**
     * ✅ NOVO: Configura o alarme de sincronização automaticamente quando o app inicia
     * Verifica se a sincronização está ativa e configura o alarme se necessário
     */
    private fun configurarAlarmeSincronizacaoAutomatico() {
        Log.d(TAG, "🔧 === CONFIGURANDO ALARME DE SINCRONIZAÇÃO AUTOMÁTICO ===")
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Verificar se a sincronização está ativa
                val sincronizacaoAtiva = ConfiguracoesManager.isSincronizacaoAtiva(this@HomeActivity)
                val intervalo = ConfiguracoesManager.getIntervaloSincronizacao(this@HomeActivity)
                
                Log.d(TAG, "🔍 Status da sincronização: ativa=$sincronizacaoAtiva, intervalo=${intervalo}h")
                
                if (sincronizacaoAtiva) {
                    Log.d(TAG, "✅ Sincronização ativa - configurando alarme automaticamente")
                    
                    // Configurar alarme
                    val sincronizacaoService = SincronizacaoService()
                    sincronizacaoService.configurarAlarme(this@HomeActivity, 0, 0, intervalo)
                    
                    // Verificar status do alarme
                    sincronizacaoService.verificarStatusAlarme(this@HomeActivity)
                    
                    Log.d(TAG, "✅ Alarme de sincronização configurado automaticamente para $intervalo hora(s)")
                } else {
                    Log.d(TAG, "ℹ️ Sincronização desativada - alarme não configurado")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao configurar alarme automático: ${e.message}")
                e.printStackTrace()
            }
        }
    }



    override fun onResume() {
        super.onResume()
        
        // ✅ NOVO: Verificar status do alarme quando voltar para a tela
        verificarStatusAlarmeSincronizacao()
    }
    
    /**
     * ✅ NOVO: Verifica o status do alarme de sincronização
     */
    private fun verificarStatusAlarmeSincronizacao() {
        Log.d(TAG, "🔍 === VERIFICANDO STATUS DO ALARME NA TELA PRINCIPAL ===")
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val sincronizacaoAtiva = ConfiguracoesManager.isSincronizacaoAtiva(this@HomeActivity)
                val intervalo = ConfiguracoesManager.getIntervaloSincronizacao(this@HomeActivity)
                
                Log.d(TAG, "📊 Status: ativa=$sincronizacaoAtiva, intervalo=${intervalo}h")
                
                if (sincronizacaoAtiva) {
                    val sincronizacaoService = SincronizacaoService()
                    sincronizacaoService.verificarStatusAlarme(this@HomeActivity)
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao verificar status do alarme: ${e.message}")
            }
        }
    }
}