package com.example.mygemma3n.feature.plant

import android.graphics.BitmapFactory
import android.util.Log
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.mygemma3n.shared_ui.CameraPreview
import com.example.mygemma3n.feature.plant.PlantScannerViewModel
import com.google.accompanist.permissions.*
import java.io.File

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PlantScannerScreen(
    viewModel: PlantScannerViewModel = hiltViewModel()
) {
    // Observe scan state from ViewModel
    val scanState by viewModel.scanState.collectAsState()

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val camPerm = rememberPermissionState(android.Manifest.permission.CAMERA)

    // Controller with IMAGE_CAPTURE enabled
    val controller = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_CAPTURE)
            setImageCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
        }
    }

    // Bind once the user grants permission
    LaunchedEffect(camPerm.status.isGranted) {
        if (camPerm.status.isGranted) controller.bindToLifecycle(lifecycleOwner)
    }

    Column(Modifier.fillMaxSize()) {
        Text("Plant Scanner", Modifier.padding(16.dp), fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        if (camPerm.status.isGranted) {
            Box(Modifier.weight(1f)) {
                CameraPreview(controller = controller)
            }

            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                onClick = {
                    val photoFile = File(context.cacheDir, "${System.currentTimeMillis()}.jpg")
                    val outputOpts = ImageCapture.OutputFileOptions.Builder(photoFile).build()
                    val executor = ContextCompat.getMainExecutor(context)

                    controller.takePicture(
                        outputOpts,
                        executor,
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(res: ImageCapture.OutputFileResults) {
                                viewModel.analyzeImage(
                                    BitmapFactory.decodeFile(photoFile.absolutePath)
                                )
                                // optional clean-up: photoFile.delete()
                            }

                            override fun onError(exc: ImageCaptureException) {
                                Log.e("PlantScanner", "Capture failed", exc)
                            }
                        }
                    )
                }
            ) {
                Text("Start Scan")
            }

            Spacer(Modifier.height(16.dp))

            // Display scan results
            when {
                scanState.isAnalyzing -> {
                    Text("Analyzing image...", Modifier.padding(16.dp))
                }

                scanState.error != null -> {
                    Text(
                        text = "Error: ${scanState.error}",
                        color = Color.Red,
                        modifier = Modifier.padding(16.dp)
                    )
                }

                scanState.currentAnalysis != null -> {
                    val a = scanState.currentAnalysis!!
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text("Species: ${a.species} (confidence ${"%.0f".format(a.confidence * 100)}%)")
                        if (a.disease?.isNotBlank() ?: false) {
                            Text("Disease: ${a.disease} (severity: ${a.severity})")
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("Recommendations:", fontWeight = FontWeight.Bold)
                        a.recommendations.forEach { rec ->
                            Text("• $rec")
                        }
                        a.additionalInfo?.let { info ->
                            Spacer(Modifier.height(8.dp))
                            Text("Extra care: ${info.wateringNeeds}, ${info.sunlightNeeds}")
                        }
                    }
                }
            }
        } else {
            val rationale = if (camPerm.status.shouldShowRationale)
                "Camera permission is required to scan plants." else "Please grant camera permission."
            Text(
                rationale,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            )
            Button(
                onClick = { camPerm.launchPermissionRequest() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text("Grant Camera")
            }
        }
    }
}

