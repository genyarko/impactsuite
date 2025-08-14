package com.example.mygemma3n.config

/**
 * Centralized API configuration for all external services.
 * This file contains all API endpoints, models, and configuration constants.
 */
object ApiConfiguration {
    
    // ═══════════════════════════════════════════════════════════════════════════════════
    // ONLINE API CONFIGURATION (Google Cloud Gemini API)
    // ═══════════════════════════════════════════════════════════════════════════════════
    
    object Online {
        // Base URLs
        const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
        
        // Online model names (Google Cloud API)
        const val GEMINI_FLASH_MODEL = "gemini-2.5-flash"
        const val GEMINI_PRO_MODEL = "gemini-2.5-pro"
        const val IMAGE_MODEL = "gemini-2.0-flash-preview-image-generation"
        const val EMBEDDING_MODEL = "embedding-001"
        
        // API endpoints (relative to BASE_URL)
        const val GENERATE_CONTENT_ENDPOINT = "/models/%s:generateContent"
        
        // Default parameters for online API
        object Defaults {
            const val TEMPERATURE = 0.7f
            const val TOP_K = 40
            const val TOP_P = 0.95f
            const val MAX_OUTPUT_TOKENS = 8192  // Gemini 2.5 Flash limit
            const val TIMEOUT_SECONDS = 30L
            const val MAX_RETRIES = 3
        }
        
        // Use case specific configurations for online models
        object Quiz {
            const val TEMPERATURE = 0.8f
            const val MAX_OUTPUT_TOKENS = 2048
        }
        
        object Story {
            const val TEMPERATURE = 0.9f
            const val MAX_OUTPUT_TOKENS = 4096
        }
        
        object Chat {
            const val TEMPERATURE = 0.7f
            const val MAX_OUTPUT_TOKENS = 1024
        }
        
        object Tutor {
            const val TEMPERATURE = 0.6f
            const val MAX_OUTPUT_TOKENS = 1536
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════════
    // OFFLINE/LOCAL AI CONFIGURATION (On-device Gemma models)
    // ═══════════════════════════════════════════════════════════════════════════════════
    
    object Offline {
        // Local model names (on-device)
        const val PRIMARY_GENERATION_MODEL = "gemma-3n-e4b-it"
        const val SENTENCE_ENCODER_MODEL = "universal_sentence_encoder.tflite"
        
        // Model file paths (in assets folder)
        const val MODELS_ASSETS_PATH = "models/"
        const val GEMMA_MODEL_FILE = "gemma-3n-E2B-it-int4.task"
        const val ENCODER_MODEL_FILE = "universal_sentence_encoder.tflite"
        
        // Default parameters for local models
        object Defaults {
            const val TEMPERATURE = 0.8f
            const val TOP_K = 40
            const val MAX_OUTPUT_TOKENS = 2048  // Conservative for mobile
            const val TIMEOUT_SECONDS = 60L  // Longer for on-device processing
        }
        
        // Use case specific configurations for offline models
        object Quiz {
            const val TEMPERATURE = 0.8f
            const val MAX_OUTPUT_TOKENS = 2048
        }
        
        object Story {
            const val TEMPERATURE = 0.9f
            const val MAX_OUTPUT_TOKENS = 4096
        }
        
        object Chat {
            const val TEMPERATURE = 0.7f
            const val MAX_OUTPUT_TOKENS = 1024
        }
        
        object Tutor {
            const val TEMPERATURE = 0.6f
            const val MAX_OUTPUT_TOKENS = 1536
        }
    }
    
    // Safety settings (applies to both online and offline)
    object Safety {
        const val HARASSMENT_THRESHOLD = "BLOCK_MEDIUM_AND_ABOVE"
        const val HATE_SPEECH_THRESHOLD = "BLOCK_MEDIUM_AND_ABOVE"
        const val SEXUALLY_EXPLICIT_THRESHOLD = "BLOCK_MEDIUM_AND_ABOVE"
        const val DANGEROUS_CONTENT_THRESHOLD = "BLOCK_MEDIUM_AND_ABOVE"
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════════
    // OPENAI API CONFIGURATION
    // ═══════════════════════════════════════════════════════════════════════════════════
    
