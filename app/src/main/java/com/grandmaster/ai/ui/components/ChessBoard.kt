package com.grandmaster.ai.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grandmaster.ai.chess.Move
import com.grandmaster.ai.chess.Piece
import com.grandmaster.ai.chess.PieceColor
import com.grandmaster.ai.chess.PieceType
import com.grandmaster.ai.chess.Position
import com.grandmaster.ai.chess.Square
import com.grandmaster.ai.ui.components.PieceGraphics.drawPiece
import com.grandmaster.ai.ui.theme.GmColors
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

data class BoardArrow(val from: Int, val to: Int, val color: Color, val widthFactor: Float = 1f)

private data class DragState(val from: Int, val pointer: Offset)

/**
 * Interactive chessboard: drag-and-drop or tap-to-move, legal move hints,
 * last-move/check highlights, engine arrows and move animation.
 *
 * @param ply a counter that increases by one when a single move is played; used to animate it.
 * @param movableColor which side the user may move (null = whichever side is to move).
 */
@Composable
fun ChessBoard(
    position: Position,
    modifier: Modifier = Modifier,
    flipped: Boolean = false,
    lastMove: Move? = null,
    ply: Int = 0,
    arrows: List<BoardArrow> = emptyList(),
    squareHighlights: Map<Int, Color> = emptyMap(),
    showCoordinates: Boolean = true,
    interactive: Boolean = true,
    movableColor: PieceColor? = null,
    onMove: (Move) -> Unit = {},
) {
    var selected by remember(position) { mutableStateOf<Int?>(null) }
    var drag by remember { mutableStateOf<DragState?>(null) }
    var pendingPromotion by remember(position) { mutableStateOf<Pair<Int, Int>?>(null) }
    var droppedMove by remember { mutableStateOf<Move?>(null) }
    var prevPly by remember { mutableIntStateOf(ply) }
    val anim = remember { Animatable(1f) }
    var animMove by remember { mutableStateOf<Move?>(null) }
    val textMeasurer = rememberTextMeasurer()

    LaunchedEffect(ply) {
        val shouldAnimate = ply == prevPly + 1 && lastMove != null && lastMove != droppedMove
        prevPly = ply
        droppedMove = null
        if (shouldAnimate) {
            animMove = lastMove
            anim.snapTo(0f)
            anim.animateTo(1f, tween(durationMillis = 190, easing = FastOutSlowInEasing))
        } else {
            animMove = null
            anim.snapTo(1f)
        }
    }

    fun canPick(sq: Int): Boolean {
        if (!interactive) return false
        val p = position[sq] ?: return false
        return p.color == position.sideToMove && (movableColor == null || p.color == movableColor)
    }

    fun attemptMove(from: Int, to: Int, dropped: Boolean) {
        val candidates = position.legalMoves().filter { it.from == from && it.to == to }
        when {
            candidates.isEmpty() -> selected = if (canPick(to) && to != from) to else if (dropped) null else selected.takeIf { it != from }
            candidates.any { it.promotion != null } -> { pendingPromotion = from to to; selected = null }
            else -> {
                if (dropped) droppedMove = candidates.first()
                selected = null
                onMove(candidates.first())
            }
        }
    }

    Box(
        modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .semantics { contentDescription = "Chess board, ${position.sideToMove.displayName} to move" }
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(position, flipped, interactive, movableColor) {
                    fun squareAt(o: Offset): Int {
                        val sqSize = size.width / 8f
                        val col = (o.x / sqSize).toInt().coerceIn(0, 7)
                        val row = (o.y / sqSize).toInt().coerceIn(0, 7)
                        val file = if (flipped) 7 - col else col
                        val rank = if (flipped) row else 7 - row
                        return Square.of(file, rank)
                    }
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val sq = squareAt(down.position)
                        val pickable = canPick(sq)
                        var dragging = false
                        var last = down.position
                        while (true) {
                            val ev = awaitPointerEvent()
                            val change = ev.changes.firstOrNull { it.id == down.id } ?: break
                            last = change.position
                            if (pickable && !dragging && (change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                                dragging = true
                                selected = sq
                            }
                            if (dragging) {
                                drag = DragState(sq, change.position)
                                change.consume()
                            }
                            if (!change.pressed) break
                        }
                        if (dragging) {
                            drag = null
                            val target = squareAt(last)
                            if (target != sq) attemptMove(sq, target, dropped = true) else selected = sq
                        } else {
                            val sel = selected
                            when {
                                sel != null && sel != sq && position.legalMovesFrom(sel).any { it.to == sq } -> attemptMove(sel, sq, dropped = false)
                                pickable -> selected = if (sel == sq) null else sq
                                else -> selected = null
                            }
                        }
                    }
                }
        ) {
            val sq = size.width / 8f
            fun topLeftOf(square: Int): Offset {
                val f = Square.file(square); val r = Square.rank(square)
                val col = if (flipped) 7 - f else f
                val row = if (flipped) r else 7 - r
                return Offset(col * sq, row * sq)
            }

            // Squares
            for (s in 0..63) {
                val tl = topLeftOf(s)
                drawRect(if (Square.isLight(s)) GmColors.LightSquare else GmColors.DarkSquare, tl, Size(sq, sq))
            }
            lastMove?.let { m ->
                drawRect(GmColors.LastMove, topLeftOf(m.from), Size(sq, sq))
                drawRect(GmColors.LastMove, topLeftOf(m.to), Size(sq, sq))
            }
            for ((s, c) in squareHighlights) drawRect(c, topLeftOf(s), Size(sq, sq))
            selected?.let { drawRect(GmColors.Selected, topLeftOf(it), Size(sq, sq)) }
            if (position.isInCheck()) {
                val k = position.kingSquare(position.sideToMove)
                if (k != Square.NONE) {
                    val c = topLeftOf(k) + Offset(sq / 2, sq / 2)
                    drawCircle(
                        Brush.radialGradient(
                            listOf(GmColors.Check, GmColors.Check.copy(alpha = 0.55f), Color.Transparent),
                            center = c, radius = sq * 0.72f,
                        ),
                        radius = sq * 0.72f, center = c,
                    )
                }
            }

            if (showCoordinates) drawCoordinates(textMeasurer, sq, flipped)

            // Legal move hints
            val hintFrom = drag?.from ?: selected
            if (hintFrom != null) {
                for (m in position.legalMovesFrom(hintFrom)) {
                    val c = topLeftOf(m.to) + Offset(sq / 2, sq / 2)
                    if (position.isCapture(m)) {
                        drawCircle(GmColors.MoveHint, radius = sq * 0.46f, center = c, style = Stroke(width = sq * 0.09f))
                    } else {
                        drawCircle(GmColors.MoveHint, radius = sq * 0.16f, center = c)
                    }
                }
            }
            drag?.let { d ->
                val s2 = run {
                    val col = (d.pointer.x / sq).toInt().coerceIn(0, 7)
                    val row = (d.pointer.y / sq).toInt().coerceIn(0, 7)
                    Offset(col * sq, row * sq)
                }
                drawRect(Color.White.copy(alpha = 0.25f), s2, Size(sq, sq), style = Stroke(width = sq * 0.06f))
            }

            // Pieces
            val am = animMove
            val progress = anim.value
            for (s in 0..63) {
                val piece = position[s] ?: continue
                if (drag?.from == s) {
                    drawPiece(piece, topLeftOf(s), sq, alpha = 0.3f)
                    continue
                }
                var tl = topLeftOf(s)
                if (am != null && progress < 1f) {
                    if (s == am.to) {
                        val from = topLeftOf(am.from)
                        tl = from + (tl - from) * progress
                    } else if (piece.type == PieceType.ROOK && isCastlingRookTarget(am, s, position)) {
                        val rookFrom = castlingRookFrom(am)
                        val from = topLeftOf(rookFrom)
                        tl = from + (tl - from) * progress
                    }
                }
                drawPiece(piece, tl, sq)
            }

            // Arrows
            for (a in arrows) drawArrow(topLeftOf(a.from) + Offset(sq / 2, sq / 2), topLeftOf(a.to) + Offset(sq / 2, sq / 2), sq, a)

            // Dragged piece on top
            drag?.let { d ->
                position[d.from]?.let { p ->
                    val big = sq * 1.25f
                    drawPiece(p, d.pointer - Offset(big / 2, big * 0.75f), big)
                }
            }
        }
    }

    pendingPromotion?.let { (from, to) ->
        PromotionDialog(
            color = position.sideToMove,
            onPick = { type ->
                pendingPromotion = null
                val m = Move(from, to, type)
                if (position.isLegal(m)) onMove(m)
            },
            onDismiss = { pendingPromotion = null },
        )
    }
}


