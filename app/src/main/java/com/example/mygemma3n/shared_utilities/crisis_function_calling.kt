package com.example.mygemma3n.shared_utilities

import android.content.Context
import android.location.LocationProvider
import com.example.mygemma3n.gemma.GemmaModelManager
import javax.inject.Inject

// CrisisFunctionCalling.kt
class CrisisFunctionCalling @Inject constructor(
    private val context: Context,
    private val locationProvider: LocationProvider
) {
    private val functionRegistry = FunctionRegistry()

    init {
        registerEmergencyFunctions()
    }

    private fun registerEmergencyFunctions() {
        functionRegistry.register(
            Function(
                name = "get_nearest_hospital",
                description = "Find nearest hospital based on current location",
                parameters = listOf(
                    Parameter("injury_type", "Type of medical emergency", required = true)
                ),
                implementation = ::getNearestHospital
            )
        )

        functionRegistry.register(
            Function(
                name = "emergency_contact",
                description = "Get emergency contact numbers for current region",
                parameters = listOf(
                    Parameter("service", "police|fire|medical", required = true)
                ),
                implementation = ::getEmergencyContact
            )
        )
    }

    suspend fun processQuery(query: String): FunctionCallResult {
        val llmResponse = modelManager.getModel(GemmaModelManager.ModelConfig.Fast2B).run(
            formatPromptWithFunctions(query, functionRegistry.getFunctions())
        )

        return parseFunctionCall(llmResponse)?.let { call ->
            functionRegistry.execute(call)
        } ?: FunctionCallResult.NoFunctionNeeded(llmResponse)
    }
}