    object OpenAI {
        // Base URL
        const val BASE_URL = "https://api.openai.com/v1"
        
        // Model names
        const val GPT_5_MINI = "gpt-5-mini"  // GPT-5 mini model
        
        // Endpoints
        const val CHAT_COMPLETION_ENDPOINT = "/chat/completions"
        
        // Default parameters
        object Defaults {
            const val TEMPERATURE = 0.7f
            const val MAX_TOKENS = 4096
            const val TOP_P = 1.0f
            const val FREQUENCY_PENALTY = 0.0f
            const val PRESENCE_PENALTY = 0.0f
            const val TIMEOUT_SECONDS = 30L
            const val MAX_RETRIES = 3
        }
        
        // Use case specific configurations for OpenAI models
        object Quiz {
            const val TEMPERATURE = 0.8f
            const val MAX_TOKENS = 2048
        }
        
        object Story {
            const val TEMPERATURE = 0.9f
            const val MAX_TOKENS = 4096
        }
        
        object Chat {
            const val TEMPERATURE = 0.7f
            const val MAX_TOKENS = 2048  // Increased from 1024 to prevent truncation
        }
        
        object Tutor {
            const val TEMPERATURE = 0.6f
            const val MAX_TOKENS = 3000  // Increased from 1536 to prevent truncation
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════════
    // GOOGLE SPEECH API CONFIGURATION
    // ═══════════════════════════════════════════════════════════════════════════════════
    
    object Speech {
        // Base URL
        const val BASE_URL = "https://speech.googleapis.com/v1"
        
        // Endpoints
        const val RECOGNIZE_ENDPOINT = "/speech:recognize"
        const val LONG_RUNNING_RECOGNIZE_ENDPOINT = "/speech:longrunningrecognize"
        
        // Default parameters
        object Defaults {
            const val LANGUAGE_CODE = "en-US"
            const val SAMPLE_RATE_HERTZ = 16000
            const val ENCODING = "LINEAR16"
            const val MAX_ALTERNATIVES = 1
            const val TIMEOUT_SECONDS = 15L
        }
        
        // Supported languages
        object Languages {
            const val ENGLISH_US = "en-US"
            const val ENGLISH_UK = "en-GB"
            const val SPANISH = "es-ES"
            const val FRENCH = "fr-FR"
            const val GERMAN = "de-DE"
            const val CHINESE = "zh-CN"
            const val JAPANESE = "ja-JP"
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════════
    // GOOGLE MAPS API CONFIGURATION
    // ═══════════════════════════════════════════════════════════════════════════════════
    
    object Maps {
        // Base URLs
        const val PLACES_API_BASE_URL = "https://maps.googleapis.com/maps/api/place"
        const val GEOCODING_API_BASE_URL = "https://maps.googleapis.com/maps/api/geocode"
        
        // Endpoints
        const val PLACE_SEARCH_ENDPOINT = "/nearbysearch/json"
        const val PLACE_DETAILS_ENDPOINT = "/details/json"
        const val GEOCODE_ENDPOINT = "/json"
        
        // Default parameters
        object Defaults {
            const val RADIUS = 5000  // 5km radius for place searches
            const val LANGUAGE = "en"
            const val TIMEOUT_SECONDS = 10L
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════════
    // API KEY VALIDATION
    // ═══════════════════════════════════════════════════════════════════════════════════
    
    object Validation {
        // API key format patterns
        const val GEMINI_API_KEY_PATTERN = "^AIza[0-9A-Za-z\\-_]{35}$"
        const val SPEECH_API_KEY_PATTERN = "^AIza[0-9A-Za-z\\-_]{35}$"
        const val MAPS_API_KEY_PATTERN = "^AIza[0-9A-Za-z\\-_]{35}$"
        const val OPENAI_API_KEY_PATTERN = "^sk-proj-[a-zA-Z0-9_-]+$"
        
