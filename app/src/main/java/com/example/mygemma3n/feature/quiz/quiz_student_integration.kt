package com.example.mygemma3n.feature.quiz

import com.example.mygemma3n.data.StudentProfileEntity
import com.example.mygemma3n.data.TutorRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QuizStudentIntegration @Inject constructor(
    private val tutorRepository: TutorRepository,
    private val quizRepository: QuizRepository
) {

    /**
     * Get or create student profile for quiz
     */
    suspend fun getOrCreateStudent(name: String, gradeLevel: Int): StudentProfileEntity {
        return tutorRepository.createOrGetStudentProfile(name, gradeLevel)
    }

    /**
     * Get suggested quiz difficulty based on student performance
     */
    suspend fun getSuggestedDifficulty(
        student: StudentProfileEntity,
        subject: Subject
    ): Difficulty {
        // Get recent performance
        val oneWeekAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000)
        val accuracy = quizRepository.progressDao().recentAccuracy(subject, oneWeekAgo)

        return when {
            student.gradeLevel <= 2 -> Difficulty.EASY // Always easy for very young
            student.gradeLevel <= 5 && accuracy < 0.6f -> Difficulty.EASY
            student.gradeLevel <= 5 -> Difficulty.MEDIUM
            accuracy > 0.85f -> Difficulty.HARD
            accuracy > 0.7f -> Difficulty.MEDIUM
            else -> Difficulty.EASY
        }
    }

    /**
     * Check if student has completed similar topics recently
     */
    suspend fun getRecentTopics(
        studentId: String,
        subject: Subject,
        daysBack: Int = 7
    ): List<String> {
        val since = System.currentTimeMillis() - (daysBack * 24 * 60 * 60 * 1000L)
        return quizRepository.getQuizzesFor(subject, "")
            .filter { it.completedAt != null && it.completedAt > since }
            .map { it.topic }
            .distinct()
    }
}