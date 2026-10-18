package com.prajwal.utilities.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.prajwal.utilities.home.HomeScreen
import com.prajwal.utilities.tools.crickettoss.CricketTossScreen
import com.prajwal.utilities.tools.crickettoss.CricketTossViewModel
import com.prajwal.utilities.tools.crickettoss.CricketTossViewModelFactory
import com.prajwal.utilities.tools.crickettoss.data.CricketTossRepository
import com.prajwal.utilities.tools.cricketstats.CricketStatsScreen
import com.prajwal.utilities.tools.cricketstats.data.db.MatchWithInnings
import com.prajwal.utilities.tools.passwordmanager.PasswordManagerScreen
import com.prajwal.utilities.tools.wealthtracker.WealthTrackerScreen
import com.prajwal.utilities.tools.wealthtracker.WealthTrackerViewModel
import com.prajwal.utilities.tools.wealthtracker.WealthTrackerViewModelFactory
import com.prajwal.utilities.tools.wealthtracker.data.WealthPreferences
import com.prajwal.utilities.tools.wealthtracker.data.WealthRepository
import com.prajwal.utilities.tools.wealthtracker.data.db.WealthDatabase
import com.prajwal.utilities.tools.wifiusage.OnboardingScreen
import com.prajwal.utilities.tools.wifiusage.ReportsScreen
import com.prajwal.utilities.tools.wifiusage.SettingsScreen
import com.prajwal.utilities.tools.wifiusage.UsageViewModel
import com.prajwal.utilities.tools.wifiusage.UsageViewModelFactory
import com.prajwal.utilities.tools.wifiusage.WifiUsageScreen
import com.prajwal.utilities.tools.wifiusage.data.UsageRepository
import com.prajwal.utilities.tools.workouttracker.WorkoutTrackerScreen

