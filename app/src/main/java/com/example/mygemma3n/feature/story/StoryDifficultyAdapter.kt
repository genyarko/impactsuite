package com.example.mygemma3n.feature.story

import com.example.mygemma3n.data.UnifiedGemmaService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

data class DifficultySettings(
    val vocabularyLevel: VocabularyLevel,
    val sentenceComplexity: SentenceComplexity,
    val averageWordsPerSentence: Int,
    val averageSentencesPerParagraph: Int,
    val maxSyllablesPerWord: Int,
    val allowComplexConcepts: Boolean
)

enum class VocabularyLevel {
    BASIC,        // Simple, common words
    INTERMEDIATE, // Some advanced vocabulary with context
    ADVANCED,     // Rich vocabulary appropriate for level
    COMPLEX       // Sophisticated vocabulary and concepts
}

enum class SentenceComplexity {
    SIMPLE,       // Simple sentences, minimal clauses
    COMPOUND,     // Compound sentences, basic clauses
    COMPLEX,      // Complex sentences with dependent clauses
    SOPHISTICATED // Advanced sentence structures
}

@Singleton
class StoryDifficultyAdapter @Inject constructor(
    private val gemmaService: UnifiedGemmaService
) {
    
    fun getDifficultySettings(targetAudience: StoryTarget): DifficultySettings {
        return when (targetAudience) {
            StoryTarget.KINDERGARTEN -> DifficultySettings(
                vocabularyLevel = VocabularyLevel.BASIC,
                sentenceComplexity = SentenceComplexity.SIMPLE,
                averageWordsPerSentence = 8,
                averageSentencesPerParagraph = 2,
                maxSyllablesPerWord = 2,
                allowComplexConcepts = false
            )
            
            StoryTarget.ELEMENTARY -> DifficultySettings(
                vocabularyLevel = VocabularyLevel.BASIC,
                sentenceComplexity = SentenceComplexity.COMPOUND,
                averageWordsPerSentence = 12,
                averageSentencesPerParagraph = 3,
                maxSyllablesPerWord = 3,
                allowComplexConcepts = false
            )
            
            StoryTarget.MIDDLE_SCHOOL -> DifficultySettings(
                vocabularyLevel = VocabularyLevel.INTERMEDIATE,
                sentenceComplexity = SentenceComplexity.COMPOUND,
                averageWordsPerSentence = 15,
                averageSentencesPerParagraph = 4,
                maxSyllablesPerWord = 4,
                allowComplexConcepts = true
            )
            
            StoryTarget.HIGH_SCHOOL -> DifficultySettings(
                vocabularyLevel = VocabularyLevel.ADVANCED,
                sentenceComplexity = SentenceComplexity.COMPLEX,
                averageWordsPerSentence = 18,
                averageSentencesPerParagraph = 5,
                maxSyllablesPerWord = 5,
                allowComplexConcepts = true
            )
            
            StoryTarget.ADULT -> DifficultySettings(
                vocabularyLevel = VocabularyLevel.COMPLEX,
                sentenceComplexity = SentenceComplexity.SOPHISTICATED,
                averageWordsPerSentence = 22,
                averageSentencesPerParagraph = 6,
                maxSyllablesPerWord = 6,
                allowComplexConcepts = true
            )
        }
    }
    
    suspend fun adaptStoryDifficulty(
        originalStory: Story,
        newTargetAudience: StoryTarget
    ): Story = withContext(Dispatchers.IO) {
        
        if (originalStory.targetAudience == newTargetAudience) {
            return@withContext originalStory
        }
        
        val newDifficulty = getDifficultySettings(newTargetAudience)
        val adaptedPages = mutableListOf<StoryPage>()
        
        try {
            originalStory.pages.forEach { page ->
                val adaptedPage = adaptPageDifficulty(page, newDifficulty, newTargetAudience)
                adaptedPages.add(adaptedPage)
            }
            
            return@withContext originalStory.copy(
                targetAudience = newTargetAudience,
                pages = adaptedPages,
                id = java.util.UUID.randomUUID().toString(), // New ID for adapted story
                title = adaptTitleDifficulty(originalStory.title, newDifficulty, newTargetAudience),
                createdAt = System.currentTimeMillis(),
                currentPage = 0,
                isCompleted = false,
                completedAt = null
            )
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to adapt story difficulty")
            throw e
        }
    }
    
    private suspend fun adaptPageDifficulty(
        page: StoryPage,
        difficulty: DifficultySettings,
        targetAudience: StoryTarget
    ): StoryPage {
        val adaptedContent = adaptTextDifficulty(page.content, difficulty, targetAudience)
        val adaptedTitle = page.title?.let { 
            adaptTextDifficulty(it, difficulty, targetAudience) 
        }
        
        return page.copy(
            content = adaptedContent,
            title = adaptedTitle,
            isRead = false,
            readAt = null
        )
    }
    
    private suspend fun adaptTextDifficulty(
        text: String,
        difficulty: DifficultySettings,
        targetAudience: StoryTarget
    ): String {
        val prompt = buildAdaptationPrompt(text, difficulty, targetAudience)
        
        return try {
            val response = gemmaService.generateResponse(prompt, image = null, temperature = 0.7f)
            extractAdaptedText(response) ?: text
        } catch (e: Exception) {
            Timber.e(e, "Failed to adapt text difficulty via AI")
            // Fallback to rule-based adaptation
            applyRuleBasedAdaptation(text, difficulty)
        }
    }
    
    private suspend fun adaptTitleDifficulty(
        title: String,
        difficulty: DifficultySettings,
        targetAudience: StoryTarget
    ): String {
        if (title.split(" ").size <= 5) return title // Keep short titles as-is
        
        return adaptTextDifficulty(title, difficulty, targetAudience)
    }
    
    private fun buildAdaptationPrompt(
        text: String,
        difficulty: DifficultySettings,
        targetAudience: StoryTarget
    ): String {
        val vocabularyGuidance = when (difficulty.vocabularyLevel) {
            VocabularyLevel.BASIC -> "Use only simple, common words that a young child would understand. Avoid words with more than ${difficulty.maxSyllablesPerWord} syllables."
            VocabularyLevel.INTERMEDIATE -> "Use intermediate vocabulary appropriate for elementary students. Include some challenging words but provide context clues."
            VocabularyLevel.ADVANCED -> "Use rich vocabulary appropriate for the reading level. Include descriptive and precise word choices."
            VocabularyLevel.COMPLEX -> "Use sophisticated vocabulary and varied word choices appropriate for advanced readers."
        }
        
        val sentenceGuidance = when (difficulty.sentenceComplexity) {
            SentenceComplexity.SIMPLE -> "Write in simple sentences with basic subject-verb-object structure. Average ${difficulty.averageWordsPerSentence} words per sentence."
            SentenceComplexity.COMPOUND -> "Use compound sentences with coordinating conjunctions. Average ${difficulty.averageWordsPerSentence} words per sentence."
            SentenceComplexity.COMPLEX -> "Use complex sentences with dependent clauses and varied structures. Average ${difficulty.averageWordsPerSentence} words per sentence."
            SentenceComplexity.SOPHISTICATED -> "Use sophisticated sentence structures with varied lengths and complex ideas. Average ${difficulty.averageWordsPerSentence} words per sentence."
        }
        
        val conceptGuidance = if (difficulty.allowComplexConcepts) {
            "Complex concepts and abstract ideas are appropriate."
        } else {
            "Keep concepts concrete and simple. Avoid abstract or complex ideas."
        }
        
        return """
            Rewrite the following text to be appropriate for $targetAudience readers:

            Original text: "$text"

            Adaptation guidelines:
            - Vocabulary: $vocabularyGuidance
            - Sentence structure: $sentenceGuidance
            - Concepts: $conceptGuidance
            - Keep the same meaning and story elements
            - Maintain the narrative flow and engagement
            - Use ${difficulty.averageSentencesPerParagraph} sentences per paragraph

            Return only the adapted text, no explanations.
            
            ADAPTED_TEXT_START
        """.trimIndent()
    }
    
    private fun extractAdaptedText(response: String): String? {
        return when {
            response.contains("ADAPTED_TEXT_START") -> {
                response.substringAfter("ADAPTED_TEXT_START").trim()
            }
            response.lines().size == 1 -> response.trim()
            else -> {
                // Try to extract the main content, avoiding meta-commentary
                val lines = response.lines().filter { line ->
                    val trimmed = line.trim()
                    trimmed.isNotEmpty() && 
                    !trimmed.startsWith("Here") &&
                    !trimmed.startsWith("The adapted") &&
                    !trimmed.startsWith("This rewrite")
                }
                lines.joinToString(" ").trim().takeIf { it.isNotEmpty() }
            }
        }
    }
    
    private fun applyRuleBasedAdaptation(
        text: String,
        difficulty: DifficultySettings
    ): String {
        var adapted = text
        
        // Apply basic word substitutions based on difficulty level
        when (difficulty.vocabularyLevel) {
            VocabularyLevel.BASIC -> {
                adapted = adapted.replace(Regex("\\babsolutely\\b", RegexOption.IGNORE_CASE), "really")
                adapted = adapted.replace(Regex("\\benormous\\b", RegexOption.IGNORE_CASE), "very big")
                adapted = adapted.replace(Regex("\\bimmediately\\b", RegexOption.IGNORE_CASE), "right away")
                adapted = adapted.replace(Regex("\\bdiscovered\\b", RegexOption.IGNORE_CASE), "found")
                adapted = adapted.replace(Regex("\\bunfortunately\\b", RegexOption.IGNORE_CASE), "sadly")
            }
            
            VocabularyLevel.INTERMEDIATE -> {
                adapted = adapted.replace(Regex("\\butilize\\b", RegexOption.IGNORE_CASE), "use")
                adapted = adapted.replace(Regex("\\bdemonstrate\\b", RegexOption.IGNORE_CASE), "show")
                adapted = adapted.replace(Regex("\\bcomprehend\\b", RegexOption.IGNORE_CASE), "understand")
            }
            
            else -> { /* Keep advanced vocabulary */ }
        }
        
        // Split into sentences and potentially simplify sentence structure
        if (difficulty.sentenceComplexity == SentenceComplexity.SIMPLE) {
            adapted = simplifyComplexSentences(adapted)
        }
        
        return adapted
    }
    
    private fun simplifyComplexSentences(text: String): String {
        val sentences = text.split(Regex("[.!?]+")).filter { it.trim().isNotEmpty() }
        val simplifiedSentences = mutableListOf<String>()
        
        sentences.forEach { sentence ->
            val trimmed = sentence.trim()
            if (trimmed.isEmpty()) return@forEach
            
            // Split long sentences at conjunctions
            if (trimmed.split(" ").size > 15) {
                val parts = trimmed.split(Regex("\\b(because|although|while|when|if|since|unless)\\b", RegexOption.IGNORE_CASE))
                if (parts.size > 1) {
                    parts.forEachIndexed { index, part ->
                        val cleanPart = part.trim()
                        if (cleanPart.isNotEmpty()) {
                            if (index == 0) {
                                simplifiedSentences.add("$cleanPart.")
                            } else {
                                // Make the dependent clause independent
                                simplifiedSentences.add(cleanPart.replaceFirstChar { it.uppercase() } + ".")
                            }
                        }
                    }
                } else {
                    simplifiedSentences.add("$trimmed.")
                }
            } else {
                simplifiedSentences.add("$trimmed.")
            }
        }
        
        return simplifiedSentences.joinToString(" ")
    }
    
    fun getReadabilityScore(text: String): ReadabilityScore {
        val sentences = text.split(Regex("[.!?]+")).filter { it.trim().isNotEmpty() }
        val words = text.split(Regex("\\s+")).filter { it.isNotEmpty() }
        val syllables = words.sumOf { countSyllables(it) }
        
        val avgWordsPerSentence = if (sentences.isNotEmpty()) words.size.toDouble() / sentences.size else 0.0
        val avgSyllablesPerWord = if (words.isNotEmpty()) syllables.toDouble() / words.size else 0.0
        
        // Simplified Flesch Reading Ease calculation
        val fleschScore = 206.835 - (1.015 * avgWordsPerSentence) - (84.6 * avgSyllablesPerWord)
        
        val gradeLevel = when {
            fleschScore >= 90 -> GradeLevel.KINDERGARTEN
            fleschScore >= 80 -> GradeLevel.ELEMENTARY
            fleschScore >= 70 -> GradeLevel.MIDDLE_SCHOOL
            fleschScore >= 60 -> GradeLevel.HIGH_SCHOOL
            else -> GradeLevel.ADULT
        }
        
        return ReadabilityScore(
            fleschScore = fleschScore,
            averageWordsPerSentence = avgWordsPerSentence,
            averageSyllablesPerWord = avgSyllablesPerWord,
            estimatedGradeLevel = gradeLevel,
            wordCount = words.size,
            sentenceCount = sentences.size
        )
    }
    
    private fun countSyllables(word: String): Int {
        val cleanWord = word.lowercase().replace(Regex("[^a-z]"), "")
        if (cleanWord.isEmpty()) return 0
        
        var count = 0
        var previousWasVowel = false
        
        cleanWord.forEach { char ->
            val isVowel = char in "aeiouy"
            if (isVowel && !previousWasVowel) {
                count++
            }
            previousWasVowel = isVowel
        }
        
        // Adjust for silent 'e'
        if (cleanWord.endsWith("e") && count > 1) {
            count--
        }
        
        return maxOf(1, count) // Every word has at least one syllable
    }
}

data class ReadabilityScore(
    val fleschScore: Double,
    val averageWordsPerSentence: Double,
    val averageSyllablesPerWord: Double,
    val estimatedGradeLevel: GradeLevel,
    val wordCount: Int,
    val sentenceCount: Int
)

enum class GradeLevel {
    KINDERGARTEN,
    ELEMENTARY, 
    MIDDLE_SCHOOL,
    HIGH_SCHOOL,
    ADULT
}