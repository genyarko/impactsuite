package com.example.mygemma3n.feature.story

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
    val imageGenerationScript: String? = null // Script with visual descriptions for each page
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
    val exactPageCount: Int? = null // Exact page count from slider
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
    val imageGenerationScript: String? = null
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

/* ─────────────────────── Converters ─────────────────────── */

class StoryConverters {
    @TypeConverter fun fromStoryGenre(g: StoryGenre): String = g.name
    @TypeConverter fun toStoryGenre(v: String): StoryGenre = StoryGenre.valueOf(v)

    @TypeConverter fun fromStoryTarget(t: StoryTarget): String = t.name
    @TypeConverter fun toStoryTarget(v: String): StoryTarget = StoryTarget.valueOf(v)

    @TypeConverter fun fromDate(d: Date?): Long? = d?.time
    @TypeConverter fun toDate(v: Long?): Date? = v?.let(::Date)
}

/* ─────────────────────── DAOs ─────────────────────── */

@Dao
interface StoryDao {
    @Query("SELECT * FROM stories ORDER BY createdAt DESC")
    fun getAllStories(): Flow<List<StoryEntity>>

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
}

/* ─────────────────────── Repository ─────────────────────── */

@Singleton
class StoryRepository @Inject constructor(
    private val storyDao: StoryDao,
    private val sessionDao: StoryReadingSessionDao,
    private val gson: Gson
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
        imageGenerationScript = imageGenerationScript
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
        imageGenerationScript = imageGenerationScript
    )
}