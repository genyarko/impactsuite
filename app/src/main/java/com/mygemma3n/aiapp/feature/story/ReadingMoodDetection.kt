package com.mygemma3n.aiapp.feature.story

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

// Mood categories for story selection
enum class ReadingMood {
    ENERGETIC,      // Action, adventure, excitement
    CALM,           // Peaceful, gentle stories
    CURIOUS,        // Mystery, educational content
    CREATIVE,       // Fantasy, imaginative stories
    SOCIAL,         // Friendship, family stories
    CONTEMPLATIVE,  // Thoughtful, philosophical stories
    PLAYFUL,        // Funny, lighthearted stories
    COMFORT,        // Familiar, comforting stories
    CHALLENGE       // Complex, thought-provoking stories
}

// Time of day influences
enum class TimeOfDay {
    EARLY_MORNING,   // 5:00 - 8:00
    MORNING,         // 8:00 - 12:00
    AFTERNOON,       // 12:00 - 17:00
    EVENING,         // 17:00 - 21:00
    NIGHT            // 21:00 - 5:00
}

// User activity context
enum class ActivityContext {
    JUST_WOKE_UP,
    BREAKFAST_TIME,
    SCHOOL_BREAK,
    AFTER_SCHOOL,
    HOMEWORK_BREAK,
    BEFORE_DINNER,
    BEDTIME,
    WEEKEND_MORNING,
    WEEKEND_AFTERNOON,
    HOLIDAY,
    UNKNOWN
}

@Entity(tableName = "reading_mood_sessions")
data class ReadingMoodSession(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val detectedMood: ReadingMood,
    val timeOfDay: TimeOfDay,
    val activityContext: ActivityContext,
    val userSelectedMood: ReadingMood? = null, // If user manually selected mood
    val storyGenreSelected: StoryGenre? = null,
    val storyCompleted: Boolean = false,
    val sessionDurationMinutes: Int = 0,
    val userFeedback: Int? = null // 1-5 rating of mood accuracy
)

// Domain model for mood recommendation
data class MoodRecommendation(
    val primaryMood: ReadingMood,
    val confidence: Float, // 0.0 to 1.0
    val reasoning: String,
    val recommendedGenres: List<StoryGenre>,
    val recommendedTemplates: List<TemplateType>,
    val suggestedLength: StoryLength,
    val timeContext: String
)

