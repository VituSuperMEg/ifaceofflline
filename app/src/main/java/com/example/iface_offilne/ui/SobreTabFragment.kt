package com.example.iface_offilne.ui

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.iface_offilne.databinding.FragmentSobreTabBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.iface_offilne.util.UpdateManager
import com.example.iface_offilne.util.UpdateCheckResult
import java.text.SimpleDateFormat
import java.util.*

class SobreTabFragment : Fragment() {

    private var _binding: FragmentSobreTabBinding? = null
    private val binding get() = _binding!!

    // Callback para comunicação com a activity
    var onUpdateCheckClick: (() -> Unit)? = null
    var onUpdateClick: (() -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSobreTabBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        setupListeners()
        carregarInformacoesVersao()
    }

    private fun setupUI() {
        // Configurar botões para usar os selectors corretamente
        binding.btnCheckUpdate.backgroundTintList = null
        binding.btnUpdate.backgroundTintList = null
    }

    private fun setupListeners() {
        binding.btnCheckUpdate.setOnClickListener {
            verificarAtualizacoes()
        }

        binding.btnUpdate.setOnClickListener {
            executarAtualizacao()
        }
    }

    private fun carregarInformacoesVersao() {
        try {
            val updateManager = UpdateManager()
            val currentVersion = updateManager.getCurrentAppVersion(requireContext())
            
            // Versão atual
            binding.tvCurrentVersion.text = "Versão ${currentVersion.versionName}"
            binding.tvVersionCode.text = "${currentVersion.versionName} (${currentVersion.versionCode})"
            binding.tvBuildDate.text = currentVersion.buildDate

            // Status inicial
            binding.tvUpdateStatus.text = "Verificar"
            binding.tvUpdateStatus.setTextColor(resources.getColor(android.R.color.holo_blue_dark, null))

        } catch (e: Exception) {
            binding.tvCurrentVersion.text = "Versão Desconhecida"
            binding.tvVersionCode.text = "N/A"
            binding.tvBuildDate.text = "N/A"
        }
    }

    private fun verificarAtualizacoes() {
        // Mostrar loading
        binding.btnCheckUpdate.isEnabled = false
        binding.btnCheckUpdate.text = "Verificando..."
        binding.progressBar.visibility = View.VISIBLE
        binding.tvUpdateMessage.text = "Verificando atualizações disponíveis..."

        lifecycleScope.launch {
            try {
                val updateManager = UpdateManager()
                val result = updateManager.checkForUpdates(requireContext())

                when (result) {
                    is UpdateCheckResult.UpdateAvailable -> {
                        // Há atualização disponível
                        binding.tvUpdateStatus.text = "Atualização Disponível"
                        binding.tvUpdateStatus.setTextColor(resources.getColor(android.R.color.holo_orange_dark, null))
                        binding.btnUpdate.visibility = View.VISIBLE
                        binding.tvUpdateMessage.text = "Nova versão ${result.updateInfo.versionName} disponível!\n\n${result.updateInfo.releaseNotes}"
                    }
                    is UpdateCheckResult.NoUpdateAvailable -> {
                        // Não há atualização
                        binding.tvUpdateStatus.text = "Atualizado"
                        binding.tvUpdateStatus.setTextColor(resources.getColor(android.R.color.holo_green_dark, null))
                        binding.btnUpdate.visibility = View.GONE
                        binding.tvUpdateMessage.text = "Você está usando a versão mais recente do aplicativo."
                    }
                    is UpdateCheckResult.Error -> {
                        binding.tvUpdateStatus.text = "Erro"
                        binding.tvUpdateStatus.setTextColor(resources.getColor(android.R.color.holo_red_dark, null))
                        binding.tvUpdateMessage.text = "Erro ao verificar atualizações: ${result.message}"
                        Toast.makeText(context, "Erro ao verificar atualizações", Toast.LENGTH_SHORT).show()
                    }
                }

                // Notificar a activity se necessário
                onUpdateCheckClick?.invoke()

            } catch (e: Exception) {
                binding.tvUpdateStatus.text = "Erro"
                binding.tvUpdateStatus.setTextColor(resources.getColor(android.R.color.holo_red_dark, null))
                binding.tvUpdateMessage.text = "Erro ao verificar atualizações: ${e.message}"
                Toast.makeText(context, "Erro ao verificar atualizações", Toast.LENGTH_SHORT).show()
            } finally {
                // Restaurar botão
                binding.btnCheckUpdate.isEnabled = true
                binding.btnCheckUpdate.text = "Verificar Atualizações"
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun executarAtualizacao() {
        // Mostrar loading
        binding.btnUpdate.isEnabled = false
        binding.btnUpdate.text = "Atualizando..."
        binding.progressBar.visibility = View.VISIBLE
        binding.progressBar.progress = 0
        binding.tvUpdateMessage.text = "Iniciando download da atualização..."

        lifecycleScope.launch {
            try {
                // Simular processo de atualização
                for (progress in 0..100 step 10) {
                    withContext(Dispatchers.Main) {
                        binding.progressBar.progress = progress
                        binding.tvUpdateMessage.text = "Baixando atualização... $progress%"
                    }
                    Thread.sleep(200) // Simular tempo de download
                }

                // Simular instalação
                binding.tvUpdateMessage.text = "Instalando atualização..."
                Thread.sleep(1000)

                // Sucesso
                binding.tvUpdateStatus.text = "Atualizado"
                binding.tvUpdateStatus.setTextColor(resources.getColor(android.R.color.holo_green_dark, null))
                binding.btnUpdate.visibility = View.GONE
                binding.tvUpdateMessage.text = "Atualização concluída com sucesso! O aplicativo será reiniciado."
                
                Toast.makeText(context, "Atualização concluída!", Toast.LENGTH_LONG).show()

                // Notificar a activity
                onUpdateClick?.invoke()

                // Simular reinicialização do app (opcional)
                // requireActivity().recreate()

            } catch (e: Exception) {
                binding.tvUpdateStatus.text = "Erro"
                binding.tvUpdateStatus.setTextColor(resources.getColor(android.R.color.holo_red_dark, null))
                binding.tvUpdateMessage.text = "Erro durante a atualização: ${e.message}"
                Toast.makeText(context, "Erro durante a atualização", Toast.LENGTH_SHORT).show()
            } finally {
                // Restaurar botão
                binding.btnUpdate.isEnabled = true
                binding.btnUpdate.text = "Atualizar Agora"
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    // Método para forçar verificação de atualização (pode ser chamado externamente)
    fun forcarVerificacaoAtualizacao() {
        verificarAtualizacoes()
    }

    // Método para mostrar mensagem personalizada
    fun mostrarMensagem(mensagem: String) {
        binding.tvUpdateMessage.text = mensagem
    }

    // Método para atualizar status
    fun atualizarStatus(status: String, cor: Int) {
        binding.tvUpdateStatus.text = status
        binding.tvUpdateStatus.setTextColor(resources.getColor(cor, null))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 