package com.example.mygemma3n.feature

import androidx.lifecycle.ViewModel
import com.example.mygemma3n.gemma.GemmaEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.update
import javax.inject.Inject

// CBTCoachViewModel.kt

@HiltViewModel
class CBTCoachViewModel @Inject constructor(
    private val gemmaEngine: GemmaEngine,
    private val emotionDetector: EmotionDetector,
    private val cbtTechniques: CBTTechniques,
    private val sessionRepository: SessionRepository
) : ViewModel() {

    private val _sessionState = MutableStateFlow(CBTSessionState())
    val sessionState: StateFlow<CBTSessionState> = _sessionState.asStateFlow()

    data class CBTSessionState(
        val isActive: Boolean = false,
        val currentEmotion: Emotion? = null,
        val suggestedTechnique: CBTTechnique? = null,
        val conversation: List<Message> = emptyList(),
        val thoughtRecord: ThoughtRecord? = null
    )

    suspend fun processVoiceInput(audioData: FloatArray) {
        // Detect emotion from voice
        val emotion = emotionDetector.detectFromAudio(audioData)
        _sessionState.update { it.copy(currentEmotion = emotion) }

        // Transcribe audio with emotion context
        val prompt = buildCBTPrompt(audioData, emotion)

        val response = gemmaEngine.generateText(
            prompt = prompt,
            maxTokens = 200,
            temperature = 0.7f
        ).toList().joinToString("")

        // Parse CBT technique from response
        val technique = parseCBTTechnique(response)
        _sessionState.update {
            it.copy(
                suggestedTechnique = technique,
                conversation = it.conversation + Message.AI(response)
            )
        }

        // Save session for progress tracking
        sessionRepository.saveSession(
            CBTSession(
                timestamp = System.currentTimeMillis(),
                emotion = emotion,
                technique = technique,
                transcript = response
            )
        )
    }

    private fun buildCBTPrompt(audio: FloatArray, emotion: Emotion): String {
        return """
            You are a compassionate CBT (Cognitive Behavioral Therapy) coach.
            The user is experiencing ${emotion.name} emotions.
            
            Audio input: [AUDIO_EMBEDDING]
            
            Provide supportive guidance using CBT techniques. Focus on:
            1. Validating their feelings
            2. Identifying thought patterns
            3. Suggesting a specific CBT technique
            4. Guiding them through it step-by-step
            
            Keep responses warm, professional, and under 200 words.
        """.trimIndent()
    }
}