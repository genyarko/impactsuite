package com.example.mygemma3n.di


import android.content.Context
import androidx.room.Room
import androidx.work.WorkManager
import com.example.mygemma3n.data.ModelDownloadManager
import com.example.mygemma3n.data.ModelRepository
import com.example.mygemma3n.data.local.*
import com.example.mygemma3n.data.local.dao.SubjectDao
import com.example.mygemma3n.feature.caption.AudioCapture
import com.example.mygemma3n.feature.caption.TranslationCache
import com.example.mygemma3n.feature.cbt.*
import com.example.mygemma3n.feature.crisis.EmergencyContactsRepository
import com.example.mygemma3n.feature.crisis.OfflineMapService
import com.example.mygemma3n.feature.plant.PlantDatabase
import com.example.mygemma3n.feature.quiz.EducationalContentRepository
import com.example.mygemma3n.feature.quiz.QuizDatabase
import com.example.mygemma3n.feature.quiz.QuizRepository
import com.example.mygemma3n.gemma.GemmaEngine
import com.example.mygemma3n.gemma.GemmaModelManager
import com.example.mygemma3n.remote.EmergencyDatabase
import com.example.mygemma3n.shared_utilities.*
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase
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

    @Provides
    @Singleton
    fun provideWorkManager(
        @ApplicationContext context: Context
    ): WorkManager = WorkManager.getInstance(context)

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
        ).fallbackToDestructiveMigration()
            .build()
    }

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
            .fallbackToDestructiveMigration()
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
            .fallbackToDestructiveMigration()
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
            .fallbackToDestructiveMigration()
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
        cbtDatabase: CBTDatabase
    ): SessionRepository = SessionRepository(cbtDatabase)

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

    // Gemma Engine and Model Manager
    @Provides
    @Singleton
    fun provideGemmaEngine(
        @ApplicationContext context: Context,
        performanceMonitor: PerformanceMonitor
    ): GemmaEngine = GemmaEngine(context, performanceMonitor)

    @Provides
    @Singleton
    fun provideGemmaModelManager(
        @ApplicationContext context: Context,
        modelRepository: ModelRepository,
        performanceMonitor: PerformanceMonitor
    ): GemmaModelManager = GemmaModelManager(context, modelRepository, performanceMonitor)

    // Shared Utilities
    @Provides
    @Singleton
    fun providePerformanceMonitor(
        @ApplicationContext context: Context,
        firebaseAnalytics: FirebaseAnalytics,
        scope: CoroutineScope
    ): PerformanceMonitor = PerformanceMonitor(context, firebaseAnalytics, scope)

    @Provides
    @Singleton
    fun provideMultimodalProcessor(
        modelManager: GemmaModelManager
    ): MultimodalProcessor = MultimodalProcessor(modelManager)

    @Provides
    @Singleton
    fun provideSubjectRepository(dao: SubjectDao): SubjectRepository =
        SubjectRepository(dao)




    @Provides
    @Singleton
    fun provideOfflineRAG(
        vectorDatabase: VectorDatabase,
        subjectRepository: SubjectRepository,
        modelManager: GemmaModelManager
    ): OfflineRAG = OfflineRAG(vectorDatabase, subjectRepository, modelManager)


    @Provides
    @Singleton
    fun provideCrisisFunctionCalling(
        @ApplicationContext context: Context,
        locationService: LocationService,
        emergencyDatabase: EmergencyDatabase,
        gemmaEngine: GemmaEngine
    ): CrisisFunctionCalling = CrisisFunctionCalling(
        context,
        locationService,
        emergencyDatabase,
        gemmaEngine
    )

    // Feature-specific services
    @Provides
    @Singleton
    fun provideAudioCapture(): AudioCapture = AudioCapture()

    @Provides
    @Singleton
    fun provideTranslationCache(): TranslationCache = TranslationCache()

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
    fun provideEmotionDetector(): EmotionDetector = EmotionDetector()

    @Provides
    @Singleton
    fun provideCBTTechniques(): CBTTechniques = CBTTechniques()
}