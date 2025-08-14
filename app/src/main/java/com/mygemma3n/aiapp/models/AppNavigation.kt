package com.mygemma3n.aiapp.models

//import androidx.compose.runtime.Composable
//import androidx.compose.ui.Modifier
//import androidx.navigation.NavHostController
//import androidx.navigation.NavType
//import androidx.navigation.compose.NavHost
//import androidx.navigation.compose.composable
//import androidx.navigation.navArgument
//import com.mygemma3n.aiapp.data.GeminiApiService
//import com.mygemma3n.aiapp.data.UnifiedGemmaService
//import com.mygemma3n.aiapp.feature.caption.LiveCaptionScreen
//import com.mygemma3n.aiapp.feature.cbt.CBTCoachScreen
//import com.mygemma3n.aiapp.feature.chat.ChatListScreen
//import com.mygemma3n.aiapp.feature.chat.ChatScreen
//import com.mygemma3n.aiapp.feature.crisis.CrisisHandbookScreen
//import com.mygemma3n.aiapp.feature.plant.PlantScannerScreen
//import com.mygemma3n.aiapp.feature.quiz.QuizScreen
//import com.mygemma3n.aiapp.feature.story.StoryModeScreen
//import com.mygemma3n.aiapp.feature.summarizer.SummarizerScreen
//import com.mygemma3n.aiapp.feature.tutor.TutorScreen
//import com.mygemma3n.aiapp.ui.screens.HomeScreen
//import com.mygemma3n.aiapp.ui.screens.UnifiedChatScreen
//import com.mygemma3n.aiapp.ui.settings.ApiSettingsScreen
//import com.mygemma3n.aiapp.ui.settings.QuizSettingsScreen
//
//@Composable
//fun AppNavigation(
//    navController: NavHostController,
//    geminiApiService: GeminiApiService,
//    unifiedGemmaService: UnifiedGemmaService,
//    modifier: Modifier = Modifier
//) {
//    NavHost(
//        navController = navController,
//        startDestination = "unified_chat", // Changed from "home" to unified chat
//        modifier = modifier
//    ) {
//        // ───── Unified Chat (New Default) ─────────────────────────────────────────
//        composable("unified_chat") {
//            UnifiedChatScreen(navController = navController)
//        }
//
//        // ───── Legacy Home & Tools ────────────────────────────────────────────────
//        composable("home") {
//            HomeScreen(navController = navController, unifiedGemmaService = unifiedGemmaService)
//        }
//
//        // ───── Feature Screens ────────────────────────────────────────────────────
//        composable("live_caption") {
//            LiveCaptionScreen()
//        }
//
//        composable("quiz_generator") {
//            QuizScreen()
//        }
//
//        composable("cbt_coach") {
//            CBTCoachScreen()
//        }
//
//        composable("summarizer") {
//            SummarizerScreen()
//        }
//
//        composable("plant_scanner") {
//            PlantScannerScreen()
//        }
//
//        composable("crisis_handbook") {
//            CrisisHandbookScreen()
//        }
//
//        // Add tutor screen if it exists
//        composable("tutor") {
//            try {
//                TutorScreen(onNavigateBack = { navController.popBackStack() })
//            } catch (e: Exception) {
//                // Fallback if TutorScreen doesn't exist
//                UnifiedChatScreen(navController = navController)
//            }
//        }
//
//        // Add story mode if it exists
//        composable("story_mode") {
//            try {
//                StoryModeScreen(onNavigateBack = { navController.popBackStack() })
//            } catch (e: Exception) {
//                // Fallback if StoryModeScreen doesn't exist
//                UnifiedChatScreen(navController = navController)
//            }
//        }
//
//        // Analytics placeholder
//        composable("analytics") {
//            // Placeholder for analytics screen
//            HomeScreen(navController = navController, unifiedGemmaService = unifiedGemmaService)
//        }
//
//        // ───── Chat System ────────────────────────────────────────────────────────
//        composable("chat_list") {
//            ChatListScreen(
//                onNavigateToChat = { sessionId ->
//                    navController.navigate("chat/$sessionId")
//                }
//            )
//        }
//
//        composable(
//            route = "chat/{sessionId}",
//            arguments = listOf(navArgument("sessionId") { type = NavType.StringType })
//        ) {
//            ChatScreen()
//        }
//
//        // ───── Settings ───────────────────────────────────────────────────────────
//        composable("settings") {
//            QuizSettingsScreen(onNavigateBack = { navController.popBackStack() })
//        }
//
//        composable("api_settings") {
//            ApiSettingsScreen(
//                geminiApiService = geminiApiService,
//                onNavigateBack = { navController.popBackStack() }
//            )
//        }
//    }
//}