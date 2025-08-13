package com.example.iface_offilne.service

data class SincronizacaoResult(
    val sucesso: Boolean,
    val quantidadePontos: Int,
    val duracaoSegundos: Long,
    val mensagem: String
) 