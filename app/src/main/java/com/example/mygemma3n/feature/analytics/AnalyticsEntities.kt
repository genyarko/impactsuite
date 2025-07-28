package com.example.mygemma3n.feature.analytics

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

/**
 * Tracks individual learning interactions (questions asked, topics explored, etc.)
 */
@Entity(
    tableName = "learning_interactions",
    indices = [Index("studentId"), Index("subject"), Index("timestamp")]
)
data class LearningInteractionEntity(
    @PrimaryKey
    val id: String,
    val studentId: String,
    val subject: String, // Subject name as string for flexibility
    val topic: String,
    val concept: String,
    val interactionType: InteractionType,
    val timestamp: Long = System.currentTimeMillis(),
    val sessionDurationMs: Long,
    val responseQuality: Float? = null, // 0.0-1.0 score
    val difficultyLevel: String,
    val wasCorrect: Boolean? = null, // For quiz interactions
    val attemptsNeeded: Int = 1,
    val helpRequested: Boolean = false,
    val followUpQuestions: Int = 0
)

/**
 * Aggregated progress data for subjects and topics
 */
@Entity(
    tableName = "subject_progress",
    indices = [Index("studentId"), Index("subject")]
)
data class SubjectProgressEntity(
    @PrimaryKey
    val id: String,
    val studentId: String,
    val subject: String,
    val totalInteractions: Int = 0,
    val totalTimeSpentMs: Long = 0,
    val masteryScore: Float = 0.0f, // 0.0-1.0
    val topicsExplored: Int = 0,
    val averageAccuracy: Float = 0.0f,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val lastInteraction: Long = 0,
    val knowledgeGaps: String = "[]" // JSON array of struggling topics
)

/**
 * Detailed topic-level mastery tracking
 */
@Entity(
    tableName = "topic_mastery",
    indices = [Index("studentId"), Index("subject"), Index("topic")]
)
data class TopicMasteryEntity(
    @PrimaryKey
    val id: String,
    val studentId: String,
    val subject: String,
    val topic: String,
    val masteryLevel: MasteryLevel,
    val confidenceScore: Float = 0.0f, // 0.0-1.0
    val practiceCount: Int = 0,
    val correctResponses: Int = 0,
    val averageResponseTime: Long = 0,
    val lastPracticed: Long = 0,
    val needsReview: Boolean = false,
    val difficultyTrend: String = "STABLE", // IMPROVING, DECLINING, STABLE
    val conceptConnections: String = "[]" // JSON array of related concepts
)

/**
 * Learning session summary data
 */
@Entity(
    tableName = "learning_sessions",
    indices = [Index("studentId"), Index("startTime")]
)
data class LearningSessionEntity(
    @PrimaryKey
    val id: String,
    val studentId: String,
    val startTime: Long,
    val endTime: Long? = null,
    val totalDurationMs: Long = 0,
    val interactionCount: Int = 0,
    val subjectsVisited: String = "[]", // JSON array
    val topicsExplored: String = "[]", // JSON array
    val overallPerformance: Float = 0.0f,
    val focusScore: Float = 0.0f, // Based on time between interactions
    val sessionType: String = "MIXED" // TUTORING, QUIZ, MIXED
)

/**
 * Knowledge gap analysis results
 */
@Entity(
    tableName = "knowledge_gaps",
    indices = [Index("studentId"), Index("subject"), Index("priority")]
)
data class KnowledgeGapEntity(
    @PrimaryKey
    val id: String,
    val studentId: String,
    val subject: String,
    val topic: String,
    val concept: String,
    val gapType: GapType,
    val priority: GapPriority,
    val description: String,
    val suggestedActions: String = "[]", // JSON array of recommended activities
    val prerequisiteTopics: String = "[]", // JSON array of topics to review first
    val identifiedAt: Long = System.currentTimeMillis(),
    val addressedAt: Long? = null,
    val isResolved: Boolean = false
)

/**
 * Study recommendations based on analytics
 */
@Entity(
    tableName = "study_recommendations",
    indices = [Index("studentId"), Index("priority")]
)
data class StudyRecommendationEntity(
    @PrimaryKey
    val id: String,
    val studentId: String,
    val title: String,
    val description: String,
    val subject: String,
    val topic: String,
    val recommendationType: RecommendationType,
    val priority: Int, // 1-5, 5 being highest
    val estimatedTimeMinutes: Int,
    val createdAt: Long = System.currentTimeMillis(),
    val scheduledFor: Long? = null,
    val completedAt: Long? = null,
    val dismissedAt: Long? = null
)

// Enums for type safety

enum class InteractionType {
    QUESTION_ASKED,
    TOPIC_EXPLORED,
    QUIZ_COMPLETED,
    CONCEPT_REVIEWED,
    HELP_REQUESTED,
    FOLLOW_UP_QUESTION,
    CHAT_MESSAGE
}

enum class MasteryLevel {
    NOT_STARTED,     // 0%
    INTRODUCED,      // 1-25%
    DEVELOPING,      // 26-50%
    PROFICIENT,      // 51-75%
    ADVANCED,        // 76-90%
    MASTERED         // 91-100%
}

enum class GapType {
    CONCEPTUAL_MISUNDERSTANDING,
    MISSING_PREREQUISITE,
    PROCEDURAL_ERROR,
    INSUFFICIENT_PRACTICE,
    DECLINING_PERFORMANCE,
    KNOWLEDGE_FRAGMENTATION
}

enum class GapPriority {
    LOW,      // Address when convenient
    MEDIUM,   // Should address soon
    HIGH,     // Important for continued progress
    CRITICAL  // Blocking further learning
}

enum class RecommendationType {
    REVIEW_TOPIC,
    PRACTICE_MORE,
    EXPLORE_PREREQUISITES,
    TRY_DIFFERENT_APPROACH,
    TAKE_BREAK,
    CHALLENGE_YOURSELF,
    CONNECT_CONCEPTS
}

/**
 * Data class for analytics computations (not stored in database)
 */
data class LearningAnalytics(
    val studentId: String,
    val overallProgress: Float,
    val subjectProgress: Map<String, SubjectAnalytics>,
    val knowledgeGaps: List<KnowledgeGapEntity>,
    val recommendations: List<StudyRecommendationEntity>,
    val weeklyStats: WeeklyStats,
    val trends: LearningTrends
)

data class SubjectAnalytics(
    val subject: String,
    val masteryScore: Float,
    val timeSpent: Long,
    val topicsExplored: Int,
    val totalTopics: Int,
    val accuracy: Float,
    val currentStreak: Int,
    val needsAttention: List<String>,
    val strongAreas: List<String>
)

data class WeeklyStats(
    val totalTimeSpent: Long,
    val sessionsCompleted: Int,
    val topicsExplored: Int,
    val averageSessionDuration: Long,
    val mostActiveSubject: String,
    val improvementAreas: List<String>
)

data class LearningTrends(
    val masteryTrend: TrendDirection, // Overall mastery improving/declining
    val engagementTrend: TrendDirection, // Time spent increasing/decreasing
    val accuracyTrend: TrendDirection, // Getting more/less accurate
    val focusTrend: TrendDirection, // Sessions getting longer/shorter
    val consistencyScore: Float // How regular is their learning
)

enum class TrendDirection {
    IMPROVING,
    DECLINING,
    STABLE,
    INSUFFICIENT_DATA
}