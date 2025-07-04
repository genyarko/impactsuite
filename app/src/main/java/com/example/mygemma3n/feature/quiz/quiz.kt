package com.example.mygemma3n.feature.quiz

import androidx.room.*
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/* ─────────────────────── Core enums ─────────────────────── */

enum class Subject { MATHEMATICS, SCIENCE, HISTORY, LANGUAGE_ARTS, GEOGRAPHY, GENERAL }

enum class Difficulty { EASY, MEDIUM, HARD, ADAPTIVE }

enum class QuestionType { MULTIPLE_CHOICE, TRUE_FALSE, FILL_IN_BLANK, SHORT_ANSWER, MATCHING }

/* ─────────────────────── Domain models ───────────────────── */

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

/* ─────────────────────── Room entities ───────────────────── */

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

@Entity(tableName = "quizzes")
data class QuizEntity(
    @PrimaryKey val id: String,
    val subject: Subject,
    val topic: String,
    val difficulty: Difficulty,
    val createdAt: Long,
    val completedAt: Long? = null,
    val score: Float? = null,
    /** Questions persisted as JSON for simplicity */
    val questionsJson: String
)

@Entity(tableName = "user_progress")
data class UserProgress(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subject: Subject,
    val difficulty: Difficulty,
    val correct: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val responseTimeMs: Long
)

data class SubjectAccuracy(val subject: Subject, val accuracy: Float)

/* ─────────────────────── Converters ─────────────────────── */

class QuizConverters {
    @TypeConverter fun fromSubject(s: Subject): String = s.name
    @TypeConverter fun toSubject(v: String): Subject = Subject.valueOf(v)

    @TypeConverter fun fromDifficulty(d: Difficulty): String = d.name
    @TypeConverter fun toDifficulty(v: String): Difficulty = Difficulty.valueOf(v)

    @TypeConverter fun fromDate(d: Date?): Long? = d?.time
    @TypeConverter fun toDate(v: Long?): Date? = v?.let(::Date)
}

/* ─────────────────────── DAOs ─────────────────────── */

@Dao
interface EducationalContentDao {
    @Query("SELECT * FROM educational_content") suspend fun getAll(): List<EducationalContent>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(list: List<EducationalContent>)
}

@Dao
interface QuizDao {
    @Insert suspend fun insertQuiz(entity: QuizEntity)

    @Query("SELECT * FROM quizzes ORDER BY createdAt DESC")
    fun getAll(): Flow<List<QuizEntity>>

    @Query(
        """
        SELECT * FROM quizzes
        WHERE subject = :subject AND topic = :topic
        ORDER BY createdAt DESC
    """
    )
    suspend fun getBySubjectAndTopic(subject: String, topic: String): List<QuizEntity>

    @Query(
        "UPDATE quizzes SET completedAt = :done, score = :score WHERE id = :id"
    )
    suspend fun markCompleted(id: String, done: Long, score: Float)
}

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
    suspend fun recentAccuracy(subject: Subject, since: Long): Float
}

/* ─────────────────────── Database ─────────────────────── */

@Database(
    entities = [EducationalContent::class, QuizEntity::class, UserProgress::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(QuizConverters::class)
abstract class QuizDatabase : RoomDatabase() {
    abstract fun contentDao(): EducationalContentDao
    abstract fun quizDao(): QuizDao
    abstract fun progressDao(): UserProgressDao
}

/* ─────────────────────── Repository layers ─────────────────────── */

@Singleton
class EducationalContentRepository @Inject constructor(
    private val db: QuizDatabase
) {
    suspend fun prepopulateContent() {
        val sample = listOf(
            EducationalContent(
                id = "math_algebra_1",
                subject = Subject.MATHEMATICS,
                gradeLevel = "9-10",
                topic = "Linear Equations",
                text = "A linear equation …",
                source = "OpenStax Algebra"
            ),
            EducationalContent(
                id = "science_biology_1",
                subject = Subject.SCIENCE,
                gradeLevel = "9-10",
                topic = "Cell Structure",
                text = "All living organisms …",
                source = "OpenStax Biology"
            )
        )
        db.contentDao().insertAll(sample)
    }

    suspend fun getAllContent(): List<EducationalContent> = db.contentDao().getAll()
}

@Singleton
class QuizRepository @Inject constructor(
    private val db: QuizDatabase,
    private val gson: Gson
) {
    private val quizDao get() = db.quizDao()

    /* -- public helpers -------------------------------------------------- */

    fun progressDao(): UserProgressDao = db.progressDao()

    suspend fun saveQuiz(quiz: Quiz) =
        quizDao.insertQuiz(quiz.toEntity(gson))

    fun getAllQuizzes(): Flow<List<Quiz>> =
        quizDao.getAll().map { list -> list.map { it.toDomain(gson) } }

    suspend fun getQuizzesFor(subject: Subject, topic: String): List<Quiz> =
        quizDao.getBySubjectAndTopic(subject.name, topic)
            .map { it.toDomain(gson) }

    suspend fun recordProgress(
        subject: Subject,
        difficulty: Difficulty,
        correct: Boolean,
        durationMs: Long
    ) = db.progressDao().insert(
        UserProgress(
            subject = subject,
            difficulty = difficulty,
            correct = correct,
            responseTimeMs = durationMs
        )
    )

    /* -- mapping helpers ------------------------------------------------- */

    private fun Quiz.toEntity(gson: Gson) = QuizEntity(
        id            = id,
        subject       = subject,
        topic         = topic,
        difficulty    = difficulty,
        createdAt     = createdAt,
        completedAt   = completedAt,
        score         = score,
        questionsJson = gson.toJson(questions)
    )

    private fun QuizEntity.toDomain(gson: Gson): Quiz = Quiz(
        id          = id,
        subject     = subject,
        topic       = topic,
        questions   = gson.fromJson(questionsJson, Array<Question>::class.java).toList(),
        difficulty  = difficulty,
        createdAt   = createdAt,
        completedAt = completedAt,
        score       = score
    )
}
