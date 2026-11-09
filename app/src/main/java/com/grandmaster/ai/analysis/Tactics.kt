package com.grandmaster.ai.analysis

import com.grandmaster.ai.chess.Move
import com.grandmaster.ai.chess.Piece
import com.grandmaster.ai.chess.PieceColor
import com.grandmaster.ai.chess.PieceType
import com.grandmaster.ai.chess.Position
import com.grandmaster.ai.chess.Square

/** Static board facts used to explain engine moves. These are exact board computations, not guesses. */
object Tactics {

    fun value(type: PieceType): Int = when (type) {
        PieceType.PAWN -> 100
        PieceType.KNIGHT -> 300
        PieceType.BISHOP -> 300
        PieceType.ROOK -> 500
        PieceType.QUEEN -> 900
        PieceType.KING -> 10_000
    }

    /**
     * Static exchange evaluation of [move] for the side to move in [pos]:
     * the material balance after the best sequence of captures/recaptures on
     * the destination square (positive = the mover gains material).
     */
    fun see(pos: Position, move: Move): Int {
        val mover = pos[move.from] ?: return 0
        val target = move.to
        val gain = IntArray(40)
        var d = 0
        gain[0] = pos.capturedPiece(move)?.let { value(it.type) } ?: 0
        if (move.promotion != null) gain[0] += value(move.promotion) - value(PieceType.PAWN)
        var occupant = value(move.promotion ?: mover.type)
        var board = pos.withPiece(target, Piece.of(mover.color, move.promotion ?: mover.type)).without(move.from)
        if (pos.isEnPassant(move)) board = board.without(Square.of(Square.file(move.to), Square.rank(move.from)))
        var side = mover.color.opposite
        while (d < 38) {
            val attackers = board.attackers(target, side)
            if (attackers.isEmpty()) break
            val from = attackers.minBy { value(board[it]!!.type) }
            val attacker = board[from]!!
            // The king may not capture onto a square that is still defended.
            if (attacker.type == PieceType.KING && board.attackers(target, side.opposite).isNotEmpty()) break
            d++
            gain[d] = occupant - gain[d - 1]
            occupant = value(attacker.type)
            board = board.withPiece(target, attacker).without(from)
            side = side.opposite
        }
        while (d > 0) {
            gain[d - 1] = -maxOf(-gain[d - 1], gain[d])
            d--
        }
        return gain[0]
    }

    /** Best material the side to move in [pos] can win by a capture (by SEE), with the target square. */
    fun bestCapture(pos: Position): Pair<Int, Int>? {
        var best: Pair<Int, Int>? = null
        for (m in pos.legalMoves()) {
            if (!pos.isCapture(m)) continue
            val v = see(pos, m)
            if (v > 0 && (best == null || v > best.first)) best = v to m.to
        }
        return best
    }

    /** Pieces of [color] that are attacked and would lose material if captured (hanging/under-defended). */
    fun hangingPieces(pos: Position, color: PieceColor): List<Pair<Int, Int>> {
        // Look at it as if the opponent were to move.
        val view = if (pos.sideToMove == color) pos.nullMove() else pos
        if (view.isInCheck(view.sideToMove.opposite)) return emptyList()
        val out = ArrayList<Pair<Int, Int>>()
        for ((sq, p) in view.pieces()) {
            if (p.color != color || p.type == PieceType.KING) continue
            var worst = 0
            for (m in view.legalMoves()) if (m.to == sq) worst = maxOf(worst, see(view, m))
            if (worst > 0) out += sq to worst
        }
        return out
    }

    fun isDefended(pos: Position, sq: Int, by: PieceColor): Boolean = pos.attackers(sq, by).isNotEmpty()

    fun isPassedPawn(pos: Position, sq: Int): Boolean {
        val p = pos[sq] ?: return false
        if (p.type != PieceType.PAWN) return false
        val f = Square.file(sq)
        val r = Square.rank(sq)
        val dir = if (p.color == PieceColor.WHITE) 1 else -1
        var rr = r + dir
        while (rr in 0..7) {
            for (ff in (f - 1)..(f + 1)) {
                if (ff !in 0..7) continue
                val q = pos[Square.of(ff, rr)]
                if (q != null && q.type == PieceType.PAWN && q.color != p.color) return false
            }
            rr += dir
        }
        return true
    }

    fun fileHasPawn(pos: Position, file: Int, color: PieceColor?): Boolean {
        for (r in 0..7) {
            val p = pos[Square.of(file, r)] ?: continue
            if (p.type == PieceType.PAWN && (color == null || p.color == color)) return true
        }
        return false
    }

    fun kingZone(pos: Position, color: PieceColor): Set<Int> {
        val k = pos.kingSquare(color)
        if (k == Square.NONE) return emptySet()
        val out = HashSet<Int>()
        for (df in -1..1) for (dr in -1..1) {
            val f = Square.file(k) + df; val r = Square.rank(k) + dr
            if (Square.isValid(f, r)) out += Square.of(f, r)
        }
        return out
    }

    fun pieceName(p: Piece): String = p.type.displayName
}
