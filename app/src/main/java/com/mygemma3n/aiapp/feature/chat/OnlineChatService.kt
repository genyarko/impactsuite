package com.mygemma3n.aiapp.feature.chat

import com.mygemma3n.aiapp.data.GeminiApiService
import com.mygemma3n.aiapp.data.GeminiApiConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OnlineChatService @Inject constructor(
    private val geminiApiService: GeminiApiService
) {

    suspend fun generateChatResponseOnline(
        userMessage: String,
        conversationHistory: List<ChatMessage> = emptyList(),
        maxTokens: Int = 2048,
        temperature: Float = 0.7f
    ): String = withContext(Dispatchers.IO) {
        
        require(geminiApiService.isInitialized()) { "GeminiApiService not initialized" }
        
        val contextPrompt = buildContextPrompt(userMessage, conversationHistory)
        
        try {
            val response = geminiApiService.generateTextComplete(contextPrompt, "chat")
            return@withContext cleanChatResponse(response)
        } catch (e: Exception) {
            Timber.e(e, "Failed to generate online chat response")
            throw e
        }
    }

    fun generateChatResponseStreamOnline(
        userMessage: String,
        conversationHistory: List<ChatMessage> = emptyList(),
        temperature: Float = 0.7f
    ): Flow<String> = flow {
        
        require(geminiApiService.isInitialized()) { "GeminiApiService not initialized" }
        
        val contextPrompt = buildContextPrompt(userMessage, conversationHistory)
        
        try {
            geminiApiService.streamText(contextPrompt)
                .collect { chunk ->
                    val cleanedChunk = cleanChatResponse(chunk, isStreaming = true)
                    if (cleanedChunk.isNotBlank()) {
                        emit(cleanedChunk)
                    }
                }
        } catch (e: Exception) {
            Timber.e(e, "Failed to generate streaming online chat response")
            throw e
        }
    }.flowOn(Dispatchers.IO)

    private fun buildContextPrompt(
        userMessage: String,
        conversationHistory: List<ChatMessage>
    ): String {
        val contextBuilder = StringBuilder()
        
        // Add system context
        contextBuilder.append("""
            You are a helpful, knowledgeable, and friendly AI assistant. You provide accurate, informative responses while being conversational and engaging.
            
            Guidelines:
            - Be helpful and accurate
            - Keep responses concise but informative
            - Maintain a friendly, conversational tone
            - If you don't know something, say so honestly
            - Provide practical examples when helpful
            
        """.trimIndent())
        
        // Add conversation history for context (last 10 messages to avoid token limits)
        val recentHistory = conversationHistory.takeLast(10)
        if (recentHistory.isNotEmpty()) {
            contextBuilder.append("\n\nConversation history:\n")
            recentHistory.forEach { message ->
                when (message) {
                    is ChatMessage.User -> contextBuilder.append("User: ${message.content}\n")
                    is ChatMessage.AI -> contextBuilder.append("Assistant: ${message.content}\n")
                }
            }
        }
        
        // Add current user message
        contextBuilder.append("\nUser: $userMessage\n")
        contextBuilder.append("Assistant: ")
        
        return contextBuilder.toString()
    }

    private fun cleanChatResponse(response: String, isStreaming: Boolean = false): String {
        var cleaned = response.trim()
        
        // Remove common AI response prefixes
        val prefixesToRemove = listOf(
            "Assistant:",
            "AI:",
            "Response:",
            "Answer:",
            "Reply:"
        )
        
        prefixesToRemove.forEach { prefix ->
            if (cleaned.startsWith(prefix, ignoreCase = true)) {
                cleaned = cleaned.substring(prefix.length).trim()
            }
        }
        
        // For streaming, don't apply end-of-response cleanup
        if (!isStreaming) {
            // Remove common AI response suffixes
            val suffixesToRemove = listOf(
                "Is there anything else I can help you with?",
                "Let me know if you need anything else!",
                "Feel free to ask if you have more questions."
            )
            
            suffixesToRemove.forEach { suffix ->
                if (cleaned.endsWith(suffix, ignoreCase = true)) {
                    cleaned = cleaned.substring(0, cleaned.length - suffix.length).trim()
                }
            }
        }
        
        return cleaned
    }

    suspend fun generateConversationalResponse(
        userMessage: String,
        conversationHistory: List<ChatMessage> = emptyList(),
        personalityType: String = "helpful"
    ): String = withContext(Dispatchers.IO) {
        
        val personalityPrompt = when (personalityType) {
            "friendly" -> "You are a warm, friendly AI assistant who loves to chat and help people. Use a casual, upbeat tone."
            "professional" -> "You are a professional, knowledgeable AI assistant. Provide clear, accurate information in a polite, business-like manner."
            "creative" -> "You are a creative, imaginative AI assistant. Feel free to use analogies, examples, and creative explanations."
            "educational" -> "You are an educational AI tutor. Focus on teaching and explaining concepts clearly with examples."
            else -> "You are a helpful, balanced AI assistant providing accurate and friendly responses."
        }
        
        val contextPrompt = """
            $personalityPrompt
            
            Current conversation:
            ${conversationHistory.takeLast(8).joinToString("\n") { message ->
                when (message) {
                    is ChatMessage.User -> "User: ${message.content}"
                    is ChatMessage.AI -> "Assistant: ${message.content}"
                }
            }}
            
            User: $userMessage
            Assistant:
        """.trimIndent()
        
        try {
            val response = geminiApiService.generateTextComplete(contextPrompt, "chat")
            return@withContext cleanChatResponse(response)
        } catch (e: Exception) {
            Timber.e(e, "Failed to generate conversational response")
            throw e
        }
    }

    suspend fun generateSmartReply(
        userMessage: String,
        conversationHistory: List<ChatMessage> = emptyList()
    ): List<String> = withContext(Dispatchers.IO) {
        
        val replyPrompt = """
            Based on this conversation, suggest 3 brief, natural follow-up responses the user might want to send.
            
            Recent conversation:
            ${conversationHistory.takeLast(5).joinToString("\n") { message ->
                when (message) {
                    is ChatMessage.User -> "User: ${message.content}"
                    is ChatMessage.AI -> "Assistant: ${message.content}"
                }
            }}
            
            User's last message: $userMessage
            
            Provide 3 short, natural follow-up questions or responses the user might want to send next.
            Format as a simple list, one per line, without numbers or bullets.
            Keep each suggestion under 10 words.
        """.trimIndent()
        
        try {
            val response = geminiApiService.generateTextComplete(replyPrompt, "chat")
            return@withContext parseSmartReplies(response)
        } catch (e: Exception) {
            Timber.e(e, "Failed to generate smart replies")
            // Return default suggestions on failure
            return@withContext listOf("Tell me more", "That's interesting", "What else?")
        }
    }

    private fun parseSmartReplies(response: String): List<String> {
        return response.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("-") && !it.matches(Regex("^\\d+\\..*")) }
            .take(3)
            .ifEmpty { 
                listOf("Tell me more", "That's interesting", "What else?") 
            }
    }
}