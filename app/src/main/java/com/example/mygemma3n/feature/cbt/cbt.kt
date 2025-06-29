package com.example.mygemma3n.feature.cbt


import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

// Emotion detection
enum class Emotion {
    HAPPY, SAD, ANGRY, ANXIOUS, NEUTRAL, FEARFUL, SURPRISED, DISGUSTED
}

@Singleton
class EmotionDetector @Inject constructor() {
    suspend fun detectFromAudio(audioData: FloatArray): Emotion {
        // Simplified emotion detection from audio features
        // In production, this would use audio analysis
        val energy = audioData.map { it * it }.average()
        val variance = audioData.map { (it - energy).let { d -> d * d } }.average()

        return when {
            energy > 0.7 && variance > 0.5 -> Emotion.ANGRY
            energy > 0.5 && variance < 0.3 -> Emotion.HAPPY
            energy < 0.3 && variance < 0.2 -> Emotion.SAD
            energy < 0.4 && variance > 0.6 -> Emotion.ANXIOUS
            else -> Emotion.NEUTRAL
        }
    }
}

// CBT Techniques
data class CBTTechnique(
    val id: String,
    val name: String,
    val description: String,
    val category: TechniqueCategory,
    val steps: List<String>,
    val duration: Int, // minutes
    val effectiveness: Map<Emotion, Float> // effectiveness score per emotion
)

enum class TechniqueCategory {
    COGNITIVE_RESTRUCTURING,
    BEHAVIORAL_ACTIVATION,
    MINDFULNESS,
    RELAXATION,
    PROBLEM_SOLVING,
    EXPOSURE_THERAPY
}

@Singleton
class CBTTechniques @Inject constructor() {
    private val techniques = listOf(
        CBTTechnique(
            id = "thought_challenging",
            name = "Thought Challenging",
            description = "Identify and challenge negative thought patterns",
            category = TechniqueCategory.COGNITIVE_RESTRUCTURING,
            steps = listOf(
                "Identify the negative thought",
                "Examine evidence for and against",
                "Consider alternative perspectives",
                "Create a balanced thought"
            ),
            duration = 15,
            effectiveness = mapOf(
                Emotion.ANXIOUS to 0.9f,
                Emotion.SAD to 0.8f,
                Emotion.ANGRY to 0.7f
            )
        ),
        CBTTechnique(
            id = "5_4_3_2_1",
            name = "5-4-3-2-1 Grounding",
            description = "Grounding technique using senses",
            category = TechniqueCategory.MINDFULNESS,
            steps = listOf(
                "Name 5 things you can see",
                "Name 4 things you can touch",
                "Name 3 things you can hear",
                "Name 2 things you can smell",
                "Name 1 thing you can taste"
            ),
            duration = 5,
            effectiveness = mapOf(
                Emotion.ANXIOUS to 0.95f,
                Emotion.FEARFUL to 0.9f
            )
        ),
        CBTTechnique(
            id = "progressive_relaxation",
            name = "Progressive Muscle Relaxation",
            description = "Systematically tense and relax muscle groups",
            category = TechniqueCategory.RELAXATION,
            steps = listOf(
                "Find a comfortable position",
                "Tense feet muscles for 5 seconds",
                "Release and notice the relaxation",
                "Move up through each muscle group"
            ),
            duration = 20,
            effectiveness = mapOf(
                Emotion.ANXIOUS to 0.85f,
                Emotion.ANGRY to 0.8f
            )
        )
    )

    fun getRecommendedTechnique(emotion: Emotion): CBTTechnique {
        return techniques
            .filter { it.effectiveness.containsKey(emotion) }
            .maxByOrNull { it.effectiveness[emotion] ?: 0f }
            ?: techniques.first()
    }

    fun getAllTechniques(): List<CBTTechnique> = techniques
}

// Messages
sealed class Message {
    abstract val timestamp: Long
    abstract val content: String

    data class User(
        override val content: String,
        override val timestamp: Long = System.currentTimeMillis(),
        val audioData: FloatArray? = null
    ) : Message()

    data class AI(
        override val content: String,
        override val timestamp: Long = System.currentTimeMillis(),
        val technique: CBTTechnique? = null
    ) : Message()
}

// Thought Record for CBT
data class ThoughtRecord(
    val id: String,
    val timestamp: Long,
    val situation: String,
    val automaticThought: String,
    val emotion: Emotion,
    val emotionIntensity: Float, // 0-1
    val evidenceFor: List<String>,
    val evidenceAgainst: List<String>,
    val balancedThought: String,
    val newEmotionIntensity: Float
)

// Session data
@Entity(tableName = "cbt_sessions")
data class CBTSession(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val timestamp: Long,
    val emotion: Emotion,
    val technique: CBTTechnique? = null,
    val transcript: String,
    val duration: Long = 0,
    val completed: Boolean = false,
    val effectiveness: Float? = null
)

// Room database for sessions
@Dao
interface CBTSessionDao {
    @Insert
    suspend fun insert(session: CBTSession)

    @Query("SELECT * FROM cbt_sessions ORDER BY timestamp DESC")
    fun getAllSessions(): Flow<List<CBTSession>>

    @Query("SELECT * FROM cbt_sessions WHERE emotion = :emotion")
    suspend fun getSessionsByEmotion(emotion: Emotion): List<CBTSession>

    @Query("UPDATE cbt_sessions SET completed = :completed, effectiveness = :effectiveness WHERE id = :sessionId")
    suspend fun updateSession(sessionId: String, completed: Boolean, effectiveness: Float?)
}

@Database(
    entities = [CBTSession::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(CBTConverters::class)
abstract class CBTDatabase : RoomDatabase() {
    abstract fun sessionDao(): CBTSessionDao
}

class CBTConverters {
    @TypeConverter
    fun fromEmotion(emotion: Emotion): String = emotion.name

    @TypeConverter
    fun toEmotion(name: String): Emotion = Emotion.valueOf(name)

    @TypeConverter
    fun fromTechnique(technique: CBTTechnique?): String? = technique?.id

    @TypeConverter
    fun toTechnique(id: String?): CBTTechnique? {
        return id?.let { techId ->
            CBTTechniques().getAllTechniques().find { it.id == techId }
        }
    }
}

@Singleton
class SessionRepository @Inject constructor(
    private val database: CBTDatabase
) {
    suspend fun saveSession(session: CBTSession) {
        database.sessionDao().insert(session)
    }

    fun getAllSessions(): Flow<List<CBTSession>> {
        return database.sessionDao().getAllSessions()
    }

    suspend fun updateSessionEffectiveness(sessionId: String, effectiveness: Float) {
        database.sessionDao().updateSession(sessionId, true, effectiveness)
    }
}

// Helper function for parsing CBT technique from response
fun parseCBTTechnique(response: String): CBTTechnique? {
    val techniques = CBTTechniques()

    // Look for technique mentions in the response
    return techniques.getAllTechniques().firstOrNull { technique ->
        response.contains(technique.name, ignoreCase = true) ||
                response.contains(technique.id.replace("_", " "), ignoreCase = true)
    }
}