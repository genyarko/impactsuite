package com.example.mygemma3n.feature.quiz

import androidx.room.*
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import com.example.mygemma3n.data.QuizDao   // add this


/* ─────────────────────── Core enums ─────────────────────── */

enum class Subject { MATHEMATICS, SCIENCE, HISTORY, LANGUAGE_ARTS, GEOGRAPHY, GENERAL }

enum class Difficulty { EASY, MEDIUM, HARD, ADAPTIVE }

enum class QuestionType { MULTIPLE_CHOICE, TRUE_FALSE, FILL_IN_BLANK, SHORT_ANSWER, MATCHING }

enum class QuizMode { NORMAL, REVIEW, ADAPTIVE }

/* ─────────────────────── Domain models ───────────────────── */

data class Quiz(
    val id: String,
    val subject: Subject,
    val topic: String,
    val questions: List<Question>,
    val difficulty: Difficulty,
    val mode: QuizMode = QuizMode.NORMAL,
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
    val timeSpentSeconds: Int = 0,
    val lastSeenAt: Long? = null
)

data class QuizResult(
    val quizId: String,
    val questionId: String,
    val wasCorrect: Boolean,
    val attemptedAt: Long,
    val difficulty: Difficulty,
    val conceptsTested: List<String>
)

data class LearnerProfile(
    val strengthsBySubject: Map<Subject, Float>,
    val weaknessesByConcept: Map<String, List<String>>, // concept -> wrong answers
    val masteredConcepts: Set<String>,
    val totalQuestionsAnswered: Int,
    val streakDays: Int
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
    val mode: QuizMode = QuizMode.NORMAL,
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
    val responseTimeMs: Long,
    val conceptsTested: String // JSON array of concepts
)

@Entity(tableName = "question_history")
data class QuestionHistory(
    @PrimaryKey val questionId: String,
    val questionText: String,
    val lastAttemptedAt: Long,
    val timesAttempted: Int,
    val timesCorrect: Int,
    val conceptsCovered: String, // JSON array
    val subject: Subject,
    val difficulty: Difficulty
)

@Entity(tableName = "wrong_answers")
data class WrongAnswer(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val questionId: String,
    val questionText: String,
    val userAnswer: String,
    val correctAnswer: String,
    val conceptsTested: String, // JSON array
    val attemptedAt: Long
)

data class SubjectAccuracy(val subject: Subject, val accuracy: Float)

/* ─────────────────────── Converters ─────────────────────── */

class QuizConverters {
    @TypeConverter fun fromSubject(s: Subject): String = s.name
    @TypeConverter fun toSubject(v: String): Subject = Subject.valueOf(v)

    @TypeConverter fun fromDifficulty(d: Difficulty): String = d.name
    @TypeConverter fun toDifficulty(v: String): Difficulty = Difficulty.valueOf(v)

    @TypeConverter fun fromQuizMode(m: QuizMode): String = m.name
    @TypeConverter fun toQuizMode(v: String): QuizMode = QuizMode.valueOf(v)

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

    @Query(
        """
        SELECT AVG(CASE WHEN correct THEN 1 ELSE 0 END)
        FROM user_progress
        WHERE subject = :subject AND difficulty = :difficulty 
        AND timestamp > :since
    """
    )
    suspend fun recentAccuracyByDifficulty(
        subject: Subject,
        difficulty: Difficulty,
        since: Long
    ): Float?

    @Query("SELECT * FROM user_progress WHERE timestamp > :since")
    suspend fun getRecentProgress(since: Long): List<UserProgress>
}

