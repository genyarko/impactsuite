package com.mygemma3n.aiapp.feature.story

import androidx.room.*
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/* ─────────────────────── Core enums ─────────────────────── */

enum class StoryGenre { 
    ADVENTURE, FANTASY, MYSTERY, SCIENCE_FICTION, HISTORICAL, 
    FRIENDSHIP, FAMILY, EDUCATIONAL, FAIRYTALE, COMEDY 
}

enum class StoryLength { SHORT, MEDIUM, LONG }

enum class StoryTarget { 
    KINDERGARTEN, ELEMENTARY, MIDDLE_SCHOOL, HIGH_SCHOOL, ADULT 
}

/* ─────────────────────── Domain models ───────────────────── */

data class Story(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val genre: StoryGenre,
    val targetAudience: StoryTarget,
    val pages: List<StoryPage>,
    val totalPages: Int,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val currentPage: Int = 0,
    val isCompleted: Boolean = false,
    val prompt: String = "", // The original user prompt
    val characters: List<String> = emptyList(),
    val setting: String = "",
    val hasImages: Boolean = false,
    val imageGenerationScript: String? = null, // Script with visual descriptions for each page
    val customCharacterIds: List<String> = emptyList(), // IDs of custom characters used
    val templateId: String? = null, // Template used for generation
    val generatedFromMood: String? = null // ReadingMood that influenced generation
)

data class StoryPage(
    val pageNumber: Int,
    val content: String,
    val title: String? = null,
    val isRead: Boolean = false,
    val readAt: Long? = null,
    val imageUrl: String? = null,
    val imageDescription: String? = null
)

data class StoryRequest(
    val prompt: String,
    val genre: StoryGenre,
    val targetAudience: StoryTarget,
    val length: StoryLength,
    val characters: List<String> = emptyList(),
    val setting: String = "",
    val theme: String = "",
    val exactPageCount: Int? = null, // Exact page count from slider
    val customCharacters: List<Character> = emptyList(), // Custom characters to include
    val templateId: String? = null, // Template to use for structure
    val moodContext: String? = null, // ReadingMood context
    val templateCustomizations: Map<String, String> = emptyMap() // Custom beat modifications
)

data class ReadingStreak(
    val date: String, // Format: "yyyy-MM-dd"
    val storiesRead: Int = 0,
    val pagesRead: Int = 0,
    val totalReadingTimeMinutes: Int = 0,
    val goalMet: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

data class ReadingGoal(
    val id: String = "daily_goal",
    val dailyPagesGoal: Int = 5,
    val dailyTimeGoalMinutes: Int = 15,
    val dailyStoriesGoal: Int = 1,
    val lastUpdated: Long = System.currentTimeMillis()
)

data class AchievementBadge(
    val id: String,
    val name: String,
    val description: String,
    val type: BadgeType,
    val milestone: Int,
    val unlockedAt: Long? = null,
    val isUnlocked: Boolean = false,
    val iconName: String = "default_badge"
)

data class ReadingStats(
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val totalStoriesRead: Int = 0,
    val totalPagesRead: Int = 0,
    val totalTimeMinutes: Int = 0,
    val goalsMet: Int = 0,
    val unlockedBadges: List<AchievementBadge> = emptyList(),
    val nextBadge: AchievementBadge? = null
)

/* ─────────────────────── Room entities ───────────────────── */

@Entity(tableName = "stories")
data class StoryEntity(
    @PrimaryKey val id: String,
    val title: String,
    val genre: StoryGenre,
    val targetAudience: StoryTarget,
    val totalPages: Int,
    val createdAt: Long,
    val completedAt: Long? = null,
    val currentPage: Int = 0,
    val isCompleted: Boolean = false,
    val prompt: String = "",
    val characters: String = "", // JSON array
    val setting: String = "",
    val pagesJson: String, // JSON serialized pages
    val hasImages: Boolean = false,
    val imageGenerationScript: String? = null,
    val customCharacterIds: String = "", // JSON array of character IDs
    val templateId: String? = null,
    val generatedFromMood: String? = null
)

@Entity(tableName = "story_reading_sessions")
data class StoryReadingSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val storyId: String,
    val startTime: Long,
    val endTime: Long? = null,
    val pagesRead: Int = 0,
    val totalTimeMinutes: Int = 0
)

