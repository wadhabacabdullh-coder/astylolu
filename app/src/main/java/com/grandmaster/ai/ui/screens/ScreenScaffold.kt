package com.grandmaster.ai.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.grandmaster.ai.ui.theme.GmColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GmScaffold(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)?,
    actions: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        containerColor = GmColors.Background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title, style = MaterialTheme.typography.titleLarge)
                        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = GmColors.TextSecondary)
                    }
                },
                navigationIcon = {
                    if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = { actions() },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = GmColors.Background),
            )
        },
        content = content,
    )
}

/**
 * Responsive board layout: on phones in portrait the board sits on top and the
 * panel scrolls below it; on landscape/tablets the board and panel sit side by side.
 */
@Composable
fun BoardScreenLayout(
    padding: PaddingValues,
    board: @Composable (Modifier) -> Unit,
    controls: @Composable () -> Unit,
    panel: LazyListScope.() -> Unit,
) {
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .padding(padding),
    ) {
        val wide = maxWidth > maxHeight || maxWidth >= 840.dp
        if (wide) {
            Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(
                    Modifier
                        .weight(0.52f)
                        .fillMaxHeight()
                        .padding(bottom = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { board(Modifier) }
                    controls()
                }
                LazyColumn(
                    Modifier.weight(0.48f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    content = panel,
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 32.dp),
            ) {
                item(key = "board") { board(Modifier) }
                item(key = "controls") { controls() }
                panel()
            }
        }
    }
}
