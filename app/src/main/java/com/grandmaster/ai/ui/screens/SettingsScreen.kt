package com.grandmaster.ai.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.grandmaster.ai.GrandmasterApp
import com.grandmaster.ai.data.EngineSettings
import com.grandmaster.ai.engine.EngineStatus
import com.grandmaster.ai.ui.components.SectionCard
import com.grandmaster.ai.ui.theme.GmColors
import com.grandmaster.ai.ui.theme.MonoFamily
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(app: GrandmasterApp, onBack: () -> Unit) {
    val repo = app.settingsRepository
    val s by repo.settings.collectAsStateWithLifecycle(repo.defaults)
    val status by app.engineRepository.status.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    fun update(f: (EngineSettings) -> EngineSettings) = scope.launch { repo.update(f, s) }

    GmScaffold(title = "Engine Settings", subtitle = "Stockfish 19 configuration", onBack = onBack) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                SectionCard(Modifier.widthIn(max = 720.dp), title = "Engine") {
                    when (val st = status) {
                        is EngineStatus.Ready -> {
                            InfoRow("Engine", st.info.name)
                            InfoRow("Binary", "${st.info.binary} (${st.info.cpuFeature})")
                            InfoRow("Evaluation", "NNUE · ${st.info.network}")
                            InfoRow("Threads / Hash", "${st.info.threads} / ${st.info.hashMb} MB")
                        }
                        is EngineStatus.Failed -> Text("Engine error: ${st.message}", color = GmColors.Danger)
                        is EngineStatus.Installing -> Text("Installing neural network… ${(st.progress * 100).toInt()}%")
                        else -> Text("Starting…", color = GmColors.TextSecondary)
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { app.engineRepository.restart() }) { Text("Restart engine") }
                }
            }
            item {
                SectionCard(Modifier.widthIn(max = 720.dp), title = "Analysis") {
                    IntSlider("Analysis depth", s.depth, 8..40, { "$it ply" }) { v -> update { it.copy(depth = v) } }
                    IntSlider("Calculation time limit", s.moveTimeSec, 0..60, { if (it == 0) "no limit (depth only)" else "$it s" }) { v -> update { it.copy(moveTimeSec = v) } }
                    IntSlider("Candidate moves (MultiPV)", s.multiPv, 1..5, { "$it lines" }) { v -> update { it.copy(multiPv = v) } }
                    IntSlider("Review depth (Game Review)", s.reviewDepth, 8..24, { "$it ply" }) { v -> update { it.copy(reviewDepth = v) } }
                    Text("Analysis always runs at full strength. Hikaru Mode always uses 5 lines.", style = MaterialTheme.typography.bodySmall, color = GmColors.TextSecondary)
                }
            }
            item {
                SectionCard(Modifier.widthIn(max = 720.dp), title = "Performance") {
                    IntSlider("Threads", s.threads, 1..repo.maxThreads, { "$it of ${repo.maxThreads} cores" }) { v -> update { it.copy(threads = v) } }
                    IntSlider("Hash (transposition table)", s.hashMb, 16..repo.maxHashMb, { "$it MB" }, step = 16) { v -> update { it.copy(hashMb = v) } }
                    Text("More threads calculate faster but use more battery. Hash is capped at about 1/8 of this device's RAM.", style = MaterialTheme.typography.bodySmall, color = GmColors.TextSecondary)
                }
            }
            item {
                SectionCard(Modifier.widthIn(max = 720.dp), title = "Engine strength (Play vs Stockfish)") {
                    ToggleRow("Limit strength by Elo", s.playLimitElo) { v -> update { it.copy(playLimitElo = v) } }
                    if (s.playLimitElo) {
                        IntSlider("Target Elo", s.playElo, 1320..3190, { "$it" }, step = 10) { v -> update { it.copy(playElo = v) } }
                    } else {
                        IntSlider("Skill level", s.playSkillLevel, 0..20, { if (it == 20) "20 (maximum)" else "$it" }) { v -> update { it.copy(playSkillLevel = v) } }
                    }
                    IntSlider("Thinking time per move", s.playMoveTimeSec, 1..30, { "$it s" }) { v -> update { it.copy(playMoveTimeSec = v) } }
                }
            }
            item {
                SectionCard(Modifier.widthIn(max = 720.dp), title = "Board") {
                    ToggleRow("Show engine arrows", s.showArrows) { v -> update { it.copy(showArrows = v) } }
                    ToggleRow("Show coordinates", s.showCoordinates) { v -> update { it.copy(showCoordinates = v) } }
                    ToggleRow("Live analysis on by default", s.liveAnalysis) { v -> update { it.copy(liveAnalysis = v) } }
                }
            }
            item {
                OutlinedButton(onClick = { scope.launch { repo.reset() } }) { Text("Reset to defaults") }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.padding(vertical = 3.dp)) {
        Text(label, color = GmColors.TextSecondary, modifier = Modifier.weight(0.4f), style = MaterialTheme.typography.bodyMedium)
        Text(value, modifier = Modifier.weight(0.6f), style = MaterialTheme.typography.bodyMedium, fontFamily = MonoFamily)
    }
}

@Composable
private fun ToggleRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = value, onCheckedChange = onChange)
    }
}

@Composable
private fun IntSlider(
    label: String,
    value: Int,
    range: IntRange,
    format: (Int) -> String,
    step: Int = 1,
    onCommit: (Int) -> Unit,
) {
    var local by remember(value) { mutableFloatStateOf(value.toFloat()) }
    val shown = (((local.roundToInt() - range.first) / step) * step + range.first).coerceIn(range)
    Column(Modifier.padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Text(format(shown), fontWeight = FontWeight.SemiBold, color = GmColors.Accent, fontFamily = MonoFamily)
        }
        Slider(
            value = local,
            onValueChange = { local = it },
            onValueChangeFinished = { onCommit(shown) },
            valueRange = range.first.toFloat()..range.last.toFloat().coerceAtLeast(range.first + 1f),
            colors = SliderDefaults.colors(thumbColor = GmColors.Accent, activeTrackColor = GmColors.Accent),
        )
    }
}
