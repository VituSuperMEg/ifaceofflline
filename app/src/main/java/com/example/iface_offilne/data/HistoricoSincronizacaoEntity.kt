package com.example.iface_offilne.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "historico_sincronizacao")
data class HistoricoSincronizacaoEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val dataHora: Date,
    val status: String, // "SUCESSO", "ERRO", "EM_ANDAMENTO"
    val mensagem: String? = null,
    val quantidadePontos: Int = 0,
    val duracaoSegundos: Long = 0
) 