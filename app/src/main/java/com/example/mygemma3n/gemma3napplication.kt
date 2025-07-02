package com.example.mygemma3n

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class Gemma3nApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // 1. Init Timber for debug logging
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        // 2. Application is ready—.tflite assets will be loaded on demand
        Timber.d("Gemma3nApplication initialized")
    }
}
