package com.grandmaster.ai.engine

import com.grandmaster.ai.analysis.HikaruInsights
import com.grandmaster.ai.analysis.InsightKind
import com.grandmaster.ai.analysis.MoveExplainer
import com.grandmaster.ai.chess.Position
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Talks to a real Stockfish process through [UciEngine].
 * Requires env vars STOCKFISH_HOST_BIN (a host build of the vendored engine)
 * and STOCKFISH_NET (the NNUE file); skipped otherwise.
 */
class StockfishIntegrationTest {
    private lateinit var engine: UciEngine

    @Before fun setUp() {
        val bin = System.getenv("STOCKFISH_HOST_BIN")
        val net = System.getenv("STOCKFISH_NET")
        assumeTrue("STOCKFISH_HOST_BIN not set", bin != null && File(bin).canExecute())
        assumeTrue("STOCKFISH_NET not set", net != null && File(net).exists())
        engine = UciEngine(listOf(bin!!))
        runBlocking {
            engine.start()
            engine.setOptions(mapOf("EvalFile" to net!!, "Threads" to "2", "Hash" to "32"))
        }
    }

    @After fun tearDown() {
        if (::engine.isInitialized) engine.destroy()
    }

    @Test fun handshakeReportsStockfish() {
        assertTrue(engine.engineName, engine.engineName.startsWith("Stockfish"))
        assertTrue("MultiPV" in engine.availableOptions)
    }

    @Test fun findsMateInOne() = runBlocking {
        val fen = "6k1/5ppp/8/8/8/8/5PPP/3R2K1 w - - 0 1"
        val snap = engine.search(SearchRequest(fen, limits = SearchLimits(depth = 10), multiPv = 3)).last()
        assertTrue(snap.finished)
        assertEquals("d1d8", snap.bestMove)
        assertEquals(Score.Mate(1), snap.lines.first().score)
        assertEquals(3, snap.lines.size)
    }

    @Test fun multiPvStartPositionIsBalanced() = runBlocking {
        val snap = engine.search(SearchRequest(Position.START_FEN, limits = SearchLimits(depth = 14), multiPv = 3)).last()
        assertEquals(3, snap.lines.size)
        val best = snap.lines.first()
        assertTrue("start position should be roughly equal: ${best.score}", (best.score as Score.Cp).cp in -60..80)
        assertTrue(snap.lines.map { it.pv.first() }.toSet().size == 3)
        assertTrue(snap.depth >= 14)
    }

    @Test fun cancellationKeepsEngineInSync() = runBlocking {
        // Start an infinite search, take the first snapshot, and cancel by leaving the flow.
        val first = engine.search(SearchRequest(Position.START_FEN, limits = SearchLimits(infinite = true))).first()
        assertTrue(first.lines.isNotEmpty())
        // The engine must be ready for the next search immediately.
        val snaps = engine.search(SearchRequest("6k1/5ppp/8/8/8/8/5PPP/3R2K1 w - - 0 1", limits = SearchLimits(depth = 6))).toList()
        assertEquals("d1d8", snaps.last().bestMove)
    }

    @Test fun findsKnownTacticAndExplainsIt() = runBlocking {
        // Legall-style trap position: White wins with a knight fork / mate pattern; check the engine and explainer agree.
        val fen = "r1bqkb1r/pppp1ppp/2n2n2/4p2Q/2B1P3/8/PPPP1PPP/RNB1K1NR w KQkq - 4 4"
        val pos = Position.fromFen(fen)
        val snap = engine.search(SearchRequest(fen, limits = SearchLimits(depth = 12), multiPv = 3)).last()
        assertEquals("h5f7", snap.bestMove) // Qxf7#
        val e = MoveExplainer.explain(pos, snap.lines.first(), snap.lines.getOrNull(1))!!
        assertEquals("Qxf7#", e.san)
        val (_, insights) = HikaruInsights.analyse(pos, snap.lines)
        assertTrue(insights.any { it.kind == InsightKind.FORCED_MATE })
    }

    @Test fun limitedStrengthStillReturnsLegalMove() = runBlocking {
        val req = SearchRequest(
            Position.START_FEN, limits = SearchLimits(moveTimeMs = 200),
            strength = StrengthOptions(limitElo = 1500),
        )
        val snap = engine.search(req).last()
        val move = com.grandmaster.ai.chess.Move.fromUci(snap.bestMove!!)!!
        assertTrue(Position.startPosition().isLegal(move))
    }
}
