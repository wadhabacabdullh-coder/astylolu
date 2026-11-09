package com.grandmaster.ai.engine

/** Engine score from the point of view of the side to move in the analysed position. */
sealed class Score {
    data class Cp(val cp: Int) : Score()
    /** Mate in [moves] full moves; positive = side to move mates, negative = gets mated. */
    data class Mate(val moves: Int) : Score()

    fun negate(): Score = when (this) {
        is Cp -> Cp(-cp)
        is Mate -> Mate(-moves)
    }

    /** Centipawn-like value usable for comparisons (mates map to large values). */
    fun toComparable(): Int = when (this) {
        is Cp -> cp
        is Mate -> if (moves > 0) MATE_VALUE - moves else -MATE_VALUE - moves
    }

    companion object {
        const val MATE_VALUE = 100_000
    }
}

enum class Bound { EXACT, LOWER, UPPER }

/** One principal variation reported by the engine ("info ... multipv N ... pv ..."). */
data class EngineLine(
    val multipv: Int,
    val depth: Int,
    val selDepth: Int,
    val score: Score,
    val bound: Bound,
    val nodes: Long,
    val nps: Long,
    val timeMs: Long,
    val pv: List<String>,
    /** Win/draw/loss in permille from the side to move, when UCI_ShowWDL is on. */
    val wdl: Triple<Int, Int, Int>? = null,
)

data class SearchLimits(
    val depth: Int? = null,
    val moveTimeMs: Long? = null,
    val nodes: Long? = null,
    val infinite: Boolean = false,
)

/** How strong the engine should play. Analysis always uses [FULL]. */
data class StrengthOptions(
    val skillLevel: Int = 20,
    val limitElo: Int? = null,
) {
    companion object {
        val FULL = StrengthOptions()
    }
}

data class SearchRequest(
    val fen: String,
    val moves: List<String> = emptyList(),
    val limits: SearchLimits,
    val multiPv: Int = 1,
    val searchMoves: List<String> = emptyList(),
    val strength: StrengthOptions = StrengthOptions.FULL,
)

/** A snapshot of an ongoing (or finished) search. Lines are sorted by multipv. */
data class SearchSnapshot(
    val request: SearchRequest,
    val lines: List<EngineLine>,
    val bestMove: String? = null,
    val ponder: String? = null,
    val finished: Boolean = false,
) {
    val depth: Int get() = lines.firstOrNull()?.depth ?: 0
    val nps: Long get() = lines.maxOfOrNull { it.nps } ?: 0
    val nodes: Long get() = lines.maxOfOrNull { it.nodes } ?: 0
}
