package com.mygemma3n.aiapp.feature.story

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class CreateStoryState(
    // Basic tab
    val prompt: String = "",
    val selectedGenre: StoryGenre = StoryGenre.ADVENTURE,
    val selectedTarget: StoryTarget = StoryTarget.ELEMENTARY,
    val pageCount: Float = 10f,
    val setting: String = "",
    val theme: String = "",
    
    // Characters tab
    val availableCharacters: List<Character> = emptyList(),
    val selectedCharacters: List<Character> = emptyList(),
    val basicCharacters: String = "",
    
    // Template tab
    val availableTemplates: List<Template> = emptyList(),
    val selectedTemplate: Template? = null,
    
    // Mood tab
    val detectedMood: MoodRecommendation? = null,
    val selectedMood: ReadingMood? = null,
    
    // State
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class CreateStoryViewModel @Inject constructor(
    private val characterRepository: CharacterRepository,
    private val templateRepository: StoryTemplateRepository,
    private val moodDetectionService: ReadingMoodDetectionService
) : ViewModel() {

    private val _state = MutableStateFlow(CreateStoryState())
    val state: StateFlow<CreateStoryState> = _state.asStateFlow()

    init {
        loadCharacters()
        loadTemplates()
    }

    private fun loadCharacters() {
        viewModelScope.launch {
            try {
                characterRepository.getAllActiveCharacters().collect { characters ->
                    _state.value = _state.value.copy(
                        availableCharacters = characters
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load characters")
                _state.value = _state.value.copy(
                    error = "Failed to load characters: ${e.message}"
                )
            }
        }
    }

    private fun loadTemplates() {
        viewModelScope.launch {
            try {
                templateRepository.getAllActiveTemplates().collect { templates ->
                    _state.value = _state.value.copy(
                        availableTemplates = templates
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load templates")
                _state.value = _state.value.copy(
                    error = "Failed to load templates: ${e.message}"
                )
            }
        }
    }

    fun updateState(newState: CreateStoryState) {
        _state.value = newState
    }

    fun detectCurrentMood() {
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(isLoading = true)
                
                val moodRecommendation = moodDetectionService.getMoodBasedRecommendations(
                    targetAudience = _state.value.selectedTarget
                )
                
                _state.value = _state.value.copy(
                    detectedMood = moodRecommendation,
                    isLoading = false
                )
                
                // Auto-apply mood recommendations
                applyMoodRecommendations(moodRecommendation)
                
            } catch (e: Exception) {
                Timber.e(e, "Failed to detect mood")
                _state.value = _state.value.copy(
                    error = "Failed to detect mood: ${e.message}",
                    isLoading = false
                )
            }
        }
    }

    private fun applyMoodRecommendations(mood: MoodRecommendation) {
        val currentState = _state.value
        
        // Auto-select a suitable template if none selected
        val suggestedTemplate = currentState.availableTemplates.find { template ->
            mood.recommendedTemplates.contains(template.type)
        }
        
        // Auto-select genre if it matches recommendations
        val suggestedGenre = mood.recommendedGenres.firstOrNull()
        
        _state.value = currentState.copy(
            selectedTemplate = suggestedTemplate ?: currentState.selectedTemplate,
            selectedGenre = suggestedGenre ?: currentState.selectedGenre,
            selectedMood = mood.primaryMood,
            pageCount = when (mood.suggestedLength) {
                StoryLength.SHORT -> 5f
                StoryLength.MEDIUM -> 10f
                StoryLength.LONG -> 15f
            }
        )
    }

    fun buildStoryRequest(): StoryRequest {
        val currentState = _state.value
        
        // Combine basic characters with custom characters
        val allCharacters = mutableListOf<String>()
        if (currentState.basicCharacters.isNotBlank()) {
            allCharacters.addAll(
                currentState.basicCharacters.split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
            )
        }
        
        // Add custom character names
        allCharacters.addAll(currentState.selectedCharacters.map { it.name })
        
        // Build template customizations if needed
        val templateCustomizations = buildTemplateCustomizations(currentState)
        
        // Use template's estimated pages if a template is selected
        // Priority: template's estimatedPages > user's manual pageCount setting
        val finalPageCount = if (currentState.selectedTemplate != null) {
            currentState.selectedTemplate.estimatedPages
        } else {
            currentState.pageCount.toInt()
        }
        
        // Only include details fields if they have meaningful content
        val finalSetting = if (currentState.setting.isNotBlank()) currentState.setting else ""
        val finalTheme = if (currentState.theme.isNotBlank()) currentState.theme else ""
        
        return StoryRequest(
            prompt = enhancePromptWithContext(currentState),
            genre = currentState.selectedGenre,
            targetAudience = currentState.selectedTarget,
            length = when (finalPageCount) {
                in 3..6 -> StoryLength.SHORT
                in 7..13 -> StoryLength.MEDIUM
                else -> StoryLength.LONG
            },
            characters = allCharacters,
            setting = finalSetting,
            theme = finalTheme,
            exactPageCount = finalPageCount,
            customCharacters = currentState.selectedCharacters,
            templateId = currentState.selectedTemplate?.id,
            moodContext = (currentState.selectedMood ?: currentState.detectedMood?.primaryMood)?.name,
            templateCustomizations = templateCustomizations
        )
    }

    private fun enhancePromptWithContext(state: CreateStoryState): String {
        val basePrompt = state.prompt
        
        // If using a template, enhance the prompt with template context
        val templateContext = state.selectedTemplate?.let { template ->
            // Only pass non-empty setting and theme to template
            val templateSetting = if (state.setting.isNotBlank()) state.setting else ""
            val templateTheme = if (state.theme.isNotBlank()) state.theme else ""
            
            template.generatePrompt(
                customCharacters = state.selectedCharacters,
                selectedSetting = templateSetting,
                selectedTheme = templateTheme,
                customizations = buildTemplateCustomizations(state)
            )
        }
        
        // Add character context only if characters are selected
        val characterContext = if (state.selectedCharacters.isNotEmpty()) {
            "\n\nCharacter Details:\n" + state.selectedCharacters.joinToString("\n") { character ->
                "- ${character.name}: ${character.getFullDescription()}"
            }
        } else ""
        
        // Add mood context only if mood is selected or detected
        val moodContext = (state.selectedMood ?: state.detectedMood?.primaryMood)?.let { mood ->
            "\n\nMood Context: This story should match a ${mood.name.lowercase().replace('_', ' ')} mood - ${getMoodRecommendationText(mood)}"
        } ?: ""
        
        return when {
            templateContext != null -> {
                // If using template and basePrompt is empty, just use template
                if (basePrompt.isBlank()) {
                    "$templateContext$characterContext$moodContext"
                } else {
                    "$templateContext\n\nAdditional Context: $basePrompt$characterContext$moodContext"
                }
            }
            else -> "$basePrompt$characterContext$moodContext"
        }
    }

    private fun buildTemplateCustomizations(state: CreateStoryState): Map<String, String> {
        // For now, return empty map. This could be enhanced with a UI
        // to let users customize individual story beats
        return emptyMap()
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }
    
    fun refreshCharacters() {
        loadCharacters() // Reload characters after creating a new one
    }
    
    fun resetToInitialState() {
        _state.value = CreateStoryState(
            // Keep the available characters and templates that were loaded
            availableCharacters = _state.value.availableCharacters,
            availableTemplates = _state.value.availableTemplates,
            // Reset all user selections to defaults
            prompt = "",
            selectedGenre = StoryGenre.ADVENTURE,
            selectedTarget = StoryTarget.ELEMENTARY,
            pageCount = 10f,
            setting = "",
            theme = "",
            selectedCharacters = emptyList(),
            basicCharacters = "",
            selectedTemplate = null,
            detectedMood = null,
            selectedMood = null,
            isLoading = false,
            error = null
        )
    }
}