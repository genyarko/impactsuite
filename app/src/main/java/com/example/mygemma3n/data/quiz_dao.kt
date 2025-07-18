package com.example.mygemma3n.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import com.example.mygemma3n.feature.quiz.QuizEntity

@Dao
interface QuizDao {

    @Insert
    suspend fun insertQuiz(entity: QuizEntity)

    @Update
    suspend fun updateQuiz(entity: QuizEntity)

    @Query("SELECT * FROM quizzes ORDER BY createdAt DESC")
    fun getAll(): Flow<List<QuizEntity>>

    @Query(
        """
        SELECT * FROM quizzes
        WHERE subject = :subject AND topic = :topic
        ORDER BY createdAt DESC
        """
    )
    suspend fun getBySubjectAndTopic(
        subject: String,
        topic: String
    ): List<QuizEntity>

    @Query(
        """
        UPDATE quizzes 
        SET completedAt = :completedAt, score = :score 
        WHERE id = :quizId
        """
    )
    suspend fun markCompleted(
        quizId: String,
        completedAt: Long,
        score: Float
    )

    @Query("SELECT * FROM quizzes WHERE id = :quizId")
    suspend fun getQuizById(quizId: String): QuizEntity?

    @Query(
        """
        SELECT COUNT(*) FROM quizzes 
        WHERE subject = :subject 
        AND completedAt IS NOT NULL
        """
    )
    suspend fun getCompletedQuizCount(subject: String): Int

    @Query(
        """
        SELECT AVG(score) FROM quizzes 
        WHERE subject = :subject 
        AND completedAt IS NOT NULL
        """
    )
    suspend fun getAverageScore(subject: String): Float?
}