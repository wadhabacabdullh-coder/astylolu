package com.grandmaster.ai.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.grandmaster.ai.GrandmasterApp
import com.grandmaster.ai.analysis.Evaluation
import com.grandmaster.ai.analysis.HikaruInsights
import com.grandmaster.ai.analysis.MoveExplainer
import com.grandmaster.ai.analysis.MoveExplanation
import com.grandmaster.ai.analysis.TacticalInsight
import com.grandmaster.ai.analysis.ThreatInfo
import com.grandmaster.ai.chess.Game
import com.grandmaster.ai.chess.Position
import com.grandmaster.ai.data.EngineSettings
import com.grandmaster.ai.engine.EngineStatus
import com.grandmaster.ai.engine.Score
import com.grandmaster.ai.engine.SearchLimits
import com.grandmaster.ai.engine.SearchRequest
import com.grandmaster.ai.engine.SearchSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AnalysisMode { STANDARD, HIKARU }

data class LineUi(
    val rank: Int,
    val scoreWhite: Score,
    val depth: Int,
    val explanation: MoveExplanation,
    val pvUci: List<String>,
)

data class AnalysisUi(
    val fen: String = "",
    val running: Boolean = false,
    val finished: Boolean = false,
    val depth: Int = 0,
    val nps: Long = 0,
    val nodes: Long = 0,
    val lines: List<LineUi> = emptyList(),
    val insights: List<TacticalInsight> = emptyList(),
    val threat: ThreatInfo? = null,
    val error: String? = null,
) {
    val bestScoreWhite: Score? get() = lines.firstOrNull()?.scoreWhite
}

/**
 * Runs Stockfish on the displayed position. In live mode every position change
 * restarts the search (the previous one is stopped). Parsing and explanation
 * building run on background dispatchers; the UI only receives finished state.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class AnalysisViewModel(app: Application) : AndroidViewModel(app) {
    private val container = app as GrandmasterApp
    private val engine = container.engineRepository
    val engineStatus: StateFlow<EngineStatus> = engine.status

    private val settings: StateFlow<EngineSettings> =
        container.settingsRepository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, container.settingsRepository.defaults)

    private val _ui = MutableStateFlow(AnalysisUi())
    val ui: StateFlow<AnalysisUi> = _ui.asStateFlow()

    private val _live = MutableStateFlow(true)
    val live: StateFlow<Boolean> = _live.asStateFlow()

    private val mode = MutableStateFlow(AnalysisMode.STANDARD)
    private val active = MutableStateFlow(false)
    private val paused = MutableStateFlow(false)
    private val game = MutableStateFlow<Game?>(null)
    private val manualTrigger = MutableStateFlow(0)
    private var liveInitialised = false

    init {
        viewModelScope.launch {
            settings.collect { s ->
                if (!liveInitialised) { _live.value = s.liveAnalysis; liveInitialised = true }
            }
        }
        viewModelScope.launch {
            combine(
                game, mode, active, paused, _live, settings.map { Triple(it.depth, it.moveTimeSec, it.multiPv) }.distinctUntilChanged(),
                manualTrigger,
            ) { args ->
                @Suppress("UNCHECKED_CAST")
                AnalysisJob(args)
            }.distinctUntilChanged().collectLatest { job ->
                val g = job.game ?: return@collectLatest
                val pos = g.current
                val fen = pos.toFen()
                if (!job.active || job.paused) {
                    _ui.value = _ui.value.copy(running = false)
                    return@collectLatest
                }
                if (fen != _ui.value.fen) {
                    _ui.value = AnalysisUi(fen = fen, threat = HikaruInsights.opponentThreat(pos))
                }
                if (pos.legalMoves().isEmpty()) {
                    _ui.value = AnalysisUi(fen = fen, finished = true)
                    return@collectLatest
                }
                if (!job.live && job.trigger == lastManualRun) {
                    _ui.value = _ui.value.copy(running = false)
                    return@collectLatest
                }
                lastManualRun = job.trigger
                delay(80) // debounce rapid navigation
                runSearch(g, pos, job.mode)
            }
        }
    }

    private var lastManualRun = 0

    private data class AnalysisJob(
        val game: Game?, val mode: AnalysisMode, val active: Boolean, val paused: Boolean,
        val live: Boolean, val limits: Triple<Int, Int, Int>, val trigger: Int,
    ) {
        constructor(a: Array<Any?>) : this(
            a[0] as Game?, a[1] as AnalysisMode, a[2] as Boolean, a[3] as Boolean, a[4] as Boolean,
            @Suppress("UNCHECKED_CAST") (a[5] as Triple<Int, Int, Int>), a[6] as Int,
        )

        override fun equals(other: Any?): Boolean = other is AnalysisJob &&
            other.game?.current?.toFen() == game?.current?.toFen() && other.mode == mode && other.active == active &&
            other.paused == paused && other.live == live && other.limits == limits && other.trigger == trigger

        override fun hashCode(): Int = (game?.current?.toFen()).hashCode()
    }

    private suspend fun runSearch(g: Game, pos: Position, m: AnalysisMode) {
        val s = settings.value
        val multiPv = when (m) {
            AnalysisMode.STANDARD -> s.multiPv.coerceIn(1, 5)
            AnalysisMode.HIKARU -> 5
        }.coerceAtMost(pos.legalMoves().size)
        val limits = SearchLimits(
            depth = s.depth,
            moveTimeMs = if (s.moveTimeSec > 0) s.moveTimeSec * 1000L else null,
        )
        val req = SearchRequest(fen = g.start.toFen(), moves = g.uciMoves(), limits = limits, multiPv = multiPv)
        val fen = pos.toFen()
        _ui.value = _ui.value.copy(running = true, error = null)
        engine.search(req)
            .conflate()
            .map { snap -> buildUi(pos, fen, snap, m) }
            .flowOn(Dispatchers.Default)
            .catch { e -> _ui.value = _ui.value.copy(running = false, error = e.message ?: "Engine error") }
            .collect { _ui.value = it }
    }

    private fun buildUi(pos: Position, fen: String, snap: SearchSnapshot, m: AnalysisMode): AnalysisUi {
        val lines = snap.lines.filter { it.pv.isNotEmpty() }
        val explanations: List<MoveExplanation>
        val insights: List<TacticalInsight>
        if (m == AnalysisMode.HIKARU) {
            val (ex, ins) = HikaruInsights.analyse(pos, lines)
            explanations = ex
            insights = ins
        } else {
            explanations = lines.mapIndexedNotNull { i, l -> MoveExplainer.explain(pos, l, lines.getOrNull(1).takeIf { i == 0 }) }
            insights = emptyList()
        }
        val lineUis = lines.zip(explanations).filter { (l, e) -> l.pv.first() == e.move.uci() }.map { (l, e) ->
            LineUi(l.multipv, Evaluation.whitePov(l.score, pos.sideToMove), l.depth, e, l.pv)
        }
        return AnalysisUi(
            fen = fen,
            running = !snap.finished,
            finished = snap.finished,
            depth = snap.depth,
            nps = snap.nps,
            nodes = snap.nodes,
            lines = lineUis,
            insights = insights,
            threat = HikaruInsights.opponentThreat(pos),
        )
    }

    fun setGame(g: Game) { game.value = g }
    fun setMode(m: AnalysisMode) { mode.value = m }
    fun setActive(a: Boolean) { active.value = a }
    fun setPaused(p: Boolean) { paused.value = p }
    fun setLive(on: Boolean) { _live.value = on }
    fun analyzeNow() { manualTrigger.value = manualTrigger.value + 1 }
    fun retryEngine() = engine.restart()
}
