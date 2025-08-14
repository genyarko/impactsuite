package com.example.mygemma3n.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApiKeyValidator @Inject constructor() {
    
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()
    
    /**
     * Validate OpenAI API key
     */
    suspend fun validateOpenAIKey(apiKey: String): ApiKeyValidationResult = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext ApiKeyValidationResult(
                isValid = false,
                errorMessage = "API key cannot be empty"
            )
        }
        
        // Basic format validation
        if (!apiKey.startsWith("sk-") || apiKey.length < 20) {
            return@withContext ApiKeyValidationResult(
                isValid = false,
                errorMessage = "Invalid OpenAI API key format. Keys should start with 'sk-' and be at least 20 characters long."
            )
        }
        
        return@withContext try {
            // Test the key with a minimal request
            val testRequestBody = """
                {
                    "model": "gpt-3.5-turbo",
                    "messages": [{"role": "user", "content": "Hi"}],
                    "max_tokens": 1
                }
            """.trimIndent()
            
            val request = Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(testRequestBody.toRequestBody("application/json".toMediaType()))
                .build()
            
            val response = withTimeoutOrNull(15000) {
                httpClient.newCall(request).execute()
            }
            
            when {
                response == null -> ApiKeyValidationResult(
                    isValid = false,
                    errorMessage = "Validation timeout. Please check your internet connection."
                )
                response.isSuccessful -> ApiKeyValidationResult(
                    isValid = true,
                    provider = "OpenAI",
                    message = "API key validated successfully"
                )
                response.code == 401 -> ApiKeyValidationResult(
                    isValid = false,
                    errorMessage = "Invalid API key. Please check your OpenAI API key."
                )
                response.code == 429 -> ApiKeyValidationResult(
                    isValid = false,
                    errorMessage = "Rate limit exceeded. Your API key is valid but currently rate-limited."
                )
                response.code == 403 -> ApiKeyValidationResult(
                    isValid = false,
                    errorMessage = "API key does not have permission for this operation."
                )
                else -> ApiKeyValidationResult(
                    isValid = false,
                    errorMessage = "API validation failed with status ${response.code}: ${response.message}"
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Error validating OpenAI API key")
            ApiKeyValidationResult(
                isValid = false,
                errorMessage = "Validation failed: ${e.message}"
            )
        }
    }
    
    /**
     * Validate Google Gemini API key
     */
    suspend fun validateGeminiKey(apiKey: String): ApiKeyValidationResult = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext ApiKeyValidationResult(
                isValid = false,
                errorMessage = "API key cannot be empty"
            )
        }
        
        // Basic format validation
        if (apiKey.length < 20) {
            return@withContext ApiKeyValidationResult(
                isValid = false,
                errorMessage = "Invalid Gemini API key format. Keys should be at least 20 characters long."
            )
        }
        
        return@withContext try {
            // Test the key with a minimal request to list models
            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey")
                .addHeader("Content-Type", "application/json")
                .get()
                .build()
            
            val response = withTimeoutOrNull(15000) {
                httpClient.newCall(request).execute()
            }
            
            when {
                response == null -> ApiKeyValidationResult(
                    isValid = false,
                    errorMessage = "Validation timeout. Please check your internet connection."
                )
                response.isSuccessful -> {
                    val responseBody = response.body?.string()
                    if (responseBody?.contains("models") == true) {
                        ApiKeyValidationResult(
                            isValid = true,
                            provider = "Google Gemini",
                            message = "API key validated successfully"
                        )
                    } else {
                        ApiKeyValidationResult(
                            isValid = false,
                            errorMessage = "Unexpected response format from Gemini API"
                        )
                    }
                }
                response.code == 400 -> ApiKeyValidationResult(
                    isValid = false,
                    errorMessage = "Invalid API key. Please check your Gemini API key."
                )
                response.code == 403 -> ApiKeyValidationResult(
                    isValid = false,
                    errorMessage = "API key does not have permission or billing is not enabled."
                )
                response.code == 429 -> ApiKeyValidationResult(
                    isValid = false,
                    errorMessage = "Rate limit exceeded. Your API key is valid but currently rate-limited."
                )
                else -> ApiKeyValidationResult(
                    isValid = false,
                    errorMessage = "API validation failed with status ${response.code}: ${response.message}"
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Error validating Gemini API key")
            ApiKeyValidationResult(
                isValid = false,
                errorMessage = "Validation failed: ${e.message}"
            )
        }
    }
    
    /**
     * Validate API key format without making network requests
     */
    fun validateKeyFormat(apiKey: String, provider: ApiProvider): ApiKeyValidationResult {
        if (apiKey.isBlank()) {
            return ApiKeyValidationResult(
                isValid = false,
                errorMessage = "API key cannot be empty"
            )
        }
        
        return when (provider) {
            ApiProvider.OPENAI -> {
                when {
                    !apiKey.startsWith("sk-") -> ApiKeyValidationResult(
                        isValid = false,
                        errorMessage = "OpenAI API keys must start with 'sk-'"
                    )
                    apiKey.length < 20 -> ApiKeyValidationResult(
                        isValid = false,
                        errorMessage = "OpenAI API key is too short"
                    )
                    apiKey.length > 200 -> ApiKeyValidationResult(
                        isValid = false,
                        errorMessage = "OpenAI API key is too long"
                    )
                    else -> ApiKeyValidationResult(
                        isValid = true,
                        provider = "OpenAI",
                        message = "Key format is valid"
                    )
                }
            }
            ApiProvider.GEMINI -> {
                when {
                    apiKey.length < 20 -> ApiKeyValidationResult(
                        isValid = false,
                        errorMessage = "Gemini API key is too short"
                    )
                    apiKey.length > 200 -> ApiKeyValidationResult(
                        isValid = false,
                        errorMessage = "Gemini API key is too long"
                    )
                    else -> ApiKeyValidationResult(
                        isValid = true,
                        provider = "Google Gemini",
                        message = "Key format is valid"
                    )
                }
            }
        }
    }
    
    /**
     * Validate multiple keys concurrently
     */
    suspend fun validateMultipleKeys(
        keys: Map<ApiProvider, String>
    ): Map<ApiProvider, ApiKeyValidationResult> = withContext(Dispatchers.IO) {
        val results = mutableMapOf<ApiProvider, ApiKeyValidationResult>()
        
        keys.forEach { (provider, key) ->
            val result = when (provider) {
                ApiProvider.OPENAI -> validateOpenAIKey(key)
                ApiProvider.GEMINI -> validateGeminiKey(key)
            }
            results[provider] = result
        }
        
        return@withContext results
    }
}

data class ApiKeyValidationResult(
    val isValid: Boolean,
    val provider: String? = null,
    val message: String? = null,
    val errorMessage: String? = null,
    val canRetry: Boolean = true
) {
    val displayMessage: String
        get() = if (isValid) {
            message ?: "API key is valid"
        } else {
            errorMessage ?: "API key validation failed"
        }
}

enum class ApiProvider {
    OPENAI,
    GEMINI
}