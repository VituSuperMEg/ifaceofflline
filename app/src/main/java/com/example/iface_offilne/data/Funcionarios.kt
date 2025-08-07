package com.example.iface_offilne.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "funcionarios")
data class FuncionariosEntity (
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val codigo: String,
    val nome: String,
    val ativo: Int = 1 // Que dizer que ta ativo
)

