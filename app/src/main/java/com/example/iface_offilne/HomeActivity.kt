package com.example.iface_offilne

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.iface_offilne.databinding.ActivityHomeBinding
import com.example.iface_offilne.helpers.PermissaoHelper
import com.example.iface_offilne.util.SessionManager

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private val TAG = "HomeActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // ✅ NOVO: Verificar se entidade está configurada
        verificarEntidadeConfigurada()

        // ✅ NOVO: Sistema de permissões implementado com tratamento robusto
        val permissaoHelper = PermissaoHelper(this)
        
        // Card Usuários
        binding.cardUsuarios.setOnClickListener {
            verificarEntidadeEExecutar {
                permissaoHelper.verificarPermissaoFuncionarios {
                    val screen = Intent(this@HomeActivity, UsuariosActivity::class.java)
                    startActivity(screen)
                }
            }
        }

        // Card Funcionários
        binding.cardFuncionarios.setOnClickListener {
            verificarEntidadeEExecutar {
                permissaoHelper.verificarPermissaoFuncionarios {
                    val screen = Intent(this@HomeActivity, FuncionariosActivity::class.java)
                    startActivity(screen)
                }
            }
        }

        // Card Registrar Ponto
        binding.cardRegistrarPonto.setOnClickListener {
            verificarEntidadeEExecutar {
                permissaoHelper.verificarPermissaoPontos {
                    val screen = Intent(this@HomeActivity, FaceRecognitionActivity::class.java)
                    startActivity(screen)
                }
            }
        }

        // Card Visualizar Pontos (Relatórios)
        binding.cardVisualizarPontos.setOnClickListener {
            verificarEntidadeEExecutar {
                permissaoHelper.verificarPermissaoRelatorios {
                    val screen = Intent(this@HomeActivity, VisualizarPontosActivity::class.java)
                    startActivity(screen)
                }
            }
        }

        // Card Configurações
        binding.cardConfiguracoes.setOnClickListener {
            verificarEntidadeEExecutar {
                permissaoHelper.verificarPermissaoConfiguracoes {
                    val screen = Intent(this@HomeActivity, ConfiguracoesActivity::class.java)
                    startActivity(screen)
                }
            }
        }
    }

    /**
     * ✅ NOVO: Verifica se a entidade está configurada
     * Se não estiver, mostra alerta e redireciona para configurações
     */
    private fun verificarEntidadeConfigurada() {
        // ✅ NOVO: Usar método utilitário do SessionManager
        if (!SessionManager.isEntidadeConfigurada()) {
            Log.w(TAG, "⚠️ Entidade não configurada - redirecionando para configurações")
            Log.w(TAG, "  🔴 ${SessionManager.getEntidadeInfo()}")
            
            Toast.makeText(this, 
                "⚠️ Entidade não configurada!\nConfigure a entidade para usar o sistema.", 
                Toast.LENGTH_LONG).show()
            
            // Redirecionar para configurações após um delay
            binding.root.postDelayed({
                try {
                    val intent = Intent(this, ConfiguracoesActivity::class.java)
                    startActivity(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Erro ao abrir configurações: ${e.message}")
                    Toast.makeText(this, "❌ Erro ao abrir configurações", Toast.LENGTH_SHORT).show()
                }
            }, 2000) // 2 segundos de delay
        } else {
            Log.d(TAG, "✅ Entidade configurada: ${SessionManager.getEntidadeName()} (${SessionManager.getEntidadeId()})")
        }
    }

    /**
     * ✅ NOVO: Verifica entidade antes de executar ação
     * @param action Ação a ser executada se entidade estiver configurada
     */
    private fun verificarEntidadeEExecutar(action: () -> Unit) {
        // ✅ NOVO: Usar método utilitário do SessionManager
        if (!SessionManager.isEntidadeConfigurada()) {
            Log.w(TAG, "❌ Tentativa de acesso com entidade não configurada")
            Log.w(TAG, "  🔴 ${SessionManager.getEntidadeInfo()}")
            
            Toast.makeText(this, 
                "❌ Entidade não configurada!\nVá em Configurações e selecione uma entidade.", 
                Toast.LENGTH_LONG).show()
            
            // Redirecionar para configurações
            try {
                val intent = Intent(this, ConfiguracoesActivity::class.java)
                startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao abrir configurações: ${e.message}")
                Toast.makeText(this, "❌ Erro ao abrir configurações", Toast.LENGTH_SHORT).show()
            }
        } else {
            Log.d(TAG, "✅ Entidade configurada - executando ação")
            try {
                action()
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao executar ação: ${e.message}")
                Toast.makeText(this, "❌ Erro interno: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        
        // ✅ NOVO: Verificar entidade sempre que voltar para a tela
        Log.d(TAG, "🔄 onResume - verificando estado da entidade")
        verificarEntidadeConfigurada()
    }
}