package com.mygemma3n.aiapp.shared_utilities

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat



/**
 * Extension function to check if location permission is granted
 */
fun Context.hasLocationPermission(): Boolean {
    return ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
}

/**
 * Get a user-friendly location status message
 */
fun Context.getLocationStatusMessage(): String {
    return when {
        !hasLocationPermission() -> "Location permission not granted. Please enable in settings."
        !isLocationEnabled() -> "Location services are disabled. Please turn on GPS."
        else -> "Location services ready"
    }
}