private fun isCastlingRookTarget(m: Move, sq: Int, pos: Position): Boolean {
    val king = pos[m.to] ?: return false
    if (king.type != PieceType.KING || kotlin.math.abs(Square.file(m.to) - Square.file(m.from)) != 2) return false
    val rank = Square.rank(m.to)
    return sq == Square.of(if (Square.file(m.to) == 6) 5 else 3, rank)
}

private fun castlingRookFrom(m: Move): Int {
    val rank = Square.rank(m.to)
    return Square.of(if (Square.file(m.to) == 6) 7 else 0, rank)
}

private fun DrawScope.drawCoordinates(tm: androidx.compose.ui.text.TextMeasurer, sq: Float, flipped: Boolean) {
    val style = TextStyle(fontSize = (sq / 5.2f / density).sp, fontWeight = FontWeight.SemiBold)
    for (i in 0..7) {
        // File letters along the bottom row
        val file = if (flipped) 7 - i else i
        val fileSq = Square.of(file, if (flipped) 7 else 0)
        val fc = if (Square.isLight(fileSq)) GmColors.DarkSquare else GmColors.LightSquare
        val fl = tm.measure("${'a' + file}", style.copy(color = fc))
        drawText(fl, topLeft = Offset(i * sq + sq - fl.size.width - sq * 0.06f, 8 * sq - fl.size.height - sq * 0.02f))
        // Rank numbers along the left column
        val rank = if (flipped) i else 7 - i
        val rankSq = Square.of(if (flipped) 7 else 0, rank)
        val rc = if (Square.isLight(rankSq)) GmColors.DarkSquare else GmColors.LightSquare
        val rl = tm.measure("${rank + 1}", style.copy(color = rc))
        drawText(rl, topLeft = Offset(sq * 0.06f, i * sq + sq * 0.03f))
    }
}

