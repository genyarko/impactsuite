package com.example.mygemma3n.feature.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class LearningAnalyticsViewModel @Inject constructor(
    private val analyticsRepository: LearningAnalyticsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AnalyticsUiState())
    val uiState: StateFlow<AnalyticsUiState> = _uiState.asStateFlow()

    private val _selectedTimeframe = MutableStateFlow(AnalyticsTimeframe.WEEK)
    val selectedTimeframe: StateFlow<AnalyticsTimeframe> = _selectedTimeframe.asStateFlow()

    private val _selectedSubject = MutableStateFlow<String?>(null)
    val selectedSubject: StateFlow<String?> = _selectedSubject.asStateFlow()

    // Simulate current student - in real app, get from UserRepository
    private val currentStudentId = "student_001"

    init {
        loadAnalytics()
    }

    private fun loadAnalytics() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true)
                
                analyticsRepository.getLearningAnalytics(currentStudentId)
                    .catch { error ->
                        Timber.e(error, "Failed to load analytics")
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = "Failed to load analytics: ${error.message}"
                        )
                    }
                    .collect { analytics ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            analytics = analytics,
                            error = null
                        )
                    }
            } catch (e: Exception) {
                Timber.e(e, "Error loading analytics")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Error loading analytics: ${e.message}"
                )
            }
        }
    }

    fun refreshAnalytics() {
        loadAnalytics()
    }

    fun selectTimeframe(timeframe: AnalyticsTimeframe) {
        _selectedTimeframe.value = timeframe
        // In a full implementation, this would filter the data accordingly
        loadAnalytics()
    }

    fun selectSubject(subject: String?) {
        _selectedSubject.value = subject
    }

    fun generateRecommendations() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isGeneratingRecommendations = true)
                
                val recommendations = analyticsRepository.generateRecommendations(currentStudentId)
                
                _uiState.value = _uiState.value.copy(
                    isGeneratingRecommendations = false,
                    analytics = _uiState.value.analytics?.copy(
                        recommendations = recommendations
                    )
                )
                
                Timber.d("Generated ${recommendations.size} new recommendations")
                
            } catch (e: Exception) {
                Timber.e(e, "Failed to generate recommendations")
                _uiState.value = _uiState.value.copy(
                    isGeneratingRecommendations = false,
                    error = "Failed to generate recommendations: ${e.message}"
                )
            }
        }
    }

    fun markRecommendationCompleted(recommendationId: String) {
        viewModelScope.launch {
            try {
                // Update in repository - would be implemented in full version
                refreshAnalytics()
            } catch (e: Exception) {
                Timber.e(e, "Failed to mark recommendation as completed")
            }
        }
    }

    fun dismissRecommendation(recommendationId: String) {
        viewModelScope.launch {
            try {
                // Update in repository - would be implemented in full version
                refreshAnalytics()
            } catch (e: Exception) {
                Timber.e(e, "Failed to dismiss recommendation")
            }
        }
    }

    fun startLearningSession(sessionType: String = "MIXED") {
        viewModelScope.launch {
            try {
                val sessionId = analyticsRepository.startLearningSession(currentStudentId, sessionType)
                _uiState.value = _uiState.value.copy(currentSessionId = sessionId)
                Timber.d("Started learning session: $sessionId")
            } catch (e: Exception) {
                Timber.e(e, "Failed to start learning session")
            }
        }
    }

    fun endLearningSession(performance: Float, focusScore: Float) {
        viewModelScope.launch {
            try {
                val sessionId = _uiState.value.currentSessionId
                if (sessionId != null) {
                    analyticsRepository.endLearningSession(sessionId, performance, focusScore)
                    _uiState.value = _uiState.value.copy(currentSessionId = null)
                    refreshAnalytics()
                    Timber.d("Ended learning session: $sessionId")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to end learning session")
            }
        }
    }
}

data class AnalyticsUiState(
    val isLoading: Boolean = false,
    val analytics: LearningAnalytics? = null,
    val error: String? = null,
    val isGeneratingRecommendations: Boolean = false,
    val currentSessionId: String? = null
)

enum class AnalyticsTimeframe(val displayName: String) {
    WEEK("This Week"),
    MONTH("This Month"),
    QUARTER("3 Months"),
    YEAR("This Year"),
    ALL_TIME("All Time")
}