@Entity(tableName = "reading_streaks")
data class ReadingStreakEntity(
    @PrimaryKey val date: String, // Format: "yyyy-MM-dd"
    val storiesRead: Int = 0,
    val pagesRead: Int = 0,
    val totalReadingTimeMinutes: Int = 0,
    val goalMet: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "reading_goals")
data class ReadingGoalEntity(
    @PrimaryKey val id: String = "daily_goal",
    val dailyPagesGoal: Int = 5,
    val dailyTimeGoalMinutes: Int = 15,
    val dailyStoriesGoal: Int = 1,
    val lastUpdated: Long = System.currentTimeMillis()
)

@Entity(tableName = "achievement_badges")
data class AchievementBadgeEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val type: BadgeType,
    val milestone: Int,
    val unlockedAt: Long? = null,
    val isUnlocked: Boolean = false,
    val iconName: String = "default_badge"
)

enum class BadgeType {
    READING_STREAK,      // 3, 7, 14, 30, 60, 100 days
    PAGES_READ,          // 50, 100, 500, 1000 pages
    STORIES_COMPLETED,   // 5, 10, 25, 50 stories
    TIME_SPENT,          // 5, 10, 25, 50 hours
    GENRE_EXPLORER,      // Read 3+ different genres
    SPEED_READER,        // Read story in under 10 minutes
    DEDICATED_READER     // Read for 7 consecutive days
}

/* ─────────────────────── Converters ─────────────────────── */

class StoryConverters {
    @TypeConverter fun fromStoryGenre(g: StoryGenre): String = g.name
    @TypeConverter fun toStoryGenre(v: String): StoryGenre = StoryGenre.valueOf(v)

    @TypeConverter fun fromStoryTarget(t: StoryTarget): String = t.name
    @TypeConverter fun toStoryTarget(v: String): StoryTarget = StoryTarget.valueOf(v)

    @TypeConverter fun fromBadgeType(t: BadgeType): String = t.name
    @TypeConverter fun toBadgeType(v: String): BadgeType = BadgeType.valueOf(v)

    @TypeConverter fun fromDate(d: Date?): Long? = d?.time
    @TypeConverter fun toDate(v: Long?): Date? = v?.let(::Date)
}

/* ─────────────────────── DAOs ─────────────────────── */

@Dao
interface StoryDao {
    @Query("SELECT * FROM stories ORDER BY createdAt DESC")
    fun getAllStories(): Flow<List<StoryEntity>>

    @Query("SELECT * FROM stories ORDER BY createdAt DESC")
    suspend fun getAllStoriesSync(): List<StoryEntity>

    @Query("SELECT * FROM stories WHERE id = :storyId")
    suspend fun getStoryById(storyId: String): StoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStory(story: StoryEntity)

    @Update
    suspend fun updateStory(story: StoryEntity)

    @Query("UPDATE stories SET currentPage = :currentPage WHERE id = :storyId")
    suspend fun updateCurrentPage(storyId: String, currentPage: Int)

    @Query("UPDATE stories SET isCompleted = 1, completedAt = :completedAt WHERE id = :storyId")
    suspend fun markStoryCompleted(storyId: String, completedAt: Long)

    @Query("DELETE FROM stories WHERE id = :storyId")
    suspend fun deleteStory(storyId: String)

    @Query("DELETE FROM stories")
    suspend fun deleteAllStories()

    @Query("SELECT * FROM stories WHERE genre = :genre ORDER BY createdAt DESC")
    suspend fun getStoriesByGenre(genre: StoryGenre): List<StoryEntity>

    @Query("SELECT * FROM stories WHERE targetAudience = :target ORDER BY createdAt DESC")
    suspend fun getStoriesByTarget(target: StoryTarget): List<StoryEntity>
}

@Dao
interface StoryReadingSessionDao {
    @Insert
    suspend fun insertSession(session: StoryReadingSession)

    @Query("SELECT * FROM story_reading_sessions WHERE storyId = :storyId ORDER BY startTime DESC")
    suspend fun getSessionsForStory(storyId: String): List<StoryReadingSession>

