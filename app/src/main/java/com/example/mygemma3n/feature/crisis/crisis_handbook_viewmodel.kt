package com.example.mygemma3n.feature.crisis

import android.content.Context
import android.content.Intent
import android.location.Location
import android.provider.Settings
import android.widget.Toast
import androidx.annotation.RawRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mygemma3n.R
import com.example.mygemma3n.data.UnifiedGemmaService
import com.example.mygemma3n.data.GeminiApiService
import com.example.mygemma3n.data.GeminiApiConfig
import com.example.mygemma3n.data.CapitalCities
import com.example.mygemma3n.data.CapitalCity
import com.example.mygemma3n.data.GooglePlacesService
import com.example.mygemma3n.domain.repository.SettingsRepository
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.mygemma3n.remote.EmergencyDatabase
import com.example.mygemma3n.remote.Hospital
import com.example.mygemma3n.shared_utilities.CrisisFunctionCalling
import com.example.mygemma3n.shared_utilities.isLocationEnabled
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@HiltViewModel
class CrisisHandbookViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val unifiedGemmaService: UnifiedGemmaService,
    private val geminiApiService: GeminiApiService,
    private val googlePlacesService: GooglePlacesService,
    private val settingsRepository: SettingsRepository,
    private val functionCalling: CrisisFunctionCalling,
    private val offlineMapService: OfflineMapService,
    private val emergencyContacts: EmergencyContactsRepository
) : ViewModel() {

    // Load all templates lazily
    private val firstAidTemplate by lazy { loadPromptTemplate(R.raw.first_aid_prompt) }
    private val kneeFallback by lazy { loadPromptTemplate(R.raw.fallback_knee_injury) }
    private val genericFallback by lazy { loadPromptTemplate(R.raw.fallback_generic) }
    private val emergencyPrompt by lazy { loadPromptTemplate(R.raw.prompt_emergency_assistant) }
    private val noResponseFallback by lazy { loadPromptTemplate(R.raw.fallback_no_response) }
    private val errorFallback by lazy { loadPromptTemplate(R.raw.fallback_error) }
    private val timeoutFallback by lazy { loadPromptTemplate(R.raw.timeout_fallback) }
    private val contextTemplate by lazy { loadPromptTemplate(R.raw.context_template) }
    private val firstAidIntro by lazy { loadPromptTemplate(R.raw.first_aid_intro) }
    private val noHospitalsFound by lazy { loadPromptTemplate(R.raw.no_hospitals_found) }
    private val accidentGuide by lazy { loadPromptTemplate(R.raw.accident_reporting_guide) }

    // Pre-defined emergency responses for common scenarios
    private val EMERGENCY_RESPONSES = mapOf(
        // Injuries
        "knee" to kneeFallback,
        "fall" to kneeFallback,
        "bike" to kneeFallback,

        // Chest/Heart
        "chest pain" to """
🚨 CHEST PAIN - POTENTIAL EMERGENCY

IMMEDIATE ACTIONS:
• Call 193 or 112 immediately if severe
• Sit down and rest in comfortable position
• Loosen tight clothing
• Take aspirin if available (unless allergic)

WARNING SIGNS requiring immediate help:
⚠️ Crushing or squeezing pain
⚠️ Pain spreading to arm, jaw, or back
⚠️ Shortness of breath
⚠️ Nausea or lightheadedness
⚠️ Cold sweats

While waiting for help:
• Stay calm and breathe slowly
• Note when symptoms started
• List any medications you take

DO NOT drive yourself to hospital!
        """.trimIndent(),

        // Burns
        "burn" to """
🔥 BURN FIRST AID

IMMEDIATE ACTIONS:
1. Remove from heat source
2. Cool the burn with running water for 10-20 minutes
3. Remove jewelry/clothing near burn (unless stuck)

ASSESS SEVERITY:
• 1st degree: Red, painful, no blisters
• 2nd degree: Blisters, severe pain
• 3rd degree: White/charred, may be painless

FIRST AID:
• DO NOT use ice, butter, or ointments
• Cover with clean, dry cloth
• Take pain medication if needed
• Keep burned area elevated

SEEK IMMEDIATE HELP IF:
⚠️ Burn larger than palm size
⚠️ On face, hands, feet, or genitals
⚠️ Deep or charred appearance
⚠️ Caused by chemicals or electricity

📞 Severe burns: Call 193 immediately
        """.trimIndent(),

        // Bleeding
        "bleeding" to """
🩸 SEVERE BLEEDING CONTROL

IMMEDIATE ACTIONS:
1. Call 193 if bleeding is severe
2. Protect yourself (gloves if available)
3. Apply direct pressure immediately

STEPS TO CONTROL BLEEDING:
1. Press firmly on wound with clean cloth
2. Don't remove cloth if blood soaks through - add more
3. Maintain pressure for 10-15 minutes
4. Elevate injured area above heart if possible

IF BLEEDING WON'T STOP:
• Apply pressure to nearest pressure point
• Consider tourniquet only as last resort
• Note the time if tourniquet applied

SHOCK PREVENTION:
• Keep person warm
• Elevate legs if no spinal injury
• Monitor breathing
• Don't give food or water

⚠️ Call 193 for any uncontrolled bleeding!
        """.trimIndent(),

        // Choking
        "choking" to """
🆘 CHOKING EMERGENCY

FOR CONSCIOUS ADULT:
1. Ask "Are you choking?" 
2. If they can't speak/cough:

HEIMLICH MANEUVER:
1. Stand behind person
2. Make fist with one hand
3. Place fist above navel, below ribcage
4. Grasp fist with other hand
5. Give quick upward thrusts
6. Repeat until object expelled

FOR PREGNANT/OBESE:
• Give chest thrusts instead
• Place fist on center of breastbone

IF PERSON BECOMES UNCONSCIOUS:
1. Lower to ground carefully
2. Call 193 immediately
3. Begin CPR
4. Check mouth before rescue breaths

FOR INFANTS (under 1 year):
• 5 back blows, then 5 chest thrusts
• Support head and neck

⚠️ Even if successful, see doctor afterward
        """.trimIndent(),

        // Fainting
        "faint" to """
😵 FAINTING/UNCONSCIOUSNESS

IMMEDIATE ACTIONS:
1. Check for responsiveness
2. Check breathing
3. Call 193 if not responsive

IF PERSON HAS FAINTED:
• Lay them flat on back
• Elevate legs 12 inches
• Loosen tight clothing
• Check airway is clear
• Fresh air if possible

WHEN THEY WAKE UP:
• Keep lying down for 10-15 minutes
• Sit up slowly
• Give water when fully alert
• Don't let them stand quickly

SEEK MEDICAL HELP IF:
⚠️ Unconscious > 1 minute
⚠️ Difficulty breathing
⚠️ Chest pain before fainting
⚠️ Confusion after waking
⚠️ Pregnant woman
⚠️ Known heart condition

PREVENTION:
• Avoid standing too long
• Stay hydrated
• Rise slowly from sitting/lying
        """.trimIndent(),

        // Fracture
        "broken" to """
🦴 SUSPECTED FRACTURE

IMMEDIATE ACTIONS:
1. Don't move the injured area
2. Call 193 for serious fractures
3. Control any bleeding first

SIGNS OF FRACTURE:
• Severe pain
• Swelling and bruising
• Deformity or odd angle
• Inability to move/bear weight
• Bone visible through skin

FIRST AID STEPS:
1. Immobilize the area
   • Use splint if trained
   • Support above and below injury
2. Apply ice wrapped in cloth
3. Elevate if possible
4. Treat for shock

DO NOT:
❌ Try to realign the bone
❌ Move without immobilizing
❌ Apply ice directly to skin
❌ Give food/water (surgery may be needed)

SPLINTING BASICS:
• Use rigid material (board, rolled newspaper)
• Pad the splint
• Secure without cutting circulation
• Check fingers/toes for numbness

📞 Call 193 for any suspected fracture
        """.trimIndent()
    )

    private val emergencyDb: EmergencyDatabase by lazy {
        EmergencyDatabase.getInstance(appContext)
    }

    private val _state = MutableStateFlow(CrisisHandbookState())
    val state: StateFlow<CrisisHandbookState> = _state.asStateFlow()
    
    /* ───────── service selection helpers ───────── */
    private fun hasNetworkConnection(): Boolean {
        val connectivityManager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
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
                    Timber.d("GeminiApiService initialized for Crisis Handbook")
                } catch (e: Exception) {
                    Timber.e(e, "Failed to initialize GeminiApiService")
                    throw e
                }
            } else {
                throw IllegalStateException("API key not found")
            }
        }
    }

    private suspend fun ensureModelInitialized(): Boolean {
        return try {
            if (!unifiedGemmaService.isInitialized()) {
                unifiedGemmaService.initializeBestAvailable()
            }
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to ensure Gemma model initialization")
            false
        }
    }

    fun handleEmergencyQuery(query: String, location: Location? = null) {
        if (query.isBlank()) {
            _state.update {
                it.copy(
                    error = "Please describe your emergency",
                    isProcessing = false
                )
            }
            return
        }

        viewModelScope.launch {
            try {
                // Update service mode indicator
                val usingOnline = shouldUseOnlineService()
                
                // Clear previous state and show processing
                _state.update {
                    it.copy(
                        isProcessing = true,
                        error = null,
                        response = null,
                        nearbyFacilities = emptyList(),
                        mapState = it.mapState?.copy(facilities = emptyList(), route = null),
                        isUsingOnlineService = usingOnline
                    )
                }

                Timber.d("Processing emergency query: $query")

                // First, check for immediate emergency keywords
                val immediateResponse = getImmediateEmergencyResponse(query)
                if (immediateResponse != null) {
                    _state.update {
                        it.copy(
                            response = immediateResponse,
                            isProcessing = false
                        )
                    }
                    return@launch
                }

                // Try to determine query type and handle accordingly
                when {
                    isHospitalQuery(query) -> {
                        handleHospitalQuery(location)
                    }
                    isContactQuery(query) -> {
                        handleContactQuery(query)
                    }
                    isFirstAidQuery(query) -> {
                        handleFirstAidQuery(query)
                    }
                    else -> {
                        // Try AI generation with service selection
                        val response = generateEmergencyResponseWithService(query)
                        _state.update {
                            it.copy(
                                response = response,
                                isProcessing = false
                            )
                        }
                    }
                }

            } catch (e: Exception) {
                Timber.e(e, "Error processing emergency query")
                _state.update {
                    it.copy(
                        isProcessing = false,
                        error = "Error: ${e.message}",
                        response = errorFallback
                    )
                }
            }
        }
    }

    private fun getImmediateEmergencyResponse(query: String): String? {
        val lowerQuery = query.lowercase()

        // Check each emergency keyword
        for ((keyword, response) in EMERGENCY_RESPONSES) {
            if (lowerQuery.contains(keyword)) {
                return response
            }
        }

        // Special multi-word checks
        return when {
            lowerQuery.contains("chest") && lowerQuery.contains("pain") -> EMERGENCY_RESPONSES["chest pain"]
            lowerQuery.contains("can't breathe") || lowerQuery.contains("cannot breathe") -> EMERGENCY_RESPONSES["chest pain"]
            lowerQuery.contains("heart") && (lowerQuery.contains("attack") || lowerQuery.contains("pain")) -> EMERGENCY_RESPONSES["chest pain"]
            lowerQuery.contains("broke") || lowerQuery.contains("fracture") -> EMERGENCY_RESPONSES["broken"]
            lowerQuery.contains("passed out") || lowerQuery.contains("unconscious") -> EMERGENCY_RESPONSES["faint"]
            lowerQuery.contains("fell") && (lowerQuery.contains("bike") || lowerQuery.contains("bicycle")) -> EMERGENCY_RESPONSES["knee"]
            else -> null
        }
    }

    private fun isHospitalQuery(query: String): Boolean {
        val hospitalKeywords = listOf("hospital", "clinic", "medical center", "emergency room", "doctor", "nearest")
        return hospitalKeywords.any { query.contains(it, ignoreCase = true) }
    }

    private fun isContactQuery(query: String): Boolean {
        val contactKeywords = listOf("call", "number", "contact", "phone", "emergency number")
        return contactKeywords.any { query.contains(it, ignoreCase = true) }
    }

    private fun isFirstAidQuery(query: String): Boolean {
        val firstAidKeywords = listOf(
            "first aid", "hurt", "pain", "injury", "bleeding", "burn", "cut",
            "fall", "fell", "broken", "fracture", "wound", "bruise", "accident",
            "sprain", "bite", "sting", "poison", "choke", "faint", "cpr"
        )
        return firstAidKeywords.any { query.contains(it, ignoreCase = true) }
    }

    private suspend fun handleHospitalQuery(location: Location?) {
        if (location == null) {
            _state.update {
                it.copy(
                    isProcessing = false,
                    response = "Location needed to find nearest hospitals. Please enable GPS and try again.",
                    error = "Location services required"
                )
            }
            return
        }

        // Use online or offline hospital search based on service availability
        if (shouldUseOnlineService()) {
            searchHospitalsOnline(location)
        } else {
            showNearestHospitalsDirectly()
        }
    }

    private suspend fun handleContactQuery(query: String) {
        val contacts = emergencyContacts.getLocalEmergencyContacts()

        // Determine which service based on query
        val service = when {
            query.contains("police", ignoreCase = true) -> "police"
            query.contains("fire", ignoreCase = true) -> "fire"
            query.contains("ambulance", ignoreCase = true) || query.contains("medical", ignoreCase = true) -> "ambulance"
            else -> "emergency"
        }

        val relevantContact = contacts.find { it.service == service } ?: contacts.first()

        displayEmergencyContact(relevantContact)

        // Also show all contacts
        _state.update { currentState ->
            currentState.copy(
                emergencyContacts = contacts,
                isProcessing = false
            )
        }
    }

    private suspend fun handleFirstAidQuery(query: String) {
        // First check predefined responses
        val immediateResponse = getImmediateEmergencyResponse(query)
        if (immediateResponse != null) {
            _state.update {
                it.copy(
                    response = immediateResponse,
                    isProcessing = false
                )
            }
            return
        }

        // Try AI generation with service selection
        val response = withTimeoutOrNull(10_000) { // 10 second timeout
            generateFirstAidResponseWithService(query)
        } ?: genericFallback

        _state.update {
            it.copy(
                response = response,
                isProcessing = false
            )
        }
    }

    private suspend fun generateFirstAidResponseWithService(query: String): String {
        return if (shouldUseOnlineService()) {
            try {
                initializeApiServiceIfNeeded()
                generateFirstAidResponseOnline(query)
            } catch (e: Exception) {
                Timber.w(e, "Online first aid response failed, falling back to offline")
                generateFirstAidResponseOptimized(query)
            }
        } else {
            generateFirstAidResponseOptimized(query)
        }
    }
    
    private suspend fun generateFirstAidResponseOnline(query: String): String {
        val prompt = """You are a qualified first aid instructor. For this situation: "$query"
        
Provide step-by-step first aid instructions:
        1. Assess the situation and ensure safety
        2. Specific first aid steps
        3. When to seek professional medical help
        
Be clear, accurate, and prioritize safety. If unsure, recommend calling emergency services."""

        val response = geminiApiService.generateTextComplete(prompt, "crisis")
        return if (response.isBlank()) genericFallback else response
    }
    
    private suspend fun generateFirstAidResponseOptimized(query: String): String {
        try {
            // Ensure model is ready
            if (!ensureModelInitialized()) {
                return genericFallback
            }

            // Use a very simple, token-efficient prompt
            val prompt = """First aid for: $query

Provide 5 brief steps:"""

            val response = unifiedGemmaService.generateTextAsync(
                prompt = prompt,
                config = UnifiedGemmaService.GenerationConfig(
                    maxTokens = 150, // Very conservative
                    temperature = 0.5f // Lower temperature for consistency
                )
            )

            // If response is too short or empty, use fallback
            return if (response.isBlank() || response.length < 30) {
                genericFallback
            } else {
                // Format the response nicely
                """
🏥 First Aid Guidance

$response

⚠️ For serious injuries, call 193 immediately!
                """.trimIndent()
            }

        } catch (e: Exception) {
            Timber.e(e, "Error generating first aid response")
            return genericFallback
        }
    }

    private suspend fun generateEmergencyResponseWithService(query: String): String {
        return if (shouldUseOnlineService()) {
            try {
                initializeApiServiceIfNeeded()
                generateEmergencyResponseOnline(query)
            } catch (e: Exception) {
                Timber.w(e, "Online emergency response failed, falling back to offline")
                generateEmergencyResponseWithFallback(query)
            }
        } else {
            generateEmergencyResponseWithFallback(query)
        }
    }
    
    private suspend fun generateEmergencyResponseOnline(query: String): String {
        return try {
            withTimeoutOrNull(8_000) {
                val prompt = """You are an emergency response assistant. For this emergency situation: "$query"
                
Provide a clear, actionable response with:
                1. Immediate safety steps (if applicable)
                2. When to call emergency services
                3. Next actions to take
                
Keep response brief but comprehensive. Focus on safety."""

                val response = geminiApiService.generateTextComplete(prompt, "crisis")
                if (response.isBlank()) noResponseFallback else response
            } ?: timeoutFallback
        } catch (e: Exception) {
            Timber.e(e, "Error generating online emergency response")
            throw e
        }
    }

    private suspend fun generateEmergencyResponseWithFallback(query: String): String {
        return try {
            // Try with very short timeout
            withTimeoutOrNull(8_000) {
                if (!ensureModelInitialized()) {
                    return@withTimeoutOrNull errorFallback
                }

                val prompt = "Emergency help for: $query\n\nProvide 3 brief action steps:"

                val response = unifiedGemmaService.generateTextAsync(
                    prompt = prompt,
                    config = UnifiedGemmaService.GenerationConfig(
                        maxTokens = 100,
                        temperature = 0.5f
                    )
                )

                if (response.isBlank()) noResponseFallback else response
            } ?: timeoutFallback

        } catch (e: Exception) {
            Timber.e(e, "Error generating emergency response")
            errorFallback
        }
    }

    private fun loadPromptTemplate(@RawRes resId: Int): String =
        appContext.resources.openRawResource(resId)
            .bufferedReader()
            .use { it.readText().trimIndent() }

    fun buildContext(
        location: String = "Accra, Ghana",
        time: String,
        emergencyNumbers: String
    ): String =
        contextTemplate
            .replace("\$location", location)
            .replace("\$time", time)
            .replace("\$emergencyNumbers", emergencyNumbers)

    private fun displayEmergencyContact(contact: EmergencyContactInfo) {
        _state.update { currentState ->
            currentState.copy(
                emergencyContacts = listOf(contact),
                isProcessing = false
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

            Timber.d("User location: ${location.latitude}, ${location.longitude}")

            // Try different search radii
            val searchRadii = listOf(10.0, 25.0, 50.0, 100.0, 200.0, 500.0)
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
                    showOnOfflineMap(hospitals.take(10))

                    if (searchRadius > 50) {
                        _state.update { currentState ->
                            currentState.copy(
                                response = "Note: The nearest hospitals are ${searchRadius.toInt()}km away. In case of emergency, call 112 or 193 immediately for ambulance services."
                            )
                        }
                    }
                }
                else -> {
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
    
    private suspend fun searchHospitalsOnline(location: Location) {
        _state.update { it.copy(isProcessing = true, error = null) }
        
        try {
            // Get API key for Google Places
            val apiKey = settingsRepository.apiKeyFlow.first()
            if (apiKey.isBlank()) {
                Timber.w("No API key available for Places search, falling back to offline")
                showNearestHospitalsDirectly()
                return
            }
            
            // Find nearest capital for context
            val nearestCapital = CapitalCities.findNearestCapital(location.latitude, location.longitude)
            val distanceToCapital = nearestCapital.distanceFrom(location.latitude, location.longitude)
            
            Timber.d("User location: ${location.latitude}, ${location.longitude}")
            Timber.d("Nearest capital: ${nearestCapital.capital}, ${nearestCapital.country} (${distanceToCapital.toInt()}km away)")
            
            // Use Google Places API to find real hospitals
            val searchRadius = if (distanceToCapital <= 50) 25000 else 50000 // 25km or 50km radius
            
            val placesResult = withTimeoutOrNull(8_000) {
                googlePlacesService.searchNearbyHospitals(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    radiusMeters = searchRadius,
                    apiKey = apiKey
                )
            }
            
            if (placesResult != null && placesResult.success && placesResult.hospitals.isNotEmpty()) {
                // Success with Google Places API
                val hospitals = placesResult.hospitals.take(15) // Limit to top 15
                
                showOnOfflineMap(hospitals)
                _state.update {
                    it.copy(
                        nearbyFacilities = hospitals,
                        response = "Found ${hospitals.size} medical facilities using live data (${searchRadius/1000}km search radius)\n\n" +
                                "Nearest capital: ${nearestCapital.capital}, ${nearestCapital.country} (${distanceToCapital.toInt()}km away)\n\n" +
                                "ℹ️ Results from Google Places - real-time hospital data",
                        isProcessing = false
                    )
                }
                
                Timber.d("Successfully found ${hospitals.size} hospitals via Google Places API")
                
            } else if (placesResult != null && !placesResult.success) {
                // Places API failed, try AI fallback for remote areas
                Timber.w("Places API failed: ${placesResult.errorMessage}, trying AI guidance")
                
                if (distanceToCapital > 100) {
                    // Use AI for remote area guidance
                    val aiGuidance = withTimeoutOrNull(6_000) {
                        geminiApiService.generateTextComplete(
                            """The user is in a remote area ${distanceToCapital.toInt()}km from ${nearestCapital.capital}, ${nearestCapital.country}.
                            
Provide emergency medical guidance:
                            1. How to find nearest medical facilities in rural ${nearestCapital.country}
                            2. Emergency contact numbers for ${nearestCapital.country}
                            3. When to seek evacuation to ${nearestCapital.capital}
                            
Keep response brief and practical.""",
                            "crisis"
                        )
                    }
                    
                    if (aiGuidance != null && aiGuidance.isNotBlank()) {
                        _state.update {
                            it.copy(
                                response = "Medical guidance for remote area near ${nearestCapital.country}:\n\n$aiGuidance",
                                isProcessing = false
                            )
                        }
                        return
                    }
                }
                
                // Final fallback to offline
                showNearestHospitalsDirectly()
                
            } else {
                // Timeout or no results
                Timber.w("Places API search timed out or returned no results, falling back to offline")
                showNearestHospitalsDirectly()
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Online hospital search failed, falling back to offline")
            showNearestHospitalsDirectly()
        }
    }
    
    // Removed parseCapitalHospitalResponse - now using real Google Places API data

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
                action = {
                    clearStateForNewAction()
                    displayEmergencyContact(
                        EmergencyContactInfo(
                            service = "emergency",
                            primaryNumber = "112",
                            secondaryNumber = "193",
                            description = "General Emergency",
                            smsNumber = null
                        )
                    )
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
                        
                        val location = getFreshLocation(appContext)
                        if (location != null) {
                            if (shouldUseOnlineService()) {
                                searchHospitalsOnline(location)
                            } else {
                                showNearestHospitalsDirectly()
                            }
                        } else {
                            _state.update {
                                it.copy(
                                    error = "Getting location... Please try again in a moment.",
                                    isProcessing = false
                                )
                            }
                        }
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
                    emptyList()
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

    private fun showAccidentReportingGuide() {
        viewModelScope.launch {
            _state.update { it.copy(isProcessing = true, error = null) }
            _state.update {
                it.copy(
                    response = accidentGuide,
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