package com.grandmaster.ai.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.grandmaster.ai.analysis.Evaluation
import com.grandmaster.ai.analysis.MoveQuality
import com.grandmaster.ai.chess.Move
import com.grandmaster.ai.chess.PieceColor
import com.grandmaster.ai.engine.Score
import com.grandmaster.ai.ui.GameViewModel
import com.grandmaster.ai.ui.ReviewUi
import com.grandmaster.ai.ui.ReviewViewModel
import com.grandmaster.ai.ui.components.BoardArea
import com.grandmaster.ai.ui.components.BoardArrow
import com.grandmaster.ai.ui.components.BoardControls
import com.grandmaster.ai.ui.components.Chip
import com.grandmaster.ai.ui.components.EvalBadge
import com.grandmaster.ai.ui.components.MoveList
import com.grandmaster.ai.ui.components.SectionCard
import com.grandmaster.ai.ui.components.qualityColor
import com.grandmaster.ai.ui.theme.GmColors
import com.grandmaster.ai.ui.theme.MonoFamily
import java.util.Locale

@Composable
fun ReviewScreen(gameVm: GameViewModel, reviewVm: ReviewViewModel, onBack: () -> Unit) {
    val game by gameVm.game.collectAsStateWithLifecycle()
    val flipped by gameVm.flipped.collectAsStateWithLifecycle()
    val ply by gameVm.ply.collectAsStateWithLifecycle()
    val settings by gameVm.settings.collectAsStateWithLifecycle()
    val ui by reviewVm.ui.collectAsStateWithLifecycle()
    val matches = ui.gameKey == reviewVm.keyOf(game)
    val reviewed = if (matches) ui.moves else emptyList()
    val selected = reviewed.getOrNull(game.index - 1)

    DisposableEffect(Unit) { onDispose { reviewVm.stop() } }

    val arrows = remember(selected, game.index) {
        val out = ArrayList<BoardArrow>()
        if (selected != null && selected.quality.ordinal >= MoveQuality.INACCURACY.ordinal) {
            // Show the better move from the position before the mistake.
            selected.bestUci?.let { Move.fromUci(it) }?.let { out += BoardArrow(it.from, it.to, Color(0xCC2ECC71)) }
        }
        out
    }
    val highlights = remember(selected) {
        selected?.let { s -> Move.fromUci(s.playedUci)?.let { mapOf(it.to to qualityColor(s.quality).copy(alpha = 0.45f)) } } ?: emptyMap()
    }
    val qualities = remember(reviewed) { reviewed.associate { it.ply to it.quality } }

    GmScaffold(title = "Game Review", subtitle = if (ui.complete && matches) "Stockfish 19 · depth ${ui.depth}" else null, onBack = onBack) { padding ->
        BoardScreenLayout(
            padding = padding,
            board = {
                BoardArea(
                    vm = gameVm, game = game, flipped = flipped, ply = ply,
                    evalWhite = if (matches) ui.evals.getOrNull(game.index) else null,
                    arrows = arrows, squareHighlights = highlights,
                    showCoordinates = settings.showCoordinates,
                )
            },
            controls = { BoardControls(gameVm, game) },
        ) {
            item(key = "control") {
                SectionCard(title = "Review") {
                    when {
                        game.moves.isEmpty() -> Text(
                            "There is no game to review yet. Play moves on the board or use the ⋮ menu to import a PGN, then start the review.",
                            style = MaterialTheme.typography.bodyMedium, color = GmColors.TextSecondary,
                        )
                        ui.running -> {
                            Text("Analysing ${game.moves.size} moves with Stockfish (depth ${ui.depth})…", style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(progress = { ui.progress }, modifier = Modifier.fillMaxWidth())
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(onClick = reviewVm::stop) { Icon(Icons.Filled.Stop, null); Spacer(Modifier.width(6.dp)); Text("Stop") }
                        }
                        else -> {
                            if (!matches && ui.moves.isNotEmpty()) {
                                Text("The game on the board changed since the last review.", style = MaterialTheme.typography.bodySmall, color = GmColors.Warning)
                                Spacer(Modifier.height(6.dp))
                            }
                            ui.error?.let { Text(it, color = GmColors.Danger, style = MaterialTheme.typography.bodySmall); Spacer(Modifier.height(6.dp)) }
                            Button(onClick = { reviewVm.start(game) }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Filled.PlayArrow, null)
                                Spacer(Modifier.width(6.dp))
                                Text(if (matches && ui.complete) "Run review again" else "Review this game (${game.moves.size} moves)")
                            }
                        }
                    }
                }
            }
            if (matches && ui.complete) {
                item(key = "accuracy") { AccuracyCard(ui) }
                item(key = "graph") {
                    SectionCard(title = "Evaluation graph") {
                        EvalGraph(ui.evals, game.index, onSelect = gameVm::goTo)
                    }
                }
                selected?.let { s ->
                    item(key = "selected") {
                        SectionCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "${game.moveNumberOf(s.ply)}${if (s.color == PieceColor.WHITE) "." else "..."} ${s.san}",
                                    fontFamily = MonoFamily, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
                                )
                                Chip("${s.quality.symbol} ${s.quality.label}", qualityColor(s.quality))
                                Spacer(Modifier.width(8.dp))
                                EvalBadge(s.scoreAfterWhite)
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(s.comment, style = MaterialTheme.typography.bodyMedium)
                            if (s.bestSan != null && s.quality.ordinal >= MoveQuality.INACCURACY.ordinal) {
                                Spacer(Modifier.height(6.dp))
                                Text("Green arrow: Stockfish's move (${s.bestSan}, ${Evaluation.formatShort(s.bestScoreWhite)}).", style = MaterialTheme.typography.bodySmall, color = GmColors.TextSecondary)
                            }
                        }
                    }
                }
                item(key = "moves") {
                    SectionCard(title = "Moves") { MoveList(game, onSelect = gameVm::goTo, qualities = qualities) }
                }
            }
        }
    }
}

