package com.example.mygemma3n.feature.chat

import android.content.Context
import android.content.Intent
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mygemma3n.data.ChatRepository
import com.example.mygemma3n.data.UnifiedGemmaService
import com.example.mygemma3n.feature.caption.SpeechRecognitionService
import com.example.mygemma3n.service.AudioCaptureService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import timber.log.Timber
import javax.inject.Inject

/** Chat view‑model backed by Room for permanent history. */
@HiltViewModel
class ChatViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gemmaService: UnifiedGemmaService,
    private val speechService: SpeechRecognitionService,
    private val repo: ChatRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    /** navArgument passed from ChatListScreen → "chat/{sessionId}" */
    private val sessionId: String = checkNotNull(
        savedStateHandle["sessionId"]
    ) { "ChatViewModel requires a sessionId NavArg." }

    private val _uiState = MutableStateFlow(ChatState())
    val uiState: StateFlow<ChatState> = _uiState.asStateFlow()

    private var recordingJob: Job? = null
    private val audioBuffer = mutableListOf<FloatArray>()

    init {
        // Stream messages from the DB straight into UI state
        viewModelScope.launch {
            repo.getMessagesForSession(sessionId)
                .collectLatest { msgs ->
                    _uiState.update { it.copy(conversation = msgs) }
                }
        }

        // Check if speech service is initialized
        viewModelScope.launch {
            if (!speechService.isInitialized) {
                Timber.w("SpeechRecognitionService is not initialized - transcription will not work")
                _uiState.update {
                    it.copy(error = "Voice transcription is not available. Please check API settings.")
                }
            } else {
                Timber.d("SpeechRecognitionService is initialized and ready")
            }
        }
    }

    // ───── User‑input helpers ────────────────────────────────────────────────
    fun updateUserTypedInput(text: String) {
        _uiState.update { it.copy(userTypedInput = text, error = null) }
    }

    // ───── Audio recording & STT ─────────────────────────────────────────────
    fun startRecording() {
        if (_uiState.value.isRecording) return

        Timber.d("Starting audio recording")

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
                Timber.v("Audio buffer size: ${audioBuffer.size}")
            }
            .launchIn(viewModelScope + Dispatchers.IO)

        Timber.d("Recording started, collecting audio data...")
    }

    fun stopRecording() {
        if (!_uiState.value.isRecording) return

        Timber.d("Stopping audio recording")

        Intent(context, AudioCaptureService::class.java).apply {
            action = AudioCaptureService.ACTION_STOP_CAPTURE
            context.startService(this)
        }

        recordingJob?.cancel()
        recordingJob = null
        _uiState.update { it.copy(isRecording = false, isTranscribing = true) }

        viewModelScope.launch {
            try {
                Timber.d("Audio buffer contains ${audioBuffer.size} chunks")

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
                    Timber.d("Transcription successful: $transcript")
                    _uiState.update {
                        it.copy(
                            userTypedInput = transcript,
                            isTranscribing = false
                        )
                    }
                } else {
                    Timber.w("Transcription returned empty result")
                    _uiState.update {
                        it.copy(
                            isTranscribing = false,
                            error = "Could not transcribe audio. Please try again or check your internet connection."
                        )
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error during transcription")
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
            Timber.e("SpeechRecognitionService is not initialized")
            _uiState.update {
                it.copy(error = "Voice transcription is not available. Please check API settings.")
            }
            return ""
        }

        try {
            // Convert float audio data to PCM16 byte array
            val data = audioBuffer.flatMap { it.asList() }.toFloatArray()
            Timber.d("Processing ${data.size} audio samples")

            if (data.isEmpty()) {
                Timber.w("No audio data to transcribe")
                return ""
            }

            val pcm = ByteArray(data.size * 2)
            data.forEachIndexed { i, f ->
                val v = (f.coerceIn(-1f, 1f) * 32767).toInt()
                pcm[i * 2] = (v and 0xFF).toByte()
                pcm[i * 2 + 1] = ((v shr 8) and 0xFF).toByte()
            }

            Timber.d("Sending ${pcm.size} bytes to speech service for transcription")
            val result = speechService.transcribeAudioData(pcm, "en-US")
            Timber.d("Transcription result: $result")

            return result
        } catch (e: Exception) {
            Timber.e(e, "Error in transcribeAudio")
            throw e
        }
    }

    // ───── Core send‑message flow ────────────────────────────────────────────
    fun sendMessage() {
        val text = _uiState.value.userTypedInput.trim()
        if (text.isBlank()) return

        viewModelScope.launch {
            // 1. Persist & update UI with user turn
            val userMsg = ChatMessage.User(text)
            repo.addMessage(sessionId, userMsg)
            _uiState.update { it.copy(userTypedInput = "", isLoading = true, error = null) }

            try {
                // 2. Call LLM
                val reply = gemmaService.generateTextAsync(
                    text,
                    UnifiedGemmaService.GenerationConfig(
                        maxTokens = 150,
                        temperature = 0.7f
                    )
                )
                val aiMsg = ChatMessage.AI(reply)

                // 3. Persist AI reply
                repo.addMessage(sessionId, aiMsg)
            } catch (e: Exception) {
                Timber.e(e, "Error generating AI response")
                _uiState.update { it.copy(error = "Failed to generate response: ${e.message}") }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}

/** Immutable UI snapshot for Compose. */
data class ChatState(
    val conversation: List<ChatMessage> = emptyList(),
    val userTypedInput: String = "",
    val isRecording: Boolean = false,
    val isTranscribing: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null
)