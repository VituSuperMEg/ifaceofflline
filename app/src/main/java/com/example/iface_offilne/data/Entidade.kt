package com.example.iface_offilne.data


data class Entidade(
    val id: String,
    val name: String
)

data class EntidadeResponse(
    val data: List<Entidade>
)