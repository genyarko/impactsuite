package com.mygemma3n.aiapp.feature.crisis

import android.location.Location
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.mygemma3n.aiapp.remote.Hospital
import com.mygemma3n.aiapp.shared_utilities.CrisisFunctionCalling
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

// Crisis Handbook State
data class CrisisHandbookState(
    val isProcessing: Boolean = false,
    val response: String? = null,
    val emergencyContacts: List<EmergencyContactInfo> = emptyList(),
    val nearbyFacilities: List<Hospital> = emptyList(),
    val mapState: OfflineMapState? = null,
    val error: String? = null,
    val isUsingOnlineService: Boolean = false
)

// Emergency Contact Info
data class EmergencyContactInfo(
    val service: String,
    val primaryNumber: String,
    val secondaryNumber: String? = null,
    val smsNumber: String? = null,
    val description: String
)

// Offline Map State
data class OfflineMapState(
    val userLocation: Location?,
    val facilities: List<MapMarker>,
    val route: List<Location>? = null,
    val estimatedTime: Int? = null // minutes
)

data class MapMarker(
    val id: String,
    val location: Location,
    val title: String,
    val type: MarkerType,
    val description: String? = null
)

enum class MarkerType {
    HOSPITAL,
    POLICE_STATION,
    FIRE_STATION,
    SHELTER,
    USER_LOCATION
}

// Offline Map Service
@Singleton
class OfflineMapService @Inject constructor() {

    fun calculateRoute(from: Location, to: Location): List<Location> {
        // Simplified route calculation
        // In production, use offline routing library like GraphHopper
        val route = mutableListOf<Location>()

        // Add start point
        route.add(from)

        // Add intermediate points (simplified linear interpolation)
        val steps = 10
        for (i in 1 until steps) {
            val ratio = i.toFloat() / steps
            val lat = from.latitude + (to.latitude - from.latitude) * ratio
            val lon = from.longitude + (to.longitude - from.longitude) * ratio

            route.add(Location("interpolated").apply {
                latitude = lat
                longitude = lon
            })
        }

        // Add end point
        route.add(to)

        return route
    }

    fun estimateTravelTime(distance: Double, mode: TravelMode = TravelMode.DRIVING): Int {
        // Estimate time in minutes based on distance in km
        val speedKmh = when (mode) {
            TravelMode.WALKING -> 5.0
            TravelMode.DRIVING -> 30.0 // City driving average
            TravelMode.EMERGENCY -> 50.0 // Emergency vehicle
        }

        return ((distance / speedKmh) * 60).toInt()
    }

    enum class TravelMode {
        WALKING,
        DRIVING,
        EMERGENCY
    }
}

// Emergency Contacts Repository
@Singleton
class EmergencyContactsRepository @Inject constructor() {

    fun getLocalEmergencyContacts(): List<EmergencyContactInfo> {
        // Ghana emergency contacts
        return listOf(
            EmergencyContactInfo(
                service = "police",
                primaryNumber = "191",
                secondaryNumber = "18555",
                description = "Ghana Police Service"
            ),
            EmergencyContactInfo(
                service = "fire",
                primaryNumber = "192",
                secondaryNumber = "0302772446",
                description = "Ghana National Fire Service"
            ),
            EmergencyContactInfo(
                service = "ambulance",
                primaryNumber = "193",
                secondaryNumber = "0302773906",
                description = "National Ambulance Service"
            ),
            EmergencyContactInfo(
                service = "emergency",
                primaryNumber = "112",
                description = "National Emergency Number"
            )
        )
    }

    fun getSpecializedContacts(): List<EmergencyContactInfo> {
        return listOf(
            EmergencyContactInfo(
                service = "domestic_violence",
                primaryNumber = "0551000900",
                smsNumber = "0551000900",
                description = "Domestic Violence Hotline"
            ),
            EmergencyContactInfo(
                service = "suicide_prevention",
                primaryNumber = "0244846701",
                description = "Mental Health Helpline"
            ),
            EmergencyContactInfo(
                service = "child_abuse",
                primaryNumber = "0800111222",
                description = "Child Abuse Hotline"
            )
        )
    }
}

