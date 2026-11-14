package com.ymcchat.app.data.auth

import com.google.gson.annotations.SerializedName

data class AuthResponse(
    val message: String?,
    val token: String,
    val user: AuthUser
)

data class AuthUser(
    val id: String,
    val username: String,
    val email: String,
    @SerializedName("created_at")
    val createdAt: String?
)
