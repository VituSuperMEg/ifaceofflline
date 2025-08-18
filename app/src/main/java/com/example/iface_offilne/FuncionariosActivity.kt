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
import com.example.iface_offilne.util.SessionManager
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


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFuncionariosBinding.inflate(layoutInflater)
        setContentView(binding.root)

        entidadeId = SessionManager.entidade?.id.toString()
        
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

        binding.sincronizarListaFunc.setOnClickListener {
            currentPage = 1
            listaFuncionarios.clear()
            adapter.notifyDataSetChanged()
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

        loadFuncionarios()
    }
    
    // ✅ NOVO: Verificar permissão de home antes de voltar
    override fun onBackPressed() {
        Log.d("FuncionariosActivity", "🔙 Botão voltar pressionado - verificando permissão de home")
        
        // ✅ TESTE TEMPORÁRIO: Verificar se entidade está configurada
        val entidade = SessionManager.entidade?.id
        if (entidade.isNullOrEmpty()) {
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
                val response = RetrofitClient.instance.getFuncionarios(entidadeId, currentPage)
                val funcionarios = response.data ?: emptyList()

                if (funcionarios.isNotEmpty()) {
                    listaFuncionarios.addAll(funcionarios)
                    todosFuncionarios.addAll(funcionarios)
                    adapter.notifyItemRangeInserted(listaFuncionarios.size - funcionarios.size, funcionarios.size)
                    currentPage++
                }
            } catch (e: Exception) {
                Log.e("API_ERROR", "Erro ao carregar página $currentPage: ${e.message}")
            } finally {
                isLoading = false
            }
        }
    }

    private fun filtrarFuncionarios(query: String) {
        val queryLower = query.lowercase()
        val funcionariosFiltrados = if (query.isEmpty()) {
            todosFuncionarios
        } else {
            todosFuncionarios.filter { funcionario ->
                funcionario.nome.lowercase().contains(queryLower)
            }
        }
        
        listaFuncionarios.clear()
        listaFuncionarios.addAll(funcionariosFiltrados)
        adapter.notifyDataSetChanged()
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
                    Toast.makeText(this@FuncionariosActivity, 
                        "❌ Erro ao atualizar funcionários: ${e.message}", 
                        Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
