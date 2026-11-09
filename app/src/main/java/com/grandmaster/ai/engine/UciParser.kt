package com.grandmaster.ai.engine

object UciParser {

    /** Parses an "info" line that carries a score and a PV. Returns null for other info lines. */
    fun parseInfo(line: String): EngineLine? {
        if (!line.startsWith("info ")) return null
        val t = line.split(' ').filter { it.isNotEmpty() }
        var depth = 0
        var selDepth = 0
        var multipv = 1
        var score: Score? = null
        var bound = Bound.EXACT
        var nodes = 0L
        var nps = 0L
        var time = 0L
        var pv: List<String> = emptyList()
        var wdl: Triple<Int, Int, Int>? = null
        var i = 1
        while (i < t.size) {
            when (t[i]) {
                "depth" -> { depth = t.getOrNull(i + 1)?.toIntOrNull() ?: 0; i += 2 }
                "seldepth" -> { selDepth = t.getOrNull(i + 1)?.toIntOrNull() ?: 0; i += 2 }
                "multipv" -> { multipv = t.getOrNull(i + 1)?.toIntOrNull() ?: 1; i += 2 }
                "nodes" -> { nodes = t.getOrNull(i + 1)?.toLongOrNull() ?: 0; i += 2 }
                "nps" -> { nps = t.getOrNull(i + 1)?.toLongOrNull() ?: 0; i += 2 }
                "time" -> { time = t.getOrNull(i + 1)?.toLongOrNull() ?: 0; i += 2 }
                "score" -> {
                    val kind = t.getOrNull(i + 1)
                    val v = t.getOrNull(i + 2)?.toIntOrNull()
                    if (v != null) score = when (kind) {
                        "cp" -> Score.Cp(v)
                        "mate" -> Score.Mate(v)
                        else -> null
                    }
                    i += 3
                    when (t.getOrNull(i)) {
                        "lowerbound" -> { bound = Bound.LOWER; i++ }
                        "upperbound" -> { bound = Bound.UPPER; i++ }
                    }
                }
                "wdl" -> {
                    val w = t.getOrNull(i + 1)?.toIntOrNull()
                    val d = t.getOrNull(i + 2)?.toIntOrNull()
                    val l = t.getOrNull(i + 3)?.toIntOrNull()
                    if (w != null && d != null && l != null) wdl = Triple(w, d, l)
                    i += 4
                }
                "pv" -> { pv = t.subList(i + 1, t.size).toList(); i = t.size }
                "string" -> return null
                else -> i++
            }
        }
        val s = score ?: return null
        if (pv.isEmpty()) return null
        return EngineLine(multipv, depth, selDepth, s, bound, nodes, nps, time, pv, wdl)
    }

    /** Parses "bestmove e2e4 ponder e7e5". Returns (bestmove, ponder) or null. */
    fun parseBestMove(line: String): Pair<String, String?>? {
        if (!line.startsWith("bestmove")) return null
        val t = line.split(' ').filter { it.isNotEmpty() }
        val best = t.getOrNull(1) ?: return null
        val ponder = if (t.getOrNull(2) == "ponder") t.getOrNull(3) else null
        return best to ponder
    }
}
