package com.example.mygemma3n.feature.analytics

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface LearningInteractionDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInteraction(interaction: LearningInteractionEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInteractions(interactions: List<LearningInteractionEntity>)
    
    @Query("SELECT * FROM learning_interactions WHERE studentId = :studentId ORDER BY timestamp DESC")
    fun getInteractionsForStudent(studentId: String): Flow<List<LearningInteractionEntity>>
    
    @Query("SELECT * FROM learning_interactions WHERE studentId = :studentId AND subject = :subject ORDER BY timestamp DESC")
    fun getInteractionsForSubject(studentId: String, subject: String): Flow<List<LearningInteractionEntity>>
    
    @Query("SELECT * FROM learning_interactions WHERE studentId = :studentId AND timestamp >= :startTime")
    suspend fun getInteractionsSince(studentId: String, startTime: Long): List<LearningInteractionEntity>
    
    @Query("SELECT COUNT(*) FROM learning_interactions WHERE studentId = :studentId AND subject = :subject")
    suspend fun getInteractionCount(studentId: String, subject: String): Int
    
    @Query("SELECT COUNT(*) FROM learning_interactions WHERE studentId = :studentId")
    suspend fun getTotalInteractionCount(studentId: String): Int
    
    @Query("SELECT AVG(responseQuality) FROM learning_interactions WHERE studentId = :studentId AND subject = :subject AND responseQuality IS NOT NULL")
    suspend fun getAverageResponseQuality(studentId: String, subject: String): Float?
    
    @Query("DELETE FROM learning_interactions WHERE studentId = :studentId")
    suspend fun clearInteractionsForStudent(studentId: String)
}

@Dao
interface SubjectProgressDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateProgress(progress: SubjectProgressEntity)
    
    @Query("SELECT * FROM subject_progress WHERE studentId = :studentId")
    fun getProgressForStudent(studentId: String): Flow<List<SubjectProgressEntity>>
    
    @Query("SELECT * FROM subject_progress WHERE studentId = :studentId AND subject = :subject")
    suspend fun getProgressForSubject(studentId: String, subject: String): SubjectProgressEntity?
    
    @Query("UPDATE subject_progress SET totalInteractions = :interactions, totalTimeSpentMs = :timeMs, lastInteraction = :timestamp WHERE studentId = :studentId AND subject = :subject")
    suspend fun updateInteractionStats(studentId: String, subject: String, interactions: Int, timeMs: Long, timestamp: Long)
    
    @Query("UPDATE subject_progress SET masteryScore = :score WHERE studentId = :studentId AND subject = :subject")
    suspend fun updateMasteryScore(studentId: String, subject: String, score: Float)
    
    @Query("DELETE FROM subject_progress WHERE studentId = :studentId")
    suspend fun clearProgressForStudent(studentId: String)
}

@Dao
interface TopicMasteryDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateMastery(mastery: TopicMasteryEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateMasteries(masteries: List<TopicMasteryEntity>)
    
    @Query("SELECT * FROM topic_mastery WHERE studentId = :studentId")
    fun getMasteryForStudent(studentId: String): Flow<List<TopicMasteryEntity>>
    
    @Query("SELECT * FROM topic_mastery WHERE studentId = :studentId AND subject = :subject ORDER BY confidenceScore DESC")
    fun getMasteryForSubject(studentId: String, subject: String): Flow<List<TopicMasteryEntity>>
    
    @Query("SELECT * FROM topic_mastery WHERE studentId = :studentId AND needsReview = 1 ORDER BY lastPracticed ASC")
    suspend fun getTopicsNeedingReview(studentId: String): List<TopicMasteryEntity>
    
    @Query("SELECT * FROM topic_mastery WHERE studentId = :studentId AND masteryLevel IN (:levels)")
    suspend fun getTopicsByMasteryLevel(studentId: String, levels: List<MasteryLevel>): List<TopicMasteryEntity>
    
    @Query("UPDATE topic_mastery SET needsReview = :needsReview WHERE studentId = :studentId AND topic = :topic")
    suspend fun updateReviewStatus(studentId: String, topic: String, needsReview: Boolean)
    
    @Query("DELETE FROM topic_mastery WHERE studentId = :studentId")
    suspend fun clearMasteryForStudent(studentId: String)
}

@Dao
interface LearningSessionDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: LearningSessionEntity)
    
    @Update
    suspend fun updateSession(session: LearningSessionEntity)
    
    @Query("SELECT * FROM learning_sessions WHERE studentId = :studentId ORDER BY startTime DESC")
    fun getSessionsForStudent(studentId: String): Flow<List<LearningSessionEntity>>
    
    @Query("SELECT * FROM learning_sessions WHERE studentId = :studentId AND startTime >= :startTime ORDER BY startTime DESC")
    suspend fun getSessionsSince(studentId: String, startTime: Long): List<LearningSessionEntity>
    
    @Query("SELECT * FROM learning_sessions WHERE id = :sessionId LIMIT 1")
    suspend fun getCurrentSession(sessionId: String): LearningSessionEntity?
    
    @Query("SELECT AVG(totalDurationMs) FROM learning_sessions WHERE studentId = :studentId AND endTime IS NOT NULL")
    suspend fun getAverageSessionDuration(studentId: String): Long?
    
    @Query("DELETE FROM learning_sessions WHERE studentId = :studentId")
    suspend fun clearSessionsForStudent(studentId: String)
}

