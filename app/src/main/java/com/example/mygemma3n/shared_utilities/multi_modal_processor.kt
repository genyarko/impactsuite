package com.example.mygemma3n.shared_utilities

import android.graphics.Bitmap
import com.example.mygemma3n.data.GeminiApiService
import com.example.mygemma3n.gemma.GemmaModelManager
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

@Singleton
class MultimodalProcessor @Inject constructor(
    private val modelManager: GemmaModelManager,
    private val geminiApiService: GeminiApiService
) {

    enum class FeatureType {
        LIVE_CAPTION, QUIZ_GENERATOR, CBT_COACH, PLANT_SCANNER, CRISIS_HANDBOOK
    }

    data class MultimodalInput(
        val text: String?           = null,
        val image: Bitmap?          = null,
        val audioSamples: FloatArray? = null,   // Reserved for future SDK
        val audioSampleRate: Int    = 16_000
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as MultimodalInput

            if (audioSampleRate != other.audioSampleRate) return false
            if (text != other.text) return false
            if (image != other.image) return false
            if (!audioSamples.contentEquals(other.audioSamples)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = audioSampleRate
            result = 31 * result + (text?.hashCode() ?: 0)
            result = 31 * result + (image?.hashCode() ?: 0)
            result = 31 * result + (audioSamples?.contentHashCode() ?: 0)
            return result
        }
    }

    data class ProcessingResult(
        val text: String,
        val processingTimeMs: Long,
        val modelUsed: String
    )

    suspend fun process(
        input: MultimodalInput,
        feature: FeatureType,
        maxTokens: Int     = 512,
        temperature: Float = 0.7f,
        topK: Int          = 40,
        topP: Float        = 0.95f
    ): ProcessingResult = withContext(Dispatchers.Default) {

        val start = System.currentTimeMillis()

        // 1 – select the best cloud model
        val modelName = modelManager.selectOptimalModel(
            requireQuality = feature == FeatureType.PLANT_SCANNER,
            requireSpeed   = feature == FeatureType.CRISIS_HANDBOOK
        ).modelName

        // 2 – build the prompt (text + image today)
        val prompt = content {
            input.image?.let { image(it) }
            input.text?.let  { text(it) }

            // TODO: enable when `audio(bytes, rate)` lands in the Kotlin SDK
            // input.audioSamples?.let { samples -> … }
        }

        // 3 – send to Gemini
        val answer = geminiApiService.generateContent(prompt)

        ProcessingResult(
            text             = answer,
            processingTimeMs = System.currentTimeMillis() - start,
            modelUsed        = modelName
        )
    }

    /* ---------- helpers ---------- */

    // simple linear-interp resampler kept for future audio support
    private fun resampleAudio(src: FloatArray, fromHz: Int, toHz: Int): FloatArray {
        val r = fromHz.toFloat() / toHz
        return FloatArray((src.size / r).toInt()) { i ->
            src[min((i * r).toInt(), src.lastIndex)]
        }
    }
}



