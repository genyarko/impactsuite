package com.example.mygemma3n.util

import com.example.mygemma3n.service.CostCalculationService
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CostPredictionUtils @Inject constructor(
    private val costCalculationService: CostCalculationService
) {
    
    /**
     * Estimate token count for input text (rough approximation)
     * Rule of thumb: ~4 characters per token for English text
     */
    private fun estimateTokenCount(text: String): Int {
        return (text.length / 4.0).toInt().coerceAtLeast(1)
    }
    
    /**
     * Predict cost before making API call
     */
    suspend fun predictRequestCost(
        modelName: String,
        inputText: String,
        expectedOutputTokens: Int,
        conversationHistory: List<String> = emptyList()
    ): CostPrediction {
        return try {
            // Estimate input tokens (including conversation history)
            val historyText = conversationHistory.joinToString(" ")
            val totalInputText = "$historyText $inputText"
            val estimatedInputTokens = estimateTokenCount(totalInputText)
            
            // Calculate estimated cost
            val estimatedCost = costCalculationService.calculateCost(
                modelName = modelName,
                inputTokens = estimatedInputTokens,
                outputTokens = expectedOutputTokens
            )
            
            Timber.d("Cost prediction for $modelName: ~${estimatedInputTokens + expectedOutputTokens} tokens ≈ $${String.format("%.4f", estimatedCost)}")
            
            CostPrediction(
                estimatedInputTokens = estimatedInputTokens,
                estimatedOutputTokens = expectedOutputTokens,
                estimatedTotalTokens = estimatedInputTokens + expectedOutputTokens,
                estimatedCost = estimatedCost,
                modelName = modelName,
                isSuccess = true
            )
        } catch (e: Exception) {
            Timber.e(e, "Error predicting cost for $modelName")
            CostPrediction(
                estimatedInputTokens = 0,
                estimatedOutputTokens = 0,
                estimatedTotalTokens = 0,
                estimatedCost = 0.0,
                modelName = modelName,
                isSuccess = false,
                errorMessage = e.message
            )
        }
    }
    
    /**
     * Check if request would exceed cost threshold
     */
    suspend fun wouldExceedCostThreshold(
        modelName: String,
        inputText: String,
        expectedOutputTokens: Int,
        costThreshold: Double,
        conversationHistory: List<String> = emptyList()
    ): CostWarning {
        val prediction = predictRequestCost(modelName, inputText, expectedOutputTokens, conversationHistory)
        
        return if (prediction.estimatedCost > costThreshold) {
            CostWarning(
                wouldExceed = true,
                estimatedCost = prediction.estimatedCost,
                threshold = costThreshold,
                warningMessage = "This request may cost $${String.format("%.4f", prediction.estimatedCost)}, which exceeds your threshold of $${String.format("%.4f", costThreshold)}",
                prediction = prediction
            )
        } else {
            CostWarning(
                wouldExceed = false,
                estimatedCost = prediction.estimatedCost,
                threshold = costThreshold,
                warningMessage = null,
                prediction = prediction
            )
        }
    }
    
    /**
     * Get affordable output token limit based on budget
     */
    suspend fun getAffordableOutputTokens(
        modelName: String,
        inputText: String,
        maxBudget: Double,
        conversationHistory: List<String> = emptyList()
    ): Int {
        return try {
            val historyText = conversationHistory.joinToString(" ")
            val totalInputText = "$historyText $inputText"
            val estimatedInputTokens = estimateTokenCount(totalInputText)
            
            // Calculate input cost first
            val inputCost = costCalculationService.calculateCost(
                modelName = modelName,
                inputTokens = estimatedInputTokens,
                outputTokens = 0
            )
            
            val remainingBudget = maxBudget - inputCost
            if (remainingBudget <= 0) return 0
            
            // Calculate max affordable output tokens
            costCalculationService.estimateMaxAffordableOutputTokens(
                modelName = modelName,
                remainingOutputTokenQuota = Long.MAX_VALUE,
                maxBudget = remainingBudget
            )
        } catch (e: Exception) {
            Timber.e(e, "Error calculating affordable output tokens for $modelName")
            0
        }
    }
}

data class CostPrediction(
    val estimatedInputTokens: Int,
    val estimatedOutputTokens: Int,
    val estimatedTotalTokens: Int,
    val estimatedCost: Double,
    val modelName: String,
    val isSuccess: Boolean,
    val errorMessage: String? = null
)

data class CostWarning(
    val wouldExceed: Boolean,
    val estimatedCost: Double,
    val threshold: Double,
    val warningMessage: String?,
    val prediction: CostPrediction
)