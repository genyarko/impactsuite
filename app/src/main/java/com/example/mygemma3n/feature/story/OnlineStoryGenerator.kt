package com.example.mygemma3n.feature.story

import com.example.mygemma3n.data.GeminiApiService
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OnlineStoryGenerator @Inject constructor(
    private val geminiApiService: GeminiApiService,
    private val openAIService: com.example.mygemma3n.feature.chat.OpenAIChatService,
    private val settingsRepository: com.example.mygemma3n.domain.repository.SettingsRepository,
    private val gson: Gson,
    private val storyImageGenerator: StoryImageGenerator,
    private val difficultyAdapter: StoryDifficultyAdapter
) {
    
    // Create a lenient Gson instance for parsing potentially malformed JSON
    private val lenientGson = gson.newBuilder()
        .setLenient()
        .create()

    private suspend fun shouldUseOpenAI(): Boolean {
        return try {
            val modelProvider = settingsRepository.modelProviderFlow.first()
            modelProvider == "openai" && openAIService.isInitialized()
        } catch (e: Exception) {
            false
        }
    }

    suspend fun generateStoryOnline(
        request: StoryRequest,
        onPageGenerated: (Int, Int) -> Unit = { _, _ -> } // Callback for progress updates
    ): Story? = withContext(Dispatchers.IO) {
        
        return@withContext try {
            val targetPageCount = request.exactPageCount ?: when (request.length) {
                StoryLength.SHORT -> 5
                StoryLength.MEDIUM -> 10
                StoryLength.LONG -> 20
            }
            
            val usingOpenAI = shouldUseOpenAI()
            val prompt = if (usingOpenAI) {
                createOpenAIStoryPrompt(request, targetPageCount)
            } else {
                createStoryPrompt(request, targetPageCount)
            }
            
            withTimeoutOrNull(180_000) { // 180 second timeout for entire story (allows time for image generation)
                val response = if (usingOpenAI) {
                    openAIService.generateStoryContent(prompt, maxTokens = 6000, temperature = 0.8f)
                } else {
                    require(geminiApiService.isInitialized()) { "GeminiApiService not initialized" }
                    geminiApiService.generateTextComplete(prompt, "story")
                }
                
                val story = parseStoryResponse(response, request, targetPageCount, usingOpenAI)
                
                // Generate images if story was created successfully
                val finalStory = if (story != null) {
                    // Simulate progress updates for better UX
                    story.pages.forEachIndexed { index, _ ->
                        onPageGenerated(index + 1, story.totalPages)
                        delay(100) // Small delay to show progress
                    }
                    
                    // Generate images for appropriate age groups
                    if (storyImageGenerator.shouldGenerateImages(story.targetAudience)) {
                        storyImageGenerator.generateImagesForStory(story, usingOpenAI)
                    } else {
                        story
                    }
                } else {
                    null
                }
                
                finalStory
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to generate story online")
            null
        }
    }

    private fun createStoryPrompt(request: StoryRequest, targetPageCount: Int): String {
        val difficultySettings = difficultyAdapter.getDifficultySettings(request.targetAudience)
        
        val audienceContext = when (request.targetAudience) {
            StoryTarget.KINDERGARTEN -> "ages 3-5, simple vocabulary, very short sentences"
            StoryTarget.ELEMENTARY -> "ages 6-10, basic vocabulary, clear simple sentences"
            StoryTarget.MIDDLE_SCHOOL -> "ages 11-13, intermediate vocabulary, engaging plots"
            StoryTarget.HIGH_SCHOOL -> "ages 14-18, advanced vocabulary, complex themes"
            StoryTarget.ADULT -> "adult audience, sophisticated language and themes"
        }
        
        val difficultyInstructions = buildDifficultyInstructions(difficultySettings)

        val genreContext = when (request.genre) {
            StoryGenre.ADVENTURE -> "exciting journey with challenges and discoveries"
            StoryGenre.FANTASY -> "magical elements, mythical creatures, imaginary worlds"
            StoryGenre.MYSTERY -> "puzzles to solve, clues to uncover, suspenseful atmosphere"
            StoryGenre.SCIENCE_FICTION -> "futuristic technology, space exploration, scientific concepts"
            StoryGenre.HISTORICAL -> "set in the past, historically accurate details"
            StoryGenre.FRIENDSHIP -> "relationships, loyalty, working together"
            StoryGenre.FAMILY -> "family bonds, love, support, togetherness"
            StoryGenre.EDUCATIONAL -> "learning opportunities, factual information woven in"
            StoryGenre.FAIRYTALE -> "classic fairytale structure, moral lessons, magical elements"
            StoryGenre.COMEDY -> "humorous situations, funny characters, light-hearted tone"
        }

        val charactersText = if (request.characters.isNotEmpty()) {
            "Characters to include: ${request.characters.joinToString(", ")}"
        } else {
            "Create memorable, age-appropriate characters"
        }

        val settingText = if (request.setting.isNotEmpty()) {
            "Setting: ${request.setting}"
        } else {
            "Choose an engaging setting that fits the genre"
        }

        val themeText = if (request.theme.isNotEmpty()) {
            "Theme/Message: ${request.theme}"
        } else {
            "Include a positive, age-appropriate message"
        }

        return """
        You are a skilled children's story writer. Create an engaging ${request.genre.name.lowercase().replace('_', ' ')} story for ${request.targetAudience.name.lowercase().replace('_', ' ')} readers.

        STORY REQUIREMENTS:
        - User Prompt: "${request.prompt}"
        - Genre: ${request.genre.name} ($genreContext)
        - Target Audience: ${request.targetAudience.name} ($audienceContext)
        - Length: Exactly $targetPageCount pages
        - $charactersText
        - $settingText
        - $themeText

        WRITING GUIDELINES:
        - Each page should be substantial but appropriate for the target audience
        - Create a compelling beginning, engaging middle, and satisfying conclusion
        - Use vivid but age-appropriate descriptions
        - Include dialogue to bring characters to life
        - Ensure smooth transitions between pages
        - Make each page end with a natural stopping point

        DIFFICULTY REQUIREMENTS:
        $difficultyInstructions

        OUTPUT FORMAT:
        Return ONLY valid JSON in this exact format:
        {
          "title": "Engaging Story Title",
          "pages": [
            {
              "pageNumber": 1,
              "title": "Page 1 Title (optional)",
              "content": "Full content for page 1. This should be substantial and engaging text appropriate for the target audience."
            },
            {
              "pageNumber": 2,
              "title": "Page 2 Title (optional)", 
              "content": "Full content for page 2. Continue the story naturally from page 1."
            }
            // ... continue for all $targetPageCount pages
          ],
          "characters": ["Character 1", "Character 2"],
          "setting": "Brief description of the main setting"
        }

        Generate the complete story now:
        """.trimIndent()
    }

    private fun createOpenAIStoryPrompt(request: StoryRequest, targetPageCount: Int): String {
        val difficultySettings = difficultyAdapter.getDifficultySettings(request.targetAudience)
        
        val audienceContext = when (request.targetAudience) {
            StoryTarget.KINDERGARTEN -> "ages 3-5, simple vocabulary, very short sentences, lots of repetition"
            StoryTarget.ELEMENTARY -> "ages 6-10, basic vocabulary, clear simple sentences, engaging plots"
            StoryTarget.MIDDLE_SCHOOL -> "ages 11-13, intermediate vocabulary, more complex themes"
            StoryTarget.HIGH_SCHOOL -> "ages 14-18, advanced vocabulary, sophisticated themes"
            StoryTarget.ADULT -> "adult audience, sophisticated language and complex themes"
        }
        
        val genreContext = when (request.genre) {
            StoryGenre.ADVENTURE -> "exciting journey with challenges, discoveries, and heroic moments"
            StoryGenre.FANTASY -> "magical elements, mythical creatures, enchanted worlds, wonder and magic"
            StoryGenre.MYSTERY -> "puzzles to solve, clues to uncover, suspenseful atmosphere, detective work"
            StoryGenre.SCIENCE_FICTION -> "futuristic technology, space exploration, scientific concepts, innovation"
            StoryGenre.HISTORICAL -> "accurate historical setting, period details, cultural authenticity"
            StoryGenre.FRIENDSHIP -> "relationships, loyalty, cooperation, emotional connections"
            StoryGenre.FAMILY -> "family bonds, love, support, togetherness, family values"
            StoryGenre.EDUCATIONAL -> "learning opportunities, factual information, educational content"
            StoryGenre.FAIRYTALE -> "classic fairytale structure, moral lessons, magical elements, happy endings"
            StoryGenre.COMEDY -> "humorous situations, funny characters, light-hearted tone, laughter"
        }

        val charactersText = if (request.characters.isNotEmpty()) {
            "Include these characters: ${request.characters.joinToString(", ")}"
        } else {
            "Create memorable, relatable characters appropriate for the target audience"
        }

        val settingText = if (request.setting.isNotEmpty()) {
            "Setting: ${request.setting}"
        } else {
            "Create an engaging setting that perfectly fits the ${request.genre.name.lowercase()} genre"
        }

        val themeText = if (request.theme.isNotEmpty()) {
            "Central theme: ${request.theme}"
        } else {
            "Include positive, age-appropriate themes and life lessons"
        }

        val difficultyInstructions = buildDifficultyInstructions(difficultySettings)

        return """
        Create an engaging, high-quality ${request.genre.name.lowercase().replace('_', ' ')} story for ${request.targetAudience.name.lowercase().replace('_', ' ')} readers.

        **STORY SPECIFICATIONS:**
        - User's Creative Prompt: "${request.prompt}"
        - Genre: ${request.genre.name} ($genreContext)
        - Target Audience: ${request.targetAudience.name} ($audienceContext)
        - Required Length: Exactly $targetPageCount pages
        - $charactersText
        - $settingText
        - $themeText

        **WRITING GUIDELINES:**
        - Create compelling, age-appropriate content with strong narrative flow
        - Each page should contain substantial, engaging content (not just a few sentences)
        - Develop interesting characters with clear motivations and growth
        - Use vivid, sensory descriptions to bring scenes to life
        - Include meaningful dialogue that advances the story
        - Ensure smooth transitions between pages with natural cliffhangers
        - Build to a satisfying, emotionally resonant conclusion

        **DIFFICULTY & LANGUAGE REQUIREMENTS:**
        $difficultyInstructions

        **CRITICAL FORMATTING REQUIREMENT:**
        You MUST return your response as a properly formatted JSON object with this EXACT structure:

        ```json
        {
          "title": "Creative, Engaging Story Title",
          "pages": [
            {
              "pageNumber": 1,
              "title": "Chapter 1 Title (optional but recommended)",
              "content": "Rich, substantial content for page 1. This should be engaging, descriptive text that draws the reader in and establishes the story world, characters, and initial situation. Make this compelling and age-appropriate."
            },
            {
              "pageNumber": 2,
              "title": "Chapter 2 Title (optional but recommended)",
              "content": "Continuing the story naturally from page 1. Develop the plot, characters, and conflict. Each page should feel complete but also connect seamlessly to the next."
            }
            // Continue this pattern for all $targetPageCount pages
          ],
          "characters": ["Main Character Name", "Supporting Character Name", "Other Characters"],
          "setting": "Detailed description of the primary story setting and world"
        }
        ```

        **IMPORTANT NOTES:**
        - The JSON must be valid and parseable
        - Each page should be substantial (appropriate for the target audience)
        - Ensure the story has a clear beginning, middle, and end
        - Make the content engaging and memorable
        - Include appropriate themes and lessons for the age group

        Create the complete $targetPageCount-page story now:
        """.trimIndent()
    }

    private fun parseStoryResponse(
        response: String,
        request: StoryRequest,
        targetPageCount: Int,
        usingOpenAI: Boolean = false
    ): Story? {
        return try {
            // Try multiple approaches to parse the response
            val story = parseWithGson(response, request) 
                ?: parseWithManualExtraction(response, request) 
                ?: createFallbackStory(request, targetPageCount)
            
            story
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse story response: $response")
            // Create a fallback story to avoid null return
            createFallbackStory(request, targetPageCount)
        }
    }

    private fun parseWithGson(response: String, request: StoryRequest): Story? {
        return try {
            val jsonString = extractStoryJson(response)
            @Suppress("UNCHECKED_CAST")
            val jsonData = lenientGson.fromJson(jsonString, Map::class.java) as Map<String, Any>

            val title = jsonData["title"]?.toString() ?: "Untitled Story"

            @Suppress("UNCHECKED_CAST")
            val pagesData = jsonData["pages"] as? List<Map<String, Any>>
                ?: return null

            val pages = pagesData.mapIndexed { index, pageData ->
                StoryPage(
                    pageNumber = index + 1,
                    title = pageData["title"]?.toString(),
                    content = pageData["content"]?.toString() ?: ""
                )
            }

            if (pages.isEmpty()) return null

            @Suppress("UNCHECKED_CAST")
            val charactersFromResponse = jsonData["characters"] as? List<String>
                ?: request.characters

            val settingFromResponse = jsonData["setting"]?.toString() ?: request.setting

            Story(
                title = title,
                genre = request.genre,
                targetAudience = request.targetAudience,
                pages = pages,
                totalPages = pages.size,
                prompt = request.prompt,
                characters = charactersFromResponse,
                setting = settingFromResponse,
                hasImages = false,
                imageGenerationScript = null
            )
        } catch (e: Exception) {
            Timber.w(e, "Gson parsing failed, trying manual extraction")
            null
        }
    }

    private fun parseWithManualExtraction(response: String, request: StoryRequest): Story? {
        return try {
            // Extract title manually
            val titleMatch = Regex("\"title\"\\s*:\\s*\"([^\"]+)\"").find(response)
            val title = titleMatch?.groupValues?.get(1) ?: "Generated Story"

            // Extract pages manually
            val pageMatches = Regex("\"pageNumber\"\\s*:\\s*(\\d+)[^}]*\"content\"\\s*:\\s*\"([^\"]+(?:\\\\.[^\"]*)*?)\"").findAll(response)
            
            val pages = pageMatches.mapIndexed { index, match ->
                val content = match.groupValues[2]
                    .replace("\\\"", "\"")  // Unescape quotes
                    .replace("\\n", "\n")   // Fix newlines
                
                StoryPage(
                    pageNumber = index + 1,
                    content = content
                )
            }.toList()

            if (pages.isEmpty()) return null

            // Extract characters manually
            val charactersMatch = Regex("\"characters\"\\s*:\\s*\\[([^\\]]+)\\]").find(response)
            val characters = charactersMatch?.groupValues?.get(1)
                ?.split(",")
                ?.map { it.trim().removeSurrounding("\"") }
                ?: request.characters

            // Extract setting manually  
            val settingMatch = Regex("\"setting\"\\s*:\\s*\"([^\"]+)\"").find(response)
            val setting = settingMatch?.groupValues?.get(1) ?: request.setting

            Story(
                title = title,
                genre = request.genre,
                targetAudience = request.targetAudience,
                pages = pages,
                totalPages = pages.size,
                prompt = request.prompt,
                characters = characters,
                setting = setting,
                hasImages = false,
                imageGenerationScript = null
            )
        } catch (e: Exception) {
            Timber.w(e, "Manual extraction also failed")
            null
        }
    }

    private fun extractStoryJson(response: String): String {
        val cleaned = response
            .replace("```json", "")
            .replace("```", "")
            .replace("**JSON:**", "")
            .replace("Here's the story:", "")
            .replace("Here is the story:", "")
            .replace("JSON:", "")
            .trim()

        val startIndex = cleaned.indexOf('{')
        val endIndex = cleaned.lastIndexOf('}')

        if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
            val jsonString = cleaned.substring(startIndex, endIndex + 1)
            return cleanJsonString(jsonString)
        }

        return cleaned
    }

    private fun cleanJsonString(jsonString: String): String {
        var cleaned = jsonString
            .replace(",\n]", "\n]")    // Remove trailing commas before array end
            .replace(", ]", " ]")      // Remove trailing commas before array end
            .replace(",]", "]")        // Remove trailing commas before array end
            .replace(",\n}", "\n}")    // Remove trailing commas in objects
            .replace(", }", " }")      // Remove trailing commas in objects
            .replace(",}", "}")        // Remove trailing commas in objects
            .replace("\\n", "\n")      // Fix escaped newlines
            .trim()

        // More robust approach: Fix quotes within string values
        // This regex finds string values and escapes any unescaped quotes within them
        val contentFieldRegex = Regex("\"content\"\\s*:\\s*\"([^\"]*(?:\\\\.[^\"]*)*)\"", RegexOption.DOT_MATCHES_ALL)
        cleaned = contentFieldRegex.replace(cleaned) { matchResult ->
            val content = matchResult.groupValues[1]
            val fixedContent = content
                .replace("\"", "\\\"")  // Escape all quotes
                .replace("\\\\\"", "\\\"") // But don't double-escape already escaped quotes
            "\"content\": \"$fixedContent\""
        }

        return cleaned
    }

    private fun createFallbackStory(request: StoryRequest, targetPageCount: Int): Story {
        val fallbackPages = when (request.targetAudience) {
            StoryTarget.KINDERGARTEN -> createKindergartenFallback(request.genre)
            StoryTarget.ELEMENTARY -> createElementaryFallback(request.genre)
            StoryTarget.MIDDLE_SCHOOL -> createMiddleSchoolFallback(request.genre)
            StoryTarget.HIGH_SCHOOL -> createHighSchoolFallback(request.genre)
            StoryTarget.ADULT -> createAdultFallback(request.genre)
        }.take(targetPageCount)

        return Story(
            title = "A ${request.genre.name.lowercase().replace('_', ' ')} Story",
            genre = request.genre,
            targetAudience = request.targetAudience,
            pages = fallbackPages,
            totalPages = fallbackPages.size,
            prompt = request.prompt,
            characters = request.characters.ifEmpty { listOf("Main Character") },
            setting = request.setting.ifEmpty { "A magical place" },
            hasImages = false,
            imageGenerationScript = null
        )
    }

    private fun createKindergartenFallback(genre: StoryGenre): List<StoryPage> {
        return when (genre) {
            StoryGenre.ADVENTURE -> listOf(
                StoryPage(1, "Once upon a time, there was a little bunny named Pip. Pip loved to explore the big garden behind his house."),
                StoryPage(2, "One day, Pip found a shiny key under a rose bush. 'I wonder what this opens?' he said to himself."),
                StoryPage(3, "Pip followed a winding path and found a tiny door in an old oak tree. The key fit perfectly! Inside was the most beautiful fairy garden Pip had ever seen. The end.")
            )
            else -> listOf(
                StoryPage(1, "There once was a kind little mouse who lived in a cozy burrow."),
                StoryPage(2, "The mouse made friends with all the animals in the forest."),
                StoryPage(3, "They all lived happily together, helping each other every day. The end.")
            )
        }
    }

    private fun createElementaryFallback(genre: StoryGenre): List<StoryPage> {
        return when (genre) {
            StoryGenre.ADVENTURE -> listOf(
                StoryPage(1, "Emma discovered an old treasure map in her grandmother's attic. The map showed a path through the nearby forest to a mysterious X mark."),
                StoryPage(2, "With her backpack full of supplies, Emma set off into the forest. She followed the winding trail, crossing streams and climbing over fallen logs."),
                StoryPage(3, "At the X mark, Emma found not gold or jewels, but something even better - a beautiful clearing where endangered butterflies lived. She had discovered a treasure worth protecting forever.")
            )
            else -> listOf(
                StoryPage(1, "Sam and his robot friend Zip lived in a world where technology and nature worked together."),
                StoryPage(2, "When the town's power went out, Sam and Zip had to find a creative solution."),
                StoryPage(3, "They discovered that working together and using their different strengths saved the day.")
            )
        }
    }

    private fun createMiddleSchoolFallback(genre: StoryGenre): List<StoryPage> {
        return listOf(
            StoryPage(1, "Maya had always felt like she didn't quite fit in at her new school. But when she discovered the school's robotics club, everything changed."),
            StoryPage(2, "Working with her teammates, Maya learned that her unique perspective and creative problem-solving skills were exactly what the team needed."),
            StoryPage(3, "At the regional competition, their robot performed flawlessly. Maya realized that being different wasn't a weakness - it was her greatest strength.")
        )
    }

    private fun createHighSchoolFallback(genre: StoryGenre): List<StoryPage> {
        return listOf(
            StoryPage(1, "The message appeared on Alex's computer screen at exactly midnight: 'The truth about the disappearances is hidden in plain sight. Look for the pattern.'"),
            StoryPage(2, "Alex spent weeks analyzing data, tracking connections, and following leads. Each discovery led to more questions, but slowly a pattern emerged."),
            StoryPage(3, "The revelation was startling - the disappearances weren't random at all. They were part of a larger conspiracy that reached the highest levels of power. Alex now faced the biggest decision of their life: stay silent and safe, or speak truth to power.")
        )
    }

    private fun createAdultFallback(genre: StoryGenre): List<StoryPage> {
        return listOf(
            StoryPage(1, "Dr. Sarah Chen had spent fifteen years studying climate patterns, but the data she was seeing now defied everything she thought she knew about atmospheric science."),
            StoryPage(2, "As she dug deeper into the anomalies, Sarah uncovered evidence of a phenomenon that could revolutionize humanity's understanding of weather systems - or destroy civilization as they knew it."),
            StoryPage(3, "Standing at the podium before the world's leading scientists, Sarah took a deep breath. The future of humanity might depend on how she presented her findings in the next twenty minutes.")
        )
    }

    
    private fun buildDifficultyInstructions(settings: DifficultySettings): String {
        val vocabularyInstruction = when (settings.vocabularyLevel) {
            VocabularyLevel.BASIC -> "Use only simple, common words. Avoid words with more than ${settings.maxSyllablesPerWord} syllables."
            VocabularyLevel.INTERMEDIATE -> "Use basic vocabulary with some challenging words, but provide context clues."
            VocabularyLevel.ADVANCED -> "Use rich, descriptive vocabulary appropriate for the grade level."
            VocabularyLevel.COMPLEX -> "Use sophisticated vocabulary and varied word choices."
        }
        
        val sentenceInstruction = when (settings.sentenceComplexity) {
            SentenceComplexity.SIMPLE -> "Use simple sentences averaging ${settings.averageWordsPerSentence} words."
            SentenceComplexity.COMPOUND -> "Use compound sentences with basic conjunctions, averaging ${settings.averageWordsPerSentence} words."
            SentenceComplexity.COMPLEX -> "Use complex sentences with dependent clauses, averaging ${settings.averageWordsPerSentence} words."
            SentenceComplexity.SOPHISTICATED -> "Use varied, sophisticated sentence structures averaging ${settings.averageWordsPerSentence} words."
        }
        
        val conceptInstruction = if (settings.allowComplexConcepts) {
            "Complex themes and abstract concepts are appropriate."
        } else {
            "Keep concepts concrete and simple. Avoid abstract ideas."
        }
        
        return """
        - Vocabulary: $vocabularyInstruction
        - Sentence Structure: $sentenceInstruction
        - Paragraph Length: Average ${settings.averageSentencesPerParagraph} sentences per paragraph
        - Concepts: $conceptInstruction
        """.trimIndent()
    }
}