package com.grandmaster.ai.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grandmaster.ai.analysis.Evaluation
import com.grandmaster.ai.analysis.MoveQuality
import com.grandmaster.ai.chess.Game
import com.grandmaster.ai.engine.Score
import com.grandmaster.ai.ui.theme.GmColors
import com.grandmaster.ai.ui.theme.MonoFamily

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(GmColors.Surface)
            .border(1.dp, GmColors.Outline, RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        if (title != null || trailing != null) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (title != null) Text(
                    title.uppercase(), style = MaterialTheme.typography.labelSmall,
                    color = GmColors.TextSecondary, modifier = Modifier.weight(1f),
                )
                trailing?.invoke()
            }
            Spacer(Modifier.height(10.dp))
        }
        content()
    }
}

@Composable
fun Chip(text: String, color: Color = GmColors.Accent, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.16f))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text, color = color, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun EvalBadge(scoreWhite: Score?, modifier: Modifier = Modifier, large: Boolean = false) {
    val text = scoreWhite?.let { Evaluation.formatShort(it) } ?: "…"
    val whiteBetter = scoreWhite?.toComparable()?.let { it >= 0 } ?: true
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (whiteBetter) Color(0xFFF1F1F1) else Color(0xFF2B2F36))
            .padding(horizontal = if (large) 12.dp else 8.dp, vertical = if (large) 6.dp else 3.dp),
    ) {
        Text(
            text,
            color = if (whiteBetter) Color(0xFF111111) else Color(0xFFF1F1F1),
            fontFamily = MonoFamily, fontWeight = FontWeight.Bold,
            fontSize = if (large) 18.sp else 13.sp,
        )
    }
}

/** Vertical evaluation bar; White's share grows from the bottom (or top if flipped). */
@Composable
fun EvalBar(scoreWhite: Score?, flipped: Boolean, modifier: Modifier = Modifier, width: Dp = 18.dp) {
    val target = scoreWhite?.let { Evaluation.whiteBarFraction(it) } ?: 0.5f
    val frac by animateFloatAsState(target, tween(450), label = "eval")
    BoxWithConstraints(
        modifier
            .width(width)
            .fillMaxHeight()
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF2B2F36)),
    ) {
        val h = maxHeight
        Box(
            Modifier
                .fillMaxWidth()
                .height(h * frac)
                .align(if (flipped) Alignment.TopCenter else Alignment.BottomCenter)
                .background(Color(0xFFF1F1F1)),
        )
        val label = scoreWhite?.let { Evaluation.formatShort(it).removePrefix("+") } ?: ""
        val whiteAhead = frac >= 0.5f
        Text(
            label, fontSize = 8.sp, fontFamily = MonoFamily, fontWeight = FontWeight.Bold,
            color = if (whiteAhead) Color(0xFF222222) else Color(0xFFEEEEEE),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .align(
                    if (whiteAhead != flipped) Alignment.BottomCenter else Alignment.TopCenter
                )
                .padding(vertical = 3.dp),
        )
    }
}

/** Horizontal evaluation bar used under the board on narrow layouts. */
@Composable
fun EvalBarHorizontal(scoreWhite: Score?, modifier: Modifier = Modifier) {
    val target = scoreWhite?.let { Evaluation.whiteBarFraction(it) } ?: 0.5f
    val frac by animateFloatAsState(target, tween(450), label = "evalh")
    Box(
        modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF2B2F36)),
    ) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(frac).background(Color(0xFFF1F1F1)))
    }
}

/** Wrapping list of numbered moves; tap a move to jump to it. */
@Composable
fun MoveList(
    game: Game,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    qualities: Map<Int, MoveQuality> = emptyMap(),
) {
    if (game.moves.isEmpty()) {
        Text("No moves yet. Make a move on the board.", color = GmColors.TextSecondary, style = MaterialTheme.typography.bodySmall, modifier = modifier)
        return
    }
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(2.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        game.moves.forEachIndexed { i, pm ->
            val white = game.isWhiteMove(i)
            if (white || i == 0) {
                Text(
                    "${game.moveNumberOf(i)}${if (white) "." else "..."}",
                    color = GmColors.TextSecondary, fontFamily = MonoFamily, fontSize = 13.sp,
                    modifier = Modifier.padding(start = 6.dp, end = 2.dp, top = 4.dp),
                )
            }
            val current = game.index == i + 1
            val q = qualities[i]
            Row(
                Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (current) GmColors.AccentSoft else Color.Transparent)
                    .clickable { onSelect(i + 1) }
                    .padding(horizontal = 6.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    pm.san, fontFamily = MonoFamily, fontSize = 14.sp,
                    fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                    color = if (current) GmColors.Accent else GmColors.TextPrimary,
                )
                if (q != null && q.symbol.isNotEmpty() && q != MoveQuality.BOOK && q != MoveQuality.GOOD && q != MoveQuality.EXCELLENT) {
                    Text(q.symbol, color = qualityColor(q), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 2.dp))
                }
            }
        }
    }
}

fun qualityColor(q: MoveQuality): Color = when (q) {
    MoveQuality.BRILLIANT -> GmColors.Brilliant
    MoveQuality.GREAT -> GmColors.Great
    MoveQuality.BEST -> GmColors.Accent
    MoveQuality.EXCELLENT -> Color(0xFF81C784)
    MoveQuality.GOOD -> Color(0xFF9CCC65)
    MoveQuality.BOOK -> Color(0xFFBCAAA4)
    MoveQuality.INACCURACY -> Color(0xFFF7C948)
    MoveQuality.MISTAKE -> GmColors.Warning
    MoveQuality.BLUNDER -> GmColors.Danger
    MoveQuality.MISSED_WIN -> Color(0xFFE57373)
}

@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(8.dp).clip(CircleShape).background(color))
}

@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = GmColors.TextSecondary, textAlign = TextAlign.Center, modifier = Modifier.padding(24.dp))
    }
}
