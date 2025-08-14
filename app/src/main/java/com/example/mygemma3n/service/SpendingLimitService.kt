package com.example.mygemma3n.service

import com.example.mygemma3n.data.local.SpendingLimitDao
import com.example.mygemma3n.data.local.entities.*
import com.example.mygemma3n.data.repository.TokenUsageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpendingLimitService @Inject constructor(
    private val spendingLimitDao: SpendingLimitDao,
    private val tokenUsageRepository: TokenUsageRepository
) {
    
    /**
     * Initialize default spending limits if none exist
     */
    suspend fun initializeDefaultLimits() = withContext(Dispatchers.IO) {
        try {
            val existingLimits = spendingLimitDao.getAllSpendingLimits()
            if (existingLimits.isEmpty()) {
                Timber.d("Initializing default spending limits")
                
                // Conservative default limits
                val defaultLimits = listOf(
                    SpendingLimitEntity(
                        id = "daily_limit",
                        limitType = SpendingLimitType.DAILY,
                        limitAmount = 1.00, // $1 per day
                        warningThreshold = 0.8
                    ),
                    SpendingLimitEntity(
                        id = "monthly_limit",
                        limitType = SpendingLimitType.MONTHLY,
                        limitAmount = 10.00, // $10 per month
                        warningThreshold = 0.8
                    )
                )
                
                defaultLimits.forEach { spendingLimitDao.insertSpendingLimit(it) }
                Timber.i("Default spending limits initialized")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error initializing default spending limits")
        }
    }
    
    /**
     * Check if a request would exceed spending limits
     */
    suspend fun wouldExceedSpendingLimits(estimatedCost: Double): SpendingLimitViolation? = withContext(Dispatchers.IO) {
        try {
            val activeLimits = spendingLimitDao.getActiveSpendingLimitsSync()
            val currentTime = LocalDateTime.now()
            
            for (limit in activeLimits) {
                val periodStart = getPeriodStart(currentTime, limit.limitType)
                val currentSpending = tokenUsageRepository.calculateCostsForSummary(periodStart)
                val projectedSpending = currentSpending + estimatedCost
                
                if (projectedSpending > limit.limitAmount) {
                    return@withContext SpendingLimitViolation(
                        limitType = limit.limitType,
                        currentSpending = currentSpending,
                        estimatedCost = estimatedCost,
                        projectedSpending = projectedSpending,
                        limitAmount = limit.limitAmount,
                        excessAmount = projectedSpending - limit.limitAmount,
                        severity = if (currentSpending >= limit.limitAmount) 
                            ViolationSeverity.ALREADY_EXCEEDED 
                        else 
                            ViolationSeverity.WOULD_EXCEED
                    )
                }
            }
            
            return@withContext null
        } catch (e: Exception) {
            Timber.e(e, "Error checking spending limits")
            return@withContext null
        }
    }
    
    /**
     * Get current spending status for all active limits
     */
    suspend fun getCurrentSpendingStatus(): SpendingOverview = withContext(Dispatchers.IO) {
        try {
            val activeLimits = spendingLimitDao.getActiveSpendingLimitsSync()
            val currentTime = LocalDateTime.now()
            
            // Calculate spending for different periods
            val dailySpending = tokenUsageRepository.calculateCostsForSummary(
                getPeriodStart(currentTime, SpendingLimitType.DAILY)
            )
            val weeklySpending = tokenUsageRepository.calculateCostsForSummary(
                getPeriodStart(currentTime, SpendingLimitType.WEEKLY)
            )
            val monthlySpending = tokenUsageRepository.calculateCostsForSummary(
                getPeriodStart(currentTime, SpendingLimitType.MONTHLY)
            )
            val yearlySpending = tokenUsageRepository.calculateCostsForSummary(
                getPeriodStart(currentTime, SpendingLimitType.YEARLY)
            )
            
            val statuses = activeLimits.map { limit ->
                val currentSpending = when (limit.limitType) {
                    SpendingLimitType.DAILY -> dailySpending
                    SpendingLimitType.WEEKLY -> weeklySpending
                    SpendingLimitType.MONTHLY -> monthlySpending
                    SpendingLimitType.YEARLY -> yearlySpending
                }
                
                val percentageUsed = if (limit.limitAmount > 0) {
                    (currentSpending / limit.limitAmount).coerceIn(0.0, 1.0)
                } else 0.0
                
                val remainingAmount = (limit.limitAmount - currentSpending).coerceAtLeast(0.0)
                val isWarningTriggered = percentageUsed >= limit.warningThreshold
                val isLimitExceeded = currentSpending >= limit.limitAmount
                
                SpendingStatus(
                    limitType = limit.limitType,
                    currentSpending = currentSpending,
                    limitAmount = limit.limitAmount,
                    percentageUsed = percentageUsed,
                    remainingAmount = remainingAmount,
                    isWarningTriggered = isWarningTriggered,
                    isLimitExceeded = isLimitExceeded,
                    warningThreshold = limit.warningThreshold,
                    isEnabled = limit.isEnabled
                )
            }
            
            return@withContext SpendingOverview(
                totalDailySpending = dailySpending,
                totalWeeklySpending = weeklySpending,
                totalMonthlySpending = monthlySpending,
                totalYearlySpending = yearlySpending,
                limits = statuses,
                hasActiveWarnings = statuses.any { it.isWarningTriggered },
                hasExceededLimits = statuses.any { it.isLimitExceeded }
            )
            
        } catch (e: Exception) {
            Timber.e(e, "Error calculating spending status")
            return@withContext SpendingOverview(
                totalDailySpending = 0.0,
                totalWeeklySpending = 0.0,
                totalMonthlySpending = 0.0,
                totalYearlySpending = 0.0,
                limits = emptyList(),
                hasActiveWarnings = false,
                hasExceededLimits = false
            )
        }
    }
    
    /**
     * Set or update a spending limit
     */
    suspend fun setSpendingLimit(request: SpendingLimitRequest) = withContext(Dispatchers.IO) {
        try {
            val entity = SpendingLimitEntity(
                id = "${request.limitType.name.lowercase()}_limit",
                limitType = request.limitType,
                limitAmount = request.limitAmount,
                isEnabled = request.isEnabled,
                warningThreshold = request.warningThreshold,
                updatedAt = LocalDateTime.now()
            )
            
            spendingLimitDao.insertSpendingLimit(entity)
            Timber.i("Spending limit set: ${request.limitType} = $${request.limitAmount}")
        } catch (e: Exception) {
            Timber.e(e, "Error setting spending limit")
            throw e
        }
    }
    
    /**
     * Remove a spending limit
     */
    suspend fun removeSpendingLimit(limitType: SpendingLimitType) = withContext(Dispatchers.IO) {
        try {
            spendingLimitDao.deleteSpendingLimitByType(limitType)
            Timber.i("Spending limit removed: $limitType")
        } catch (e: Exception) {
            Timber.e(e, "Error removing spending limit")
            throw e
        }
    }
    
    /**
     * Enable or disable a spending limit
     */
    suspend fun setSpendingLimitEnabled(limitType: SpendingLimitType, enabled: Boolean) = withContext(Dispatchers.IO) {
        try {
            spendingLimitDao.setSpendingLimitEnabled(limitType, enabled)
            Timber.i("Spending limit ${limitType} ${if (enabled) "enabled" else "disabled"}")
        } catch (e: Exception) {
            Timber.e(e, "Error updating spending limit status")
            throw e
        }
    }
    
    private fun getPeriodStart(currentTime: LocalDateTime, limitType: SpendingLimitType): LocalDateTime {
        return when (limitType) {
            SpendingLimitType.DAILY -> currentTime.truncatedTo(ChronoUnit.DAYS)
            SpendingLimitType.WEEKLY -> {
                val daysFromMonday = currentTime.dayOfWeek.value - 1
                currentTime.minusDays(daysFromMonday.toLong()).truncatedTo(ChronoUnit.DAYS)
            }
            SpendingLimitType.MONTHLY -> currentTime.withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS)
            SpendingLimitType.YEARLY -> currentTime.withDayOfYear(1).truncatedTo(ChronoUnit.DAYS)
        }
    }
}

data class SpendingLimitViolation(
    val limitType: SpendingLimitType,
    val currentSpending: Double,
    val estimatedCost: Double,
    val projectedSpending: Double,
    val limitAmount: Double,
    val excessAmount: Double,
    val severity: ViolationSeverity
) {
    val warningMessage: String
        get() = when (severity) {
            ViolationSeverity.ALREADY_EXCEEDED -> 
                "Your ${limitType.name.lowercase()} spending limit of $${String.format("%.2f", limitAmount)} has already been exceeded. Current spending: $${String.format("%.2f", currentSpending)}"
            ViolationSeverity.WOULD_EXCEED ->
                "This request ($${String.format("%.4f", estimatedCost)}) would exceed your ${limitType.name.lowercase()} spending limit of $${String.format("%.2f", limitAmount)}. Current spending: $${String.format("%.2f", currentSpending)}"
        }
}

enum class ViolationSeverity {
    WOULD_EXCEED,
    ALREADY_EXCEEDED
}