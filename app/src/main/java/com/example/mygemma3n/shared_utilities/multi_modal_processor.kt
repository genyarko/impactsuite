package com.example.mygemma3n.shared_utilities

import android.graphics.Bitmap
import com.example.mygemma3n.gemma.GemmaModelManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

@Singleton
class MultimodalProcessor @Inject constructor(
    private val modelManager: GemmaModelManager
) {

    enum class FeatureType {
        LIVE_CAPTION,
        QUIZ_GENERATOR,
        CBT_COACH,
        PLANT_SCANNER,
        CRISIS_HANDBOOK
    }

    data class MultimodalInput(
        val text: String? = null,
        val image: Bitmap? = null,
        val audioSamples: FloatArray? = null,
        val audioSampleRate: Int = 16000
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
        val startTime = System.currentTimeMillis()

        // Select optimal model based on feature and input
        val (model, modelName) = selectOptimalModel(feature, input)

        // Prepare inputs according to Gemma 3n multimodal format
        val inputTensors = prepareInterleavedInput(input, model)

        // Run inference
        val outputs = HashMap<Int, Any>()
        val outputBuffer = ByteBuffer.allocateDirect(VOCAB_SIZE * 4).order(ByteOrder.nativeOrder())
        outputs[0] = outputBuffer

        model.runForMultipleInputsOutputs(
            inputTensors.values.toTypedArray(),
            outputs
        )

        // Decode output tokens
        val result = decodeOutput(outputs)

        val processingTime = System.currentTimeMillis() - startTime

        ProcessingResult(
            text = result,
            processingTimeMs = processingTime,
            modelUsed = modelName
        )
    }

    private suspend fun selectOptimalModel(
        feature: FeatureType,
        input: MultimodalInput
    ): Pair<Interpreter, String> {
        return when {
            // Crisis response needs fast 2B model for low latency
            feature == FeatureType.CRISIS_HANDBOOK && input.audioSamples != null -> {
                modelManager.getModel(GemmaModelManager.ModelConfig.FAST_2B) to "gemma3n-2b"
            }
            // Plant scanner needs quality 4B model for accuracy
            feature == FeatureType.PLANT_SCANNER && input.image != null -> {
                modelManager.getModel(GemmaModelManager.ModelConfig.QUALITY_4B) to "gemma3n-4b"
            }
            // Live caption needs balanced performance
            feature == FeatureType.LIVE_CAPTION -> {
                modelManager.getModel(GemmaModelManager.ModelConfig.BALANCED_3B) to "gemma3n-3b"
            }
            // Default to 3B for other features
            else -> {
                modelManager.getModel(GemmaModelManager.ModelConfig.BALANCED_3B) to "gemma3n-3b"
            }
        }
    }

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

    private fun preprocessImage(bitmap: Bitmap): ByteBuffer {
        val modelInputSize = 224 // Gemma 3n uses 224x224 images
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, modelInputSize, modelInputSize, true)

        val buffer = ByteBuffer.allocateDirect(
            modelInputSize * modelInputSize * 3 * 4 // RGB float
        ).order(ByteOrder.nativeOrder())

        val pixels = IntArray(modelInputSize * modelInputSize)
        resizedBitmap.getPixels(
            pixels, 0, modelInputSize, 0, 0,
            modelInputSize, modelInputSize
        )

        // Convert to normalized float values
        for (pixel in pixels) {
            buffer.putFloat(((pixel shr 16 and 0xFF) - 127.5f) / 127.5f) // R
            buffer.putFloat(((pixel shr 8 and 0xFF) - 127.5f) / 127.5f)  // G
            buffer.putFloat(((pixel and 0xFF) - 127.5f) / 127.5f)        // B
        }

        buffer.rewind()
        return buffer
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