package com.mygemma3n.aiapp.feature.story

import androidx.compose.runtime.*
import androidx.compose.ui.tooling.preview.Preview

/**
 * Integration example showing how to use the new Enhanced Personalization features
 * in the Story mode. This demonstrates:
 * 
 * 1. Character Creator integration
 * 2. Story Templates usage
 * 3. Reading Mood Detection
 */

// Example of how to replace the existing CreateStoryDialog
@Composable
fun UpdatedStoryCreationFlow(
    onDismiss: () -> Unit,
    onCreateStory: (StoryRequest) -> Unit
) {
    var showCharacterCreator by remember { mutableStateOf(false) }
    
    if (showCharacterCreator) {
        CharacterCreatorScreen(
            onBackClick = { showCharacterCreator = false },
            onCharacterCreated = { character ->
                // Character created successfully
                showCharacterCreator = false
                // Could auto-add to selected characters in the story dialog
            }
        )
    } else {
        EnhancedCreateStoryDialog(
            onDismiss = onDismiss,
            onCreateStory = onCreateStory
        )
    }
}

// Example of how the StoryViewModel would be enhanced
class EnhancedStoryViewModel(
    // ... existing dependencies
    private val characterRepository: CharacterRepository,
    private val templateRepository: StoryTemplateRepository,
    private val moodDetectionService: ReadingMoodDetectionService
) {
    
    suspend fun generateStoryWithEnhancements(request: StoryRequest) {
        // 1. Use custom characters in story generation
        val characterContext = if (request.customCharacters.isNotEmpty()) {
            buildCharacterContext(request.customCharacters)
        } else ""
        
        // 2. Use template structure if specified
        val templateContext = request.templateId?.let { templateId ->
            getTemplateById(templateId)?.generatePrompt(
                customCharacters = request.customCharacters,
                selectedSetting = request.setting,
                selectedTheme = request.theme,
                customizations = request.templateCustomizations
            )
        } ?: ""
        
        // 3. Apply mood-based adjustments
        val moodAdjustments = request.moodContext?.let { moodName ->
            val mood = ReadingMood.valueOf(moodName)
            getMoodBasedAdjustments(mood)
        } ?: ""
        
        // Combine all enhancements into the final prompt
        val enhancedPrompt = buildString {
            append(request.prompt)
            if (templateContext.isNotEmpty()) {
                append("\n\nStructure: $templateContext")
            }
            if (characterContext.isNotEmpty()) {
                append("\n\nCharacters: $characterContext")
            }
            if (moodAdjustments.isNotEmpty()) {
                append("\n\nMood Context: $moodAdjustments")
            }
        }
        
        // Continue with existing story generation logic...
        generateStoryWithEnhancedPrompt(enhancedPrompt, request)
    }
    
    private fun buildCharacterContext(characters: List<Character>): String {
        return characters.joinToString("\n") { character ->
            "${character.name}: ${character.getFullDescription()}"
        }
    }
    
    private fun getMoodBasedAdjustments(mood: ReadingMood): String {
        return when (mood) {
            ReadingMood.ENERGETIC -> "Make this story exciting and action-packed with lots of adventure"
            ReadingMood.CALM -> "Create a gentle, peaceful story with soothing elements"
            ReadingMood.CURIOUS -> "Include mystery elements and learning opportunities"
            ReadingMood.CREATIVE -> "Add imaginative and fantastical elements"
            ReadingMood.SOCIAL -> "Focus on relationships and connections between characters"
            ReadingMood.CONTEMPLATIVE -> "Include deeper themes and thoughtful moments"
            ReadingMood.PLAYFUL -> "Make it fun and lighthearted with humor"
            ReadingMood.COMFORT -> "Create a warm, comforting story that feels familiar"
            ReadingMood.CHALLENGE -> "Include complex plot elements and thought-provoking themes"
        }
    }
    
    private suspend fun getTemplateById(templateId: String): Template? {
        return templateRepository.getTemplateById(templateId)
    }
    
    private fun generateStoryWithEnhancedPrompt(prompt: String, request: StoryRequest) {
        // This would integrate with the existing OnlineStoryGenerator
        // but with the enhanced prompt that includes character, template, and mood context
    }
}

// Example usage in the main StoryScreen
@Composable
fun EnhancedStoryListScreen(
    stories: List<Story>,
    isLoading: Boolean,
    readingStats: ReadingStats,
    onCreateNewStory: (StoryRequest) -> Unit,
    onSelectStory: (String) -> Unit,
    onDeleteStory: (String) -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var showMoodDetection by remember { mutableStateOf(false) }
    
    // ... existing UI code
    
    if (showCreateDialog) {
        UpdatedStoryCreationFlow(
            onDismiss = { showCreateDialog = false },
            onCreateStory = { request ->
                onCreateNewStory(request)
                showCreateDialog = false
            }
        )
    }
}

/**
 * Key Benefits of the Enhanced Personalization Features:
 * 
 * 1. CHARACTER CREATOR:
 *    - Users can create detailed, reusable characters
 *    - Characters have rich descriptions including appearance, personality, and backstory
 *    - Characters can be used across multiple stories
 *    - Builds emotional connection through character investment
 * 
 * 2. STORY TEMPLATES:
 *    - Pre-built story structures (Hero's Journey, Mystery, etc.)
 *    - Ensures well-structured, engaging narratives
 *    - Customizable beats for personalization
 *    - Educational value in teaching story structure
 * 
 * 3. READING MOOD DETECTION:
 *    - AI-powered mood detection based on time, context, and history
 *    - Personalized story recommendations
 *    - Adaptive content selection
 *    - Enhanced user engagement through relevant content
 * 
 * Integration Points:
 * - Database: All new entities are integrated into AppDatabase
 * - DI: New repositories and services are provided via Hilt
 * - UI: New screens and dialogs are composable and theme-aware
 * - Data Flow: Reactive flows with Room and StateFlow
 * - AI Integration: Works with existing UnifiedGemmaService
 */