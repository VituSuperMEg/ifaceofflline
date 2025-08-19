package com.example.iface_offilne

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
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
import com.example.iface_offilne.service.SincronizacaoService
import com.example.iface_offilne.util.ConfiguracoesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class EntidadeActivity: AppCompatActivity() {

    private lateinit var binding: ActivityEntidadeBinding
    private var countyName: Array<String> = arrayOf("Selecione o Estado", "Acre", "Alagoas", "Amapá", "Amazonas", "Bahia", "Ceará")
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityEntidadeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // ✅ NOVO: Inicializar SharedPreferences
        sharedPreferences = getSharedPreferences("LoginPrefs", MODE_PRIVATE)

        // ✅ NOVO: Verificar se usuário já está logado
        checkSavedLoginAndNavigate()

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

    /**
     * ✅ NOVO: Verifica se há dados de login salvos e navega direto para Home
     */
    private fun checkSavedLoginAndNavigate() {
        val manterLogado = sharedPreferences.getBoolean("manter_logado", false)
        val savedCpf = sharedPreferences.getString("saved_cpf", "")
        val savedSenha = sharedPreferences.getString("saved_senha", "")

        if (manterLogado && !savedCpf.isNullOrEmpty() && !savedSenha.isNullOrEmpty()) {
            Log.d("EntidadeActivity", "🔄 Usuário já logado - verificando entidade...")
            
            // ✅ NOVO: Usar método do SessionManager para restaurar entidade
            val entidadeRestaurada = com.example.iface_offilne.util.SessionManager.restoreEntidadeFromPreferences(this)
            
            if (entidadeRestaurada) {
                Log.d("EntidadeActivity", "✅ Entidade restaurada com sucesso")
                
                // ✅ NOVO: Configurar alarme de sincronização antes de navegar
                configurarAlarmeSincronizacaoInicial()
                
                // ✅ NOVO: Mostrar confirmação
                val entidadeName = com.example.iface_offilne.util.SessionManager.getEntidadeName()
                Toast.makeText(this, "✅ Entidade restaurada: $entidadeName", Toast.LENGTH_SHORT).show()
                
                // Navegar direto para Home
                val intent = Intent(this, com.example.iface_offilne.HomeActivity::class.java)
                startActivity(intent)
                finish() // Fechar esta activity para não voltar
            } else {
                Log.d("EntidadeActivity", "⚠️ Usuário logado mas entidade não configurada - continuando fluxo normal")
                Toast.makeText(this, "⚠️ Entidade não configurada - configure uma entidade", Toast.LENGTH_SHORT).show()
            }
        } else {
            Log.d("EntidadeActivity", "ℹ️ Usuário não logado - fluxo normal de configuração")
        }
    }
    
    /**
     * ✅ NOVO: Configura o alarme de sincronização na inicialização do app
     * Executado quando o usuário já está logado e vai direto para a Home
     */
    private fun configurarAlarmeSincronizacaoInicial() {
        Log.d("EntidadeActivity", "🔧 === CONFIGURANDO ALARME DE SINCRONIZAÇÃO INICIAL ===")
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Verificar se a sincronização está ativa
                val sincronizacaoAtiva = ConfiguracoesManager.isSincronizacaoAtiva(this@EntidadeActivity)
                val intervalo = ConfiguracoesManager.getIntervaloSincronizacao(this@EntidadeActivity)
                
                Log.d("EntidadeActivity", "🔍 Status da sincronização: ativa=$sincronizacaoAtiva, intervalo=${intervalo}h")
                
                if (sincronizacaoAtiva) {
                    Log.d("EntidadeActivity", "✅ Sincronização ativa - configurando alarme na inicialização")
                    
                    // Configurar alarme
                    val sincronizacaoService = SincronizacaoService()
                    sincronizacaoService.configurarAlarme(this@EntidadeActivity, 0, 0, intervalo)
                    
                    // Verificar status do alarme
                    sincronizacaoService.verificarStatusAlarme(this@EntidadeActivity)
                    
                    Log.d("EntidadeActivity", "✅ Alarme de sincronização configurado na inicialização para $intervalo hora(s)")
                } else {
                    Log.d("EntidadeActivity", "ℹ️ Sincronização desativada - alarme não configurado na inicialização")
                }
                
            } catch (e: Exception) {
                Log.e("EntidadeActivity", "❌ Erro ao configurar alarme inicial: ${e.message}")
                e.printStackTrace()
            }
        }
    }
}