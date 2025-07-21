package com.example.mygemma3n.data.local

import androidx.room.*
import com.example.mygemma3n.data.AppDatabase
import com.example.mygemma3n.data.TextEmbeddingServiceExtensions
import com.example.mygemma3n.data.UnifiedGemmaService
import com.example.mygemma3n.data.VectorDatabase
import com.example.mygemma3n.data.VectorEntity
import com.example.mygemma3n.feature.quiz.Subject
import com.example.mygemma3n.shared_utilities.OfflineRAG
import com.example.mygemma3n.shared_utilities.OfflineRAG.Chunk
import com.example.mygemma3n.shared_utilities.OfflineRAG.Companion.MAX_CONTEXT_LENGTH
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

@Singleton
class OptimizedVectorDatabase @Inject constructor(
    private val roomDatabase: AppDatabase
) {
    // In-memory cache for frequently accessed vectors
    private val vectorCache = ConcurrentHashMap<String, VectorDatabase.VectorDocument>()

    // Pre-computed norms for faster cosine similarity
    private val normCache = ConcurrentHashMap<String, Float>()

    // Batch processing for better performance
    suspend fun insertBatchOptimized(
        documents: List<VectorDatabase.VectorDocument>,
        batchSize: Int = 100
    ) = withContext(Dispatchers.IO) {
        documents.chunked(batchSize).forEach { batch ->
            // Pre-compute norms
            batch.forEach { doc ->
                normCache[doc.id] = computeNorm(doc.embedding)
            }

            // Insert in transaction
            roomDatabase.withTransaction {
                val entities = batch.map { doc ->
                    VectorEntity(
                        id = doc.id,
                        content = doc.content,
                        embedding = doc.embedding.toList(),
                        metadata = doc.metadata
                    )
                }
                roomDatabase.vectorDao().insertAll(entities)
            }

            // Update cache
            batch.forEach { doc ->
                vectorCache[doc.id] = doc
            }
            val lsh = LSH(numTables = 10, hashSize = 8, vectorDim = VectorDatabase.DEFAULT_EMBEDDING_DIM)
            batch.forEach { doc ->
                val hashes = lsh.hash(doc.embedding)
                hashes.forEach { hash ->
                    lshIndex.computeIfAbsent(hash) { mutableSetOf() }.add(doc.id)
                }
            }

        }
    }

    private fun getBucketForHash(hash: String): Set<String> {
        return lshIndex[hash] ?: emptySet()
    }

    fun clearLSHIndex() {
        lshIndex.clear()
    }

    fun trimLSHIndex(maxEntries: Int = 10000) {
        if (lshIndex.size > maxEntries) {
            val keysToRemove = lshIndex.keys.take(lshIndex.size - maxEntries)
            keysToRemove.forEach { lshIndex.remove(it) }
        }
    }


    private fun VectorEntity.toDocument(): VectorDatabase.VectorDocument {
        return VectorDatabase.VectorDocument(
            id = this.id,
            content = this.content,
            embedding = this.embedding.toFloatArray(),
            metadata = this.metadata
        )
    }


    // Optimized search with parallel processing
    suspend fun searchOptimized(
        queryEmbedding: FloatArray,
        k: Int = 5,
        filter: Map<String, String>? = null,
        threshold: Float = 0.0f
    ): List<VectorDatabase.SearchResult> = withContext(Dispatchers.Default) {
        val queryNorm = computeNorm(queryEmbedding)

        // Get candidates
        val candidates = getCandidates(filter)

        // Parallel similarity computation
        val results = candidates
            .chunked(100) // Process in chunks
            .map { chunk ->
                async {
                    chunk.mapNotNull { entity ->
                        val doc = entity.toDocument()
                        val score = computeOptimizedCosineSimilarity(
                            queryEmbedding,
                            doc.embedding,
                            queryNorm,
                            normCache[doc.id] ?: computeNorm(doc.embedding)
                        )

                        if (score >= threshold) {
                            VectorDatabase.SearchResult(doc, score)
                        } else null
                    }
                }
            }
            .awaitAll()
            .flatten()
            .sortedByDescending { it.score }
            .take(k)

        results
    }
    private suspend fun getCandidates(
        filter: Map<String, String>? = null
    ): List<VectorEntity> = withContext(Dispatchers.IO) {
        if (filter != null && filter.isNotEmpty()) {
            val pattern = filter.entries.joinToString("%") { "${it.key}:${it.value}" }
            roomDatabase.vectorDao().getByMetadataPattern("%$pattern%")
        } else {
            roomDatabase.vectorDao().getAll()
        }
    }

    // LSH index: hash → set of document IDs
    private val lshIndex: MutableMap<String, MutableSet<String>> = ConcurrentHashMap()


    // Approximate Nearest Neighbor search using LSH
    suspend fun searchWithLSH(
        queryEmbedding: FloatArray,
        k: Int = 5,
        numHashTables: Int = 10,
        hashSize: Int = 8
    ): List<VectorDatabase.SearchResult> = withContext(Dispatchers.Default) {
        // Implement Locality Sensitive Hashing for faster search
        val lsh = LSH(numHashTables, hashSize, queryEmbedding.size)

        // Hash the query
        val queryHashes = lsh.hash(queryEmbedding)

        // Get candidates from similar buckets
        val candidates = mutableSetOf<String>()
        queryHashes.forEach { hash ->
            val bucket = getBucketForHash(hash)
            candidates.addAll(bucket)
        }

        // Refine with exact similarity
        val results = candidates
            .mapNotNull { id ->
                vectorCache[id]?.let { doc ->
                    val score = cosineSimilarity(queryEmbedding, doc.embedding)
                    VectorDatabase.SearchResult(doc, score)
                }
            }
            .sortedByDescending { it.score }
            .take(k)

        results
    }
    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) { "Vectors must have the same size" }

        var dotProduct = 0f
        var normA = 0f
        var normB = 0f

        for (i in a.indices) {
            dotProduct += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }

        val denominator = sqrt(normA) * sqrt(normB)
        return if (denominator > 0f) dotProduct / denominator else 0f
    }


    private fun computeNorm(vector: FloatArray): Float {
        return sqrt(vector.fold(0f) { acc, v -> acc + v * v })
    }

    private fun computeOptimizedCosineSimilarity(
        a: FloatArray,
        b: FloatArray,
        normA: Float,
        normB: Float
    ): Float {
        val dotProduct = a.indices.sumOf { i -> (a[i] * b[i]).toDouble() }.toFloat()
        val denominator = normA * normB
        return if (denominator > 0) dotProduct / denominator else 0f
    }

    // Periodic cache cleanup
    suspend fun cleanupCache() {
        withContext(Dispatchers.IO) {
            vectorCache.clear()
            normCache.clear()
        }
    }
}

