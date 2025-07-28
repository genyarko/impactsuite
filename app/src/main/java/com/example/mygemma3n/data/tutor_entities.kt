package com.example.mygemma3n.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.ForeignKey
import com.example.mygemma3n.shared_utilities.OfflineRAG
import java.util.UUID
import androidx.room.PrimaryKey


@Entity(
    tableName = "student_profiles",
    indices = [
        Index(value = ["gradeLevel"]),
        Index(value = ["lastActiveAt"]),
        Index(value = ["createdAt"])
    ]
)
data class StudentProfileEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val gradeLevel: Int, // 1-12
    val preferredLearningStyle: LearningStyle,
    val createdAt: Long = System.currentTimeMillis(),
    val lastActiveAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "tutor_sessions",
    foreignKeys = [
        ForeignKey(
            entity = StudentProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["studentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["studentId"]),
        Index(value = ["subject"]),
        Index(value = ["startedAt"]),
        Index(value = ["endedAt"]),
        Index(value = ["studentId", "subject"]),
        Index(value = ["studentId", "startedAt"])
    ]
)
data class TutorSessionEntity(
    @PrimaryKey val id: String,
    val studentId: String,
    val subject: OfflineRAG.Subject,
    val sessionType: TutorSessionType,
    val topic: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val summaryNotes: String? = null,
    val conceptsCovered: String, // JSON array
    val understandingLevel: Float? = null // 0-1 scale
)

@Entity(
    tableName = "learning_preferences",
    foreignKeys = [
        ForeignKey(
            entity = StudentProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["studentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["studentId"]),
        Index(value = ["subject"]),
        Index(value = ["studentId", "subject"], unique = true)
    ]
)
data class LearningPreferenceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val studentId: String,
    val subject: OfflineRAG.Subject,
    val preferredExplanationDepth: ExplanationDepth,
    val includeExamples: Boolean = true,
    val preferredPace: LearningPace,
    val visualAidsPreference: Boolean = false
)

@Entity(
    tableName = "concept_mastery",
    foreignKeys = [
        ForeignKey(
            entity = StudentProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["studentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["studentId"]),
        Index(value = ["subject"]),
        Index(value = ["concept"]),
        Index(value = ["gradeLevel"]),
        Index(value = ["lastReviewedAt"]),
        Index(value = ["masteryLevel"]),
        Index(value = ["studentId", "subject"]),
        Index(value = ["studentId", "concept"], unique = true),
        Index(value = ["subject", "gradeLevel"])
    ]
)
data class ConceptMasteryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val studentId: String,
    val subject: OfflineRAG.Subject,
    val concept: String,
    val gradeLevel: Int,
    val masteryLevel: Float, // 0-1
    val lastReviewedAt: Long,
    val reviewCount: Int = 0
)

// Enums
enum class LearningStyle { VISUAL, VERBAL, LOGICAL, KINESTHETIC }
enum class TutorSessionType { HOMEWORK_HELP, CONCEPT_EXPLANATION, EXAM_PREP, PRACTICE_PROBLEMS }
enum class ExplanationDepth { SIMPLE, STANDARD, DETAILED }
enum class LearningPace { SLOW, NORMAL, FAST }