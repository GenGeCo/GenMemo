package com.example.genmemo.ui.navigation

import androidx.compose.runtime.*
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.genmemo.data.auth.AuthManager
import com.example.genmemo.data.model.OnlinePackage
import com.example.genmemo.data.repository.InstalledPackageRepository
import com.example.genmemo.data.repository.MemoryRepository
import com.example.genmemo.data.sync.ProgressSyncService
import com.example.genmemo.ui.screens.*

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Memorize : Screen("memorize")
    object ReviewSetup : Screen("review_setup")
    object ReviewSession : Screen("review_session/{type}/{categoryId}/{count}/{infiniteMode}") {
        fun createRoute(type: String, categoryId: Long, count: Int, infiniteMode: Boolean): String {
            return "review_session/$type/$categoryId/$count/$infiniteMode"
        }
    }
    object Settings : Screen("settings")
    object ManageCategories : Screen("manage_categories")
    object ManageItems : Screen("manage_items")
    object ImportExport : Screen("import_export")
    object Login : Screen("login")
    object OnlinePackage : Screen("online_package")
    object OnlineQuiz : Screen("online_quiz")
}

@Composable
fun NavGraph(
    navController: NavHostController,
    repository: MemoryRepository,
    installedPackageRepository: InstalledPackageRepository,
    authManager: AuthManager,
    progressSyncService: ProgressSyncService
) {
    // State to hold the current online package for quiz
    var currentOnlinePackage by remember { mutableStateOf<OnlinePackage?>(null) }

    // Observe auth state
    val authState by authManager.authState.collectAsState()

    // Determine start destination based on auth state
    val startDestination = if (authManager.isLoggedIn) Screen.Home.route else Screen.Login.route

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                repository = repository,
                installedPackageRepository = installedPackageRepository,
                progressSyncService = progressSyncService,
                onNavigateToMemorize = { navController.navigate(Screen.Memorize.route) },
                onNavigateToReview = { navController.navigate(Screen.ReviewSetup.route) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                onNavigateToOnlinePackage = { navController.navigate(Screen.OnlinePackage.route) },
                onStartPackageQuiz = { pkg ->
                    currentOnlinePackage = pkg
                    navController.navigate(Screen.OnlineQuiz.route)
                }
            )
        }

        composable(Screen.Memorize.route) {
            MemorizeScreen(
                repository = repository,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.ReviewSetup.route) {
            ReviewSetupScreen(
                repository = repository,
                onNavigateBack = { navController.popBackStack() },
                onStartReview = { type, categoryId, count, infiniteMode ->
                    navController.navigate(
                        Screen.ReviewSession.createRoute(type, categoryId, count, infiniteMode)
                    )
                }
            )
        }

        composable(
            route = Screen.ReviewSession.route,
            arguments = listOf(
                navArgument("type") { type = NavType.StringType },
                navArgument("categoryId") { type = NavType.LongType },
                navArgument("count") { type = NavType.IntType },
                navArgument("infiniteMode") { type = NavType.BoolType }
            )
        ) { backStackEntry ->
            val type = backStackEntry.arguments?.getString("type") ?: "ALL"
            val categoryId = backStackEntry.arguments?.getLong("categoryId") ?: -1L
            val count = backStackEntry.arguments?.getInt("count") ?: 20
            val infiniteMode = backStackEntry.arguments?.getBoolean("infiniteMode") ?: false

            ReviewSessionScreen(
                repository = repository,
                itemType = type,
                categoryId = if (categoryId == -1L) null else categoryId,
                questionCount = count,
                infiniteMode = infiniteMode,
                onFinish = {
                    navController.popBackStack(Screen.Home.route, inclusive = false)
                }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToCategories = { navController.navigate(Screen.ManageCategories.route) },
                onNavigateToItems = { navController.navigate(Screen.ManageItems.route) },
                onNavigateToImportExport = { navController.navigate(Screen.ImportExport.route) }
            )
        }

        composable(Screen.ManageCategories.route) {
            ManageCategoriesScreen(
                repository = repository,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.ManageItems.route) {
            ManageItemsScreen(
                repository = repository,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.ImportExport.route) {
            ImportExportScreen(
                repository = repository,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Login.route) {
            LoginScreen(
                authManager = authManager,
                onNavigateBack = {
                    // If not logged in and trying to go back, do nothing (or close app)
                    if (authManager.isLoggedIn) {
                        navController.popBackStack()
                    }
                },
                onLoginSuccess = {
                    // Navigate to Home after successful login
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.OnlinePackage.route) {
            PackageStoreScreen(
                authManager = authManager,
                installedPackageRepository = installedPackageRepository,
                progressSyncService = progressSyncService,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.OnlineQuiz.route) {
            currentOnlinePackage?.let { pkg ->
                OnlineQuizScreen(
                    onlinePackage = pkg,
                    authManager = if (authManager.isLoggedIn) authManager else null,
                    progressSyncService = if (authManager.isLoggedIn) progressSyncService else null,
                    onFinish = {
                        currentOnlinePackage = null
                        navController.popBackStack(Screen.Home.route, inclusive = false)
                    }
                )
            }
        }
    }
}
