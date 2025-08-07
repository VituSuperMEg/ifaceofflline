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

}