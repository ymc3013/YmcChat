package com.ymcchat.app.repository

import com.ymcchat.app.data.messages.*
import com.ymcchat.app.network.ApiClient
import retrofit2.HttpException
import java.io.IOException

class MessagesRepository {
    suspend fun conversations(token: String): Result<List<ConversationItem>> =
        execute { ApiClient.service.conversations(auth(token)).conversations }

    suspend fun friends(token: String): Result<List<FriendItem>> =
        execute { ApiClient.service.friends(auth(token)).friends }

    suspend fun openDirect(token: String, userId: String): Result<String> =
        execute {
            ApiClient.service.openDirect(auth(token), DirectConversationRequest(userId)).conversationId
        }

    suspend fun messages(token: String, conversationId: String): Result<List<ChatMessage>> =
        execute {
            ApiClient.service.messages(auth(token), conversationId, 100).messages
        }

    suspend fun sendMessage(
        token: String,
        conversationId: String,
        content: String
    ): Result<ChatMessage> =
        execute {
            ApiClient.service.sendMessage(
                auth(token),
                conversationId,
                SendMessageRequest(content)
            ).message
        }

    private fun auth(token: String) = "Bearer $token"

    private suspend fun <T> execute(action: suspend () -> T): Result<T> {
        return try {
            Result.success(action())
        } catch (error: HttpException) {
            val message = when (error.code()) {
                401 -> "Your session has expired. Please sign in again."
                403 -> "You do not have permission to do that."
                404 -> "This conversation or user could not be found."
                500 -> "The server encountered an internal error."
                else -> "Server error: ${error.code()}"
            }
            Result.failure(Exception(message))
        } catch (_: IOException) {
            Result.failure(Exception("Unable to reach YmcChat. Check your connection."))
        } catch (error: Exception) {
            Result.failure(Exception(error.message ?: "Something went wrong."))
        }
    }
}
