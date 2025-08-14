package com.mygemma3n.aiapp.feature.analytics

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.util.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

@Singleton
class LearningAnalyticsRepository @Inject constructor(
    private val interactionDao: LearningInteractionDao,
    private val progressDao: SubjectProgressDao,
    private val masteryDao: TopicMasteryDao,
    private val sessionDao: LearningSessionDao,
    private val gapDao: KnowledgeGapDao,
    private val recommendationDao: StudyRecommendationDao,
    private val computationDao: AnalyticsComputationDao,
    private val knowledgeGapAnalyzer: KnowledgeGapAnalyzer
) {

    /**
     * Record a learning interaction (question asked, topic explored, etc.)
     */
    suspend fun recordInteraction(
        studentId: String,
        subject: String,
        topic: String,
        concept: String,
        interactionType: InteractionType,
        sessionDurationMs: Long,
        responseQuality: Float? = null,
        difficultyLevel: String = "MEDIUM",
        wasCorrect: Boolean? = null,
        attemptsNeeded: Int = 1,
        helpRequested: Boolean = false,
        followUpQuestions: Int = 0
    ) {
        try {
            val interaction = LearningInteractionEntity(
                id = UUID.randomUUID().toString(),
                studentId = studentId,
                subject = subject,
                topic = topic,
                concept = concept,
                interactionType = interactionType,
                sessionDurationMs = sessionDurationMs,
                responseQuality = responseQuality,
                difficultyLevel = difficultyLevel,
                wasCorrect = wasCorrect,
                attemptsNeeded = attemptsNeeded,
                helpRequested = helpRequested,
                followUpQuestions = followUpQuestions
            )
            
            interactionDao.insertInteraction(interaction)
            
            // Update aggregated data
            updateSubjectProgress(studentId, subject)
            updateTopicMastery(studentId, subject, topic, concept, responseQuality, wasCorrect)
            
            // Analyze for knowledge gaps
            analyzeForKnowledgeGaps(studentId, subject, topic, concept, interaction)
            
            // Run comprehensive knowledge gap analysis periodically
            if (shouldRunComprehensiveAnalysis(studentId)) {
                knowledgeGapAnalyzer.analyzeKnowledgeGaps(studentId)
            }
            
            Timber.d("Recorded learning interaction: $subject/$topic/$concept")
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to record learning interaction")
        }
    }

    /**
     * Get comprehensive learning analytics for a student
     */
    fun getLearningAnalytics(studentId: String): Flow<LearningAnalytics> {
        return combine(
            progressDao.getProgressForStudent(studentId),
            gapDao.getActiveGapsForStudent(studentId),
            recommendationDao.getActiveRecommendationsForStudent(studentId)
        ) { progress, gaps, recommendations ->
            
            // Create basic subject analytics without async calls
            val subjectAnalytics = progress.associate { subjectProgress ->
                subjectProgress.subject to SubjectAnalytics(
                    subject = subjectProgress.subject,
                    masteryScore = subjectProgress.masteryScore,
                    timeSpent = subjectProgress.totalTimeSpentMs,
                    topicsExplored = subjectProgress.topicsExplored,
                    totalTopics = getTotalTopicsForSubject(subjectProgress.subject),
                    accuracy = subjectProgress.averageAccuracy,
                    currentStreak = subjectProgress.currentStreak,
                    needsAttention = extractNeedsAttention(subjectProgress.knowledgeGaps),
                    strongAreas = emptyList() // Will be populated separately
                )
            }
            
            LearningAnalytics(
                studentId = studentId,
                overallProgress = calculateOverallProgress(progress),
                subjectProgress = subjectAnalytics,
                knowledgeGaps = gaps,
                recommendations = recommendations,
                weeklyStats = WeeklyStats(
                    totalTimeSpent = 0L,
                    sessionsCompleted = 0,
                    topicsExplored = 0,
                    averageSessionDuration = 0L,
                    mostActiveSubject = "",  // No data available yet
                    improvementAreas = emptyList()
                ),
                trends = LearningTrends(
                    masteryTrend = TrendDirection.INSUFFICIENT_DATA,
                    engagementTrend = TrendDirection.INSUFFICIENT_DATA,
                    accuracyTrend = TrendDirection.INSUFFICIENT_DATA,
                    focusTrend = TrendDirection.INSUFFICIENT_DATA,
                    consistencyScore = 0.0f  // No data available yet
                )
            )
        }.map { analytics ->
            // Enhance with async data
            try {
                enhanceAnalyticsWithAsyncData(analytics)
            } catch (e: Exception) {
                Timber.w(e, "Failed to enhance analytics, returning basic data")
                analytics
            }
        }
    }

    private suspend fun enhanceAnalyticsWithAsyncData(analytics: LearningAnalytics): LearningAnalytics {
        try {
            val weeklyStats = getWeeklyStats(analytics.studentId)
            val trends = calculateLearningTrends(analytics.studentId)
            
            // Enhance subject analytics with strong areas
            val enhancedSubjectProgress = analytics.subjectProgress.mapValues { (subject, subjectAnalytics) ->
                val strongAreas = getStrongAreas(analytics.studentId, subject)
                subjectAnalytics.copy(strongAreas = strongAreas)
            }
            
            return analytics.copy(
                subjectProgress = enhancedSubjectProgress,
                weeklyStats = weeklyStats,
                trends = trends
            )
        } catch (e: Exception) {
            Timber.w(e, "Failed to enhance analytics with async data, returning basic analytics")
            return analytics
        }
    }

    /**
     * Start a new learning session
     */
    suspend fun startLearningSession(studentId: String, sessionType: String = "MIXED"): String {
        val sessionId = UUID.randomUUID().toString()
        val session = LearningSessionEntity(
            id = sessionId,
            studentId = studentId,
            startTime = System.currentTimeMillis(),
            sessionType = sessionType
        )
        sessionDao.insertSession(session)
        return sessionId
    }

    /**
     * End a learning session
     */
    suspend fun endLearningSession(sessionId: String, performance: Float, focusScore: Float) {
        try {
            val session = sessionDao.getCurrentSession(sessionId) ?: return
            val endTime = System.currentTimeMillis()
            
            val updatedSession = session.copy(
                endTime = endTime,
                totalDurationMs = endTime - session.startTime,
                overallPerformance = performance,
                focusScore = focusScore
            )
            
            sessionDao.updateSession(updatedSession)
            
            // Generate recommendations based on session performance
            generateSessionRecommendations(session.studentId, updatedSession)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to end learning session")
        }
    }

    /**
     * Generate personalized study recommendations
     */
    suspend fun generateRecommendations(studentId: String): List<StudyRecommendationEntity> {
        try {
            val gaps = gapDao.getActiveGapsForSubject(studentId, "")
            val masteryData = masteryDao.getTopicsNeedingReview(studentId)
            val weakAreas = identifyWeakAreas(studentId)
            
            val recommendations = mutableListOf<StudyRecommendationEntity>()
            
            // Recommendations based on knowledge gaps
            gaps.take(3).forEach { gap ->
                recommendations.add(createGapRecommendation(studentId, gap))
            }
            
            // Recommendations for topics needing review
            masteryData.take(2).forEach { mastery ->
                recommendations.add(createReviewRecommendation(studentId, mastery))
            }
            
            // Challenge recommendations for strong areas
            val strongAreas = identifyStrongAreas(studentId)
            strongAreas.take(1).forEach { subject ->
                recommendations.add(createChallengeRecommendation(studentId, subject))
            }
            
            // Insert recommendations
            recommendationDao.insertRecommendations(recommendations)
            
            return recommendations
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to generate recommendations")
            return emptyList()
        }
    }

    /**
     * Identify knowledge gaps using analytics
     */
    private suspend fun analyzeForKnowledgeGaps(
        studentId: String,
        subject: String,
        topic: String,
        concept: String,
        interaction: LearningInteractionEntity
    ) {
        try {
            val existingGaps = gapDao.getActiveGapsForSubject(studentId, subject)
            
            // Check for declining performance
            if (interaction.responseQuality != null && interaction.responseQuality < 0.6f) {
                val gapId = UUID.randomUUID().toString()
                val gap = KnowledgeGapEntity(
                    id = gapId,
                    studentId = studentId,
                    subject = subject,
                    topic = topic,
                    concept = concept,
                    gapType = GapType.CONCEPTUAL_MISUNDERSTANDING,
                    priority = determineGapPriority(interaction.responseQuality),
                    description = "Student showing difficulty with $concept in $topic"
                )
                
                gapDao.insertGap(gap)
            }
            
            // Check for excessive attempts
            if (interaction.attemptsNeeded > 3) {
                val gapId = UUID.randomUUID().toString()
                val gap = KnowledgeGapEntity(
                    id = gapId,
                    studentId = studentId,
                    subject = subject,
                    topic = topic,
                    concept = concept,
                    gapType = GapType.PROCEDURAL_ERROR,
                    priority = GapPriority.MEDIUM,
                    description = "Student required multiple attempts for $concept"
                )
                
                gapDao.insertGap(gap)
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to analyze knowledge gaps")
        }
    }

    /**
     * Update subject-level progress aggregations
     */
    private suspend fun updateSubjectProgress(studentId: String, subject: String) {
        try {
            val existing = progressDao.getProgressForSubject(studentId, subject)
            val interactionCount = interactionDao.getInteractionCount(studentId, subject)
            val avgQuality = interactionDao.getAverageResponseQuality(studentId, subject) ?: 0.5f
            val totalTimeSpent = interactionDao.getTotalTimeSpent(studentId, subject) ?: 0L
            
            // Calculate unique topics explored for this subject
            val uniqueTopics = interactionDao.getUniqueTopicsForSubject(studentId, subject)
            val topicsExplored = uniqueTopics.size
            
            // Calculate accuracy from correct/incorrect interactions
            val accuracy = interactionDao.getAccuracyForSubject(studentId, subject) ?: 0.0f
            
            val progress = existing?.copy(
                totalInteractions = interactionCount,
                totalTimeSpentMs = totalTimeSpent,
                masteryScore = avgQuality,
                topicsExplored = topicsExplored,
                averageAccuracy = accuracy,
                lastInteraction = System.currentTimeMillis()
            ) ?: SubjectProgressEntity(
                id = UUID.randomUUID().toString(),
                studentId = studentId,
                subject = subject,
                totalInteractions = interactionCount,
                totalTimeSpentMs = totalTimeSpent,
                masteryScore = avgQuality,
                topicsExplored = topicsExplored,
                averageAccuracy = accuracy,
                lastInteraction = System.currentTimeMillis()
            )
            
            progressDao.insertOrUpdateProgress(progress)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to update subject progress")
        }
    }

    /**
     * Update topic-level mastery tracking
     */
    private suspend fun updateTopicMastery(
        studentId: String,
        subject: String,
        topic: String,
        concept: String,
        responseQuality: Float?,
        wasCorrect: Boolean?
    ) {
        try {
            val masteryId = "$studentId-$subject-$topic"
            val existing = masteryDao.getTopicsByMasteryLevel(studentId, MasteryLevel.values().toList())
                .find { it.topic == topic && it.subject == subject }
            
            val practiceCount = (existing?.practiceCount ?: 0) + 1
            val correctResponses = (existing?.correctResponses ?: 0) + if (wasCorrect == true) 1 else 0
            val newConfidence = calculateConfidenceScore(responseQuality, practiceCount, correctResponses)
            val masteryLevel = determineMasteryLevel(newConfidence, practiceCount)
            
            val mastery = existing?.copy(
                practiceCount = practiceCount,
                correctResponses = correctResponses,
                confidenceScore = newConfidence,
                masteryLevel = masteryLevel,
                lastPracticed = System.currentTimeMillis(),
                needsReview = newConfidence < 0.6f
            ) ?: TopicMasteryEntity(
                id = masteryId,
                studentId = studentId,
                subject = subject,
                topic = topic,
                masteryLevel = masteryLevel,
                confidenceScore = newConfidence,
                practiceCount = practiceCount,
                correctResponses = correctResponses,
                lastPracticed = System.currentTimeMillis(),
                needsReview = newConfidence < 0.6f
            )
            
            masteryDao.insertOrUpdateMastery(mastery)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to update topic mastery")
        }
    }

    // Helper methods

    private fun calculateOverallProgress(progress: List<SubjectProgressEntity>): Float {
        if (progress.isEmpty()) return 0f
        return progress.map { it.masteryScore }.average().toFloat()
    }

    private fun getTotalTopicsForSubject(subject: String): Int {
        // This would be populated from curriculum data
        return when (subject.uppercase()) {
            "MATHEMATICS" -> 45
            "SCIENCE" -> 38
            "ENGLISH" -> 32
            "HISTORY" -> 28
            "GEOGRAPHY" -> 25
            "ECONOMICS" -> 22
            else -> 30
        }
    }

    private fun extractNeedsAttention(knowledgeGapsJson: String): List<String> {
        // Parse JSON array of struggling topics
        return try {
            // Simplified - in real implementation, parse JSON
            listOf()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun getStrongAreas(studentId: String, subject: String): List<String> {
        return try {
            masteryDao.getMasteryForSubject(studentId, subject)
                .first() // Get first emission from Flow
                .filter { it.masteryLevel in listOf(MasteryLevel.ADVANCED, MasteryLevel.MASTERED) }
                .map { it.topic }
                .take(3) // Take only first 3 topics
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun getWeeklyStats(studentId: String): WeeklyStats {
        val weekStart = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)
        val stats = computationDao.getWeeklySessionStats(studentId, weekStart)
        
        // Calculate topics explored this week from interactions
        val recentInteractions = interactionDao.getInteractionsSince(studentId, weekStart)
        val topicsThisWeek = recentInteractions.map { it.topic }.distinct().size
        
        // Calculate most active subject from recent interactions
        val mostActiveSubject = recentInteractions
            .groupBy { it.subject }
            .maxByOrNull { it.value.size }
            ?.key ?: "MATHEMATICS"
        
        return WeeklyStats(
            totalTimeSpent = stats?.totalTime ?: 0L,
            sessionsCompleted = stats?.sessionCount ?: 0,
            topicsExplored = topicsThisWeek,
            averageSessionDuration = stats?.avgDuration ?: 0L,
            mostActiveSubject = mostActiveSubject,
            improvementAreas = listOf()
        )
    }

    private suspend fun calculateLearningTrends(studentId: String): LearningTrends {
        val thirtyDaysAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30)
        val performanceData = computationDao.getPerformanceTrend(studentId, thirtyDaysAgo)
        
        // Calculate actual consistency score based on performance data
        val consistencyScore = if (performanceData.size >= 3) {
            calculateConsistencyScore(performanceData.map { it.avgQuality })
        } else {
            0.0f  // Insufficient data for consistency calculation
        }
        
        return LearningTrends(
            masteryTrend = analyzeTrend(performanceData.map { it.avgQuality }),
            engagementTrend = analyzeTrend(performanceData.map { it.interactions.toFloat() }),
            accuracyTrend = if (performanceData.isEmpty()) TrendDirection.INSUFFICIENT_DATA else TrendDirection.STABLE,
            focusTrend = if (performanceData.isEmpty()) TrendDirection.INSUFFICIENT_DATA else TrendDirection.STABLE,
            consistencyScore = consistencyScore
        )
    }

    private fun analyzeTrend(values: List<Float>): TrendDirection {
        if (values.size < 3) return TrendDirection.INSUFFICIENT_DATA
        
        val firstHalf = values.take(values.size / 2).average()
        val secondHalf = values.drop(values.size / 2).average()
        
        return when {
            secondHalf > firstHalf * 1.1 -> TrendDirection.IMPROVING
            secondHalf < firstHalf * 0.9 -> TrendDirection.DECLINING
            else -> TrendDirection.STABLE
        }
    }
    
    private fun calculateConsistencyScore(values: List<Float>): Float {
        if (values.size < 3) return 0.0f
        
        // Calculate coefficient of variation (lower is more consistent)
        val mean = values.average().toFloat()
        if (mean == 0.0f) return 0.0f
        
        val variance = values.map { (it - mean) * (it - mean) }.average().toFloat()
        val standardDeviation = kotlin.math.sqrt(variance)
        val coefficientOfVariation = standardDeviation / mean
        
        // Convert to consistency score (0-1, higher is better)
        // Invert and normalize the coefficient of variation
        return (1.0f - coefficientOfVariation.coerceIn(0.0f, 1.0f)).coerceIn(0.0f, 1.0f)
    }

    private fun calculateConfidenceScore(
        responseQuality: Float?,
        practiceCount: Int,
        correctResponses: Int
    ): Float {
        val accuracyScore = if (practiceCount > 0) correctResponses.toFloat() / practiceCount else 0f
        val qualityScore = responseQuality ?: 0.5f
        val practiceBonus = min(practiceCount.toFloat() / 10f, 0.2f)
        
        return min((accuracyScore * 0.5f + qualityScore * 0.4f + practiceBonus), 1.0f)
    }

    private fun determineMasteryLevel(confidence: Float, practiceCount: Int): MasteryLevel {
        return when {
            practiceCount == 0 -> MasteryLevel.NOT_STARTED
            confidence < 0.25f -> MasteryLevel.INTRODUCED
            confidence < 0.5f -> MasteryLevel.DEVELOPING
            confidence < 0.75f -> MasteryLevel.PROFICIENT
            confidence < 0.9f -> MasteryLevel.ADVANCED
            else -> MasteryLevel.MASTERED
        }
    }

    private fun determineGapPriority(responseQuality: Float): GapPriority {
        return when {
            responseQuality < 0.3f -> GapPriority.CRITICAL
            responseQuality < 0.5f -> GapPriority.HIGH
            responseQuality < 0.7f -> GapPriority.MEDIUM
            else -> GapPriority.LOW
        }
    }

    private suspend fun generateSessionRecommendations(
        studentId: String,
        session: LearningSessionEntity
    ) {
        // Generate recommendations based on session performance
        if (session.overallPerformance < 0.6f) {
            val recommendation = StudyRecommendationEntity(
                id = UUID.randomUUID().toString(),
                studentId = studentId,
                title = "Review Recent Topics",
                description = "Your recent session showed some challenges. Consider reviewing the topics you covered.",
                subject = "GENERAL",
                topic = "Review",
                recommendationType = RecommendationType.REVIEW_TOPIC,
                priority = 3,
                estimatedTimeMinutes = 15
            )
            recommendationDao.insertRecommendation(recommendation)
        }
    }

    private fun createGapRecommendation(studentId: String, gap: KnowledgeGapEntity): StudyRecommendationEntity {
        return StudyRecommendationEntity(
            id = UUID.randomUUID().toString(),
            studentId = studentId,
            title = "Address Knowledge Gap",
            description = "Focus on ${gap.concept} in ${gap.topic}",
            subject = gap.subject,
            topic = gap.topic,
            recommendationType = RecommendationType.REVIEW_TOPIC,
            priority = when (gap.priority) {
                GapPriority.CRITICAL -> 5
                GapPriority.HIGH -> 4
                GapPriority.MEDIUM -> 3
                GapPriority.LOW -> 2
            },
            estimatedTimeMinutes = 20
        )
    }

    private fun createReviewRecommendation(studentId: String, mastery: TopicMasteryEntity): StudyRecommendationEntity {
        return StudyRecommendationEntity(
            id = UUID.randomUUID().toString(),
            studentId = studentId,
            title = "Review ${mastery.topic}",
            description = "This topic needs some review to maintain your understanding",
            subject = mastery.subject,
            topic = mastery.topic,
            recommendationType = RecommendationType.REVIEW_TOPIC,
            priority = 3,
            estimatedTimeMinutes = 15
        )
    }

    private fun createChallengeRecommendation(studentId: String, subject: String): StudyRecommendationEntity {
        return StudyRecommendationEntity(
            id = UUID.randomUUID().toString(),
            studentId = studentId,
            title = "Challenge Yourself",
            description = "You're doing great in $subject! Try some advanced topics.",
            subject = subject,
            topic = "Advanced",
            recommendationType = RecommendationType.CHALLENGE_YOURSELF,
            priority = 2,
            estimatedTimeMinutes = 25
        )
    }

    private suspend fun identifyWeakAreas(studentId: String): List<String> {
        return try {
            val progress = progressDao.getProgressForStudent(studentId)
            progress.first() // Get first emission from Flow
                .filter { it.masteryScore < 0.6f } // Filter the list
                .map { it.subject } // Map to subject names
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun identifyStrongAreas(studentId: String): List<String> {
        return try {
            val progress = progressDao.getProgressForStudent(studentId)
            progress.first() // Get first emission from Flow
                .filter { it.masteryScore > 0.8f } // Filter the list
                .map { it.subject } // Map to subject names
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Determines if comprehensive knowledge gap analysis should run
     * Runs every 10 interactions or when specific patterns are detected
     */
    private suspend fun shouldRunComprehensiveAnalysis(studentId: String): Boolean {
        return try {
            val totalInteractions = interactionDao.getTotalInteractionCount(studentId)
            // Run comprehensive analysis every 10 interactions
            totalInteractions > 0 && totalInteractions % 10 == 0
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Initialize demo data for analytics testing (development only)
     */
    suspend fun initializeDemoData(studentId: String = "student_001") {
        try {
            // Check if data already exists to avoid clearing
            val existingInteractions = interactionDao.getTotalInteractionCount(studentId)
            if (existingInteractions > 0) {
                Timber.d("Demo data already exists, skipping initialization")
                return
            }
            
            val currentTime = System.currentTimeMillis()
            val dayInMs = 24 * 60 * 60 * 1000L
            
            // Insert sample interactions over the past week
            val sampleInteractions = listOf(
                LearningInteractionEntity(
                    id = "demo_1",
                    studentId = studentId,
                    subject = "Mathematics",
                    topic = "Algebra",
                    concept = "Linear Equations",
                    interactionType = InteractionType.QUESTION_ASKED,
                    timestamp = currentTime - 6 * dayInMs,
                    sessionDurationMs = 300000, // 5 minutes
                    responseQuality = 0.8f,
                    difficultyLevel = "MEDIUM",
                    wasCorrect = true
                ),
                LearningInteractionEntity(
                    id = "demo_2",
                    studentId = studentId,
                    subject = "Science",
                    topic = "Physics",
                    concept = "Newton's Laws",
                    interactionType = InteractionType.CONCEPT_REVIEWED,
                    timestamp = currentTime - 4 * dayInMs,
                    sessionDurationMs = 450000, // 7.5 minutes
                    responseQuality = 0.6f,
                    difficultyLevel = "HARD",
                    wasCorrect = false,
                    attemptsNeeded = 2
                ),
                LearningInteractionEntity(
                    id = "demo_3",
                    studentId = studentId,
                    subject = "Mathematics",
                    topic = "Geometry",
                    concept = "Circle Area",
                    interactionType = InteractionType.QUIZ_COMPLETED,
                    timestamp = currentTime - 2 * dayInMs,
                    sessionDurationMs = 180000, // 3 minutes
                    responseQuality = 0.9f,
                    difficultyLevel = "EASY",
                    wasCorrect = true
                )
            )
            
            interactionDao.insertInteractions(sampleInteractions)
            
            // Initialize progress for each subject
            updateSubjectProgress(studentId, "Mathematics")
            updateSubjectProgress(studentId, "Science")
            
            // Create sample recommendations
            val recommendations = listOf(
                StudyRecommendationEntity(
                    id = "rec_1",
                    studentId = studentId,
                    title = "Review Physics Concepts",
                    description = "Focus on Newton's Laws - you had some difficulty here",
                    subject = "Science",
                    topic = "Physics",
                    recommendationType = RecommendationType.REVIEW_TOPIC,
                    priority = 4,
                    estimatedTimeMinutes = 20
                ),
                StudyRecommendationEntity(
                    id = "rec_2",
                    studentId = studentId,
                    title = "Practice More Algebra",
                    description = "Great work on linear equations! Try some advanced problems",
                    subject = "Mathematics",
                    topic = "Algebra",
                    recommendationType = RecommendationType.CHALLENGE_YOURSELF,
                    priority = 3,
                    estimatedTimeMinutes = 15
                )
            )
            
            recommendationDao.insertRecommendations(recommendations)
            
            Timber.d("Demo analytics data initialized successfully")
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize demo data")
        }
    }

    /**
     * Mark a study recommendation as completed
     */
    suspend fun markRecommendationCompleted(recommendationId: String) {
        try {
            recommendationDao.markRecommendationCompleted(recommendationId, System.currentTimeMillis())
            Timber.d("Marked recommendation $recommendationId as completed")
        } catch (e: Exception) {
            Timber.e(e, "Failed to mark recommendation as completed")
            throw e
        }
    }

    /**
     * Dismiss a study recommendation
     */
    suspend fun dismissRecommendation(recommendationId: String) {
        try {
            recommendationDao.dismissRecommendation(recommendationId, System.currentTimeMillis())
            Timber.d("Dismissed recommendation $recommendationId")
        } catch (e: Exception) {
            Timber.e(e, "Failed to dismiss recommendation")
            throw e
        }
    }
}