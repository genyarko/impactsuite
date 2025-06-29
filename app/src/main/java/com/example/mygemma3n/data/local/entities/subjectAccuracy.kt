package com.example.mygemma3n.data.local.entities

import androidx.room.Dao
import androidx.room.Query
import com.example.mygemma3n.shared_utilities.OfflineRAG

// Define a POJO to match your query result
data class SubjectAccuracy(
    val subject: OfflineRAG.Subject,
    val accuracy: Float
)

// DAO returns a list of POJOs
@Dao
interface UserProgressDao {
    @Query("""
    SELECT subject,
           SUM(CASE WHEN correct THEN 1 ELSE 0 END) * 1.0 / COUNT(*) AS accuracy
    FROM user_progress
    GROUP BY subject
  """)
    suspend fun getAccuracyBySubject(): List<SubjectAccuracy>
}
