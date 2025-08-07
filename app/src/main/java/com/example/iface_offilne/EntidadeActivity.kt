package com.example.iface_offilne

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.get
import com.example.iface_offilne.cities.Ceara
import com.example.iface_offilne.databinding.ActivityEntidadeBinding
import com.example.iface_offilne.databinding.ActivityMainBinding
import android.widget.Button

class EntidadeActivity: AppCompatActivity() {

    private lateinit var binding: ActivityEntidadeBinding
    private var countyName: Array<String> = arrayOf("Selecione o Estado", "Acre", "Alagoas", "Amapá", "Amazonas", "Bahia", "Ceará")


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityEntidadeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val spinner = findViewById<Spinner>(R.id.spinner_estado)
        val spinnerCidades = findViewById<Spinner>(R.id.spinner_cidade)

        val countyAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, countyName)
        countyAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

        spinner.adapter = countyAdapter

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val selectedItem = parent.getItemAtPosition(position).toString()
                Toast.makeText(this@EntidadeActivity, "Selecionado: $selectedItem", Toast.LENGTH_SHORT).show()

                val cidades = when (selectedItem) {
                    "Ceará" -> Ceara.cidades
                    else -> listOf("Nenhma Cidade")
                }

                // Atualiza o Spinner de cidades
                val adapterCidades = ArrayAdapter(this@EntidadeActivity, android.R.layout.simple_spinner_dropdown_item, cidades)
                spinnerCidades.adapter = adapterCidades
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // Caso nada seja selecionado (pouco comum em Spinner)
            }
        }

        spinnerCidades.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val cidadeItem = parent.getItemAtPosition(position).toString()

                Toast.makeText(this@EntidadeActivity, "Selecionado: $cidadeItem", Toast.LENGTH_SHORT).show()
            }

            override fun onNothingSelected(p0: AdapterView<*>?) {
            }
        }

        /** Botão de Confirmar **/
        val btnConfirmar = findViewById<Button>(R.id.btnConfirmar)
        btnConfirmar.backgroundTintList = null

        binding.btnConfirmar.setOnClickListener {
            val estadoSelect = spinner.selectedItem.toString()
            val cidadeSelect = spinnerCidades.selectedItem.toString()

            val intent = Intent(this, EntidadeEstado::class.java)
            intent.putExtra("estado", estadoSelect)
            intent.putExtra("cidade", cidadeSelect)

            startActivity(intent)
        }
    }
}