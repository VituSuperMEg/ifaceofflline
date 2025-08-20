package com.example.iface_offilne.util

import android.content.Context
import com.example.iface_offilne.data.AppDatabase
import com.example.iface_offilne.data.ConfiguracoesEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ConfiguracoesManager {
    
    // Localização ID
    suspend fun getLocalizacaoId(context: Context): String {
        return withContext(Dispatchers.IO) {
            val database = AppDatabase.getInstance(context)
            val configuracoes = database.configuracoesDao().getConfiguracoes()
            configuracoes?.localizacaoId ?: ""
        }
    }

    suspend fun setLocalizacaoId(context: Context, localizacaoId: String) {
        withContext(Dispatchers.IO) {
            val database = AppDatabase.getInstance(context)
            val configuracoes = database.configuracoesDao().getConfiguracoes()
            
            if (configuracoes != null) {
                val updatedConfig = configuracoes.copy(localizacaoId = localizacaoId)
                database.configuracoesDao().updateConfiguracoes(updatedConfig)
            } else {
                val newConfig = ConfiguracoesEntity(
                    localizacaoId = localizacaoId,
                    codigoSincronizacao = "",
                    horaSincronizacao = 8,
                    minutoSincronizacao = 0,
                    sincronizacaoAtiva = false,
                    entidadeId = ""
                )
                database.configuracoesDao().insertConfiguracoes(newConfig)
            }
        }
    }

    // Código de Sincronização
    suspend fun getCodigoSincronizacao(context: Context): String {
        return withContext(Dispatchers.IO) {
            val database = AppDatabase.getInstance(context)
            val configuracoes = database.configuracoesDao().getConfiguracoes()
            configuracoes?.codigoSincronizacao ?: ""
        }
    }

    suspend fun setCodigoSincronizacao(context: Context, codigo: String) {
        withContext(Dispatchers.IO) {
            val database = AppDatabase.getInstance(context)
            val configuracoes = database.configuracoesDao().getConfiguracoes()
            
            if (configuracoes != null) {
                val updatedConfig = configuracoes.copy(codigoSincronizacao = codigo)
                database.configuracoesDao().updateConfiguracoes(updatedConfig)
            } else {
                val newConfig = ConfiguracoesEntity(
                    localizacaoId = "",
                    codigoSincronizacao = codigo,
                    horaSincronizacao = 8,
                    minutoSincronizacao = 0,
                    sincronizacaoAtiva = false,
                    entidadeId = ""
                )
                database.configuracoesDao().insertConfiguracoes(newConfig)
            }
        }
    }

    // Horário de Sincronização
    suspend fun getHoraSincronizacao(context: Context): Int {
        return withContext(Dispatchers.IO) {
            val database = AppDatabase.getInstance(context)
            val configuracoes = database.configuracoesDao().getConfiguracoes()
            configuracoes?.horaSincronizacao ?: 8
        }
    }

    suspend fun getMinutoSincronizacao(context: Context): Int {
        return withContext(Dispatchers.IO) {
            val database = AppDatabase.getInstance(context)
            val configuracoes = database.configuracoesDao().getConfiguracoes()
            configuracoes?.minutoSincronizacao ?: 0
        }
    }

    suspend fun setHorarioSincronizacao(context: Context, hora: Int, minuto: Int) {
        withContext(Dispatchers.IO) {
            val database = AppDatabase.getInstance(context)
            val configuracoes = database.configuracoesDao().getConfiguracoes()
            
            if (configuracoes != null) {
                val updatedConfig = configuracoes.copy(
                    horaSincronizacao = hora,
                    minutoSincronizacao = minuto
                )
                database.configuracoesDao().updateConfiguracoes(updatedConfig)
            } else {
                val newConfig = ConfiguracoesEntity(
                    localizacaoId = "",
                    codigoSincronizacao = "",
                    horaSincronizacao = hora,
                    minutoSincronizacao = minuto,
                    sincronizacaoAtiva = false,
                    entidadeId = ""
                )
                database.configuracoesDao().insertConfiguracoes(newConfig)
            }
        }
    }

    // Status da Sincronização
    suspend fun isSincronizacaoAtiva(context: Context): Boolean {
        return withContext(Dispatchers.IO) {
            val database = AppDatabase.getInstance(context)
            val configuracoes = database.configuracoesDao().getConfiguracoes()
            configuracoes?.sincronizacaoAtiva ?: false
        }
    }

    suspend fun setSincronizacaoAtiva(context: Context, ativa: Boolean) {
        withContext(Dispatchers.IO) {
            val database = AppDatabase.getInstance(context)
            val configuracoes = database.configuracoesDao().getConfiguracoes()
            
            if (configuracoes != null) {
                val updatedConfig = configuracoes.copy(sincronizacaoAtiva = ativa)
                database.configuracoesDao().updateConfiguracoes(updatedConfig)
            } else {
                val newConfig = ConfiguracoesEntity(
                    localizacaoId = "",
                    codigoSincronizacao = "",
                    horaSincronizacao = 8,
                    minutoSincronizacao = 0,
                    sincronizacaoAtiva = ativa,
                    entidadeId = ""
                )
                database.configuracoesDao().insertConfiguracoes(newConfig)
            }
        }
    }

    // Verificar se as configurações estão completas
    suspend fun isConfiguracaoCompleta(context: Context): Boolean {
        val localizacaoId = getLocalizacaoId(context)
        val codigoSincronizacao = getCodigoSincronizacao(context)
        return localizacaoId.isNotEmpty() && codigoSincronizacao.isNotEmpty()
    }

    // Limpar todas as configurações
    suspend fun limparConfiguracoes(context: Context) {
        withContext(Dispatchers.IO) {
            val database = AppDatabase.getInstance(context)
            database.configuracoesDao().deleteConfiguracoes()
        }
    }

    // Obter horário formatado
    suspend fun getHorarioFormatado(context: Context): String {
        val hora = getHoraSincronizacao(context)
        val minuto = getMinutoSincronizacao(context)
        return String.format("%02d:%02d", hora, minuto)
    }

    // Intervalo de Sincronização
    suspend fun getIntervaloSincronizacao(context: Context): Int {
        return withContext(Dispatchers.IO) {
            val database = AppDatabase.getInstance(context)
            val configuracoes = database.configuracoesDao().getConfiguracoes()
            configuracoes?.intervaloSincronizacao ?: 24
        }
    }

    suspend fun setIntervaloSincronizacao(context: Context, intervalo: Int) {
        withContext(Dispatchers.IO) {
            val database = AppDatabase.getInstance(context)
            val configuracoes = database.configuracoesDao().getConfiguracoes()
            
            if (configuracoes != null) {
                val updatedConfig = configuracoes.copy(intervaloSincronizacao = intervalo)
                database.configuracoesDao().updateConfiguracoes(updatedConfig)
            } else {
                val newConfig = ConfiguracoesEntity(
                    localizacaoId = "",
                    codigoSincronizacao = "",
                    horaSincronizacao = 8,
                    minutoSincronizacao = 0,
                    sincronizacaoAtiva = false,
                    intervaloSincronizacao = intervalo,
                    entidadeId = ""
                )
                database.configuracoesDao().insertConfiguracoes(newConfig)
            }
        }
    }

    // Salvar todas as configurações de uma vez
    suspend fun salvarConfiguracoes(
        context: Context,
        localizacaoId: String,
        codigoSincronizacao: String,
        hora: Int,
        minuto: Int,
        sincronizacaoAtiva: Boolean,
        intervalo: Int = 24,
        entidadeId: String
    ) {
        withContext(Dispatchers.IO) {
            val database = AppDatabase.getInstance(context)
            val configuracoes = ConfiguracoesEntity(
                localizacaoId = localizacaoId,
                codigoSincronizacao = codigoSincronizacao,
                horaSincronizacao = hora,
                minutoSincronizacao = minuto,
                sincronizacaoAtiva = sincronizacaoAtiva,
                intervaloSincronizacao = intervalo,
                entidadeId = entidadeId
            )
            database.configuracoesDao().insertConfiguracoes(configuracoes)
        }
    }

    // ===== MÉTODOS PARA GERENCIAR ENTIDADE NAS CONFIGURAÇÕES =====
    
    // Obter entidade ID das configurações
    suspend fun getEntidadeId(context: Context): String {
        return withContext(Dispatchers.IO) {
            val database = AppDatabase.getInstance(context)
            val configuracoes = database.configuracoesDao().getConfiguracoes()
            configuracoes?.entidadeId ?: ""
        }
    }
    
    // Salvar entidade ID nas configurações
    suspend fun setEntidadeId(context: Context, entidadeId: String) {
        withContext(Dispatchers.IO) {
            val database = AppDatabase.getInstance(context)
            val configuracoes = database.configuracoesDao().getConfiguracoes()
            
            if (configuracoes != null) {
                val updatedConfig = configuracoes.copy(entidadeId = entidadeId)
                database.configuracoesDao().updateConfiguracoes(updatedConfig)
            } else {
                val newConfig = ConfiguracoesEntity(
                    localizacaoId = "",
                    codigoSincronizacao = "",
                    horaSincronizacao = 8,
                    minutoSincronizacao = 0,
                    sincronizacaoAtiva = false,
                    entidadeId = entidadeId
                )
                database.configuracoesDao().insertConfiguracoes(newConfig)
            }
        }
    }
    
    // Verificar se entidade está configurada
    suspend fun isEntidadeConfigurada(context: Context): Boolean {
        return withContext(Dispatchers.IO) {
            val database = AppDatabase.getInstance(context)
            val configuracoes = database.configuracoesDao().getConfiguracoes()
            val entidadeId = configuracoes?.entidadeId ?: ""
            entidadeId.isNotEmpty()
        }
    }
} 