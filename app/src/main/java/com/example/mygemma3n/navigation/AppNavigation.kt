package com.example.mygemma3n.navigation

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.mygemma3n.data.GeminiApiService
import com.example.mygemma3n.data.UnifiedGemmaService
import com.example.mygemma3n.data.repository.TokenUsageRepository
import com.example.mygemma3n.di.SpeechRecognitionServiceEntryPoint
import com.example.mygemma3n.TokenUsageRepositoryEntryPoint
import dagger.hilt.android.EntryPointAccessors
import com.example.mygemma3n.feature.analytics.AnalyticsDashboardScreen
import com.example.mygemma3n.feature.caption.LiveCaptionScreen
import com.example.mygemma3n.data.SpeechRecognitionService
import com.example.mygemma3n.feature.cbt.CBTCoachScreen
import com.example.mygemma3n.feature.chat.ChatListScreen
import com.example.mygemma3n.feature.chat.ChatScreen
import com.example.mygemma3n.feature.crisis.CrisisHandbookScreen
import com.example.mygemma3n.feature.plant.PlantScannerScreen
import com.example.mygemma3n.feature.quiz.QuizScreen
import com.example.mygemma3n.feature.story.StoryScreen
import com.example.mygemma3n.feature.summarizer.SummarizerScreen
import com.example.mygemma3n.feature.tutor.TutorScreen
import com.example.mygemma3n.ui.screens.HomeScreen
import com.example.mygemma3n.ui.screens.UnifiedChatScreen
import com.example.mygemma3n.ui.settings.ApiSettingsScreen
import com.example.mygemma3n.ui.settings.QuizSettingsScreen

/**
 * Main navigation component for the Gemma3n app.
 * Contains all navigation routes and screen destinations.
 */
@Composable
fun AppNavigation(
    navController: NavHostController,
    geminiApiService: GeminiApiService,
    unifiedGemmaService: UnifiedGemmaService,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = "unified_chat",
        modifier = modifier
    ) {
        // ───── Unified Chat (Default) ─────────────────────────────────────
        composable("unified_chat") { 
            UnifiedChatScreen(navController) 
        }
        
        // ───── Home & tools ───────────────────────────────────────────────
        composable("home") { 
            HomeScreen(navController, unifiedGemmaService) 
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
        
        composable("summarizer") { 
            SummarizerScreen() 
        }

        // ───── Chat list first ───────────────────────────────────────────
        composable("chat_list") {
            ChatListScreen(
                onNavigateToChat = { id ->
                    navController.navigate("chat/$id")
                }
            )
        }

        // ───── Individual session ───────────────────────────────────────
        composable(
            route = "chat/{sessionId}",
            arguments = listOf(navArgument("sessionId") { type = NavType.StringType })
        ) {
            ChatScreen()
        }

        // ───── Other screens ────────────────────────────────────────────
        composable("plant_scanner") { 
            PlantScannerScreen() 
        }
        
        composable("crisis_handbook") { 
            CrisisHandbookScreen(onNavigateBack = { navController.popBackStack() }) 
        }
        
        composable("settings") {
            QuizSettingsScreen(onNavigateBack = { navController.popBackStack() })
        }
        
        composable("tutor") {
            TutorScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToChatList = { navController.navigate("chat_list") }
            )
        }
        
        composable("analytics") {
            AnalyticsDashboardScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        composable("story_mode") {
            StoryScreen()
        }
        
        composable("api_settings") {
            val context = LocalContext.current
            val speechService = remember {
                EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    SpeechRecognitionServiceEntryPoint::class.java
                ).speechRecognitionService()
            }
            val tokenUsageRepository = remember {
                EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    TokenUsageRepositoryEntryPoint::class.java
                ).tokenUsageRepository()
            }
            ApiSettingsScreen(
                geminiApiService = geminiApiService,
                speechService = speechService,
                tokenUsageRepository = tokenUsageRepository,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}


/**
 * Navigation routes as constants for type safety
 */
object AppRoutes {
    const val UNIFIED_CHAT = "unified_chat"
    const val HOME = "home"
    const val LIVE_CAPTION = "live_caption"
    const val QUIZ_GENERATOR = "quiz_generator"
    const val CBT_COACH = "cbt_coach"
    const val SUMMARIZER = "summarizer"
    const val CHAT_LIST = "chat_list"
    const val CHAT_SESSION = "chat/{sessionId}"
    const val PLANT_SCANNER = "plant_scanner"
    const val CRISIS_HANDBOOK = "crisis_handbook"
    const val SETTINGS = "settings"
    const val TUTOR = "tutor"
    const val ANALYTICS = "analytics"
    const val STORY_MODE = "story_mode"
    const val API_SETTINGS = "api_settings"
    
    /**
     * Helper function to create chat session route with ID
     */
    fun chatSession(sessionId: String) = "chat/$sessionId"
}