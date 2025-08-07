package com.example.iface_offilne

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import com.example.iface_offilne.data.AppDatabase
import com.example.iface_offilne.data.FuncionariosEntity
import com.example.iface_offilne.databinding.ActivityUsuarioEditBinding
import com.example.iface_offilne.models.FuncionariosLocalModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class UsuarioEdit : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityUsuarioEditBinding
    private var usuario: FuncionariosLocalModel? = null
    
    companion object {
        const val RESULT_USER_UPDATED = 100
        const val EXTRA_USER_UPDATED = "user_updated"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        usuario = intent.getSerializableExtra("usuario") as? FuncionariosLocalModel

        binding = ActivityUsuarioEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Carregar dados do usuário nos campos
        loadUserData()

        // Carregar foto facial se existir
        loadFacialPhoto(usuario)

        binding.btnCadastrarFacial.setOnClickListener {
            val screen = Intent(this@UsuarioEdit, CameraActivity::class.java)
            screen.putExtra("usuario", usuario)
            startActivity(screen)
        }

        binding.btnSalvar.setOnClickListener {
            saveUserChanges()
        }

        binding.btnCancelar.setOnClickListener {
            finish()
        }

        binding.btnExcluir.setOnClickListener {
            showDeleteConfirmationDialog()
        }

        // Configurar clique na seta de voltar
        binding.arrowLeft.setOnClickListener {
            finish()
        }

        // Remover backgroundTint dos botões para usar o background personalizado
        binding.btnSalvar.backgroundTintList = null
        binding.btnExcluir.backgroundTintList = null
        binding.btnCadastrarFacial.backgroundTintList = null
        binding.btnCancelar.backgroundTintList = null
    }

    private fun loadUserData() {
        usuario?.let { user ->
            binding.editTextCodigo.setText(user.codigo)
            binding.editTextNome.setText(user.nome)
            
            // Configurar status ativo/inativo
            if (user.ativo == 1) {
                binding.radioButtonAtivo.isChecked = true
            } else {
                binding.radioButtonInativo.isChecked = true
            }
        }
    }

    private fun saveUserChanges() {
        val codigo = binding.editTextCodigo.text.toString().trim()
        val nome = binding.editTextNome.text.toString().trim()
        
        // Validação básica
        if (codigo.isEmpty()) {
            Toast.makeText(this, "Por favor, preencha o código", Toast.LENGTH_SHORT).show()
            binding.editTextCodigo.requestFocus()
            return
        }
        
        if (nome.isEmpty()) {
            Toast.makeText(this, "Por favor, preencha o nome", Toast.LENGTH_SHORT).show()
            binding.editTextNome.requestFocus()
            return
        }

        usuario?.let { user ->
            // Atualizar dados do usuário
            user.codigo = codigo
            user.nome = nome
            user.ativo = if (binding.radioButtonAtivo.isChecked) 1 else 0

            // Salvar no banco de dados
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val database = AppDatabase.getInstance(applicationContext)
                    val funcionarioDao = database.funcionarioDao()
                    
                    // Converter FuncionariosLocalModel para FuncionariosEntity
                    val funcionarioEntity = FuncionariosEntity(
                        id = user.id,
                        codigo = user.codigo,
                        nome = user.nome,
                        ativo = user.ativo
                    )
                    
                    funcionarioDao.update(funcionarioEntity)
                    
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@UsuarioEdit, "Alterações salvas com sucesso!", Toast.LENGTH_SHORT).show()
                        
                        // Retornar resultado indicando que o usuário foi atualizado
                        val resultIntent = Intent()
                        resultIntent.putExtra(EXTRA_USER_UPDATED, user)
                        setResult(RESULT_USER_UPDATED, resultIntent)
                        
                        finish()
                    }
                } catch (e: Exception) {
                    Log.e("UsuarioEdit", "Erro ao salvar alterações", e)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@UsuarioEdit, "Erro ao salvar alterações: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        } ?: run {
            Toast.makeText(this, "Erro: usuário não encontrado", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        // Recarregar foto quando voltar da tela de cadastro
        loadFacialPhoto(usuario)
    }

    private fun loadFacialPhoto(usuario: FuncionariosLocalModel?) {
        if (usuario == null) return

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val database = AppDatabase.getInstance(applicationContext)
                val faceDao = database.faceDao()
                val faceEntity = faceDao.getByFuncionarioId(usuario.codigo)

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

    private fun showDeleteConfirmationDialog() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val database = AppDatabase.getInstance(applicationContext)
                val faceDao = database.faceDao()
                val pontosDao = database.pontosGenericosDao()
                
                // Verificar se tem foto facial
                val hasFace = faceDao.getByFuncionarioId(usuario?.codigo ?: "") != null
                
                // Verificar se tem pontos registrados
                val pontos = pontosDao.getPontosByFuncionario(usuario?.codigo ?: "")
                val hasPontos = pontos.isNotEmpty()
                
                withContext(Dispatchers.Main) {
                    val message = buildString {
                        append("Tem certeza que deseja excluir o funcionário ${usuario?.nome}?")
                        if (hasFace) {
                            append("\n\n⚠️ Este funcionário possui foto facial cadastrada.")
                        }
                        if (hasPontos) {
                            append("\n\n⚠️ Este funcionário possui ${pontos.size} ponto(s) registrado(s).")
                        }
                        append("\n\nEsta ação não pode ser desfeita!")
                    }
                    
                    AlertDialog.Builder(this@UsuarioEdit)
                        .setTitle("Confirmar Exclusão")
                        .setMessage(message)
                        .setPositiveButton("Excluir") { dialog, _ ->
                            deleteUser()
                            dialog.dismiss()
                        }
                        .setNegativeButton("Cancelar") { dialog, _ ->
                            dialog.dismiss()
                        }
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .show()
                }
            } catch (e: Exception) {
                Log.e("UsuarioEdit", "Erro ao verificar dados para exclusão", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@UsuarioEdit, "Erro ao verificar dados: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun deleteUser() {
        usuario?.let { user ->
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val database = AppDatabase.getInstance(applicationContext)
                    val funcionarioDao = database.funcionarioDao()
                    val faceDao = database.faceDao()
                    val pontosDao = database.pontosGenericosDao()
                    
                    // Excluir funcionário
                    val funcionarioEntity = FuncionariosEntity(
                        id = user.id,
                        codigo = user.codigo,
                        nome = user.nome,
                        ativo = user.ativo
                    )
                    
                    // Excluir foto facial se existir
                    val faceEntity = faceDao.getByFuncionarioId(user.codigo)
                    if (faceEntity != null) {
                        faceDao.delete(faceEntity)
                    }
                    
                    // Excluir pontos se existirem
                    val pontos = pontosDao.getPontosByFuncionario(user.codigo)
                    pontos.forEach { ponto ->
                        pontosDao.delete(ponto)
                    }
                    
                    // Excluir funcionário
                    funcionarioDao.delete(funcionarioEntity)
                    
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@UsuarioEdit, "Funcionário excluído com sucesso!", Toast.LENGTH_SHORT).show()
                        
                        // Retornar resultado indicando que o usuário foi excluído
                        val resultIntent = Intent()
                        resultIntent.putExtra(EXTRA_USER_UPDATED, user)
                        setResult(RESULT_USER_UPDATED, resultIntent)
                        
                        finish()
                    }
                } catch (e: Exception) {
                    Log.e("UsuarioEdit", "Erro ao excluir funcionário", e)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@UsuarioEdit, "Erro ao excluir funcionário: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        } ?: run {
            Toast.makeText(this, "Erro: usuário não encontrado", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_usuario_edit)
        return navController.navigateUp(appBarConfiguration)
                || super.onSupportNavigateUp()
    }
}