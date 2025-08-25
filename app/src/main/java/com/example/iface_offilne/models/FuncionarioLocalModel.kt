package com.example.iface_offilne.models

import java.io.Serializable


data class FuncionariosLocalModel(
    val id: Int,
    var codigo: String,
    var nome: String,
    var ativo: Int = 1,
    var cargo: String = "",
    var secretaria: String = ""
) : Serializable

