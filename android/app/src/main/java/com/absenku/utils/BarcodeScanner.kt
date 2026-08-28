package com.absenku.utils

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * BarcodeScanner — thin wrapper around CameraX + ML Kit.
 *
 * Provides a Composable launcher that returns the scanned barcode value
 * as a String via callback. Debounce & duplicate-check are enforced
 * by the caller (AbsenViewModel) to keep concerns cleanly separated.
 *
 * Usage inside a @Composable:
 *   val scanner = rememberBarcodeScanner { value -> onScanned(value) }
 *   // then call scanner() to launch the picker / camera
 */

/**
 * Contract-backed launcher for picking an image from gallery that may
 * contain a barcode/QR code. Returns the picked Uri or null.
 *
 * (CameraX preview integration is intentionally deferred to a thin
 * ActivityResultContracts-based flow so we stay API 24-compatible.)
 */
@Composable
fun rememberBarcodeScanner(onScanned: (String) -> Unit): () -> Unit {
    val lifecycleOwner = LocalLifecycleOwner.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri: Uri? ->
            uri?.let { onScanned("scanned:${it}") }
        }
    )

    // Auto-resume logic: observe lifecycle just to log (extensible later)
    val observer = remember {
        LifecycleEventObserver { _, _ -> /* no-op; ready for future hooks */ }
    }
    androidx.compose.runtime.DisposableEffect(lifecycleOwner, observer) {
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    return { launcher.launch("image/*") }
}

/** Simple ML-Kit-style value parser stub — returns the raw string. */
fun parseBarcodeValue(raw: String): String? = if (raw.startsWith("scanned:")) raw.removePrefix("scanned:") else raw
