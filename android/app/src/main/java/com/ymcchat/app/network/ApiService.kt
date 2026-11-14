package com.ymcchat.app.network

import com.ymcchat.app.data.auth.AuthResponse
import com.ymcchat.app.data.auth.RegisterRequest
import retrofit2.http.Body
import retrofit2.http.POST

interface ApiService {

    @POST("auth/register")
    suspend fun register(
        @Body request: RegisterRequest
    ): AuthResponse
}
