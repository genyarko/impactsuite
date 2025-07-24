package com.example.mygemma3n.di


import android.content.Context
import com.example.mygemma3n.shared_utilities.TextToSpeechManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TtsModule {

    @Provides
    @Singleton
    fun provideTextToSpeechManager(
        @ApplicationContext context: Context
    ): TextToSpeechManager = TextToSpeechManager(context)
}