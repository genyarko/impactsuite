package com.example.mygemma3n.data.local

import androidx.room.*
import com.example.mygemma3n.data.local.entities.UserQuotaEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

@Dao
interface UserQuotaDao {
    
    @Query("SELECT * FROM user_quotas WHERE userId = :userId AND isActive = 1 LIMIT 1")
    suspend fun getUserQuota(userId: String): UserQuotaEntity?
    
    @Query("SELECT * FROM user_quotas WHERE userId = :userId AND isActive = 1 LIMIT 1")
    fun getUserQuotaFlow(userId: String): Flow<UserQuotaEntity?>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateUserQuota(quota: UserQuotaEntity)
    
    @Update
    suspend fun updateUserQuota(quota: UserQuotaEntity)
    
    @Query("UPDATE user_quotas SET currentMonthInputTokens = currentMonthInputTokens + :inputTokens, currentMonthOutputTokens = currentMonthOutputTokens + :outputTokens, updatedAt = :timestamp WHERE userId = :userId")
    suspend fun incrementTokenUsage(
        userId: String, 
        inputTokens: Int, 
        outputTokens: Int, 
        timestamp: LocalDateTime = LocalDateTime.now()
    )
    
    @Query("SELECT * FROM user_quotas WHERE isActive = 1 ORDER BY createdAt DESC")
    fun getAllActiveQuotas(): Flow<List<UserQuotaEntity>>
    
    @Query("SELECT * FROM user_quotas WHERE planType = :planType AND isActive = 1")
    suspend fun getUsersByPlanType(planType: String): List<UserQuotaEntity>
    
    @Query("SELECT COUNT(*) FROM user_quotas WHERE isActive = 1")
    suspend fun getActiveUserCount(): Int
    
    @Query("SELECT * FROM user_quotas WHERE currentMonthInputTokens >= monthlyInputTokenLimit OR currentMonthOutputTokens >= monthlyOutputTokenLimit")
    suspend fun getUsersOverLimit(): List<UserQuotaEntity>
    
    @Query("UPDATE user_quotas SET currentMonthInputTokens = 0, currentMonthOutputTokens = 0, currentPeriodStart = :newPeriodStart, updatedAt = :timestamp WHERE userId = :userId")
    suspend fun resetMonthlyUsage(
        userId: String, 
        newPeriodStart: LocalDateTime, 
        timestamp: LocalDateTime = LocalDateTime.now()
    )
    
    @Query("UPDATE user_quotas SET isActive = 0, updatedAt = :timestamp WHERE userId = :userId")
    suspend fun deactivateUserQuota(userId: String, timestamp: LocalDateTime = LocalDateTime.now())
    
    @Query("DELETE FROM user_quotas WHERE userId = :userId")
    suspend fun deleteUserQuota(userId: String)
    
    @Query("DELETE FROM user_quotas WHERE isActive = 0 AND updatedAt < :cutoffDate")
    suspend fun deleteInactiveQuotasOlderThan(cutoffDate: LocalDateTime)
    
    // Analytics queries
    @Query("""
        SELECT 
            planType,
            COUNT(*) as userCount,
            SUM(currentMonthInputTokens) as totalInputTokens,
            SUM(currentMonthOutputTokens) as totalOutputTokens,
            AVG(currentMonthInputTokens + currentMonthOutputTokens) as avgTokensPerUser
        FROM user_quotas 
        WHERE isActive = 1 
        GROUP BY planType
    """)
    suspend fun getQuotaAnalyticsByPlan(): List<QuotaAnalytics>
    
    @Query("""
        SELECT AVG(
            CASE 
                WHEN monthlyInputTokenLimit + monthlyOutputTokenLimit > 0 
                THEN ((currentMonthInputTokens + currentMonthOutputTokens) * 100.0) / (monthlyInputTokenLimit + monthlyOutputTokenLimit)
                ELSE 0 
            END
        ) FROM user_quotas WHERE isActive = 1 AND planType = :planType
    """)
    suspend fun getAverageUsagePercentageByPlan(planType: String): Double?
}

data class QuotaAnalytics(
    val planType: String,
    val userCount: Int,
    val totalInputTokens: Long,
    val totalOutputTokens: Long,
    val avgTokensPerUser: Double
)