// Optimized RAG with streaming and parallel processing
@Singleton
class OptimizedOfflineRAG @Inject constructor(
    private val vectorDB: OptimizedVectorDatabase,
    private val gemma: UnifiedGemmaService,
    private val embedder: TextEmbeddingServiceExtensions,
) {
    companion object {
        private const val CHUNK_SIZE = 512
        private const val CHUNK_OVERLAP = 128
        private const val EMBEDDING_BATCH_SIZE = 32
    }

    // Parallel document processing
    suspend fun setupOptimized(
        documents: List<OfflineRAG.Document>
    ) = withContext(Dispatchers.IO) {


        // Process documents in parallel
        val vectorDocuments = documents
            .flatMap { doc ->
                val chunks = chunkText(doc.content, CHUNK_SIZE, CHUNK_OVERLAP)
                chunks.mapIndexed { idx, chunk ->
                    Triple(doc, idx, chunk)
                }
            }
            .chunked(EMBEDDING_BATCH_SIZE)
            .map { batch ->
                async {
                    // Batch embedding generation
                    val embeddings = embedder.embedBatch(batch.map { it.third.text })

                    batch.zip(embeddings).map { (triple, embedding) ->
                        val (doc, idx, chunk) = triple
                        VectorDatabase.VectorDocument(
                            id = "${doc.id}_$idx",
                            content = chunk.text,
                            embedding = embedding,
                            metadata = mapOf(
                                "subject" to doc.metadata.subject.name,
                                "source" to doc.metadata.source,
                                "chunkIndex" to idx.toString()
                            )
                        )
                    }
                }
            }
            .awaitAll()
            .flatten()

        // Batch insert
        vectorDB.insertBatchOptimized(vectorDocuments)
    }

    private fun chunkText(text: String, size: Int, overlap: Int): List<Chunk> {
        val words = text.split("\\s+".toRegex())
        val chunks = mutableListOf<Chunk>()
        var i = 0
        while (i < words.size) {
            val end = minOf(i + size, words.size)
            val slice = words.subList(i, end)
            chunks += Chunk(slice.joinToString(" "))
            i += size - overlap
        }
        return chunks
    }

    // Streaming response generation
    suspend fun queryWithStreaming(
        query: String,
        subject: Subject? = null,
        onToken: (String) -> Unit
    ) = withContext(Dispatchers.Default) {

        // Get context
        val queryEmbedding = embedder.embedWithCache(query)

        val searchResults = vectorDB.searchOptimized(
            queryEmbedding,
            k = 5,
            filter = subject?.let { mapOf("subject" to it.name) }
        )

        val context = buildContext(searchResults)
        val prompt = buildPrompt(query, context, subject)

        // Stream response
        val fullResponse = gemma.generateTextAsync(
            prompt,
            UnifiedGemmaService.GenerationConfig(
                maxTokens = 512,
                temperature = 0.7f
            )
        )


    }
}

private fun buildContext(results: List<VectorDatabase.SearchResult>): String {
    return results.joinToString("\n\n") {
        "[${it.document.id}]: ${it.document.content}"
    }.take(MAX_CONTEXT_LENGTH)
}

private fun buildPrompt(query: String, context: String, subject: Subject?): String {
    val subjectLine = subject?.let {
        "You are answering a question about ${it.name.lowercase().replace('_', ' ')}."
    } ?: "You are a helpful educational assistant."

    return """
        $subjectLine
        
        Context:
        $context
        
        Question: $query
        
        Answer:
    """.trimIndent()
}


// Locality Sensitive Hashing implementation
class LSH(
    private val numTables: Int,
    private val hashSize: Int,
    private val vectorDim: Int
) {
    private val hashFunctions = List(numTables) {
        RandomProjection(hashSize, vectorDim)
    }

    fun hash(vector: FloatArray): List<String> {
        return hashFunctions.map { it.hash(vector) }
    }
}
private fun nextGaussian(): Float {
    val u = kotlin.random.Random.nextDouble()
    val v = kotlin.random.Random.nextDouble()
    return (kotlin.math.sqrt(-2.0 * kotlin.math.ln(u)) * kotlin.math.cos(2.0 * Math.PI * v)).toFloat()
}

class RandomProjection(size: Int, dim: Int) {
    private val projections = Array(size) {
        FloatArray(dim) { nextGaussian() }
    }


    fun hash(vector: FloatArray): String {
        return projections.map { projection ->
            val dot = vector.indices.sumOf { i ->
                (vector[i] * projection[i]).toDouble()
            }
            if (dot >= 0) '1' else '0'
        }.joinToString("")
    }
}