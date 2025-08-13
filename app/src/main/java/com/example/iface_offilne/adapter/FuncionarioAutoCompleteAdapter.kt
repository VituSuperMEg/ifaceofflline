package com.example.iface_offilne.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Filter
import android.widget.TextView
import com.example.iface_offilne.R
import com.example.iface_offilne.data.FuncionariosEntity

class FuncionarioAutoCompleteAdapter(
    context: Context,
    private val funcionarios: List<FuncionariosEntity>
) : ArrayAdapter<FuncionariosEntity>(context, 0, funcionarios) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.item_funcionario_autocomplete, parent, false)

        val funcionario = getItem(position)
        
        val textNome = view.findViewById<TextView>(R.id.textNome)
        val textCodigo = view.findViewById<TextView>(R.id.textCodigo)
        
        funcionario?.let {
            textNome.text = it.nome
            textCodigo.text = "Código: ${it.codigo}"
        }

        return view
    }

    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val filterResults = FilterResults()
                
                if (constraint == null || constraint.isEmpty()) {
                    filterResults.values = funcionarios
                    filterResults.count = funcionarios.size
                } else {
                    val filterPattern = constraint.toString().lowercase()
                    val filteredList = funcionarios.filter { funcionario ->
                        funcionario.nome.lowercase().contains(filterPattern) ||
                        funcionario.codigo.lowercase().contains(filterPattern)
                    }
                    filterResults.values = filteredList
                    filterResults.count = filteredList.size
                }
                
                return filterResults
            }

            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                clear()
                if (results?.values != null) {
                    addAll(results.values as List<FuncionariosEntity>)
                }
                notifyDataSetChanged()
            }
        }
    }
} 