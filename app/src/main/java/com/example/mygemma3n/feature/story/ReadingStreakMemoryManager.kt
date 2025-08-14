package com.example.mygemma3n.feature.story

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReadingStreakMemoryManager @Inject constructor() {
    
    // Track last stats calculation to avoid excessive recalculations
    private var lastStatsCalculation = 0L
    private var cachedStats: ReadingStats? = null
    private val cacheValidityMs = 30_000L // 30 seconds cache
    
    suspend fun getOptimizedReadingStats(
        storyRepository: StoryRepository,
        forceRefresh: Boolean = false
    ): ReadingStats = withContext(Dispatchers.IO) {
        try {
            val now = System.currentTimeMillis()
            
            // Return cached stats if valid and not forced refresh
            if (!forceRefresh && cachedStats != null && 
                (now - lastStatsCalculation) < cacheValidityMs) {
                return@withContext cachedStats!!
            }
            
            // Calculate fresh stats with memory optimization
            val stats = calculateOptimizedStats(storyRepository)
            
            // Update cache
            cachedStats = stats
            lastStatsCalculation = now
            
            // Request garbage collection after heavy calculation
            System.gc()
            
            stats
        } catch (e: Exception) {
            Timber.e(e, "Error calculating optimized reading stats")
            cachedStats ?: ReadingStats() // Return cached or default
        }
    }
    
    private suspend fun calculateOptimizedStats(storyRepository: StoryRepository): ReadingStats {
        // Batch all database queries to reduce overhead
        val allStoriesEntities = storyRepository.storyDao.getAllStoriesSync()
        val allSessions = storyRepository.sessionDao.getAllSessions()
        val goalsMet = storyRepository.streakDao.getTotalGoalDaysMet()
        val unlockedBadgesEntities = storyRepository.badgeDao.getUnlockedBadges()
        
        // Process in memory to reduce database hits
        val allStories = allStoriesEntities.map { it.toDomain(storyRepository.gson) }
        val completedStories = allStories.filter { it.isCompleted }
        val totalPages = completedStories.sumOf { it.totalPages }
        val totalTime = allSessions.sumOf { it.totalTimeMinutes }
        
        // Calculate streaks efficiently
        val currentStreak = calculateCurrentStreakOptimized(storyRepository)
        val longestStreak = storyRepository.streakDao.getLongestStreak() ?: 0
        
        // Convert badges efficiently
        val unlockedBadges = unlockedBadgesEntities.map { it.toDomain() }
        val nextBadge = findNextBadgeOptimized(storyRepository, allStories.size, totalPages, totalTime, currentStreak)
        
        return ReadingStats(
            currentStreak = currentStreak,
            longestStreak = longestStreak,
            totalStoriesRead = completedStories.size,
            totalPagesRead = totalPages,
            totalTimeMinutes = totalTime,
            goalsMet = goalsMet,
            unlockedBadges = unlockedBadges,
            nextBadge = nextBadge
        )
    }
    
    private suspend fun calculateCurrentStreakOptimized(storyRepository: StoryRepository): Int {
        return try {
            val today = java.time.LocalDate.now().toString()
            storyRepository.streakDao.getConsecutiveStreakCount(today)
        } catch (e: Exception) {
            Timber.e(e, "Error calculating current streak")
            0
        }
    }
    
    private suspend fun findNextBadgeOptimized(
        storyRepository: StoryRepository,
        totalStories: Int,
        totalPages: Int,
        totalTimeMinutes: Int,
        currentStreak: Int
    ): AchievementBadge? {
        return try {
            val lockedBadges = storyRepository.badgeDao.getLockedBadges()
            val uniqueGenres = storyRepository.storyDao.getAllStoriesSync().map { it.genre }.toSet().size
            
            lockedBadges.minByOrNull { badge ->
                when (badge.type) {
                    BadgeType.READING_STREAK -> badge.milestone - currentStreak
                    BadgeType.PAGES_READ -> badge.milestone - totalPages
                    BadgeType.STORIES_COMPLETED -> badge.milestone - totalStories
                    BadgeType.TIME_SPENT -> (badge.milestone * 60) - totalTimeMinutes
                    BadgeType.GENRE_EXPLORER -> badge.milestone - uniqueGenres
                    BadgeType.SPEED_READER -> if (hasSpeedReadingAchievement(storyRepository)) 0 else 1
                    BadgeType.DEDICATED_READER -> badge.milestone - currentStreak
                }.coerceAtLeast(0)
            }?.toDomain()
        } catch (e: Exception) {
            Timber.e(e, "Error finding next badge")
            null
        }
    }
    
    private suspend fun hasSpeedReadingAchievement(storyRepository: StoryRepository): Boolean {
        return try {
            val sessions = storyRepository.sessionDao.getAllSessions()
            sessions.any { it.totalTimeMinutes < 10 }
        } catch (e: Exception) {
            Timber.e(e, "Error checking speed reading achievement")
            false
        }
    }
    
    fun invalidateCache() {
        cachedStats = null
        lastStatsCalculation = 0L
    }
    
    // Extension functions for entity conversion
    private fun StoryEntity.toDomain(gson: com.google.gson.Gson): Story = Story(
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
}