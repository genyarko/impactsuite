package com.mygemma3n.aiapp.feature.plant

import android.graphics.Bitmap
import androidx.core.graphics.scale
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mygemma3n.aiapp.data.UnifiedGemmaService
import com.mygemma3n.aiapp.data.GeminiApiService
import com.mygemma3n.aiapp.data.GeminiApiConfig
import com.mygemma3n.aiapp.domain.repository.SettingsRepository
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.os.Environment

/**
 * General image‑scanner ViewModel powered by on‑device Gemma 3n.
 * – Classifies *any* image (objects, scenes, etc.)
 * – If the label is a plant, also returns species & disease info.
 */
@HiltViewModel
class PlantScannerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gemma: UnifiedGemmaService,
    private val geminiApiService: GeminiApiService,
    private val openAIService: com.mygemma3n.aiapp.feature.chat.OpenAIChatService,
    private val settingsRepository: SettingsRepository,
    private val plantDatabase: PlantDatabase
) : ViewModel() {

    /* ---------- UI state ---------- */
    private val _scanState = MutableStateFlow(ImageScanState())
    val scanState: StateFlow<ImageScanState> = _scanState.asStateFlow()
    
    /* ---------- Text Recognition ---------- */
    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /* ---------- Helper Methods ---------- */
    
    private fun hasNetworkConnection(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    private suspend fun shouldUseOnlineService(): Boolean {
        return try {
            val useOnlineService = settingsRepository.useOnlineServiceFlow.first()
            val hasNetwork = hasNetworkConnection()
            
            if (!useOnlineService || !hasNetwork) return false
            
            // Check if any API key is available
            val modelProvider = settingsRepository.modelProviderFlow.first()
            val hasValidApiKey = when (modelProvider) {
                "openai" -> openAIService.isInitialized()
                "gemini" -> settingsRepository.apiKeyFlow.first().isNotBlank()
                else -> settingsRepository.apiKeyFlow.first().isNotBlank() // Default to Gemini
            }
            
            hasValidApiKey
        } catch (e: Exception) {
            Timber.w(e, "Error checking service preference, defaulting to offline")
            false
        }
    }

    private suspend fun shouldUseOpenAI(): Boolean {
        return try {
            val modelProvider = settingsRepository.modelProviderFlow.first()
            modelProvider == "openai" && openAIService.isInitialized()
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun initializeApiServiceIfNeeded() {
        if (!geminiApiService.isInitialized()) {
            val apiKey = settingsRepository.apiKeyFlow.first()
            if (apiKey.isNotBlank()) {
                try {
                    geminiApiService.initialize(GeminiApiConfig(apiKey = apiKey))
                    Timber.d("GeminiApiService initialized for image analysis")
                } catch (e: Exception) {
                    Timber.e(e, "Failed to initialize GeminiApiService")
                    throw e
                }
            } else {
                throw IllegalStateException("API key not found")
            }
        }
    }

    private suspend fun extractTextFromImage(bitmap: Bitmap): String? {
        return try {
            // Preprocess image for better handwriting recognition
            val preprocessedBitmap = preprocessImageForOCR(bitmap)
            val image = InputImage.fromBitmap(preprocessedBitmap, 0)
            val task = textRecognizer.process(image)
            
            // Convert to coroutine
            kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
                task.addOnSuccessListener { visionText ->
                    val extractedText = visionText.text
                    continuation.resumeWith(Result.success(extractedText.takeIf { it.isNotBlank() }))
                }.addOnFailureListener { exception ->
                    Timber.w(exception, "Text recognition failed")
                    continuation.resumeWith(Result.success(null))
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Text extraction failed")
            null
        }
    }

    private fun preprocessImageForOCR(bitmap: Bitmap): Bitmap {
        return try {
            // Convert to mutable bitmap
            val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
            
            // Apply comprehensive preprocessing pipeline
            val width = mutableBitmap.width
            val height = mutableBitmap.height
            val pixels = IntArray(width * height)
            mutableBitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            
            // Step 1: Noise Reduction with Gaussian blur approximation
            val blurredPixels = applyGaussianBlur(pixels, width, height)
            
            // Step 2: Convert to grayscale and enhance contrast
            for (i in blurredPixels.indices) {
                val pixel = blurredPixels[i]
                val r = (pixel shr 16) and 0xff
                val g = (pixel shr 8) and 0xff
                val b = pixel and 0xff
                
                // Convert to grayscale
                val gray = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
                
                // Enhance contrast for handwriting (make darks darker, lights lighter)
                val enhanced = when {
                    gray < 85 -> (gray * 0.7).toInt() // Darken dark areas
                    gray > 170 -> 255 // Pure white for very light areas
                    else -> ((gray - 127) * 1.5 + 127).toInt().coerceIn(0, 255) // Enhance mid-tones
                }
                
                blurredPixels[i] = (255 shl 24) or (enhanced shl 16) or (enhanced shl 8) or enhanced
            }
            
            mutableBitmap.setPixels(blurredPixels, 0, width, 0, 0, width, height)
            mutableBitmap
        } catch (e: Exception) {
            Timber.w(e, "Image preprocessing failed, using original")
            bitmap
        }
    }
    
    private fun applyGaussianBlur(pixels: IntArray, width: Int, height: Int): IntArray {
        // Simple 3x3 Gaussian kernel approximation for noise reduction
        val kernel = arrayOf(
            intArrayOf(1, 2, 1),
            intArrayOf(2, 4, 2),
            intArrayOf(1, 2, 1)
        )
        val kernelSum = 16
        
        val result = IntArray(pixels.size)
        
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                var totalR = 0
                var totalG = 0
                var totalB = 0
                
                // Apply kernel
                for (ky in -1..1) {
                    for (kx in -1..1) {
                        val pixelIndex = (y + ky) * width + (x + kx)
                        val pixel = pixels[pixelIndex]
                        val weight = kernel[ky + 1][kx + 1]
                        
                        totalR += ((pixel shr 16) and 0xff) * weight
                        totalG += ((pixel shr 8) and 0xff) * weight
                        totalB += (pixel and 0xff) * weight
                    }
                }
                
                // Normalize and set pixel
                val finalR = (totalR / kernelSum).coerceIn(0, 255)
                val finalG = (totalG / kernelSum).coerceIn(0, 255)
                val finalB = (totalB / kernelSum).coerceIn(0, 255)
                
                result[y * width + x] = (255 shl 24) or (finalR shl 16) or (finalG shl 8) or finalB
            }
        }
        
        // Copy edge pixels unchanged
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (y == 0 || y == height - 1 || x == 0 || x == width - 1) {
                    result[y * width + x] = pixels[y * width + x]
                }
            }
        }
        
        return result
    }
    
    private fun applyEdgeDetection(bitmap: Bitmap): Bitmap {
        // Sobel edge detection for text boundaries
        val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val width = mutableBitmap.width
        val height = mutableBitmap.height
        val pixels = IntArray(width * height)
        mutableBitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        
        // Convert to grayscale first
        val grayPixels = IntArray(pixels.size)
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = (pixel shr 16) and 0xff
            val g = (pixel shr 8) and 0xff
            val b = pixel and 0xff
            val gray = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
            grayPixels[i] = gray
        }
        
        val result = IntArray(pixels.size)
        
        // Sobel kernels
        val sobelX = arrayOf(
            intArrayOf(-1, 0, 1),
            intArrayOf(-2, 0, 2),
            intArrayOf(-1, 0, 1)
        )
        val sobelY = arrayOf(
            intArrayOf(-1, -2, -1),
            intArrayOf(0, 0, 0),
            intArrayOf(1, 2, 1)
        )
        
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                var gx = 0
                var gy = 0
                
                // Apply Sobel kernels
                for (ky in -1..1) {
                    for (kx in -1..1) {
                        val pixelIndex = (y + ky) * width + (x + kx)
                        val grayValue = grayPixels[pixelIndex]
                        
                        gx += grayValue * sobelX[ky + 1][kx + 1]
                        gy += grayValue * sobelY[ky + 1][kx + 1]
                    }
                }
                
                // Calculate magnitude
                val magnitude = kotlin.math.sqrt((gx * gx + gy * gy).toDouble()).toInt()
                val edgeValue = magnitude.coerceIn(0, 255)
                
                // Invert for text (make text dark, background light)
                val finalValue = 255 - edgeValue
                result[y * width + x] = (255 shl 24) or (finalValue shl 16) or (finalValue shl 8) or finalValue
            }
        }
        
        // Copy edge pixels
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (y == 0 || y == height - 1 || x == 0 || x == width - 1) {
                    result[y * width + x] = (255 shl 24) or (255 shl 16) or (255 shl 8) or 255
                }
            }
        }
        
        mutableBitmap.setPixels(result, 0, width, 0, 0, width, height)
        return mutableBitmap
    }
    
    private fun applyDilation(bitmap: Bitmap): Bitmap {
        // Morphological dilation - makes text thicker/bolder
        val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val width = mutableBitmap.width
        val height = mutableBitmap.height
        val pixels = IntArray(width * height)
        mutableBitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        
        // Convert to grayscale and binary
        val binaryPixels = IntArray(pixels.size)
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = (pixel shr 16) and 0xff
            val g = (pixel shr 8) and 0xff
            val b = pixel and 0xff
            val gray = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
            binaryPixels[i] = if (gray < 128) 0 else 255 // Binary threshold
        }
        
        val result = IntArray(pixels.size)
        val structuringElement = arrayOf(
            intArrayOf(0, 1, 0),
            intArrayOf(1, 1, 1),
            intArrayOf(0, 1, 0)
        ) // Cross-shaped structuring element
        
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                var maxValue = 0
                
                // Apply structuring element
                for (ky in -1..1) {
                    for (kx in -1..1) {
                        if (structuringElement[ky + 1][kx + 1] == 1) {
                            val pixelIndex = (y + ky) * width + (x + kx)
                            maxValue = maxOf(maxValue, binaryPixels[pixelIndex])
                        }
                    }
                }
                
                result[y * width + x] = (255 shl 24) or (maxValue shl 16) or (maxValue shl 8) or maxValue
            }
        }
        
        // Copy edge pixels
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (y == 0 || y == height - 1 || x == 0 || x == width - 1) {
                    val originalValue = binaryPixels[y * width + x]
                    result[y * width + x] = (255 shl 24) or (originalValue shl 16) or (originalValue shl 8) or originalValue
                }
            }
        }
        
        mutableBitmap.setPixels(result, 0, width, 0, 0, width, height)
        return mutableBitmap
    }
    
    private fun applyErosion(bitmap: Bitmap): Bitmap {
        // Morphological erosion - makes text thinner
        val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val width = mutableBitmap.width
        val height = mutableBitmap.height
        val pixels = IntArray(width * height)
        mutableBitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        
        // Convert to grayscale and binary
        val binaryPixels = IntArray(pixels.size)
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = (pixel shr 16) and 0xff
            val g = (pixel shr 8) and 0xff
            val b = pixel and 0xff
            val gray = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
            binaryPixels[i] = if (gray < 128) 0 else 255 // Binary threshold
        }
        
        val result = IntArray(pixels.size)
        val structuringElement = arrayOf(
            intArrayOf(0, 1, 0),
            intArrayOf(1, 1, 1),
            intArrayOf(0, 1, 0)
        ) // Cross-shaped structuring element
        
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                var minValue = 255
                
                // Apply structuring element
                for (ky in -1..1) {
                    for (kx in -1..1) {
                        if (structuringElement[ky + 1][kx + 1] == 1) {
                            val pixelIndex = (y + ky) * width + (x + kx)
                            minValue = minOf(minValue, binaryPixels[pixelIndex])
                        }
                    }
                }
                
                result[y * width + x] = (255 shl 24) or (minValue shl 16) or (minValue shl 8) or minValue
            }
        }
        
        // Copy edge pixels
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (y == 0 || y == height - 1 || x == 0 || x == width - 1) {
                    val originalValue = binaryPixels[y * width + x]
                    result[y * width + x] = (255 shl 24) or (originalValue shl 16) or (originalValue shl 8) or originalValue
                }
            }
        }
        
        mutableBitmap.setPixels(result, 0, width, 0, 0, width, height)
        return mutableBitmap
    }

    private suspend fun analyzeImageWithService(bitmap: Bitmap): String {
        // Extract text first for potential enhancement
        val extractedText = extractTextFromImage(bitmap)
        
        return if (shouldUseOnlineService()) {
            try {
                if (shouldUseOpenAI()) {
                    analyzeImageWithOpenAI(bitmap, extractedText)
                } else {
                    initializeApiServiceIfNeeded()
                    // Use Gemini's vision capabilities
                    val prompt = buildPromptForOnlineService(extractedText)
                    geminiApiService.generateContentWithImageAndModel(
                        modelName = GeminiApiService.GEMINI_FLASH_MODEL, // Use vision model
                        prompt = prompt,
                        image = bitmap.resizeToGemma(512),
                        serviceType = "plant"
                    )
                }
            } catch (e: Exception) {
                Timber.w(e, "Online image analysis failed, falling back to offline")
                analyzeImageOffline(bitmap, extractedText)
            }
        } else {
            analyzeImageOffline(bitmap, extractedText)
        }
    }

    private suspend fun analyzeImageWithOpenAI(bitmap: Bitmap, extractedText: String? = null): String {
        val prompt = buildPromptForOpenAI(extractedText)
        return openAIService.analyzeImageWithBitmap(
            prompt = prompt,
            bitmap = bitmap.resizeToGemma(512), // Consistent with other services
            maxTokens = 1000, // Sufficient for detailed JSON response
            temperature = 0.3f // Lower temperature for more consistent analysis
        )
    }

    private suspend fun analyzeImageOffline(bitmap: Bitmap, extractedText: String? = null): String {
        val square = bitmap.resizeToGemma(512)
        val mpImage: MPImage = BitmapImageBuilder(square).build()
        gemma.initialize()
        val prompt = buildPromptWithImage(extractedText)
        return gemma.generateResponse(prompt, mpImage)
    }

    private fun buildPromptForOnlineService(extractedText: String? = null): String {
        val textContext = extractedText?.let { "\n\nExtracted text from image: \"$it\"" } ?: ""
        return """
            Analyze this image and determine if it contains plants or food. Return JSON with this structure:$textContext
            
            If it's a plant:
            {
                "label": "main object/subject",
                "confidence": 0.95,
                "analysisType": "PLANT",
                "plantSpecies": "species name or N/A",
                "disease": "disease name or None if healthy",
                "severity": "severity level if disease present",
                "recommendations": ["list of care recommendations"]
            }
            
            If it's food:
            {
                "label": "main food type",
                "confidence": 0.95,
                "analysisType": "FOOD",
                "foodItems": [
                    {
                        "name": "food item name",
                        "confidence": 0.9,
                        "estimatedWeight": 150,
                        "caloriesPer100g": 250,
                        "totalCalories": 375,
                        "macros": {
                            "carbs": 45.0,
                            "protein": 12.0,
                            "fat": 8.5,
                            "fiber": 3.2
                        }
                    }
                ],
                "totalCalories": 375,
                "nutritionalInfo": {
                    "totalCarbs": 45.0,
                    "totalProtein": 12.0,
                    "totalFat": 8.5,
                    "totalFiber": 3.2,
                    "servingSize": "1 serving"
                },
                "recommendations": ["nutritional advice or serving suggestions"]
            }
            
            If neither plant nor food:
            {
                "label": "object description",
                "confidence": 0.95,
                "analysisType": "GENERAL",
                "recommendations": ["general observations"]
            }
        """.trimIndent()
    }

    private fun buildPromptForOpenAI(extractedText: String? = null): String {
        val textContext = extractedText?.let { "\n\nExtracted text from image: \"$it\"\nUse this text context to enhance your analysis." } ?: ""
        return """
            You are an expert image analyst specializing in plants, food, and general object recognition. Analyze this image carefully and provide a detailed JSON response.$textContext

            **INSTRUCTIONS:**
            1. Determine if the image contains primarily PLANTS, FOOD, or is a GENERAL object/scene
            2. Provide accurate identification with confidence scores
            3. For plants: Include species, health status, diseases, and care recommendations
            4. For food: Include nutritional analysis, calorie estimates, and serving information
            5. For general objects: Provide clear description and relevant observations

            **REQUIRED JSON FORMAT:**
            ```json
            {
                "label": "primary object or subject description",
                "confidence": 0.95,
                "analysisType": "PLANT|FOOD|GENERAL",
                
                // IF PLANT (include these fields):
                "plantSpecies": "scientific or common name, or 'Unknown' if uncertain",
                "disease": "disease name or 'None' if healthy",
                "severity": "mild|moderate|severe (only if disease present)",
                "recommendations": ["specific care advice", "treatment suggestions"],
                
                // IF FOOD (include these fields):
                "foodItems": [
                    {
                        "name": "food item name",
                        "confidence": 0.9,
                        "estimatedWeight": 150,
                        "caloriesPer100g": 250,
                        "totalCalories": 375,
                        "macros": {
                            "carbs": 45.0,
                            "protein": 12.0,
                            "fat": 8.5,
                            "fiber": 3.2
                        }
                    }
                ],
                "totalCalories": 375,
                "nutritionalInfo": {
                    "totalCarbs": 45.0,
                    "totalProtein": 12.0,
                    "totalFat": 8.5,
                    "totalFiber": 3.2,
                    "servingSize": "1 medium portion"
                },
                "recommendations": ["nutritional insights", "serving suggestions"],
                
                // IF GENERAL (always include):
                "recommendations": ["general observations", "relevant information"]
            }
            ```

            **ANALYSIS GUIDELINES:**
            - Be specific and accurate in identification
            - Provide confidence scores between 0.0 and 1.0
            - For plants: Focus on health, species ID, and actionable care advice
            - For food: Estimate realistic portions and nutritional values
            - For general objects: Describe clearly and provide useful context
            - If uncertain, acknowledge limitations and provide best estimates

            Analyze the image now and respond with ONLY the JSON formatted exactly as shown above.
        """.trimIndent()
    }

    /* ---------- Public API ---------- */
    fun analyzeImage(bitmap: Bitmap) = viewModelScope.launch {
        // 0 · UI → “busy”
        val usingOnline = shouldUseOnlineService()
        _scanState.update { it.copy(isAnalyzing = true, error = null, isUsingOnlineService = usingOnline) }

        try {
            /* 1 · Pre‑process --------------------------------------------------- */

            /* 2 · Lazy‑load local model --------------------------------------- */

            /* 3 · Compose compact prompt -------------------------------------- */

            /* 4 · Generate multimodal answer ---------------------------------- */
            val raw = analyzeImageWithService(bitmap)

            /* 5 · Parse + enrich --------------------------------------------- */
            val extractedText = extractTextFromImage(bitmap)
            val analysis  = parseGeneralAnalysis(raw).copy(extractedText = extractedText)
            val enriched  = analysis.plantSpecies?.let { plantDatabase.getAdditionalInfo(it) }

            _scanState.update {
                it.copy(
                    isAnalyzing     = false,
                    currentAnalysis = analysis.copy(additionalInfo = enriched),
                    scanHistory     = it.scanHistory + analysis
                )
            }

        } catch (e: Exception) {
            Timber.e(e)
            _scanState.update { it.copy(isAnalyzing = false, error = e.localizedMessage) }
        }
    }

    /** OCR-only analysis for text extraction with enhanced handwriting recognition */
    fun analyzeImageForOCR(bitmap: Bitmap) = viewModelScope.launch {
        _scanState.update { it.copy(isAnalyzing = true, error = null, isOcrMode = true, extractedText = null) }

        try {
            val usingOnlineOCR = shouldUseOnlineService()
            val extractedText = if (usingOnlineOCR) {
                // Use Gemini 2.5 Flash for superior online OCR with handwriting recognition
                performGeminiOCR(bitmap)
            } else {
                // Fallback to local ML Kit with multiple processing strategies
                extractTextWithMultipleStrategies(bitmap)
            }
            
            _scanState.update {
                it.copy(
                    isAnalyzing = false,
                    extractedText = extractedText,
                    currentAnalysis = null, // Clear previous analysis in OCR mode
                    isUsingGeminiOCR = usingOnlineOCR
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "OCR analysis failed")
            _scanState.update { it.copy(isAnalyzing = false, error = e.localizedMessage) }
        }
    }

    private suspend fun performGeminiOCR(bitmap: Bitmap): String? {
        return try {
            // Initialize Gemini service if needed
            if (!geminiApiService.isInitialized()) {
                initializeApiServiceIfNeeded()
            }
            
            Timber.d("Using Gemini 2.5 Flash for OCR - superior handwriting recognition")
            val result = geminiApiService.performOCR(bitmap)
            
            // Clean up the result
            val cleanedResult = result.trim()
            if (cleanedResult.equals("No text detected", ignoreCase = true) || cleanedResult.isBlank()) {
                null
            } else {
                cleanedResult
            }
        } catch (e: Exception) {
            Timber.w(e, "Gemini OCR failed, falling back to local processing")
            // Fallback to local processing if Gemini fails
            extractTextWithMultipleStrategies(bitmap)
        }
    }

    private suspend fun extractTextWithMultipleStrategies(bitmap: Bitmap): String? {
        val results = mutableListOf<String>()
        
        try {
            // Strategy 1: Original image
            val originalText = extractTextFromImage(bitmap)
            if (!originalText.isNullOrBlank()) results.add(originalText)
            
            // Strategy 2: High contrast version
            val contrastBitmap = applyHighContrast(bitmap)
            val contrastText = extractTextFromImage(contrastBitmap)
            if (!contrastText.isNullOrBlank()) results.add(contrastText)
            
            // Strategy 3: Scaled version for better resolution
            val scaledBitmap = bitmap.scale(bitmap.width * 2, bitmap.height * 2)
            val scaledText = extractTextFromImage(scaledBitmap)
            if (!scaledText.isNullOrBlank()) results.add(scaledText)
            
            // Strategy 4: Rotation Detection - try different orientations
            val rotationAngles = listOf(90f, 180f, 270f)
            for (angle in rotationAngles) {
                val rotatedBitmap = rotateBitmap(bitmap, angle)
                val rotatedText = extractTextFromImage(rotatedBitmap)
                if (!rotatedText.isNullOrBlank()) results.add(rotatedText)
            }
            
            // Strategy 5: Edge Detection for text boundaries
            val edgeDetectedBitmap = applyEdgeDetection(bitmap)
            val edgeText = extractTextFromImage(edgeDetectedBitmap)
            if (!edgeText.isNullOrBlank()) results.add(edgeText)
            
            // Strategy 6: Morphological Operations - Dilation for thicker text
            val dilatedBitmap = applyDilation(bitmap)
            val dilatedText = extractTextFromImage(dilatedBitmap)
            if (!dilatedText.isNullOrBlank()) results.add(dilatedText)
            
            // Strategy 7: Morphological Operations - Erosion for thinner text
            val erodedBitmap = applyErosion(bitmap)
            val erodedText = extractTextFromImage(erodedBitmap)
            if (!erodedText.isNullOrBlank()) results.add(erodedText)
            
            // Return the longest result (usually most complete)
            return results.maxByOrNull { it.length }?.trim()
        } catch (e: Exception) {
            Timber.w(e, "Multi-strategy OCR failed")
            return null
        }
    }
    
    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        return try {
            val matrix = android.graphics.Matrix()
            matrix.postRotate(degrees)
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (e: Exception) {
            Timber.w(e, "Bitmap rotation failed for $degrees degrees")
            bitmap
        }
    }

    private fun applyHighContrast(bitmap: Bitmap): Bitmap {
        val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val width = mutableBitmap.width
        val height = mutableBitmap.height
        val pixels = IntArray(width * height)
        mutableBitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = (pixel shr 16) and 0xff
            val g = (pixel shr 8) and 0xff
            val b = pixel and 0xff
            val gray = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
            
            // Extreme contrast for handwriting
            val enhanced = if (gray < 128) 0 else 255
            pixels[i] = (255 shl 24) or (enhanced shl 16) or (enhanced shl 8) or enhanced
        }
        
        mutableBitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return mutableBitmap
    }

    /** Toggle between OCR mode and regular analysis mode */
    fun toggleOcrMode() {
        _scanState.update { 
            it.copy(
                isOcrMode = !it.isOcrMode,
                extractedText = null,
                currentAnalysis = null,
                error = null,
                isUsingGeminiOCR = false
            )
        }
    }

    /** Save extracted text to clipboard or file */
    fun saveExtractedText(): String? {
        return _scanState.value.extractedText
    }

    /** Download extracted text as TXT file */
    fun downloadAsTXT(text: String): File? {
        return try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val filename = "OCR_Text_$timestamp.txt"
            val downloadsDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "OCR")
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs()
            }
            
            val file = File(downloadsDir, filename)
            file.writeText(text, Charsets.UTF_8)
            Timber.d("TXT file saved: ${file.absolutePath}")
            file
        } catch (e: IOException) {
            Timber.e(e, "Failed to save TXT file")
            null
        }
    }

    /** Download extracted text as DOCX file */
    fun downloadAsDOCX(text: String): File? {
        return try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val filename = "OCR_Text_$timestamp.docx"
            val downloadsDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "OCR")
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs()
            }
            
            val file = File(downloadsDir, filename)
            val document = XWPFDocument()
            val paragraph = document.createParagraph()
            val run = paragraph.createRun()
            run.setText(text)
            run.fontSize = 12
            run.fontFamily = "Arial"
            
            FileOutputStream(file).use { outputStream ->
                document.write(outputStream)
            }
            document.close()
            
            Timber.d("DOCX file saved: ${file.absolutePath}")
            file
        } catch (e: Exception) {
            Timber.e(e, "Failed to save DOCX file")
            null
        }
    }

    /** Download extracted text as PDF file - Note: For basic text PDF, we'll create a simple text file and rename it for now */
    fun downloadAsPDF(text: String): File? {
        return try {
            // Note: Creating a true PDF would require additional PDF libraries
            // For now, we'll create a text file that can be easily converted to PDF
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val filename = "OCR_Text_$timestamp.pdf.txt"
            val downloadsDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "OCR")
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs()
            }
            
            val file = File(downloadsDir, filename)
            val pdfReadyText = """
                |OCR Extracted Text
                |=================
                |Date: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}
                |
                |$text
                |
                |---
                |Generated by G3N OCR Scanner
            """.trimMargin()
            
            file.writeText(pdfReadyText, Charsets.UTF_8)
            Timber.d("PDF-ready text file saved: ${file.absolutePath}")
            file
        } catch (e: IOException) {
            Timber.e(e, "Failed to save PDF-ready text file")
            null
        }
    }

    /** Insert the image token before the text – Gemma 3n best practice. */
    private fun buildPromptWithImage(extractedText: String? = null): String {
        val textContext = extractedText?.let { " Extracted text: \"$it\"." } ?: ""
        return "```img```<|image|>$BASE_PROMPT$textContext\nJSON Response:"
    }

    /** Resize to a model‑friendly square side. */
    private fun Bitmap.resizeToGemma(target: Int): Bitmap =
        if (width == target && height == target) this else scale(target, target)

    /* ---------- Enhanced JSON parser ---------- */
    private fun parseGeneralAnalysis(raw: String): GeneralAnalysis {
        val cleaned = raw.trim()
            .removePrefix("```json")
            .removeSuffix("```")
            .trim()
        val obj = JSONObject(cleaned)
        
        val analysisTypeStr = obj.optString("analysisType", "GENERAL")
        val analysisType = try {
            AnalysisType.valueOf(analysisTypeStr)
        } catch (e: Exception) {
            AnalysisType.GENERAL
        }
        
        val foodItems = if (analysisType == AnalysisType.FOOD) {
            parseFoodItems(obj.optJSONArray("foodItems"))
        } else {
            emptyList()
        }
        
        val nutritionalInfo = if (analysisType == AnalysisType.FOOD) {
            parseNutritionalInfo(obj.optJSONObject("nutritionalInfo"))
        } else {
            null
        }
        
        return GeneralAnalysis(
            id             = UUID.randomUUID().toString(),
            timestamp      = System.currentTimeMillis(),
            label          = obj.optString("label"),
            confidence     = obj.optDouble("confidence", 0.0).toFloat(),
            plantSpecies   = obj.optString("plantSpecies").takeIf { it != "N/A" && it.isNotBlank() },
            disease        = obj.optString("disease").takeIf { it != "None" && it != "N/A" && it.isNotBlank() },
            severity       = obj.optString("severity"),
            recommendations= obj.optJSONArray("recommendations")?.let { arr ->
                List(arr.length()) { i -> arr.getString(i) }
            } ?: emptyList(),
            additionalInfo = null,
            foodItems      = foodItems,
            totalCalories  = obj.optInt("totalCalories", 0),
            nutritionalInfo = nutritionalInfo,
            analysisType   = analysisType,
            extractedText  = null // Will be set separately during analysis
        )
    }
    
    private fun parseFoodItems(jsonArray: org.json.JSONArray?): List<FoodItem> {
        if (jsonArray == null) return emptyList()
        
        return List(jsonArray.length()) { i ->
            val item = jsonArray.getJSONObject(i)
            val macros = item.optJSONObject("macros")?.let { macrosObj ->
                Macronutrients(
                    carbs = macrosObj.optDouble("carbs", 0.0).toFloat(),
                    protein = macrosObj.optDouble("protein", 0.0).toFloat(),
                    fat = macrosObj.optDouble("fat", 0.0).toFloat(),
                    fiber = macrosObj.optDouble("fiber", 0.0).toFloat()
                )
            } ?: Macronutrients(0f, 0f, 0f, 0f)
            
            FoodItem(
                name = item.optString("name", "Unknown food"),
                confidence = item.optDouble("confidence", 0.0).toFloat(),
                estimatedWeight = item.optInt("estimatedWeight", 0),
                caloriesPer100g = item.optInt("caloriesPer100g", 0),
                totalCalories = item.optInt("totalCalories", 0),
                macros = macros
            )
        }
    }
    
    private fun parseNutritionalInfo(jsonObj: JSONObject?): NutritionalInfo? {
        if (jsonObj == null) return null
        
        return NutritionalInfo(
            totalCarbs = jsonObj.optDouble("totalCarbs", 0.0).toFloat(),
            totalProtein = jsonObj.optDouble("totalProtein", 0.0).toFloat(),
            totalFat = jsonObj.optDouble("totalFat", 0.0).toFloat(),
            totalFiber = jsonObj.optDouble("totalFiber", 0.0).toFloat(),
            servingSize = jsonObj.optString("servingSize", "1 serving")
        )
    }

    companion object {
        /** Enhanced prompt for food and plant analysis with minimal tokens. */
        private const val BASE_PROMPT =
            "Analyze image. Return JSON with: label, confidence(0-1), analysisType(PLANT/FOOD/GENERAL). " +
            "If PLANT: plantSpecies, disease|None, severity, recommendations[]. " +
            "If FOOD: foodItems[{name, estimatedWeight, totalCalories, macros{carbs,protein,fat}}], totalCalories, recommendations[]. " +
            "If GENERAL: recommendations[]."
    }
}

