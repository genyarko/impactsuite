package com.example.mygemma3n.feature.caption

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

// Language support
enum class Language(val code: String, val displayName: String) {
    AUTO("auto", "Auto-detect"),
    ENGLISH("en", "English"),
    SPANISH("es", "Spanish"),
    FRENCH("fr", "French"),
    GERMAN("de", "German"),
    CHINESE("zh", "Chinese"),
    JAPANESE("ja", "Japanese"),
    KOREAN("ko", "Korean"),
    HINDI("hi", "Hindi"),
    ARABIC("ar", "Arabic"),
    PORTUGUESE("pt", "Portuguese"),
    RUSSIAN("ru", "Russian"),
    ITALIAN("it", "Italian"),
    DUTCH("nl", "Dutch"),
    SWEDISH("sv", "Swedish"),
    POLISH("pl", "Polish"),
    TURKISH("tr", "Turkish"),
    INDONESIAN("id", "Indonesian"),
    VIETNAMESE("vi", "Vietnamese"),
    THAI("th", "Thai"),
    HEBREW("he", "Hebrew")
}

// Audio capture service
@Singleton
class AudioCapture @Inject constructor() {

    companion object {
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BUFFER_SIZE_FACTOR = 2
    }

    private var audioRecord: AudioRecord? = null
    private var isRecording = false

    fun startCapture(): Flow<FloatArray> = flow @androidx.annotation.RequiresPermission(android.Manifest.permission.RECORD_AUDIO) {
        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT
        )

        val bufferSize = minBufferSize * BUFFER_SIZE_FACTOR

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize
        ).apply {
            if (state != AudioRecord.STATE_INITIALIZED) {
                throw IllegalStateException("AudioRecord initialization failed")
            }
        }

        val audioBuffer = ShortArray(bufferSize)
        val floatBuffer = FloatArray(bufferSize)

        audioRecord?.startRecording()
        isRecording = true

        while (coroutineContext.isActive && isRecording) {
            val readSize = audioRecord?.read(audioBuffer, 0, bufferSize) ?: 0

            if (readSize > 0) {
                // Convert PCM16 to float
                for (i in 0 until readSize) {
                    floatBuffer[i] = audioBuffer[i] / 32768.0f
                }

                // Emit audio chunk
                emit(floatBuffer.copyOfRange(0, readSize))
            }
        }
    }.flowOn(Dispatchers.IO)

    fun stopCapture() {
        isRecording = false
        audioRecord?.apply {
            stop()
            release()
        }
        audioRecord = null
    }
}

// Translation cache
@Singleton
class TranslationCache @Inject constructor() {
    private val cache = ConcurrentHashMap<String, CachedTranslation>()
    private val maxCacheSize = 1000
    private val cacheExpirationMs = 24 * 60 * 60 * 1000L // 24 hours

    data class CachedTranslation(
        val translation: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    fun get(key: String): String? {
        val cached = cache[key] ?: return null

        // Check if expired
        if (System.currentTimeMillis() - cached.timestamp > cacheExpirationMs) {
            cache.remove(key)
            return null
        }

        return cached.translation
    }

    fun put(key: String, translation: String) {
        // Simple LRU: remove oldest entries if cache is full
        if (cache.size >= maxCacheSize) {
            val oldestKey = cache.entries
                .minByOrNull { it.value.timestamp }
                ?.key

            oldestKey?.let { cache.remove(it) }
        }

        cache[key] = CachedTranslation(translation)
    }

    fun clear() {
        cache.clear()
    }

    fun getCacheSize(): Int = cache.size
}

// Helper function to build multimodal prompt
fun buildMultimodalPrompt(
    instruction: String,
    audioData: FloatArray? = null,
    imageData: ByteArray? = null,
    previousContext: String? = null
): String {
    val promptBuilder = StringBuilder()

    // Add instruction
    promptBuilder.append(instruction)

    // Add previous context if available
    previousContext?.let {
        promptBuilder.append("\n\nPrevious context:\n$it")
    }

    // Add audio placeholder (in real implementation, this would be processed)
    audioData?.let {
        promptBuilder.append("\n\n[AUDIO INPUT: ${it.size} samples at 16kHz]")
    }

    // Add image placeholder (in real implementation, this would be processed)
    imageData?.let {
        promptBuilder.append("\n\n[IMAGE INPUT: ${it.size} bytes]")
    }

    promptBuilder.append("\n\nResponse:")

    return promptBuilder.toString()
}

// Audio processing utilities
object AudioUtils {

    fun chunked(audioFlow: Flow<FloatArray>, chunkSize: Int): Flow<FloatArray> = flow {
        val buffer = mutableListOf<Float>()

        audioFlow.collect { chunk ->
            buffer.addAll(chunk.toList())

            while (buffer.size >= chunkSize) {
                val outputChunk = buffer.take(chunkSize).toFloatArray()
                buffer.subList(0, chunkSize).clear()
                emit(outputChunk)
            }
        }

        // Emit remaining data
        if (buffer.isNotEmpty()) {
            emit(buffer.toFloatArray())
        }
    }

    fun calculateRMS(audioData: FloatArray): Float {
        val sum = audioData.sumOf { (it * it).toDouble() }
        return kotlin.math.sqrt(sum / audioData.size).toFloat()
    }

    fun detectSilence(audioData: FloatArray, threshold: Float = 0.01f): Boolean {
        return calculateRMS(audioData) < threshold
    }

    fun normalizeAudio(audioData: FloatArray): FloatArray {
        val maxValue = audioData.maxOfOrNull { kotlin.math.abs(it) } ?: 1f

        return if (maxValue > 0) {
            audioData.map { it / maxValue }.toFloatArray()
        } else {
            audioData
        }
    }
}

// Extension function for Flow<FloatArray>
fun Flow<FloatArray>.chunked(size: Int): Flow<FloatArray> =
    AudioUtils.chunked(this, size)