@Dao
interface QuestionHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(history: QuestionHistory)

    @Query(
        """
        SELECT * FROM question_history
        WHERE subject = :subject 
        AND lastAttemptedAt < :beforeTimestamp
        ORDER BY lastAttemptedAt ASC
        LIMIT :limit
    """
    )
    suspend fun getQuestionsForReview(
        subject: Subject,
        beforeTimestamp: Long,
        limit: Int
    ): List<QuestionHistory>

    @Query("SELECT * FROM question_history WHERE questionId = :id")
    suspend fun getById(id: String): QuestionHistory?

    @Query(
        """
        SELECT DISTINCT conceptsCovered FROM question_history
        WHERE subject = :subject AND timesCorrect > timesAttempted * 0.8
    """
    )
    suspend fun getMasteredConcepts(subject: Subject): List<String>
}

@Dao
interface WrongAnswerDao {
    @Insert suspend fun insert(wrongAnswer: WrongAnswer)

    @Query(
        """
        SELECT * FROM wrong_answers
        WHERE conceptsTested LIKE :concept
        ORDER BY attemptedAt DESC
        LIMIT :limit
    """
    )
    suspend fun getRecentWrongAnswersByConcept(concept: String, limit: Int = 5): List<WrongAnswer>

    @Query(
        """
        SELECT * FROM wrong_answers
        WHERE questionId = :questionId
        ORDER BY attemptedAt DESC
    """
    )
    suspend fun getWrongAnswersForQuestion(questionId: String): List<WrongAnswer>
}

/* ─────────────────────── Database ─────────────────────── */

