package com.grandmaster.ai.chess

/**
 * Squares are plain Ints 0..63: a1 = 0, b1 = 1, ..., h1 = 7, a2 = 8, ..., h8 = 63.
 */
object Square {
    const val NONE = -1

    fun of(file: Int, rank: Int): Int = rank * 8 + file
    fun file(sq: Int): Int = sq and 7
    fun rank(sq: Int): Int = sq shr 3
    fun isValid(file: Int, rank: Int): Boolean = file in 0..7 && rank in 0..7

    fun name(sq: Int): String = "${'a' + file(sq)}${'1' + rank(sq)}"

    fun parse(name: String): Int {
        if (name.length != 2) return NONE
        val f = name[0] - 'a'
        val r = name[1] - '1'
        return if (isValid(f, r)) of(f, r) else NONE
    }

    fun isLight(sq: Int): Boolean = (file(sq) + rank(sq)) % 2 == 1
}
