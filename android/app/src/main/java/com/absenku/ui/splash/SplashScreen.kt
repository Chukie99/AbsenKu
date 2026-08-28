package com.absenku.ui.splash

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.absenku.ui.theme.AksenSplashTheme
import com.absenku.utils.DeviceIdHelper

/**
 * SplashScreen — shows the logo briefly, then decides:
 *  - device already activated → MainActivity (4-tab bottom nav)
 *  - device not activated → ActivationScreen
 *
 * Activation state is held by ActivationViewModel (singleton via Hilt).
 */
@Composable
fun SplashScreen() {
    val context = LocalContext.current
    val vm: ActivationViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()

    if (state.deviceId.isBlank()) {
        // ensure device id is generated once
        LaunchedEffect(Unit) { vm.loadDeviceId() }
    }

    when {
        // show a tiny loading indicator while we determine activation
        state.isLoading -> {
            androidx.compose.material3.Surface(
                modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                color = androidx.compose.ui.graphics.Color(0xFFE8F0FE)
            ) {
                androidx.compose.material3.Box(
                    modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    androidx.compose.material3.CircularProgressIndicator(
                        color = androidx.compose.ui.graphics.Color(0xFF1A73E8)
                    )
                }
            }
        }
        state.isActivated -> {
            val nav = rememberNavController()
            AbsenNavHost(navController = nav)
        }
        else -> {
            ActivationScreen(onActivated = { vm.refresh() })
        }
    }
}
