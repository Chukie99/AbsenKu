package com.absenku.ui.ranking

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * RankingScreen — leaderboard for students based on grades or discipline points.
 *
 * Two tabs: Nilai (grade-based) and Poin (discipline-based).
 * The Nilai tab allows filtering by a specific subject (mapel).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RankingScreen(
    onBack: () -> Unit,
    viewModel: RankingViewModel = hiltViewModel(),
) {
    val s by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Papan Peringkat") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Kembali") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // Tabs
            TabRow(selectedTabIndex = s.selectedTab.ordinal) {
                Tab(selected = s.selectedTab == RankingTab.NILAI, onClick = { viewModel.selectTab(RankingTab.NILAI) }) {
                    Text("Nilai", modifier = Modifier.padding(12.dp), fontWeight = FontWeight.SemiBold)
                }
                Tab(selected = s.selectedTab == RankingTab.POIN, onClick = { viewModel.selectTab(RankingTab.POIN) }) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
                        Icon(Icons.Default.EmojiEvents, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Poin Disiplin", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            when (s.selectedTab) {
                RankingTab.NILAI -> NilaiRankingTab(s, viewModel)
                RankingTab.POIN -> PoinRankingTab(s)
            }
        }
    }
}

@Composable
private fun NilaiRankingTab(s: RankingUiState, viewModel: RankingViewModel) {
    var mapelExpanded by remember { mutableStateOf(false) }
    val selectedMapelNama = s.allMapelNames.find { it.first == s.selectedMapelId }?.second ?: "Semua Mapel"

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        // Mapel filter dropdown
        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Mapel:", fontSize = 13.sp, color = Color(0xFF5F6368), modifier = Modifier.padding(end = 8.dp))
            Box {
                OutlinedButton(onClick = { mapelExpanded = true }) {
                    Text(selectedMapelNama, fontSize = 13.sp, modifier = Modifier.weight(1f, fill = false))
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                }
                DropdownMenu(expanded = mapelExpanded, onDismissRequest = { mapelExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Semua Mapel") },
                        onClick = { viewModel.selectMapel(null); mapelExpanded = false },
                    )
                    s.allMapelNames.forEach { (id, nama) ->
                        DropdownMenuItem(
                            text = { Text(nama) },
                            onClick = { viewModel.selectMapel(id); mapelExpanded = false },
                        )
                    }
                }
            }
        }

        if (s.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF1A73E8))
            }
        } else if (s.nilairanking.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Belum ada data nilai", color = Color(0xFF5F6368))
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(s.nilairanking, key = { it.siswa.id }) { entry ->
                    NilaiRankingCard(entry)
                }
            }
        }
    }
}

@Composable
private fun PoinRankingTab(s: RankingUiState) {
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        if (s.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF1A73E8))
            }
        } else if (s.poinRanking.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Belum ada data poin disiplin", color = Color(0xFF5F6368))
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(s.poinRanking, key = { it.siswa.id }) { entry ->
                    PoinRankingCard(entry)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Ranking cards
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun NilaiRankingCard(entry: RankingEntry) {
    val badgeColor = when (entry.rank) {
        1 -> Color(0xFFFF9800) // Gold
        2 -> Color(0xFF9C27B0) // Silver (purple-ish)
        3 -> Color(0xFF4CAF50) // Bronze (green)
        else -> Color(0xFF1A73E8)
    }
    val bgColor = when (entry.rank) {
        1 -> Color(0xFFFFF3E0)
        2 -> Color(0xFFF3E5F5)
        3 -> Color(0xFFE8F5E9)
        else -> MaterialTheme.colorScheme.surface
    }

    Card(
        shape = RoundedCornerShape(10.dp),
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Rank badge
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(badgeColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(entry.rank.toString(), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = badgeColor)
            }
            Spacer(Modifier.width(12.dp))
            // Student info
            Column(Modifier.weight(1f)) {
                Text(entry.siswa.nama, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(entry.siswa.nis, fontSize = 11.sp, color = Color(0xFF5F6368))
            }
            // Score
            Column(horizontalAlignment = Alignment.End) {
                Text(String.format("%.1f", entry.avgNilai), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF1A73E8))
                Text("avg", fontSize = 10.sp, color = Color(0xFF5F6368))
            }
        }
    }
}

@Composable
private fun PoinRankingCard(entry: PoinRankingEntry) {
    val badgeColor = when (entry.rank) {
        1 -> Color(0xFFFF9800)
        2 -> Color(0xFF9C27B0)
        3 -> Color(0xFF4CAF50)
        else -> Color(0xFF1A73E8)
    }
    val bgColor = when (entry.rank) {
        1 -> Color(0xFFFFF3E0)
        2 -> Color(0xFFF3E5F5)
        3 -> Color(0xFFE8F5E9)
        else -> MaterialTheme.colorScheme.surface
    }

    Card(
        shape = RoundedCornerShape(10.dp),
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(badgeColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(entry.rank.toString(), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = badgeColor)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(entry.siswa.nama, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(entry.siswa.nis, fontSize = 11.sp, color = Color(0xFF5F6368))
            }
            Column(horizontalAlignment = Alignment.End) {
                val net = entry.netPoin
                Text(
                    "${if (net >= 0) "+" else ""}$net",
                    fontWeight = FontWeight.Bold, fontSize = 16.sp,
                    color = if (net >= 0) Color(0xFF34A853) else Color(0xFFD93025),
                )
                Text("poin", fontSize = 10.sp, color = Color(0xFF5F6368))
            }
        }
    }
}
