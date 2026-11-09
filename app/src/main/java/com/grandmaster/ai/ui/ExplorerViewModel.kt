package com.grandmaster.ai.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.grandmaster.ai.GrandmasterApp
import com.grandmaster.ai.analysis.Evaluation
import com.grandmaster.ai.chess.Game
import com.grandmaster.ai.data.BookContinuation
import com.grandmaster.ai.data.Opening
import com.grandmaster.ai.data.OpeningBook
import com.grandmaster.ai.engine.Score
import com.grandmaster.ai.engine.SearchLimits
import com.grandmaster.ai.engine.SearchRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.launch

data class ExplorerMove(
    val san: String,
    val uci: String,
    val book: BookContinuation?,
    val scoreWhite: Score?,
    val engineRank: Int?,
)

data class ExplorerUi(
    val customStart: Boolean = false,
    val opening: Opening? = null,
    val inBook: Boolean = true,
    val moves: List<ExplorerMove> = emptyList(),
    val evaluating: Boolean = false,
    val error: String? = null,
)

/** Opening Explorer: book theory for the current line plus Stockfish evaluations of each option. */
class ExplorerViewModel(app: Application) : AndroidViewModel(app) {
    private val container = app as GrandmasterApp
    private val engine = container.engineRepository
    private val game = MutableStateFlow<Game?>(null)
    private val active = MutableStateFlow(false)
    private val _ui = MutableStateFlow(ExplorerUi())
    val ui: StateFlow<ExplorerUi> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            combine(game, active) { g, a -> g to a }
                .distinctUntilChanged { a, b -> a.second == b.second && a.first?.current?.toFen() == b.first?.current?.toFen() && a.first?.index == b.first?.index }
                .collectLatest { (g, a) -> if (g != null && a) refresh(g) }
        }
    }

    fun setGame(g: Game) { game.value = g }
    fun setActive(a: Boolean) { active.value = a }

    private suspend fun refresh(g: Game) {
        val pos = g.current
        val sans = OpeningBook.sansOf(g)
        val opening = sans?.let { OpeningBook.identify(it) }
        val book = sans?.let { OpeningBook.continuations(it) }.orEmpty()
        val bookMoves = book.mapNotNull { c -> pos.parseSan(c.san)?.let { it to c } }
        val base = bookMoves.map { (m, c) -> ExplorerMove(c.san, m.uci(), c, null, null) }
        _ui.value = ExplorerUi(
            customStart = sans == null, opening = opening, inBook = sans != null && OpeningBook.isInBook(sans),
            moves = base, evaluating = pos.legalMoves().isNotEmpty(),
        )
        if (pos.legalMoves().isEmpty()) return
        delay(100)
        try {
            val depth = container.settingsRepository.settings.first().depth.coerceAtMost(18)
            // 1) Stockfish's own top 3 moves.
            val top = engine.search(
                SearchRequest(g.start.toFen(), g.uciMoves(), SearchLimits(depth = depth), multiPv = minOf(3, pos.legalMoves().size))
            ).last().lines
            // 2) Evaluate every book move that Stockfish did not already rank.
            val missing = bookMoves.map { it.first.uci() }.filter { u -> top.none { it.pv.firstOrNull() == u } }
            val bookLines = if (missing.isEmpty()) emptyList() else engine.search(
                SearchRequest(g.start.toFen(), g.uciMoves(), SearchLimits(depth = depth), multiPv = missing.size, searchMoves = missing)
            ).last().lines
            val scores = HashMap<String, Score>()
            for (l in top + bookLines) l.pv.firstOrNull()?.let { u -> scores.putIfAbsent(u, Evaluation.whitePov(l.score, pos.sideToMove)) }
            val ranks = top.mapIndexed { i, l -> l.pv.first() to i + 1 }.toMap()
            val merged = LinkedHashMap<String, ExplorerMove>()
            for (mv in base) merged[mv.uci] = mv.copy(scoreWhite = scores[mv.uci], engineRank = ranks[mv.uci])
            for (l in top) {
                val u = l.pv.first()
                if (u !in merged) {
                    val m = com.grandmaster.ai.chess.Move.fromUci(u) ?: continue
                    if (!pos.isLegal(m)) continue
                    merged[u] = ExplorerMove(pos.san(m), u, null, scores[u], ranks[u])
                }
            }
            val sorted = merged.values.sortedByDescending { mv ->
                val s = mv.scoreWhite?.toComparable() ?: Int.MIN_VALUE / 2
                if (pos.sideToMove == com.grandmaster.ai.chess.PieceColor.WHITE) s else -s
            }
            _ui.value = _ui.value.copy(moves = sorted, evaluating = false)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            _ui.value = _ui.value.copy(evaluating = false, error = e.message)
        }
    }
}