    @Query("SELECT COUNT(*) FROM story_reading_sessions WHERE storyId = :storyId")
    suspend fun getSessionCountForStory(storyId: String): Int

    @Query("SELECT SUM(totalTimeMinutes) FROM story_reading_sessions WHERE storyId = :storyId")
    suspend fun getTotalReadingTimeForStory(storyId: String): Int?

    @Query("DELETE FROM story_reading_sessions")
    suspend fun deleteAllSessions()

    @Query("SELECT * FROM story_reading_sessions WHERE date(startTime/1000, 'unixepoch') = :date")
    suspend fun getSessionsForDate(date: String): List<StoryReadingSession>

    @Query("SELECT * FROM story_reading_sessions")
    suspend fun getAllSessions(): List<StoryReadingSession>
}

@Dao
interface ReadingStreakDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateStreak(streak: ReadingStreakEntity)

    @Query("SELECT * FROM reading_streaks WHERE date = :date")
    suspend fun getStreakForDate(date: String): ReadingStreakEntity?

    @Query("SELECT * FROM reading_streaks ORDER BY date DESC LIMIT :limit")
    suspend fun getRecentStreaks(limit: Int = 30): List<ReadingStreakEntity>

    @Query("SELECT COUNT(*) FROM reading_streaks WHERE goalMet = 1 AND date >= :startDate ORDER BY date DESC")
    suspend fun getConsecutiveStreakCount(startDate: String): Int

    @Query("SELECT MAX(consecutiveDays) FROM (SELECT COUNT(*) as consecutiveDays FROM reading_streaks WHERE goalMet = 1 GROUP BY (julianday(date) - julianday((SELECT MIN(date) FROM reading_streaks WHERE goalMet = 1))))")
    suspend fun getLongestStreak(): Int?

    @Query("SELECT COUNT(*) FROM reading_streaks WHERE goalMet = 1")
    suspend fun getTotalGoalDaysMet(): Int

    @Query("DELETE FROM reading_streaks")
    suspend fun deleteAllStreaks()
}

@Dao
interface ReadingGoalDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateGoal(goal: ReadingGoalEntity)

    @Query("SELECT * FROM reading_goals WHERE id = :id")
    suspend fun getGoal(id: String = "daily_goal"): ReadingGoalEntity?

    @Query("DELETE FROM reading_goals")
    suspend fun deleteAllGoals()
}

@Dao
interface AchievementBadgeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateBadge(badge: AchievementBadgeEntity)

    @Query("SELECT * FROM achievement_badges ORDER BY unlockedAt DESC")
    suspend fun getAllBadges(): List<AchievementBadgeEntity>

    @Query("SELECT * FROM achievement_badges WHERE isUnlocked = 1 ORDER BY unlockedAt DESC")
    suspend fun getUnlockedBadges(): List<AchievementBadgeEntity>

    @Query("SELECT * FROM achievement_badges WHERE isUnlocked = 0 ORDER BY type, milestone")
    suspend fun getLockedBadges(): List<AchievementBadgeEntity>

    @Query("UPDATE achievement_badges SET isUnlocked = 1, unlockedAt = :unlockedAt WHERE id = :badgeId")
    suspend fun unlockBadge(badgeId: String, unlockedAt: Long)

    @Query("SELECT * FROM achievement_badges WHERE type = :type AND isUnlocked = 1")
    suspend fun getUnlockedBadgesOfType(type: BadgeType): List<AchievementBadgeEntity>

    @Query("DELETE FROM achievement_badges")
    suspend fun deleteAllBadges()
}

/* ─────────────────────── Repository ─────────────────────── */

