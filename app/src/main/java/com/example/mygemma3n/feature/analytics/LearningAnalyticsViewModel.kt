package com.example.mygemma3n.feature.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
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
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)
                
                // Demo data initialization is disabled for production use
                // Only initialize demo data if explicitly called via UI button
                // launch {
                //     try {
                //         analyticsRepository.initializeDemoData(currentStudentId)
                //     } catch (e: Exception) {
                //         Timber.w(e, "Failed to initialize demo data, continuing anyway")
                //     }
                // }
                
                val analytics = analyticsRepository.getLearningAnalytics(currentStudentId).first()
                
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    analytics = analytics,
                    error = null
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Expected during navigation - don't log as error
                Timber.d("Analytics loading was cancelled")
            } catch (e: Exception) {
                Timber.e(e, "Error loading analytics")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Error loading analytics: ${e.message}"
                )
            }
        }
    }

    private suspend fun initializeDemoDataIfNeeded() {
        try {
            // Check if we have any data by looking at a simple count
            val hasData = try {
                // Use a more direct approach to check for data
                analyticsRepository.getLearningAnalytics(currentStudentId)
                    .first()
                    .let { analytics ->
                        analytics.subjectProgress.isNotEmpty() || analytics.knowledgeGaps.isNotEmpty()
                    }
            } catch (e: Exception) {
                false
            }

            if (!hasData) {
                Timber.d("No analytics data found, initializing demo data")
                analyticsRepository.initializeDemoData(currentStudentId)
            }
        } catch (e: Exception) {
            Timber.w(e, "Could not check for existing data, initializing demo data anyway")
            analyticsRepository.initializeDemoData(currentStudentId)
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
                analyticsRepository.markRecommendationCompleted(recommendationId)
                refreshAnalytics()
                Timber.d("Marked recommendation $recommendationId as completed")
            } catch (e: Exception) {
                Timber.e(e, "Failed to mark recommendation as completed")
            }
        }
    }

    fun dismissRecommendation(recommendationId: String) {
        viewModelScope.launch {
            try {
                analyticsRepository.dismissRecommendation(recommendationId)
                refreshAnalytics()
                Timber.d("Dismissed recommendation $recommendationId")
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

    fun initializeDemoData() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true)
                analyticsRepository.initializeDemoData(currentStudentId)
                if (isActive) {
                    refreshAnalytics()
                    Timber.d("Demo data initialized successfully")
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                Timber.d("Demo data initialization cancelled")
            } catch (e: Exception) {
                if (isActive) {
                    Timber.e(e, "Failed to initialize demo data")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Failed to initialize demo data: ${e.message}"
                    )
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        Timber.d("LearningAnalyticsViewModel cleared")
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