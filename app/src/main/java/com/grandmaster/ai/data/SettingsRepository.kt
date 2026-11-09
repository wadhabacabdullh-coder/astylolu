package com.grandmaster.ai.data

import android.app.ActivityManager
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "engine_settings")

data class EngineSettings(
    /** Maximum search depth for analysis. */
    val depth: Int = 22,
    /** Maximum calculation time per analysis in seconds; 0 = limited by depth only. */
    val moveTimeSec: Int = 10,
    /** Number of candidate lines (MultiPV) for the analysis board. */
    val multiPv: Int = 3,
    val threads: Int = 2,
    val hashMb: Int = 64,
    /** Strength used when playing against the engine. */
    val playSkillLevel: Int = 20,
    val playLimitElo: Boolean = false,
    val playElo: Int = 2000,
    /** Seconds the engine may think per move in play mode. */
    val playMoveTimeSec: Int = 2,
    /** Depth used for each position in Game Review. */
    val reviewDepth: Int = 16,
    val showArrows: Boolean = true,
    val showCoordinates: Boolean = true,
    val liveAnalysis: Boolean = true,
)

class SettingsRepository(private val context: Context) {

    val maxThreads: Int = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)

    /** Upper bound for the transposition table, about 1/8 of physical RAM, capped at 1 GB. */
    val maxHashMb: Int = run {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        ((info.totalMem / (1024 * 1024)) / 8).toInt().coerceIn(16, 1024)
    }

    val defaults: EngineSettings = EngineSettings(
        threads = (maxThreads / 2).coerceIn(1, 4),
        hashMb = 64.coerceAtMost(maxHashMb),
    )

    val settings: Flow<EngineSettings> = context.dataStore.data.map { p ->
        EngineSettings(
            depth = p[DEPTH] ?: defaults.depth,
            moveTimeSec = p[MOVE_TIME] ?: defaults.moveTimeSec,
            multiPv = p[MULTI_PV] ?: defaults.multiPv,
            threads = (p[THREADS] ?: defaults.threads).coerceIn(1, maxThreads),
            hashMb = (p[HASH] ?: defaults.hashMb).coerceIn(16, maxHashMb),
            playSkillLevel = p[SKILL] ?: defaults.playSkillLevel,
            playLimitElo = p[LIMIT_ELO] ?: defaults.playLimitElo,
            playElo = p[ELO] ?: defaults.playElo,
            playMoveTimeSec = p[PLAY_TIME] ?: defaults.playMoveTimeSec,
            reviewDepth = p[REVIEW_DEPTH] ?: defaults.reviewDepth,
            showArrows = p[ARROWS] ?: defaults.showArrows,
            showCoordinates = p[COORDS] ?: defaults.showCoordinates,
            liveAnalysis = p[LIVE] ?: defaults.liveAnalysis,
        )
    }

    suspend fun update(transform: (EngineSettings) -> EngineSettings, current: EngineSettings) {
        val s = transform(current)
        context.dataStore.edit { p ->
            p[DEPTH] = s.depth
            p[MOVE_TIME] = s.moveTimeSec
            p[MULTI_PV] = s.multiPv
            p[THREADS] = s.threads
            p[HASH] = s.hashMb
            p[SKILL] = s.playSkillLevel
            p[LIMIT_ELO] = s.playLimitElo
            p[ELO] = s.playElo
            p[PLAY_TIME] = s.playMoveTimeSec
            p[REVIEW_DEPTH] = s.reviewDepth
            p[ARROWS] = s.showArrows
            p[COORDS] = s.showCoordinates
            p[LIVE] = s.liveAnalysis
        }
    }

    suspend fun reset() {
        context.dataStore.edit { it.clear() }
    }

    private companion object {
        val DEPTH = intPreferencesKey("depth")
        val MOVE_TIME = intPreferencesKey("move_time")
        val MULTI_PV = intPreferencesKey("multipv")
        val THREADS = intPreferencesKey("threads")
        val HASH = intPreferencesKey("hash")
        val SKILL = intPreferencesKey("skill")
        val LIMIT_ELO = booleanPreferencesKey("limit_elo")
        val ELO = intPreferencesKey("elo")
        val PLAY_TIME = intPreferencesKey("play_time")
        val REVIEW_DEPTH = intPreferencesKey("review_depth")
        val ARROWS = booleanPreferencesKey("arrows")
        val COORDS = booleanPreferencesKey("coords")
        val LIVE = booleanPreferencesKey("live")
    }
}
