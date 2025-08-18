package com.example.iface_offilne

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.example.iface_offilne.adapter.ConfiguracoesPagerAdapter
import com.example.iface_offilne.databinding.ActivityConfiguracoesBinding
import com.example.iface_offilne.service.SincronizacaoService
import com.example.iface_offilne.ui.ConfiguracoesTabFragment
import com.example.iface_offilne.ui.HistoricoTabFragment
import com.example.iface_offilne.ui.SobreTabFragment
import com.example.iface_offilne.util.ConfiguracoesManager
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class ConfiguracoesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityConfiguracoesBinding
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var pagerAdapter: ConfiguracoesPagerAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityConfiguracoesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Inicializar SharedPreferences
        sharedPreferences = getSharedPreferences("ConfiguracoesPrefs", MODE_PRIVATE)

        setupViewPager()
        setupUI()
        setupListeners()
        
        // Carregar configurações após um delay para garantir que os fragments estejam prontos
        binding.viewPager.post {
            carregarConfiguracoes()
        }
    }

    private fun setupViewPager() {
        pagerAdapter = ConfiguracoesPagerAdapter(this)
        binding.viewPager.adapter = pagerAdapter

        // Configurar TabLayout com ViewPager2
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Configurações"
                1 -> "Histórico"
                2 -> "Sobre"
                else -> ""
            }
        }.attach()

        // Configurar orientação do ViewPager2
        binding.viewPager.orientation = ViewPager2.ORIENTATION_HORIZONTAL
        
        // Configurar listener para mostrar/ocultar botões conforme a aba
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                when (position) {
                    0 -> { // Aba Configurações
                        binding.bottomButtons.visibility = android.view.View.VISIBLE
                    }
                    1 -> { // Aba Histórico
                        binding.bottomButtons.visibility = android.view.View.GONE
                    }
                    2 -> { // Aba Sobre
                        binding.bottomButtons.visibility = android.view.View.GONE
                    }
                }
            }
        })
    }

    private fun setupUI() {
        // Configurar botões para usar os selectors corretamente
        binding.btnSalvar.backgroundTintList = null
        binding.btnCancelar.backgroundTintList = null
        binding.btnSair.backgroundTintList = null

        // Inicialmente mostrar os botões (aba Configurações é a primeira)
        binding.bottomButtons.visibility = android.view.View.VISIBLE

        // Configurar botão de voltar
        binding.arrowLeft.setOnClickListener {
            finish()
        }
    }

    private fun carregarConfiguracoes() {
        // Carregar valores salvos usando ConfiguracoesManager
        lifecycleScope.launch {
            try {
                val localizacaoId = ConfiguracoesManager.getLocalizacaoId(this@ConfiguracoesActivity)
                val codigoSincronizacao = ConfiguracoesManager.getCodigoSincronizacao(this@ConfiguracoesActivity)
                val sincronizacaoAtiva = ConfiguracoesManager.isSincronizacaoAtiva(this@ConfiguracoesActivity)
                val intervalo = ConfiguracoesManager.getIntervaloSincronizacao(this@ConfiguracoesActivity)
                
                // Aguardar um pouco mais para garantir que os fragments estejam completamente criados
                binding.viewPager.postDelayed({
                    val configFragment = getCurrentConfiguracoesFragment()
                    configFragment?.let {
                        it.setLocalizacaoId(localizacaoId)
                        it.setCodigoSincronizacao(codigoSincronizacao)
                        it.setIntervalo(intervalo)
                        it.setSincronizacaoAtiva(sincronizacaoAtiva)
                        
                        // Configurar callbacks
                        it.onSincronizarClick = {
                            executarSincronizacaoManual()
                        }
                    }
                    
                    val historicoFragment = getCurrentHistoricoFragment()
                    historicoFragment?.onSincronizarClick = {
                        executarSincronizacaoManual()
                    }
                    
                    val sobreFragment = getCurrentSobreFragment()
                    sobreFragment?.onUpdateCheckClick = {
                        // Aqui você pode adicionar lógica adicional quando verificar atualizações
                        Toast.makeText(this@ConfiguracoesActivity, "Verificação de atualização iniciada", Toast.LENGTH_SHORT).show()
                    }
                    sobreFragment?.onUpdateClick = {
                        // Aqui você pode adicionar lógica adicional quando atualizar
                        Toast.makeText(this@ConfiguracoesActivity, "Atualização iniciada", Toast.LENGTH_SHORT).show()
                    }
                }, 500)
                
            } catch (e: Exception) {
                Toast.makeText(this@ConfiguracoesActivity, "Erro ao carregar configurações: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun getCurrentConfiguracoesFragment(): ConfiguracoesTabFragment? {
        return try {
            supportFragmentManager.findFragmentByTag("f0") as? ConfiguracoesTabFragment
        } catch (e: Exception) {
            null
        }
    }

    private fun getCurrentHistoricoFragment(): HistoricoTabFragment? {
        return try {
            supportFragmentManager.findFragmentByTag("f1") as? HistoricoTabFragment
        } catch (e: Exception) {
            null
        }
    }

    private fun getCurrentSobreFragment(): SobreTabFragment? {
        return try {
            supportFragmentManager.findFragmentByTag("f2") as? SobreTabFragment
        } catch (e: Exception) {
            null
        }
    }

    private fun setupListeners() {
        binding.btnSalvar.setOnClickListener {
            salvarConfiguracoes()
        }

        binding.btnCancelar.setOnClickListener {
            finish()
        }

        binding.btnSair.setOnClickListener {
            // Limpar dados de sessão e voltar para login
            val sharedPreferences = getSharedPreferences("LoginPrefs", MODE_PRIVATE)
            sharedPreferences.edit().clear().apply()
            
            // Voltar para a tela de login
            val intent = Intent(this, Login::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    private fun salvarConfiguracoes() {
        try {
            val configFragment = getCurrentConfiguracoesFragment()
            
            if (configFragment == null) {
                Toast.makeText(this, "Erro: Fragment de configurações não encontrado", Toast.LENGTH_SHORT).show()
                return
            }

            val localizacaoId = configFragment.getLocalizacaoId()
            val codigoSincronizacao = configFragment.getCodigoSincronizacao()
            val sincronizacaoAtiva = configFragment.isSincronizacaoAtiva()
            val intervalo = configFragment.getIntervalo()

            // Validações básicas
            if (localizacaoId.isEmpty()) {
                configFragment.setLocalizacaoIdError("ID da Localização é obrigatório")
                return
            }

            if (codigoSincronizacao.isEmpty()) {
                configFragment.setCodigoSincronizacaoError("Código de sincronização é obrigatório")
                return
            }

            if (intervalo <= 0) {
                Toast.makeText(this, "Intervalo deve ser maior que zero", Toast.LENGTH_SHORT).show()
                return
            }

            // Limpar erros
            configFragment.setLocalizacaoIdError(null)
            configFragment.setCodigoSincronizacaoError(null)

            // Salvar usando ConfiguracoesManager
            lifecycleScope.launch {
                ConfiguracoesManager.salvarConfiguracoes(
                    this@ConfiguracoesActivity, 
                    localizacaoId, 
                    codigoSincronizacao, 
                    8, // hora padrão
                    0, // minuto padrão
                    sincronizacaoAtiva,
                    intervalo
                )
            }

            // Mostrar mensagem de sucesso
            Toast.makeText(this, "Configurações salvas com sucesso!", Toast.LENGTH_SHORT).show()

            // Configurar alarme para sincronização se estiver ativa
            val sincronizacaoService = SincronizacaoService()
            if (sincronizacaoAtiva) {
                Log.d("ConfiguracoesActivity", "✅ Configurando alarme: sincronização ativa com intervalo de $intervalo horas")
                sincronizacaoService.configurarAlarme(this, 0, 0, intervalo) // ✅ CORREÇÃO: Usar tempo atual
                
                // ✅ NOVO: Iniciar sincronização imediatamente
                sincronizacaoService.iniciarSincronizacaoImediata(this)
                
                Toast.makeText(this, "⏰ Alarme configurado para $intervalo hora(s) - Sincronização iniciada!", Toast.LENGTH_LONG).show()
            } else {
                Log.d("ConfiguracoesActivity", "❌ Cancelando alarme: sincronização desativada")
                sincronizacaoService.cancelarAlarme(this)
                Toast.makeText(this, "🛑 Sincronização automática desativada", Toast.LENGTH_SHORT).show()
            }

            finish()

        } catch (e: Exception) {
            Toast.makeText(this, "Erro ao salvar configurações: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun executarSincronizacaoManual() {
        Toast.makeText(this, "🔄 Iniciando sincronização manual...", Toast.LENGTH_SHORT).show()
        Log.d("ConfiguracoesActivity", "🔄 Botão sincronizar pressionado - executarSincronizacaoManual")
        
        val sincronizacaoService = SincronizacaoService()
        
        // Executar verificação de debug primeiro
        sincronizacaoService.verificarStatusSincronizacao(this)
        
        // Executar sincronização
        sincronizacaoService.executarSincronizacaoManual(this)
        
        Toast.makeText(this, "📤 Sincronização enviada para processamento", Toast.LENGTH_LONG).show()
        
        // Aguardar um pouco e atualizar o histórico
        binding.viewPager.postDelayed({
            getCurrentHistoricoFragment()?.atualizarHistorico()
            Toast.makeText(this, "🔄 Histórico atualizado", Toast.LENGTH_SHORT).show()
        }, 2000)
    }
    
    // ✅ NOVA FUNÇÃO: Testar alarme de sincronização
    fun testarAlarmeSincronizacao() {
        Toast.makeText(this, "🧪 Testando alarme em 10 segundos...", Toast.LENGTH_LONG).show()
        Log.d("ConfiguracoesActivity", "🧪 Testando alarme de sincronização")
        
        val sincronizacaoService = SincronizacaoService()
        sincronizacaoService.verificarStatusSincronizacao(this)
        sincronizacaoService.testarAlarmeImediato(this)
        
        Toast.makeText(this, "⏰ Alarme configurado! Veja os logs em 10s", Toast.LENGTH_LONG).show()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        finish()
        super.onBackPressed()
    }
} 