package com.example.mygemma3n.feature.tutor

import android.content.Context
import com.example.mygemma3n.data.ExplanationDepth
import com.example.mygemma3n.data.LearningStyle
import com.example.mygemma3n.feature.quiz.Difficulty
import com.example.mygemma3n.feature.quiz.Subject
import com.example.mygemma3n.shared_utilities.OfflineRAG
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext


// feature/tutor/prompts/TutorPromptManager.kt

@Singleton
class TutorPromptManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val promptTemplates = mutableMapOf<String, String>()

    init {
        loadPromptTemplates()
    }

    private fun loadPromptTemplates() {
        // Load from assets/tutor_prompts/
        val templates = mapOf(
            "socratic_method" to loadTemplate("socratic_method.txt"),
            "concept_explanation" to loadTemplate("concept_explanation.txt"),
            "problem_solving" to loadTemplate("problem_solving.txt"),
            "encouragement" to loadTemplate("encouragement.txt"),
            "misconception_correction" to loadTemplate("misconception_correction.txt")
        )
        promptTemplates.putAll(templates)
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

    fun getSocraticPrompt(
        subject: OfflineRAG.Subject,
        concept: String,
        studentGrade: Int,
        studentQuestion: String
    ): String {
        val template = promptTemplates["socratic_method"] ?: DEFAULT_SOCRATIC

        return template
            .replace("{SUBJECT}", subject.name)
            .replace("{CONCEPT}", concept)
            .replace("{GRADE}", studentGrade.toString())
            .replace("{QUESTION}", studentQuestion)
            .plus("\n\nGuide the student to discover the answer through questions, don't give the answer directly.")
    }


    fun getConceptExplanationPrompt(
        subject: OfflineRAG.Subject,
        concept: String,
        studentGrade: Int,
        learningStyle: LearningStyle,
        depth: ExplanationDepth
    ): String {
        val baseTemplate = promptTemplates["concept_explanation"] ?: DEFAULT_EXPLANATION

        val styleGuide = when (learningStyle) {
            LearningStyle.VISUAL -> "Use visual analogies and suggest diagrams."
            LearningStyle.VERBAL -> "Use clear verbal explanations with examples."
            LearningStyle.LOGICAL -> "Use step-by-step logical reasoning."
            LearningStyle.KINESTHETIC -> "Suggest hands-on activities and real applications."
        }

        val depthGuide = when (depth) {
            ExplanationDepth.SIMPLE -> "Keep it very simple with basic vocabulary."
            ExplanationDepth.STANDARD -> "Use grade-appropriate language and examples."
            ExplanationDepth.DETAILED -> "Provide comprehensive explanation with multiple examples."
        }

        // Add character limits based on grade level
        val maxCharacters = when (studentGrade) {
            in 1..3 -> 200
            in 4..6 -> 300
            in 7..9 -> 400
            else -> 500
        }

        return """
        $baseTemplate
        
        Subject: $subject
        Concept: $concept
        Student Grade: $studentGrade
        
        Style Guide: $styleGuide
        Depth: $depthGuide
        
        CRITICAL INSTRUCTIONS:
        1. Keep response under $maxCharacters characters
        2. Structure your answer as:
           - First: List key components/facts (if applicable)
           - Then: Brief explanation
           - Finally: One simple example
        3. For lists (like food groups), name ALL items FIRST before explaining
        4. Use bullet points (•) for lists within the text
        5. Avoid lengthy metaphors - use them sparingly
        
        Example format for balanced diet:
        "A balanced diet has 6 main parts: • Carbs • Proteins • Fats • Vitamins • Minerals • Fiber
        
        These work together like LEGO bricks to build a strong body. Carbs give energy, proteins build muscles..."
    """.trimIndent()
    }

    fun getProblemSolvingPrompt(
        problemType: String,
        subject: OfflineRAG.Subject,
        difficulty: Difficulty,
        studentApproach: String? = null
    ): String {
        val template = promptTemplates["problem_solving"] ?: DEFAULT_PROBLEM_SOLVING

        val difficultyGuide = when (difficulty) {
            Difficulty.EASY -> "Provide detailed step-by-step guidance."
            Difficulty.MEDIUM -> "Give hints and check each step."
            Difficulty.HARD -> "Provide minimal hints, encourage independent thinking."
            Difficulty.ADAPTIVE -> "Adjust support based on student responses."
        }

        return """
            $template
            
            Problem Type: $problemType
            Subject: $subject
            Difficulty: $difficultyGuide
            ${studentApproach?.let { "Student's Approach: $it" } ?: ""}
            
            Help the student solve this step-by-step:
            1. Understand what's being asked
            2. Identify relevant concepts
            3. Plan the solution
            4. Execute the plan
            5. Check the answer
        """.trimIndent()
    }

    fun getEncouragementPrompt(
        context: String,
        struggleArea: String? = null,
        previousAttempts: Int = 0
    ): String {
        return """
            Student context: $context
            ${struggleArea?.let { "Area of difficulty: $it" } ?: ""}
            ${if (previousAttempts > 0) "Previous attempts: $previousAttempts" else ""}
            
            Provide encouraging feedback that:
            1. Acknowledges their effort
            2. Normalizes struggle as part of learning
            3. Offers a specific strategy to try
            4. Expresses confidence in their ability
            
            Keep it brief, warm, and supportive.
        """.trimIndent()
    }

    fun getMisconceptionCorrectionPrompt(
        misconception: String,
        correctConcept: String,
        subject: Subject
    ): String {
        return """
            Subject: $subject
            Student's Understanding: $misconception
            Correct Concept: $correctConcept
            
            Gently correct this misconception by:
            1. Acknowledging what they got right
            2. Identifying where the confusion might be
            3. Explaining the correct concept clearly
            4. Providing a memorable example
            5. Checking their new understanding
            
            Be supportive and avoid making the student feel wrong.
        """.trimIndent()
    }

    fun getAdaptiveHintPrompt(
        problem: String,
        hintLevel: Int,
        studentProgress: String
    ): String {
        val hintGuidance = when (hintLevel) {
            1 -> "Give a very general hint about the approach."
            2 -> "Hint at the specific concept or formula needed."
            3 -> "Show the first step of the solution."
            4 -> "Provide detailed guidance for the next step."
            else -> "Guide them to the complete solution."
        }

        return """
            Problem: $problem
            Student's Progress: $studentProgress
            Hint Level: $hintLevel
            
            $hintGuidance
            
            Make the hint helpful but not too revealing.
            Encourage the student to think about why this approach works.
        """.trimIndent()
    }

    companion object {
        private const val DEFAULT_SOCRATIC = """
            You are a tutor using the Socratic method.
            Ask guiding questions to help the student discover the answer.
            Never give the answer directly.
        """

        private const val DEFAULT_EXPLANATION = """
            You are explaining a concept to a student.
            Use clear, age-appropriate language.
            Provide examples and check understanding.
        """

        private const val DEFAULT_PROBLEM_SOLVING = """
            You are helping a student solve a problem.
            Guide them step-by-step without doing it for them.
            Encourage their thinking process.
        """
    }

    private fun getDefaultTemplate(filename: String): String {
        return when (filename) {
            "socratic_method.txt" -> DEFAULT_SOCRATIC
            "concept_explanation.txt" -> DEFAULT_EXPLANATION
            "problem_solving.txt" -> DEFAULT_PROBLEM_SOLVING
            else -> "You are a helpful tutor. Assist the student with their learning."
        }
    }
}