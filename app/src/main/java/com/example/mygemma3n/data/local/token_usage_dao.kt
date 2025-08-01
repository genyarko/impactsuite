package com.example.mygemma3n.data.local

import androidx.room.*
import com.example.mygemma3n.data.local.entities.TokenUsageEntity
import com.example.mygemma3n.data.local.entities.ServiceTokenUsage
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

@Dao
interface TokenUsageDao {
    
    @Insert
    suspend fun insertTokenUsage(tokenUsage: TokenUsageEntity)
    
    @Query("SELECT * FROM token_usage ORDER BY timestamp DESC")
    fun getAllTokenUsage(): Flow<List<TokenUsageEntity>>
    
    @Query("SELECT * FROM token_usage WHERE timestamp >= :fromDate ORDER BY timestamp DESC")
    fun getTokenUsageSince(fromDate: LocalDateTime): Flow<List<TokenUsageEntity>>
    
    @Query("SELECT * FROM token_usage WHERE serviceType = :serviceType ORDER BY timestamp DESC")
    fun getTokenUsageByService(serviceType: String): Flow<List<TokenUsageEntity>>
    
    @Query("""
        SELECT 
            serviceType,
            SUM(inputTokens) as inputTokens,
            SUM(outputTokens) as outputTokens,
            SUM(totalTokens) as totalTokens,
            COUNT(*) as requestCount
        FROM token_usage 
        WHERE timestamp >= :fromDate
        GROUP BY serviceType
    """)
    suspend fun getServiceTokenUsageSummary(fromDate: LocalDateTime): List<ServiceTokenUsage>
    
    @Query("SELECT SUM(inputTokens) FROM token_usage WHERE timestamp >= :fromDate")
    suspend fun getTotalInputTokensSince(fromDate: LocalDateTime): Long?
    
    @Query("SELECT SUM(outputTokens) FROM token_usage WHERE timestamp >= :fromDate")
    suspend fun getTotalOutputTokensSince(fromDate: LocalDateTime): Long?
    
    @Query("SELECT SUM(totalTokens) FROM token_usage WHERE timestamp >= :fromDate")
    suspend fun getTotalTokensSince(fromDate: LocalDateTime): Long?
    
    @Query("DELETE FROM token_usage WHERE timestamp < :beforeDate")
    suspend fun deleteTokenUsageBefore(beforeDate: LocalDateTime)
    
    @Query("DELETE FROM token_usage")
    suspend fun deleteAllTokenUsage()
    
    // Get token usage for today
    @Query("""
        SELECT SUM(inputTokens) as totalInputTokens,
               SUM(outputTokens) as totalOutputTokens,
               SUM(totalTokens) as totalTokens,
               COUNT(*) as requestCount
        FROM token_usage 
        WHERE DATE(timestamp) = DATE(:date)
    """)
    suspend fun getTodayTokenUsage(date: LocalDateTime): TodayTokenUsage?
    
    // Get token usage for current month
    @Query("""
        SELECT SUM(inputTokens) as totalInputTokens,
               SUM(outputTokens) as totalOutputTokens,
               SUM(totalTokens) as totalTokens,
               COUNT(*) as requestCount
        FROM token_usage 
        WHERE strftime('%Y-%m', timestamp) = strftime('%Y-%m', :date)
    """)
    suspend fun getMonthlyTokenUsage(date: LocalDateTime): MonthlyTokenUsage?
}

data class TodayTokenUsage(
    val totalInputTokens: Long,
    val totalOutputTokens: Long,
    val totalTokens: Long,
    val requestCount: Int
)

data class MonthlyTokenUsage(
    val totalInputTokens: Long,
    val totalOutputTokens: Long,
    val totalTokens: Long,
    val requestCount: Int
)