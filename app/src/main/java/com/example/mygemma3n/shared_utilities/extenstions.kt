package com.example.mygemma3n.shared_utilities

// File: Extensions.kt

import com.example.mygemma3n.gemma.GemmaEngine
import com.example.mygemma3n.gemma.GemmaModelManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

// Extension functions for GemmaEngine

enum class ModelVariant {
    FAST_2B,
    BALANCED_3B,
    QUALITY_4B
}

suspend fun GemmaEngine.generateWithModel(
    prompt: String,
    modelVariant: ModelVariant,
    maxTokens: Int = 256,
    temperature: Float = 0.7f
): String {
    val config = when (modelVariant) {
        ModelVariant.FAST_2B -> GemmaEngine.GenerationConfig(
            maxNewTokens = maxTokens,
            temperature = temperature
        )
        ModelVariant.BALANCED_3B -> GemmaEngine.GenerationConfig(
            maxNewTokens = maxTokens,
            temperature = temperature
        )
        ModelVariant.QUALITY_4B -> GemmaEngine.GenerationConfig(
            maxNewTokens = maxTokens,
            temperature = temperature,
            topK = 50,
            topP = 0.95f
        )
    }

    return generateText(prompt, config)
        .toList()
        .joinToString("")
}

suspend fun GemmaEngine.generateText(
    prompt: String,
    imageInput: ByteArray? = null,
    audioInput: FloatArray? = null,
    maxTokens: Int = 256,
    temperature: Float = 0.7f,
    modelConfig: GemmaModelManager.ModelConfig? = null
): Flow<String> {
    // Build multimodal prompt if needed
    val fullPrompt = buildString {
        append(prompt)

        imageInput?.let {
            append("\n[IMAGE INPUT PROVIDED: ${it.size} bytes]")
        }

        audioInput?.let {
            append("\n[AUDIO INPUT PROVIDED: ${it.size} samples]")
        }
    }

    return generateText(
        fullPrompt,
        GemmaEngine.GenerationConfig(
            maxNewTokens = maxTokens,
            temperature = temperature
        )
    )
}

suspend fun GemmaEngine.generateEmbedding(text: String): FloatArray {
    // Simplified embedding generation
    // In production, use dedicated embedding model
    val tokens = text.split(" ").size
    return FloatArray(768) { index ->
        // Generate deterministic embeddings based on text
        val hash = text.hashCode()
        val value = (hash + index) % 1000 / 1000f
        value
    }
}

// Flow utilities
suspend fun <T> Flow<T>.toList(): List<T> {
    val list = mutableListOf<T>()
    collect { list.add(it) }
    return list
}