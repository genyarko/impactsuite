package com.example.mygemma3n.di

import android.content.Context
import androidx.room.Room
import androidx.work.WorkManager
import com.example.mygemma3n.data.*
import com.example.mygemma3n.data.local.*
import com.example.mygemma3n.data.local.dao.SubjectDao
import com.example.mygemma3n.feature.caption.SpeechRecognitionService
import com.example.mygemma3n.feature.cbt.*
import com.example.mygemma3n.feature.crisis.EmergencyContactsRepository
import com.example.mygemma3n.feature.crisis.OfflineMapService
import com.example.mygemma3n.feature.plant.PlantDatabase
import com.example.mygemma3n.feature.quiz.EducationalContentRepository
import com.example.mygemma3n.feature.quiz.QuizDatabase
import com.example.mygemma3n.feature.quiz.QuizRepository
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

    /* ------------------------------------------------------------------ *
     * Core singletons                                                    *
     * ------------------------------------------------------------------ */
    @Provides @Singleton
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides @Singleton fun provideGson(): Gson = Gson()

    @Provides @Singleton
    fun provideFirebaseAnalytics(): FirebaseAnalytics = Firebase.analytics

    @Provides @Singleton
    fun provideWorkManager(@ApplicationContext ctx: Context): WorkManager =
        WorkManager.getInstance(ctx)

    /* ------------------------------------------------------------------ *
     * Databases                                                          *
     * ------------------------------------------------------------------ */
    @Provides @Singleton
    fun provideAppDatabase(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, "vector_database")
            .fallbackToDestructiveMigration()
            .build()

    @Provides @Singleton fun provideSubjectDao(db: AppDatabase): SubjectDao = db.subjectDao()

    @Provides @Singleton
    fun provideVectorDatabase(db: AppDatabase): VectorDatabase = VectorDatabase(db)

    @Provides @Singleton
    fun provideEmergencyDatabase(@ApplicationContext ctx: Context): EmergencyDatabase =
        EmergencyDatabase.getInstance(ctx)

    @Provides @Singleton
    fun provideCBTDatabase(@ApplicationContext ctx: Context): CBTDatabase =
        Room.databaseBuilder(ctx, CBTDatabase::class.java, "cbt_database")
            .fallbackToDestructiveMigration()
            .build()

    @Provides @Singleton
    fun providePlantDatabase(@ApplicationContext ctx: Context): PlantDatabase =
        Room.databaseBuilder(ctx, PlantDatabase::class.java, "plant_database")
            .fallbackToDestructiveMigration()
            .addCallback(object : androidx.room.RoomDatabase.Callback() {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    super.onCreate(db)
                    // Pre-populate plant data here if desired
                }
            })
            .build()

    @Provides @Singleton
    fun provideQuizDatabase(@ApplicationContext ctx: Context): QuizDatabase =
        Room.databaseBuilder(ctx, QuizDatabase::class.java, "quiz_database")
            .fallbackToDestructiveMigration()
            .build()

    /* ------------------------------------------------------------------ *
     * Repositories & managers                                            *
     * ------------------------------------------------------------------ */
    @Provides @Singleton
    fun provideModelRepository(@ApplicationContext ctx: Context): ModelRepository =
        ModelRepository(ctx)

    @Provides @Singleton
    fun provideModelDownloadManager(
        @ApplicationContext ctx: Context,
        workManager: WorkManager,
        modelRepository: ModelRepository
    ): ModelDownloadManager = ModelDownloadManager(ctx, workManager, modelRepository)

    @Provides @Singleton
    fun provideSessionRepository(
        database: CBTDatabase,
        cbtTechniques: CBTTechniques  // Add this dependency
    ): SessionRepository = SessionRepository(database, cbtTechniques)

    @Provides @Singleton
    fun provideEducationalContentRepository(db: QuizDatabase): EducationalContentRepository =
        EducationalContentRepository(db)

    @Provides @Singleton
    fun provideQuizRepository(db: QuizDatabase, gson: Gson): QuizRepository =
        QuizRepository(db, gson)

    /* ------------------------------------------------------------------ *
     * Gemini / Gemma                                                     *
     * ------------------------------------------------------------------ */
    @Provides @Singleton
    fun provideGeminiApiService(@ApplicationContext ctx: Context): GeminiApiService =
        GeminiApiService(ctx)

    @Provides @Singleton
    fun providePerformanceMonitor(
        @ApplicationContext ctx: Context,
        firebaseAnalytics: FirebaseAnalytics,
        scope: CoroutineScope
    ): PerformanceMonitor = PerformanceMonitor(ctx, firebaseAnalytics, scope)

    @Provides @Singleton
    fun provideGemmaModelManager(
        @ApplicationContext ctx: Context,
        modelRepository: ModelRepository,
        performanceMonitor: PerformanceMonitor,
        geminiApiService: GeminiApiService
    ): GemmaModelManager =
        GemmaModelManager(ctx, modelRepository, performanceMonitor, geminiApiService)

    /*  ── If your code still relies on the on-device Gemma engine, keep this. ── */
