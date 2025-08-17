package com.mygemma3n.aiapp.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.work.WorkManager
import com.mygemma3n.aiapp.data.AppDatabase
import com.mygemma3n.aiapp.data.ChatRepository
import com.mygemma3n.aiapp.data.GeminiApiService
import com.mygemma3n.aiapp.data.ModelDownloadManager
import com.mygemma3n.aiapp.data.ModelRepository
import com.mygemma3n.aiapp.data.TextEmbeddingService
import com.mygemma3n.aiapp.data.TextEmbeddingServiceExtensions
import com.mygemma3n.aiapp.data.TutorDao
import com.mygemma3n.aiapp.data.TutorRepository
import com.mygemma3n.aiapp.data.UnifiedGemmaService
import com.mygemma3n.aiapp.data.VectorDatabase
import com.mygemma3n.aiapp.data.local.*
import com.mygemma3n.aiapp.data.local.dao.SubjectDao
import com.mygemma3n.aiapp.data.repository.TokenUsageRepository
import com.mygemma3n.aiapp.service.QuotaService
import com.mygemma3n.aiapp.service.CostCalculationService
import com.mygemma3n.aiapp.service.SpendingLimitService
import com.mygemma3n.aiapp.service.DatabaseCleanupService
import com.mygemma3n.aiapp.util.CostPredictionUtils
import com.mygemma3n.aiapp.util.ApiKeyValidator
import com.mygemma3n.aiapp.data.local.UserQuotaDao
import com.mygemma3n.aiapp.data.local.PricingConfigDao
import com.mygemma3n.aiapp.dataStore
import com.mygemma3n.aiapp.ui.settings.QuizPreferencesRepository
import com.mygemma3n.aiapp.feature.analytics.LearningAnalyticsRepository
import com.mygemma3n.aiapp.feature.analytics.LearningInteractionDao
import com.mygemma3n.aiapp.feature.analytics.TopicMasteryDao
import com.mygemma3n.aiapp.feature.cbt.*
import com.mygemma3n.aiapp.feature.crisis.EmergencyContactsRepository
import com.mygemma3n.aiapp.feature.crisis.OfflineMapService
import com.mygemma3n.aiapp.feature.plant.PlantDatabase
import com.mygemma3n.aiapp.feature.progress.LearningProgressTracker
import com.mygemma3n.aiapp.feature.quiz.EducationalContentRepository
import com.mygemma3n.aiapp.feature.quiz.EnhancedPromptManager
import com.mygemma3n.aiapp.feature.quiz.OnlineQuizGenerator
import com.mygemma3n.aiapp.feature.quiz.PerformanceOptimizedQuizGenerator
import com.mygemma3n.aiapp.feature.chat.OnlineChatService
import com.mygemma3n.aiapp.feature.chat.OpenAIChatService
import com.mygemma3n.aiapp.domain.repository.SettingsRepository
import com.mygemma3n.aiapp.feature.quiz.QuizDatabase
import com.mygemma3n.aiapp.feature.quiz.QuizRepository
import com.mygemma3n.aiapp.feature.story.StoryRepository
import com.mygemma3n.aiapp.feature.story.OnlineStoryGenerator
import com.mygemma3n.aiapp.feature.story.StoryImageGenerator
import com.mygemma3n.aiapp.feature.story.StoryDao
import com.mygemma3n.aiapp.feature.story.StoryReadingSessionDao
import com.mygemma3n.aiapp.feature.story.ReadingStreakDao
import com.mygemma3n.aiapp.feature.story.ReadingGoalDao
import com.mygemma3n.aiapp.feature.story.AchievementBadgeDao
import com.mygemma3n.aiapp.feature.story.ReadingStreakMemoryManager
import com.mygemma3n.aiapp.feature.story.StoryRecommendationService
import com.mygemma3n.aiapp.feature.story.StoryDifficultyAdapter
import com.mygemma3n.aiapp.feature.story.CharacterRepository
import com.mygemma3n.aiapp.feature.story.CustomCharacterDao
import com.mygemma3n.aiapp.feature.story.StoryTemplateRepository
import com.mygemma3n.aiapp.feature.story.StoryTemplateDao
import com.mygemma3n.aiapp.feature.story.ReadingMoodDetectionService
import com.mygemma3n.aiapp.feature.story.ReadingMoodDao
import com.mygemma3n.aiapp.feature.story.ImageGenerationQueue
import com.mygemma3n.aiapp.feature.tutor.TutorProgressIntegrationService
import com.mygemma3n.aiapp.feature.tutor.TutorPromptManager
import com.mygemma3n.aiapp.remote.EmergencyDatabase
import com.mygemma3n.aiapp.remote.TutorDatabase
import com.mygemma3n.aiapp.shared_utilities.*
import com.google.firebase.Firebase
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.analytics

