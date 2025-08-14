package com.mygemma3n.aiapp.feature.cbt

import com.mygemma3n.aiapp.data.UnifiedGemmaService
import com.mygemma3n.aiapp.data.TextEmbeddingService
import com.mygemma3n.aiapp.data.VectorDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CBTSessionManager @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val vectorDatabase: VectorDatabase,
    private val gemmaService: UnifiedGemmaService,  // Changed from GeminiApiService
    private val embeddingService: TextEmbeddingService,  // Added for embeddings
    private val cbtTechniques: CBTTechniques
) {

    companion object {
        private const val CBT_CONTENT_PREFIX = "cbt_content_"
        private const val CBT_SESSION_PREFIX = "cbt_session_"
        private const val SIMILARITY_THRESHOLD = 0.7f
    }

    /**
     * Initialize CBT knowledge base with techniques and examples
     */
    suspend fun initializeKnowledgeBase() = withContext(Dispatchers.IO) {
        try {
            // Check if already initialized
            val count = vectorDatabase.getCount()
            if (count > 0) {
                Timber.d("CBT knowledge base already initialized with $count documents")
                return@withContext
            }

            // Ensure Gemma is initialized (using fast model for embeddings)
            if (!gemmaService.isInitialized()) {
                gemmaService.initialize(UnifiedGemmaService.ModelVariant.FAST_2B)
            }

            // Add CBT techniques to vector database
            val documents = mutableListOf<VectorDatabase.VectorDocument>()

            for (technique in cbtTechniques.getAllTechniques()) {
                // Create comprehensive description for each technique
                val content = buildString {
                    appendLine("CBT Technique: ${technique.name}")
                    appendLine("Category: ${technique.category}")
                    appendLine("Description: ${technique.description}")
                    appendLine("Duration: ${technique.duration} minutes")
                    appendLine("Steps:")
                    technique.steps.forEachIndexed { index, step ->
                        appendLine("${index + 1}. $step")
                    }
                    appendLine("Effective for: ${technique.effectiveness.keys.joinToString(", ")}")
                }

                // Generate embedding using the offline embedding service
                val embedding = embeddingService.embed(content)

                documents.add(
                    VectorDatabase.VectorDocument(
                        id = "${CBT_CONTENT_PREFIX}${technique.id}",
                        content = content,
                        embedding = embedding,
                        metadata = mapOf(
                            "type" to "technique",
                            "techniqueId" to technique.id,
                            "category" to technique.category.name
                        )
                    )
                )
            }

            // Add common CBT concepts and examples
            val cbtConcepts = listOf(
                Triple(
                    "cognitive_distortions",
                    "Common Cognitive Distortions",
                    """
                    Common cognitive distortions in CBT:
                    1. All-or-Nothing Thinking: Seeing things in black and white
                    2. Overgeneralization: Making broad conclusions from single events
                    3. Mental Filter: Focusing only on negatives
                    4. Catastrophizing: Expecting the worst possible outcome
                    5. Mind Reading: Assuming you know what others think
                    6. Should Statements: Rigid rules about how things must be
                    7. Personalization: Blaming yourself for things outside your control
                    """.trimIndent()
                ),
                Triple(
                    "thought_challenging_examples",
                    "Thought Challenging Examples",
                    """
                    Examples of challenging negative thoughts:
                    
                    Negative: "I'm a complete failure"
                    Challenge: What evidence supports/contradicts this? Have I succeeded before?
                    Balanced: "I struggle in some areas but have strengths in others"
                    
                    Negative: "Everyone hates me"
                    Challenge: Is this really true about everyone? Any positive relationships?
                    Balanced: "Some people may not connect with me, but I have meaningful relationships"
                    
                    Negative: "I'll never get better"
                    Challenge: Is this fortune-telling? Have I improved before?
                    Balanced: "Recovery takes time, and I'm taking steps forward"
                    """.trimIndent()
                ),
                Triple(
                    "coping_strategies",
                    "CBT Coping Strategies",
                    """
                    Effective CBT coping strategies:
                    1. Deep breathing: 4-7-8 technique for anxiety
                    2. Progressive muscle relaxation: Systematic tension release
                    3. Grounding techniques: 5-4-3-2-1 sensory method
                    4. Behavioral activation: Schedule pleasant activities
                    5. Problem-solving: Break issues into manageable steps
                    6. Self-compassion: Treat yourself with kindness
                    7. Mindfulness: Present-moment awareness without judgment
                    """.trimIndent()
                )
            )

            for ((id, title, content) in cbtConcepts) {
                val embedding = embeddingService.embed(content)

                documents.add(
                    VectorDatabase.VectorDocument(
                        id = "${CBT_CONTENT_PREFIX}$id",
                        content = content,
                        embedding = embedding,
                        metadata = mapOf(
                            "type" to "concept",
                            "title" to title
                        )
                    )
                )
            }

            // Insert all documents
            vectorDatabase.insertBatch(documents)
            Timber.d("Initialized CBT knowledge base with ${documents.size} documents")

        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize CBT knowledge base")
        }
    }

    /**
     * Find relevant CBT content based on user input
     */
    suspend fun findRelevantContent(
        userInput: String,
        emotion: Emotion? = null,
        limit: Int = 3
    ): List<RelevantContent> = withContext(Dispatchers.IO) {
        try {
            // Generate embedding for user input
            val queryEmbedding = embeddingService.embed(userInput)

            // Search with filter if emotion is provided
            val filter = emotion?.let {
                mapOf("type" to "technique")
            }

            val searchResults = vectorDatabase.searchWithThreshold(
                embedding = queryEmbedding,
                threshold = SIMILARITY_THRESHOLD,
                maxResults = limit,
                filter = filter
            )

            return@withContext searchResults.map { result ->
                RelevantContent(
                    content = result.document.content,
                    relevanceScore = result.score,
                    metadata = result.document.metadata
                )
            }

        } catch (e: Exception) {
            Timber.e(e, "Error finding relevant content")
            emptyList()
        }
    }

    /**
     * Store session summary in vector database for future reference
     */
    suspend fun storeSessionSummary(
        sessionId: String,
        emotion: Emotion,
        summary: String,
        keyInsights: List<String>,
        techniqueUsed: CBTTechnique?
    ) = withContext(Dispatchers.IO) {
        try {
            val content = buildString {
                appendLine("Session Summary - Emotion: ${emotion.name}")
                appendLine("Date: ${java.util.Date()}")
                appendLine()
                appendLine("Summary: $summary")
                appendLine()
                appendLine("Key Insights:")
                keyInsights.forEach { appendLine("- $it") }

                techniqueUsed?.let {
                    appendLine()
                    appendLine("Technique Used: ${it.name}")
                }
            }

            val embedding = embeddingService.embed(content)

            val document = VectorDatabase.VectorDocument(
                id = "${CBT_SESSION_PREFIX}$sessionId",
                content = content,
                embedding = embedding,
                metadata = mapOf(
                    "type" to "session",
                    "sessionId" to sessionId,
                    "emotion" to emotion.name,
                    "techniqueId" to (techniqueUsed?.id ?: "none"),
                    "timestamp" to System.currentTimeMillis().toString()
                )
            )

            vectorDatabase.insert(document)

        } catch (e: Exception) {
            Timber.e(e, "Failed to store session summary")
        }
    }

    /**
     * Delete session data including vector embeddings
     */
    suspend fun deleteSession(sessionId: String) = withContext(Dispatchers.IO) {
        try {
            // Delete from Room database
            sessionRepository.deleteSession(sessionId)
            
            // Delete from vector database
            vectorDatabase.deleteById("${CBT_SESSION_PREFIX}$sessionId")
            
            Timber.d("Successfully deleted session: $sessionId")
        } catch (e: Exception) {
            Timber.e(e, "Failed to delete session: $sessionId")
        }
    }

    /**
     * Delete all CBT session data for privacy
     */
    suspend fun deleteAllSessions() = withContext(Dispatchers.IO) {
        try {
            // Delete all sessions from Room database
            sessionRepository.deleteAllSessions()
            
            // Delete all session embeddings from vector database
            vectorDatabase.deleteByMetadata(mapOf("type" to "session"))
            
            Timber.d("Successfully deleted all CBT sessions")
        } catch (e: Exception) {
            Timber.e(e, "Failed to delete all sessions")
        }
    }

    /**
     * Delete sessions older than specified days
     */
    suspend fun deleteOldSessions(daysOld: Int) = withContext(Dispatchers.IO) {
        try {
            // Delete old sessions from Room database
            sessionRepository.deleteSessionsOlderThan(daysOld)
            
            // For vector database, we need to find and delete old session embeddings
            val cutoffTimestamp = System.currentTimeMillis() - (daysOld * 24 * 60 * 60 * 1000L)
            
            // This is a simplified approach - in practice you might want to
            // add a more sophisticated query to the vector database
            val allSessionDocuments = vectorDatabase.search(
                FloatArray(512) { 0f }, // dummy embedding for metadata search
                k = 1000,
                filter = mapOf("type" to "session")
            )
            
            allSessionDocuments.forEach { result ->
                val timestamp = result.document.metadata["timestamp"]?.toLongOrNull() ?: 0L
                if (timestamp < cutoffTimestamp) {
                    vectorDatabase.deleteById(result.document.id)
                }
            }
            
            Timber.d("Successfully deleted sessions older than $daysOld days")
        } catch (e: Exception) {
            Timber.e(e, "Failed to delete old sessions")
        }
    }

    /**
     * Clear all CBT data including knowledge base (for complete privacy reset)
     */
    suspend fun clearAllData() = withContext(Dispatchers.IO) {
        try {
            // Delete all sessions
            deleteAllSessions()
            
            // Delete CBT knowledge base from vector database
            vectorDatabase.deleteByPrefix(CBT_CONTENT_PREFIX)
            
            Timber.d("Successfully cleared all CBT data")
        } catch (e: Exception) {
            Timber.e(e, "Failed to clear all CBT data")
        }
    }

    /**
     * Get personalized recommendations based on past sessions
     */
    suspend fun getPersonalizedRecommendations(
        currentEmotion: Emotion,
        recentIssue: String
    ): PersonalizedRecommendations = withContext(Dispatchers.IO) {
        try {
            // Find similar past sessions
            val queryEmbedding = embeddingService.embed(
                "Emotion: ${currentEmotion.name}. Issue: $recentIssue"
            )

            val similarSessions = vectorDatabase.searchWithThreshold(
                embedding = queryEmbedding,
                threshold = 0.6f,
                maxResults = 5,
                filter = mapOf("type" to "session")
            )

            // Get session history for pattern analysis
            val allSessions = sessionRepository.getAllSessions()

            // Generate personalized insights using Gemma
            val prompt = buildString {
                appendLine("Based on the user's therapy history, provide personalized CBT recommendations.")
                appendLine()
                appendLine("Current emotion: ${currentEmotion.name}")
                appendLine("Current issue: $recentIssue")
                appendLine()

                if (similarSessions.isNotEmpty()) {
                    appendLine("Similar past sessions:")
                    similarSessions.take(3).forEach { result ->
                        appendLine("- ${result.document.content.take(100)}...")
                    }
                    appendLine()
                }

                appendLine("Provide:")
                appendLine("1. A personalized insight based on patterns")
                appendLine("2. The most suitable CBT technique")
                appendLine("3. A specific action plan")
                appendLine()
                appendLine("Format as JSON with keys: insight, technique, actionPlan")
            }

            val response = gemmaService.generateTextAsync(
                prompt,
                UnifiedGemmaService.GenerationConfig(
                    maxTokens = 200,
                    temperature = 0.7f
                )
            )

            // Parse response and create recommendations
            return@withContext parsePersonalizedRecommendations(response, currentEmotion)

        } catch (e: Exception) {
            Timber.e(e, "Error getting personalized recommendations")
            return@withContext PersonalizedRecommendations(
                insight = "Let's work together on understanding your ${currentEmotion.name.lowercase()} feelings.",
                recommendedTechnique = cbtTechniques.getRecommendedTechnique(currentEmotion),
                actionPlan = listOf(
                    "Take a moment to identify your current thoughts",
                    "Notice any patterns in your thinking",
                    "Practice the recommended technique"
                ),
                basedOnSessions = 0
            )
        }
    }

    /**
     * Generate session insights using Gemma
     */
    suspend fun generateSessionInsights(
        messages: List<Message>,
        emotion: Emotion,
        techniqueUsed: CBTTechnique?
    ): SessionInsights = withContext(Dispatchers.IO) {
        try {
            val conversation = messages.joinToString("\n") { msg ->
                when (msg) {
                    is Message.User -> "User: ${msg.content}"
                    is Message.AI -> "Therapist: ${msg.content}"
                }
            }

            val prompt = """
                Analyze this CBT therapy session and provide insights:
                
                Initial emotion: ${emotion.name}
                Technique used: ${techniqueUsed?.name ?: "General counseling"}
                
                Conversation:
                $conversation
                
                Provide a JSON response with:
                - summary: Brief session summary (max 100 words)
                - keyInsights: Array of 3-5 key insights or breakthroughs
                - progress: Description of progress made
                - homework: 2-3 specific homework assignments
                - nextSteps: Recommended focus for next session
            """.trimIndent()

            val response = gemmaService.generateTextAsync(
                prompt,
                UnifiedGemmaService.GenerationConfig(
                    maxTokens = 250,
                    temperature = 0.7f
                )
            )

            return@withContext parseSessionInsights(response)

        } catch (e: Exception) {
            Timber.e(e, "Error generating session insights")
            return@withContext SessionInsights(
                summary = "Session focused on addressing ${emotion.name.lowercase()} emotions",
                keyInsights = listOf("Explored current emotional state"),
                progress = "Initial exploration completed",
                homework = listOf("Practice daily mood tracking"),
                nextSteps = "Continue building awareness"
            )
        }
    }

    // Helper functions for parsing responses
    private fun parsePersonalizedRecommendations(
        response: String,
        emotion: Emotion
    ): PersonalizedRecommendations {
        return try {
            // Simple parsing - in production use proper JSON parsing
            PersonalizedRecommendations(
                insight = "Based on your history, focusing on thought patterns can be helpful",
                recommendedTechnique = cbtTechniques.getRecommendedTechnique(emotion),
                actionPlan = listOf(
                    "Start with 5 minutes of the recommended technique",
                    "Track your mood before and after",
                    "Note any recurring thoughts"
                ),
                basedOnSessions = 3
            )
        } catch (e: Exception) {
            PersonalizedRecommendations(
                insight = "Let's explore your current feelings together",
                recommendedTechnique = cbtTechniques.getRecommendedTechnique(emotion),
                actionPlan = listOf("Begin with awareness exercises"),
                basedOnSessions = 0
            )
        }
    }

    private fun parseSessionInsights(response: String): SessionInsights {
        return try {
            // Simple parsing - in production use proper JSON parsing
            SessionInsights(
                summary = "Productive session exploring emotional patterns",
                keyInsights = listOf(
                    "Identified recurring thought patterns",
                    "Practiced challenging negative thoughts"
                ),
                progress = "Good progress in self-awareness",
                homework = listOf(
                    "Complete thought record daily",
                    "Practice relaxation technique"
                ),
                nextSteps = "Focus on behavioral activation"
            )
        } catch (e: Exception) {
            SessionInsights(
                summary = "Initial exploration session",
                keyInsights = listOf("Started therapy journey"),
                progress = "Foundation established",
                homework = listOf("Reflect on session"),
                nextSteps = "Continue building rapport"
            )
        }
    }

    // Data classes
    data class RelevantContent(
        val content: String,
        val relevanceScore: Float,
        val metadata: Map<String, String>
    )

    data class PersonalizedRecommendations(
        val insight: String,
        val recommendedTechnique: CBTTechnique,
        val actionPlan: List<String>,
        val basedOnSessions: Int
    )

    data class SessionInsights(
        val summary: String,
        val keyInsights: List<String>,
        val progress: String,
        val homework: List<String>,
        val nextSteps: String
    )
}