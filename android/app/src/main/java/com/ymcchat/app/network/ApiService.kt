package com.ymcchat.app.network

import com.ymcchat.app.data.auth.AuthResponse
import com.ymcchat.app.data.auth.MeResponse
import com.ymcchat.app.data.auth.RegisterRequest
import com.ymcchat.app.data.auth.LoginRequest
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

interface ApiService {

    @POST("auth/register")
    suspend fun register(
        @Body request: RegisterRequest
    ): AuthResponse

    @POST("auth/login")
    suspend fun login(
        @Body request: LoginRequest
    ): AuthResponse

    @GET("auth/me")
    suspend fun me(
        @Header("Authorization") authorization: String
    ): MeResponse
}