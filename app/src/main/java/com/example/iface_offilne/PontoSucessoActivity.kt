package com.example.iface_offilne

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.*

/**
 * Activity para mostrar confirmação de ponto registrado com sucesso
 */
class PontoSucessoActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "PontoSucessoActivity"
        
        // Constantes para extras
        const val EXTRA_FUNCIONARIO_NOME = "funcionario_nome"
        const val EXTRA_DATA_HORA = "data_hora"
        const val EXTRA_LATITUDE = "latitude"
        const val EXTRA_LONGITUDE = "longitude"
    }

    private lateinit var tvFuncionarioNome: TextView
    private lateinit var tvDataPonto: TextView
    private lateinit var tvHoraPonto: TextView
    private lateinit var tvCoordenadas: TextView
    private lateinit var btnFechar: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ponto_sucesso)
        
        Log.d(TAG, "🎉 Iniciando PontoSucessoActivity")
        
        try {
            setupViews()
            loadData()
            setupAutoReturn()
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro na inicialização: ${e.message}")
            e.printStackTrace()
            finish()
        }
    }

    private fun setupViews() {
        try {
            tvFuncionarioNome = findViewById(R.id.tvFuncionarioNome)
            tvDataPonto = findViewById(R.id.tvDataPonto)
            tvHoraPonto = findViewById(R.id.tvHoraPonto)
            tvCoordenadas = findViewById(R.id.tvCoordenadas)
            btnFechar = findViewById(R.id.btnFechar)
            
            // Configurar botão fechar
            btnFechar.setOnClickListener {
                Log.d(TAG, "🔘 Botão fechar clicado")
                returnToPontoActivity()
            }
            
            Log.d(TAG, "✅ Views configuradas")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao configurar views: ${e.message}")
            throw e
        }
    }

    private fun loadData() {
        try {
            val intent = intent
            
            // Carregar nome do funcionário
            val funcionarioNome = intent.getStringExtra(EXTRA_FUNCIONARIO_NOME) ?: "FUNCIONÁRIO"
            tvFuncionarioNome.text = funcionarioNome.uppercase()
            
            // Carregar data e hora
            val dataHora = intent.getLongExtra(EXTRA_DATA_HORA, System.currentTimeMillis())
            val date = Date(dataHora)
            
            val dataFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            val horaFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            
            tvDataPonto.text = dataFormat.format(date)
            tvHoraPonto.text = horaFormat.format(date)
            
            // Carregar coordenadas
            val latitude = intent.getDoubleExtra(EXTRA_LATITUDE, 0.0)
            val longitude = intent.getDoubleExtra(EXTRA_LONGITUDE, 0.0)
            
            if (latitude != 0.0 && longitude != 0.0) {
                tvCoordenadas.text = "$latitude | $longitude"
            } else {
                tvCoordenadas.text = "Localização não disponível"
            }
            
            Log.d(TAG, "✅ Dados carregados:")
            Log.d(TAG, "   Nome: $funcionarioNome")
            Log.d(TAG, "   Data: ${tvDataPonto.text}")
            Log.d(TAG, "   Hora: ${tvHoraPonto.text}")
            Log.d(TAG, "   Coordenadas: ${tvCoordenadas.text}")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao carregar dados: ${e.message}")
            e.printStackTrace()
            
            // Fallback com dados padrão
            tvFuncionarioNome.text = "FUNCIONÁRIO"
            tvDataPonto.text = "Data não disponível"
            tvHoraPonto.text = "Hora não disponível"
            tvCoordenadas.text = "Localização não disponível"
        }
    }

    private fun setupAutoReturn() {
        try {
            // Retornar automaticamente após 3 segundos
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    if (!isFinishing && !isDestroyed) {
                        Log.d(TAG, "⏰ Retorno automático após 3 segundos")
                        returnToPontoActivity()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Erro no retorno automático: ${e.message}")
                }
            }, 3000)
            
            Log.d(TAG, "✅ Retorno automático configurado para 3 segundos")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao configurar retorno automático: ${e.message}")
        }
    }

    private fun returnToPontoActivity() {
        try {
            Log.d(TAG, "🔄 Retornando para PontoActivity")
            
            // Criar intent para voltar à tela de ponto
            val intent = Intent(this, PontoActivity::class.java)
            
            // ✅ FORÇAR REINICIALIZAÇÃO COMPLETA DA ACTIVITY
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            
            // ✅ ADICIONAR FLAG PARA INDICAR QUE VEM DA TELA DE SUCESSO
            intent.putExtra("FROM_SUCCESS_SCREEN", true)
            
            startActivity(intent)
            finish()
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao retornar para PontoActivity: ${e.message}")
            e.printStackTrace()
            finish()
        }
    }

    override fun onBackPressed() {
        // Permitir voltar com o botão físico também
        Log.d(TAG, "🔙 Botão voltar pressionado")
        returnToPontoActivity()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "🧹 PontoSucessoActivity finalizada")
    }
} 