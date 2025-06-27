package com.example.mygemma3n.shared_utilities

import com.example.mygemma3n.feature.VectorDatabase
import com.example.mygemma3n.gemma.GemmaModelManager
import com.google.ai.edge.localagents.rag.chunking.TextChunker
import javax.inject.Inject
import javax.security.auth.Subject

// OfflineRAG.kt
class OfflineRAG @Inject constructor(
    private val vectorDB: VectorDatabase,
    private val chunker: TextChunker
) {
    suspend fun setupEducationalContent() {
        // Pre-process and store educational content with embeddings
        val documents = loadEducationalDocuments()

        documents.forEach { doc ->
            val chunks = chunker.chunk(doc.content, maxTokens = 512)
            chunks.forEach { chunk ->
                val embedding = generateEmbedding(chunk)
                vectorDB.insert(
                    VectorDatabase.VectorDocument(
                        content = chunk,
                        embedding = embedding,
                        metadata = doc.metadata
                    )
                )
            }
        }
    }

    suspend fun queryWithContext(query: String, subject: Subject): String {
        val queryEmbedding = generateEmbedding(query)
        val relevantDocs = vectorDB.search(queryEmbedding, k = 5, filter = subject)

        val context = relevantDocs.joinToString("\n") { it.content }

        return modelManager.getModel(GemmaModelManager.ModelConfig.Balanced3B).run(
            buildRAGPrompt(query, context)
        )
    }
}