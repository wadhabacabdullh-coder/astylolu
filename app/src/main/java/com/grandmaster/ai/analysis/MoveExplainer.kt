package com.grandmaster.ai.analysis

import com.grandmaster.ai.chess.Move
import com.grandmaster.ai.chess.PieceColor
import com.grandmaster.ai.chess.PieceType
import com.grandmaster.ai.chess.Position
import com.grandmaster.ai.chess.Square
import com.grandmaster.ai.engine.EngineLine
import com.grandmaster.ai.engine.Score

enum class MoveTag(val label: String) {
    MATE("Mate"),
    CHECK("Check"),
    CAPTURE("Capture"),
    WINS_MATERIAL("Wins material"),
    SACRIFICE("Sacrifice"),
    FORK("Fork"),
    THREAT("Threat"),
    DISCOVERED("Discovered attack"),
    PROMOTION("Promotion"),
    CASTLING("Castling"),
    DEVELOPMENT("Development"),
    CENTER("Centre"),
    PASSED_PAWN("Passed pawn"),
    OPEN_FILE("Open file"),
    ESCAPE("Saves piece"),
    DEFENDS("Defence"),
    PARRIES_THREAT("Parries threat"),
    KING_ATTACK("King attack"),
    ACTIVITY("Activity"),
    QUIET("Quiet move"),
    ONLY_MOVE("Only move"),
    FORCING("Forcing line"),
}

data class MoveExplanation(
    val move: Move,
    val san: String,
    /** Engine score after the move, from White's point of view (null if unknown). */
    val scoreWhite: Score?,
    val tags: Set<MoveTag>,
    val reasons: List<String>,
    val pvSan: String,
    /** Number of consecutive forcing moves (checks/captures) by the mover at the start of the PV. */
    val forcingMoves: Int,
    /** Static exchange result of the move for the mover (negative = material offered). */
    val see: Int,
)

/**
 * Builds human-readable explanations for engine moves. Every statement is
 * derived either from the Stockfish line (score, PV) or from exact board
 * computations (attacks, static exchange, pawn structure). Nothing is invented.
 */
object MoveExplainer {

    fun explain(
        pos: Position,
        line: EngineLine,
        secondBest: EngineLine? = null,
    ): MoveExplanation? {
        val move = line.pv.firstOrNull()?.let { Move.fromUci(it) } ?: return null
        if (!pos.isLegal(move)) return null
        return explainMove(pos, move, line.score, line.pv, line.depth, secondBest?.score)
    }

