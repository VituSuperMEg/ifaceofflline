package com.example.iface_offilne.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "configuracoes")
data class ConfiguracoesEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 1, // Sempre será 1, pois só teremos uma configuração
    
    val localizacaoId: String,
    val codigoSincronizacao: String,
    val horaSincronizacao: Int,
    val minutoSincronizacao: Int,
    val sincronizacaoAtiva: Boolean,
    val intervaloSincronizacao: Int = 24
) 