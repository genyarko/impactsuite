package com.example.mygemma3n.shared_utilities

import com.example.mygemma3n.data.TextEmbeddingService
import com.example.mygemma3n.data.UnifiedGemmaService
import com.example.mygemma3n.data.local.SubjectRepository
import com.example.mygemma3n.data.local.VectorDatabase
import com.example.mygemma3n.data.local.entities.SubjectEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfflineRAG @Inject constructor(
    private val vectorDB: VectorDatabase,
    private val subjectRepo: SubjectRepository,
    private val gemma: UnifiedGemmaService,
    private val embedderService: TextEmbeddingService
) {

    companion object {
        private const val CHUNK_SIZE = 512
        private const val CHUNK_OVERLAP = 128
        private const val MAX_CONTEXT_LENGTH = 2048
        private const val TOP_K = 5
    }

    enum class Subject {
        MATHEMATICS,
        SCIENCE,
        HISTORY,
        LANGUAGE_ARTS,
        GEOGRAPHY,
        GENERAL
    }

    data class Document(
        val id: String,
        val content: String,
        val metadata: DocumentMetadata
    )

    data class DocumentMetadata(
        val subject: Subject,
        val title: String,
        val source: String,
        val difficulty: String? = null,
        val tags: List<String> = emptyList()
    )

    fun SubjectEntity.toModel() =
        OfflineRAG.Subject.valueOf(subject) to accuracy

    suspend fun loadSubjects(): List<Pair<OfflineRAG.Subject, Float>> =
        subjectRepo.getSubjectsWithAccuracy()

    suspend fun setup(documents: List<Document>) = withContext(Dispatchers.IO) {
        // Ensure model is initialized for embeddings (if needed)
        ensureModelInitialized()

        documents.forEach { doc ->
            val chunks = chunkText(doc.content, CHUNK_SIZE, CHUNK_OVERLAP)
            chunks.forEachIndexed { idx, chunk ->
                val emb = embedderService.embed(chunk.text)
                vectorDB.insert(VectorDatabase.VectorDocument(
                    id = "${doc.id}_$idx",
                    content = chunk.text,
                    embedding = emb,
                    metadata = mapOf("subject" to doc.metadata.subject.name)
                ))
            }
        }
        vectorDB.optimizeIndex()
    }

    data class TextChunk(
        val text: String,
        val startIndex: Int,
        val endIndex: Int
    )

    private fun estimateTokens(text: String): Int {
        // Rough estimation: ~4 characters per token
        return (text.length / 4).coerceAtLeast(1)
    }

    private fun extractOverlap(text: String, overlapTokens: Int): String {
        val words = text.split(" ")
        val overlapWords = overlapTokens / 2 // Rough conversion
        return words.takeLast(overlapWords).joinToString(" ")
    }

    /** Runs query with optional subject filter */
    suspend fun queryWithContext(
        query: String,
        subject: Subject? = null
    ): String = withContext(Dispatchers.Default) {
        // Ensure model is initialized
        ensureModelInitialized()

        val qEmb = embedderService.embed(query)
        var results = vectorDB.search(qEmb, k = TOP_K, filter = subject?.let { mapOf("subject" to it.name) } ?: emptyMap())
        val context = buildContext(results)
        val prompt = """
            You are an educational assistant.
            Use the following context to answer:
            $context
            Question: $query
        """.trimIndent()

        gemma.generateTextAsync(
            prompt,
            UnifiedGemmaService.GenerationConfig(
                maxTokens = 512,
                temperature = 0.7f
            )
        )
    }

    private suspend fun ensureModelInitialized() {
        if (!gemma.isInitialized()) {
            try {
                gemma.initializeBestAvailable()
                Timber.d("Initialized Gemma model for RAG")
            } catch (e: Exception) {
                Timber.e(e, "Failed to initialize Gemma for RAG")
                throw IllegalStateException("Offline model not available", e)
            }
        }
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

    private fun buildContext(results: List<VectorDatabase.SearchResult>): String {
        return results.joinToString("\n\n") {
            "[${it.document.id}]: ${it.document.content}"
        }.take(MAX_CONTEXT_LENGTH)
    }

    data class Chunk(val text: String)

    private fun buildRAGPrompt(
        query: String,
        context: String,
        subject: Subject?
    ): String {
        val subjectContext = subject?.let {
            "You are answering a question about ${it.name.lowercase().replace('_', ' ')}."
        } ?: "You are a helpful educational assistant."

        return """
            $subjectContext
            
            Use the following context to answer the question. If the answer cannot be found in the context, say so.
            
            Context:
            $context
            
            Question: $query
            
            Answer:
        """.trimIndent()
    }

    suspend fun loadEducationalDocuments(): List<Document> {
        // Load pre-packaged educational content
        // This would typically load from assets or pre-downloaded content
        return listOf(
            Document(
                id = "math_algebra_basics",
                content = "Algebra is a branch of mathematics dealing with symbols...",
                metadata = DocumentMetadata(
                    subject = Subject.MATHEMATICS,
                    title = "Introduction to Algebra",
                    source = "Educational Content Pack v1.0",
                    difficulty = "beginner",
                    tags = listOf("algebra", "basics", "equations")
                )
            ),
            // Add more documents...
        )
    }

    // Function calling support for crisis scenarios
    suspend fun executeFunction(
        functionName: String,
        parameters: Map<String, Any>
    ): String {
        // Ensure model is initialized
        ensureModelInitialized()

        return when (functionName) {
            "emergency_contact" -> getEmergencyContact(
                parameters["location"] as? String ?: "general"
            )
            "first_aid_steps" -> getFirstAidSteps(
                parameters["situation"] as? String ?: "general"
            )
            "evacuation_route" -> getEvacuationRoute(
                parameters["building"] as? String ?: "unknown"
            )
            else -> "Function not found"
        }
    }

    private fun getEmergencyContact(location: String): String {
        // Return location-specific emergency contacts
        return """
            Emergency Contacts:
            - Police: 911
            - Fire: 911
            - Medical: 911
            - Poison Control: 1-800-222-1222
        """.trimIndent()
    }

    private fun getFirstAidSteps(situation: String): String {
        // Return situation-specific first aid steps
        val steps = when (situation.lowercase()) {
            "burns" -> "1. Cool the burn with water\n2. Cover with sterile gauze\n3. Seek medical help"
            "bleeding" -> "1. Apply direct pressure\n2. Elevate if possible\n3. Call 911 for severe bleeding"
            else -> "1. Ensure safety\n2. Check responsiveness\n3. Call 911\n4. Provide appropriate care"
        }
        return steps
    }

    private fun getEvacuationRoute(building: String): String {
        // Return building-specific evacuation routes
        return "Follow exit signs to nearest emergency exit. Do not use elevators."
    }

    private fun buildRAGPrompt(query: String, subject: Subject?): String {
        val subjectContext = subject?.let {
            "You are answering a question about ${it.name.lowercase().replace('_', ' ')}."
        } ?: "You are a helpful educational assistant."

        return """
            $subjectContext
            
            Question: $query
            
            Please provide a comprehensive answer based on your knowledge.
        """.trimIndent()
    }
}