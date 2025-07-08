package com.example.mygemma3n.feature.caption

import android.content.Context
import com.google.api.gax.core.FixedCredentialsProvider
import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.speech.v1.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import timber.log.Timber
import java.io.ByteArrayInputStream
import javax.inject.Inject
import javax.inject.Singleton

import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

private val okHttpClient = OkHttpClient()

@Singleton
class SpeechRecognitionService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var speechClient: SpeechClient? = null
    var isInitialized = false
    private var apiKeyForRest: String? = null

    companion object {
        private const val SAMPLE_RATE = 16000
        private const val STREAMING_LIMIT = 290000 // ~290 seconds
    }

    data class TranscriptionResult(
        val transcript: String,
        val isFinal: Boolean,
        val confidence: Float,
        val language: String? = null
    )

    /**
     * Initialize the Speech-to-Text client with API credentials
     * (Full service account JSON, not typical for mobile, more for backend)
     */
    suspend fun initialize(apiKey: String) = withContext(Dispatchers.IO) {
        try {
            // Service account credentials (not typical for just API key)
            val credentialsJson = """
                {
                  "type": "service_account",
                  "project_id": "your-project-id",
                  "private_key_id": "key-id",
                  "private_key": "$apiKey",
                  "client_email": "your-service-account@your-project-id.iam.gserviceaccount.com",
                  "client_id": "client-id",
                  "auth_uri": "https://accounts.google.com/o/oauth2/auth",
                  "token_uri": "https://oauth2.googleapis.com/token",
                  "auth_provider_x509_cert_url": "https://www.googleapis.com/oauth2/v1/certs",
                  "client_x509_cert_url": "https://www.googleapis.com/robot/v1/metadata/x509/your-service-account%40your-project-id.iam.gserviceaccount.com"
                }
            """.trimIndent()

            val credentials = GoogleCredentials.fromStream(
                ByteArrayInputStream(credentialsJson.toByteArray())
            )

            val speechSettings = SpeechSettings.newBuilder()
                .setCredentialsProvider(FixedCredentialsProvider.create(credentials))
                .build()

            speechClient = SpeechClient.create(speechSettings)
            isInitialized = true

            Timber.d("Speech-to-Text client initialized successfully")
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize Speech-to-Text client")
            throw e
        }
    }

    /**
     * Simplified initialization using just an API key.
     * This is the normal, recommended mobile pattern.
     */
    suspend fun initializeWithApiKey(apiKey: String) = withContext(Dispatchers.IO) {
        try {
            apiKeyForRest = apiKey
            isInitialized = true
            Timber.d("Speech-to-Text service initialized with API key")
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize Speech-to-Text service")
            throw e
        }
    }

    /**
     * Transcribe audio stream using REST API (simpler for API key auth)
     */
    fun transcribeAudioStream(
        audioFlow: Flow<FloatArray>,
        languageCode: String = "en-US"
    ): Flow<TranscriptionResult> = flow {
        if (!isInitialized || apiKeyForRest == null) {
            throw IllegalStateException("Speech service not initialized")
        }

        val audioBuffer = mutableListOf<Byte>()
        var lastTranscriptTime = System.currentTimeMillis()

        audioFlow.collect { audioChunk ->
            // Convert float array to byte array (PCM16)
            val byteArray = ByteArray(audioChunk.size * 2)
            for (i in audioChunk.indices) {
                val pcm16Value = (audioChunk[i] * 32767).toInt().coerceIn(-32768, 32767)
                byteArray[i * 2] = (pcm16Value and 0xFF).toByte()
                byteArray[i * 2 + 1] = ((pcm16Value shr 8) and 0xFF).toByte()
            }
            audioBuffer.addAll(byteArray.toList())

            // Process when we have enough audio (1 second worth)
            if (audioBuffer.size >= SAMPLE_RATE * 2) {
                val audioData = audioBuffer.toByteArray()
                audioBuffer.clear()
                try {
                    val result = transcribeAudioREST(
                        audioData = audioData,
                        languageCode = languageCode,
                        apiKey = apiKeyForRest!!
                    )
                    if (result.transcript.isNotEmpty()) {
                        emit(result)
                        lastTranscriptTime = System.currentTimeMillis()
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error transcribing audio chunk")
                }
            }

            // Emit partial results if we haven't had a transcript in a while
            if (System.currentTimeMillis() - lastTranscriptTime > 3000 && audioBuffer.isNotEmpty()) {
                val audioData = audioBuffer.toByteArray()
                audioBuffer.clear()
                try {
                    val result = transcribeAudioREST(
                        audioData = audioData,
                        languageCode = languageCode,
                        apiKey = apiKeyForRest!!,
                        isFinal = false
                    )
                    if (result.transcript.isNotEmpty()) {
                        emit(result)
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error transcribing partial audio")
                }
            }
        }

        // Process any remaining audio
        if (audioBuffer.isNotEmpty()) {
            val audioData = audioBuffer.toByteArray()
            try {
                val result = transcribeAudioREST(
                    audioData = audioData,
                    languageCode = languageCode,
                    apiKey = apiKeyForRest!!,
                    isFinal = true
                )
                if (result.transcript.isNotEmpty()) {
                    emit(result)
                }
            } catch (e: Exception) {
                Timber.e(e, "Error transcribing final audio")
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Make REST API call to Google Cloud Speech-to-Text
     * TODO: Replace with a real network call!
     */
    private suspend fun transcribeAudioREST(
        audioData: ByteArray,
        languageCode: String,
        apiKey: String,
        isFinal: Boolean = true
    ): TranscriptionResult = withContext(Dispatchers.IO) {
        val url = "https://speech.googleapis.com/v1/speech:recognize?key=$apiKey"
        val audioBase64 = android.util.Base64.encodeToString(audioData, android.util.Base64.NO_WRAP)
        val json = JSONObject().apply {
            put("config", JSONObject().apply {
                put("encoding", "LINEAR16")
                put("sampleRateHertz", 16000)
                put("languageCode", languageCode)
                put("enableAutomaticPunctuation", true)
            })
            put("audio", JSONObject().apply {
                put("content", audioBase64)
            })
        }
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = json.toString().toRequestBody(mediaType)
        val request = Request.Builder().url(url).post(body).build()

        val response = okHttpClient.newCall(request).execute()
        val responseBody = response.body?.string()

        if (!response.isSuccessful || responseBody.isNullOrBlank()) {
            return@withContext TranscriptionResult(
                transcript = "",
                isFinal = isFinal,
                confidence = 0f,
                language = languageCode
            )
        }

        val jsonResp = JSONObject(responseBody)
        val results = jsonResp.optJSONArray("results")
        if (results != null && results.length() > 0) {
            val first = results.getJSONObject(0)
            val alternatives = first.getJSONArray("alternatives")
            if (alternatives.length() > 0) {
                val alt = alternatives.getJSONObject(0)
                return@withContext TranscriptionResult(
                    transcript = alt.optString("transcript", ""),
                    isFinal = isFinal,
                    confidence = alt.optDouble("confidence", 0.0).toFloat(),
                    language = languageCode
                )
            }
        }
        return@withContext TranscriptionResult(
            transcript = "",
            isFinal = isFinal,
            confidence = 0f,
            language = languageCode
        )
    }
