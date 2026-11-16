package com.ymcchat.app.data.messages

import com.google.gson.annotations.SerializedName

data class ConversationsResponse(
    val conversations: List<ConversationItem>
)

data class ConversationItem(
    val id: String,
    @SerializedName("friend_id") val friendId: String,
    @SerializedName("friend_username") val friendUsername: String,
    @SerializedName("friend_display_name") val friendDisplayName: String?,
    @SerializedName("friend_avatar_url") val friendAvatarUrl: String?,
    @SerializedName("friend_status") val friendStatus: String?,
    @SerializedName("last_message") val lastMessage: String?,
    @SerializedName("last_message_at") val lastMessageAt: String?,
    @SerializedName("last_message_sender_id") val lastMessageSenderId: String?
)

data class DirectConversationRequest(
    val userId: String
)

data class DirectConversationResponse(
    val conversationId: String
)

data class MessagesResponse(
    val messages: List<ChatMessage>
)

data class ChatMessage(
    val id: String,
    @SerializedName("conversation_id") val conversationId: String,
    @SerializedName("sender_id") val senderId: String,
    @SerializedName("sender_username") val senderUsername: String?,
    @SerializedName("sender_display_name") val senderDisplayName: String?,
    val content: String,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("edited_at") val editedAt: String?
)

data class SendMessageRequest(
    val content: String
)

data class SendMessageResponse(
    val message: ChatMessage
)

data class FriendsResponse(
    val friends: List<FriendItem>
)

data class FriendItem(
    val id: String,
    val username: String,
    @SerializedName("display_name") val displayName: String?,
    @SerializedName("avatar_url") val avatarUrl: String?,
    val status: String?,
    @SerializedName("status_message") val statusMessage: String?
)

data class FriendSearchResponse(
    val users: List<FriendSearchUser>
)

data class FriendSearchUser(
    val id: String,
    val username: String,
    @SerializedName("display_name") val displayName: String?,
    @SerializedName("avatar_url") val avatarUrl: String?,
    val bio: String?,
    val status: String?
)

data class FriendRequestsResponse(
    val requests: List<FriendRequestItem>
)

data class FriendRequestItem(
    val id: String,
    val direction: String,
    val username: String,
    @SerializedName("display_name") val displayName: String?,
    @SerializedName("avatar_url") val avatarUrl: String?,
    @SerializedName("user_status") val userStatus: String?
)

data class AddFriendRequest(
    val username: String
)

data class ApiMessageResponse(
    val message: String?
)
