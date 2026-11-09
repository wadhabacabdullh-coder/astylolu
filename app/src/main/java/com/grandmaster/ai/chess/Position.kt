package com.grandmaster.ai.chess

class FenException(message: String) : IllegalArgumentException(message)

/**
 * Immutable chess position with full rules support: legal move generation,
 * castling, en passant, promotion, check, checkmate and stalemate detection.
 */
class Position private constructor(
    private val board: Array<Piece?>,
    val sideToMove: PieceColor,
    /** Bit mask of [WK], [WQ], [BK], [BQ]. */
    val castling: Int,
    /** Square a pawn may capture onto en passant, or [Square.NONE]. */
    val epSquare: Int,
    val halfmoveClock: Int,
    val fullmoveNumber: Int,
) {
    private var legalCache: List<Move>? = null

    operator fun get(sq: Int): Piece? = board[sq]

    fun pieces(): List<Pair<Int, Piece>> = board.indices.mapNotNull { sq -> board[sq]?.let { sq to it } }

    fun kingSquare(color: PieceColor): Int {
        for (sq in 0..63) {
            val p = board[sq]
            if (p != null && p.type == PieceType.KING && p.color == color) return sq
        }
        return Square.NONE
    }

    // ------------------------------------------------------------------ attacks

    /** True if [sq] is attacked by any piece of color [by]. */
    fun isAttacked(sq: Int, by: PieceColor): Boolean = attackers(sq, by, stopAtFirst = true).isNotEmpty()

    /** Squares of all pieces of [by] that attack [sq]. */
    fun attackers(sq: Int, by: PieceColor, stopAtFirst: Boolean = false): List<Int> {
        val result = ArrayList<Int>(4)
        val f = Square.file(sq)
        val r = Square.rank(sq)
        // pawns
        val pawnRank = if (by == PieceColor.WHITE) r - 1 else r + 1
        for (df in intArrayOf(-1, 1)) {
            val pf = f + df
            if (Square.isValid(pf, pawnRank)) {
                val s = Square.of(pf, pawnRank)
                val p = board[s]
                if (p != null && p.color == by && p.type == PieceType.PAWN) {
                    result += s; if (stopAtFirst) return result
                }
            }
        }
        for ((df, dr) in KNIGHT_DELTAS) {
            val nf = f + df; val nr = r + dr
            if (Square.isValid(nf, nr)) {
                val s = Square.of(nf, nr)
                val p = board[s]
                if (p != null && p.color == by && p.type == PieceType.KNIGHT) {
                    result += s; if (stopAtFirst) return result
                }
            }
        }
        for ((df, dr) in KING_DELTAS) {
            val nf = f + df; val nr = r + dr
            if (Square.isValid(nf, nr)) {
                val s = Square.of(nf, nr)
                val p = board[s]
                if (p != null && p.color == by && p.type == PieceType.KING) {
                    result += s; if (stopAtFirst) return result
                }
            }
        }
        for ((df, dr) in ROOK_DIRS) {
            var nf = f + df; var nr = r + dr
            while (Square.isValid(nf, nr)) {
                val s = Square.of(nf, nr)
                val p = board[s]
                if (p != null) {
                    if (p.color == by && (p.type == PieceType.ROOK || p.type == PieceType.QUEEN)) {
                        result += s; if (stopAtFirst) return result
                    }
                    break
                }
                nf += df; nr += dr
            }
        }
        for ((df, dr) in BISHOP_DIRS) {
            var nf = f + df; var nr = r + dr
            while (Square.isValid(nf, nr)) {
                val s = Square.of(nf, nr)
                val p = board[s]
                if (p != null) {
                    if (p.color == by && (p.type == PieceType.BISHOP || p.type == PieceType.QUEEN)) {
                        result += s; if (stopAtFirst) return result
                    }
                    break
                }
                nf += df; nr += dr
            }
        }
        return result
    }

    /** Squares attacked by the piece standing on [sq] (ignores pins). */
    fun attacksFrom(sq: Int): List<Int> {
        val p = board[sq] ?: return emptyList()
        val f = Square.file(sq)
        val r = Square.rank(sq)
        val out = ArrayList<Int>(8)
        when (p.type) {
            PieceType.PAWN -> {
                val dr = if (p.color == PieceColor.WHITE) 1 else -1
                for (df in intArrayOf(-1, 1)) if (Square.isValid(f + df, r + dr)) out += Square.of(f + df, r + dr)
            }
            PieceType.KNIGHT -> for ((df, dr) in KNIGHT_DELTAS) if (Square.isValid(f + df, r + dr)) out += Square.of(f + df, r + dr)
            PieceType.KING -> for ((df, dr) in KING_DELTAS) if (Square.isValid(f + df, r + dr)) out += Square.of(f + df, r + dr)
            else -> {
                val dirs = when (p.type) {
                    PieceType.ROOK -> ROOK_DIRS
                    PieceType.BISHOP -> BISHOP_DIRS
                    else -> ROOK_DIRS + BISHOP_DIRS
                }
                for ((df, dr) in dirs) {
                    var nf = f + df; var nr = r + dr
                    while (Square.isValid(nf, nr)) {
                        val s = Square.of(nf, nr)
                        out += s
                        if (board[s] != null) break
                        nf += df; nr += dr
                    }
                }
            }
        }
        return out
    }

    fun isInCheck(color: PieceColor = sideToMove): Boolean {
        val k = kingSquare(color)
        return k != Square.NONE && isAttacked(k, color.opposite)
    }

    // ---------------------------------------------------------- move generation

    fun legalMoves(): List<Move> {
        legalCache?.let { return it }
        val result = pseudoLegalMoves().filter { m ->
            val next = applyUnchecked(m)
            !next.isInCheck(sideToMove)
        }
        legalCache = result
        return result
    }

    fun legalMovesFrom(sq: Int): List<Move> = legalMoves().filter { it.from == sq }

    fun isLegal(move: Move): Boolean = move in legalMoves()

    private fun pseudoLegalMoves(): List<Move> {
        val moves = ArrayList<Move>(48)
        val us = sideToMove
        for (sq in 0..63) {
            val p = board[sq] ?: continue
            if (p.color != us) continue
            val f = Square.file(sq)
            val r = Square.rank(sq)
            when (p.type) {
                PieceType.PAWN -> genPawn(sq, f, r, us, moves)
                PieceType.KNIGHT -> for ((df, dr) in KNIGHT_DELTAS) addStep(sq, f + df, r + dr, us, moves)
                PieceType.KING -> {
                    for ((df, dr) in KING_DELTAS) addStep(sq, f + df, r + dr, us, moves)
                    genCastling(sq, us, moves)
                }
                PieceType.BISHOP -> slide(sq, f, r, BISHOP_DIRS, us, moves)
                PieceType.ROOK -> slide(sq, f, r, ROOK_DIRS, us, moves)
                PieceType.QUEEN -> {
                    slide(sq, f, r, BISHOP_DIRS, us, moves)
                    slide(sq, f, r, ROOK_DIRS, us, moves)
                }
            }
        }
        return moves
    }

    private fun addStep(from: Int, nf: Int, nr: Int, us: PieceColor, moves: MutableList<Move>) {
        if (!Square.isValid(nf, nr)) return
        val to = Square.of(nf, nr)
        val t = board[to]
        if (t == null || t.color != us) moves += Move(from, to)
    }

    private fun slide(from: Int, f: Int, r: Int, dirs: Array<IntArray>, us: PieceColor, moves: MutableList<Move>) {
        for ((df, dr) in dirs) {
            var nf = f + df; var nr = r + dr
            while (Square.isValid(nf, nr)) {
                val to = Square.of(nf, nr)
                val t = board[to]
                if (t == null) {
                    moves += Move(from, to)
                } else {
                    if (t.color != us) moves += Move(from, to)
                    break
                }
                nf += df; nr += dr
            }
        }
    }

    private fun genPawn(sq: Int, f: Int, r: Int, us: PieceColor, moves: MutableList<Move>) {
        val dir = if (us == PieceColor.WHITE) 1 else -1
        val startRank = if (us == PieceColor.WHITE) 1 else 6
        val promoRank = if (us == PieceColor.WHITE) 7 else 0
        fun add(to: Int) {
            if (Square.rank(to) == promoRank) {
                for (pt in PROMOTION_TYPES) moves += Move(sq, to, pt)
            } else moves += Move(sq, to)
        }
        val one = r + dir
        if (one in 0..7) {
            val to1 = Square.of(f, one)
            if (board[to1] == null) {
                add(to1)
                if (r == startRank) {
                    val to2 = Square.of(f, r + 2 * dir)
                    if (board[to2] == null) moves += Move(sq, to2)
                }
            }
            for (df in intArrayOf(-1, 1)) {
                val cf = f + df
                if (cf !in 0..7) continue
                val to = Square.of(cf, one)
                val t = board[to]
                if (t != null && t.color != us) add(to)
                else if (to == epSquare) moves += Move(sq, to)
            }
        }
    }

    private fun genCastling(kingSq: Int, us: PieceColor, moves: MutableList<Move>) {
        val them = us.opposite
        val rank = if (us == PieceColor.WHITE) 0 else 7
        if (kingSq != Square.of(4, rank)) return
        if (isAttacked(kingSq, them)) return
        val kingSide = if (us == PieceColor.WHITE) WK else BK
        val queenSide = if (us == PieceColor.WHITE) WQ else BQ
        val rook = Piece.of(us, PieceType.ROOK)
        if (castling and kingSide != 0 &&
            board[Square.of(7, rank)] === rook &&
            board[Square.of(5, rank)] == null && board[Square.of(6, rank)] == null &&
            !isAttacked(Square.of(5, rank), them) && !isAttacked(Square.of(6, rank), them)
        ) moves += Move(kingSq, Square.of(6, rank))
        if (castling and queenSide != 0 &&
            board[Square.of(0, rank)] === rook &&
            board[Square.of(1, rank)] == null && board[Square.of(2, rank)] == null && board[Square.of(3, rank)] == null &&
            !isAttacked(Square.of(3, rank), them) && !isAttacked(Square.of(2, rank), them)
        ) moves += Move(kingSq, Square.of(2, rank))
    }

    // ------------------------------------------------------------- making moves

    /** Plays a legal move. Throws if [move] is illegal. */
    fun play(move: Move): Position {
        require(isLegal(move)) { "Illegal move ${move.uci()} in ${toFen()}" }
        return applyUnchecked(move)
    }

    fun isCastling(move: Move): Boolean {
        val p = board[move.from] ?: return false
        return p.type == PieceType.KING && kotlin.math.abs(Square.file(move.to) - Square.file(move.from)) == 2
    }

    fun isEnPassant(move: Move): Boolean {
        val p = board[move.from] ?: return false
        return p.type == PieceType.PAWN && move.to == epSquare && Square.file(move.from) != Square.file(move.to) && board[move.to] == null
    }

    fun isCapture(move: Move): Boolean = board[move.to] != null || isEnPassant(move)

    /** The piece captured by [move], if any (handles en passant). */
    fun capturedPiece(move: Move): Piece? =
        if (isEnPassant(move)) Piece.of(sideToMove.opposite, PieceType.PAWN) else board[move.to]

    private fun applyUnchecked(move: Move): Position {
        val b = board.copyOf()
        val piece = b[move.from] ?: error("No piece on ${Square.name(move.from)}")
        val us = piece.color
        var newCastling = castling
        var newEp = Square.NONE
        val captured = capturedPiece(move)
        val isPawn = piece.type == PieceType.PAWN

        if (isEnPassant(move)) {
            val capSq = Square.of(Square.file(move.to), Square.rank(move.from))
            b[capSq] = null
        }
        b[move.to] = if (move.promotion != null) Piece.of(us, move.promotion) else piece
        b[move.from] = null

        if (piece.type == PieceType.KING) {
            newCastling = newCastling and (if (us == PieceColor.WHITE) (WK or WQ) else (BK or BQ)).inv()
            if (isCastling(move)) {
                val rank = Square.rank(move.from)
                if (Square.file(move.to) == 6) {
                    b[Square.of(5, rank)] = b[Square.of(7, rank)]; b[Square.of(7, rank)] = null
                } else {
                    b[Square.of(3, rank)] = b[Square.of(0, rank)]; b[Square.of(0, rank)] = null
                }
            }
        }
        newCastling = newCastling and CASTLE_MASK[move.from] and CASTLE_MASK[move.to]

        if (isPawn && kotlin.math.abs(move.to - move.from) == 16) {
            val epSq = (move.from + move.to) / 2
            // Only record the en-passant square if an enemy pawn could capture there.
            val them = us.opposite
            val f = Square.file(move.to)
            val r = Square.rank(move.to)
            for (df in intArrayOf(-1, 1)) {
                if (f + df in 0..7) {
                    val p = b[Square.of(f + df, r)]
                    if (p != null && p.color == them && p.type == PieceType.PAWN) newEp = epSq
                }
            }
        }
        val hm = if (isPawn || captured != null) 0 else halfmoveClock + 1
        val fm = if (us == PieceColor.BLACK) fullmoveNumber + 1 else fullmoveNumber
        return Position(b, us.opposite, newCastling, newEp, hm, fm)
    }

    /** Position with the side to move flipped (a "null move"); used for threat detection. */
    fun nullMove(): Position = Position(board.copyOf(), sideToMove.opposite, castling, Square.NONE, halfmoveClock, fullmoveNumber)

    /** Returns a copy of this position with [sq] cleared; used for static exchange evaluation. */
    internal fun without(sq: Int): Position {
        val b = board.copyOf(); b[sq] = null
        return Position(b, sideToMove, castling, epSquare, halfmoveClock, fullmoveNumber)
    }

    internal fun withPiece(sq: Int, piece: Piece?): Position {
        val b = board.copyOf(); b[sq] = piece
        return Position(b, sideToMove, castling, epSquare, halfmoveClock, fullmoveNumber)
    }

    // -------------------------------------------------------------- game status

    val isCheckmate: Boolean get() = isInCheck() && legalMoves().isEmpty()
    val isStalemate: Boolean get() = !isInCheck() && legalMoves().isEmpty()

    fun isInsufficientMaterial(): Boolean {
        val minors = ArrayList<Pair<Int, Piece>>()
        for (sq in 0..63) {
            val p = board[sq] ?: continue
            when (p.type) {
                PieceType.KING -> {}
                PieceType.BISHOP, PieceType.KNIGHT -> minors += sq to p
                else -> return false
            }
        }
        if (minors.size <= 1) return true
        // Only bishops, all on the same square colour -> no mate possible.
        if (minors.all { it.second.type == PieceType.BISHOP }) {
            val light = Square.isLight(minors[0].first)
            return minors.all { Square.isLight(it.first) == light }
        }
        return false
    }

    /** Key for repetition detection (board, side, castling, en passant). */
    fun repetitionKey(): String = toFen().split(' ').take(4).joinToString(" ")

    // --------------------------------------------------------------------- FEN

    fun toFen(): String = buildString {
        for (rank in 7 downTo 0) {
            var empty = 0
            for (file in 0..7) {
                val p = board[Square.of(file, rank)]
                if (p == null) empty++ else {
                    if (empty > 0) { append(empty); empty = 0 }
                    append(p.fenChar)
                }
            }
            if (empty > 0) append(empty)
            if (rank > 0) append('/')
        }
        append(' ').append(sideToMove.fenChar).append(' ')
        if (castling == 0) append('-') else {
            if (castling and WK != 0) append('K')
            if (castling and WQ != 0) append('Q')
            if (castling and BK != 0) append('k')
            if (castling and BQ != 0) append('q')
        }
        append(' ').append(if (epSquare == Square.NONE) "-" else Square.name(epSquare))
        append(' ').append(halfmoveClock).append(' ').append(fullmoveNumber)
    }

    override fun equals(other: Any?): Boolean = other is Position && other.toFen() == toFen()
    override fun hashCode(): Int = toFen().hashCode()
    override fun toString(): String = toFen()

    // --------------------------------------------------------------------- SAN

    /** Standard Algebraic Notation for a legal [move], including check/mate suffix. */
    fun san(move: Move): String {
        val piece = board[move.from] ?: return move.uci()
        val sb = StringBuilder()
        if (isCastling(move)) {
            sb.append(if (Square.file(move.to) == 6) "O-O" else "O-O-O")
        } else {
            val capture = isCapture(move)
            if (piece.type == PieceType.PAWN) {
                if (capture) sb.append('a' + Square.file(move.from)).append('x')
                sb.append(Square.name(move.to))
                move.promotion?.let { sb.append('=').append(it.sanLetter) }
            } else {
                sb.append(piece.type.sanLetter)
                val others = legalMoves().filter {
                    it.to == move.to && it.from != move.from && board[it.from] === piece
                }
                if (others.isNotEmpty()) {
                    val sameFile = others.any { Square.file(it.from) == Square.file(move.from) }
                    val sameRank = others.any { Square.rank(it.from) == Square.rank(move.from) }
                    when {
                        !sameFile -> sb.append('a' + Square.file(move.from))
                        !sameRank -> sb.append('1' + Square.rank(move.from))
                        else -> sb.append(Square.name(move.from))
                    }
                }
                if (capture) sb.append('x')
                sb.append(Square.name(move.to))
            }
        }
        val next = applyUnchecked(move)
        if (next.isInCheck()) sb.append(if (next.legalMoves().isEmpty()) '#' else '+')
        return sb.toString()
    }

    /** Parses a SAN move (tolerant of missing/extra check marks, annotations, 0-0, missing '='). */
    fun parseSan(sanInput: String): Move? {
        val cleaned = normalizeSan(sanInput)
        if (cleaned.isEmpty()) return null
        val legal = legalMoves()
        legal.firstOrNull { normalizeSan(san(it)) == cleaned }?.let { return it }
        // Accept UCI-style input too.
        Move.fromUci(sanInput.trim())?.let { m -> if (m in legal) return m }
        // Accept redundant disambiguation, e.g. "Ngf3" when only one knight can go there,
        // or a pawn capture without 'x' like "ed5".
        val m = SAN_REGEX.matchEntire(cleaned) ?: return null
        val pieceLetter = m.groupValues[1]
        val fromFile = m.groupValues[2]
        val fromRank = m.groupValues[3]
        val toSq = Square.parse(m.groupValues[5])
        val promo = m.groupValues[6].takeIf { it.isNotEmpty() }?.let { PieceType.fromLetter(it.last()) }
        val type = if (pieceLetter.isEmpty()) PieceType.PAWN else PieceType.fromLetter(pieceLetter[0]) ?: return null
        val candidates = legal.filter { mv ->
            val p = board[mv.from]!!
            p.type == type && mv.to == toSq && mv.promotion == promo &&
                (fromFile.isEmpty() || Square.file(mv.from) == fromFile[0] - 'a') &&
                (fromRank.isEmpty() || Square.rank(mv.from) == fromRank[0] - '1')
        }
        return candidates.singleOrNull()
    }

    fun materialCount(color: PieceColor): Int =
        board.filterNotNull().filter { it.color == color && it.type != PieceType.KING }.sumOf { it.type.value }

    companion object {
        const val WK = 1
        const val WQ = 2
        const val BK = 4
        const val BQ = 8
        const val START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

        private val KNIGHT_DELTAS = arrayOf(
            intArrayOf(1, 2), intArrayOf(2, 1), intArrayOf(2, -1), intArrayOf(1, -2),
            intArrayOf(-1, -2), intArrayOf(-2, -1), intArrayOf(-2, 1), intArrayOf(-1, 2),
        )
        private val KING_DELTAS = arrayOf(
            intArrayOf(1, 0), intArrayOf(1, 1), intArrayOf(0, 1), intArrayOf(-1, 1),
            intArrayOf(-1, 0), intArrayOf(-1, -1), intArrayOf(0, -1), intArrayOf(1, -1),
        )
        private val ROOK_DIRS = arrayOf(intArrayOf(1, 0), intArrayOf(-1, 0), intArrayOf(0, 1), intArrayOf(0, -1))
        private val BISHOP_DIRS = arrayOf(intArrayOf(1, 1), intArrayOf(1, -1), intArrayOf(-1, 1), intArrayOf(-1, -1))
        private val PROMOTION_TYPES = listOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT)

        /** Castling-rights mask applied for moves from/to each square (rook/king squares). */
        private val CASTLE_MASK = IntArray(64) { 0xF }.also {
            it[0] = 0xF and WQ.inv(); it[7] = 0xF and WK.inv(); it[4] = 0xF and (WK or WQ).inv()
            it[56] = 0xF and BQ.inv(); it[63] = 0xF and BK.inv(); it[60] = 0xF and (BK or BQ).inv()
        }

        private val SAN_REGEX = Regex("^([NBRQK]?)([a-h]?)([1-8]?)(x?)([a-h][1-8])(=?[NBRQ])?$")

        fun normalizeSan(s: String): String =
            s.trim()
                .replace("0-0-0", "O-O-O").replace("0-0", "O-O")
                .trimEnd('+', '#', '!', '?')
                .replace(Regex("[+#!?]"), "")
                .replace(Regex("(?<=[1-8])([NBRQ])$"), "=$1")
                .replace("e.p.", "").replace("ep", "").trim()

        fun startPosition(): Position = fromFen(START_FEN)

        fun fromFen(fenInput: String): Position {
            val parts = fenInput.trim().split(Regex("\\s+"))
            if (parts.isEmpty() || parts[0].isEmpty()) throw FenException("Empty FEN")
            val rows = parts[0].split('/')
            if (rows.size != 8) throw FenException("FEN board must have 8 ranks")
            val board = arrayOfNulls<Piece>(64)
            for ((i, row) in rows.withIndex()) {
                val rank = 7 - i
                var file = 0
                for (c in row) {
                    if (c.isDigit()) {
                        val n = c - '0'
                        if (n !in 1..8) throw FenException("Bad empty-square count '$c'")
                        file += n
                    } else {
                        val p = Piece.fromFen(c) ?: throw FenException("Unknown piece '$c'")
                        if (file > 7) throw FenException("Rank ${rank + 1} is too long")
                        if (p.type == PieceType.PAWN && (rank == 0 || rank == 7)) {
                            throw FenException("Pawn on first/last rank")
                        }
                        board[Square.of(file, rank)] = p
                        file++
                    }
                }
                if (file != 8) throw FenException("Rank ${rank + 1} does not have 8 squares")
            }
            val side = when (parts.getOrElse(1) { "w" }) {
                "w" -> PieceColor.WHITE
                "b" -> PieceColor.BLACK
                else -> throw FenException("Side to move must be 'w' or 'b'")
            }
            var castling = 0
            val c = parts.getOrElse(2) { "-" }
            if (c != "-") for (ch in c) castling = castling or when (ch) {
                'K' -> WK; 'Q' -> WQ; 'k' -> BK; 'q' -> BQ
                else -> throw FenException("Bad castling field '$c'")
            }
            // Drop castling rights that are inconsistent with the board.
            if (board[4] !== Piece.of(PieceColor.WHITE, PieceType.KING)) castling = castling and (WK or WQ).inv()
            if (board[7] !== Piece.of(PieceColor.WHITE, PieceType.ROOK)) castling = castling and WK.inv()
            if (board[0] !== Piece.of(PieceColor.WHITE, PieceType.ROOK)) castling = castling and WQ.inv()
            if (board[60] !== Piece.of(PieceColor.BLACK, PieceType.KING)) castling = castling and (BK or BQ).inv()
            if (board[63] !== Piece.of(PieceColor.BLACK, PieceType.ROOK)) castling = castling and BK.inv()
            if (board[56] !== Piece.of(PieceColor.BLACK, PieceType.ROOK)) castling = castling and BQ.inv()

            val epStr = parts.getOrElse(3) { "-" }
            var ep = if (epStr == "-") Square.NONE else Square.parse(epStr).also {
                if (it == Square.NONE) throw FenException("Bad en-passant square '$epStr'")
            }
            if (ep != Square.NONE) {
                val expectedRank = if (side == PieceColor.WHITE) 5 else 2
                if (Square.rank(ep) != expectedRank) ep = Square.NONE
            }
            val hm = parts.getOrElse(4) { "0" }.toIntOrNull()?.coerceAtLeast(0) ?: throw FenException("Bad halfmove clock")
            val fm = parts.getOrElse(5) { "1" }.toIntOrNull()?.coerceAtLeast(1) ?: throw FenException("Bad fullmove number")

            val whiteKings = board.count { it === Piece.of(PieceColor.WHITE, PieceType.KING) }
            val blackKings = board.count { it === Piece.of(PieceColor.BLACK, PieceType.KING) }
            if (whiteKings != 1 || blackKings != 1) throw FenException("Each side must have exactly one king")
            val pos = Position(board, side, castling, ep, hm, fm)
            if (pos.isInCheck(side.opposite)) throw FenException("The side not to move is in check")
            return pos
        }
    }
}
