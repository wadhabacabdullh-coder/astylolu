package com.grandmaster.ai.ui

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.grandmaster.ai.GrandmasterApp
import com.grandmaster.ai.MainActivity
import com.grandmaster.ai.chess.Move
import com.grandmaster.ai.chess.PieceColor
import com.grandmaster.ai.chess.PieceType
import com.grandmaster.ai.chess.Square
import com.grandmaster.ai.engine.EngineStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.Shadows.shadowOf
import android.os.Looper
import java.time.Duration
import androidx.compose.ui.test.isRoot
import java.io.File

/**
 * End-to-end UI tests of the real app (MainActivity) on the JVM with Robolectric.
 * The engine is a host build of the same vendored Stockfish source
 * (env STOCKFISH_HOST_BIN); tests are skipped without it.
 * Screenshots are written to app/build/screenshots.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class AppUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private val app get() = rule.activity.application as GrandmasterApp
    private val gameVm get() = ViewModelProvider(rule.activity)[GameViewModel::class.java]
    private val analysisVm get() = ViewModelProvider(rule.activity)[AnalysisViewModel::class.java]

    @Before fun setUp() {
        val bin = System.getenv("STOCKFISH_HOST_BIN")
        assumeTrue("STOCKFISH_HOST_BIN not set", bin != null && File(bin).canExecute())
        val libDir = File(app.filesDir.parentFile, "test-native-libs").apply { mkdirs() }
        File(bin!!).copyTo(File(libDir, "libstockfish.so"), overwrite = true).setExecutable(true)
        app.applicationInfo.nativeLibraryDir = libDir.absolutePath
        runBlocking {
            val repo = app.settingsRepository
            repo.update({ it.copy(depth = 14, moveTimeSec = 4, threads = 2, hashMb = 32, reviewDepth = 10, playMoveTimeSec = 1) }, repo.settings.first())
        }
        app.engineRepository.restart()
        waitFor(120_000) { app.engineRepository.status.value is EngineStatus.Ready }
    }

    /** Waits for [cond] while advancing Robolectric's simulated clock so delayed main-thread work runs. */
    private fun waitFor(timeoutMs: Long, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (!cond()) {
            if (System.currentTimeMillis() > end) {
                val ui = analysisVm.ui.value
                throw AssertionError(
                    "Condition not met within $timeoutMs ms; engine=${app.engineRepository.status.value}; " +
                        "analysis(running=${ui.running}, finished=${ui.finished}, depth=${ui.depth}, lines=${ui.lines.size}, " +
                        "error=${ui.error}, fen=${ui.fen}); board=${gameVm.game.value.current.toFen()}; live=${analysisVm.live.value}"
                )
            }
            rule.mainClock.advanceTimeBy(50)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50))
            Thread.sleep(20)
        }
        rule.waitForIdle()
    }

    private fun shot(name: String) {
        repeat(10) { rule.mainClock.advanceTimeBy(50); shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50)) }
        val dir = File(System.getProperty("screenshots.dir") ?: "build/screenshots").apply { mkdirs() }
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bmp))
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun open(title: String) {
        rule.onAllNodesWithText(title).onFirst().performClick()
        rule.waitForIdle()
    }

    private fun waitAnalysisDone(timeout: Long = 60_000) {
        waitFor(timeout) {
            val ui = analysisVm.ui.value
            ui.finished && ui.lines.isNotEmpty() && ui.fen == gameVm.game.value.current.toFen()
        }
        rule.waitForIdle()
    }

    private fun board(): SemanticsNodeInteraction =
        rule.onNode(hasContentDescription("Chess board", substring = true))

    /** Taps the centre of [square] on the (unflipped) board. */
    private fun tapSquare(square: String) {
        val sq = Square.parse(square)
        board().performTouchInput {
            val s = width / 8f
            click(Offset((Square.file(sq) + 0.5f) * s, (7 - Square.rank(sq) + 0.5f) * s))
        }
        rule.waitForIdle()
    }

    @Test fun homeScreenShowsEngineAndAllPages() {
        rule.onNodeWithText("Grandmaster AI").assertExists()
        rule.onNodeWithText("Stockfish 19 · NNUE", substring = true).assertExists()
        for (t in listOf("Hikaru Mode", "Game Review", "Opening Explorer", "Engine Settings", "About")) {
            rule.onNodeWithText(t).assertExists()
        }
        shot("01_home_phone")
    }

    @Test fun analysisFindsMateAndExplainsIt() {
        open("Analyse")
        rule.runOnUiThread { gameVm.importFen("r1bqkb1r/pppp1ppp/2n2n2/4p2Q/2B1P3/8/PPPP1PPP/RNB1K1NR w KQkq - 4 4") }
        waitAnalysisDone()
        assertEquals("h5f7", analysisVm.ui.value.lines.first().pvUci.first())
        rule.onAllNodesWithText("Qxf7#", substring = true).onFirst().assertExists()
        rule.onNodeWithText("BEST MOVE").assertExists()
        shot("02_analysis_phone_mate")
    }

    @Test fun tapToMoveDragAndUndoRedo() {
        open("Analyse")
        tapSquare("e2"); tapSquare("e4")
        assertEquals(listOf("e4"), gameVm.game.value.moves.map { it.san })
        // Drag the black knight g8 -> f6.
        board().performTouchInput {
            val s = width / 8f
            swipe(Offset(6.5f * s, 0.5f * s), Offset(5.5f * s, 2.5f * s), durationMillis = 300)
        }
        rule.waitForIdle()
        assertEquals(listOf("e4", "Nf6"), gameVm.game.value.moves.map { it.san })
        // Illegal tap sequence does nothing.
        tapSquare("e4"); tapSquare("e6")
        assertEquals(2, gameVm.game.value.moves.size)
        rule.onNodeWithContentDescription("Undo / previous move").performClick()
        assertEquals(1, gameVm.game.value.index)
        rule.onNodeWithContentDescription("Redo / next move").performClick()
        assertEquals(2, gameVm.game.value.index)
        rule.onNodeWithContentDescription("Flip board").performClick()
        assertTrue(gameVm.flipped.value)
        shot("03_analysis_after_moves_flipped")
    }

    @Test fun promotionDialogLetsUserUnderpromote() {
        open("Analyse")
        rule.runOnUiThread { gameVm.importFen("7k/P7/8/8/8/8/8/K7 w - - 0 1") }
        rule.waitForIdle()
        tapSquare("a7"); tapSquare("a8")
        rule.onNodeWithText("Promote pawn to").assertExists()
        shot("04_promotion_dialog")
        rule.onNodeWithContentDescription("knight").performClick()
        rule.waitForIdle()
        assertEquals(Move(Square.parse("a7"), Square.parse("a8"), PieceType.KNIGHT), gameVm.game.value.lastMove)
    }

    @Test fun hikaruModeFindsTacticalShot() {
        rule.runOnUiThread { gameVm.importFen("r3k2r/ppp2ppp/2n5/3q4/8/2N5/PPP2PPP/R2QK2R w KQkq - 0 1") }
        open("Hikaru Mode")
        waitAnalysisDone()
        waitFor(10_000) { analysisVm.ui.value.insights.isNotEmpty() }
        rule.onNodeWithText("TACTICAL OPPORTUNITIES").assertExists()
        assertEquals(5, analysisVm.ui.value.lines.size)
        shot("05_hikaru_phone")
    }

    @Test fun gameReviewOfImportedPgn() {
        rule.runOnUiThread {
            gameVm.importPgn(
                """[White "Paul Morphy"]
                   [Black "Duke Karl / Count Isouard"]
                   [Result "1-0"]
                   1. e4 e5 2. Nf3 d6 3. d4 Bg4 4. dxe5 Bxf3 5. Qxf3 dxe5 6. Bc4 Nf6 7. Qb3 Qe7
                   8. Nc3 c6 9. Bg5 b5 10. Nxb5 cxb5 11. Bxb5+ Nbd7 12. O-O-O Rd8 13. Rxd7 Rxd7
                   14. Rd1 Qe6 15. Bxd7+ Nxd7 16. Qb8+ Nxb8 17. Rd8# 1-0""".trimIndent()
            )
        }
        open("Game Review")
        rule.onNodeWithText("Review this game (33 moves)").performClick()
        val reviewVm = ViewModelProvider(rule.activity)[ReviewViewModel::class.java]
        waitFor(240_000) { reviewVm.ui.value.complete }
        val ui = reviewVm.ui.value
        assertEquals(33, ui.moves.size)
        assertTrue(ui.whiteAccuracy!! > ui.blackAccuracy!!)
        assertTrue(ui.moves.last().san == "Rd8#")
        rule.waitForIdle()
        rule.onNodeWithText("ACCURACY").assertExists()
        shot("06_review_phone")
        rule.runOnUiThread { gameVm.goTo(18) } // after 9...b5
        rule.waitForIdle()
        shot("07_review_selected_move")
    }

    @Test fun openingExplorerShowsTheory() {
        rule.runOnUiThread { gameVm.importPgn("1. e4 e5 2. Nf3 Nc6") }
        open("Opening Explorer")
        val explorerVm = ViewModelProvider(rule.activity)[ExplorerViewModel::class.java]
        waitFor(60_000) { !explorerVm.ui.value.evaluating && explorerVm.ui.value.moves.isNotEmpty() }
        val moves = explorerVm.ui.value.moves
        assertTrue(moves.any { it.san == "Bb5" && it.book != null && it.scoreWhite != null })
        rule.waitForIdle()
        rule.onAllNodesWithText("Ruy Lopez", substring = true).onFirst().assertExists()
        shot("08_explorer_phone")
    }

    @Test fun playAgainstStockfish() {
        rule.onNodeWithText("Play").performClick()
        rule.onNode(hasText("White")).performClick()
        rule.waitForIdle()
        assertEquals(PieceColor.WHITE, gameVm.playMode.value?.userColor)
        tapSquare("d2"); tapSquare("d4")
        waitFor(30_000) { gameVm.game.value.moves.size == 2 }
        assertEquals("d4", gameVm.game.value.moves.first().san)
        shot("09_play_vs_engine")
    }

    @Test fun settingsAndAboutPages() {
        open("Engine Settings")
        rule.onNodeWithText("Analysis depth").assertExists()
        rule.onNodeWithText("Restart engine").assertExists()
        shot("10_settings_phone")
        rule.onNodeWithContentDescription("Back").performClick()
        open("About")
        rule.onNodeWithText("Show GPL v3 license").performClick()
        rule.onNodeWithText("GNU GENERAL PUBLIC LICENSE", substring = true).assertExists()
        shot("11_about_phone")
    }

    @Test @Config(qualifiers = "w320dp-h568dp-hdpi")
    fun smallPhoneLayout() {
        open("Analyse")
        waitAnalysisDone()
        shot("12_analysis_small_phone")
    }

    @Test @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    fun tabletLandscapeLayout() {
        shot("13_home_tablet")
        open("Analyse")
        rule.runOnUiThread { gameVm.importPgn("1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 a6") }
        waitAnalysisDone()
        shot("14_analysis_tablet_landscape")
        rule.onNodeWithContentDescription("Back").performClick()
        open("Hikaru Mode")
        waitAnalysisDone()
        shot("15_hikaru_tablet_landscape")
    }

    @Test @Config(qualifiers = "w891dp-h411dp-land-xxhdpi")
    fun phoneLandscapeLayout() {
        open("Analyse")
        waitAnalysisDone()
        shot("16_analysis_phone_landscape")
    }

    @Test fun liveAnalysisToggleAndManualAnalyze() {
        open("Analyse")
        waitAnalysisDone()
        rule.runOnUiThread { analysisVm.setLive(false) }
        rule.runOnUiThread { gameVm.importFen("6k1/5ppp/8/8/8/8/5PPP/3R2K1 w - - 0 1") }
        rule.waitForIdle()
        rule.onNodeWithText("Analyze this position").assertExists().performClick()
        waitAnalysisDone()
        assertEquals("d1d8", analysisVm.ui.value.lines.first().pvUci.first())
    }
}
