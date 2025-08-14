package com.mygemma3n.aiapp.shared_utilities

import android.content.Context
import android.location.Location
import com.mygemma3n.aiapp.data.UnifiedGemmaService
import com.mygemma3n.aiapp.remote.EmergencyDatabase
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

// Function definitions
data class FunctionParameter(
    val name: String,
    val description: String,
    val type: String = "string",
    val required: Boolean = true,
    val enum: List<String>? = null
)

data class FunctionDefinition(
    val name: String,
    val description: String,
    val parameters: List<FunctionParameter>
)

data class FunctionCall(
    @SerializedName("function") val functionName: String,
    @SerializedName("arguments") val arguments: Map<String, Any>
)

sealed class FunctionCallResult {
    data class Success(val result: String) : FunctionCallResult()
    data class Error(val message: String) : FunctionCallResult()
    data class NoFunctionNeeded(val response: String) : FunctionCallResult()
}

// Function Registry
class FunctionRegistry {
    private val functions = mutableMapOf<String, suspend (Map<String, Any>) -> String>()
    private val definitions = mutableMapOf<String, FunctionDefinition>()

    fun register(
        definition: FunctionDefinition,
        implementation: suspend (Map<String, Any>) -> String
    ) {
        functions[definition.name] = implementation
        definitions[definition.name] = definition
    }

    fun getFunctions(): List<FunctionDefinition> = definitions.values.toList()

    suspend fun execute(call: FunctionCall): FunctionCallResult {
        val function = functions[call.functionName]
        return if (function != null) {
            try {
                val result = function(call.arguments)
                FunctionCallResult.Success(result)
            } catch (e: Exception) {
                FunctionCallResult.Error("Function execution failed: ${e.message}")
            }
        } else {
            FunctionCallResult.Error("Function ${call.functionName} not found")
        }
    }
}

// Location Provider Service
@Singleton
class LocationService @Inject constructor(
    private val context: Context
) {
    fun getCurrentLocation(): Location? {
        // In production, use FusedLocationProviderClient
        // For now, return mock location
        return Location("mock").apply {
            latitude = 5.6037  // Accra, Ghana
            longitude = -0.1870
        }
    }
}