@Composable
fun AppNavHost(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        // ── Home Hub ──────────────────────────────────────────────────
        composable(Screen.Home.route) {
            HomeScreen(
                onToolSelected = { route -> navController.navigate(route) }
            )
        }

        // ── WiFi Usage tool ───────────────────────────────────────────
        // The ViewModel is scoped HERE, inside the WiFi composable, so it
        // is only created when the user actually navigates to the WiFi tool.
        // This prevents WiFi-specific state (onboarding, data refresh) from
        // running at app startup before the home hub is shown.
        composable(Screen.WifiUsageDashboard.route) {
            val context = LocalContext.current
            val usageRepository = remember { UsageRepository(context) }
            val wifiViewModel: UsageViewModel = viewModel(
                factory = UsageViewModelFactory(usageRepository)
            )
            val isOnboardingCompleted by wifiViewModel.isOnboardingCompleted.collectAsState()

            when (isOnboardingCompleted) {
                false -> OnboardingScreen(
                    onOnboardingComplete = { wifiViewModel.completeOnboarding() },
                    viewModel = wifiViewModel
                )
                true -> WifiUsageScreen(
                    viewModel = wifiViewModel,
                    onNavigateToSettings = { navController.navigate(Screen.WifiUsageSettings.route) },
                    onNavigateToReports = { navController.navigate(Screen.WifiUsageReports.route) },
                    onNavigateBack = { navController.popBackStack() }
                )
                // null = DataStore still loading; show nothing (brief, invisible)
                null -> {}
            }
        }

        composable(Screen.WifiUsageSettings.route) {
            val context = LocalContext.current
            val usageRepository = remember { UsageRepository(context) }
            val wifiViewModel: UsageViewModel = viewModel(
                factory = UsageViewModelFactory(usageRepository)
            )
            SettingsScreen(
                viewModel = wifiViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.WifiUsageReports.route) {
            ReportsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // ── Cricket Toss ──────────────────────────────────────────────
        composable(Screen.CricketToss.route) {
            val context = LocalContext.current
            val repository = remember {
                CricketTossRepository(
                    com.prajwal.utilities.tools.crickettoss.data.db.CricketTossDatabase.getDatabase(context).tossDao()
                )
            }
            val viewModel: CricketTossViewModel = viewModel(
                factory = CricketTossViewModelFactory(repository)
            )
            CricketTossScreen(
                onNavigateBack = { navController.popBackStack() },
                viewModel = viewModel
            )
        }

        // ── Cricket Stats ─────────────────────────────────────────────
        composable(Screen.CricketStats.route) {
            val context = LocalContext.current
            val repository = remember { 
                com.prajwal.utilities.tools.cricketstats.data.CricketStatsRepository(
                    com.prajwal.utilities.tools.cricketstats.data.db.CricketStatsDatabase.getDatabase(context).cricketStatsDao()
                ) 
            }
            val viewModel: com.prajwal.utilities.tools.cricketstats.CricketStatsViewModel = viewModel(
                factory = com.prajwal.utilities.tools.cricketstats.CricketStatsViewModelFactory(repository)
            )
            CricketStatsScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToAddMatch = { navController.navigate(Screen.CricketStatsAddMatch.route) },
                onNavigateToEditMatch = { matchId -> navController.navigate(Screen.CricketStatsEditMatch.createRoute(matchId)) }
            )
        }

        composable(Screen.CricketStatsAddMatch.route) {
            val context = LocalContext.current
            val repository = remember { 
                com.prajwal.utilities.tools.cricketstats.data.CricketStatsRepository(
                    com.prajwal.utilities.tools.cricketstats.data.db.CricketStatsDatabase.getDatabase(context).cricketStatsDao()
                ) 
            }
            val viewModel: com.prajwal.utilities.tools.cricketstats.CricketStatsViewModel = viewModel(
                factory = com.prajwal.utilities.tools.cricketstats.CricketStatsViewModelFactory(repository)
            )
            com.prajwal.utilities.tools.cricketstats.AddMatchScreen(
                onNavigateBack = { navController.popBackStack() },
                onSaveMatch = { match, batting, bowling ->
                    viewModel.saveMatch(match, batting, bowling)
                    navController.popBackStack()
                }
            )
        }

        // ── Edit existing Cricket Stats match ─────────────────────────
        composable(
            route = Screen.CricketStatsEditMatch.route,
            arguments = listOf(navArgument("matchId") { type = NavType.IntType })
        ) { backStackEntry ->
            val matchId = backStackEntry.arguments?.getInt("matchId") ?: return@composable
            val context = LocalContext.current
            val repository = remember {
                com.prajwal.utilities.tools.cricketstats.data.CricketStatsRepository(
                    com.prajwal.utilities.tools.cricketstats.data.db.CricketStatsDatabase.getDatabase(context).cricketStatsDao()
                )
            }
            val viewModel: com.prajwal.utilities.tools.cricketstats.CricketStatsViewModel = viewModel(
                factory = com.prajwal.utilities.tools.cricketstats.CricketStatsViewModelFactory(repository)
            )
            var matchData by remember { mutableStateOf<MatchWithInnings?>(null) }
            LaunchedEffect(matchId) {
                matchData = viewModel.getMatchById(matchId)
            }
            matchData?.let { data ->
                com.prajwal.utilities.tools.cricketstats.AddMatchScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onSaveMatch = { _, _, _ -> }, // unused in edit mode
                    initialData = data,
                    onUpdateMatch = { match, batting, bowling ->
                        viewModel.updateMatch(match, batting, bowling)
                        navController.popBackStack()
                    }
                )
            }
        }

        // ── Password Manager ──────────────────────────────────────────
        composable(Screen.PasswordManager.route) {
            PasswordManagerScreen(onNavigateBack = { navController.popBackStack() })
        }

        // ── Workout Tracker ───────────────────────────────────────────
        composable(Screen.WorkoutTracker.route) {
            WorkoutTrackerScreen(onNavigateBack = { navController.popBackStack() })
        }

        // ── Wealth Tracker ────────────────────────────────────────────
        composable(Screen.WealthTracker.route) {
            val context = LocalContext.current
            val db = remember { com.prajwal.utilities.tools.wealthtracker.data.db.WealthDatabase.getDatabase(context) }
            val repository = remember {
                com.prajwal.utilities.tools.wealthtracker.data.WealthRepository(
                    db.wealthDao(),
                    db.holdingsDao()
                )
            }
            val marketRepo = remember { com.prajwal.utilities.tools.wealthtracker.data.network.MarketDataRepository.getInstance() }
            val prefs = remember { com.prajwal.utilities.tools.wealthtracker.data.WealthPreferences(context) }
            val viewModel: com.prajwal.utilities.tools.wealthtracker.WealthTrackerViewModel = viewModel(
                factory = com.prajwal.utilities.tools.wealthtracker.WealthTrackerViewModelFactory(repository, marketRepo, prefs)
            )
            WealthTrackerScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // ── Traffic Challans ──────────────────────────────────────────
        composable(Screen.ChallanTracker.route) {
            val context = LocalContext.current
            val db = remember { com.prajwal.utilities.tools.challantracker.data.db.ChallanDatabase.getDatabase(context) }
            val prefs = remember { com.prajwal.utilities.tools.challantracker.data.ChallanPreferences(context) }
            val repository = remember {
                com.prajwal.utilities.tools.challantracker.data.ChallanRepository(
                    db.challanDao(),
                    prefs
                )
            }
            val viewModel: com.prajwal.utilities.tools.challantracker.ChallanTrackerViewModel = viewModel(
                factory = com.prajwal.utilities.tools.challantracker.ChallanTrackerViewModelFactory(repository, prefs)
            )
            com.prajwal.utilities.tools.challantracker.ChallanTrackerScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}

