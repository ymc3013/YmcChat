package com.ymcchat.app.data.auth

import com.google.gson.annotations.SerializedName

data class AuthResponse(
    val message: String?,
    val token: String,
    val user: AuthUser
)

data class MeResponse(
    val user: AuthUser
)

data class AuthUser(
    val id: String,
    val username: String,
    val email: String,

    @SerializedName("display_name")
    val displayName: String?,

    @SerializedName("avatar_url")
    val avatarUrl: String?,

    val bio: String?,
    val status: String?,

    @SerializedName("created_at")
    val createdAt: String?
)
