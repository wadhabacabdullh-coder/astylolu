package com.grandmaster.ai.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import com.grandmaster.ai.chess.Piece
import com.grandmaster.ai.chess.PieceColor
import com.grandmaster.ai.chess.PieceType

/**
 * Original vector chess pieces drawn on a 100x100 grid (Staunton-inspired silhouettes).
 */
object PieceGraphics {
    private const val BASE = "M24 80 Q20 80 20 84 L20 90 L80 90 L80 84 Q80 80 76 80 Z"

    private val shapes: Map<PieceType, String> = mapOf(
        PieceType.PAWN to
            "M50 16 A12 12 0 1 1 49.99 16 Z " +
            "M37 44 L63 44 L60 51 L40 51 Z " +
            "M41 51 C41 63 33 69 29 79 L71 79 C67 69 59 63 59 51 Z " + BASE,
        PieceType.ROOK to
            "M27 18 L37 18 L37 26 L44 26 L44 18 L56 18 L56 26 L63 26 L63 18 L73 18 L73 36 L67 42 L33 42 L27 36 Z " +
            "M34 44 L66 44 L69 79 L31 79 Z " + BASE,
        PieceType.KNIGHT to
            "M30 79 C30 66 35 58 44 51 C39 50 34 52 30 56 C26 59 21 57 21 52 C21 45 29 39 35 33 " +
            "C40 28 41 22 45 18 L47 10 L52 17 C66 19 76 31 76 50 C76 62 72 71 70 79 Z " + BASE,
        PieceType.BISHOP to
            "M50 9 A5.5 5.5 0 1 1 49.99 9 Z " +
            "M50 21 C40 29 33 39 35 50 C37 56 43 59 50 59 C57 59 63 56 65 50 C67 39 60 29 50 21 Z " +
            "M37 61 L63 61 L63 66 L37 66 Z " +
            "M41 66 C41 72 34 75 30 79 L70 79 C66 75 59 72 59 66 Z " + BASE,
        PieceType.QUEEN to
            "M23 34 L33 66 L67 66 L77 34 L64 51 L62 27 L54 49 L50 22 L46 49 L38 27 L36 51 Z " +
            "M23 34 m-5 0 a5 5 0 1 0 10 0 a5 5 0 1 0 -10 0 Z " +
            "M38 26 m-4.5 0 a4.5 4.5 0 1 0 9 0 a4.5 4.5 0 1 0 -9 0 Z " +
            "M50 20 m-5 0 a5 5 0 1 0 10 0 a5 5 0 1 0 -10 0 Z " +
            "M62 26 m-4.5 0 a4.5 4.5 0 1 0 9 0 a4.5 4.5 0 1 0 -9 0 Z " +
            "M77 34 m-5 0 a5 5 0 1 0 10 0 a5 5 0 1 0 -10 0 Z " +
            "M33 66 L67 66 L70 79 L30 79 Z " + BASE,
        PieceType.KING to
            "M46.5 6 L53.5 6 L53.5 12 L59.5 12 L59.5 18 L53.5 18 L53.5 27 L46.5 27 L46.5 18 L40.5 18 L40.5 12 L46.5 12 Z " +
            "M50 29 C37 26 21 32 23 47 C25 57 33 62 31 70 L69 70 C67 62 75 57 77 47 C79 32 63 26 50 29 Z " +
            "M30 70 L70 70 L72 79 L28 79 Z " + BASE,
    )

    /** Inner detail strokes (drawn in the outline colour) to give the pieces some depth. */
    private val details: Map<PieceType, String> = mapOf(
        PieceType.BISHOP to "M54 32 L45 45",
        PieceType.ROOK to "M33 42 L67 42",
        PieceType.KING to "M33 58 C44 54 56 54 67 58",
        PieceType.QUEEN to "M33 66 L67 66",
        PieceType.KNIGHT to "M36 32 C38 36 40 38 44 40",
    )

    private val pathCache = HashMap<PieceType, Path>()
    private val detailCache = HashMap<PieceType, Path>()

    private fun path(type: PieceType): Path = pathCache.getOrPut(type) {
        PathParser().parsePathString(shapes.getValue(type)).toPath()
    }

    private fun detail(type: PieceType): Path? = details[type]?.let { d ->
        detailCache.getOrPut(type) { PathParser().parsePathString(d).toPath() }
    }

    private val WhiteFill = Color(0xFFF7F5EE)
    private val WhiteStroke = Color(0xFF1B1B1B)
    private val BlackFill = Color(0xFF2A2A2E)
    private val BlackStroke = Color(0xFF0B0B0B)
    private val BlackHighlight = Color(0xFFBFC3C9)

    /** Draws [piece] centred in a square of side [size] whose top-left corner is [topLeft]. */
    fun DrawScope.drawPiece(piece: Piece, topLeft: Offset, size: Float, alpha: Float = 1f) {
        val s = size / 100f
        translate(topLeft.x, topLeft.y) {
            scale(s, s, pivot = Offset.Zero) {
                val p = path(piece.type)
                val white = piece.color == PieceColor.WHITE
                drawPath(p, if (white) WhiteFill else BlackFill, alpha = alpha, style = Fill)
                drawPath(
                    p, if (white) WhiteStroke else BlackStroke, alpha = alpha,
                    style = Stroke(width = 3.2f, join = StrokeJoin.Round),
                )
                detail(piece.type)?.let {
                    drawPath(
                        it, if (white) WhiteStroke else BlackHighlight, alpha = alpha * 0.85f,
                        style = Stroke(width = 2.4f, join = StrokeJoin.Round),
                    )
                }
                if (piece.type == PieceType.KNIGHT) {
                    drawCircle(if (white) WhiteStroke else BlackHighlight, radius = 3f, center = Offset(47f, 30f), alpha = alpha)
                }
            }
        }
    }
}

@Composable
fun PieceIcon(piece: Piece, size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        with(PieceGraphics) { drawPiece(piece, Offset.Zero, this@Canvas.size.minDimension) }
    }
}
