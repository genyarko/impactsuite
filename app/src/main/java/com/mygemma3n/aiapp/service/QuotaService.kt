package com.mygemma3n.aiapp.service

import com.mygemma3n.aiapp.data.local.entities.*
import com.mygemma3n.aiapp.data.local.UserQuotaDao
import com.mygemma3n.aiapp.data.local.PricingConfigDao
import com.mygemma3n.aiapp.data.repository.TokenUsageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QuotaService @Inject constructor(
    private val userQuotaDao: UserQuotaDao,
    private val pricingConfigDao: PricingConfigDao,
    private val costCalculationService: CostCalculationService,
    private val tokenUsageRepository: TokenUsageRepository
) {

    /**
     * Check if user can proceed with API call based on token limits
     */
    suspend fun checkQuota(
        userId: String,
        modelName: String,
        estimatedInputTokens: Int,
        estimatedOutputTokens: Int
    ): QuotaCheckResult = withContext(Dispatchers.IO) {
        try {
            // Get user quota
            val userQuota = userQuotaDao.getUserQuota(userId)
                ?: return@withContext QuotaCheckResult(
                    canProceed = false,
                    reason = "User quota not found. Please contact support."
                )

            // Check if quota needs monthly reset
            val resetQuota = checkAndResetMonthlyQuota(userQuota)

            // Check if user has enough tokens
            if (!resetQuota.canUseTokens(estimatedInputTokens, estimatedOutputTokens)) {
                val reason = when {
                    resetQuota.isInputLimitReached -> "Monthly input token limit reached (${resetQuota.monthlyInputTokenLimit} tokens)"
                    resetQuota.isOutputLimitReached -> "Monthly output token limit reached (${resetQuota.monthlyOutputTokenLimit} tokens)"
                    else -> "Monthly token limit would be exceeded"
                }
                
                return@withContext QuotaCheckResult(
                    canProceed = false,
                    reason = reason,
                    inputTokensRemaining = resetQuota.inputTokensRemaining,
                    outputTokensRemaining = resetQuota.outputTokensRemaining
                )
            }

            // Calculate estimated cost
            val estimatedCost = costCalculationService.calculateCost(
                modelName = modelName,
                inputTokens = estimatedInputTokens,
                outputTokens = estimatedOutputTokens
            )

            Timber.d("Quota check passed for user $userId: ${estimatedInputTokens}in + ${estimatedOutputTokens}out tokens, cost: $${estimatedCost}")

            return@withContext QuotaCheckResult(
                canProceed = true,
                inputTokensRemaining = resetQuota.inputTokensRemaining - estimatedInputTokens,
                outputTokensRemaining = resetQuota.outputTokensRemaining - estimatedOutputTokens,
                estimatedCost = estimatedCost
            )

        } catch (e: Exception) {
            Timber.e(e, "Error checking quota for user $userId")
            return@withContext QuotaCheckResult(
                canProceed = false,
                reason = "Error checking quota: ${e.message}"
            )
        }
    }

    /**
     * Update user quota after successful API call
     */
    suspend fun updateQuotaUsage(
        userId: String,
        modelName: String,
        actualInputTokens: Int,
        actualOutputTokens: Int
    ): QuotaUsageUpdate = withContext(Dispatchers.IO) {
        try {
            val userQuota = userQuotaDao.getUserQuota(userId)
                ?: throw IllegalStateException("User quota not found")

            // Calculate actual cost
            val actualCost = costCalculationService.calculateCost(
                modelName = modelName,
                inputTokens = actualInputTokens,
                outputTokens = actualOutputTokens
            )

            // Update quota usage
            val updatedQuota = userQuota.copy(
                currentMonthInputTokens = userQuota.currentMonthInputTokens + actualInputTokens,
                currentMonthOutputTokens = userQuota.currentMonthOutputTokens + actualOutputTokens,
                updatedAt = LocalDateTime.now()
            )

            userQuotaDao.updateUserQuota(updatedQuota)

            Timber.d("Updated quota for user $userId: +${actualInputTokens}in +${actualOutputTokens}out tokens, cost: $${actualCost}")

            return@withContext QuotaUsageUpdate(
                userId = userId,
                inputTokensUsed = actualInputTokens,
                outputTokensUsed = actualOutputTokens,
                costIncurred = actualCost
            )

        } catch (e: Exception) {
            Timber.e(e, "Error updating quota usage for user $userId")
            throw e
        }
    }

    /**
     * Get current quota status for user
     */
    suspend fun getQuotaStatus(userId: String): UserQuotaEntity? = withContext(Dispatchers.IO) {
        try {
            val quota = userQuotaDao.getUserQuota(userId)
            return@withContext quota?.let { checkAndResetMonthlyQuota(it) }
        } catch (e: Exception) {
            Timber.e(e, "Error getting quota status for user $userId")
            return@withContext null
        }
    }

    /**
     * Create or update user quota
     */
    suspend fun createOrUpdateUserQuota(
        userId: String,
        plan: SubscriptionPlan
    ): UserQuotaEntity = withContext(Dispatchers.IO) {
        try {
            val existingQuota = userQuotaDao.getUserQuota(userId)
            
            val newQuota = if (existingQuota != null) {
                // Update existing quota (preserve current usage if in same month)
                val isNewMonth = existingQuota.currentPeriodStart.monthValue != LocalDateTime.now().monthValue ||
                        existingQuota.currentPeriodStart.year != LocalDateTime.now().year
                
                existingQuota.copy(
                    planType = plan.planName,
                    monthlyInputTokenLimit = plan.monthlyInputTokenLimit,
                    monthlyOutputTokenLimit = plan.monthlyOutputTokenLimit,
                    currentMonthInputTokens = if (isNewMonth) 0 else existingQuota.currentMonthInputTokens,
                    currentMonthOutputTokens = if (isNewMonth) 0 else existingQuota.currentMonthOutputTokens,
                    currentPeriodStart = if (isNewMonth) LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0) else existingQuota.currentPeriodStart,
                    updatedAt = LocalDateTime.now()
                )
            } else {
                // Create new quota
                plan.toUserQuotaEntity(userId)
            }

            userQuotaDao.insertOrUpdateUserQuota(newQuota)
            return@withContext newQuota

        } catch (e: Exception) {
            Timber.e(e, "Error creating/updating quota for user $userId")
            throw e
        }
    }

    /**
     * Estimate tokens for a given prompt (improved estimation)
     */
    suspend fun estimateTokens(text: String, isInput: Boolean = true): Int {
        // Improved token estimation based on text characteristics
        val baseEstimate = text.length / 4 // Basic 4 chars per token
        
        // Adjust based on text complexity
        val wordCount = text.split("\\s+".toRegex()).size
        val avgWordLength = if (wordCount > 0) text.length.toDouble() / wordCount else 4.0
        
        // More complex text (longer words) tends to have fewer tokens per character
        val complexityFactor = when {
            avgWordLength > 8 -> 0.8 // Technical/complex text
            avgWordLength < 4 -> 1.2 // Simple/short words
            else -> 1.0
        }
        
        val adjustedEstimate = (baseEstimate * complexityFactor).toInt()
        
        // Add some buffer for output tokens (they tend to be slightly higher)
        return if (isInput) adjustedEstimate else (adjustedEstimate * 1.1).toInt()
    }

    /**
     * Check if user quota needs monthly reset and perform reset if needed
     */
    private suspend fun checkAndResetMonthlyQuota(quota: UserQuotaEntity): UserQuotaEntity {
        val now = LocalDateTime.now()
        val quotaMonth = quota.currentPeriodStart.monthValue
        val quotaYear = quota.currentPeriodStart.year
        
        return if (now.monthValue != quotaMonth || now.year != quotaYear) {
            // Reset quota for new month
            val resetQuota = quota.copy(
                currentMonthInputTokens = 0,
                currentMonthOutputTokens = 0,
                currentPeriodStart = now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0),
                updatedAt = now
            )
            
            userQuotaDao.updateUserQuota(resetQuota)
            Timber.i("Reset monthly quota for user ${quota.userId}")
            resetQuota
        } else {
            quota
        }
    }

    /**
     * Check quota without updating (for readonly operations)
     */
    suspend fun canUserAfford(
        userId: String,
        modelName: String,
        estimatedInputTokens: Int,
        estimatedOutputTokens: Int
    ): Boolean = withContext(Dispatchers.IO) {
        val result = checkQuota(userId, modelName, estimatedInputTokens, estimatedOutputTokens)
        return@withContext result.canProceed
    }

    /**
     * Get usage statistics for user
     */
    suspend fun getUserUsageStats(userId: String): UserUsageStats? = withContext(Dispatchers.IO) {
        try {
            val quota = getQuotaStatus(userId) ?: return@withContext null
            
            return@withContext UserUsageStats(
                userId = userId,
                planType = quota.planType,
                currentMonthInputTokens = quota.currentMonthInputTokens,
                currentMonthOutputTokens = quota.currentMonthOutputTokens,
                monthlyInputTokenLimit = quota.monthlyInputTokenLimit,
                monthlyOutputTokenLimit = quota.monthlyOutputTokenLimit,
                usagePercentage = quota.getUsagePercentage(),
                daysUntilReset = java.time.temporal.ChronoUnit.DAYS.between(
                    LocalDateTime.now().toLocalDate(),
                    quota.currentPeriodStart.plusMonths(1).toLocalDate()
                ).toInt()
            )
        } catch (e: Exception) {
            Timber.e(e, "Error getting usage stats for user $userId")
            return@withContext null
        }
    }
}

// Supporting data classes
data class UserUsageStats(
    val userId: String,
    val planType: String,
    val currentMonthInputTokens: Long,
    val currentMonthOutputTokens: Long,
    val monthlyInputTokenLimit: Long,
    val monthlyOutputTokenLimit: Long,
    val usagePercentage: Double,
    val daysUntilReset: Int
)