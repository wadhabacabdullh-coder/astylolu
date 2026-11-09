package com.grandmaster.ai.analysis

import com.grandmaster.ai.chess.Move
import com.grandmaster.ai.chess.PieceColor
import com.grandmaster.ai.chess.Position
import com.grandmaster.ai.engine.Score
import java.util.Locale
import kotlin.math.abs
import kotlin.math.exp

object Evaluation {

    /** Converts a side-to-move score into White's point of view. */
    fun whitePov(score: Score, sideToMove: PieceColor): Score =
        if (sideToMove == PieceColor.WHITE) score else score.negate()

    /** "+1.85", "-0.30", "0.00", "#3", "#-2" (White's point of view expected). */
    fun format(scoreWhite: Score): String = when (scoreWhite) {
        is Score.Mate -> if (scoreWhite.moves > 0) "#${scoreWhite.moves}" else "#-${abs(scoreWhite.moves)}"
        is Score.Cp -> {
            val v = scoreWhite.cp / 100.0
            when {
                scoreWhite.cp > 0 -> String.format(Locale.US, "+%.2f", v)
                scoreWhite.cp < 0 -> String.format(Locale.US, "%.2f", v)
                else -> "0.00"
            }
        }
    }

    /** Short evaluation for compact chips, e.g. "+1.8". */
    fun formatShort(scoreWhite: Score): String = when (scoreWhite) {
        is Score.Mate -> format(scoreWhite)
        is Score.Cp -> {
            val v = scoreWhite.cp / 100.0
            if (scoreWhite.cp > 0) String.format(Locale.US, "+%.1f", v) else String.format(Locale.US, "%.1f", v)
        }
    }

    /** Winning chances (0..100) for the player whose point of view [score] is in (Lichess model). */
    fun winPercent(score: Score): Double = when (score) {
        is Score.Mate -> if (score.moves > 0) 100.0 else 0.0
        is Score.Cp -> {
            val cp = score.cp.coerceIn(-1000, 1000)
            50 + 50 * (2 / (1 + exp(-0.00368208 * cp)) - 1)
        }
    }

    /** Fraction of the evaluation bar that belongs to White (0..1). */
    fun whiteBarFraction(scoreWhite: Score): Float = (winPercent(scoreWhite) / 100.0).toFloat()

    fun describe(scoreWhite: Score): String = when (scoreWhite) {
        is Score.Mate -> if (scoreWhite.moves > 0) "White mates in ${scoreWhite.moves}" else "Black mates in ${abs(scoreWhite.moves)}"
        is Score.Cp -> {
            val cp = scoreWhite.cp
            val side = if (cp > 0) "White" else "Black"
            when (abs(cp)) {
                in 0..25 -> "The position is equal"
                in 26..75 -> "$side is slightly better"
                in 76..175 -> "$side is clearly better"
                in 176..400 -> "$side is winning"
                else -> "$side is completely winning"
            }
        }
    }

    /** Converts a UCI principal variation to numbered SAN ("12. Nf3 Nc6 13. Bb5"). */
    fun pvToSan(start: Position, pv: List<String>, maxMoves: Int = 12): String {
        val sb = StringBuilder()
        var pos = start
        for ((i, uci) in pv.take(maxMoves).withIndex()) {
            val move = Move.fromUci(uci) ?: break
            if (!pos.isLegal(move)) break
            val white = pos.sideToMove == PieceColor.WHITE
            if (white) sb.append(pos.fullmoveNumber).append(". ")
            else if (i == 0) sb.append(pos.fullmoveNumber).append("... ")
            sb.append(pos.san(move)).append(' ')
            pos = pos.play(move)
        }
        return sb.toString().trim()
    }

    /** SAN moves of a PV (unnumbered). */
    fun pvSanList(start: Position, pv: List<String>, maxMoves: Int = 12): List<String> {
        val out = ArrayList<String>()
        var pos = start
        for (uci in pv.take(maxMoves)) {
            val move = Move.fromUci(uci) ?: break
            if (!pos.isLegal(move)) break
            out += pos.san(move)
            pos = pos.play(move)
        }
        return out
    }
}
