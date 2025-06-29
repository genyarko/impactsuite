package com.example.mygemma3n

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.google.firebase.crashlytics.BuildConfig
import com.google.firebase.crashlytics.FirebaseCrashlytics
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class ImpactSuiteApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    // -- WorkManager configuration ------------------------------------------
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(
                if (BuildConfig.DEBUG) Log.DEBUG else Log.INFO
            )
            .build()
    // -----------------------------------------------------------------------

    override fun onCreate() {
        super.onCreate()

        // Timber setup
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        } else {
            Timber.plant(CrashReportingTree())
        }

        // AI-Edge cache setup
        initializeAIEdge()

        // Crashlytics
        FirebaseCrashlytics.getInstance()
            .setCrashlyticsCollectionEnabled(!BuildConfig.DEBUG)

        Timber.d("ImpactSuite Application initialized")
    }

    private fun initializeAIEdge() {
        try {
            val modelCacheDir = getDir("model_cache", MODE_PRIVATE).apply { mkdirs() }
            listOf("gemma", "mobilenet", "audio", "temp").forEach { name ->
                java.io.File(modelCacheDir, name).mkdirs()
            }

            val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
            if (prefs.getBoolean("is_first_launch", true)) {
                prefs.edit().putBoolean("is_first_launch", false).apply()
                Timber.d("First launch detected – models will download on demand.")
            }

            Timber.d("AI Edge cache directories initialized at: ${modelCacheDir.absolutePath}")
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize AI Edge")
        }
    }

    private class CrashReportingTree : Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            if (priority >= Log.WARN) {
                FirebaseCrashlytics.getInstance().log(message)
                t?.let { FirebaseCrashlytics.getInstance().recordException(it) }
            }
        }
    }
}
