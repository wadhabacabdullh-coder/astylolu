package com.grandmaster.ai

import android.app.Application
import com.grandmaster.ai.data.SettingsRepository
import com.grandmaster.ai.engine.EngineRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class GrandmasterApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    lateinit var settingsRepository: SettingsRepository
        private set
    lateinit var engineRepository: EngineRepository
        private set

    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository(this)
        engineRepository = EngineRepository(this, appScope)
        settingsRepository.settings.onEach { engineRepository.updateSettings(it) }.launchIn(appScope)
        // Install the network and start Stockfish in the background right away.
        engineRepository.warmUp()
    }
}
