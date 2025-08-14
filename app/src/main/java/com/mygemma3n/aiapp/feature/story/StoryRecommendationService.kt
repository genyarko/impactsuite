package com.mygemma3n.aiapp.feature.story

import com.mygemma3n.aiapp.data.UnifiedGemmaService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

data class StoryRecommendation(
    val suggestedPrompt: String,
    val genre: StoryGenre,
    val targetAudience: StoryTarget,
    val length: StoryLength,
    val reasoning: String,
    val confidence: Float // 0.0 to 1.0
)

@Singleton
class StoryRecommendationService @Inject constructor(
    private val storyRepository: StoryRepository,
    private val gemmaService: UnifiedGemmaService
) {
    
    suspend fun getPersonalizedRecommendations(
        targetAudience: StoryTarget,
        count: Int = 3
    ): List<StoryRecommendation> = withContext(Dispatchers.IO) {
        try {
            val readingHistory = analyzeReadingHistory()
            return@withContext generateAIRecommendations(readingHistory, targetAudience, count)
        } catch (e: Exception) {
            Timber.e(e, "Failed to generate personalized recommendations")
            return@withContext getFallbackRecommendations(targetAudience, count)
        }
    }
    
    private suspend fun analyzeReadingHistory(): ReadingHistoryAnalysis {
        val allStories = storyRepository.getAllStoriesSync()
        val completedStories = allStories.filter { it.isCompleted }
        val recentStories = allStories.sortedByDescending { it.createdAt }.take(10)
        
        return ReadingHistoryAnalysis(
            totalStoriesRead = completedStories.size,
            favoriteGenres = analyzeGenrePreferences(completedStories),
            averageLength = calculateAverageLength(completedStories),
            readingFrequency = calculateReadingFrequency(allStories),
            commonThemes = extractCommonThemes(recentStories),
            lastReadGenres = recentStories.take(5).map { it.genre }.distinct()
        )
    }
    
    private fun analyzeGenrePreferences(stories: List<StoryEntity>): Map<StoryGenre, Float> {
        if (stories.isEmpty()) return emptyMap()
        
        val genreCounts = stories.groupingBy { it.genre }.eachCount()
        val total = stories.size.toFloat()
        
        return genreCounts.mapValues { (_, count) ->
            count / total
        }.toList().sortedByDescending { it.second }.take(3).toMap()
    }
    
    private fun calculateAverageLength(stories: List<StoryEntity>): Float {
        if (stories.isEmpty()) return 10f
        return stories.map { it.totalPages }.average().toFloat()
    }
    
    private fun calculateReadingFrequency(stories: List<StoryEntity>): ReadingFrequency {
        if (stories.isEmpty()) return ReadingFrequency.OCCASIONAL
        
        val now = System.currentTimeMillis()
        val oneWeekAgo = now - (7 * 24 * 60 * 60 * 1000)
        val oneMonthAgo = now - (30 * 24 * 60 * 60 * 1000)
        
        val recentStories = stories.count { it.createdAt > oneWeekAgo }
        val monthlyStories = stories.count { it.createdAt > oneMonthAgo }
        
        return when {
            recentStories >= 3 -> ReadingFrequency.DAILY
            monthlyStories >= 5 -> ReadingFrequency.WEEKLY
            else -> ReadingFrequency.OCCASIONAL
        }
    }
    
    private fun extractCommonThemes(stories: List<StoryEntity>): List<String> {
        val themes = mutableListOf<String>()
        
        stories.forEach { story ->
            // Extract themes from prompts and settings
            val words = (story.prompt + " " + story.setting).lowercase()
                .split(Regex("\\W+"))
                .filter { it.length > 3 }
            
            themes.addAll(words)
        }
        
        return themes.groupingBy { it }
            .eachCount()
            .toList()
            .sortedByDescending { it.second }
            .take(5)
            .map { it.first }
    }
    
    private suspend fun generateAIRecommendations(
        history: ReadingHistoryAnalysis,
        targetAudience: StoryTarget,
        count: Int
    ): List<StoryRecommendation> {
        val prompt = buildRecommendationPrompt(history, targetAudience, count)
        
        return try {
            val response = gemmaService.generateResponse(prompt, image = null, temperature = 0.8f)
            parseRecommendationsFromResponse(response, targetAudience)
        } catch (e: Exception) {
            Timber.e(e, "AI recommendation generation failed")
            getFallbackRecommendations(targetAudience, count)
        }
    }
    
    private fun buildRecommendationPrompt(
        history: ReadingHistoryAnalysis,
        targetAudience: StoryTarget,
        count: Int
    ): String {
        return """
            As an AI reading assistant, generate $count personalized story recommendations based on this reading history:
            
            Reading Profile:
            - Total stories read: ${history.totalStoriesRead}
            - Favorite genres: ${history.favoriteGenres.keys.joinToString(", ")}
            - Average story length: ${history.averageLength.toInt()} pages
            - Reading frequency: ${history.readingFrequency}
            - Recent interests: ${history.commonThemes.joinToString(", ")}
            - Target audience: $targetAudience
            
            For each recommendation, provide:
            1. A specific story prompt that would appeal to this reader
            2. The most suitable genre
            3. Recommended length (SHORT, MEDIUM, LONG)
            4. Why this story would appeal to them
            5. Confidence level (0.0-1.0)
            
            Format each recommendation as:
            RECOMMENDATION_START
            Prompt: [story prompt]
            Genre: [genre]
            Length: [length]
            Reasoning: [why this appeals to the reader]
            Confidence: [0.0-1.0]
            RECOMMENDATION_END
            
            Make the recommendations diverse but aligned with their preferences.
        """.trimIndent()
    }
    
    private fun parseRecommendationsFromResponse(
        response: String,
        targetAudience: StoryTarget
    ): List<StoryRecommendation> {
        val recommendations = mutableListOf<StoryRecommendation>()
        val sections = response.split("RECOMMENDATION_START")
            .filter { it.contains("RECOMMENDATION_END") }
        
        sections.forEach { section ->
            try {
                val content = section.split("RECOMMENDATION_END")[0]
                val lines = content.lines().map { it.trim() }.filter { it.isNotEmpty() }
                
                var prompt = ""
                var genre = StoryGenre.ADVENTURE
                var length = StoryLength.MEDIUM
                var reasoning = ""
                var confidence = 0.8f
                
                lines.forEach { line ->
                    when {
                        line.startsWith("Prompt:", ignoreCase = true) -> 
                            prompt = line.substringAfter(":").trim()
                        line.startsWith("Genre:", ignoreCase = true) -> 
                            genre = parseGenre(line.substringAfter(":").trim())
                        line.startsWith("Length:", ignoreCase = true) -> 
                            length = parseLength(line.substringAfter(":").trim())
                        line.startsWith("Reasoning:", ignoreCase = true) -> 
                            reasoning = line.substringAfter(":").trim()
                        line.startsWith("Confidence:", ignoreCase = true) -> 
                            confidence = line.substringAfter(":").trim().toFloatOrNull() ?: 0.8f
                    }
                }
                
                if (prompt.isNotEmpty()) {
                    recommendations.add(
                        StoryRecommendation(
                            suggestedPrompt = prompt,
                            genre = genre,
                            targetAudience = targetAudience,
                            length = length,
                            reasoning = reasoning,
                            confidence = confidence
                        )
                    )
                }
            } catch (e: Exception) {
                Timber.w(e, "Failed to parse recommendation section: $section")
            }
        }
        
        return recommendations.ifEmpty { getFallbackRecommendations(targetAudience, 3) }
    }
    
    private fun parseGenre(genreStr: String): StoryGenre {
        return try {
            StoryGenre.valueOf(genreStr.uppercase().replace(" ", "_"))
        } catch (e: Exception) {
            when (genreStr.lowercase()) {
                "adventure" -> StoryGenre.ADVENTURE
                "fantasy" -> StoryGenre.FANTASY
                "mystery" -> StoryGenre.MYSTERY
                "science fiction", "sci-fi" -> StoryGenre.SCIENCE_FICTION
                "historical" -> StoryGenre.HISTORICAL
                "friendship" -> StoryGenre.FRIENDSHIP
                "family" -> StoryGenre.FAMILY
                "educational" -> StoryGenre.EDUCATIONAL
                "fairytale", "fairy tale" -> StoryGenre.FAIRYTALE
                "comedy" -> StoryGenre.COMEDY
                else -> StoryGenre.ADVENTURE
            }
        }
    }
    
    private fun parseLength(lengthStr: String): StoryLength {
        return when (lengthStr.uppercase()) {
            "SHORT" -> StoryLength.SHORT
            "MEDIUM" -> StoryLength.MEDIUM
            "LONG" -> StoryLength.LONG
            else -> StoryLength.MEDIUM
        }
    }
    
    private fun getFallbackRecommendations(
        targetAudience: StoryTarget,
        count: Int
    ): List<StoryRecommendation> {
        val fallbacks = when (targetAudience) {
            StoryTarget.KINDERGARTEN -> listOf(
                StoryRecommendation(
                    "A friendly dragon who loves to bake cookies for forest animals",
                    StoryGenre.FAIRYTALE,
                    targetAudience,
                    StoryLength.SHORT,
                    "Simple, friendly story perfect for young readers",
                    0.7f
                ),
                StoryRecommendation(
                    "A little mouse who discovers a magical garden where vegetables talk",
                    StoryGenre.ADVENTURE,
                    targetAudience,
                    StoryLength.SHORT,
                    "Combines adventure with familiar elements like gardens",
                    0.7f
                )
            )
            
            StoryTarget.ELEMENTARY -> listOf(
                StoryRecommendation(
                    "A group of kids who find a time machine in their school basement",
                    StoryGenre.SCIENCE_FICTION,
                    targetAudience,
                    StoryLength.MEDIUM,
                    "Exciting time travel adventure with educational elements",
                    0.7f
                ),
                StoryRecommendation(
                    "A young detective who solves mysteries around their neighborhood",
                    StoryGenre.MYSTERY,
                    targetAudience,
                    StoryLength.MEDIUM,
                    "Engaging mystery that develops problem-solving skills",
                    0.7f
                )
            )
            
            StoryTarget.MIDDLE_SCHOOL -> listOf(
                StoryRecommendation(
                    "A teenager discovers they can communicate with animals and must save the local wildlife preserve",
                    StoryGenre.ADVENTURE,
                    targetAudience,
                    StoryLength.LONG,
                    "Combines supernatural elements with environmental themes",
                    0.7f
                ),
                StoryRecommendation(
                    "Friends start a band and navigate the challenges of middle school while preparing for a talent show",
                    StoryGenre.FRIENDSHIP,
                    targetAudience,
                    StoryLength.MEDIUM,
                    "Relatable social situations with creative expression",
                    0.7f
                )
            )
            
            StoryTarget.HIGH_SCHOOL -> listOf(
                StoryRecommendation(
                    "A high school student discovers their family's involvement in a historical mystery",
                    StoryGenre.HISTORICAL,
                    targetAudience,
                    StoryLength.LONG,
                    "Combines personal growth with historical learning",
                    0.7f
                ),
                StoryRecommendation(
                    "Students create an AI for a science fair that begins to show signs of consciousness",
                    StoryGenre.SCIENCE_FICTION,
                    targetAudience,
                    StoryLength.LONG,
                    "Explores ethics and technology relevant to their generation",
                    0.7f
                )
            )
            
            StoryTarget.ADULT -> listOf(
                StoryRecommendation(
                    "A professional discovers a conspiracy at their workplace that threatens their entire industry",
                    StoryGenre.MYSTERY,
                    targetAudience,
                    StoryLength.LONG,
                    "Complex plot with workplace and ethical dilemmas",
                    0.7f
                ),
                StoryRecommendation(
                    "A family inherits an old house and uncovers letters that reveal family secrets spanning generations",
                    StoryGenre.FAMILY,
                    targetAudience,
                    StoryLength.LONG,
                    "Multi-generational story exploring family dynamics",
                    0.7f
                )
            )
        }
        
        return fallbacks.take(count)
    }
}

data class ReadingHistoryAnalysis(
    val totalStoriesRead: Int,
    val favoriteGenres: Map<StoryGenre, Float>,
    val averageLength: Float,
    val readingFrequency: ReadingFrequency,
    val commonThemes: List<String>,
    val lastReadGenres: List<StoryGenre>
)

enum class ReadingFrequency {
    DAILY, WEEKLY, OCCASIONAL
}