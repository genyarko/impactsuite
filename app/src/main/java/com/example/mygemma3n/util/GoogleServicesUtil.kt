package com.example.mygemma3n.util

import android.content.Context
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import timber.log.Timber

/**
 * Utility class to check Google Play Services availability
 * Prevents crashes on devices without Google Services (e.g., Huawei phones)
 */
object GoogleServicesUtil {
    
    /**
     * Check if Google Play Services is available on this device
     * @param context Application context
     * @return true if Google Play Services is available and up to date
     */
    fun isGooglePlayServicesAvailable(context: Context): Boolean {
        return try {
            val googleApiAvailability = GoogleApiAvailability.getInstance()
            val resultCode = googleApiAvailability.isGooglePlayServicesAvailable(context)
            
            when (resultCode) {
                ConnectionResult.SUCCESS -> {
                    Timber.d("Google Play Services is available")
                    true
                }
                ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED -> {
                    Timber.w("Google Play Services needs update")
                    false
                }
                ConnectionResult.SERVICE_DISABLED -> {
                    Timber.w("Google Play Services is disabled")
                    false
                }
                ConnectionResult.SERVICE_MISSING -> {
                    Timber.w("Google Play Services is not installed (likely Huawei or other non-GMS device)")
                    false
                }
                else -> {
                    Timber.w("Google Play Services not available, result code: $resultCode")
                    false
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error checking Google Play Services availability")
            false
        }
    }
    
    /**
     * Execute a block of code only if Google Play Services is available
     * @param context Application context
     * @param block Code block to execute if GMS is available
     * @param onUnavailable Optional callback when GMS is not available
     */
    inline fun <T> withGoogleServices(
        context: Context,
        block: () -> T,
        noinline onUnavailable: (() -> Unit)? = null
    ): T? {
        return if (isGooglePlayServicesAvailable(context)) {
            try {
                block()
            } catch (e: Exception) {
                Timber.e(e, "Error executing Google Services dependent code")
                onUnavailable?.invoke()
                null
            }
        } else {
            Timber.d("Skipping Google Services dependent operation - GMS not available")
            onUnavailable?.invoke()
            null
        }
    }
    
    /**
     * Execute a suspending block of code only if Google Play Services is available
     */
    suspend inline fun <T> withGoogleServicesSuspend(
        context: Context,
        crossinline block: suspend () -> T,
        noinline onUnavailable: (suspend () -> Unit)? = null
    ): T? {
        return if (isGooglePlayServicesAvailable(context)) {
            try {
                block()
            } catch (e: Exception) {
                Timber.e(e, "Error executing Google Services dependent code")
                onUnavailable?.invoke()
                null
            }
        } else {
            Timber.d("Skipping Google Services dependent operation - GMS not available")
            onUnavailable?.invoke()
            null
        }
    }
}