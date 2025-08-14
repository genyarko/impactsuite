package com.example.mygemma3n.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Entity(tableName = "spending_limits")
data class SpendingLimitEntity(
    @PrimaryKey val id: String,
    val limitType: SpendingLimitType,
    val limitAmount: Double, // in USD
    val isEnabled: Boolean = true,
    val warningThreshold: Double = 0.8, // Warn when 80% of limit is reached
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now()
)

enum class SpendingLimitType {
    DAILY,
    WEEKLY, 
    MONTHLY,
    YEARLY
}

data class SpendingStatus(
    val limitType: SpendingLimitType,
    val currentSpending: Double,
    val limitAmount: Double,
    val percentageUsed: Double,
    val remainingAmount: Double,
    val isWarningTriggered: Boolean,
    val isLimitExceeded: Boolean,
    val warningThreshold: Double,
    val isEnabled: Boolean
) {
    val statusMessage: String
        get() = when {
            isLimitExceeded -> "Spending limit exceeded!"
            isWarningTriggered -> "Warning: ${(percentageUsed * 100).toInt()}% of ${limitType.name.lowercase()} limit used"
            else -> "${(percentageUsed * 100).toInt()}% of ${limitType.name.lowercase()} limit used"
        }
        
    val statusColor: SpendingStatusColor
        get() = when {
            isLimitExceeded -> SpendingStatusColor.ERROR
            isWarningTriggered -> SpendingStatusColor.WARNING
            percentageUsed > 0.5 -> SpendingStatusColor.CAUTION
            else -> SpendingStatusColor.GOOD
        }
}

enum class SpendingStatusColor {
    GOOD,    // Green - under 50%
    CAUTION, // Yellow - 50-80%
    WARNING, // Orange - 80%+ but under limit
    ERROR    // Red - limit exceeded
}

data class SpendingLimitRequest(
    val limitType: SpendingLimitType,
    val limitAmount: Double,
    val isEnabled: Boolean = true,
    val warningThreshold: Double = 0.8
)

data class SpendingOverview(
    val totalDailySpending: Double,
    val totalWeeklySpending: Double, 
    val totalMonthlySpending: Double,
    val totalYearlySpending: Double,
    val limits: List<SpendingStatus>,
    val hasActiveWarnings: Boolean,
    val hasExceededLimits: Boolean
) {
    val mostRestrictiveStatus: SpendingStatus?
        get() = limits.filter { it.isEnabled }
            .minByOrNull { it.remainingAmount }
}