        // Minimum key lengths
        const val MIN_API_KEY_LENGTH = 39
        const val MAX_API_KEY_LENGTH = 45
        const val MIN_OPENAI_KEY_LENGTH = 100
        const val MAX_OPENAI_KEY_LENGTH = 200
        
        fun isValidGeminiApiKey(key: String): Boolean {
            return key.matches(Regex(GEMINI_API_KEY_PATTERN)) && 
                   key.length in MIN_API_KEY_LENGTH..MAX_API_KEY_LENGTH
        }
        
        fun isValidSpeechApiKey(key: String): Boolean {
            return key.matches(Regex(SPEECH_API_KEY_PATTERN)) &&
                   key.length in MIN_API_KEY_LENGTH..MAX_API_KEY_LENGTH
        }
        
        fun isValidMapsApiKey(key: String): Boolean {
            return key.matches(Regex(MAPS_API_KEY_PATTERN)) &&
                   key.length in MIN_API_KEY_LENGTH..MAX_API_KEY_LENGTH
        }
        
        fun isValidOpenAIApiKey(key: String): Boolean {
            return key.matches(Regex(OPENAI_API_KEY_PATTERN)) &&
                   key.length in MIN_OPENAI_KEY_LENGTH..MAX_OPENAI_KEY_LENGTH
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════════
    // RATE LIMITING & QUOTAS
    // ═══════════════════════════════════════════════════════════════════════════════════
    
    object RateLimits {
        // Requests per minute limits
        const val GEMINI_REQUESTS_PER_MINUTE = 15
        const val SPEECH_REQUESTS_PER_MINUTE = 100
        const val MAPS_REQUESTS_PER_MINUTE = 100
        const val OPENAI_REQUESTS_PER_MINUTE = 60
        
        // Daily quotas (free tier)
        const val GEMINI_DAILY_QUOTA = 1500
        const val SPEECH_DAILY_QUOTA = 60  // minutes
        const val MAPS_DAILY_QUOTA = 100
        const val OPENAI_DAILY_QUOTA = 200000  // tokens for free tier
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════════
    // ERROR CODES & MESSAGES
    // ═══════════════════════════════════════════════════════════════════════════════════
    
    object ErrorCodes {
        // HTTP status codes
        const val UNAUTHORIZED = 401
        const val FORBIDDEN = 403
        const val QUOTA_EXCEEDED = 429
        const val SERVER_ERROR = 500
        
        // Custom error messages
        const val INVALID_API_KEY = "Invalid API key format"
        const val API_KEY_MISSING = "API key is required"
        const val QUOTA_EXCEEDED_MESSAGE = "API quota exceeded"
        const val SERVICE_UNAVAILABLE = "Service temporarily unavailable"
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════════
    // HELP & DOCUMENTATION URLS
    // ═══════════════════════════════════════════════════════════════════════════════════
    
    object Documentation {
        const val GEMINI_API_KEY_URL = "https://makersuite.google.com/app/apikey"
        const val SPEECH_API_SETUP_URL = "https://cloud.google.com/speech-to-text/docs/quickstart-client-libraries"
        const val MAPS_API_SETUP_URL = "https://developers.google.com/maps/documentation/places/web-service/get-api-key"
        const val OPENAI_API_KEY_URL = "https://platform.openai.com/api-keys"
        
        const val GEMINI_DOCS = "https://ai.google.dev/docs"
        const val SPEECH_DOCS = "https://cloud.google.com/speech-to-text/docs"
        const val MAPS_DOCS = "https://developers.google.com/maps/documentation"
        const val OPENAI_DOCS = "https://platform.openai.com/docs"
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════════
    // HELPER FUNCTIONS
    // ═══════════════════════════════════════════════════════════════════════════════════
    
    /**
     * Build complete API URL for online Gemini endpoints
     */
    fun buildOnlineGeminiUrl(endpoint: String, model: String, apiKey: String): String {
        return "${Online.BASE_URL}${endpoint.format(model)}?key=$apiKey"
    }
    
    /**
     * Build complete API URL for Speech endpoints
     */
    fun buildSpeechUrl(endpoint: String, apiKey: String): String {
        return "${Speech.BASE_URL}$endpoint?key=$apiKey"
    }
    
    /**
     * Build complete API URL for Maps endpoints
     */
    fun buildMapsUrl(endpoint: String, apiKey: String): String {
        return "${Maps.PLACES_API_BASE_URL}$endpoint?key=$apiKey"
    }
    
    /**
     * Build complete API URL for OpenAI endpoints
     */
    fun buildOpenAIUrl(endpoint: String): String {
        return "${OpenAI.BASE_URL}$endpoint"
    }
    
    /**
     * Get recommended online configuration for specific use case
     */
    fun getOnlineConfigForUseCase(useCase: String): Triple<Float, Int, Int> {
        return when (useCase.lowercase()) {
            "quiz" -> Triple(Online.Quiz.TEMPERATURE, Online.Defaults.TOP_K, Online.Quiz.MAX_OUTPUT_TOKENS)
            "story" -> Triple(Online.Story.TEMPERATURE, Online.Defaults.TOP_K, Online.Story.MAX_OUTPUT_TOKENS)
            "chat" -> Triple(Online.Chat.TEMPERATURE, Online.Defaults.TOP_K, Online.Chat.MAX_OUTPUT_TOKENS)
            "tutor" -> Triple(Online.Tutor.TEMPERATURE, Online.Defaults.TOP_K, Online.Tutor.MAX_OUTPUT_TOKENS)
            else -> Triple(Online.Defaults.TEMPERATURE, Online.Defaults.TOP_K, Online.Defaults.MAX_OUTPUT_TOKENS)
        }
    }
    
    /**
     * Get recommended offline configuration for specific use case
     */
    fun getOfflineConfigForUseCase(useCase: String): Triple<Float, Int, Int> {
        return when (useCase.lowercase()) {
            "quiz" -> Triple(Offline.Quiz.TEMPERATURE, Offline.Defaults.TOP_K, Offline.Quiz.MAX_OUTPUT_TOKENS)
            "story" -> Triple(Offline.Story.TEMPERATURE, Offline.Defaults.TOP_K, Offline.Story.MAX_OUTPUT_TOKENS)
            "chat" -> Triple(Offline.Chat.TEMPERATURE, Offline.Defaults.TOP_K, Offline.Chat.MAX_OUTPUT_TOKENS)
            "tutor" -> Triple(Offline.Tutor.TEMPERATURE, Offline.Defaults.TOP_K, Offline.Tutor.MAX_OUTPUT_TOKENS)
            else -> Triple(Offline.Defaults.TEMPERATURE, Offline.Defaults.TOP_K, Offline.Defaults.MAX_OUTPUT_TOKENS)
        }
    }
    
    /**
     * Get recommended OpenAI configuration for specific use case
     */
    fun getOpenAIConfigForUseCase(useCase: String): Pair<Float, Int> {
        return when (useCase.lowercase()) {
            "quiz" -> Pair(OpenAI.Quiz.TEMPERATURE, OpenAI.Quiz.MAX_TOKENS)
            "story" -> Pair(OpenAI.Story.TEMPERATURE, OpenAI.Story.MAX_TOKENS)
            "chat" -> Pair(OpenAI.Chat.TEMPERATURE, OpenAI.Chat.MAX_TOKENS)
            "tutor" -> Pair(OpenAI.Tutor.TEMPERATURE, OpenAI.Tutor.MAX_TOKENS)
            else -> Pair(OpenAI.Defaults.TEMPERATURE, OpenAI.Defaults.MAX_TOKENS)
        }
    }
}