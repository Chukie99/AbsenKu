package com.absenku.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.absenku.ui.dashboard.DashboardScreen
import com.absenku.ui.absen.AbsenScreen
import com.absenku.ui.scanner.QrScannerScreen
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

/** Routes that should NOT show the bottom nav bar. */
private val noBottomBarRoutes = setOf(
    "siswa_list", "siswa_form/{siswaId}", "poin_disiplin", "jadwal_pelajaran", "nilai", "qr_scanner"
)

/**
 * Main navigation host inside the activated app — 4 bottom-nav tabs + sub-screens.
 */
@Composable
fun AbsenNavHost(navController: NavHostController, modifier: Modifier = Modifier) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute !in noBottomBarRoutes

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { item ->
                        NavigationBarItem(
                            selected = currentRoute == item.route,
                            onClick = { navController.navigate(item.route) { launchSingleTop = true; popUpTo(navController.graph.startDestinationId) { saveState = true } } },
                            icon = { Icon(item.icon, contentDescription = null) },
                            label = { Text(item.title) },
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController,
            startDestination = BottomNavItem.Dashboard.route,
            modifier = modifier.padding(innerPadding)
        ) {
            // ── Bottom tabs ──
            composable(BottomNavItem.Dashboard.route) {
                DashboardScreen(
                    onNavigateToAbsen = { navController.navigate(BottomNavItem.Absen.route) { launchSingleTop = true } },
                    onNavigateToSiswa = { navController.navigate("siswa_list") },
                    onNavigateToPoinDisiplin = { navController.navigate("poin_disiplin") },
                    onNavigateToJadwal = { navController.navigate("jadwal_pelajaran") },
                    onNavigateToNilai = { navController.navigate("nilai") },
                    onNavigateToQrScanner = { navController.navigate("qr_scanner") },
                )
            }
            composable(BottomNavItem.Absen.route) { AbsenScreen() }
            composable(BottomNavItem.Report.route) { com.absenku.ui.report.ReportScreen() }
            composable(BottomNavItem.Settings.route) { SettingsScreen() }

            // ── Siswa screens ──
            composable("siswa_list") {
                com.absenku.ui.siswa.SiswaListScreen(
                    onAdd = { navController.navigate("siswa_form/0") },
                    onEdit = { siswa -> navController.navigate("siswa_form/${siswa.id}") },
                )
            }
            composable(
                "siswa_form/{siswaId}",
                arguments = listOf(navArgument("siswaId") { type = NavType.LongType })
            ) { backStackEntry ->
                val siswaId = backStackEntry.arguments?.getLong("siswaId") ?: 0L
                com.absenku.ui.siswa.SiswaFormScreen(
                    siswaId = siswaId,
                    onSaved = { navController.popBackStack() },
                    onCancel = { navController.popBackStack() },
                )
            }

            // ── Poin Disiplin ──
            composable("poin_disiplin") {
                com.absenku.ui.poin_disiplin.PoinDisiplinScreen()
            }

            // ── Jadwal Pelajaran ──
            composable("jadwal_pelajaran") {
                com.absenku.ui.jadwal.JadwalPelajaranScreen()
            }

            // ── Nilai (with Charts) ──
            composable("nilai") {
                com.absenku.ui.nilai.NilaiScreen()
            }

            // ── QR Scanner ──
            composable("qr_scanner") {
                QrScannerScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
