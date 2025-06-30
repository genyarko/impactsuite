package com.example.mygemma3n.feature.plant


import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import android.graphics.Bitmap
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.view.LifecycleCameraController
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import com.example.mygemma3n.shared_utilities.toBitmap
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
    onScanClick: (android.graphics.Bitmap) -> Unit
) {
    val cameraPermissionState = rememberPermissionState(android.Manifest.permission.CAMERA)
    val context = androidx.compose.ui.platform.LocalContext.current
    val cameraController = remember { LifecycleCameraController(context) }

    Column(Modifier.fillMaxSize()) {
        Text("Plant Scanner", Modifier.padding(16.dp))
        Spacer(Modifier.height(16.dp))

        if (cameraPermissionState.status.isGranted) {
            Box(Modifier.weight(1f)) {
                CameraPreview(controller = cameraController)
            }
            Button(onClick = {
                val executor = androidx.core.content.ContextCompat.getMainExecutor(context)
                cameraController.takePicture(executor, object : androidx.camera.core.ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: androidx.camera.core.ImageProxy) {
                        val bitmap = image.toBitmap()
                        image.close()
                        onScanClick(bitmap)
                    }

                    override fun onError(exception: androidx.camera.core.ImageCaptureException) {
                    }
                })
            }, Modifier.fillMaxWidth().padding(16.dp)) {
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