package com.example.mygemma3n.feature.caption

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mygemma3n.gemma.GemmaEngine
import com.example.mygemma3n.service.AudioCaptureService
import com.example.mygemma3n.shared_utilities.toGoogleLanguageCode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import kotlin.math.log10
import kotlin.math.max

@HiltViewModel
class LiveCaptionViewModel @Inject constructor(
    application: Application,
    private val gemmaEngine: GemmaEngine,
    private val translationCache: TranslationCache,
    private val speechService: SpeechRecognitionService
) : AndroidViewModel(application) {

    private val _captionState = MutableStateFlow(CaptionState())
    val captionState: StateFlow<CaptionState> = _captionState.asStateFlow()

    private var audioProcessingJob: Job? = null

    init {
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
            _captionState.update { it.copy(error = "Failed to start service: ${e.message}") }
        }
    }

    fun stopLiveCaption() {
        val context = getApplication<Application>()
        val intent = Intent(context, AudioCaptureService::class.java).apply {
            action = AudioCaptureService.ACTION_STOP_CAPTURE
        }
        context.startService(intent)
    }

    /* ───────── AUDIO + STT PIPELINE ───────── */
    private fun startProcessingAudio() {
        if (!speechService.isInitialized) {
            _captionState.update {
                it.copy(
                    error = "Speech-to-text service is not initialized. Please open API Settings and enter a valid Speech API key."
                )
            }
            return
        }

        audioProcessingJob = viewModelScope.launch {
            _captionState.update { it.copy(error = null, isProcessingAudio = true) }

            var currentInterimTranscript = ""
            var lastFinalTranscript = ""
            var lastAudioUpdate = 0L

            /* ─── 1.  AUDIO-LEVEL UI  ─── */
            launch {
                AudioCaptureService.audioDataFlow
                    .filterNotNull()
                    .collect { audioData ->
                        val now = System.currentTimeMillis()
                        if (now - lastAudioUpdate > 100) {          // update ~10 Hz
                            val rms = AudioUtils.calculateRMS(audioData)
                            val dbfs = if (rms <= 0f) -90f else max(-90f, 20f * log10(rms))
                            val normalizedLevel = ((dbfs + 90f) / 70f).coerceIn(0f, 1f)

                            _captionState.update { state ->
                                state.copy(
                                    lastAudioLevel = normalizedLevel,
                                    isProcessingAudio = dbfs > -60f
                                )
                            }
                            lastAudioUpdate = now
                        }
                    }
            }

            /* ─── 2.  SPEECH-TO-TEXT  ─── */
            speechService
                .transcribeLiveCaptions(                                   // ← updated call
                    AudioCaptureService.audioDataFlow.filterNotNull(),
                    _captionState.value.sourceLanguage.toGoogleLanguageCode()
                )
                .catch { e ->
                    Timber.tag("LiveCaption").e(e, "Transcription error")
                    _captionState.update {
                        it.copy(
                            error = "Transcription error: ${e.message}",
                            isProcessingAudio = false
                        )
                    }
                }
                .collect { result ->
                    val startTime = System.currentTimeMillis()

                    val displayTranscript = if (result.isFinal) {
                        lastFinalTranscript = result.transcript
                        currentInterimTranscript = ""
                        result.transcript
                    } else {
                        currentInterimTranscript = result.transcript
                        if (lastFinalTranscript.isNotEmpty()) {
                            "$lastFinalTranscript $currentInterimTranscript"
                        } else {
                            currentInterimTranscript
                        }
                    }

                    val latency = System.currentTimeMillis() - startTime

                    _captionState.update { state ->
                        Timber.tag("LiveCaption").d(
                            "Transcription result: ${result.transcript} " +
                                    "(final=${result.isFinal}, confidence=${result.confidence})"
                        )
                        state.copy(
                            currentTranscript = displayTranscript,
                            latencyMs = latency,
                            error = null,
                            isInterimResult = !result.isFinal
                        )
                    }

                    // Auto-translate final results
                    val state = _captionState.value
                    if (result.isFinal &&
                        state.targetLanguage != state.sourceLanguage &&
                        state.sourceLanguage != Language.AUTO &&
                        result.transcript.isNotBlank()
                    ) {
                        translateText(result.transcript)
                    }
                }
        }
    }

    private fun stopProcessingAudio() {
        audioProcessingJob?.cancel()
        audioProcessingJob = null
        _captionState.update { it.copy(isProcessingAudio = false, lastAudioLevel = 0f) }
    }

    /* ───────── DATA CLASS ───────── */
    data class CaptionState(
        val isListening: Boolean = false,
        val currentTranscript: String = "",
        val translatedText: String = "",
        val sourceLanguage: Language = Language.AUTO,
        val targetLanguage: Language = Language.ENGLISH,
        val latencyMs: Long = 0,
        val error: String? = null,
        val isProcessingAudio: Boolean = false,
        val lastAudioLevel: Float = 0f,
        val isInterimResult: Boolean = false
    )

    /* ───────── LANGUAGE PICKERS ───────── */
    fun setSourceLanguage(language: Language) {
        _captionState.update {
            it.copy(sourceLanguage = language, translatedText = "")
        }
    }

    fun setTargetLanguage(language: Language) {
        _captionState.update {
            it.copy(targetLanguage = language, translatedText = "")
        }

        val currentTranscript = _captionState.value.currentTranscript
        if (currentTranscript.isNotEmpty() &&
            language != _captionState.value.sourceLanguage
        ) {
            viewModelScope.launch { translateText(currentTranscript) }
        }
    }

    /* ───────── TRANSLATION ───────── */
    private suspend fun translateText(text: String) {
        if (text.isBlank()) return

        val cacheKey =
            "${text.take(100)}_${_captionState.value.sourceLanguage.code}_${_captionState.value.targetLanguage.code}"
        translationCache.get(cacheKey)?.let { cached ->
            _captionState.update { it.copy(translatedText = cached) }
            return
        }

        val translationPrompt = """
            Translate the following text from ${_captionState.value.sourceLanguage.displayName} 
            to ${_captionState.value.targetLanguage.displayName}. 
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
            _captionState.update { it.copy(error = "Translation error: ${e.message}") }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopLiveCaption()
    }
}

/* ───────── EXTENSION ───────── */
private suspend fun <T> Flow<T>.toList(): List<T> {
    val list = mutableListOf<T>()
    collect { list.add(it) }
    return list
}
