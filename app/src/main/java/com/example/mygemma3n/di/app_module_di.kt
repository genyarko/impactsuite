package com.example.mygemma3n.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.work.WorkManager
import com.example.mygemma3n.data.AppDatabase
import com.example.mygemma3n.data.ChatRepository
import com.example.mygemma3n.data.GeminiApiService
import com.example.mygemma3n.data.ModelDownloadManager
import com.example.mygemma3n.data.ModelRepository
import com.example.mygemma3n.data.TextEmbeddingService
import com.example.mygemma3n.data.TextEmbeddingServiceExtensions
import com.example.mygemma3n.data.TutorDao
import com.example.mygemma3n.data.TutorRepository
import com.example.mygemma3n.data.UnifiedGemmaService
import com.example.mygemma3n.data.VectorDatabase
import com.example.mygemma3n.data.local.*
import com.example.mygemma3n.data.local.dao.SubjectDao
import com.example.mygemma3n.dataStore
import com.example.mygemma3n.feature.analytics.LearningAnalyticsRepository
import com.example.mygemma3n.feature.analytics.LearningInteractionDao
import com.example.mygemma3n.feature.analytics.TopicMasteryDao
import com.example.mygemma3n.feature.cbt.*
import com.example.mygemma3n.feature.crisis.EmergencyContactsRepository
import com.example.mygemma3n.feature.crisis.OfflineMapService
import com.example.mygemma3n.feature.plant.PlantDatabase
import com.example.mygemma3n.feature.progress.LearningProgressTracker
import com.example.mygemma3n.feature.quiz.EducationalContentRepository
import com.example.mygemma3n.feature.quiz.EnhancedPromptManager
import com.example.mygemma3n.feature.quiz.OnlineQuizGenerator
import com.example.mygemma3n.feature.quiz.PerformanceOptimizedQuizGenerator
import com.example.mygemma3n.feature.chat.OnlineChatService
import com.example.mygemma3n.feature.quiz.QuizDatabase
import com.example.mygemma3n.feature.quiz.QuizRepository
import com.example.mygemma3n.feature.tutor.TutorProgressIntegrationService
import com.example.mygemma3n.feature.tutor.TutorPromptManager
import com.example.mygemma3n.remote.EmergencyDatabase
import com.example.mygemma3n.remote.TutorDatabase
import com.example.mygemma3n.shared_utilities.*
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
        ).fallbackToDestructiveMigration(false)
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
        @ApplicationContext context: Context
    ): GeminiApiService = GeminiApiService(context)

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

    @Module
    @InstallIn(SingletonComponent::class)
    object DataStoreModule {

        @Provides
        @Singleton
        fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
            return context.dataStore
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

}