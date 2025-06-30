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
    private val translationCache: TranslationCache
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
        audioProcessingJob = viewModelScope.launch {
            AudioCaptureService.audioDataFlow
                .filterNotNull()
                .chunked(1600) // Process in 100ms chunks (16kHz * 0.1s)
                .transform { audioChunk ->
                    val startTime = System.currentTimeMillis()

                    // Process audio through Gemma for transcription
                    val transcript = processAudioChunk(audioChunk)

                    if (transcript.isNotEmpty()) {
                        emit(transcript)

                        val latency = System.currentTimeMillis() - startTime
                        _captionState.update { it.copy(latencyMs = latency) }
                    }
                }
                .scan("") { acc, newText ->
                    // Keep last 500 characters for context
                    val combined = acc + " " + newText
                    if (combined.length > 500) {
                        combined.takeLast(500)
                    } else {
                        combined
                    }.trim()
                }
                .collect { transcript ->
                    _captionState.update { it.copy(currentTranscript = transcript) }

                    // Translate if needed
                    if (_captionState.value.targetLanguage != _captionState.value.sourceLanguage &&
                        _captionState.value.sourceLanguage != Language.AUTO) {
                        translateText(transcript)
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
        // In production, this would use Gemma's audio processing capabilities
        // For now, we'll use a simplified approach

        val prompt = buildMultimodalPrompt(
            instruction = """
                Transcribe the audio to text. 
                Language: ${_captionState.value.sourceLanguage.displayName}
                Output only the transcribed text, nothing else.
            """.trimIndent(),
            audioData = audioChunk,
            previousContext = _captionState.value.currentTranscript.takeLast(100)
        )

        return try {
            gemmaEngine.generateText(
                prompt = prompt,
                config = GemmaEngine.GenerationConfig(
                    maxNewTokens = 50,
                    temperature = 0.1f,
                    doSample = false // Greedy decoding for accuracy
                )
            ).toList().joinToString("")
        } catch (e: Exception) {
            ""
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

        // Generate translation
        val translationPrompt = """
            Translate the following text from ${_captionState.value.sourceLanguage.displayName} to ${_captionState.value.targetLanguage.displayName}.
            Maintain the original meaning and tone.
            
            Text: "$text"
            
            Translation:
        """.trimIndent()

        try {
            val translation = gemmaEngine.generateText(
                prompt = translationPrompt,
                config = GemmaEngine.GenerationConfig(
                    maxNewTokens = 100,
                    temperature = 0.3f
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

// Extension function for Flow operations
suspend fun <T> Flow<T>.toList(): List<T> {
    val list = mutableListOf<T>()
    collect { list.add(it) }
    return list
}