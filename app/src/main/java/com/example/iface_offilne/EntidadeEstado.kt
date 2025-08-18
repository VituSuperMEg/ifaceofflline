package com.example.iface_offilne

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.iface_offilne.cities.CodigoIBge
import com.example.iface_offilne.data.Entidade
import com.example.iface_offilne.data.api.RetrofitClient
import com.example.iface_offilne.data.request.EntidadeRequest
import com.example.iface_offilne.databinding.ActivityEntidadeEstadoBinding
import com.example.iface_offilne.util.SessionManager
import kotlinx.coroutines.launch

class EntidadeEstado : AppCompatActivity() {

    private lateinit var binding: ActivityEntidadeEstadoBinding
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityEntidadeEstadoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // ✅ NOVO: Inicializar SharedPreferences
        sharedPreferences = getSharedPreferences("LoginPrefs", MODE_PRIVATE)

        val estado = intent.getStringExtra("estado")
        SessionManager.estadoBr = estado.toString()
        val cidade = intent.getStringExtra("cidade")
        SessionManager.municipio = cidade.toString()
        val codigoIbgeCidade = CodigoIBge(cidade) ?: "2305506"

        val spinnerEntidade = findViewById<Spinner>(R.id.spinnerEntidade)

        lifecycleScope.launch {
            try {
                val request = EntidadeRequest(municipio_id = codigoIbgeCidade)
                val response = RetrofitClient.instance.getEntidade(request)

                val entidades = response.data

                val adapter = object : ArrayAdapter<Entidade>(
                    this@EntidadeEstado,
                    android.R.layout.simple_spinner_dropdown_item,
                    entidades
                ) {
                    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                        val view = super.getView(position, convertView, parent)
                        (view as TextView).text = entidades[position].name
                        return view
                    }

                    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                        val view = super.getDropDownView(position, convertView, parent)
                        (view as TextView).text = entidades[position].name
                        return view
                    }
                }

                spinnerEntidade.adapter = adapter

            } catch (e: Exception) {
                Log.e("API_ERROR", "Erro: ${e.message}")
            }
        }

        spinnerEntidade.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val entidade = parent.getItemAtPosition(position) as Entidade
                SessionManager.entidade = entidade
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        binding.btnEntidadeEstadoConfirmar.backgroundTintList = null

        binding.btnEntidadeEstadoConfirmar.setOnClickListener {
            // ✅ NOVO: Salvar dados da entidade no SharedPreferences
            val entidade = SessionManager.entidade
            if (entidade != null) {
                val editor = sharedPreferences.edit()
                editor.putString("saved_entidade_id", entidade.id)
                editor.putString("saved_entidade_name", entidade.name)
                editor.putString("saved_estado", SessionManager.estadoBr)
                editor.putString("saved_municipio", SessionManager.municipio)
                editor.apply()
                
                Log.d("EntidadeEstado", "💾 Dados da entidade salvos: ${entidade.name}")
            }
            
            val intent = Intent(this, Login::class.java)
            startActivity(intent)
        }
    }
}