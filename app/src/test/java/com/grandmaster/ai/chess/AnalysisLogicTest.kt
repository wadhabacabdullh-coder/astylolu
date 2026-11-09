package com.grandmaster.ai.chess

import com.grandmaster.ai.analysis.ClassificationInput
import com.grandmaster.ai.analysis.Evaluation
import com.grandmaster.ai.analysis.MoveClassifier
import com.grandmaster.ai.analysis.MoveExplainer
import com.grandmaster.ai.analysis.MoveQuality
import com.grandmaster.ai.analysis.MoveTag
import com.grandmaster.ai.analysis.Tactics
import com.grandmaster.ai.data.OpeningBook
import com.grandmaster.ai.engine.Bound
import com.grandmaster.ai.engine.Score
import com.grandmaster.ai.engine.UciParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisLogicTest {

    @Test fun everyBookLineIsLegal() {
        for (o in OpeningBook.openings) {
            var g = Game()
            for (san in o.moves) {
                g = try { g.playSan(san) } catch (e: Exception) {
                    throw AssertionError("Illegal move '$san' in ${o.eco} ${o.name}", e)
                }
            }
            // SAN in the book must match the canonical SAN produced by the rules engine.
            assertEquals("${o.name}", o.moves, g.moves.map { it.san })
        }
    }

    @Test fun identifiesOpenings() {
        val najdorf = "e4 c5 Nf3 d6 d4 cxd4 Nxd4 Nf6 Nc3 a6 Be2 e5".split(' ')
        assertEquals("Sicilian Najdorf: Opocensky Variation", OpeningBook.identify(najdorf)?.name)
        assertTrue(OpeningBook.continuations(listOf("e4", "e5", "Nf3", "Nc6")).any { it.san == "Bb5" })
    }

    @Test fun parsesUciInfoLines() {
        val l = UciParser.parseInfo("info depth 12 seldepth 13 multipv 2 score cp -35 upperbound nodes 24243 nps 1010125 hashfull 5 tbhits 0 time 24 pv d1a1 g7g6")!!
        assertEquals(12, l.depth); assertEquals(2, l.multipv)
        assertEquals(Score.Cp(-35), l.score); assertEquals(Bound.UPPER, l.bound)
        assertEquals(listOf("d1a1", "g7g6"), l.pv)
        val m = UciParser.parseInfo("info depth 12 seldepth 2 multipv 1 score mate 1 wdl 1000 0 0 nodes 1 nps 1 time 1 pv d1d8")!!
        assertEquals(Score.Mate(1), m.score)
        assertEquals(Triple(1000, 0, 0), m.wdl)
        assertEquals("e7e5" to "g1f3", UciParser.parseBestMove("bestmove e7e5 ponder g1f3"))
        assertEquals(null, UciParser.parseInfo("info string NNUE evaluation using nn.nnue"))
    }

    @Test fun evaluationFormatting() {
        assertEquals("+1.80", Evaluation.format(Score.Cp(180)))
        assertEquals("-0.35", Evaluation.format(Score.Cp(-35)))
        assertEquals("#3", Evaluation.format(Score.Mate(3)))
        assertEquals("#-2", Evaluation.format(Score.Mate(-2)))
        assertEquals(Score.Cp(-50), Evaluation.whitePov(Score.Cp(50), PieceColor.BLACK))
        assertEquals("1. e4 e5 2. Nf3", Evaluation.pvToSan(Position.startPosition(), listOf("e2e4", "e7e5", "g1f3")))
    }

    @Test fun staticExchange() {
        // Pawn takes undefended knight: +300.
        val p = Position.fromFen("4k3/8/8/3n4/4P3/8/8/4K3 w - - 0 1")
        assertEquals(300, Tactics.see(p, p.parseSan("exd5")!!))
        // Queen takes pawn defended by pawn: loses queen for pawn.
        val q = Position.fromFen("4k3/2p5/3p4/8/8/8/3Q4/4K3 w - - 0 1")
        assertEquals(100 - 900, Tactics.see(q, q.parseSan("Qxd6")!!))
        // Quiet move to a square attacked by a pawn = offering the knight.
        val r = Position.fromFen("4k3/8/8/2p5/8/8/2N5/4K3 w - - 0 1")
        assertEquals(-300, Tactics.see(r, r.parseSan("Nd4")!!))
    }

    @Test fun explainsForkThreatAndMate() {
        val t = Position.fromFen("4k3/8/8/1q6/8/8/2N5/4K3 w - - 0 1")
        val te = MoveExplainer.explainMove(t, t.parseSan("Nd4")!!, Score.Cp(-800))
        assertTrue(te.reasons.joinToString(), MoveTag.THREAT in te.tags)

        val nfork = Position.fromFen("2r1k3/8/8/1N6/8/8/8/4K3 w - - 0 1")
        val fe = MoveExplainer.explainMove(nfork, nfork.parseSan("Nd6+")!!, Score.Cp(500))
        assertTrue(fe.reasons.joinToString(), MoveTag.FORK in fe.tags)
        assertTrue(MoveTag.CHECK in fe.tags)

        val mate = Position.fromFen("6k1/5ppp/8/8/8/8/8/3R2K1 w - - 0 1")
        val me = MoveExplainer.explainMove(mate, mate.parseSan("Rd8")!!, Score.Mate(1))
        assertTrue(MoveTag.MATE in me.tags)
        assertTrue(me.reasons.first().startsWith("Checkmate"))
    }

    @Test fun classifiesMoves() {
        fun c(best: Int, played: Int, isBest: Boolean = false, see: Int = 0, second: Int? = null) =
            MoveClassifier.classify(ClassificationInput(Score.Cp(best), Score.Cp(played), second?.let { Score.Cp(it) }, isBest, see, false))
        assertEquals(MoveQuality.BEST, c(30, 30, isBest = true))
        assertEquals(MoveQuality.BLUNDER, c(30, -500))
        assertEquals(MoveQuality.MISTAKE, c(30, -120))
        assertEquals(MoveQuality.INACCURACY, c(30, -40))
        assertEquals(MoveQuality.BRILLIANT, c(150, 150, isBest = true, see = -300))
        assertEquals(MoveQuality.GREAT, c(50, 50, isBest = true, second = -250))
        assertTrue(MoveClassifier.moveAccuracy(0.0) > 99.0)
    }
}
