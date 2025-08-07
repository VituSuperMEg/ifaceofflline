package com.example.iface_offilne

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.iface_offilne.data.AppDatabase
import com.example.iface_offilne.databinding.ActivityLoginBinding
import com.example.iface_offilne.util.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class Login : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private var logoClickCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

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

        binding.btnLogin.setOnClickListener {
            val cpf = binding.cpfLogin.text.toString().trim()
            val senha = binding.senhaLogin.text.toString().trim()

            if(cpf.isEmpty() || senha.isEmpty()) {
                Toast.makeText(this, "Preecha todos os campos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val db = AppDatabase.getInstance(this)
            val operadorDap = db.operadorDao()

            CoroutineScope(Dispatchers.IO).launch {
                val usuario = operadorDap.getOperador(cpf, senha)
                withContext(Dispatchers.Main) {
                    withContext(Dispatchers.Main) {
                        if (usuario != null) {
                            Toast.makeText(this@Login, "Login com sucesso", Toast.LENGTH_SHORT).show()
                            val intent = Intent(this@Login, HomeActivity::class.java)
                            startActivity(intent)
                        } else {
                            Toast.makeText(this@Login, "Usuário ou senha incorretos", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }


    
    private fun ensureRootUserExists() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getInstance(this@Login)
                val operadorDao = db.operadorDao()
                
                // Verificar se ROOT existe
                val rootUser = operadorDao.getOperador("99999999", "00331520")
                
                if (rootUser == null) {
                    // Criar usuário ROOT se não existir
                    operadorDao.insert(
                        com.example.iface_offilne.data.OperadorEntity(
                            cpf = "99999999",
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
                        cpf = "99999999",
                        nome = "ROOT",
                        senha = "00331520"
                    )
                )
                
                Log.d("Login", "🔧 Usuário ROOT recriado via easter egg")
                
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@Login, 
                        "🔧 Usuário ROOT recriado!\nCPF: 99999999\nSenha: 00331520", 
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