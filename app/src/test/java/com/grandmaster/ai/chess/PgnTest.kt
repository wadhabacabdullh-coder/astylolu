package com.grandmaster.ai.chess

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PgnTest {

    private val operaGame = """
        [Event "Paris"]
        [Site "Paris FRA"]
        [Date "1858.??.??"]
        [Round "?"]
        [White "Paul Morphy"]
        [Black "Duke Karl / Count Isouard"]
        [Result "1-0"]

        1. e4 e5 2. Nf3 d6 3. d4 Bg4 {This is a weak move already.} 4. dxe5 Bxf3
        5. Qxf3 dxe5 6. Bc4 Nf6 7. Qb3 qe7 8. Nc3 c6 9. Bg5 b5 $2 10. Nxb5 cxb5
        11. Bxb5+ Nbd7 12. O-O-O Rd8 13. Rxd7 Rxd7 14. Rd1 Qe6 (14... Qb4 15. Qxb4) 15. Bxd7+ Nxd7
        16. Qb8+ Nxb8 17. Rd8# 1-0
    """.trimIndent().replace("qe7", "Qe7")

    @Test fun parsesFamousGameWithCommentsNagsAndVariations() {
        val g = Pgn.parse(operaGame)
        assertEquals(33, g.moves.size)
        assertEquals("Rd8#", g.moves.last().san)
        assertTrue(g.current.isCheckmate)
        assertEquals("Paul Morphy", g.tags["White"])
        assertEquals(GameResult.WHITE_WINS, g.result())
    }

    @Test fun exportThenReimportRoundTrip() {
        val g = Pgn.parse(operaGame)
        val text = Pgn.export(g)
        assertTrue(text.contains("[White \"Paul Morphy\"]"))
        assertTrue(text.contains("17. Rd8# 1-0"))
        val again = Pgn.parse(text)
        assertEquals(g.moves.map { it.san }, again.moves.map { it.san })
    }

    @Test fun fenTagAndBlackToMoveNumbering() {
        val pgn = """
            [FEN "6k1/5ppp/8/8/8/8/5PPP/3R2K1 b - - 0 30"]
            [SetUp "1"]

            30... h6 31. Rd8+ Kh7 *
        """.trimIndent()
        val g = Pgn.parse(pgn)
        assertEquals(3, g.moves.size)
        val out = Pgn.export(g)
        assertTrue(out, out.contains("30... h6 31. Rd8+ Kh7 *"))
        assertTrue(out.contains("[FEN \"6k1/5ppp/8/8/8/8/5PPP/3R2K1 b - - 0 30\"]"))
    }

    @Test fun movesWithoutTagsAndGluedNumbers() {
        val g = Pgn.parse("1.e4 e5 2.Nf3 Nc6 3.Bb5 a6")
        assertEquals(listOf("e4", "e5", "Nf3", "Nc6", "Bb5", "a6"), g.moves.map { it.san })
    }

    @Test fun illegalMoveReportsError() {
        val e = assertThrows(PgnException::class.java) { Pgn.parse("1. e4 e5 2. Ke3") }
        assertTrue(e.message!!.contains("Ke3"))
    }

    @Test fun castlingWithZeros() {
        val g = Pgn.parse("1. e4 e5 2. Nf3 Nc6 3. Bc4 Bc5 4. 0-0 Nf6")
        assertEquals("O-O", g.moves[6].san)
    }
}
