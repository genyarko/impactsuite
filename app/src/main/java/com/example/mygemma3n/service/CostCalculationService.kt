package com.example.mygemma3n.service

import com.example.mygemma3n.data.local.PricingConfigDao
import com.example.mygemma3n.data.local.entities.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CostCalculationService @Inject constructor(
    private val pricingConfigDao: PricingConfigDao
) {

    /**
     * Calculate cost for a given model and token usage
     */
    suspend fun calculateCost(
        modelName: String,
        inputTokens: Int,
        outputTokens: Int,
        contextCacheTokens: Int = 0,
        audioInputTokens: Int = 0,
        audioOutputTokens: Int = 0
    ): Double = withContext(Dispatchers.IO) {
        try {
            var totalCost = 0.0

            // Calculate text input cost
            if (inputTokens > 0) {
                val inputPricing = pricingConfigDao.getPricingByModelAndType(modelName, TokenType.INPUT)
                if (inputPricing != null) {
                    totalCost += inputPricing.calculateCost(inputTokens)
                    Timber.d("Input cost for $modelName: ${inputPricing.calculateCost(inputTokens)} ($inputTokens tokens)")
                }
            }

            // Calculate text output cost
            if (outputTokens > 0) {
                val outputPricing = pricingConfigDao.getPricingByModelAndType(modelName, TokenType.OUTPUT)
                if (outputPricing != null) {
                    totalCost += outputPricing.calculateCost(outputTokens)
                    Timber.d("Output cost for $modelName: ${outputPricing.calculateCost(outputTokens)} ($outputTokens tokens)")
                }
            }

            // Calculate context cache cost if applicable
            if (contextCacheTokens > 0) {
                val cachePricing = pricingConfigDao.getPricingByModelAndType(modelName, TokenType.CONTEXT_CACHE)
                if (cachePricing != null) {
                    totalCost += cachePricing.calculateCost(contextCacheTokens)
                    Timber.d("Context cache cost for $modelName: ${cachePricing.calculateCost(contextCacheTokens)} ($contextCacheTokens tokens)")
                }
            }

            // Calculate audio input cost if applicable
            if (audioInputTokens > 0) {
                val audioInputPricing = pricingConfigDao.getPricingByModelAndType(modelName, TokenType.AUDIO_INPUT)
                if (audioInputPricing != null) {
                    totalCost += audioInputPricing.calculateCost(audioInputTokens)
                    Timber.d("Audio input cost for $modelName: ${audioInputPricing.calculateCost(audioInputTokens)} ($audioInputTokens tokens)")
                }
            }

            // Calculate audio output cost if applicable
            if (audioOutputTokens > 0) {
                val audioOutputPricing = pricingConfigDao.getPricingByModelAndType(modelName, TokenType.AUDIO_OUTPUT)
                if (audioOutputPricing != null) {
                    totalCost += audioOutputPricing.calculateCost(audioOutputTokens)
                    Timber.d("Audio output cost for $modelName: ${audioOutputPricing.calculateCost(audioOutputTokens)} ($audioOutputTokens tokens)")
                }
            }

            Timber.d("Total cost for $modelName: $$totalCost")
            return@withContext totalCost

        } catch (e: Exception) {
            Timber.e(e, "Error calculating cost for model $modelName")
            return@withContext 0.0
        }
    }

    /**
     * Get model pricing information
     */
    suspend fun getModelPricing(modelName: String): ModelPricing? = withContext(Dispatchers.IO) {
        try {
            val inputPricing = pricingConfigDao.getPricingByModelAndType(modelName, TokenType.INPUT)
            val outputPricing = pricingConfigDao.getPricingByModelAndType(modelName, TokenType.OUTPUT)
            val contextCachePricing = pricingConfigDao.getPricingByModelAndType(modelName, TokenType.CONTEXT_CACHE)

            if (inputPricing != null && outputPricing != null) {
                return@withContext ModelPricing(
                    modelName = modelName,
                    inputPricePerMillion = inputPricing.pricePerMillionTokens,
                    outputPricePerMillion = outputPricing.pricePerMillionTokens,
                    contextCachePricePerMillion = contextCachePricing?.pricePerMillionTokens
                )
            }
            return@withContext null
        } catch (e: Exception) {
            Timber.e(e, "Error getting pricing for model $modelName")
            return@withContext null
        }
    }

    /**
     * Calculate detailed cost breakdown
     */
    suspend fun calculateCostBreakdown(
        modelName: String,
        inputTokens: Int,
        outputTokens: Int
    ): CostBreakdown? = withContext(Dispatchers.IO) {
        try {
            val inputPricing = pricingConfigDao.getPricingByModelAndType(modelName, TokenType.INPUT)
            val outputPricing = pricingConfigDao.getPricingByModelAndType(modelName, TokenType.OUTPUT)

            if (inputPricing != null && outputPricing != null) {
                val inputCost = inputPricing.calculateCost(inputTokens)
                val outputCost = outputPricing.calculateCost(outputTokens)
                val totalCost = inputCost + outputCost

                return@withContext CostBreakdown(
                    inputTokens = inputTokens,
                    outputTokens = outputTokens,
                    inputCost = inputCost,
                    outputCost = outputCost,
                    totalCost = totalCost,
                    modelName = modelName
                )
            }
            return@withContext null
        } catch (e: Exception) {
            Timber.e(e, "Error calculating cost breakdown for model $modelName")
            return@withContext null
        }
    }

    /**
     * Estimate maximum output tokens user can afford based on remaining quota
     */
    suspend fun estimateMaxAffordableOutputTokens(
        modelName: String,
        remainingOutputTokenQuota: Long,
        maxBudget: Double? = null
    ): Int = withContext(Dispatchers.IO) {
        try {
            val outputPricing = pricingConfigDao.getPricingByModelAndType(modelName, TokenType.OUTPUT)
                ?: return@withContext 0

            // Calculate based on token quota
            val quotaBasedLimit = remainingOutputTokenQuota.toInt()

            // Calculate based on budget if provided
            val budgetBasedLimit = if (maxBudget != null && maxBudget > 0) {
                val tokensPerDollar = 1_000_000.0 / outputPricing.pricePerMillionTokens
                (maxBudget * tokensPerDollar).toInt()
            } else {
                Int.MAX_VALUE
            }

            // Return the more restrictive limit
            val affordableTokens = minOf(quotaBasedLimit, budgetBasedLimit)
            Timber.d("Max affordable output tokens for $modelName: $affordableTokens (quota: $quotaBasedLimit, budget: $budgetBasedLimit)")
            
            return@withContext affordableTokens

        } catch (e: Exception) {
            Timber.e(e, "Error estimating max affordable tokens for model $modelName")
            return@withContext 0
        }
    }

    /**
     * Check if a request would exceed cost limits
     */
    suspend fun wouldExceedCostLimit(
        modelName: String,
        inputTokens: Int,
        outputTokens: Int,
        maxCostLimit: Double
    ): Boolean = withContext(Dispatchers.IO) {
        val estimatedCost = calculateCost(modelName, inputTokens, outputTokens)
        return@withContext estimatedCost > maxCostLimit
    }

    /**
     * Get cost per token for specific model and token type
     */
    suspend fun getCostPerToken(
        modelName: String,
        tokenType: TokenType
    ): Double = withContext(Dispatchers.IO) {
        try {
            val pricing = pricingConfigDao.getPricingByModelAndType(modelName, tokenType)
            return@withContext pricing?.pricePerMillionTokens?.div(1_000_000.0) ?: 0.0
        } catch (e: Exception) {
            Timber.e(e, "Error getting cost per token for $modelName $tokenType")
            return@withContext 0.0
        }
    }

    /**
     * Compare costs between different models for same usage
     */
    suspend fun compareModelCosts(
        modelNames: List<String>,
        inputTokens: Int,
        outputTokens: Int
    ): List<ModelCostComparison> = withContext(Dispatchers.IO) {
        modelNames.mapNotNull { modelName ->
            try {
                val cost = calculateCost(modelName, inputTokens, outputTokens)
                val pricing = getModelPricing(modelName)
                
                if (pricing != null) {
                    ModelCostComparison(
                        modelName = modelName,
                        totalCost = cost,
                        inputCostPerMillion = pricing.inputPricePerMillion,
                        outputCostPerMillion = pricing.outputPricePerMillion
                    )
                } else null
            } catch (e: Exception) {
                Timber.e(e, "Error comparing cost for model $modelName")
                null
            }
        }.sortedBy { it.totalCost }
    }

    /**
     * Initialize default pricing if not already present
     */
    suspend fun initializeDefaultPricing() = withContext(Dispatchers.IO) {
        try {
            val existingPricing = pricingConfigDao.getAllActivePricing()
            if (existingPricing.isEmpty()) {
                Timber.i("Initializing default pricing configuration")
                GeminiPricingConfig.getAllDefaultPricing().forEach { pricing ->
                    pricingConfigDao.insertPricing(pricing)
                }
                Timber.i("Default pricing configuration initialized")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error initializing default pricing")
        }
    }
}

// Supporting data classes
data class ModelCostComparison(
    val modelName: String,
    val totalCost: Double,
    val inputCostPerMillion: Double,
    val outputCostPerMillion: Double
)

data class CostEstimation(
    val estimatedCost: Double,
    val maxAffordableOutputTokens: Int,
    val wouldExceedLimit: Boolean,
    val breakdown: CostBreakdown?
)