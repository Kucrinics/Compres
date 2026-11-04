package com.kompres.app.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.kompres.app.ui.compress.CompressScreen
import com.kompres.app.ui.convert.ImageScreen
import com.kompres.app.ui.docs.DocsScreen
import com.kompres.app.ui.home.HomeScreen
import com.kompres.app.ui.merge.MergeScreen
import com.kompres.app.ui.ocr.OcrScreen
import com.kompres.app.ui.privacy.PrivacyScreen
import com.kompres.app.ui.settings.SettingsScreen
import com.kompres.app.ui.zip.ZipScreen

@Composable
fun KompresApp() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onOpen = { navController.navigate(it) },
                onSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }
        composable(Routes.ZIP) {
            ZipScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.CONVERT) {
            ImageScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.DOCS) {
            DocsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.OCR) {
            OcrScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.MERGE) {
            MergeScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.COMPRESS) {
            CompressScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onPrivacy = { navController.navigate(Routes.PRIVACY) }
            )
        }
        composable(Routes.PRIVACY) {
            PrivacyScreen(onBack = { navController.popBackStack() })
        }
    }
}
