package com.example.iface_offilne

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.iface_offilne.Adpater.UsuariosAdapter
import com.example.iface_offilne.data.AppDatabase
import com.example.iface_offilne.databinding.ActivityUsuariosBinding
import com.example.iface_offilne.models.FuncionariosLocalModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class UsuariosActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityUsuariosBinding

    private val listaUsuarios = mutableListOf<FuncionariosLocalModel>()
    private lateinit var adapter: UsuariosAdapter

    private var isLoading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityUsuariosBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = UsuariosAdapter(listaUsuarios) { usuario ->
            val screen  = Intent(this@UsuariosActivity, UsuarioEdit::class.java)
            screen.putExtra("usuario", usuario)
            startActivity(screen)
        }

        binding.listaUsuarios.layoutManager = LinearLayoutManager(this)
        binding.listaUsuarios.adapter = adapter

        binding.listaUsuarios.setOnClickListener {
            listaUsuarios.clear()
            adapter.notifyDataSetChanged()
        }

        loadUsuarios()

        binding.listaUsuarios.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                val totalItemCount = layoutManager.itemCount
                val lastVisibleItem = layoutManager.findLastVisibleItemPosition()

                if (!isLoading && lastVisibleItem + 3 >= totalItemCount) {
                    loadUsuarios()
                }
            }
        })
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_usuarios)
        return navController.navigateUp(appBarConfiguration)
                || super.onSupportNavigateUp()
    }

    private fun loadUsuarios() {
        isLoading = true

        val daoFunc = AppDatabase.getInstance(this).usuariosDao()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val users = daoFunc.getUsuario()

                Log.e("M", "$users")
                val mappedUsers = users.map { entity ->
                    FuncionariosLocalModel(
                        codigo = entity.id.toString(),
                        id = entity.id,
                        nome = entity.nome,
                        ativo = entity.ativo
                    )
                }

                listaUsuarios.clear()
                listaUsuarios.addAll(mappedUsers)

                launch(Dispatchers.Main) {
                    adapter.notifyDataSetChanged()
                }

            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoading = false
            }
        }
    }

}