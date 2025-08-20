package com.example.iface_offilne.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.iface_offilne.EntidadeActivity
import com.example.iface_offilne.databinding.FragmentConfiguracoesTabBinding
import com.example.iface_offilne.util.ConfiguracoesManager
import com.example.iface_offilne.util.DuplicatePointManager
import com.example.iface_offilne.util.SessionManager
import androidx.lifecycle.lifecycleScope
import androidx.appcompat.app.AlertDialog
import com.example.iface_offilne.util.DiagnosticoHelper
import kotlinx.coroutines.launch

class ConfiguracoesTabFragment : Fragment() {

    private var _binding: FragmentConfiguracoesTabBinding? = null
    private val binding get() = _binding!!

    var onSincronizarClick: (() -> Unit)? = null
    var onSwitchChange: ((Boolean) -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentConfiguracoesTabBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupListeners()
        atualizarStatusEntidade()
    }

    override fun onResume() {
        super.onResume()
        // Atualizar status da entidade sempre que voltar para o fragment
        atualizarStatusEntidade()
    }

    private fun setupListeners() {
        // Configurar cor do botão para usar o padrão
        binding.btnSincronizarAgora.backgroundTintList = null
        binding.btnVerificarDuplicatas.backgroundTintList = null
        binding.btnForcarMarcacao.backgroundTintList = null

        binding.switchSincronizacao.setOnCheckedChangeListener { _, isChecked ->
            binding.layoutIntervaloSincronizacao.visibility = if (isChecked) View.VISIBLE else View.GONE
            onSwitchChange?.invoke(isChecked)
        }

        // ✅ NOVO: Long press para testar alarme
        binding.btnSincronizarAgora.setOnLongClickListener {
            try {
                (requireActivity() as? com.example.iface_offilne.ConfiguracoesActivity)?.testarAlarmeSincronizacao()
            } catch (e: Exception) {
                android.util.Log.e("ConfigTab", "❌ Erro ao testar alarme: ${e.message}")
                Toast.makeText(context, "❌ Erro ao testar alarme", Toast.LENGTH_SHORT).show()
            }
            true
        }
        
        // ✅ NOVO: Double tap para testar alarme de 1 minuto
        var lastClickTime = 0L
        binding.btnSincronizarAgora.setOnClickListener {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastClickTime < 300) { // Double tap detectado
                testarAlarmeUmMinuto()
            }
            lastClickTime = currentTime
            
            // Executar ação normal do botão
            onSincronizarClick?.invoke()
        }
        
        // Botão para verificar e marcar duplicatas
        binding.btnVerificarDuplicatas.setOnClickListener {
            verificarDuplicatas()
        }
        
        // Botão para forçar marcação de todas as batidas
        binding.btnForcarMarcacao.setOnClickListener {
            forcarMarcacaoTodasBatidas()
        }

        // ✅ NOVO: Botão para executar diagnóstico
        binding.btnDiagnostico.setOnClickListener {
            executarDiagnostico()
        }

