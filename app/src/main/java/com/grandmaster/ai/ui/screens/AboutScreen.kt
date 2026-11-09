package com.grandmaster.ai.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grandmaster.ai.BuildConfig
import com.grandmaster.ai.ui.components.SectionCard
import com.grandmaster.ai.ui.components.toast
import com.grandmaster.ai.ui.theme.GmColors
import com.grandmaster.ai.ui.theme.MonoFamily

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var licenseText by remember { mutableStateOf<String?>(null) }
    fun open(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { toast(context, "No browser available: $url") }
    }
    GmScaffold(title = "About", onBack = onBack) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                SectionCard(Modifier.widthIn(max = 720.dp)) {
                    Text("Grandmaster AI", style = MaterialTheme.typography.headlineMedium)
                    Text("Version ${BuildConfig.VERSION_NAME}", color = GmColors.TextSecondary)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "A chess coach and analysis board powered by the real ${BuildConfig.STOCKFISH_VERSION} engine with NNUE evaluation, running entirely on your device. " +
                            "Every evaluation, best move and principal variation comes from Stockfish; explanations are built from the engine's lines and exact board calculations, never invented.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            item {
                SectionCard(Modifier.widthIn(max = 720.dp), title = "Stockfish") {
                    Text(
                        "Stockfish is free software by the Stockfish developers (see AUTHORS), licensed under the GNU General Public License v3. " +
                            "This app bundles an unmodified Stockfish 19 build (compiled for Android from the official source) and the official NNUE network.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text("Network: ${BuildConfig.NNUE_FILE}", fontFamily = MonoFamily, fontSize = 12.sp)
                    Text("SHA-256: ${BuildConfig.NNUE_SHA256}", fontFamily = MonoFamily, fontSize = 11.sp, color = GmColors.TextSecondary)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Source code: the complete corresponding source of the engine, plus the build script used for this app, is in the app's repository under third_party/stockfish. Upstream: github.com/official-stockfish/Stockfish (tag sf_19).",
                        style = MaterialTheme.typography.bodySmall, color = GmColors.TextSecondary,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { open("https://github.com/official-stockfish/Stockfish/tree/sf_19") }) { Text("Stockfish source code") }
                    TextButton(onClick = { open("https://stockfishchess.org") }) { Text("stockfishchess.org") }
                    TextButton(onClick = {
                        licenseText = if (licenseText == null) runCatching {
                            context.assets.open("licenses/stockfish-COPYING.txt").bufferedReader().readText()
                        }.getOrElse { "License file missing: ${it.message}" } else null
                    }) { Text(if (licenseText == null) "Show GPL v3 license" else "Hide license") }
                    licenseText?.let { text ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(max = 420.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(GmColors.SurfaceHigh)
                                .verticalScroll(rememberScrollState())
                                .horizontalScroll(rememberScrollState())
                                .padding(10.dp),
                        ) { Text(text, fontFamily = MonoFamily, fontSize = 10.sp) }
                    }
                }
            }
            item {
                SectionCard(Modifier.widthIn(max = 720.dp), title = "Hikaru Mode") {
                    Text(
                        "Hikaru Mode is named in tribute to the fast, tactical style of play associated with Hikaru Nakamura. It is not affiliated with, endorsed by, or a simulation of him: " +
                            "it simply re-reads Stockfish's top lines to surface sacrifices, forcing sequences, defensive resources and attacking ideas.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            item {
                SectionCard(Modifier.widthIn(max = 720.dp), title = "How moves are labelled") {
                    Text(
                        "Game Review compares each move with Stockfish's best move using the expected-score (win%) model. " +
                            "Inaccuracy, mistake and blunder correspond to win-chance drops of 5, 10 and 20 points. " +
                            "\"Brilliant\" requires a material sacrifice (by static exchange) that is also Stockfish's top choice and keeps the evaluation; " +
                            "\"Great\" marks the only move that holds when every alternative is clearly worse.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}
