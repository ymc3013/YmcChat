package com.ymcchat.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ymcchat.app.data.messages.ChatMessage
import com.ymcchat.app.data.messages.ConversationItem
import com.ymcchat.app.data.messages.FriendItem
import com.ymcchat.app.repository.MessagesRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun MessagesScreen(
    token: String,
    palette: AppPalette,
    onOpenConversation: (String, String) -> Unit
) {
    val repository = remember { MessagesRepository() }
    val scope = rememberCoroutineScope()
    var conversations by remember { mutableStateOf<List<ConversationItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun refresh() {
        repository.conversations(token)
            .onSuccess { conversations = it; error = null }
            .onFailure { error = it.message ?: "Unable to load conversations." }
        loading = false
    }

    LaunchedEffect(token) { refresh() }

    Column(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Your messages", color = palette.text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("Your private conversations", color = palette.muted, fontSize = 14.sp)
            }
            TextButton(onClick = { scope.launch { refresh() } }) {
                Text("Refresh", color = palette.accent)
            }
        }
        when {
            loading -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = palette.accent)
            }
            error != null && conversations.isEmpty() -> EmptyState(
                title = "Couldn't load messages",
                message = error ?: "",
                palette = palette,
                action = { scope.launch { loading = true; refresh() } }
            )
            conversations.isEmpty() -> EmptyState(
                title = "No conversations yet",
                message = "Open the Friends tab and start a conversation with someone.",
                palette = palette
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                items(conversations, key = { it.id }) { conversation ->
                    ConversationRow(conversation, palette) {
                        onOpenConversation(
                            conversation.id,
                            conversation.friendDisplayName?.takeIf { it.isNotBlank() }
                                ?: conversation.friendUsername
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FriendsScreen(
    token: String,
    palette: AppPalette,
    onOpenConversation: (String, String) -> Unit
) {
    val repository = remember { MessagesRepository() }
    val scope = rememberCoroutineScope()
    var friends by remember { mutableStateOf<List<FriendItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var openingFriend by remember { mutableStateOf<String?>(null) }

    suspend fun refresh() {
        repository.friends(token)
            .onSuccess { friends = it; error = null }
            .onFailure { error = it.message ?: "Unable to load friends." }
        loading = false
    }

    LaunchedEffect(token) { refresh() }

    Column(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column {
            Text("Your friends", color = palette.text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("Start a private conversation", color = palette.muted, fontSize = 14.sp)
        }
        when {
            loading -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = palette.accent)
            }
            error != null && friends.isEmpty() -> EmptyState(
                title = "Couldn't load friends",
                message = error ?: "",
                palette = palette,
                action = { scope.launch { loading = true; refresh() } }
            )
            friends.isEmpty() -> EmptyState(
                title = "No friends yet",
                message = "Accepted friends will appear here. You can then message them privately.",
                palette = palette
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                items(friends, key = { it.id }) { friend ->
                    FriendRow(friend, palette, openingFriend == friend.id) {
                        if (openingFriend == null) {
                            openingFriend = friend.id
                            scope.launch {
                                repository.openDirect(token, friend.id)
                                    .onSuccess { conversationId ->
                                        onOpenConversation(
                                            conversationId,
                                            friend.displayName?.takeIf { it.isNotBlank() } ?: friend.username
                                        )
                                    }
                                    .onFailure { error = it.message ?: "Unable to open conversation." }
                                openingFriend = null
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ConversationScreen(
    token: String,
    conversationId: String,
    title: String,
    currentUserId: String,
    palette: AppPalette,
    onBack: () -> Unit
) {
    val repository = remember { MessagesRepository() }
    val scope = rememberCoroutineScope()
    var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var draft by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun refresh() {
        repository.messages(token, conversationId)
            .onSuccess { messages = it; error = null }
            .onFailure { error = it.message ?: "Unable to load messages." }
        loading = false
    }

    LaunchedEffect(token, conversationId) {
        refresh()
        while (true) {
            delay(5000)
            refresh()
        }
    }

    Column(Modifier.fillMaxSize().background(palette.background)) {
        Row(
            Modifier.fillMaxWidth().background(palette.surface).padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = palette.text)
            }
            Column(Modifier.weight(1f)) {
                Text(title, color = palette.text, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text("Direct message", color = palette.muted, fontSize = 12.sp)
            }
        }
        HorizontalDivider(color = palette.border)

        if (loading && messages.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = palette.accent)
            }
        } else if (error != null && messages.isEmpty()) {
            EmptyState(
                title = "Couldn't load this conversation",
                message = error ?: "",
                palette = palette,
                action = { scope.launch { loading = true; refresh() } }
            )
        } else if (messages.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("No messages yet. Say hello!", color = palette.muted)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                reverseLayout = false
            ) {
                items(messages, key = { it.id }) { message ->
                    MessageBubble(message, message.senderId == currentUserId, palette)
                }
            }
        }

        if (error != null && messages.isNotEmpty()) {
            Text(error ?: "", color = palette.accent, modifier = Modifier.padding(horizontal = 14.dp))
        }

        Row(
            Modifier.fillMaxWidth().background(palette.surface).padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { if (it.length <= 4000) draft = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Write a message...") },
                maxLines = 4,
                shape = RoundedCornerShape(22.dp)
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                enabled = draft.isNotBlank() && !sending,
                onClick = {
                    val content = draft.trim()
                    if (content.isNotEmpty() && !sending) {
                        sending = true
                        scope.launch {
                            repository.sendMessage(token, conversationId, content)
                                .onSuccess {
                                    draft = ""
                                    refresh()
                                }
                                .onFailure { error = it.message ?: "Message could not be sent." }
                            sending = false
                        }
                    }
                }
            ) {
                if (sending) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = palette.accent, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Send, contentDescription = "Send message", tint = palette.accent)
                }
            }
        }
    }
}

@Composable
private fun ConversationRow(
    conversation: ConversationItem,
    palette: AppPalette,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().background(palette.surface, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(conversation.friendDisplayName ?: conversation.friendUsername, conversation.friendStatus, palette)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                conversation.friendDisplayName?.takeIf { it.isNotBlank() } ?: conversation.friendUsername,
                color = palette.text,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                conversation.lastMessage?.takeIf { it.isNotBlank() } ?: "No messages yet",
                color = palette.muted,
                fontSize = 13.sp,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun FriendRow(
    friend: FriendItem,
    palette: AppPalette,
    opening: Boolean,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().background(palette.surface, RoundedCornerShape(16.dp))
            .clickable(enabled = !opening, onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(friend.displayName ?: friend.username, friend.status, palette)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(friend.displayName?.takeIf { it.isNotBlank() } ?: friend.username, color = palette.text, fontWeight = FontWeight.SemiBold)
            Text(
                if (friend.status.equals("online", true)) "Online" else "Offline",
                color = palette.muted,
                fontSize = 12.sp
            )
        }
        if (opening) CircularProgressIndicator(Modifier.size(20.dp), color = palette.accent, strokeWidth = 2.dp)
        else Icon(Icons.Default.Chat, contentDescription = "Message", tint = palette.accent)
    }
}

@Composable
private fun MessageBubble(message: ChatMessage, mine: Boolean, palette: AppPalette) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start
    ) {
        Column(
            Modifier.widthIn(max = 300.dp)
                .background(
                    if (mine) palette.accent.copy(alpha = 0.18f) else palette.surface,
                    RoundedCornerShape(16.dp)
                )
                .padding(horizontal = 13.dp, vertical = 9.dp)
        ) {
            if (!mine) {
                Text(
                    message.senderDisplayName ?: message.senderUsername ?: "User",
                    color = palette.accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(message.content, color = palette.text, fontSize = 14.sp)
        }
    }
}

@Composable
private fun Avatar(label: String, status: String?, palette: AppPalette) {
    Box {
        Box(
            Modifier.size(46.dp).background(palette.surfaceAlt, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(label.firstOrNull()?.uppercase() ?: "?", color = palette.text, fontWeight = FontWeight.Bold)
        }
        Box(
            Modifier.align(Alignment.BottomEnd).size(11.dp)
                .background(
                    if (status.equals("online", true)) Color(0xFF36C875) else palette.muted,
                    CircleShape
                )
        )
    }
}

@Composable
private fun EmptyState(
    title: String,
    message: String,
    palette: AppPalette,
    action: (() -> Unit)? = null
) {
    Column(
        Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Icons.Default.Person, contentDescription = null, tint = palette.muted, modifier = Modifier.size(34.dp))
        Text(title, color = palette.text, fontWeight = FontWeight.Bold)
        Text(message, color = palette.muted, fontSize = 13.sp)
        if (action != null) TextButton(onClick = action) { Text("Try again", color = palette.accent) }
    }
}
