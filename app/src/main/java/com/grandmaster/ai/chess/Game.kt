package com.grandmaster.ai.chess

data class PlayedMove(
    val move: Move,
    val san: String,
    /** Position after the move was played. */
    val after: Position,
)

enum class GameResult(val pgn: String) {
    WHITE_WINS("1-0"), BLACK_WINS("0-1"), DRAW("1/2-1/2"), ONGOING("*");

    companion object {
        fun fromPgn(s: String): GameResult = entries.firstOrNull { it.pgn == s } ?: ONGOING
    }
}

/** Why the game ended (if it did) at the currently displayed position. */
enum class GameEnd(val description: String) {
    CHECKMATE("Checkmate"),
    STALEMATE("Stalemate"),
    INSUFFICIENT_MATERIAL("Draw by insufficient material"),
    FIFTY_MOVES("Draw by the fifty-move rule"),
    THREEFOLD("Draw by threefold repetition"),
}

/**
 * An immutable game record: a starting position, a main line of moves and a
 * cursor ([index] = number of moves applied to reach the displayed position).
 * Undo/redo just moves the cursor; playing a new move from an earlier cursor
 * truncates the moves after it.
 */
data class Game(
    val start: Position = Position.startPosition(),
    val moves: List<PlayedMove> = emptyList(),
    val index: Int = 0,
    val tags: Map<String, String> = emptyMap(),
) {
    val current: Position get() = if (index == 0) start else moves[index - 1].after
    val lastMove: Move? get() = if (index == 0) null else moves[index - 1].move
    val canUndo: Boolean get() = index > 0
    val canRedo: Boolean get() = index < moves.size
    val isAtEnd: Boolean get() = index == moves.size

    fun positionAt(i: Int): Position = if (i == 0) start else moves[i - 1].after

    fun play(move: Move): Game {
        val pos = current
        val san = pos.san(move)
        val after = pos.play(move)
        val kept = moves.subList(0, index)
        // If the move equals the next move in the line, just step forward (keeps the rest).
        if (index < moves.size && moves[index].move == move) return copy(index = index + 1)
        return copy(moves = kept + PlayedMove(move, san, after), index = index + 1)
    }

    fun playSan(san: String): Game {
        val move = current.parseSan(san) ?: throw IllegalArgumentException("Illegal or unknown move '$san'")
        return play(move)
    }

    fun undo(): Game = if (canUndo) copy(index = index - 1) else this
    fun redo(): Game = if (canRedo) copy(index = index + 1) else this
    fun goTo(i: Int): Game = copy(index = i.coerceIn(0, moves.size))

    /** Number (1-based) of the full move that [plyIndex] (0-based move index) belongs to. */
    fun moveNumberOf(plyIndex: Int): Int {
        val startBlack = start.sideToMove == PieceColor.BLACK
        val ply = plyIndex + if (startBlack) 1 else 0
        return start.fullmoveNumber + ply / 2
    }

    fun isWhiteMove(plyIndex: Int): Boolean {
        val startBlack = start.sideToMove == PieceColor.BLACK
        return (plyIndex + if (startBlack) 1 else 0) % 2 == 0
    }

    /** The reason the game is over at the displayed position, or null. */
    fun gameEnd(): GameEnd? {
        val pos = current
        if (pos.legalMoves().isEmpty()) return if (pos.isInCheck()) GameEnd.CHECKMATE else GameEnd.STALEMATE
        if (pos.isInsufficientMaterial()) return GameEnd.INSUFFICIENT_MATERIAL
        if (pos.halfmoveClock >= 100) return GameEnd.FIFTY_MOVES
        val key = pos.repetitionKey()
        var count = 0
        for (i in 0..index) if (positionAt(i).repetitionKey() == key) count++
        if (count >= 3) return GameEnd.THREEFOLD
        return null
    }

    fun result(): GameResult = when (gameEnd()) {
        null -> tags["Result"]?.let { GameResult.fromPgn(it) }?.takeIf { isAtEnd } ?: GameResult.ONGOING
        GameEnd.CHECKMATE -> if (current.sideToMove == PieceColor.WHITE) GameResult.BLACK_WINS else GameResult.WHITE_WINS
        else -> GameResult.DRAW
    }

    /** UCI move list from the start position up to the cursor. */
    fun uciMoves(upTo: Int = index): List<String> = moves.subList(0, upTo).map { it.move.uci() }

    companion object {
        fun fromFen(fen: String): Game = Game(start = Position.fromFen(fen))
    }
}
