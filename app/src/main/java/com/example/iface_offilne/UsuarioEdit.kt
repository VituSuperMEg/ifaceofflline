package com.example.iface_offilne

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.ImageView
import android.widget.Toast
import java.io.File
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
            binding.editTextCargo.setText(user.cargo)
            binding.editTextSecretaria.setText(user.secretaria)
            
            // Configurar status ativo/inativo
            if (user.ativo == 1) {
                binding.chipAtivo.isChecked = true
            } else {
                binding.chipInativo.isChecked = true
            }
        }
    }

        private fun saveUserChanges() {
        val codigo = binding.editTextCodigo.text.toString().trim()
        val nome = binding.editTextNome.text.toString().trim()
        
        // Validação básica - campos agora são somente leitura, mas mantemos a validação por segurança
        if (codigo.isEmpty()) {
            binding.layoutCodigo.error = "Código é obrigatório"
            return
        }
        
        if (nome.isEmpty()) {
            binding.layoutNome.error = "Nome é obrigatório"
            return
        }

        usuario?.let { user ->
            // Atualizar apenas o status ativo/inativo (código, nome, cargo e secretaria são somente leitura)
            user.ativo = if (binding.chipAtivo.isChecked) 1 else 0

            // Salvar no banco de dados
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val database = AppDatabase.getInstance(applicationContext)
                    val funcionarioDao = database.funcionarioDao()
                    
                    // Converter FuncionariosLocalModel para FuncionariosEntity (funcionário importado)
                    // Código, nome, cargo e secretaria permanecem inalterados (somente leitura)
                    val funcionarioEntity = FuncionariosEntity(
                        id = user.id,
                        codigo = user.codigo,
                        nome = user.nome,
                        ativo = user.ativo,
                        cargo = user.cargo,
                        secretaria = user.secretaria
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
                        
                        // ✅ CARREGAR E EXIBIR A FOTO REAL SALVA
                        loadAndDisplayFacialImage(faceEntity)
                    } else {
                        // Usuário não tem foto facial
                        binding.textViewStatusFacial.text = "❌ Nenhuma foto facial cadastrada"
                        binding.textViewStatusFacial.setTextColor(getColor(android.R.color.holo_red_dark))
                        binding.btnCadastrarFacial.text = "Cadastrar Facial"
                        
                        // ✅ EXIBIR ÍCONE DE CÂMERA PARA INDICAR QUE NÃO HÁ FOTO
                        binding.imageViewFacial.setImageResource(android.R.drawable.ic_menu_camera)
                        binding.imageViewFacial.scaleType = ImageView.ScaleType.CENTER_INSIDE
                        binding.imageViewFacial.setColorFilter(getColor(android.R.color.darker_gray))
                    }
                }
            } catch (e: Exception) {
                Log.e("UsuarioEdit", "Erro ao carregar foto facial", e)
                withContext(Dispatchers.Main) {
                    binding.textViewStatusFacial.text = "Erro ao verificar facial"
                    binding.textViewStatusFacial.setTextColor(getColor(android.R.color.holo_red_dark))
                    binding.imageViewFacial.setImageResource(android.R.drawable.ic_dialog_alert)
                }
            }
        }
    }

    /**
     * ✅ CARREGAR E EXIBIR A FOTO FACIAL REAL SALVA
     */
    private fun loadAndDisplayFacialImage(faceEntity: com.example.iface_offilne.data.FaceEntity) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // ✅ PROCURAR FOTO NOS LOCAIS ONDE O CAMERAACTIVITY SALVA
                val bitmap = findAndLoadFacialImage(faceEntity.funcionarioId)
                
                withContext(Dispatchers.Main) {
                    if (bitmap != null) {
                        // ✅ EXIBIR A FOTO REAL
                        binding.imageViewFacial.setImageBitmap(bitmap)
                        binding.imageViewFacial.scaleType = ImageView.ScaleType.CENTER_CROP
                        binding.imageViewFacial.clearColorFilter()
                        
                        Log.d("UsuarioEdit", "✅ Foto facial carregada com sucesso")
                    } else {
                        // ✅ FALHA AO CARREGAR - USAR ÍCONE
                        showDefaultFacialIcon()
                        Log.d("UsuarioEdit", "ℹ️ Nenhuma foto encontrada - usando ícone padrão")
                    }
                }
            } catch (e: Exception) {
                Log.e("UsuarioEdit", "Erro ao carregar foto", e)
                withContext(Dispatchers.Main) {
                    showDefaultFacialIcon()
                }
            }
        }
    }

    /**
     * ✅ PROCURAR FOTO NOS LOCAIS ONDE O CAMERAACTIVITY SALVA
     */
    private fun findAndLoadFacialImage(funcionarioId: String): Bitmap? {
        try {
            // ✅ 1. PROCURAR NO DIRETÓRIO INTERNO (face_captures)
            val internalDir = File(filesDir, "face_captures")
            if (internalDir.exists()) {
                val files = internalDir.listFiles { file ->
                    file.name.startsWith("face_final_") && file.extension == "jpg"
                }
                
                if (files != null && files.isNotEmpty()) {
                    // ✅ PEGAR O ARQUIVO MAIS RECENTE
                    val latestFile = files.maxByOrNull { it.lastModified() }
                    if (latestFile != null) {
                        val bitmap = BitmapFactory.decodeFile(latestFile.absolutePath)
                        if (bitmap != null) {
                            Log.d("UsuarioEdit", "✅ Foto encontrada em face_captures: ${latestFile.name}")
                            return bitmap
                        }
                    }
                }
            }
            
            // ✅ 2. PROCURAR NO CACHE (face_captures)
            val cacheDir = File(cacheDir, "face_captures")
            if (cacheDir.exists()) {
                val files = cacheDir.listFiles { file ->
                    file.name.startsWith("face_final_") && file.extension == "jpg"
                }
                
                if (files != null && files.isNotEmpty()) {
                    val latestFile = files.maxByOrNull { it.lastModified() }
                    if (latestFile != null) {
                        val bitmap = BitmapFactory.decodeFile(latestFile.absolutePath)
                        if (bitmap != null) {
                            Log.d("UsuarioEdit", "✅ Foto encontrada em cache: ${latestFile.name}")
                            return bitmap
                        }
                    }
                }
            }
            
            // ✅ 3. PROCURAR NO ARMAZENAMENTO EXTERNO (Android < 13)
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
                val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val appDir = File(picturesDir, "FaceApp")
                if (appDir.exists()) {
                    val files = appDir.listFiles { file ->
                        file.name.startsWith("face_final_") && file.extension == "jpg"
                    }
                    
                    if (files != null && files.isNotEmpty()) {
                        val latestFile = files.maxByOrNull { it.lastModified() }
                        if (latestFile != null) {
                            val bitmap = BitmapFactory.decodeFile(latestFile.absolutePath)
                            if (bitmap != null) {
                                Log.d("UsuarioEdit", "✅ Foto encontrada em armazenamento externo: ${latestFile.name}")
                                return bitmap
                            }
                        }
                    }
                }
            }
            
            // ✅ 4. PROCURAR QUALQUER ARQUIVO DE FACE NO DIRETÓRIO INTERNO
            val allFaceFiles = filesDir.listFiles { file ->
                file.name.startsWith("face_") && file.extension == "jpg"
            }
            
            if (allFaceFiles != null && allFaceFiles.isNotEmpty()) {
                val latestFile = allFaceFiles.maxByOrNull { it.lastModified() }
                if (latestFile != null) {
                    val bitmap = BitmapFactory.decodeFile(latestFile.absolutePath)
                    if (bitmap != null) {
                        Log.d("UsuarioEdit", "✅ Foto encontrada no diretório interno: ${latestFile.name}")
                        return bitmap
                    }
                }
            }
            
            Log.d("UsuarioEdit", "ℹ️ Nenhuma foto facial encontrada nos locais esperados")
            return null
            
        } catch (e: Exception) {
            Log.e("UsuarioEdit", "Erro ao procurar foto facial", e)
            return null
        }
    }

    /**
     * ✅ TENTAR CARREGAR FOTO DO BANCO DE DADOS (FALLBACK)
     */
    private fun loadFacialImageFromDatabase(faceEntity: com.example.iface_offilne.data.FaceEntity) {
        // ✅ COMO A FACEENTITY NÃO TEM DADOS DE IMAGEM, USAR ÍCONE PADRÃO
        lifecycleScope.launch(Dispatchers.Main) {
            showDefaultFacialIcon()
            Log.d("UsuarioEdit", "ℹ️ FaceEntity não contém dados de imagem - usando ícone padrão")
        }
    }

    /**
     * ✅ EXIBIR ÍCONE PADRÃO PARA FOTO FACIAL
     */
    private fun showDefaultFacialIcon() {
        binding.imageViewFacial.setImageResource(android.R.drawable.ic_menu_camera)
        binding.imageViewFacial.scaleType = ImageView.ScaleType.CENTER_INSIDE
        binding.imageViewFacial.setColorFilter(getColor(android.R.color.holo_blue_dark))
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
                        ativo = user.ativo,
                        cargo = user.cargo,
                        secretaria = user.secretaria
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