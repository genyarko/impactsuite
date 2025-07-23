// GemmaModelHandler.kt
package com.example.mygemma3n.gemma

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation that runs Gemma (or any .task LLM bundle) via MediaPipe LLM Inference.
 */
@Singleton
class GemmaModelHandler @Inject constructor(
    private val context: Context
) {

    private var llmInference: LlmInference? = null
    private val mutex = Mutex()

    data class ModelConfig(
        val modelPath: String,
        val maxTokens: Int = 512,
        val temperature: Float = 0.7f,
        val topK: Int = 40,
        val randomSeed: Int = 0
    )

    /** Load or reload the model. */
    suspend fun initialize(config: ModelConfig) = withContext(Dispatchers.IO) {
        mutex.withLock {
            // Close any previous instance
            llmInference?.close()
            llmInference = null

            val modelFile = File(config.modelPath)
            require(modelFile.exists()) {
                "Model file does not exist: ${config.modelPath}"
            }

            Timber.d("Initializing Gemma model from: ${config.modelPath}")

            // Only .task bundles are supported here
            check(config.modelPath.endsWith(".task")) {
                "Unsupported model format. Expected .task file."
            }

            // Build MediaPipe options
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(config.modelPath)
                .setMaxTokens(config.maxTokens)
                .build()

            llmInference = LlmInference.createFromOptions(context, options)
            Timber.d("Gemma model initialized successfully")
        }
    }

    /** Streamed generation as a Flow. */
    suspend fun generateText(prompt: String): Flow<String> = flow {
        // Take a snapshot of the inference object under the lock
        val inference = mutex.withLock {
            llmInference ?: error("Model not initialized")
        }

        // Run the heavy work off the main thread
        val response = withContext(Dispatchers.Default) {
            inference.generateResponse(prompt)
        }
        emit(response)
    }.flowOn(Dispatchers.Default)

    /** Convenience one-shot generator. */
    suspend fun generateTextAsync(prompt: String): String {
        val inference = mutex.withLock {
            llmInference ?: error("Model not initialized")
        }
        return withContext(Dispatchers.Default) {
            inference.generateResponse(prompt)
        }
    }

    fun isInitialized(): Boolean = llmInference != null

    /** Release resources. */
    suspend fun close() = mutex.withLock {
        llmInference?.close()
        llmInference = null
    }
}

