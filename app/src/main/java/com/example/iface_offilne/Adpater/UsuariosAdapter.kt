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

        fun bind(usuario: FuncionariosLocalModel) {
            nomeTextView.text = usuario.nome
            codigoTextView.text = "Código: ${usuario.codigo}"
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
