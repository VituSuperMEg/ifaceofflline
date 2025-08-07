package com.example.iface_offilne

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.iface_offilne.data.AppDatabase
import com.example.iface_offilne.data.PontosGenericosEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class VisualizarPontosActivity : AppCompatActivity() {

    private lateinit var scrollView: ScrollView
    private lateinit var pontosContainer: LinearLayout
    private lateinit var statusText: TextView

    companion object {
        private const val TAG = "VisualizarPontosActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setupUI()
        carregarPontos()
    }

    private fun setupUI() {
        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#F5F5F5"))
        }

        // Header
        val header = TextView(this).apply {
            text = "📋 PONTOS REGISTRADOS"
            textSize = 24f
            gravity = android.view.Gravity.CENTER
            setTextColor(Color.parseColor("#333333"))
            setPadding(16, 32, 16, 16)
            setBackgroundColor(Color.parseColor("#FFFFFF"))
        }

        // Status
        statusText = TextView(this).apply {
            text = "🔄 Carregando pontos..."
            textSize = 16f
            gravity = android.view.Gravity.CENTER
            setTextColor(Color.parseColor("#666666"))
            setPadding(16, 16, 16, 16)
        }

        // Container dos pontos
        pontosContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0, 1f // weight = 1 para ocupar espaço disponível
            )
            addView(pontosContainer)
        }

        // Botão voltar
        val btnVoltar = Button(this).apply {
            text = "⬅️ VOLTAR"
            textSize = 14f
            setBackgroundColor(Color.parseColor("#757575"))
            setTextColor(Color.WHITE)
            setPadding(16, 12, 16, 12)
            setOnClickListener { finish() }
        }

        // Montar layout principal
        mainLayout.addView(header)
        mainLayout.addView(statusText)
        mainLayout.addView(scrollView)
        mainLayout.addView(btnVoltar)

        setContentView(mainLayout)
    }

    private fun carregarPontos() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val pontos = AppDatabase.getInstance(this@VisualizarPontosActivity)
                    .pontosGenericosDao()
                    .getAllPontos()

                withContext(Dispatchers.Main) {
                    if (pontos.isNotEmpty()) {
                        statusText.text = "📊 ${pontos.size} pontos encontrados"
                        exibirPontos(pontos)
                    } else {
                        statusText.text = "📭 Nenhum ponto registrado"
                        mostrarMensagemVazia()
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Erro ao carregar pontos", e)
                withContext(Dispatchers.Main) {
                    statusText.text = "❌ Erro ao carregar pontos"
                    mostrarErro(e.message ?: "Erro desconhecido")
                }
            }
        }
    }

    private fun exibirPontos(pontos: List<PontosGenericosEntity>) {
        pontosContainer.removeAllViews()

        val formato = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val formatoHora = SimpleDateFormat("HH:mm", Locale.getDefault())
        
        // Agrupar por data
        val pontosPorData = pontos.groupBy { ponto ->
            formato.format(Date(ponto.dataHora))
        }

        for ((data, pontosDoDia) in pontosPorData) {
            // Header da data
            val headerData = TextView(this).apply {
                text = "📅 $data"
                textSize = 18f
                setTextColor(Color.parseColor("#333333"))
                setPadding(16, 24, 16, 8)
                setBackgroundColor(Color.parseColor("#E3F2FD"))
            }
            pontosContainer.addView(headerData)

            // Pontos do dia
            for (ponto in pontosDoDia) {
                val pontoView = criarViewPonto(ponto, formatoHora)
                pontosContainer.addView(pontoView)
            }

            // Espaçamento entre dias
            val espacamento = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dpToPx(8)
                )
            }
            pontosContainer.addView(espacamento)
        }
    }

    private fun criarViewPonto(ponto: PontosGenericosEntity, formatoHora: SimpleDateFormat): View {
        val pontoLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(16, 12, 16, 12)
            setBackgroundColor(Color.parseColor("#FFFFFF"))
        }

        // Ícone do tipo de ponto
        val icone = TextView(this).apply {
            text = if (ponto.tipoPonto == "ENTRADA") "🟢" else "🔴"
            textSize = 20f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Informações do ponto
        val infoLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            )
            setPadding(16, 0, 0, 0)
        }

        val nomeHora = TextView(this).apply {
            text = "${ponto.funcionarioNome} - ${formatoHora.format(Date(ponto.dataHora))}"
            textSize = 16f
            setTextColor(Color.parseColor("#333333"))
        }

        val tipoPonto = TextView(this).apply {
            text = ponto.tipoPonto
            textSize = 14f
            setTextColor(
                if (ponto.tipoPonto == "ENTRADA") 
                    Color.parseColor("#4CAF50") 
                else 
                    Color.parseColor("#F44336")
            )
        }

        infoLayout.addView(nomeHora)
        infoLayout.addView(tipoPonto)

        // Status de sincronização
        val statusSync = TextView(this).apply {
            text = if (ponto.synced) "✅" else "⏳"
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        pontoLayout.addView(icone)
        pontoLayout.addView(infoLayout)
        pontoLayout.addView(statusSync)

        // Linha separadora
        val separador = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                1
            )
            setBackgroundColor(Color.parseColor("#E0E0E0"))
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        container.addView(pontoLayout)
        container.addView(separador)

        return container
    }

    private fun mostrarMensagemVazia() {
        pontosContainer.removeAllViews()

        val mensagem = TextView(this).apply {
            text = "📭\n\nNenhum ponto foi registrado ainda.\n\nUse o sistema de reconhecimento facial para registrar pontos."
            textSize = 16f
            gravity = android.view.Gravity.CENTER
            setTextColor(Color.parseColor("#666666"))
            setPadding(32, 64, 32, 64)
        }

        pontosContainer.addView(mensagem)
    }

    private fun mostrarErro(mensagem: String) {
        pontosContainer.removeAllViews()

        val erro = TextView(this).apply {
            text = "❌\n\nErro ao carregar pontos:\n$mensagem"
            textSize = 16f
            gravity = android.view.Gravity.CENTER
            setTextColor(Color.parseColor("#F44336"))
            setPadding(32, 64, 32, 64)
        }

        pontosContainer.addView(erro)
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
} 