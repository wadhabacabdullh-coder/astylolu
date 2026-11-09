package com.grandmaster.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.grandmaster.ai.ui.AnalysisViewModel
import com.grandmaster.ai.ui.ExplorerViewModel
import com.grandmaster.ai.ui.GameViewModel
import com.grandmaster.ai.ui.ReviewViewModel
import com.grandmaster.ai.ui.components.toast
import com.grandmaster.ai.ui.screens.AboutScreen
import com.grandmaster.ai.ui.screens.AnalysisScreen
import com.grandmaster.ai.ui.screens.Destination
import com.grandmaster.ai.ui.screens.ExplorerScreen
import com.grandmaster.ai.ui.screens.HikaruScreen
import com.grandmaster.ai.ui.screens.HomeScreen
import com.grandmaster.ai.ui.screens.ReviewScreen
import com.grandmaster.ai.ui.screens.SettingsScreen
import com.grandmaster.ai.ui.theme.GmColors
import com.grandmaster.ai.ui.theme.GrandmasterTheme

class MainActivity : ComponentActivity() {
    private val gameVm: GameViewModel by viewModels()
    private val analysisVm: AnalysisViewModel by viewModels()
    private val reviewVm: ReviewViewModel by viewModels()
    private val explorerVm: ExplorerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as GrandmasterApp
        setContent {
            GrandmasterTheme {
                val nav = rememberNavController()
                val context = LocalContext.current
                LaunchedEffect(Unit) { gameVm.messages.collect { toast(context, it) } }
                Box(Modifier.fillMaxSize().background(GmColors.Background).navigationBarsPadding()) {
                    NavHost(
                        navController = nav,
                        startDestination = "home",
                        enterTransition = { slideInHorizontally { it / 6 } + fadeIn() },
                        exitTransition = { fadeOut() },
                        popEnterTransition = { fadeIn() },
                        popExitTransition = { slideOutHorizontally { it / 6 } + fadeOut() },
                    ) {
                        val back: () -> Unit = { nav.popBackStack() }
                        composable("home") {
                            HomeScreen(
                                gameVm = gameVm,
                                engineStatus = app.engineRepository.status,
                                onNavigate = { d -> nav.navigate(d.route) { launchSingleTop = true } },
                                onStartPlay = { color ->
                                    analysisVm.setLive(false)
                                    gameVm.startPlay(color)
                                    nav.navigate(Destination.ANALYSIS.route) { launchSingleTop = true }
                                },
                            )
                        }
                        composable(Destination.ANALYSIS.route) { AnalysisScreen(gameVm, analysisVm, back) }
                        composable(Destination.HIKARU.route) {
                            LaunchedEffect(Unit) { if (gameVm.playMode.value != null) gameVm.stopPlay() }
                            HikaruScreen(gameVm, analysisVm, back)
                        }
                        composable(Destination.REVIEW.route) {
                            LaunchedEffect(Unit) { if (gameVm.playMode.value != null) gameVm.stopPlay() }
                            ReviewScreen(gameVm, reviewVm, back)
                        }
                        composable(Destination.EXPLORER.route) {
                            LaunchedEffect(Unit) { if (gameVm.playMode.value != null) gameVm.stopPlay() }
                            ExplorerScreen(gameVm, explorerVm, back)
                        }
                        composable(Destination.SETTINGS.route) { SettingsScreen(app, back) }
                        composable(Destination.ABOUT.route) { AboutScreen(back) }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) (application as GrandmasterApp).engineRepository.shutdown()
    }
}
