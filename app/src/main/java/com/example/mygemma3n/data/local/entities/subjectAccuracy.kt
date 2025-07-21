package com.example.mygemma3n.data.local.entities

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.mygemma3n.feature.quiz.Difficulty
import com.example.mygemma3n.feature.quiz.UserProgress
import com.google.mlkit.vision.segmentation.subject.Subject

// Define a POJO to match your query result
data class SubjectAccuracy(
    val subject: Subject,
    val accuracy: Float
)

// DAO returns a list of POJOs
@Dao
interface UserProgressDao {
    @Insert suspend fun insert(progress: UserProgress)

    @Query(
        """
        SELECT AVG(CASE WHEN correct THEN 1 ELSE 0 END)
        FROM user_progress
        WHERE subject = :subject AND timestamp > :since
    """
    )
    suspend fun recentAccuracy(subject: com.example.mygemma3n.feature.quiz.Subject, since: Long): Float

    @Query(
        """
        SELECT AVG(CASE WHEN correct THEN 1 ELSE 0 END)
        FROM user_progress
        WHERE subject = :subject AND difficulty = :difficulty 
        AND timestamp > :since
    """
    )
    suspend fun recentAccuracyByDifficulty(
        subject: com.example.mygemma3n.feature.quiz.Subject,
        difficulty: Difficulty,
        since: Long
    ): Float?

    @Query("SELECT * FROM user_progress WHERE timestamp > :since")
    suspend fun getRecentProgress(since: Long): List<UserProgress>
}