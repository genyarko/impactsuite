package com.mygemma3n.aiapp.di

import android.content.Context
import com.mygemma3n.aiapp.feature.caption.AudioCapture
import com.mygemma3n.aiapp.feature.caption.TranslationCache
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