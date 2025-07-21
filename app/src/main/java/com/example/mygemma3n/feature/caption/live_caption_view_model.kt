package com.example.mygemma3n.feature.caption

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mygemma3n.data.UnifiedGemmaService
import com.example.mygemma3n.service.AudioCaptureService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import com.example.mygemma3n.dataStore
import com.example.mygemma3n.SPEECH_API_KEY

/**
 * Live‑caption ViewModel, now using **batch buffering** instead of the
 * streaming API so it performs acceptably on mid‑tier phones.
 *
 * Workflow
 * --------
 * 1. Collect raw audio chunks from [AudioCaptureService].
 * 2. Detect 300 ms+ of silence *or* 1 s of accumulated speech.
 * 3. When either mark is hit, flush the current buffer, hand it to
 *    [SpeechRecognitionService.transcribeAudioData] (same path CBT uses),
 *    and append the result to the on‑screen transcript (plus optional
 *    translation).
 *
 * Double‑buffering (`audioBuffer` + `pendingBuffer`) ensures that we never
 * block audio capture while we’re waiting for the recogniser.
 */
@HiltViewModel
class LiveCaptionViewModel @Inject constructor(
    application: Application,
    private val speechService: SpeechRecognitionService,
    private val gemmaService: UnifiedGemmaService,
    private val translationCache: TranslationCache
) : AndroidViewModel(application) {

    /* ───────── UI STATE ───────── */
    private val _state = MutableStateFlow(CaptionState())
    val captionState: StateFlow<CaptionState> = _state.asStateFlow()

    /* ───────── internal buffers ───────── */
    private val audioBuffer   = mutableListOf<FloatArray>()   // accumulating live chunks
    private val pendingBuffer = mutableListOf<FloatArray>()   // handed off for STT
    private var silenceStartTime: Long? = null                // first silent chunk ts
    private var audioJob: Job? = null

    /* ───────── constants ───────── */
    private val sampleRate = 16_000                           // 16 kHz mono

    /* ───────── init ───────── */
    init {
        initialiseEngines()
        observeRecorderState()
    }

    /* ─────────────────────────────────────────────── */
    /*  Public API                                */
    /* ─────────────────────────────────────────────── */

    fun startLiveCaption() {
        if (_state.value.isListening) return
        if (!_state.value.isModelReady) {
            _state.update { it.copy(error = "AI model not ready. Please wait.") }
            return
        }

        getApplication<Application>().apply {
            startService(Intent(this, AudioCaptureService::class.java).apply {
                action = AudioCaptureService.ACTION_START_CAPTURE
            })
        }
    }

    fun stopLiveCaption() {
        getApplication<Application>().apply {
            startService(Intent(this, AudioCaptureService::class.java).apply {
                action = AudioCaptureService.ACTION_STOP_CAPTURE
            })
        }

        audioJob?.cancel()
        audioJob = null
        audioBuffer.clear()
        pendingBuffer.clear()
        silenceStartTime = null
        _state.update { it.copy(currentTranscript = "", translatedText = "") }
    }

    fun setSourceLanguage(language: Language) =
        _state.update { it.copy(sourceLanguage = language, translatedText = "") }

    fun setTargetLanguage(language: Language) {
        _state.update { it.copy(targetLanguage = language, translatedText = "") }
        if (_state.value.currentTranscript.isNotBlank()
            && language != _state.value.sourceLanguage) {
            viewModelScope.launch { translateText(_state.value.currentTranscript) }
        }
    }

    /* ─────────────────────────────────────────────── */
    /*  Internal initialisation                    */
    /* ─────────────────────────────────────────────── */

    private fun initialiseEngines() = viewModelScope.launch {
        try {
            /* Speech‑to‑text key from DataStore (same flow CBT view‑model uses) */
            if (!speechService.isInitialized) {
                val key = getApplication<Application>().dataStore.data
                    .map { it[SPEECH_API_KEY] ?: "" }
                    .first()

                if (key.isBlank()) {
                    _state.update { it.copy(error = "Please enter your Speech API key in Settings") }
                    return@launch
                }
                speechService.initializeWithApiKey(key)
            }

            /* Gemma for translation only */
            if (!gemmaService.isInitialized()) {
                _state.update { it.copy(error = "Initialising AI model.") }
                gemmaService.initializeBestAvailable()
            }

            _state.update { it.copy(isModelReady = true, error = null) }
        } catch (e: Exception) {
            Timber.e(e, "Initialisation failed")
            _state.update { it.copy(error = e.message ?: "Initialisation failure") }
        }
    }

    private fun observeRecorderState() = viewModelScope.launch {
        AudioCaptureService.isRunning.collect { running ->
            _state.update { it.copy(isListening = running) }
            if (running) startProcessingAudio() else stopProcessingAudio()
        }
    }

    /* ─────────────────────────────────────────────── */
    /*  Audio processing pipeline                  */
    /* ─────────────────────────────────────────────── */

    private fun startProcessingAudio() {
        audioJob?.cancel()
        audioJob = AudioCaptureService.audioDataFlow
            .filterNotNull()
            .onEach { chunk ->
                audioBuffer += chunk

                if (AudioUtils.detectSilence(chunk)) {
                    if (silenceStartTime == null) silenceStartTime = now()
                    if (now() - (silenceStartTime ?: 0) > 300) flushCurrentBuffer()
                } else {
                    silenceStartTime = null
                    if (bufferDurationMs(audioBuffer) >= 1_000) flushCurrentBuffer()
                }
            }
            .catch { e -> _state.update { it.copy(error = "Audio error: ${e.message}") } }
            .launchIn(viewModelScope)
    }

    private fun stopProcessingAudio() {
        audioJob?.cancel()
        audioJob = null
    }

    private fun flushCurrentBuffer() {
        if (audioBuffer.isEmpty()) return
        pendingBuffer.clear()
        pendingBuffer += audioBuffer
        audioBuffer.clear()
        silenceStartTime = null

        viewModelScope.launch { processPendingBuffer() }
    }

    private suspend fun processPendingBuffer() {
        if (pendingBuffer.isEmpty() || !speechService.isInitialized) return

        val combined = combineAudioChunks(pendingBuffer)
        val pcmBytes  = floatArrayToPcm(combined)

        val start   = now()
        val text    = try {
            speechService.transcribeAudioData(pcmBytes, languageCode())
        } catch (e: Exception) {
            Timber.e(e, "STT failed")
            _state.update { it.copy(error = "Transcription error: ${e.message}") }
            return
        }
        val latency = now() - start

        if (text.isBlank()) return
        _state.update {
            it.copy(
                currentTranscript = (it.currentTranscript + ' ' + text).takeLast(500).trim(),
                latencyMs = latency,
                error = null
            )
        }

        if (shouldTranslate()) translateText(text)
    }

    /* ─────────────────────────────────────────────── */
    /*  Helpers                                    */
    /* ─────────────────────────────────────────────── */

    private fun combineAudioChunks(chunks: List<FloatArray>): FloatArray {
        val total = chunks.sumOf { it.size }
        val out   = FloatArray(total)
        var pos   = 0
        chunks.forEach { c ->
            c.copyInto(out, pos)
            pos += c.size
        }
        return out
    }

    private fun floatArrayToPcm(samples: FloatArray): ByteArray =
        ByteArray(samples.size * 2).also { bytes ->
            samples.forEachIndexed { i, s ->
                val v = (s.coerceIn(-1f, 1f) * 32767).toInt()
                bytes[i * 2]     = (v and 0xFF).toByte()
                bytes[i * 2 + 1] = ((v ushr 8) and 0xFF).toByte()
            }
        }

    private fun languageCode(): String = when (val src = _state.value.sourceLanguage) {
        Language.AUTO -> "en-US"          // fallback
        else          -> "${src.code}-US" // quick 2‑letter → xx‑US
    }

    private fun bufferDurationMs(buffer: List<FloatArray>): Long =
        if (buffer.isEmpty()) 0L
        else buffer.sumOf { it.size }.let { samples -> samples * 1_000L / sampleRate }

    private fun shouldTranslate(): Boolean =
        _state.value.targetLanguage != _state.value.sourceLanguage

    private suspend fun translateText(text: String) {
        if (text.isBlank()) return

        val key = "${text.take(100)}_${_state.value.sourceLanguage.code}_${_state.value.targetLanguage.code}"
        translationCache.get(key)?.let { cached ->
            _state.update { it.copy(translatedText = cached) }
            return
        }

        val prompt = """
            Translate the following text from ${_state.value.sourceLanguage.displayName} 
            to ${_state.value.targetLanguage.displayName}. Keep it natural.
            
            Text: "$text"
            
            Translation:
        """.trimIndent()

        try {
            val translation = gemmaService.generateTextAsync(
                prompt,
                UnifiedGemmaService.GenerationConfig(maxTokens = 100, temperature = 0.3f)
            ).trim()
            if (translation.isNotEmpty()) {
                translationCache.put(key, translation)
                _state.update { it.copy(translatedText = translation) }
            }
        } catch (e: Exception) {
            Timber.e(e, "Translation failed")
            _state.update { it.copy(error = "Translation error: ${e.message}") }
        }
    }

    private fun now() = System.currentTimeMillis()

    /* ─────────────────────────────────────────────── */
    /*  UI‑state data class                         */
    /* ─────────────────────────────────────────────── */

    data class CaptionState(
        val isListening: Boolean = false,
        val currentTranscript: String = "",
        val translatedText: String = "",
        val sourceLanguage: Language = Language.AUTO,
        val targetLanguage: Language = Language.ENGLISH,
        val latencyMs: Long = 0,
        val isModelReady: Boolean = false,
        val error: String? = null,

        /* new diagnostics / buffers */
        val audioBuffer: MutableList<FloatArray> = mutableListOf(),
        val pendingBuffer: MutableList<FloatArray> = mutableListOf(),
        val lastProcessedTime: Long = 0L,
        val silenceStartTime: Long? = null
    )
}