// Crisis Function Calling Implementation
@Singleton
class CrisisFunctionCalling @Inject constructor(
    private val context: Context,
    private val locationService: LocationService,
    private val emergencyDb: EmergencyDatabase,
    private val gemmaService: UnifiedGemmaService  // Updated to use UnifiedGemmaService
) {
    private val functionRegistry = FunctionRegistry()
    private val gson = Gson()

    init {
        registerEmergencyFunctions()
    }

    private fun registerEmergencyFunctions() {
        // Get nearest hospital
        functionRegistry.register(
            FunctionDefinition(
                name = "get_nearest_hospital",
                description = "Find nearest hospital based on current location and injury type",
                parameters = listOf(
                    FunctionParameter(
                        name = "injury_type",
                        description = "Type of medical emergency",
                        enum = listOf("trauma", "cardiac", "stroke", "burn", "general")
                    ),
                    FunctionParameter(
                        name = "max_distance_km",
                        description = "Maximum search distance in kilometers",
                        type = "number",
                        required = false
                    )
                )
            ),
            ::getNearestHospital
        )

        // Get emergency contacts
        functionRegistry.register(
            FunctionDefinition(
                name = "get_emergency_contact",
                description = "Get emergency contact numbers for current region",
                parameters = listOf(
                    FunctionParameter(
                        name = "service",
                        description = "Emergency service type",
                        enum = listOf("police", "fire", "medical", "poison_control", "disaster")
                    )
                )
            ),
            ::getEmergencyContact
        )

        // First aid instructions
        functionRegistry.register(
            FunctionDefinition(
                name = "get_first_aid",
                description = "Get step-by-step first aid instructions",
                parameters = listOf(
                    FunctionParameter(
                        name = "condition",
                        description = "Medical condition requiring first aid",
                        enum = listOf("cpr", "bleeding", "burns", "choking", "fracture", "shock", "poisoning")
                    )
                )
            ),
            ::getFirstAidInstructions
        )

        // Safety check
        functionRegistry.register(
            FunctionDefinition(
                name = "safety_check",
                description = "Perform safety assessment of current situation",
                parameters = listOf(
                    FunctionParameter(
                        name = "situation",
                        description = "Description of the current situation"
                    )
                )
            ),
            ::performSafetyCheck
        )
    }

    suspend fun processQuery(query: String): FunctionCallResult =
        withContext(Dispatchers.Default) {
            try {
                // Build prompt
                val prompt = buildFunctionPrompt(query)

                // Run Gemma using UnifiedGemmaService
                val response = gemmaService.generateWithModel(
                    prompt = prompt,
                    modelVariant = UnifiedGemmaService.ModelVariant.FAST_2B,
                    maxTokens = 256,
                    temperature = 0.1f  // deterministic for function calls
                )

                // Parse & execute function call if present
                val functionCall = parseFunctionCall(response)
                if (functionCall != null) {
                    functionRegistry.execute(functionCall)
                } else {
                    FunctionCallResult.NoFunctionNeeded(response)
                }
            } catch (e: Exception) {
                Timber.e(e, "Error processing crisis query")
                FunctionCallResult.Error("Failed to process query: ${e.message}")
            }
        }

    private fun buildFunctionPrompt(query: String): String {
        val functionsJson = gson.toJson(functionRegistry.getFunctions())

        return """
            You are an emergency assistant with access to these functions:
            $functionsJson
            
            User query: $query
            
            If the query requires a function call, respond with:
            FUNCTION_CALL: {"function": "function_name", "arguments": {"arg1": "value1"}}
            
            Otherwise, provide a direct helpful response.
        """.trimIndent()
    }

    private fun parseFunctionCall(response: String): FunctionCall? {
        return try {
            if (response.contains("FUNCTION_CALL:")) {
                val jsonStart = response.indexOf("{")
                val jsonEnd = response.lastIndexOf("}") + 1
                if (jsonStart != -1 && jsonEnd > jsonStart) {
                    val json = response.substring(jsonStart, jsonEnd)
                    gson.fromJson(json, FunctionCall::class.java)
                } else null
            } else null
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse function call")
            null
        }
    }

    // Function implementations
    private suspend fun getNearestHospital(args: Map<String, Any>): String {
        val injuryType = args["injury_type"] as? String ?: "general"
        val maxDistance = (args["max_distance_km"] as? Number)?.toDouble() ?: 25.0

        val location = locationService.getCurrentLocation()
        if (location == null) {
            return "Unable to determine current location. Please enable location services."
        }

        // Query emergency database
        val hospitals = emergencyDb.hospitalDao().getNearbyHospitals(
            latitude = location.latitude,
            longitude = location.longitude,
            maxDistanceKm = maxDistance,
            specialization = injuryType
        )

        return if (hospitals.isNotEmpty()) {
            val nearest = hospitals.first()
            """
            Nearest ${injuryType} hospital:
            ${nearest.name}
            Distance: ${String.format("%.1f", nearest.distanceKm)} km
            Address: ${nearest.address}
            Phone: ${nearest.phone}
            ETA: ${nearest.estimatedMinutes} minutes
            """.trimIndent()
        } else {
            "No hospitals found within ${maxDistance}km for $injuryType emergencies."
        }
    }

    private suspend fun getEmergencyContact(args: Map<String, Any>): String {
        val service = args["service"] as? String ?: return "Service type required"

        val contacts = emergencyDb.contactDao().getEmergencyContacts()
        val contact = contacts.find { it.service == service }

        return if (contact != null) {
            """
            ${service.uppercase()} Emergency Contact:
            Primary: ${contact.primaryNumber}
            Secondary: ${contact.secondaryNumber ?: "N/A"}
            SMS: ${contact.smsNumber ?: "N/A"}
            """.trimIndent()
        } else {
            "Emergency contact not found for $service"
        }
    }

    private suspend fun getFirstAidInstructions(args: Map<String, Any>): String {
        val condition = args["condition"] as? String ?: return "Condition required"

        val instructions = emergencyDb.firstAidDao().getInstructions(condition)

        return if (instructions != null) {
            """
            First Aid for ${condition.uppercase()}:
            
            ${instructions.steps.joinToString("\n") { "${it.order}. ${it.instruction}" }}
            
            ⚠️ ${instructions.warnings.joinToString("\n")}
            
            Remember: Call emergency services if condition is severe.
            """.trimIndent()
        } else {
            "First aid instructions not available for $condition"
        }
    }

    private suspend fun performSafetyCheck(args: Map<String, Any>): String {
        val situation = args["situation"] as? String ?: return "Situation description required"

        // Use Gemma to analyze the situation
        val analysisPrompt = """
            Analyze this emergency situation and provide a safety assessment:
            "$situation"
            
            Consider: immediate dangers, required actions, and priority concerns.
            Be concise and action-oriented.
        """.trimIndent()

        return gemmaService.generateWithModel(
            prompt = analysisPrompt,
            modelVariant = UnifiedGemmaService.ModelVariant.FAST_2B,
            maxTokens = 150
        )
    }
}