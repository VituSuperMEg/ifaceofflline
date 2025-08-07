package com.example.iface_offilne

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton
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

        // Header com estilo da tela de usuários
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(48)
            ).apply {
                setMargins(dpToPx(16), dpToPx(14), dpToPx(16), 0)
            }
            setBackgroundResource(R.drawable.roundend)
            elevation = 5f
            setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14))
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        // Botão voltar com ícone
        val btnVoltar = ImageButton(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setImageResource(R.drawable.arrowleft)
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener { finish() }
        }

        // Título do header - não centralizado, ao lado da seta
        val tituloHeader = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            )
            text = "Pontos Registrados"
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.parseColor("#333333"))
            gravity = android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL
            setPadding(dpToPx(16), 0, 0, 0)
        }

        // Adicionar views ao header
        header.addView(btnVoltar)
        header.addView(tituloHeader)

        // Status
        statusText = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
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
                0, 1f
            )
            addView(pontosContainer)
        }

        // Adicionar views ao layout principal
        mainLayout.addView(header)
        mainLayout.addView(statusText)
        mainLayout.addView(scrollView)

        // FloatingActionButton para sincronização
        val fabSync = FloatingActionButton(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.BOTTOM or android.view.Gravity.END
                setMargins(0, 0, dpToPx(16), dpToPx(16))
            }
            setImageResource(android.R.drawable.ic_menu_upload)
            setOnClickListener {
                sincronizarPontos()
            }
        }

        // Container principal com FAB
        val containerPrincipal = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }

        containerPrincipal.addView(mainLayout)
        containerPrincipal.addView(fabSync)

        setContentView(containerPrincipal)
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
        val formatoHora = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        
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
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(16, 12, 16, 12)
            setBackgroundColor(Color.parseColor("#FFFFFF"))
        }

        val nomeHora = TextView(this).apply {
            text = "${ponto.funcionarioNome} - ${formatoHora.format(Date(ponto.dataHora))}"
            textSize = 16f
            setTextColor(Color.parseColor("#333333"))
            setPadding(0, 0, 0, 4)
        }

        val dataCompleta = TextView(this).apply {
            val formatoCompleto = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
            text = "Data: ${formatoCompleto.format(Date(ponto.dataHora))}"
            textSize = 12f
            setTextColor(Color.parseColor("#666666"))
        }

        pontoLayout.addView(nomeHora)
        pontoLayout.addView(dataCompleta)

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

    private fun sincronizarPontos() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Buscar pontos não sincronizados
                val pontosNaoSincronizados = AppDatabase.getInstance(this@VisualizarPontosActivity)
                    .pontosGenericosDao()
                    .getPendingSync()

                withContext(Dispatchers.Main) {
                    if (pontosNaoSincronizados.isNotEmpty()) {
                        statusText.text = "🔄 Sincronizando ${pontosNaoSincronizados.size} pontos..."
                        // Aqui você implementaria a lógica de sincronização com o servidor
                        // Por enquanto, apenas simular
                        Thread.sleep(2000)
                        statusText.text = "✅ ${pontosNaoSincronizados.size} pontos sincronizados!"
                    } else {
                        statusText.text = "✅ Todos os pontos já estão sincronizados!"
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Erro ao sincronizar pontos", e)
                withContext(Dispatchers.Main) {
                    statusText.text = "❌ Erro na sincronização"
                }
            }
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
} 