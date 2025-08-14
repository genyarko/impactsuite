package com.mygemma3n.aiapp.di


import com.mygemma3n.aiapp.data.SpeechRecognitionService
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SpeechRecognitionServiceEntryPoint {
    fun speechRecognitionService(): SpeechRecognitionService
}