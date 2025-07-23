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
import androidx.annotation.RawRes
import com.example.mygemma3n.R
import com.example.mygemma3n.data.UnifiedGemmaService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber


@HiltViewModel
class CrisisHandbookViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val unifiedGemmaService: UnifiedGemmaService,
    private val functionCalling: CrisisFunctionCalling,
    private val offlineMapService: OfflineMapService,
    private val emergencyContacts: EmergencyContactsRepository
) : ViewModel() {

    private val firstAidTemplate by lazy { loadPromptTemplate(R.raw.first_aid_prompt) }
    private val kneeFallback  by lazy { loadPromptTemplate(R.raw.fallback_knee_injury) }
    private val genericFallback by lazy { loadPromptTemplate(R.raw.fallback_generic) }
    private val emergencyPrompt by lazy { loadPromptTemplate(R.raw.prompt_emergency_assistant) }
    private val noResponseFallback by lazy { loadPromptTemplate(R.raw.fallback_no_response) }
    private val errorFallback by lazy { loadPromptTemplate(R.raw.fallback_error) }
    private val timeoutFallback by lazy { loadPromptTemplate(R.raw.timeout_fallback) }

    private suspend fun ensureModelInitialized(): Boolean {
        return try {
            if (!unifiedGemmaService.isInitialized()) {
                // Try to initialize if not already done
                unifiedGemmaService.initializeBestAvailable()
            }
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to ensure Gemma model initialization")
            false
        }
    }
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
                kotlinx.coroutines.withTimeout(120000) { // 2 minutes timeout

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
                                val response = functionResult.response.ifEmpty {
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
                        response = timeoutFallback
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

    private fun loadPromptTemplate(@RawRes resId: Int): String =
        appContext.resources.openRawResource(resId)
            .bufferedReader()
            .use { it.readText().trimIndent() }



    fun buildFirstAidPrompt(query: String, contextText: String): String =
        firstAidTemplate
            .replace("\$query", query)
            .replace("\$context", contextText)


    private suspend fun generateFirstAidResponse(query: String): String {
        ensureModelInitialized()

        val context = loadEmergencyContext()

        val prompt =buildFirstAidPrompt(query, context)

        return try {

            if (!unifiedGemmaService.isInitialized()) {
                unifiedGemmaService.initializeBestAvailable()
            }
            val response = withContext(Dispatchers.Default) {
                unifiedGemmaService.generateTextAsync(
                    prompt = prompt,
                    config = UnifiedGemmaService.GenerationConfig(
                        maxTokens = 512,
                        temperature = 0.7f
                    )
                )
            }


            if (response.isBlank() || response.length < 50) {
                // Provide a fallback response for knee injury
               kneeFallback
            } else {
                response
            }
        } catch (e: Exception) {
            Timber.e(e, "Error generating first aid response")
            genericFallback
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

        // inject the placeholders before calling Gemma
        val prompt = emergencyPrompt
            .replace("\$context", context)
            .replace("\$query", query)

        return try {
            if (!unifiedGemmaService.isInitialized()) {
                unifiedGemmaService.initializeBestAvailable()
            }

            val response = withContext(Dispatchers.Default) {
                unifiedGemmaService.generateTextAsync(
                    prompt = prompt,
                    config = UnifiedGemmaService.GenerationConfig(
                        maxTokens = 256,
                        temperature = 0.7f
                    )
                )
            }

            if (response.isBlank() || response.length < 20) noResponseFallback else response
        } catch (e: Exception) {
            Timber.e(e, "Error generating emergency response")
            errorFallback
        }
    }

    private suspend fun loadEmergencyContext(): String {
        val contacts = emergencyContacts.getLocalEmergencyContacts()
        val emergencyNumbers = contacts.joinToString(", ") { "${it.service}: ${it.primaryNumber}" }

        val timeNow = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

        // location stays default ("Accra, Ghana") in buildContext()
        return buildContext(
            time = timeNow,
            emergencyNumbers = emergencyNumbers
        )
    }


    private val contextTemplate by lazy { loadPromptTemplate(R.raw.context_template) }
    private val firstAidIntro by lazy { loadPromptTemplate(R.raw.first_aid_intro) }
    private val noHospitalsFound by lazy { loadPromptTemplate(R.raw.no_hospitals_found) }

    private val accidentGuide by lazy { loadPromptTemplate(R.raw.accident_reporting_guide) }


    fun buildContext(
        location: String = "Accra, Ghana",
        time: String,
        emergencyNumbers: String
    ): String =
        contextTemplate
            .replace("\$location", location)
            .replace("\$time", time)
            .replace("\$emergencyNumbers", emergencyNumbers)


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
                    _state.update { it.copy(response = firstAidIntro, isProcessing = false) }
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
                    val message = noHospitalsFound
                        .replace("\$lat", String.format("%.4f", location.latitude))
                        .replace("\$lon", String.format("%.4f", location.longitude))

                    _state.update {
                        it.copy(
                            isProcessing = false,
                            response = message,
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
    private fun showAccidentReportingGuide() {
        viewModelScope.launch {
            _state.update { it.copy(isProcessing = true, error = null) }
            kotlinx.coroutines.delay(100)          // keep if you still need the UI pause
            _state.update {
                it.copy(
                    response = accidentGuide,      // file content
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