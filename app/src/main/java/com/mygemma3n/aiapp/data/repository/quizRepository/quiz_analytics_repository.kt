package com.mygemma3n.aiapp.data.repository.quizRepository


import com.mygemma3n.aiapp.feature.quiz.ConceptMasteryInfo
import com.mygemma3n.aiapp.feature.quiz.DailyProgress
import com.mygemma3n.aiapp.feature.quiz.Difficulty
import com.mygemma3n.aiapp.feature.quiz.DifficultyStats
import com.mygemma3n.aiapp.feature.quiz.MasteryTrend
import com.mygemma3n.aiapp.feature.quiz.MissedQuestionInfo
import com.mygemma3n.aiapp.feature.quiz.Question
import com.mygemma3n.aiapp.feature.quiz.QuestionSession
import com.mygemma3n.aiapp.feature.quiz.Quiz
import com.mygemma3n.aiapp.feature.quiz.QuizAnalytics
import com.mygemma3n.aiapp.feature.quiz.QuizDatabase
import com.mygemma3n.aiapp.feature.quiz.QuizEntity
import com.mygemma3n.aiapp.feature.quiz.Subject
import com.mygemma3n.aiapp.feature.quiz.SubjectAnalytics
import com.mygemma3n.aiapp.feature.quiz.TopicCoverageInfo
import com.mygemma3n.aiapp.feature.quiz.UserProgress
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

