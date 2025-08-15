package com.mygemma3n.aiapp.navigation

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
import com.mygemma3n.aiapp.data.GeminiApiService
import com.mygemma3n.aiapp.data.UnifiedGemmaService
import com.mygemma3n.aiapp.data.repository.TokenUsageRepository
import com.mygemma3n.aiapp.di.SpeechRecognitionServiceEntryPoint
import com.mygemma3n.aiapp.TokenUsageRepositoryEntryPoint
import dagger.hilt.android.EntryPointAccessors
import com.mygemma3n.aiapp.ui.screens.HomeScreen
import com.mygemma3n.aiapp.ui.screens.UnifiedChatScreen

// Safe imports - these will be conditionally used
// Import your screens here but wrap their usage in safe composable wrappers

/**
 * Safe wrapper for screens that might not exist in all build variants
 */
@Composable
private fun SafeScreen(
    navController: NavHostController,
    unifiedGemmaService: UnifiedGemmaService? = null,
    screenContent: @Composable () -> Unit
) {
    // In a real implementation, you might check build variants or feature flags here
    // For now, we'll assume all screens exist but this provides the structure for fallbacks
    screenContent()
}

/**
 * Main navigation component for the Gemma3n app.
 * Contains all navigation routes and screen destinations with fallbacks for offline versions.
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
        // ───── Unified Chat (Default) ─────────────────────────────────────────
        composable("unified_chat") {
            UnifiedChatScreen(navController)
        }

        // ───── Home & tools ─────────────────────────────────────────────────
        composable("home") {
            HomeScreen(navController, unifiedGemmaService)
        }

        composable("live_caption") {
            SafeScreen(navController, unifiedGemmaService) {
                // Check if LiveCaptionScreen is available
                if (isFeatureAvailable("live_caption")) {
                    com.mygemma3n.aiapp.feature.caption.LiveCaptionScreen()
                } else {
                    UnifiedChatScreen(navController)
                }
            }
        }

        composable("quiz_generator") {
            SafeScreen(navController, unifiedGemmaService) {
                if (isFeatureAvailable("quiz_generator")) {
                    com.mygemma3n.aiapp.feature.quiz.QuizScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                } else {
                    UnifiedChatScreen(navController)
                }
            }
        }

        composable("cbt_coach") {
            SafeScreen(navController, unifiedGemmaService) {
                if (isFeatureAvailable("cbt_coach")) {
                    com.mygemma3n.aiapp.feature.cbt.CBTCoachScreen()
                } else {
                    UnifiedChatScreen(navController)
                }
            }
        }

        composable("summarizer") {
            SafeScreen(navController, unifiedGemmaService) {
                if (isFeatureAvailable("summarizer")) {
                    com.mygemma3n.aiapp.feature.summarizer.SummarizerScreen(
                        onNavigateToQuiz = { navController.navigate("quiz_generator") }
                    )
                } else {
                    UnifiedChatScreen(navController)
                }
            }
        }

        // ───── Chat system ──────────────────────────────────────────────────
        composable("chat_list") {
            SafeScreen(navController, unifiedGemmaService) {
                if (isFeatureAvailable("chat_list")) {
                    com.mygemma3n.aiapp.feature.chat.ChatListScreen(
                        onNavigateToChat = { id ->
                            navController.navigate("chat/$id")
                        }
                    )
                } else {
                    UnifiedChatScreen(navController)
                }
            }
        }

        composable(
            route = "chat/{sessionId}",
            arguments = listOf(navArgument("sessionId") { type = NavType.StringType })
        ) {
            SafeScreen(navController, unifiedGemmaService) {
                if (isFeatureAvailable("chat_session")) {
                    com.mygemma3n.aiapp.feature.chat.ChatScreen(
                        onNavigateToQuiz = { navController.navigate("quiz_generator") }
                    )
                } else {
                    UnifiedChatScreen(navController)
                }
            }
        }

        // ───── Other screens ─────────────────────────────────────────────────
        composable("plant_scanner") {
            SafeScreen(navController, unifiedGemmaService) {
                if (isFeatureAvailable("plant_scanner")) {
                    com.mygemma3n.aiapp.feature.plant.PlantScannerScreen()
                } else {
                    UnifiedChatScreen(navController)
                }
            }
        }

        composable("crisis_handbook") {
            SafeScreen(navController, unifiedGemmaService) {
                if (isFeatureAvailable("crisis_handbook")) {
                    com.mygemma3n.aiapp.feature.crisis.CrisisHandbookScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                } else {
                    UnifiedChatScreen(navController)
                }
            }
        }

        composable("settings") {
            SafeScreen(navController, unifiedGemmaService) {
                if (isFeatureAvailable("settings")) {
                    com.mygemma3n.aiapp.ui.settings.QuizSettingsScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                } else {
                    HomeScreen(navController, unifiedGemmaService)
                }
            }
        }

        composable("tutor") {
            SafeScreen(navController, unifiedGemmaService) {
                if (isFeatureAvailable("tutor")) {
                    com.mygemma3n.aiapp.feature.tutor.TutorScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateToChatList = { navController.navigate("chat_list") },
                        onNavigateToQuiz = { navController.navigate("quiz_generator") }
                    )
                } else {
                    UnifiedChatScreen(navController)
                }
            }
        }

        composable("analytics") {
            SafeScreen(navController, unifiedGemmaService) {
                if (isFeatureAvailable("analytics")) {
                    com.mygemma3n.aiapp.feature.analytics.AnalyticsDashboardScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                } else {
                    HomeScreen(navController, unifiedGemmaService)
                }
            }
        }

        composable("story_mode") {
            com.mygemma3n.aiapp.feature.story.StoryScreen()
        }

        composable("api_settings") {
            SafeScreen(navController, unifiedGemmaService) {
                if (isFeatureAvailable("api_settings")) {
                    val context = LocalContext.current

                    // Safe dependency injection - only proceed if we can get required services
                    val speechService = remember {
                        try {
                            EntryPointAccessors.fromApplication(
                                context.applicationContext,
                                SpeechRecognitionServiceEntryPoint::class.java
                            ).speechRecognitionService()
                        } catch (e: Exception) {
                            null
                        }
                    }

                    val tokenUsageRepository = remember {
                        try {
                            EntryPointAccessors.fromApplication(
                                context.applicationContext,
                                TokenUsageRepositoryEntryPoint::class.java
                            ).tokenUsageRepository()
                        } catch (e: Exception) {
                            null
                        }
                    }

                    // Only show ApiSettingsScreen if we have all required dependencies
                    if (speechService != null && tokenUsageRepository != null) {
                        com.mygemma3n.aiapp.ui.settings.ApiSettingsScreen(
                            geminiApiService = geminiApiService,
                            speechService = speechService,
                            tokenUsageRepository = tokenUsageRepository,
                            onNavigateBack = { navController.popBackStack() }
                        )
                    } else {
                        // Fallback to HomeScreen if dependencies are missing
                        HomeScreen(navController, unifiedGemmaService)
                    }
                } else {
                    HomeScreen(navController, unifiedGemmaService)
                }
            }
        }
    }
}

/**
 * Check if a feature is available in the current build variant
 * This is where you'd implement your feature flag logic
 */
private fun isFeatureAvailable(featureName: String): Boolean {
    // TODO: Implement your feature flag logic here
    // For now, return true for all features
    // In production, you might check:
    // - Build variant (debug, release, offline)
    // - Remote config flags
    // - Local preferences
    // - Class existence checks

    return when (featureName) {
        // Example of how to disable features in offline builds:
        // "live_caption" -> BuildConfig.FLAVOR != "offline"
        // "analytics" -> BuildConfig.FLAVOR != "offline"
        else -> true // All features enabled by default
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