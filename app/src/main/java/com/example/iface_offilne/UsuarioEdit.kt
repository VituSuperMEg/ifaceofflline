package com.example.iface_offilne

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import com.example.iface_offilne.data.AppDatabase
import com.example.iface_offilne.databinding.ActivityUsuarioEditBinding
import com.example.iface_offilne.models.FuncionariosLocalModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class UsuarioEdit : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityUsuarioEditBinding


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val usuario = intent.getSerializableExtra("usuario") as? FuncionariosLocalModel

        binding = ActivityUsuarioEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.nomeEditUsuario.text = usuario?.nome.toString()

        // Carregar foto facial se existir
        loadFacialPhoto(usuario)

        binding.btnCadastrarFacial.setOnClickListener {
            val screen = Intent(this@UsuarioEdit, CameraActivity::class.java)
            screen.putExtra("usuario", usuario)
            startActivity(screen)
        }

        // Configurar clique na seta de voltar
        binding.arrowLeft.setOnClickListener {
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        // Recarregar foto quando voltar da tela de cadastro
        val usuario = intent.getSerializableExtra("usuario") as? FuncionariosLocalModel
        loadFacialPhoto(usuario)
    }

    private fun loadFacialPhoto(usuario: FuncionariosLocalModel?) {
        if (usuario == null) return

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val database = AppDatabase.getInstance(applicationContext)
                val faceDao = database.faceDao()
                val faceEntity = faceDao.getByFuncionarioId(usuario.id.toString())

                withContext(Dispatchers.Main) {
                    if (faceEntity != null) {
                        // Usuário tem foto facial cadastrada
                        binding.textViewStatusFacial.text = "✅ Facial cadastrado"
                        binding.textViewStatusFacial.setTextColor(getColor(android.R.color.holo_green_dark))
                        binding.btnCadastrarFacial.text = "Atualizar Facial"
                        
                        // Tentar carregar uma foto salva (se houver)
                        // Por enquanto, exibir ícone de sucesso
                        binding.imageViewFacial.setImageResource(android.R.drawable.ic_menu_camera)
                    } else {
                        // Usuário não tem foto facial
                        binding.textViewStatusFacial.text = "❌ Nenhuma foto facial cadastrada"
                        binding.textViewStatusFacial.setTextColor(getColor(android.R.color.holo_red_dark))
                        binding.btnCadastrarFacial.text = "Cadastrar Facial"
                        binding.imageViewFacial.setImageResource(android.R.drawable.ic_menu_camera)
                    }
                }
            } catch (e: Exception) {
                Log.e("UsuarioEdit", "Erro ao carregar foto facial", e)
                withContext(Dispatchers.Main) {
                    binding.textViewStatusFacial.text = "Erro ao verificar facial"
                    binding.textViewStatusFacial.setTextColor(getColor(android.R.color.holo_red_dark))
                }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_usuario_edit)
        return navController.navigateUp(appBarConfiguration)
                || super.onSupportNavigateUp()
    }
}