package com.example.mygemma3n.feature.quiz

import android.content.Context
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * Enhanced Prompt Manager with better variety and dynamic prompt generation
 */
@Singleton
class EnhancedPromptManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson
) {
    private var promptData: QuizPromptData? = null

    // Cache for variety tracking
    private val recentPromptHashes = mutableListOf<Int>()
    private val maxRecentPrompts = 20

    data class QuizPromptData(
        val questionTypes: Map<String, QuestionTypeData>,
        val fallbackQuestions: Map<String, Map<String, QuestionExample>>,
        val questionStems: Map<String, List<String>>?,
        val conceptVariations: Map<String, List<String>>?
    )

    data class QuestionTypeData(
        val instructions: String,
        val examples: Map<String, List<QuestionExample>>, // Changed to List for multiple examples
        val promptVariations: List<String>? // Different ways to ask for the same type
    )

    data class QuestionExample(
        val question: String,
        val options: List<String>,
        val correctAnswer: String,
        val explanation: String? = null,
        val hint: String? = null,
        val conceptsCovered: List<String>? = null
    )

    init {
        loadPromptData()
    }

    private fun getTopicVariations(baseTopic: String): List<String> {
        return listOf(
            baseTopic,
            "fundamentals of $baseTopic",
            "applications of $baseTopic",
            "advanced $baseTopic concepts",
            "$baseTopic in practice",
            "understanding $baseTopic"
        )
    }

    private fun loadPromptData() {
        try {
            val jsonString = context.assets.open("quiz_prompts.json").bufferedReader().use { it.readText() }
            promptData = gson.fromJson(jsonString, QuizPromptData::class.java)
            Timber.d("Loaded quiz prompts from assets")
        } catch (e: Exception) {
            Timber.w(e, "Failed to load quiz prompts from assets, using enhanced defaults")
            promptData = createEnhancedDefaultPromptData()
        }
    }

    /**
     * Get varied instructions for question generation
     */
    fun getVariedQuestionPrompt(
        questionType: QuestionType,
        subject: Subject,
        topic: String,
        difficulty: Difficulty,
        attemptNumber: Int = 0
    ): Pair<String, String> {
        val data = promptData ?: createEnhancedDefaultPromptData()
        val typeData = data.questionTypes[questionType.name] ?: return getEmbeddedInstructions(questionType)

        // Get subject-specific examples (now returns multiple)
        val subjectName = if (subject == Subject.LANGUAGE_ARTS) "ENGLISH" else subject.name
        val examples = typeData.examples[subjectName]
            ?: typeData.examples["SCIENCE"]
            ?: listOf()

        // Select a random example if multiple exist
        val selectedExample = if (examples.isNotEmpty()) {
            examples[(attemptNumber + Random.nextInt(100)) % examples.size]
        } else {
            return getEmbeddedInstructions(questionType)
        }

        // Get varied instructions
        val baseInstructions = typeData.instructions
        val variations = typeData.promptVariations ?: listOf()
        val selectedVariation = if (variations.isNotEmpty()) {
            variations[attemptNumber % variations.size]
        } else {
            ""
        }

        // Generate dynamic prompt elements
        val dynamicElements = generateDynamicPromptElements(subject, topic, difficulty, attemptNumber)

        // Combine everything with variety
        val enhancedInstructions = """
            $baseInstructions
            
            $selectedVariation
            
            ${dynamicElements.joinToString("\n")}
            
            IMPORTANT: Create a completely unique question. Be creative and original.
            Consider different angles, scenarios, or applications of the concept.
            
            Variation hint #${attemptNumber + 1}: ${getVariationHint(questionType, attemptNumber)}
        """.trimIndent()

        val exampleJson = gson.toJson(selectedExample)

        // Track this prompt to avoid repetition
        val promptHash = (enhancedInstructions + topic + difficulty).hashCode()
        trackPromptUsage(promptHash)

        return Pair(enhancedInstructions, exampleJson)
    }

    /**
     * Generate dynamic elements to add variety
     */
    private fun generateDynamicPromptElements(
        subject: Subject,
        topic: String,
        difficulty: Difficulty,
        attemptNumber: Int
    ): List<String> {
        val elements = mutableListOf<String>()

        // Add context variations
        val contexts = getContextVariations(subject)
        if (contexts.isNotEmpty()) {
            elements.add("Context suggestion: ${contexts[attemptNumber % contexts.size]}")
        }

        // Add difficulty-specific guidance
        elements.add(getDifficultyGuidance(difficulty, attemptNumber))

        // Add topic-specific variations
        if (topic.isNotBlank()) {
            elements.add("Focus area: ${getTopicVariation(topic, attemptNumber)}")
        }

        // Add style variations
        elements.add("Style: ${getStyleVariation(attemptNumber)}")

        return elements
    }

    /**
     * Get context variations for a subject
     */
    private fun getContextVariations(subject: Subject): List<String> = when (subject) {
        Subject.MATHEMATICS -> listOf(
            "real-world application",
            "word problem scenario",
            "abstract mathematical concept",
            "visual/geometric interpretation",
            "practical calculation",
            "pattern recognition",
            "logical reasoning"
        )
        Subject.SCIENCE -> listOf(
            "laboratory experiment",
            "natural phenomenon",
            "everyday observation",
            "scientific discovery",
            "technology application",
            "environmental context",
            "health and medicine"
        )
        Subject.HISTORY -> listOf(
            "cause and effect",
            "historical figure perspective",
            "timeline and chronology",
            "cultural impact",
            "primary source analysis",
            "historical comparison",
            "modern relevance"
        )
        Subject.ENGLISH, Subject.LANGUAGE_ARTS -> listOf(
            "literary analysis",
            "grammar in context",
            "creative writing element",
            "vocabulary in use",
            "reading comprehension",
            "figurative language",
            "author's perspective"
        )
        else -> listOf("general knowledge", "practical application", "conceptual understanding")
    }

    /**
     * Get difficulty-specific guidance
     */
    private fun getDifficultyGuidance(difficulty: Difficulty, attempt: Int): String {
        val easyGuidance = listOf(
            "Use simple, clear language",
            "Focus on basic recognition",
            "Test fundamental understanding",
            "Use familiar examples"
        )

        val mediumGuidance = listOf(
            "Require application of concepts",
            "Include some analysis",
            "Test connections between ideas",
            "Use moderately complex scenarios"
        )

        val hardGuidance = listOf(
            "Require synthesis of multiple concepts",
            "Include complex reasoning",
            "Test deep understanding",
            "Use challenging scenarios"
        )

        return when (difficulty) {
            Difficulty.EASY -> easyGuidance[attempt % easyGuidance.size]
            Difficulty.MEDIUM -> mediumGuidance[attempt % mediumGuidance.size]
            Difficulty.HARD -> hardGuidance[attempt % hardGuidance.size]
            Difficulty.ADAPTIVE -> "Adjust complexity based on student level"
        }
    }

    /**
     * Get topic variation suggestions
     */
    private fun getTopicVariation(topic: String, attempt: Int): String {
        val variations = listOf(
            "core concept of $topic",
            "application of $topic",
            "common misconception about $topic",
            "relationship of $topic to other concepts",
            "real-world example of $topic",
            "problem-solving using $topic"
        )
        return variations[attempt % variations.size]
    }

    /**
     * Get style variations for questions
     */
    private fun getStyleVariation(attempt: Int): String {
        val styles = listOf(
            "straightforward and direct",
            "scenario-based",
            "analytical",
            "comparative",
            "cause-and-effect focused",
            "application-oriented",
            "conceptual",
            "problem-solving"
        )
        return styles[attempt % styles.size]
    }

    /**
     * Get variation hints for specific question types
     */
    private fun getVariationHint(questionType: QuestionType, attempt: Int): String {
        val hints = when (questionType) {
            QuestionType.MULTIPLE_CHOICE -> listOf(
                "Focus on different aspects of the concept",
                "Use a scenario or story context",
                "Test application rather than memorization",
                "Include a 'which is NOT' style question",
                "Use a data interpretation question",
                "Create a comparison question",
                "Test understanding of exceptions or edge cases"
            )

            QuestionType.TRUE_FALSE -> listOf(
                "Test a common misconception",
                "Use a statement with subtle complexity",
                "Focus on cause-and-effect relationships",
                "Test understanding of definitions",
                "Include conditional statements",
                "Test knowledge of exceptions"
            )

            QuestionType.FILL_IN_BLANK -> listOf(
                "Use the term in a different context",
                "Test related vocabulary",
                "Focus on process or sequence",
                "Use an analogy or comparison",
                "Test understanding of relationships"
            )

            QuestionType.SHORT_ANSWER -> listOf(
                "Ask for explanation of a process",
                "Request comparison between concepts",
                "Ask about real-world applications",
                "Test understanding of 'why' not just 'what'",
                "Ask for examples or counter-examples"
            )

            else -> listOf("Be creative and original")
        }

        return hints[attempt % hints.size]
    }

    /**
     * Track prompt usage to detect patterns
     */
    private fun trackPromptUsage(hash: Int) {
        recentPromptHashes.add(hash)
        if (recentPromptHashes.size > maxRecentPrompts) {
            recentPromptHashes.removeAt(0)
        }

        // Log if we're seeing repeated patterns
        val uniqueCount = recentPromptHashes.toSet().size
        if (uniqueCount < recentPromptHashes.size * 0.7) {
            Timber.w("Low prompt variety detected: $uniqueCount unique out of ${recentPromptHashes.size}")
        }
    }

    /**
     * Create enhanced default data with more variety
     */
    private fun createEnhancedDefaultPromptData(): QuizPromptData {
        return QuizPromptData(
            questionTypes = mapOf(
                "MULTIPLE_CHOICE" to QuestionTypeData(
                    instructions = "Create a multiple choice question with 4 distinct options.",
                    examples = mapOf(
                        "SCIENCE" to listOf(
                            QuestionExample(
                                question = "What is the powerhouse of the cell?",
                                options = listOf("Nucleus", "Mitochondria", "Ribosome", "Cell membrane"),
                                correctAnswer = "Mitochondria",
                                explanation = "Mitochondria produce ATP through cellular respiration.",
                                hint = "This organelle is involved in energy production.",
                                conceptsCovered = listOf("cell-biology", "organelles")
                            ),
                            QuestionExample(
                                question = "Which process converts light energy into chemical energy?",
                                options = listOf("Respiration", "Photosynthesis", "Fermentation", "Digestion"),
                                correctAnswer = "Photosynthesis",
                                explanation = "Photosynthesis occurs in chloroplasts and produces glucose.",
                                hint = "This process occurs in green plants.",
                                conceptsCovered = listOf("photosynthesis", "energy-conversion")
                            ),
                            QuestionExample(
                                question = "What type of rock forms from cooled magma?",
                                options = listOf("Sedimentary", "Metamorphic", "Igneous", "Limestone"),
                                correctAnswer = "Igneous",
                                explanation = "Igneous rocks form when molten rock cools and solidifies.",
                                hint = "Think about volcanic activity.",
                                conceptsCovered = listOf("geology", "rock-cycle")
                            )
                        ),
                        "MATHEMATICS" to listOf(
                            QuestionExample(
                                question = "What is the slope of a horizontal line?",
                                options = listOf("1", "0", "Undefined", "-1"),
                                correctAnswer = "0",
                                explanation = "Horizontal lines have no vertical change, so slope = 0.",
                                hint = "Think about rise over run.",
                                conceptsCovered = listOf("linear-equations", "slope")
                            )
                        )
                    ),
                    promptVariations = listOf(
                        "Think of an interesting scenario to frame this question.",
                        "Consider what students often confuse about this topic.",
                        "Create a question that tests deep understanding, not memorization.",
                        "Frame this as a problem-solving question.",
                        "Use a real-world context for this question."
                    )
                )
            ),
            fallbackQuestions = mapOf(),
            questionStems = mapOf(
                "MULTIPLE_CHOICE" to listOf(
                    "Which of the following best describes...",
                    "What is the primary difference between...",
                    "In which situation would you...",
                    "What would happen if...",
                    "Which statement is true about...",
                    "What is the main purpose of...",
                    "How does X relate to Y?"
                )
            ),
            conceptVariations = mapOf()
        )
    }

    // Keep existing methods for compatibility
    fun getQuestionTypeInstructions(questionType: QuestionType, subject: Subject): Pair<String, String> {
        return getVariedQuestionPrompt(questionType, subject, "", Difficulty.MEDIUM, 0)
    }

    fun getFallbackQuestion(questionType: QuestionType, subject: Subject, difficulty: Difficulty): Question {
        // Implementation remains the same but with more variety in the pool
        return getEmbeddedFallback(questionType, difficulty)
    }

    private fun getEmbeddedInstructions(questionType: QuestionType): Pair<String, String> {
        // Enhanced embedded instructions
        return when (questionType) {
            QuestionType.MULTIPLE_CHOICE -> Pair(
                """
                Create a unique multiple choice question with 4 options.
                Make it engaging and test real understanding.
                Avoid simple recall questions.
                """.trimIndent(),
                """{"question": "Sample question", "options": ["A", "B", "C", "D"], "correctAnswer": "A"}"""
            )
            else -> Pair(
                "Create a unique question that tests understanding.",
                """{"question": "Sample", "options": [], "correctAnswer": "Answer"}"""
            )
        }
    }

    private fun getEmbeddedFallback(questionType: QuestionType, difficulty: Difficulty): Question {
        // Pool of fallback questions for each type
        val fallbackPools = mapOf(
            QuestionType.MULTIPLE_CHOICE to listOf(
                Question(
                    questionText = "What is the result of 5 + 3?",
                    questionType = questionType,
                    options = listOf("6", "7", "8", "9"),
                    correctAnswer = "8",
                    explanation = "5 + 3 equals 8.",
                    difficulty = difficulty
                ),
                Question(
                    questionText = "Which season comes after summer?",
                    questionType = questionType,
                    options = listOf("Spring", "Fall", "Winter", "Summer"),
                    correctAnswer = "Fall",
                    explanation = "The seasons go: Spring, Summer, Fall, Winter.",
                    difficulty = difficulty
                )
            )
        )

        val pool = fallbackPools[questionType] ?: listOf()
        return if (pool.isNotEmpty()) {
            pool.random()
        } else {
            Question(
                questionText = "Sample question",
                questionType = questionType,
                options = emptyList(),
                correctAnswer = "Sample answer",
                explanation = "This is a fallback question.",
                difficulty = difficulty
            )
        }
    }
}