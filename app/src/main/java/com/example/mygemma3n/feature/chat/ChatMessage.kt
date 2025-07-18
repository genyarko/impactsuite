package com.example.mygemma3n.feature.chat

/** Simple chat message model */
sealed class ChatMessage {
    abstract val timestamp: Long
    abstract val content: String

    data class User(
        override val content: String,
        override val timestamp: Long = System.currentTimeMillis()
    ) : ChatMessage()

    data class AI(
        override val content: String,
        override val timestamp: Long = System.currentTimeMillis()
    ) : ChatMessage()
}
