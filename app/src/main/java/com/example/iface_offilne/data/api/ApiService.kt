package com.example.iface_offilne.data.api

import com.example.iface_offilne.data.Entidade
import com.example.iface_offilne.data.EntidadeResponse
import com.example.iface_offilne.data.request.EntidadeRequest
import com.example.iface_offilne.models.FuncionariosModel
import com.example.iface_offilne.models.FuncionariosResponse
import com.example.iface_offilne.util.SessionManager
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {

    @POST("/null/ponto/entidades/entidades")
    suspend fun getEntidade(
        @Body request: EntidadeRequest
    ): EntidadeResponse

    @GET("/{entidade}/services/funcionarios/list")
    suspend fun getFuncionarios(
        @Path("entidade") entidade: String,
        @Query("page") page: Int
    ): FuncionariosResponse

    @POST("/{entidade}/services/util/sincronizar-ponto-table")
    suspend fun sincronizarPontos(
        @Path("entidade") entidade: String,
        @Body pontos: List<PontoSyncRequest>
    ): Response<PontoSyncResponse>

    @POST("/{entidade}/services/util/sincronizar-ponto-table")
    suspend fun sincronizarPontosVazio(
        @Path("entidade") entidade: String,
        @Body pontos: List<PontoSyncRequest>
    ): Response<Unit>
    
    // ✅ NOVO: Endpoint com formato completo (configurações + pontos)
    @POST("/{entidade}/services/util/sincronizar-ponto-table")
    suspend fun sincronizarPontosCompleto(
        @Path("entidade") entidade: String,
        @Body request: PontoSyncCompleteRequest
    ): Response<PontoSyncResponse>
    
    @POST("/{entidade}/services/util/sincronizar-ponto-table")
    suspend fun sincronizarPontosCompletoVazio(
        @Path("entidade") entidade: String,
        @Body request: PontoSyncCompleteRequest
    ): Response<Unit>

    @GET("/{entidade}/services/util/test")
    suspend fun testConnection(
        @Path("entidade") entidade: String
    ): Response<SimpleResponse>
}

// ✅ NOVO: Modelo completo para sincronização com configurações no nível raiz
data class PontoSyncCompleteRequest(
    val localizacao_id: String,
    val cod_sincroniza: String,
    val pontos: List<PontoSyncRequest>
)

// Modelos para sincronização de pontos
data class PontoSyncRequest(
    val funcionarioId: String,
    val funcionarioNome: String,
    val dataHora: String, // formato: "dd/MM/yyyy HH:mm:ss"
    val tipoPonto: String, // "ENTRADA" ou "SAIDA"
    val latitude: Double? = null,
    val longitude: Double? = null,
    val observacao: String? = null,
    val fotoBase64: String? = null // Foto da batida em base64
)

data class PontoSyncResponse(
    val success: Boolean,
    val message: String,
    val pontosSincronizados: Int
)

// Modelo de resposta simples para teste
data class SimpleResponse(
    val status: String,
    val message: String
)