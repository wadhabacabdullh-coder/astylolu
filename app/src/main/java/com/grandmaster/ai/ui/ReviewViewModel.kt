package com.grandmaster.ai.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.grandmaster.ai.GrandmasterApp
import com.grandmaster.ai.analysis.ClassificationInput
import com.grandmaster.ai.analysis.Evaluation
import com.grandmaster.ai.analysis.MoveClassifier
import com.grandmaster.ai.analysis.MoveExplainer
import com.grandmaster.ai.analysis.MoveQuality
import com.grandmaster.ai.analysis.Tactics
import com.grandmaster.ai.chess.Game
import com.grandmaster.ai.chess.Move
import com.grandmaster.ai.chess.PieceColor
import com.grandmaster.ai.chess.Position
import com.grandmaster.ai.data.OpeningBook
import com.grandmaster.ai.engine.EngineLine
import com.grandmaster.ai.engine.Score
import com.grandmaster.ai.engine.SearchLimits
import com.grandmaster.ai.engine.SearchRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ReviewedMove(
    val ply: Int,
    val san: String,
    val color: PieceColor,
    val quality: MoveQuality,
    val playedUci: String,
    val bestUci: String?,
    val bestSan: String?,
    /** Evaluation after the played move (White POV). */
    val scoreAfterWhite: Score,
    /** Evaluation of the best move (White POV). */
    val bestScoreWhite: Score,
    val winLoss: Double,
    val comment: String,
)

data class ReviewUi(
    val running: Boolean = false,
    val progress: Float = 0f,
    val gameKey: String? = null,
    val moves: List<ReviewedMove> = emptyList(),
    /** Evaluations of each position (index 0 = start), White POV, used for the graph. */
    val evals: List<Score> = emptyList(),
    val whiteAccuracy: Double? = null,
    val blackAccuracy: Double? = null,
    val error: String? = null,
    val depth: Int = 0,
) {
    val complete: Boolean get() = !running && moves.isNotEmpty() && error == null
    fun counts(color: PieceColor): Map<MoveQuality, Int> =
        moves.filter { it.color == color }.groupingBy { it.quality }.eachCount()
}

/** Game Review: evaluates every position with Stockfish and classifies each move. */
class ReviewViewModel(app: Application) : AndroidViewModel(app) {
    private val container = app as GrandmasterApp
    private val engine = container.engineRepository

    private val _ui = MutableStateFlow(ReviewUi())
    val ui: StateFlow<ReviewUi> = _ui.asStateFlow()
    private var job: Job? = null

    fun keyOf(game: Game): String = game.start.toFen() + "|" + game.moves.joinToString(" ") { it.move.uci() }

    fun start(game: Game) {
        if (game.moves.isEmpty()) {
            _ui.value = ReviewUi(error = "Play or import a game first (Analysis Board → menu → Import PGN).")
            return
        }
        job?.cancel()
        job = viewModelScope.launch {
            val settings = container.settingsRepository.settings.first()
            val depth = settings.reviewDepth
            val key = keyOf(game)
            _ui.value = ReviewUi(running = true, gameKey = key, depth = depth)
            try {
                engine.newGame()
                val positions = (0..game.moves.size).map { game.positionAt(it) }
                val lines = ArrayList<List<EngineLine>>()
                for ((i, pos) in positions.withIndex()) {
                    lines += analysePosition(game, i, pos, depth)
                    _ui.value = _ui.value.copy(progress = (i + 1f) / positions.size)
                }
                val result = withContext(Dispatchers.Default) { buildReview(game, positions, lines) }
                _ui.value = result.copy(running = false, progress = 1f, gameKey = key, depth = depth)
            } catch (e: CancellationException) {
                _ui.value = _ui.value.copy(running = false, error = "Review stopped.")
                throw e
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(running = false, error = "Engine error: ${e.message}")
            }
        }
    }

    fun stop() {
        job?.cancel()
    }

    private suspend fun analysePosition(game: Game, index: Int, pos: Position, depth: Int): List<EngineLine> {
        if (pos.legalMoves().isEmpty()) return emptyList()
        val req = SearchRequest(
            fen = game.start.toFen(),
            moves = game.uciMoves(index),
            limits = SearchLimits(depth = depth),
            multiPv = minOf(2, pos.legalMoves().size),
        )
        return engine.search(req).last().lines
    }

