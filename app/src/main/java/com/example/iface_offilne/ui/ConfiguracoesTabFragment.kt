package com.example.iface_offilne.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.iface_offilne.databinding.FragmentConfiguracoesTabBinding
import com.example.iface_offilne.util.DuplicatePointManager

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

        binding.btnSincronizarAgora.setOnClickListener {
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
    }

    fun getLocalizacaoId(): String {
        return _binding?.editTextLocalizacaoId?.text?.toString()?.trim() ?: ""
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