package com.example.mygemma3n


import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class ImpactSuiteApplication(override val workManagerConfiguration: Configuration) : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()

        // Initialize Timber for logging
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        } else {
            Timber.plant(CrashReportingTree())
        }

        // Initialize AI Edge
        initializeAIEdge()

        // Configure Firebase Crashlytics
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(!BuildConfig.DEBUG)
    }

    private fun initializeAIEdge() {
        // Pre-warm GPU delegate
        System.loadLibrary("tensorflowlite_gpu_jni")

        // Initialize model cache directory
        val modelCacheDir = getDir("model_cache", MODE_PRIVATE)
        modelCacheDir.mkdirs()

        // Copy models from assets on first launch
        if (isFirstLaunch()) {
            copyModelsFromAssets()
        }
    }

    override fun getWorkManagerConfiguration(): Configuration {
        return Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
    }

    private class CrashReportingTree : Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            if (priority >= android.util.Log.WARN) {
                FirebaseCrashlytics.getInstance().log(message)
                t?.let { FirebaseCrashlytics.getInstance().recordException(it) }
            }
        }
    }
}