package com.example.mygemma3n.feature.tutor

import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Speed
import com.example.mygemma3n.feature.analytics.*
import com.example.mygemma3n.feature.progress.LearningProgressTracker
import com.example.mygemma3n.shared_utilities.OfflineRAG
import kotlinx.coroutines.flow.*
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service that integrates the AI Tutor with the LearningProgressTracker
 * to ensure progress, achievements, and mastery are properly tracked
 */
@Singleton
class TutorProgressIntegrationService @Inject constructor(
    private val progressTracker: LearningProgressTracker,
    private val analyticsRepository: LearningAnalyticsRepository,
    private val masteryDao: TopicMasteryDao,
    private val interactionDao: LearningInteractionDao
) {

    // Cache for current progress to avoid repeated lookups
    private val progressCache = mutableMapOf<String, LearningProgressTracker.LearningProgress>()

    /**
     * Initialize or get existing progress for a student
     */
    suspend fun initializeStudentProgress(
        studentId: String,
        subject: OfflineRAG.Subject
    ): LearningProgressTracker.LearningProgress {
        val cacheKey = "$studentId-${subject.name}"

        return progressCache[cacheKey] ?: run {
            // Load existing progress or create new
            val existingMastery = getExistingMasteryData(studentId, subject)
            val weeklyGoal = createWeeklyGoal()

            val progress = LearningProgressTracker.LearningProgress(
                studentId = studentId,
                subject = subject,
                overallProgress = calculateOverallProgress(existingMastery),
                conceptMasteries = existingMastery,
                streakDays = calculateCurrentStreak(studentId),
                totalSessionTime = getTotalSessionTime(studentId),
                achievements = loadExistingAchievements(studentId),
                weeklyGoal = weeklyGoal
            )

            progressCache[cacheKey] = progress
            progress
        }
    }

    /**
     * Record a tutoring interaction and update all progress systems
     */
    suspend fun recordTutoringInteraction(
        studentId: String,
        subject: OfflineRAG.Subject,
        topic: String,
        concept: String,
        responseQuality: Float,
        sessionDurationMs: Long,
        wasCorrect: Boolean,
        approach: TutorViewModel.TeachingApproach,
        studentNeed: TutorViewModel.StudentNeed
    ) {
        try {
            // 1. Record in analytics repository (existing functionality)
            analyticsRepository.recordInteraction(
                studentId = studentId,
                subject = subject.name,
                topic = topic,
                concept = concept,
                interactionType = determineInteractionType(studentNeed),
                sessionDurationMs = sessionDurationMs,
                responseQuality = responseQuality,
                difficultyLevel = approach.name,
                wasCorrect = wasCorrect,
                attemptsNeeded = 1,
                helpRequested = studentNeed.type == TutorViewModel.NeedType.STEP_BY_STEP_HELP
            )

            // 2. Update LearningProgressTracker
            val cacheKey = "$studentId-${subject.name}"
            val currentProgress = progressCache[cacheKey] ?: initializeStudentProgress(studentId, subject)

            // Calculate concept score based on response quality and correctness
            val conceptScore = calculateConceptScore(responseQuality, wasCorrect, approach)

            // Update progress
            val updatedProgress = progressTracker.updateProgress(
                current = currentProgress,
                sessionDuration = sessionDurationMs,
                topicsStudied = listOf(topic),
                conceptScores = mapOf(concept to conceptScore)
            )

            // 3. Check for milestone achievements specific to tutoring
            val tutoringAchievements = checkTutoringAchievements(
                updatedProgress,
                concept,
                conceptScore,
                approach
            )

            // 4. Update weekly goal progress
            val updatedGoal = updateWeeklyGoalForTutoring(
                updatedProgress.weeklyGoal,
                sessionDurationMs,
                topic
            )

            // 5. Combine everything
            val finalProgress = updatedProgress.copy(
                achievements = updatedProgress.achievements + tutoringAchievements,
                weeklyGoal = updatedGoal
            )

            // Update cache
            progressCache[cacheKey] = finalProgress

            // 6. Sync concept mastery with analytics database
            syncConceptMastery(studentId, subject, concept, finalProgress)

            Timber.d("Tutoring interaction recorded: $concept (score: $conceptScore, mastery: ${finalProgress.conceptMasteries[concept]?.progressPercentage})")

        } catch (e: Exception) {
            Timber.e(e, "Failed to record tutoring interaction")
        }
    }

    /**
     * Get current learning progress for display
     */
    fun getCurrentProgress(studentId: String, subject: OfflineRAG.Subject): LearningProgressTracker.LearningProgress? {
        return progressCache["$studentId-${subject.name}"]
    }

    /**
     * Get concept mastery level for adaptive tutoring
     */
    suspend fun getConceptMastery(
        studentId: String,
        subject: OfflineRAG.Subject,
        concept: String
    ): LearningProgressTracker.ConceptMastery? {
        val progress = progressCache["$studentId-${subject.name}"]
            ?: initializeStudentProgress(studentId, subject)
        return progress.conceptMasteries[concept]
    }

    /**
     * Suggest next concept based on mastery levels
     */
    suspend fun suggestNextConcept(
        studentId: String,
        subject: OfflineRAG.Subject
    ): String? {
        val progress = progressCache["$studentId-${subject.name}"]
            ?: initializeStudentProgress(studentId, subject)

        // Find concepts that need work
        val weakConcepts = progress.conceptMasteries
            .filter { it.value.level in listOf(
                LearningProgressTracker.MasteryLevel.NOT_STARTED,
                LearningProgressTracker.MasteryLevel.LEARNING,
                LearningProgressTracker.MasteryLevel.PRACTICING
            )}
            .map { it.key to it.value }

        // Prioritize by:
        // 1. Started but not mastered (continuity)
        // 2. Not started (new learning)
        // 3. Least recently practiced

        return weakConcepts
            .sortedWith(compareBy(
                { it.second.level == LearningProgressTracker.MasteryLevel.NOT_STARTED },
                { -it.second.practiceCount },
                { it.second.lastPracticed }
            ))
            .firstOrNull()?.first
    }

    // Helper methods

    private suspend fun getExistingMasteryData(
        studentId: String,
        subject: OfflineRAG.Subject
    ): Map<String, LearningProgressTracker.ConceptMastery> {
        return try {
            masteryDao.getMasteryForSubject(studentId, subject.name)
                .first()
                .associate { mastery ->
                    mastery.topic to LearningProgressTracker.ConceptMastery(
                        concept = mastery.topic,
                        level = mapMasteryLevel(mastery.masteryLevel),
                        progressPercentage = mastery.confidenceScore,
                        lastPracticed = mastery.lastPracticed,
                        practiceCount = mastery.practiceCount
                    )
                }
        } catch (e: Exception) {
            Timber.e(e, "Failed to load existing mastery data")
            emptyMap()
        }
    }

    private fun mapMasteryLevel(level: MasteryLevel): LearningProgressTracker.MasteryLevel {
        return when (level) {
            MasteryLevel.NOT_STARTED -> LearningProgressTracker.MasteryLevel.NOT_STARTED
            MasteryLevel.INTRODUCED -> LearningProgressTracker.MasteryLevel.LEARNING
            MasteryLevel.DEVELOPING -> LearningProgressTracker.MasteryLevel.PRACTICING
            MasteryLevel.PROFICIENT -> LearningProgressTracker.MasteryLevel.PROFICIENT
            MasteryLevel.ADVANCED, MasteryLevel.MASTERED -> LearningProgressTracker.MasteryLevel.MASTERED
        }
    }

    private fun calculateOverallProgress(masteries: Map<String, LearningProgressTracker.ConceptMastery>): Float {
        return if (masteries.isEmpty()) 0f
        else masteries.values.map { it.progressPercentage }.average().toFloat()
    }

    private suspend fun calculateCurrentStreak(studentId: String): Int {
        // Get recent interactions to calculate streak
        val oneWeekAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000)
        val recentInteractions = interactionDao.getInteractionsSince(studentId, oneWeekAgo)

        // Group by day and count consecutive days
        val daysWithActivity = recentInteractions
            .map { it.timestamp }
            .map { timestamp ->
                // Convert to day start
                val cal = java.util.Calendar.getInstance()
                cal.timeInMillis = timestamp
                cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                cal.set(java.util.Calendar.MINUTE, 0)
                cal.set(java.util.Calendar.SECOND, 0)
                cal.set(java.util.Calendar.MILLISECOND, 0)
                cal.timeInMillis
            }
            .distinct()
            .sorted()

        // Count consecutive days from today backwards
        var streak = 0
        val today = System.currentTimeMillis()
        val oneDayMs = 24 * 60 * 60 * 1000

        for (i in 0..6) {
            val dayToCheck = today - (i * oneDayMs)
            val dayStart = dayToCheck - (dayToCheck % oneDayMs)

            if (daysWithActivity.any {
                    kotlin.math.abs(it - dayStart) < oneDayMs
                }) {
                if (i <= 1) streak++ // Only count if yesterday or today
            } else if (i <= 1) {
                break // Streak broken
            }
        }

        return streak
    }

    private suspend fun getTotalSessionTime(studentId: String): Long {
        return try {
            val allInteractions = interactionDao.getInteractionsSince(studentId, 0)
            allInteractions.sumOf { it.sessionDurationMs }
        } catch (e: Exception) {
            0L
        }
    }

    private fun loadExistingAchievements(studentId: String): List<LearningProgressTracker.Achievement> {
        // In a full implementation, achievements would be persisted
        // For now, return empty list
        return emptyList()
    }

    private fun createWeeklyGoal(): LearningProgressTracker.WeeklyGoal {
        val weekStart = System.currentTimeMillis() -
                ((System.currentTimeMillis() % (7 * 24 * 60 * 60 * 1000)))

        return LearningProgressTracker.WeeklyGoal(
            targetSessionMinutes = 120, // 2 hours per week
            currentSessionMinutes = 0,
            targetTopicsCount = 5,
            currentTopicsCount = 0,
            weekStartTime = weekStart
        )
    }

    private fun determineInteractionType(studentNeed: TutorViewModel.StudentNeed): InteractionType {
        return when (studentNeed.type) {
            TutorViewModel.NeedType.CLARIFICATION -> InteractionType.QUESTION_ASKED
            TutorViewModel.NeedType.CONCEPT_EXPLANATION -> InteractionType.CONCEPT_REVIEWED
            TutorViewModel.NeedType.STEP_BY_STEP_HELP -> InteractionType.HELP_REQUESTED
            TutorViewModel.NeedType.PRACTICE -> InteractionType.TOPIC_EXPLORED
            TutorViewModel.NeedType.ENCOURAGEMENT -> InteractionType.FOLLOW_UP_QUESTION
        }
    }

    private fun calculateConceptScore(
        responseQuality: Float,
        wasCorrect: Boolean,
        approach: TutorViewModel.TeachingApproach
    ): Float {
        // Weight the score based on teaching approach
        val approachMultiplier = when (approach) {
            TutorViewModel.TeachingApproach.SOCRATIC -> 1.2f // Higher value for discovery
            TutorViewModel.TeachingApproach.PROBLEM_SOLVING -> 1.1f
            TutorViewModel.TeachingApproach.EXPLANATION -> 1.0f
            TutorViewModel.TeachingApproach.ENCOURAGEMENT -> 0.9f // Lower as student struggled
            TutorViewModel.TeachingApproach.CORRECTION -> 0.8f // Learning from mistakes
        }

        val baseScore = if (wasCorrect) responseQuality else responseQuality * 0.7f
        return (baseScore * approachMultiplier).coerceIn(0f, 1f)
    }

    private fun checkTutoringAchievements(
        progress: LearningProgressTracker.LearningProgress,
        concept: String,
        conceptScore: Float,
        approach: TutorViewModel.TeachingApproach
    ): List<LearningProgressTracker.Achievement> {
        val achievements = mutableListOf<LearningProgressTracker.Achievement>()
        val now = System.currentTimeMillis()

        // Socratic Success - Discovered answer through guided questions
        if (approach == TutorViewModel.TeachingApproach.SOCRATIC && conceptScore >= 0.8f) {
            achievements.add(
                LearningProgressTracker.Achievement(
                    id = "socratic_success_$now",
                    title = "Independent Thinker",
                    description = "Discovered the answer through guided questioning!",
                    icon = androidx.compose.material.icons.Icons.Default.Lightbulb,
                    unlockedAt = now,
                    category = LearningProgressTracker.AchievementCategory.MASTERY
                )
            )
        }

        // Persistence Achievement - Kept trying after encouragement
        if (approach == TutorViewModel.TeachingApproach.ENCOURAGEMENT && conceptScore >= 0.6f) {
            achievements.add(
                LearningProgressTracker.Achievement(
                    id = "persistence_$now",
                    title = "Never Give Up",
                    description = "Persevered through a challenging concept!",
                    icon = androidx.compose.material.icons.Icons.Default.EmojiEvents,
                    unlockedAt = now,
                    category = LearningProgressTracker.AchievementCategory.PERSISTENCE
                )
            )
        }

        // Quick Learner - High score on first attempt
        val conceptMastery = progress.conceptMasteries[concept]
        if (conceptMastery?.practiceCount == 1 && conceptScore >= 0.9f) {
            achievements.add(
                LearningProgressTracker.Achievement(
                    id = "quick_learner_$now",
                    title = "Quick Learner",
                    description = "Mastered $concept on the first try!",
                    icon = androidx.compose.material.icons.Icons.Default.Speed,
                    unlockedAt = now,
                    category = LearningProgressTracker.AchievementCategory.SPEED
                )
            )
        }

        // Deep Understanding - Asked follow-up questions
        if (progress.conceptMasteries.values.count {
                it.practiceCount > 3 && it.progressPercentage > 0.8f
            } >= 3) {
            achievements.add(
                LearningProgressTracker.Achievement(
                    id = "deep_understanding_$now",
                    title = "Deep Thinker",
                    description = "Thoroughly explored multiple concepts!",
                    icon = androidx.compose.material.icons.Icons.Default.Psychology,
                    unlockedAt = now,
                    category = LearningProgressTracker.AchievementCategory.EXPLORATION
                )
            )
        }

        return achievements
    }

    private fun updateWeeklyGoalForTutoring(
        currentGoal: LearningProgressTracker.WeeklyGoal,
        sessionDurationMs: Long,
        topic: String
    ): LearningProgressTracker.WeeklyGoal {
        val sessionMinutes = (sessionDurationMs / (60 * 1000)).toInt()

        // Check if this is a new topic for the week
        val isNewTopic = !progressCache.values.any { progress ->
            progress.weeklyGoal.weekStartTime == currentGoal.weekStartTime &&
                    progress.conceptMasteries.keys.contains(topic)
        }

        return currentGoal.copy(
            currentSessionMinutes = currentGoal.currentSessionMinutes + sessionMinutes,
            currentTopicsCount = if (isNewTopic) {
                currentGoal.currentTopicsCount + 1
            } else {
                currentGoal.currentTopicsCount
            }
        )
    }

    private suspend fun syncConceptMastery(
        studentId: String,
        subject: OfflineRAG.Subject,
        concept: String,
        progress: LearningProgressTracker.LearningProgress
    ) {
        val conceptMastery = progress.conceptMasteries[concept] ?: return

        try {
            // Update the analytics database with progress tracker data
            val existingMastery = masteryDao.getMasteryForSubject(studentId, subject.name)
                .first()
                .find { it.topic == concept }

            if (existingMastery != null) {
                masteryDao.insertOrUpdateMastery(
                    existingMastery.copy(
                        confidenceScore = conceptMastery.progressPercentage,
                        masteryLevel = mapToAnalyticsMasteryLevel(conceptMastery.level),
                        practiceCount = conceptMastery.practiceCount,
                        lastPracticed = conceptMastery.lastPracticed
                    )
                )
            } else {
                masteryDao.insertOrUpdateMastery(
                    TopicMasteryEntity(
                        id = "$studentId-${subject.name}-$concept",
                        studentId = studentId,
                        subject = subject.name,
                        topic = concept,
                        masteryLevel = mapToAnalyticsMasteryLevel(conceptMastery.level),
                        confidenceScore = conceptMastery.progressPercentage,
                        practiceCount = conceptMastery.practiceCount,
                        lastPracticed = conceptMastery.lastPracticed,
                        correctResponses = (conceptMastery.practiceCount * conceptMastery.progressPercentage).toInt()
                    )
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to sync concept mastery")
        }
    }

    private fun mapToAnalyticsMasteryLevel(level: LearningProgressTracker.MasteryLevel): MasteryLevel {
        return when (level) {
            LearningProgressTracker.MasteryLevel.NOT_STARTED -> MasteryLevel.NOT_STARTED
            LearningProgressTracker.MasteryLevel.LEARNING -> MasteryLevel.INTRODUCED
            LearningProgressTracker.MasteryLevel.PRACTICING -> MasteryLevel.DEVELOPING
            LearningProgressTracker.MasteryLevel.PROFICIENT -> MasteryLevel.PROFICIENT
            LearningProgressTracker.MasteryLevel.MASTERED -> MasteryLevel.MASTERED
        }
    }

    /**
     * Clear cache for a student (useful when switching students)
     */
    fun clearStudentCache(studentId: String) {
        progressCache.keys.removeAll { it.startsWith("$studentId-") }
    }

    /**
     * Get progress summary for UI display
     */
    fun getProgressSummary(
        studentId: String,
        subject: OfflineRAG.Subject
    ): ProgressSummary? {
        val progress = progressCache["$studentId-${subject.name}"] ?: return null

        return ProgressSummary(
            overallProgress = progress.overallProgress,
            totalConcepts = progress.conceptMasteries.size,
            masteredConcepts = progress.conceptMasteries.count {
                it.value.level == LearningProgressTracker.MasteryLevel.MASTERED
            },
            currentStreak = progress.streakDays,
            weeklyGoalProgress = progress.weeklyGoal.sessionProgress,
            recentAchievements = progress.achievements.takeLast(3),
            suggestedNextConcept = progress.conceptMasteries
                .filter { it.value.level != LearningProgressTracker.MasteryLevel.MASTERED }
                .minByOrNull { it.value.lastPracticed }
                ?.key
        )
    }

    data class ProgressSummary(
        val overallProgress: Float,
        val totalConcepts: Int,
        val masteredConcepts: Int,
        val currentStreak: Int,
        val weeklyGoalProgress: Float,
        val recentAchievements: List<LearningProgressTracker.Achievement>,
        val suggestedNextConcept: String?
    )
}