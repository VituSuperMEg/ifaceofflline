package com.example.iface_offilne.Adpater

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.iface_offilne.models.FuncionariosLocalModel
import com.example.iface_offilne.R

class UsuariosAdapter(
    private val usuarios: List<FuncionariosLocalModel>,
    private val onClick: (FuncionariosLocalModel) -> Unit
) : RecyclerView.Adapter<UsuariosAdapter.UsuarioViewHolder>() {

    inner class UsuarioViewHolder(itemView: View): RecyclerView.ViewHolder(itemView) {
        private val nomeTextView: TextView = itemView.findViewById(R.id.tuNomeUsuario)
        private val codigoTextView: TextView = itemView.findViewById(R.id.codigoUsuario)
        private val cargoTextView: TextView = itemView.findViewById(R.id.cargoUsuario)
        private val secretariaTextView: TextView = itemView.findViewById(R.id.secretariaUsuario)

        fun bind(usuario: FuncionariosLocalModel) {
            nomeTextView.text = usuario.nome
            
            // Mascarar CPF no formato 999.****.999-**
            val cpfMascarado = if (usuario.codigo.length >= 11) {
                val cpf = usuario.codigo.replace(Regex("[^0-9]"), "")
                if (cpf.length >= 11) {
                    "${cpf.substring(0, 3)}.****.${cpf.substring(cpf.length - 3)}-**"
                } else {
                    "999.****.999-**"
                }
            } else {
                "999.****.999-**"
            }
            
            codigoTextView.text = "CPF: $cpfMascarado"
            cargoTextView.text = "Cargo: ${usuario.cargo.ifEmpty { "Não informado" }}"
            secretariaTextView.text = "Secretaria: ${usuario.secretaria.ifEmpty { "Não informado" }}"
            
            itemView.setOnClickListener {
                onClick(usuario)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UsuarioViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_usuarios, parent, false)
        return UsuarioViewHolder(view)
    }

    override fun onBindViewHolder(holder: UsuarioViewHolder, position: Int) {
        holder.bind(usuarios[position])
    }

    override fun getItemCount(): Int = usuarios.size
}