// Fixed Function Call Result types
sealed class FunctionCallResult {
    data class EmergencyContact(val contact: EmergencyContactInfo) : FunctionCallResult()
    data class NearestFacility(val facilities: List<Hospital>) : FunctionCallResult()
    data class FirstAidInstructions(val steps: List<String>) : FunctionCallResult()
    data class GeneralResponse(val response: String) : FunctionCallResult()
}

// Extension for CrisisFunctionCalling
suspend fun CrisisFunctionCalling.processQuery(
    query: String,
    location: Location?
): FunctionCallResult {
    val result = this.processQuery(query)

    return when (result) {
        is com.mygemma3n.aiapp.shared_utilities.FunctionCallResult.Success -> {
            // Parse the result and determine the type
            when {
                result.result.contains("Emergency Contact:", ignoreCase = true) -> {
                    val contact = parseEmergencyContact(result.result)
                    FunctionCallResult.EmergencyContact(contact)
                }
                result.result.contains("Nearest", ignoreCase = true) &&
                        result.result.contains("hospital", ignoreCase = true) -> {
                    val facilities = parseFacilities(result.result)
                    FunctionCallResult.NearestFacility(facilities)
                }
                result.result.contains("First Aid", ignoreCase = true) -> {
                    val steps = parseFirstAidSteps(result.result)
                    FunctionCallResult.FirstAidInstructions(steps)
                }
                else -> FunctionCallResult.GeneralResponse(result.result)
            }
        }
        is com.mygemma3n.aiapp.shared_utilities.FunctionCallResult.Error -> {
            FunctionCallResult.GeneralResponse("Error: ${result.message}")
        }
        is com.mygemma3n.aiapp.shared_utilities.FunctionCallResult.NoFunctionNeeded -> {
            FunctionCallResult.GeneralResponse(result.response)
        }
    }
}

private fun parseEmergencyContact(result: String): EmergencyContactInfo {
    // Parse contact info from result string
    val lines = result.lines()
    var primary = ""
    var secondary: String? = null
    var sms: String? = null
    var service = ""

    lines.forEach { line ->
        when {
            line.contains("Primary:", ignoreCase = true) ->
                primary = line.substringAfter("Primary:", "").substringAfter(":", "").trim()
            line.contains("Secondary:", ignoreCase = true) ->
                secondary = line.substringAfter("Secondary:", "").substringAfter(":", "").trim()
                    .takeIf { it.isNotEmpty() && !it.equals("N/A", ignoreCase = true) }
            line.contains("SMS:", ignoreCase = true) ->
                sms = line.substringAfter("SMS:", "").substringAfter(":", "").trim()
                    .takeIf { it.isNotEmpty() && !it.equals("N/A", ignoreCase = true) }
            line.contains("Emergency Contact:", ignoreCase = true) ->
                service = line.substringAfter("Emergency Contact:", "").substringAfter(":", "").trim()
        }
    }

    // If service wasn't found in the expected format, try to extract it from the content
    if (service.isEmpty() && primary.isNotEmpty()) {
        service = when {
            result.contains("police", ignoreCase = true) -> "Police"
            result.contains("fire", ignoreCase = true) -> "Fire"
            result.contains("ambulance", ignoreCase = true) || result.contains("medical", ignoreCase = true) -> "Medical"
            else -> "Emergency"
        }
    }

    return EmergencyContactInfo(
        service = service.ifEmpty { "emergency" }.lowercase(),
        primaryNumber = primary.ifEmpty { "112" }, // Default to general emergency
        secondaryNumber = secondary,
        smsNumber = sms,
        description = service
    )
}

private fun parseFacilities(result: String): List<Hospital> {
    // This is a simplified parser
    // In production, the function calling would return structured data
    // For now, return an empty list as the actual hospitals would come from the database
    return emptyList()
}

private fun parseFirstAidSteps(result: String): List<String> {
    return result.lines()
        .filter { line ->
            line.trim().matches(Regex("^\\d+\\..*")) ||
                    line.trim().matches(Regex("^-\\s+.*")) ||
                    line.trim().matches(Regex("^•\\s+.*"))
        }
        .map { line ->
            line.trim()
                .removePrefix("-")
                .removePrefix("•")
                .replaceFirst(Regex("^\\d+\\."), "")
                .trim()
        }
        .filter { it.isNotEmpty() }
}