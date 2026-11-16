package com.ymcchat.app.network

import com.ymcchat.app.data.auth.AuthResponse
import com.ymcchat.app.data.auth.MeResponse
import com.ymcchat.app.data.auth.RegisterRequest
import com.ymcchat.app.data.auth.LoginRequest
import com.ymcchat.app.data.messages.*

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

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

    @GET("conversations")
    suspend fun conversations(
        @Header("Authorization") authorization: String
    ): ConversationsResponse

    @POST("conversations/direct")
    suspend fun openDirect(
        @Header("Authorization") authorization: String,
        @Body request: DirectConversationRequest
    ): DirectConversationResponse

    @GET("conversations/{id}/messages")
    suspend fun messages(
        @Header("Authorization") authorization: String,
        @Path("id") conversationId: String,
        @Query("limit") limit: Int = 100
    ): MessagesResponse

    @POST("conversations/{id}/messages")
    suspend fun sendMessage(
        @Header("Authorization") authorization: String,
        @Path("id") conversationId: String,
        @Body request: SendMessageRequest
    ): SendMessageResponse

    @GET("friends")
    suspend fun friends(
        @Header("Authorization") authorization: String
    ): FriendsResponse

    @GET("friends/search")
    suspend fun searchFriends(
        @Header("Authorization") authorization: String,
        @Query("q") query: String
    ): FriendSearchResponse

    @POST("friends/requests")
    suspend fun addFriend(
        @Header("Authorization") authorization: String,
        @Body request: AddFriendRequest
    ): ApiMessageResponse

    @GET("friends/requests")
    suspend fun friendRequests(
        @Header("Authorization") authorization: String
    ): FriendRequestsResponse

    @POST("friends/requests/{id}/accept")
    suspend fun acceptFriendRequest(
        @Header("Authorization") authorization: String,
        @Path("id") requestId: String
    ): ApiMessageResponse
}
