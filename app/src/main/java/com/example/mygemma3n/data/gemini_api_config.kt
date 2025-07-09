// GeminiApiConfig.kt  ─────────────────────────────────────────────────────
package com.example.mygemma3n.data

/**
 * Configuration for initialising [GeminiApiService].
 */
data class GeminiApiConfig(
    val apiKey: String,
    val modelName: String = GeminiApiService.PRIMARY_GENERATION_MODEL,
    val temperature: Float = 0.7f,
    val topK: Int = 40,
    val topP: Float = 0.95f,
    val maxOutputTokens: Int = 8_192        // hard limit for Gemini 1.5
)
