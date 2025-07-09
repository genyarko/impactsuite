package com.example.mygemma3n.feature.caption

import android.content.Context
import com.google.api.gax.core.FixedCredentialsProvider
import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.speech.v1.SpeechClient
import com.google.cloud.speech.v1.SpeechSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
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
    // ───────────────────────────── data class ──────────────────────────────
    data class TranscriptionResult(
        val transcript: String,
        val isFinal: Boolean,
        val confidence: Float,
        val language: String? = null
    )

    // ───────────────────────────── constants ───────────────────────────────
    companion object {
        private const val SAMPLE_RATE = 16_000         // Hz
    }

    // ─────────────────────────── internal state ────────────────────────────
    private var speechClient: SpeechClient? = null
    private var apiKeyForRest: String? = null
    var  isInitialized: Boolean = false
        private set

    // ─────────────────────────── initialisation ────────────────────────────
    /** *Service-account* initialisation (rare on mobile) */
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

    /** API-key initialisation (recommended for Android) */
    suspend fun initializeWithApiKey(apiKey: String) = withContext(Dispatchers.IO) {
        apiKeyForRest = apiKey
        isInitialized = true
        Timber.d("Speech-to-Text service initialised with API key")
    }

    // ───────────────────── main streaming helper (Flow) ────────────────────
    // Updated transcribeAudioStream function with fixes
    // Updated transcribeAudioStream with faster partial results
    // Updated transcribeAudioStream with faster partial results
    fun transcribeAudioStream(
        audioFlow: Flow<FloatArray>,
        languageCode: String = "en-US"
    ): Flow<TranscriptionResult> = flow {
        check(isInitialized && apiKeyForRest != null) { "Speech service not initialised" }

        /* ───── adaptive-gate configuration ───── */
        val NOISE_WINDOW = 50                       // #chunks to average ≈ 2.5 s @ 20 ms
        val SPEECH_FACTOR = 5f                      // gate = noiseFloor × SPEECH_FACTOR
        var noiseSum = 0f
        var noiseCount = 0
        var noiseFloor = 0.0005f                    // start at −66 dBFS ≈ room noise

        /* ───── stream state ───── */
        val buffer = mutableListOf<Byte>()
        var consecutiveSilenceChunks = 0
        var lastTranscript = ""

        val MIN_AUDIO_LENGTH = 32_000               // 0.25 s
        val MAX_AUDIO_LENGTH = 64_000               // 1 s
        val MAX_SILENCE_CHUNKS = 3
        val MAX_BUFFER_AGE = 350                    // ms
        suspend fun flush(isFinal: Boolean) {
            if (buffer.isEmpty()) return
            if (!isFinal && buffer.size < MIN_AUDIO_LENGTH) return    // ignore tiny bursts

            val data = buffer.toByteArray()
            buffer.clear()

            try {
                val res = transcribeAudioREST(
                    audioData = data,
                    languageCode = languageCode,
                    apiKey = apiKeyForRest!!,
                    isFinal = isFinal
                )
                if (res.transcript.isNotBlank()) {
                    emit(res)
                    lastTranscript = res.transcript
                    consecutiveSilenceChunks = 0
                }
            } catch (e: Exception) {
                Timber.e(e, "STT request failed")
            }
        }

        try {
            audioFlow.collect { chunk ->
                val rms = AudioUtils.calculateRMS(chunk)

                /* -- update adaptive noise estimate when current chunk is quiet -- */
                if (rms < noiseFloor * 1.2f) {                   // treat as noise sample
                    noiseSum += rms
                    noiseCount++
                    if (noiseCount >= NOISE_WINDOW) {
                        noiseFloor = (noiseSum / noiseCount).coerceAtLeast(1e-5f)
                        noiseSum = 0f
                        noiseCount = 0
                    }
                }

                val dynamicThreshold = noiseFloor * SPEECH_FACTOR
                val isSpeech = rms > dynamicThreshold

                if (isSpeech) {
                    consecutiveSilenceChunks = 0

                    // PCM-16 conversion
                    val pcm = ByteArray(chunk.size * 2)
                    chunk.forEachIndexed { i, s ->
                        val v = (s.coerceIn(-1f, 1f) * 32767).toInt()
                        pcm[i * 2] = (v and 0xFF).toByte()
                        pcm[i * 2 + 1] = ((v shr 8) and 0xFF).toByte()
                    }
                    buffer += pcm.toList()

                    if (buffer.size in MIN_AUDIO_LENGTH..MAX_AUDIO_LENGTH) {
                        flush(false)
                    } else if (buffer.size > MAX_AUDIO_LENGTH) {
                        flush(false)
                    }
                } else {
                    consecutiveSilenceChunks++
                    if (buffer.size >= MIN_AUDIO_LENGTH &&
                        consecutiveSilenceChunks >= MAX_SILENCE_CHUNKS
                    ) {
                        flush(false)
                    }
                }
            }
        } finally {
            flush(true)        // make sure we send the tail when the flow completes
        }
    }.flowOn(Dispatchers.IO)


    // ─────────────────────── synchronous REST call ────────────────────────
    // Enhanced transcribeAudioREST with debugging
    private suspend fun transcribeAudioREST(
        audioData: ByteArray,
        languageCode: String,
        apiKey: String,
        isFinal: Boolean = true
    ): TranscriptionResult = withContext(Dispatchers.IO) {

        val url = "https://speech.googleapis.com/v1/speech:recognize?key=$apiKey"
        val audioBase64 = android.util.Base64.encodeToString(
            audioData, android.util.Base64.NO_WRAP
        )

        // Debug: Check audio data
        Timber.d("Audio data size: ${audioData.size} bytes")
        Timber.d("Base64 size: ${audioBase64.length} chars")
        Timber.d("Language code: $languageCode")

        val requestJson = JSONObject().apply {
            put("config", JSONObject().apply {
                put("encoding", "LINEAR16")
                put("sampleRateHertz", 16_000)
                put("audioChannelCount", 1)
                put("languageCode", languageCode)
                put("enableAutomaticPunctuation", true)
                put("model", "latest_short")
                // Add these for better recognition
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

        // Debug: Log request (without full base64 data)
        val debugJson = JSONObject(requestJson.toString()).apply {
            getJSONObject("audio").put("content", "[BASE64_DATA_${audioBase64.length}_CHARS]")
        }
        Timber.d("STT Request: ${debugJson.toString(2)}")

        val response = okHttpClient.newCall(
            Request.Builder()
                .url(url)
                .post(
                    requestJson
                        .toString()
                        .toRequestBody("application/json; charset=utf-8".toMediaType())
                )
                .build()
        ).execute()

        val bodyText = response.body?.string().orEmpty()
        Timber.d("STT raw response ($isFinal): $bodyText")

        if (!response.isSuccessful) {
            throw IllegalStateException(
                "HTTP ${response.code} – ${response.message}"
            )
        }

        val json = JSONObject(bodyText)

        // Check for API errors
        if (json.has("error")) {
            val err = json.getJSONObject("error")
            val msg = err.optString("message", "unknown")
            val code = err.optInt("code", -1)

            // Check for specific error types
            when (code) {
                403 -> throw IllegalStateException("API key invalid or Speech API not enabled: $msg")
                429 -> throw IllegalStateException("Rate limit exceeded: $msg")
                else -> throw IllegalStateException("STT error $code: $msg")
            }
        }

        val results = json.optJSONArray("results")
        if (results != null && results.length() > 0) {
            val firstAlt = results
                .getJSONObject(0)
                .getJSONArray("alternatives")
                .getJSONObject(0)

            return@withContext TranscriptionResult(
                transcript = firstAlt.optString("transcript", ""),
                isFinal = isFinal,
                confidence = firstAlt.optDouble("confidence", 0.0).toFloat(),
                language = languageCode
            )
        }

        // No speech recognized - this is normal for silence
        Timber.d("No speech detected in audio segment")
        TranscriptionResult(
            transcript = "",
            isFinal = isFinal,
            confidence = 0f,
            language = languageCode
        )
    }
}
