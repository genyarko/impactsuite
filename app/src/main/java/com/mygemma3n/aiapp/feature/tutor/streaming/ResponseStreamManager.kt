package com.mygemma3n.aiapp.feature.tutor.streaming

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ResponseStreamManager @Inject constructor() {
    
    data class StreamingResponse(
        val messageId: String,
        val partialContent: String,
        val isComplete: Boolean = false,
        val metadata: ResponseMetadata? = null
    )
    
    data class ResponseMetadata(
        val tokensGenerated: Int = 0,
        val responseTimeMs: Long = 0,
        val confidence: Float = 0f,
        val isFromCache: Boolean = false
    )
    
    private val _streamingResponses = MutableSharedFlow<StreamingResponse>(
        replay = 0,
        extraBufferCapacity = 10,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val streamingResponses: Flow<StreamingResponse> = _streamingResponses.asSharedFlow()
    
    private val activeStreams = mutableMapOf<String, StringBuilder>()
    
    fun startStream(messageId: String) {
        activeStreams[messageId] = StringBuilder()
        Timber.d("Started streaming for message: $messageId")
    }
    
    fun appendToStream(messageId: String, chunk: String) {
        val buffer = activeStreams[messageId]
        if (buffer != null) {
            buffer.append(chunk)
            
            // Emit partial response
            _streamingResponses.tryEmit(
                StreamingResponse(
                    messageId = messageId,
                    partialContent = buffer.toString(),
                    isComplete = false
                )
            )
            
            Timber.v("Appended chunk to stream $messageId: length=${buffer.length}")
        } else {
            Timber.w("Attempted to append to non-existent stream: $messageId")
        }
    }
    
    fun completeStream(messageId: String, metadata: ResponseMetadata? = null) {
        val buffer = activeStreams[messageId]
        if (buffer != null) {
            val finalContent = buffer.toString()
            
            // Emit final response
            _streamingResponses.tryEmit(
                StreamingResponse(
                    messageId = messageId,
                    partialContent = finalContent,
                    isComplete = true,
                    metadata = metadata
                )
            )
            
            // Clean up
            activeStreams.remove(messageId)
            Timber.d("Completed stream for message: $messageId, final length: ${finalContent.length}")
        } else {
            Timber.w("Attempted to complete non-existent stream: $messageId")
        }
    }
    
    fun cancelStream(messageId: String) {
        activeStreams.remove(messageId)
        Timber.d("Cancelled stream for message: $messageId")
    }
    
    fun getActiveStreams(): Set<String> = activeStreams.keys.toSet()
    
    fun clearAllStreams() {
        activeStreams.clear()
        Timber.d("Cleared all active streams")
    }
}

/**
 * Response cache for common questions
 */
@Singleton 
class ResponseCache @Inject constructor() {
    
    data class CachedResponse(
        val content: String,
        val timestamp: Long,
        val accessCount: Int,
        val metadata: ResponseStreamManager.ResponseMetadata
    )
    
    private val cache = mutableMapOf<String, CachedResponse>()
    private val maxCacheSize = 100
    private val cacheExpirationTime = 60 * 60 * 1000L // 1 hour
    
    fun getCachedResponse(questionHash: String): CachedResponse? {
        val cached = cache[questionHash]
        if (cached != null) {
            val now = System.currentTimeMillis()
            if (now - cached.timestamp < cacheExpirationTime) {
                // Update access count
                cache[questionHash] = cached.copy(accessCount = cached.accessCount + 1)
                Timber.d("Cache hit for question hash: $questionHash")
                return cached
            } else {
                // Expired, remove
                cache.remove(questionHash)
                Timber.d("Cache expired for question hash: $questionHash")
            }
        }
        return null
    }
    
    fun cacheResponse(questionHash: String, content: String, metadata: ResponseStreamManager.ResponseMetadata) {
        // Evict oldest if cache is full
        if (cache.size >= maxCacheSize) {
            val oldestKey = cache.entries.minByOrNull { it.value.timestamp }?.key
            oldestKey?.let { cache.remove(it) }
        }
        
        cache[questionHash] = CachedResponse(
            content = content,
            timestamp = System.currentTimeMillis(),
            accessCount = 1,
            metadata = metadata.copy(isFromCache = true)
        )
        
        Timber.d("Cached response for question hash: $questionHash, cache size: ${cache.size}")
    }
    
    fun getCacheStats(): Map<String, Any> {
        return mapOf(
            "size" to cache.size,
            "maxSize" to maxCacheSize,
            "totalAccesses" to cache.values.sumOf { it.accessCount },
            "averageAge" to if (cache.isNotEmpty()) {
                val now = System.currentTimeMillis()
                cache.values.map { now - it.timestamp }.average()
            } else 0.0
        )
    }
    
    fun clearCache() {
        cache.clear()
        Timber.d("Response cache cleared")
    }
}

/**
 * Generates hash for questions to enable caching
 */
object QuestionHasher {
    fun hash(question: String, subject: String, gradeLevel: Int): String {
        val normalized = question.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
        
        return "${subject}_${gradeLevel}_${normalized.hashCode()}"
    }
}