    /**
     * @param scoreStm engine score of the position *before* the move from the mover's point of view,
     *                 assuming [move] is played (i.e. the score of the line starting with [move]).
     */
    fun explainMove(
        pos: Position,
        move: Move,
        scoreStm: Score?,
        pv: List<String> = listOf(move.uci()),
        depth: Int = 0,
        secondBestStm: Score? = null,
    ): MoveExplanation {
        val us = pos.sideToMove
        val them = us.opposite
        val piece = pos[move.from]!!
        val after = pos.play(move)
        val san = pos.san(move)
        val tags = LinkedHashSet<MoveTag>()
        val reasons = ArrayList<String>()
        val themName = them.displayName
        val see = Tactics.see(pos, move)
        val captured = pos.capturedPiece(move)

        // ---- forced mate / mate
        if (after.isCheckmate) {
            tags += MoveTag.MATE
            reasons += "Checkmate! ${themName}'s king has no escape."
        } else if (scoreStm is Score.Mate && scoreStm.moves > 0) {
            tags += MoveTag.MATE
            reasons += "Forces checkmate in ${scoreStm.moves} (Stockfish has found a forced mate)."
        }

        // ---- check
        val givesCheck = after.isInCheck()
        if (givesCheck && !after.isCheckmate) {
            tags += MoveTag.CHECK
            val checkers = after.attackers(after.kingSquare(them), us)
            if (checkers.any { it != move.to }) {
                tags += MoveTag.DISCOVERED
                reasons += "Discovered check: moving the ${piece.type.displayName} unleashes an attack on the king."
            } else {
                val replies = after.legalMoves().size
                reasons += "Gives check, so $themName must respond to it" + if (replies <= 3) " (only $replies legal ${if (replies == 1) "reply" else "replies"})." else "."
            }
        }

        // ---- special moves
        if (pos.isCastling(move)) {
            tags += MoveTag.CASTLING
            reasons += "Castles: tucks the king away and connects the rooks."
        }
        if (move.promotion != null) {
            tags += MoveTag.PROMOTION
            reasons += "Promotes the pawn to a ${move.promotion.displayName}."
        }

        // ---- captures and material
        if (captured != null) {
            tags += MoveTag.CAPTURE
            when {
                see >= 100 -> {
                    tags += MoveTag.WINS_MATERIAL
                    reasons += "Wins material: captures the ${captured.type.displayName} on ${Square.name(move.to)} (static exchange: +${see / 100} pawn${if (see >= 200) "s" else ""})."
                }
                see >= 0 -> reasons += "Captures the ${captured.type.displayName} on ${Square.name(move.to)}" +
                    if (Tactics.value(captured.type) == Tactics.value(piece.type)) " (an even trade)." else "."
                else -> reasons += "Captures on ${Square.name(move.to)}, giving up material for a bigger goal."
            }
        }

        // ---- sacrifice (material offered, engine still approves)
        val isSacrifice = see <= -200 && !after.isCheckmate
        if (isSacrifice) {
            tags += MoveTag.SACRIFICE
            val offered = if (captured == null) "the ${piece.type.displayName}" else "material"
            reasons += "Sacrifices $offered (static exchange ${see / 100}), yet Stockfish rates the line " +
                (scoreStm?.let { "at ${Evaluation.formatShort(Evaluation.whitePov(it, us))}" } ?: "favourably") + "."
        }

        // ---- threats from the moved piece
        val movedType = move.promotion ?: piece.type
        val targets = after.attacksFrom(move.to).mapNotNull { sq ->
            val t = after[sq] ?: return@mapNotNull null
            if (t.color != them) return@mapNotNull null
            if (t.type == PieceType.KING) return@mapNotNull sq
            val defended = Tactics.isDefended(after, sq, them)
            if (!defended || Tactics.value(t.type) > Tactics.value(movedType)) sq else null
        }
        val nonKingTargets = targets.filter { after[it]?.type != PieceType.KING }
        if (targets.size >= 2 && nonKingTargets.isNotEmpty()) {
            // A fork only matters if the forking piece cannot simply be taken for free.
            val forkerSafe = after.attackers(move.to, them).isEmpty() || see >= 0
            if (forkerSafe) {
                tags += MoveTag.FORK
                val names = targets.map { sq -> "${after[sq]!!.type.displayName} on ${Square.name(sq)}" }
                reasons += "Forks the ${names.joinToString(" and ")}."
            }
        } else if (nonKingTargets.isNotEmpty()) {
            tags += MoveTag.THREAT
            val sq = nonKingTargets.maxBy { Tactics.value(after[it]!!.type) }
            val t = after[sq]!!
            val undefended = !Tactics.isDefended(after, sq, them)
            reasons += "Attacks the ${if (undefended) "undefended " else ""}${t.type.displayName} on ${Square.name(sq)}."
        }

        // ---- discovered attacks on pieces (not check)
        if (!givesCheck) {
            val before = pos.nullMove()
            for ((sq, p) in after.pieces()) {
                if (p.color != them || p.type == PieceType.KING) continue
                val nowAttackers = after.attackers(sq, us).filter { it != move.to }
                val wasAttackers = before.attackers(sq, us).filter { it != move.from }
                if (nowAttackers.size > wasAttackers.size && Tactics.value(p.type) >= 300 &&
                    nowAttackers.any { a -> a !in wasAttackers && Tactics.value(after[a]!!.type) < Tactics.value(p.type) }
                ) {
                    tags += MoveTag.DISCOVERED
                    reasons += "Opens a line: the ${p.type.displayName} on ${Square.name(sq)} is now attacked."
                    break
                }
            }
        }

        // ---- defence
        val threatBefore = if (pos.isInCheck()) null else Tactics.bestCapture(pos.nullMove())
        val threatAfter = if (after.legalMoves().isEmpty()) null else Tactics.bestCapture(after)
        if (pos.isInCheck()) {
            tags += MoveTag.DEFENDS
            reasons += "Gets out of check."
        } else if (threatBefore != null && threatBefore.first >= 100 && (threatAfter == null || threatAfter.first < threatBefore.first)) {
            val (_, sq) = threatBefore
            if (sq == move.from) {
                tags += MoveTag.ESCAPE
                reasons += "Moves the attacked ${piece.type.displayName} out of danger."
            } else {
                tags += MoveTag.PARRIES_THREAT
                val p = pos[sq]
                reasons += "Parries the threat against the ${p?.type?.displayName ?: "piece"} on ${Square.name(sq)}."
            }
        }

        // ---- king attack
        val zone = Tactics.kingZone(after, them)
        if (piece.type != PieceType.KING && !after.isCheckmate) {
            val hits = after.attacksFrom(move.to).count { it in zone }
            val hitsBefore = if (pos[move.from] != null) pos.attacksFrom(move.from).count { it in Tactics.kingZone(pos, them) } else 0
            if (hits >= 2 && hits > hitsBefore) {
                tags += MoveTag.KING_ATTACK
                reasons += "Joins the attack on the enemy king (eyes $hits squares around it)."
            }
        }

        // ---- positional facts
        val rankFromWhite = if (us == PieceColor.WHITE) Square.rank(move.from) else 7 - Square.rank(move.from)
        if ((piece.type == PieceType.KNIGHT || piece.type == PieceType.BISHOP) && rankFromWhite == 0 && pos.fullmoveNumber <= 15) {
            tags += MoveTag.DEVELOPMENT
            reasons += "Develops the ${piece.type.displayName} from the back rank."
        }
        if (piece.type == PieceType.PAWN && move.to in CENTER && pos.fullmoveNumber <= 15) {
            tags += MoveTag.CENTER
            reasons += "Claims space in the centre with the ${Square.name(move.to)}-pawn."
        }
        if (piece.type == PieceType.KNIGHT && move.to in CENTER_16 && move.from !in CENTER_16) {
            tags += MoveTag.ACTIVITY
            reasons += "Centralises the knight on ${Square.name(move.to)}."
        }
        if (piece.type == PieceType.PAWN && move.promotion == null && Tactics.isPassedPawn(after, move.to)) {
            tags += MoveTag.PASSED_PAWN
            val rel = if (us == PieceColor.WHITE) Square.rank(move.to) + 1 else 8 - Square.rank(move.to)
            reasons += "Advances a passed pawn (now on its ${ordinal(rel)} rank)."
        }
        if (piece.type == PieceType.ROOK && Square.file(move.to) != Square.file(move.from)) {
            val f = Square.file(move.to)
            if (!Tactics.fileHasPawn(after, f, null)) {
                tags += MoveTag.OPEN_FILE
                reasons += "Puts the rook on the open ${'a' + f}-file."
            } else if (!Tactics.fileHasPawn(after, f, us)) {
                tags += MoveTag.OPEN_FILE
                reasons += "Puts the rook on the half-open ${'a' + f}-file."
            }
        }
        if (piece.type in setOf(PieceType.BISHOP, PieceType.ROOK, PieceType.QUEEN, PieceType.KNIGHT) && MoveTag.ACTIVITY !in tags) {
            val before = pos.attacksFrom(move.from).size
            val now = after.attacksFrom(move.to).size
            if (now - before >= 4) {
                tags += MoveTag.ACTIVITY
                reasons += "Activates the ${piece.type.displayName}: it now controls $now squares instead of $before."
            }
        }

        // ---- forcing line
        var forcing = 0
        run {
            var p = pos
            for ((i, uci) in pv.withIndex()) {
                val m = Move.fromUci(uci) ?: break
                if (!p.isLegal(m)) break
                val next = p.play(m)
                if (i % 2 == 0) {
                    if (p.isCapture(m) || next.isInCheck()) forcing++ else break
                }
                p = next
            }
        }
        if (forcing >= 2) {
            tags += MoveTag.FORCING
            reasons += "Starts a forcing sequence: $forcing checks or captures in a row in the main line."
        }

        if (!givesCheck && captured == null && MoveTag.THREAT !in tags && MoveTag.FORK !in tags && move.promotion == null) {
            tags += MoveTag.QUIET
        }

        // ---- only move
        if (scoreStm != null && secondBestStm != null) {
            val gap = Evaluation.winPercent(scoreStm) - Evaluation.winPercent(secondBestStm)
            if (gap >= 15) {
                tags += MoveTag.ONLY_MOVE
                reasons += "The only good move: the next best option is clearly worse (${Evaluation.formatShort(Evaluation.whitePov(secondBestStm, us))})."
            }
        }

        val scoreWhite = scoreStm?.let { Evaluation.whitePov(it, us) }
        if (scoreWhite != null) {
            val depthText = if (depth > 0) " at depth $depth" else ""
            reasons += "Stockfish evaluation$depthText: ${Evaluation.format(scoreWhite)} — ${Evaluation.describe(scoreWhite).lowercaseFirst()}."
        }
        if (reasons.size == (if (scoreWhite != null) 1 else 0)) {
            reasons.add(0, "A quiet improving move; Stockfish ranks it as the strongest continuation here.")
        }

        return MoveExplanation(
            move = move,
            san = san,
            scoreWhite = scoreWhite,
            tags = tags,
            reasons = reasons,
            pvSan = Evaluation.pvToSan(pos, pv),
            forcingMoves = forcing,
            see = see,
        )
    }

    private fun String.lowercaseFirst() = if (isEmpty()) this else this[0].lowercaseChar() + substring(1)

    private fun ordinal(n: Int) = when (n) {
        1 -> "1st"; 2 -> "2nd"; 3 -> "3rd"; else -> "${n}th"
    }

    private val CENTER = setOf(Square.parse("d4"), Square.parse("e4"), Square.parse("d5"), Square.parse("e5"))
    private val CENTER_16 = (2..5).flatMap { f -> (2..5).map { r -> Square.of(f, r) } }.toSet()

    /** Annotation symbol for a move given its classification-free facts. */
    fun annotation(tags: Set<MoveTag>, isBest: Boolean): String = when {
        isBest && MoveTag.SACRIFICE in tags -> "!!"
        isBest && (MoveTag.ONLY_MOVE in tags || MoveTag.MATE in tags) -> "!"
        isBest -> "!"
        else -> ""
    }
}
