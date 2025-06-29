package com.example.mygemma3n.feature.quiz

import androidx.room.*
import com.example.mygemma3n.data.local.entities.SubjectAccuracy
import com.example.mygemma3n.shared_utilities.OfflineRAG
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

// Subject enum (matching OfflineRAG.Subject)
typealias Subject = OfflineRAG.Subject

// Difficulty levels
enum class Difficulty {
    EASY,
    MEDIUM,
    HARD,
    ADAPTIVE
}

// Question types
enum class QuestionType {
    MULTIPLE_CHOICE,
    TRUE_FALSE,
    FILL_IN_BLANK,
    SHORT_ANSWER,
    MATCHING
}

// Quiz data models
data class Quiz(
    val id: String,
    val subject: Subject,
    val topic: String,
    val questions: List<Question>,
    val difficulty: Difficulty,
    val createdAt: Long,
    val completedAt: Long? = null,
    val score: Float? = null
)

data class Question(
    val id: String = java.util.UUID.randomUUID().toString(),
    val questionText: String,
    val questionType: QuestionType,
    val options: List<String> = emptyList(),
    val correctAnswer: String,
    val explanation: String,
    val hint: String? = null,
    val conceptsCovered: List<String> = emptyList(),
    val difficulty: Difficulty,
    val points: Int = 1,
    val userAnswer: String? = null,
    val isAnswered: Boolean = false,
    val feedback: String? = null,
    val timeSpentSeconds: Int = 0
)

// Educational content
@Entity(tableName = "educational_content")
data class EducationalContent(
    @PrimaryKey val id: String,
    val subject: Subject,
    val gradeLevel: String,
    val topic: String,
    val text: String,
    val source: String,
    val lastUpdated: Date = Date()
)

// User progress tracking
@Entity(tableName = "user_progress")
data class UserProgress(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subject: Subject,
    val difficulty: Difficulty,
    val correct: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val responseTimeMs: Long
)
// Aggregated accuracy by subject
data class SubjectAccuracy(
    val subject: Subject,
    val accuracy: Float
)

// Room entities for quiz storage
@Entity(tableName = "quizzes")
data class QuizEntity(
    @PrimaryKey val id: String,
    val subject: Subject,
    val topic: String,
    val difficulty: Difficulty,
    val createdAt: Long,
    val completedAt: Long? = null,
    val score: Float? = null,
    val questionsJson: String // Store questions as JSON
)

// DAOs
@Dao
interface EducationalContentDao {
    @Query("SELECT * FROM educational_content")
    suspend fun getAllContent(): List<EducationalContent>

    @Query("SELECT * FROM educational_content WHERE subject = :subject")
    suspend fun getContentBySubject(subject: Subject): List<EducationalContent>

    @Query("SELECT * FROM educational_content WHERE subject = :subject AND topic = :topic")
    suspend fun getContentByTopic(subject: Subject, topic: String): List<EducationalContent>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContent(content: EducationalContent)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllContent(content: List<EducationalContent>)
}

@Dao
interface QuizDao {
    @Insert
    suspend fun insertQuiz(quiz: QuizEntity)

    @Query("SELECT * FROM quizzes ORDER BY createdAt DESC")
    fun getAllQuizzes(): Flow<List<QuizEntity>>

    @Query("SELECT * FROM quizzes WHERE subject = :subject ORDER BY createdAt DESC")
    fun getQuizzesBySubject(subject: Subject): Flow<List<QuizEntity>>

    @Query("UPDATE quizzes SET completedAt = :completedAt, score = :score WHERE id = :quizId")
    suspend fun updateQuizCompletion(quizId: String, completedAt: Long, score: Float)
}

@Dao
interface UserProgressDao {
    @Insert
    suspend fun insertProgress(progress: UserProgress)

    @Query("SELECT * FROM user_progress WHERE subject = :subject ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentProgress(subject: Subject, limit: Int = 20): List<UserProgress>

    @Query("""
  SELECT subject, 
         SUM(CASE WHEN correct THEN 1 ELSE 0 END)*1.0 / COUNT(*) as accuracy
  FROM user_progress
  GROUP BY subject
""")
    suspend fun getAccuracyBySubject(): List<SubjectAccuracy>


    @Query("""
        SELECT AVG(CASE WHEN correct THEN 1 ELSE 0 END) as accuracy
        FROM user_progress
        WHERE subject = :subject AND timestamp > :since
    """)
    suspend fun getRecentAccuracy(subject: Subject, since: Long): Float
}

// Type converters
class QuizConverters {
    @TypeConverter
    fun fromSubject(subject: Subject): String = subject.name

    @TypeConverter
    fun toSubject(name: String): Subject = Subject.valueOf(name)

    @TypeConverter
    fun fromDifficulty(difficulty: Difficulty): String = difficulty.name

    @TypeConverter
    fun toDifficulty(name: String): Difficulty = Difficulty.valueOf(name)

    @TypeConverter
    fun fromDate(date: Date?): Long? = date?.time

