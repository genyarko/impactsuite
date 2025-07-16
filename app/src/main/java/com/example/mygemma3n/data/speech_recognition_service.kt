package com.example.mygemma3n.feature.caption

import android.content.Context
import com.google.api.gax.core.FixedCredentialsProvider
import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.speech.v1.SpeechClient
import com.google.cloud.speech.v1.SpeechSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.ByteArrayInputStream
import javax.inject.Inject
import javax.inject.Singleton

private val okHttpClient = OkHttpClient()

@Singleton
class SpeechRecognitionService @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /* ─────────────────────────────── data class ─────────────────────────────── */
    data class TranscriptionResult(
        val transcript: String,
        val isFinal: Boolean,
        val confidence: Float,
        val language: String? = null
    )

    /* ────────────────────────────── stream configs ───────────────────────────── */
    private data class StreamConfig(
        val minSpeechMs: Int,
        val maxSpeechMs: Int,
        val silenceMs: Int,
        val modelName: String
    )

    private val LIVE_CONFIG = StreamConfig(
        minSpeechMs = 250,   // 0.25 s → lowest latency
        maxSpeechMs = 1000,  // 1 s   → flush frequently
        silenceMs   = 300,   // 0.3 s of pause → partial
        modelName   = "latest_short"
    )

    private val UTTER_CONFIG = StreamConfig(
        minSpeechMs = 1500,  // 1.5 s → ignore ums/ers
        maxSpeechMs = 10000, // 10 s  → hard stop
        silenceMs   = 800,   // 0.8 s silence → utterance complete
        modelName   = "latest_long"
    )

    /* ─────────────────────────────── constants ──────────────────────────────── */
    companion object {
        private const val SAMPLE_RATE = 16_000 // Hz
        private const val CHUNK_MS    = 20     // each FloatArray ≈ 20 ms
    }

    /* ───────────────────────────── internal state ───────────────────────────── */
    private var speechClient: SpeechClient? = null
    private var apiKeyForRest: String? = null
    var isInitialized: Boolean = false
        private set

    /* ──────────────────────────── initialisation ───────────────────────────── */
    /** *Service-account* initialisation (rare on mobile). */
    suspend fun initialize(credentialsJson: String) = withContext(Dispatchers.IO) {
        val creds = GoogleCredentials.fromStream(
            ByteArrayInputStream(credentialsJson.toByteArray())
        )
        speechClient = SpeechClient.create(
            SpeechSettings
                .newBuilder()
                .setCredentialsProvider(FixedCredentialsProvider.create(creds))
                .build()
        )
        isInitialized = true
        Timber.d("Speech-to-Text client (service account) initialised")
    }

    /** API-key initialisation (recommended for Android). */
    suspend fun initializeWithApiKey(apiKey: String) = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            throw IllegalArgumentException("API key cannot be blank")
        }
        apiKeyForRest = apiKey
        isInitialized = true
        Timber.d("Speech-to-Text service initialised with API key (length: ${apiKey.length})")

        // Test the API key with a simple request
        try {
            testApiKey(apiKey)
            Timber.d("API key validated successfully")
        } catch (e: Exception) {
            Timber.e(e, "API key validation failed: ${e.message}")
            isInitialized = false
            apiKeyForRest = null
            throw e
        }
    }

    private suspend fun testApiKey(apiKey: String) = withContext(Dispatchers.IO) {
        val url = "https://speech.googleapis.com/v1/speech:recognize?key=$apiKey"

        // Create a minimal test request with silence
        val testAudio = ByteArray(3200) // 0.1 seconds of silence
        val audioBase64 = android.util.Base64.encodeToString(testAudio, android.util.Base64.NO_WRAP)

        val requestJson = JSONObject().apply {
            put("config", JSONObject().apply {
                put("encoding", "LINEAR16")
                put("sampleRateHertz", SAMPLE_RATE)
                put("languageCode", "en-US")
            })
            put("audio", JSONObject().apply {
                put("content", audioBase64)
            })
        }

        val response = okHttpClient.newCall(
            Request.Builder()
                .url(url)
                .post(requestJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
        ).execute()

        if (!response.isSuccessful) {
            val body = response.body?.string() ?: ""
            throw IllegalStateException("API key test failed: HTTP ${response.code} - $body")
        }
    }

    /** Direct transcription of audio data (non-streaming) */
    suspend fun transcribeAudioData(
        audioData: ByteArray,
        languageCode: String = "en-US"
    ): String = withContext(Dispatchers.IO) {
        check(isInitialized && apiKeyForRest != null) {
            "Speech service not initialized"
        }

        Timber.d("Transcribing ${audioData.size} bytes of audio")

        val url = "https://speech.googleapis.com/v1/speech:recognize?key=$apiKeyForRest"
        val audioBase64 = android.util.Base64.encodeToString(
            audioData,
            android.util.Base64.NO_WRAP
        )

        val requestJson = JSONObject().apply {
            put("config", JSONObject().apply {
                put("encoding", "LINEAR16")
                put("sampleRateHertz", SAMPLE_RATE)
                put("audioChannelCount", 1)
                put("languageCode", languageCode)
                put("enableAutomaticPunctuation", true)
                put("model", "default")  // Use default model for better accuracy
                put("useEnhanced", true)  // Use enhanced model if available
                put("enableWordTimeOffsets", false)
                put("profanityFilter", false)
            })
            put("audio", JSONObject().apply {
                put("content", audioBase64)
            })
        }

        val response = okHttpClient.newCall(
            Request.Builder()
                .url(url)
                .post(
                    requestJson.toString()
                        .toRequestBody("application/json; charset=utf-8".toMediaType())
                )
                .build()
        ).execute()

        val bodyText = response.body?.string().orEmpty()

        if (!response.isSuccessful) {
            val errorDetail = try {
                val json = JSONObject(bodyText)
                if (json.has("error")) {
                    val err = json.getJSONObject("error")
                    val msg = err.optString("message", "unknown")
                    val code = err.optInt("code", -1)
                    "Error $code: $msg"
                } else {
                    "HTTP ${response.code}"
                }
            } catch (e: Exception) {
                "HTTP ${response.code}"
            }

            Timber.e("STT API error: $errorDetail")
            throw IllegalStateException("Speech recognition failed: $errorDetail")
        }

        val json = JSONObject(bodyText)
        val results = json.optJSONArray("results")

        if (results != null && results.length() > 0) {
            val transcripts = mutableListOf<String>()

            for (i in 0 until results.length()) {
                val result = results.getJSONObject(i)
                val alternatives = result.getJSONArray("alternatives")

                if (alternatives.length() > 0) {
                    val transcript = alternatives
                        .getJSONObject(0)
                        .optString("transcript", "")
                        .trim()

                    if (transcript.isNotEmpty()) {
                        transcripts.add(transcript)
                    }
                }
            }

            val fullTranscript = transcripts.joinToString(" ")
            Timber.d("Transcription result: '$fullTranscript'")
            return@withContext fullTranscript
        }

        Timber.d("No speech detected in audio")
        return@withContext ""
    }

    /* ───────────────────────────── public APIs ─────────────────────────────── */
    /** Low-latency live captions / translate overlay. */
    fun transcribeLiveCaptions(
        audioFlow: Flow<FloatArray>,
        languageCode: String = "en-US"
    ): Flow<TranscriptionResult> =
        transcribeInternal(audioFlow, languageCode, LIVE_CONFIG)

    /** Long, punctuated utterances for CBT recordings. */
    fun transcribeUtterances(
        audioFlow: Flow<FloatArray>,
        languageCode: String = "en-US"
    ): Flow<TranscriptionResult> =
        transcribeInternal(audioFlow, languageCode, UTTER_CONFIG)

    /* ─────────────────────────── main streaming engine ─────────────────────── */
    private fun transcribeInternal(
        audioFlow: Flow<FloatArray>,
        languageCode: String,
        cfg: StreamConfig
    ): Flow<TranscriptionResult> = flow {
        Timber.d("transcribeInternal started with language=$languageCode, config=$cfg")
        check(isInitialized && apiKeyForRest != null) {
            "Speech service not initialised. isInitialized=$isInitialized, hasApiKey=${apiKeyForRest != null}"
        }

        /* ───── adaptive gate: noise tracking ───── */
        val NOISE_WINDOW = 50                 // #chunks ≈ 1 s
        val SPEECH_FACTOR = 2f                // threshold = noise × factor (further reduced)
        var noiseSum = 0f
        var noiseCount = 0
        var noiseFloor = 0.00005f             // Much lower for sensitive detection

        /* ───── derived thresholds ───── */
        val MIN_AUDIO_LENGTH  =
            cfg.minSpeechMs * SAMPLE_RATE * 2 / 1000       // bytes
        val MAX_AUDIO_LENGTH  =
            cfg.maxSpeechMs * SAMPLE_RATE * 2 / 1000       // bytes
        val MAX_SILENCE_CHUNKS = cfg.silenceMs / CHUNK_MS  // chunks

        /* ───── stream buffers ───── */
        val buffer = mutableListOf<Byte>()
        var consecutiveSilenceChunks = 0
        var lastTranscript = ""
        var chunkCount = 0
        var totalAudioReceived = 0

        Timber.d("STT Config: minSpeech=${cfg.minSpeechMs}ms (${MIN_AUDIO_LENGTH} bytes), maxSpeech=${cfg.maxSpeechMs}ms (${MAX_AUDIO_LENGTH} bytes), silence=${cfg.silenceMs}ms")

        suspend fun flush(isFinal: Boolean) {
            if (buffer.isEmpty()) return
            if (!isFinal && buffer.size < MIN_AUDIO_LENGTH) {
                Timber.d("Buffer too small: ${buffer.size} < $MIN_AUDIO_LENGTH bytes, skipping")
                return
            }

            val data = buffer.toByteArray()
            Timber.d("Flushing ${data.size} bytes, isFinal=$isFinal")
            buffer.clear()

            try {
                val res = withContext(
                    if (isFinal) NonCancellable else Dispatchers.IO
                ) {
                    transcribeAudioREST(
                        audioData = data,
                        languageCode = languageCode,
                        apiKey = apiKeyForRest!!,
                        isFinal = isFinal,
                        model = cfg.modelName
                    )
                }
                if (res.transcript.isNotBlank()) {
                    Timber.d("Got transcript: '${res.transcript}', isFinal=${res.isFinal}")
                    emit(res)
                    lastTranscript = res.transcript
                    consecutiveSilenceChunks = 0
                } else {
                    Timber.d("Empty transcript returned")
                }
            } catch (e: Exception) {
                Timber.e(e, "STT request failed: ${e.message}")
            }
        }

        try {
            audioFlow.collect { chunk ->
                chunkCount++
                totalAudioReceived += chunk.size
                val rms = AudioUtils.calculateRMS(chunk)

                /* update dynamic noise estimate when chunk is quiet */
                if (rms < noiseFloor * 1.2f) {
                    noiseSum += rms
                    noiseCount++
                    if (noiseCount >= NOISE_WINDOW) {
                        noiseFloor =
                            (noiseSum / noiseCount).coerceAtLeast(1e-6f)
                        Timber.d("Updated noise floor: $noiseFloor")
                        noiseSum = 0f
                        noiseCount = 0
                    }
                }

                val dynamicThreshold = noiseFloor * SPEECH_FACTOR
                val isSpeech = rms > dynamicThreshold

                if (chunkCount % 25 == 0) {  // Log every 0.5 seconds
                    Timber.d("Chunk $chunkCount: RMS=$rms, threshold=$dynamicThreshold, isSpeech=$isSpeech, bufferSize=${buffer.size}, totalAudio=$totalAudioReceived")
                }

                // Always add audio to buffer for testing (bypass speech detection temporarily)
                val pcm = ByteArray(chunk.size * 2)
                chunk.forEachIndexed { i, s ->
                    val v = (s.coerceIn(-1f, 1f) * 32767).toInt()
                    pcm[i * 2] = (v and 0xFF).toByte()
                    pcm[i * 2 + 1] = ((v shr 8) and 0xFF).toByte()
                }
                buffer += pcm.toList()

                // Force flush every 2 seconds of audio for testing
                if (buffer.size >= SAMPLE_RATE * 2 * 2) { // 2 seconds
                    Timber.d("Force flushing after 2 seconds of audio")
                    flush(false)
                    consecutiveSilenceChunks = 0
                }

                /* Original speech detection logic (kept but with force flush above) */
                if (isSpeech) {
                    consecutiveSilenceChunks = 0

                    if (buffer.size >= MIN_AUDIO_LENGTH && buffer.size <= MAX_AUDIO_LENGTH) {
                        Timber.d("Speech detected, flushing ${buffer.size} bytes")
                        flush(false)
                    }
                } else {
                    consecutiveSilenceChunks++
                    if (buffer.size >= MIN_AUDIO_LENGTH &&
                        consecutiveSilenceChunks >= MAX_SILENCE_CHUNKS
                    ) {
                        Timber.d("Silence detected after ${consecutiveSilenceChunks} chunks, flushing ${buffer.size} bytes")
                        flush(false)
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error in audio collection: ${e.message}")
            throw e
        } finally {
            Timber.d("Audio flow completed, final flush with ${buffer.size} bytes")
            flush(true) // send the tail when the flow completes
        }
    }.flowOn(Dispatchers.IO)

    /* ───────────────────── synchronous REST helper ──────────────────────── */
    private suspend fun transcribeAudioREST(
        audioData: ByteArray,
        languageCode: String,
        apiKey: String,
        isFinal: Boolean = true,
        model: String = "latest_short"
    ): TranscriptionResult = withContext(Dispatchers.IO) {

        val url =
            "https://speech.googleapis.com/v1/speech:recognize?key=$apiKey"
        val audioBase64 = android.util.Base64.encodeToString(
            audioData,
            android.util.Base64.NO_WRAP
        )

        Timber.d("Sending ${audioData.size} bytes to STT API, model=$model")

        val requestJson = JSONObject().apply {
            put("config", JSONObject().apply {
                put("encoding", "LINEAR16")
                put("sampleRateHertz", SAMPLE_RATE)
                put("audioChannelCount", 1)
                put("languageCode", languageCode)
                put("enableAutomaticPunctuation", true)
                put("model", model)
                put("enableWordTimeOffsets", false)
                put("profanityFilter", false)
                put("speechContexts", JSONArray().apply {
                    put(JSONObject().apply {
                        put("phrases", JSONArray())
                        put("boost", 20)
                    })
                })
            })
            put("audio", JSONObject().apply { put("content", audioBase64) })
        }

        val response = okHttpClient.newCall(
            Request.Builder()
                .url(url)
                .post(
                    requestJson
                        .toString()
                        .toRequestBody(
                            "application/json; charset=utf-8".toMediaType()
                        )
                )
                .build()
        ).execute()

        val bodyText = response.body?.string().orEmpty()

        if (!response.isSuccessful) {
            Timber.e("STT API error: HTTP ${response.code} – ${response.message}")
            Timber.e("Response body: $bodyText")
            throw IllegalStateException(
                "HTTP ${response.code} – ${response.message}"
            )
        }

        val json = JSONObject(bodyText)

        if (json.has("error")) {
            val err = json.getJSONObject("error")
            val msg = err.optString("message", "unknown")
            val code = err.optInt("code", -1)
            Timber.e("STT API error $code: $msg")

            // Provide more helpful error messages
            val detailedError = when (code) {
                403 -> "API key is invalid or Speech-to-Text API is not enabled for this project"
                401 -> "Authentication failed - check your API key"
                400 -> "Bad request - audio format may be incorrect"
                else -> "STT error $code: $msg"
            }
            throw IllegalStateException(detailedError)
        }

        val results = json.optJSONArray("results")
        Timber.d("API Response - has results: ${results != null && results.length() > 0}")

        if (results != null && results.length() > 0) {
            val firstResult = results.getJSONObject(0)
            val alternatives = firstResult.getJSONArray("alternatives")

            if (alternatives.length() > 0) {
                val firstAlt = alternatives.getJSONObject(0)
                val transcript = firstAlt.optString("transcript", "")
                val confidence = firstAlt.optDouble("confidence", 0.0).toFloat()

                Timber.d("STT API returned: '$transcript' (confidence: $confidence)")

                return@withContext TranscriptionResult(
                    transcript = transcript,
                    isFinal = isFinal,
                    confidence = confidence,
                    language = languageCode
                )
            }
        }

        Timber.d("No speech detected in audio segment (${audioData.size} bytes)")
        TranscriptionResult(
            transcript = "",
            isFinal = isFinal,
            confidence = 0f,
            language = languageCode
        )
    }
}