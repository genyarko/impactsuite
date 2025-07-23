package com.example.mygemma3n.feature.tutor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mygemma3n.data.ChatRepository
import com.example.mygemma3n.data.LearningStyle
import com.example.mygemma3n.data.StudentProfileEntity
import com.example.mygemma3n.data.TutorRepository
import com.example.mygemma3n.data.TutorSessionType
import com.example.mygemma3n.data.UnifiedGemmaService
import com.example.mygemma3n.feature.caption.SpeechRecognitionService
import com.example.mygemma3n.shared_utilities.OfflineRAG
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
@HiltViewModel
class TutorViewModel @Inject constructor(
    private val tutorRepository: TutorRepository,
    private val chatRepository: ChatRepository,
    private val gemmaService: UnifiedGemmaService,
    private val offlineRAG: OfflineRAG,
    private val speechService: SpeechRecognitionService,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _state = MutableStateFlow(TutorState())
    val state: StateFlow<TutorState> = _state.asStateFlow()

    private var currentStudent: StudentProfileEntity? = null
    private var currentSessionId: String? = null
    private var conversationContext = mutableListOf<String>()

    data class TutorState(
        val isLoading: Boolean = false,
        val currentSubject: OfflineRAG.Subject? = null,
        val sessionType: TutorSessionType? = null,
        val messages: List<TutorMessage> = emptyList(),
        val isListening: Boolean = false,
        val currentTopic: String = "",
        val suggestedTopics: List<String> = emptyList(),
        val error: String? = null,
        val studentProfile: StudentProfileEntity? = null,
        val conceptMastery: Map<String, Float> = emptyMap()
    )

    data class TutorMessage(
        val content: String,
        val isUser: Boolean,
        val timestamp: Long = System.currentTimeMillis(),
        val metadata: MessageMetadata? = null
    )

    data class MessageMetadata(
        val concept: String? = null,
        val difficulty: String? = null,
        val explanationType: String? = null
    )

    fun initializeStudent(name: String, gradeLevel: Int) = viewModelScope.launch {
        currentStudent = tutorRepository.createOrGetStudentProfile(name, gradeLevel)
        _state.update { it.copy(studentProfile = currentStudent) }
        loadStudentMastery()           // still suspend – runs inside this coroutine
    }


    fun startTutorSession(subject: OfflineRAG.Subject, sessionType: TutorSessionType, topic: String) {
        viewModelScope.launch {
            try {
                _state.update { it.copy(
                    isLoading = true,
                    currentSubject = subject,
                    sessionType = sessionType,
                    currentTopic = topic
                )}

                currentStudent?.let { student ->
                    currentSessionId = tutorRepository.startTutorSession(
                        student.id,
                        subject,
                        sessionType,
                        topic
                    )

                    // Generate welcome message based on session type
                    val welcomeMessage = generateWelcomeMessage(
                        student,
                        subject,
                        sessionType,
                        topic
                    )

                    addTutorMessage(welcomeMessage, isUser = false)
                }

                _state.update { it.copy(isLoading = false) }

            } catch (e: Exception) {
                _state.update { it.copy(
                    isLoading = false,
                    error = "Failed to start session: ${e.message}"
                )}
            }
        }
    }

    fun processUserInput(input: String) = viewModelScope.launch {
        addTutorMessage(input, isUser = true)
        _state.update { it.copy(isLoading = true) }

        try {
            val student = currentStudent ?: throw IllegalStateException("No student profile")
            val subject = _state.value.currentSubject ?: throw IllegalStateException("No subject selected")

            // Check for structured response first
            val structuredResponse = generateStructuredResponse(student, input, subject)

            val response = if (structuredResponse != null) {
                structuredResponse
            } else {
                // Fall back to AI generation
                val studentNeed = analyzeStudentInput(input)
                val relevantContent = offlineRAG.queryWithContext(
                    query = input,
                    subject = subject
                )

                generateTutorResponse(
                    student = student,
                    userInput = input,
                    studentNeed = studentNeed,
                    relevantContent = relevantContent,
                    sessionType = _state.value.sessionType ?: TutorSessionType.CONCEPT_EXPLANATION,
                    subject = subject
                )
            }

            addTutorMessage(response, isUser = false)

        } catch (e: Exception) {
            _state.update { it.copy(
                isLoading = false,
                error = "Error: ${e.message}"
            )}
        } finally {
            _state.update { it.copy(isLoading = false) }
        }
    }

    private suspend fun generateStructuredResponse(
        student: StudentProfileEntity,
        userInput: String,
        subject: OfflineRAG.Subject
    ): String? {
        // Check for common question patterns and provide structured responses
        return when {
            userInput.contains("balanced diet", ignoreCase = true) -> {
                TutorResponseFormatter.formatListResponse(
                    items = listOf(
                        "Carbohydrates", "Proteins", "Fats",
                        "Vitamins", "Minerals", "Fiber"
                    ),
                    intro = "A balanced diet has 6 main food groups:",
                    explanations = mapOf(
                        "Carbohydrates" to "Give you energy for activities",
                        "Proteins" to "Help your body grow and repair",
                        "Fats" to "Store energy and keep you warm"
                    ),
                    student = student
                )
            }

            userInput.contains("what is matter", ignoreCase = true) -> {
                """Matter is anything that has mass and takes up space.
            
            Examples:
            • Heavy things: bowling ball
            • Light things: feather  
            • Everything around you!
            
            If you can touch it or it fills space, it's matter!""".formatForTutor(student)
            }

            userInput.contains("area of", ignoreCase = true) &&
                    userInput.contains("trapezoid", ignoreCase = true) -> {
                TutorResponseFormatter.formatStepsResponse(
                    steps = listOf(
                        "Find the two parallel sides (bases)",
                        "Add the bases together",
                        "Multiply by the height",
                        "Divide by 2"
                    ),
                    intro = "To find the area of a trapezoid:",
                    student = student
                )
            }

            else -> null
        }
    }

    private suspend fun generateTutorResponse(
        student: StudentProfileEntity,
        userInput: String,
        studentNeed: StudentNeed,
        relevantContent: String,
        sessionType: TutorSessionType,
        subject: OfflineRAG.Subject
    ): String {
        val prompt = buildTutorPrompt(
            student = student,
            userInput = userInput,
            studentNeed = studentNeed,
            relevantContent = relevantContent,
            sessionType = sessionType,
            subject = subject,
            conversationHistory = conversationContext.takeLast(3) // Reduce context for brevity
        )

        val rawResponse = gemmaService.generateTextAsync(
            prompt,
            UnifiedGemmaService.GenerationConfig(
                maxTokens = 200, // Reduced from 400
                temperature = 0.4f, // Lower temperature for more focused responses
                topK = 30 // Slightly lower for more predictable outputs
            )
        )

        // Format the response for the student's grade level
        return rawResponse.formatForTutor(student)
    }

    // Replace the buildTutorPrompt method in TutorViewModel.kt

    private fun buildTutorPrompt(
        student: StudentProfileEntity,
        userInput: String,
        studentNeed: StudentNeed,
        relevantContent: String,
        sessionType: TutorSessionType,
        subject: OfflineRAG.Subject,
        conversationHistory: List<String>
    ): String {
        val roleContext = when (sessionType) {
            TutorSessionType.HOMEWORK_HELP ->
                "You are helping a grade ${student.gradeLevel} student with their homework. Guide them to find the answer themselves rather than giving it directly."

            TutorSessionType.CONCEPT_EXPLANATION ->
                "You are explaining ${subject.name} concepts to a grade ${student.gradeLevel} student. Use age-appropriate language and examples."

            TutorSessionType.EXAM_PREP ->
                "You are helping a grade ${student.gradeLevel} student prepare for an exam. Focus on key concepts and common question types."

            TutorSessionType.PRACTICE_PROBLEMS ->
                "You are providing practice problems for a grade ${student.gradeLevel} student. Start with their level and adjust difficulty based on performance."
        }

        val learningStyleGuide = when (student.preferredLearningStyle) {
            LearningStyle.VISUAL -> "Use visual descriptions and suggest diagrams when helpful."
            LearningStyle.VERBAL -> "Use clear explanations and encourage discussion."
            LearningStyle.LOGICAL -> "Use step-by-step reasoning and logical connections."
            LearningStyle.KINESTHETIC -> "Suggest hands-on activities and real-world applications."
        }

        // More aggressive word limits based on grade
        val maxWords = when (student.gradeLevel) {
            in 1..3  -> 30   // Very short for young kids
            in 4..6  -> 50   // Still concise for elementary
            in 7..9  -> 75   // Moderate for middle school
            in 10..12-> 100  // More detailed for high school
            else     -> 80
        }

        // Response format guidelines
        val formatGuide = when {
            userInput.contains("what is", ignoreCase = true) ||
                    userInput.contains("what are", ignoreCase = true) ->
                "Start with a one-sentence definition. If listing items, list ALL items first with bullet points (•), then explain briefly."

            userInput.contains("how", ignoreCase = true) ->
                "Give numbered steps (1. 2. 3.) Keep each step to one sentence."

            else -> "Be direct and conversational. No unnecessary metaphors."
        }

        return """
        $roleContext
        
        Learning style: $learningStyleGuide
        
        Context: $relevantContent
        
        Student asks: "$userInput"
        
        STRICT REQUIREMENTS:
        1. Maximum $maxWords words - BE CONCISE
        2. $formatGuide
        3. For grade ${student.gradeLevel}: Use simple vocabulary
        4. If explaining multiple concepts/items:
           - List them ALL first
           - Then give ONE sentence about each
        5. Avoid long analogies - use them sparingly or unless specifically requested
        6. End with encouragement if appropriate
        
        Remember: Quality over quantity. Clear and short is better than detailed and long.
    """.trimIndent()
    }

    // Helper classes and methods
    data class StudentNeed(
        val type: NeedType,
        val description: String
    )

    enum class NeedType {
        CLARIFICATION,
        STEP_BY_STEP_HELP,
        CONCEPT_EXPLANATION,
        PRACTICE,
        ENCOURAGEMENT
    }

    private fun analyzeStudentInput(input: String): StudentNeed {
        return when {
            input.contains("don't understand", ignoreCase = true) ||
                    input.contains("confused", ignoreCase = true) ->
                StudentNeed(NeedType.CLARIFICATION, "Student needs clarification")

            input.contains("how do I", ignoreCase = true) ||
                    input.contains("steps", ignoreCase = true) ->
                StudentNeed(NeedType.STEP_BY_STEP_HELP, "Student needs step-by-step guidance")

            input.contains("what is", ignoreCase = true) ||
                    input.contains("explain", ignoreCase = true) ->
                StudentNeed(NeedType.CONCEPT_EXPLANATION, "Student needs concept explanation")

            input.contains("practice", ignoreCase = true) ||
                    input.contains("more examples", ignoreCase = true) ->
                StudentNeed(NeedType.PRACTICE, "Student wants practice problems")

            else ->
                StudentNeed(NeedType.ENCOURAGEMENT, "Student needs encouragement and support")
        }
    }

    private fun addTutorMessage(content: String, isUser: Boolean) {
        val message = TutorMessage(content = content, isUser = isUser)
        _state.update { it.copy(messages = it.messages + message) }
        conversationContext.add("${if (isUser) "Student" else "Tutor"}: $content")
    }

    private fun extractConcepts(text: String): List<String> {
        // Simple concept extraction - in production, use NLP
        val conceptKeywords = listOf(
            "equation", "formula", "theorem", "principle", "rule",
            "definition", "concept", "method", "technique", "process"
        )

        return conceptKeywords.filter { text.contains(it, ignoreCase = true) }
    }

    private suspend fun updateConceptTracking(concepts: List<String>) {
        currentStudent?.let { student ->
            _state.value.currentSubject?.let { subject ->
                concepts.forEach { concept ->
                    tutorRepository.updateConceptMastery(
                        studentId = student.id,
                        subject = subject,
                        concept = concept,
                        gradeLevel = student.gradeLevel,
                        performanceIndicator = 0.7f // Default, adjust based on interaction
                    )
                }
            }
        }
    }

    private suspend fun loadStudentMastery() {
        currentStudent?.let { student ->
            _state.value.currentSubject?.let { subject ->
                tutorRepository.getStudentMasteryForSubject(student.id, subject)
                    .collect { masteryList ->
                        val masteryMap = masteryList.associate {
                            it.concept to it.masteryLevel
                        }
                        _state.update { it.copy(conceptMastery = masteryMap) }
                    }
            }
        }
    }

    private suspend fun generateWelcomeMessage(
        student: StudentProfileEntity,
        subject: OfflineRAG.Subject,
        sessionType: TutorSessionType,
        topic: String
    ): String {
        return when (sessionType) {
            TutorSessionType.HOMEWORK_HELP ->
                "Hi ${student.name}! I'm here to help you with your $subject homework on $topic. What specific problem are you working on?"

            TutorSessionType.CONCEPT_EXPLANATION ->
                "Hello ${student.name}! Let's explore $topic together. What would you like to understand better?"

            TutorSessionType.EXAM_PREP ->
                "Hi ${student.name}! Let's prepare for your $subject exam. We'll focus on $topic. What areas do you feel need the most review?"

            TutorSessionType.PRACTICE_PROBLEMS ->
                "Ready to practice some $subject problems, ${student.name}? We'll start with $topic. Should we begin with easy, medium, or challenging problems?"
        }
    }
}