/* ---------- UI‑state & data classes ---------- */

data class ImageScanState(
    val isAnalyzing: Boolean            = false,
    val currentAnalysis: GeneralAnalysis? = null,
    val scanHistory: List<GeneralAnalysis> = emptyList(),
    val error: String?                  = null,
    val isUsingOnlineService: Boolean   = false,
    val isOcrMode: Boolean              = false,
    val extractedText: String?          = null,
    val isUsingGeminiOCR: Boolean       = false
)

data class GeneralAnalysis(
    val id: String,
    val timestamp: Long,
    val label: String,
    val confidence: Float,
    val plantSpecies: String?          = null,
    val disease: String?               = null,
    val severity: String?              = null,
    val recommendations: List<String>  = emptyList(),
    val additionalInfo: PlantInfo?     = null,
    // Food analysis fields
    val foodItems: List<FoodItem>      = emptyList(),
    val totalCalories: Int             = 0,
    val nutritionalInfo: NutritionalInfo? = null,
    val analysisType: AnalysisType     = AnalysisType.GENERAL,
    // Text recognition field
    val extractedText: String?         = null
)

enum class AnalysisType {
    GENERAL, PLANT, FOOD
}

data class FoodItem(
    val name: String,
    val confidence: Float,
    val estimatedWeight: Int, // in grams
    val caloriesPer100g: Int,
    val totalCalories: Int,
    val macros: Macronutrients
)

data class Macronutrients(
    val carbs: Float,     // grams
    val protein: Float,   // grams  
    val fat: Float,       // grams
    val fiber: Float = 0f // grams
)

data class NutritionalInfo(
    val totalCarbs: Float,
    val totalProtein: Float,
    val totalFat: Float,
    val totalFiber: Float,
    val servingSize: String = "1 serving"
)
