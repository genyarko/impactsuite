package com.example.mygemma3n.feature.story

import android.content.Context
import com.example.mygemma3n.data.GeminiApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
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
    @ApplicationContext private val context: Context
) {

    /**
     * Requests a set of visual descriptions for each page in the provided [story].
     * Returns a single string containing all page descriptions, or null if
     * generation fails. The call runs on an IO dispatcher.
     */
    suspend fun generateImageScript(story: Story): String? = withContext(Dispatchers.IO) {
        try {
            require(geminiApiService.isInitialized()) { "GeminiApiService not initialized" }
            val prompt = createImageScriptPrompt(story)
            val response = geminiApiService.generateTextComplete(prompt, "story")
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
    private suspend fun createRealImageUrl(page: Int, desc: String): String = withContext(Dispatchers.IO) {
        val bytes = geminiApiService.generateImageBytes(desc)
        val file = File.createTempFile("story_page_${page}_", ".png", context.cacheDir)
        file.writeBytes(bytes)
        file.toURI().toString()
    }

    /**
     * Coordinates the end‑to‑end process of generating images for an entire story.
     * If the target audience does not warrant images or if any part of the
     * pipeline fails, the story is returned with [hasImages] set accordingly and
     * without mutating the original object. Only pages for which an image and
     * description are successfully produced will have their [StoryPage.imageUrl]
     * and [StoryPage.imageDescription] fields populated.
     */
    suspend fun generateImagesForStory(story: Story): Story? = withContext(Dispatchers.IO) {
        if (!shouldGenerateImages(story.targetAudience)) {
            return@withContext story.copy(hasImages = false)
        }
        val imageScript = generateImageScript(story)
        if (imageScript.isNullOrBlank()) {
            return@withContext story.copy(hasImages = false)
        }
        val descriptions = parseImageScript(imageScript, story.totalPages)
        val updatedPages = story.pages.mapIndexed { index, page ->
            val desc = descriptions.getOrNull(index)
            if (desc != null) {
                try {
                    val uri = createRealImageUrl(index + 1, desc)
                    page.copy(imageUrl = uri, imageDescription = desc)
                } catch (e: Exception) {
                    Timber.e(e, "Failed to generate image for page ${index + 1}")
                    page
                }
            } else {
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
        val regex = Regex("Page\\s+(\\d+):\\s+(.*?)(?=(Page\\s+\\d+:)|$)", RegexOption.DOT_MATCHES_ALL)
        regex.findAll(script).forEach { matchResult ->
            val description = matchResult.groups[2]?.value?.trim()
            if (!description.isNullOrBlank()) {
                results.add(description)
            }
        }
        return results.take(totalPages)
    }
}