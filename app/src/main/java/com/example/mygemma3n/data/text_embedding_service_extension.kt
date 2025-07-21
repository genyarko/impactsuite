package com.example.mygemma3n.data

import kotlinx.coroutines.*
import javax.inject.Inject
import javax.inject.Singleton

// Extension for TextEmbeddingService to add batch processing
@Singleton
class TextEmbeddingServiceExtensions @Inject constructor(
    private val textEmbeddingService: TextEmbeddingService
) {

    /**
     * Process embeddings in batches for better performance
     * @param texts List of texts to embed
     * @param batchSize Number of texts to process in parallel
     * @return List of embeddings in the same order as input texts
     */
    suspend fun embedBatch(
        texts: List<String>,
        batchSize: Int = 10
    ): List<FloatArray> = coroutineScope {
        texts.chunked(batchSize)
            .flatMap { batch ->
                batch.map { text ->
                    async(Dispatchers.Default) {
                        textEmbeddingService.embed(text)
                    }
                }.awaitAll()
            }
    }

    /**
     * Process embeddings with caching to avoid re-computing
     */
    private val embeddingCache = mutableMapOf<String, FloatArray>()

    suspend fun embedWithCache(text: String): FloatArray {
        return embeddingCache.getOrPut(text) {
            textEmbeddingService.embed(text)
        }
    }

    fun clearCache() {
        embeddingCache.clear()
    }

    // Keep cache size under control
    fun trimCache(maxSize: Int = 1000) {
        if (embeddingCache.size > maxSize) {
            val toRemove = embeddingCache.size - maxSize
            embeddingCache.keys.take(toRemove).forEach {
                embeddingCache.remove(it)
            }
        }
    }
}