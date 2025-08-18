package com.example.iface_offilne.Adpater

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.iface_offilne.R
import com.example.iface_offilne.models.FuncionariosModel

class FuncionariosAdapter(
    private val funcionarios: List<FuncionariosModel>,
    private val onClick: (FuncionariosModel) -> Unit
) : RecyclerView.Adapter<FuncionariosAdapter.FuncionarioViewHolder>() {

    inner class FuncionarioViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nomeTextView: TextView = itemView.findViewById(R.id.tvNomeFuncionario)
        private val matriculaTextView: TextView = itemView.findViewById(R.id.matricula)
        private val cpfTextView: TextView = itemView.findViewById(R.id.cpf)
        private val cargoTextView: TextView = itemView.findViewById(R.id.cargo)
        private val orgaoTextView: TextView = itemView.findViewById(R.id.orgao)
        private val setorTextView: TextView = itemView.findViewById(R.id.setor)
        private val localizacaoTextView: TextView = itemView.findViewById(R.id.localizacao)
        private val jaImportadoTextView: TextView = itemView.findViewById(R.id.jaImportado)

        fun bind(funcionario: FuncionariosModel, importados: Set<Int>?) {
            nomeTextView.text = funcionario.nome ?: "Sem nome"
            matriculaTextView.text = "Matrícula: ${funcionario.matricula ?: "00"}"
            cpfTextView.text = "CPF: ${funcionario.numero_cpf ?: "0000"}"
            cargoTextView.text = "Cargo: ${funcionario.cargo_descricao ?: "Sem cargo"}"
            
            // Dados gerais da requisição
            orgaoTextView.text = "Órgão: ${funcionario.orgao_descricao ?: "Não informado"}"
            setorTextView.text = "Setor: ${funcionario.setor_descricao ?: "Não informado"}"
            localizacaoTextView.text = "Localização: ${funcionario.localizacao_descricao ?: "Não informado"}"

            val jaImportado = importados?.contains(funcionario.id) == true
            jaImportadoTextView.text = "Já Importado: ${if (jaImportado) "Sim" else "Não"}"
            jaImportadoTextView.setTextColor(
                if (jaImportado) 0xFF4CAF50.toInt() else 0xFFFF6B6B.toInt()
            )

            itemView.setOnClickListener { onClick(funcionario) }
        }
    }

    private var idsImportados: Set<Int>? = null

    fun atualizarImportados(ids: Set<Int>) {
        idsImportados = ids
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FuncionarioViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_funcionarios, parent, false)
        return FuncionarioViewHolder(view)
    }

    override fun onBindViewHolder(holder: FuncionarioViewHolder, position: Int) {
        holder.bind(funcionarios[position], idsImportados)
    }

    override fun getItemCount(): Int = funcionarios.size
}
