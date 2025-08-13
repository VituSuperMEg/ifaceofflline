package com.example.iface_offilne.models

import com.example.iface_offilne.data.FuncionariosEntity


data class FuncionariosModel(
    val id: Int,
    val nome: String,
    val nome_mae: String,
    val nome_pai: String,
    val numero_cpf: String,
    val matricula: String,
    val cargo_descricao: String,
    val orgao_descricao: String?,
    val setor_descricao: String?,
    val localizacao_descricao: String?
)

data class FuncionariosResponse (
    val data: List<FuncionariosModel>
)