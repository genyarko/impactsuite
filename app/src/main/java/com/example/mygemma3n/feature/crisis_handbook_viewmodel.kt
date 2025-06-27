package com.example.mygemma3n.feature

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mygemma3n.gemma.GemmaEngine
import com.example.mygemma3n.shared_utilities.CrisisFunctionCalling
import com.example.mygemma3n.gemma.GemmaModelManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import javax.inject.Inject

// CrisisHandbookViewModel.kt

@HiltViewModel
class CrisisHandbookViewModel @Inject constructor(
    private val gemmaEngine: GemmaEngine,
    private val functionCalling: CrisisFunctionCalling,
    private val offlineMapService: OfflineMapService,
    private val emergencyContacts: EmergencyContactsRepository
) : ViewModel() {

    fun handleEmergencyQuery(query: String, location: Location? = null) {
        viewModelScope.launch {
            _state.update { it.copy(isProcessing = true) }

            // First, try function calling for structured actions
            val functionResult = functionCalling.processQuery(query, location)

            when (functionResult) {
                is FunctionCallResult.EmergencyContact -> {
                    displayEmergencyContact(functionResult.contact)
                }
                is FunctionCallResult.NearestFacility -> {
                    showOnOfflineMap(functionResult.facilities)
                }
                is FunctionCallResult.FirstAidInstructions -> {
                    displayStepByStepGuide(functionResult.steps)
                }
                is FunctionCallResult.GeneralResponse -> {
                    // Use standard generation for general queries
                    val response = generateEmergencyResponse(query)
                    _state.update {
                        it.copy(
                            response = response,
                            isProcessing = false
                        )
                    }
                }
            }
        }
    }

    private suspend fun generateEmergencyResponse(query: String): String {
        val context = loadEmergencyContext()

        val prompt = """
            EMERGENCY ASSISTANT - Respond quickly and clearly.
            
            Context: ${context}
            Query: $query
            
            Provide:
            1. Immediate actions (if urgent)
            2. Clear, step-by-step guidance
            3. When to seek professional help
            
            Keep response concise and actionable.
        """.trimIndent()

        return gemmaEngine.generateText(
            prompt = prompt,
            maxTokens = 200,
            temperature = 0.3f,
            modelConfig = GemmaModelManager.ModelConfig.Fast2B // Speed is critical
        ).toList().joinToString("")
    }
}