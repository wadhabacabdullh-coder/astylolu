package com.grandmaster.ai.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.grandmaster.ai.chess.Move
import com.grandmaster.ai.ui.ExplorerViewModel
import com.grandmaster.ai.ui.GameViewModel
import com.grandmaster.ai.ui.components.BoardArea
import com.grandmaster.ai.ui.components.BoardArrow
import com.grandmaster.ai.ui.components.BoardControls
import com.grandmaster.ai.ui.components.Chip
import com.grandmaster.ai.ui.components.EvalBadge
import com.grandmaster.ai.ui.components.SectionCard
import com.grandmaster.ai.ui.theme.GmColors
import com.grandmaster.ai.ui.theme.MonoFamily
import androidx.compose.ui.graphics.Color

@Composable
fun ExplorerScreen(gameVm: GameViewModel, explorerVm: ExplorerViewModel, onBack: () -> Unit) {
    val game by gameVm.game.collectAsStateWithLifecycle()
    val flipped by gameVm.flipped.collectAsStateWithLifecycle()
    val ply by gameVm.ply.collectAsStateWithLifecycle()
    val settings by gameVm.settings.collectAsStateWithLifecycle()
    val ui by explorerVm.ui.collectAsStateWithLifecycle()
    LaunchedEffect(game) { explorerVm.setGame(game) }
    DisposableEffect(Unit) {
        explorerVm.setActive(true)
        onDispose { explorerVm.setActive(false) }
    }
    val arrows = ui.moves.filter { it.book != null }.take(4).mapNotNull { m ->
        Move.fromUci(m.uci)?.let { BoardArrow(it.from, it.to, Color(0x995AB4F0), 0.8f) }
    }

    GmScaffold(title = "Opening Explorer", subtitle = ui.opening?.let { "${it.eco} · ${it.name}" }, onBack = onBack) { padding ->
        BoardScreenLayout(
            padding = padding,
            board = {
                BoardArea(
                    vm = gameVm, game = game, flipped = flipped, ply = ply, evalWhite = null,
                    arrows = if (settings.showArrows) arrows else emptyList(),
                    showCoordinates = settings.showCoordinates, showEvalBar = false,
                )
            },
            controls = { BoardControls(gameVm, game) },
        ) {
            item(key = "opening") {
                SectionCard(title = "Opening") {
                    when {
                        ui.customStart -> Text("This game starts from a custom position, so opening theory does not apply. Stockfish's suggestions are shown below.", color = GmColors.TextSecondary)
                        ui.opening != null -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Chip(ui.opening!!.eco)
                                Spacer(Modifier.width(8.dp))
                                Text(ui.opening!!.name, style = MaterialTheme.typography.titleMedium)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(ui.opening!!.moves.chunked(2).mapIndexed { i, p -> "${i + 1}. ${p.joinToString(" ")}" }.joinToString(" "), fontFamily = MonoFamily, fontSize = 13.sp, color = GmColors.TextSecondary)
                            if (!ui.inBook) {
                                Spacer(Modifier.height(6.dp))
                                Text("You have left the book lines — the moves below come from Stockfish.", style = MaterialTheme.typography.bodySmall, color = GmColors.Warning)
                            }
                        }
                        else -> Text("Starting position. Choose a first move to explore the main openings.", color = GmColors.TextSecondary)
                    }
                }
            }
            item(key = "moves") {
                SectionCard(title = "Continuations", trailing = { if (ui.evaluating) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) }) {
                    if (ui.moves.isEmpty() && !ui.evaluating) Text("No moves available.", color = GmColors.TextSecondary)
                    ui.moves.forEachIndexed { i, m ->
                        if (i > 0) HorizontalDivider(color = GmColors.Outline)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { Move.fromUci(m.uci)?.let(gameVm::onMove) }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(m.san, fontFamily = MonoFamily, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.width(76.dp))
                            Column(Modifier.weight(1f)) {
                                Text(m.book?.openingName ?: "Engine suggestion", style = MaterialTheme.typography.bodyMedium)
                                val sub = buildString {
                                    m.book?.let { append(it.eco); if (it.lines > 1) append(" · ${it.lines} book lines") }
                                    m.engineRank?.let { if (isNotEmpty()) append(" · "); append("Stockfish #$it") }
                                }
                                if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall, color = GmColors.TextSecondary)
                            }
                            if (m.scoreWhite != null) EvalBadge(m.scoreWhite)
                            else if (ui.evaluating) Text("…", color = GmColors.TextSecondary)
                        }
                    }
                    ui.error?.let { Text(it, color = GmColors.Danger, style = MaterialTheme.typography.bodySmall) }
                }
            }
            item(key = "note") {
                Text(
                    "Book moves come from established opening theory; evaluations are computed live by Stockfish 19 for this position.",
                    style = MaterialTheme.typography.bodySmall, color = GmColors.TextSecondary, modifier = Modifier.padding(4.dp),
                )
            }
        }
    }
}
