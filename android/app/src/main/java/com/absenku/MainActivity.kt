package com.absenku

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import com.absenku.ui.theme.AbsenKuTheme
import com.absenku.ui.splash.SplashScreen
import dagger.hilt.android.AndroidEntryPoint

/**
 * MainActivity — single-activity architecture.
 * Shows a splash screen while determining activation state, then nav host.
 * Hilt injection enabled via @AndroidEntryPoint for ViewModel injection.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Edge-to-edge content (draw under system bars). No fits-system-windows needed
        // because our Compose Material3 Scaffold already handles insets.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
        setContent {
            AbsenKuTheme {
                SplashScreen()
            }
        }
    }
}
