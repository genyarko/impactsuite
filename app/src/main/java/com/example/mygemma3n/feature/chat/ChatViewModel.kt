package com.example.mygemma3n.feature.chat

import android.content.Context
import android.content.Intent
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mygemma3n.data.ChatRepository
import com.example.mygemma3n.data.UnifiedGemmaService
import com.example.mygemma3n.data.GeminiApiService
import com.example.mygemma3n.data.GeminiApiConfig
import com.example.mygemma3n.domain.repository.SettingsRepository
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.mygemma3n.data.SpeechRecognitionService
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.delay
import timber.log.Timber
import javax.inject.Inject

/** Chat view‑model backed by Room for permanent history. */
@HiltViewModel
class ChatViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gemmaService: UnifiedGemmaService,
    private val geminiApiService: GeminiApiService,
    private val settingsRepository: SettingsRepository,
    private val speechService: SpeechRecognitionService,
    private val repo: ChatRepository,
    private val onlineChatService: OnlineChatService,
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

    /* ───────── Online/Offline Service Selection ───────── */
    private fun hasNetworkConnection(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    private suspend fun shouldUseOnlineService(): Boolean {
        return try {
            val useOnlineService = settingsRepository.useOnlineServiceFlow.first()
            val hasApiKey = settingsRepository.apiKeyFlow.first().isNotBlank()
            val hasNetwork = hasNetworkConnection()
            
            useOnlineService && hasApiKey && hasNetwork
        } catch (e: Exception) {
            Timber.w(e, "Error checking service preference, defaulting to offline")
            false
        }
    }

    private suspend fun initializeApiServiceIfNeeded() {
        if (!geminiApiService.isInitialized()) {
            val apiKey = settingsRepository.apiKeyFlow.first()
            if (apiKey.isNotBlank()) {
                try {
                    geminiApiService.initialize(GeminiApiConfig(apiKey = apiKey))
                    Timber.d("GeminiApiService initialized for Chat")
                } catch (e: Exception) {
                    Timber.e(e, "Failed to initialize GeminiApiService")
                    throw e
                }
            } else {
                throw IllegalStateException("API key not found")
            }
        }
    }

    private suspend fun warmUpApiService() {
        try {
            val warmupPrompt = "Hi" // Minimal prompt
            geminiApiService.generateTextComplete(warmupPrompt, "chat_warmup")
            Timber.d("Chat API service warmed up successfully")
        } catch (e: Exception) {
            Timber.w(e, "Chat API warmup failed, but service should still work")
        }
    }

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

        // Preload API service for faster first response
        viewModelScope.launch {
            delay(1000) // Give settings time to load
            if (shouldUseOnlineService()) {
                try {
                    Timber.d("Preloading API service for Chat")
                    initializeApiServiceIfNeeded()
                    warmUpApiService()
                } catch (e: Exception) {
                    Timber.w(e, "Failed to preload API service for Chat")
                }
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
                // 2. Check if we should use online or offline generation
                val useOnline = shouldUseOnlineService()
                val reply = if (useOnline) {
                    try {
                        initializeApiServiceIfNeeded()
                        val conversationHistory = _uiState.value.conversation
                        onlineChatService.generateChatResponseOnline(
                            userMessage = text,
                            conversationHistory = conversationHistory,
                            maxTokens = 150,
                            temperature = 0.7f
                        )
                    } catch (e: Exception) {
                        Timber.w(e, "Online chat generation failed, falling back to offline")
                        // Fallback to offline generation
                        generateOfflineResponse(text)
                    }
                } else {
                    // Use offline generation
                    generateOfflineResponse(text)
                }

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

    private suspend fun generateOfflineResponse(text: String): String {
        return gemmaService.generateTextAsync(
            text,
            UnifiedGemmaService.GenerationConfig(
                maxTokens = 150,
                temperature = 0.7f
            )
        )
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    // ───── Enhanced online chat features ─────────────────────────────────────

    fun sendMessageWithStreaming() {
        val text = _uiState.value.userTypedInput.trim()
        if (text.isBlank()) return

        viewModelScope.launch {
            // 1. Persist & update UI with user turn
            val userMsg = ChatMessage.User(text)
            repo.addMessage(sessionId, userMsg)
            _uiState.update { it.copy(userTypedInput = "", isLoading = true, error = null) }

            try {
                // 2. Check if we should use online streaming
                val useOnline = shouldUseOnlineService()
                if (useOnline) {
                    try {
                        initializeApiServiceIfNeeded()
                        val conversationHistory = _uiState.value.conversation
                        
                        // Create initial AI message for streaming
                        val streamingMsg = ChatMessage.AI("")
                        repo.addMessage(sessionId, streamingMsg)
                        
                        var fullResponse = ""
                        onlineChatService.generateChatResponseStreamOnline(
                            userMessage = text,
                            conversationHistory = conversationHistory,
                            temperature = 0.7f
                        ).collect { chunk ->
                            fullResponse += chunk
                            // Update the last AI message with accumulated response
                            val updatedMsg = ChatMessage.AI(fullResponse, streamingMsg.timestamp)
                            // Note: In a real implementation, you'd need to update the message in the database
                            // For now, this provides the streaming framework
                        }
                        
                    } catch (e: Exception) {
                        Timber.w(e, "Online streaming failed, falling back to regular generation")
                        // Fallback to regular generation
                        sendMessage()
                        return@launch
                    }
                } else {
                    // Use offline generation
                    val reply = generateOfflineResponse(text)
                    val aiMsg = ChatMessage.AI(reply)
                    repo.addMessage(sessionId, aiMsg)
                }
            } catch (e: Exception) {
                Timber.e(e, "Error generating streaming response")
                _uiState.update { it.copy(error = "Failed to generate response: ${e.message}") }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun generateSmartReplies() {
        viewModelScope.launch {
            try {
                val useOnline = shouldUseOnlineService()
                if (useOnline) {
                    initializeApiServiceIfNeeded()
                    val lastMessage = _uiState.value.conversation.lastOrNull()
                    if (lastMessage is ChatMessage.AI) {
                        val suggestions = onlineChatService.generateSmartReply(
                            userMessage = lastMessage.content,
                            conversationHistory = _uiState.value.conversation
                        )
                        // Update UI state with smart reply suggestions
                        _uiState.update { it.copy(smartReplies = suggestions) }
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "Failed to generate smart replies")
                // Ignore errors for optional feature
            }
        }
    }

    fun useSmartReply(suggestion: String) {
        _uiState.update { 
            it.copy(
                userTypedInput = suggestion,
                smartReplies = emptyList()
            )
        }
    }

    fun clearSmartReplies() {
        _uiState.update { it.copy(smartReplies = emptyList()) }
    }
}

/** Immutable UI snapshot for Compose. */
data class ChatState(
    val conversation: List<ChatMessage> = emptyList(),
    val userTypedInput: String = "",
    val isRecording: Boolean = false,
    val isTranscribing: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val smartReplies: List<String> = emptyList()
)