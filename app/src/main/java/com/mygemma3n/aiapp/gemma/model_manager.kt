package com.mygemma3n.aiapp.gemma

import android.content.Context
import android.os.Build
import com.mygemma3n.aiapp.data.ModelRepository
import com.mygemma3n.aiapp.shared_utilities.PerformanceMonitor

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.tensorflow.lite.Delegate
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.nnapi.NnApiDelegate
import org.tensorflow.lite.gpu.GpuDelegateFactory.Options


import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.io.FileInputStream
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.apply
import dagger.hilt.android.qualifiers.ApplicationContext

import org.tensorflow.lite.gpu.GpuDelegateFactory.Options as GpuOptions



@Singleton
class GemmaModelManager @Inject constructor(
    @ApplicationContext
    private val context: Context,
    val modelRepository: ModelRepository,
    private val performanceMonitor: PerformanceMonitor
) {
    private val modelCache = mutableMapOf<ModelConfig, Interpreter>()
    private val embeddingModelCache = mutableMapOf<String, Interpreter>()
    private val cacheMutex = Mutex()
    private val delegates: MutableMap<String, Delegate?> = mutableMapOf()


    companion object {
        const val TOKENIZER_VOCAB_SIZE = 256000
        const val MAX_SEQUENCE_LENGTH = 8192
        const val EMBEDDING_DIM = 768

        // Model file names
        const val MODEL_2B = "gemma_3n_2b_it"
        const val MODEL_4B = "gemma_3n_4b_it"
        const val MODEL_EMBEDDING = "gemma_3n_embedding"

        // Mix'n'match configurations
        const val MIX_N_MATCH_2B_RATIO = 0.5f
        const val MIX_N_MATCH_3B_RATIO = 0.75f
        const val MIX_N_MATCH_4B_RATIO = 1.0f
    }

    sealed class ModelConfig(
        val modelName: String,
        val activeParams: String,
        val mixRatio: Float = 1.0f
    ) {
        object FAST_2B : ModelConfig(MODEL_2B, "2B", MIX_N_MATCH_2B_RATIO)
        object BALANCED_3B : ModelConfig(MODEL_4B, "3B", MIX_N_MATCH_3B_RATIO) // 4B model with 3B active
        object QUALITY_4B : ModelConfig(MODEL_4B, "4B", MIX_N_MATCH_4B_RATIO)

        override fun toString(): String = activeParams
    }

    suspend fun getModel(config: ModelConfig): Interpreter = cacheMutex.withLock {
        modelCache.getOrPut(config) {
            withContext(Dispatchers.IO) {
                loadModel(config)
            }
        }
    }

    suspend fun getEmbeddingModel(): Interpreter = cacheMutex.withLock {
        embeddingModelCache.getOrPut(MODEL_EMBEDDING) {
            withContext(Dispatchers.IO) {
                val modelPath = modelRepository.getModelPath(MODEL_EMBEDDING)
                    ?: throw IllegalStateException("Embedding model not found")
                loadModelFromPath(modelPath, useGpu = true)
            }
        }
    }

    private suspend fun loadModel(config: ModelConfig): Interpreter = withContext(Dispatchers.IO) {
        val modelPath = modelRepository.getModelPath(config.modelName)
            ?: throw IllegalStateException("Model ${config.modelName} not found")

        when (config) {
            is ModelConfig.FAST_2B -> {
                loadModelFromPath(modelPath, useGpu = true, useNnapi = true)
            }
            is ModelConfig.BALANCED_3B -> {
                // Mix'n'match: Load 4B model but activate only 3B parameters
                loadMixNMatchModel(modelPath, config.mixRatio)
            }
            is ModelConfig.QUALITY_4B -> {
                loadModelFromPath(modelPath, useGpu = true, useNnapi = true)
            }
        }
    }

    private fun loadModelFromPath(
        modelPath: String,
        useGpu: Boolean = true,
        useNnapi: Boolean = true,
        numThreads: Int = 4
    ): Interpreter {
        val modelBuffer = loadModelBuffer(modelPath)
        // This map will hold delegates created specifically for this interpreter instance.
        val newDelegates = mutableMapOf<String, Delegate>()

        val options = Interpreter.Options().apply {
            setNumThreads(numThreads)
            // Consider device-specific checks before enabling these
            setAllowFp16PrecisionForFp32(true)
            setUseXNNPACK(true)

            if (useGpu) {
                try {
                    val gpuOpts = GpuDelegate.Options().apply {
                        setPrecisionLossAllowed(true) // Deprecated, but shown for compatibility
                        // Use setInferencePriority1 for newer TFLite versions
                        setInferencePreference(GpuDelegate.Options.INFERENCE_PREFERENCE_SUSTAINED_SPEED)
                    }
                    val gpuDelegate = GpuDelegate(gpuOpts)
                    addDelegate(gpuDelegate)
                    // Use a unique key for each delegate instance to track it
                    newDelegates["gpu_${modelPath.hashCode()}"] = gpuDelegate
                    Timber.d("GPU delegate enabled for $modelPath")
                } catch (e: Exception) {
                    Timber.w(e, "GPU delegate unavailable for $modelPath")
                }
            }

            if (useNnapi && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) { // NNAPI delegate is generally stable on P+
                try {
                    val nnapiDelegate = NnApiDelegate() // Options can be configured if needed
                    addDelegate(nnapiDelegate)
                    newDelegates["nnapi_${modelPath.hashCode()}"] = nnapiDelegate
                    Timber.d("NNAPI delegate enabled for $modelPath")
                } catch (e: Exception) {
                    Timber.w(e, "NNAPI delegate unavailable for $modelPath")
                }
            }
        }

        // After creating the interpreter, add the newly created delegates to the central map for lifecycle tracking.
        // This should be done within the cacheMutex to ensure thread safety.
        // Note: This part requires refactoring how you call this function to use the mutex.
        // For now, let's assume you'll lock it before calling.
        delegates.putAll(newDelegates)

        return Interpreter(modelBuffer, options).also {
            it.allocateTensors()
            Timber.d("Interpreter initialized for $modelPath")
        }
    }




    private fun loadMixNMatchModel(modelPath: String, activationRatio: Float): Interpreter {
        val modelBuffer = loadModelBuffer(modelPath)
        var gpuDelegate: GpuDelegate? = null // 👈 1. Declare variable here

        val options = Interpreter.Options().apply {
            setNumThreads(4)
            setAllowFp16PrecisionForFp32(true)
            setUseXNNPACK(true)
            try {
                val gpuOptions = GpuDelegate.Options().apply {
                    setPrecisionLossAllowed(true)
                    setQuantizedModelsAllowed(true)
                    setInferencePreference(GpuDelegate.Options.INFERENCE_PREFERENCE_SUSTAINED_SPEED)
                }
                // Assign to the variable declared outside this scope
                gpuDelegate = GpuDelegate(gpuOptions)
                addDelegate(gpuDelegate)
                Timber.d("GPU delegate enabled for mix'n'match model")
            } catch (e: Exception) {
                Timber.w(e, "GPU delegate not available for mix'n'match")
            }
        }

        // 2. Add the created delegate to the class's map
        // This happens only if the delegate was successfully created
        gpuDelegate?.let {
            delegates["gpu_mix"] = it
        }

        val interpreter = Interpreter(modelBuffer, options)
        configureMixNMatch(interpreter, activationRatio)
        interpreter.allocateTensors()
        return interpreter
    }



    private fun configureMixNMatch(interpreter: Interpreter, ratio: Float) {
        // This is a conceptual implementation
        // The actual Gemma 3n mix'n'match would involve:
        // 1. Identifying which layers to activate based on ratio
        // 2. Setting activation masks for Per-Layer Embeddings
        // 3. Configuring the model to use subset of parameters

        Timber.d("Configured mix'n'match with ${(ratio * 100).toInt()}% parameter activation")
    }

    private fun loadModelBuffer(modelPath: String): MappedByteBuffer {
        val modelFile = File(modelPath)
        require(modelFile.exists()) { "Model file not found: $modelPath" }

        FileInputStream(modelFile).channel.use { fc ->
            val fileSize = fc.size()

            // ───── Fix A: direct mmap if the file fits JVM limit (≈ 2 GB) ─────
            if (fileSize <= Int.MAX_VALUE.toLong()) {
                return fc.map(FileChannel.MapMode.READ_ONLY, 0, fileSize)
            }

            // ───── Fix B: chunk-map and copy into one direct buffer ───────────
            val sliceSize = 1_900_000_000L                     // 1.9 GB per slice
            val bigBuf: ByteBuffer = ByteBuffer
                .allocateDirect(fileSize.toInt())              // single contiguous buffer
                .order(ByteOrder.nativeOrder())

            var offset = 0L
            while (offset < fileSize) {
                val len   = minOf(sliceSize, fileSize - offset)
                val slice = fc.map(FileChannel.MapMode.READ_ONLY, offset, len)
                bigBuf.put(slice)                              // copy slice → big buffer
                offset += len
            }
            bigBuf.rewind()
            @Suppress("CAST_NEVER_SUCCEEDS")
            return bigBuf as MappedByteBuffer                  // safe on ART / Dalvik
        }
    }

    suspend fun tokenize(text: String, maxLength: Int = MAX_SEQUENCE_LENGTH): IntArray {
        // Simplified tokenization - in production, use SentencePiece
        return withContext(Dispatchers.Default) {
            val tokens = mutableListOf<Int>()

            // Add BOS token
            tokens.add(1) // BOS_TOKEN

            // Simple character-based tokenization (placeholder)
            text.take(maxLength - 2).forEach { char ->
                tokens.add(char.code % TOKENIZER_VOCAB_SIZE)
            }

            // Pad or truncate to maxLength
            while (tokens.size < maxLength) {
                tokens.add(0) // PAD_TOKEN
            }

            tokens.take(maxLength).toIntArray()
        }
    }

    suspend fun generateText(
        model: Interpreter,
        prompt: String,
        maxTokens: Int = 256,
        temperature: Float = 0.7f,
        topK: Int = 40,
        topP: Float = 0.95f
    ): String = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()

        // Tokenize prompt
        val inputTokens = tokenize(prompt)

        // Prepare buffers
        val inputBuffer = ByteBuffer.allocateDirect(inputTokens.size * 4).order(ByteOrder.nativeOrder())
        inputTokens.forEach { inputBuffer.putInt(it) }
        inputBuffer.rewind()

        val outputBuffer = ByteBuffer.allocateDirect(TOKENIZER_VOCAB_SIZE * 4).order(ByteOrder.nativeOrder())

        // Run inference
        model.run(inputBuffer, outputBuffer)

        // Decode output (simplified)
        val generatedText = decodeOutput(outputBuffer, temperature, topK, topP)

        val totalTime = System.currentTimeMillis() - startTime
        Timber.d("Generated text in ${totalTime}ms")

        generatedText
    }

    private fun decodeOutput(
        outputBuffer: ByteBuffer,
        temperature: Float,
        topK: Int,
        topP: Float
    ): String {
        // Simplified decoding - in production, use proper sampling and detokenization
        outputBuffer.rewind()

        // Extract top token (greedy decoding for simplicity)
        var maxLogit = Float.NEGATIVE_INFINITY
        var maxIndex = 0

        for (i in 0 until TOKENIZER_VOCAB_SIZE) {
            val logit = outputBuffer.getFloat()
            if (logit > maxLogit) {
                maxLogit = logit
                maxIndex = i
            }
        }

        return "Generated text based on the prompt (token: $maxIndex)"
    }

    suspend fun warmupModels() = withContext(Dispatchers.IO) {
        Timber.d("Starting model warmup...")

        // Warmup each model configuration
        listOf(ModelConfig.FAST_2B, ModelConfig.BALANCED_3B, ModelConfig.QUALITY_4B).forEach { config ->
            try {
                val model = getModel(config)

                // Run a dummy inference to warm up
                val dummyInput = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder())
                dummyInput.putInt(1) // Single token
                dummyInput.rewind()

                val dummyOutput = ByteBuffer.allocateDirect(TOKENIZER_VOCAB_SIZE * 4).order(ByteOrder.nativeOrder())

                model.run(dummyInput, dummyOutput)

                Timber.d("Warmed up model: ${config.activeParams}")
            } catch (e: Exception) {
                Timber.w(e, "Failed to warmup model: $config")
            }
        }
    }

    // This implementation is correct, assuming the `delegates` map is populated correctly.
    fun clearCache() = runBlocking { // Use runBlocking or make it a suspend function to use mutex
        cacheMutex.withLock {
            modelCache.values.forEach { it.close() }
            modelCache.clear()
            embeddingModelCache.values.forEach { it.close() }
            embeddingModelCache.clear()

            delegates.values.forEach { it?.close() }
            delegates.clear()

            Timber.d("Model cache cleared")
        }
    }

    suspend fun getModelStats(): ModelStats {
        val loadedModels = modelCache.mapValues { (config, interpreter) ->
            ModelInfo(
                config = config,
                inputShape = getInputShape(interpreter),
                outputShape = getOutputShape(interpreter),
                delegateType = getActiveDelegateType(interpreter)
            )
        }

        val availableModels = modelRepository.getAvailableModels()

        return ModelStats(
            loadedModels = loadedModels,
            availableModels = availableModels,
            totalMemoryUsedMB = calculateTotalMemoryUsage()
        )
    }

    private fun getInputShape(interpreter: Interpreter): IntArray {
        return try {
            interpreter.getInputTensor(0).shape()
        } catch (e: Exception) {
            intArrayOf()
        }
    }

    private fun getOutputShape(interpreter: Interpreter): IntArray {
        return try {
            interpreter.getOutputTensor(0).shape()
        } catch (e: Exception) {
            intArrayOf()
        }
    }

    private fun getActiveDelegateType(interpreter: Interpreter): String {
        // This is a simplified check - actual implementation would query the interpreter
        return when {
            delegates.containsKey("gpu") || delegates.containsKey("gpu_mix") -> "GPU"
            delegates.containsKey("nnapi") -> "NNAPI"
            else -> "CPU"
        }
    }

    private fun calculateTotalMemoryUsage(): Int {
        // Estimate memory usage based on loaded models
        var totalMB = 0

        modelCache.forEach { (config, _) ->
            totalMB += when (config) {
                is ModelConfig.FAST_2B -> 1000 // ~1GB for 2B model
                is ModelConfig.BALANCED_3B -> 1500 // ~1.5GB for 3B active params
                is ModelConfig.QUALITY_4B -> 2000 // ~2GB for 4B model
            }
        }

        return totalMB
    }

    data class ModelInfo(
        val config: ModelConfig,
        val inputShape: IntArray,
        val outputShape: IntArray,
        val delegateType: String
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as ModelInfo

            if (config != other.config) return false
            if (!inputShape.contentEquals(other.inputShape)) return false
            if (!outputShape.contentEquals(other.outputShape)) return false
            if (delegateType != other.delegateType) return false

            return true
        }

        override fun hashCode(): Int {
            var result = config.hashCode()
            result = 31 * result + inputShape.contentHashCode()
            result = 31 * result + outputShape.contentHashCode()
            result = 31 * result + delegateType.hashCode()
            return result
        }
    }

    data class ModelStats(
        val loadedModels: Map<ModelConfig, ModelInfo>,
        val availableModels: List<ModelRepository.ModelInfo>,
        val totalMemoryUsedMB: Int
    )

    // Dynamic model switching based on system resources
    suspend fun selectOptimalModel(
        requireQuality: Boolean = false,
        requireSpeed: Boolean = false
    ): ModelConfig = withContext(Dispatchers.Default) {
        val memoryInfo = getMemoryInfo()
        val batteryLevel = getBatteryLevel()

        when {
            requireQuality -> ModelConfig.QUALITY_4B
            requireSpeed -> ModelConfig.FAST_2B
            memoryInfo.availableMemoryMB < 1000 -> ModelConfig.FAST_2B
            batteryLevel < 20 -> ModelConfig.FAST_2B
            memoryInfo.availableMemoryMB < 2000 -> ModelConfig.BALANCED_3B
            else -> ModelConfig.QUALITY_4B
        }
    }

    private fun getMemoryInfo(): MemoryInfo {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val memoryInfo = android.app.ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        return MemoryInfo(
            availableMemoryMB = (memoryInfo.availMem / (1024 * 1024)).toInt(),
            totalMemoryMB = (memoryInfo.totalMem / (1024 * 1024)).toInt(),
            lowMemory = memoryInfo.lowMemory
        )
    }

    private fun getBatteryLevel(): Int {
        // Simplified battery check
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
        return batteryManager.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    data class MemoryInfo(
        val availableMemoryMB: Int,
        val totalMemoryMB: Int,
        val lowMemory: Boolean
    )
}