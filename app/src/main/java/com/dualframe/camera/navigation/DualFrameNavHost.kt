package com.dualframe.camera.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.dualframe.camera.presentation.home.HomeScreen

private object Routes {
    const val HOME = "home"
}

@Composable
fun DualFrameNavHost() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.HOME
    ) {
        composable(Routes.HOME) {
            HomeScreen()
        }
    }
}
