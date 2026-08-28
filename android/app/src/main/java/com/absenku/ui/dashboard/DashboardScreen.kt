package com.absenku.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.graphics.drawscope.Stroke
import com.absenku.utils.DateFormatter

/** Dashboard: today's summary counts + weekly bar chart + real-time clock. */
@Composable
fun DashboardScreen(
    modifier: Modifier = Modifier,
    onNavigateToAbsen: () -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val clock = DateFormatter.nowDisplayString()

    if (state.isLoading) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color(0xFF1A73E8))
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Header
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("AbsenKu", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A73E8))
            Text(clock, fontSize = 13.sp, color = Color(0xFF5F6368))
        }

        Spacer(Modifier.height(16.dp))

        // Summary cards
        val cards = listOf(
            "Hadir" to state.todayHadir to Color(0xFF34A853),
            "Izin" to state.todayIzin to Color(0xFFFBBC04),
            "Sakit" to state.todaySakit to Color(0xFFFF9800),
            "Alfa" to state.todayAlfa to Color(0xFFD93025),
        )
        LazyRowOrGrid(cards)

        Spacer(Modifier.height(8.dp))

        // Weekly chart
        if (state.weekly.isNotEmpty()) {
            Text("Kehadiran 7 Hari Terakhir", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
            SimpleBarChart(entries = state.weekly, modifier = Modifier.fillMaxWidth().height(160.dp))
        }
    }
}

@Composable
private fun LazyRowOrGrid(cards: List<Pair<String, Pair<Int, Color>>>) {
    val ctx = LocalContext
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        cards.forEach { (label, pair) ->
            val (count, color) = pair
            Card(
                modifier = Modifier.weight(1f).height(100.dp),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(
                    Modifier.fillMaxSize().padding(12.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(count.toString(), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = color)
                    Text(label, fontSize = 12.sp, color = Color(0xFF5F6368))
                }
            }
        }
    }
}

/** Minimal bar chart using Compose Canvas (no external chart lib needed). */
@Composable
private fun SimpleBarChart(entries: List<com.absenku.ui.dashboard.WeeklyEntry>, modifier: Modifier = Modifier) {
    val maxVal = (entries.maxOfOrNull { it.count } ?: 1).coerceAtLeast(1)
    val barColor = Color(0xFF1A73E8)
    val axisColor = Color(0xFFD1D5DB)

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Box(Modifier.padding(12.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                val barW = size.width / entries.size * 0.7f
                val slotW = size.width / entries.size
                entries.forEachIndexed { idx, e ->
                    val barH = (e.count.toFloat() / maxVal) * size.height * 0.8f
                    val x = slotW * idx + (slotW - barW) / 2
                    val yTop = size.height - barH
                    drawRect(
                        color = barColor,
                        topLeft = Offset(x, yTop),
                        size = Size(barW, barH),
                    )
                    drawLine(axisColor, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 2f)
                }
            }
        }
    }
}
