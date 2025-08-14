package com.example.mygemma3n.feature.quiz

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Generates prompts for AI models to create quiz questions.
 * Handles different question types, difficulty levels, and learning contexts.
 */
@Singleton
class QuizPromptGenerator @Inject constructor() {
    
    /**
     * Create a structured prompt for offline question generation
     */
    fun createStructuredPrompt(
        questionType: QuestionType,
        subject: Subject,
        topic: String,
        difficulty: Difficulty,
        learnerProfile: LearnerProfile?,
        previousQuestions: List<String> = emptyList(),
        attemptNumber: Int = 0
    ): String {
        val difficultyContext = getComplexityDescriptor(difficulty)
        val (instructions, format) = getQuestionTypeInstructions(questionType)
        val avoidancePattern = getAvoidancePatterns(previousQuestions)
        
        val learnerContext = learnerProfile?.let { profile ->
            """
            LEARNER PROFILE:
            - Total Questions Answered: ${profile.totalQuestionsAnswered}
            - Mastered Concepts: ${profile.masteredConcepts.take(5).joinToString(", ")}
            - Strengths by Subject: ${profile.strengthsBySubject.entries.take(3).joinToString { "${it.key}: ${(it.value * 100).toInt()}%" }}
            
            """.trimIndent()
        } ?: ""
        
        return """
        You are an expert educational content creator specializing in $subject.
        
        TASK: Create ONE $difficultyContext ${questionType.name.lowercase().replace('_', ' ')} question.
        
        CONTEXT:
        - Subject: $subject
        - Topic: $topic
        - Difficulty: $difficulty
        
        $learnerContext
        
        REQUIREMENTS:
        $instructions
        $avoidancePattern
        
        CRITICAL RULES:
        - Question must be directly related to $topic
        - Ensure $difficultyContext complexity level
        - Provide clear, educational explanations
        - Use proper grammar and clear language
        - Make content engaging and relevant
        
        OUTPUT FORMAT:
        Return ONLY valid JSON in this exact format (no markdown, no extra text):
        $format
        
        Generate the question now:
        """.trimIndent()
    }
    
    /**
     * Generate avoidance patterns from previous questions
     */
    private fun getAvoidancePatterns(previousQuestions: List<String>): String {
        if (previousQuestions.isEmpty()) return ""
        
        val recentQuestions = previousQuestions.takeLast(3)
        val patterns = recentQuestions.joinToString("\n") { "- ${it.take(60)}..." }
        
        return """
        CRITICAL AVOIDANCE:
        Create a question completely different from these recent ones:
        $patterns
        
        - Use different question formats and approaches
        - Focus on different aspects of the topic
        - Employ different cognitive skills and thinking processes
        """.trimIndent()
    }
    
    /**
     * Get complexity descriptor for difficulty level
     */
    private fun getComplexityDescriptor(difficulty: Difficulty): String {
        return when (difficulty) {
            Difficulty.EASY -> "beginner-level, foundational"
            Difficulty.MEDIUM -> "intermediate-level, application-focused"
            Difficulty.HARD -> "advanced-level, synthesis and analysis"
            Difficulty.ADAPTIVE -> "dynamically-adjusted, personalized"
        }
    }
    
    /**
     * Get question type instructions and format
     */
    private fun getQuestionTypeInstructions(questionType: QuestionType): Pair<String, String> {
        return when (questionType) {
            QuestionType.MULTIPLE_CHOICE -> Pair(
                """
                - Create exactly 4 answer options (A, B, C, D)
                - Make one option clearly correct
                - Create plausible but incorrect distractors
                - Ensure options are similar in length and structure
                """.trimIndent(),
                """
                {
                  "question": "Your complete question here?",
                  "type": "MULTIPLE_CHOICE",
                  "options": ["Option A", "Option B", "Option C", "Option D"],
                  "correctAnswer": "Option A",
                  "explanation": "Clear explanation of why this is correct"
                }
                """.trimIndent()
            )
            
            QuestionType.TRUE_FALSE -> Pair(
                """
                - Create a clear statement that can be definitively true or false
                - Avoid ambiguous or trick statements
                - Test important concepts, not trivial details
                """.trimIndent(),
                """
                {
                  "question": "Your true/false statement here.",
                  "type": "TRUE_FALSE",
                  "options": ["True", "False"],
                  "correctAnswer": "True",
                  "explanation": "Explanation of why this statement is true or false"
                }
                """.trimIndent()
            )
            
            QuestionType.FILL_IN_BLANK -> Pair(
                """
                - Use underscores (___) to indicate the blank
                - Place the blank at a key concept or term
                - Provide enough context for students to determine the answer
                """.trimIndent(),
                """
                {
                  "question": "Complete sentence with _____ for the blank.",
                  "type": "FILL_IN_BLANK",
                  "correctAnswer": "word or phrase that fills the blank",
                  "explanation": "Why this answer is correct"
                }
                """.trimIndent()
            )
            
            QuestionType.SHORT_ANSWER -> Pair(
                """
                - Ask open-ended questions that require explanation
                - Expect answers of 1-3 sentences
                - Focus on understanding and application
                """.trimIndent(),
                """
                {
                  "question": "Your open-ended question here?",
                  "type": "SHORT_ANSWER",
                  "correctAnswer": "Sample correct answer",
                  "explanation": "What makes a good answer to this question"
                }
                """.trimIndent()
            )
            
            QuestionType.MATCHING -> Pair(
                """
                - Create items to match with their corresponding pairs
                - Use clear, unambiguous items
                - Ensure one-to-one correspondence
                """.trimIndent(),
                """
                {
                  "question": "Match the following items.",
                  "type": "MATCHING",
                  "options": ["Item 1", "Item 2", "Item 3", "Match A", "Match B", "Match C"],
                  "correctAnswer": "1-A, 2-B, 3-C",
                  "explanation": "Explanation of the correct matches"
                }
                """.trimIndent()
            )
        }
    }
}