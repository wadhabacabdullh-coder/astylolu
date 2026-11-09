package com.grandmaster.ai.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.grandmaster.ai.GrandmasterApp
import com.grandmaster.ai.chess.FenException
import com.grandmaster.ai.chess.Game
import com.grandmaster.ai.chess.Move
import com.grandmaster.ai.chess.Pgn
import com.grandmaster.ai.chess.PgnException
import com.grandmaster.ai.chess.PieceColor
import com.grandmaster.ai.data.EngineSettings
import com.grandmaster.ai.engine.SearchLimits
import com.grandmaster.ai.engine.SearchRequest
import com.grandmaster.ai.engine.StrengthOptions
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PlayMode(val userColor: PieceColor, val strengthLabel: String)

/** Shared game state (board, history, play-vs-engine) used by every board screen. */
class GameViewModel(app: Application) : AndroidViewModel(app) {
    private val container = app as GrandmasterApp
    private val engine = container.engineRepository

    val settings: StateFlow<EngineSettings> =
        container.settingsRepository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, container.settingsRepository.defaults)

    private val _game = MutableStateFlow(Game())
    val game: StateFlow<Game> = _game.asStateFlow()

    private val _flipped = MutableStateFlow(false)
    val flipped: StateFlow<Boolean> = _flipped.asStateFlow()

    private val _playMode = MutableStateFlow<PlayMode?>(null)
    val playMode: StateFlow<PlayMode?> = _playMode.asStateFlow()

    private val _engineThinking = MutableStateFlow(false)
    val engineThinking: StateFlow<Boolean> = _engineThinking.asStateFlow()

    /** Increments when exactly one move is added at the end (for board animation). */
    private val _ply = MutableStateFlow(0)
    val ply: StateFlow<Int> = _ply.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    private var engineJob: Job? = null

    private fun setGame(g: Game, animate: Boolean = false) {
        _game.value = g
        _ply.value = if (animate) _ply.value + 1 else _ply.value + 2
        maybeEngineMove()
    }

    fun onMove(move: Move) {
        val g = _game.value
        if (!g.current.isLegal(move)) return
        val mode = _playMode.value
        if (mode != null && (g.current.sideToMove != mode.userColor || _engineThinking.value)) return
        setGame(g.play(move), animate = true)
    }

    fun undo() {
        val mode = _playMode.value
        if (mode != null) {
            // Take back to the user's previous turn and drop the moves after it.
            engineJob?.cancel(); _engineThinking.value = false
            var g = _game.value
            if (g.index == 0) return
            g = g.undo()
            if (g.current.sideToMove != mode.userColor && g.index > 0) g = g.undo()
            setGame(g.copy(moves = g.moves.subList(0, g.index)))
            return
        }
        setGame(_game.value.undo())
    }

    fun redo() {
        if (_playMode.value != null) return
        val g = _game.value
        if (g.canRedo) setGame(g.redo(), animate = true)
    }

    fun goTo(index: Int) {
        if (_playMode.value != null) return
        val g = _game.value
        setGame(g.goTo(index), animate = index == g.index + 1)
    }

    fun first() = goTo(0)
    fun last() = goTo(_game.value.moves.size)
    fun flip() { _flipped.value = !_flipped.value }

    fun newGame() {
        stopPlay()
        setGame(Game())
        viewModelScope.launch { engine.newGame() }
    }

    /** Returns null on success or an error message. */
    fun importFen(fen: String): String? = try {
        stopPlay()
        setGame(Game.fromFen(fen.trim()))
        _messages.tryEmit("Position loaded")
        null
    } catch (e: FenException) {
        e.message ?: "Invalid FEN"
    }

    fun importPgn(text: String): String? = try {
        stopPlay()
        val g = Pgn.parse(text)
        setGame(g.goTo(g.moves.size))
        _messages.tryEmit("Game loaded: ${g.moves.size} moves")
        null
    } catch (e: PgnException) {
        e.message ?: "Invalid PGN"
    } catch (e: IllegalArgumentException) {
        e.message ?: "Invalid PGN"
    }

    fun exportPgn(): String {
        val mode = _playMode.value
        val extra = if (mode != null) {
            val engineName = "Stockfish 19 (${mode.strengthLabel})"
            if (mode.userColor == PieceColor.WHITE) mapOf("White" to "You", "Black" to engineName)
            else mapOf("White" to engineName, "Black" to "You")
        } else emptyMap()
        return Pgn.export(_game.value, extra)
    }

    fun currentFen(): String = _game.value.current.toFen()

    fun loadGame(game: Game) {
        stopPlay()
        setGame(game)
    }

    // ------------------------------------------------------------ play mode

    fun startPlay(userColor: PieceColor) {
        val s = settings.value
        val label = if (s.playLimitElo) "Elo ${s.playElo}" else "Skill ${s.playSkillLevel}/20"
        _playMode.value = PlayMode(userColor, label)
        _flipped.value = userColor == PieceColor.BLACK
        setGame(Game())
        viewModelScope.launch { engine.newGame() }
        maybeEngineMove()
    }

    fun stopPlay() {
        engineJob?.cancel()
        engineJob = null
        _engineThinking.value = false
        _playMode.value = null
    }

    private fun maybeEngineMove() {
        val mode = _playMode.value ?: return
        val g = _game.value
        if (!g.isAtEnd || g.gameEnd() != null || g.current.sideToMove == mode.userColor) return
        if (engineJob?.isActive == true) return
        val s = settings.value
        engineJob = viewModelScope.launch {
            _engineThinking.value = true
            try {
                val strength = if (s.playLimitElo) StrengthOptions(limitElo = s.playElo.coerceIn(1320, 3190))
                else StrengthOptions(skillLevel = s.playSkillLevel)
                val req = SearchRequest(
                    fen = g.start.toFen(), moves = g.uciMoves(),
                    limits = SearchLimits(moveTimeMs = s.playMoveTimeSec.coerceAtLeast(1) * 1000L),
                    strength = strength,
                )
                val result = engine.search(req).last()
                val mv = result.bestMove?.let { Move.fromUci(it) }
                val now = _game.value
                if (mv != null && now === g && now.current.isLegal(mv)) {
                    _engineThinking.value = false
                    setGame(now.play(mv), animate = true)
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) _messages.tryEmit("Engine error: ${e.message}")
            } finally {
                _engineThinking.value = false
            }
        }
    }
}