@Singleton
class QuizAnalyticsRepository @Inject constructor(
    private val db: QuizDatabase,
    private val gson: Gson
) {

    suspend fun getQuizAnalytics(): QuizAnalytics {
        val now = System.currentTimeMillis()
        val oneWeekAgo = now - (7L * 24 * 60 * 60 * 1000)
        val oneMonthAgo = now - (30L * 24 * 60 * 60 * 1000)
        val todayStart = getTodayStart()

        // Get overall stats
        val allProgress = db.progressDao().getRecentProgress(oneMonthAgo)
        val overallAccuracy = if (allProgress.isNotEmpty()) {
            allProgress.count { it.correct }.toFloat() / allProgress.size
        } else 0f

        val todayQuestions = allProgress.count { it.timestamp >= todayStart }

        // Calculate streaks
        val (currentStreak, longestStreak) = calculateStreaks()

        // Get subject performance
        val subjectPerformance = calculateSubjectPerformance(oneMonthAgo)

        // Get frequently missed questions
        val frequentlyMissed = getFrequentlyMissedQuestions()

        // Get topic coverage
        val topicCoverage = calculateTopicCoverage()

        // Get difficulty breakdown
        val difficultyBreakdown = calculateDifficultyBreakdown(oneMonthAgo)

        // Get weekly progress
        val weeklyProgress = calculateWeeklyProgress(oneWeekAgo)

        // Get concept mastery
        val conceptMastery = calculateConceptMastery()

        return QuizAnalytics(
            overallAccuracy = overallAccuracy,
            totalQuestionsAnswered = allProgress.size,
            questionsToday = todayQuestions,
            currentStreak = currentStreak,
            longestStreak = longestStreak,
            subjectPerformance = subjectPerformance,
            frequentlyMissedQuestions = frequentlyMissed,
            topicCoverage = topicCoverage,
            difficultyBreakdown = difficultyBreakdown,
            weeklyProgress = weeklyProgress,
            conceptMastery = conceptMastery
        )
    }

    private suspend fun calculateStreaks(): Pair<Int, Int> {
        val allQuizzes = db.quizDao().getAll()
            .map { list -> list.map { it.toDomain(gson) } }
            .map { quizzes ->
                quizzes.filter { it.completedAt != null }
                    .sortedByDescending { it.completedAt }
            }

        var currentStreak = 0
        var longestStreak = 0
        var tempStreak = 0
        var lastDate = 0L

        // This is a simplified implementation - you'd want to check actual dates
        // For now, just counting consecutive days with activity

        return Pair(currentStreak, longestStreak)
    }

    private suspend fun calculateSubjectPerformance(since: Long): Map<Subject, SubjectAnalytics> {
        val subjects = Subject.entries
        val result = mutableMapOf<Subject, SubjectAnalytics>()

        subjects.forEach { subject ->
            val progress = db.progressDao().getRecentProgress(since)
                .filter { it.subject == subject }

            if (progress.isNotEmpty()) {
                val accuracy = progress.count { it.correct }.toFloat() / progress.size
                val avgTime = progress.map { it.responseTimeMs }.average().toFloat() / 1000f

                // Get topic breakdown
                val topicAccuracy = mutableMapOf<String, MutableList<Boolean>>()
                progress.forEach { p ->
                    val concepts = gson.fromJson(p.conceptsTested, Array<String>::class.java) ?: emptyArray()
                    concepts.forEach { concept ->
                        topicAccuracy.getOrPut(concept) { mutableListOf() }.add(p.correct)
                    }
                }

                val topicBreakdown = topicAccuracy.mapValues { (_, results) ->
                    results.count { it }.toFloat() / results.size
                }

                result[subject] = SubjectAnalytics(
                    accuracy = accuracy,
                    questionsAnswered = progress.size,
                    averageTimePerQuestion = avgTime,
                    lastAttempted = progress.maxOf { it.timestamp },
                    topicBreakdown = topicBreakdown
                )
            }
        }

        return result
    }

    private suspend fun getFrequentlyMissedQuestions(): List<MissedQuestionInfo> {
        // Get all question history where success rate is low
        val allHistory = db.questionHistoryDao().getMasteredConcepts(Subject.GENERAL)

        // This is simplified - in real implementation, you'd query all subjects
        // and calculate miss rates
        val missedQuestions = mutableListOf<MissedQuestionInfo>()

        // Query wrong answers and group by question
        val wrongAnswers = Subject.entries.flatMap { subject ->
            db.wrongAnswerDao().getRecentWrongAnswersByConcept("%", 100)
        }

        val groupedByQuestion = wrongAnswers.groupBy { it.questionText }

        groupedByQuestion.forEach { (questionText, wrongs) ->
            if (wrongs.size >= 2) { // At least 2 wrong attempts
                missedQuestions.add(
                    MissedQuestionInfo(
                        questionText = questionText,
                        timesAttempted = wrongs.size + 1, // Assume at least one more attempt
                        timesCorrect = 1, // Simplified
                        lastAttempted = wrongs.maxOf { it.attemptedAt },
                        concepts = gson.fromJson(wrongs.first().conceptsTested, Array<String>::class.java)?.toList() ?: emptyList(),
                        difficulty = Difficulty.MEDIUM, // Would need to store this
                        subject = Subject.GENERAL // Would need to store this
                    )
                )
            }
        }

        return missedQuestions.sortedByDescending { it.timesAttempted - it.timesCorrect }
    }

    private suspend fun calculateTopicCoverage(): Map<String, TopicCoverageInfo> {
        // Load curriculum topics and calculate coverage
        val allContent = db.contentDao().getAll()
        val topicCoverage = mutableMapOf<String, TopicCoverageInfo>()

        // Get all attempted questions
        val allQuizzes = db.quizDao().getAll()
            .map { list -> list.map { it.toDomain(gson) } }

        // This is a simplified implementation
        // In reality, you'd match questions to curriculum topics

        return topicCoverage
    }

    private suspend fun calculateDifficultyBreakdown(since: Long): Map<Difficulty, DifficultyStats> {
        val difficulties = Difficulty.entries
        val result = mutableMapOf<Difficulty, DifficultyStats>()

        difficulties.forEach { difficulty ->
            val progress = db.progressDao().getRecentProgress(since)
                .filter { it.difficulty == difficulty }

            if (progress.isNotEmpty()) {
                val accuracy = progress.count { it.correct }.toFloat() / progress.size
                val avgTime = progress.map { it.responseTimeMs }.average().toFloat() / 1000f

                result[difficulty] = DifficultyStats(
                    questionsAttempted = progress.size,
                    accuracy = accuracy,
                    averageTime = avgTime
                )
            } else {
                result[difficulty] = DifficultyStats(0, 0f, 0f)
            }
        }

        return result
    }

    private suspend fun calculateWeeklyProgress(since: Long): List<DailyProgress> {
        val progress = db.progressDao().getRecentProgress(since)
        val dailyMap = mutableMapOf<Long, MutableList<UserProgress>>()

        progress.forEach { p ->
            val dayStart = getDayStart(p.timestamp)
            dailyMap.getOrPut(dayStart) { mutableListOf() }.add(p)
        }

        return dailyMap.map { (date, dayProgress) ->
            DailyProgress(
                date = date,
                questionsAnswered = dayProgress.size,
                accuracy = if (dayProgress.isNotEmpty()) {
                    dayProgress.count { it.correct }.toFloat() / dayProgress.size
                } else 0f,
                subjects = dayProgress.map { it.subject }.toSet()
            )
        }.sortedBy { it.date }
    }

    private suspend fun calculateConceptMastery(): Map<String, ConceptMasteryInfo> {
        val conceptStats = mutableMapOf<String, MutableList<Pair<Long, Boolean>>>()

        // Collect all concept attempts
        val progress = db.progressDao().getRecentProgress(0) // All time
        progress.forEach { p ->
            val concepts = gson.fromJson(p.conceptsTested, Array<String>::class.java) ?: emptyArray()
            concepts.forEach { concept ->
                conceptStats.getOrPut(concept) { mutableListOf() }
                    .add(Pair(p.timestamp, p.correct))
            }
        }

        // Calculate mastery for each concept
        return conceptStats.mapValues { (concept, attempts) ->
            val sorted = attempts.sortedBy { it.first }
            val recent = sorted.takeLast(10)
            val recentAccuracy = recent.count { it.second }.toFloat() / recent.size
            val older = sorted.dropLast(10).takeLast(10)
            val olderAccuracy = if (older.isNotEmpty()) {
                older.count { it.second }.toFloat() / older.size
            } else recentAccuracy

            val trend = when {
                recentAccuracy > olderAccuracy + 0.1f -> MasteryTrend.IMPROVING
                recentAccuracy < olderAccuracy - 0.1f -> MasteryTrend.DECLINING
                else -> MasteryTrend.STABLE
            }

            ConceptMasteryInfo(
                concept = concept,
                masteryLevel = recentAccuracy,
                questionsAnswered = attempts.size,
                lastSeen = attempts.maxOf { it.first },
                trend = trend
            )
        }
    }

    private fun getTodayStart(): Long {
        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    private fun getDayStart(timestamp: Long): Long {
        val calendar = java.util.Calendar.getInstance()
        calendar.timeInMillis = timestamp
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    // Entity conversion helper
    private fun QuizEntity.toDomain(gson: Gson): Quiz = Quiz(
        id = id,
        subject = subject,
        topic = topic,
        questions = gson.fromJson(questionsJson, Array<Question>::class.java).toList(),
        difficulty = difficulty,
        mode = mode,
        createdAt = createdAt,
        completedAt = completedAt,
        score = score
    )
}

// Adaptive cooldown system
@Singleton
class AdaptiveCooldownManager @Inject constructor(
    private val db: QuizDatabase
) {

    suspend fun calculateAdaptiveCooldown(
        questionHash: Int,
        wasCorrect: Boolean
    ): Int {
        // Get history for this question
        val sessions = mutableListOf<QuestionSession>()
        var currentHash = questionHash

        // Get last 5 sessions for this question
        for (i in 0..4) {
            val session = db.questionSessionDao().getLatestSession(currentHash)
            if (session != null) {
                sessions.add(session)
            } else {
                break
            }
        }

        // Calculate adaptive cooldown
        val consecutiveCorrect = sessions.takeWhile { it.wasCorrect }.size

        return when {
            !wasCorrect -> 1 // Always 1 session for incorrect
            consecutiveCorrect >= 4 -> 10 // Max cooldown for 4+ consecutive correct
            consecutiveCorrect >= 3 -> 7
            consecutiveCorrect >= 2 -> 5
            consecutiveCorrect >= 1 -> 3
            else -> 3 // Default for first correct answer
        }
    }

    suspend fun updateQuestionSession(
        questionText: String,
        wasCorrect: Boolean,
        currentSessionNumber: Int
    ) {
        val hash = questionText.hashCode()
        val adaptiveCooldown = calculateAdaptiveCooldown(hash, wasCorrect)

        val session = QuestionSession(
            questionHash = hash,
            questionText = questionText,
            lastSessionNumber = currentSessionNumber,
            wasCorrect = wasCorrect,
            cooldownSessions = adaptiveCooldown
        )

        db.questionSessionDao().insertSession(session)

        Timber.d("Updated question session: cooldown = $adaptiveCooldown sessions (consecutive correct: ${if (wasCorrect) "yes" else "no"})")
    }
}

// Topic rotation tracker
@Singleton
class TopicRotationTracker @Inject constructor(
    private val db: QuizDatabase,
    private val gson: Gson
) {

    data class TopicRotationInfo(
        val topic: String,
        val subject: Subject,
        val lastCovered: Long?,
        val timesCovered: Int,
        val averageAccuracy: Float,
        val priority: Float // Higher = needs more attention
    )

    suspend fun getTopicRotationPriorities(
        subject: Subject,
        gradeLevel: Int
    ): List<TopicRotationInfo> {
        // Get all curriculum topics for this subject and grade
        val curriculumTopics = loadCurriculumTopicsForGrade(subject, gradeLevel)

        // Get coverage data for each topic
        val topicInfoList = mutableListOf<TopicRotationInfo>()

        curriculumTopics.forEach { topicName ->
            val quizzesForTopic = db.quizDao()
                .getBySubjectAndTopic(subject.name, topicName)
                .map { it.toDomain(gson) }

            val lastCovered = quizzesForTopic
                .mapNotNull { it.completedAt }
                .maxOrNull()

            val allQuestions = quizzesForTopic.flatMap { it.questions }
            val accuracy = if (allQuestions.isNotEmpty()) {
                allQuestions.count { it.userAnswer == it.correctAnswer }.toFloat() / allQuestions.size
            } else 0f

            // Calculate priority based on:
            // 1. Time since last covered (older = higher priority)
            // 2. Low coverage count
            // 3. Low accuracy (needs reinforcement)
            val daysSinceLastCovered = if (lastCovered != null) {
                (System.currentTimeMillis() - lastCovered) / (24L * 60 * 60 * 1000)
            } else 999L // Very high if never covered

            val priority = calculateTopicPriority(
                daysSinceLastCovered = daysSinceLastCovered.toFloat(),
                timesCovered = quizzesForTopic.size,
                accuracy = accuracy
            )

            topicInfoList.add(
                TopicRotationInfo(
                    topic = topicName,
                    subject = subject,
                    lastCovered = lastCovered,
                    timesCovered = quizzesForTopic.size,
                    averageAccuracy = accuracy,
                    priority = priority
                )
            )
        }

        return topicInfoList.sortedByDescending { it.priority }
    }

    private fun calculateTopicPriority(
        daysSinceLastCovered: Float,
        timesCovered: Int,
        accuracy: Float
    ): Float {
        // Weighted priority calculation
        val timeFactor = minOf(daysSinceLastCovered / 7f, 2f) // Max 2x weight for old topics
        val coverageFactor = maxOf(1f - (timesCovered / 5f), 0.2f) // Less coverage = higher priority
        val accuracyFactor = maxOf(1f - accuracy, 0.2f) // Lower accuracy = higher priority

        return timeFactor * 0.4f + coverageFactor * 0.3f + accuracyFactor * 0.3f
    }

    private suspend fun loadCurriculumTopicsForGrade(
        subject: Subject,
        gradeLevel: Int
    ): List<String> {
        // This would load from your curriculum files
        // For now, returning a placeholder
        return when (subject) {
            Subject.MATHEMATICS -> listOf(
                "Numbers and Operations",
                "Algebra",
                "Geometry",
                "Measurement",
                "Data Analysis"
            )
            Subject.SCIENCE -> listOf(
                "Life Science",
                "Physical Science",
                "Earth Science",
                "Scientific Method"
            )
            else -> listOf("General Topics")
        }
    }

    suspend fun suggestNextTopic(
        subject: Subject,
        gradeLevel: Int,
        recentTopics: List<String>
    ): String? {
        val priorities = getTopicRotationPriorities(subject, gradeLevel)

        // Filter out very recent topics
        val filtered = priorities.filter { topic ->
            !recentTopics.take(3).contains(topic.topic)
        }

        // Return highest priority topic
        return filtered.firstOrNull()?.topic
    }

    // Helper for entity conversion
    private fun QuizEntity.toDomain(gson: Gson): Quiz = Quiz(
        id = id,
        subject = subject,
        topic = topic,
        questions = gson.fromJson(questionsJson, Array<Question>::class.java).toList(),
        difficulty = difficulty,
        mode = mode,
        createdAt = createdAt,
        completedAt = completedAt,
        score = score
    )
}