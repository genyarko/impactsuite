package com.mygemma3n.aiapp.feature.story

import android.content.Context
import com.mygemma3n.aiapp.data.GeminiApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.io.File
import android.util.Base64
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext

/**
 * Responsible for generating visual descriptions for story pages and producing
 * corresponding image files. This class delegates text and image generation to
 * [GeminiApiService] and handles parsing and file I/O.
 */
@Singleton
class StoryImageGenerator @Inject constructor(
    private val geminiApiService: GeminiApiService,
    private val openAIService: com.mygemma3n.aiapp.feature.chat.OpenAIChatService,
    private val settingsRepository: com.mygemma3n.aiapp.domain.repository.SettingsRepository,
    @ApplicationContext private val context: Context
) {

    private suspend fun shouldUseOpenAI(): Boolean {
        return try {
            val modelProvider = settingsRepository.modelProviderFlow.first()
            modelProvider == "openai" && openAIService.isInitialized()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Requests a set of visual descriptions for each page in the provided [story].
     * Returns a single string containing all page descriptions, or null if
     * generation fails. The call runs on an IO dispatcher.
     */
    suspend fun generateImageScript(story: Story, usingOpenAI: Boolean = false): String? = withContext(Dispatchers.IO) {
        try {
            val prompt = createImageScriptPrompt(story)
            
            val response = if (usingOpenAI) {
                Timber.d("Using OpenAI for image script generation")
                openAIService.generateStoryContent(prompt, maxTokens = 8000, temperature = 1.0f)
            } else {
                Timber.d("Using Gemini for image script generation")
                require(geminiApiService.isInitialized()) { "GeminiApiService not initialized" }
                geminiApiService.generateTextComplete(prompt, "story")
            }
            
            Timber.d("Generated Image Script Response: $response")
            if (response.isNotBlank()) response.trim() else {
                Timber.e("Image script response was blank.")
                null
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to generate image script for story: ${story.id}")
            null
        }
    }

    /**
     * Builds a prompt instructing the language model to generate detailed visual
     * descriptions for each page of a story. The prompt includes contextual
     * information about the story's genre and target audience to influence the
     * style of the illustrations.
     */
    private fun createImageScriptPrompt(story: Story): String {
        val audienceContext = when (story.targetAudience) {
            StoryTarget.KINDERGARTEN, StoryTarget.ELEMENTARY ->
                "bright, colorful, child‑friendly illustrations suitable for young children"
            StoryTarget.MIDDLE_SCHOOL ->
                "engaging, detailed illustrations suitable for middle school readers"
            StoryTarget.HIGH_SCHOOL, StoryTarget.ADULT ->
                "sophisticated, artistic illustrations suitable for older readers"
        }

        val genreContext = when (story.genre) {
            StoryGenre.ADVENTURE       -> "dynamic action scenes, exciting landscapes"
            StoryGenre.FANTASY         -> "magical creatures, enchanted settings, mystical elements"
            StoryGenre.MYSTERY         -> "atmospheric, shadowy scenes with intriguing details"
            StoryGenre.SCIENCE_FICTION -> "futuristic technology, space scenes, advanced settings"
            StoryGenre.HISTORICAL      -> "historically accurate clothing, architecture, and environments"
            StoryGenre.FRIENDSHIP      -> "warm, interpersonal scenes showing connection"
            StoryGenre.FAMILY          -> "cozy, domestic scenes showing family bonds"
            StoryGenre.EDUCATIONAL     -> "clear, informative illustrations supporting learning"
            StoryGenre.FAIRYTALE       -> "classical fairytale imagery, castles, magical elements"
            StoryGenre.COMEDY          -> "expressive, humorous characters and situations"
        }

        val pagesSection = story.pages.mapIndexed { index, page ->
            val titleSuffix = page.title?.let { " - $it" } ?: ""
            "Page ${index + 1}$titleSuffix: ${page.content}"
        }.joinToString(separator = "\n\n")

        return """
        You are an expert visual storytelling consultant. Create detailed visual descriptions for each page of this story that could be used to generate illustrations.

        STORY DETAILS:
        Title: ${story.title}
        Genre: ${story.genre.name} ($genreContext)
        Target Audience: ${story.targetAudience.name} ($audienceContext)
        Characters: ${story.characters.joinToString(", ")}
        Setting: ${story.setting}

        STORY PAGES:
        $pagesSection

        INSTRUCTIONS:
        For each page, create a detailed visual description that captures:
        1. The key scene or moment from that page
        2. Character appearances, expressions, and positions
        3. Setting details and atmosphere
        4. Color palette and mood
        5. Composition and visual focus

        Each description should be 2-3 sentences and suitable for generating illustrations.
        Make descriptions age‑appropriate for ${story.targetAudience.name.lowercase().replace('_', ' ')} readers.

        FORMAT YOUR RESPONSE AS:
        Page 1: [Visual description for page 1]
        Page 2: [Visual description for page 2]
        [Continue for all ${story.totalPages} pages]

        Generate the visual descriptions now:
        """.trimIndent()
    }

    /**
     * Determines whether images should be generated for a given target audience. Only
     * younger audiences (kindergarten through middle school) receive pictures.
     */
    fun shouldGenerateImages(targetAudience: StoryTarget): Boolean = when (targetAudience) {
        StoryTarget.KINDERGARTEN, StoryTarget.ELEMENTARY, StoryTarget.MIDDLE_SCHOOL -> true
        StoryTarget.HIGH_SCHOOL, StoryTarget.ADULT -> false
    }

    /**
     * Fallback method to construct a placeholder image URL. In the absence of
     * generated images, UI code may use this to display a basic placeholder.
     */
    fun createPlaceholderImageUrl(pageNumber: Int, description: String): String {
        return "https://via.placeholder.com/400x300/4CAF50/FFFFFF?text=Page+$pageNumber"
    }

    /**
     * Generates an image for a single page description and returns a file URI
     * pointing to a temporary PNG on disk. The file is created in the app's
     * cache directory so image loaders like Coil can access it.
     */
    private suspend fun createRealImageUrl(page: Int, desc: String, usingOpenAI: Boolean = false): String = withContext(Dispatchers.IO) {
        val bytes = if (usingOpenAI) {
            generateImageWithDALLE(desc)
        } else {
            geminiApiService.generateImageBytes(desc)
        }
        
        val file = File.createTempFile("story_page_${page}_", ".png", context.cacheDir)
        file.writeBytes(bytes)
        file.toURI().toString()
    }

    /**
     * Sanitizes description to avoid DALL-E content policy violations
     */
    private fun sanitizeDescriptionForDALLE(description: String): String {
        var sanitized = description
        
        // Replace potentially problematic phrases
        val replacements = mapOf(
            "tangled and stuck in the web" to "standing near a colorful web pattern",
            "arms trapped in" to "arms reaching toward",
            "trapped in delicate, tight threads" to "surrounded by gentle, sparkly threads",
            "face a mix of fear and shame" to "face showing surprise and curiosity",
            "stuck in" to "standing near",
            "trapped" to "surrounded by",
            "fear and shame" to "surprise and wonder",
            "crying" to "looking thoughtful",
            "distress" to "concern",
            "frightened" to "curious",
            "scared" to "surprised"
        )
        
        for ((problematic, safe) in replacements) {
            sanitized = sanitized.replace(problematic, safe, ignoreCase = true)
        }
        
        return sanitized
    }

    /**
     * Generates image bytes using DALL-E API with timeout and retry logic
     */
    private suspend fun generateImageWithDALLE(description: String): ByteArray {
        val maxRetries = 2 // Allow one retry for better reliability
        var lastException: Exception? = null
        
        repeat(maxRetries) { attempt ->
            try {
                // Sanitize description to avoid content policy violations
                val sanitizedDescription = sanitizeDescriptionForDALLE(description)
                
                // Simplified prompt for faster generation
                val imagePrompt = "Simple children's book illustration: $sanitizedDescription. Cartoon style, bright colors."
                Timber.d("DALL-E attempt ${attempt + 1}/$maxRetries for image generation")
                
                // Use longer timeout per attempt to allow DALL-E sufficient time
                val result = withTimeoutOrNull(300_000) { // 5 minute timeout per attempt (matches HTTP client)
                    openAIService.generateImageWithDALLE(
                        prompt = imagePrompt,
                        size = "1024x1024", // Use supported DALL-E 3 size
                        quality = "standard", // Standard quality is faster than HD
                        style = "natural" // Natural style is often faster than vivid
                    )
                }
                
                if (result != null) {
                    return result
                } else {
                    throw Exception("DALL-E request timed out after 5 minutes")
                }
            } catch (e: Exception) {
                lastException = e
                val errorType = when {
                    e is kotlinx.coroutines.TimeoutCancellationException -> "timeout"
                    e.message?.contains("content_policy_violation") == true -> "content policy"
                    e.message?.contains("rate_limit") == true -> "rate limit"
                    else -> "API error"
                }
                Timber.w(e, "DALL-E attempt ${attempt + 1} failed ($errorType)${if (attempt < maxRetries - 1) ", retrying..." else ""}")
                if (attempt < maxRetries - 1) {
                    val delayMs = when (errorType) {
                        "rate limit" -> 30000L // 30s delay for rate limits
                        "timeout" -> 5000L // 5s delay for timeouts
                        else -> 2000L // 2s delay for other errors
                    }
                    delay(delayMs)
                }
            }
        }
        
        Timber.e(lastException, "Failed to generate image with DALL-E after $maxRetries attempts")
        throw lastException ?: Exception("Unknown error in DALL-E image generation")
    }

    /**
     * Coordinates the end‑to‑end process of generating images for an entire story.
     * If the target audience does not warrant images or if any part of the
     * pipeline fails, the story is returned with [hasImages] set accordingly and
     * without mutating the original object. Only pages for which an image and
     * description are successfully produced will have their [StoryPage.imageUrl]
     * and [StoryPage.imageDescription] fields populated.
     */
    suspend fun generateImagesForStory(story: Story, usingOpenAI: Boolean = false): Story? = withContext(Dispatchers.IO) {
        if (!shouldGenerateImages(story.targetAudience)) {
            return@withContext story.copy(hasImages = false)
        }
        val imageScript = generateImageScript(story, usingOpenAI)
        if (imageScript.isNullOrBlank()) {
            return@withContext story.copy(hasImages = false)
        }
        val descriptions = parseImageScript(imageScript, story.totalPages)
        Timber.d("Parsed ${descriptions.size} image descriptions from script")
        
        // Circuit breaker pattern: allow some failures before stopping
        var consecutiveFailures = 0
        val maxConsecutiveFailures = 3 // Allow more failures for DALL-E which can be slow
        var circuitBreakerOpen = false
        
        val updatedPages = story.pages.mapIndexed { index, page ->
            val desc = descriptions.getOrNull(index)
            if (desc != null) {
                // Check circuit breaker
                if (circuitBreakerOpen) {
                    Timber.w("Circuit breaker open, skipping image generation for page ${index + 1}")
                    return@mapIndexed page.copy(imageDescription = desc)
                }
                
                try {
                    Timber.d("Generating image for page ${index + 1} using ${if (usingOpenAI) "DALL-E" else "Gemini"}")
                    
                    var uri: String? = null
                    var usedFallback = false
                    
                    // Try the primary method first
                    try {
                        // Increased timeout for individual image generation to allow DALL-E sufficient time
                        uri = withTimeoutOrNull(600_000) { // 10 minute timeout per image (allows for 2 retries at 5min each)
                            createRealImageUrl(index + 1, desc, usingOpenAI)
                        }
                    } catch (e: Exception) {
                        // Check if this is a content policy violation and fallback to Gemini
                        if (usingOpenAI && e.message?.contains("content_policy_violation") == true) {
                            Timber.w("DALL-E content policy violation for page ${index + 1}, falling back to Gemini")
                            try {
                                uri = withTimeoutOrNull(180_000) { // 3 minute timeout for Gemini fallback
                                    createRealImageUrl(index + 1, desc, false) // Use Gemini
                                }
                                usedFallback = true
                            } catch (fallbackException: Exception) {
                                Timber.e(fallbackException, "Gemini fallback also failed for page ${index + 1}")
                                throw e // Throw original exception if fallback fails
                            }
                        } else {
                            throw e // Re-throw if not a content policy issue
                        }
                    }
                    
                    if (uri != null) {
                        val method = if (usedFallback) "Gemini (fallback)" else if (usingOpenAI) "DALL-E" else "Gemini"
                        Timber.d("Successfully generated image for page ${index + 1} using $method: $uri")
                        consecutiveFailures = 0 // Reset failure counter on success
                        page.copy(imageUrl = uri, imageDescription = desc)
                    } else {
                        consecutiveFailures++
                        Timber.w("Image generation timed out for page ${index + 1} (failure $consecutiveFailures/$maxConsecutiveFailures)")
                        if (consecutiveFailures >= maxConsecutiveFailures) {
                            circuitBreakerOpen = true
                            Timber.w("Opening circuit breaker after $maxConsecutiveFailures consecutive failures - stopping image generation for remaining pages")
                        }
                        page.copy(imageDescription = desc) // Keep description but no image
                    }
                } catch (e: Exception) {
                    consecutiveFailures++
                    Timber.e(e, "Failed to generate image for page ${index + 1} (failure $consecutiveFailures/$maxConsecutiveFailures)")
                    if (consecutiveFailures >= maxConsecutiveFailures) {
                        circuitBreakerOpen = true
                        Timber.w("Opening circuit breaker after $maxConsecutiveFailures consecutive failures - stopping image generation for remaining pages")
                    }
                    page.copy(imageDescription = desc) // Keep description but no image
                }
            } else {
                Timber.w("No description found for page ${index + 1}")
                page
            }
        }
        val anyImages = updatedPages.any { it.imageUrl != null }
        story.copy(
            pages = updatedPages,
            hasImages = anyImages,
            imageGenerationScript = imageScript
        )
    }

    /**
     * Extracts page‑level descriptions from the raw script returned by the
     * language model. The script is expected to follow the format:
     *   Page 1: description…\nPage 2: description…\n...
     * Only the first [totalPages] descriptions are returned.
     */
    private fun parseImageScript(script: String, totalPages: Int): List<String> {
        val results = mutableListOf<String>()
        
        // Try to parse as JSON first (OpenAI format)
        if (script.trim().startsWith("{") || script.contains("\"Page")) {
            try {
                Timber.d("Attempting to parse image script as JSON")
                val cleanScript = script.replace("```json", "").replace("```", "").trim()
                val jsonStart = cleanScript.indexOf('{')
                val jsonEnd = cleanScript.lastIndexOf('}')
                
                if (jsonStart != -1 && jsonEnd != -1 && jsonEnd > jsonStart) {
                    val jsonString = cleanScript.substring(jsonStart, jsonEnd + 1)
                    Timber.d("Extracted JSON string length: ${jsonString.length}")
                    val gson = com.google.gson.Gson()
                    @Suppress("UNCHECKED_CAST")
                    val jsonData = gson.fromJson(jsonString, Map::class.java) as Map<String, Any>
                    
                    Timber.d("Parsed JSON with ${jsonData.size} keys: ${jsonData.keys}")
                    
                    // Extract descriptions in order
                    for (i in 1..totalPages) {
                        val pageKey = "Page $i"
                        val description = jsonData[pageKey]?.toString()?.trim()
                        if (!description.isNullOrBlank()) {
                            results.add(description)
                            Timber.d("Added description for $pageKey: ${description.take(50)}...")
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "Failed to parse JSON image script, falling back to regex parsing")
            }
        }
        
        // Fallback to regex parsing (Gemini format)
        if (results.isEmpty()) {
            val regex = Regex("Page\\s+(\\d+):\\s+(.*?)(?=(Page\\s+\\d+:)|$)", RegexOption.DOT_MATCHES_ALL)
            regex.findAll(script).forEach { matchResult ->
                val description = matchResult.groups[2]?.value?.trim()
                if (!description.isNullOrBlank()) {
                    results.add(description)
                }
            }
        }
        
        return results.take(totalPages)
    }
}