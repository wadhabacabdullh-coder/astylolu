package com.grandmaster.ai.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grandmaster.ai.analysis.MoveTag
import com.grandmaster.ai.analysis.InsightKind
import com.grandmaster.ai.analysis.MoveExplainer
import com.grandmaster.ai.analysis.TacticalInsight
import com.grandmaster.ai.analysis.ThreatInfo
import com.grandmaster.ai.engine.EngineStatus
import com.grandmaster.ai.ui.AnalysisUi
import com.grandmaster.ai.ui.LineUi
import com.grandmaster.ai.ui.theme.GmColors
import com.grandmaster.ai.ui.theme.MonoFamily
import java.util.Locale

val ArrowColors = listOf(
    Color(0xCC2ECC71), // best
    Color(0x995AB4F0), // second
    Color(0x80F2C14E), // third
    Color(0x66B39DDB),
    Color(0x66EF9A9A),
)

@Composable
fun EngineHeader(
    status: EngineStatus,
    ui: AnalysisUi,
    live: Boolean,
    onLiveChange: (Boolean) -> Unit,
    onAnalyze: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    showLiveToggle: Boolean = true,
) {
    SectionCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                val (statusText, color) = when (status) {
                    is EngineStatus.Ready -> "${status.info.name} · NNUE" to GmColors.Accent
                    is EngineStatus.Installing -> "Installing neural network ${(status.progress * 100).toInt()}%" to GmColors.Warning
                    EngineStatus.Starting -> "Starting Stockfish…" to GmColors.Warning
                    EngineStatus.Idle -> "Engine idle" to GmColors.TextSecondary
                    is EngineStatus.Failed -> "Engine error" to GmColors.Danger
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(color)
                    Spacer(Modifier.width(8.dp))
                    Text(statusText, style = MaterialTheme.typography.titleSmall)
                }
                Spacer(Modifier.height(2.dp))
                val stats = buildString {
                    if (ui.depth > 0) append("Depth ${ui.depth}")
                    if (ui.nps > 0) append(" · ${formatNps(ui.nps)}")
                    if (ui.nodes > 0) append(" · ${formatNodes(ui.nodes)} nodes")
                    if (ui.running) append(" · calculating") else if (ui.finished) append(" · done")
                }
                Text(stats.ifEmpty { if (live) "Waiting for a position" else "Live analysis is off" }, style = MaterialTheme.typography.bodySmall, color = GmColors.TextSecondary)
            }
            if (ui.running) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            if (showLiveToggle) {
                Spacer(Modifier.width(10.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("LIVE", style = MaterialTheme.typography.labelSmall, color = if (live) GmColors.Accent else GmColors.TextSecondary)
                    Switch(
                        checked = live, onCheckedChange = onLiveChange,
                        colors = SwitchDefaults.colors(checkedTrackColor = GmColors.AccentDim, checkedThumbColor = GmColors.Accent),
                    )
                }
            }
        }
        if (status is EngineStatus.Installing) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(progress = { status.progress }, modifier = Modifier.fillMaxWidth())
        }
        if (status is EngineStatus.Failed) {
            Spacer(Modifier.height(8.dp))
            Text(status.message, color = GmColors.Danger, style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = onRetry) { Text("Restart engine") }
        }
        ui.error?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, color = GmColors.Danger, style = MaterialTheme.typography.bodySmall)
        }
        if (!live && showLiveToggle) {
            Spacer(Modifier.height(8.dp))
            Button(onClick = onAnalyze, enabled = !ui.running, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.PlayArrow, null)
                Spacer(Modifier.width(6.dp))
                Text("Analyze this position")
            }
        }
    }
}

fun formatNps(nps: Long): String = when {
    nps >= 1_000_000 -> String.format(Locale.US, "%.1f Mn/s", nps / 1_000_000.0)
    nps >= 1_000 -> "${nps / 1000} kn/s"
    else -> "$nps n/s"
}

fun formatNodes(n: Long): String = when {
    n >= 1_000_000 -> String.format(Locale.US, "%.1fM", n / 1_000_000.0)
    n >= 1_000 -> "${n / 1000}k"
    else -> "$n"
}

/** The headline card: "BEST MOVE: Nf6!" with evaluation, reasons and principal variation. */
@Composable
fun BestMoveCard(line: LineUi, onPlay: (() -> Unit)?, modifier: Modifier = Modifier) {
    val e = line.explanation
    val annotation = MoveExplainer.annotation(e.tags, isBest = true)
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(GmColors.Surface)
            .border(1.5.dp, GmColors.Accent.copy(alpha = 0.6f), RoundedCornerShape(18.dp))
            .padding(16.dp)
            .animateContentSize(),
    ) {
        Text("BEST MOVE", style = MaterialTheme.typography.labelSmall, color = GmColors.Accent)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(e.san + annotation, fontSize = 34.sp, fontWeight = FontWeight.Bold, fontFamily = MonoFamily, modifier = Modifier.weight(1f))
            EvalBadge(line.scoreWhite, large = true)
        }
        Text(
            "Evaluation ${com.grandmaster.ai.analysis.Evaluation.format(line.scoreWhite)} · depth ${line.depth}",
            style = MaterialTheme.typography.bodySmall, color = GmColors.TextSecondary,
        )
        if (e.tags.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            TagRow(e.tags)
        }
        Spacer(Modifier.height(12.dp))
        Text("Why?", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(4.dp))
        for (r in e.reasons) Bullet(r)
        Spacer(Modifier.height(10.dp))
        PvText(e.pvSan)
        if (onPlay != null) {
            Spacer(Modifier.height(10.dp))
            Button(onClick = onPlay, modifier = Modifier.fillMaxWidth()) { Text("Play ${e.san}") }
        }
    }
}

