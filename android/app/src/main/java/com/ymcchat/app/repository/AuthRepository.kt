package com.ymcchat.app.repository

import com.ymcchat.app.data.auth.*
import com.ymcchat.app.network.ApiClient

import retrofit2.HttpException
import java.io.IOException

class AuthRepository {

    suspend fun register(
        username: String,
        email: String,
        password: String
    ): Result<AuthResponse> {
        return execute {
            ApiClient.service.register(
                RegisterRequest(username, email, password)
            )
        }
    }

    suspend fun login(
        identifier: String,
        password: String
    ): Result<AuthResponse> {
        return execute {
            ApiClient.service.login(
                LoginRequest(identifier, password)
            )
        }
    }

    suspend fun me(token: String): Result<AuthUser> {
        return execute {
            ApiClient.service.me("Bearer $token").user
        }
    }

    private suspend fun <T> execute(
        action: suspend () -> T
    ): Result<T> {
        return try {
            Result.success(action())
        } catch (error: HttpException) {
            val message = when (error.code()) {
                400 -> "Informations invalides."
                401 -> "Identifiant ou mot de passe incorrect."
                409 -> "Ce pseudo ou cet email est déjà utilisé."
                else -> "Erreur serveur : ${error.code()}"
            }
            Result.failure(Exception(message))
        } catch (error: IOException) {
            Result.failure(
                Exception("Impossible de contacter le serveur.")
            )
        } catch (error: Exception) {
            Result.failure(
                Exception(error.message ?: "Une erreur est survenue.")
            )
        }
    }
}