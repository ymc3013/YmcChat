package com.ymcchat.app.repository

import com.ymcchat.app.data.auth.AuthResponse
import com.ymcchat.app.data.auth.RegisterRequest
import com.ymcchat.app.network.ApiClient
import java.io.IOException

class AuthRepository {

    suspend fun register(
        username: String,
        email: String,
        password: String
    ): Result<AuthResponse> {

        return try {
            val response = ApiClient.service.register(
                RegisterRequest(
                    username = username,
                    email = email,
                    password = password
                )
            )

            Result.success(response)

        } catch (error: retrofit2.HttpException) {
            val message = when (error.code()) {
                409 -> "Ce nom d'utilisateur ou cet email est déjà utilisé."
                400 -> "Les informations envoyées sont invalides."
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
