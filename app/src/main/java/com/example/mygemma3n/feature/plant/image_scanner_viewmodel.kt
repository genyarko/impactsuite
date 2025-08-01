package com.example.mygemma3n.feature.plant

import android.graphics.Bitmap
import androidx.core.graphics.scale
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mygemma3n.data.UnifiedGemmaService
import com.example.mygemma3n.data.GeminiApiService
import com.example.mygemma3n.data.GeminiApiConfig
import com.example.mygemma3n.domain.repository.SettingsRepository
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONObject
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

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
    private val settingsRepository: SettingsRepository,
    private val plantDatabase: PlantDatabase
) : ViewModel() {

    /* ---------- UI state ---------- */
    private val _scanState = MutableStateFlow(ImageScanState())
    val scanState: StateFlow<ImageScanState> = _scanState.asStateFlow()

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
            val hasApiKey = settingsRepository.apiKeyFlow.first().isNotBlank()
            val hasNetwork = hasNetworkConnection()
            
            useOnlineService && hasApiKey && hasNetwork
        } catch (e: Exception) {
            Timber.w(e, "Error checking service preference, defaulting to offline")
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

    private suspend fun analyzeImageWithService(bitmap: Bitmap): String {
        return if (shouldUseOnlineService()) {
            try {
                initializeApiServiceIfNeeded()
                // Use Gemini's vision capabilities
                val prompt = buildPromptForOnlineService()
                geminiApiService.generateContentWithImageAndModel(
                    modelName = GeminiApiService.GEMINI_FLASH_MODEL, // Use vision model
                    prompt = prompt,
                    image = bitmap.resizeToGemma(512),
                    serviceType = "plant"
                )
            } catch (e: Exception) {
                Timber.w(e, "Online image analysis failed, falling back to offline")
                analyzeImageOffline(bitmap)
            }
        } else {
            analyzeImageOffline(bitmap)
        }
    }

    private suspend fun analyzeImageOffline(bitmap: Bitmap): String {
        val square = bitmap.resizeToGemma(512)
        val mpImage: MPImage = BitmapImageBuilder(square).build()
        gemma.initialize()
        val prompt = buildPromptWithImage()
        return gemma.generateResponse(prompt, mpImage)
    }

    private fun buildPromptForOnlineService(): String {
        return """
            Analyze this image and determine if it contains plants or food. Return JSON with this structure:
            
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
            val analysis  = parseGeneralAnalysis(raw)
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

    /** Insert the image token before the text – Gemma 3n best practice. */
    private fun buildPromptWithImage(): String =
        "```img```<|image|>$BASE_PROMPT\nJSON Response:"

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
            analysisType   = analysisType
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
    val isUsingOnlineService: Boolean   = false
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
    val analysisType: AnalysisType     = AnalysisType.GENERAL
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
