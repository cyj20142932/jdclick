package com.jdhelper.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.jdhelper.ui.screens.home.HomeScreen
import com.jdhelper.ui.screens.settings.SettingsScreen
import com.jdhelper.ui.screens.history.HistoryScreen
import com.jdhelper.ui.screens.log.LogScreen

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    data object Home : Screen("home", "首页", Icons.Default.Home)
    data object Settings : Screen("settings", "设置", Icons.Default.Settings)
    data object History : Screen("history", "历史", Icons.Default.History)
    data object Log : Screen("log", "日志", Icons.Default.Terminal)
}

val bottomNavItems = listOf(
    Screen.Home,
    Screen.Settings,
    Screen.History,
    Screen.Log
)

@Composable
fun JDHelperNavHost(
    navController: NavHostController = rememberNavController()
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // 判断当前是否是 tab 页面
    val bottomNavRoutes = bottomNavItems.map { it.route }
    val isTabScreen = currentDestination?.route in bottomNavRoutes

    Scaffold(
        bottomBar = {
            if (isTabScreen) {
                NavigationBar {
                    bottomNavItems.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = screen.title) },
                            label = { Text(screen.title) },
                            selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(navController = navController)
            }

            composable(Screen.Settings.route) {
                SettingsScreen()
            }

            composable(Screen.History.route) {
                HistoryScreen()
            }

            composable(Screen.Log.route) {
                LogScreen()
            }
        }
    }
}