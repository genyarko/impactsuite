package com.example.mygemma3n.feature.plant


import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.mygemma3n.shared_ui.CameraPreview
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PlantScannerScreen(
    onScanClick: () -> Unit
) {
    val cameraPermissionState = rememberPermissionState(android.Manifest.permission.CAMERA)

    Column(Modifier.fillMaxSize()) {
        Text("Plant Scanner", Modifier.padding(16.dp))
        Spacer(Modifier.height(16.dp))

        if (cameraPermissionState.status.isGranted) {
            Box(Modifier.weight(1f)) {
                CameraPreview()
            }
            Button(onClick = onScanClick, Modifier.fillMaxWidth().padding(16.dp)) {
                Text("Start Scan")
            }
        }
        else {
            Text(
                text = if (cameraPermissionState.status.shouldShowRationale)
                    "Camera needed to scan plant disease."
                else
                    "Please grant camera permission",
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            )
            Button(
                onClick = { cameraPermissionState.launchPermissionRequest() },
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Text("Grant Camera")
            }
        }
    }
}