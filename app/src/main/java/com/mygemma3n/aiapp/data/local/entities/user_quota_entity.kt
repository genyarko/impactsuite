package com.mygemma3n.aiapp.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Entity(tableName = "user_quotas")
data class UserQuotaEntity(
    @PrimaryKey
    val userId: String,
    val planType: String, // "free", "basic", "premium", etc.
    val monthlyInputTokenLimit: Long,
    val monthlyOutputTokenLimit: Long,
    val currentMonthInputTokens: Long = 0,
    val currentMonthOutputTokens: Long = 0,
    val currentPeriodStart: LocalDateTime,
    val isActive: Boolean = true,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now()
) {
    val currentMonthTotalTokens: Long
        get() = currentMonthInputTokens + currentMonthOutputTokens

    val monthlyTotalTokenLimit: Long
        get() = monthlyInputTokenLimit + monthlyOutputTokenLimit

    val inputTokensRemaining: Long
        get() = (monthlyInputTokenLimit - currentMonthInputTokens).coerceAtLeast(0)

    val outputTokensRemaining: Long
        get() = (monthlyOutputTokenLimit - currentMonthOutputTokens).coerceAtLeast(0)

    val totalTokensRemaining: Long
        get() = inputTokensRemaining + outputTokensRemaining

    val isInputLimitReached: Boolean
        get() = currentMonthInputTokens >= monthlyInputTokenLimit

    val isOutputLimitReached: Boolean
        get() = currentMonthOutputTokens >= monthlyOutputTokenLimit

    val isAnyLimitReached: Boolean
        get() = isInputLimitReached || isOutputLimitReached

    fun canUseTokens(inputTokens: Int, outputTokens: Int): Boolean {
        return (currentMonthInputTokens + inputTokens) <= monthlyInputTokenLimit &&
                (currentMonthOutputTokens + outputTokens) <= monthlyOutputTokenLimit
    }

    fun getUsagePercentage(): Double {
        if (monthlyTotalTokenLimit == 0L) return 0.0
        return (currentMonthTotalTokens.toDouble() / monthlyTotalTokenLimit.toDouble()) * 100.0
    }
}

// Predefined plan configurations
enum class SubscriptionPlan(
    val planName: String,
    val monthlyInputTokenLimit: Long,
    val monthlyOutputTokenLimit: Long,
    val displayName: String
) {
    FREE("free", 100_000L, 10_000L, "Free Plan"),
    BASIC("basic", 1_000_000L, 100_000L, "Basic Plan"),
    PREMIUM("premium", 10_000_000L, 1_000_000L, "Premium Plan"),
    UNLIMITED("unlimited", Long.MAX_VALUE, Long.MAX_VALUE, "Unlimited Plan");

    fun toUserQuotaEntity(userId: String): UserQuotaEntity {
        return UserQuotaEntity(
            userId = userId,
            planType = planName,
            monthlyInputTokenLimit = monthlyInputTokenLimit,
            monthlyOutputTokenLimit = monthlyOutputTokenLimit,
            currentPeriodStart = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0)
        )
    }
}

// Data classes for quota management
data class QuotaCheckResult(
    val canProceed: Boolean,
    val reason: String? = null,
    val inputTokensRemaining: Long = 0,
    val outputTokensRemaining: Long = 0,
    val estimatedCost: Double = 0.0
)

data class QuotaUsageUpdate(
    val userId: String,
    val inputTokensUsed: Int,
    val outputTokensUsed: Int,
    val costIncurred: Double
)