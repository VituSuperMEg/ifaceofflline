package com.example.iface_offilne

import android.content.Intent
import android.os.Bundle
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import com.example.iface_offilne.databinding.ActivityHomeBinding

class HomeActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
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
            // Ir para tela de reconhecimento facial para registrar ponto
            val screen = Intent(this@HomeActivity, FaceRecognitionActivity::class.java)
            startActivity(screen)
        }

        // Card Visualizar Pontos (Relatórios)
        binding.cardVisualizarPontos.setOnClickListener {
            val screen = Intent(this@HomeActivity, VisualizarPontosActivity::class.java)
            startActivity(screen)
        }

        // Card Configurações (placeholder - pode implementar depois)
        binding.cardConfiguracoes.setOnClickListener {
            // TODO: Implementar tela de configurações
            android.widget.Toast.makeText(this, "⚙️ Configurações em desenvolvimento", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_home)
        return navController.navigateUp(appBarConfiguration)
                || super.onSupportNavigateUp()
    }
}