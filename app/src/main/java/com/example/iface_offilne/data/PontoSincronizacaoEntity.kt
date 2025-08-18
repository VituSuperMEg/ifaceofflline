package com.example.iface_offilne.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pontos_sincronizacao")
data class PontoSincronizacaoEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val funcionarioId: String,
    val funcionarioNome: String,
    val funcionarioMatricula: String = "",
    val funcionarioCpf: String = "",
    val funcionarioCargo: String = "",
    val funcionarioSecretaria: String = "",
    val funcionarioLotacao: String = "",
    val dataHora: String,
    val tipo: String, // "entrada" ou "saida"
    val sincronizado: Boolean = false,
    val dataSincronizacao: String? = null,
    val localizacaoId: String,
    val codigoSincronizacao: String,
    val fotoBase64: String? = null, // Foto da batida em base64
    val latitude: Double? = null, // ✅ NOVA: Latitude da localização
    val longitude: Double? = null // ✅ NOVA: Longitude da localização
) 