//    @Provides @Singleton
//    fun provideGemmaEngine(
//        @ApplicationContext ctx: Context,
//        performanceMonitor: PerformanceMonitor
//    ): GemmaEngine = GemmaEngine(ctx, performanceMonitor)

    /* ------------------------------------------------------------------ *
     * Shared utilities                                                   *
     * ------------------------------------------------------------------ */
    @Provides
    @Singleton
    fun provideMultimodalProcessor(
        modelManager: GemmaModelManager,
        geminiApiService: GeminiApiService      // ← add this
    ): MultimodalProcessor =
        MultimodalProcessor(modelManager, geminiApiService)


    @Provides @Singleton
    fun provideSubjectRepository(dao: SubjectDao): SubjectRepository =
        SubjectRepository(dao)

    @Provides
    @Singleton
    fun provideOfflineRAG(
        vectorDatabase: VectorDatabase,
        subjectRepository: SubjectRepository,
        modelManager: GemmaModelManager,
        geminiApiService: GeminiApiService  // NEW
    ): OfflineRAG = OfflineRAG(vectorDatabase, subjectRepository, modelManager, geminiApiService)


    /* ------------------------------------------------------------------ *
     * Crisis & location                                                  *
     * ------------------------------------------------------------------ */
    @Provides @Singleton
    fun provideLocationService(@ApplicationContext ctx: Context): LocationService =
        LocationService(ctx)

    @Provides @Singleton
    fun provideEmergencyContactsRepository(): EmergencyContactsRepository =
        EmergencyContactsRepository()

    @Provides @Singleton
    fun provideOfflineMapService(): OfflineMapService = OfflineMapService()


    @Provides @Singleton
    fun provideCBTTechniques(): CBTTechniques = CBTTechniques()

    @Provides
    @Singleton
    fun provideCrisisFunctionCalling(
        @ApplicationContext context: Context,
        locationService: LocationService,
        emergencyDatabase: EmergencyDatabase,
        geminiApiService: GeminiApiService // Changed from GemmaEngine
    ): CrisisFunctionCalling {
        return CrisisFunctionCalling(
            context,
            locationService,
            emergencyDatabase,
            geminiApiService
        )
    }

    /* ------------------------------------------------------------------ *
     * Caption                                                         *
     * ------------------------------------------------------------------ */

    @Provides
    @Singleton
    fun provideSpeechRecognitionService(
        @ApplicationContext context: Context
    ): SpeechRecognitionService = SpeechRecognitionService(context)

    /* ------------------------------------------------------------------ *
     * Cbt                          *
     * ------------------------------------------------------------------ */

    @Provides @Singleton
    fun provideEmotionDetector(
        geminiApiService: GeminiApiService  // Add this dependency
    ): EmotionDetector = EmotionDetector(geminiApiService)

    @Provides @Singleton
    fun provideCBTSessionManager(
        sessionRepository: SessionRepository,
        vectorDatabase: VectorDatabase,
        geminiApiService: GeminiApiService,
        cbtTechniques: CBTTechniques
    ): CBTSessionManager = CBTSessionManager(
        sessionRepository,
        vectorDatabase,
        geminiApiService,
        cbtTechniques
    )

}
