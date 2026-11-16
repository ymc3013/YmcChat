package com.ymcchat.app.repository

import com.ymcchat.app.data.auth.AuthResponse
import com.ymcchat.app.data.auth.AuthUser
import com.ymcchat.app.data.auth.LoginRequest
import com.ymcchat.app.data.auth.RegisterRequest
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
                RegisterRequest(
                    username = username,
                    email = email,
                    password = password
                )
            )
        }
    }

    suspend fun login(
        identifier: String,
        password: String
    ): Result<AuthResponse> {
        return execute {
            ApiClient.service.login(
                LoginRequest(
                    identifier = identifier,
                    password = password
                )
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
                400 -> "Invalid information."
                401 -> "Incorrect username or password."
                409 -> "That username or email is already in use."
                500 -> "The server encountered an internal error."
                else -> "Server error: ${error.code()}"
            }

            Result.failure(Exception(message))

        } catch (error: IOException) {

            Result.failure(
                Exception("Unable to contact the server.")
            )

        } catch (error: Exception) {

            Result.failure(
                Exception(error.message ?: "Something went wrong.")
            )
        }
    }
}
