package com.example.mygemma3n.shared_utilities

import com.example.mygemma3n.data.GeminiApiService            // NEW
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
import kotlin.math.sqrt

@Singleton
class OfflineRAG @Inject constructor(
    private val vectorDB: VectorDatabase,
    private val subjectRepo: SubjectRepository,
    private val modelManager: GemmaModelManager,
    private val geminiApi: GeminiApiService                  // NEW
) {

    companion object {
        private const val CHUNK_SIZE         = 512
        private const val CHUNK_OVERLAP      = 128
        private const val EMBEDDING_DIM      = 3072          // gemini-embedding-001 :contentReference[oaicite:2]{index=2}
        private const val MAX_CONTEXT_LENGTH = 2048
        private const val TOP_K_RESULTS      = 5
    }

    /* ─────────────────────────  enums / DTOs  ───────────────────────── */

    enum class Subject {
        MATHEMATICS, SCIENCE, HISTORY, LANGUAGE_ARTS, GEOGRAPHY, GENERAL
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

    fun SubjectEntity.toModel() = Subject.valueOf(subject) to accuracy

    suspend fun loadSubjects(): List<Pair<Subject, Float>> = subjectRepo.getSubjectsWithAccuracy()

    /* ──────────────────  INDEXING  ────────────────── */

    suspend fun setupEducationalContent(docs: List<Document>) = withContext(Dispatchers.IO) {
        docs.chunked(10).forEach { batch ->
            batch.map { async { processDocument(it) } }.awaitAll()
        }
        vectorDB.optimizeIndex()
    }

    private suspend fun processDocument(doc: Document) {
        chunkText(doc.content, CHUNK_SIZE, CHUNK_OVERLAP).forEachIndexed { i, chunk ->
            val embedding = embedText(chunk.text)
            vectorDB.insert(
                VectorDatabase.VectorDocument(
                    id        = "${doc.id}_$i",
                    content   = chunk.text,
                    embedding = embedding,
                    metadata  = mapOf(
                        "subject"      to doc.metadata.subject.name,
                        "title"        to doc.metadata.title,
                        "source"       to doc.metadata.source,
                        "chunk_index"  to i.toString(),
                        "total_chunks" to chunkText(doc.content, CHUNK_SIZE, CHUNK_OVERLAP).size.toString(),
                        "difficulty"   to (doc.metadata.difficulty ?: "medium"),
                        "tags"         to doc.metadata.tags.joinToString(",")
                    )
                )
            )
        }
    }

    /* ──────────────────  SEARCH  ────────────────── */

    suspend fun queryWithContext(
        query: String,
        subject: Subject? = null,
        useReranking: Boolean = true
    ): String = withContext(Dispatchers.Default) {

        val queryVec  = embedText(query)
        val filter    = subject?.let { mapOf("subject" to it.name) } ?: emptyMap()
        var results   = vectorDB.search(queryVec, if (useReranking) TOP_K_RESULTS * 2 else TOP_K_RESULTS, filter)

        if (useReranking && results.size > TOP_K_RESULTS)
            results = rerankDocuments(query, results).take(TOP_K_RESULTS)

        val context = buildContext(results)
        val prompt  = buildRAGPrompt(query, context, subject)

        modelManager.generateText(
            model       = modelManager.getModel(GemmaModelManager.ModelConfig.BALANCED_3B),
            prompt      = prompt,
            maxTokens   = 512,
            temperature = 0.7f
        )
    }

    /* ──────────────────  EMBEDDINGS  ────────────────── */

    private suspend fun embedText(text: String): FloatArray = withContext(Dispatchers.IO) {
        // single API call → FloatArray
        val raw = geminiApi.embedText(text)                 // helper added in GeminiApiService
        // L2-normalise for cosine similarity
        val norm = sqrt(raw.fold(0f) { acc, v -> acc + v * v })
        FloatArray(raw.size) { i -> raw[i] / norm }
    }

    /* ───────────────  RERANKING  ─────────────── */

    private suspend fun rerankDocuments(
        query: String,
        docs: List<VectorDatabase.SearchResult>
    ) = coroutineScope {
        val fastModel = modelManager.getModel(GemmaModelManager.ModelConfig.FAST_2B)
        docs.map { doc ->
            async {
                val score = calculateRelevanceScore(query, doc.document.content, fastModel)
                doc.copy(score = score)
            }
        }.awaitAll().sortedByDescending { it.score }
    }

    private suspend fun calculateRelevanceScore(
        query: String,
        document: String,
        model: String                       // cloud model name
    ): Float {
        val response = modelManager.generateText(
            model       = model,
            prompt      = "Score 0-10 relevance of: \"$document\" to \"$query\". Return only the number.",
            maxTokens   = 10,
            temperature = 0.1f
        )
        return response.trim().toFloatOrNull() ?: 0f
    }

    /* ─────────────  CONTEXT & PROMPT  ───────────── */

    private fun buildContext(results: List<VectorDatabase.SearchResult>): String {
        val sb = StringBuilder()
        var tokens = 0
        results.forEachIndexed { i, res ->
            val snippet = """
            [Source ${i + 1}: ${res.document.metadata["title"]}]
            ${res.document.content}
            
        """.trimIndent()
            val add = snippet.length / 4
            if (tokens + add > MAX_CONTEXT_LENGTH) return@forEachIndexed  // ← fixed label
            sb.append(snippet)
            tokens += add
        }
        return sb.toString()
    }


    private fun buildRAGPrompt(query: String, ctx: String, subject: Subject?) = """
        ${subject?.let { "You are an expert in ${it.name.lowercase().replace('_', ' ')}." } ?: ""}
        Use the context below to answer. If unsure, say so.

        Context:
        $ctx
        
        Question: $query
        
        Answer:
    """.trimIndent()

    /* ────────────────  TEXT CHUNKING  ──────────────── */

    private data class TextChunk(val text: String, val startIndex: Int, val endIndex: Int)

    private fun chunkText(text: String, size: Int, overlap: Int): List<TextChunk> {
        val out = mutableListOf<TextChunk>()
        val sentences = text.split(Regex("[.!?]+\\s+"))
        var chunk = StringBuilder()
        var tokens = 0
        var start = 0
        sentences.forEach { sent ->
            val t = sent.length / 4
            if (tokens + t > size && chunk.isNotEmpty()) {
                out += TextChunk(chunk.toString().trim(), start, start + chunk.length)
                val ov = extractOverlap(chunk.toString(), overlap)
                chunk = StringBuilder(ov)
                tokens = ov.length / 4
                start += chunk.length - ov.length
            }
            chunk.append(sent).append(". ")
            tokens += t
        }
        if (chunk.isNotEmpty())
            out += TextChunk(chunk.toString().trim(), start, text.length)
        return out
    }

    private fun extractOverlap(text: String, ovTokens: Int): String =
        text.split(" ").takeLast(ovTokens / 2).joinToString(" ")

    /* ─────────────  CRISIS FUNCTIONS  ───────────── */

    suspend fun executeFunction(name: String, params: Map<String, Any>) = when (name) {
        "emergency_contact" -> getEmergencyContact(params["location"] as? String ?: "general")
        "first_aid_steps"   -> getFirstAidSteps(params["situation"] as? String ?: "general")
        "evacuation_route"  -> getEvacuationRoute(params["building"] as? String ?: "unknown")
        else                -> "Function not found."
    }

    private fun getEmergencyContact(loc: String) = """
        Emergency Numbers for $loc
        • Police / Fire / Medical: 911
        • Poison Control: 1-800-222-1222
    """.trimIndent()

    private fun getFirstAidSteps(sit: String) = when (sit.lowercase()) {
        "burns"    -> "Cool burn with water, cover with sterile gauze, seek care."
        "bleeding" -> "Apply pressure, elevate limb, call 911 if severe."
        else       -> "Ensure safety, check responsiveness, call 911."
    }

    private fun getEvacuationRoute(building: String) =
        "Follow illuminated EXIT signs to stairwell; avoid elevators."
}