@Dao
interface ReadingMoodDao {
    @Query("SELECT * FROM reading_mood_sessions ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentMoodSessions(limit: Int = 50): List<ReadingMoodSession>
    
    @Query("SELECT * FROM reading_mood_sessions WHERE timestamp >= :startTime ORDER BY timestamp DESC")
    suspend fun getMoodSessionsSince(startTime: Long): List<ReadingMoodSession>
    
    @Insert
    suspend fun insertMoodSession(session: ReadingMoodSession)
    
    @Update
    suspend fun updateMoodSession(session: ReadingMoodSession)
    
    @Query("SELECT COUNT(*) FROM reading_mood_sessions WHERE detectedMood = :mood AND timestamp >= :startTime")
    suspend fun getMoodCountSince(mood: ReadingMood, startTime: Long): Int
    
    @Query("DELETE FROM reading_mood_sessions WHERE timestamp < :cutoffTime")
    suspend fun deleteOldSessions(cutoffTime: Long)
}

@Singleton
class ReadingMoodDetectionService @Inject constructor(
    private val moodDao: ReadingMoodDao,
    private val storyRepository: StoryRepository
) {
    
    suspend fun detectCurrentMood(): MoodRecommendation {
        val currentTime = System.currentTimeMillis()
        val timeOfDay = getCurrentTimeOfDay()
        val activityContext = detectActivityContext(timeOfDay)
        val recentHistory = getRecentReadingHistory()
        
        val detectedMood = analyzeMoodFactors(
            timeOfDay = timeOfDay,
            activityContext = activityContext,
            recentHistory = recentHistory,
            dayOfWeek = getCurrentDayOfWeek()
        )
        
        // Save mood session for learning
        val session = ReadingMoodSession(
            detectedMood = detectedMood.primaryMood,
            timeOfDay = timeOfDay,
            activityContext = activityContext
        )
        moodDao.insertMoodSession(session)
        
        return detectedMood
    }
    
    suspend fun getMoodBasedRecommendations(
        targetAudience: StoryTarget,
        userSelectedMood: ReadingMood? = null
    ): MoodRecommendation {
        val mood = userSelectedMood ?: detectCurrentMood().primaryMood
        
        return when (mood) {
            ReadingMood.ENERGETIC -> MoodRecommendation(
                primaryMood = mood,
                confidence = 0.8f,
                reasoning = "Perfect time for exciting adventures and action stories",
                recommendedGenres = listOf(StoryGenre.ADVENTURE, StoryGenre.SCIENCE_FICTION),
                recommendedTemplates = listOf(TemplateType.HEROS_JOURNEY, TemplateType.ADVENTURE),
                suggestedLength = StoryLength.MEDIUM,
                timeContext = getTimeContext()
            )
            
            ReadingMood.CALM -> MoodRecommendation(
                primaryMood = mood,
                confidence = 0.9f,
                reasoning = "A good time for gentle, peaceful stories",
                recommendedGenres = listOf(StoryGenre.FRIENDSHIP, StoryGenre.FAMILY, StoryGenre.FAIRYTALE),
                recommendedTemplates = listOf(TemplateType.FRIENDSHIP, TemplateType.SLICE_OF_LIFE),
                suggestedLength = StoryLength.SHORT,
                timeContext = getTimeContext()
            )
            
            ReadingMood.CURIOUS -> MoodRecommendation(
                primaryMood = mood,
                confidence = 0.8f,
                reasoning = "Great for mysteries and learning new things",
                recommendedGenres = listOf(StoryGenre.MYSTERY, StoryGenre.EDUCATIONAL, StoryGenre.SCIENCE_FICTION),
                recommendedTemplates = listOf(TemplateType.MYSTERY),
                suggestedLength = StoryLength.MEDIUM,
                timeContext = getTimeContext()
            )
            
            ReadingMood.CREATIVE -> MoodRecommendation(
                primaryMood = mood,
                confidence = 0.8f,
                reasoning = "Perfect for imaginative and fantastical stories",
                recommendedGenres = listOf(StoryGenre.FANTASY, StoryGenre.FAIRYTALE, StoryGenre.SCIENCE_FICTION),
                recommendedTemplates = listOf(TemplateType.FAIRY_TALE, TemplateType.HEROS_JOURNEY),
                suggestedLength = StoryLength.LONG,
                timeContext = getTimeContext()
            )
            
            ReadingMood.SOCIAL -> MoodRecommendation(
                primaryMood = mood,
                confidence = 0.8f,
                reasoning = "Great for stories about relationships and connections",
                recommendedGenres = listOf(StoryGenre.FRIENDSHIP, StoryGenre.FAMILY, StoryGenre.COMEDY),
                recommendedTemplates = listOf(TemplateType.FRIENDSHIP, TemplateType.FAMILY_DRAMA),
                suggestedLength = StoryLength.MEDIUM,
                timeContext = getTimeContext()
            )
            
            ReadingMood.CONTEMPLATIVE -> MoodRecommendation(
                primaryMood = mood,
                confidence = 0.7f,
                reasoning = "Time for deeper, more thoughtful stories",
                recommendedGenres = listOf(StoryGenre.HISTORICAL, StoryGenre.EDUCATIONAL, StoryGenre.FAMILY),
                recommendedTemplates = listOf(TemplateType.COMING_OF_AGE, TemplateType.FAMILY_DRAMA),
                suggestedLength = StoryLength.LONG,
                timeContext = getTimeContext()
            )
            
            ReadingMood.PLAYFUL -> MoodRecommendation(
                primaryMood = mood,
                confidence = 0.9f,
                reasoning = "Perfect for fun and silly stories",
                recommendedGenres = listOf(StoryGenre.COMEDY, StoryGenre.ADVENTURE, StoryGenre.FAIRYTALE),
                recommendedTemplates = listOf(TemplateType.ANIMAL_ADVENTURE, TemplateType.FAIRY_TALE),
                suggestedLength = StoryLength.SHORT,
                timeContext = getTimeContext()
            )
            
            ReadingMood.COMFORT -> MoodRecommendation(
                primaryMood = mood,
                confidence = 0.8f,
                reasoning = "Time for familiar, comforting stories",
                recommendedGenres = listOf(StoryGenre.FAMILY, StoryGenre.FRIENDSHIP, StoryGenre.FAIRYTALE),
                recommendedTemplates = listOf(TemplateType.FRIENDSHIP, TemplateType.SLICE_OF_LIFE),
                suggestedLength = StoryLength.SHORT,
                timeContext = getTimeContext()
            )
            
            ReadingMood.CHALLENGE -> MoodRecommendation(
                primaryMood = mood,
                confidence = 0.7f,
                reasoning = "Ready for complex and challenging stories",
                recommendedGenres = listOf(StoryGenre.MYSTERY, StoryGenre.SCIENCE_FICTION, StoryGenre.HISTORICAL),
                recommendedTemplates = listOf(TemplateType.MYSTERY, TemplateType.HEROS_JOURNEY),
                suggestedLength = StoryLength.LONG,
                timeContext = getTimeContext()
            )
        }
    }
    
    private fun getCurrentTimeOfDay(): TimeOfDay {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 5..7 -> TimeOfDay.EARLY_MORNING
            in 8..11 -> TimeOfDay.MORNING
            in 12..16 -> TimeOfDay.AFTERNOON
            in 17..20 -> TimeOfDay.EVENING
            else -> TimeOfDay.NIGHT
        }
    }
    
