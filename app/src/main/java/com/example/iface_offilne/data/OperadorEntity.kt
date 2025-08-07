package com.example.iface_offilne.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "operador")
data class OperadorEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val cpf: String,
    val nome: String,
    val senha: String
)