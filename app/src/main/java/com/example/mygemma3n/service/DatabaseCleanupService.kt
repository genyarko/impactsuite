package com.example.mygemma3n.service

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.work.*
import com.example.mygemma3n.data.repository.TokenUsageRepository
import com.example.mygemma3n.dataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DatabaseCleanupService @Inject constructor(
    private val tokenUsageRepository: TokenUsageRepository,
    private val context: Context
) {
    
    companion object {
        private const val CLEANUP_WORK_NAME = "database_cleanup_work"
        private const val DEFAULT_RETENTION_DAYS = 365 // Keep 1 year for billing purposes
        private val LAST_CLEANUP_KEY = longPreferencesKey("last_cleanup_timestamp")
        private const val CLEANUP_INTERVAL_HOURS = 24L // Run daily
        private const val CLEANUP_ENABLED = false // Disabled until online database migration
    }
    
    /**
     * Schedule periodic database cleanup
     * Note: Currently disabled to preserve billing data until online migration
     */
    fun schedulePeriodicCleanup() {
        if (!CLEANUP_ENABLED) {
            Timber.i("Database cleanup disabled - preserving token usage data for billing")
            return
        }
        
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .setRequiresBatteryNotLow(true)
            .build()
        
        val cleanupRequest = PeriodicWorkRequestBuilder<DatabaseCleanupWorker>(
            CLEANUP_INTERVAL_HOURS, TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .setInitialDelay(1, TimeUnit.HOURS) // Start 1 hour after app launch
            .build()
        
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                CLEANUP_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP, // Don't restart if already scheduled
                cleanupRequest
            )
        
        Timber.i("Database cleanup scheduled to run every $CLEANUP_INTERVAL_HOURS hours")
    }
    
    /**
     * Perform manual cleanup
     * Note: Currently disabled to preserve billing data until online migration
     */
    suspend fun performCleanup(
        retentionDays: Int = DEFAULT_RETENTION_DAYS,
        forceCleanup: Boolean = false
    ): CleanupResult = withContext(Dispatchers.IO) {
        if (!CLEANUP_ENABLED && !forceCleanup) {
            return@withContext CleanupResult(
                success = true,
                recordsDeleted = 0,
                skipped = true,
                message = "Cleanup disabled - preserving billing data until online database migration"
            )
        }
        
        return@withContext try {
            val lastCleanup = getLastCleanupTimestamp()
            val now = System.currentTimeMillis()
            val timeSinceLastCleanup = now - lastCleanup
            val hoursSinceLastCleanup = timeSinceLastCleanup / (1000 * 60 * 60)
            
            // Skip if cleaned up recently (unless forced)
            if (!forceCleanup && hoursSinceLastCleanup < 12) {
                Timber.d("Skipping cleanup - last cleanup was $hoursSinceLastCleanup hours ago")
                return@withContext CleanupResult(
                    success = true,
                    recordsDeleted = 0,
                    skipped = true,
                    message = "Cleanup skipped - last run was ${hoursSinceLastCleanup}h ago"
                )
            }
            
            Timber.i("Starting database cleanup - keeping last $retentionDays days of records")
            
            // Count records before cleanup
            val cutoffDate = LocalDateTime.now().minusDays(retentionDays.toLong())
            val oldRecordsCount = tokenUsageRepository.getTokenUsageSince(cutoffDate).first().size
            
            // Perform cleanup
            tokenUsageRepository.clearOldTokenUsage(retentionDays)
            
            // Update last cleanup timestamp
            setLastCleanupTimestamp(now)
            
            val message = "Cleanup completed - removed records older than $retentionDays days"
            Timber.i(message)
            
            CleanupResult(
                success = true,
                recordsDeleted = oldRecordsCount,
                skipped = false,
                message = message
            )
            
        } catch (e: Exception) {
            val errorMessage = "Database cleanup failed: ${e.message}"
            Timber.e(e, errorMessage)
            CleanupResult(
                success = false,
                recordsDeleted = 0,
                skipped = false,
                message = errorMessage
            )
        }
    }
    
    /**
     * Cancel scheduled cleanup
     */
    fun cancelScheduledCleanup() {
        WorkManager.getInstance(context).cancelUniqueWork(CLEANUP_WORK_NAME)
        Timber.i("Database cleanup cancelled")
    }
    
    /**
     * Get database size statistics
     */
    suspend fun getDatabaseStats(): DatabaseStats = withContext(Dispatchers.IO) {
        return@withContext try {
            val now = LocalDateTime.now()
            val totalRecords = tokenUsageRepository.getAllTokenUsage().first().size
            val last30Days = tokenUsageRepository.getTokenUsageSince(now.minusDays(30)).first().size
            val last90Days = tokenUsageRepository.getTokenUsageSince(now.minusDays(90)).first().size
            val lastYear = tokenUsageRepository.getTokenUsageSince(now.minusDays(365)).first().size
            
            DatabaseStats(
                totalRecords = totalRecords,
                recordsLast30Days = last30Days,
                recordsLast90Days = last90Days,
                recordsLastYear = lastYear,
                oldRecords = totalRecords - last90Days,
                lastCleanup = getLastCleanupTimestamp()
            )
        } catch (e: Exception) {
            Timber.e(e, "Error getting database stats")
            DatabaseStats()
        }
    }
    
    private suspend fun getLastCleanupTimestamp(): Long {
        return context.dataStore.data
            .map { it[LAST_CLEANUP_KEY] ?: 0L }
            .first()
    }
    
    private suspend fun setLastCleanupTimestamp(timestamp: Long) {
        context.dataStore.edit { preferences ->
            preferences[LAST_CLEANUP_KEY] = timestamp
        }
    }
}

/**
 * WorkManager worker for database cleanup
 */
class DatabaseCleanupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    override suspend fun doWork(): Result {
        return try {
            // Use dependency injection to get the service
            // Note: In a real app, you'd use proper DI here
            Timber.i("Background database cleanup started")
            
            // For now, just log that cleanup would run
            // In a full implementation, you'd inject the DatabaseCleanupService
            Timber.i("Background database cleanup completed")
            
            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "Background database cleanup failed")
            Result.retry()
        }
    }
}

data class CleanupResult(
    val success: Boolean,
    val recordsDeleted: Int,
    val skipped: Boolean,
    val message: String
)

data class DatabaseStats(
    val totalRecords: Int = 0,
    val recordsLast30Days: Int = 0,
    val recordsLast90Days: Int = 0,
    val recordsLastYear: Int = 0,
    val oldRecords: Int = 0,
    val lastCleanup: Long = 0L
) {
    val cleanupRecommended: Boolean
        get() = oldRecords > 1000 || totalRecords > 10000
    
    val lastCleanupDaysAgo: Long
        get() = if (lastCleanup > 0) {
            (System.currentTimeMillis() - lastCleanup) / (1000 * 60 * 60 * 24)
        } else -1
}