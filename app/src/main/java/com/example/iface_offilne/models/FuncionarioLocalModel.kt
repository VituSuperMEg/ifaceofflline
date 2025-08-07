package com.example.iface_offilne.models

import java.io.Serializable


data class FuncionariosLocalModel(
    val id: Int,
    val codigo: String,
    val nome: String,
    val ativo: Int = 1
) : Serializable

