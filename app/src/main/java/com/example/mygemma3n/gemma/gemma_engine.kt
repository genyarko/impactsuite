package com.example.mygemma3n.gemma

// GemmaEngine.kt - Main inference engine with model binding

import android.content.Context
import android.text.util.Rfc822Tokenizer.tokenize
import com.example.mygemma3n.shared_utilities.PerformanceMonitor
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class GemmaEngine @Inject constructor(
    private val context: Context,
    private val modelCache: ModelCache,
    private val performanceMonitor: PerformanceMonitor
) {
    private var interpreter: Interpreter? = null
    private val inferenceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    data class InferenceConfig(
        val modelPath: String,
        val useGpu: Boolean = true,
        val useNnapi: Boolean = true,
        val numThreads: Int = 4,
        val enablePLE: Boolean = true,
        val kvCacheSize: Int = 4096
    )

    suspend fun initialize(config: InferenceConfig) = withContext(Dispatchers.IO) {
        val modelBuffer = loadModelBuffer(config.modelPath)

        val options = Interpreter.Options().apply {
            setNumThreads(config.numThreads)

            if (config.useGpu) {
                val gpuDelegate = GpuDelegate(
                    GpuDelegate.Options().apply {
                        setPrecisionLossAllowed(true)
                        setQuantizedModelsAllowed(true)
                        setInferencePreference(
                            GpuDelegate.Options.InferencePreference.SUSTAINED_SPEED
                        )
                    }
                )
                addDelegate(gpuDelegate)
            }

            if (config.useNnapi) {
                val nnapiDelegate = NnApiDelegate(
                    NnApiDelegate.Options().apply {
                        setAllowFp16(true)
                        setExecutionPreference(
                            NnApiDelegate.Options.ExecutionPreference.SUSTAINED_SPEED
                        )
                        setCacheDir(context.cacheDir.absolutePath)
                        setModelToken("gemma_3n")
                    }
                )
                addDelegate(nnapiDelegate)
            }

            // Enable PLE caching
            if (config.enablePLE) {
                setAllowFp16PrecisionForFp32(true)
                setUseXNNPACK(true)
                experimental_setUseCaching(true)
                experimental_setCachingDir(context.cacheDir.absolutePath)
            }
        }

        interpreter = Interpreter(modelBuffer, options)
    }

    suspend fun generateText(
        prompt: String,
        maxTokens: Int = 256,
        temperature: Float = 0.7f,
        topK: Int = 40,
        topP: Float = 0.95f
    ): Flow<String> = flow {
        requireNotNull(interpreter) { "Model not initialized" }

        val startTime = System.currentTimeMillis()
        var generatedTokens = 0

        // Tokenize input
        val inputTokens = tokenize(prompt)
        val inputBuffer = prepareInputBuffer(inputTokens)

        // Prepare output buffers
        val outputBuffer = ByteBuffer.allocateDirect(4 * vocabSize)

        // Initialize KV cache for efficient generation
        val kvCache = KVCache(cacheSize = 4096)

        // Prefill phase
        interpreter!!.run(inputBuffer, outputBuffer)
        performanceMonitor.recordPrefill(System.currentTimeMillis() - startTime)

        // Decode phase - streaming generation
        while (generatedTokens < maxTokens) {
            val nextToken = sampleToken(outputBuffer, temperature, topK, topP)

            if (nextToken == EOS_TOKEN) break

            val decodedText = decodeToken(nextToken)
            emit(decodedText)

            generatedTokens++

            // Update KV cache and prepare next input
            kvCache.update(nextToken)
            updateInputBuffer(inputBuffer, nextToken)

            // Run next inference
            val decodeStart = System.currentTimeMillis()
            interpreter!!.run(inputBuffer, outputBuffer)
            performanceMonitor.recordDecode(System.currentTimeMillis() - decodeStart)
        }

        performanceMonitor.logMetrics(generatedTokens)
    }

    // Mix'n'match implementation for dynamic model sizing
    suspend fun createMixNMatchModel(
        baseSize: Float,
        targetSize: Float,
        ratio: Float
    ): Interpreter = withContext(Dispatchers.IO) {
        val modelPath = "gemma_3n_${baseSize}b_it.tflite"
        val modelBuffer = loadModelBuffer(modelPath)

        // Configure for dynamic parameter activation
        val options = Interpreter.Options().apply {
            experimental_setDynamicParameterActivation(true)
            experimental_setActivationRatio(ratio)
            experimental_setTargetParams(targetSize)
        }

        Interpreter(modelBuffer, options)
    }
}