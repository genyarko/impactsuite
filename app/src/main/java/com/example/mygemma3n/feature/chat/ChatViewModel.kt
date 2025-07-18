package com.example.mygemma3n.feature.chat

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import javax.inject.Inject

/** Chat view model handling basic conversation. */
@HiltViewModel
class ChatViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gemmaService: UnifiedGemmaService,
    private val speechService: SpeechRecognitionService
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatState())
    val uiState: StateFlow<ChatState> = _uiState.asStateFlow()

    private var recordingJob: Job? = null
    private val audioBuffer = mutableListOf<FloatArray>()

    fun updateUserTypedInput(text: String) {
        _uiState.update { it.copy(userTypedInput = text) }
    }

    fun startRecording() {
        if (_uiState.value.isRecording) return

        Intent(context, AudioCaptureService::class.java).apply {
            action = AudioCaptureService.ACTION_START_CAPTURE
            context.startService(this)
        }
        audioBuffer.clear()
        _uiState.update { it.copy(userTypedInput = "", isRecording = true) }

        recordingJob = AudioCaptureService.audioDataFlow
            .filterNotNull()
            .onEach { chunk -> audioBuffer.add(chunk.clone()) }
            .launchIn(viewModelScope + Dispatchers.IO)
    }

    fun stopRecording() {
        if (!_uiState.value.isRecording) return

        Intent(context, AudioCaptureService::class.java).apply {
            action = AudioCaptureService.ACTION_STOP_CAPTURE
            context.startService(this)
        }
        recordingJob?.cancel()
        recordingJob = null
        _uiState.update { it.copy(isRecording = false) }

        viewModelScope.launch {
            val transcript = transcribeAudio()
            if (transcript.isNotBlank()) {
                _uiState.update { it.copy(userTypedInput = transcript) }
            }
        }
    }

    private suspend fun transcribeAudio(): String {
        if (!speechService.isInitialized) return ""
        val data = audioBuffer.flatten().toFloatArray()
        val pcm = ByteArray(data.size * 2)
        data.forEachIndexed { i, f ->
            val v = (f.coerceIn(-1f, 1f) * 32767).toInt()
            pcm[i * 2] = (v and 0xFF).toByte()
            pcm[i * 2 + 1] = ((v shr 8) and 0xFF).toByte()
        }
        return speechService.transcribeAudioData(pcm, "en-US")
    }

    fun sendMessage() {
        val text = _uiState.value.userTypedInput.trim()
        if (text.isBlank()) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    conversation = it.conversation + ChatMessage.User(text),
                    userTypedInput = ""
                )
            }
            try {
                val reply = gemmaService.generateTextAsync(
                    text,
                    UnifiedGemmaService.GenerationConfig(
                        maxTokens = 150,
                        temperature = 0.7f
                    )
                )
                _uiState.update {
                    it.copy(
                        conversation = it.conversation + ChatMessage.AI(reply)
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }
}

data class ChatState(
    val conversation: List<ChatMessage> = emptyList(),
    val userTypedInput: String = "",
    val isRecording: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null
)
