package com.example.iface_offilne.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.iface_offilne.R
import com.example.iface_offilne.data.HistoricoSincronizacaoEntity
import com.example.iface_offilne.databinding.ItemHistoricoSincronizacaoBinding
import java.text.SimpleDateFormat
import java.util.*

class HistoricoSincronizacaoAdapter(
    private val historico: List<HistoricoSincronizacaoEntity>
) : RecyclerView.Adapter<HistoricoSincronizacaoAdapter.HistoricoViewHolder>() {

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale("pt", "BR"))

    inner class HistoricoViewHolder(private val binding: ItemHistoricoSincronizacaoBinding) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(sincronizacao: HistoricoSincronizacaoEntity) {
            // Formatar data e hora
            binding.textDataHora.text = dateFormat.format(sincronizacao.dataHora)
            
            // Configurar status e ícone
            when (sincronizacao.status) {
                "SUCESSO" -> {
                    binding.iconStatus.setImageResource(R.drawable.ic_check_circle)
                    binding.iconStatus.setColorFilter(android.graphics.Color.parseColor("#4CAF50"))
                    binding.textStatus.text = "Sincronização realizada com sucesso"
                    binding.textStatus.setTextColor(android.graphics.Color.parseColor("#4CAF50"))
                }
                "ERRO" -> {
                    binding.iconStatus.setImageResource(R.drawable.ic_error)
                    binding.iconStatus.setColorFilter(android.graphics.Color.parseColor("#F44336"))
                    binding.textStatus.text = sincronizacao.mensagem ?: "Erro na sincronização"
                    binding.textStatus.setTextColor(android.graphics.Color.parseColor("#F44336"))
                }
                "EM_ANDAMENTO" -> {
                    binding.iconStatus.setImageResource(R.drawable.ic_sync)
                    binding.iconStatus.setColorFilter(android.graphics.Color.parseColor("#FF9800"))
                    binding.textStatus.text = "Sincronização em andamento..."
                    binding.textStatus.setTextColor(android.graphics.Color.parseColor("#FF9800"))
                }
            }
            
            // Configurar detalhes
            val detalhes = StringBuilder()
            if (sincronizacao.quantidadePontos > 0) {
                detalhes.append("${sincronizacao.quantidadePontos} pontos sincronizados")
            }
            if (sincronizacao.duracaoSegundos > 0) {
                if (detalhes.isNotEmpty()) detalhes.append(" • ")
                detalhes.append("${sincronizacao.duracaoSegundos} segundos")
            }
            
            binding.textDetalhes.text = detalhes.toString()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoricoViewHolder {
        val binding = ItemHistoricoSincronizacaoBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return HistoricoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HistoricoViewHolder, position: Int) {
        holder.bind(historico[position])
    }

    override fun getItemCount(): Int = historico.size
} 