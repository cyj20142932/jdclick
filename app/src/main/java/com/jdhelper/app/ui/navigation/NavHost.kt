package com.jdhelper.app.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
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
import com.jdhelper.app.ui.screens.home.HomeScreen
import com.jdhelper.app.ui.screens.settings.SettingsScreen
import com.jdhelper.app.ui.screens.history.HistoryScreen
import com.jdhelper.app.ui.screens.log.LogScreen

// 页面转场动画配置
private const val ANIMATION_DURATION = 300

private val fadeIn = fadeIn(animationSpec = tween(ANIMATION_DURATION))
private val fadeOut = fadeOut(animationSpec = tween(ANIMATION_DURATION))
private val slideInLeft = slideInHorizontally(
    initialOffsetX = { it },
    animationSpec = tween(ANIMATION_DURATION)
)
private val slideOutLeft = slideOutHorizontally(
    targetOffsetX = { -it },
    animationSpec = tween(ANIMATION_DURATION)
)
private val slideInRight = slideInHorizontally(
    initialOffsetX = { -it },
    animationSpec = tween(ANIMATION_DURATION)
)
private val slideOutRight = slideOutHorizontally(
    targetOffsetX = { it },
    animationSpec = tween(ANIMATION_DURATION)
)

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
            modifier = Modifier.padding(innerPadding),
            enterTransition = { slideInLeft + fadeIn },
            exitTransition = { slideOutLeft + fadeOut },
            popEnterTransition = { slideInRight + fadeIn },
            popExitTransition = { slideOutRight + fadeOut }
        ) {
            composable(
                route = Screen.Home.route,
                enterTransition = { slideInLeft + fadeIn },
                exitTransition = { slideOutLeft + fadeOut }
            ) {
                HomeScreen(navController = navController)
            }

            composable(
                route = Screen.Settings.route,
                enterTransition = { slideInLeft + fadeIn },
                exitTransition = { slideOutLeft + fadeOut }
            ) {
                SettingsScreen()
            }

            composable(
                route = Screen.History.route,
                enterTransition = { slideInLeft + fadeIn },
                exitTransition = { slideOutLeft + fadeOut }
            ) {
                HistoryScreen()
            }

            composable(
                route = Screen.Log.route,
                enterTransition = { slideInLeft + fadeIn },
                exitTransition = { slideOutLeft + fadeOut }
            ) {
                LogScreen()
            }
        }
    }
}