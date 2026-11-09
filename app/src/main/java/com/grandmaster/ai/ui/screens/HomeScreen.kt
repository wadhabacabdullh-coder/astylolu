package com.grandmaster.ai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Troubleshoot
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.grandmaster.ai.chess.Piece
import com.grandmaster.ai.chess.PieceColor
import com.grandmaster.ai.chess.PieceType
import com.grandmaster.ai.engine.EngineStatus
import com.grandmaster.ai.ui.GameViewModel
import com.grandmaster.ai.ui.components.PieceIcon
import com.grandmaster.ai.ui.components.StatusDot
import com.grandmaster.ai.ui.theme.GmColors
import kotlinx.coroutines.flow.StateFlow

enum class Destination(val route: String, val title: String, val description: String, val icon: ImageVector, val accent: Color) {
    ANALYSIS("analysis", "Analysis Board", "Best moves, evaluation and explanations", Icons.Filled.Troubleshoot, GmColors.Accent),
    HIKARU("hikaru", "Hikaru Mode", "Sacrifices, forcing lines, attacking ideas", Icons.Filled.Bolt, Color(0xFFFF7043)),
    REVIEW("review", "Game Review", "Accuracy, blunders and brilliant moves", Icons.Filled.QueryStats, GmColors.Info),
    EXPLORER("explorer", "Opening Explorer", "Theory with live engine evaluations", Icons.AutoMirrored.Filled.MenuBook, GmColors.Gold),
    SETTINGS("settings", "Engine Settings", "Depth, time, lines, threads, strength", Icons.Filled.Settings, Color(0xFFB39DDB)),
    ABOUT("about", "About", "Stockfish, licences and methodology", Icons.Filled.Info, GmColors.TextSecondary),
}

@Composable
fun HomeScreen(
    gameVm: GameViewModel,
    engineStatus: StateFlow<EngineStatus>,
    onNavigate: (Destination) -> Unit,
    onStartPlay: (PieceColor) -> Unit,
) {
    val status by engineStatus.collectAsStateWithLifecycle()
    val game by gameVm.game.collectAsStateWithLifecycle()
    var playDialog by remember { mutableStateOf(false) }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 300.dp),
        modifier = Modifier
            .fillMaxSize()
            .background(GmColors.Background)
            .statusBarsPadding(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { Hero(status) }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BigAction(
                    "Analyse", if (game.moves.isEmpty()) "Open the analysis board" else "Continue (${game.moves.size} moves)",
                    Icons.Filled.Troubleshoot, GmColors.Accent, Modifier.weight(1f),
                ) { onNavigate(Destination.ANALYSIS) }
                BigAction("Play", "vs Stockfish 19", Icons.Filled.SportsEsports, GmColors.Info, Modifier.weight(1f)) { playDialog = true }
            }
        }
        items(Destination.entries.filter { it != Destination.ANALYSIS }) { d -> NavCard(d) { onNavigate(d) } }
    }

    if (playDialog) {
        AlertDialog(
            onDismissRequest = { playDialog = false },
            title = { Text("Play vs Stockfish") },
            text = {
                Column {
                    Text("Choose your colour. Engine strength and thinking time are set in Engine Settings.", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ColorChoice(PieceColor.WHITE, Modifier.weight(1f)) { playDialog = false; onStartPlay(PieceColor.WHITE) }
                        ColorChoice(PieceColor.BLACK, Modifier.weight(1f)) { playDialog = false; onStartPlay(PieceColor.BLACK) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    playDialog = false
                    onStartPlay(if (System.nanoTime() % 2 == 0L) PieceColor.WHITE else PieceColor.BLACK)
                }) { Text("Random colour") }
            },
            dismissButton = { TextButton(onClick = { playDialog = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Hero(status: EngineStatus) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF173A27), Color(0xFF0F1A14), GmColors.Surface)))
            .border(1.dp, GmColors.AccentDim.copy(alpha = 0.5f), RoundedCornerShape(22.dp))
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PieceIcon(Piece.of(PieceColor.WHITE, PieceType.KING), 54.dp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Grandmaster AI", style = MaterialTheme.typography.headlineLarge)
                Text("Your elite chess coach", color = GmColors.TextSecondary, style = MaterialTheme.typography.bodyLarge)
            }
        }
        Spacer(Modifier.height(16.dp))
        val (text, color) = when (status) {
            is EngineStatus.Ready -> "${status.info.name} · NNUE · ${status.info.cpuFeature}" to GmColors.Accent
            is EngineStatus.Installing -> "Preparing neural network (first launch)…" to GmColors.Warning
            EngineStatus.Starting -> "Starting Stockfish…" to GmColors.Warning
            EngineStatus.Idle -> "Engine idle" to GmColors.TextSecondary
            is EngineStatus.Failed -> "Engine error: ${status.message}" to GmColors.Danger
        }
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(Color(0x22000000))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusDot(color)
            Spacer(Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.labelLarge, color = color)
        }
        if (status is EngineStatus.Installing) {
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(progress = { status.progress }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun BigAction(title: String, sub: String, icon: ImageVector, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(10.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(sub, style = MaterialTheme.typography.bodySmall, color = GmColors.TextSecondary)
    }
}

@Composable
private fun NavCard(d: Destination, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(GmColors.Surface)
            .border(1.dp, GmColors.Outline, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(d.accent.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) { Icon(d.icon, null, tint = d.accent) }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(d.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(d.description, style = MaterialTheme.typography.bodySmall, color = GmColors.TextSecondary)
        }
    }
}

@Composable
private fun ColorChoice(color: PieceColor, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(GmColors.SurfaceHigh)
            .clickable(onClick = onClick)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PieceIcon(Piece.of(color, PieceType.KING), 48.dp)
        Text(color.displayName, style = MaterialTheme.typography.labelLarge)
    }
}