private fun DrawScope.drawArrow(from: Offset, to: Offset, sq: Float, arrow: BoardArrow) {
    val angle = atan2(to.y - from.y, to.x - from.x)
    val shaft = sq * 0.16f * arrow.widthFactor
    val headLen = sq * 0.42f
    val headW = sq * 0.38f * arrow.widthFactor.coerceAtLeast(0.8f)
    val start = from + Offset(cos(angle), sin(angle)) * (sq * 0.15f)
    val tip = to
    val base = tip - Offset(cos(angle), sin(angle)) * headLen
    val nx = -sin(angle); val ny = cos(angle)
    val p = Path().apply {
        moveTo(start.x + nx * shaft / 2, start.y + ny * shaft / 2)
        lineTo(base.x + nx * shaft / 2, base.y + ny * shaft / 2)
        lineTo(base.x + nx * headW, base.y + ny * headW)
        lineTo(tip.x, tip.y)
        lineTo(base.x - nx * headW, base.y - ny * headW)
        lineTo(base.x - nx * shaft / 2, base.y - ny * shaft / 2)
        lineTo(start.x - nx * shaft / 2, start.y - ny * shaft / 2)
        close()
    }
    drawPath(p, arrow.color)
}

@Composable
fun PromotionDialog(color: PieceColor, onPick: (PieceType) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Promote pawn to") },
        text = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                for (t in listOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT)) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(GmColors.SurfaceHigh)
                            .clickable { onPick(t) }
                            .padding(6.dp)
                            .semantics { contentDescription = t.displayName },
                    ) {
                        PieceIcon(Piece.of(color, t), 52.dp)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
