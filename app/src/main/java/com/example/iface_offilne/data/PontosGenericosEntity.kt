package com.example.iface_offilne.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "pontos_genericos")
data class PontosGenericosEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val funcionarioId: String,
    val funcionarioNome: String,
    val funcionarioMatricula: String = "",
    val funcionarioCpf: String = "",
    val funcionarioCargo: String = "",
    val funcionarioSecretaria: String = "",
    val funcionarioLotacao: String = "",
    val dataHora: Long = System.currentTimeMillis(), // Timestamp em milissegundos
    val tipoPonto: String, // "ENTRADA" ou "SAIDA"
    val latitude: Double? = null,
    val longitude: Double? = null,
    val observacao: String? = null,
    val fotoBase64: String? = null, // Foto da batida em base64
    val synced: Boolean = false // Para sincronização com servidor
) 