    private fun detectActivityContext(timeOfDay: TimeOfDay): ActivityContext {
        val calendar = Calendar.getInstance()
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        val isWeekend = dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY
        
        return when {
            isWeekend && timeOfDay == TimeOfDay.MORNING -> ActivityContext.WEEKEND_MORNING
            isWeekend && timeOfDay == TimeOfDay.AFTERNOON -> ActivityContext.WEEKEND_AFTERNOON
            timeOfDay == TimeOfDay.EARLY_MORNING -> ActivityContext.JUST_WOKE_UP
            timeOfDay == TimeOfDay.MORNING -> ActivityContext.BREAKFAST_TIME
            timeOfDay == TimeOfDay.AFTERNOON && !isWeekend -> ActivityContext.AFTER_SCHOOL
            timeOfDay == TimeOfDay.EVENING -> ActivityContext.BEFORE_DINNER
            timeOfDay == TimeOfDay.NIGHT -> ActivityContext.BEDTIME
            else -> ActivityContext.UNKNOWN
        }
    }
    
    private suspend fun getRecentReadingHistory(): List<Story> {
        return try {
            val allStories = storyRepository.getAllStories().first()
            val recentCutoff = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000) // Last 7 days
            allStories.filter { it.createdAt > recentCutoff }
                .sortedByDescending { it.createdAt }
                .take(10)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get recent reading history")
            emptyList()
        }
    }
    
    private fun getCurrentDayOfWeek(): Int {
        return Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
    }
    
    private fun analyzeMoodFactors(
        timeOfDay: TimeOfDay,
        activityContext: ActivityContext,
        recentHistory: List<Story>,
        dayOfWeek: Int
    ): MoodRecommendation {
        val isWeekend = dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY
        
        // Time-based mood detection
        val timeMood = when (timeOfDay) {
            TimeOfDay.EARLY_MORNING -> ReadingMood.CALM
            TimeOfDay.MORNING -> if (isWeekend) ReadingMood.CREATIVE else ReadingMood.ENERGETIC
            TimeOfDay.AFTERNOON -> ReadingMood.CURIOUS
            TimeOfDay.EVENING -> ReadingMood.SOCIAL
            TimeOfDay.NIGHT -> ReadingMood.COMFORT
        }
        
        // Activity context adjustments
        val contextMood = when (activityContext) {
            ActivityContext.JUST_WOKE_UP -> ReadingMood.CALM
            ActivityContext.BREAKFAST_TIME -> ReadingMood.PLAYFUL
            ActivityContext.SCHOOL_BREAK -> ReadingMood.ENERGETIC
            ActivityContext.AFTER_SCHOOL -> ReadingMood.CURIOUS
            ActivityContext.HOMEWORK_BREAK -> ReadingMood.PLAYFUL
            ActivityContext.BEFORE_DINNER -> ReadingMood.SOCIAL
            ActivityContext.BEDTIME -> ReadingMood.COMFORT
            ActivityContext.WEEKEND_MORNING -> ReadingMood.CREATIVE
            ActivityContext.WEEKEND_AFTERNOON -> ReadingMood.ENERGETIC
            ActivityContext.HOLIDAY -> ReadingMood.CREATIVE
            ActivityContext.UNKNOWN -> timeMood
        }
        
        // Recent history patterns
        val historyMood = analyzeHistoryPatterns(recentHistory)
        
        // Combine factors (context gets highest weight)
        val finalMood = when {
            activityContext != ActivityContext.UNKNOWN -> contextMood
            historyMood != null -> historyMood
            else -> timeMood
        }
        
        val confidence = when {
            activityContext != ActivityContext.UNKNOWN && historyMood == contextMood -> 0.9f
            activityContext != ActivityContext.UNKNOWN -> 0.8f
            historyMood != null -> 0.7f
            else -> 0.6f
        }
        
        return MoodRecommendation(
            primaryMood = finalMood,
            confidence = confidence,
            reasoning = buildReasoningText(timeOfDay, activityContext, isWeekend),
            recommendedGenres = getMoodGenres(finalMood),
            recommendedTemplates = getMoodTemplates(finalMood),
            suggestedLength = getMoodLength(finalMood, timeOfDay),
            timeContext = getTimeContext()
        )
    }
    
    private fun analyzeHistoryPatterns(recentHistory: List<Story>): ReadingMood? {
        if (recentHistory.isEmpty()) return null
        
        // Look for patterns in recent genre preferences
        val genreCounts = recentHistory.groupingBy { it.genre }.eachCount()
        val mostReadGenre = genreCounts.maxByOrNull { it.value }?.key
        
        return when (mostReadGenre) {
            StoryGenre.ADVENTURE -> ReadingMood.ENERGETIC
            StoryGenre.FANTASY -> ReadingMood.CREATIVE
            StoryGenre.MYSTERY -> ReadingMood.CURIOUS
            StoryGenre.FRIENDSHIP -> ReadingMood.SOCIAL
            StoryGenre.FAMILY -> ReadingMood.COMFORT
            StoryGenre.COMEDY -> ReadingMood.PLAYFUL
            StoryGenre.EDUCATIONAL -> ReadingMood.CONTEMPLATIVE
            else -> null
        }
    }
    
    private fun buildReasoningText(
        timeOfDay: TimeOfDay,
        activityContext: ActivityContext,
        isWeekend: Boolean
    ): String {
        return when (activityContext) {
            ActivityContext.BEDTIME -> "It's bedtime - perfect for calming, gentle stories"
            ActivityContext.JUST_WOKE_UP -> "Starting the day gently with peaceful stories"
            ActivityContext.WEEKEND_MORNING -> "Weekend morning - great time for creative adventures"
            ActivityContext.AFTER_SCHOOL -> "After school - perfect for exploring and learning"
            ActivityContext.BREAKFAST_TIME -> "Morning energy - time for fun and playful stories"
            else -> "Based on the time of day, this mood seems like a good fit"
        }
    }
    
    private fun getMoodGenres(mood: ReadingMood): List<StoryGenre> {
        return when (mood) {
            ReadingMood.ENERGETIC -> listOf(StoryGenre.ADVENTURE, StoryGenre.SCIENCE_FICTION)
            ReadingMood.CALM -> listOf(StoryGenre.FRIENDSHIP, StoryGenre.FAMILY, StoryGenre.FAIRYTALE)
            ReadingMood.CURIOUS -> listOf(StoryGenre.MYSTERY, StoryGenre.EDUCATIONAL)
            ReadingMood.CREATIVE -> listOf(StoryGenre.FANTASY, StoryGenre.FAIRYTALE)
            ReadingMood.SOCIAL -> listOf(StoryGenre.FRIENDSHIP, StoryGenre.FAMILY)
            ReadingMood.CONTEMPLATIVE -> listOf(StoryGenre.HISTORICAL, StoryGenre.EDUCATIONAL)
            ReadingMood.PLAYFUL -> listOf(StoryGenre.COMEDY, StoryGenre.ADVENTURE)
            ReadingMood.COMFORT -> listOf(StoryGenre.FAMILY, StoryGenre.FRIENDSHIP)
            ReadingMood.CHALLENGE -> listOf(StoryGenre.MYSTERY, StoryGenre.SCIENCE_FICTION)
        }
    }
    
    private fun getMoodTemplates(mood: ReadingMood): List<TemplateType> {
        return when (mood) {
            ReadingMood.ENERGETIC -> listOf(TemplateType.HEROS_JOURNEY, TemplateType.ADVENTURE)
            ReadingMood.CALM -> listOf(TemplateType.FRIENDSHIP, TemplateType.SLICE_OF_LIFE)
            ReadingMood.CURIOUS -> listOf(TemplateType.MYSTERY)
            ReadingMood.CREATIVE -> listOf(TemplateType.FAIRY_TALE, TemplateType.HEROS_JOURNEY)
            ReadingMood.SOCIAL -> listOf(TemplateType.FRIENDSHIP, TemplateType.FAMILY_DRAMA)
            ReadingMood.CONTEMPLATIVE -> listOf(TemplateType.COMING_OF_AGE)
            ReadingMood.PLAYFUL -> listOf(TemplateType.ANIMAL_ADVENTURE, TemplateType.FAIRY_TALE)
            ReadingMood.COMFORT -> listOf(TemplateType.FRIENDSHIP, TemplateType.SLICE_OF_LIFE)
            ReadingMood.CHALLENGE -> listOf(TemplateType.MYSTERY, TemplateType.HEROS_JOURNEY)
        }
    }
    
    private fun getMoodLength(mood: ReadingMood, timeOfDay: TimeOfDay): StoryLength {
        return when {
            timeOfDay == TimeOfDay.NIGHT -> StoryLength.SHORT
            mood in listOf(ReadingMood.CALM, ReadingMood.COMFORT, ReadingMood.PLAYFUL) -> StoryLength.SHORT
            mood in listOf(ReadingMood.CREATIVE, ReadingMood.CONTEMPLATIVE, ReadingMood.CHALLENGE) -> StoryLength.LONG
            else -> StoryLength.MEDIUM
        }
    }
    
    private fun getTimeContext(): String {
        val timeOfDay = getCurrentTimeOfDay()
        val calendar = Calendar.getInstance()
        val isWeekend = calendar.get(Calendar.DAY_OF_WEEK) in listOf(Calendar.SATURDAY, Calendar.SUNDAY)
        
        return when (timeOfDay) {
            TimeOfDay.EARLY_MORNING -> "Early morning"
            TimeOfDay.MORNING -> if (isWeekend) "Weekend morning" else "School morning"
            TimeOfDay.AFTERNOON -> if (isWeekend) "Weekend afternoon" else "After school"
            TimeOfDay.EVENING -> "Evening time"
            TimeOfDay.NIGHT -> "Bedtime"
        }
    }
    
    suspend fun provideFeedback(sessionId: String, rating: Int, actualMoodSelected: ReadingMood?) {
        try {
            val sessions = moodDao.getRecentMoodSessions(100)
            val session = sessions.find { it.id == sessionId }
            
            session?.let {
                val updatedSession = it.copy(
                    userFeedback = rating,
                    userSelectedMood = actualMoodSelected
                )
                moodDao.updateMoodSession(updatedSession)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to provide mood feedback")
        }
    }
    
    suspend fun cleanupOldSessions() {
        try {
            val cutoffTime = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000) // 30 days
            moodDao.deleteOldSessions(cutoffTime)
        } catch (e: Exception) {
            Timber.e(e, "Failed to cleanup old mood sessions")
        }
    }
}