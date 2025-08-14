package com.mygemma3n.aiapp.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Entity(tableName = "pricing_config")
data class PricingConfigEntity(
    @PrimaryKey
    val id: String, // Format: "modelName_tokenType" e.g., "gemini-2.5-flash_input"
    val modelName: String,
    val tokenType: TokenType,
    val pricePerMillionTokens: Double, // Price per 1M tokens in USD
    val isActive: Boolean = true,
    val effectiveDate: LocalDateTime = LocalDateTime.now(),
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now()
) {
    fun calculateCost(tokenCount: Int): Double {
        return (tokenCount.toDouble() / 1_000_000.0) * pricePerMillionTokens
    }
}

enum class TokenType(val displayName: String) {
    INPUT("Input"),
    OUTPUT("Output"),
    CONTEXT_CACHE("Context Cache"),
    AUDIO_INPUT("Audio Input"),
    AUDIO_OUTPUT("Audio Output"),
    GROUNDING("Grounding"),
    LIVE_INPUT("Live Input"),
    LIVE_OUTPUT("Live Output")
}

// Current Gemini pricing configuration (as of your provided rates)
object GeminiPricingConfig {
    
    // Gemini 2.5 Flash pricing
    val GEMINI_FLASH_INPUT = PricingConfigEntity(
        id = "gemini-2.5-flash_input",
        modelName = "gemini-2.5-flash",
        tokenType = TokenType.INPUT,
        pricePerMillionTokens = 0.30 // $0.30 per 1M tokens
    )
    
    val GEMINI_FLASH_OUTPUT = PricingConfigEntity(
        id = "gemini-2.5-flash_output",
        modelName = "gemini-2.5-flash",
        tokenType = TokenType.OUTPUT,
        pricePerMillionTokens = 2.50 // $2.50 per 1M tokens
    )
    
    val GEMINI_FLASH_CONTEXT_CACHE = PricingConfigEntity(
        id = "gemini-2.5-flash_context_cache",
        modelName = "gemini-2.5-flash",
        tokenType = TokenType.CONTEXT_CACHE,
        pricePerMillionTokens = 0.075 // $0.075 per 1M tokens
    )
    
    // Gemini 2.5 Pro pricing (same as Flash based on your data)
    val GEMINI_PRO_INPUT = PricingConfigEntity(
        id = "gemini-2.5-pro_input", 
        modelName = "gemini-2.5-pro",
        tokenType = TokenType.INPUT,
        pricePerMillionTokens = 0.30
    )
    
    val GEMINI_PRO_OUTPUT = PricingConfigEntity(
        id = "gemini-2.5-pro_output",
        modelName = "gemini-2.5-pro", 
        tokenType = TokenType.OUTPUT,
        pricePerMillionTokens = 2.50
    )
    
    // Audio pricing
    val GEMINI_AUDIO_INPUT = PricingConfigEntity(
        id = "gemini-2.5-flash_audio_input",
        modelName = "gemini-2.5-flash",
        tokenType = TokenType.AUDIO_INPUT,
        pricePerMillionTokens = 1.00 // $1.00 per 1M tokens
    )
    
    val GEMINI_AUDIO_OUTPUT = PricingConfigEntity(
        id = "gemini-2.5-flash_audio_output", 
        modelName = "gemini-2.5-flash",
        tokenType = TokenType.AUDIO_OUTPUT,
        pricePerMillionTokens = 12.00 // $12.00 per 1M tokens
    )
    
    // Live API pricing
    val GEMINI_LIVE_INPUT = PricingConfigEntity(
        id = "gemini-2.5-flash_live_input",
        modelName = "gemini-2.5-flash",
        tokenType = TokenType.LIVE_INPUT, 
        pricePerMillionTokens = 0.50 // $0.50 per 1M tokens
    )
    
    val GEMINI_LIVE_OUTPUT = PricingConfigEntity(
        id = "gemini-2.5-flash_live_output",
        modelName = "gemini-2.5-flash",
        tokenType = TokenType.LIVE_OUTPUT,
        pricePerMillionTokens = 2.00 // $2.00 per 1M tokens
    )
    
    // On-device models (free)
    val GEMMA_3N_INPUT = PricingConfigEntity(
        id = "gemma-3n-e2b-it_input",
        modelName = "gemma-3n-e2b-it",
        tokenType = TokenType.INPUT,
        pricePerMillionTokens = 0.0 // Free for on-device
    )
    
    val GEMMA_3N_OUTPUT = PricingConfigEntity(
        id = "gemma-3n-e2b-it_output", 
        modelName = "gemma-3n-e2b-it",
        tokenType = TokenType.OUTPUT,
        pricePerMillionTokens = 0.0 // Free for on-device
    )
    
    // OpenAI GPT-5 mini pricing (as of January 2025)
    val GPT_5_MINI_INPUT = PricingConfigEntity(
        id = "gpt-5-mini_input",
        modelName = "gpt-5-mini",
        tokenType = TokenType.INPUT,
        pricePerMillionTokens = 0.15 // $0.15 per 1M tokens
    )
    
    val GPT_5_MINI_OUTPUT = PricingConfigEntity(
        id = "gpt-5-mini_output",
        modelName = "gpt-5-mini",
        tokenType = TokenType.OUTPUT,
        pricePerMillionTokens = 0.60 // $0.60 per 1M tokens
    )
    
    // DALL-E 3 pricing (per image, not per token)
    val DALLE_3_IMAGE = PricingConfigEntity(
        id = "dall-e-3_output",
        modelName = "dall-e-3",
        tokenType = TokenType.OUTPUT,
        pricePerMillionTokens = 40000.0 // $0.04 per image = $40 per 1M "images"
    )
    
    fun getAllDefaultPricing(): List<PricingConfigEntity> {
        return listOf(
            GEMINI_FLASH_INPUT,
            GEMINI_FLASH_OUTPUT,
            GEMINI_FLASH_CONTEXT_CACHE,
            GEMINI_PRO_INPUT,
            GEMINI_PRO_OUTPUT,
            GEMINI_AUDIO_INPUT,
            GEMINI_AUDIO_OUTPUT,
            GEMINI_LIVE_INPUT,
            GEMINI_LIVE_OUTPUT,
            GEMMA_3N_INPUT,
            GEMMA_3N_OUTPUT,
            GPT_5_MINI_INPUT,
            GPT_5_MINI_OUTPUT,
            DALLE_3_IMAGE
        )
    }
}

// Helper data classes
data class ModelPricing(
    val modelName: String,
    val inputPricePerMillion: Double,
    val outputPricePerMillion: Double,
    val contextCachePricePerMillion: Double? = null
) {
    fun calculateCost(inputTokens: Int, outputTokens: Int, contextCacheTokens: Int = 0): Double {
        val inputCost = (inputTokens.toDouble() / 1_000_000.0) * inputPricePerMillion
        val outputCost = (outputTokens.toDouble() / 1_000_000.0) * outputPricePerMillion
        val cacheCost = if (contextCachePricePerMillion != null && contextCacheTokens > 0) {
            (contextCacheTokens.toDouble() / 1_000_000.0) * contextCachePricePerMillion
        } else 0.0
        
        return inputCost + outputCost + cacheCost
    }
}

data class CostBreakdown(
    val inputTokens: Int,
    val outputTokens: Int,
    val inputCost: Double,
    val outputCost: Double,
    val totalCost: Double,
    val modelName: String
)