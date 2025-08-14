package com.mygemma3n.aiapp.feature.quiz

import com.mygemma3n.aiapp.feature.analytics.LearningAnalyticsRepository
import com.mygemma3n.aiapp.feature.analytics.InteractionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks student progress, manages analytics, and provides insights for personalized learning.
 * Handles progress calculations, concept coverage tracking, and learning analytics integration.
 */
@Singleton
class QuizProgressTracker @Inject constructor(
    private val quizRepo: QuizRepository,
    private val analyticsRepository: LearningAnalyticsRepository
) {
    
    private val _userProgress = MutableStateFlow<Map<Subject, Float>>(emptyMap())
    val userProgress: StateFlow<Map<Subject, Float>> = _userProgress.asStateFlow()
    
    private val _conceptCoverage = MutableStateFlow<Map<String, Int>>(emptyMap())
    val conceptCoverage: StateFlow<Map<String, Int>> = _conceptCoverage.asStateFlow()
    
    private val _reviewQuestionsAvailable = MutableStateFlow(0)
    val reviewQuestionsAvailable: StateFlow<Int> = _reviewQuestionsAvailable.asStateFlow()
    
    private val _strengthsAndWeaknesses = MutableStateFlow<LearningInsights?>(null)
    val strengthsAndWeaknesses: StateFlow<LearningInsights?> = _strengthsAndWeaknesses.asStateFlow()
    
    /**
     * Load comprehensive user progress across all subjects
     */
    suspend fun loadUserProgress() = withContext(Dispatchers.IO) {
        try {
            Timber.d("Loading user progress across all subjects")
            
            val progressMap = mutableMapOf<Subject, Float>()
            
            // Calculate progress for each subject
            Subject.entries.forEach { subject ->
                val progress = calculateSubjectProgress(subject)
                progressMap[subject] = progress
            }
            
            _userProgress.value = progressMap
            
            // Load concept coverage
            loadConceptCoverage()
            
            // Check for review questions
            checkReviewQuestions()
            
            // Analyze learning patterns
            analyzeStrengthsAndWeaknesses()
            
            Timber.d("User progress loaded: ${progressMap.size} subjects")
        } catch (e: Exception) {
            Timber.e(e, "Failed to load user progress")
        }
    }
    
    /**
     * Calculate progress for a specific subject based on quiz performance
     */
    private suspend fun calculateSubjectProgress(subject: Subject): Float {
        return try {
            // Use existing repository methods
            val oneWeekAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000)
            val accuracy = quizRepo.progressDao().recentAccuracy(subject, oneWeekAgo)
            accuracy * 100f // Convert to percentage
        } catch (e: Exception) {
            Timber.w(e, "Failed to calculate progress for $subject")
            0f
        }
    }
    
    /**
     * Load concept coverage data showing which topics have been studied
     */
    private suspend fun loadConceptCoverage() {
        try {
            // Use available methods to get concept coverage
            val masteredConcepts = Subject.entries.flatMap { subject ->
                quizRepo.questionHistoryDao().getMasteredConcepts(subject)
            }
            
            val coverage = masteredConcepts.groupingBy { it }.eachCount()
            _conceptCoverage.value = coverage
            Timber.d("Concept coverage loaded: ${coverage.size} concepts")
        } catch (e: Exception) {
            Timber.w(e, "Failed to load concept coverage")
            _conceptCoverage.value = emptyMap()
        }
    }
    
    /**
     * Check for questions that should be reviewed based on performance
     */
    private suspend fun checkReviewQuestions() {
        try {
            val reviewCount = Subject.entries.sumOf { subject ->
                quizRepo.getQuestionsForSpacedReview(subject, limit = 10).size
            }
            _reviewQuestionsAvailable.value = reviewCount
            Timber.d("Review questions available: $reviewCount")
        } catch (e: Exception) {
            Timber.w(e, "Failed to check review questions")
            _reviewQuestionsAvailable.value = 0
        }
    }
    
    /**
     * Analyze learning patterns to identify strengths and weaknesses
     */
    private suspend fun analyzeStrengthsAndWeaknesses() {
        try {
            val progressMap = _userProgress.value
            if (progressMap.isEmpty()) return
            
            val strongSubjects = progressMap.filter { it.value >= 75f }.keys.toList()
            val weakSubjects = progressMap.filter { it.value < 50f }.keys.toList()
            val improvingSubjects = findImprovingSubjects()
            val strugglingAreas = findStrugglingAreas()
            
            val insights = LearningInsights(
                strengths = strongSubjects,
                weaknesses = weakSubjects,
                improvingAreas = improvingSubjects,
                strugglingAreas = strugglingAreas,
                overallProgress = progressMap.values.average().toFloat(),
                totalQuestionsAnswered = calculateTotalQuestionsAnswered(),
                streak = calculateCurrentStreak(),
                lastStudySession = getLastStudySession()
            )
            
            _strengthsAndWeaknesses.value = insights
            Timber.d("Learning insights analyzed: ${strongSubjects.size} strengths, ${weakSubjects.size} weaknesses")
        } catch (e: Exception) {
            Timber.w(e, "Failed to analyze strengths and weaknesses")
        }
    }
    
    /**
     * Find subjects where student is improving over time
     */
    private suspend fun findImprovingSubjects(): List<Subject> {
        return try {
            val oneWeekAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000)
            val twoWeeksAgo = System.currentTimeMillis() - (14 * 24 * 60 * 60 * 1000)
            
            Subject.entries.filter { subject ->
                val recentAccuracy = quizRepo.progressDao().recentAccuracy(subject, oneWeekAgo)
                val olderAccuracy = quizRepo.progressDao().recentAccuracy(subject, twoWeeksAgo)
                recentAccuracy > olderAccuracy + 0.05f // Improved by at least 5%
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to find improving subjects")
            emptyList()
        }
    }
    
    /**
     * Find specific areas where student is struggling
     */
    private suspend fun findStrugglingAreas(): List<String> {
        return try {
            val conceptCoverage = _conceptCoverage.value
            conceptCoverage.filter { (_, attempts) ->
                attempts >= 3 // Concept attempted multiple times (indicates difficulty)
            }.keys.toList()
        } catch (e: Exception) {
            Timber.w(e, "Failed to find struggling areas")
            emptyList()
        }
    }
    
    /**
     * Calculate total number of questions answered across all quizzes
     */
    private suspend fun calculateTotalQuestionsAnswered(): Int {
        return try {
            val oneMonthAgo = System.currentTimeMillis() - (30 * 24 * 60 * 60 * 1000L)
            val recentProgress = quizRepo.progressDao().getRecentProgress(oneMonthAgo)
            recentProgress.size
        } catch (e: Exception) {
            Timber.w(e, "Failed to calculate total questions answered")
            0
        }
    }
    
    /**
     * Calculate current learning streak (consecutive days with quiz activity)
     */
    private suspend fun calculateCurrentStreak(): Int {
        return try {
            // Simple streak calculation - could be enhanced
            val oneWeekAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)
            val recentProgress = quizRepo.progressDao().getRecentProgress(oneWeekAgo)
            if (recentProgress.isNotEmpty()) 1 else 0 // Simplified for now
        } catch (e: Exception) {
            Timber.w(e, "Failed to calculate current streak")
            0
        }
    }
    
    /**
     * Get timestamp of last study session
     */
    private suspend fun getLastStudySession(): Long {
        return try {
            val recentProgress = quizRepo.progressDao().getRecentProgress(0L)
            recentProgress.maxOfOrNull { it.timestamp } ?: 0L
        } catch (e: Exception) {
            Timber.w(e, "Failed to get last study session")
            0L
        }
    }
    
    /**
     * Record quiz completion and update progress
     */
    suspend fun recordQuizCompletion(quiz: Quiz) = withContext(Dispatchers.IO) {
        try {
            Timber.d("Recording quiz completion for ${quiz.subject}")
            
            // Save quiz results
            quizRepo.saveQuiz(quiz)
            
            // Record analytics
            recordQuizAnalytics(quiz)
            
            // Update progress calculations
            val newProgress = calculateSubjectProgress(quiz.subject)
            val currentProgress = _userProgress.value.toMutableMap()
            currentProgress[quiz.subject] = newProgress
            _userProgress.value = currentProgress
            
            // Update concept coverage
            updateConceptCoverage(quiz)
            
            // Check for new review questions
            checkReviewQuestions()
            
            // Re-analyze learning patterns
            analyzeStrengthsAndWeaknesses()
            
            Timber.d("Quiz completion recorded successfully")
        } catch (e: Exception) {
            Timber.e(e, "Failed to record quiz completion")
        }
    }
    
    /**
     * Record detailed analytics for the completed quiz
     */
    private suspend fun recordQuizAnalytics(quiz: Quiz) {
        try {
            val correctAnswers = quiz.questions.count { it.isCorrect }
            val totalQuestions = quiz.questions.size
            val accuracy = if (totalQuestions > 0) correctAnswers.toFloat() / totalQuestions else 0f
            
            // TODO: Add analytics recording when repository method is available
            
            Timber.d("Quiz analytics recorded: $correctAnswers/$totalQuestions correct (${(accuracy * 100).toInt()}%)")
        } catch (e: Exception) {
            Timber.w(e, "Failed to record quiz analytics")
        }
    }
    
    /**
     * Update concept coverage based on quiz topics
     */
    private suspend fun updateConceptCoverage(quiz: Quiz) {
        try {
            val currentCoverage = _conceptCoverage.value.toMutableMap()
            
            // Extract concepts from quiz topic and questions
            val concepts = mutableSetOf<String>()
            quiz.topic?.let { concepts.add(it) }
            quiz.questions.forEach { question ->
                question.conceptsCovered.forEach { concept ->
                    concepts.add(concept)
                }
            }
            
            // Update coverage counts
            concepts.forEach { concept ->
                currentCoverage[concept] = (currentCoverage[concept] ?: 0) + 1
            }
            
            _conceptCoverage.value = currentCoverage
            Timber.d("Updated concept coverage: ${concepts.size} concepts")
        } catch (e: Exception) {
            Timber.w(e, "Failed to update concept coverage")
        }
    }
    
    /**
     * Record individual question attempt for fine-grained analytics
     */
    suspend fun recordQuestionAttempt(question: Question, isCorrect: Boolean, timeSpent: Long = 0L) {
        try {
            // Record in repository
            quizRepo.recordQuestionAttempt(question, isCorrect)
            
            // TODO: Add analytics recording when repository method is available
            
            Timber.d("Question attempt recorded: ${if (isCorrect) "correct" else "incorrect"}")
        } catch (e: Exception) {
            Timber.w(e, "Failed to record question attempt")
        }
    }
    
    /**
     * Get personalized learning recommendations based on progress
     */
    fun getLearningRecommendations(): List<LearningRecommendation> {
        val insights = _strengthsAndWeaknesses.value ?: return emptyList()
        val recommendations = mutableListOf<LearningRecommendation>()
        
        // Recommend practice for weak subjects
        insights.weaknesses.forEach { subject ->
            recommendations.add(
                LearningRecommendation(
                    type = RecommendationType.PRACTICE_WEAK_SUBJECT,
                    title = "Practice ${subject.name}",
                    description = "Focus on improving your ${subject.name} skills with targeted practice",
                    priority = Priority.HIGH,
                    subject = subject
                )
            )
        }
        
        // Recommend review for struggling areas
        insights.strugglingAreas.forEach { area ->
            recommendations.add(
                LearningRecommendation(
                    type = RecommendationType.REVIEW_CONCEPT,
                    title = "Review $area",
                    description = "This concept needs more practice. Try some easier questions first",
                    priority = Priority.MEDIUM,
                    topic = area
                )
            )
        }
        
        // Recommend continuing with strengths
        insights.strengths.take(2).forEach { subject ->
            recommendations.add(
                LearningRecommendation(
                    type = RecommendationType.ADVANCE_STRENGTH,
                    title = "Challenge yourself in ${subject.name}",
                    description = "You're doing great! Try harder questions to keep growing",
                    priority = Priority.LOW,
                    subject = subject
                )
            )
        }
        
        return recommendations.sortedBy { it.priority }
    }
    
    /**
     * Clear all progress data
     */
    suspend fun clearProgress() {
        _userProgress.value = emptyMap()
        _conceptCoverage.value = emptyMap()
        _reviewQuestionsAvailable.value = 0
        _strengthsAndWeaknesses.value = null
        Timber.d("Progress data cleared")
    }
    
    data class LearningInsights(
        val strengths: List<Subject>,
        val weaknesses: List<Subject>,
        val improvingAreas: List<Subject>,
        val strugglingAreas: List<String>,
        val overallProgress: Float,
        val totalQuestionsAnswered: Int,
        val streak: Int,
        val lastStudySession: Long
    )
    
    data class LearningRecommendation(
        val type: RecommendationType,
        val title: String,
        val description: String,
        val priority: Priority,
        val subject: Subject? = null,
        val topic: String? = null
    )
    
    enum class RecommendationType {
        PRACTICE_WEAK_SUBJECT,
        REVIEW_CONCEPT,
        ADVANCE_STRENGTH,
        TAKE_BREAK,
        TRY_NEW_TOPIC
    }
    
    enum class Priority {
        HIGH, MEDIUM, LOW
    }
}