package com.example.mygemma3n.feature.analytics

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import android.content.Context

@Database(
    entities = [
        LearningInteractionEntity::class,
        SubjectProgressEntity::class,
        TopicMasteryEntity::class,
        LearningSessionEntity::class,
        KnowledgeGapEntity::class,
        StudyRecommendationEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(AnalyticsTypeConverters::class)
abstract class AnalyticsDatabase : RoomDatabase() {
    
    abstract fun learningInteractionDao(): LearningInteractionDao
    abstract fun subjectProgressDao(): SubjectProgressDao
    abstract fun topicMasteryDao(): TopicMasteryDao
    abstract fun learningSessionDao(): LearningSessionDao
    abstract fun knowledgeGapDao(): KnowledgeGapDao
    abstract fun studyRecommendationDao(): StudyRecommendationDao
    abstract fun analyticsComputationDao(): AnalyticsComputationDao

    companion object {
        @Volatile
        private var INSTANCE: AnalyticsDatabase? = null

        fun getDatabase(context: Context): AnalyticsDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AnalyticsDatabase::class.java,
                    "learning_analytics_database"
                )
                    .fallbackToDestructiveMigration() // For development
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}