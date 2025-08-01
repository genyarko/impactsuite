package com.example.mygemma3n.feature.story

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mygemma3n.shared_utilities.TextToSpeechManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class StoryViewModel @Inject constructor(
    private val storyRepository: StoryRepository,
    private val onlineStoryGenerator: OnlineStoryGenerator,
    private val textToSpeechManager: TextToSpeechManager
) : ViewModel() {

    data class StoryState(
        val isGenerating: Boolean = false,
        val generationProgress: Pair<Int, Int> = 0 to 0, // current page to total pages
        val generationPhase: String = "Preparing story...",
        val currentStory: Story? = null,
        val allStories: List<Story> = emptyList(),
        val error: String? = null,
        val isLoading: Boolean = false,
        val currentPage: Int = 0,
        val showStoryList: Boolean = true,
        val readingSessionStartTime: Long? = null,
        val isReadingAloud: Boolean = false,
        val autoReadAloud: Boolean = false,
        val speechRate: Float = 1.0f
    )

    private val _state = MutableStateFlow(StoryState())
    val state: StateFlow<StoryState> = _state.asStateFlow()

    init {
        loadAllStories()
    }

    private fun loadAllStories() {
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(isLoading = true)
                
                storyRepository.getAllStories().collect { stories ->
                    _state.value = _state.value.copy(
                        allStories = stories,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load stories")
                _state.value = _state.value.copy(
                    error = "Failed to load stories: ${e.message}",
                    isLoading = false
                )
            }
        }
    }

    fun generateStory(request: StoryRequest) {
        viewModelScope.launch {
            try {
                val targetPageCount = request.exactPageCount ?: when(request.length) {
                    StoryLength.SHORT -> 5
                    StoryLength.MEDIUM -> 10
                    StoryLength.LONG -> 20
                }
                
                _state.value = _state.value.copy(
                    isGenerating = true,
                    generationProgress = 0 to targetPageCount,
                    generationPhase = "Creating your ${request.genre.name.lowercase().replace('_', ' ')} story...",
                    error = null
                )

                val story = onlineStoryGenerator.generateStoryOnline(
                    request = request,
                    onPageGenerated = { current, total ->
                        val phase = when {
                            current == 1 -> "Writing the beginning..."
                            current < total -> "Continuing the adventure... (Page $current of $total)"
                            current == total && shouldGenerateImages(request.targetAudience) -> "Generating visual descriptions..."
                            else -> "Finishing the story..."
                        }
                        _state.value = _state.value.copy(
                            generationProgress = current to total,
                            generationPhase = phase
                        )
                    }
                )

                if (story != null) {
                    // Save the story to the database
                    storyRepository.saveStory(story)
                    
                    _state.value = _state.value.copy(
                        isGenerating = false,
                        currentStory = story,
                        currentPage = 0,
                        showStoryList = false,
                        generationPhase = "Story complete!"
                    )
                } else {
                    _state.value = _state.value.copy(
                        isGenerating = false,
                        error = "Failed to generate story. Please try again."
                    )
                }

            } catch (e: Exception) {
                Timber.e(e, "Story generation failed")
                _state.value = _state.value.copy(
                    isGenerating = false,
                    error = "Story generation failed: ${e.message}"
                )
            }
        }
    }

    fun loadStory(storyId: String) {
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(isLoading = true)
                
                val story = storyRepository.getStoryById(storyId)
                if (story != null) {
                    _state.value = _state.value.copy(
                        currentStory = story,
                        currentPage = story.currentPage,
                        showStoryList = false,
                        isLoading = false
                    )
                    
                    // Start reading session
                    startReadingSession(storyId)
                } else {
                    _state.value = _state.value.copy(
                        error = "Story not found",
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load story")
                _state.value = _state.value.copy(
                    error = "Failed to load story: ${e.message}",
                    isLoading = false
                )
            }
        }
    }

    fun goToNextPage() {
        val currentStory = _state.value.currentStory ?: return
        val currentPage = _state.value.currentPage
        
        if (currentPage < currentStory.totalPages - 1) {
            val newPage = currentPage + 1
            _state.value = _state.value.copy(currentPage = newPage, isReadingAloud = false)
            
            // Auto-read the new page if enabled
            autoReadPageIfEnabled()
            
            // Mark page as read and update in database
            viewModelScope.launch {
                try {
                    storyRepository.updateCurrentPage(currentStory.id, newPage)
                    
                    // If this is the last page, mark story as completed
                    if (newPage == currentStory.totalPages - 1) {
                        storyRepository.markStoryCompleted(currentStory.id)
                        val updatedStory = currentStory.copy(
                            currentPage = newPage,
                            isCompleted = true,
                            completedAt = System.currentTimeMillis()
                        )
                        _state.value = _state.value.copy(currentStory = updatedStory)
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to update page progress")
                }
            }
        }
    }

    fun goToPreviousPage() {
        val currentPage = _state.value.currentPage
        if (currentPage > 0) {
            val newPage = currentPage - 1
            _state.value = _state.value.copy(currentPage = newPage, isReadingAloud = false)
            
            // Auto-read the new page if enabled
            autoReadPageIfEnabled()
            
            // Update in database
            val currentStory = _state.value.currentStory
            if (currentStory != null) {
                viewModelScope.launch {
                    try {
                        storyRepository.updateCurrentPage(currentStory.id, newPage)
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to update page progress")
                    }
                }
            }
        }
    }

    fun goToPage(pageNumber: Int) {
        val currentStory = _state.value.currentStory ?: return
        if (pageNumber in 0 until currentStory.totalPages) {
            _state.value = _state.value.copy(currentPage = pageNumber, isReadingAloud = false)
            
            // Auto-read the new page if enabled
            autoReadPageIfEnabled()
            
            viewModelScope.launch {
                try {
                    storyRepository.updateCurrentPage(currentStory.id, pageNumber)
                } catch (e: Exception) {
                    Timber.e(e, "Failed to update page progress")
                }
            }
        }
    }

    fun backToStoryList() {
        _state.value = _state.value.copy(
            showStoryList = true,
            currentStory = null,
            currentPage = 0,
            readingSessionStartTime = null
        )
    }

    fun deleteStory(storyId: String) {
        viewModelScope.launch {
            try {
                storyRepository.deleteStory(storyId)
                // The flow will automatically update the UI
            } catch (e: Exception) {
                Timber.e(e, "Failed to delete story")
                _state.value = _state.value.copy(
                    error = "Failed to delete story: ${e.message}"
                )
            }
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    fun showNewStoryDialog() {
        _state.value = _state.value.copy(
            showStoryList = true,
            currentStory = null
        )
    }

    private fun startReadingSession(storyId: String) {
        viewModelScope.launch {
            try {
                val startTime = storyRepository.startReadingSession(storyId)
                _state.value = _state.value.copy(readingSessionStartTime = startTime)
            } catch (e: Exception) {
                Timber.e(e, "Failed to start reading session")
            }
        }
    }

    fun getStoriesByGenre(genre: StoryGenre, callback: (List<Story>) -> Unit) {
        viewModelScope.launch {
            try {
                val stories = storyRepository.getStoriesByGenre(genre)
                callback(stories)
            } catch (e: Exception) {
                Timber.e(e, "Failed to get stories by genre")
                callback(emptyList())
            }
        }
    }

    fun getStoriesByTarget(target: StoryTarget, callback: (List<Story>) -> Unit) {
        viewModelScope.launch {
            try {
                val stories = storyRepository.getStoriesByTarget(target)
                callback(stories)
            } catch (e: Exception) {
                Timber.e(e, "Failed to get stories by target")
                callback(emptyList())
            }
        }
    }

    fun getReadingStats(storyId: String, callback: (Int, Int) -> Unit) {
        viewModelScope.launch {
            try {
                val (sessionCount, totalTime) = storyRepository.getReadingStats(storyId)
                callback(sessionCount, totalTime)
            } catch (e: Exception) {
                Timber.e(e, "Failed to get reading stats")
                callback(0, 0)
            }
        }
    }
    
    private fun shouldGenerateImages(targetAudience: StoryTarget): Boolean {
        return when (targetAudience) {
            StoryTarget.KINDERGARTEN, StoryTarget.ELEMENTARY, StoryTarget.MIDDLE_SCHOOL -> true
            StoryTarget.HIGH_SCHOOL, StoryTarget.ADULT -> false
        }
    }

    // Text-to-Speech functionality
    fun startReadingAloud() {
        val currentStory = _state.value.currentStory ?: return
        val currentPage = _state.value.currentPage
        val currentStoryPage = currentStory.pages.getOrNull(currentPage) ?: return

        _state.value = _state.value.copy(isReadingAloud = true)
        
        // Speak the page content
        val textToSpeak = buildString {
            currentStoryPage.title?.let { title ->
                append(title)
                append(". ")
            }
            append(currentStoryPage.content)
        }
        
        textToSpeechManager.speak(textToSpeak)
    }

    fun stopReadingAloud() {
        _state.value = _state.value.copy(isReadingAloud = false)
        // The TextToSpeechManager will handle stopping in its lifecycle methods
    }

    fun toggleAutoReadAloud() {
        val newAutoRead = !_state.value.autoReadAloud
        _state.value = _state.value.copy(autoReadAloud = newAutoRead)
        
        // If auto-read is enabled and we're not currently reading, start reading
        if (newAutoRead && !_state.value.isReadingAloud) {
            startReadingAloud()
        }
    }

    fun setSpeechRate(rate: Float) {
        val clampedRate = rate.coerceIn(0.5f, 2.0f)
        _state.value = _state.value.copy(speechRate = clampedRate)
        // Note: TextToSpeech speech rate would need to be set in TextToSpeechManager
    }

    private fun autoReadPageIfEnabled() {
        if (_state.value.autoReadAloud) {
            startReadingAloud()
        }
    }
}