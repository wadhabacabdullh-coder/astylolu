package com.grandmaster.ai.analysis

import com.grandmaster.ai.chess.Position
import com.grandmaster.ai.chess.Square
import com.grandmaster.ai.engine.EngineLine
import com.grandmaster.ai.engine.Score

enum class InsightKind(val title: String) {
    FORCED_MATE("Forced mate"),
    SACRIFICE("Sacrifice"),
    FORCING("Forcing sequence"),
    DEFENSIVE_RESOURCE("Defensive resource"),
    UNEXPECTED("Unexpected move"),
    KING_ATTACK("Attack on the king"),
    TACTICAL_SHOT("Tactical shot"),
}

data class TacticalInsight(
    val kind: InsightKind,
    val rank: Int,
    val explanation: MoveExplanation,
    val headline: String,
)

data class ThreatInfo(val square: Int, val pieceName: String, val material: Int)

/**
 * Hikaru Mode: re-reads Stockfish's MultiPV output through a tactical lens.
 * The engine's ranking and evaluations are never changed; this only finds and
 * highlights lines that contain sacrifices, forcing play, defensive resources
 * and attacking ideas, with the evidence that supports each label.
 */
object HikaruInsights {

    fun analyse(pos: Position, lines: List<EngineLine>): Pair<List<MoveExplanation>, List<TacticalInsight>> {
        if (lines.isEmpty()) return emptyList<MoveExplanation>() to emptyList()
        val explanations = lines.mapIndexedNotNull { i, l ->
            MoveExplainer.explain(pos, l, lines.getOrNull(i + 1).takeIf { i == 0 })
        }
        val bestScore = lines.first().score
        val bestWp = Evaluation.winPercent(bestScore)
        val insights = ArrayList<TacticalInsight>()
        for ((i, e) in explanations.withIndex()) {
            val l = lines[i]
            val wp = Evaluation.winPercent(l.score)
            // Only lines that are close to the best are recommended (within 8 win% points).
            val sound = i == 0 || bestWp - wp <= 8
            if (!sound) continue
            val rank = i + 1
            val s = l.score
            when {
                s is Score.Mate && s.moves > 0 ->
                    insights += TacticalInsight(InsightKind.FORCED_MATE, rank, e, "${e.san} forces mate in ${s.moves}")
                MoveTag.SACRIFICE in e.tags ->
                    insights += TacticalInsight(InsightKind.SACRIFICE, rank, e, "${e.san} gives up material for the initiative")
                e.forcingMoves >= 2 ->
                    insights += TacticalInsight(InsightKind.FORCING, rank, e, "${e.san} starts ${e.forcingMoves} forcing moves in a row")
                MoveTag.FORK in e.tags || MoveTag.DISCOVERED in e.tags || MoveTag.WINS_MATERIAL in e.tags ->
                    insights += TacticalInsight(InsightKind.TACTICAL_SHOT, rank, e, "${e.san}: ${e.tags.filter { it in TACTIC_TAGS }.joinToString { it.label.lowercase() }}")
                MoveTag.KING_ATTACK in e.tags ->
                    insights += TacticalInsight(InsightKind.KING_ATTACK, rank, e, "${e.san} brings another attacker toward the king")
            }
            if (i == 0) {
                if (MoveTag.ONLY_MOVE in e.tags && (bestWp <= 60 || MoveTag.PARRIES_THREAT in e.tags || MoveTag.ESCAPE in e.tags || MoveTag.DEFENDS in e.tags)) {
                    insights += TacticalInsight(InsightKind.DEFENSIVE_RESOURCE, rank, e, "${e.san} is the only move that holds")
                } else if (MoveTag.QUIET in e.tags && hasForcingAlternatives(pos)) {
                    insights += TacticalInsight(InsightKind.UNEXPECTED, rank, e, "${e.san}: a quiet move beats all the checks and captures")
                } else if (isRetreat(pos, e)) {
                    insights += TacticalInsight(InsightKind.UNEXPECTED, rank, e, "${e.san}: a backward move is strongest")
                }
            }
        }
        return explanations to insights.sortedBy { it.kind.ordinal * 10 + it.rank }
    }

    /** What the opponent would win if it were their move right now (static exchange). */
    fun opponentThreat(pos: Position): ThreatInfo? {
        if (pos.isInCheck()) return null
        val view = pos.nullMove()
        if (view.isInCheck(view.sideToMove.opposite)) return null
        val (gain, sq) = Tactics.bestCapture(view) ?: return null
        if (gain < 100) return null
        val p = pos[sq] ?: return null
        return ThreatInfo(sq, "${p.type.displayName} on ${Square.name(sq)}", gain)
    }

    private fun hasForcingAlternatives(pos: Position): Boolean =
        pos.legalMoves().any { m -> pos.isCapture(m) || pos.play(m).isInCheck() }

    private fun isRetreat(pos: Position, e: MoveExplanation): Boolean {
        val p = pos[e.move.from] ?: return false
        if (p.type == com.grandmaster.ai.chess.PieceType.PAWN || p.type == com.grandmaster.ai.chess.PieceType.KING) return false
        val fromRank = Square.rank(e.move.from)
        val toRank = Square.rank(e.move.to)
        val backward = if (pos.sideToMove == com.grandmaster.ai.chess.PieceColor.WHITE) toRank < fromRank else toRank > fromRank
        return backward && MoveTag.CAPTURE !in e.tags && MoveTag.CHECK !in e.tags && MoveTag.ESCAPE !in e.tags
    }

    private val TACTIC_TAGS = setOf(MoveTag.FORK, MoveTag.DISCOVERED, MoveTag.WINS_MATERIAL)
}
