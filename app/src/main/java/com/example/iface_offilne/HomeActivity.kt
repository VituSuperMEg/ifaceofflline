package com.example.iface_offilne

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.iface_offilne.databinding.ActivityHomeBinding
import com.example.iface_offilne.helpers.PermissaoHelper

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // ✅ NOVO: Sistema de permissões implementado
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
}