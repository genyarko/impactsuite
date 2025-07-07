package com.example.mygemma3n.feature.caption

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mygemma3n.gemma.GemmaEngine
import com.example.mygemma3n.service.AudioCaptureService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LiveCaptionViewModel @Inject constructor(
    application: Application,
    private val gemmaEngine: GemmaEngine,
    private val translationCache: TranslationCache,
    private val speechService: SpeechRecognitionService // <-- Inject the speech-to-text service
) : AndroidViewModel(application) {

    private val _captionState = MutableStateFlow(CaptionState())
    val captionState: StateFlow<CaptionState> = _captionState.asStateFlow()

    private var audioProcessingJob: Job? = null

    data class CaptionState(
        val isListening: Boolean = false,
        val currentTranscript: String = "",
        val translatedText: String = "",
        val sourceLanguage: Language = Language.AUTO,
        val targetLanguage: Language = Language.ENGLISH,
        val latencyMs: Long = 0,
        val error: String? = null
    )

    init {
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
    }

    private fun startProcessingAudio() {
        // Check if speech service is initialized BEFORE starting
        if (!speechService.isInitialized) {
            _captionState.update {
                it.copy(
                    error = "Speech-to-text service is not initialized. Please open API Settings and enter a valid Speech API key."
                )
            }
            return
        }

        audioProcessingJob = viewModelScope.launch {
            _captionState.update { it.copy(error = null) }

            speechService
                .transcribeAudioStream(
                    AudioCaptureService.audioDataFlow.filterNotNull(),
                    _captionState.value.sourceLanguage.toGoogleLanguageCode()
                )
                .catch { e ->
                    _captionState.update { it.copy(error = "Transcription error: ${e.message}") }
                }
                .collect { result ->
                    _captionState.update { state ->
                        state.copy(
                            currentTranscript = result.transcript,
                            // You can set latencyMs if you compute it elsewhere
                            error = null
                        )
                    }
                    val state = _captionState.value
                    // Auto-translate if needed
                    if (state.targetLanguage != state.sourceLanguage && state.sourceLanguage != Language.AUTO) {
                        translateText(result.transcript)
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

    private suspend fun translateText(text: String) {
        if (text.isBlank()) return

        // Check cache first
        val cacheKey = "${text.take(100)}_${_captionState.value.sourceLanguage.code}_${_captionState.value.targetLanguage.code}"
        translationCache.get(cacheKey)?.let { cached ->
            _captionState.update { it.copy(translatedText = cached) }
            return
        }

        // Generate translation using Gemini API
        val translationPrompt = """
            Translate the following text from ${_captionState.value.sourceLanguage.displayName} to ${_captionState.value.targetLanguage.displayName}.
            Maintain the original meaning and tone.
            Output only the translation, nothing else.

            Text: "$text"
        """.trimIndent()

        try {
            val translation = gemmaEngine.generateText(
                prompt = translationPrompt,
                config = GemmaEngine.GenerationConfig(
                    maxNewTokens = 100,
                    temperature = 0.3f,
                    doSample = false
                )
            ).toList().joinToString("").trim()

            if (translation.isNotEmpty()) {
                translationCache.put(cacheKey, translation)
                _captionState.update { it.copy(translatedText = translation) }
            }
        } catch (e: Exception) {
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

// Extension function to collect Flow into a List
private suspend fun <T> Flow<T>.toList(): List<T> {
    val list = mutableListOf<T>()
    collect { list.add(it) }
    return list
}


fun Language.toGoogleLanguageCode(): String = when (this) {
    Language.AUTO -> "auto"
    Language.ENGLISH -> "en-US"
    Language.SPANISH -> "es-ES"
    Language.FRENCH -> "fr-FR"
    Language.GERMAN -> "de-DE"
    Language.CHINESE -> "zh-CN"
    Language.JAPANESE -> "ja-JP"
    Language.KOREAN -> "ko-KR"
    Language.HINDI -> "hi-IN"
    Language.ARABIC -> "ar-SA"
    Language.PORTUGUESE -> "pt-BR"
    Language.RUSSIAN -> "ru-RU"
    Language.ITALIAN -> "it-IT"
    Language.DUTCH -> "nl-NL"
    Language.SWEDISH -> "sv-SE"
    Language.POLISH -> "pl-PL"
    Language.TURKISH -> "tr-TR"
    Language.INDONESIAN -> "id-ID"
    Language.VIETNAMESE -> "vi-VN"
    Language.THAI -> "th-TH"
    Language.HEBREW -> "he-IL"
}

