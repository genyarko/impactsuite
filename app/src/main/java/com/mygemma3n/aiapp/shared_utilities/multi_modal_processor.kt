package com.mygemma3n.aiapp.shared_utilities

import android.graphics.Bitmap
import com.mygemma3n.aiapp.data.UnifiedGemmaService
import com.mygemma3n.aiapp.gemma.GemmaModelManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import androidx.core.graphics.scale

@Singleton
class MultimodalProcessor @Inject constructor(
    private val gemma: UnifiedGemmaService
) {

    suspend fun processText(
        text: String,
        maxTokens: Int = 256
    ): String = gemma.generateWithModel(         // one-shot helper in UnifiedGemmaService
        prompt       = text.trim(),
        modelVariant = UnifiedGemmaService.ModelVariant.FAST_2B,
        maxTokens    = maxTokens,
        temperature  = 0.7f
    )

    suspend fun processWithImage(
        text: String,
        image: Bitmap,
        maxTokens: Int = 256
    ): String {
        val prompt = buildPrompt(text, image = image)
        return gemma.generateWithModel(
            prompt       = prompt,
            modelVariant = UnifiedGemmaService.ModelVariant.QUALITY_4B,
            maxTokens    = maxTokens,
            temperature  = 0.7f
        )
    }

    enum class FeatureType { LIVE_CAPTION, QUIZ_GENERATOR, CBT_COACH, PLANT_SCANNER, CRISIS_HANDBOOK }

    data class MultimodalInput(
        val text: String?             = null,
        val image: Bitmap?            = null,
        val audioSamples: FloatArray? = null,
        val audioSampleRate: Int      = 16_000
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
        maxTokens: Int = 512
    ): ProcessingResult = withContext(Dispatchers.Default) {
        val t0      = System.currentTimeMillis()
        val variant = chooseVariant(feature, input)
        val prompt  = buildPrompt(input.text, input.image, input.audioSamples)

        val output  = gemma.generateWithModel(
            prompt       = prompt,
            modelVariant = variant,
            maxTokens    = maxTokens,
            temperature  = 0.7f
        )

        ProcessingResult(
            text             = output.trim(),
            processingTimeMs = System.currentTimeMillis() - t0,
            modelUsed        = variant.displayName
        )
    }

    /*────────────── Model selection ─────────────*/

    private fun chooseVariant(
        feature: FeatureType,
        input: MultimodalInput
    ): UnifiedGemmaService.ModelVariant =
        when {
            feature == FeatureType.CRISIS_HANDBOOK -> UnifiedGemmaService.ModelVariant.FAST_2B   // low-latency :contentReference[oaicite:3]{index=3}
            feature == FeatureType.PLANT_SCANNER && input.image != null ->
                UnifiedGemmaService.ModelVariant.QUALITY_4B                                        // higher accuracy on vision-rich tasks :contentReference[oaicite:4]{index=4}
            else -> UnifiedGemmaService.ModelVariant.FAST_2B
        }

    /*────────────── Prompt builder ─────────────*/

    private fun buildPrompt(
        text: String?,
        image: Bitmap?            = null,
        audioSamples: FloatArray? = null
    ): String = buildString {
        if (!text.isNullOrBlank()) appendLine(text.trim())
        image?.let { appendLine("[user-provided image attached]") }                  // current LiteRT is text-only :contentReference[oaicite:5]{index=5}
        audioSamples?.let { appendLine("[audio clip of ${audioSamples.size} samples]") }
    }.trim()





    private fun prepareInterleavedInput(
        input: MultimodalInput,
        model: Interpreter
    ): Map<Int, Any> {
        val inputs = mutableMapOf<Int, Any>()
        var tensorIndex = 0

        // Text tokenization
        input.text?.let { text ->
            val tokens = tokenizeText(text)
            val tokenBuffer = ByteBuffer.allocateDirect(tokens.size * 4).order(ByteOrder.nativeOrder())
            tokens.forEach { tokenBuffer.putInt(it) }
            tokenBuffer.rewind()
            inputs[tensorIndex++] = tokenBuffer

            // Attention mask
            val maskBuffer = ByteBuffer.allocateDirect(tokens.size * 4).order(ByteOrder.nativeOrder())
            repeat(tokens.size) { maskBuffer.putFloat(1.0f) }
            maskBuffer.rewind()
            inputs[tensorIndex++] = maskBuffer
        }

        // Image preprocessing for vision
        input.image?.let { bitmap ->
            val imageBuffer = preprocessImage(bitmap)
            inputs[tensorIndex++] = imageBuffer
        }

        // Audio preprocessing
        input.audioSamples?.let { audio ->
            val audioBuffer = preprocessAudio(audio, input.audioSampleRate)
            inputs[tensorIndex++] = audioBuffer
        }

        return inputs
    }

    private fun tokenizeText(text: String): IntArray {
        // Simplified tokenization - in production, use proper tokenizer
        val tokens = text.split(" ").map { it.hashCode() % VOCAB_SIZE }.toIntArray()
        return tokens.take(MAX_SEQUENCE_LENGTH).toIntArray()
    }

    private fun preprocessImage(bitmap: Bitmap): FloatArray {
        val size = 224                                        // 224×224 expected by Gemma vision adaptor :contentReference[oaicite:6]{index=6}
        val bmp  = bitmap.scale(size, size)
        val px   = IntArray(size * size)
        bmp.getPixels(px, 0, size, 0, 0, size, size)

        val result = FloatArray(px.size * 3)
        for (i in px.indices) {
            val p = px[i]
            result[i * 3]     = ((p shr 16 and 0xFF) - 127.5f) / 127.5f   // R
            result[i * 3 + 1] = ((p shr 8  and 0xFF) - 127.5f) / 127.5f   // G
            result[i * 3 + 2] = ((p        and 0xFF) - 127.5f) / 127.5f   // B
        }
        return result                                                  // ready for future vision signature
    }

    private fun preprocessAudio(
        audioSamples: FloatArray,
        sampleRate: Int
    ): ByteBuffer {
        // Resample to 16kHz if needed
        val resampled = if (sampleRate != 16000) {
            resampleAudio(audioSamples, sampleRate, 16000)
        } else {
            audioSamples
        }

        // Apply mel-spectrogram transformation (simplified)
        val melFeatures = extractMelFeatures(resampled)

        val buffer = ByteBuffer.allocateDirect(
            melFeatures.size * 4
        ).order(ByteOrder.nativeOrder())

        melFeatures.forEach { buffer.putFloat(it) }
        buffer.rewind()

        return buffer
    }

    private fun preprocessAudio(
        samples: FloatArray,
        fromRate: Int,
        toRate: Int = 16_000
    ): FloatArray {
        if (fromRate == toRate) return samples
        val ratio   = fromRate.toFloat() / toRate
        val newLen  = (samples.size / ratio).toInt()
        return FloatArray(newLen) { i -> samples[(i * ratio).toInt()] }  // simple linear resample :contentReference[oaicite:7]{index=7}
    }

    private fun extractMelFrames(samples: FloatArray): FloatArray {
        // Minimal energy-per-frame mel approximation — replace with proper DSP later. :contentReference[oaicite:8]{index=8}
        val frame = 512; val hop = 256; val bins = 80
        val frames = (samples.size - frame) / hop + 1
        val feats  = FloatArray(frames * bins)
        for (f in 0 until frames) {
            val start = f * hop
            var energy = 0f
            for (j in 0 until frame) energy += samples[start + j] * samples[start + j]
            for (b in 0 until bins) feats[f * bins + b] = energy / bins
        }
        return feats
    }

    private fun resampleAudio(
        samples: FloatArray,
        fromRate: Int,
        toRate: Int
    ): FloatArray {
        // Simple linear interpolation resampling
        val ratio = fromRate.toFloat() / toRate
        val newLength = (samples.size / ratio).toInt()
        return FloatArray(newLength) { i ->
            val srcIndex = (i * ratio).toInt()
            if (srcIndex < samples.size) samples[srcIndex] else 0f
        }
    }

    private fun extractMelFeatures(samples: FloatArray): FloatArray {
        // Simplified mel-spectrogram extraction
        // In production, use proper DSP library
        val frameSize = 512
        val hopSize = 256
        val numMelBins = 80

        val numFrames = (samples.size - frameSize) / hopSize + 1
        val features = FloatArray(numFrames * numMelBins)

        // Basic energy calculation per frame
        for (i in 0 until numFrames) {
            val frameStart = i * hopSize
            val frameEnd = min(frameStart + frameSize, samples.size)

            var energy = 0f
            for (j in frameStart until frameEnd) {
                energy += samples[j] * samples[j]
            }

            // Distribute energy across mel bins (simplified)
            for (bin in 0 until numMelBins) {
                features[i * numMelBins + bin] = energy / numMelBins
            }
        }

        return features
    }

    private fun decodeOutput(outputs: Map<Int, Any>): String {
        // Extract logits from model output
        val outputBuffer = outputs[0] as? ByteBuffer ?: return "Error: No output generated"
        outputBuffer.rewind()

        val vocabSize = VOCAB_SIZE
        val numTokens = outputBuffer.remaining() / (vocabSize * 4)
        val tokens = mutableListOf<Int>()

        for (i in 0 until numTokens) {
            var maxLogit = Float.NEGATIVE_INFINITY
            var maxIndex = 0

            for (j in 0 until vocabSize) {
                val logit = outputBuffer.getFloat()
                if (logit > maxLogit) {
                    maxLogit = logit
                    maxIndex = j
                }
            }

            tokens.add(maxIndex)

            // Stop at EOS token (simplified)
            if (maxIndex == EOS_TOKEN) break
        }

        // Detokenize (simplified - in production use proper detokenizer)
        return tokens.joinToString(" ") { "token_$it" }
    }

    companion object {
        private const val VOCAB_SIZE = 32000
        private const val MAX_SEQUENCE_LENGTH = 512
        private const val EOS_TOKEN = 2
    }


}