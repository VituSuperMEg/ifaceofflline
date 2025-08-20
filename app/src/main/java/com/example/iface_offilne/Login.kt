package com.example.iface_offilne

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.iface_offilne.data.AppDatabase
import com.example.iface_offilne.databinding.ActivityLoginBinding
import com.example.iface_offilne.util.SessionManager
import com.example.iface_offilne.util.MaskUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class Login : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private var logoClickCount = 0
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Configurar ajuste automático do layout quando o teclado aparecer
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        // Inicializar SharedPreferences
        sharedPreferences = getSharedPreferences("LoginPrefs", MODE_PRIVATE)

        // Garantir que o usuário ROOT existe
        ensureRootUserExists()

        val myEntidade = SessionManager.entidade;
        val myMuni = SessionManager.municipio
        val myEstado = SessionManager.estadoBr

        binding.entidade.text = myEntidade?.name.toString()
        binding.muni.text = myMuni.toString()
        binding.estado.text = myEstado.toString()

        // Easter egg: clicar 5 vezes no logo para recriar usuário ROOT
        setupLogoEasterEgg()

        // Verificar se usuário já está logado
        checkSavedLogin()

        // Configurar botão para usar o background personalizado
        binding.btnLogin.backgroundTintList = null
        
        // ✅ MÁSCARA: Aplicar máscara de CPF
        MaskUtil.applyCpfMask(binding.cpfLogin)

        binding.btnLogin.setOnClickListener {
            val cpfComMascara = binding.cpfLogin.text.toString().trim()
            val cpf = MaskUtil.unmask(cpfComMascara) // ✅ MÁSCARA: Remover máscara para validação
            val senha = binding.senhaLogin.text.toString().trim()
            val manterLogado = binding.checkboxManterLogado.isChecked

            if(cpf.isEmpty() || senha.isEmpty()) {
                Toast.makeText(this, "Preencha todos os campos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            // ✅ MÁSCARA: Validar se CPF tem 11 dígitos
            if (cpf.length != 11) {
                Toast.makeText(this, "CPF deve ter 11 dígitos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val db = AppDatabase.getInstance(this)
            val operadorDap = db.operadorDao()

            CoroutineScope(Dispatchers.IO).launch {
                val usuario = operadorDap.getOperador(cpf, senha)
                withContext(Dispatchers.Main) {
                    if (usuario != null) {
                        // Salvar dados de login se "manter logado" estiver marcado
                        if (manterLogado) {
                            saveLoginData(cpf, senha)
                        } else {
                            clearLoginData()
                        }
                        
                        Toast.makeText(this@Login, "Login com sucesso", Toast.LENGTH_SHORT).show()
                        val intent = Intent(this@Login, HomeActivity::class.java)
                        startActivity(intent)
                        finish() // Fechar a tela de login
                    } else {
                        Toast.makeText(this@Login, "Usuário ou senha incorretos", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    /**
     * Verifica se há dados de login salvos e faz login automático
     */
    private fun checkSavedLogin() {
        val savedCpf = sharedPreferences.getString("saved_cpf", "")
        val savedSenha = sharedPreferences.getString("saved_senha", "")
        val manterLogado = sharedPreferences.getBoolean("manter_logado", false)

        if (manterLogado && !savedCpf.isNullOrEmpty() && !savedSenha.isNullOrEmpty()) {
            Log.d("Login", "🔄 Tentando login automático com dados salvos...")
            
            // ✅ MÁSCARA: Aplicar máscara ao CPF salvo
            val cpfComMascara = MaskUtil.maskCpf(savedCpf)
            
            // Preencher campos
            binding.cpfLogin.setText(cpfComMascara)
            binding.senhaLogin.setText(savedSenha)
            binding.checkboxManterLogado.isChecked = true
            
            // Tentar login automático
            CoroutineScope(Dispatchers.IO).launch {
                val db = AppDatabase.getInstance(this@Login)
                val operadorDao = db.operadorDao()
                val usuario = operadorDao.getOperador(savedCpf, savedSenha)
                
                withContext(Dispatchers.Main) {
                    if (usuario != null) {
                        Log.d("Login", "✅ Login automático realizado com sucesso")
                        Toast.makeText(this@Login, "Login automático realizado", Toast.LENGTH_SHORT).show()
                        val intent = Intent(this@Login, HomeActivity::class.java)
                        startActivity(intent)
                        finish()
                    } else {
                        Log.d("Login", "❌ Login automático falhou, dados inválidos")
                        clearLoginData() // Limpar dados inválidos
                    }
                }
            }
        }
    }

    /**
     * Salva os dados de login no SharedPreferences
     */
    private fun saveLoginData(cpf: String, senha: String) {
        val editor = sharedPreferences.edit()
        editor.putString("saved_cpf", cpf)
        editor.putString("saved_senha", senha)
        editor.putBoolean("manter_logado", true)
        editor.apply()
        Log.d("Login", "💾 Dados de login salvos para: $cpf")
    }

    /**
     * Limpa os dados de login salvos
     */
    private fun clearLoginData() {
        val editor = sharedPreferences.edit()
        editor.remove("saved_cpf")
        editor.remove("saved_senha")
        editor.remove("manter_logado")
        // ✅ NOVO: Limpar também dados da entidade
        editor.remove("saved_entidade_id")
        editor.remove("saved_entidade_name")
        editor.remove("saved_estado")
        editor.remove("saved_municipio")
        editor.apply()
        Log.d("Login", "🗑️ Dados de login e entidade limpos")
    }


    
    private fun ensureRootUserExists() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getInstance(this@Login)
                val operadorDao = db.operadorDao()
                
                // Verificar se ROOT existe
                val rootUser = operadorDao.getOperador("99999999999", "00331520")
                
                if (rootUser == null) {
                    // Criar usuário ROOT se não existir
                    operadorDao.insert(
                        com.example.iface_offilne.data.OperadorEntity(
                            cpf = "99999999999",
                            nome = "ROOT",
                            senha = "00331520"
                        )
                    )
                    Log.d("Login", "✅ Usuário ROOT criado com sucesso")
                    
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@Login, "Usuário ROOT inicializado", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Log.d("Login", "✅ Usuário ROOT já existe")
                }
                
            } catch (e: Exception) {
                Log.e("Login", "❌ Erro ao verificar usuário ROOT: ${e.message}")
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@Login, "Erro ao inicializar sistema", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun setupLogoEasterEgg() {
        binding.imageView3.setOnClickListener {
            logoClickCount++
            
            if (logoClickCount >= 5) {
                logoClickCount = 0
                forceCreateRootUser()
            } else {
                // Reset contador após 3 segundos de inatividade
                binding.imageView3.postDelayed({
                    logoClickCount = 0
                }, 3000)
            }
        }
    }
    
    private fun forceCreateRootUser() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getInstance(this@Login)
                val operadorDao = db.operadorDao()
                
                // Forçar inserção do usuário ROOT (substituir se existir)
                operadorDao.insert(
                    com.example.iface_offilne.data.OperadorEntity(
                        cpf = "99999999999",
                        nome = "ROOT",
                        senha = "00331520"
                    )
                )
                
                Log.d("Login", "🔧 Usuário ROOT recriado via easter egg")
                
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@Login, 
                        "🔧 Usuário ROOT recriado!\nCPF: 999.999.999-99\nSenha: 00331520", 
                        Toast.LENGTH_LONG).show()
                }
                
            } catch (e: Exception) {
                Log.e("Login", "❌ Erro ao recriar usuário ROOT: ${e.message}")
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@Login, "❌ Erro ao recriar usuário ROOT", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}