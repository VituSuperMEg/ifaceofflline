package com.example.iface_offilne.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.iface_offilne.R
import com.example.iface_offilne.data.PontosGenericosEntity
import java.text.SimpleDateFormat
import java.util.*

class PontosAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_PONTO = 1
    }

    private var pontos: List<Any> = emptyList()

    fun setPontos(pontos: List<PontosGenericosEntity>) {
        val items = mutableListOf<Any>()
        
        // Ordenar por data de forma decrescente (mais recente primeiro)
        val pontosOrdenados = pontos.sortedByDescending { it.dataHora }
        
        // Agrupar por data
        val pontosPorData = pontosOrdenados.groupBy { ponto ->
            SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(ponto.dataHora))
        }

        for ((data, pontosDoDia) in pontosPorData) {
            items.add(DataHeader(data, pontosDoDia.size))
            items.addAll(pontosDoDia)
        }

        this.pontos = items
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return if (pontos[position] is DataHeader) TYPE_HEADER else TYPE_PONTO
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_HEADER -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_data_header, parent, false)
                HeaderViewHolder(view)
            }
            TYPE_PONTO -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_ponto, parent, false)
                PontoViewHolder(view)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is HeaderViewHolder -> holder.bind(pontos[position] as DataHeader)
            is PontoViewHolder -> holder.bind(pontos[position] as PontosGenericosEntity)
        }
    }

    override fun getItemCount(): Int = pontos.size

    class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textData: TextView = itemView.findViewById(R.id.textData)
        private val textQuantidadeDia: TextView = itemView.findViewById(R.id.textQuantidadeDia)

        fun bind(header: DataHeader) {
            textData.text = formatarData(header.data)
            textQuantidadeDia.text = "${header.quantidade} ponto${if (header.quantidade > 1) "s" else ""}"
        }

        private fun formatarData(data: String): String {
            val formato = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            val date = formato.parse(data)
            val formatoExibicao = SimpleDateFormat("dd 'de' MMMM 'de' yyyy", Locale("pt", "BR"))
            return formatoExibicao.format(date!!)
        }
    }

    class PontoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textNomeFuncionario: TextView = itemView.findViewById(R.id.textNomeFuncionario)
        private val textMatricula: TextView = itemView.findViewById(R.id.textMatricula)
        private val textCpf: TextView = itemView.findViewById(R.id.textCpf)
        private val textCargo: TextView = itemView.findViewById(R.id.textCargo)
        private val textSecretaria: TextView = itemView.findViewById(R.id.textSecretaria)
        private val textLotacao: TextView = itemView.findViewById(R.id.textLotacao)
        private val textHora: TextView = itemView.findViewById(R.id.textHora)
        private val textDataCompleta: TextView = itemView.findViewById(R.id.textDataCompleta)
        private val textTipo: TextView = itemView.findViewById(R.id.textTipo)
        private val textStatusSincronizacao: TextView = itemView.findViewById(R.id.textStatusSincronizacao)

        fun bind(ponto: PontosGenericosEntity) {
            val formatoHora = SimpleDateFormat("HH:mm", Locale.getDefault())
            val formatoCompleto = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
            
            // Informações básicas
            textNomeFuncionario.text = ponto.funcionarioNome
            textHora.text = formatoHora.format(Date(ponto.dataHora))
            textDataCompleta.text = "Data: ${formatoCompleto.format(Date(ponto.dataHora))}"
            // Ocultar tipo de ponto (não mais necessário)
            textTipo.visibility = View.GONE
            
            // Informações detalhadas do funcionário
            textMatricula.text = if (ponto.funcionarioMatricula.isNotEmpty()) "Matrícula: ${ponto.funcionarioMatricula}" else "Matrícula: N/A"
            textCpf.text = if (ponto.funcionarioCpf.isNotEmpty()) "CPF: ${formatarCpf(ponto.funcionarioCpf)}" else "CPF: N/A"
            textCargo.text = if (ponto.funcionarioCargo.isNotEmpty()) "Cargo: ${ponto.funcionarioCargo}" else "Cargo: N/A"
            textSecretaria.text = if (ponto.funcionarioSecretaria.isNotEmpty()) "Secretaria: ${ponto.funcionarioSecretaria}" else "Secretaria: N/A"
            textLotacao.text = if (ponto.funcionarioLotacao.isNotEmpty()) "Lotação: ${ponto.funcionarioLotacao}" else "Lotação: N/A"
            
            // Status de sincronização
            if (ponto.synced) {
                textStatusSincronizacao.text = "Sincronizado"
                textStatusSincronizacao.setTextColor(itemView.context.getColor(android.R.color.holo_green_dark))
                textStatusSincronizacao.setBackgroundColor(itemView.context.getColor(R.color.green_light))
            } else {
                textStatusSincronizacao.text = "Pendente"
                textStatusSincronizacao.setTextColor(itemView.context.getColor(android.R.color.holo_orange_dark))
                textStatusSincronizacao.setBackgroundColor(itemView.context.getColor(R.color.orange_light))
            }
        }
        
        private fun formatarCpf(cpf: String): String {
            return if (cpf.length == 11) {
                "${cpf.substring(0, 3)}.${cpf.substring(3, 6)}.${cpf.substring(6, 9)}-${cpf.substring(9)}"
            } else {
                cpf
            }
        }
    }

    data class DataHeader(val data: String, val quantidade: Int)
} 