    /** Score of [pos] for its side to move: from engine lines, or exact for terminal positions. */
    private fun scoreStm(pos: Position, lines: List<EngineLine>): Score = when {
        pos.isCheckmate -> Score.Mate(-0).let { Score.Mate(-1) } // side to move is mated
        pos.legalMoves().isEmpty() -> Score.Cp(0)
        else -> lines.firstOrNull()?.score ?: Score.Cp(0)
    }

    private fun buildReview(game: Game, positions: List<Position>, lines: List<List<EngineLine>>): ReviewUi {
        val evals = positions.mapIndexed { i, p -> Evaluation.whitePov(scoreStm(p, lines[i]), p.sideToMove) }
        val sans = OpeningBook.sansOf(game, game.moves.size)
        val reviewed = ArrayList<ReviewedMove>()
        val lossesW = ArrayList<Double>()
        val lossesB = ArrayList<Double>()
        var stillBook = sans != null
        for (i in game.moves.indices) {
            val before = positions[i]
            val after = positions[i + 1]
            val pm = game.moves[i]
            val mover = before.sideToMove
            val best = lines[i].firstOrNull()
            val bestScore = best?.score ?: Score.Cp(0)
            val playedScore = scoreStm(after, lines[i + 1]).negate()
            val second = lines[i].getOrNull(1)?.score
            val bestUci = best?.pv?.firstOrNull()
            val playedIsBest = bestUci == pm.move.uci() ||
                (lines[i].getOrNull(1)?.let { it.pv.firstOrNull() == pm.move.uci() && it.score == bestScore } ?: false)
            if (stillBook && sans != null) stillBook = OpeningBook.isInBook(sans.subList(0, i + 1))
            val input = ClassificationInput(bestScore, playedScore, second, playedIsBest, Tactics.see(before, pm.move), stillBook)
            val quality = MoveClassifier.classify(input)
            val loss = MoveClassifier.winLoss(input)
            if (mover == PieceColor.WHITE) lossesW += loss else lossesB += loss
            val bestMove = bestUci?.let { Move.fromUci(it) }?.takeIf { before.isLegal(it) }
            val bestSan = bestMove?.let { before.san(it) }
            reviewed += ReviewedMove(
                ply = i,
                san = pm.san,
                color = mover,
                quality = quality,
                playedUci = pm.move.uci(),
                bestUci = bestUci,
                bestSan = bestSan,
                scoreAfterWhite = Evaluation.whitePov(playedScore, mover),
                bestScoreWhite = Evaluation.whitePov(bestScore, mover),
                winLoss = loss,
                comment = comment(quality, pm.san, before, bestMove, bestSan, best, lines[i + 1], after),
            )
        }
        return ReviewUi(
            moves = reviewed,
            evals = evals,
            whiteAccuracy = MoveClassifier.accuracy(lossesW),
            blackAccuracy = MoveClassifier.accuracy(lossesB),
        )
    }

    private fun comment(
        q: MoveQuality, san: String, before: Position, bestMove: Move?, bestSan: String?,
        best: EngineLine?, linesAfter: List<EngineLine>, after: Position,
    ): String {
        val bestEval = best?.let { Evaluation.formatShort(Evaluation.whitePov(it.score, before.sideToMove)) }
        val reply = linesAfter.firstOrNull()?.pv?.firstOrNull()?.let { Move.fromUci(it) }?.takeIf { after.isLegal(it) }
        val replySan = reply?.let { after.san(it) }
        return when (q) {
            MoveQuality.BOOK -> "$san is established opening theory."
            MoveQuality.BRILLIANT -> "$san!! gives up material, and Stockfish confirms it is the strongest move ($bestEval)."
            MoveQuality.GREAT -> "$san! is the only move that keeps the evaluation; every alternative is clearly worse."
            MoveQuality.BEST -> "$san is Stockfish's top choice ($bestEval)."
            MoveQuality.EXCELLENT, MoveQuality.GOOD -> "$san is fine. Stockfish slightly prefers $bestSan ($bestEval)."
            else -> buildString {
                append("$san is ${if (q == MoveQuality.INACCURACY) "an" else "a"} ${q.label.lowercase()}. ")
                if (bestSan != null) {
                    append("Better was $bestSan ($bestEval)")
                    if (bestMove != null && best != null) {
                        val why = MoveExplainer.explainMove(before, bestMove, best.score, best.pv, best.depth)
                            .reasons.firstOrNull { !it.startsWith("Stockfish evaluation") }
                        if (why != null) append(": ").append(why.replaceFirstChar { it.lowercase() }.trimEnd('.'))
                    }
                    append(". ")
                }
                if (replySan != null) append("Now $replySan is strong for the opponent.")
            }.trim()
        }
    }
}
