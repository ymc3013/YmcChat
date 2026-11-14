package com.ymcchat.app.data.auth

data class RegisterRequest(
    val username: String,
    val email: String,
    val password: String
)
