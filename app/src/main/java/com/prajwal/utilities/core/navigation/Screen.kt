package com.prajwal.utilities.core.navigation

sealed class Screen(val route: String) {
    // Root
    object Home : Screen("home")

    // WiFi Usage tool (has sub-screens)
    object WifiUsageOnboarding : Screen("wifi_usage/onboarding")
    object WifiUsageDashboard : Screen("wifi_usage/dashboard")
    object WifiUsageSettings : Screen("wifi_usage/settings")
    object WifiUsageReports : Screen("wifi_usage/reports")

    // Other tools (single screen each for now)
    object CricketToss : Screen("cricket_toss")
    object CricketStats : Screen("cricket_stats")
    object CricketStatsAddMatch : Screen("cricket_stats/add")
    object CricketStatsEditMatch : Screen("cricket_stats/edit/{matchId}") {
        fun createRoute(matchId: Int) = "cricket_stats/edit/$matchId"
    }
    object PasswordManager : Screen("password_manager")
    object WorkoutTracker : Screen("workout_tracker")
    object WealthTracker : Screen("wealth_tracker")
    object ChallanTracker : Screen("challan_tracker")
}