@Singleton
class StoryRepository @Inject constructor(
    val storyDao: StoryDao,
    val sessionDao: StoryReadingSessionDao,
    val streakDao: ReadingStreakDao,
    val goalDao: ReadingGoalDao,
    val badgeDao: AchievementBadgeDao,
    private val memoryManager: ReadingStreakMemoryManager,
    val gson: Gson
) {
    
    fun getAllStories(): Flow<List<Story>> =
        storyDao.getAllStories().map { entities ->
            entities.map { it.toDomain(gson) }
        }

    suspend fun getStoryById(storyId: String): Story? =
        storyDao.getStoryById(storyId)?.toDomain(gson)

    suspend fun saveStory(story: Story) {
        val entity = story.toEntity(gson)
        storyDao.insertStory(entity)
    }

    suspend fun updateStory(story: Story) {
        val entity = story.toEntity(gson)
        storyDao.updateStory(entity)
    }

    suspend fun updateCurrentPage(storyId: String, currentPage: Int) {
        storyDao.updateCurrentPage(storyId, currentPage)
    }

    suspend fun markStoryCompleted(storyId: String) {
        storyDao.markStoryCompleted(storyId, System.currentTimeMillis())
    }

    suspend fun deleteStory(storyId: String) {
        storyDao.deleteStory(storyId)
    }

    suspend fun getStoriesByGenre(genre: StoryGenre): List<Story> =
        storyDao.getStoriesByGenre(genre).map { it.toDomain(gson) }

    suspend fun getStoriesByTarget(target: StoryTarget): List<Story> =
        storyDao.getStoriesByTarget(target).map { it.toDomain(gson) }

    suspend fun getAllStoriesSync(): List<StoryEntity> =
        storyDao.getAllStoriesSync()

    suspend fun startReadingSession(storyId: String): Long {
        val session = StoryReadingSession(
            storyId = storyId,
            startTime = System.currentTimeMillis()
        )
        sessionDao.insertSession(session)
        return session.startTime
    }

    suspend fun getReadingStats(storyId: String): Pair<Int, Int> {
        val sessionCount = sessionDao.getSessionCountForStory(storyId)
        val totalTime = sessionDao.getTotalReadingTimeForStory(storyId) ?: 0
        return Pair(sessionCount, totalTime)
    }

    // Reading Streak Methods
    suspend fun updateDailyStreak(date: String, pagesRead: Int = 0, timeMinutes: Int = 0, storiesRead: Int = 0) {
        val existing = streakDao.getStreakForDate(date)
        val goalEntity = goalDao.getGoal()
        val goal = goalEntity?.toDomain() ?: getDefaultGoal()
        
        val updatedStreak = if (existing != null) {
            existing.copy(
                pagesRead = existing.pagesRead + pagesRead,
                totalReadingTimeMinutes = existing.totalReadingTimeMinutes + timeMinutes,
                storiesRead = existing.storiesRead + storiesRead
            )
        } else {
            ReadingStreakEntity(
                date = date,
                pagesRead = pagesRead,
                totalReadingTimeMinutes = timeMinutes,
                storiesRead = storiesRead
            )
        }
        
        val goalMet = updatedStreak.pagesRead >= goal.dailyPagesGoal ||
                     updatedStreak.totalReadingTimeMinutes >= goal.dailyTimeGoalMinutes ||
                     updatedStreak.storiesRead >= goal.dailyStoriesGoal
        
        streakDao.insertOrUpdateStreak(updatedStreak.copy(goalMet = goalMet))
        
        // Check for new badge achievements
        checkAndUnlockBadges()
        
        // Invalidate cache after potential badge changes
        memoryManager.invalidateCache()
    }

    suspend fun getCurrentStreak(): Int {
        val today = java.time.LocalDate.now().toString()
        return streakDao.getConsecutiveStreakCount(today)
    }

    suspend fun getLongestStreak(): Int {
        return streakDao.getLongestStreak() ?: 0
    }

    suspend fun getReadingGoal(): ReadingGoal {
        return goalDao.getGoal()?.toDomain() ?: getDefaultGoal()
    }

    suspend fun updateReadingGoal(goal: ReadingGoal) {
        goalDao.insertOrUpdateGoal(goal.toEntity())
    }

    suspend fun getOverallReadingStats(forceRefresh: Boolean = false): ReadingStats {
        return memoryManager.getOptimizedReadingStats(this, forceRefresh)
    }

    suspend fun getAllBadges(): List<AchievementBadge> {
        return badgeDao.getAllBadges().map { it.toDomain() }
    }

    suspend fun initializeDefaultBadges() {
        val defaultBadges = createDefaultBadges()
        defaultBadges.forEach { badge ->
            badgeDao.insertOrUpdateBadge(badge.toEntity())
        }
    }

    private suspend fun checkAndUnlockBadges() {
        val stats = getOverallReadingStats()
        val lockedBadges = badgeDao.getLockedBadges()
        
        lockedBadges.forEach { badge ->
            val shouldUnlock = when (badge.type) {
                BadgeType.READING_STREAK -> stats.currentStreak >= badge.milestone
                BadgeType.PAGES_READ -> stats.totalPagesRead >= badge.milestone
                BadgeType.STORIES_COMPLETED -> stats.totalStoriesRead >= badge.milestone
                BadgeType.TIME_SPENT -> stats.totalTimeMinutes >= badge.milestone * 60
                BadgeType.GENRE_EXPLORER -> getUniqueGenresRead() >= badge.milestone
                BadgeType.SPEED_READER -> hasSpeedReadingAchievement()
                BadgeType.DEDICATED_READER -> stats.currentStreak >= badge.milestone
            }
            
            if (shouldUnlock) {
                badgeDao.unlockBadge(badge.id, System.currentTimeMillis())
            }
        }
    }

    private suspend fun getNextBadgeToUnlock(): AchievementBadge? {
        val lockedBadges = badgeDao.getLockedBadges()
        val stats = getOverallReadingStats()
        
        return lockedBadges.minByOrNull { badge ->
            when (badge.type) {
                BadgeType.READING_STREAK -> badge.milestone - stats.currentStreak
                BadgeType.PAGES_READ -> badge.milestone - stats.totalPagesRead
                BadgeType.STORIES_COMPLETED -> badge.milestone - stats.totalStoriesRead
                BadgeType.TIME_SPENT -> (badge.milestone * 60) - stats.totalTimeMinutes
                BadgeType.GENRE_EXPLORER -> badge.milestone - getUniqueGenresRead()
                BadgeType.SPEED_READER -> if (hasSpeedReadingAchievement()) 0 else 1
                BadgeType.DEDICATED_READER -> badge.milestone - stats.currentStreak
            }
        }?.toDomain()
    }

    private suspend fun getUniqueGenresRead(): Int {
        val stories = storyDao.getAllStoriesSync()
        return stories.map { it.genre }.toSet().size
    }

    private suspend fun hasSpeedReadingAchievement(): Boolean {
        // Check if any story was completed in under 10 minutes
        val allSessions = sessionDao.getAllSessions()
        return allSessions.any { it.totalTimeMinutes < 10 }
    }

    private fun getDefaultGoal() = ReadingGoal()

    private fun createDefaultBadges(): List<AchievementBadge> = listOf(
        // Reading Streak Badges
        AchievementBadge("streak_3", "First Steps", "Read for 3 consecutive days", BadgeType.READING_STREAK, 3, iconName = "streak_bronze"),
        AchievementBadge("streak_7", "Week Warrior", "Read for 7 consecutive days", BadgeType.READING_STREAK, 7, iconName = "streak_silver"),
        AchievementBadge("streak_14", "Two Week Champion", "Read for 14 consecutive days", BadgeType.READING_STREAK, 14, iconName = "streak_gold"),
        AchievementBadge("streak_30", "Monthly Master", "Read for 30 consecutive days", BadgeType.READING_STREAK, 30, iconName = "streak_platinum"),
        
        // Pages Read Badges
        AchievementBadge("pages_50", "Page Turner", "Read 50 pages", BadgeType.PAGES_READ, 50, iconName = "pages_bronze"),
        AchievementBadge("pages_100", "Bookworm", "Read 100 pages", BadgeType.PAGES_READ, 100, iconName = "pages_silver"),
        AchievementBadge("pages_500", "Reading Machine", "Read 500 pages", BadgeType.PAGES_READ, 500, iconName = "pages_gold"),
        
        // Stories Completed Badges
        AchievementBadge("stories_5", "Story Starter", "Complete 5 stories", BadgeType.STORIES_COMPLETED, 5, iconName = "stories_bronze"),
        AchievementBadge("stories_10", "Tale Collector", "Complete 10 stories", BadgeType.STORIES_COMPLETED, 10, iconName = "stories_silver"),
        AchievementBadge("stories_25", "Story Master", "Complete 25 stories", BadgeType.STORIES_COMPLETED, 25, iconName = "stories_gold"),
        
        // Time Spent Badges
        AchievementBadge("time_5h", "Reading Enthusiast", "Spend 5 hours reading", BadgeType.TIME_SPENT, 5, iconName = "time_bronze"),
        AchievementBadge("time_10h", "Devoted Reader", "Spend 10 hours reading", BadgeType.TIME_SPENT, 10, iconName = "time_silver"),
        
        // Special Badges
        AchievementBadge("genre_explorer", "Genre Explorer", "Read stories from 3 different genres", BadgeType.GENRE_EXPLORER, 3, iconName = "explorer"),
        AchievementBadge("speed_reader", "Speed Reader", "Complete a story in under 10 minutes", BadgeType.SPEED_READER, 1, iconName = "speed")
    )

    /* -- mapping helpers ------------------------------------------------- */

    private fun Story.toEntity(gson: Gson) = StoryEntity(
        id = id,
        title = title,
        genre = genre,
        targetAudience = targetAudience,
        totalPages = totalPages,
        createdAt = createdAt,
        completedAt = completedAt,
        currentPage = currentPage,
        isCompleted = isCompleted,
        prompt = prompt,
        characters = gson.toJson(characters),
        setting = setting,
        pagesJson = gson.toJson(pages),
        hasImages = hasImages,
        imageGenerationScript = imageGenerationScript,
        customCharacterIds = gson.toJson(customCharacterIds),
        templateId = templateId,
        generatedFromMood = generatedFromMood
    )

    private fun StoryEntity.toDomain(gson: Gson): Story = Story(
        id = id,
        title = title,
        genre = genre,
        targetAudience = targetAudience,
        pages = gson.fromJson(pagesJson, Array<StoryPage>::class.java).toList(),
        totalPages = totalPages,
        createdAt = createdAt,
        completedAt = completedAt,
        currentPage = currentPage,
        isCompleted = isCompleted,
        prompt = prompt,
        characters = gson.fromJson(characters, Array<String>::class.java).toList(),
        setting = setting,
        hasImages = hasImages,
        imageGenerationScript = imageGenerationScript,
        customCharacterIds = gson.fromJson(customCharacterIds, Array<String>::class.java).toList(),
        templateId = templateId,
        generatedFromMood = generatedFromMood
    )

    private fun ReadingStreakEntity.toDomain() = ReadingStreak(
        date = date,
        storiesRead = storiesRead,
        pagesRead = pagesRead,
        totalReadingTimeMinutes = totalReadingTimeMinutes,
        goalMet = goalMet,
        createdAt = createdAt
    )

    private fun ReadingStreak.toEntity() = ReadingStreakEntity(
        date = date,
        storiesRead = storiesRead,
        pagesRead = pagesRead,
        totalReadingTimeMinutes = totalReadingTimeMinutes,
        goalMet = goalMet,
        createdAt = createdAt
    )

    private fun ReadingGoalEntity.toDomain() = ReadingGoal(
        id = id,
        dailyPagesGoal = dailyPagesGoal,
        dailyTimeGoalMinutes = dailyTimeGoalMinutes,
        dailyStoriesGoal = dailyStoriesGoal,
        lastUpdated = lastUpdated
    )

    private fun ReadingGoal.toEntity() = ReadingGoalEntity(
        id = id,
        dailyPagesGoal = dailyPagesGoal,
        dailyTimeGoalMinutes = dailyTimeGoalMinutes,
        dailyStoriesGoal = dailyStoriesGoal,
        lastUpdated = lastUpdated
    )

    private fun AchievementBadgeEntity.toDomain() = AchievementBadge(
        id = id,
        name = name,
        description = description,
        type = type,
        milestone = milestone,
        unlockedAt = unlockedAt,
        isUnlocked = isUnlocked,
        iconName = iconName
    )

    private fun AchievementBadge.toEntity() = AchievementBadgeEntity(
        id = id,
        name = name,
        description = description,
        type = type,
        milestone = milestone,
        unlockedAt = unlockedAt,
        isUnlocked = isUnlocked,
        iconName = iconName
    )
}