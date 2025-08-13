package com.example.iface_offilne.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.iface_offilne.adapter.HistoricoSincronizacaoAdapter
import com.example.iface_offilne.data.AppDatabase
import com.example.iface_offilne.data.HistoricoSincronizacaoEntity
import com.example.iface_offilne.databinding.FragmentHistoricoTabBinding
import kotlinx.coroutines.launch

class HistoricoTabFragment : Fragment() {

    private var _binding: FragmentHistoricoTabBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: HistoricoSincronizacaoAdapter
    var onSincronizarClick: (() -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoricoTabBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupListeners()
        carregarHistorico()
    }

    private fun setupRecyclerView() {
        adapter = HistoricoSincronizacaoAdapter(emptyList())
        _binding?.recyclerViewHistorico?.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@HistoricoTabFragment.adapter
        }
    }

    private fun setupListeners() {
        // Configurar cor do botão para usar o padrão
        _binding?.btnSincronizarHistorico?.backgroundTintList = null
        
        _binding?.btnSincronizarHistorico?.setOnClickListener {
            android.util.Log.d("HistoricoTab", "🔄 Botão sincronizar pressionado")
            onSincronizarClick?.invoke()
        }
        
        // Long press para criar ponto de teste (debug)
        _binding?.btnSincronizarHistorico?.setOnLongClickListener {
            android.util.Log.d("HistoricoTab", "🧪 Criando ponto de teste...")
            lifecycleScope.launch {
                try {
                    val pontoService = com.example.iface_offilne.service.PontoSincronizacaoService()
                    pontoService.criarPontoTeste(requireContext())
                    android.widget.Toast.makeText(requireContext(), "✅ Ponto de teste criado!", android.widget.Toast.LENGTH_SHORT).show()
                    
                    // Atualizar histórico após criar ponto
                    kotlinx.coroutines.delay(500)
                    atualizarHistorico()
                } catch (e: Exception) {
                    android.util.Log.e("HistoricoTab", "❌ Erro ao criar ponto de teste: ${e.message}")
                    android.widget.Toast.makeText(requireContext(), "❌ Erro ao criar ponto de teste", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            true
        }
    }

    private fun carregarHistorico() {
        if (_binding == null) return
        
        lifecycleScope.launch {
            try {
                _binding?.progressBarHistorico?.visibility = View.VISIBLE
                
                val database = AppDatabase.getInstance(requireContext())
                val historicoDao = database.historicoSincronizacaoDao()
                
                // Usar getAllSincronizacoes() em vez de Flow para evitar loop infinito
                val historico = historicoDao.getAllSincronizacoes()
                
                if (_binding == null) return@launch
                
                if (historico.isEmpty()) {
                    _binding?.layoutVazio?.visibility = View.VISIBLE
                    _binding?.recyclerViewHistorico?.visibility = View.GONE
                } else {
                    _binding?.layoutVazio?.visibility = View.GONE
                    _binding?.recyclerViewHistorico?.visibility = View.VISIBLE
                    adapter = HistoricoSincronizacaoAdapter(historico)
                    _binding?.recyclerViewHistorico?.adapter = adapter
                }
                
            } catch (e: Exception) {
                android.util.Log.e("HistoricoTab", "Erro ao carregar histórico: ${e.message}")
                if (_binding != null) {
                    _binding?.layoutVazio?.visibility = View.VISIBLE
                    _binding?.recyclerViewHistorico?.visibility = View.GONE
                }
            } finally {
                if (_binding != null) {
                    _binding?.progressBarHistorico?.visibility = View.GONE
                }
            }
        }
    }

    fun atualizarHistorico() {
        if (_binding != null) {
            carregarHistorico()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 