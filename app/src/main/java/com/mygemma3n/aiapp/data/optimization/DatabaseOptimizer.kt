package com.mygemma3n.aiapp.data.optimization

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import android.content.Context
import androidx.room.migration.Migration
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DatabaseOptimizer @Inject constructor() {
    
    /**
     * Optimizes database with performance-focused configurations
     */
    @OptIn(DelicateCoroutinesApi::class)
    fun optimizeDatabase(builder: RoomDatabase.Builder<*>): RoomDatabase.Builder<*> {
        return builder.apply {
            // Enable Write-Ahead Logging for better concurrency
            setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            
            // Add callback for performance optimizations
            addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    optimizeDatabaseSettings(db)
                }
                
                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    optimizeDatabaseSettings(db)
                }
            })
            
            // Set query executor for background operations
            setQueryExecutor { runnable ->
                // Run database queries on IO dispatcher
                kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    runnable.run()
                }
            }
            
            // Set transaction executor
            setTransactionExecutor { runnable ->
                kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    runnable.run()
                }
            }
        }
    }
    
    private fun optimizeDatabaseSettings(db: SupportSQLiteDatabase) {
        try {
            // Optimize SQLite settings for performance
            db.execSQL("PRAGMA synchronous = NORMAL") // Balance safety vs performance
            db.execSQL("PRAGMA cache_size = 10000") // Increase cache size (10MB)
            db.execSQL("PRAGMA temp_store = MEMORY") // Store temp data in memory
            db.execSQL("PRAGMA mmap_size = 134217728") // 128MB memory-mapped I/O
            db.execSQL("PRAGMA optimize") // Optimize query planner
            
            Timber.d("Database performance settings applied")
            
            // Create additional performance indices if needed
            createPerformanceIndices(db)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to apply database optimizations")
        }
    }
    
    private fun createPerformanceIndices(db: SupportSQLiteDatabase) {
        val performanceIndices = listOf(
            // Chat performance indices
            "CREATE INDEX IF NOT EXISTS idx_chat_sessions_updated_at ON chat_sessions(updatedAt DESC)",
            "CREATE INDEX IF NOT EXISTS idx_chat_messages_timestamp ON chat_messages(timestamp DESC)",
            "CREATE INDEX IF NOT EXISTS idx_chat_messages_session_timestamp ON chat_messages(sessionId, timestamp DESC)",
            
            // Quiz performance indices  
            "CREATE INDEX IF NOT EXISTS idx_quizzes_subject_topic ON quizzes(subject, topic)",
            "CREATE INDEX IF NOT EXISTS idx_quizzes_created_at ON quizzes(createdAt DESC)",
            "CREATE INDEX IF NOT EXISTS idx_quizzes_completed_at ON quizzes(completedAt DESC)",
            
            // Tutor session performance indices
            "CREATE INDEX IF NOT EXISTS idx_tutor_sessions_recent ON tutor_sessions(studentId, startedAt DESC)",
            "CREATE INDEX IF NOT EXISTS idx_tutor_sessions_by_subject ON tutor_sessions(subject, startedAt DESC)",
            
            // Concept mastery performance indices
            "CREATE INDEX IF NOT EXISTS idx_concept_mastery_recent ON concept_mastery(studentId, lastReviewedAt DESC)",
            "CREATE INDEX IF NOT EXISTS idx_concept_mastery_level ON concept_mastery(masteryLevel DESC)",
            
            // Composite indices for common queries
            "CREATE INDEX IF NOT EXISTS idx_student_subject_performance ON concept_mastery(studentId, subject, masteryLevel DESC)",
            "CREATE INDEX IF NOT EXISTS idx_recent_activity ON tutor_sessions(studentId, endedAt DESC) WHERE endedAt IS NOT NULL"
        )
        
        performanceIndices.forEach { indexSql ->
            try {
                db.execSQL(indexSql)
                Timber.v("Created performance index: ${indexSql.substringAfter("idx_").substringBefore(" ")}")
            } catch (e: Exception) {
                Timber.w(e, "Failed to create index: $indexSql")
            }
        }
    }
    
    /**
     * Database maintenance operations
     */
    suspend fun performMaintenance(db: SupportSQLiteDatabase) {
        try {
            // Analyze tables for query optimization
            db.execSQL("ANALYZE")
            
            // Vacuum database to reclaim space (use sparingly)
            val dbSize = getDatabaseSize(db)
            if (dbSize > 50 * 1024 * 1024) { // 50MB threshold
                db.execSQL("VACUUM")
                Timber.d("Database vacuumed, size was ${dbSize / 1024 / 1024}MB")
            }
            
            // Update table statistics
            db.execSQL("PRAGMA optimize")
            
            Timber.d("Database maintenance completed")
            
        } catch (e: Exception) {
            Timber.e(e, "Database maintenance failed")
        }
    }
    
    private fun getDatabaseSize(db: SupportSQLiteDatabase): Long {
        return try {
            val cursor = db.query("PRAGMA page_count")
            cursor.use {
                if (it.moveToFirst()) {
                    val pageCount = it.getLong(0)
                    val cursor2 = db.query("PRAGMA page_size")
                    cursor2.use { pageSizeCursor ->
                        if (pageSizeCursor.moveToFirst()) {
                            val pageSize = pageSizeCursor.getLong(0)
                            pageCount * pageSize
                        } else 0L
                    }
                } else 0L
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to get database size")
            0L
        }
    }
    
    /**
     * Query performance monitoring
     */
    fun analyzeQueryPerformance(db: SupportSQLiteDatabase, query: String) {
        try {
            val explainQuery = "EXPLAIN QUERY PLAN $query"
            val cursor = db.query(explainQuery)
            
            cursor.use {
                val performanceInfo = mutableListOf<String>()
                while (it.moveToNext()) {
                    val detail = it.getString(3) // detail column
                    performanceInfo.add(detail)
                }
                
                // Log performance analysis
                if (performanceInfo.any { detail -> 
                    detail.contains("SCAN", ignoreCase = true) && 
                    !detail.contains("INDEX", ignoreCase = true) 
                }) {
                    Timber.w("Potential slow query detected (table scan): $query")
                    Timber.w("Query plan: ${performanceInfo.joinToString("; ")}")
                } else {
                    Timber.v("Query uses indices efficiently: $query")
                }
            }
            
        } catch (e: Exception) {
            Timber.w(e, "Failed to analyze query performance: $query")
        }
    }
    
    /**
     * Database statistics and health check
     */
    suspend fun getDatabaseStats(db: SupportSQLiteDatabase): DatabaseStats {
        return try {
            DatabaseStats(
                totalSize = getDatabaseSize(db),
                pageCount = getPageCount(db),
                pageSize = getPageSize(db),
                freelistPages = getFreelistPages(db),
                tableStats = getTableStats(db),
                indexStats = getIndexStats(db)
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to get database stats")
            DatabaseStats()
        }
    }
    
    private fun getPageCount(db: SupportSQLiteDatabase): Long {
        return db.query("PRAGMA page_count").use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) else 0L
        }
    }
    
    private fun getPageSize(db: SupportSQLiteDatabase): Long {
        return db.query("PRAGMA page_size").use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) else 0L
        }
    }
    
    private fun getFreelistPages(db: SupportSQLiteDatabase): Long {
        return db.query("PRAGMA freelist_count").use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) else 0L
        }
    }
    
    private fun getTableStats(db: SupportSQLiteDatabase): Map<String, TableStats> {
        val stats = mutableMapOf<String, TableStats>()
        
        try {
            // Get table names
            db.query("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'").use { cursor ->
                while (cursor.moveToNext()) {
                    val tableName = cursor.getString(0)
                    
                    // Get row count
                    val rowCount = db.query("SELECT COUNT(*) FROM `$tableName`").use { countCursor ->
                        if (countCursor.moveToFirst()) countCursor.getLong(0) else 0L
                    }
                    
                    stats[tableName] = TableStats(
                        name = tableName,
                        rowCount = rowCount
                    )
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to get table stats")
        }
        
        return stats
    }
    
    private fun getIndexStats(db: SupportSQLiteDatabase): Map<String, IndexStats> {
        val stats = mutableMapOf<String, IndexStats>()
        
        try {
            db.query("SELECT name, tbl_name FROM sqlite_master WHERE type='index' AND name NOT LIKE 'sqlite_%'").use { cursor ->
                while (cursor.moveToNext()) {
                    val indexName = cursor.getString(0)
                    val tableName = cursor.getString(1)
                    
                    stats[indexName] = IndexStats(
                        name = indexName,
                        tableName = tableName
                    )
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to get index stats")
        }
        
        return stats
    }
}

data class DatabaseStats(
    val totalSize: Long = 0L,
    val pageCount: Long = 0L,
    val pageSize: Long = 0L,
    val freelistPages: Long = 0L,
    val tableStats: Map<String, TableStats> = emptyMap(),
    val indexStats: Map<String, IndexStats> = emptyMap()
) {
    val fragmentationPercentage: Float
        get() = if (pageCount > 0) (freelistPages.toFloat() / pageCount) * 100f else 0f
        
    val totalSizeMB: Float
        get() = totalSize.toFloat() / (1024 * 1024)
}

data class TableStats(
    val name: String,
    val rowCount: Long
)

data class IndexStats(
    val name: String,
    val tableName: String
)