@Composable
private fun AccuracyCard(ui: ReviewUi) {
    SectionCard(title = "Accuracy") {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AccuracyTile("White", ui.whiteAccuracy, Modifier.weight(1f), light = true)
            AccuracyTile("Black", ui.blackAccuracy, Modifier.weight(1f), light = false)
        }
        Spacer(Modifier.height(12.dp))
        val shown = listOf(
            MoveQuality.BRILLIANT, MoveQuality.GREAT, MoveQuality.BEST, MoveQuality.EXCELLENT, MoveQuality.GOOD,
            MoveQuality.BOOK, MoveQuality.INACCURACY, MoveQuality.MISTAKE, MoveQuality.MISSED_WIN, MoveQuality.BLUNDER,
        )
        val w = ui.counts(PieceColor.WHITE)
        val b = ui.counts(PieceColor.BLACK)
        for (q in shown) {
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${w[q] ?: 0}", modifier = Modifier.width(36.dp), fontFamily = MonoFamily, fontWeight = FontWeight.Bold)
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center) {
                    Text("${q.symbol}  ${q.label}", color = qualityColor(q), style = MaterialTheme.typography.labelLarge)
                }
                Text("${b[q] ?: 0}", modifier = Modifier.width(36.dp), fontFamily = MonoFamily, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun AccuracyTile(label: String, acc: Double?, modifier: Modifier, light: Boolean) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (light) Color(0xFFF1F1F1) else Color(0xFF2B2F36))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, color = if (light) Color(0xFF333333) else GmColors.TextSecondary, style = MaterialTheme.typography.labelMedium)
        Text(
            acc?.let { String.format(Locale.US, "%.1f", it) } ?: "–",
            color = if (light) Color(0xFF111111) else Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun EvalGraph(evals: List<Score>, currentIndex: Int, onSelect: (Int) -> Unit) {
    val values = evals.map { Evaluation.whiteBarFraction(it) }
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clip(RoundedCornerShape(10.dp))
            .pointerInput(values.size) {
                detectTapGestures { o ->
                    if (values.size > 1) onSelect(((o.x / size.width) * (values.size - 1)).toInt().coerceIn(0, values.size - 1))
                }
            },
    ) {
        drawRect(Color(0xFF2B2F36))
        if (values.size < 2) return@Canvas
        val step = size.width / (values.size - 1)
        val path = Path().apply {
            moveTo(0f, size.height)
            values.forEachIndexed { i, v -> lineTo(i * step, size.height * (1 - v)) }
            lineTo(size.width, size.height)
            close()
        }
        drawPath(path, Color(0xFFF1F1F1))
        drawLine(Color(0x66888888), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = 1.dp.toPx())
        val x = currentIndex.coerceIn(0, values.size - 1) * step
        drawRect(GmColors.Accent, Offset(x - 1.dp.toPx(), 0f), Size(2.dp.toPx(), size.height))
    }
}
