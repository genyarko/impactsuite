package com.example.mygemma3n.feature.cbt

data class CBTSessionState(
    val isActive: Boolean = false,
    val currentEmotion: Emotion? = null,
    val suggestedTechnique: CBTTechnique? = null,
    val conversation: List<Message> = emptyList(),
    val thoughtRecord: ThoughtRecord? = null,
    val isRecording: Boolean = false,
    val liveTranscript: String = "",
    val currentStep: Int = 0,
    val userTypedInput: String = "",
    val sessionDuration: Long = 0L,
    val personalizedRecommendations: CBTSessionManager.PersonalizedRecommendations? = null,
    val sessionInsights: CBTSessionManager.SessionInsights? = null,
    /* ------------------------------------------------------------------------
       add any existing fields you already had below; e.g.:
    -------------------------------------------------------------------------*/
    val error: String? = null,
    val isUsingOnlineService: Boolean = false

)