package com.example.iface_offilne

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.iface_offilne.models.FuncionariosLocalModel

class FaceRegistrationSuccessActivity : AppCompatActivity() {
    
    private lateinit var imageViewPhoto: ImageView
    private lateinit var textViewMessage: TextView
    private lateinit var textViewUserName: TextView
    private lateinit var textViewSuccessIcon: TextView
    private lateinit var buttonContinue: Button
    private lateinit var buttonBack: Button
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setupUI()
        setupData()
        setupButtons()
    }
    
    private fun setupUI() {
        // Criar layout programaticamente
        val layout = androidx.constraintlayout.widget.ConstraintLayout(this).apply {
            layoutParams = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams(
                androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.MATCH_PARENT,
                androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(android.graphics.Color.parseColor("#F5F5F5"))
        }
        
        // ImageView para a foto
        imageViewPhoto = ImageView(this).apply {
            id = View.generateViewId()
            scaleType = ImageView.ScaleType.FIT_CENTER
            background = androidx.core.content.ContextCompat.getDrawable(
                this@FaceRegistrationSuccessActivity, 
                R.drawable.circle_image_background
            )
            // Melhorar qualidade da imagem
            setPadding(8, 8, 8, 8)
            adjustViewBounds = true
            // Aplicar elevação para sombra
            elevation = dpToPx(8).toFloat()
        }
        
        // TextView para o nome do usuário
        textViewUserName = TextView(this).apply {
            id = View.generateViewId()
            textSize = 28f
            setTextColor(android.graphics.Color.parseColor("#333333"))
            gravity = android.view.Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(16, 0, 16, 0)
        }
        
        // TextView para o ícone de sucesso
        textViewSuccessIcon = TextView(this).apply {
            id = View.generateViewId()
            text = "✅"
            textSize = 56f
            gravity = android.view.Gravity.CENTER
        }
        
        // TextView para a mensagem de sucesso
        textViewMessage = TextView(this).apply {
            id = View.generateViewId()
            text = "Facial cadastrado com sucesso!"
            textSize = 20f
            setTextColor(android.graphics.Color.parseColor("#4CAF50"))
            gravity = android.view.Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(24, 0, 24, 0)
        }
        
        // Botão continuar
        buttonContinue = Button(this).apply {
            id = View.generateViewId()
            text = "Continuar"
            textSize = 18f
            setBackgroundColor(android.graphics.Color.parseColor("#4CAF50"))
            setTextColor(android.graphics.Color.WHITE)
            isAllCaps = false
            setPadding(16, 16, 16, 16)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        
        // Botão voltar
        buttonBack = Button(this).apply {
            id = View.generateViewId()
            text = "Voltar"
            textSize = 18f
            setBackgroundColor(android.graphics.Color.parseColor("#757575"))
            setTextColor(android.graphics.Color.WHITE)
            isAllCaps = false
            setPadding(16, 16, 16, 16)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        
        // Adicionar views ao layout
        layout.addView(imageViewPhoto)
        layout.addView(textViewUserName)
        layout.addView(textViewSuccessIcon)
        layout.addView(textViewMessage)
        layout.addView(buttonContinue)
        layout.addView(buttonBack)
        
        // Configurar constraints
        val constraintSet = androidx.constraintlayout.widget.ConstraintSet()
        constraintSet.clone(layout)
        
        // ImageView constraints (centro da tela, tamanho 280x280dp - maior)
        constraintSet.constrainWidth(imageViewPhoto.id, dpToPx(280))
        constraintSet.constrainHeight(imageViewPhoto.id, dpToPx(280))
        constraintSet.connect(imageViewPhoto.id, androidx.constraintlayout.widget.ConstraintSet.TOP, 
                             androidx.constraintlayout.widget.ConstraintSet.PARENT_ID, androidx.constraintlayout.widget.ConstraintSet.TOP, dpToPx(80))
        constraintSet.connect(imageViewPhoto.id, androidx.constraintlayout.widget.ConstraintSet.START, 
                             androidx.constraintlayout.widget.ConstraintSet.PARENT_ID, androidx.constraintlayout.widget.ConstraintSet.START)
        constraintSet.connect(imageViewPhoto.id, androidx.constraintlayout.widget.ConstraintSet.END, 
                             androidx.constraintlayout.widget.ConstraintSet.PARENT_ID, androidx.constraintlayout.widget.ConstraintSet.END)
        
        // TextView nome constraints
        constraintSet.connect(textViewUserName.id, androidx.constraintlayout.widget.ConstraintSet.TOP, 
                             imageViewPhoto.id, androidx.constraintlayout.widget.ConstraintSet.BOTTOM, dpToPx(32))
        constraintSet.connect(textViewUserName.id, androidx.constraintlayout.widget.ConstraintSet.START, 
                             androidx.constraintlayout.widget.ConstraintSet.PARENT_ID, androidx.constraintlayout.widget.ConstraintSet.START, dpToPx(16))
        constraintSet.connect(textViewUserName.id, androidx.constraintlayout.widget.ConstraintSet.END, 
                             androidx.constraintlayout.widget.ConstraintSet.PARENT_ID, androidx.constraintlayout.widget.ConstraintSet.END, dpToPx(16))
        
        // TextView ícone de sucesso constraints
        constraintSet.connect(textViewSuccessIcon.id, androidx.constraintlayout.widget.ConstraintSet.TOP, 
                             textViewUserName.id, androidx.constraintlayout.widget.ConstraintSet.BOTTOM, dpToPx(32))
        constraintSet.connect(textViewSuccessIcon.id, androidx.constraintlayout.widget.ConstraintSet.START, 
                             androidx.constraintlayout.widget.ConstraintSet.PARENT_ID, androidx.constraintlayout.widget.ConstraintSet.START)
        constraintSet.connect(textViewSuccessIcon.id, androidx.constraintlayout.widget.ConstraintSet.END, 
                             androidx.constraintlayout.widget.ConstraintSet.PARENT_ID, androidx.constraintlayout.widget.ConstraintSet.END)
        
        // TextView mensagem constraints
        constraintSet.connect(textViewMessage.id, androidx.constraintlayout.widget.ConstraintSet.TOP, 
                             textViewSuccessIcon.id, androidx.constraintlayout.widget.ConstraintSet.BOTTOM, dpToPx(20))
        constraintSet.connect(textViewMessage.id, androidx.constraintlayout.widget.ConstraintSet.START, 
                             androidx.constraintlayout.widget.ConstraintSet.PARENT_ID, androidx.constraintlayout.widget.ConstraintSet.START, dpToPx(16))
        constraintSet.connect(textViewMessage.id, androidx.constraintlayout.widget.ConstraintSet.END, 
                             androidx.constraintlayout.widget.ConstraintSet.PARENT_ID, androidx.constraintlayout.widget.ConstraintSet.END, dpToPx(16))
        
        // Botão continuar constraints
        constraintSet.constrainWidth(buttonContinue.id, dpToPx(240))
        constraintSet.constrainHeight(buttonContinue.id, dpToPx(56))
        constraintSet.connect(buttonContinue.id, androidx.constraintlayout.widget.ConstraintSet.TOP, 
                             textViewMessage.id, androidx.constraintlayout.widget.ConstraintSet.BOTTOM, dpToPx(48))
        constraintSet.connect(buttonContinue.id, androidx.constraintlayout.widget.ConstraintSet.START, 
                             androidx.constraintlayout.widget.ConstraintSet.PARENT_ID, androidx.constraintlayout.widget.ConstraintSet.START)
        constraintSet.connect(buttonContinue.id, androidx.constraintlayout.widget.ConstraintSet.END, 
                             androidx.constraintlayout.widget.ConstraintSet.PARENT_ID, androidx.constraintlayout.widget.ConstraintSet.END)
        
        // Botão voltar constraints
        constraintSet.constrainWidth(buttonBack.id, dpToPx(240))
        constraintSet.constrainHeight(buttonBack.id, dpToPx(56))
        constraintSet.connect(buttonBack.id, androidx.constraintlayout.widget.ConstraintSet.TOP, 
                             buttonContinue.id, androidx.constraintlayout.widget.ConstraintSet.BOTTOM, dpToPx(20))
        constraintSet.connect(buttonBack.id, androidx.constraintlayout.widget.ConstraintSet.START, 
                             androidx.constraintlayout.widget.ConstraintSet.PARENT_ID, androidx.constraintlayout.widget.ConstraintSet.START)
        constraintSet.connect(buttonBack.id, androidx.constraintlayout.widget.ConstraintSet.END, 
                             androidx.constraintlayout.widget.ConstraintSet.PARENT_ID, androidx.constraintlayout.widget.ConstraintSet.END)
        
        constraintSet.applyTo(layout)
        setContentView(layout)
    }
    
    private fun setupData() {
        // Recuperar dados do intent
        val usuario = intent.getSerializableExtra("usuario") as? FuncionariosLocalModel
        
        // Configurar nome do usuário
        if (usuario != null) {
            textViewUserName.text = usuario.nome
        } else {
            textViewUserName.text = "Usuário"
        }
        
        // Recuperar foto do TempImageStorage
        val faceBitmap = TempImageStorage.getFaceBitmap()
        if (faceBitmap != null) {
            // Redimensionar e tornar circular (orientação já corrigida no CameraActivity)
            val resizedBitmap = resizeBitmapToSquare(faceBitmap, 280)
            val circularBitmap = createCircularBitmap(resizedBitmap)
            imageViewPhoto.setImageBitmap(circularBitmap)
        }
    }
    
    private fun setupButtons() {
        buttonContinue.setOnClickListener {
            // Limpar imagem temporária
            TempImageStorage.clearFaceBitmap()
            
            // Voltar para a HomeActivity (tela principal)
            val intent = Intent(this, HomeActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
            finish()
        }
        
        buttonBack.setOnClickListener {
            // Limpar imagem temporária
            TempImageStorage.clearFaceBitmap()
            
            // Voltar para a tela anterior
            finish()
        }
    }
    
    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
    
    private fun correctImageOrientation(bitmap: Bitmap): Bitmap {
        // Para câmera frontal, normalmente precisamos rotacionar para ficar em modo retrato
        val matrix = android.graphics.Matrix()
        
        // Se a imagem estiver mais larga que alta, rotacionar para modo retrato
        if (bitmap.width > bitmap.height) {
            matrix.postRotate(90f)
            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }
        
        // Se já estiver em retrato, verificar se não está de cabeça para baixo
        // Para câmera frontal, pode precisar de 180 graus
        return bitmap
    }
    
    private fun resizeBitmapToSquare(bitmap: Bitmap, targetSize: Int): Bitmap {
        val targetSizePx = dpToPx(targetSize)
        
        // Primeiro, criar um bitmap quadrado a partir do centro da imagem original
        val size = minOf(bitmap.width, bitmap.height)
        val x = (bitmap.width - size) / 2
        val y = (bitmap.height - size) / 2
        
        val squaredBitmap = Bitmap.createBitmap(bitmap, x, y, size, size)
        
        // Depois redimensionar com boa qualidade
        return Bitmap.createScaledBitmap(squaredBitmap, targetSizePx, targetSizePx, true)
    }
    
    private fun createCircularBitmap(bitmap: Bitmap): Bitmap {
        val size = bitmap.width // Já deve ser quadrado após redimensionamento
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        
        val canvas = android.graphics.Canvas(output)
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            isFilterBitmap = true
            color = android.graphics.Color.WHITE
        }
        
        val rect = android.graphics.Rect(0, 0, size, size)
        val rectF = android.graphics.RectF(rect)
        
        // Desenhar círculo branco como máscara
        canvas.drawOval(rectF, paint)
        
        // Aplicar a imagem usando modo SRC_IN para criar efeito circular
        paint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(bitmap, rect, rect, paint)
        
        return output
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Garantir que a imagem seja limpa se a activity for destruída
        TempImageStorage.clearFaceBitmap()
    }
    
    companion object {
        fun start(context: android.content.Context, usuario: FuncionariosLocalModel?) {
            val intent = Intent(context, FaceRegistrationSuccessActivity::class.java)
            intent.putExtra("usuario", usuario)
            context.startActivity(intent)
        }
    }
} 