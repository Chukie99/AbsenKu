package com.absenku.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.absenku.utils.DeviceIdHelper

/**
 * SettingsScreen — school data, activation status, sync pairing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val deviceId = DeviceIdHelper.getDeviceId(ctx)

    // load once
    androidx.compose.runtime.LaunchedEffect(deviceId) { viewModel.load(deviceId) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Pengaturan") }) }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
                .fillMaxWidth()
        ) {
            Text("Data Sekolah", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(value = s.schoolName, onValueChange = {}, label = { Text("Nama Sekolah") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = s.teacherName, onValueChange = {}, label = { Text("Nama Guru") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = s.year, onValueChange = {}, label = { Text("Tahun Ajaran") }, placeholder = { Text("2025/2026") }, modifier = Modifier.fillMaxWidth(), singleLine = true)

            Text("Status Aktivasi", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), elevation = CardDefaults.cardElevation(2.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Device ID", fontSize = 12.sp, color = Color(0xFF5F6368))
                    Text(s.deviceId, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("Serial:", fontSize = 12.sp, color = Color(0xFF5F6368))
                    Text(s.serial ?: "(belum diaktivasi)", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Box(Modifier.align(Alignment.CenterHorizontally)) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (s.isActivated) Color(0xFF34A853) else Color(0xFFD93025)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(if (s.isActivated) "AKTIF" else "BELUM AKTIF", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Sync pairing section
            Text("Sinkronisasi", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), elevation = CardDefaults.cardElevation(2.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Pairing Token", fontSize = 12.sp, color = Color(0xFF5F6368))
                    Text(s.pairingToken ?: "(belum tertautkan ke Desktop)", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { /* WiFi pairing flow */ }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Wifi, contentDescription = null, tint = Color(0xFF1A73E8))
                            Text("WiFi")
                        }
                        OutlinedButton(onClick = { /* Bluetooth pairing flow */ }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Bluetooth, contentDescription = null, tint = Color(0xFF1A73E8))
                            Text("Bluetooth")
                        }
                    }
                }
            }
        }
    }
}
