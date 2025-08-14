package com.mygemma3n.aiapp.feature.quiz

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages student profiles, curriculum data, and learning context for personalized quiz generation.
 * Handles grade-level appropriate content and curriculum-based topic loading.
 */
@Singleton
class QuizStudentManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val quizRepo: QuizRepository
) {
    
    private val _studentProfile = MutableStateFlow<StudentProfile?>(null)
    val studentProfile: StateFlow<StudentProfile?> = _studentProfile.asStateFlow()
    
    private val _curriculumTopics = MutableStateFlow<List<String>>(emptyList())
    val curriculumTopics: StateFlow<List<String>> = _curriculumTopics.asStateFlow()
    
    private val _isLoadingCurriculum = MutableStateFlow(false)
    val isLoadingCurriculum: StateFlow<Boolean> = _isLoadingCurriculum.asStateFlow()
    
    /**
     * Initialize student profile with basic information
     */
    suspend fun initializeStudent(
        name: String, 
        gradeLevel: Int, 
        country: String = ""
    ) = withContext(Dispatchers.IO) {
        try {
            Timber.d("Initializing student: $name, Grade: $gradeLevel, Country: $country")
            
            // Check if country has changed and clear history if needed
            val currentProfile = _studentProfile.value
            if (currentProfile?.country != country && country.isNotBlank()) {
                Timber.i("Country changed from ${currentProfile?.country} to $country - clearing quiz history")
                quizRepo.clearAllQuizzes()
            }
            
            // Create new student profile
            val profile = StudentProfile(
                name = name,
                gradeLevel = gradeLevel,
                country = country,
                preferredSubjects = emptyList(),
                strengths = emptyList(),
                weakAreas = emptyList(),
                learningStyle = LearningStyle.MIXED,
                createdAt = System.currentTimeMillis()
            )
            
            _studentProfile.value = profile
            
            // Load curriculum topics for the grade level
            loadCurriculumTopics(gradeLevel)
            
            Timber.d("Student profile initialized successfully")
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize student profile")
            throw e
        }
    }
    
    /**
     * Load curriculum topics for a specific grade level
     */
    suspend fun loadCurriculumTopics(gradeLevel: Int) = withContext(Dispatchers.IO) {
        try {
            _isLoadingCurriculum.value = true
            Timber.d("Loading curriculum topics for grade $gradeLevel")
            
            // Generate grade-appropriate topics based on common curriculum standards
            val topics = generateCurriculumTopics(gradeLevel)
            _curriculumTopics.value = topics
            
            Timber.d("Loaded ${topics.size} curriculum topics for grade $gradeLevel")
        } catch (e: Exception) {
            Timber.e(e, "Failed to load curriculum topics for grade $gradeLevel")
            // Provide fallback topics
            _curriculumTopics.value = getFallbackTopics(gradeLevel)
        } finally {
            _isLoadingCurriculum.value = false
        }
    }
    
    /**
     * Generate curriculum topics based on grade level and educational standards
     */
    private fun generateCurriculumTopics(gradeLevel: Int): List<String> {
        return when (gradeLevel) {
            in 1..2 -> listOf(
                // Early Elementary
                "Number Recognition", "Basic Addition", "Basic Subtraction", "Shapes and Patterns",
                "Letter Recognition", "Phonics", "Simple Words", "Reading Comprehension",
                "Animals and Habitats", "Weather and Seasons", "My Family", "Community Helpers",
                "Colors and Art", "Music and Movement", "Safety Rules", "Healthy Habits"
            )
            
            in 3..5 -> listOf(
                // Late Elementary  
                "Multiplication and Division", "Fractions", "Geometry", "Measurement",
                "Grammar and Punctuation", "Vocabulary Building", "Story Elements", "Poetry",
                "Plant and Animal Life Cycles", "Matter and Energy", "Earth and Space", "Scientific Method",
                "Maps and Geography", "Historical Timeline", "Cultural Studies", "Government Basics",
                "Art Techniques", "Physical Fitness", "Character Education", "Problem Solving"
            )
            
            in 6..8 -> listOf(
                // Middle School
                "Algebra Basics", "Ratios and Proportions", "Statistics and Probability", "Advanced Geometry",
                "Essay Writing", "Literary Analysis", "Research Skills", "Public Speaking",
                "Cell Biology", "Chemistry Basics", "Physics Principles", "Environmental Science",
                "World History", "Geography Skills", "Economics", "Civics and Government",
                "Digital Literacy", "Critical Thinking", "Social Issues", "Career Exploration"
            )
            
            in 9..12 -> listOf(
                // High School
                "Advanced Algebra", "Calculus", "Statistics", "Trigonometry",
                "Creative Writing", "World Literature", "Rhetoric", "Media Analysis",
                "Biology", "Chemistry", "Physics", "Advanced Sciences",
                "World History", "Government", "Psychology", "Philosophy",
                "Computer Science", "Engineering", "Business", "Life Skills"
            )
            
            else -> getFallbackTopics(gradeLevel)
        }
    }
    
    /**
     * Get fallback topics when grade level is unusual or loading fails
     */
    private fun getFallbackTopics(gradeLevel: Int): List<String> {
        return listOf(
            "Reading Comprehension", "Math Problem Solving", "Science Exploration",
            "Social Studies", "Critical Thinking", "Creative Expression",
            "Technology Skills", "Physical Education", "Art and Music",
            "Life Skills", "Study Strategies", "Communication"
        )
    }
    
    /**
     * Update student's learning preferences based on quiz performance
     */
    suspend fun updateLearningPreferences(
        preferredSubjects: List<Subject>,
        strengths: List<String>,
        weakAreas: List<String>,
        learningStyle: LearningStyle
    ) {
        val currentProfile = _studentProfile.value ?: return
        
        val updatedProfile = currentProfile.copy(
            preferredSubjects = preferredSubjects,
            strengths = strengths,
            weakAreas = weakAreas,
            learningStyle = learningStyle,
            lastUpdated = System.currentTimeMillis()
        )
        
        _studentProfile.value = updatedProfile
        
        Timber.d("Updated student learning preferences: ${preferredSubjects.size} subjects, ${strengths.size} strengths")
    }
    
    /**
     * Get age-appropriate content guidelines for the current student
     */
    fun getContentGuidelines(): ContentGuidelines {
        val gradeLevel = _studentProfile.value?.gradeLevel ?: 6
        
        return when (gradeLevel) {
            in 1..2 -> ContentGuidelines(
                vocabularyLevel = VocabularyLevel.SIMPLE,
                conceptComplexity = ConceptComplexity.BASIC,
                attentionSpan = AttentionSpan.SHORT,
                preferredFormats = listOf("visual", "interactive", "story-based"),
                avoidTopics = listOf("abstract concepts", "complex reasoning")
            )
            
            in 3..5 -> ContentGuidelines(
                vocabularyLevel = VocabularyLevel.ELEMENTARY,
                conceptComplexity = ConceptComplexity.INTERMEDIATE,
                attentionSpan = AttentionSpan.MEDIUM,
                preferredFormats = listOf("hands-on", "collaborative", "project-based"),
                avoidTopics = listOf("highly abstract", "controversial")
            )
            
            in 6..8 -> ContentGuidelines(
                vocabularyLevel = VocabularyLevel.INTERMEDIATE,
                conceptComplexity = ConceptComplexity.ADVANCED,
                attentionSpan = AttentionSpan.MEDIUM,
                preferredFormats = listOf("analytical", "discussion", "research"),
                avoidTopics = listOf("inappropriate content")
            )
            
            else -> ContentGuidelines(
                vocabularyLevel = VocabularyLevel.ADVANCED,
                conceptComplexity = ConceptComplexity.EXPERT,
                attentionSpan = AttentionSpan.LONG,
                preferredFormats = listOf("analytical", "critical thinking", "independent"),
                avoidTopics = emptyList()
            )
        }
    }
    
    /**
     * Get recommended difficulty level based on student profile and subject performance
     */
    fun getRecommendedDifficulty(subject: Subject): Difficulty {
        val profile = _studentProfile.value
        val guidelines = getContentGuidelines()
        
        return when {
            profile?.preferredSubjects?.contains(subject) == true -> {
                // Student likes this subject, can handle higher difficulty
                when (guidelines.conceptComplexity) {
                    ConceptComplexity.BASIC -> Difficulty.EASY
                    ConceptComplexity.INTERMEDIATE -> Difficulty.MEDIUM
                    ConceptComplexity.ADVANCED -> Difficulty.MEDIUM
                    ConceptComplexity.EXPERT -> Difficulty.HARD
                }
            }
            profile?.weakAreas?.any { it.contains(subject.name, ignoreCase = true) } == true -> {
                // This is a weak area, use easier difficulty
                Difficulty.EASY
            }
            else -> {
                // Default based on grade level
                when (guidelines.conceptComplexity) {
                    ConceptComplexity.BASIC -> Difficulty.EASY
                    ConceptComplexity.INTERMEDIATE -> Difficulty.EASY
                    ConceptComplexity.ADVANCED -> Difficulty.MEDIUM
                    ConceptComplexity.EXPERT -> Difficulty.MEDIUM
                }
            }
        }
    }
    
    /**
     * Create a learner profile for AI generation based on student data
     */
    fun createLearnerProfile(): LearnerProfile? {
        val profile = _studentProfile.value ?: return null
        val guidelines = getContentGuidelines()
        
        return LearnerProfile(
            strengthsBySubject = profile.preferredSubjects.associateWith { 0.8f },
            weaknessesByConcept = emptyMap(),
            masteredConcepts = emptySet(),
            totalQuestionsAnswered = 0,
            streakDays = 0
        )
    }
    
    /**
     * Clear the current student profile
     */
    fun clearStudentProfile() {
        _studentProfile.value = null
        _curriculumTopics.value = emptyList()
        Timber.d("Student profile cleared")
    }
    
    data class StudentProfile(
        val name: String,
        val gradeLevel: Int,
        val country: String,
        val preferredSubjects: List<Subject>,
        val strengths: List<String>,
        val weakAreas: List<String>, 
        val learningStyle: LearningStyle,
        val createdAt: Long,
        val lastUpdated: Long = createdAt
    )
    
    data class ContentGuidelines(
        val vocabularyLevel: VocabularyLevel,
        val conceptComplexity: ConceptComplexity,
        val attentionSpan: AttentionSpan,
        val preferredFormats: List<String>,
        val avoidTopics: List<String>
    )
    
    enum class VocabularyLevel {
        SIMPLE, ELEMENTARY, INTERMEDIATE, ADVANCED
    }
    
    enum class ConceptComplexity {
        BASIC, INTERMEDIATE, ADVANCED, EXPERT
    }
    
    enum class AttentionSpan {
        SHORT, MEDIUM, LONG
    }
    
    enum class LearningStyle {
        VISUAL, AUDITORY, KINESTHETIC, MIXED
    }
}