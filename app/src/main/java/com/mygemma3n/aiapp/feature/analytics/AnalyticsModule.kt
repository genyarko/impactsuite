package com.mygemma3n.aiapp.feature.analytics

import androidx.room.Room
import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AnalyticsModule {

    @Provides
    @Singleton
    fun provideLearningInteractionDao(
        database: AnalyticsDatabase
    ): LearningInteractionDao = database.learningInteractionDao()

    @Provides
    @Singleton
    fun provideSubjectProgressDao(
        database: AnalyticsDatabase
    ): SubjectProgressDao = database.subjectProgressDao()

    @Provides
    @Singleton
    fun provideTopicMasteryDao(
        database: AnalyticsDatabase
    ): TopicMasteryDao = database.topicMasteryDao()

    @Provides
    @Singleton
    fun provideLearningSessionDao(
        database: AnalyticsDatabase
    ): LearningSessionDao = database.learningSessionDao()

    @Provides
    @Singleton
    fun provideKnowledgeGapDao(
        database: AnalyticsDatabase
    ): KnowledgeGapDao = database.knowledgeGapDao()

    @Provides
    @Singleton
    fun provideStudyRecommendationDao(
        database: AnalyticsDatabase
    ): StudyRecommendationDao = database.studyRecommendationDao()

    @Provides
    @Singleton
    fun provideAnalyticsComputationDao(
        database: AnalyticsDatabase
    ): AnalyticsComputationDao = database.analyticsComputationDao()

    @Provides
    @Singleton
    fun provideAnalyticsDatabase(
        @ApplicationContext context: Context
    ): AnalyticsDatabase {
        return Room.databaseBuilder(
            context,
            AnalyticsDatabase::class.java,
            "learning_analytics_database"
        )
            .fallbackToDestructiveMigration() // For development - remove in production
            .build()
    }

    @Provides
    @Singleton
    fun provideKnowledgeGapAnalyzer(
        interactionDao: LearningInteractionDao,
        masteryDao: TopicMasteryDao,
        progressDao: SubjectProgressDao,
        gapDao: KnowledgeGapDao
    ): KnowledgeGapAnalyzer {
        return KnowledgeGapAnalyzer(interactionDao, masteryDao, progressDao, gapDao)
    }
}