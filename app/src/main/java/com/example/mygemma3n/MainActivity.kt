// Fixed MainActivity.kt
package com.example.mygemma3n

import android.Manifest
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material3.LinearProgressIndicator
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
import com.google.android.play.core.assetpacks.AssetPackStateUpdateListener
import com.google.android.play.core.assetpacks.model.AssetPackStatus
import com.google.android.play.core.assetpacks.AssetPackManager
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
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
    var isPreparingModel by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current

    // in MainActivity.kt, inside HomeScreen’s LaunchedEffect:
    LaunchedEffect(Unit) {
        checkModelAvailability(context) { progress, ready, preparing, error ->
            downloadProgress = progress
            isModelReady    = ready
            isPreparingModel= preparing
            errorMessage    = error
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

        // Show appropriate status
        when {
            errorMessage != null -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            "Error loading model:",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            errorMessage!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            isPreparingModel -> {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Preparing model for first use...")
                Spacer(modifier = Modifier.height(16.dp))
            }
            downloadProgress > 0 && downloadProgress < 100 && !isModelReady -> {
                LinearProgressIndicator(
                    progress = { downloadProgress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Downloading model: ${downloadProgress.toInt()}%")
                Spacer(modifier = Modifier.height(16.dp))
            }
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

        if (!isModelReady && !isPreparingModel) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Model needs to be available before using features",
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

// Updated model availability check with error handling
fun checkModelAvailability(
    ctx: Context,
    onStatusUpdate: (progress: Float, ready: Boolean, preparing: Boolean, error: String?) -> Unit
) {
    CoroutineScope(Dispatchers.IO).launch {
        try {
            // Always ready if all .tflite shards are in assets/models/
            val assetsDir = "models"
            val needed = listOf(
                "TF_LITE_EMBEDDER.tflite",
                "TF_LITE_PER_LAYER_EMBEDDER.tflite",
                "TF_LITE_PREFILL_DECODE.tflite",
                "TF_LITE_VISION_ADAPTER.tflite",
                "TF_LITE_VISION_ENCODER.tflite",
                "TOKENIZER_MODEL.tflite"
            )
            val available = ctx.assets.list(assetsDir)?.toList() ?: emptyList()
            val missing = needed.filterNot { available.contains(it) }

            if (missing.isEmpty()) {
                onStatusUpdate(100f, true, false, null)  // ready
            } else {
                onStatusUpdate(
                    0f, false, false,
                    "Missing assets: ${missing.joinToString()}"
                )
            }
        } catch (e: Exception) {
            onStatusUpdate(0f, false, false, "Error checking assets: ${e.message}")
        }
    }
}


// ---- checkAssetModel -------------------------------------------------
private fun checkAssetModel(ctx: Context): Boolean = try {
    val dir = "models"
    ctx.assets.list(dir)?.any {
        it == "gemma-3n-E2B-it-int4.task" || it == "gemma-3n-E4B-it-int4.task"
    } ?: false
} catch (e: Exception) {
    Timber.e(e, "Error checking asset model")
    false
}

// Copy model from assets to cache
// ---- copyAssetModelToCache ------------------------------------------
private suspend fun copyAssetModelToCache(ctx: Context): String? = withContext(Dispatchers.IO) {
    val names = listOf("gemma-3n-E2B-it-int4.task", "gemma-3n-E4B-it-int4.task")
    for (name in names) {
        try {
            val cacheFile = File(ctx.cacheDir, name)
            if (!cacheFile.exists()) {
                ctx.assets.open("models/$name").use { input ->
                    cacheFile.outputStream().use { output -> input.copyTo(output) }
                }
            }
            return@withContext cacheFile.absolutePath
        } catch (_: Exception) { /* try next */ }
    }
    Timber.e("No model found in assets")
    null
}

// Check if Google Play Services is available
private fun isPlayStoreAvailable(context: Context): Boolean {
    return try {
        // Check if we can access Google Play services
        val packageInfo = context.packageManager.getPackageInfo("com.android.vending", 0)
        packageInfo != null
    } catch (e: Exception) {
        false
    }
}

// Original Google Play asset pack download (with error handling)
fun checkAndDownloadFromPlay(
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
                    monitorDownload(assetPackManager, packName) { progress, ready ->
                        onProgressUpdate(progress, ready)
                    }
                }
                else -> {
                    // Start download
                    startDownload(assetPackManager, packName) { progress, ready ->
                        onProgressUpdate(progress, ready)
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Asset pack not available, falling back to bundled model")
            // Don't fail, just use bundled model
            onProgressUpdate(100f, checkAssetModel(context))
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

// ---- getModelFilePath -----------------------------------------------
fun getModelFilePath(ctx: Context): String? {
    val pack = AssetPackManagerFactory.getInstance(ctx)
        .getPackLocation("gemma3n_assetpack")
    pack?.assetsPath()?.let { base ->
        File(base, "gemma-3n-E4B-it-int4.task").takeIf { it.exists() }?.let { return it.path }
    }
    listOf("gemma-3n-E2B-it-int4.task", "gemma-3n-E4B-it-int4.task").forEach { name ->
        File(ctx.cacheDir, name).takeIf { it.exists() }?.let { return it.path }
    }
    return null
}

// FIXED: Better error handling for large model extraction
suspend fun ensureLargeModelOnDisk(ctx: Context): String = withContext(Dispatchers.IO) {
    val target = File(ctx.cacheDir, "gemma-3n-E2B-it-int4.task")
    if (target.exists()) return@withContext target.path          // already done

    try {
        val modelParts = ctx.assets.list("models")!!
            .filter { it.startsWith("gemma-3n-E2B-it-int4.part") }
            .sorted()                                               // part0, part1, …

        if (modelParts.isEmpty()) {
            throw IllegalStateException("No model parts found in assets/models/")
        }

        Timber.d("Found ${modelParts.size} model parts to concatenate")

        // Use append mode to avoid keeping everything in memory
        target.outputStream().use { output ->
            modelParts.forEach { partName ->
                Timber.d("Processing $partName...")
                ctx.assets.open("models/$partName").use { input ->
                    input.copyTo(output)
                }
            }
        }

        Timber.i("Model assembled successfully at ${target.absolutePath}")
        target.path
    } catch (e: Exception) {
        Timber.e(e, "Failed to assemble model from parts")
        throw e
    }
}