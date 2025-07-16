package com.example.mygemma3n.data


import androidx.room.Dao
import androidx.room.Query
import com.example.mygemma3n.feature.quiz.Quiz
import com.example.mygemma3n.feature.quiz.QuizEntity
import com.example.mygemma3n.feature.quiz.Subject

@Dao
interface QuizDao {

    /* already-existing INSERT / UPDATE / … */

    /** Return every saved quiz that matches the same subject *and* topic. */
    @Query(
        """
        SELECT *
        FROM quizzes                           -- ← table that stores QuizEntity
        WHERE subject = :subject
          AND topic   = :topic
        """
    )
    suspend fun getBySubjectAndTopic(
        subject: String,
        topic:   String
    ): List<QuizEntity>
}