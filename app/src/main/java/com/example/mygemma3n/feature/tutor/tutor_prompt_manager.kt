package com.example.mygemma3n.feature.tutor

import android.content.Context
import com.example.mygemma3n.data.ExplanationDepth
import com.example.mygemma3n.data.LearningStyle
import com.example.mygemma3n.feature.quiz.Difficulty
import com.example.mygemma3n.feature.quiz.Subject
import com.example.mygemma3n.shared_utilities.OfflineRAG
import com.google.gson.Gson
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext

@Singleton
class TutorPromptManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val promptTemplates = mutableMapOf<String, String>()
    private val promptCache = mutableMapOf<String, String>() // Cache computed prompts

    init {
        loadPromptTemplates()
        // Clear any cached prompts to ensure fresh instructions
        promptCache.clear()
    }

    data class EducatorConfig(
        val promptCustomizations: PromptCustomizations,
        val responseRules: ResponseRules
    )

    data class PromptCustomizations(
        val gradeLanguage: Map<String, GradeLanguageConfig>,
        val subjectMetaphors: Map<String, SubjectMetaphorConfig>,
        val encouragementPhrases: Map<String, List<String>>,
        val questionStarters: Map<String, List<String>>
    )

    data class GradeLanguageConfig(
        val vocabulary: List<String>,
        val sentenceLength: Int,
        val conceptsPerResponse: Int
    )

    data class SubjectMetaphorConfig(
        val metaphors: List<String>,
        val examples: List<String>
    )

    data class ResponseRules(
        val maxResponseLength: Map<String, Int>,
        val useExamples: Map<String, String>,
        val formalityLevel: Map<String, String>
    )

    private fun loadPromptTemplates() {
        val templates = listOf(
            "socratic_method",
            "concept_explanation",
            "problem_solving",
            "encouragement",
            "misconception_correction",
            "adaptive_hints"
        )

        templates.forEach { templateName ->
            promptTemplates[templateName] = loadTemplate("$templateName.txt")
        }
    }

    private fun loadTemplate(filename: String): String {
        return try {
            context.assets.open("tutor_prompts/$filename")
                .bufferedReader()
                .use { it.readText() }
        } catch (e: Exception) {
            Timber.w(e, "Failed to load prompt template: $filename")
            getDefaultTemplate(filename)
        }
    }

    /**
     * Get an adaptive prompt that can switch strategies based on student performance
     */
    fun getAdaptivePrompt(
        subject: OfflineRAG.Subject,
        concept: String,
        studentGrade: Int,
        studentQuestion: String,
        attemptNumber: Int,
        previousResponses: List<String> = emptyList()
    ): String {
        // Clear cache to ensure fresh prompts with updated instructions
        promptCache.clear()
        
        // Cache key for performance
        val cacheKey = "$subject-$concept-$studentGrade-$attemptNumber"

        return when (attemptNumber) {
            0 -> {
                // First attempt: Use Socratic method
                getSocraticPrompt(subject, concept, studentGrade, studentQuestion)
            }
            1 -> {
                // Second attempt: Mix Socratic with hints
                """
                ${getSocraticPrompt(subject, concept, studentGrade, studentQuestion)}
                
                Additional guidance: Since this is their second attempt, include a subtle hint that points them in the right direction without giving away the answer.
                """.trimIndent()
            }
            2 -> {
                // Third attempt: Switch to explanation with problem-solving
                """
                The student has attempted this twice. Time for a different approach.
                
                ${getConceptExplanationPrompt(
                    subject, concept, studentGrade,
                    LearningStyle.VERBAL, ExplanationDepth.SIMPLE
                )}
                
                After explaining, give them a simpler version of the problem to build confidence.
                """.trimIndent()
            }
            else -> {
                // Multiple attempts: Encouragement + step-by-step
                """
                ${getEncouragementPrompt(
                    "Learning $concept",
                    concept,
                    attemptNumber
                )}
                
                ${getProblemSolvingPrompt(
                    "practice",
                    subject,
                    Difficulty.EASY,
                    previousResponses.lastOrNull()
                )}
                """.trimIndent()
            }
        }
    }

    fun getSocraticPrompt(
        subject: OfflineRAG.Subject,
        concept: String,
        studentGrade: Int,
        studentQuestion: String
    ): String {
        val template = promptTemplates["socratic_method"] ?: DEFAULT_SOCRATIC
        val gradeAdjustment = getGradeAdjustment(studentGrade)

        return """
            $template
            
            Subject: $subject
            Concept: $concept
            Student Grade: $studentGrade
            Question: "$studentQuestion"
            
            $gradeAdjustment
            
            Remember: Guide with questions, don't give answers directly.
        """.trimIndent()
    }

    fun getConceptExplanationPrompt(
        subject: OfflineRAG.Subject,
        concept: String,
        studentGrade: Int,
        learningStyle: LearningStyle,
        depth: ExplanationDepth
    ): String {
        val template = promptTemplates["concept_explanation"] ?: DEFAULT_EXPLANATION
        val styleGuide = getLearningStyleGuide(learningStyle)
        val depthGuide = getDepthGuide(depth)
        val maxWords = getMaxWordsForGrade(studentGrade)

        return """
            $template
            
            Subject: $subject
            Concept: $concept
            Student Grade: $studentGrade
            
            $styleGuide
            $depthGuide
            
            !!MANDATORY!! RESPONSE MUST START WITH: "[Topic] is..." or "[Topic] means..."
            
            !!FORBIDDEN OPENINGS!! "Okay!", "Great!", "Well", "So", "Let's", "Here's", "To give you", "fascinating", "complex"
            
            WRONG EXAMPLE: "Okay! Great! Executive Leadership is a fascinating topic. To give you the best information..."
            RIGHT EXAMPLE: "Executive leadership is the practice of guiding and making decisions for organizations..."
            
            Total response: around $maxWords words. Be factual like a textbook definition.
            
            ${getGradeSpecificInstructions(studentGrade)}
        """.trimIndent()
    }

    fun getProblemSolvingPrompt(
        problemType: String,
        subject: OfflineRAG.Subject,
        difficulty: Difficulty,
        studentApproach: String? = null
    ): String {
        val template = promptTemplates["problem_solving"] ?: DEFAULT_PROBLEM_SOLVING
        val difficultyGuide = getDifficultyGuide(difficulty)

        return """
            $template
            
            Problem Type: $problemType
            Subject: $subject
            $difficultyGuide
            ${studentApproach?.let { "\nStudent's Approach: $it" } ?: ""}
            
            Focus on making the first step crystal clear.
        """.trimIndent()
    }

    fun getAdaptiveHintPrompt(
        problem: String,
        hintLevel: Int,
        studentProgress: String
    ): String {
        val hintsTemplate = promptTemplates["adaptive_hints"] ?: DEFAULT_HINTS
        val specificLevel = when (hintLevel.coerceIn(1, 5)) {
            1 -> "LEVEL 1 (General direction)"
            2 -> "LEVEL 2 (Concept hint)"
            3 -> "LEVEL 3 (First step)"
            4 -> "LEVEL 4 (Detailed guidance)"
            else -> "LEVEL 5 (Nearly complete)"
        }

        return """
            Use hint level: $specificLevel
            
            From template:
            $hintsTemplate
            
            Problem: $problem
            Student Progress: $studentProgress
            
            Provide ONLY the hint at the specified level.
        """.trimIndent()
    }

    fun getEncouragementPrompt(
        context: String,
        struggleArea: String? = null,
        previousAttempts: Int = 0
    ): String {
        val template = promptTemplates["encouragement"] ?: DEFAULT_ENCOURAGEMENT

        return """
            $template
            
            Context: $context
            ${struggleArea?.let { "Struggle Area: $it" } ?: ""}
            Previous Attempts: $previousAttempts
            
            Be genuine and specific in your encouragement.
        """.trimIndent()
    }

    fun getMisconceptionCorrectionPrompt(
        misconception: String,
        correctConcept: String,
        subject: Subject
    ): String {
        val template = promptTemplates["misconception_correction"] ?: DEFAULT_CORRECTION

        return """
            $template
            
            Subject: $subject
            Student's Misconception: "$misconception"
            Correct Concept: $correctConcept
            
            Be gentle and supportive while being clear about the correction.
        """.trimIndent()
    }

    // Helper methods for grade-specific adjustments
    private fun getGradeAdjustment(grade: Int): String {
        return when (grade) {
            in 1..3 -> "Use very simple words. Ask questions a young child can answer."
            in 4..6 -> "Use elementary-level vocabulary. Questions should be concrete."
            in 7..9 -> "Use middle school vocabulary. Questions can be more abstract."
            else -> "Use appropriate high school vocabulary. Questions can be complex."
        }
    }

    private fun getGradeSpecificInstructions(grade: Int): String {
        return when (grade) {
            in 1..3 -> """
                GRADES 1-3 (Elementary Early):
                - MUST START: "[Topic] is..." - NO other opening
                - Use familiar objects and simple words (under 6 letters when possible)
                - Structure: Direct answer → Simple explanation → One concrete example from their world
                - NO follow-up questions - just clear information
                - Example: "A dog is an animal that lives with people. Dogs are pets that bark and wag their tails. You might see dogs at the park."
            """.trimIndent()

            in 4..6 -> """
                GRADES 4-6 (Elementary Late):
                - MUST START: "[Topic] is..." - NO other opening
                - Structure: Direct answer → Clear explanation with 2 key points → One simple real-world example
                - NO critical thinking questions - be factual like a textbook
                - Use clear vocabulary that 4th-6th graders understand
                - Example: "Forms of government are different ways countries organize their political systems. There are democracies where people vote, and monarchies where kings or queens rule. The United States is a democracy."
            """.trimIndent()

            in 7..8 -> """
                GRADES 7-8 (Middle School):
                - MUST START: "[Topic] is..." - NO other opening
                - Structure: Direct answer → Structured explanation with 2-3 key points → One example with connection → ONE simple follow-up question
                - Can use some technical terms with brief explanations
                - Connect to their world and interests
                - Example question: "How might this apply to situations you've seen?"
            """.trimIndent()

            else -> """
                GRADES 9-12 (High School):
                - MUST START: "[Topic] is..." - NO other opening
                - Structure: Direct answer → Comprehensive explanation with multiple aspects → Real-world applications → 1-2 analytical questions
                - Use full academic vocabulary
                - Encourage critical thinking and analysis
                - Example questions: "What factors might influence this?" "How do you think this concept applies to current events?"
            """.trimIndent()
        }
    }

    private fun getLearningStyleGuide(style: LearningStyle): String {
        return "Learning Style Adaptation: " + when (style) {
            LearningStyle.VISUAL -> "Use spatial words (see, look, picture). Suggest drawing/diagrams."
            LearningStyle.VERBAL -> "Use discussion words (tell, explain, describe). Encourage talking through."
            LearningStyle.LOGICAL -> "Use sequence words (first, then, therefore). Show logical connections."
            LearningStyle.KINESTHETIC -> "Use action words (do, move, build). Suggest hands-on activities."
        }
    }

    private fun getDepthGuide(depth: ExplanationDepth): String {
        return "Explanation Depth: " + when (depth) {
            ExplanationDepth.SIMPLE -> "Surface level only. One main idea."
            ExplanationDepth.STANDARD -> "Main idea plus 2-3 supporting details."
            ExplanationDepth.DETAILED -> "Comprehensive with examples and connections."
        }
    }

    private fun getDifficultyGuide(difficulty: Difficulty): String {
        return "Support Level: " + when (difficulty) {
            Difficulty.EASY -> "Maximum support. Work through most steps together."
            Difficulty.MEDIUM -> "Moderate support. Guide through the first half."
            Difficulty.HARD -> "Minimal support. Only hints when stuck."
            Difficulty.ADAPTIVE -> "Adjust based on their responses."
        }
    }

    private fun getMaxWordsForGrade(grade: Int): Int {
        return when (grade) {
            in 1..3 -> 40   // Elementary Early: simple responses
            in 4..6 -> 60   // Elementary Late: clear explanations
            in 7..8 -> 80   // Middle School: structured with follow-up
            else -> 120     // High School: comprehensive with critical thinking
        }
    }

    companion object {
        // Fallback templates
        private const val DEFAULT_SOCRATIC = """
            !!CRITICAL!! ALL GRADES: START WITH "[Topic] is..." or "[Topic] means..."
            
            !!FORBIDDEN!! "Okay!", "Great!", "Well", "So", "Let's", "Here's", "I can definitely help"
            
            Grade-appropriate approach:
            - Grades 1-6: Direct answer + explanation, NO questions back
            - Grades 7-8: Direct answer + explanation + ONE simple follow-up question  
            - Grades 9-12: Direct answer + explanation + 1-2 analytical questions
            
            Always be factual and direct like a textbook definition.
        """

        private const val DEFAULT_EXPLANATION = """
            !!CRITICAL!! ALL GRADES: START WITH "[Topic] is..." or "[Topic] means..."
            
            !!FORBIDDEN!! "Okay!", "Great!", "Well", "So", "Let's", "Here's", "To give you"
            
            Grade-scaled structure:
            - Grades 1-3: Direct answer → Simple explanation → Concrete example
            - Grades 4-6: Direct answer → Clear explanation (2 points) → Real-world example
            - Grades 7-8: Direct answer → Structured explanation → Example → One follow-up question
            - Grades 9-12: Direct answer → Comprehensive explanation → Applications → Analytical questions
            
            Be factual like a textbook, scaled to grade level.
        """

        private const val DEFAULT_PROBLEM_SOLVING = """
            You are helping a student solve a problem.
            Guide them step-by-step without doing it for them.
            Encourage their thinking process.
        """

        private const val DEFAULT_ENCOURAGEMENT = """
            Provide warm, specific encouragement.
            Acknowledge effort and normalize struggle.
            Offer one helpful strategy.
        """

        private const val DEFAULT_CORRECTION = """
            Gently correct the misconception.
            Find what they understood correctly first.
            Explain the right concept simply.
        """

        private const val DEFAULT_HINTS = """
            Provide progressive hints at different levels.
            Start general, become more specific.
            Never give the complete answer.
        """
    }

    private fun getDefaultTemplate(filename: String): String {
        return when (filename) {
            "socratic_method.txt" -> DEFAULT_SOCRATIC
            // Map to the filename directly; when the loader fails to open the file,
            // it calls getDefaultTemplate with just "concept_explanation.txt".
            "concept_explanation.txt" -> DEFAULT_EXPLANATION
            "problem_solving.txt" -> DEFAULT_PROBLEM_SOLVING
            "encouragement.txt" -> DEFAULT_ENCOURAGEMENT
            "misconception_correction.txt" -> DEFAULT_CORRECTION
            "adaptive_hints.txt" -> DEFAULT_HINTS
            else -> "You are a helpful tutor. Assist the student with their learning."
        }
    }

    private var educatorConfig: EducatorConfig? = null
    private val gson = Gson()

    private fun loadEducatorConfig() {
        try {
            val configJson = context.assets.open("tutor_prompts/educator_config.json")
                .bufferedReader()
                .use { it.readText() }

            educatorConfig = gson.fromJson(configJson, EducatorConfig::class.java)

            // Try to load local customizations
            try {
                val localJson = context.assets.open("tutor_prompts/local_customizations.json")
                    .bufferedReader()
                    .use { it.readText() }
                // Merge local customizations
                // In production, this would be more sophisticated
            } catch (e: Exception) {
                Timber.d("No local customizations found")
            }

        } catch (e: Exception) {
            Timber.e(e, "Failed to load educator config")
        }
    }

    fun getCustomizedPrompt(
        basePrompt: String,
        grade: Int,
        subject: OfflineRAG.Subject
    ): String {
        val config = educatorConfig ?: return basePrompt

        val gradeRange = when (grade) {
            in 1..3 -> "1-3"
            in 4..6 -> "4-6"
            in 7..9 -> "7-9"
            else -> "10-12"
        }

        val gradeConfig = config.promptCustomizations.gradeLanguage[gradeRange]
        val subjectConfig = config.promptCustomizations.subjectMetaphors[subject.name]
        val rules = config.responseRules

        val customizations = mutableListOf<String>()

        // Add vocabulary guidance
        gradeConfig?.vocabulary?.let { vocab ->
            customizations.add("Use these grade-appropriate words: ${vocab.joinToString(", ")}")
        }

        // Add metaphor suggestions
        subjectConfig?.let {
            customizations.add("Consider these metaphors: ${it.metaphors.random()}")
            customizations.add("Use examples from: ${it.examples.random()}")
        }

        // Add response rules
        rules.maxResponseLength[gradeRange]?.let { maxLength ->
            customizations.add("Keep response under $maxLength words")
        }

        rules.formalityLevel[gradeRange]?.let { formality ->
            customizations.add("Formality level: $formality")
        }

        return if (customizations.isNotEmpty()) {
            """
        $basePrompt
        
        EDUCATOR CUSTOMIZATIONS:
        ${customizations.joinToString("\n")}
        """.trimIndent()
        } else {
            basePrompt
        }
    }

    // Helper method to get random encouragement
    fun getRandomEncouragement(type: String): String {
        val phrases = educatorConfig?.promptCustomizations?.encouragementPhrases?.get(type)
        return phrases?.random() ?: when (type) {
            "effort" -> "Good effort!"
            "progress" -> "You're making progress!"
            "struggle" -> "It's okay to find this challenging!"
            else -> "Keep going!"
        }
    }

    // Helper method to get question starters
    fun getQuestionStarter(type: String): String {
        val starters = educatorConfig?.promptCustomizations?.questionStarters?.get(type)
        return starters?.random() ?: when (type) {
            "socratic" -> "What do you think..."
            "clarifying" -> "Can you explain..."
            "extending" -> "How would this apply to..."
            else -> "Let's think about..."
        }
    }
}
