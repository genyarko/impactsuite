package com.example.mygemma3n.feature.plant

import android.graphics.BitmapFactory
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mygemma3n.shared_ui.CameraPreview
import com.google.accompanist.permissions.*
import java.io.File
import kotlin.math.roundToInt

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PlantScannerScreen(
    viewModel: PlantScannerViewModel = hiltViewModel()
) {
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val camPerm = rememberPermissionState(android.Manifest.permission.CAMERA)

    val controller = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_CAPTURE)
            setImageCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
        }
    }

    LaunchedEffect(camPerm.status.isGranted) {
        if (camPerm.status.isGranted) controller.bindToLifecycle(lifecycleOwner)
    }

    Column(Modifier.fillMaxSize()) {
        Text("Image Scanner", Modifier.padding(16.dp), fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        if (camPerm.status.isGranted) {
            Box(Modifier.weight(1f)) { CameraPreview(controller) }

            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                onClick = {
                    val photo = File(context.cacheDir, "${System.currentTimeMillis()}.jpg")
                    val opts  = ImageCapture.OutputFileOptions.Builder(photo).build()
                    val exec  = ContextCompat.getMainExecutor(context)

                    controller.takePicture(opts, exec,
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(r: ImageCapture.OutputFileResults) {
                                viewModel.analyzeImage(
                                    BitmapFactory.decodeFile(photo.absolutePath)
                                )
                            }
                            override fun onError(e: ImageCaptureException) = e.printStackTrace()
                        })
                }
            ) { Text("Start Scan") }

            Spacer(Modifier.height(16.dp))

            when {
                scanState.isAnalyzing -> Text("Analyzing image...", Modifier.padding(16.dp))
                scanState.error != null -> Text(
                    "Error: ${scanState.error}",
                    color = Color.Red,
                    modifier = Modifier.padding(16.dp)
                )
                scanState.currentAnalysis != null -> {
                    val a = scanState.currentAnalysis!!
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        // GENERAL LABEL (always)
                        Text(
                            text = "Label: ${a.label} (${(a.confidence * 100).roundToInt()}%)",
                            fontWeight = FontWeight.SemiBold,
                            color = Color.Red
                        )

                        // PLANT‑SPECIFIC DETAILS (optional)
                        a.plantSpecies?.let { species ->
                            Spacer(Modifier.height(4.dp))
                            Text("Plant species: $species", color = Color.Red)

                            a.disease?.let { dis ->
                                Text("Disease: $dis (severity: ${a.severity ?: "N/A"})", color = Color.Red)
                            }

                            if (a.recommendations.isNotEmpty()) {
                                Spacer(Modifier.height(4.dp))
                                Text("Recommendations:", fontWeight = FontWeight.Bold, color = Color.Red)
                                a.recommendations.forEach { Text("• $it", color = Color.Red) }
                            }

                            a.additionalInfo?.let { info ->
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Extra care: ${info.wateringNeeds}, ${info.sunlightNeeds}",
                                    color = Color.Red
                                )
                            }
                        }
                    }
                }
            }
        } else {
            val rationale = if (camPerm.status.shouldShowRationale)
                "Camera permission is required to scan images."
            else "Please grant camera permission."

            Text(
                rationale,
                textAlign = TextAlign.Center,
                modifier  = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            )
            Button(
                onClick  = { camPerm.launchPermissionRequest() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) { Text("Grant Camera") }
        }
    }
}

