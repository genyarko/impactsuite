// In a new file: GemmaServiceEntryPoint.kt
package com.example.mygemma3n.di

import com.example.mygemma3n.data.UnifiedGemmaService
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface GemmaServiceEntryPoint {
    fun unifiedGemmaService(): UnifiedGemmaService
}