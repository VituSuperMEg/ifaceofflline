package com.example.iface_offilne

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
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
import java.io.File
import android.view.View

class UsuarioEdit : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityUsuarioEditBinding
    private var usuario: FuncionariosLocalModel? = null
    
    companion object {
        const val RESULT_USER_UPDATED = 100
        const val EXTRA_USER_UPDATED = "user_updated"
        const val REQUEST_FACE_REGISTRATION = 200
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
        
        // ✅ CONFIGURAR IMAGEVIEW PARA MELHOR QUALIDADE
        binding.imageViewFacial.apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setLayerType(View.LAYER_TYPE_HARDWARE, null) // Aceleração por hardware
        }

        binding.btnCadastrarFacial.setOnClickListener {
            try {
                // ✅ Usar a nova FaceRegistrationActivity em vez da CameraActivity
                val screen = Intent(this@UsuarioEdit, FaceRegistrationActivity::class.java)
                screen.putExtra("usuario", usuario)
                startActivityForResult(screen, REQUEST_FACE_REGISTRATION)
            } catch (e: Exception) {
                Log.e("UsuarioEdit", "Erro ao abrir cadastro facial", e)
                Toast.makeText(this@UsuarioEdit, "Erro ao abrir cadastro facial: ${e.message}", Toast.LENGTH_LONG).show()
            }
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
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == REQUEST_FACE_REGISTRATION) {
            if (resultCode == RESULT_OK) {
                Toast.makeText(this, "Cadastro facial realizado com sucesso!", Toast.LENGTH_SHORT).show()
                // Recarregar foto facial
                loadFacialPhoto(usuario)
            } else {
                Toast.makeText(this, "Cadastro facial cancelado", Toast.LENGTH_SHORT).show()
            }
        }
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
                        
                        // ✅ CARREGAR FOTO SALVA DO FUNCIONÁRIO
                        loadSavedFacePhoto(usuario.codigo)
                        
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
    
    /**
     * ✅ NOVA FUNÇÃO: Carregar foto salva do funcionário - MELHORADA
     */
    private fun loadSavedFacePhoto(funcionarioId: String) {
        try {
            // ✅ TENTAR CARREGAR FOTO SALVA NO ARMAZENAMENTO INTERNO
            val photoFile = getFacePhotoFile(funcionarioId)
            
            if (photoFile.exists()) {
                Log.d("UsuarioEdit", "📸 Carregando foto salva: ${photoFile.absolutePath}")
                
                // ✅ CARREGAR E EXIBIR A FOTO COM MELHOR QUALIDADE
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeFile(photoFile.absolutePath, options)
                
                // ✅ CALCULAR TAMANHO IDEAL PARA EXIBIÇÃO
                val targetSize = 300 // Tamanho ideal para exibição
                val sampleSize = calculateInSampleSize(options, targetSize, targetSize)
                
                // ✅ CARREGAR BITMAP COM TAMANHO OTIMIZADO
                val loadOptions = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                
                val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath, loadOptions)
                if (bitmap != null) {
                    // ✅ CRIAR VERSÃO OTIMIZADA DA FOTO
                    val optimizedBitmap = createOptimizedCircularBitmap(bitmap)
                    binding.imageViewFacial.setImageBitmap(optimizedBitmap)
                    
                    Log.d("UsuarioEdit", "✅ Foto facial carregada e otimizada: ${bitmap.width}x${bitmap.height}")
                    
                    // ✅ LIMPAR BITMAP ORIGINAL
                    bitmap.recycle()
                } else {
                    Log.w("UsuarioEdit", "⚠️ Erro ao decodificar foto salva")
                    binding.imageViewFacial.setImageResource(android.R.drawable.ic_menu_camera)
                }
            } else {
                Log.d("UsuarioEdit", "📸 Nenhuma foto salva encontrada para $funcionarioId")
                binding.imageViewFacial.setImageResource(android.R.drawable.ic_menu_camera)
            }
            
        } catch (e: Exception) {
            Log.e("UsuarioEdit", "❌ Erro ao carregar foto salva", e)
            binding.imageViewFacial.setImageResource(android.R.drawable.ic_menu_camera)
        }
    }
    
    /**
     * ✅ NOVA FUNÇÃO: Calcular tamanho de amostra ideal
     */
    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1
        
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        
        return inSampleSize
    }
    
    /**
     * ✅ NOVA FUNÇÃO: Criar bitmap circular otimizado com melhor qualidade
     */
    private fun createOptimizedCircularBitmap(bitmap: Bitmap): Bitmap {
        try {
            // ✅ TAMANHO IDEAL PARA EXIBIÇÃO
            val targetSize = 300
            
            // ✅ REDIMENSIONAR PARA TAMANHO IDEAL
            val resizedBitmap = if (bitmap.width != targetSize || bitmap.height != targetSize) {
                Bitmap.createScaledBitmap(bitmap, targetSize, targetSize, true)
            } else {
                bitmap
            }
            
            // ✅ MELHORAR QUALIDADE DA IMAGEM
            val enhancedBitmap = enhanceImageQuality(resizedBitmap)
            
            // ✅ CORREÇÃO: Rotacionar a foto em -90 graus para ficar correta
            val rotatedBitmap = rotateBitmap(enhancedBitmap, -90f)
            
            // ✅ CRIAR BITMAP CIRCULAR COM QUALIDADE SUPERIOR
            val circularBitmap = createHighQualityCircularBitmap(rotatedBitmap)
            
            // ✅ LIMPAR BITMAPS TEMPORÁRIOS
            if (resizedBitmap != bitmap) {
                resizedBitmap.recycle()
            }
            if (enhancedBitmap != resizedBitmap) {
                enhancedBitmap.recycle()
            }
            if (rotatedBitmap != enhancedBitmap) {
                rotatedBitmap.recycle()
            }
            
            return circularBitmap
            
        } catch (e: Exception) {
            Log.e("UsuarioEdit", "❌ Erro ao criar bitmap circular otimizado", e)
            return bitmap
        }
    }
    
    /**
     * ✅ NOVA FUNÇÃO: Criar bitmap circular de alta qualidade
     */
    private fun createHighQualityCircularBitmap(bitmap: Bitmap): Bitmap {
        try {
            val size = bitmap.width
            val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            
            val canvas = Canvas(output)
            
            // ✅ PAINT PARA O CÍRCULO DE FUNDO (BORDA)
            val borderPaint = Paint().apply {
                isAntiAlias = true
                color = Color.parseColor("#E0E0E0") // Cinza claro para borda
                style = Paint.Style.FILL
            }
            
            // ✅ PAINT PARA A IMAGEM
            val imagePaint = Paint().apply {
                isAntiAlias = true
                isFilterBitmap = true
                isDither = true // Melhorar qualidade em dispositivos de baixa resolução
            }
            
            // ✅ DESENHAR CÍRCULO DE FUNDO (BORDA)
            val centerX = size / 2f
            val centerY = size / 2f
            val radius = (size / 2f) - 4f // Deixar 4px de borda
            
            canvas.drawCircle(centerX, centerY, radius, borderPaint)
            
            // ✅ CRIAR MÁSCARA CIRCULAR PARA A IMAGEM
            val maskPaint = Paint().apply {
                isAntiAlias = true
                color = Color.WHITE
                style = Paint.Style.FILL
            }
            
            // ✅ APLICAR MÁSCARA CIRCULAR
            val maskBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val maskCanvas = Canvas(maskBitmap)
            maskCanvas.drawCircle(centerX, centerY, radius - 2f, maskPaint) // 2px menor que a borda
            
            // ✅ APLICAR A IMAGEM COM MÁSCARA
            imagePaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            canvas.drawBitmap(bitmap, 0f, 0f, imagePaint)
            
            // ✅ APLICAR MÁSCARA
            val finalPaint = Paint().apply {
                isAntiAlias = true
                xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
            }
            canvas.drawBitmap(maskBitmap, 0f, 0f, finalPaint)
            
            // ✅ LIMPAR BITMAP DA MÁSCARA
            maskBitmap.recycle()
            
            return output
            
        } catch (e: Exception) {
            Log.e("UsuarioEdit", "❌ Erro ao criar bitmap circular de alta qualidade", e)
            return bitmap
        }
    }
    
    /**
     * ✅ NOVA FUNÇÃO: Obter arquivo da foto do funcionário
     */
    private fun getFacePhotoFile(funcionarioId: String): File {
        val photosDir = File(filesDir, "face_photos")
        if (!photosDir.exists()) {
            photosDir.mkdirs()
        }
        return File(photosDir, "face_${funcionarioId}.jpg")
    }
    
    /**
     * ✅ NOVA FUNÇÃO: Rotacionar bitmap
     */
    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        try {
            val matrix = Matrix()
            matrix.postRotate(degrees)
            
            val rotatedBitmap = Bitmap.createBitmap(
                bitmap, 
                0, 
                0, 
                bitmap.width, 
                bitmap.height, 
                matrix, 
                true
            )
            
            Log.d("UsuarioEdit", "✅ Bitmap rotacionado: ${bitmap.width}x${bitmap.height} -> ${rotatedBitmap.width}x${rotatedBitmap.height}")
            return rotatedBitmap
            
        } catch (e: Exception) {
            Log.e("UsuarioEdit", "❌ Erro ao rotacionar bitmap", e)
            return bitmap
        }
    }

    /**
     * ✅ NOVA FUNÇÃO: Melhorar qualidade da imagem automaticamente
     */
    private fun enhanceImageQuality(bitmap: Bitmap): Bitmap {
        try {
            val width = bitmap.width
            val height = bitmap.height
            val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            
            // ✅ CALCULAR BRILHO MÉDIO
            var totalBrightness = 0f
            for (pixel in pixels) {
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                totalBrightness += (r + g + b) / 3f
            }
            val avgBrightness = totalBrightness / pixels.size
            
            // ✅ AJUSTAR BRILHO E CONTRASTE AUTOMATICAMENTE
            val brightnessAdjustment = when {
                avgBrightness < 80f -> 1.3f  // Escurecer
                avgBrightness > 180f -> 0.8f // Clarear
                else -> 1.0f // Manter
            }
            
            val contrastAdjustment = 1.1f // Aumentar contraste levemente
            
            // ✅ APLICAR AJUSTES
            for (i in pixels.indices) {
                val pixel = pixels[i]
                var r = (pixel shr 16) and 0xFF
                var g = (pixel shr 8) and 0xFF
                var b = pixel and 0xFF
                
                // Ajustar brilho
                r = ((r * brightnessAdjustment).toInt()).coerceIn(0, 255)
                g = ((g * brightnessAdjustment).toInt()).coerceIn(0, 255)
                b = ((b * brightnessAdjustment).toInt()).coerceIn(0, 255)
                
                // Ajustar contraste
                val factor = (259 * (contrastAdjustment * 255 + 255)) / (255 * (259 - contrastAdjustment * 255))
                r = ((factor * (r - 128) + 128).toInt()).coerceIn(0, 255)
                g = ((factor * (g - 128) + 128).toInt()).coerceIn(0, 255)
                b = ((factor * (b - 128) + 128).toInt()).coerceIn(0, 255)
                
                pixels[i] = (pixel and 0xFF000000.toInt()) or (r shl 16) or (g shl 8) or b
            }
            
            output.setPixels(pixels, 0, width, 0, 0, width, height)
            return output
            
        } catch (e: Exception) {
            Log.e("UsuarioEdit", "❌ Erro ao melhorar qualidade da imagem", e)
            return bitmap
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