package com.grandmaster.ai.chess

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ChessRulesTest {

    private fun perft(pos: Position, depth: Int): Long {
        if (depth == 0) return 1
        val moves = pos.legalMoves()
        if (depth == 1) return moves.size.toLong()
        var n = 0L
        for (m in moves) n += perft(pos.play(m), depth - 1)
        return n
    }

    // Reference node counts from https://www.chessprogramming.org/Perft_Results
    @Test fun perftStartPosition() {
        val p = Position.startPosition()
        assertEquals(20L, perft(p, 1))
        assertEquals(400L, perft(p, 2))
        assertEquals(8902L, perft(p, 3))
        assertEquals(197281L, perft(p, 4))
    }

    @Test fun perftKiwipete() {
        val p = Position.fromFen("r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1")
        assertEquals(48L, perft(p, 1))
        assertEquals(2039L, perft(p, 2))
        assertEquals(97862L, perft(p, 3))
    }

    @Test fun perftPosition3EnPassantAndPins() {
        val p = Position.fromFen("8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1")
        assertEquals(14L, perft(p, 1))
        assertEquals(191L, perft(p, 2))
        assertEquals(2812L, perft(p, 3))
        assertEquals(43238L, perft(p, 4))
    }

    @Test fun perftPosition4PromotionsAndCastling() {
        val p = Position.fromFen("r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1")
        assertEquals(6L, perft(p, 1))
        assertEquals(264L, perft(p, 2))
        assertEquals(9467L, perft(p, 3))
    }

    @Test fun perftPosition5() {
        val p = Position.fromFen("rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8")
        assertEquals(44L, perft(p, 1))
        assertEquals(1486L, perft(p, 2))
        assertEquals(62379L, perft(p, 3))
    }

    @Test fun castlingBothSidesAndRights() {
        val p = Position.fromFen("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1")
        val oo = Move(Square.parse("e1"), Square.parse("g1"))
        val ooo = Move(Square.parse("e1"), Square.parse("c1"))
        assertTrue(p.isLegal(oo)); assertTrue(p.isLegal(ooo))
        assertEquals("O-O", p.san(oo)); assertEquals("O-O-O", p.san(ooo))
        val after = p.play(oo)
        assertEquals(Piece.of(PieceColor.WHITE, PieceType.ROOK), after[Square.parse("f1")])
        assertNull(after[Square.parse("h1")])
        assertEquals("r3k2r/8/8/8/8/8/8/R4RK1 b kq - 1 1", after.toFen())
        // Rook move removes only that side's right.
        val rookMoved = p.play(Move(Square.parse("a1"), Square.parse("a2")))
        assertEquals("Kkq", rookMoved.toFen().split(' ')[2])
    }

    @Test fun cannotCastleThroughOrOutOfCheck() {
        // Black rook on f8 attacks f1: king-side castling forbidden, queen-side allowed.
        val p = Position.fromFen("5r2/8/3k4/8/8/8/8/R3K2R w KQ - 0 1")
        assertFalse(p.isLegal(Move(Square.parse("e1"), Square.parse("g1"))))
        assertTrue(p.isLegal(Move(Square.parse("e1"), Square.parse("c1"))))
        // In check: no castling at all.
        val inCheck = Position.fromFen("4r3/8/3k4/8/8/8/8/R3K2R w KQ - 0 1")
        assertFalse(inCheck.isLegal(Move(Square.parse("e1"), Square.parse("g1"))))
        assertFalse(inCheck.isLegal(Move(Square.parse("e1"), Square.parse("c1"))))
        // b1 attacked does not prevent queen-side castling.
        val b1 = Position.fromFen("1r6/8/3k4/8/8/8/8/R3K2R w KQ - 0 1")
        assertTrue(b1.isLegal(Move(Square.parse("e1"), Square.parse("c1"))))
    }

    @Test fun capturingRookRemovesCastlingRight() {
        val p = Position.fromFen("r3k2r/8/8/8/8/8/6B1/R3K2R w KQkq - 0 1")
        val after = p.play(Move(Square.parse("g2"), Square.parse("a8")))
        assertEquals("KQk", after.toFen().split(' ')[2])
    }

    @Test fun enPassant() {
        var g = Game()
        for (san in listOf("e4", "a6", "e5", "d5")) g = g.playSan(san)
        assertEquals("d6", g.current.toFen().split(' ')[3])
        val ep = g.current.parseSan("exd6")
        assertNotNull(ep)
        assertTrue(g.current.isEnPassant(ep!!))
        g = g.play(ep)
        assertNull(g.current[Square.parse("d5")])
        assertEquals(Piece.of(PieceColor.WHITE, PieceType.PAWN), g.current[Square.parse("d6")])
        assertEquals("exd6", g.moves.last().san)
    }

    @Test fun enPassantOnlyImmediately() {
        var g = Game()
        for (san in listOf("e4", "a6", "e5", "d5", "Nf3", "h6")) g = g.playSan(san)
        assertNull(g.current.parseSan("exd6"))
    }

    @Test fun enPassantIllegalWhenItExposesKing() {
        // Classic horizontal pin: capturing e.p. would expose the white king on a5 to the rook on h5.
        val p = Position.fromFen("8/8/8/KPp4r/8/8/8/7k w - c6 0 1")
        assertFalse(p.isLegal(Move(Square.parse("b5"), Square.parse("c6"))))
    }

    @Test fun promotionAllPiecesAndSan() {
        val p = Position.fromFen("7k/P7/8/8/8/8/8/K7 w - - 0 1")
        val promos = p.legalMovesFrom(Square.parse("a7"))
        assertEquals(setOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT), promos.map { it.promotion }.toSet())
        val q = Move(Square.parse("a7"), Square.parse("a8"), PieceType.QUEEN)
        assertEquals("a8=Q+", p.san(q))
        val after = p.play(q)
        assertEquals(Piece.of(PieceColor.WHITE, PieceType.QUEEN), after[Square.parse("a8")])
        assertEquals(q, p.parseSan("a8=Q"))
        assertEquals(q, p.parseSan("a8Q"))
        assertEquals(Move(Square.parse("a7"), Square.parse("a8"), PieceType.KNIGHT), p.parseSan("a8=N"))
    }

    @Test fun captureWithPromotion() {
        val p = Position.fromFen("1r5k/P7/8/8/8/8/8/K7 w - - 0 1")
        val m = p.parseSan("axb8=Q+")
        assertNotNull(m)
        assertEquals("axb8=Q+", p.san(m!!))
    }

    @Test fun foolsMateCheckmate() {
        var g = Game()
        for (san in listOf("f3", "e5", "g4", "Qh4#")) g = g.playSan(san)
        assertTrue(g.current.isCheckmate)
        assertEquals(GameEnd.CHECKMATE, g.gameEnd())
        assertEquals(GameResult.BLACK_WINS, g.result())
        assertEquals("Qh4#", g.moves.last().san)
    }

    @Test fun backRankMate() {
        val p = Position.fromFen("6k1/5ppp/8/8/8/8/8/3R2K1 w - - 0 1")
        val m = p.parseSan("Rd8")!!
        assertEquals("Rd8#", p.san(m))
        assertTrue(p.play(m).isCheckmate)
    }

    @Test fun stalemate() {
        val p = Position.fromFen("7k/5Q2/6K1/8/8/8/8/8 b - - 0 1")
        assertTrue(p.isStalemate)
        assertFalse(p.isCheckmate)
        assertEquals(GameEnd.STALEMATE, Game.fromFen(p.toFen()).gameEnd())
        assertEquals(GameResult.DRAW, Game.fromFen(p.toFen()).result())
    }

    @Test fun insufficientMaterial() {
        assertTrue(Position.fromFen("8/8/8/4k3/8/8/8/4K3 w - - 0 1").isInsufficientMaterial())
        assertTrue(Position.fromFen("8/8/8/4k3/8/8/8/4KN2 w - - 0 1").isInsufficientMaterial())
        assertTrue(Position.fromFen("8/8/8/4k3/2b5/8/8/4KB2 w - - 0 1").isInsufficientMaterial()) // same colour bishops
        assertFalse(Position.fromFen("8/8/1b6/4k3/8/8/8/4KB2 w - - 0 1").isInsufficientMaterial())
        assertFalse(Position.fromFen("8/8/8/4k3/8/8/4P3/4K3 w - - 0 1").isInsufficientMaterial())
    }

    @Test fun threefoldRepetition() {
        var g = Game()
        repeat(2) { for (san in listOf("Nf3", "Nf6", "Ng1", "Ng8")) g = g.playSan(san) }
        assertEquals(GameEnd.THREEFOLD, g.gameEnd())
    }

    @Test fun sanDisambiguation() {
        val p = Position.fromFen("4k3/8/8/8/8/8/4K3/R6R w - - 0 1")
        assertEquals("Rad1", p.san(Move(Square.parse("a1"), Square.parse("d1"))))
        assertEquals("Rhd1", p.san(Move(Square.parse("h1"), Square.parse("d1"))))
        val r = Position.fromFen("4k3/8/8/R7/8/8/8/R3K3 w - - 0 1")
        assertEquals("R1a3", r.san(Move(Square.parse("a1"), Square.parse("a3"))))
        assertEquals("R5a3", r.san(Move(Square.parse("a5"), Square.parse("a3"))))
        val q = Position.fromFen("5k2/8/8/8/Q6Q/8/8/Q3K3 w - - 0 1")
        assertEquals("Qa4d4", q.san(Move(Square.parse("a4"), Square.parse("d4"))))
    }

    @Test fun pinnedPieceCannotMove() {
        val p = Position.fromFen("4r1k1/8/8/8/8/8/4N3/4K3 w - - 0 1")
        assertTrue(p.legalMovesFrom(Square.parse("e2")).isEmpty())
    }

    @Test fun fenRoundTripAndValidation() {
        val fens = listOf(
            Position.START_FEN,
            "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1",
            "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1",
        )
        for (f in fens) assertEquals(f, Position.fromFen(f).toFen())
        assertThrows(FenException::class.java) { Position.fromFen("8/8/8/8/8/8/8/8 w - - 0 1") }
        assertThrows(FenException::class.java) { Position.fromFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP w KQkq - 0 1") }
        assertThrows(FenException::class.java) { Position.fromFen("rnbqkbnr/pppppppp/9/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1") }
        // Side not to move in check is invalid.
        assertThrows(FenException::class.java) { Position.fromFen("4k3/8/8/8/8/8/8/4R1K1 w - - 0 1") }
    }

    @Test fun undoRedoAndTruncate() {
        var g = Game()
        for (san in listOf("e4", "e5", "Nf3")) g = g.playSan(san)
        g = g.undo().undo()
        assertEquals(1, g.index)
        assertTrue(g.canRedo)
        g = g.redo()
        assertEquals("Nf3", g.moves[2].san)
        g = g.undo().playSan("c5") // new branch truncates
        assertEquals(listOf("e4", "c5"), g.moves.map { it.san })
        assertFalse(g.canRedo)
    }
}
