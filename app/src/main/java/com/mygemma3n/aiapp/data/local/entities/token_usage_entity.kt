package com.mygemma3n.aiapp.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Entity(tableName = "token_usage")
data class TokenUsageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val serviceType: String, // "chat", "quiz", "tutor", "cbt", etc.
    val modelName: String, // "gemini-2.5-flash", "gemini-2.5-pro", etc.
    val inputTokens: Int,
    val outputTokens: Int,
    val totalTokens: Int,
    val timestamp: LocalDateTime,
    val userId: String? = null // For future user-specific tracking
)

// Data class for token usage analytics
data class TokenUsageSummary(
    val totalInputTokens: Long,
    val totalOutputTokens: Long,
    val totalTokens: Long,
    val totalCost: Double = 0.0, // Future: calculated based on model pricing
    val serviceBreakdown: Map<String, ServiceTokenUsage>
)

data class ServiceTokenUsage(
    val serviceType: String,
    val inputTokens: Long,
    val outputTokens: Long,
    val totalTokens: Long,
    val requestCount: Int
)

// Response model that includes token usage information
data class ApiResponse<T>(
    val data: T,
    val tokenUsage: TokenUsage
)

data class TokenUsage(
    val inputTokens: Int,
    val outputTokens: Int,
    val totalTokens: Int = inputTokens + outputTokens
)