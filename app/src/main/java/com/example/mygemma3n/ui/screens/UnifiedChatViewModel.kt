package com.example.mygemma3n.ui.screens

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mygemma3n.data.UnifiedGemmaService
import com.example.mygemma3n.data.GeminiApiService
import com.example.mygemma3n.domain.repository.SettingsRepository
import com.example.mygemma3n.data.SpeechRecognitionService
import com.example.mygemma3n.feature.chat.ChatMessage
import com.example.mygemma3n.feature.chat.OnlineChatService
import com.example.mygemma3n.feature.chat.OpenAIChatService
import com.example.mygemma3n.service.AudioCaptureService
import com.example.mygemma3n.shared_utilities.TextToSpeechManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import timber.log.Timber
import javax.inject.Inject

data class UnifiedChatState(
    val conversation: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val isRecording: Boolean = false,
    val isTranscribing: Boolean = false,
    val userTypedInput: String = "",
    val error: String? = null,
    val isSpeaking: Boolean = false,
    val speakingText: String = ""
)

@HiltViewModel
class UnifiedChatViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gemmaService: UnifiedGemmaService,
    private val geminiApiService: GeminiApiService,
    private val settingsRepository: SettingsRepository,
    private val speechService: SpeechRecognitionService,
    private val onlineChatService: OnlineChatService,
    private val openAIChatService: OpenAIChatService,
    private val textToSpeechManager: TextToSpeechManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(UnifiedChatState())
    val uiState: StateFlow<UnifiedChatState> = _uiState.asStateFlow()

    private var recordingJob: Job? = null
    private val audioBuffer = mutableListOf<FloatArray>()

    fun updateUserTypedInput(text: String) {
        _uiState.value = _uiState.value.copy(userTypedInput = text)
    }

    fun sendMessage() {
        val currentInput = _uiState.value.userTypedInput.trim()
        if (currentInput.isEmpty()) return

        // Add user message immediately
        val userMessage = ChatMessage.User(
            content = currentInput,
            timestamp = System.currentTimeMillis()
        )
        
        _uiState.value = _uiState.value.copy(
            conversation = _uiState.value.conversation + userMessage,
            userTypedInput = "",
            isLoading = true,
            error = null
        )

        // Generate AI response
        viewModelScope.launch {
            try {
                val response = generateResponse(currentInput)
                val aiMessage = ChatMessage.AI(
                    content = response,
                    timestamp = System.currentTimeMillis()
                )
                
                _uiState.value = _uiState.value.copy(
                    conversation = _uiState.value.conversation + aiMessage,
                    isLoading = false
                )
            } catch (e: Exception) {
                Timber.e(e, "Failed to generate AI response")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to generate response: ${e.message}"
                )
            }
        }
    }

    private suspend fun generateResponse(input: String): String {
        return try {
            // Respect user's provider preference first
            when {
                // Use OpenAI if user selected it and it's available
                shouldUseOpenAI() -> {
                    openAIChatService.generateChatResponseOnline(input, emptyList())
                }
                // Use Gemini if user selected it (default) and it's available
                geminiApiService.isInitialized() -> {
                    onlineChatService.generateChatResponseOnline(input, emptyList())
                }
                // Fall back to local Gemma if online services aren't available
                gemmaService.isInitialized() -> {
                    val prompt = "User: $input\n\nAssistant: "
                    try {
                        gemmaService.generateTextAsync(prompt)
                    } catch (e: Exception) {
                        "I'm sorry, I'm having trouble generating a response right now."
                    }
                }
                else -> {
                    "I'm not fully initialized yet. Please check your AI model setup in Settings."
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error generating response")
            "I encountered an error while generating a response. Please try again."
        }
    }
    
    private suspend fun shouldUseOpenAI(): Boolean {
        return settingsRepository.modelProviderFlow.first() == "openai"
    }

    fun startRecording() {
        if (_uiState.value.isRecording) return

        Timber.d("UnifiedChat: Starting audio recording")

        // Clear any previous errors
        _uiState.update { it.copy(error = null) }

        // Start audio capture service
        Intent(context, AudioCaptureService::class.java).apply {
            action = AudioCaptureService.ACTION_START_CAPTURE
            context.startService(this)
        }

        audioBuffer.clear()
        _uiState.update { it.copy(userTypedInput = "", isRecording = true) }

        recordingJob = AudioCaptureService.audioDataFlow
            .filterNotNull()
            .onEach { chunk ->
                audioBuffer.add(chunk.clone())
                Timber.v("UnifiedChat: Audio buffer size: ${audioBuffer.size}")
            }
            .launchIn(viewModelScope + Dispatchers.IO)

        Timber.d("UnifiedChat: Recording started, collecting audio data...")
    }

    fun stopRecording() {
        if (!_uiState.value.isRecording) return

        Timber.d("UnifiedChat: Stopping audio recording")

        Intent(context, AudioCaptureService::class.java).apply {
            action = AudioCaptureService.ACTION_STOP_CAPTURE
            context.startService(this)
        }

        recordingJob?.cancel()
        recordingJob = null
        _uiState.update { it.copy(isRecording = false, isTranscribing = true) }

        viewModelScope.launch {
            try {
                Timber.d("UnifiedChat: Audio buffer contains ${audioBuffer.size} chunks")

                if (audioBuffer.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            isTranscribing = false,
                            error = "No audio recorded. Please try again."
                        )
                    }
                    return@launch
                }

                val transcript = transcribeAudio()

                if (transcript.isNotBlank()) {
                    Timber.d("UnifiedChat: Transcription successful: $transcript")
                    _uiState.update {
                        it.copy(
                            userTypedInput = transcript,
                            isTranscribing = false
                        )
                    }
                } else {
                    Timber.w("UnifiedChat: Transcription returned empty result")
                    _uiState.update {
                        it.copy(
                            isTranscribing = false,
                            error = "Could not transcribe audio. Please try again or check your internet connection."
                        )
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "UnifiedChat: Error during transcription")
                _uiState.update {
                    it.copy(
                        isTranscribing = false,
                        error = "Transcription failed: ${e.message}"
                    )
                }
            }
        }
    }

    private suspend fun transcribeAudio(): String {
        if (!speechService.isInitialized) {
            Timber.e("UnifiedChat: SpeechRecognitionService is not initialized")
            _uiState.update {
                it.copy(error = "Voice transcription is not available. Please check API settings.")
            }
            return ""
        }
        
        try {
            // Convert float audio data to PCM16 byte array
            val data = audioBuffer.flatMap { it.asList() }.toFloatArray()
            Timber.d("UnifiedChat: Processing ${data.size} audio samples")
            
            if (data.isEmpty()) {
                Timber.w("UnifiedChat: No audio data to transcribe")
                return ""
            }
            
            val pcm = ByteArray(data.size * 2)
            data.forEachIndexed { i, f ->
                val sample = (f * 32767f).toInt().coerceIn(-32768, 32767)
                pcm[i * 2] = (sample and 0xFF).toByte()
                pcm[i * 2 + 1] = (sample shr 8 and 0xFF).toByte()
            }
            
            val result = speechService.transcribeAudioData(pcm, "en-US")
            Timber.d("UnifiedChat: Transcription result: $result")
            return result
        } catch (e: Exception) {
            Timber.e(e, "UnifiedChat: Error in transcribeAudio")
            throw e
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun speakText(text: String) {
        if (text.isNotBlank()) {
            Timber.d("UnifiedChat: Speaking text: ${text.take(50)}...")
            
            // Update state to show speaking indicator
            _uiState.update { 
                it.copy(
                    isSpeaking = true,
                    speakingText = text.take(100) + if (text.length > 100) "..." else ""
                )
            }
            
            textToSpeechManager.speak(text)
            
            // Auto-clear speaking state after a delay (TTS will handle the actual timing)
            viewModelScope.launch {
                // Estimate speaking time: ~150 words per minute, average 5 characters per word
                val estimatedDuration = (text.length / 5 / 150.0 * 60 * 1000).toLong().coerceAtLeast(2000)
                kotlinx.coroutines.delay(estimatedDuration)
                _uiState.update { 
                    it.copy(isSpeaking = false, speakingText = "")
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        recordingJob?.cancel()
        // Stop audio capture service
        Intent(context, AudioCaptureService::class.java).apply {
            action = AudioCaptureService.ACTION_STOP_CAPTURE
            context.startService(this)
        }
    }
}