        // ✅ NOVO: Botão para limpar dados
        binding.btnLimparDados.setOnClickListener {
            executarLimpezaDados()
        }
    }

    /**
     * ✅ NOVO: Atualiza o status da entidade na interface
     */
    private fun atualizarStatusEntidade() {
        try {
            lifecycleScope.launch {
                val entidadeConfigurada = ConfiguracoesManager.isEntidadeConfigurada(requireContext())
                
                if (entidadeConfigurada) {
                    // Entidade configurada - mostrar status positivo
                    val entidadeId = ConfiguracoesManager.getEntidadeId(requireContext())
                    Log.d("ConfigTab", "✅ Status da entidade atualizado: $entidadeId")
                } else {
                    // Entidade não configurada - mostrar status negativo
                    Log.d("ConfigTab", "⚠️ Status da entidade atualizado: não configurada")
                }
            }
        } catch (e: Exception) {
            Log.e("ConfigTab", "❌ Erro ao atualizar status da entidade: ${e.message}")
        }
    }

    /**
     * ✅ NOVO: Abre a tela de configuração de entidade
     */
    private fun configurarEntidade() {
        try {
            Log.d("ConfigTab", "🔧 Abrindo tela de configuração de entidade")
            
            val intent = Intent(requireContext(), EntidadeActivity::class.java)
            startActivity(intent)
            
            Toast.makeText(context, "🔧 Abrindo configuração de entidade...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("ConfigTab", "❌ Erro ao abrir configuração de entidade: ${e.message}")
            Toast.makeText(context, "❌ Erro ao abrir configuração: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * ✅ NOVO: Abre a tela de alteração de entidade
     */
    private fun alterarEntidade() {
        try {
            Log.d("ConfigTab", "🔄 Abrindo tela de alteração de entidade")
            
            val intent = Intent(requireContext(), EntidadeActivity::class.java)
            startActivity(intent)
            
            Toast.makeText(context, "🔄 Abrindo alteração de entidade...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("ConfigTab", "❌ Erro ao abrir alteração de entidade: ${e.message}")
            Toast.makeText(context, "❌ Erro ao abrir alteração: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * ✅ NOVO: Testa o alarme com intervalo de 1 minuto para debug
     */
    private fun testarAlarmeUmMinuto() {
        try {
            android.util.Log.d("ConfigTab", "🧪 === TESTE DE ALARME DE 1 MINUTO ===")
            
            val sincronizacaoService = com.example.iface_offilne.service.SincronizacaoService()
            sincronizacaoService.testarAlarmeComIntervalo(requireContext(), 1)
            
            Toast.makeText(context, "🧪 Alarme de teste configurado para 1 minuto!\nVeja os logs para acompanhar.", Toast.LENGTH_LONG).show()
            
        } catch (e: Exception) {
            android.util.Log.e("ConfigTab", "❌ Erro ao testar alarme de 1 minuto: ${e.message}")
            Toast.makeText(context, "❌ Erro ao testar alarme: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun getLocalizacaoId(): String {
        return _binding?.editTextLocalizacaoId?.text?.toString()?.trim() ?: ""
    }

    fun getEntidade(): String {
        return _binding?.editTextEntidadeId?.text?.toString()?.trim() ?: ""
    }


    fun getCodigoSincronizacao(): String {
        return _binding?.editTextCodigoSincronizacao?.text?.toString()?.trim() ?: ""
    }

    fun getIntervalo(): Int {
        return _binding?.editTextIntervalo?.text?.toString()?.toIntOrNull() ?: 24
    }

    fun isSincronizacaoAtiva(): Boolean {
        return _binding?.switchSincronizacao?.isChecked ?: false
    }

    fun setLocalizacaoId(value: String) {
        _binding?.editTextLocalizacaoId?.setText(value)
    }

    fun setCodigoSincronizacao(value: String) {
        _binding?.editTextCodigoSincronizacao?.setText(value)
    }

    fun setIntervalo(value: Int) {
        _binding?.editTextIntervalo?.setText(value.toString())
    }

    fun setSincronizacaoAtiva(value: Boolean) {
        _binding?.let { binding ->
            binding.switchSincronizacao.isChecked = value
            binding.layoutIntervaloSincronizacao.visibility = if (value) View.VISIBLE else View.GONE
        }
    }

    fun setLocalizacaoIdError(error: String?) {
        _binding?.editTextLocalizacaoId?.error = error
    }

    fun setCodigoSincronizacaoError(error: String?) {
        _binding?.editTextCodigoSincronizacao?.error = error
    }
    
    fun setEntidade(value: String) {
        _binding?.editTextEntidadeId?.setText(value)
    }
    
    fun setEntidadeError(error: String?) {
        _binding?.editTextEntidadeId?.error = error
    }
    
    private fun verificarDuplicatas() {
        binding.btnVerificarDuplicatas.isEnabled = false
        binding.btnVerificarDuplicatas.text = "🔄 Verificando..."
        
        DuplicatePointManager.forcarMarcacaoBatidasDuplicadasAsync(requireContext()) { result ->
            binding.btnVerificarDuplicatas.isEnabled = true
            binding.btnVerificarDuplicatas.text = "🔍 Verificar e Marcar Duplicatas"
            
            if (result.success) {
                val message = if (result.marcadasComoSincronizadas > 0) {
                    "✅ ${result.marcadasComoSincronizadas} batidas duplicadas foram marcadas como sincronizadas!\n\nBatidas processadas:\n${result.batidasMarcadas.take(5).joinToString("\n")}"
                } else {
                    "ℹ️ Nenhuma batida duplicada encontrada. Todas as batidas pendentes são únicas."
                }
                
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(context, "❌ ${result.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    /**
     * 🔍 EXECUTAR DIAGNÓSTICO COMPLETO
     */
    private fun executarDiagnostico() {
        binding.btnDiagnostico.isEnabled = false
        binding.btnDiagnostico.text = "🔍 Executando..."
        
        lifecycleScope.launch {
            try {
                val resultado = DiagnosticoHelper.executarDiagnosticoCompleto(requireContext())
                val relatorio = DiagnosticoHelper.gerarRelatorio(resultado)
                
                binding.btnDiagnostico.isEnabled = true
                binding.btnDiagnostico.text = "🔍 Executar Diagnóstico"
                
                // Mostrar relatório em dialog
                mostrarRelatorioDiagnostico(relatorio, resultado)
                
            } catch (e: Exception) {
                binding.btnDiagnostico.isEnabled = true
                binding.btnDiagnostico.text = "🔍 Executar Diagnóstico"
                
                Toast.makeText(context, "❌ Erro no diagnóstico: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    /**
     * 📋 MOSTRAR RELATÓRIO DE DIAGNÓSTICO
     */
    private fun mostrarRelatorioDiagnostico(relatorio: String, resultado: DiagnosticoHelper.DiagnosticoResult) {
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("🔍 Relatório de Diagnóstico")
            .setMessage(relatorio)
            .setPositiveButton("✅ OK") { dialog, _ ->
                dialog.dismiss()
            }
            .setNeutralButton("🧹 Limpar Dados") { dialog, _ ->
                dialog.dismiss()
                executarLimpezaDados()
            }
            .setCancelable(true)
            .create()
        
        dialog.show()
    }
    
    /**
     * 🧹 EXECUTAR LIMPEZA DE DADOS
     */
    private fun executarLimpezaDados() {
        val confirmDialog = AlertDialog.Builder(requireContext())
            .setTitle("🧹 Limpeza de Dados")
            .setMessage("Esta ação irá remover dados problemáticos como:\n\n• Pontos duplicados\n• Sincronizações antigas\n• Dados corrompidos\n\nDeseja continuar?")
            .setPositiveButton("✅ Sim, Limpar") { dialog, _ ->
                dialog.dismiss()
                
                binding.btnLimparDados.isEnabled = false
                binding.btnLimparDados.text = "🧹 Limpando..."
                
                lifecycleScope.launch {
                    try {
                        val resultado = DiagnosticoHelper.limparDadosProblematicos(requireContext())
                        
                        binding.btnLimparDados.isEnabled = true
                        binding.btnLimparDados.text = "🧹 Limpar Dados"
                        
                        val message = if (resultado.sucesso) {
                            "✅ Limpeza concluída!\n\n• Pontos removidos: ${resultado.pontosRemovidos}\n• Sincronizações removidas: ${resultado.sincronizacoesRemovidas}"
                        } else {
                            "❌ Erro na limpeza: ${resultado.mensagem}"
                        }
                        
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                        
                    } catch (e: Exception) {
                        binding.btnLimparDados.isEnabled = true
                        binding.btnLimparDados.text = "🧹 Limpar Dados"
                        
                        Toast.makeText(context, "❌ Erro na limpeza: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("❌ Cancelar") { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(true)
            .create()
        
        confirmDialog.show()
    }
    
    private fun forcarMarcacaoTodasBatidas() {
        // Confirmar a ação perigosa
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("⚠️ ATENÇÃO - Ação Irreversível")
            .setMessage("Você está prestes a marcar TODAS as batidas pendentes como sincronizadas.\n\n" +
                    "Isso deve ser feito APENAS se você tem CERTEZA que todas já foram enviadas ao servidor.\n\n" +
                    "Esta ação NÃO PODE ser desfeita!\n\n" +
                    "Tem certeza que deseja continuar?")
            .setPositiveButton("SIM, FORÇAR MARCAÇÃO") { _, _ ->
                executarForcaMarcacao()
            }
            .setNegativeButton("Cancelar", null)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .show()
    }
    
    private fun executarForcaMarcacao() {
        binding.btnForcarMarcacao.isEnabled = false
        binding.btnForcarMarcacao.text = "⚠️ Forçando..."
        
        DuplicatePointManager.forcarMarcacaoTodasBatidasAsync(requireContext()) { result ->
            binding.btnForcarMarcacao.isEnabled = true
            binding.btnForcarMarcacao.text = "⚠️ Forçar Marcação de TODAS as Batidas"
            
            if (result.success) {
                val message = "⚠️ CONCLUÍDO!\n\n${result.marcadasComoSincronizadas} batidas foram FORÇADAMENTE marcadas como sincronizadas.\n\nBatidas processadas:\n${result.batidasMarcadas.take(5).joinToString("\n")}"
                
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(context, "❌ ${result.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 