package com.example.mygemma3n.di


import com.example.mygemma3n.feature.caption.SpeechRecognitionService
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SpeechRecognitionServiceEntryPoint {
    fun speechRecognitionService(): SpeechRecognitionService
}