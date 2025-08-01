package com.example.mygemma3n.data.local

import androidx.room.*
import com.example.mygemma3n.data.local.entities.PricingConfigEntity
import com.example.mygemma3n.data.local.entities.TokenType
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

@Dao
interface PricingConfigDao {
    
    @Query("SELECT * FROM pricing_config WHERE modelName = :modelName AND tokenType = :tokenType AND isActive = 1 LIMIT 1")
    suspend fun getPricingByModelAndType(modelName: String, tokenType: TokenType): PricingConfigEntity?
    
    @Query("SELECT * FROM pricing_config WHERE modelName = :modelName AND isActive = 1")
    suspend fun getPricingByModel(modelName: String): List<PricingConfigEntity>
    
    @Query("SELECT * FROM pricing_config WHERE isActive = 1 ORDER BY modelName, tokenType")
    suspend fun getAllActivePricing(): List<PricingConfigEntity>
    
    @Query("SELECT * FROM pricing_config WHERE isActive = 1 ORDER BY modelName, tokenType")
    fun getAllActivePricingFlow(): Flow<List<PricingConfigEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPricing(pricing: PricingConfigEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPricingList(pricingList: List<PricingConfigEntity>)
    
    @Update
    suspend fun updatePricing(pricing: PricingConfigEntity)
    
    @Query("UPDATE pricing_config SET pricePerMillionTokens = :newPrice, updatedAt = :timestamp WHERE id = :id")
    suspend fun updatePrice(id: String, newPrice: Double, timestamp: LocalDateTime = LocalDateTime.now())
    
    @Query("UPDATE pricing_config SET isActive = 0, updatedAt = :timestamp WHERE id = :id")
    suspend fun deactivatePricing(id: String, timestamp: LocalDateTime = LocalDateTime.now())
    
    @Query("UPDATE pricing_config SET isActive = 0, updatedAt = :timestamp WHERE modelName = :modelName")
    suspend fun deactivatePricingByModel(modelName: String, timestamp: LocalDateTime = LocalDateTime.now())
    
    @Delete
    suspend fun deletePricing(pricing: PricingConfigEntity)
    
    @Query("DELETE FROM pricing_config WHERE modelName = :modelName")
    suspend fun deletePricingByModel(modelName: String)
    
    @Query("DELETE FROM pricing_config WHERE isActive = 0 AND updatedAt < :cutoffDate")
    suspend fun deleteInactivePricingOlderThan(cutoffDate: LocalDateTime)
    
    // Utility queries
    @Query("SELECT DISTINCT modelName FROM pricing_config WHERE isActive = 1 ORDER BY modelName")
    suspend fun getAllActiveModelNames(): List<String>
    
    @Query("SELECT DISTINCT tokenType FROM pricing_config WHERE isActive = 1 ORDER BY tokenType")
    suspend fun getAllActiveTokenTypes(): List<TokenType>
    
    @Query("""
        SELECT 
            modelName,
            COUNT(*) as tokenTypeCount,
            MIN(pricePerMillionTokens) as minPrice,
            MAX(pricePerMillionTokens) as maxPrice,
            AVG(pricePerMillionTokens) as avgPrice
        FROM pricing_config 
        WHERE isActive = 1 
        GROUP BY modelName
        ORDER BY modelName
    """)
    suspend fun getPricingSummaryByModel(): List<ModelPricingSummary>
    
    @Query("""
        SELECT 
            p1.modelName as modelName,
            p1.pricePerMillionTokens as inputPrice,
            p2.pricePerMillionTokens as outputPrice,
            (p2.pricePerMillionTokens / p1.pricePerMillionTokens) as outputInputRatio
        FROM pricing_config p1
        JOIN pricing_config p2 ON p1.modelName = p2.modelName
        WHERE p1.tokenType = 'INPUT' 
        AND p2.tokenType = 'OUTPUT'
        AND p1.isActive = 1 
        AND p2.isActive = 1
        ORDER BY outputInputRatio DESC
    """)
    suspend fun getInputOutputPriceComparison(): List<PriceComparison>
}

data class ModelPricingSummary(
    val modelName: String,
    val tokenTypeCount: Int,
    val minPrice: Double,
    val maxPrice: Double,
    val avgPrice: Double
)

data class PriceComparison(
    val modelName: String,
    val inputPrice: Double,
    val outputPrice: Double,
    val outputInputRatio: Double
)