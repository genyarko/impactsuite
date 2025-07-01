// Fixed MainActivity.kt
package com.example.mygemma3n

import android.Manifest
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.mygemma3n.ui.theme.Gemma3nTheme
import com.example.mygemma3n.feature.caption.LiveCaptionScreen
import com.example.mygemma3n.feature.quiz.QuizScreen
import com.example.mygemma3n.feature.plant.PlantScannerScreen
import com.example.mygemma3n.feature.plant.PlantScannerViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.play.core.assetpacks.AssetPackManagerFactory
import com.google.android.play.core.assetpacks.AssetPackState
import com.google.android.play.core.assetpacks.AssetPackStateUpdateListener
import com.google.android.play.core.assetpacks.AssetPackStates
import com.google.android.play.core.assetpacks.model.AssetPackStatus
import com.google.android.play.core.assetpacks.AssetPackManager
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import timber.log.Timber

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        setContent {
            Gemma3nTheme {
                Gemma3nApp()
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun Gemma3nApp() {
    val navController = rememberNavController()

    // Request necessary permissions
    val permissions = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    )

    LaunchedEffect(Unit) {
        permissions.launchMultiplePermissionRequest()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { paddingValues ->
        Gemma3nNavigation(
            navController = navController,
            modifier = Modifier.padding(paddingValues)
        )
    }
}

// Navigation
@Composable
fun Gemma3nNavigation(
    navController: androidx.navigation.NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = "home",
        modifier = modifier
    ) {
        composable("home") {
            HomeScreen(navController)
        }
        composable("live_caption") {
            LiveCaptionScreen()
        }
        composable("quiz_generator") {
            QuizScreen()
        }
        composable("cbt_coach") {
            CBTCoachScreen()
        }
        composable("plant_scanner") {
            val vm: PlantScannerViewModel = hiltViewModel()
            PlantScannerScreen(onScanClick = { bitmap ->
                vm.analyzeImage(bitmap)
            })
        }
        composable("crisis_handbook") {
            CrisisHandbookScreen()
        }
    }
}

// Home screen with asset pack download
@Composable
fun HomeScreen(navController: androidx.navigation.NavHostController) {
    var downloadProgress by remember { mutableStateOf(0f) }
    var isModelReady by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(Unit) {
        checkAndDownloadModel(context) { progress, ready ->
            downloadProgress = progress
            isModelReady = ready
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Gemma 3n Impact Suite",
            style = MaterialTheme.typography.headlineLarge
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Show download progress if model is downloading
        if (downloadProgress > 0 && downloadProgress < 100 && !isModelReady) {
            LinearProgressIndicator(
                progress = downloadProgress / 100f,
                modifier = Modifier.fillMaxWidth()
            )
            Text("Downloading model: ${downloadProgress.toInt()}%")
            Spacer(modifier = Modifier.height(16.dp))
        }

        Button(
            onClick = { navController.navigate("live_caption") },
            modifier = Modifier.fillMaxWidth(),
            enabled = isModelReady
        ) {
            Text("Live Caption & Translation")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = { navController.navigate("quiz_generator") },
            modifier = Modifier.fillMaxWidth(),
            enabled = isModelReady
        ) {
            Text("Offline Quiz Generator")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = { navController.navigate("cbt_coach") },
            modifier = Modifier.fillMaxWidth(),
            enabled = isModelReady
        ) {
            Text("Voice CBT Coach")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = { navController.navigate("plant_scanner") },
            modifier = Modifier.fillMaxWidth(),
            enabled = isModelReady
        ) {
            Text("Plant Disease Scanner")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = { navController.navigate("crisis_handbook") },
            modifier = Modifier.fillMaxWidth(),
            enabled = isModelReady
        ) {
            Text("Crisis Handbook")
        }

        if (!isModelReady) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Model needs to be downloaded before using features",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
fun CBTCoachScreen() {
    Text("CBT Coach Screen")
}

@Composable
fun CrisisHandbookScreen() {
    Text("Crisis Handbook Screen")
}

// Asset Pack Management
fun checkAndDownloadModel(
    context: Context,
    onProgressUpdate: (progress: Float, ready: Boolean) -> Unit
) {
    val assetPackManager = AssetPackManagerFactory.getInstance(context)
    val packName = "gemma3n_assetpack"

    CoroutineScope(Dispatchers.IO).launch {
        try {
            // Check if already installed
            val packStates = assetPackManager.getPackStates(listOf(packName)).await()
            val packState = packStates.packStates()[packName]

            when (packState?.status()) {
                AssetPackStatus.COMPLETED -> {
                    onProgressUpdate(100f, true)
                    Timber.d("Asset pack already installed")
                }
                AssetPackStatus.DOWNLOADING -> {
                    // Monitor download progress
                    monitorDownload(assetPackManager, packName, onProgressUpdate)
                }
                else -> {
                    // Start download
                    startDownload(assetPackManager, packName, onProgressUpdate)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error checking asset pack")
            onProgressUpdate(0f, false)
        }
    }
}

private fun startDownload(
    assetPackManager: AssetPackManager,
    packName: String,
    onProgressUpdate: (progress: Float, ready: Boolean) -> Unit
) {
    // Create listener for updates
    lateinit var listener: AssetPackStateUpdateListener
    listener = AssetPackStateUpdateListener { state ->
        when (state.status()) {
            AssetPackStatus.DOWNLOADING -> {
                val progress = if (state.totalBytesToDownload() > 0) {
                    (state.bytesDownloaded() * 100f / state.totalBytesToDownload())
                } else 0f
                onProgressUpdate(progress, false)
                Timber.d("Downloading: $progress%")
            }
            AssetPackStatus.COMPLETED -> {
                onProgressUpdate(100f, true)
                Timber.d("Download completed")
                assetPackManager.unregisterListener(listener)
            }
            AssetPackStatus.FAILED -> {
                onProgressUpdate(0f, false)
                Timber.e("Download failed: ${state.errorCode()}")
                assetPackManager.unregisterListener(listener)
            }
            else -> {
                Timber.d("Status: ${state.status()}")
            }
        }
    }

    // Register listener and start download
    assetPackManager.registerListener(listener)
    assetPackManager.fetch(listOf(packName))
}

private fun monitorDownload(
    assetPackManager: AssetPackManager,
    packName: String,
    onProgressUpdate: (progress: Float, ready: Boolean) -> Unit
) {
    lateinit var listener: AssetPackStateUpdateListener
    listener = AssetPackStateUpdateListener { state ->
        if (state.name() == packName) {
            when (state.status()) {
                AssetPackStatus.DOWNLOADING -> {
                    val progress = if (state.totalBytesToDownload() > 0) {
                        (state.bytesDownloaded() * 100f / state.totalBytesToDownload())
                    } else 0f
                    onProgressUpdate(progress, false)
                }
                AssetPackStatus.COMPLETED -> {
                    onProgressUpdate(100f, true)
                    assetPackManager.unregisterListener(listener)
                }
                AssetPackStatus.FAILED -> {
                    onProgressUpdate(0f, false)
                    assetPackManager.unregisterListener(listener)
                }
            }
        }
    }

    assetPackManager.registerListener(listener)
}

// Helper function to get model file path
fun getModelFilePath(context: Context): String? {
    val assetPackManager = AssetPackManagerFactory.getInstance(context)
    val location = assetPackManager.getPackLocation("gemma3n_assetpack")
    return location?.assetsPath()?.let { path ->
        File(path, "gemma-3n-E4B-it-int4.task").absolutePath
    }
}