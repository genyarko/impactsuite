// In a new file: GemmaServiceEntryPoint.kt
package com.mygemma3n.aiapp.di

import com.mygemma3n.aiapp.data.UnifiedGemmaService
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface GemmaServiceEntryPoint {
    fun unifiedGemmaService(): UnifiedGemmaService
}