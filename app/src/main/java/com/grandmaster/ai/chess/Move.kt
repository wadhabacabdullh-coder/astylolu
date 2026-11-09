package com.grandmaster.ai.chess

/** A move in coordinate form. [promotion] is set only for pawn promotions. */
data class Move(val from: Int, val to: Int, val promotion: PieceType? = null) {

    /** UCI long algebraic notation, e.g. "e2e4", "e7e8q". */
    fun uci(): String = buildString {
        append(Square.name(from))
        append(Square.name(to))
        promotion?.let { append(it.sanLetter.lowercaseChar()) }
    }

    override fun toString(): String = uci()

    companion object {
        fun fromUci(uci: String): Move? {
            val s = uci.trim()
            if (s.length !in 4..5) return null
            val from = Square.parse(s.substring(0, 2))
            val to = Square.parse(s.substring(2, 4))
            if (from == Square.NONE || to == Square.NONE) return null
            val promo = if (s.length == 5) {
                PieceType.fromLetter(s[4])?.takeIf {
                    it != PieceType.PAWN && it != PieceType.KING
                } ?: return null
            } else null
            return Move(from, to, promo)
        }
    }
}
