package com.absenku.ui.splash

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Box
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.absenku.ui.AbsenNavHost
import com.absenku.ui.activation.ActivationViewModel
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
    val state by vm.uiState.collectAsStateWithLifecycle()

    if (state.deviceId.isBlank()) {
        // ensure device id is generated once
        LaunchedEffect(Unit) { vm.loadDeviceId() }
    }

    when {
        // show a tiny loading indicator while we determine activation
        state.isChecking -> {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color(0xFFE8F0FE)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = Color(0xFF1A73E8)
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
