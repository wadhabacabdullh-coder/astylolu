package com.grandmaster.ai.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.grandmaster.ai.ui.AnalysisMode
import com.grandmaster.ai.ui.AnalysisViewModel
import com.grandmaster.ai.ui.GameViewModel
import com.grandmaster.ai.ui.components.BoardArea
import com.grandmaster.ai.ui.components.BoardControls
import com.grandmaster.ai.ui.components.CandidateCard
import com.grandmaster.ai.ui.components.EngineHeader
import com.grandmaster.ai.ui.components.InsightCard
import com.grandmaster.ai.ui.components.SectionCard
import com.grandmaster.ai.ui.components.ThreatBanner
import com.grandmaster.ai.ui.theme.GmColors

@Composable
fun HikaruScreen(gameVm: GameViewModel, analysisVm: AnalysisViewModel, onBack: () -> Unit) {
    AttachAnalysis(gameVm, analysisVm, AnalysisMode.HIKARU)
    val game by gameVm.game.collectAsStateWithLifecycle()
    val flipped by gameVm.flipped.collectAsStateWithLifecycle()
    val ply by gameVm.ply.collectAsStateWithLifecycle()
    val settings by gameVm.settings.collectAsStateWithLifecycle()
    val ui by analysisVm.ui.collectAsStateWithLifecycle()
    val live by analysisVm.live.collectAsStateWithLifecycle()
    val status by analysisVm.engineStatus.collectAsStateWithLifecycle()
    val current = ui.fen == game.current.toFen()

    GmScaffold(title = "Hikaru Mode", subtitle = "Tactics · sacrifices · forcing lines", onBack = onBack) { padding ->
        BoardScreenLayout(
            padding = padding,
            board = {
                BoardArea(
                    vm = gameVm, game = game, flipped = flipped, ply = ply,
                    evalWhite = if (current) ui.bestScoreWhite else null,
                    arrows = arrowsFor(ui, game, settings.showArrows, max = 5),
                    showCoordinates = settings.showCoordinates,
                )
            },
            controls = { BoardControls(gameVm, game) },
        ) {
            item(key = "engine") {
                EngineHeader(
                    status = status, ui = ui, live = live,
                    onLiveChange = analysisVm::setLive, onAnalyze = analysisVm::analyzeNow, onRetry = analysisVm::retryEngine,
                )
            }
            if (current) {
                ui.threat?.let { t ->
                    item(key = "threat") { ThreatBanner(t, game.current.sideToMove.opposite.displayName) }
                }
                if (ui.insights.isNotEmpty()) {
                    item(key = "ins-title") {
                        Text("TACTICAL OPPORTUNITIES", style = MaterialTheme.typography.labelSmall, color = GmColors.TextSecondary, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
                    }
                    items(ui.insights, key = { "ins-${it.kind}-${it.rank}" }) { ins ->
                        InsightCard(ins, onPlay = { gameVm.onMove(ins.explanation.move) })
                    }
                } else if (ui.lines.isNotEmpty() && !ui.running) {
                    item(key = "no-ins") {
                        SectionCard {
                            Text(
                                "No sacrifice, forced mate or forcing sequence stands out here. Stockfish's top lines are positional; they are listed below.",
                                style = MaterialTheme.typography.bodyMedium, color = GmColors.TextSecondary,
                            )
                        }
                    }
                }
                if (ui.lines.isNotEmpty()) {
                    item(key = "lines-title") {
                        Text("ALL ENGINE LINES (MULTIPV ${ui.lines.size})", style = MaterialTheme.typography.labelSmall, color = GmColors.TextSecondary, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
                    }
                    items(ui.lines, key = { "line-${it.rank}" }) { l ->
                        CandidateCard(l, onPlay = { gameVm.onMove(l.explanation.move) }, startExpanded = l.rank == 1)
                    }
                }
            }
            item(key = "disclaimer") {
                Text(
                    "Hikaru Mode highlights tactical and aggressive ideas found by Stockfish 19. It is inspired by a fast, tactical playing style; it is not affiliated with or endorsed by Hikaru Nakamura and does not imitate his personal choices.",
                    style = MaterialTheme.typography.bodySmall, color = GmColors.TextSecondary,
                    modifier = Modifier.padding(4.dp),
                )
            }
        }
    }
}
