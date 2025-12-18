package com.example

import io.swiftify.annotations.SwiftAsync
import io.swiftify.annotations.SwiftFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlin.time.TimeSource

// Multiplatform time helper
private val timeSource = TimeSource.Monotonic
private val startMark = timeSource.markNow()
private fun currentTimeMillis(): Long = startMark.elapsedNow().inWholeMilliseconds

/**
 * Real-world example: Chat/Messaging Repository
 * Common patterns for real-time messaging apps
 */
class ChatRepository {

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    private val _unreadCount = MutableStateFlow(0)

    /**
     * Connection state for showing online/offline status
     */
    @SwiftFlow
    val connectionState: StateFlow<ConnectionState> = _connectionState

    /**
     * Unread message count for badges
     */
    @SwiftFlow
    val unreadCount: StateFlow<Int> = _unreadCount

    // MARK: - Connection Management

    /**
     * Connect to chat server
     */
    @SwiftAsync
    suspend fun connect(): ConnectionState {
        _connectionState.value = ConnectionState.CONNECTING
        delay(500) // Simulate connection
        _connectionState.value = ConnectionState.CONNECTED
        return ConnectionState.CONNECTED
    }

    /**
     * Disconnect from chat server
     */
    @SwiftAsync
    suspend fun disconnect() {
        delay(100)
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    // MARK: - Conversations

    /**
     * Get all conversations with pagination
     */
    @SwiftAsync
    suspend fun getConversations(
        page: Int = 1,
        includeArchived: Boolean = false
    ): List<Conversation> {
        delay(200)
        return listOf(
            Conversation(
                id = "conv_1",
                participantName = "John Doe",
                lastMessage = "Hey, how are you?",
                lastMessageTime = currentTimeMillis() - 60000,
                unreadCount = 2,
                isOnline = true
            ),
            Conversation(
                id = "conv_2",
                participantName = "Jane Smith",
                lastMessage = "See you tomorrow!",
                lastMessageTime = currentTimeMillis() - 3600000,
                unreadCount = 0,
                isOnline = false
            ),
            Conversation(
                id = "conv_3",
                participantName = "Team Chat",
                lastMessage = "Meeting at 3pm",
                lastMessageTime = currentTimeMillis() - 7200000,
                unreadCount = 5,
                isOnline = true
            )
        )
    }

    /**
     * Get messages for a conversation with pagination
     */
    @SwiftAsync
    suspend fun getMessages(
        conversationId: String,
        beforeMessageId: String? = null,
        limit: Int = 50
    ): MessagePage {
        delay(150)
        val messages = (1..limit).map { i ->
            Message(
                id = "msg_${conversationId}_$i",
                conversationId = conversationId,
                senderId = if (i % 2 == 0) "me" else "other",
                content = "Message $i in conversation $conversationId",
                timestamp = currentTimeMillis() - (i * 60000L),
                status = if (i % 2 == 0) MessageStatus.READ else MessageStatus.DELIVERED
            )
        }
        return MessagePage(
            messages = messages,
            hasMore = true,
            oldestMessageId = messages.lastOrNull()?.id
        )
    }

    // MARK: - Sending Messages

    /**
     * Send a text message
     */
    @SwiftAsync
    suspend fun sendMessage(
        conversationId: String,
        content: String
    ): Message {
        delay(100)
        return Message(
            id = "msg_${currentTimeMillis()}",
            conversationId = conversationId,
            senderId = "me",
            content = content,
            timestamp = currentTimeMillis(),
            status = MessageStatus.SENT
        )
    }

    /**
     * Send a message with attachment
     */
    @SwiftAsync
    suspend fun sendMessageWithAttachment(
        conversationId: String,
        content: String,
        attachmentType: AttachmentType,
        attachmentUrl: String
    ): Message {
        delay(300) // Longer for upload simulation
        return Message(
            id = "msg_${currentTimeMillis()}",
            conversationId = conversationId,
            senderId = "me",
            content = content,
            timestamp = currentTimeMillis(),
            status = MessageStatus.SENT,
            attachment = Attachment(attachmentType, attachmentUrl)
        )
    }

    /**
     * Mark messages as read
     */
    @SwiftAsync
    suspend fun markAsRead(conversationId: String) {
        delay(50)
        _unreadCount.value = (_unreadCount.value - 1).coerceAtLeast(0)
    }

    // MARK: - Real-time Streams

    /**
     * Watch for new messages in a conversation - essential for chat apps
     */
    @SwiftFlow
    fun watchMessages(conversationId: String): Flow<Message> = flow {
        var messageCount = 0
        while (true) {
            delay(3000) // New message every 3 seconds for demo
            messageCount++
            emit(Message(
                id = "realtime_msg_$messageCount",
                conversationId = conversationId,
                senderId = "other",
                content = "New incoming message #$messageCount",
                timestamp = currentTimeMillis(),
                status = MessageStatus.DELIVERED
            ))
            _unreadCount.value++
        }
    }

    /**
     * Watch typing indicators
     */
    @SwiftFlow
    fun watchTypingStatus(conversationId: String): Flow<TypingStatus> = flow {
        while (true) {
            delay(5000)
            emit(TypingStatus(conversationId, "John", true))
            delay(2000)
            emit(TypingStatus(conversationId, "John", false))
        }
    }

    /**
     * Watch online status of users
     */
    @SwiftFlow
    fun watchOnlineStatus(userIds: List<String>): Flow<OnlineStatus> = flow {
        userIds.forEach { userId ->
            emit(OnlineStatus(userId, true, currentTimeMillis()))
        }
        delay(10000)
        userIds.forEach { userId ->
            emit(OnlineStatus(userId, false, currentTimeMillis()))
        }
    }
}

// MARK: - Data Models

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING
}

data class Conversation(
    val id: String,
    val participantName: String,
    val lastMessage: String,
    val lastMessageTime: Long,
    val unreadCount: Int,
    val isOnline: Boolean
)

data class Message(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val content: String,
    val timestamp: Long,
    val status: MessageStatus,
    val attachment: Attachment? = null
)

data class MessagePage(
    val messages: List<Message>,
    val hasMore: Boolean,
    val oldestMessageId: String?
)

enum class MessageStatus {
    SENDING,
    SENT,
    DELIVERED,
    READ,
    FAILED
}

enum class AttachmentType {
    IMAGE,
    VIDEO,
    AUDIO,
    FILE
}

data class Attachment(
    val type: AttachmentType,
    val url: String
)

data class TypingStatus(
    val conversationId: String,
    val userName: String,
    val isTyping: Boolean
)

data class OnlineStatus(
    val userId: String,
    val isOnline: Boolean,
    val lastSeen: Long
)
