package com.example.mygemma3n.feature.quiz

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.mygemma3n.data.GeminiApiService
import com.example.mygemma3n.domain.repository.SettingsRepository
import com.example.mygemma3n.feature.chat.OpenAIChatService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages network connectivity and online service selection for quiz generation.
 * Handles switching between Gemini API, OpenAI, and offline generation based on 
 * network availability and user preferences.
 */
@Singleton
class QuizNetworkManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepo: SettingsRepository,
    private val geminiApiService: GeminiApiService,
    private val openAIChatService: OpenAIChatService
) {
    
    /**
     * Check if device has active network connection
     */
    fun hasNetworkConnection(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
               capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
    }
    
    /**
     * Determine if online services should be used for quiz generation
     */
    suspend fun shouldUseOnlineService(): Boolean {
        if (!hasNetworkConnection()) {
            Timber.d("No network connection - using offline generation")
            return false
        }
        
        // Check if any online API is configured and available
        val hasGeminiKey = hasValidGeminiApiKey()
        val hasOpenAIKey = hasValidOpenAIApiKey()
        
        return hasGeminiKey || hasOpenAIKey
    }
    
    /**
     * Check if Gemini API key is configured and valid
     */
    private suspend fun hasValidGeminiApiKey(): Boolean {
        return try {
            geminiApiService.isInitialized()
        } catch (e: Exception) {
            Timber.w("Gemini API not available: ${e.message}")
            false
        }
    }
    
    /**
     * Check if OpenAI API key is configured and valid
     */
    private suspend fun hasValidOpenAIApiKey(): Boolean {
        return try {
            openAIChatService.isInitialized()
        } catch (e: Exception) {
            Timber.w("OpenAI API not available: ${e.message}")
            false
        }
    }
    
    /**
     * Determine if OpenAI should be used instead of Gemini
     */
    suspend fun shouldUseOpenAI(): Boolean {
        val modelProvider = settingsRepo.modelProviderFlow.first()
        return modelProvider == "openai" && hasValidOpenAIApiKey()
    }
    
    /**
     * Initialize and warm up the appropriate API service
     */
    suspend fun initializeApiServiceIfNeeded() = withContext(Dispatchers.IO) {
        try {
            if (shouldUseOnlineService()) {
                if (shouldUseOpenAI()) {
                    Timber.d("Initializing OpenAI service")
                    warmUpOpenAIService()
                } else if (hasValidGeminiApiKey()) {
                    Timber.d("Initializing Gemini service")
                    warmUpGeminiService()
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to initialize API service")
        }
    }
    
    /**
     * Warm up OpenAI service with a minimal request
     */
    private suspend fun warmUpOpenAIService() {
        try {
            if (openAIChatService.isInitialized()) {
                // Simple warmup request
                val warmupPrompt = "Hi"
                openAIChatService.generateConversationalResponse(
                    userMessage = warmupPrompt,
                    personalityType = "helpful"
                )
                Timber.d("OpenAI service warmed up successfully")
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to warm up OpenAI service")
        }
    }
    
    /**
     * Warm up Gemini service with a minimal request
     */
    private suspend fun warmUpGeminiService() {
        try {
            if (geminiApiService.isInitialized()) {
                // Simple warmup request
                val warmupPrompt = "Hi"
                geminiApiService.generateTextComplete(warmupPrompt, "chat")
                Timber.d("Gemini service warmed up successfully")
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to warm up Gemini service")
        }
    }
    
    /**
     * Get the current online service status for UI display
     */
    suspend fun getOnlineServiceStatus(): OnlineServiceStatus {
        if (!hasNetworkConnection()) {
            return OnlineServiceStatus.NO_NETWORK
        }
        
        val hasOpenAI = hasValidOpenAIApiKey()
        val hasGemini = hasValidGeminiApiKey()
        val shouldUseOpenAI = shouldUseOpenAI()
        
        return when {
            shouldUseOpenAI && hasOpenAI -> OnlineServiceStatus.OPENAI_AVAILABLE
            hasGemini -> OnlineServiceStatus.GEMINI_AVAILABLE
            hasOpenAI -> OnlineServiceStatus.OPENAI_AVAILABLE
            else -> OnlineServiceStatus.NO_API_KEYS
        }
    }
    
    enum class OnlineServiceStatus {
        NO_NETWORK,
        NO_API_KEYS, 
        GEMINI_AVAILABLE,
        OPENAI_AVAILABLE
    }
}