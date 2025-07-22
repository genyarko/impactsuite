package com.example.mygemma3n.feature.caption

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mygemma3n.data.ChatRepository
import com.example.mygemma3n.data.UnifiedGemmaService
import com.example.mygemma3n.feature.chat.ChatMessage
import com.example.mygemma3n.service.AudioCaptureService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import com.example.mygemma3n.dataStore
import com.example.mygemma3n.SPEECH_API_KEY

/**
 * Live‑caption ViewModel – **v2.1**
 *
 * Improvements
 * ------------
 * • **More tolerant buffering** – waits up to **2 s** of speech before flushing
 *   and requires **4 consecutive silent frames (~400 ms)** to count as a pause.
 * • Fixes premature truncation (e.g. "my name is" → "my name is George") on
 *   mid‑tier phones where inference is slower than capture.
 *
 * Reduce max tokens for almost instantaneous response
 */
@HiltViewModel
class LiveCaptionViewModel @Inject constructor(
    application: Application,
    private val speechService: SpeechRecognitionService,
    private val gemmaService: UnifiedGemmaService,
    private val translationCache: TranslationCache,
    private val chatRepository: ChatRepository,
) : AndroidViewModel(application) {

    /* ───────── UI STATE ───────── */
    private val _state = MutableStateFlow(CaptionState())
    val captionState: StateFlow<CaptionState> = _state.asStateFlow()

    /* ───────── buffers / jobs ───────── */
    private val audioBuffer   = mutableListOf<FloatArray>()
    private val pendingBuffer = mutableListOf<FloatArray>()
    private var silenceStart: Long? = null
    private var silentFrames = 0
    private var audioJob: Job? = null

    /* ───────── chat session ───────── */
    private var sessionId: String? = null

    /* ───────── constants ───────── */
    private val sampleRate = 16_000
    private val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    private val MAX_UTTERANCE_MS = 2_000L
    private val SILENCE_FRAMES_TO_FLUSH = 4

    /* ───────── init engines ───────── */
    init { initialiseEngines(); observeRecorder() }

    /* ───────────────────────────────────────────────────────────── */
    /*  PUBLIC  API                                                */
    /* ───────────────────────────────────────────────────────────── */

    fun startLiveCaption() {
        if (_state.value.isListening) return
        if (!_state.value.isModelReady) {
            _state.update { it.copy(error = "AI model not ready. Please wait.") }
            return
        }

        viewModelScope.launch {
            /* create a fresh chat session */
            sessionId = chatRepository.createNewSession(
                title = "Live caption ${sdf.format(Date())}"
            )
        }

        getApplication<Application>().startService(
            Intent(getApplication(), AudioCaptureService::class.java).apply {
                action = AudioCaptureService.ACTION_START_CAPTURE
            }
        )
    }

    fun stopLiveCaption() {
        getApplication<Application>().startService(
            Intent(getApplication(), AudioCaptureService::class.java).apply {
                action = AudioCaptureService.ACTION_STOP_CAPTURE
            }
        )

        /* clear UI and history */
        _state.update { it.copy(
            currentTranscript = "",
            translatedText = "",
            transcriptHistory = emptyList()
        ) }
        audioBuffer.clear(); pendingBuffer.clear(); silenceStart = null; silentFrames = 0; audioJob?.cancel()
        sessionId = null
    }

    fun setSourceLanguage(lang: Language) =
        _state.update { it.copy(sourceLanguage = lang, translatedText = "") }

    fun setTargetLanguage(lang: Language) {
        _state.update { it.copy(targetLanguage = lang) }
        // Re-translate all history entries if language changed
        if (lang != _state.value.sourceLanguage) {
            viewModelScope.launch {
                val currentHistory = _state.value.transcriptHistory
                currentHistory.forEach { entry ->
                    if (entry.transcript.isNotBlank()) {
                        translateHistoryEntry(entry.transcript, entry.timestamp)
                    }
                }
            }
        }
    }

    private suspend fun translateHistoryEntry(transcript: String, timestamp: Long) {
        val tgt = _state.value.targetLanguage
        val srcLang = _state.value.sourceLanguage
        if (tgt == srcLang) return

        val key = "${transcript.take(100)}_${srcLang.code}_${tgt.code}"
        val translation = translationCache.get(key) ?: run {
            val prompt = """
                Translate this ${srcLang.displayName} text to ${tgt.displayName}. Give ONLY the direct translation, no explanations or alternatives:
                "$transcript"
            """.trimIndent()
            val result = gemmaService.generateTextAsync(
                prompt,
                UnifiedGemmaService.GenerationConfig(maxTokens = 50, temperature = 0.1f)
            ).trim()
            translationCache.put(key, result)
            result
        }

        if (translation.isNotBlank()) {
            _state.update { state ->
                val updatedHistory = state.transcriptHistory.map { entry ->
                    if (entry.timestamp == timestamp && entry.transcript == transcript) {
                        entry.copy(translation = translation)
                    } else {
                        entry
                    }
                }
                state.copy(transcriptHistory = updatedHistory)
            }
        }
    }

    /* ───────────────────────────────────────────────────────────── */
    /*  ENGINE INITIALISATION                                      */
    /* ───────────────────────────────────────────────────────────── */

    private fun initialiseEngines() = viewModelScope.launch {
        try {
            // Speech key from DataStore
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

            // Gemma for translation
            if (!gemmaService.isInitialized()) {
                _state.update { it.copy(error = "Initialising AI model…") }
                gemmaService.initializeBestAvailable()
            }

            _state.update { it.copy(isModelReady = true, error = null) }
        } catch (e: Exception) {
            Timber.e(e, "Init failed")
            _state.update { it.copy(error = e.message, isModelReady = false) }
        }
    }

    private fun observeRecorder() = viewModelScope.launch {
        AudioCaptureService.isRunning.collect { running ->
            _state.update { it.copy(isListening = running) }
            if (running) startProcessingAudio() else stopProcessingAudioInternally()
        }
    }

    /* ───────────────────────────────────────────────────────────── */
    /*  AUDIO BUFFERING + STT                                       */
    /* ───────────────────────────────────────────────────────────── */

    private fun startProcessingAudio() {
        audioJob?.cancel()
        audioJob = AudioCaptureService.audioDataFlow
            .filterNotNull()
            .onEach { chunk ->
                audioBuffer += chunk

                if (AudioUtils.detectSilence(chunk)) {
                    silentFrames++
                    if (silentFrames >= SILENCE_FRAMES_TO_FLUSH) {
                        flushBuffer(); silentFrames = 0
                    }
                } else {
                    silentFrames = 0
                    if (durationMs(audioBuffer) >= MAX_UTTERANCE_MS) flushBuffer()
                }
            }
            .catch { e -> _state.update { it.copy(error = "Audio error: ${e.message}") } }
            .launchIn(viewModelScope)
    }

    private fun stopProcessingAudioInternally() { audioJob?.cancel(); audioJob = null }

    private fun flushBuffer() {
        if (audioBuffer.isEmpty()) return
        pendingBuffer.clear(); pendingBuffer += audioBuffer; audioBuffer.clear(); silenceStart = null
        viewModelScope.launch { processPending() }
    }

    private suspend fun processPending() {
        if (pendingBuffer.isEmpty() || !speechService.isInitialized) return
        val combined = combine(pendingBuffer)
        val pcm = toPcmBytes(combined)

        val transcript = try {
            speechService.transcribeAudioData(pcm, langCode())
        } catch (e: Exception) {
            _state.update { it.copy(error = "Transcription error: ${e.message}") }
            return
        }
        if (transcript.isBlank()) return

        Timber.d("Got transcript: '$transcript'")

        // Add to history with VOICE source
        val newEntry = TranscriptEntry(
            transcript = transcript,
            source = TranscriptSource.VOICE
        )
        _state.update {
            it.copy(
                currentTranscript = transcript,
                transcriptHistory = it.transcriptHistory + newEntry
            )
        }
        persistUserMessage(transcript)

        // Check if we should translate
        val shouldTrans = shouldTranslate()
        Timber.d("Should translate: $shouldTrans (source: ${_state.value.sourceLanguage}, target: ${_state.value.targetLanguage})")

        if (shouldTrans) {
            translateAndPersist(transcript)
        }
    }

    /* ───────────────────────────────────────────────────────────── */
    /*  TRANSLATION + PERSIST                                       */
    /* ───────────────────────────────────────────────────────────── */

    private suspend fun translateAndPersist(src: String) {
        val tgt = _state.value.targetLanguage
        var srcLang = _state.value.sourceLanguage

        // If source is AUTO, default to English for translation purposes
        if (srcLang == Language.AUTO) {
            srcLang = Language.ENGLISH
            Timber.d("Source language is AUTO, using English as default")
        }

        if (tgt == srcLang) {
            Timber.d("Source and target languages are the same, skipping translation")
            return
        }

        Timber.d("Starting translation: $srcLang -> $tgt for text: '$src'")

        val key = "${src.take(100)}_${srcLang.code}_${tgt.code}"
        val translation = translationCache.get(key) ?: run {
            val prompt = """
                Translate this ${srcLang.displayName} text to ${tgt.displayName}. Give ONLY the direct translation, no explanations or alternatives:
                "$src"
            """.trimIndent()

            Timber.d("Sending prompt to Gemma: $prompt")

            try {
                val result = gemmaService.generateTextAsync(
                    prompt,
                    UnifiedGemmaService.GenerationConfig(maxTokens = 50, temperature = 0.1f)
                ).trim()
                translationCache.put(key, result)
                result
            } catch (e: Exception) {
                Timber.e(e, "Translation failed")
                ""
            }
        }

        if (translation.isNotBlank()) {
            // Update the last history entry with translation
            _state.update { state ->
                val updatedHistory = state.transcriptHistory.toMutableList()
                val lastIndex = updatedHistory.lastIndex
                if (lastIndex >= 0 && updatedHistory[lastIndex].transcript == src) {
                    updatedHistory[lastIndex] = updatedHistory[lastIndex].copy(translation = translation)
                    Timber.d("Updated history entry at index $lastIndex with translation: '$translation'")
                } else {
                    Timber.w("Could not find matching history entry for transcript: '$src'")
                }
                state.copy(
                    translatedText = translation,
                    transcriptHistory = updatedHistory
                )
            }
            persistAiMessage(translation)
        } else {
            Timber.w("Translation was blank")
        }
    }

    /* ───────────────────────────────────────────────────────────── */
    /*  CHAT PERSISTENCE                                            */
    /* ───────────────────────────────────────────────────────────── */

    private fun persistUserMessage(text: String) {
        sessionId?.let { sid ->
            viewModelScope.launch { chatRepository.addMessage(sid, ChatMessage.User(content = text)) }
        }
    }

    private fun persistAiMessage(text: String) {
        sessionId?.let { sid ->
            viewModelScope.launch { chatRepository.addMessage(sid, ChatMessage.AI(content = text)) }
        }
    }

    /* ───────────────────────────────────────────────────────────── */
    /*  Text input                                                   */
    /* ───────────────────────────────────────────────────────────── */
    fun submitTextInput(text: String) {
        if (text.isBlank()) return

        viewModelScope.launch {
            // Create or ensure we have a session
            if (sessionId == null) {
                sessionId = chatRepository.createNewSession(
                    title = "Text translation ${sdf.format(Date())}"
                )
            }

            // Add to history with TYPED source
            val newEntry = TranscriptEntry(
                transcript = text,
                source = TranscriptSource.TYPED
            )
            _state.update {
                it.copy(
                    currentTranscript = text,
                    transcriptHistory = it.transcriptHistory + newEntry
                )
            }

            // Persist to chat
            persistUserMessage(text)

            // Translate if needed
            if (shouldTranslate()) {
                translateAndPersist(text)
            }
        }
    }



    /* ───────────────────────────────────────────────────────────── */
    /*  UTILS                                                       */
    /* ───────────────────────────────────────────────────────────── */

    private fun combine(chunks: List<FloatArray>): FloatArray {
        val out = FloatArray(chunks.sumOf { it.size }); var pos = 0
        for (c in chunks) { c.copyInto(out, pos); pos += c.size }
        return out
    }

    private fun toPcmBytes(samples: FloatArray): ByteArray {
        val bytes = ByteArray(samples.size * 2)
        for (i in samples.indices) {
            val v = (samples[i].coerceIn(-1f, 1f) * 32767).toInt()
            bytes[i * 2] = (v and 0xFF).toByte()
            bytes[i * 2 + 1] = ((v ushr 8) and 0xFF).toByte()
        }
        return bytes
    }

    private fun langCode(): String = when (val src = _state.value.sourceLanguage) {
        Language.AUTO -> "en-US"
        else -> "${src.code}-US"
    }

    private fun durationMs(chunks: List<FloatArray>): Long =
        if (chunks.isEmpty()) 0 else chunks.sumOf { it.size } * 1_000L / sampleRate

    private fun shouldTranslate() = _state.value.targetLanguage != _state.value.sourceLanguage
    private fun now() = System.currentTimeMillis()

    /* ───────────────────────────────────────────────────────────── */
    /*  CLEANUP                                                      */
    /* ───────────────────────────────────────────────────────────── */

    override fun onCleared() {
        super.onCleared()
        audioJob?.cancel()
        stopLiveCaption()
    }
}