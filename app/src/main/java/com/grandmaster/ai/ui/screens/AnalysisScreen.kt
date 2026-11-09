package com.grandmaster.ai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.grandmaster.ai.chess.Game
import com.grandmaster.ai.chess.Move
import com.grandmaster.ai.data.OpeningBook
import com.grandmaster.ai.ui.AnalysisMode
import com.grandmaster.ai.ui.AnalysisUi
import com.grandmaster.ai.ui.AnalysisViewModel
import com.grandmaster.ai.ui.GameViewModel
import com.grandmaster.ai.ui.components.ArrowColors
import com.grandmaster.ai.ui.components.BestMoveCard
import com.grandmaster.ai.ui.components.BoardArea
import com.grandmaster.ai.ui.components.BoardArrow
import com.grandmaster.ai.ui.components.BoardControls
import com.grandmaster.ai.ui.components.CandidateCard
import com.grandmaster.ai.ui.components.Chip
import com.grandmaster.ai.ui.components.EngineHeader
import com.grandmaster.ai.ui.components.MoveList
import com.grandmaster.ai.ui.components.SectionCard
import com.grandmaster.ai.ui.theme.GmColors

fun arrowsFor(ui: AnalysisUi, game: Game, show: Boolean, max: Int = 3): List<BoardArrow> {
    if (!show || ui.fen != game.current.toFen()) return emptyList()
    return ui.lines.take(max).mapNotNull { l ->
        val m = Move.fromUci(l.pvUci.first()) ?: return@mapNotNull null
        BoardArrow(m.from, m.to, ArrowColors.getOrElse(l.rank - 1) { ArrowColors.last() }, if (l.rank == 1) 1f else 0.75f)
    }.reversed()
}

/** Keeps the analysis view model attached to the shared game while this screen is visible. */
@Composable
fun AttachAnalysis(gameVm: GameViewModel, analysisVm: AnalysisViewModel, mode: AnalysisMode) {
    val game by gameVm.game.collectAsStateWithLifecycle()
    val thinking by gameVm.engineThinking.collectAsStateWithLifecycle()
    LaunchedEffect(game) { analysisVm.setGame(game) }
    LaunchedEffect(thinking) { analysisVm.setPaused(thinking) }
    LaunchedEffect(mode) { analysisVm.setMode(mode) }
    DisposableEffect(Unit) {
        analysisVm.setActive(true)
        onDispose { analysisVm.setActive(false) }
    }
}

@Composable
fun AnalysisScreen(gameVm: GameViewModel, analysisVm: AnalysisViewModel, onBack: () -> Unit) {
    AttachAnalysis(gameVm, analysisVm, AnalysisMode.STANDARD)
    val game by gameVm.game.collectAsStateWithLifecycle()
    val flipped by gameVm.flipped.collectAsStateWithLifecycle()
    val ply by gameVm.ply.collectAsStateWithLifecycle()
    val settings by gameVm.settings.collectAsStateWithLifecycle()
    val playMode by gameVm.playMode.collectAsStateWithLifecycle()
    val thinking by gameVm.engineThinking.collectAsStateWithLifecycle()
    val ui by analysisVm.ui.collectAsStateWithLifecycle()
    val live by analysisVm.live.collectAsStateWithLifecycle()
    val status by analysisVm.engineStatus.collectAsStateWithLifecycle()
    val current = ui.fen == game.current.toFen()
    val opening = remember(game) { OpeningBook.sansOf(game)?.let { OpeningBook.identify(it) } }
    val end = remember(game) { game.gameEnd() }

    GmScaffold(
        title = if (playMode != null) "Play vs Stockfish" else "Analysis Board",
        subtitle = opening?.let { "${it.eco} · ${it.name}" },
        onBack = onBack,
    ) { padding ->
        BoardScreenLayout(
            padding = padding,
            board = {
                BoardArea(
                    vm = gameVm, game = game, flipped = flipped, ply = ply,
                    evalWhite = if (current) ui.bestScoreWhite else null,
                    arrows = arrowsFor(ui, game, settings.showArrows),
                    showCoordinates = settings.showCoordinates,
                    movableColor = playMode?.userColor,
                    interactive = !thinking,
                )
            },
            controls = { BoardControls(gameVm, game, playMode = playMode != null) },
        ) {
            playMode?.let { pm ->
                item(key = "play") {
                    SectionCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("You play ${pm.userColor.displayName}", style = MaterialTheme.typography.titleMedium)
                                Text("Stockfish 19 · ${pm.strengthLabel}", style = MaterialTheme.typography.bodySmall, color = GmColors.TextSecondary)
                            }
                            if (thinking) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("Thinking…", style = MaterialTheme.typography.bodySmall)
                            }
                            TextButton(onClick = { gameVm.stopPlay() }) { Text("End game") }
                        }
                    }
                }
            }
            if (end != null) {
                item(key = "end") {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(GmColors.AccentSoft)
                            .padding(14.dp),
                    ) {
                        Text("${end.description} · ${game.result().pgn}", style = MaterialTheme.typography.titleMedium, color = GmColors.Accent)
                    }
                }
            }
            item(key = "engine") {
                EngineHeader(
                    status = status, ui = ui, live = live,
                    onLiveChange = analysisVm::setLive, onAnalyze = analysisVm::analyzeNow,
                    onRetry = analysisVm::retryEngine,
                )
            }
            if (current && ui.lines.isNotEmpty()) {
                val best = ui.lines.first()
                val canPlay = playMode == null
                item(key = "best") {
                    BestMoveCard(best, onPlay = if (canPlay) ({ gameVm.onMove(best.explanation.move) }) else null)
                }
                val rest = ui.lines.drop(1)
                if (rest.isNotEmpty()) {
                    item(key = "cand-title") {
                        Text("OTHER CANDIDATE MOVES", style = MaterialTheme.typography.labelSmall, color = GmColors.TextSecondary, modifier = Modifier.padding(top = 4.dp, start = 4.dp))
                    }
                    items(rest, key = { "cand-${it.rank}" }) { l ->
                        CandidateCard(l, onPlay = if (canPlay) ({ gameVm.onMove(l.explanation.move) }) else null)
                    }
                }
            }
            item(key = "moves") {
                SectionCard(title = "Moves", trailing = { opening?.let { Chip(it.eco) } }) {
                    MoveList(game, onSelect = gameVm::goTo)
                }
            }
        }
    }
}