    @TypeConverter
    fun toDate(timestamp: Long?): Date? = timestamp?.let { Date(it) }
}

// Database
@Database(
    entities = [
        EducationalContent::class,
        QuizEntity::class,
        UserProgress::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(QuizConverters::class)
abstract class QuizDatabase : RoomDatabase() {
    abstract fun contentDao(): EducationalContentDao
    abstract fun quizDao(): QuizDao
    abstract fun progressDao(): UserProgressDao
}

// Repositories
@Singleton
class EducationalContentRepository @Inject constructor(
    private val database: QuizDatabase
) {
    suspend fun getAllContent(): List<EducationalContent> {
        return database.contentDao().getAllContent()
    }

    suspend fun prepopulateContent() {
        val content = listOf(
            EducationalContent(
                id = "math_algebra_1",
                subject = Subject.MATHEMATICS,
                gradeLevel = "9-10",
                topic = "Linear Equations",
                text = "A linear equation is an algebraic equation in which each term is either a constant or the product of a constant and a single variable...",
                source = "OpenStax Algebra"
            ),
            EducationalContent(
                id = "science_biology_1",
                subject = Subject.SCIENCE,
                gradeLevel = "9-10",
                topic = "Cell Structure",
                text = "All living organisms are composed of cells. The cell is the basic unit of life...",
                source = "OpenStax Biology"
            )
            // Add more content...
        )

        database.contentDao().insertAllContent(content)
    }
}

@Singleton
class QuizRepository @Inject constructor(
    private val database: QuizDatabase,
    private val gson: com.google.gson.Gson
) {
    /** Expose the DAO so other layers (e.g. ViewModel) can read accuracy data. */
    fun progressDao(): UserProgressDao = database.progressDao()
    suspend fun saveQuiz(quiz: Quiz) {
        val entity = QuizEntity(
            id = quiz.id,
            subject = quiz.subject,
            topic = quiz.topic,
            difficulty = quiz.difficulty,
            createdAt = quiz.createdAt,
            completedAt = quiz.completedAt,
            score = quiz.score,
            questionsJson = gson.toJson(quiz.questions)
        )
        database.quizDao().insertQuiz(entity)
    }

    fun getAllQuizzes(): Flow<List<Quiz>> {
        return database.quizDao().getAllQuizzes().map { entities ->
            entities.map { entity ->
                Quiz(
                    id = entity.id,
                    subject = entity.subject,
                    topic = entity.topic,
                    questions = gson.fromJson(entity.questionsJson, Array<Question>::class.java).toList(),
                    difficulty = entity.difficulty,
                    createdAt = entity.createdAt,
                    completedAt = entity.completedAt,
                    score = entity.score
                )
            }
        }
    }

    suspend fun recordProgress(subject: Subject, difficulty: Difficulty, correct: Boolean, responseTimeMs: Long) {
        database.progressDao().insertProgress(
            UserProgress(
                subject = subject,
                difficulty = difficulty,
                correct = correct,
                responseTimeMs = responseTimeMs
            )
        )
    }
}

// Helper functions
fun generateQuizId(): String = "quiz_${System.currentTimeMillis()}_${java.util.UUID.randomUUID().toString().take(8)}"

suspend fun getUserProficiencyLevel(
    subject: Subject,
    progressDao: UserProgressDao
): Float {
    val thirtyDaysAgo = System.currentTimeMillis() - (30 * 24 * 60 * 60 * 1000L)
    return progressDao.getRecentAccuracy(subject, thirtyDaysAgo)
}

fun calculateAdaptiveDifficulty(
    userLevel: Float,
    previousQuestions: List<Question>
): Difficulty {
    val recentCorrect = previousQuestions.takeLast(3).count { it.userAnswer == it.correctAnswer }

    return when {
        userLevel < 0.5f || recentCorrect == 0 -> Difficulty.EASY
        userLevel > 0.8f && recentCorrect >= 2 -> Difficulty.HARD
        else -> Difficulty.MEDIUM
    }
}

fun selectQuestionType(questionNumber: Int): QuestionType {
    // Vary question types for engagement
    return when (questionNumber % 5) {
        0 -> QuestionType.TRUE_FALSE
        1 -> QuestionType.FILL_IN_BLANK
        4 -> QuestionType.SHORT_ANSWER
        else -> QuestionType.MULTIPLE_CHOICE
    }
}

fun parseQuestionFromJson(
    jsonResponse: String,
    expectedType: QuestionType,
    difficulty: Difficulty
): Question {
    return try {
        val gson = com.google.gson.Gson()
        val jsonStart = jsonResponse.indexOf("{")
        val jsonEnd = jsonResponse.lastIndexOf("}") + 1

        if (jsonStart != -1 && jsonEnd > jsonStart) {
            val json = jsonResponse.substring(jsonStart, jsonEnd)
            val parsed = gson.fromJson(json, Map::class.java)

            Question(
                questionText = parsed["question"] as? String ?: "Question parsing failed",
                questionType = expectedType,
                options = (parsed["options"] as? List<String>) ?: emptyList(),
                correctAnswer = parsed["correctAnswer"] as? String ?: "A",
                explanation = parsed["explanation"] as? String ?: "No explanation available",
                hint = parsed["hint"] as? String,
                conceptsCovered = (parsed["conceptsCovered"] as? List<String>) ?: emptyList(),
                difficulty = difficulty
            )
        } else {
            // Fallback question
            Question(
                questionText = "Failed to generate question",
                questionType = expectedType,
                options = listOf("A", "B", "C", "D"),
                correctAnswer = "A",
                explanation = "Question generation error",
                difficulty = difficulty
            )
        }
    } catch (e: Exception) {
        // Fallback question on error
        Question(
            questionText = "Failed to generate question",
            questionType = expectedType,
            options = listOf("A", "B", "C", "D"),
            correctAnswer = "A",
            explanation = "Question generation error",
            difficulty = difficulty
        )
    }
}