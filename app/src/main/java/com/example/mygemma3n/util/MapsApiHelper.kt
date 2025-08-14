package com.example.mygemma3n.util

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import timber.log.Timber

/**
 * Helper for Google Maps API integration
 * Handles cases where API key might not be configured in manifest
 */
object MapsApiHelper {
    
    /**
     * Check if Google Maps API key is properly configured in AndroidManifest.xml
     */
    fun isApiKeyAvailable(context: Context): Boolean {
        return try {
            val applicationInfo: ApplicationInfo = context.packageManager
                .getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
            
            val apiKey = ""
            
            val isValid = apiKey.isNotEmpty()
            
            if (!isValid) {
                Timber.w("Google Maps API key not properly configured in manifest: '$apiKey'")
            } else {
                Timber.d("Google Maps API key found in manifest")
            }
            
            isValid
            
        } catch (e: Exception) {
            Timber.e(e, "Error checking Maps API key availability")
            false
        }
    }
    
    /**
     * Get the configured API key from manifest (if available)
     */
    fun getApiKeyFromManifest(context: Context): String? {
        return try {
            val applicationInfo: ApplicationInfo = context.packageManager
                .getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
            
            applicationInfo.metaData?.getString("com.google.android.geo.API_KEY")
            
        } catch (e: Exception) {
            Timber.e(e, "Error getting Maps API key from manifest")
            null
        }
    }
}