package com.mati.tradenotify.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mati.tradenotify.ui.screens.ChannelPickerScreen
import com.mati.tradenotify.ui.screens.HistoryScreen
import com.mati.tradenotify.ui.screens.HomeScreen
import com.mati.tradenotify.ui.screens.RuleEditScreen
import com.mati.tradenotify.ui.screens.RulesScreen
import com.mati.tradenotify.ui.screens.SetupWizardScreen
import com.mati.tradenotify.ui.screens.SettingsScreen
import com.mati.tradenotify.ui.theme.TradeNotifyTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TradeNotifyTheme {
                AppRoot()
            }
        }
    }
}

private data class Tab(val route: String, val label: String, val glyph: String)

private val TABS = listOf(
    Tab("home", "Status", "◉"),
    Tab("rules", "Rules", "☰"),
    Tab("history", "History", "◔"),
    Tab("settings", "Settings", "⚙"),
)

@Composable
private fun AppRoot(viewModel: MainViewModel = viewModel()) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val setupComplete by viewModel.setupComplete.collectAsStateWithLifecycle()

    RequestNotificationPermission()

    LaunchedEffect(Unit) { viewModel.checkForUpdate() }

    when (setupComplete) {
        // Still reading DataStore — show nothing rather than flashing the wizard.
        null -> return
        false -> {
            SetupWizardScreen(viewModel, onFinish = {})
            return
        }

        else -> Unit
    }

    Scaffold(
        bottomBar = {
            if (currentRoute in TABS.map { it.route }) {
                NavigationBar {
                    TABS.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Text(tab.glyph) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(padding),
        ) {
            composable("home") {
                HomeScreen(
                    viewModel = viewModel,
                    onOpenRules = { navController.navigate("rules") },
                    onOpenChannels = { navController.navigate("channels") },
                )
            }
            composable("rules") {
                RulesScreen(
                    viewModel = viewModel,
                    onAddRule = { navController.navigate("rule/0") },
                    onEditRule = { id -> navController.navigate("rule/$id") },
                    onPickChannel = { navController.navigate("channels") },
                )
            }
            composable(
                route = "rule/{ruleId}?channel={channel}",
                arguments = listOf(
                    navArgument("ruleId") { type = NavType.LongType },
                    navArgument("channel") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                ),
            ) { entry ->
                RuleEditScreen(
                    viewModel = viewModel,
                    ruleId = entry.arguments?.getLong("ruleId") ?: 0L,
                    prefillChannel = entry.arguments?.getString("channel").orEmpty(),
                    onDone = { navController.popBackStack() },
                )
            }
            composable("channels") {
                ChannelPickerScreen(
                    viewModel = viewModel,
                    onPick = { channel ->
                        val encoded = android.net.Uri.encode(channel)
                        navController.navigate("rule/0?channel=$encoded")
                    },
                    onBack = { navController.popBackStack() },
                )
            }
            composable("history") { HistoryScreen(viewModel) }
            composable("settings") { SettingsScreen(viewModel) }
        }
    }
}

/** Asked once on first launch; the checklist on Home covers it if the user declines. */
@Composable
private fun RequestNotificationPermission() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    LaunchedEffect(Unit) {
        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
