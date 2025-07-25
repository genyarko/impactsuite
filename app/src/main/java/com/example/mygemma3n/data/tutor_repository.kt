package com.example.mygemma3n.data

import com.example.mygemma3n.shared_utilities.OfflineRAG
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

// feature/tutor/data/TutorRepository.kt

@Singleton
class TutorRepository @Inject constructor(
    private val tutorDao: TutorDao,
    private val chatRepository: ChatRepository,
    private val gson: Gson
) {
    suspend fun createOrGetStudentProfile(name: String, gradeLevel: Int): StudentProfileEntity {
        val existing = tutorDao.getStudentByName(name)
        return if (existing != null) {
            tutorDao.updateLastActive(existing.id, System.currentTimeMillis())
            existing
        } else {
            val newProfile = StudentProfileEntity(
                name = name,
                gradeLevel = gradeLevel,
                preferredLearningStyle = LearningStyle.VERBAL // default
            )
            tutorDao.insertStudentProfile(newProfile)
            newProfile
        }
    }

    suspend fun startTutorSession(
        studentId: String,
        subject: OfflineRAG.Subject,
        sessionType: TutorSessionType,
        topic: String
    ): Pair<String, String> {
        val sessionId = UUID.randomUUID().toString()

        // Create tutor session
        val session = TutorSessionEntity(
            id = sessionId,
            studentId = studentId,
            subject = subject,
            sessionType = sessionType,
            topic = topic,
            startedAt = System.currentTimeMillis(),
            conceptsCovered = "[]"
        )
        tutorDao.insertSession(session)

        // Create corresponding chat session and capture ID
        val chatTitle = "${subject.name} - $topic"
        val chatSessionId = chatRepository.createNewSession(chatTitle)

        return sessionId to chatSessionId
    }

    suspend fun updateConceptMastery(
        studentId: String,
        subject: OfflineRAG.Subject,
        concept: String,
        gradeLevel: Int,
        performanceIndicator: Float
    ) {
        val existing = tutorDao.getConceptMastery(studentId, subject, concept)

        if (existing != null) {
            // Update with weighted average
            val newMastery = (existing.masteryLevel * 0.7f + performanceIndicator * 0.3f)
            tutorDao.updateConceptMastery(
                existing.copy(
                    masteryLevel = newMastery.coerceIn(0f, 1f),
                    lastReviewedAt = System.currentTimeMillis(),
                    reviewCount = existing.reviewCount + 1
                )
            )
        } else {
            tutorDao.insertConceptMastery(
                ConceptMasteryEntity(
                    studentId = studentId,
                    subject = subject,
                    concept = concept,
                    gradeLevel = gradeLevel,
                    masteryLevel = performanceIndicator,
                    lastReviewedAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun getStudentMasteryForSubject(
        studentId: String,
        subject: OfflineRAG.Subject
    ): Flow<List<ConceptMasteryEntity>> {
        return tutorDao.getConceptMasteryFlow(studentId, subject)
    }

    suspend fun getWeakConcepts(
        studentId: String,
        subject: OfflineRAG.Subject,
        threshold: Float = 0.6f
    ): List<ConceptMasteryEntity> {
        return tutorDao.getWeakConcepts(studentId, subject, threshold)
    }
}