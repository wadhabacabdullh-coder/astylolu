package com.grandmaster.ai.data

import com.grandmaster.ai.chess.Game

data class Opening(val eco: String, val name: String, val moves: List<String>)

data class BookContinuation(
    val san: String,
    /** Name of the most common/first opening reached through this move. */
    val openingName: String,
    val eco: String,
    /** How many named lines in the book continue with this move. */
    val lines: Int,
)

/**
 * A compact opening book of well-established main lines (ECO codes and names
 * follow standard opening theory). It contains no popularity statistics, so
 * the explorer pairs it with live Stockfish evaluations instead.
 */
object OpeningBook {

    private val raw = listOf(
        // ---------------------------------------------------------------- 1.e4 e5
        "C20" to "King's Pawn Game" to "e4 e5",
        "C40" to "King's Knight Opening" to "e4 e5 Nf3",
        "C44" to "King's Pawn Game: Nc6" to "e4 e5 Nf3 Nc6",
        "C60" to "Ruy Lopez" to "e4 e5 Nf3 Nc6 Bb5",
        "C68" to "Ruy Lopez: Exchange Variation" to "e4 e5 Nf3 Nc6 Bb5 a6 Bxc6",
        "C70" to "Ruy Lopez: Morphy Defence" to "e4 e5 Nf3 Nc6 Bb5 a6 Ba4",
        "C78" to "Ruy Lopez: Morphy Defence, 5.O-O" to "e4 e5 Nf3 Nc6 Bb5 a6 Ba4 Nf6 O-O",
        "C84" to "Ruy Lopez: Closed" to "e4 e5 Nf3 Nc6 Bb5 a6 Ba4 Nf6 O-O Be7",
        "C88" to "Ruy Lopez: Closed, 7...O-O" to "e4 e5 Nf3 Nc6 Bb5 a6 Ba4 Nf6 O-O Be7 Re1 b5 Bb3 O-O",
        "C89" to "Ruy Lopez: Marshall Attack" to "e4 e5 Nf3 Nc6 Bb5 a6 Ba4 Nf6 O-O Be7 Re1 b5 Bb3 O-O c3 d5",
        "C92" to "Ruy Lopez: Closed, 9.h3" to "e4 e5 Nf3 Nc6 Bb5 a6 Ba4 Nf6 O-O Be7 Re1 b5 Bb3 d6 c3 O-O h3",
        "C80" to "Ruy Lopez: Open Variation" to "e4 e5 Nf3 Nc6 Bb5 a6 Ba4 Nf6 O-O Nxe4",
        "C65" to "Ruy Lopez: Berlin Defence" to "e4 e5 Nf3 Nc6 Bb5 Nf6",
        "C67" to "Ruy Lopez: Berlin Defence, Berlin Wall" to "e4 e5 Nf3 Nc6 Bb5 Nf6 O-O Nxe4 d4 Nd6 Bxc6 dxc6 dxe5 Nf5 Qxd8+ Kxd8",
        "C63" to "Ruy Lopez: Schliemann Defence" to "e4 e5 Nf3 Nc6 Bb5 f5",
        "C50" to "Italian Game" to "e4 e5 Nf3 Nc6 Bc4",
        "C50" to "Italian Game: Giuoco Piano" to "e4 e5 Nf3 Nc6 Bc4 Bc5",
        "C54" to "Italian Game: Giuoco Pianissimo" to "e4 e5 Nf3 Nc6 Bc4 Bc5 c3 Nf6 d3",
        "C54" to "Italian Game: Classical Variation" to "e4 e5 Nf3 Nc6 Bc4 Bc5 c3 Nf6 d4",
        "C51" to "Italian Game: Evans Gambit" to "e4 e5 Nf3 Nc6 Bc4 Bc5 b4",
        "C55" to "Italian Game: Two Knights Defence" to "e4 e5 Nf3 Nc6 Bc4 Nf6",
        "C57" to "Two Knights Defence: Fried Liver Attack" to "e4 e5 Nf3 Nc6 Bc4 Nf6 Ng5 d5 exd5 Nxd5 Nxf7",
        "C58" to "Two Knights Defence: Polerio Defence" to "e4 e5 Nf3 Nc6 Bc4 Nf6 Ng5 d5 exd5 Na5",
        "C57" to "Two Knights Defence: Traxler Counterattack" to "e4 e5 Nf3 Nc6 Bc4 Nf6 Ng5 Bc5",
        "C50" to "Italian Game: Hungarian Defence" to "e4 e5 Nf3 Nc6 Bc4 Be7",
        "C45" to "Scotch Game" to "e4 e5 Nf3 Nc6 d4 exd4 Nxd4",
        "C45" to "Scotch Game: Classical Variation" to "e4 e5 Nf3 Nc6 d4 exd4 Nxd4 Bc5",
        "C45" to "Scotch Game: Schmidt Variation" to "e4 e5 Nf3 Nc6 d4 exd4 Nxd4 Nf6",
        "C44" to "Scotch Gambit" to "e4 e5 Nf3 Nc6 d4 exd4 Bc4",
        "C46" to "Four Knights Game" to "e4 e5 Nf3 Nc6 Nc3 Nf6",
        "C48" to "Four Knights Game: Spanish Variation" to "e4 e5 Nf3 Nc6 Nc3 Nf6 Bb5",
        "C47" to "Four Knights Game: Scotch Variation" to "e4 e5 Nf3 Nc6 Nc3 Nf6 d4",
        "C44" to "Ponziani Opening" to "e4 e5 Nf3 Nc6 c3",
        "C42" to "Petrov's Defence" to "e4 e5 Nf3 Nf6",
        "C42" to "Petrov's Defence: Classical Attack" to "e4 e5 Nf3 Nf6 Nxe5 d6 Nf3 Nxe4 d4",
        "C43" to "Petrov's Defence: Steinitz Attack" to "e4 e5 Nf3 Nf6 d4",
        "C41" to "Philidor Defence" to "e4 e5 Nf3 d6",
        "C41" to "Philidor Defence: Exchange Variation" to "e4 e5 Nf3 d6 d4 exd4",
        "C40" to "Latvian Gambit" to "e4 e5 Nf3 f5",
        "C40" to "Elephant Gambit" to "e4 e5 Nf3 d5",
        "C30" to "King's Gambit" to "e4 e5 f4",
        "C33" to "King's Gambit Accepted" to "e4 e5 f4 exf4",
        "C34" to "King's Gambit Accepted: King's Knight Gambit" to "e4 e5 f4 exf4 Nf3",
        "C30" to "King's Gambit Declined: Classical Variation" to "e4 e5 f4 Bc5",
        "C31" to "King's Gambit Declined: Falkbeer Countergambit" to "e4 e5 f4 d5",
        "C25" to "Vienna Game" to "e4 e5 Nc3",
        "C29" to "Vienna Gambit" to "e4 e5 Nc3 Nf6 f4",
        "C26" to "Vienna Game: Falkbeer Variation" to "e4 e5 Nc3 Nf6",
        "C23" to "Bishop's Opening" to "e4 e5 Bc4",
        "C21" to "Danish Gambit" to "e4 e5 d4 exd4 c3",
        "C22" to "Centre Game" to "e4 e5 d4 exd4 Qxd4",

        // ---------------------------------------------------------------- 1.e4 c5
        "B20" to "Sicilian Defence" to "e4 c5",
        "B27" to "Sicilian Defence: 2.Nf3" to "e4 c5 Nf3",
        "B50" to "Sicilian Defence: 2...d6" to "e4 c5 Nf3 d6",
        "B54" to "Sicilian Defence: Open, 2...d6" to "e4 c5 Nf3 d6 d4 cxd4 Nxd4",
        "B90" to "Sicilian Defence: Najdorf Variation" to "e4 c5 Nf3 d6 d4 cxd4 Nxd4 Nf6 Nc3 a6",
        "B90" to "Sicilian Najdorf: English Attack" to "e4 c5 Nf3 d6 d4 cxd4 Nxd4 Nf6 Nc3 a6 Be3 e5 Nb3 Be6 f3",
        "B96" to "Sicilian Najdorf: 6.Bg5" to "e4 c5 Nf3 d6 d4 cxd4 Nxd4 Nf6 Nc3 a6 Bg5",
        "B92" to "Sicilian Najdorf: Opocensky Variation" to "e4 c5 Nf3 d6 d4 cxd4 Nxd4 Nf6 Nc3 a6 Be2",
        "B70" to "Sicilian Defence: Dragon Variation" to "e4 c5 Nf3 d6 d4 cxd4 Nxd4 Nf6 Nc3 g6",
        "B76" to "Sicilian Dragon: Yugoslav Attack" to "e4 c5 Nf3 d6 d4 cxd4 Nxd4 Nf6 Nc3 g6 Be3 Bg7 f3 O-O Qd2 Nc6",
        "B80" to "Sicilian Defence: Scheveningen Variation" to "e4 c5 Nf3 d6 d4 cxd4 Nxd4 Nf6 Nc3 e6",
        "B56" to "Sicilian Defence: Classical Variation" to "e4 c5 Nf3 d6 d4 cxd4 Nxd4 Nf6 Nc3 Nc6",
        "B52" to "Sicilian Defence: Moscow Variation" to "e4 c5 Nf3 d6 Bb5+",
        "B30" to "Sicilian Defence: Old Sicilian" to "e4 c5 Nf3 Nc6",
        "B33" to "Sicilian Defence: Sveshnikov Variation" to "e4 c5 Nf3 Nc6 d4 cxd4 Nxd4 Nf6 Nc3 e5",
        "B32" to "Sicilian Defence: Kalashnikov Variation" to "e4 c5 Nf3 Nc6 d4 cxd4 Nxd4 e5",
        "B35" to "Sicilian Defence: Accelerated Dragon" to "e4 c5 Nf3 Nc6 d4 cxd4 Nxd4 g6",
        "B30" to "Sicilian Defence: Rossolimo Variation" to "e4 c5 Nf3 Nc6 Bb5",
        "B40" to "Sicilian Defence: 2...e6" to "e4 c5 Nf3 e6",
        "B47" to "Sicilian Defence: Taimanov Variation" to "e4 c5 Nf3 e6 d4 cxd4 Nxd4 Nc6",
        "B42" to "Sicilian Defence: Kan Variation" to "e4 c5 Nf3 e6 d4 cxd4 Nxd4 a6",
        "B22" to "Sicilian Defence: Alapin Variation" to "e4 c5 c3",
        "B23" to "Sicilian Defence: Closed" to "e4 c5 Nc3",
        "B21" to "Sicilian Defence: Smith-Morra Gambit" to "e4 c5 d4 cxd4 c3",
        "B23" to "Sicilian Defence: Grand Prix Attack" to "e4 c5 Nc3 Nc6 f4",

        // ---------------------------------------------------------------- other 1.e4
        "C00" to "French Defence" to "e4 e6",
        "C01" to "French Defence: Exchange Variation" to "e4 e6 d4 d5 exd5",
        "C02" to "French Defence: Advance Variation" to "e4 e6 d4 d5 e5",
        "C03" to "French Defence: Tarrasch Variation" to "e4 e6 d4 d5 Nd2",
        "C10" to "French Defence: Paulsen Variation" to "e4 e6 d4 d5 Nc3",
        "C11" to "French Defence: Classical Variation" to "e4 e6 d4 d5 Nc3 Nf6",
        "C15" to "French Defence: Winawer Variation" to "e4 e6 d4 d5 Nc3 Bb4",
        "C10" to "French Defence: Rubinstein Variation" to "e4 e6 d4 d5 Nc3 dxe4",
        "B10" to "Caro-Kann Defence" to "e4 c6",
        "B12" to "Caro-Kann Defence: Advance Variation" to "e4 c6 d4 d5 e5",
        "B13" to "Caro-Kann Defence: Exchange Variation" to "e4 c6 d4 d5 exd5 cxd5",
        "B14" to "Caro-Kann Defence: Panov-Botvinnik Attack" to "e4 c6 d4 d5 exd5 cxd5 c4",
        "B18" to "Caro-Kann Defence: Classical Variation" to "e4 c6 d4 d5 Nc3 dxe4 Nxe4 Bf5",
        "B17" to "Caro-Kann Defence: Karpov Variation" to "e4 c6 d4 d5 Nc3 dxe4 Nxe4 Nd7",
        "B11" to "Caro-Kann Defence: Two Knights Attack" to "e4 c6 Nc3 d5 Nf3",
        "B01" to "Scandinavian Defence" to "e4 d5",
        "B01" to "Scandinavian Defence: Main Line" to "e4 d5 exd5 Qxd5 Nc3 Qa5",
        "B01" to "Scandinavian Defence: Modern Variation" to "e4 d5 exd5 Nf6",
        "B07" to "Pirc Defence" to "e4 d6 d4 Nf6 Nc3 g6",
        "B09" to "Pirc Defence: Austrian Attack" to "e4 d6 d4 Nf6 Nc3 g6 f4",
        "B06" to "Modern Defence" to "e4 g6",
        "B02" to "Alekhine's Defence" to "e4 Nf6",
        "B03" to "Alekhine's Defence: Four Pawns Attack" to "e4 Nf6 e5 Nd5 d4 d6 c4 Nb6 f4",
        "B04" to "Alekhine's Defence: Modern Variation" to "e4 Nf6 e5 Nd5 d4 d6 Nf3",
        "B00" to "Nimzowitsch Defence" to "e4 Nc6",
        "B00" to "Owen's Defence" to "e4 b6",

        // ---------------------------------------------------------------- 1.d4 d5
        "D00" to "Queen's Pawn Game" to "d4 d5",
        "D02" to "London System" to "d4 d5 Nf3 Nf6 Bf4",
        "D00" to "London System: 2.Bf4" to "d4 d5 Bf4",
        "D06" to "Queen's Gambit" to "d4 d5 c4",
        "D20" to "Queen's Gambit Accepted" to "d4 d5 c4 dxc4",
        "D30" to "Queen's Gambit Declined" to "d4 d5 c4 e6",
        "D35" to "Queen's Gambit Declined: Exchange Variation" to "d4 d5 c4 e6 Nc3 Nf6 cxd5 exd5",
        "D37" to "Queen's Gambit Declined: Harrwitz Attack" to "d4 d5 c4 e6 Nc3 Nf6 Nf3 Be7 Bf4",
        "D53" to "Queen's Gambit Declined: Orthodox Defence" to "d4 d5 c4 e6 Nc3 Nf6 Bg5 Be7",
        "D32" to "Queen's Gambit Declined: Tarrasch Defence" to "d4 d5 c4 e6 Nc3 c5",
        "D38" to "Queen's Gambit Declined: Ragozin Defence" to "d4 d5 c4 e6 Nc3 Nf6 Nf3 Bb4",
        "D10" to "Slav Defence" to "d4 d5 c4 c6",
        "D15" to "Slav Defence: Main Line" to "d4 d5 c4 c6 Nf3 Nf6 Nc3",
        "D17" to "Slav Defence: Czech Variation" to "d4 d5 c4 c6 Nf3 Nf6 Nc3 dxc4 a4 Bf5",
        "D43" to "Semi-Slav Defence" to "d4 d5 c4 c6 Nf3 Nf6 Nc3 e6",
        "D45" to "Semi-Slav Defence: Meran Variation" to "d4 d5 c4 c6 Nf3 Nf6 Nc3 e6 e3 Nbd7 Bd3 dxc4 Bxc4 b5",
        "D08" to "Queen's Gambit Declined: Albin Countergambit" to "d4 d5 c4 e5",
        "D07" to "Queen's Gambit Declined: Chigorin Defence" to "d4 d5 c4 Nc6",
        "D05" to "Colle System" to "d4 d5 Nf3 Nf6 e3 e6 Bd3",

        // ---------------------------------------------------------------- 1.d4 Nf6
        "A45" to "Indian Defence" to "d4 Nf6",
        "A46" to "Indian Defence: 2.Nf3" to "d4 Nf6 Nf3",
        "A45" to "Trompowsky Attack" to "d4 Nf6 Bg5",
        "A50" to "Indian Defence: 2.c4" to "d4 Nf6 c4",
        "E20" to "Nimzo-Indian Defence" to "d4 Nf6 c4 e6 Nc3 Bb4",
        "E32" to "Nimzo-Indian Defence: Classical Variation" to "d4 Nf6 c4 e6 Nc3 Bb4 Qc2",
        "E40" to "Nimzo-Indian Defence: Rubinstein Variation" to "d4 Nf6 c4 e6 Nc3 Bb4 e3",
        "E12" to "Queen's Indian Defence" to "d4 Nf6 c4 e6 Nf3 b6",
        "E11" to "Bogo-Indian Defence" to "d4 Nf6 c4 e6 Nf3 Bb4+",
        "E00" to "Catalan Opening" to "d4 Nf6 c4 e6 g3",
        "E04" to "Catalan Opening: Open Defence" to "d4 Nf6 c4 e6 g3 d5 Bg2 dxc4",
        "E60" to "King's Indian Defence" to "d4 Nf6 c4 g6",
        "E61" to "King's Indian Defence: 3.Nc3" to "d4 Nf6 c4 g6 Nc3 Bg7",
        "E92" to "King's Indian Defence: Classical Variation" to "d4 Nf6 c4 g6 Nc3 Bg7 e4 d6 Nf3 O-O Be2 e5",
        "E97" to "King's Indian Defence: Mar del Plata Variation" to "d4 Nf6 c4 g6 Nc3 Bg7 e4 d6 Nf3 O-O Be2 e5 O-O Nc6 d5 Ne7",
        "E80" to "King's Indian Defence: Sämisch Variation" to "d4 Nf6 c4 g6 Nc3 Bg7 e4 d6 f3",
        "E76" to "King's Indian Defence: Four Pawns Attack" to "d4 Nf6 c4 g6 Nc3 Bg7 e4 d6 f4",
        "D80" to "Grünfeld Defence" to "d4 Nf6 c4 g6 Nc3 d5",
        "D85" to "Grünfeld Defence: Exchange Variation" to "d4 Nf6 c4 g6 Nc3 d5 cxd5 Nxd5 e4 Nxc3 bxc3",
        "D90" to "Grünfeld Defence: Three Knights Variation" to "d4 Nf6 c4 g6 Nc3 d5 Nf3",
        "A56" to "Benoni Defence" to "d4 Nf6 c4 c5",
        "A60" to "Modern Benoni" to "d4 Nf6 c4 c5 d5 e6",
        "A57" to "Benko Gambit" to "d4 Nf6 c4 c5 d5 b5",
        "A51" to "Budapest Gambit" to "d4 Nf6 c4 e5",
        "A53" to "Old Indian Defence" to "d4 Nf6 c4 d6",
        "A80" to "Dutch Defence" to "d4 f5",
        "A87" to "Dutch Defence: Leningrad Variation" to "d4 f5 c4 Nf6 g3 g6 Bg2 Bg7",
        "A90" to "Dutch Defence: Stonewall Variation" to "d4 f5 c4 Nf6 g3 e6 Bg2 d5",
        "A40" to "Englund Gambit" to "d4 e5",
        "A40" to "Modern Defence: 1.d4" to "d4 g6",

        // ---------------------------------------------------------------- flank openings
        "A10" to "English Opening" to "c4",
        "A20" to "English Opening: King's English" to "c4 e5",
        "A22" to "English Opening: Reversed Sicilian, 2.Nc3 Nf6" to "c4 e5 Nc3 Nf6",
        "A30" to "English Opening: Symmetrical Variation" to "c4 c5",
        "A15" to "English Opening: Anglo-Indian Defence" to "c4 Nf6",
        "A04" to "Réti Opening" to "Nf3",
        "A09" to "Réti Opening: 2.c4" to "Nf3 d5 c4",
        "A07" to "King's Indian Attack" to "Nf3 d5 g3",
        "A00" to "Grob Opening" to "g4",
        "A01" to "Nimzo-Larsen Attack" to "b3",
        "A02" to "Bird's Opening" to "f4",
        "A00" to "Polish Opening" to "b4",
        "A00" to "Van 't Kruijs Opening" to "e3",
        "A00" to "Hungarian Opening" to "g3",
    )

