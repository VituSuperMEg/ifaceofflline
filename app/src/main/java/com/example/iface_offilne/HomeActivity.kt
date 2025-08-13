package com.example.iface_offilne

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.iface_offilne.databinding.ActivityHomeBinding

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Card Usuários
        binding.cardUsuarios.setOnClickListener {
            val screen = Intent(this@HomeActivity, UsuariosActivity::class.java)
            startActivity(screen)
        }

        // Card Funcionários
        binding.cardFuncionarios.setOnClickListener {
            val screen = Intent(this@HomeActivity, FuncionariosActivity::class.java)
            startActivity(screen)
        }

        // Card Registrar Ponto
        binding.cardRegistrarPonto.setOnClickListener {
            val screen = Intent(this@HomeActivity, FaceRecognitionActivity::class.java)
            startActivity(screen)
        }

        // Card Visualizar Pontos (Relatórios)
        binding.cardVisualizarPontos.setOnClickListener {
            val screen = Intent(this@HomeActivity, VisualizarPontosActivity::class.java)
            startActivity(screen)
        }

        // Card Configurações
        binding.cardConfiguracoes.setOnClickListener {
            val screen = Intent(this@HomeActivity, ConfiguracoesActivity::class.java)
            startActivity(screen)
        }
    }
}