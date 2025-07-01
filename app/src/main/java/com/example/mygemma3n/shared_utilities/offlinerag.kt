package com.example.mygemma3n.shared_utilities

import com.example.mygemma3n.data.local.Converters
import com.example.mygemma3n.data.local.SubjectRepository
import com.example.mygemma3n.data.local.VectorDatabase
import com.example.mygemma3n.data.local.entities.SubjectEntity
import com.example.mygemma3n.gemma.GemmaModelManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

@Singleton
class OfflineRAG @Inject constructor(
    private val vectorDB: VectorDatabase,
    private val subjectRepo: SubjectRepository,
    private val modelManager: GemmaModelManager
) {

    companion object {
        private const val CHUNK_SIZE = 512
        private const val CHUNK_OVERLAP = 128
        private const val EMBEDDING_DIM = 768
        private const val MAX_CONTEXT_LENGTH = 2048
        private const val TOP_K_RESULTS = 5
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

    suspend fun setupEducationalContent(documents: List<Document>) = withContext(Dispatchers.IO) {
        // Process documents in parallel for faster indexing
        documents.chunked(10).forEach { batch ->
            batch.map { doc ->
                async {
                    processDocument(doc)
                }
            }.awaitAll()
        }

        // Optimize vector index after bulk insertion
        vectorDB.optimizeIndex()
    }

    private suspend fun processDocument(doc: Document) {
        val chunks = chunkText(doc.content, CHUNK_SIZE, CHUNK_OVERLAP)

        chunks.forEachIndexed { index, chunk ->
            // Generate embedding using Gemma 3n's embedding capability
            val embedding = generateEmbedding(chunk.text)

            vectorDB.insert(
                VectorDatabase.VectorDocument(
                    id = "${doc.id}_chunk_$index",
                    content = chunk.text,
                    embedding = embedding,
                    metadata = mapOf(
                        "subject" to doc.metadata.subject.name,
                        "title" to doc.metadata.title,
                        "source" to doc.metadata.source,
                        "chunk_index" to index.toString(),
                        "total_chunks" to chunks.size.toString(),
                        "difficulty" to (doc.metadata.difficulty ?: "medium"),
                        "tags" to doc.metadata.tags.joinToString(",")
                    )
                )
            )
        }
    }

    data class TextChunk(
        val text: String,
        val startIndex: Int,
        val endIndex: Int
    )

    private fun chunkText(
        text: String,
        chunkSize: Int,
        overlap: Int
    ): List<TextChunk> {
        val chunks = mutableListOf<TextChunk>()
        val sentences = text.split(Regex("[.!?]+\\s+"))

        var currentChunk = StringBuilder()
        var currentTokens = 0
        var chunkStartIndex = 0
        var currentIndex = 0

        for (sentence in sentences) {
            val sentenceTokens = estimateTokens(sentence)

            if (currentTokens + sentenceTokens > chunkSize && currentChunk.isNotEmpty()) {
                // Save current chunk
                chunks.add(
                    TextChunk(
                        text = currentChunk.toString().trim(),
                        startIndex = chunkStartIndex,
                        endIndex = currentIndex
                    )
                )

                // Start new chunk with overlap
                val overlapText = extractOverlap(currentChunk.toString(), overlap)
                currentChunk = StringBuilder(overlapText)
                currentTokens = estimateTokens(overlapText)
                chunkStartIndex = currentIndex - overlapText.length
            }

            currentChunk.append(sentence).append(". ")
            currentTokens += sentenceTokens
            currentIndex += sentence.length + 2 // +2 for ". "
        }

        // Add final chunk
        if (currentChunk.isNotEmpty()) {
            chunks.add(
                TextChunk(
                    text = currentChunk.toString().trim(),
                    startIndex = chunkStartIndex,
                    endIndex = text.length
                )
            )
        }

        return chunks
    }

    private fun estimateTokens(text: String): Int {
        // Rough estimation: ~4 characters per token
        return (text.length / 4).coerceAtLeast(1)
    }

    private fun extractOverlap(text: String, overlapTokens: Int): String {
        val words = text.split(" ")
        val overlapWords = overlapTokens / 2 // Rough conversion
        return words.takeLast(overlapWords).joinToString(" ")
    }

    suspend fun queryWithContext(
        query: String,
        subject: Subject? = null,
        useReranking: Boolean = true
    ): String = withContext(Dispatchers.Default) {

        // 1️⃣ Generate query embedding
        val queryEmbedding = generateEmbedding(query)

        // 2️⃣ Retrieve relevant documents
        val filter = subject?.let { mapOf("subject" to it.name) } ?: emptyMap()
        var relevantDocs = vectorDB.search(
            embedding = queryEmbedding,
            k = if (useReranking) TOP_K_RESULTS * 2 else TOP_K_RESULTS,
            filter = filter
        )

        // 3️⃣ Optional reranking
        if (useReranking && relevantDocs.size > TOP_K_RESULTS) {
            relevantDocs = rerankDocuments(query, relevantDocs)
                .take(TOP_K_RESULTS)
        }

        // 4️⃣ Build context + prompt
        val context = buildContext(relevantDocs)
        val prompt = buildRAGPrompt(query, context, subject)

        // 5️⃣ Run Gemma and **return its output as the lambda's last expression**
        modelManager.generateText(
            model = modelManager.getModel(GemmaModelManager.ModelConfig.BALANCED_3B),
            prompt = prompt,
            maxTokens = 512,
            temperature = 0.7f
        )
    }


    private suspend fun generateEmbedding(text: String): FloatArray = withContext(Dispatchers.Default) {
        // 1️⃣ Get the embedding interpreter
        val interpreter = modelManager.getEmbeddingModel()      // org.tensorflow.lite.Interpreter

        // 2️⃣ Tokenize the text (e.g. IntArray of size ≤ 512)
        val inputIds = modelManager.tokenize(text, maxLength = 512)

        // 3️⃣ Prepare input / output maps for the signature runner
        val inputs  = mapOf("input_ids" to inputIds)
        val outputs = mutableMapOf<String, Any>(
            "embeddings" to FloatArray(EMBEDDING_DIM)           // e.g. 768-d vector
        )

        // 4️⃣ Run inference
        interpreter.runSignature(inputs, outputs)

        // 5️⃣ Extract and L2-normalise the embedding
        val embedding = outputs["embeddings"] as FloatArray
        val norm = kotlin.math.sqrt(
            embedding.fold(0f) { acc, v -> acc + v*v }       // returns Float
        )

        FloatArray(embedding.size) { i -> embedding[i] / norm }
    }


    private suspend fun rerankDocuments(
        query: String,
        documents: List<VectorDatabase.SearchResult>
    ): List<VectorDatabase.SearchResult> = coroutineScope {    // ← provides a scope
        val rerankModel = modelManager.getModel(
            GemmaModelManager.ModelConfig.FAST_2B
        )

        documents
            .map { doc ->
                async {
                    val score = calculateRelevanceScore(
                        query,
                        doc.document.content,
                        rerankModel
                    )
                    doc.copy(score = score)
                }
            }
            .awaitAll()                      // wait for all async jobs
            .sortedByDescending { it.score } // then sort
    }


    private suspend fun calculateRelevanceScore(
        query: String,
        document: String,
        model: org.tensorflow.lite.Interpreter
    ): Float {
        val prompt = """
            On a scale of 0-10, rate how relevant this document is to the query.
            Return only a number.
            
            Query: $query
            
            Document: ${document.take(256)}...
            
            Relevance score:
        """.trimIndent()

        val response = modelManager.generateText(
            model = model,
            prompt = prompt,
            maxTokens = 10,
            temperature = 0.1f
        )

        return response.trim().toFloatOrNull() ?: 0f
    }

    private fun buildContext(documents: List<VectorDatabase.SearchResult>): String {
        val contextBuilder = StringBuilder()
        var currentLength = 0

        for ((index, result) in documents.withIndex()) {
            val doc = result.document
            val docText = """
                [Source ${index + 1}: ${doc.metadata["title"]} - ${doc.metadata["source"]}]
                ${doc.content}
                
            """.trimIndent()

            val docLength = estimateTokens(docText)
            if (currentLength + docLength > MAX_CONTEXT_LENGTH) {
                break
            }

            contextBuilder.append(docText)
            currentLength += docLength
        }

        return contextBuilder.toString().trim()
    }

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
}