import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideApplicationScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    @Provides
    @Singleton
    fun provideGson(): Gson = Gson()

    @Provides
    @Singleton
    fun provideFirebaseAnalytics(): FirebaseAnalytics = Firebase.analytics

    // Databases
    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "vector_database"
        ).fallbackToDestructiveMigration() // Allow migration for token tracking feature
            .build()
    }

    @Provides
    @Singleton
    fun provideTutorProgressIntegrationService(
        progressTracker: LearningProgressTracker,
        analyticsRepository: LearningAnalyticsRepository,
        masteryDao: TopicMasteryDao,
        interactionDao: LearningInteractionDao
    ): TutorProgressIntegrationService = TutorProgressIntegrationService(
        progressTracker,
        analyticsRepository,
        masteryDao,
        interactionDao
    )


    @Provides
    @Singleton
    fun provideSubjectDao(db: AppDatabase): SubjectDao = db.subjectDao()

    @Provides
    @Singleton
    fun provideVectorDatabase(
        appDatabase: AppDatabase
    ): VectorDatabase = VectorDatabase(appDatabase)

    @Provides
    @Singleton
    fun provideEmergencyDatabase(
        @ApplicationContext context: Context
    ): EmergencyDatabase = EmergencyDatabase.getInstance(context)

    @Provides
    @Singleton
    fun provideCBTDatabase(
        @ApplicationContext context: Context
    ): CBTDatabase {
        return Room.databaseBuilder(
            context,
            CBTDatabase::class.java,
            "cbt_database"
        )
            .fallbackToDestructiveMigration(false)
            .build()
    }

    @Provides
    @Singleton
    fun providePlantDatabase(
        @ApplicationContext context: Context
    ): PlantDatabase {
        return Room.databaseBuilder(
            context,
            PlantDatabase::class.java,
            "plant_database"
        )
            .fallbackToDestructiveMigration(false)
            .addCallback(object : androidx.room.RoomDatabase.Callback() {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    super.onCreate(db)
                    // Prepopulate plant data
                }
            })
            .build()
    }

    @Provides
    @Singleton
    fun provideQuizDatabase(
        @ApplicationContext context: Context
    ): QuizDatabase {
        return Room.databaseBuilder(
            context,
            QuizDatabase::class.java,
            "quiz_database"
        )
            .fallbackToDestructiveMigration(false)
            .build()
    }

    // Repositories
    @Provides
    @Singleton
    fun provideModelRepository(
        @ApplicationContext context: Context
    ): ModelRepository = ModelRepository(context)

    @Provides
    @Singleton
    fun provideModelDownloadManager(
        @ApplicationContext context: Context,
        workManager: WorkManager,
        modelRepository: ModelRepository
    ): ModelDownloadManager = ModelDownloadManager(context, workManager, modelRepository)

    @Provides
    @Singleton
    fun provideSessionRepository(
        cbtDatabase: CBTDatabase,
        cbtTechniques: CBTTechniques,    // ← add this
    ): SessionRepository = SessionRepository(cbtDatabase, cbtTechniques)

    @Provides
    @Singleton
    fun provideEducationalContentRepository(
        quizDatabase: QuizDatabase
    ): EducationalContentRepository = EducationalContentRepository(quizDatabase)

    @Provides
    @Singleton
    fun provideQuizRepository(
        quizDatabase: QuizDatabase,
        gson: Gson
    ): QuizRepository = QuizRepository(quizDatabase, gson)

    // Gemma Service - UPDATED to use UnifiedGemmaService
    @Provides
    @Singleton
    fun provideUnifiedGemmaService(
        @ApplicationContext context: Context
    ): UnifiedGemmaService = UnifiedGemmaService(context)

    // Shared Utilities
    @Provides
    @Singleton
    fun providePerformanceMonitor(
        @ApplicationContext context: Context,
        firebaseAnalytics: FirebaseAnalytics,
        scope: CoroutineScope
    ): PerformanceMonitor = PerformanceMonitor(context, firebaseAnalytics, scope)

    @Provides @Singleton
    fun provideMultimodalProcessor(
        gemma: UnifiedGemmaService
    ): MultimodalProcessor = MultimodalProcessor(gemma)

    @Provides
    @Singleton
    fun provideSubjectRepository(dao: SubjectDao): SubjectRepository =
        SubjectRepository(dao)

    @Provides
    @Singleton
    fun provideTokenUsageRepository(
        tokenUsageDao: TokenUsageDao,
        costCalculationService: CostCalculationService
    ): TokenUsageRepository = TokenUsageRepository(tokenUsageDao, costCalculationService)

    @Provides
    @Singleton
    fun provideTokenUsageDao(database: AppDatabase): TokenUsageDao =
        database.tokenUsageDao()



    @Provides
    @Singleton
    fun provideOfflineRAG(
        vectorDatabase: OptimizedVectorDatabase,
        subjectRepository: SubjectRepository,
        gemma: UnifiedGemmaService,
        embedderService: TextEmbeddingService,
        embedderServiceExtensions: TextEmbeddingServiceExtensions // ✅ Add this
    ): OfflineRAG = OfflineRAG(
        vectorDatabase,
        subjectRepository,
        gemma,
        embedderService,
        embedderServiceExtensions // ✅ Pass it in
    )

    @Provides
    @Singleton
    fun provideOptimizedVectorDatabase(
        appDatabase: AppDatabase
    ): OptimizedVectorDatabase = OptimizedVectorDatabase(appDatabase)



    @Provides
    @Singleton
    fun provideCrisisFunctionCalling(
        @ApplicationContext context: Context,
        locationService: LocationService,
        emergencyDatabase: EmergencyDatabase,
        unifiedGemmaService: UnifiedGemmaService
    ): CrisisFunctionCalling = CrisisFunctionCalling(
        context,
        locationService,
        emergencyDatabase,
        unifiedGemmaService
    )

    // Feature-specific services
    @Provides
    @Singleton
    fun provideLocationService(
        @ApplicationContext context: Context
    ): LocationService = LocationService(context)

    @Provides
    @Singleton
    fun provideEmergencyContactsRepository(): EmergencyContactsRepository =
        EmergencyContactsRepository()

    @Provides
    @Singleton
    fun provideOfflineMapService(): OfflineMapService = OfflineMapService()

    @Provides
    @Singleton
    fun provideEmotionDetector(
        gemmaService: UnifiedGemmaService
    ): EmotionDetector = EmotionDetector(gemmaService)


    @Provides
    @Singleton
    fun provideCBTTechniques(): CBTTechniques = CBTTechniques()

    @Provides
    @Singleton
    fun provideGeminiApiService(
        @ApplicationContext context: Context,
        tokenUsageRepositoryProvider: javax.inject.Provider<TokenUsageRepository>
    ): GeminiApiService = GeminiApiService(context, tokenUsageRepositoryProvider)

    @Provides
    @Singleton
    fun provideTextEmbeddingServiceExtensions(
        textEmbeddingService: TextEmbeddingService
    ): TextEmbeddingServiceExtensions {
        return TextEmbeddingServiceExtensions(textEmbeddingService)
    }


    @Provides
    @Singleton
    fun providePerformanceOptimizedQuizGenerator(
        gemmaService: UnifiedGemmaService,
        performanceMonitor: PerformanceMonitor,
        promptManager: EnhancedPromptManager
    ): PerformanceOptimizedQuizGenerator = PerformanceOptimizedQuizGenerator(
        gemmaService,
        performanceMonitor,
        promptManager
    )

    @Provides
    @Singleton
    fun provideOnlineQuizGenerator(
        geminiApiService: GeminiApiService,
        gson: Gson
    ): OnlineQuizGenerator = OnlineQuizGenerator(
        geminiApiService,
        gson
    )

    @Provides
    @Singleton
    fun provideOnlineChatService(
        geminiApiService: GeminiApiService
    ): OnlineChatService = OnlineChatService(
        geminiApiService
    )

    // Story Feature
    @Provides
    @Singleton
    fun provideStoryDao(db: AppDatabase): StoryDao = db.storyDao()

    @Provides
    @Singleton
    fun provideStoryReadingSessionDao(db: AppDatabase): StoryReadingSessionDao = db.storyReadingSessionDao()

    @Provides
    @Singleton
    fun provideReadingStreakDao(db: AppDatabase): ReadingStreakDao = db.readingStreakDao()

    @Provides
    @Singleton
    fun provideReadingGoalDao(db: AppDatabase): ReadingGoalDao = db.readingGoalDao()

    @Provides
    @Singleton
    fun provideAchievementBadgeDao(db: AppDatabase): AchievementBadgeDao = db.achievementBadgeDao()
    
    @Provides
    @Singleton
    fun provideCustomCharacterDao(db: AppDatabase): CustomCharacterDao = db.customCharacterDao()
    
    @Provides
    @Singleton
    fun provideStoryTemplateDao(db: AppDatabase): StoryTemplateDao = db.storyTemplateDao()
    
    @Provides
    @Singleton
    fun provideReadingMoodDao(db: AppDatabase): ReadingMoodDao = db.readingMoodDao()

    @Provides
    @Singleton
    fun provideStoryRepository(
        storyDao: StoryDao,
        sessionDao: StoryReadingSessionDao,
        streakDao: ReadingStreakDao,
        goalDao: ReadingGoalDao,
        badgeDao: AchievementBadgeDao,
        memoryManager: ReadingStreakMemoryManager,
        gson: Gson
    ): StoryRepository = StoryRepository(storyDao, sessionDao, streakDao, goalDao, badgeDao, memoryManager, gson)

    @Provides
    @Singleton
    fun provideStoryImageGenerator(
        geminiApiService: GeminiApiService,
        openAIService: OpenAIChatService,
        settingsRepository: SettingsRepository,
        @ApplicationContext context: Context
    ): StoryImageGenerator = StoryImageGenerator(geminiApiService, openAIService, settingsRepository, context)

    @Provides
    @Singleton
    fun provideOnlineStoryGenerator(
        geminiApiService: GeminiApiService,
        openAIService: OpenAIChatService,
        settingsRepository: SettingsRepository,
        gson: Gson,
        storyImageGenerator: StoryImageGenerator,
        difficultyAdapter: StoryDifficultyAdapter
    ): OnlineStoryGenerator = OnlineStoryGenerator(geminiApiService, openAIService, settingsRepository, gson, storyImageGenerator, difficultyAdapter)

    @Provides
    @Singleton
    fun provideImageGenerationQueue(
        onlineStoryGenerator: OnlineStoryGenerator,
        storyRepository: StoryRepository
    ): ImageGenerationQueue = ImageGenerationQueue(onlineStoryGenerator, storyRepository)

    @Provides
    @Singleton
    fun provideStoryRecommendationService(
        storyRepository: StoryRepository,
        gemmaService: UnifiedGemmaService
    ): StoryRecommendationService = StoryRecommendationService(storyRepository, gemmaService)

    @Provides
    @Singleton
    fun provideStoryDifficultyAdapter(
        gemmaService: UnifiedGemmaService
    ): StoryDifficultyAdapter = StoryDifficultyAdapter(gemmaService)

    @Module
    @InstallIn(SingletonComponent::class)
    object DataStoreModule {

        @Provides
        @Singleton
        fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
            return context.dataStore
        }

        @Provides
        @Singleton
        fun provideQuizPreferencesRepository(dataStore: DataStore<Preferences>): QuizPreferencesRepository {
            return QuizPreferencesRepository(dataStore)
        }
    }

    @Provides
    @Singleton
    fun provideChatDao(db: AppDatabase): ChatDao = db.chatDao()


    // Tutor Feature
    // In AppModule.kt, add:

    @Provides
    @Singleton
    fun provideTutorDatabase(
        @ApplicationContext context: Context
    ): TutorDatabase {
        return Room.databaseBuilder(
            context,
            TutorDatabase::class.java,
            "tutor_database"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    @Singleton
    fun provideTutorDao(database: TutorDatabase): TutorDao = database.tutorDao()

    @Provides
    @Singleton
    fun provideTutorRepository(
        tutorDao: TutorDao,
        chatRepository: ChatRepository,
        gson: Gson
    ): TutorRepository = TutorRepository(tutorDao, chatRepository, gson)

    @Provides
    @Singleton
    fun provideTutorPromptManager(
        @ApplicationContext context: Context
    ): TutorPromptManager = TutorPromptManager(context)

    @Provides
    @Singleton
    fun provideTutorOfflineRAGExtension(
        offlineRAG: OfflineRAG,
        vectorDB: OptimizedVectorDatabase,
        embeddingService: TextEmbeddingService,
        @ApplicationContext context: Context
    ): TutorOfflineRAGExtension = TutorOfflineRAGExtension(
        offlineRAG,
        vectorDB,
        embeddingService,
        context
    )

    // New billing-related DAOs
    @Provides
    @Singleton
    fun provideUserQuotaDao(database: AppDatabase): UserQuotaDao =
        database.userQuotaDao()

    @Provides
    @Singleton
    fun providePricingConfigDao(database: AppDatabase): PricingConfigDao =
        database.pricingConfigDao()

    // New billing-related services
    @Provides
    @Singleton
    fun provideCostCalculationService(
        pricingConfigDao: PricingConfigDao
    ): CostCalculationService = CostCalculationService(pricingConfigDao)

    @Provides
    @Singleton
    fun provideSpendingLimitDao(database: AppDatabase): SpendingLimitDao =
        database.spendingLimitDao()

    @Provides
    @Singleton
    fun provideSpendingLimitService(
        spendingLimitDao: SpendingLimitDao,
        tokenUsageRepository: TokenUsageRepository
    ): SpendingLimitService = SpendingLimitService(spendingLimitDao, tokenUsageRepository)

    @Provides
    @Singleton
    fun provideCostPredictionUtils(
        costCalculationService: CostCalculationService
    ): CostPredictionUtils = CostPredictionUtils(costCalculationService)

    @Provides
    @Singleton
    fun provideDatabaseCleanupService(
        tokenUsageRepository: TokenUsageRepository,
        @ApplicationContext context: Context
    ): DatabaseCleanupService = DatabaseCleanupService(tokenUsageRepository, context)

    @Provides
    @Singleton
    fun provideApiKeyValidator(): ApiKeyValidator = ApiKeyValidator()

    @Provides
    @Singleton
    fun provideQuotaService(
        userQuotaDao: UserQuotaDao,
        pricingConfigDao: PricingConfigDao,
        costCalculationService: CostCalculationService,
        tokenUsageRepository: TokenUsageRepository
    ): QuotaService = QuotaService(
        userQuotaDao,
        pricingConfigDao,
        costCalculationService,
        tokenUsageRepository
    )

    @Provides
    @Singleton
    fun provideCharacterRepository(
        characterDao: CustomCharacterDao,
        gson: Gson
    ): CharacterRepository = CharacterRepository(characterDao, gson)

    @Provides
    @Singleton
    fun provideStoryTemplateRepository(
        templateDao: StoryTemplateDao,
        gson: Gson
    ): StoryTemplateRepository = StoryTemplateRepository(templateDao, gson)

    @Provides
    @Singleton
    fun provideReadingMoodDetectionService(
        moodDao: ReadingMoodDao,
        storyRepository: StoryRepository
    ): ReadingMoodDetectionService = ReadingMoodDetectionService(moodDao, storyRepository)

}