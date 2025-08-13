package com.example.iface_offilne

import android.app.DatePickerDialog
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.iface_offilne.adapter.FuncionarioAutoCompleteAdapter
import com.example.iface_offilne.adapter.PontosAdapter
import com.example.iface_offilne.data.AppDatabase
import com.example.iface_offilne.data.FuncionariosEntity
import com.example.iface_offilne.data.PontosGenericosEntity
import com.example.iface_offilne.databinding.ActivityVisualizarPontosBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay

class VisualizarPontosActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVisualizarPontosBinding
    private lateinit var adapter: PontosAdapter
    private var todosPontos: List<PontosGenericosEntity> = emptyList()
    private var todosFuncionarios: List<FuncionariosEntity> = emptyList()
    private lateinit var bottomSheetDialog: BottomSheetDialog

    companion object {
        private const val TAG = "VisualizarPontosActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVisualizarPontosBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        carregarPontos()
    }

    private fun setupUI() {
        adapter = PontosAdapter()
        binding.recyclerViewPontos.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewPontos.adapter = adapter

        binding.arrowLeft.setOnClickListener {
            finish()
        }

        binding.btnAbrirFiltros.setOnClickListener {
            abrirModalFiltros()
        }

        binding.fabSync.setOnClickListener {
            sincronizarPontos()
        }
        
        binding.fabSync.setOnLongClickListener {
            Log.d("VisualizarPontos", "🧪 Criando pontos de teste...")
            lifecycleScope.launch {
                try {
                    val pontoService = com.example.iface_offilne.service.PontoSincronizacaoService()
                    
                    repeat(3) { i ->
                        pontoService.criarPontoTeste(this@VisualizarPontosActivity)
                        delay(100) 
                    }
                    
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@VisualizarPontosActivity, "✅ 3 pontos de teste criados!", Toast.LENGTH_LONG).show()
                        
                        delay(500)
                        carregarPontos()
                    }
                    
                } catch (e: Exception) {
                    Log.e("VisualizarPontos", "❌ Erro ao criar pontos de teste: ${e.message}")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@VisualizarPontosActivity, "❌ Erro ao criar pontos de teste", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            true
        }

        binding.fabExport.setOnClickListener {
            exportarPontos()
        }
    }

    private fun abrirModalFiltros() {
        bottomSheetDialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_filtros, null)
        bottomSheetDialog.setContentView(view)

        setupFiltrosModal(view)

        bottomSheetDialog.show()
    }

    private fun setupFiltrosModal(view: View) {
        // Configurar chips de período
        val chipGroupPeriodo = view.findViewById<com.google.android.material.chip.ChipGroup>(R.id.chipGroupPeriodo)
        chipGroupPeriodo.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.chipHoje -> filtrarPorPeriodo("hoje")
                R.id.chipSemana -> filtrarPorPeriodo("semana")
                R.id.chipMes -> filtrarPorPeriodo("mes")
                R.id.chipTodos -> filtrarPorPeriodo("todos")
            }
        }

        // Configurar campos de data
        val editTextDataInicio = view.findViewById<TextInputEditText>(R.id.editTextDataInicio)
        val editTextDataFim = view.findViewById<TextInputEditText>(R.id.editTextDataFim)

        editTextDataInicio.setOnClickListener {
            mostrarDatePicker(editTextDataInicio)
        }

        editTextDataFim.setOnClickListener {
            mostrarDatePicker(editTextDataFim)
        }

        // Configurar campo de funcionário com autocomplete
        val autoCompleteFuncionario = view.findViewById<android.widget.AutoCompleteTextView>(R.id.autoCompleteFuncionario)
        
        // Configurar adapter do autocomplete
        val funcionarioAdapter = FuncionarioAutoCompleteAdapter(this, todosFuncionarios)
        autoCompleteFuncionario.setAdapter(funcionarioAdapter)
        
        // Configurar listener para quando um funcionário for selecionado
        autoCompleteFuncionario.setOnItemClickListener { _, _, position, _ ->
            val funcionarioSelecionado = funcionarioAdapter.getItem(position)
            // O texto já será preenchido automaticamente pelo AutoCompleteTextView
        }

        // Configurar botões
        val btnFecharFiltros = view.findViewById<android.widget.ImageButton>(R.id.btnFecharFiltros)
        val btnLimparFiltros = view.findViewById<android.widget.Button>(R.id.btnLimparFiltros)
        val btnAplicarFiltros = view.findViewById<android.widget.Button>(R.id.btnAplicarFiltros)

        // Configurar backgroundTintList = null para os botões
        btnLimparFiltros.backgroundTintList = null
        btnAplicarFiltros.backgroundTintList = null

        btnFecharFiltros.setOnClickListener {
            bottomSheetDialog.dismiss()
        }

        btnLimparFiltros.setOnClickListener {
            limparFiltros(view)
        }

        btnAplicarFiltros.setOnClickListener {
            aplicarFiltrosPersonalizadosModal(view)
            bottomSheetDialog.dismiss()
        }
    }

    private fun limparFiltros(view: View) {
        val chipGroupPeriodo = view.findViewById<com.google.android.material.chip.ChipGroup>(R.id.chipGroupPeriodo)
        val editTextDataInicio = view.findViewById<TextInputEditText>(R.id.editTextDataInicio)
        val editTextDataFim = view.findViewById<TextInputEditText>(R.id.editTextDataFim)
        val autoCompleteFuncionario = view.findViewById<android.widget.AutoCompleteTextView>(R.id.autoCompleteFuncionario)

        chipGroupPeriodo.clearCheck()
        editTextDataInicio.setText("")
        editTextDataFim.setText("")
        autoCompleteFuncionario.setText("")

        // Mostrar todos os pontos
        atualizarLista(todosPontos)
    }

    private fun mostrarDatePicker(editText: TextInputEditText) {
        val calendar = Calendar.getInstance()
        val datePickerDialog = DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val data = String.format("%02d/%02d/%04d", dayOfMonth, month + 1, year)
                editText.setText(data)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        datePickerDialog.show()
    }

    private fun filtrarPorPeriodo(periodo: String) {
        val calendar = Calendar.getInstance()
        val hoje = calendar.timeInMillis

        val pontosFiltrados = when (periodo) {
            "hoje" -> {
                val inicioDia = calendar.apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                val fimDia = calendar.apply {
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }.timeInMillis
                todosPontos.filter { it.dataHora in inicioDia..fimDia }
            }
            "semana" -> {
                val inicioSemana = calendar.apply {
                    set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                todosPontos.filter { it.dataHora >= inicioSemana }
            }
            "mes" -> {
                val inicioMes = calendar.apply {
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                todosPontos.filter { it.dataHora >= inicioMes }
            }
            "todos" -> todosPontos
            else -> todosPontos
        }

        atualizarLista(pontosFiltrados)
    }

    private fun aplicarFiltrosPersonalizados() {
        aplicarFiltrosPersonalizadosModal(null)
    }

    private fun aplicarFiltrosPersonalizadosModal(view: View?) {
        val dataInicio: String
        val dataFim: String
        val funcionario: String

        if (view != null) {
            // Usando o modal
            val editTextDataInicio = view.findViewById<TextInputEditText>(R.id.editTextDataInicio)
            val editTextDataFim = view.findViewById<TextInputEditText>(R.id.editTextDataFim)
            val autoCompleteFuncionario = view.findViewById<android.widget.AutoCompleteTextView>(R.id.autoCompleteFuncionario)
            dataInicio = editTextDataInicio.text.toString()
            dataFim = editTextDataFim.text.toString()
            funcionario = autoCompleteFuncionario.text.toString().trim()
        } else {
            // Fallback - não há campos no layout principal
            dataInicio = ""
            dataFim = ""
            funcionario = ""
        }

        // Se não há nenhum filtro aplicado, mostrar todos
        if (dataInicio.isEmpty() && dataFim.isEmpty() && funcionario.isEmpty()) {
            atualizarLista(todosPontos)
            return
        }

        val formato = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val calendar = Calendar.getInstance()

        val pontosFiltrados = todosPontos.filter { ponto ->
            var passaFiltro = true

            // Filtro por funcionário
            if (funcionario.isNotEmpty()) {
                val nomeFuncionario = ponto.funcionarioNome.lowercase()
                val buscaFuncionario = funcionario.lowercase()
                if (!nomeFuncionario.contains(buscaFuncionario)) {
                    passaFiltro = false
                }
            }

            // Filtro por data
            if (passaFiltro && (dataInicio.isNotEmpty() || dataFim.isNotEmpty())) {
                val dataPonto = Date(ponto.dataHora)

                if (dataInicio.isNotEmpty()) {
                    val dataInicioObj = formato.parse(dataInicio)
                    if (dataPonto.before(dataInicioObj)) {
                        passaFiltro = false
                    }
                }

                if (dataFim.isNotEmpty()) {
                    val dataFimObj = formato.parse(dataFim)
                    calendar.time = dataFimObj!!
                    calendar.set(Calendar.HOUR_OF_DAY, 23)
                    calendar.set(Calendar.MINUTE, 59)
                    calendar.set(Calendar.SECOND, 59)
                    val dataFimAjustada = calendar.time
                    
                    if (dataPonto.after(dataFimAjustada)) {
                        passaFiltro = false
                    }
                }
            }

            passaFiltro
        }

        atualizarLista(pontosFiltrados)
    }

    private fun carregarPontos() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val database = AppDatabase.getInstance(this@VisualizarPontosActivity)
                val pontos = database.pontosGenericosDao().getAllPontos()
                val funcionarios = database.funcionarioDao().getAll()

                withContext(Dispatchers.Main) {
                    todosPontos = pontos
                    todosFuncionarios = funcionarios
                    
                    if (pontos.isNotEmpty()) {
                        binding.statusText.text = "📊 ${pontos.size} pontos encontrados"
                        binding.textQuantidade.text = "${pontos.size} pontos"
                        atualizarLista(pontos)
                        binding.layoutMensagemVazia.visibility = View.GONE
                        binding.recyclerViewPontos.visibility = View.VISIBLE
                    } else {
                        binding.statusText.text = "📭 Nenhum ponto registrado"
                        binding.textQuantidade.text = "0 pontos"
                        binding.layoutMensagemVazia.visibility = View.VISIBLE
                        binding.recyclerViewPontos.visibility = View.GONE
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Erro ao carregar pontos", e)
                withContext(Dispatchers.Main) {
                    binding.statusText.text = "❌ Erro ao carregar pontos"
                    Toast.makeText(this@VisualizarPontosActivity, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun atualizarLista(pontos: List<PontosGenericosEntity>) {
        adapter.setPontos(pontos)
        binding.textQuantidade.text = "${pontos.size} pontos"
        
        if (pontos.isEmpty()) {
            binding.layoutMensagemVazia.visibility = View.VISIBLE
            binding.recyclerViewPontos.visibility = View.GONE
        } else {
            binding.layoutMensagemVazia.visibility = View.GONE
            binding.recyclerViewPontos.visibility = View.VISIBLE
        }
    }

    private fun sincronizarPontos() {
        Toast.makeText(this, "🔄 Iniciando sincronização...", Toast.LENGTH_SHORT).show()
        Log.d("VisualizarPontos", "🔄 Botão sincronizar pressionado na tela de pontos")
        
        lifecycleScope.launch {
            try {
                // ✅ MELHOR FEEDBACK: Mostrar quantidade de pontos antes da sincronização
                val pontoService = com.example.iface_offilne.service.PontoSincronizacaoService()
                val pontosPendentes = pontoService.getQuantidadePontosPendentes(this@VisualizarPontosActivity)
                
                withContext(Dispatchers.Main) {
                    if (pontosPendentes == 0) {
                        Toast.makeText(this@VisualizarPontosActivity, "ℹ️ Não há pontos para sincronizar", Toast.LENGTH_LONG).show()
                        return@withContext
                    } else {
                        Toast.makeText(this@VisualizarPontosActivity, "📊 Sincronizando $pontosPendentes pontos...", Toast.LENGTH_LONG).show()
                    }
                }
                
                // Usar o serviço de sincronização
                val sincronizacaoService = com.example.iface_offilne.service.SincronizacaoService()
                
                // Verificar status primeiro
                sincronizacaoService.verificarStatusSincronizacao(this@VisualizarPontosActivity)
                
                // ✅ EXECUTAR SINCRONIZAÇÃO REAL
                Log.d("VisualizarPontos", "🚀 Executando sincronização de $pontosPendentes pontos...")
                val resultado = pontoService.sincronizarPontosPendentes(this@VisualizarPontosActivity)
                
                withContext(Dispatchers.Main) {
                    if (resultado.sucesso) {
                        Toast.makeText(
                            this@VisualizarPontosActivity, 
                            "✅ ${resultado.quantidadePontos} pontos sincronizados com sucesso!", 
                            Toast.LENGTH_LONG
                        ).show()
                        
                        // Aguardar um pouco e recarregar a lista
                        delay(1000)
                        Toast.makeText(this@VisualizarPontosActivity, "🔄 Atualizando lista...", Toast.LENGTH_SHORT).show()
                        carregarPontos() // Recarregar a lista após sincronização
                        
                    } else {
                        Toast.makeText(
                            this@VisualizarPontosActivity, 
                            "❌ Erro na sincronização: ${resultado.mensagem}", 
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
                
            } catch (e: Exception) {
                Log.e("VisualizarPontos", "❌ Erro na sincronização: ${e.message}")
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@VisualizarPontosActivity, 
                        "❌ Erro na sincronização: ${e.message}", 
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun exportarPontos() {
        Toast.makeText(this, "Exportando pontos...", Toast.LENGTH_SHORT).show()
        // Implementar lógica de exportação
    }
} 