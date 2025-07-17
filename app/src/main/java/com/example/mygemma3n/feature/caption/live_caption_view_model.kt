package com.example.mygemma3n.feature.caption

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mygemma3n.BuildConfig
import com.example.mygemma3n.data.UnifiedGemmaService
import com.example.mygemma3n.service.AudioCaptureService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import com.example.mygemma3n.dataStore            // ← ADD
import com.example.mygemma3n.SPEECH_API_KEY

@HiltViewModel
class LiveCaptionViewModel @Inject constructor(
    application: Application,
    private val speechRecognitionService: SpeechRecognitionService,
    private val unifiedGemmaService: UnifiedGemmaService,
    private val translationCache: TranslationCache
) : AndroidViewModel(application) {

    private val _captionState = MutableStateFlow(CaptionState())
    val captionState: StateFlow<CaptionState> = _captionState.asStateFlow()

    private var audioProcessingJob: Job? = null
    private var transcriptionBuffer = StringBuilder()
    private var lastTranscriptionTime = 0L

    data class CaptionState(
        val isListening: Boolean = false,
        val currentTranscript: String = "",
        val translatedText: String = "",
        val sourceLanguage: Language = Language.AUTO,
        val targetLanguage: Language = Language.ENGLISH,
        val latencyMs: Long = 0,
        val error: String? = null,
        val isModelReady: Boolean = false
    )

    init {
        // Check if model is ready
        viewModelScope.launch {
            try {

                // 1️⃣  get a Context
                val context = getApplication<Application>()

                // 2️⃣  read the key from DataStore
                if (!speechRecognitionService.isInitialized) {
                    val key = context.dataStore.data
                        .map { it[SPEECH_API_KEY] ?: "" }
                        .first()

                    if (key.isNotBlank()) {
                        speechRecognitionService.initializeWithApiKey(key)
                    } else {
                        _captionState.update {
                            it.copy(error = "Please enter your Speech API key in Settings")
                        }
                        return@launch
                    }
                }


                if (!unifiedGemmaService.isInitialized()) {
                    _captionState.update { it.copy(error = "Initializing AI model...") }
                    unifiedGemmaService.initializeBestAvailable()
                }
                _captionState.update {
                    it.copy(
                        isModelReady = true,
                        error = null
                    )
                }
                Timber.d("Gemma model ready for live caption")
            } catch (e: Exception) {
                Timber.e(e, "Failed to initialize Gemma model")
                _captionState.update {
                    it.copy(
                        error = "Failed to initialize AI model: ${e.message}",
                        isModelReady = false
                    )
                }
            }
        }

        // Monitor service state
        viewModelScope.launch {
            AudioCaptureService.isRunning.collect { isRunning ->
                _captionState.update { it.copy(isListening = isRunning) }

                if (isRunning) {
                    startProcessingAudio()
                } else {
                    stopProcessingAudio()
                }
            }
        }
    }

    fun startLiveCaption() {
        if (_captionState.value.isListening) return

        if (!_captionState.value.isModelReady) {
            _captionState.update {
                it.copy(error = "AI model not ready. Please wait...")
            }
            return
        }

        try {
            val context = getApplication<Application>()
            val intent = Intent(context, AudioCaptureService::class.java).apply {
                action = AudioCaptureService.ACTION_START_CAPTURE
            }
            context.startService(intent)

            _captionState.update { it.copy(error = null) }
        } catch (e: Exception) {
            _captionState.update {
                it.copy(error = "Failed to start service: ${e.message}")
            }
        }
    }

    fun stopLiveCaption() {
        val context = getApplication<Application>()
        val intent = Intent(context, AudioCaptureService::class.java).apply {
            action = AudioCaptureService.ACTION_STOP_CAPTURE
        }
        context.startService(intent)

        // Clear transcript when stopping
        transcriptionBuffer.clear()
        _captionState.update {
            it.copy(
                currentTranscript = "",
                translatedText = ""
            )
        }
    }

    private fun startProcessingAudio() {
        audioProcessingJob?.cancel()          // avoid duplicates
        audioProcessingJob = viewModelScope.launch {
            speechRecognitionService
                .transcribeLiveCaptions(
                    AudioCaptureService.audioDataFlow.filterNotNull(),
                    languageCode = when (val src = _captionState.value.sourceLanguage) {
                        Language.AUTO   -> "en-US"            // fallback
                        else            -> "${src.code}-US"   // quick 2‑letter → xx‑US map
                    }
                )
                .collect { result ->
                    if (result.transcript.isBlank()) return@collect

                    val now = System.currentTimeMillis()
                    _captionState.update {
                        val newBuf = (it.currentTranscript + " " + result.transcript)
                            .takeLast(500).trim()
                        it.copy(
                            currentTranscript = newBuf,
                            latencyMs = now - result.confidence.toLong() /* crude RTT */,
                            error = null
                        )
                    }

                    // translate if requested
                    if (_captionState.value.targetLanguage != _captionState.value.sourceLanguage) {
                        translateText(_captionState.value.currentTranscript)
                    }
                }
        }
    }


    private fun stopProcessingAudio() {
        audioProcessingJob?.cancel()
        audioProcessingJob = null
    }

    fun setSourceLanguage(language: Language) {
        _captionState.update {
            it.copy(
                sourceLanguage = language,
                translatedText = "" // Clear translation when language changes
            )
        }
    }

    fun setTargetLanguage(language: Language) {
        _captionState.update {
            it.copy(
                targetLanguage = language,
                translatedText = "" // Clear translation when language changes
            )
        }

        // Retranslate current text if available
        val currentTranscript = _captionState.value.currentTranscript
        if (currentTranscript.isNotEmpty() &&
            language != _captionState.value.sourceLanguage) {
            viewModelScope.launch {
                translateText(currentTranscript)
            }
        }
    }

    private suspend fun processAudioChunk(audioChunk: FloatArray): String {
        // Use the offline Gemma model for transcription
        return try {
            // For better results, we'll use a more sophisticated prompt
            val languageHint = when (_captionState.value.sourceLanguage) {
                Language.AUTO -> ""
                else -> "The speaker is speaking in ${_captionState.value.sourceLanguage.displayName}. "
            }

            // Get recent context for better accuracy
            val recentContext = transcriptionBuffer.toString().takeLast(100)

            // Since Gemma doesn't have direct audio transcription, we'll use audio features
            // In a real implementation, you might want to use a separate audio feature extractor
            val audioFeatures = analyzeAudioFeatures(audioChunk)

            val prompt = """
                Audio Analysis Task:
                ${languageHint}Based on audio characteristics indicating ${audioFeatures}, 
                ${if (recentContext.isNotEmpty()) "continuing from: '$recentContext'," else ""}
                what is the most likely spoken text?
                
                Output only the transcribed words, nothing else:
            """.trimIndent()

            val result = unifiedGemmaService.generateTextAsync(
                prompt = prompt,
                config = UnifiedGemmaService.GenerationConfig(
                    maxTokens = 30, // Keep it short for real-time
                    temperature = 0.1f, // Low temperature for accuracy
                    topK = 10
                )
            )

            // Clean up the result
            result.trim()
                .replace("\"", "")
                .replace(".", "")
                .take(50) // Limit length for real-time display

        } catch (e: Exception) {
            Timber.e(e, "Failed to process audio chunk")
            ""
        }
    }

    private fun analyzeAudioFeatures(audioData: FloatArray): String {
        // Simple audio feature analysis
        val rms = AudioUtils.calculateRMS(audioData)
        val isSilent = AudioUtils.detectSilence(audioData)

        return when {
            isSilent -> "silence"
            rms < 0.1f -> "quiet speech"
            rms < 0.3f -> "normal speech"
            else -> "loud speech"
        }
    }

    private suspend fun translateText(text: String) {
        if (text.isBlank()) return

        // Check cache first
        val cacheKey = "${text.take(100)}_${_captionState.value.sourceLanguage.code}_${_captionState.value.targetLanguage.code}"
        translationCache.get(cacheKey)?.let { cached ->
            _captionState.update { it.copy(translatedText = cached) }
            return
        }

        // Generate translation using offline model
        val translationPrompt = """
            Translate the following text from ${_captionState.value.sourceLanguage.displayName} to ${_captionState.value.targetLanguage.displayName}.
            Keep the translation natural and conversational.
            
            Text: "$text"
            
            Translation:
        """.trimIndent()

        try {
            val translation = unifiedGemmaService.generateTextAsync(
                prompt = translationPrompt,
                config = UnifiedGemmaService.GenerationConfig(
                    maxTokens = 100,
                    temperature = 0.3f
                )
            ).trim()

            if (translation.isNotEmpty()) {
                translationCache.put(cacheKey, translation)
                _captionState.update { it.copy(translatedText = translation) }
            }
        } catch (e: Exception) {
            Timber.e(e, "Translation failed")
            _captionState.update {
                it.copy(error = "Translation error: ${e.message}")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopLiveCaption()
    }
}

// Extension function for Flow operations
suspend fun <T> Flow<T>.toList(): List<T> {
    val list = mutableListOf<T>()
    collect { list.add(it) }
    return list
}
