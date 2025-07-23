package com.example.mygemma3n.shared_utilities     // or com.example.mygemma3n.util

import android.content.Context
import android.location.LocationManager

fun Context.isLocationEnabled(): Boolean =
    (getSystemService(Context.LOCATION_SERVICE) as LocationManager)
        .isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            (getSystemService(Context.LOCATION_SERVICE) as LocationManager)
                .isProviderEnabled(LocationManager.NETWORK_PROVIDER)