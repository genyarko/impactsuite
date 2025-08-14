package com.mygemma3n.aiapp.shared_utilities     // or com.mygemma3n.aiapp.util

import android.content.Context
import android.location.LocationManager

fun Context.isLocationEnabled(): Boolean =
    (getSystemService(Context.LOCATION_SERVICE) as LocationManager)
        .isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            (getSystemService(Context.LOCATION_SERVICE) as LocationManager)
                .isProviderEnabled(LocationManager.NETWORK_PROVIDER)