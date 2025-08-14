// GeminiApiConfig.kt  ─────────────────────────────────────────────────────
package com.mygemma3n.aiapp.data

import com.mygemma3n.aiapp.config.ApiConfiguration

/**
 * Configuration for initialising [GeminiApiService].
 * Now uses centralized ApiConfiguration for defaults.
 */
data class GeminiApiConfig(
    val apiKey: String,
    val modelName: String = ApiConfiguration.Online.GEMINI_FLASH_MODEL,
    val temperature: Float = ApiConfiguration.Online.Defaults.TEMPERATURE,
    val topK: Int = ApiConfiguration.Online.Defaults.TOP_K,
    val topP: Float = ApiConfiguration.Online.Defaults.TOP_P,
    val maxOutputTokens: Int = ApiConfiguration.Online.Defaults.MAX_OUTPUT_TOKENS
) {
    companion object {
        /**
         * Create configuration optimized for specific online use cases
         */
        fun forQuiz(apiKey: String): GeminiApiConfig {
            val (temp, topK, maxTokens) = ApiConfiguration.getOnlineConfigForUseCase("quiz")
            return GeminiApiConfig(
                apiKey = apiKey,
                modelName = ApiConfiguration.Online.GEMINI_FLASH_MODEL,
                temperature = temp,
                topK = topK,
                maxOutputTokens = maxTokens
            )
        }
        
        fun forStory(apiKey: String): GeminiApiConfig {
            val (temp, topK, maxTokens) = ApiConfiguration.getOnlineConfigForUseCase("story")
            return GeminiApiConfig(
                apiKey = apiKey,
                modelName = ApiConfiguration.Online.GEMINI_FLASH_MODEL,
                temperature = temp,
                topK = topK,
                maxOutputTokens = maxTokens
            )
        }
        
        fun forChat(apiKey: String): GeminiApiConfig {
            val (temp, topK, maxTokens) = ApiConfiguration.getOnlineConfigForUseCase("chat")
            return GeminiApiConfig(
                apiKey = apiKey,
                modelName = ApiConfiguration.Online.GEMINI_FLASH_MODEL,
                temperature = temp,
                topK = topK,
                maxOutputTokens = maxTokens
            )
        }
        
        fun forTutor(apiKey: String): GeminiApiConfig {
            val (temp, topK, maxTokens) = ApiConfiguration.getOnlineConfigForUseCase("tutor")
            return GeminiApiConfig(
                apiKey = apiKey,
                modelName = ApiConfiguration.Online.GEMINI_FLASH_MODEL,
                temperature = temp,
                topK = topK,
                maxOutputTokens = maxTokens
            )
        }
    }
}