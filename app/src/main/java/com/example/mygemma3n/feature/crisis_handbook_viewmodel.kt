package com.example.mygemma3n.feature

import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mygemma3n.feature.caption.toList
import com.example.mygemma3n.feature.crisis.*
import com.example.mygemma3n.gemma.GemmaEngine
import com.example.mygemma3n.remote.Hospital
import com.example.mygemma3n.shared_utilities.CrisisFunctionCalling
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CrisisHandbookViewModel @Inject constructor(
    private val gemmaEngine: GemmaEngine,
    private val functionCalling: CrisisFunctionCalling,
    private val offlineMapService: OfflineMapService,
    private val emergencyContacts: EmergencyContactsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(CrisisHandbookState())
    val state: StateFlow<CrisisHandbookState> = _state.asStateFlow()

    fun handleEmergencyQuery(query: String, location: Location? = null) {
        viewModelScope.launch {
            _state.update { it.copy(isProcessing = true, error = null) }

            try {
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
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isProcessing = false,
                        error = "Error processing request: ${e.message}"
                    )
                }
            }
        }
    }

    private fun displayEmergencyContact(contact: EmergencyContactInfo) {
        _state.update { currentState ->
            currentState.copy(
                emergencyContacts = listOf(contact),
                isProcessing = false
            )
        }
    }

    private fun showOnOfflineMap(facilities: List<Hospital>) {
        val markers = facilities.map { hospital ->
            MapMarker(
                id = hospital.id,
                location = Location("").apply {
                    latitude = hospital.latitude
                    longitude = hospital.longitude
                },
                title = hospital.name,
                type = MarkerType.HOSPITAL,
                description = "${hospital.address}\nPhone: ${hospital.phone}\nDistance: ${String.format("%.1f", hospital.distanceKm)}km"
            )
        }

        // Calculate route to nearest facility if available
        val nearestFacility = facilities.firstOrNull()
        val route = if (nearestFacility != null && _state.value.mapState?.userLocation != null) {
            offlineMapService.calculateRoute(
                from = _state.value.mapState!!.userLocation!!,
                to = Location("").apply {
                    latitude = nearestFacility.latitude
                    longitude = nearestFacility.longitude
                }
            )
        } else null

        _state.update { currentState ->
            currentState.copy(
                nearbyFacilities = facilities,
                mapState = OfflineMapState(
                    userLocation = _state.value.mapState?.userLocation,
                    facilities = markers,
                    route = route,
                    estimatedTime = nearestFacility?.estimatedMinutes
                ),
                isProcessing = false
            )
        }
    }

    private fun displayStepByStepGuide(steps: List<String>) {
        val formattedResponse = buildString {
            appendLine("📋 Emergency Instructions:")
            appendLine()
            steps.forEachIndexed { index, step ->
                appendLine("${index + 1}. $step")
                if (index < steps.size - 1) appendLine()
            }
            appendLine()
            appendLine("⚠️ If condition worsens, call emergency services immediately.")
        }

        _state.update { currentState ->
            currentState.copy(
                response = formattedResponse,
                isProcessing = false
            )
        }
    }

    private suspend fun generateEmergencyResponse(query: String): String {
        val context = loadEmergencyContext()

        val prompt = """
            EMERGENCY ASSISTANT - Respond quickly and clearly.
            
            Context: $context
            Query: $query
            
            Provide:
            1. Immediate actions (if urgent)
            2. Clear, step-by-step guidance
            3. When to seek professional help
            
            Keep response concise and actionable. Use bullet points for clarity.
            Prioritize life-saving information first.
        """.trimIndent()

        return try {
            gemmaEngine.generateText(
                prompt = prompt,
                config = GemmaEngine.GenerationConfig(
                    maxNewTokens = 200,
                    temperature = 0.3f,
                    doSample = false // Use greedy decoding for consistency in emergencies
                )
            ).toList().joinToString("")
        } catch (e: Exception) {
            "Error generating response. Please call emergency services: 112"
        }
    }

    private suspend fun loadEmergencyContext(): String {
        val contacts = emergencyContacts.getLocalEmergencyContacts()
        val emergencyNumbers = contacts.joinToString(", ") {
            "${it.service}: ${it.primaryNumber}"
        }

        return """
            Location: Accra, Ghana
            Time: ${java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())}
            Emergency services: $emergencyNumbers
            Nearest hospitals: Korle Bu Teaching Hospital, Ridge Hospital, 37 Military Hospital
            Weather: Clear (assumed)
            Note: User may be in distress - provide calm, clear instructions
        """.trimIndent()
    }

    fun updateUserLocation(location: Location) {
        _state.update { currentState ->
            currentState.copy(
                mapState = currentState.mapState?.copy(
                    userLocation = location
                ) ?: OfflineMapState(
                    userLocation = location,
                    facilities = emptyList()
                )
            )
        }
    }

    fun selectFacility(facilityId: String) {
        val facility = _state.value.nearbyFacilities.find { it.id == facilityId }
        if (facility != null && _state.value.mapState?.userLocation != null) {
            viewModelScope.launch {
                val route = offlineMapService.calculateRoute(
                    from = _state.value.mapState!!.userLocation!!,
                    to = Location("").apply {
                        latitude = facility.latitude
                        longitude = facility.longitude
                    }
                )

                _state.update { currentState ->
                    currentState.copy(
                        mapState = currentState.mapState?.copy(
                            route = route,
                            estimatedTime = facility.estimatedMinutes
                        )
                    )
                }
            }
        }
    }

    fun getQuickActions(): List<QuickAction> {
        return listOf(
            QuickAction(
                id = "call_emergency",
                title = "Call Emergency",
                icon = "phone",
                action = { handleEmergencyQuery("emergency contact numbers") }
            ),
            QuickAction(
                id = "nearest_hospital",
                title = "Nearest Hospital",
                icon = "hospital",
                action = { handleEmergencyQuery("nearest hospital") }
            ),
            QuickAction(
                id = "first_aid",
                title = "First Aid",
                icon = "medical",
                action = { handleEmergencyQuery("first aid instructions") }
            ),
            QuickAction(
                id = "report_accident",
                title = "Report Accident",
                icon = "warning",
                action = { handleEmergencyQuery("how to report an accident") }
            )
        )
    }

    data class QuickAction(
        val id: String,
        val title: String,
        val icon: String,
        val action: () -> Unit
    )
}

// Extension function for Flow
suspend fun <T> kotlinx.coroutines.flow.Flow<T>.toList(): List<T> {
    val list = mutableListOf<T>()
    collect { list.add(it) }
    return list
}