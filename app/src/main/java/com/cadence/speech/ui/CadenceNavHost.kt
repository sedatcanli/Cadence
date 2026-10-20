package com.cadence.speech.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.cadence.speech.di.AppContainer
import com.cadence.speech.ui.screens.HistoryScreen
import com.cadence.speech.ui.screens.HomeScreen
import com.cadence.speech.ui.screens.ReportScreen
import com.cadence.speech.ui.screens.SessionScreen
import com.cadence.speech.ui.screens.SettingsScreen

object Routes {
    const val HOME = "home"
    const val SESSION = "session"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val REPORT = "report/{id}"

    fun report(id: String) = "report/$id"
}

@Composable
fun CadenceNavHost(container: AppContainer) {
    val navController = rememberNavController()

    val navigateMain: (String) -> Unit = { route ->
        navController.navigate(route) {
            launchSingleTop = true
            popUpTo(Routes.HOME) { saveState = true }
            restoreState = true
        }
    }

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                container = container,
                onStart = { mode ->
                    container.sessionController.start(mode)
                    navController.navigate(Routes.SESSION)
                },
                onNavigate = navigateMain
            )
        }

        composable(Routes.SESSION) {
            SessionScreen(
                container = container,
                onFinished = { id ->
                    navController.navigate(Routes.report(id)) {
                        popUpTo(Routes.HOME)
                    }
                }
            )
        }

        composable(Routes.REPORT) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            ReportScreen(
                container = container,
                reportId = id,
                onNewSession = {
                    navController.popBackStack(Routes.HOME, inclusive = false)
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.HISTORY) {
            HistoryScreen(
                container = container,
                onNavigate = navigateMain,
                onOpen = { id -> navController.navigate(Routes.report(id)) }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                container = container,
                onNavigate = navigateMain
            )
        }
    }
}
