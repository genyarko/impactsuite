package com.example.mygemma3n.feature.crisis

import android.content.Context
import android.content.Intent
import android.location.Location
import android.widget.Toast
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
import com.example.mygemma3n.shared_utilities.isLocationEnabled
import dagger.hilt.android.qualifiers.ApplicationContext
import android.provider.Settings
import timber.log.Timber


@HiltViewModel
class CrisisHandbookViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val geminiApiService: GeminiApiService,
    private val functionCalling: CrisisFunctionCalling,
    private val offlineMapService: OfflineMapService,
    private val emergencyContacts: EmergencyContactsRepository
) : ViewModel() {

    // Remove the init block that was causing the crash
    // The lazy property will be initialized when first accessed
    private val emergencyDb: EmergencyDatabase by lazy {
        EmergencyDatabase.getInstance(appContext)
    }

    private val _state = MutableStateFlow(CrisisHandbookState())
    val state: StateFlow<CrisisHandbookState> = _state.asStateFlow()

    fun handleEmergencyQuery(query: String, location: Location? = null) {
        // Don't process empty queries
        if (query.isBlank()) {
            _state.update {
                it.copy(
                    error = "Please enter a query",
                    isProcessing = false
                )
            }
            return
        }

        viewModelScope.launch {
            try {
                // Clear previous state and show processing
                _state.update {
                    it.copy(
                        isProcessing = true,
                        error = null,
                        response = null,
                        nearbyFacilities = emptyList(),
                        mapState = it.mapState?.copy(facilities = emptyList(), route = null)
                    )
                }

                // Log the query for debugging
                Timber.d("Processing emergency query: $query")

                // Add timeout to prevent hanging
                kotlinx.coroutines.withTimeout(30000) { // 30 second timeout

                    // Check if this is a first aid related query
                    val firstAidKeywords = listOf(
                        "hurt", "pain", "injury", "bleeding", "burn", "cut",
                        "fall", "fell", "broken", "fracture", "wound", "bruise",
                        "sprain", "knee", "ankle", "head", "arm", "leg"
                    )

                    val isFirstAidQuery = firstAidKeywords.any {
                        query.contains(it, ignoreCase = true)
                    }

                    if (isFirstAidQuery || query.contains("first aid", ignoreCase = true)) {
                        Timber.d("Detected first aid query, generating response directly")

                        // Generate first aid response directly
                        val response = generateFirstAidResponse(query)

                        _state.update {
                            it.copy(
                                response = response,
                                isProcessing = false
                            )
                        }
                        return@withTimeout
                    }

                    // Try function calling for other queries
                    try {
                        val functionResult = functionCalling.processQuery(query, location)
                        Timber.d("Function result: ${functionResult::class.simpleName}")

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
                                val response = if (functionResult.response.isNotEmpty()) {
                                    functionResult.response
                                } else {
                                    generateEmergencyResponse(query)
                                }

                                _state.update {
                                    it.copy(
                                        response = response,
                                        isProcessing = false
                                    )
                                }
                            }
                        }
                    } catch (functionError: Exception) {
                        Timber.e(functionError, "Function calling failed, using direct generation")
                        // Fall back to direct generation
                        val response = generateEmergencyResponse(query)
                        _state.update {
                            it.copy(
                                response = response,
                                isProcessing = false
                            )
                        }
                    }
                }

            } catch (timeoutError: kotlinx.coroutines.TimeoutCancellationException) {
                Timber.e(timeoutError, "Query processing timed out")
                _state.update {
                    it.copy(
                        isProcessing = false,
                        error = "Request timed out. Please try again.",
                        response = """
                        The request took too long to process.
                        
                        For immediate help:
                        📞 Emergency: 112
                        🚑 Medical: 193
                        
                        Please try again with a simpler query.
                    """.trimIndent()
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Error processing emergency query")
                _state.update {
                    it.copy(
                        isProcessing = false,
                        error = "Error: ${e.message}",
                        response = """
                        Sorry, I couldn't process your request.
                        
                        For emergencies, please call:
                        📞 112 (General Emergency)
                        🚑 193 (Medical Emergency)
                        
                        Error: ${e.localizedMessage}
                    """.trimIndent()
                    )
                }
            }
        }
    }

    private suspend fun generateFirstAidResponse(query: String): String {
        val context = loadEmergencyContext()

        val prompt = """
        You are an emergency first aid assistant. The user has reported: "$query"
        
        Context: $context
        
        Provide immediate first aid guidance:
        
        1. IMMEDIATE ACTIONS (what to do right now)
        2. ASSESS THE INJURY (what to check)
        3. FIRST AID STEPS (numbered instructions)
        4. WHEN TO SEEK MEDICAL HELP (warning signs)
        5. DO NOT (common mistakes to avoid)
        
        Keep instructions clear, calm, and actionable.
        Format with headers and bullet points for easy reading.
    """.trimIndent()

        return try {
            val response = geminiApiService.generateTextComplete(prompt)

            if (response.isBlank() || response.length < 50) {
                // Provide a fallback response for knee injury
                """
            🏥 First Aid for Knee Injury from Bike Fall
            
            IMMEDIATE ACTIONS:
            • Stay calm and don't put weight on the injured knee
            • Sit or lie down in a comfortable position
            • If bleeding, apply gentle pressure with a clean cloth
            
            ASSESS THE INJURY:
            • Check for obvious deformity or bone protruding
            • Look for severe swelling or inability to move the knee
            • Note if you heard a "pop" sound when you fell
            
            FIRST AID STEPS:
            1. Clean any wounds with water if available
            2. Apply ice wrapped in a cloth for 15-20 minutes
            3. Elevate the leg above heart level if possible
            4. Rest and avoid putting weight on the knee
            5. Consider taking over-the-counter pain medication
            
            SEEK MEDICAL HELP IF:
            ⚠️ Severe pain or unable to bear any weight
            ⚠️ Knee appears deformed or unstable
            ⚠️ Numbness or tingling below the knee
            ⚠️ Deep cuts that won't stop bleeding
            ⚠️ Signs of infection (increasing pain, redness, warmth)
            
            DO NOT:
            ❌ Try to "walk it off" if severely painful
            ❌ Apply ice directly to skin
            ❌ Ignore severe pain or deformity
            
            📞 If severe, call emergency services: 112 or 193
            """.trimIndent()
            } else {
                response
            }
        } catch (e: Exception) {
            Timber.e(e, "Error generating first aid response")
            """
        First Aid Guidance:
        
        For your injury, please follow R.I.C.E:
        • Rest - Avoid using the injured area
        • Ice - Apply for 15-20 minutes at a time
        • Compression - Use a bandage if available
        • Elevation - Raise the injured area
        
        Seek medical attention if:
        • Severe pain or swelling
        • Unable to move or bear weight
        • Visible deformity
        • Numbness or tingling
        
        Emergency numbers:
        📞 112 (General Emergency)
        🚑 193 (Medical Emergency)
        """.trimIndent()
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
        User Query: $query
        
        Instructions:
        - If this is about first aid, provide clear step-by-step instructions
        - If this is a medical emergency query, provide immediate actions
        - Always mention when to call emergency services
        - Keep response concise and actionable
        - Use bullet points or numbered lists for clarity
        - Prioritize life-saving information first
        
        Response:
    """.trimIndent()

        return try {
            val response = geminiApiService.generateTextComplete(prompt)

            // If response is empty or too short, provide a fallback
            if (response.isBlank() || response.length < 20) {
                """
            I'm having trouble generating a response. 
            
            For any emergency:
            📞 Call 112 or 193 immediately
            
            Please try rephrasing your query or select one of the quick actions above.
            """.trimIndent()
            } else {
                response
            }
        } catch (e: Exception) {
            Timber.e(e, "Error generating emergency response")
            """
        Error generating response. 
        
        📞 For emergencies, call:
        - General Emergency: 112
        - Medical Emergency: 193
        - Police: 191
        - Fire: 192
        
        Please try again or seek immediate help if needed.
        """.trimIndent()
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

    // In CrisisHandbookViewModel.getQuickActions(), temporarily add this debug action:

    fun getQuickActions(): List<QuickAction> {
        return listOf(
            QuickAction(
                id = "call_emergency",
                title = "Call Emergency",
                icon = "phone",
                action = {
                    clearStateForNewAction()
                    // This will trigger the emergency contacts display
                    displayEmergencyContact(
                        EmergencyContactInfo(
                            service = "emergency",
                            primaryNumber = "112",
                            secondaryNumber = "193",
                            description = "General Emergency",
                            smsNumber = null
                        )
                    )
                    // Also load all emergency contacts
                    viewModelScope.launch {
                        _state.update { currentState ->
                            currentState.copy(
                                emergencyContacts = emergencyContacts.getLocalEmergencyContacts()
                            )
                        }
                    }
                }
            ),
            QuickAction(
                id = "nearest_hospital",
                title = "Nearest Hospital",
                icon = "hospital",
                action = {
                    viewModelScope.launch {
                        clearStateForNewAction()
                        if (!appContext.isLocationEnabled()) {
                            Toast.makeText(
                                appContext,
                                "Turn on Location (GPS) to find nearby hospitals",
                                Toast.LENGTH_LONG
                            ).show()

                            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            appContext.startActivity(intent)
                            return@launch
                        }
                        showNearestHospitalsDirectly()
                    }
                }
            ),
            QuickAction(
                id = "first_aid",
                title = "First Aid",
                icon = "medical",
                action = {
                    clearStateForNewAction()
                    // Show general first aid prompt
                    _state.update {
                        it.copy(
                            response = """
                        🏥 First Aid Assistant Ready
                        
                        Please describe the situation or injury:
                        - Type of injury (cuts, burns, fractures, etc.)
                        - Severity and symptoms
                        - Any immediate concerns
                        
                        Or try these common scenarios:
                        • "Someone is choking"
                        • "Severe bleeding from a cut"
                        • "Person fainted"
                        • "Burn from hot water"
                        • "Possible broken bone"
                        
                        Type your query below and press enter.
                        """.trimIndent(),
                            isProcessing = false
                        )
                    }
                }
            ),
            QuickAction(
                id = "report_accident",
                title = "Report Accident",
                icon = "warning",
                action = {
                    clearStateForNewAction()
                    showAccidentReportingGuide()
                }
            )
        )
    }

    private fun clearStateForNewAction() {
        _state.update { currentState ->
            currentState.copy(
                response = null,
                emergencyContacts = if (currentState.emergencyContacts.isNotEmpty()) {
                    emptyList()  // Clear if showing specific contact
                } else {
                    currentState.emergencyContacts
                },
                nearbyFacilities = emptyList(),
                mapState = currentState.mapState?.copy(
                    facilities = emptyList(),
                    route = null,
                    estimatedTime = null
                ),
                error = null
            )
        }
    }



    fun checkDatabaseContents() {
        viewModelScope.launch {
            try {
                val totalHospitals = emergencyDb.hospitalDao().countAll()
                val allHospitals = emergencyDb.hospitalDao().getAllHospitals() // You'll need to add this query

                val diagnosticInfo = buildString {
                    appendLine("🔍 DATABASE DIAGNOSTIC")
                    appendLine("Total hospitals: $totalHospitals")
                    appendLine("\nHospitals by city:")

                    val cities = allHospitals.groupBy { it.address.substringAfterLast(", ") }
                    cities.forEach { (city, hospitals) ->
                        appendLine("$city: ${hospitals.size} hospitals")
                        hospitals.take(3).forEach { h ->
                            appendLine("  - ${h.name}")
                        }
                    }
                }

                _state.update {
                    it.copy(
                        response = diagnosticInfo,
                        isProcessing = false
                    )
                }

                Timber.d(diagnosticInfo)
            } catch (e: Exception) {
                Timber.e(e, "Error checking database")
                _state.update {
                    it.copy(
                        error = "Database check failed: ${e.message}",
                        isProcessing = false
                    )
                }
            }
        }
    }

    private suspend fun showNearestHospitalsDirectly() {
        _state.update { it.copy(isProcessing = true, error = null) }

        try {
            val location = getFreshLocation(appContext)
            if (location == null) {
                _state.update {
                    it.copy(
                        isProcessing = false,
                        error = "Waiting for GPS lock… try again in a few seconds"
                    )
                }
                return
            }

            // Log current location for debugging
            Timber.d("User location: ${location.latitude}, ${location.longitude}")

            // Try different search radii - expanding search if nothing found nearby
            val searchRadii = listOf(10.0, 25.0, 50.0, 100.0, 200.0, 500.0) // km
            var hospitals = emptyList<Hospital>()
            var searchRadius = 0.0

            for (radius in searchRadii) {
                try {
                    hospitals = emergencyDb.hospitalDao().getNearbyHospitals(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        maxDistanceKm = radius,
                        specialization = "general"
                    )

                    if (hospitals.isNotEmpty()) {
                        searchRadius = radius
                        Timber.d("Found ${hospitals.size} hospitals within ${radius}km")
                        break
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error accessing database at radius $radius")
                }
            }

            when {
                hospitals.isNotEmpty() -> {
                    // Found hospitals nearby
                    showOnOfflineMap(hospitals.take(10)) // Show max 10 closest

                    // If we had to search far, add a note
                    if (searchRadius > 50) {
                        _state.update { currentState ->
                            currentState.copy(
                                response = "Note: The nearest hospitals are ${searchRadius.toInt()}km away. In case of emergency, call 112 or 193 immediately for ambulance services."
                            )
                        }
                    }
                }

                else -> {
                    // No hospitals found even with expanded search
                    _state.update {
                        it.copy(
                            isProcessing = false,
                            response = """
                            ⚠️ No hospitals found in the database near your location.
                            
                            📍 Your coordinates: ${String.format("%.4f", location.latitude)}, ${String.format("%.4f", location.longitude)}
                            
                            Emergency numbers (work nationwide):
                            📞 General Emergency: 112
                            👮 Police: 191
                            🚑 Ambulance: 193
                            🔥 Fire: 192
                            
                            What to do:
                            1. Call 193 for medical emergencies
                            2. Ask locals for the nearest hospital
                            3. Try searching online for hospitals in your area
                            
                            The app's hospital database covers major cities but may not include all areas yet.
                        """.trimIndent(),
                            emergencyContacts = emergencyContacts.getLocalEmergencyContacts()
                        )
                    }

                    // Log for debugging
                    Timber.w("No hospitals found near lat=${location.latitude}, lon=${location.longitude} even with ${searchRadii.last()}km radius")

                    // Check if database has any hospitals at all
                    val totalHospitals = emergencyDb.hospitalDao().countAll()
                    Timber.d("Total hospitals in database: $totalHospitals")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error in hospital search")
            _state.update {
                it.copy(
                    isProcessing = false,
                    error = "Error finding hospitals: ${e.message}",
                    emergencyContacts = emergencyContacts.getLocalEmergencyContacts()
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
        viewModelScope.launch {
            _state.update { it.copy(isProcessing = true, error = null) }

            // Small delay to ensure UI updates
            kotlinx.coroutines.delay(100)

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
    }

    data class QuickAction(
        val id: String,
        val title: String,
        val icon: String,
        val action: () -> Unit
    )
}