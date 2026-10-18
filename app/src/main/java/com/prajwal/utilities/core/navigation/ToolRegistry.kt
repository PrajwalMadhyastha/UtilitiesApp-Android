package com.prajwal.utilities.core.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Fitbit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.SportsBaseball
import androidx.compose.material.icons.filled.SportsScore
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.ui.graphics.vector.ImageVector

data class ToolDefinition(
    val id: String,
    val name: String,
    val description: String,
    val icon: ImageVector,
    val route: String
)

object ToolRegistry {
    val tools: List<ToolDefinition> = listOf(
        ToolDefinition(
            id = "wifi_usage",
            name = "WiFi Usage",
            description = "Track your monthly broadband data cap",
            icon = Icons.Default.Wifi,
            route = Screen.WifiUsageDashboard.route
        ),
        ToolDefinition(
            id = "cricket_toss",
            name = "Cricket Toss",
            description = "Fair coin flip for the toss decision",
            icon = Icons.Default.SportsScore,
            route = Screen.CricketToss.route
        ),
        ToolDefinition(
            id = "cricket_stats",
            name = "Cricket Stats",
            description = "Track your personal cricket batting & bowling stats",
            icon = Icons.Default.SportsBaseball,
            route = Screen.CricketStats.route
        ),
        ToolDefinition(
            id = "password_manager",
            name = "Passwords",
            description = "Offline personal password vault",
            icon = Icons.Default.Lock,
            route = Screen.PasswordManager.route
        ),
        ToolDefinition(
            id = "workout_tracker",
            name = "Workout Tracker",
            description = "Log your gym sessions and exercises",
            icon = Icons.Default.Fitbit,
            route = Screen.WorkoutTracker.route
        ),
        ToolDefinition(
            id = "wealth_tracker",
            name = "Wealth Tracker",
            description = "Track portfolio, diversification & milestones",
            icon = Icons.AutoMirrored.Filled.TrendingUp,
            route = Screen.WealthTracker.route
        ),
        ToolDefinition(
            id = "challan_tracker",
            name = "Traffic Challans",
            description = "Track Bangalore traffic police fines & photo evidence",
            icon = Icons.Default.DirectionsCar,
            route = Screen.ChallanTracker.route
        )
    )
}
