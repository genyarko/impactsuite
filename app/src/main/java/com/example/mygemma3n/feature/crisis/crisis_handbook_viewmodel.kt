package com.example.mygemma3n.feature.crisis

import android.content.Context
import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mygemma3n.data.GeminiApiService
import com.example.mygemma3n.remote.Hospital
import com.example.mygemma3n.shared_utilities.CrisisFunctionCalling
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import com.example.mygemma3n.remote.EmergencyDatabase
import dagger.hilt.android.qualifiers.ApplicationContext


@HiltViewModel
class CrisisHandbookViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,   // ✨ add this
    private val geminiApiService: GeminiApiService,
    private val functionCalling: CrisisFunctionCalling,
    private val offlineMapService: OfflineMapService,
    private val emergencyContacts: EmergencyContactsRepository
) : ViewModel() {

    private val emergencyDb: EmergencyDatabase by lazy {
        EmergencyDatabase.getInstance(appContext)
    }
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
            geminiApiService.generateTextComplete(prompt)
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
            Time: ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())}
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
                action = {
                    viewModelScope.launch {
                        showNearestHospitalsDirectly()
                    }
                }
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
                action = {
                    showAccidentReportingGuide()
                }
            )
        )
    }

    private suspend fun showNearestHospitalsDirectly() {
        _state.update { it.copy(isProcessing = true, error = null) }

        try {
            val location = _state.value.mapState?.userLocation
            if (location == null) {
                _state.update {
                    it.copy(
                        isProcessing = false,
                        error = "Please enable location to find nearest hospitals"
                    )
                }
                return
            }

            // Get hospitals from database
            val hospitals = emergencyDb.hospitalDao().getNearbyHospitals(
                latitude = location.latitude,
                longitude = location.longitude,
                maxDistanceKm = 10.0,
                specialization = "general"
            )

            if (hospitals.isNotEmpty()) {
                showOnOfflineMap(hospitals)
            } else {
                // If no hospitals in database, show default Accra hospitals
                val defaultHospitals = listOf(
                    Hospital(
                        id = "korle_bu",
                        name = "Korle Bu Teaching Hospital",
                        address = "Guggisberg Ave, Accra",
                        phone = "0302674000",
                        latitude = 5.5365,
                        longitude = -0.2257,
                        specialization = "general",
                        hasEmergency = true
                    ).apply {
                        distanceKm = calculateDistance(location, this)
                        estimatedMinutes = (distanceKm * 2.5).toInt()
                    },
                    Hospital(
                        id = "ridge",
                        name = "Ridge Hospital",
                        address = "Castle Rd, Accra",
                        phone = "0302667812",
                        latitude = 5.5641,
                        longitude = -0.1969,
                        specialization = "general",
                        hasEmergency = true
                    ).apply {
                        distanceKm = calculateDistance(location, this)
                        estimatedMinutes = (distanceKm * 2.5).toInt()
                    },
                    Hospital(
                        id = "37_military",
                        name = "37 Military Hospital",
                        address = "Liberation Rd, Accra",
                        phone = "0302779906",
                        latitude = 5.6202,
                        longitude = -0.1713,
                        specialization = "trauma",
                        hasEmergency = true
                    ).apply {
                        distanceKm = calculateDistance(location, this)
                        estimatedMinutes = (distanceKm * 2.5).toInt()
                    }
                ).sortedBy { it.distanceKm }

                showOnOfflineMap(defaultHospitals)
            }
        } catch (e: Exception) {
            _state.update {
                it.copy(
                    isProcessing = false,
                    error = "Error finding hospitals: ${e.message}"
                )
            }
        }
    }

    private fun calculateDistance(location: Location, hospital: Hospital): Double {
        val earthRadius = 6371.0 // km
        val dLat = Math.toRadians(hospital.latitude - location.latitude)
        val dLon = Math.toRadians(hospital.longitude - location.longitude)

        val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
                kotlin.math.cos(Math.toRadians(location.latitude)) *
                kotlin.math.cos(Math.toRadians(hospital.latitude)) *
                kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)

        val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))

        return earthRadius * c
    }

    private fun showAccidentReportingGuide() {
        val guideText = """
            📋 ACCIDENT REPORTING GUIDE
            
            1. ENSURE SAFETY FIRST
               • Move to a safe location if possible
               • Turn on hazard lights if in a vehicle
               • Check for injuries
            
            2. CALL EMERGENCY SERVICES
               • Police: 191 or 18555
               • Ambulance: 193 (if injuries)
               • Fire Service: 192 (if fire risk)
            
            3. DOCUMENT THE SCENE
               • Take photos of:
                 - Vehicle positions
                 - Damage to all vehicles
                 - Road conditions
                 - Traffic signs/signals
                 - Injuries (if permitted)
               • Note the exact time and location
            
            4. EXCHANGE INFORMATION
               • Names and contact details
               • Insurance information
               • Vehicle registration numbers
               • Driver's license numbers
            
            5. GET WITNESS DETAILS
               • Names and phone numbers
               • Brief statements if possible
            
            6. DO NOT:
               • Admit fault or blame
               • Sign any documents except police report
               • Leave the scene without permission
            
            7. REPORT TO:
               • Your insurance company (within 24 hours)
               • MTTD (Motor Traffic and Transport Department)
               • Get a police report for insurance claims
            
            ⚠️ If injuries are involved, do not move vehicles until police arrive!
        """.trimIndent()

        _state.update {
            it.copy(
                response = guideText,
                isProcessing = false
            )
        }
    }


    data class QuickAction(
        val id: String,
        val title: String,
        val icon: String,
        val action: () -> Unit
    )
}