@Database(
    entities = [
        EducationalContent::class,
        QuizEntity::class,
        UserProgress::class,
        QuestionHistory::class,
        WrongAnswer::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(QuizConverters::class)
abstract class QuizDatabase : RoomDatabase() {
    abstract fun contentDao(): EducationalContentDao
    abstract fun quizDao(): QuizDao
    abstract fun progressDao(): UserProgressDao
    abstract fun questionHistoryDao(): QuestionHistoryDao
    abstract fun wrongAnswerDao(): WrongAnswerDao
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
                gradeLevel = "6-12",
                topic = "Linear Equations",
                text = "A linear equation represents a straight line when graphed...",
                source = "OpenStax Algebra"
            ),
            EducationalContent(
                id = "science_biology_1",
                subject = Subject.SCIENCE,
                gradeLevel = "6-12",
                topic = "Cell Structure",
                text = "All living organisms are composed of cells...",
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
    fun questionHistoryDao(): QuestionHistoryDao = db.questionHistoryDao()
    fun wrongAnswerDao(): WrongAnswerDao = db.wrongAnswerDao()

    suspend fun saveQuiz(quiz: Quiz) {
        val entity = quiz.toEntity(gson)
        if (quiz.completedAt != null) {
            // If quiz already exists, update it
            val existing = db.quizDao().getQuizById(quiz.id)
            if (existing != null) {
                db.quizDao().updateQuiz(entity)
            } else {
                db.quizDao().insertQuiz(entity)
            }
        } else {
            db.quizDao().insertQuiz(entity)
        }
    }

    suspend fun markQuizCompleted(quizId: String, score: Float) {
        db.quizDao().markCompleted(
            quizId = quizId,
            completedAt = System.currentTimeMillis(),
            score = score
        )
    }

    fun getAllQuizzes(): Flow<List<Quiz>> =
        db.quizDao().getAll().map { list ->
            list.map { it.toDomain(gson) }
        }

    suspend fun getQuizzesFor(subject: Subject, topic: String): List<Quiz> =
        db.quizDao().getBySubjectAndTopic(subject.name, topic)
            .map { it.toDomain(gson) }

    suspend fun getQuizById(quizId: String): Quiz? =
        db.quizDao().getQuizById(quizId)?.toDomain(gson)

    // Progress tracking methods
    suspend fun recordProgress(
        subject: Subject,
        difficulty: Difficulty,
        correct: Boolean,
        durationMs: Long,
        conceptsTested: List<String> = emptyList()
    ) {
        db.progressDao().insert(
            UserProgress(
                subject = subject,
                difficulty = difficulty,
                correct = correct,
                responseTimeMs = durationMs,
                conceptsTested = gson.toJson(conceptsTested)
            )
        )
    }

    suspend fun recordQuestionAttempt(question: Question, wasCorrect: Boolean) {
        val existing = db.questionHistoryDao().getById(question.id)
        val history = if (existing != null) {
            existing.copy(
                lastAttemptedAt = System.currentTimeMillis(),
                timesAttempted = existing.timesAttempted + 1,
                timesCorrect = existing.timesCorrect + if (wasCorrect) 1 else 0
            )
        } else {
            QuestionHistory(
                questionId = question.id,
                questionText = question.questionText,
                lastAttemptedAt = System.currentTimeMillis(),
                timesAttempted = 1,
                timesCorrect = if (wasCorrect) 1 else 0,
                conceptsCovered = gson.toJson(question.conceptsCovered),
                subject = Subject.GENERAL, // You'd need to pass this
                difficulty = question.difficulty
            )
        }
        db.questionHistoryDao().upsert(history)
    }

    suspend fun recordWrongAnswer(
        question: Question,
        userAnswer: String
    ) = db.wrongAnswerDao().insert(
        WrongAnswer(
            questionId = question.id,
            questionText = question.questionText,
            userAnswer = userAnswer,
            correctAnswer = question.correctAnswer,
            conceptsTested = gson.toJson(question.conceptsCovered),
            attemptedAt = System.currentTimeMillis()
        )
    )

    suspend fun getAdaptiveDifficulty(
        subject: Subject,
        currentDifficulty: Difficulty
    ): Difficulty {
        val oneWeekAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000)
        val accuracy = db.progressDao().recentAccuracyByDifficulty(
            subject, currentDifficulty, oneWeekAgo
        ) ?: 0.5f

        return when {
            accuracy > 0.8f && currentDifficulty != Difficulty.HARD -> Difficulty.HARD
            accuracy < 0.4f && currentDifficulty != Difficulty.EASY -> Difficulty.EASY
            else -> currentDifficulty
        }
    }

    suspend fun getQuestionsForSpacedReview(
        subject: Subject,
        daysOld: Int = 7,
        limit: Int = 10
    ): List<QuestionHistory> {
        val threshold = System.currentTimeMillis() - (daysOld * 24 * 60 * 60 * 1000L)
        return db.questionHistoryDao().getQuestionsForReview(subject, threshold, limit)
    }

    suspend fun getLearnerProfile(subject: Subject): LearnerProfile {
        val oneMonthAgo = System.currentTimeMillis() - (30 * 24 * 60 * 60 * 1000L)
        val recentProgress = db.progressDao().getRecentProgress(oneMonthAgo)

        val accuracy = recentProgress
            .filter { it.subject == subject }
            .let { list ->
                if (list.isEmpty()) 0f
                else list.count { it.correct }.toFloat() / list.size
            }

        val masteredConcepts = db.questionHistoryDao()
            .getMasteredConcepts(subject)
            .flatMap { gson.fromJson(it, Array<String>::class.java).toList() }
            .toSet()

        // Group wrong answers by concept
        val weaknesses = mutableMapOf<String, MutableList<String>>()
        // This would need more implementation to get wrong answers by concept

        return LearnerProfile(
            strengthsBySubject = mapOf(subject to accuracy),
            weaknessesByConcept = weaknesses,
            masteredConcepts = masteredConcepts,
            totalQuestionsAnswered = recentProgress.size,
            streakDays = 0 // Would need to calculate from daily activity
        )
    }

    /* -- mapping helpers ------------------------------------------------- */

    private fun Quiz.toEntity(gson: Gson) = QuizEntity(
        id            = id,
        subject       = subject,
        topic         = topic,
        difficulty    = difficulty,
        mode          = mode,
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
        mode        = mode,
        createdAt   = createdAt,
        completedAt = completedAt,
        score       = score
    )
}