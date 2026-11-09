package com.grandmaster.ai.chess

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PgnException(message: String) : IllegalArgumentException(message)

object Pgn {
    private val SEVEN_TAG_ROSTER = listOf("Event", "Site", "Date", "Round", "White", "Black", "Result")

    /** Exports the full main line of [game] (regardless of the cursor) as PGN. */
    fun export(game: Game, extraTags: Map<String, String> = emptyMap()): String {
        val full = game.goTo(game.moves.size)
        val result = full.result()
        val tags = LinkedHashMap<String, String>()
        val date = SimpleDateFormat("yyyy.MM.dd", Locale.US).format(Date())
        val defaults = mapOf(
            "Event" to "Grandmaster AI analysis", "Site" to "Grandmaster AI (Android)", "Date" to date,
            "Round" to "-", "White" to "?", "Black" to "?",
        )
        for (t in SEVEN_TAG_ROSTER) tags[t] = game.tags[t] ?: extraTags[t] ?: defaults[t] ?: ""
        tags["Result"] = result.pgn
        val startFen = game.start.toFen()
        if (startFen != Position.START_FEN) {
            tags["SetUp"] = "1"
            tags["FEN"] = startFen
        }
        for ((k, v) in game.tags + extraTags) if (k !in tags && k != "SetUp" && k != "FEN") tags[k] = v

        val sb = StringBuilder()
        for ((k, v) in tags) sb.append('[').append(k).append(" \"").append(v.replace("\\", "\\\\").replace("\"", "\\\"")).append("\"]\n")
        sb.append('\n')

        val tokens = ArrayList<String>()
        for ((i, pm) in game.moves.withIndex()) {
            val white = game.isWhiteMove(i)
            val num = game.moveNumberOf(i)
            if (white) tokens += "$num."
            else if (i == 0) tokens += "$num..."
            tokens += pm.san
        }
        tokens += result.pgn
        var line = StringBuilder()
        for (t in tokens) {
            if (line.isNotEmpty() && line.length + 1 + t.length > 79) {
                sb.append(line).append('\n'); line = StringBuilder()
            }
            if (line.isNotEmpty()) line.append(' ')
            line.append(t)
        }
        if (line.isNotEmpty()) sb.append(line).append('\n')
        return sb.toString()
    }

    /** Parses the first game in [text]. Comments, NAGs and variations are skipped. */
    fun parse(text: String): Game {
        val tags = LinkedHashMap<String, String>()
        val tagRegex = Regex("""\[\s*(\w+)\s+"((?:[^"\\]|\\.)*)"\s*]""")
        var body = StringBuilder()
        var inMoves = false
        for (rawLine in text.lines()) {
            val line = rawLine.trim()
            if (!inMoves && line.startsWith("[")) {
                tagRegex.findAll(line).forEach { m ->
                    tags[m.groupValues[1]] = m.groupValues[2].replace("\\\"", "\"").replace("\\\\", "\\")
                }
                continue
            }
            if (line.startsWith("%")) continue // escape mechanism
            if (line.isEmpty()) {
                if (inMoves && endsWithResult(body)) break
                continue
            }
            if (inMoves && line.startsWith("[") && endsWithResult(body)) break // next game
            inMoves = true
            body.append(line).append('\n')
        }

        var game = tags["FEN"]?.let {
            try { Game.fromFen(it) } catch (e: FenException) { throw PgnException("Invalid FEN tag: ${e.message}") }
        } ?: Game()
        game = game.copy(tags = tags)

        val tokens = tokenize(body.toString())
        for (tok in tokens) {
            if (tok in RESULTS) break
            game = try {
                game.playSan(tok)
            } catch (e: IllegalArgumentException) {
                val moveNo = game.moveNumberOf(game.moves.size)
                throw PgnException("Illegal move '$tok' at move $moveNo")
            }
        }
        return game
    }

    private val RESULTS = setOf("1-0", "0-1", "1/2-1/2", "*")

    private fun endsWithResult(body: CharSequence): Boolean {
        val t = body.trim()
        return RESULTS.any { t.endsWith(it) }
    }

    private fun tokenize(body: String): List<String> {
        val out = ArrayList<String>()
        var i = 0
        var depth = 0
        val n = body.length
        while (i < n) {
            val c = body[i]
            when {
                c == '{' -> { val end = body.indexOf('}', i); i = if (end < 0) n else end + 1 }
                c == ';' -> { val end = body.indexOf('\n', i); i = if (end < 0) n else end + 1 }
                c == '(' -> { depth++; i++ }
                c == ')' -> { depth = (depth - 1).coerceAtLeast(0); i++ }
                c.isWhitespace() -> i++
                else -> {
                    var j = i
                    while (j < n && !body[j].isWhitespace() && body[j] !in "{}();") j++
                    val tok = body.substring(i, j)
                    i = j
                    if (depth > 0) continue
                    var t = tok
                    if (t.startsWith("$")) continue // NAG
                    // Strip leading move numbers such as "12." or "12..." (possibly glued to the move).
                    t = t.replace(Regex("^\\d+\\.+"), "")
                    if (t.isEmpty()) continue
                    if (t.all { it.isDigit() }) continue
                    if (t == "--") throw PgnException("Null moves are not supported")
                    out += t
                }
            }
        }
        return out
    }
}
