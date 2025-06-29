// Fixed MainActivity.kt
package com.example.mygemma3n

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import dagger.hilt.android.AndroidEntryPoint

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
            QuizGeneratorScreen()
        }
        composable("cbt_coach") {
            CBTCoachScreen()
        }
        composable("plant_scanner") {
            PlantScannerScreen()
        }
        composable("crisis_handbook") {
            CrisisHandbookScreen()
        }
    }
}

// Placeholder screens (implement these in separate files)
@Composable
fun HomeScreen(navController: androidx.navigation.NavHostController) {
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

        Button(
            onClick = { navController.navigate("live_caption") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Live Caption & Translation")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = { navController.navigate("quiz_generator") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Offline Quiz Generator")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = { navController.navigate("cbt_coach") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Voice CBT Coach")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = { navController.navigate("plant_scanner") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Plant Disease Scanner")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = { navController.navigate("crisis_handbook") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Crisis Handbook")
        }
    }
}

@Composable
fun LiveCaptionScreen() {
    // Implement screen
    Text("Live Caption Screen")
}

@Composable
fun QuizGeneratorScreen() {
    // Implement screen
    Text("Quiz Generator Screen")
}

@Composable
fun CBTCoachScreen() {
    // Implement screen
    Text("CBT Coach Screen")
}

@Composable
fun PlantScannerScreen() {
    // Implement screen
    Text("Plant Scanner Screen")
}

@Composable
fun CrisisHandbookScreen() {
    // Implement screen
    Text("Crisis Handbook Screen")
}
