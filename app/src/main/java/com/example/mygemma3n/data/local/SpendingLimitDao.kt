package com.example.mygemma3n.data.local

import androidx.room.*
import com.example.mygemma3n.data.local.entities.SpendingLimitEntity
import com.example.mygemma3n.data.local.entities.SpendingLimitType
import kotlinx.coroutines.flow.Flow

@Dao
interface SpendingLimitDao {
    
    @Query("SELECT * FROM spending_limits WHERE isEnabled = 1")
    fun getActiveSpendingLimits(): Flow<List<SpendingLimitEntity>>
    
    @Query("SELECT * FROM spending_limits WHERE isEnabled = 1")
    suspend fun getActiveSpendingLimitsSync(): List<SpendingLimitEntity>
    
    @Query("SELECT * FROM spending_limits WHERE limitType = :limitType LIMIT 1")
    suspend fun getSpendingLimitByType(limitType: SpendingLimitType): SpendingLimitEntity?
    
    @Query("SELECT * FROM spending_limits")
    suspend fun getAllSpendingLimits(): List<SpendingLimitEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSpendingLimit(spendingLimit: SpendingLimitEntity)
    
    @Update
    suspend fun updateSpendingLimit(spendingLimit: SpendingLimitEntity)
    
    @Delete
    suspend fun deleteSpendingLimit(spendingLimit: SpendingLimitEntity)
    
    @Query("DELETE FROM spending_limits WHERE limitType = :limitType")
    suspend fun deleteSpendingLimitByType(limitType: SpendingLimitType)
    
    @Query("UPDATE spending_limits SET isEnabled = :enabled WHERE limitType = :limitType")
    suspend fun setSpendingLimitEnabled(limitType: SpendingLimitType, enabled: Boolean)
    
    @Query("UPDATE spending_limits SET limitAmount = :amount WHERE limitType = :limitType")
    suspend fun updateSpendingLimitAmount(limitType: SpendingLimitType, amount: Double)
    
    @Query("UPDATE spending_limits SET warningThreshold = :threshold WHERE limitType = :limitType")
    suspend fun updateWarningThreshold(limitType: SpendingLimitType, threshold: Double)
}