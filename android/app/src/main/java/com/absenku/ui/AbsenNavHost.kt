package com.absenku.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.absenku.ui.dashboard.DashboardScreen
import com.absenku.ui.absen.AbsenScreen
import com.absenku.ui.settings.SettingsScreen

/**
 * BottomNavItem — 4-tab navigation.
 */
sealed class BottomNavItem(val route: String, val title: String, val icon: ImageVector) {
    object Dashboard : BottomNavItem("dashboard", "Beranda", Icons.Rounded.Dashboard)
    object Absen : BottomNavItem("absen", "Absen", Icons.Rounded.EditNote)
    object Report : BottomNavItem("report", "Laporan", Icons.Rounded.ReceiptLong)
    object Settings : BottomNavItem("settings", "Pengaturan", Icons.Rounded.Settings)
}

val bottomNavItems = listOf(
    BottomNavItem.Dashboard, BottomNavItem.Absen,
    BottomNavItem.Report, BottomNavItem.Settings,
)

/**
 * Main navigation host inside the activated app — 4 bottom-nav tabs.
 */
@Composable
fun AbsenNavHost(navController: NavHostController, modifier: Modifier = Modifier) {
    Scaffold(
        bottomBar = {
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = backStackEntry?.destination?.route
            NavigationBar {
                bottomNavItems.forEach { item ->
                    NavigationBarItem(
                        selected = currentRoute == item.route,
                        onClick = { navController.navigate(item.route) { launchSingleTop = true } },
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(item.title) },
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController,
            startDestination = BottomNavItem.Dashboard.route,
            modifier = modifier.padding(innerPadding)
        ) {
            composable(BottomNavItem.Dashboard.route) { DashboardScreen(onNavigateToAbsen = { navController.navigate(BottomNavItem.Absen.route) { launchSingleTop = true } }) }
            composable(BottomNavItem.Absen.route) { AbsenScreen() }
            composable(BottomNavItem.Report.route) { com.absenku.ui.report.ReportScreen() }
            composable(BottomNavItem.Settings.route) { SettingsScreen() }
        }
    }
}