    val openings: List<Opening> = raw.map { (head, moves) ->
        Opening(head.first, head.second, moves.split(' ').filter { it.isNotBlank() })
    }

    /** The deepest named opening matching a prefix of [sans] (SAN moves from the start position). */
    fun identify(sans: List<String>): Opening? =
        openings.filter { o -> o.moves.size <= sans.size && sans.subList(0, o.moves.size) == o.moves }
            .maxByOrNull { it.moves.size }

    /** True if [sans] is a prefix of (or equal to) at least one book line. */
    fun isInBook(sans: List<String>): Boolean =
        openings.any { o -> sans.size <= o.moves.size && o.moves.subList(0, sans.size) == sans }

    /** Book moves that continue [sans], grouped by move. */
    fun continuations(sans: List<String>): List<BookContinuation> {
        val matches = openings.filter { o -> o.moves.size > sans.size && o.moves.subList(0, sans.size) == sans }
        return matches.groupBy { it.moves[sans.size] }.map { (san, list) ->
            val first = list.minBy { it.moves.size }
            BookContinuation(san, first.name, first.eco, list.size)
        }.sortedByDescending { it.lines }
    }

    /** SAN moves of [game] from the standard start, or null if the game began from a custom FEN. */
    fun sansOf(game: Game, upTo: Int = game.index): List<String>? {
        if (game.start.toFen() != com.grandmaster.ai.chess.Position.START_FEN) return null
        return game.moves.subList(0, upTo).map { it.san }
    }
}
