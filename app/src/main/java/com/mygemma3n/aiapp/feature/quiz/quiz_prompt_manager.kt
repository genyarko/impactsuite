package com.mygemma3n.aiapp.feature.quiz

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
        val questionStems: Map<String, List<String>>? = null,
        val conceptVariations: Map<String, List<String>>? = null
    )

    data class QuestionTypeData(
        val instructions: String,
        val examples: Map<String, QuestionExample>, // Fixed: Single example per subject, not List
        val promptVariations: List<String>? = null
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

    fun getVariedQuestionPrompt(
        questionType: QuestionType,
        subject: Subject,
        topic: String,
        difficulty: Difficulty,
        attemptNumber: Int = 0
    ): Pair<String, String> {
        val data = promptData ?: createEnhancedDefaultPromptData()
        val typeData = data.questionTypes[questionType.name] ?: return getEmbeddedInstructions(questionType)

        // Get subject-specific example (now returns single example)
        val subjectName = if (subject == Subject.LANGUAGE_ARTS) "ENGLISH" else subject.name
        val example = typeData.examples[subjectName]
            ?: typeData.examples["SCIENCE"]
            ?: return getEmbeddedInstructions(questionType)

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

        val exampleJson = gson.toJson(example)

        // Track this prompt to avoid repetition
        val promptHash = (enhancedInstructions + topic + difficulty).hashCode()
        trackPromptUsage(promptHash)

        return Pair(enhancedInstructions, exampleJson)
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
                        "SCIENCE" to QuestionExample(
                            question = "What is the powerhouse of the cell?",
                            options = listOf("Nucleus", "Mitochondria", "Ribosome", "Cell membrane"),
                            correctAnswer = "Mitochondria",
                            explanation = "Mitochondria produce ATP through cellular respiration.",
                            hint = "This organelle is involved in energy production.",
                            conceptsCovered = listOf("cell-biology", "organelles")
                        ),
                        "MATHEMATICS" to QuestionExample(
                            question = "What is the slope of a horizontal line?",
                            options = listOf("1", "0", "Undefined", "-1"),
                            correctAnswer = "0",
                            explanation = "Horizontal lines have no vertical change, so slope = 0.",
                            hint = "Think about rise over run.",
                            conceptsCovered = listOf("linear-equations", "slope")
                        ),
                        "ENGLISH" to QuestionExample(
                            question = "Which literary device involves giving human qualities to non-human things?",
                            options = listOf("Metaphor", "Simile", "Personification", "Alliteration"),
                            correctAnswer = "Personification",
                            explanation = "Personification attributes human characteristics to non-human entities.",
                            hint = "Think about when we say 'the wind whispered' or 'the sun smiled'.",
                            conceptsCovered = listOf("literary-devices", "figurative-language")
                        ),
                        "HISTORY" to QuestionExample(
                            question = "Which event started World War I?",
                            options = listOf("Assassination of Archduke Franz Ferdinand", "Bombing of Pearl Harbor", "Invasion of Poland", "Russian Revolution"),
                            correctAnswer = "Assassination of Archduke Franz Ferdinand",
                            explanation = "The assassination in Sarajevo on June 28, 1914, triggered WWI.",
                            hint = "This event happened in Sarajevo in 1914.",
                            conceptsCovered = listOf("world-war-1", "european-history")
                        ),
                        "GEOGRAPHY" to QuestionExample(
                            question = "Which is the longest river in the world?",
                            options = listOf("Amazon River", "Nile River", "Yangtze River", "Mississippi River"),
                            correctAnswer = "Nile River",
                            explanation = "The Nile River in Africa is approximately 6,650 kilometers long.",
                            hint = "This river flows through northeastern Africa.",
                            conceptsCovered = listOf("rivers", "physical-geography")
                        ),
                        "LANGUAGE_ARTS" to QuestionExample(
                            question = "What is the main purpose of a persuasive essay?",
                            options = listOf("To entertain readers", "To convince readers of a viewpoint", "To provide instructions", "To describe a scene"),
                            correctAnswer = "To convince readers of a viewpoint",
                            explanation = "Persuasive essays aim to convince readers to accept an opinion.",
                            hint = "Think about what 'persuade' means.",
                            conceptsCovered = listOf("essay-types", "writing-purposes")
                        ),
                        "GENERAL" to QuestionExample(
                            question = "What is the smallest unit of matter?",
                            options = listOf("Molecule", "Atom", "Cell", "Particle"),
                            correctAnswer = "Atom",
                            explanation = "Atoms are the basic building blocks of matter.",
                            hint = "This unit makes up all elements.",
                            conceptsCovered = listOf("chemistry", "matter")
                        )
                    ),
                    promptVariations = listOf(
                        "Think of an interesting scenario to frame this question.",
                        "Consider what students often confuse about this topic.",
                        "Create a question that tests deep understanding, not memorization.",
                        "Frame this as a problem-solving question.",
                        "Use a real-world context for this question."
                    )
                ),
                "TRUE_FALSE" to QuestionTypeData(
                    instructions = "Create a TRUE/FALSE statement that is clearly true or false.",
                    examples = mapOf(
                        "SCIENCE" to QuestionExample(
                            question = "All mammals lay eggs.",
                            options = listOf("True", "False"),
                            correctAnswer = "False",
                            explanation = "Most mammals give birth to live young. Only monotremes lay eggs.",
                            hint = "Think about how most mammals reproduce.",
                            conceptsCovered = listOf("mammal-reproduction", "animal-classification")
                        ),
                        "GENERAL" to QuestionExample(
                            question = "The Earth is flat.",
                            options = listOf("True", "False"),
                            correctAnswer = "False",
                            explanation = "The Earth is a sphere, as proven by science.",
                            conceptsCovered = listOf("earth-science")
                        )
                    ),
                    promptVariations = listOf(
                        "Test a common misconception.",
                        "Create a statement with subtle complexity.",
                        "Focus on exceptions to general rules."
                    )
                ),
                "FILL_IN_BLANK" to QuestionTypeData(
                    instructions = "Create a sentence with ONE blank indicated by _____.",
                    examples = mapOf(
                        "SCIENCE" to QuestionExample(
                            question = "The process of water changing from liquid to gas is called _____.",
                            options = emptyList(),
                            correctAnswer = "evaporation",
                            explanation = "Evaporation occurs when water gains energy to become vapor.",
                            hint = "This happens when water is heated.",
                            conceptsCovered = listOf("states-of-matter", "water-cycle")
                        ),
                        "GENERAL" to QuestionExample(
                            question = "The capital of France is _____.",
                            options = emptyList(),
                            correctAnswer = "Paris",
                            explanation = "Paris is the capital and largest city of France.",
                            conceptsCovered = listOf("geography")
                        )
                    )
                ),
                "SHORT_ANSWER" to QuestionTypeData(
                    instructions = "Create an open-ended question requiring a brief explanation.",
                    examples = mapOf(
                        "SCIENCE" to QuestionExample(
                            question = "Describe the water cycle.",
                            options = emptyList(),
                            correctAnswer = "The water cycle is the continuous movement of water through evaporation, condensation, and precipitation",
                            explanation = "Water moves between Earth's surface and atmosphere continuously.",
                            conceptsCovered = listOf("water-cycle", "earth-science")
                        ),
                        "GENERAL" to QuestionExample(
                            question = "What is gravity?",
                            options = emptyList(),
                            correctAnswer = "Gravity is the force that attracts objects with mass toward each other",
                            explanation = "This force keeps us on Earth's surface.",
                            conceptsCovered = listOf("physics")
                        )
                    )
                )
            ),
            fallbackQuestions = createFallbackQuestions(),
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

    private fun createFallbackQuestions(): Map<String, Map<String, QuestionExample>> {
        return mapOf(
            "MULTIPLE_CHOICE" to mapOf(
                "SCIENCE" to QuestionExample(
                    question = "Which planet is closest to the Sun?",
                    options = listOf("Venus", "Mercury", "Earth", "Mars"),
                    correctAnswer = "Mercury",
                    conceptsCovered = listOf("solar-system")
                ),
                "MATHEMATICS" to QuestionExample(
                    question = "What is 15 + 27?",
                    options = listOf("40", "41", "42", "43"),
                    correctAnswer = "42",
                    conceptsCovered = listOf("addition")
                ),
                "GENERAL" to QuestionExample(
                    question = "How many days are in a week?",
                    options = listOf("5", "6", "7", "8"),
                    correctAnswer = "7",
                    conceptsCovered = listOf("time")
                )
            ),
            "TRUE_FALSE" to mapOf(
                "SCIENCE" to QuestionExample(
                    question = "The Earth revolves around the Sun.",
                    options = listOf("True", "False"),
                    correctAnswer = "True",
                    conceptsCovered = listOf("solar-system")
                ),
                "GENERAL" to QuestionExample(
                    question = "Water freezes at 0 degrees Celsius.",
                    options = listOf("True", "False"),
                    correctAnswer = "True",
                    conceptsCovered = listOf("temperature")
                )
            )
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