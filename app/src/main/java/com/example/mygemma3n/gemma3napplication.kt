package com.example.mygemma3n

import com.google.firebase.BuildConfig

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class Gemma3nApplication(override val workManagerConfiguration: Configuration) : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()

        // Initialize Timber for logging
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        } else {
            // Plant a crash reporting tree in production
            Timber.plant(CrashReportingTree())
        }

        Timber.d("Gemma3n Application started")
    }

    fun getWorkManagerConfiguration(): Configuration {
        return Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) android.util.Log.DEBUG else android.util.Log.ERROR)
            .build()
    }

    private class CrashReportingTree : Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            if (priority == android.util.Log.ERROR || priority == android.util.Log.WARN) {
                // Log to Firebase Crashlytics or your crash reporting service
                // FirebaseCrashlytics.getInstance().log(message)
                // t?.let { FirebaseCrashlytics.getInstance().recordException(it) }
            }
        }
    }
}