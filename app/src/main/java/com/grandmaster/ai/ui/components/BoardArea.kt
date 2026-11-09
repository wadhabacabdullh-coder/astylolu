package com.grandmaster.ai.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FirstPage
import androidx.compose.material.icons.automirrored.filled.LastPage
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.outlined.AddBox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.grandmaster.ai.chess.Game
import com.grandmaster.ai.chess.PieceColor
import com.grandmaster.ai.engine.Score
import com.grandmaster.ai.ui.GameViewModel
import com.grandmaster.ai.ui.theme.GmColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Board + evaluation bar + navigation controls, wired to the shared [GameViewModel]. */
@Composable
fun BoardArea(
    vm: GameViewModel,
    game: Game,
    flipped: Boolean,
    ply: Int,
    evalWhite: Score?,
    arrows: List<BoardArrow>,
    showCoordinates: Boolean,
    modifier: Modifier = Modifier,
    showEvalBar: Boolean = true,
    movableColor: PieceColor? = null,
    interactive: Boolean = true,
    squareHighlights: Map<Int, Color> = emptyMap(),
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val barWidth = if (showEvalBar) 16.dp else 0.dp
        val gap = if (showEvalBar) 6.dp else 0.dp
        val boardSize = minOf(maxWidth - barWidth - gap, if (maxHeight == Dp.Infinity) maxWidth else maxHeight)
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            if (showEvalBar) {
                Box(Modifier.width(barWidth).height(boardSize)) {
                    EvalBar(evalWhite, flipped, width = barWidth)
                }
            }
            ChessBoard(
                position = game.current,
                modifier = Modifier.size(boardSize),
                flipped = flipped,
                lastMove = game.lastMove,
                ply = ply,
                arrows = arrows,
                squareHighlights = squareHighlights,
                showCoordinates = showCoordinates,
                interactive = interactive,
                movableColor = movableColor,
                onMove = vm::onMove,
            )
        }
    }
}

@Composable
fun BoardControls(
    vm: GameViewModel,
    game: Game,
    modifier: Modifier = Modifier,
    playMode: Boolean = false,
) {
    var menu by remember { mutableStateOf(false) }
    var fenDialog by remember { mutableStateOf(false) }
    var pgnDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } }.getOrNull()
            }
            if (text == null) toast(context, "Could not read the file")
            else vm.importPgn(text)?.let { toast(context, it) }
        }
    }

    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        if (!playMode) {
            CtrlButton(Icons.Filled.FirstPage, "First move", game.canUndo) { vm.first() }
            CtrlButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Undo / previous move", game.canUndo) { vm.undo() }
            CtrlButton(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Redo / next move", game.canRedo) { vm.redo() }
            CtrlButton(Icons.AutoMirrored.Filled.LastPage, "Last move", game.canRedo) { vm.last() }
        } else {
            TextButton(onClick = { vm.undo() }, enabled = game.canUndo) { Text("Take back") }
        }
        CtrlButton(Icons.Filled.SwapVert, "Flip board", true) { vm.flip() }
        Box {
            CtrlButton(Icons.Filled.MoreVert, "Game menu", true) { menu = true }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("New game") }, leadingIcon = { Icon(Icons.Outlined.AddBox, null) }, onClick = { menu = false; vm.newGame() })
                HorizontalDivider()
                DropdownMenuItem(text = { Text("Import FEN") }, leadingIcon = { Icon(Icons.Filled.Upload, null) }, onClick = { menu = false; fenDialog = true })
                DropdownMenuItem(text = { Text("Import PGN (paste)") }, leadingIcon = { Icon(Icons.Filled.Upload, null) }, onClick = { menu = false; pgnDialog = true })
                DropdownMenuItem(text = { Text("Open PGN file") }, leadingIcon = { Icon(Icons.Filled.FileOpen, null) }, onClick = {
                    menu = false; openFile.launch(arrayOf("application/x-chess-pgn", "application/vnd.chess-pgn", "text/*", "application/octet-stream"))
                })
                HorizontalDivider()
                DropdownMenuItem(text = { Text("Export PGN (share)") }, leadingIcon = { Icon(Icons.Filled.Share, null) }, onClick = {
                    menu = false; sharePgn(context, vm.exportPgn())
                })
                DropdownMenuItem(text = { Text("Copy PGN") }, leadingIcon = { Icon(Icons.Filled.ContentCopy, null) }, onClick = {
                    menu = false; copy(context, "PGN", vm.exportPgn()); toast(context, "PGN copied")
                })
                DropdownMenuItem(text = { Text("Copy FEN") }, leadingIcon = { Icon(Icons.Filled.ContentCopy, null) }, onClick = {
                    menu = false; copy(context, "FEN", vm.currentFen()); toast(context, "FEN copied")
                })
            }
        }
    }

    if (fenDialog) TextInputDialog(
        title = "Import FEN",
        hint = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
        initial = "",
        singleLine = true,
        onDismiss = { fenDialog = false },
        onSubmit = { text -> vm.importFen(text).also { if (it == null) fenDialog = false } },
    )
    if (pgnDialog) TextInputDialog(
        title = "Import PGN",
        hint = "[Event \"...\"]\n\n1. e4 e5 2. Nf3 ...",
        initial = "",
        singleLine = false,
        onDismiss = { pgnDialog = false },
        onSubmit = { text -> vm.importPgn(text).also { if (it == null) pgnDialog = false } },
    )
}

@Composable
private fun CtrlButton(icon: ImageVector, desc: String, enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(icon, contentDescription = desc, tint = if (enabled) GmColors.TextPrimary else GmColors.Outline)
    }
}

/** Dialog with a text field; [onSubmit] returns an error message or null on success. */
@Composable
fun TextInputDialog(
    title: String,
    hint: String,
    initial: String,
    singleLine: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (String) -> String?,
) {
    var text by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; error = null },
                    placeholder = { Text(hint, style = MaterialTheme.typography.bodySmall) },
                    singleLine = singleLine,
                    minLines = if (singleLine) 1 else 6,
                    maxLines = if (singleLine) 1 else 12,
                    isError = error != null,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    keyboardOptions = KeyboardOptions(imeAction = if (singleLine) ImeAction.Done else ImeAction.Default),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (error != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(error!!, color = GmColors.Danger, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = {
                    val clip = (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip
                    clip?.getItemAt(0)?.coerceToText(context)?.toString()?.let { text = it }
                }) {
                    Icon(Icons.Filled.Refresh, null, Modifier.padding(end = 6.dp))
                    Text("Paste from clipboard")
                }
            }
        },
        confirmButton = { TextButton(onClick = { error = onSubmit(text) }, enabled = text.isNotBlank()) { Text("Load") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

fun sharePgn(context: Context, pgn: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/x-chess-pgn"
        putExtra(Intent.EXTRA_TEXT, pgn)
        putExtra(Intent.EXTRA_SUBJECT, "Chess game (PGN)")
    }
    context.startActivity(Intent.createChooser(send, "Export PGN").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

fun copy(context: Context, label: String, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
}

fun toast(context: Context, text: String) {
    android.widget.Toast.makeText(context, text, android.widget.Toast.LENGTH_SHORT).show()
}
