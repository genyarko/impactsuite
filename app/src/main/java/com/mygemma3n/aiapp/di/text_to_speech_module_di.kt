package com.mygemma3n.aiapp.di


import android.content.Context
import com.mygemma3n.aiapp.shared_utilities.TextToSpeechManager
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