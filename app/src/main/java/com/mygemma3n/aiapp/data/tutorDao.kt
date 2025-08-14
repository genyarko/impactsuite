package com.mygemma3n.aiapp.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.mygemma3n.aiapp.shared_utilities.OfflineRAG
import kotlinx.coroutines.flow.Flow

// feature/tutor/data/TutorDao.kt

@Dao
interface TutorDao {
    // Student Profile operations
    @Insert
    suspend fun insertStudentProfile(profile: StudentProfileEntity)

    @Update
    suspend fun updateStudentProfile(profile: StudentProfileEntity)

    @Query("SELECT * FROM student_profiles WHERE name = :name LIMIT 1")
    suspend fun getStudentByName(name: String): StudentProfileEntity?

    @Query("SELECT * FROM student_profiles WHERE id = :id")
    suspend fun getStudentById(id: String): StudentProfileEntity?

    @Query("UPDATE student_profiles SET lastActiveAt = :timestamp WHERE id = :studentId")
    suspend fun updateLastActive(studentId: String, timestamp: Long)

    @Query("SELECT * FROM student_profiles ORDER BY lastActiveAt DESC")
    suspend fun getAllStudents(): List<StudentProfileEntity>

    // Tutor Session operations
    @Insert
    suspend fun insertSession(session: TutorSessionEntity)

    @Update
    suspend fun updateSession(session: TutorSessionEntity)

    @Query("SELECT * FROM tutor_sessions WHERE studentId = :studentId ORDER BY startedAt DESC")
    fun getSessionsForStudent(studentId: String): Flow<List<TutorSessionEntity>>

    @Query("""
        SELECT * FROM tutor_sessions 
        WHERE studentId = :studentId AND subject = :subject 
        ORDER BY startedAt DESC 
        LIMIT :limit
    """)
    suspend fun getRecentSessions(
        studentId: String,
        subject: OfflineRAG.Subject,
        limit: Int = 10
    ): List<TutorSessionEntity>

    @Query("UPDATE tutor_sessions SET endedAt = :endTime, summaryNotes = :summary WHERE id = :sessionId")
    suspend fun endSession(sessionId: String, endTime: Long, summary: String?)

    // Learning Preferences operations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLearningPreference(preference: LearningPreferenceEntity)

    @Query("""
        SELECT * FROM learning_preferences 
        WHERE studentId = :studentId AND subject = :subject
    """)
    suspend fun getLearningPreference(
        studentId: String,
        subject: OfflineRAG.Subject
    ): LearningPreferenceEntity?

    // Concept Mastery operations
    @Insert
    suspend fun insertConceptMastery(mastery: ConceptMasteryEntity)

    @Update
    suspend fun updateConceptMastery(mastery: ConceptMasteryEntity)

    @Query("""
        SELECT * FROM concept_mastery 
        WHERE studentId = :studentId AND subject = :subject AND concept = :concept
        LIMIT 1
    """)
    suspend fun getConceptMastery(
        studentId: String,
        subject: OfflineRAG.Subject,
        concept: String
    ): ConceptMasteryEntity?

    @Query("""
        SELECT * FROM concept_mastery 
        WHERE studentId = :studentId AND subject = :subject
        ORDER BY masteryLevel DESC
    """)
    fun getConceptMasteryFlow(
        studentId: String,
        subject: OfflineRAG.Subject
    ): Flow<List<ConceptMasteryEntity>>

    @Query("""
        SELECT * FROM concept_mastery 
        WHERE studentId = :studentId AND subject = :subject AND masteryLevel < :threshold
        ORDER BY lastReviewedAt ASC
    """)
    suspend fun getWeakConcepts(
        studentId: String,
        subject: OfflineRAG.Subject,
        threshold: Float
    ): List<ConceptMasteryEntity>

    @Query("""
        SELECT * FROM concept_mastery 
        WHERE studentId = :studentId AND gradeLevel = :gradeLevel
        ORDER BY masteryLevel ASC
        LIMIT :limit
    """)
    suspend fun getConceptsForReview(
        studentId: String,
        gradeLevel: Int,
        limit: Int = 5
    ): List<ConceptMasteryEntity>

    // Analytics queries
    @Query("""
        SELECT subject, AVG(understandingLevel) as averageUnderstanding
        FROM tutor_sessions 
        WHERE studentId = :studentId AND endedAt IS NOT NULL
        GROUP BY subject
    """)
    suspend fun getSubjectPerformance(studentId: String): List<SubjectPerformance>

    @Query("""
        SELECT COUNT(*) as sessionCount, SUM(endedAt - startedAt) as totalTime
        FROM tutor_sessions
        WHERE studentId = :studentId AND startedAt > :since
    """)
    suspend fun getStudyStats(studentId: String, since: Long): StudyStats

    @Query("""
        SELECT conceptsCovered, COUNT(*) as frequency
        FROM tutor_sessions
        WHERE studentId = :studentId AND subject = :subject
        GROUP BY conceptsCovered
        ORDER BY frequency DESC
        LIMIT 10
    """)
    suspend fun getMostStudiedConcepts(
        studentId: String,
        subject: OfflineRAG.Subject
    ): List<ConceptFrequency>
}

// Data classes for complex queries
data class SubjectPerformance(
    val subject: OfflineRAG.Subject,
    val averageUnderstanding: Float
)

data class StudyStats(
    val sessionCount: Int,
    val totalTime: Long
)

data class ConceptFrequency(
    val conceptsCovered: String,
    val frequency: Int
)