@Dao
interface KnowledgeGapDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGap(gap: KnowledgeGapEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGaps(gaps: List<KnowledgeGapEntity>)
    
    @Update
    suspend fun updateGap(gap: KnowledgeGapEntity)
    
    @Query("SELECT * FROM knowledge_gaps WHERE studentId = :studentId AND isResolved = 0 ORDER BY priority DESC, identifiedAt DESC")
    fun getActiveGapsForStudent(studentId: String): Flow<List<KnowledgeGapEntity>>
    
    @Query("SELECT * FROM knowledge_gaps WHERE studentId = :studentId AND subject = :subject AND isResolved = 0 ORDER BY priority DESC")
    suspend fun getActiveGapsForSubject(studentId: String, subject: String): List<KnowledgeGapEntity>
    
    @Query("UPDATE knowledge_gaps SET isResolved = 1, addressedAt = :timestamp WHERE id = :gapId")
    suspend fun markGapResolved(gapId: String, timestamp: Long)
    
    @Query("DELETE FROM knowledge_gaps WHERE studentId = :studentId AND isResolved = 1 AND addressedAt < :cutoffTime")
    suspend fun cleanupResolvedGaps(studentId: String, cutoffTime: Long)
    
    @Query("DELETE FROM knowledge_gaps WHERE studentId = :studentId")
    suspend fun clearGapsForStudent(studentId: String)
}

@Dao
interface StudyRecommendationDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecommendation(recommendation: StudyRecommendationEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecommendations(recommendations: List<StudyRecommendationEntity>)
    
    @Update
    suspend fun updateRecommendation(recommendation: StudyRecommendationEntity)
    
    @Query("SELECT * FROM study_recommendations WHERE studentId = :studentId AND completedAt IS NULL AND dismissedAt IS NULL ORDER BY priority DESC, createdAt DESC")
    fun getActiveRecommendationsForStudent(studentId: String): Flow<List<StudyRecommendationEntity>>
    
    @Query("UPDATE study_recommendations SET completedAt = :timestamp WHERE id = :recommendationId")
    suspend fun markRecommendationCompleted(recommendationId: String, timestamp: Long)
    
    @Query("UPDATE study_recommendations SET dismissedAt = :timestamp WHERE id = :recommendationId")
    suspend fun dismissRecommendation(recommendationId: String, timestamp: Long)
    
    @Query("DELETE FROM study_recommendations WHERE studentId = :studentId AND (completedAt IS NOT NULL OR dismissedAt IS NOT NULL) AND createdAt < :cutoffTime")
    suspend fun cleanupOldRecommendations(studentId: String, cutoffTime: Long)
    
    @Query("DELETE FROM study_recommendations WHERE studentId = :studentId")
    suspend fun clearRecommendationsForStudent(studentId: String)
}

/**
 * Combined queries for analytics computations
 */
@Dao
interface AnalyticsComputationDao {
    
    // Weekly statistics
    @Query("""
        SELECT 
            COUNT(*) as sessionCount,
            SUM(totalDurationMs) as totalTime,
            AVG(totalDurationMs) as avgDuration,
            AVG(overallPerformance) as avgPerformance
        FROM learning_sessions 
        WHERE studentId = :studentId 
        AND startTime >= :weekStart 
        AND endTime IS NOT NULL
    """)
    suspend fun getWeeklySessionStats(studentId: String, weekStart: Long): WeeklySessionStats?
    
    // Subject accuracy over time
    @Query("""
        SELECT subject, 
               AVG(CASE WHEN wasCorrect = 1 THEN 1.0 ELSE 0.0 END) as accuracy,
               COUNT(*) as total
        FROM learning_interactions 
        WHERE studentId = :studentId 
        AND wasCorrect IS NOT NULL 
        AND timestamp >= :since
        GROUP BY subject
    """)
    suspend fun getSubjectAccuracyStats(studentId: String, since: Long): List<SubjectAccuracyStats>
    
    // Topic interaction frequency
    @Query("""
        SELECT topic, COUNT(*) as frequency, MAX(timestamp) as lastSeen
        FROM learning_interactions 
        WHERE studentId = :studentId 
        AND subject = :subject
        GROUP BY topic 
        ORDER BY frequency DESC
    """)
    suspend fun getTopicFrequency(studentId: String, subject: String): List<TopicFrequencyStats>
    
    // Performance trends (last 30 days)
    @Query("""
        SELECT 
            DATE(timestamp / 1000, 'unixepoch') as date,
            AVG(responseQuality) as avgQuality,
            COUNT(*) as interactions
        FROM learning_interactions 
        WHERE studentId = :studentId 
        AND responseQuality IS NOT NULL
        AND timestamp >= :thirtyDaysAgo
        GROUP BY DATE(timestamp / 1000, 'unixepoch')
        ORDER BY date
    """)
    suspend fun getPerformanceTrend(studentId: String, thirtyDaysAgo: Long): List<DailyPerformanceStats>
}

// Data classes for query results
data class WeeklySessionStats(
    val sessionCount: Int,
    val totalTime: Long,
    val avgDuration: Long,
    val avgPerformance: Float
)

data class SubjectAccuracyStats(
    val subject: String,
    val accuracy: Float,
    val total: Int
)

data class TopicFrequencyStats(
    val topic: String,
    val frequency: Int,
    val lastSeen: Long
)

data class DailyPerformanceStats(
    val date: String,
    val avgQuality: Float,
    val interactions: Int
)