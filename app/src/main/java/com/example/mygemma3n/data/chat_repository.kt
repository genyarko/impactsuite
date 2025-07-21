package com.example.mygemma3n.data

import com.example.mygemma3n.data.local.ChatDao
import com.example.mygemma3n.data.local.entities.ChatMessageEntity
import com.example.mygemma3n.data.local.entities.ChatSessionEntity
import com.example.mygemma3n.feature.chat.ChatMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val chatDao: ChatDao
) {
    fun getAllSessions(): Flow<List<ChatSessionEntity>> = chatDao.getAllSessions()

    suspend fun createNewSession(title: String = "New Chat"): String {
        val sessionId = UUID.randomUUID().toString()
        val session = ChatSessionEntity(
            id = sessionId,
            title = title,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        chatDao.insertSession(session)
        return sessionId
    }

    suspend fun updateSessionTitle(sessionId: String, title: String) {
        chatDao.getSession(sessionId)?.let { session ->
            chatDao.updateSession(session.copy(title = title))
        }
    }

    suspend fun updateSessionLastMessage(sessionId: String, lastMessage: String) {
        chatDao.getSession(sessionId)?.let { session ->
            chatDao.updateSession(
                session.copy(
                    lastMessage = lastMessage,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun getMessagesForSession(sessionId: String): Flow<List<ChatMessage>> {
        return chatDao.getMessagesForSession(sessionId).map { entities ->
            entities.map { entity ->
                if (entity.isUser) {
                    ChatMessage.User(
                        content = entity.content,
                        timestamp = entity.timestamp
                    )
                } else {
                    ChatMessage.AI(
                        content = entity.content,
                        timestamp = entity.timestamp
                    )
                }
            }
        }
    }

    suspend fun addMessage(sessionId: String, message: ChatMessage) {
        val entity = ChatMessageEntity(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            content = message.content,
            isUser = message is ChatMessage.User,
            timestamp = message.timestamp
        )
        chatDao.insertMessage(entity)

        // Update session's last message and timestamp
        updateSessionLastMessage(sessionId, message.content.take(50) + if (message.content.length > 50) "..." else "")
    }

    suspend fun deleteSession(session: ChatSessionEntity) {
        chatDao.deleteSessionWithMessages(session)
    }

    suspend fun getSession(sessionId: String): ChatSessionEntity? {
        return chatDao.getSession(sessionId)
    }
}