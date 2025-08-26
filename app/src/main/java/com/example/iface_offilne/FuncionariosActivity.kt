package com.example.iface_offilne

import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.iface_offilne.Adpater.FuncionariosAdapter
import com.example.iface_offilne.data.AppDatabase
import com.example.iface_offilne.data.FuncionariosEntity
import com.example.iface_offilne.data.api.RetrofitClient
import com.example.iface_offilne.data.dao.FuncionarioDao
import com.example.iface_offilne.databinding.ActivityFuncionariosBinding
import com.example.iface_offilne.helpers.PermissaoHelper
import com.example.iface_offilne.models.FuncionariosLocalModel
import com.example.iface_offilne.models.FuncionariosModel
import com.example.iface_offilne.util.ConfiguracoesManager
import com.example.iface_offilne.util.ErrorMessageHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FuncionariosActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFuncionariosBinding
    private val listaFuncionarios = mutableListOf<FuncionariosModel>()
    private val todosFuncionarios = mutableListOf<FuncionariosModel>()
    private lateinit var adapter: FuncionariosAdapter

    private var isLoading = false
    private var currentPage = 1
    private var entidadeId: String = ""
    private lateinit var model: FuncionariosModel
    private var searchJob: kotlinx.coroutines.Job? = null
    private var usarBuscaLocal = false // Flag para alternar entre busca backend/local


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFuncionariosBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // ✅ CARREGAMENTO ROBUSTO: Obter entidade ID das configurações
        lifecycleScope.launch {
            try {
                entidadeId = ConfiguracoesManager.getEntidadeId(this@FuncionariosActivity)
                Log.d("FuncionariosActivity", "🏢 Entidade carregada: '$entidadeId'")
            } catch (e: Exception) {
                Log.e("FuncionariosActivity", "❌ Erro ao carregar entidade: ${e.message}")
                entidadeId = ""
            }
        }
        
        var daoFunc: FuncionarioDao
        daoFunc = AppDatabase.getInstance(this).funcionarioDao()

        adapter = FuncionariosAdapter(listaFuncionarios) { funcionariosModel ->
            AlertDialog.Builder(this@FuncionariosActivity)
                .setTitle("Importar Funcionário")
                .setMessage("Deseja importar ${funcionariosModel.nome} ao sistema?")
                .setPositiveButton("Sim") { dialog, _ ->
                    val cpfLimpo = funcionariosModel.numero_cpf.replace(Regex("[^0-9]"), "")
                    
                    val funcionario = FuncionariosEntity(
                        id = funcionariosModel.id,
                        codigo = cpfLimpo,
                        nome = funcionariosModel.nome,
                        ativo = 1,
                        matricula = funcionariosModel.matricula,
                        cpf = cpfLimpo,
                        cargo = funcionariosModel.cargo_descricao,
                        secretaria = funcionariosModel.orgao_descricao ?: "N/A",
                        lotacao = funcionariosModel.setor_descricao ?: "N/A"
                    )

                    lifecycleScope.launch(Dispatchers.IO) {
                        daoFunc.insert(funcionario)
                        Log.d("INSERT_FUNC", "Funcionário importado: ${funcionario.nome}")
                        
                        // Atualizar conjunto de IDs importados e UI
                        val idsImportados = daoFunc.getAll().map { it.id }.toSet()
                        withContext(Dispatchers.Main) {
                            adapter.atualizarImportados(idsImportados)
                            Toast.makeText(this@FuncionariosActivity, "${funcionario.nome} foi importado!", Toast.LENGTH_SHORT).show()
                        }
                    }
                    dialog.dismiss()
                }
                .setNegativeButton("Não") { dialog, _ ->
                    dialog.dismiss()
                }
                .show()

        }
        binding.listaFuncionarios.layoutManager = LinearLayoutManager(this)
        binding.listaFuncionarios.adapter = adapter

        // Carregar IDs já importados no início
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val idsImportados = daoFunc.getAll().map { it.id }.toSet()
                withContext(Dispatchers.Main) {
                    adapter.atualizarImportados(idsImportados)
                }
            } catch (_: Exception) { }
        }

        // ✅ BOTÃO RECARREGAR: Agora funciona como "recarregar" já que o carregamento é automático
        binding.sincronizarListaFunc.setOnClickListener {
            Log.d("FuncionariosActivity", "🔄 Botão recarregar pressionado")
            
            // Mostrar feedback visual
            Toast.makeText(this, "🔄 Recarregando funcionários...", Toast.LENGTH_SHORT).show()
            
            // Resetar dados e carregar novamente
            currentPage = 1
            listaFuncionarios.clear()
            todosFuncionarios.clear()
            adapter.notifyDataSetChanged()
            
            // Ocultar lista durante recarregamento
            binding.listaFuncionarios.visibility = android.view.View.GONE
            
            // Carregar funcionários
            loadFuncionarios()
        }

        binding.listaFuncionarios.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                val totalItemCount = layoutManager.itemCount
                val lastVisibleItem = layoutManager.findLastVisibleItemPosition()

                if (!isLoading && lastVisibleItem + 3 >= totalItemCount) {
                    loadFuncionarios()
                }
            }
        })

        binding.arrowLeft.setOnClickListener {
            finish()
        }

        // Botão para atualizar funcionários existentes com dados de exemplo
        binding.sincronizarListaFunc.setOnLongClickListener {
            AlertDialog.Builder(this)
                .setTitle("Atualizar Dados")
                .setMessage("Deseja atualizar os funcionários existentes com dados de exemplo?")
                .setPositiveButton("Sim") { dialog, _ ->
                    atualizarFuncionariosExistentes()
                    dialog.dismiss()
                }
                .setNegativeButton("Não") { dialog, _ ->
                    dialog.dismiss()
                }
                .show()
            true
        }

        // Configurar busca
        binding.editTextBusca.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                filtrarFuncionarios(s.toString())
            }
        })
        
        // Long press no campo de busca para alternar modo de busca
        binding.editTextBusca.setOnLongClickListener {
            usarBuscaLocal = !usarBuscaLocal
            val modo = if (usarBuscaLocal) "LOCAL (Precisa)" else "BACKEND"
            Toast.makeText(this, "Modo de busca: $modo", Toast.LENGTH_SHORT).show()
            
            // Atualizar hint do campo para mostrar o modo
            val hintOriginal = "Buscar funcionário para importar..."
            val hintComModo = if (usarBuscaLocal) "$hintOriginal [LOCAL]" else "$hintOriginal [BACKEND]"
            binding.editTextBusca.hint = hintComModo
            
            Log.d("PESQUISA", "Modo de busca alterado para: $modo")
            true
        }
        
        // ✅ CARREGAMENTO AUTOMÁTICO: Carregar funcionários automaticamente ao entrar na tela
        Log.d("FuncionariosActivity", "🚀 Iniciando carregamento automático de funcionários")
        
        // Mostrar loading inicial
        binding.listaFuncionarios.visibility = android.view.View.GONE
        
        // ✅ NOVO: Carregar funcionários automaticamente
        lifecycleScope.launch {
            try {
                // Aguardar um pouco para garantir que a entidade foi carregada
                kotlinx.coroutines.delay(500)
                
                if (entidadeId.isNotEmpty()) {
                    Log.d("FuncionariosActivity", "✅ Entidade carregada: $entidadeId - iniciando carregamento")
                    loadFuncionarios()
                } else {
                    Log.w("FuncionariosActivity", "⚠️ Entidade não carregada ainda - aguardando...")
                    // Tentar novamente após 1 segundo
                    kotlinx.coroutines.delay(1000)
                    if (entidadeId.isNotEmpty()) {
                        Log.d("FuncionariosActivity", "✅ Entidade carregada na segunda tentativa - iniciando carregamento")
                        loadFuncionarios()
                    } else {
                        Log.e("FuncionariosActivity", "❌ Entidade não carregada após tentativas - mostrando lista vazia")
                        withContext(Dispatchers.Main) {
                            binding.listaFuncionarios.visibility = android.view.View.VISIBLE
                            Toast.makeText(this@FuncionariosActivity, 
                                "⚠️ Entidade não configurada. Configure a entidade nas configurações.", 
                                Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("FuncionariosActivity", "❌ Erro no carregamento automático: ${e.message}")
                withContext(Dispatchers.Main) {
                    binding.listaFuncionarios.visibility = android.view.View.VISIBLE
                    Toast.makeText(this@FuncionariosActivity, 
                        "❌ Erro ao carregar funcionários: ${e.message}", 
                        Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    // ✅ NOVO: Verificar permissão de home antes de voltar
    override fun onBackPressed() {
        Log.d("FuncionariosActivity", "🔙 Botão voltar pressionado - verificando permissão de home")
        
        // ✅ TESTE TEMPORÁRIO: Verificar se entidade está configurada
        if (entidadeId.isEmpty()) {
            Log.w("FuncionariosActivity", "⚠️ Entidade não configurada - voltando sem verificação")
            super.onBackPressed()
            return
        }
        
        val permissaoHelper = PermissaoHelper(this)
        permissaoHelper.verificarPermissao(
            menu = PermissaoHelper.MENU_HOME,
            onSuccess = {
                Log.d("FuncionariosActivity", "✅ Permissão de home concedida - voltando")
                try {
                    super.onBackPressed()
                } catch (e: Exception) {
                    Log.e("FuncionariosActivity", "❌ Erro ao chamar super.onBackPressed(): ${e.message}")
                    finish()
                }
            },
            onError = { mensagem ->
                Log.w("FuncionariosActivity", "❌ Permissão de home negada: $mensagem")
                // ✅ FALLBACK: Se API falhar, permitir voltar com aviso
                Toast.makeText(this, "⚠️ $mensagem - Voltando mesmo assim", Toast.LENGTH_LONG).show()
                try {
                    super.onBackPressed()
                } catch (e: Exception) {
                    finish()
                }
            }
        )
    }

    private fun loadFuncionarios() {
        isLoading = true
        lifecycleScope.launch {
            try {
                Log.d("FuncionariosActivity", "📡 Carregando funcionários - página $currentPage, entidade: $entidadeId")
                
                val response = RetrofitClient.instance.getFuncionarios(entidadeId, currentPage)
                val funcionarios = response.data ?: emptyList()

                Log.d("FuncionariosActivity", "✅ Funcionários carregados: ${funcionarios.size} na página $currentPage")

                if (funcionarios.isNotEmpty()) {
                    listaFuncionarios.addAll(funcionarios)
                    todosFuncionarios.addAll(funcionarios)
                    adapter.notifyItemRangeInserted(listaFuncionarios.size - funcionarios.size, funcionarios.size)
                    currentPage++
                    
                    // ✅ FEEDBACK: Mostrar quantidade total carregada
                    withContext(Dispatchers.Main) {
                        if (currentPage == 2) { // Primeira página carregada
                            Toast.makeText(this@FuncionariosActivity, 
                                "✅ ${listaFuncionarios.size} funcionários carregados", 
                                Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Log.d("FuncionariosActivity", "📭 Nenhum funcionário encontrado na página $currentPage")
                }
                
                // Mostrar lista após carregar
                withContext(Dispatchers.Main) {
                    binding.listaFuncionarios.visibility = android.view.View.VISIBLE
                }
                
            } catch (e: Exception) {
                Log.e("API_ERROR", "❌ Erro ao carregar página $currentPage: ${e.message}")
                withContext(Dispatchers.Main) {
                    binding.listaFuncionarios.visibility = android.view.View.VISIBLE
                    
                    // ✅ FEEDBACK: Mostrar erro padronizado para o usuário
                    if (currentPage == 1) { // Só mostrar erro na primeira página
                        ErrorMessageHelper.showErrorMessage(this@FuncionariosActivity, e)
                    }
                }
            } finally {
                isLoading = false
            }
        }
    }

    private fun filtrarFuncionarios(query: String) {
        // Cancelar busca anterior se ainda estiver em andamento
        searchJob?.cancel()
        
        val descricao = query.trim().uppercase()
        
        if (descricao.isEmpty()) {
            // Se não há busca, carregar todos os funcionários
            currentPage = 1
            listaFuncionarios.clear()
            todosFuncionarios.clear()
            adapter.notifyDataSetChanged()
            loadFuncionarios()
        } else {
            // Debounce: aguardar 500ms antes de fazer a busca
            searchJob = lifecycleScope.launch {
                kotlinx.coroutines.delay(500) // Aguardar 500ms
                
                if (usarBuscaLocal) {
                    // Busca local mais precisa
                    buscarLocalmente(descricao)
                } else {
                    // Busca no backend
                    buscarFuncionariosNoBackend(descricao)
                }
            }
        }
        
        val modo = if (usarBuscaLocal) "LOCAL" else "BACKEND"
        Log.d("PESQUISA", "Enviando descricao '$descricao' via $modo")
    }
    
    private fun buscarFuncionariosNoBackend(descricao: String) {
        isLoading = true
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.getFuncionarios(entidadeId, 1, descricao)
                val funcionarios = response.data ?: emptyList()
                
                // ✅ NOVO: Filtrar resultados no frontend para garantir precisão
                val funcionariosFiltrados = filtrarResultadosPrecisos(funcionarios, descricao)
                
                listaFuncionarios.clear()
                todosFuncionarios.clear()
                listaFuncionarios.addAll(funcionariosFiltrados)
                todosFuncionarios.addAll(funcionariosFiltrados)
                
                withContext(Dispatchers.Main) {
                    adapter.notifyDataSetChanged()
                    binding.listaFuncionarios.visibility = android.view.View.VISIBLE
                    
                    // Mostrar feedback sobre a filtragem
                    if (funcionarios.size != funcionariosFiltrados.size) {
                        Toast.makeText(this@FuncionariosActivity, 
                            "Backend retornou ${funcionarios.size} resultados, filtrados para ${funcionariosFiltrados.size} resultados precisos", 
                            Toast.LENGTH_SHORT).show()
                    }
                }
                
                Log.d("PESQUISA", "Backend retornou ${funcionarios.size} funcionários, filtrados para ${funcionariosFiltrados.size} para descricao: '$descricao'")
                
            } catch (e: Exception) {
                Log.e("PESQUISA_ERROR", "Erro ao buscar funcionários no backend: ${e.message}")
                withContext(Dispatchers.Main) {
                    ErrorMessageHelper.showErrorMessage(this@FuncionariosActivity, e)
                    binding.listaFuncionarios.visibility = android.view.View.VISIBLE
                }
            } finally {
                isLoading = false
            }
        }
    }
    
    /**
     * Busca localmente nos dados já carregados (mais precisa)
     */
    private fun buscarLocalmente(descricao: String) {
        lifecycleScope.launch {
            try {
                // Se não temos dados carregados, carregar primeiro
                if (todosFuncionarios.isEmpty()) {
                    loadFuncionarios()
                    return@launch
                }
                
                val funcionariosFiltrados = filtrarResultadosPrecisos(todosFuncionarios, descricao)
                
                withContext(Dispatchers.Main) {
                    listaFuncionarios.clear()
                    listaFuncionarios.addAll(funcionariosFiltrados)
                    adapter.notifyDataSetChanged()
                    binding.listaFuncionarios.visibility = android.view.View.VISIBLE
                    
                    Toast.makeText(this@FuncionariosActivity, 
                        "Busca LOCAL: ${funcionariosFiltrados.size} resultados para '$descricao'", 
                        Toast.LENGTH_SHORT).show()
                }
                
                Log.d("PESQUISA", "Busca LOCAL: ${funcionariosFiltrados.size} resultados para '$descricao'")
                
            } catch (e: Exception) {
                Log.e("PESQUISA_LOCAL_ERROR", "Erro na busca local: ${e.message}")
                withContext(Dispatchers.Main) {
                    ErrorMessageHelper.showErrorMessage(this@FuncionariosActivity, e)
                }
            }
        }
    }
    
    /**
     * Filtra os resultados do backend para garantir que realmente contenham o termo buscado
     */
    private fun filtrarResultadosPrecisos(funcionarios: List<FuncionariosModel>, descricao: String): List<FuncionariosModel> {
        if (descricao.isEmpty()) return funcionarios
        
        return funcionarios.filter { funcionario ->
            val nome = funcionario.nome.uppercase()
            val cargo = funcionario.cargo_descricao.uppercase()
            val orgao = funcionario.orgao_descricao?.uppercase() ?: ""
            val setor = funcionario.setor_descricao?.uppercase() ?: ""
            val matricula = funcionario.matricula.uppercase()
            val cpf = funcionario.numero_cpf.replace(Regex("[^0-9]"), "").uppercase()
            val cpfBusca = descricao.replace(Regex("[^0-9]"), "")
            
            // Verificar se algum campo contém a descrição buscada
            nome.contains(descricao) ||
            cargo.contains(descricao) ||
            orgao.contains(descricao) ||
            setor.contains(descricao) ||
            matricula.contains(descricao) ||
            (cpfBusca.isNotEmpty() && cpf.contains(cpfBusca))
        }
    }

    private fun atualizarFuncionariosExistentes() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val daoFunc = AppDatabase.getInstance(this@FuncionariosActivity).funcionarioDao()
                val funcionariosExistentes = daoFunc.getAll()
                
                val cargosExemplo = listOf(
                    "Analista Administrativo",
                    "Técnico em Informática", 
                    "Assistente Administrativo",
                    "Coordenador",
                    "Gerente",
                    "Diretor",
                    "Supervisor",
                    "Auxiliar Administrativo"
                )
                
                val secretariasExemplo = listOf(
                    "SEAD - Secretaria de Administração",
                    "SEMEF - Secretaria Municipal de Fazenda",
                    "SEMEC - Secretaria Municipal de Educação",
                    "SESAU - Secretaria Municipal de Saúde",
                    "SEMOB - Secretaria Municipal de Mobilidade",
                    "SEPLAN - Secretaria Municipal de Planejamento"
                )
                
                val lotacoesExemplo = listOf(
                    "Diretoria Geral",
                    "Departamento de Recursos Humanos",
                    "Departamento Financeiro",
                    "Departamento de TI",
                    "Coordenadoria de Gestão",
                    "Assessoria Técnica",
                    "Núcleo de Apoio"
                )
                
                funcionariosExistentes.forEachIndexed { index, funcionario ->
                    // Gerar matrícula se não existir
                    val matricula = if (funcionario.matricula.isEmpty()) {
                        String.format("%06d", 100000 + funcionario.id)
                    } else funcionario.matricula
                    
                    // Usar CPF existente ou gerar um exemplo
                    val cpf = if (funcionario.cpf.isEmpty()) {
                        funcionario.codigo.padStart(11, '0')
                    } else funcionario.cpf
                    
                    // Atribuir cargo, secretaria e lotação de forma cíclica
                    val cargo = if (funcionario.cargo.isEmpty()) {
                        cargosExemplo[index % cargosExemplo.size]
                    } else funcionario.cargo
                    
                    val secretaria = if (funcionario.secretaria.isEmpty()) {
                        secretariasExemplo[index % secretariasExemplo.size]
                    } else funcionario.secretaria
                    
                    val lotacao = if (funcionario.lotacao.isEmpty()) {
                        lotacoesExemplo[index % lotacoesExemplo.size]
                    } else funcionario.lotacao
                    
                    // Criar funcionário atualizado
                    val funcionarioAtualizado = funcionario.copy(
                        matricula = matricula,
                        cpf = cpf,
                        cargo = cargo,
                        secretaria = secretaria,
                        lotacao = lotacao
                    )
                    
                    // Atualizar no banco
                    daoFunc.update(funcionarioAtualizado)
                    Log.d("UPDATE_FUNC", "Funcionário atualizado: ${funcionarioAtualizado.nome}")
                }
                
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@FuncionariosActivity, 
                        "✅ ${funcionariosExistentes.size} funcionários atualizados com sucesso!", 
                        Toast.LENGTH_LONG).show()
                }
                
            } catch (e: Exception) {
                Log.e("UPDATE_ERROR", "Erro ao atualizar funcionários: ${e.message}")
                withContext(Dispatchers.Main) {
                    ErrorMessageHelper.showErrorMessage(this@FuncionariosActivity, e)
                }
            }
        }
    }
}

