package com.example.mygemma3n.data.repository

import com.example.mygemma3n.data.local.TokenUsageDao
import com.example.mygemma3n.data.local.entities.TokenUsageEntity
import com.example.mygemma3n.data.local.entities.TokenUsageSummary
import com.example.mygemma3n.data.local.entities.ServiceTokenUsage
import com.example.mygemma3n.service.CostCalculationService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenUsageRepository @Inject constructor(
    private val tokenUsageDao: TokenUsageDao,
    private val costCalculationService: CostCalculationService
) {
    
    suspend fun recordTokenUsage(
        serviceType: String,
        modelName: String,
        inputTokens: Int,
        outputTokens: Int,
        userId: String? = null
    ) {
        val tokenUsage = TokenUsageEntity(
            serviceType = serviceType,
            modelName = modelName,
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            totalTokens = inputTokens + outputTokens,
            timestamp = LocalDateTime.now(),
            userId = userId
        )
        tokenUsageDao.insertTokenUsage(tokenUsage)
    }
    
    fun getAllTokenUsage(): Flow<List<TokenUsageEntity>> {
        return tokenUsageDao.getAllTokenUsage()
    }
    
    fun getTokenUsageSince(fromDate: LocalDateTime): Flow<List<TokenUsageEntity>> {
        return tokenUsageDao.getTokenUsageSince(fromDate)
    }
    
    fun getTokenUsageByService(serviceType: String): Flow<List<TokenUsageEntity>> {
        return tokenUsageDao.getTokenUsageByService(serviceType)
    }
    
    suspend fun getTokenUsageSummary(fromDate: LocalDateTime): TokenUsageSummary {
        val serviceBreakdown = tokenUsageDao.getServiceTokenUsageSummary(fromDate)
            .associateBy { it.serviceType }
        
        val totalInputTokens = tokenUsageDao.getTotalInputTokensSince(fromDate) ?: 0L
        val totalOutputTokens = tokenUsageDao.getTotalOutputTokensSince(fromDate) ?: 0L
        val totalTokens = totalInputTokens + totalOutputTokens
        
        // Return summary WITHOUT cost calculation for faster loading
        return TokenUsageSummary(
            totalInputTokens = totalInputTokens,
            totalOutputTokens = totalOutputTokens,
            totalTokens = totalTokens,
            totalCost = 0.0, // Will be calculated on demand
            serviceBreakdown = serviceBreakdown
        )
    }
    
    suspend fun calculateCostsForSummary(fromDate: LocalDateTime): Double {
        var totalCost = 0.0
        try {
            val allUsage = tokenUsageDao.getTokenUsageSinceSync(fromDate)
            for (usage in allUsage) {
                val cost = costCalculationService.calculateCost(
                    modelName = usage.modelName,
                    inputTokens = usage.inputTokens,
                    outputTokens = usage.outputTokens
                )
                totalCost += cost
            }
        } catch (e: Exception) {
            // Log error but don't fail the calculation
            totalCost = 0.0
        }
        return totalCost
    }
    
    suspend fun getTodayTokenUsage(): TokenUsageSummary {
        return getTokenUsageSummary(LocalDateTime.now().toLocalDate().atStartOfDay())
    }
    
    suspend fun getMonthlyTokenUsage(): TokenUsageSummary {
        val firstDayOfMonth = LocalDateTime.now().toLocalDate().withDayOfMonth(1).atStartOfDay()
        return getTokenUsageSummary(firstDayOfMonth)
    }
    
    suspend fun getWeeklyTokenUsage(): TokenUsageSummary {
        val weekAgo = LocalDateTime.now().minusDays(7)
        return getTokenUsageSummary(weekAgo)
    }
    
    suspend fun clearOldTokenUsage(daysToKeep: Int = 90) {
        val cutoffDate = LocalDateTime.now().minusDays(daysToKeep.toLong())
        tokenUsageDao.deleteTokenUsageBefore(cutoffDate)
    }
    
    suspend fun clearAllTokenUsage() {
        tokenUsageDao.deleteAllTokenUsage()
    }
}