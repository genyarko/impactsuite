package com.example.mygemma3n.gemma

import android.content.Context
import android.os.Build
import com.example.mygemma3n.shared_utilities.PerformanceMonitor
import com.example.mygemma3n.shared_utilities.loadTfliteAsset
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.tensorflow.lite.Delegate
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.nnapi.NnApiDelegate
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.exp
import kotlin.random.Random

@Singleton
class GemmaEngine @Inject constructor(
    private val context: Context,
    private val performanceMonitor: PerformanceMonitor
) {
    // ---------- NEW: model variants ------------------------------------------------------------
    /** On-device Gemma model variants you bundle. */
    enum class ModelVariant(val fileName: String) {
        FAST_2B("gemma-2b-it-fast.tflite"),       // low-latency int8
        QUALITY_2B("gemma-2b-it-fp16.tflite"),    // fp16, better quality
        PRO_7B("gemma-7b-it-fp16.tflite")         // fp16, highest quality
    }
    // -------------------------------------------------------------------------------------------

    private var interpreter: Interpreter? = null
    private val inferenceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var currentConfig: InferenceConfig? = null
    private val delegates = mutableListOf<Delegate>()

    companion object {
        const val VOCAB_SIZE = 256000
        const val EOS_TOKEN = 2
        const val PAD_TOKEN = 0
        const val BOS_TOKEN = 1
        const val MAX_SEQUENCE_LENGTH = 8192

        const val PLE_CACHE_SIZE = 4096
        const val PLE_LAYER_CACHE_RATIO = 0.5f
    }

    data class InferenceConfig(
        val modelPath: String,
        val useGpu: Boolean = true,
        val useNnapi: Boolean = true,
        val numThreads: Int = 4,
        val enablePLE: Boolean = true,
        val kvCacheSize: Int = PLE_CACHE_SIZE,
        val temperature: Float = 0.7f,
        val topK: Int = 40,
        val topP: Float = 0.95f
    )

    data class GenerationConfig(
        val maxNewTokens: Int = 256,
        val temperature: Float = 0.7f,
        val topK: Int = 40,
        val topP: Float = 0.95f,
        val repetitionPenalty: Float = 1.1f,
        val doSample: Boolean = true
    )

    // ---------- NEW: one-shot helper for callers -----------------------------------------------
    /**
     * Convenience wrapper used by `CrisisFunctionCalling`:
     *  – Loads the requested variant if not already loaded
     *  – Collects the Flow into a single String
     */
    suspend fun generateWithModel(
        prompt: String,
        modelVariant: ModelVariant,
        maxTokens: Int = 256,
        temperature: Float = 0.7f
    ): String {
        val desiredPath = modelAbsolutePath(modelVariant.fileName)
        if (currentConfig?.modelPath != desiredPath) {
            initialize(
                InferenceConfig(
                    modelPath = desiredPath,
                    temperature = temperature
                )
            )
        }

        return generateText(
            prompt,
            GenerationConfig(
                maxNewTokens = maxTokens,
                temperature = temperature
            )
        ).toList().joinToString("")
    }

    private fun modelAbsolutePath(fileName: String): String =
        File(context.filesDir, "models/gemma/$fileName").absolutePath
    // -------------------------------------------------------------------------------------------

    suspend fun initialize(config: InferenceConfig) = withContext(Dispatchers.IO) {
        try {
            cleanup() // Clean up any existing interpreter

            currentConfig = config

            // 1) Load each TFLite shard directly from assets/models/*.tflite
            val embedderBuf       = loadTfliteAsset(context, "models/TF_LITE_EMBEDDER")
            val perLayerBuf       = loadTfliteAsset(context, "models/TF_LITE_PER_LAYER_EMBEDDER")
            val prefillDecodeBuf  = loadTfliteAsset(context, "models/TF_LITE_PREFILL_DECODE")
            val visionAdapterBuf  = loadTfliteAsset(context, "models/TF_LITE_VISION_ADAPTER")
            val visionEncoderBuf  = loadTfliteAsset(context, "models/TF_LITE_VISION_ENCODER")
            val tokenizerModelBuf = loadTfliteAsset(context, "models/TOKENIZER_MODEL")

            // 2) Build Interpreter options (threads, delegates, etc.)
            val options = Interpreter.Options().apply {
                setNumThreads(config.numThreads)

                if (config.useGpu) {
                    runCatching {
                        GpuDelegate(
                            GpuDelegate.Options().apply {
                                setPrecisionLossAllowed(true)
                                setQuantizedModelsAllowed(true)
                                setInferencePreference(
                                    GpuDelegate.Options.INFERENCE_PREFERENCE_SUSTAINED_SPEED
                                )
                            }
                        ).also { addDelegate(it); delegates.add(it) }
                    }.onFailure { Timber.w(it, "GPU delegate not available") }
                }

                if (config.useNnapi && Build.VERSION.SDK_INT >= 27) {
                    runCatching {
                        NnApiDelegate(
                            NnApiDelegate.Options().apply {
                                setAllowFp16(true)
                                setExecutionPreference(
                                    NnApiDelegate.Options.EXECUTION_PREFERENCE_SUSTAINED_SPEED
                                )
                                if (Build.VERSION.SDK_INT >= 29) {
                                    setCacheDir(context.cacheDir.absolutePath)
                                    setModelToken("gemma_3n_${config.hashCode()}")
                                }
                            }
                        ).also { addDelegate(it); delegates.add(it) }
                    }.onFailure { Timber.w(it, "NNAPI delegate not available") }
                }

                setAllowFp16PrecisionForFp32(true)
                setUseXNNPACK(true)
            }

            // 3) Initialize the Interpreter with the embedder buffer (or your multi-buffer loader)
            interpreter = Interpreter(embedderBuf, options)

            // 4) Allocate tensors and initialize PLE
            interpreter?.allocateTensors()
            if (config.enablePLE) initializePLECache()

        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize Gemma engine")
            throw e
        }
    }


    /**
     * FIXED: Handle large model files (>2GB) by either direct mapping or chunking
     */
    private fun loadModelBuffer(modelPath: String): MappedByteBuffer {
        val modelFile = File(modelPath)
        require(modelFile.exists()) { "Model file not found: $modelPath" } //

        FileInputStream(modelFile).channel.use { fc -> //
            val fileSize = fc.size() //

            // The 'chunked loading' logic below is flawed for allocateDirect
            // Instead of trying to allocate a single giant ByteBuffer,
            // we should rely on MappedByteBuffer's ability to handle large files.
            // If the model is truly >2GB, you cannot use allocateDirect for the whole thing.

            // The simplest and most common way to load large TFLite models is via MappedByteBuffer
            // which can handle sizes up to the addressable memory space (often much larger than 2GB).
            // If this still fails for extremely large files, it's an OS or TFLite limitation.

            // Remove the flawed 'if (fileSize <= Int.MAX_VALUE.toLong())' check
            // and the subsequent 'chunked loading' allocation, and just return the map.
            // MappedByteBuffer is designed for this.

            // Just return the memory-mapped buffer for any size (within OS limits)
            return fc.map(FileChannel.MapMode.READ_ONLY, 0, fileSize) //
        }
    }

    private fun initializePLECache() {
        // Initialize Per-Layer Embedding cache
        // This would interact with the model's internal caching mechanism
        // For now, this is a placeholder for PLE optimization
    }

    suspend fun generateText(
        prompt: String,
        config: GenerationConfig = GenerationConfig()
    ): Flow<String> = flow {
        requireNotNull(interpreter) { "Model not initialized" }

        val startTime = System.currentTimeMillis()
        var generatedTokens = 0
        var prefillTime = 0L

        // Tokenize input
        val inputTokens = tokenizeText(prompt)
        if (inputTokens.isEmpty()) {
            emit("")
            return@flow
        }

        // Prepare input tensors
        val inputIds = prepareInputTensor(inputTokens)
        val attentionMask = prepareAttentionMask(inputTokens.size)

        // Initialize KV cache
        val kvCache = KVCache(currentConfig?.kvCacheSize ?: PLE_CACHE_SIZE)

        // Track token generation history for repetition penalty
        val generatedTokensList = mutableListOf<Int>()

        // Prefill phase - process the entire prompt
        val outputs = HashMap<Int, Any>()
        val logitsBuffer = ByteBuffer.allocateDirect(VOCAB_SIZE * 4).order(ByteOrder.nativeOrder())
        outputs[0] = logitsBuffer

        val inputs = HashMap<Int, Any>()
        inputs[0] = inputIds
        inputs[1] = attentionMask

        val prefillStart = System.currentTimeMillis()
        interpreter?.runForMultipleInputsOutputs(arrayOf(inputIds, attentionMask), outputs)
        prefillTime = System.currentTimeMillis() - prefillStart

        performanceMonitor.recordInference(
            prefillTime = prefillTime,
            decodeTime = 0,
            tokens = 0,
            model = currentConfig?.modelPath ?: "unknown",
            feature = "text_generation",
            cacheHitRate = 0f,
            delegateType = getActiveDelegateType()
        )

        // Decode phase - generate tokens one by one
        var currentToken = getLastTokenFromLogits(logitsBuffer, config, generatedTokensList)
        var position = inputTokens.size

        while (generatedTokens < config.maxNewTokens && currentToken != EOS_TOKEN) {
            val decodeStart = System.currentTimeMillis()

            // Emit the decoded token
            val decodedText = decodeToken(currentToken)
            emit(decodedText)

            generatedTokensList.add(currentToken)
            generatedTokens++

            // Update KV cache
            kvCache.update(position, currentToken)

            // Prepare next input (single token)
            val nextInputIds = IntArray(1) { currentToken }
            val nextInputTensor = prepareInputTensor(nextInputIds)
            val nextAttentionMask = prepareAttentionMask(position + 1)

            // Run next inference
            logitsBuffer.clear()
            interpreter?.runForMultipleInputsOutputs(
                arrayOf(nextInputTensor, nextAttentionMask),
                outputs
            )

            // Sample next token
            currentToken = sampleToken(
                logitsBuffer,
                config,
                generatedTokensList
            )

            position++

            val decodeTime = System.currentTimeMillis() - decodeStart

            // Record metrics for every 10 tokens
            if (generatedTokens % 10 == 0) {
                performanceMonitor.recordInference(
                    prefillTime = 0,
                    decodeTime = decodeTime * 10,
                    tokens = 10,
                    model = currentConfig?.modelPath ?: "unknown",
                    feature = "text_generation",
                    cacheHitRate = kvCache.getCacheHitRate(),
                    delegateType = getActiveDelegateType()
                )
            }
        }

        // Final metrics
        val totalTime = System.currentTimeMillis() - startTime
        Timber.d("Generated $generatedTokens tokens in ${totalTime}ms " +
                "(prefill: ${prefillTime}ms, decode: ${totalTime - prefillTime}ms)")
    }.flowOn(Dispatchers.Default)

    private fun tokenizeText(text: String): IntArray {
        // Simplified tokenization - in production, use proper SentencePiece tokenizer
        // This is a placeholder that converts text to token IDs
        val tokens = mutableListOf<Int>()
        tokens.add(BOS_TOKEN) // Add beginning of sequence token

        // Simple character-level tokenization for demo
        text.forEach { char ->
            tokens.add(char.code % VOCAB_SIZE)
        }

        return tokens.toIntArray()
    }

    private fun decodeToken(tokenId: Int): String {
        // Simplified detokenization - in production, use proper SentencePiece detokenizer
        return if (tokenId in 32..126) {
            tokenId.toChar().toString()
        } else {
            "" // Skip special tokens
        }
    }

    private fun prepareInputTensor(tokens: IntArray): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(tokens.size * 4).order(ByteOrder.nativeOrder())
        tokens.forEach { buffer.putInt(it) }
        buffer.rewind()
        return buffer
    }

    private fun prepareAttentionMask(length: Int): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(length * 4).order(ByteOrder.nativeOrder())
        repeat(length) { buffer.putFloat(1.0f) }
        buffer.rewind()
        return buffer
    }

    private fun getLastTokenFromLogits(
        logitsBuffer: ByteBuffer,
        config: GenerationConfig,
        previousTokens: List<Int>
    ): Int {
        logitsBuffer.rewind()
        val logits = FloatArray(VOCAB_SIZE)
        logitsBuffer.asFloatBuffer().get(logits)

        // Apply repetition penalty
        if (config.repetitionPenalty != 1.0f && previousTokens.isNotEmpty()) {
            previousTokens.forEach { token ->
                if (token < VOCAB_SIZE) {
                    logits[token] = logits[token] / config.repetitionPenalty
                }
            }
        }

        return if (config.doSample) {
            sampleTokenWithTopKTopP(logits, config.temperature, config.topK, config.topP)
        } else {
            // Greedy decoding
            logits.indices.maxByOrNull { logits[it] } ?: 0
        }
    }

    private fun sampleToken(
        logitsBuffer: ByteBuffer,
        config: GenerationConfig,
        previousTokens: List<Int>
    ): Int {
        return getLastTokenFromLogits(logitsBuffer, config, previousTokens)
    }

    private fun sampleTokenWithTopKTopP(
        logits: FloatArray,
        temperature: Float,
        topK: Int,
        topP: Float
    ): Int {
        // Apply temperature
        val scaledLogits = if (temperature != 1.0f) {
            logits.map { it / temperature }.toFloatArray()
        } else {
            logits
        }

        // Get top-k tokens
        val topKIndices = scaledLogits.indices
            .sortedByDescending { scaledLogits[it] }
            .take(topK)

        // Apply softmax to top-k logits
        val topKLogits = topKIndices.map { scaledLogits[it] }.toFloatArray()
        val probabilities = softmax(topKLogits)

        // Apply top-p (nucleus sampling)
        val sortedProbs = probabilities.indices
            .sortedByDescending { probabilities[it] }

        var cumulativeProb = 0f
        val nucleusIndices = mutableListOf<Int>()

        for (idx in sortedProbs) {
            cumulativeProb += probabilities[idx]
            nucleusIndices.add(idx)
            if (cumulativeProb >= topP) break
        }

        // Sample from the nucleus
        val nucleusProbs = nucleusIndices.map { probabilities[it] }.toFloatArray()
        val normalizedProbs = normalizeProbs(nucleusProbs)

        val sampledIdx = sampleFromDistribution(normalizedProbs)
        return topKIndices[nucleusIndices[sampledIdx]]
    }

    private fun softmax(logits: FloatArray): FloatArray {
        val maxLogit = logits.maxOrNull() ?: 0f
        val expLogits = logits.map { exp(it - maxLogit) }
        val sumExp = expLogits.sum()
        return expLogits.map { it / sumExp }.toFloatArray()
    }

    private fun normalizeProbs(probs: FloatArray): FloatArray {
        val sum = probs.sum()
        return if (sum > 0) {
            probs.map { it / sum }.toFloatArray()
        } else {
            FloatArray(probs.size) { 1f / probs.size }
        }
    }

    private fun sampleFromDistribution(probs: FloatArray): Int {
        val random = Random.nextFloat()
        var cumulativeProb = 0f

        for (i in probs.indices) {
            cumulativeProb += probs[i]
            if (random < cumulativeProb) {
                return i
            }
        }

        return probs.size - 1
    }

    private fun getActiveDelegateType(): String {
        return when {
            delegates.any { it is GpuDelegate } -> "GPU"
            delegates.any { it is NnApiDelegate } -> "NNAPI"
            else -> "CPU"
        }
    }

    fun cleanup() {
        interpreter?.close()
        interpreter = null
        delegates.forEach { it.close() }
        delegates.clear()
    }

    // KV Cache implementation for efficient generation
    inner class KVCache(private val maxSize: Int) {
        private val keyCache = mutableMapOf<Int, FloatArray>()
        private val valueCache = mutableMapOf<Int, FloatArray>()
        private var hits = 0
        private var misses = 0

        fun update(position: Int, token: Int) {
            // In a real implementation, this would store the key-value states
            // from the transformer layers
        }

        fun get(position: Int): Pair<FloatArray, FloatArray>? {
            val key = keyCache[position]
            val value = valueCache[position]

            return if (key != null && value != null) {
                hits++
                Pair(key, value)
            } else {
                misses++
                null
            }
        }

        fun getCacheHitRate(): Float {
            val total = hits + misses
            return if (total > 0) hits.toFloat() / total else 0f
        }

        fun clear() {
            keyCache.clear()
            valueCache.clear()
            hits = 0
            misses = 0
        }
    }

    // Batch inference for multiple inputs
    suspend fun batchGenerateText(
        prompts: List<String>,
        config: GenerationConfig = GenerationConfig()
    ): List<String> = withContext(Dispatchers.Default) {
        prompts.map { prompt ->
            async {
                generateText(prompt, config)
                    .toList()
                    .joinToString("")
            }
        }.awaitAll()
    }

    // Get model information
    fun getModelInfo(): ModelInfo? {
        val interpreter = interpreter ?: return null

        return ModelInfo(
            inputTensorCount = interpreter.inputTensorCount,
            outputTensorCount = interpreter.outputTensorCount,
            delegateType = getActiveDelegateType()
        )
    }

    data class ModelInfo(
        val inputTensorCount: Int,
        val outputTensorCount: Int,
        val delegateType: String
    )
}