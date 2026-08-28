package com.absenku.ui.activation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.widget.Toast

/**
 * ActivationScreen — shows Device ID + input Serial.
 * After valid serial → app unlocked permanently.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivationScreen(
    onActivated: () -> Unit,
    viewModel: ActivationViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    if (state.isActivated) {
        LaunchedEffect(Unit) { onActivated() }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Aktivasi AbsenKu") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Logo
            Icon(
                imageVector = Icons.Default.ContentCopy, // fallback icon (logo drawn elsewhere)
                contentDescription = null,
                tint = Color(0xFF1A73E8),
                modifier = Modifier.size(80.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text("Selamat datang di AbsenKu", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("Sistem Absensi & Nilai Siswa Offline", fontSize = 13.sp, color = Color(0xFF5F6368))

            Spacer(Modifier.height(28.dp))

            // Device ID
            OutlinedTextField(
                value = state.deviceId,
                onValueChange = {},
                readOnly = true,
                label = { Text("Device ID") },
                trailingIcon = {
                    IconButton(onClick = {
                        val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("Device ID", state.deviceId)
                        cm.setPrimaryClip(clip)
                        Toast.makeText(context, "Device ID disalin!", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Text(
                "Kirim Device ID di atas ke WA 082261407123 untuk dapat Serial.",
                fontSize = 12.sp, color = Color(0xFF5F6368),
                modifier = Modifier.padding(top = 8.dp),
            )

            Spacer(Modifier.height(20.dp))

            // Serial input
            var serialInput by remember { mutableStateOf("") }
            OutlinedTextField(
                value = serialInput,
                onValueChange = { if (it.length <= 8) serialInput = it.uppercase() },
                label = { Text("Masukkan Serial") },
                placeholder = { Text("8 karakter, contoh: A7K3P9Q2") },
                singleLine = true,
                isError = false,
                modifier = Modifier.fillMaxWidth(),
            )

            if (state.errorMsg != null) {
                Text(state.errorMsg!!, color = Color(0xFFD93025), fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            }

            Spacer(Modifier.height(28.dp))

            Button(
                onClick = { viewModel.submitSerial(state.deviceId, serialInput) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                enabled = serialInput.length == 8,
            ) {
                Text("Aktifkan", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}
