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
    val techniques = listOf(
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

    fun getTechniqueById(id: String): CBTTechnique? = techniques.find { it.id == id }
}

// Messages
sealed class Message {
    abstract val timestamp: Long
    abstract val content: String

    data class User(
        override val content: String,
        override val timestamp: Long = System.currentTimeMillis(),
        val audioData: FloatArray? = null
    ) : Message() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as User

            if (content != other.content) return false
            if (timestamp != other.timestamp) return false
            if (audioData != null) {
                if (other.audioData == null) return false
                if (!audioData.contentEquals(other.audioData)) return false
            } else if (other.audioData != null) return false

            return true
        }

        override fun hashCode(): Int {
            var result = content.hashCode()
            result = 31 * result + timestamp.hashCode()
            result = 31 * result + (audioData?.contentHashCode() ?: 0)
            return result
        }
    }

    data class AI(
        override val content: String,
        override val timestamp: Long = System.currentTimeMillis(),
        val techniqueId: String? = null  // Changed from CBTTechnique? to String?
    ) : Message() {
        // Helper property to get technique name without needing the full object
        fun getTechniqueName(cbtTechniques: CBTTechniques): String? {
            return techniqueId?.let { cbtTechniques.getTechniqueById(it)?.name }
        }
    }
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

// Session data - Modified to store techniqueId instead of CBTTechnique object
@Entity(tableName = "cbt_sessions")
data class CBTSession(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val timestamp: Long,
    val emotion: Emotion,
    val techniqueId: String? = null,  // Changed from CBTTechnique? to String?
    val transcript: String,
    val duration: Long = 0,
    val completed: Boolean = false,
    val effectiveness: Float? = null
) {
    // Transient property to get the technique object when needed
    @Ignore
    var technique: CBTTechnique? = null
}

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
}

@Singleton
class SessionRepository @Inject constructor(
    private val database: CBTDatabase,
    private val cbtTechniques: CBTTechniques  // Inject this to resolve techniques
) {
    suspend fun saveSession(session: CBTSession) {
        database.sessionDao().insert(session)
    }

    fun getAllSessions(): Flow<List<CBTSession>> {
        return database.sessionDao().getAllSessions()
    }

    suspend fun getAllSessionsWithTechniques(): List<CBTSession> {
        val sessions = database.sessionDao().getAllSessions()
        // This would need to be collected from the Flow
        // For now, returning empty list - you'd implement this properly
        return emptyList()
    }

    suspend fun updateSessionEffectiveness(sessionId: String, effectiveness: Float) {
        database.sessionDao().updateSession(sessionId, true, effectiveness)
    }

    // Helper method to get session with technique resolved
    suspend fun getSessionWithTechnique(sessionId: String): CBTSession? {
        // You'd implement this to fetch the session and resolve its technique
        return null
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