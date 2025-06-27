package com.example.mygemma3n.feature

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mygemma3n.gemma.GemmaEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.intellij.lang.annotations.Language
import javax.inject.Inject

@HiltViewModel
class LiveCaptionViewModel @Inject constructor(
    private val gemmaEngine: GemmaEngine,
    private val audioCapture: AudioCapture,
    private val translationCache: TranslationCache
) : ViewModel() {

    private val _captionState = MutableStateFlow(CaptionState())
    val captionState: StateFlow<CaptionState> = _captionState.asStateFlow()

    data class CaptionState(
        val isListening: Boolean = false,
        val currentTranscript: String = "",
        val translatedText: String = "",
        val sourceLanguage: Language = Language.AUTO,
        val targetLanguage: Language = Language.ENGLISH,
        val latencyMs: Long = 0
    )

    fun startLiveCaption() {
        viewModelScope.launch {
            _captionState.update { it.copy(isListening = true) }

            audioCapture.startCapture()
                .chunked(160) // Process in 10ms chunks for low latency
                .transform { audioChunk ->
                    val startTime = System.currentTimeMillis()

                    // Multimodal input with audio
                    val prompt = buildMultimodalPrompt(
                        instruction = "Transcribe the following audio to text:",
                        audioData = audioChunk
                    )

                    gemmaEngine.generateText(
                        prompt = prompt,
                        maxTokens = 50,
                        temperature = 0.1f // Low temperature for accuracy
                    ).collect { token ->
                        emit(token)
                    }

                    val latency = System.currentTimeMillis() - startTime
                    _captionState.update { it.copy(latencyMs = latency) }
                }
                .scan("") { acc, token -> acc + token }
                .collect { transcript ->
                    _captionState.update { it.copy(currentTranscript = transcript) }

                    // Translate if needed
                    if (_captionState.value.targetLanguage != _captionState.value.sourceLanguage) {
                        translateText(transcript)
                    }
                }
        }
    }

    private suspend fun translateText(text: String) {
        // Check cache first
        val cacheKey = "${text}_${_captionState.value.targetLanguage.code}"
        translationCache.get(cacheKey)?.let { cached ->
            _captionState.update { it.copy(translatedText = cached) }
            return
        }

        // Generate translation
        val translationPrompt = """
            Translate the following text to ${_captionState.value.targetLanguage.name}:
            "$text"
            
            Translation:
        """.trimIndent()

        gemmaEngine.generateText(
            prompt = translationPrompt,
            maxTokens = 100,
            temperature = 0.3f
        ).toList().joinToString("").let { translation ->
            translationCache.put(cacheKey, translation)
            _captionState.update { it.copy(translatedText = translation) }
        }
    }
}