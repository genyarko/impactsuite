package com.example.mygemma3n.feature.plant

import android.graphics.Bitmap
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.accompanist.permissions.*
import com.example.mygemma3n.shared_ui.CameraPreview
import java.io.File
import java.util.*

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PlantScannerScreen(onScanClick: (Bitmap) -> Unit) {

    val context         = LocalContext.current
    val lifecycleOwner  = LocalLifecycleOwner.current
    val camPerm         = rememberPermissionState(android.Manifest.permission.CAMERA)

    // Controller with IMAGE_CAPTURE enabled
    val controller = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_CAPTURE)      // binds Preview + ImageCapture
            setImageCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
        }
    }

    /* Bind once the user grants permission */
    LaunchedEffect(camPerm.status.isGranted) {
        if (camPerm.status.isGranted) controller.bindToLifecycle(lifecycleOwner)   // single arg ✔
    }

    Column(Modifier.fillMaxSize()) {

        Text("Plant Scanner", Modifier.padding(16.dp))
        Spacer(Modifier.height(16.dp))

        if (camPerm.status.isGranted) {

            Box(Modifier.weight(1f)) { CameraPreview(controller = controller) }

            /* -- Capture via controller ------------------------------------------------------- */
            Button(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                onClick  = {
                    val photoFile   = File(context.cacheDir, "${System.currentTimeMillis()}.jpg")
                    val outputOpts  = ImageCapture.OutputFileOptions.Builder(photoFile).build()
                    val exec        = ContextCompat.getMainExecutor(context)

                    controller.takePicture(                          // 📸 use controller here
                        outputOpts,
                        exec,
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(res: ImageCapture.OutputFileResults) {
                                onScanClick(BitmapFactory.decodeFile(photoFile.absolutePath))
                                // photoFile.delete()   // optional clean-up
                            }
                            override fun onError(exc: ImageCaptureException) {
                                Log.e("PlantScanner","Capture failed", exc)
                            }
                        }
                    )
                }
            ) { Text("Start Scan") }

        } else {
            val rationale = if (camPerm.status.shouldShowRationale)
                "Camera permission is required to scan plants." else "Please grant camera permission."
            Text(rationale, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(16.dp))
            Button(
                onClick = { camPerm.launchPermissionRequest() },
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) { Text("Grant Camera") }
        }
    }
}