@Composable
fun CandidateCard(line: LineUi, onPlay: (() -> Unit)?, modifier: Modifier = Modifier, startExpanded: Boolean = false) {
    var expanded by remember { mutableStateOf(startExpanded) }
    val e = line.explanation
    val color = ArrowColors.getOrElse(line.rank - 1) { GmColors.TextSecondary }
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(GmColors.Surface)
            .border(1.dp, GmColors.Outline, RoundedCornerShape(14.dp))
            .clickable { expanded = !expanded }
            .padding(12.dp)
            .animateContentSize(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "#${line.rank}", color = color.copy(alpha = 1f), fontWeight = FontWeight.Bold,
                modifier = Modifier.width(30.dp), fontFamily = MonoFamily,
            )
            Text(e.san, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, fontFamily = MonoFamily, modifier = Modifier.weight(1f))
            EvalBadge(line.scoreWhite)
            Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = if (expanded) "Collapse" else "Expand", tint = GmColors.TextSecondary)
        }
        Text(e.pvSan, style = MaterialTheme.typography.bodySmall, color = GmColors.TextSecondary, fontFamily = MonoFamily, maxLines = if (expanded) 4 else 1)
        AnimatedVisibility(expanded) {
            Column {
                Spacer(Modifier.height(8.dp))
                if (e.tags.isNotEmpty()) { TagRow(e.tags); Spacer(Modifier.height(6.dp)) }
                for (r in e.reasons) Bullet(r)
                if (onPlay != null) {
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(onClick = onPlay) { Text("Play ${e.san}") }
                }
            }
        }
    }
}

@Composable
fun TagRow(tags: Set<MoveTag>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (t in tags) Chip(t.label, tagColor(t))
    }
}

fun tagColor(t: MoveTag): Color = when (t) {
    MoveTag.MATE, MoveTag.SACRIFICE -> GmColors.Brilliant
    MoveTag.CHECK, MoveTag.KING_ATTACK, MoveTag.FORCING -> GmColors.Danger
    MoveTag.FORK, MoveTag.THREAT, MoveTag.DISCOVERED, MoveTag.WINS_MATERIAL, MoveTag.CAPTURE -> GmColors.Warning
    MoveTag.ONLY_MOVE, MoveTag.PARRIES_THREAT, MoveTag.ESCAPE, MoveTag.DEFENDS -> GmColors.Info
    else -> GmColors.Accent
}

@Composable
fun Bullet(text: String) {
    Row(Modifier.padding(vertical = 2.dp)) {
        Text("•", color = GmColors.Accent, modifier = Modifier.width(16.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun PvText(pv: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(GmColors.SurfaceHigh)
            .padding(10.dp),
    ) {
        Text("PRINCIPAL VARIATION", style = MaterialTheme.typography.labelSmall, color = GmColors.TextSecondary)
        Spacer(Modifier.height(4.dp))
        Text(pv, fontFamily = MonoFamily, fontSize = 13.sp)
    }
}

@Composable
fun ThreatBanner(threat: ThreatInfo, opponentName: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(GmColors.Warning.copy(alpha = 0.12f))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Warning, null, tint = GmColors.Warning)
        Spacer(Modifier.width(8.dp))
        Text(
            "Threat: if it were $opponentName's move, they could win material against your ${threat.pieceName} (≈${threat.material / 100} pawn${if (threat.material >= 200) "s" else ""}).",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
fun InsightCard(insight: TacticalInsight, onPlay: (() -> Unit)?, modifier: Modifier = Modifier) {
    val color = when (insight.kind) {
        InsightKind.FORCED_MATE -> GmColors.Danger
        InsightKind.SACRIFICE -> GmColors.Brilliant
        InsightKind.FORCING -> GmColors.Warning
        InsightKind.DEFENSIVE_RESOURCE -> GmColors.Info
        InsightKind.UNEXPECTED -> GmColors.Gold
        InsightKind.KING_ATTACK -> Color(0xFFFF7043)
        InsightKind.TACTICAL_SHOT -> GmColors.Accent
    }
    var expanded by remember { mutableStateOf(insight.rank == 1) }
    val e = insight.explanation
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(GmColors.Surface)
            .border(1.5.dp, color.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
            .clickable { expanded = !expanded }
            .padding(14.dp)
            .animateContentSize(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Chip(insight.kind.title.uppercase(), color)
            Spacer(Modifier.width(8.dp))
            Text("Engine rank #${insight.rank}", style = MaterialTheme.typography.labelMedium, color = GmColors.TextSecondary, modifier = Modifier.weight(1f))
            EvalBadge(e.scoreWhite)
        }
        Spacer(Modifier.height(8.dp))
        Text(e.san + MoveExplainer.annotation(e.tags, insight.rank == 1), fontSize = 26.sp, fontWeight = FontWeight.Bold, fontFamily = MonoFamily)
        Text(insight.headline, style = MaterialTheme.typography.bodyMedium, color = GmColors.TextSecondary)
        if (expanded) {
            Spacer(Modifier.height(8.dp))
            for (r in e.reasons) Bullet(r)
            Spacer(Modifier.height(8.dp))
            PvText(e.pvSan)
            if (onPlay != null) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onPlay) { Text("Play ${e.san}") }
            }
        }
    }
}
