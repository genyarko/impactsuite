package com.example.mygemma3n.shared_utilities

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Build

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Delegate
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.nnapi.NnApiDelegate
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MobileNetV5 Encoder for efficient image feature extraction
 * Optimized for on-device performance with Gemma 3n
 */
@Singleton
class MobileNetV5Encoder @Inject constructor(
    private val context: Context
) {
    private var interpreter: Interpreter? = null
    private var inputShape: IntArray? = null
    private var outputShape: IntArray? = null
    private val delegates = mutableListOf<Delegate>()
    enum class Resolution(val size: Int, val description: String) {
        LOW(224, "60 FPS on mid-range devices"),
        MEDIUM(384, "30 FPS on mid-range devices"),
        HIGH(512, "15 FPS on mid-range devices")
    }

    data class EncoderConfig(
        val resolution: Resolution = Resolution.MEDIUM,
        val useGpu: Boolean = true,
        val useNnapi: Boolean = false,
        val numThreads: Int = 4
    )

    /**
     * Initialise the MobileNet V5 encoder and select the best delegate.
     */
    suspend fun initialize(config: EncoderConfig = EncoderConfig()) = withContext(Dispatchers.IO) {
        try {
            // Clean up any existing interpreter + delegates
            cleanup()

            /* ── 1. Load the TFLite model from assets ───────────────────────────── */
            val modelPath = "models/mobilenet_v5_gemma.tflite"
            val modelBuffer = context.assets.open(modelPath).readBytes().let { bytes ->
                ByteBuffer.allocateDirect(bytes.size).apply {
                    order(ByteOrder.nativeOrder())
                    put(bytes)
                    rewind()
                }
            }

            /* ── 2. Build Interpreter options and add delegates as requested ────── */
            val options = Interpreter.Options().apply {
                setNumThreads(config.numThreads)

                // ---- GPU delegate -------------------------------------------------
                if (config.useGpu) {
                    try {
                        val gpuDelegate = GpuDelegate(
                            GpuDelegate.Options().apply {
                                isPrecisionLossAllowed = true
                                setQuantizedModelsAllowed(true)
                                setInferencePreference(
                                    GpuDelegate.Options.INFERENCE_PREFERENCE_SUSTAINED_SPEED
                                )
                            }
                        )
                        addDelegate(gpuDelegate)
                        delegates.add(gpuDelegate)
                        Timber.d("GPU delegate enabled for MobileNetV5")
                    } catch (e: Exception) {
                        Timber.w("GPU delegate not available: ${e.message}")
                    }
                }

                // ---- NNAPI delegate (fallback) -----------------------------------
                if (config.useNnapi && !config.useGpu && Build.VERSION.SDK_INT >= 27) {
                    try {
                        val nnapiDelegate = NnApiDelegate(
                            NnApiDelegate.Options().apply {
                                setAllowFp16(true)
                                if (Build.VERSION.SDK_INT >= 29) {
                                    setCacheDir(context.cacheDir.absolutePath)
                                    setModelToken("mobilenet_v5")
                                }
                                setExecutionPreference(
                                    NnApiDelegate.Options.EXECUTION_PREFERENCE_SUSTAINED_SPEED
                                )
                            }
                        )
                        addDelegate(nnapiDelegate)
                        delegates.add(nnapiDelegate)
                        Timber.d("NNAPI delegate enabled for MobileNetV5")
                    } catch (e: Exception) {
                        Timber.w("NNAPI delegate not available: ${e.message}")
                    }
                }

                // ---- CPU optimisations -------------------------------------------
                setAllowFp16PrecisionForFp32(true)
                setUseXNNPACK(true)
            }

            /* ── 3. Create and allocate the Interpreter ─────────────────────────── */
            interpreter = Interpreter(modelBuffer, options)
            interpreter?.allocateTensors()

            /* ── 4. Cache input/output tensor shapes for later use ──────────────── */
            interpreter?.let { interp ->
                inputShape  = interp.getInputTensor(0).shape()
                outputShape = interp.getOutputTensor(0).shape()
                Timber.d(
                    "MobileNetV5 initialised — Input: ${inputShape?.contentToString()}, " +
                            "Output: ${outputShape?.contentToString()}"
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialise MobileNetV5")
            throw e
        }
    }


    /**
     * Encode image to feature vector
     * @param bitmap Input image
     * @param config Encoder configuration
     * @return Float array of features (typically 1280 dimensions for MobileNetV5)
     */
    suspend fun encode(
        bitmap: Bitmap,
        config: EncoderConfig = EncoderConfig()
    ): FloatArray = withContext(Dispatchers.Default) {
        val interp = interpreter ?: throw IllegalStateException("Encoder not initialized")

        // Preprocess image
        val preprocessed = preprocessImage(bitmap, config.resolution)

        // Prepare input buffer
        val inputBuffer = convertBitmapToBuffer(preprocessed)

        // Prepare output buffer
        val outputSize = outputShape?.reduce { acc, i -> acc * i } ?: 1280
        val outputBuffer = ByteBuffer.allocateDirect(outputSize * 4).apply {
            order(ByteOrder.nativeOrder())
        }

        // Run inference
        val startTime = System.currentTimeMillis()
        interp.run(inputBuffer, outputBuffer)
        val inferenceTime = System.currentTimeMillis() - startTime

        Timber.d("MobileNetV5 inference completed in ${inferenceTime}ms")

        // Extract embeddings
        outputBuffer.rewind()
        val embeddings = FloatArray(outputSize)
        outputBuffer.asFloatBuffer().get(embeddings)

        // Normalize embeddings
        normalizeEmbeddings(embeddings)

        embeddings
    }

    /**
     * Batch encode multiple images
     */
    suspend fun encodeBatch(
        bitmaps: List<Bitmap>,
        config: EncoderConfig = EncoderConfig()
    ): List<FloatArray> = withContext(Dispatchers.Default) {
        bitmaps.map { bitmap ->
            encode(bitmap, config)
        }
    }

    private fun preprocessImage(bitmap: Bitmap, resolution: Resolution): Bitmap {
        // Calculate scaling to maintain aspect ratio
        val targetSize = resolution.size
        val scale = minOf(
            targetSize.toFloat() / bitmap.width,
            targetSize.toFloat() / bitmap.height
        )

        val scaledWidth = (bitmap.width * scale).toInt()
        val scaledHeight = (bitmap.height * scale).toInt()

        // Scale bitmap
        val matrix = Matrix().apply {
            postScale(scale, scale)
        }

        val scaled = Bitmap.createBitmap(
            bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
        )

        // Center crop to target size
        val xOffset = maxOf(0, (scaledWidth - targetSize) / 2)
        val yOffset = maxOf(0, (scaledHeight - targetSize) / 2)

        return if (scaledWidth >= targetSize && scaledHeight >= targetSize) {
            Bitmap.createBitmap(
                scaled,
                xOffset,
                yOffset,
                targetSize,
                targetSize
            )
        } else {
            // If scaled image is smaller than target, create a new bitmap with padding
            val result = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(result)
            canvas.drawColor(android.graphics.Color.BLACK) // Black padding

            val left = (targetSize - scaledWidth) / 2
            val top = (targetSize - scaledHeight) / 2
            canvas.drawBitmap(scaled, left.toFloat(), top.toFloat(), null)

            result
        }
    }

    private fun convertBitmapToBuffer(bitmap: Bitmap): ByteBuffer {
        val inputSize = inputShape?.get(1) ?: bitmap.width
        val channels = inputShape?.get(3) ?: 3
        val bufferSize = inputSize * inputSize * channels * 4

        val buffer = ByteBuffer.allocateDirect(bufferSize).apply {
            order(ByteOrder.nativeOrder())
        }

        val pixels = IntArray(inputSize * inputSize)
        bitmap.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)

        // Convert to normalized float values
        // MobileNetV5 expects values in [-1, 1] range
        pixels.forEach { pixel ->
            buffer.putFloat(((pixel shr 16 and 0xFF) - 127.5f) / 127.5f) // R
            buffer.putFloat(((pixel shr 8 and 0xFF) - 127.5f) / 127.5f)  // G
            buffer.putFloat(((pixel and 0xFF) - 127.5f) / 127.5f)         // B
        }

        buffer.rewind()
        return buffer
    }

    private fun normalizeEmbeddings(embeddings: FloatArray) {
        // L2 normalization for better similarity comparisons
        val norm = kotlin.math.sqrt(embeddings.map { it * it }.sum())
        if (norm > 0) {
            for (i in embeddings.indices) {
                embeddings[i] /= norm
            }
        }
    }

    /**
     * Calculate cosine similarity between two embeddings
     */
    fun calculateSimilarity(embedding1: FloatArray, embedding2: FloatArray): Float {
        require(embedding1.size == embedding2.size) { "Embeddings must have same size" }

        var dotProduct = 0f
        for (i in embedding1.indices) {
            dotProduct += embedding1[i] * embedding2[i]
        }

        // Since embeddings are normalized, dot product equals cosine similarity
        return dotProduct
    }

    /**
     * Get memory usage statistics
     */
    fun getMemoryStats(): MemoryStats {
        val modelSizeBytes = try {
            // Estimate model size based on typical MobileNetV5 sizes
            when (inputShape?.get(1)) {
                224 -> 14_000_000L // ~14MB for 224x224
                384 -> 20_000_000L // ~20MB for 384x384
                512 -> 25_000_000L // ~25MB for 512x512
                else -> 15_000_000L // Default estimate
            }
        } catch (e: Exception) {
            15_000_000L
        }

        return MemoryStats(
            modelSizeMB = modelSizeBytes / 1_048_576f,
            inputBufferSizeMB = (inputShape?.reduce { acc, i -> acc * i } ?: 0) * 4 / 1_048_576f,
            outputBufferSizeMB = (outputShape?.reduce { acc, i -> acc * i } ?: 0) * 4 / 1_048_576f
        )
    }

    data class MemoryStats(
        val modelSizeMB: Float,
        val inputBufferSizeMB: Float,
        val outputBufferSizeMB: Float
    ) {
        val totalMB: Float get() = modelSizeMB + inputBufferSizeMB + outputBufferSizeMB
    }

    fun cleanup() {
        interpreter?.close()
        interpreter = null
        delegates.forEach { it.close() }
        delegates.clear()
    }

    // Alias for backward compatibility
    fun close() = cleanup()
}