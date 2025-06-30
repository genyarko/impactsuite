package com.example.mygemma3n.di

import android.content.Context
import com.example.mygemma3n.feature.caption.AudioCapture
import com.example.mygemma3n.feature.caption.TranslationCache
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CaptionModule {

    @Provides
    @Singleton
    fun provideAudioCapture(
        @ApplicationContext context: Context
    ): AudioCapture = AudioCapture(context)

    @Provides
    @Singleton
    fun provideTranslationCache(): TranslationCache = TranslationCache()
}