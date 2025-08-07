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
import com.example.iface_offilne.models.FuncionariosLocalModel
import com.example.iface_offilne.models.FuncionariosModel
import com.example.iface_offilne.util.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FuncionariosActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFuncionariosBinding
    private val listaFuncionarios = mutableListOf<FuncionariosModel>()
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
                .setTitle("Sincronizar Funcionário")
                .setMessage("Deseja sincronizar ${funcionariosModel.nome} ao sistema?")
                .setPositiveButton("Sim") { dialog, _ ->
                    val cpfLimpo = funcionariosModel.numero_cpf.replace(Regex("[^0-9]"), "")
                    
                    val funcionario = FuncionariosEntity(
                        id = funcionariosModel.id,
                        codigo = cpfLimpo,
                        nome = funcionariosModel.nome,
                        ativo = 1
                    )

                    lifecycleScope.launch(Dispatchers.IO) {
                        daoFunc.insert(funcionario)
                        Log.d("INSERT_FUNC", "Funcionário inserido: ${funcionario.nome}")
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

        loadFuncionarios()
    }

    private fun loadFuncionarios() {
        isLoading = true
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.getFuncionarios(entidadeId, currentPage)
                val funcionarios = response.data ?: emptyList()

                if (funcionarios.isNotEmpty()) {
                    listaFuncionarios.addAll(funcionarios)
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
}
