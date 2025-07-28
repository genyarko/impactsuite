package com.example.mygemma3n.feature.tutor.optimization

import com.example.mygemma3n.feature.tutor.streaming.QuestionHasher
import com.example.mygemma3n.feature.tutor.streaming.ResponseCache
import com.example.mygemma3n.feature.tutor.streaming.ResponseStreamManager
import com.example.mygemma3n.shared_utilities.OfflineRAG
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AIOptimizationService @Inject constructor(
    private val responseCache: ResponseCache,
    private val streamManager: ResponseStreamManager
) {
    
    data class OptimizationContext(
        val question: String,
        val subject: OfflineRAG.Subject,
        val gradeLevel: Int,
        val conversationHistory: List<String> = emptyList(),
        val maxTokens: Int = 200,
        val enableStreaming: Boolean = true,
        val enableCaching: Boolean = true
    )
    
    data class OptimizedResponse(
        val content: String,
        val metadata: ResponseStreamManager.ResponseMetadata,
        val optimizationApplied: List<String> = emptyList()
    )
    
    /**
     * Optimized response generation with multiple strategies
     */
    fun generateOptimizedResponse(
        context: OptimizationContext,
        generateResponse: suspend (String, Int) -> String
    ): Flow<ResponseStreamManager.StreamingResponse> = flow {
        val startTime = System.currentTimeMillis()
        val messageId = java.util.UUID.randomUUID().toString()
        val optimizations = mutableListOf<String>()
        
        try {
            // 1. Check cache first
            if (context.enableCaching) {
                val questionHash = QuestionHasher.hash(
                    context.question, 
                    context.subject.name, 
                    context.gradeLevel
                )
                
                responseCache.getCachedResponse(questionHash)?.let { cached ->
                    optimizations.add("cache_hit")
                    emit(
                        ResponseStreamManager.StreamingResponse(
                            messageId = messageId,
                            partialContent = cached.content,
                            isComplete = true,
                            metadata = cached.metadata.copy(
                                responseTimeMs = System.currentTimeMillis() - startTime,
                                isFromCache = true
                            )
                        )
                    )
                    return@flow
                }
            }
            
            // 2. Apply context optimization
            val optimizedPrompt = optimizePrompt(context)
            optimizations.add("prompt_optimization")
            
            // 3. Apply token optimization
            val optimizedTokens = optimizeTokenAllocation(context)
            optimizations.add("token_optimization")
            
            // 4. Generate response with streaming
            if (context.enableStreaming) {
                streamManager.startStream(messageId)
                optimizations.add("streaming_enabled")
                
                // Simulate streaming response generation
                val fullResponse = generateResponse(optimizedPrompt, optimizedTokens)
                
                // Stream response in chunks
                val chunks = fullResponse.chunked(20) // 20 characters per chunk
                var partialContent = ""
                
                chunks.forEachIndexed { index, chunk ->
                    partialContent += chunk
                    
                    emit(
                        ResponseStreamManager.StreamingResponse(
                            messageId = messageId,
                            partialContent = partialContent,
                            isComplete = index == chunks.size - 1,
                            metadata = if (index == chunks.size - 1) {
                                ResponseStreamManager.ResponseMetadata(
                                    tokensGenerated = fullResponse.split(" ").size,
                                    responseTimeMs = System.currentTimeMillis() - startTime,
                                    confidence = calculateConfidence(fullResponse),
                                    isFromCache = false
                                )
                            } else null
                        )
                    )
                    
                    // Add realistic delay between chunks
                    delay(50)
                }
                
                // Cache the response
                if (context.enableCaching) {
                    val questionHash = QuestionHasher.hash(
                        context.question, 
                        context.subject.name, 
                        context.gradeLevel
                    )
                    responseCache.cacheResponse(
                        questionHash,
                        fullResponse,
                        ResponseStreamManager.ResponseMetadata(
                            tokensGenerated = fullResponse.split(" ").size,
                            responseTimeMs = System.currentTimeMillis() - startTime,
                            confidence = calculateConfidence(fullResponse),
                            isFromCache = false
                        )
                    )
                    optimizations.add("response_cached")
                }
                
            } else {
                // Non-streaming response
                val response = generateResponse(optimizedPrompt, optimizedTokens)
                
                emit(
                    ResponseStreamManager.StreamingResponse(
                        messageId = messageId,
                        partialContent = response,
                        isComplete = true,
                        metadata = ResponseStreamManager.ResponseMetadata(
                            tokensGenerated = response.split(" ").size,
                            responseTimeMs = System.currentTimeMillis() - startTime,
                            confidence = calculateConfidence(response),
                            isFromCache = false
                        )
                    )
                )
            }
            
            Timber.d("Response generated with optimizations: $optimizations")
            
        } catch (e: Exception) {
            Timber.e(e, "Error in optimized response generation")
            streamManager.cancelStream(messageId)
            throw e
        }
    }.flowOn(Dispatchers.IO)
    
    private fun optimizePrompt(context: OptimizationContext): String {
        val basePrompt = context.question
        
        // Add context-specific optimizations
        val optimizations = mutableListOf<String>()
        
        // Grade-level optimization
        when (context.gradeLevel) {
            in 1..3 -> optimizations.add("Use simple words and short sentences.")
            in 4..6 -> optimizations.add("Explain with concrete examples.")
            in 7..9 -> optimizations.add("Include reasoning steps.")
            in 10..12 -> optimizations.add("Provide comprehensive analysis.")
        }
        
        // Subject-specific optimization
        when (context.subject) {
            OfflineRAG.Subject.MATHEMATICS -> optimizations.add("Show step-by-step calculations.")
            OfflineRAG.Subject.SCIENCE -> optimizations.add("Explain the scientific method.")
            OfflineRAG.Subject.ENGLISH -> optimizations.add("Focus on grammar and writing techniques.")
            OfflineRAG.Subject.HISTORY -> optimizations.add("Provide historical context and dates.")
            OfflineRAG.Subject.GEOGRAPHY -> optimizations.add("Include geographical relationships.")
            OfflineRAG.Subject.ECONOMICS -> optimizations.add("Explain economic principles clearly.")
            else -> optimizations.add("Provide clear explanations.")
        }
        
        // Context history optimization
        if (context.conversationHistory.isNotEmpty()) {
            optimizations.add("Build on previous discussion points.")
        }
        
        return if (optimizations.isNotEmpty()) {
            "$basePrompt\n\nInstructions: ${optimizations.joinToString(" ")}"
        } else {
            basePrompt
        }
    }
    
    private fun optimizeTokenAllocation(context: OptimizationContext): Int {
        var baseTokens = context.maxTokens
        
        // Adjust based on grade level
        baseTokens = when (context.gradeLevel) {
            in 1..3 -> (baseTokens * 0.7).toInt() // Shorter responses for younger students
            in 4..6 -> (baseTokens * 0.85).toInt()
            in 7..9 -> baseTokens
            in 10..12 -> (baseTokens * 1.2).toInt() // More detailed responses for older students
            else -> baseTokens
        }
        
        // Adjust based on subject complexity
        baseTokens = when (context.subject) {
            OfflineRAG.Subject.MATHEMATICS -> (baseTokens * 1.1).toInt() // Need space for step-by-step
            OfflineRAG.Subject.SCIENCE -> (baseTokens * 1.15).toInt() // Complex explanations
            OfflineRAG.Subject.ECONOMICS -> (baseTokens * 1.1).toInt()
            else -> baseTokens
        }
        
        // Consider conversation history
        if (context.conversationHistory.size > 5) {
            baseTokens = (baseTokens * 0.9).toInt() // Shorter responses in long conversations
        }
        
        return baseTokens.coerceAtLeast(50).coerceAtMost(500)
    }
    
    private fun calculateConfidence(response: String): Float {
        // Simple confidence calculation based on response characteristics
        var confidence = 0.5f
        
        // Length-based confidence
        when (response.length) {
            in 50..200 -> confidence += 0.2f
            in 200..400 -> confidence += 0.3f
            in 400..600 -> confidence += 0.2f
            else -> confidence += 0.1f
        }
        
        // Structure-based confidence
        if (response.contains("because") || response.contains("therefore")) {
            confidence += 0.1f // Has reasoning
        }
        
        if (response.split(".").size > 2) {
            confidence += 0.1f // Multiple sentences
        }
        
        if (response.contains(Regex("\\d+"))) {
            confidence += 0.1f // Contains numbers/facts
        }
        
        return confidence.coerceAtMost(1.0f)
    }
    
    /**
     * Smart context management to reduce token usage
     */
    fun optimizeConversationContext(
        messages: List<String>,
        maxContextLength: Int = 1000
    ): List<String> {
        if (messages.isEmpty()) return emptyList()
        
        // Start with the most recent messages
        val reversedMessages = messages.reversed()
        val optimizedContext = mutableListOf<String>()
        var currentLength = 0
        
        for (message in reversedMessages) {
            val messageLength = message.length
            if (currentLength + messageLength <= maxContextLength) {
                optimizedContext.add(0, message) // Add to beginning to maintain order
                currentLength += messageLength
            } else {
                // Try to include a truncated version of this message
                val remainingSpace = maxContextLength - currentLength
                if (remainingSpace > 50) {
                    val truncated = message.take(remainingSpace - 3) + "..."
                    optimizedContext.add(0, truncated)
                }
                break
            }
        }
        
        Timber.d("Context optimization: ${messages.size} -> ${optimizedContext.size} messages, ${messages.sumOf { it.length }} -> $currentLength chars")
        
        return optimizedContext
    }
    
    fun getOptimizationStats(): Map<String, Any> {
        return mapOf(
            "cacheStats" to responseCache.getCacheStats(),
            "activeStreams" to streamManager.getActiveStreams().size
        )
    }
}