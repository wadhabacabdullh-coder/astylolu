package com.grandmaster.ai.analysis

import com.grandmaster.ai.engine.Score
import kotlin.math.exp

enum class MoveQuality(val label: String, val symbol: String) {
    BRILLIANT("Brilliant", "!!"),
    GREAT("Great", "!"),
    BEST("Best", "★"),
    EXCELLENT("Excellent", "✓"),
    GOOD("Good", "✓"),
    BOOK("Book", "📖"),
    INACCURACY("Inaccuracy", "?!"),
    MISTAKE("Mistake", "?"),
    BLUNDER("Blunder", "??"),
    MISSED_WIN("Missed win", "?"),
}

data class ClassificationInput(
    /** Best line score before the move, mover's point of view. */
    val bestScore: Score,
    /** Score after the played move, mover's point of view. */
    val playedScore: Score,
    /** Second best line score before the move (mover's POV), if known. */
    val secondBestScore: Score?,
    val playedIsBest: Boolean,
    /** Static exchange of the played move (negative = material offered). */
    val playedSee: Int,
    val isBook: Boolean,
)

/**
 * Classifies moves from Stockfish evaluations, using the expected-points (win%)
 * model popularised by Lichess. A move is only called "Brilliant" when it is
 * the engine's top choice (or practically equal to it), gives up material by
 * static exchange, and does not throw away the advantage — i.e. the label is
 * backed by engine evidence, never assigned cosmetically.
 */
object MoveClassifier {

    fun winLoss(input: ClassificationInput): Double =
        (Evaluation.winPercent(input.bestScore) - Evaluation.winPercent(input.playedScore)).coerceAtLeast(0.0)

    fun classify(input: ClassificationInput): MoveQuality {
        if (input.isBook) return MoveQuality.BOOK
        val best = input.bestScore
        val played = input.playedScore
        val loss = winLoss(input)
        val wpAfter = Evaluation.winPercent(played)
        val wpBefore = Evaluation.winPercent(best)

        // Allowed a forced mate against yourself, or missed a forced mate you had.
        if (played is Score.Mate && played.moves < 0 && !(best is Score.Mate && best.moves < 0)) return MoveQuality.BLUNDER
        if (best is Score.Mate && best.moves > 0 && !(played is Score.Mate && played.moves > 0)) {
            return if (wpAfter >= 90) MoveQuality.GOOD else MoveQuality.MISSED_WIN
        }

        val nearBest = input.playedIsBest || loss < 1.0
        if (nearBest && input.playedSee <= -200 && wpAfter >= 50 && wpBefore < 97) return MoveQuality.BRILLIANT
        if (input.playedIsBest && input.secondBestScore != null) {
            val gap = Evaluation.winPercent(best) - Evaluation.winPercent(input.secondBestScore)
            if (gap >= 15 && wpBefore in 20.0..97.0) return MoveQuality.GREAT
        }
        if (input.playedIsBest) return MoveQuality.BEST
        return when {
            loss < 2 -> MoveQuality.EXCELLENT
            loss < 5 -> MoveQuality.GOOD
            loss < 10 -> MoveQuality.INACCURACY
            loss < 20 -> if (wpBefore >= 70 && wpAfter < 50) MoveQuality.MISSED_WIN else MoveQuality.MISTAKE
            else -> MoveQuality.BLUNDER
        }
    }

    /** Per-move accuracy (0..100) from the win-percentage drop (Lichess formula). */
    fun moveAccuracy(winLoss: Double): Double =
        (103.1668 * exp(-0.04354 * winLoss) - 3.1669).coerceIn(0.0, 100.0)

    fun accuracy(losses: List<Double>): Double? =
        if (losses.isEmpty()) null else losses.map { moveAccuracy(it) }.average()
}
