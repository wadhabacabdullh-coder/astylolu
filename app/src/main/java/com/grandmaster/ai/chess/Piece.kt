package com.grandmaster.ai.chess

enum class PieceColor {
    WHITE, BLACK;

    val opposite: PieceColor get() = if (this == WHITE) BLACK else WHITE
    val fenChar: Char get() = if (this == WHITE) 'w' else 'b'
    val displayName: String get() = if (this == WHITE) "White" else "Black"
}

enum class PieceType(val sanLetter: Char, val value: Int, val displayName: String) {
    PAWN('P', 100, "pawn"),
    KNIGHT('N', 320, "knight"),
    BISHOP('B', 330, "bishop"),
    ROOK('R', 500, "rook"),
    QUEEN('Q', 900, "queen"),
    KING('K', 20000, "king");

    companion object {
        fun fromLetter(c: Char): PieceType? = when (c.uppercaseChar()) {
            'P' -> PAWN
            'N' -> KNIGHT
            'B' -> BISHOP
            'R' -> ROOK
            'Q' -> QUEEN
            'K' -> KING
            else -> null
        }
    }
}

/** An immutable chess piece. Use [Piece.of] to obtain the shared instance. */
class Piece private constructor(val color: PieceColor, val type: PieceType) {
    val fenChar: Char
        get() = if (color == PieceColor.WHITE) type.sanLetter else type.sanLetter.lowercaseChar()

    override fun toString(): String = fenChar.toString()

    companion object {
        private val cache = Array(2) { c -> Array(6) { t -> Piece(PieceColor.entries[c], PieceType.entries[t]) } }

        fun of(color: PieceColor, type: PieceType): Piece = cache[color.ordinal][type.ordinal]

        fun fromFen(c: Char): Piece? {
            val type = PieceType.fromLetter(c) ?: return null
            return of(if (c.isUpperCase()) PieceColor.WHITE else PieceColor.BLACK, type)
        }
    }
}
