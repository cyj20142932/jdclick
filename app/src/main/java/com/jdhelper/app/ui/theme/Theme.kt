package com.jdhelper.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// 蓝绿渐变配色
val BlueGreenStart = Color(0xFF00B4DB)
val BlueGreenEnd = Color(0xFF0083B0)

// 深色主题背景
val DarkBackground = Color(0xFF1A1A2E)
val DarkSurface = Color(0xFF252540)
val DarkSurfaceVariant = Color(0xFF2D2D4A)

// 浅色主题背景
val LightBackground = Color(0xFFF5F5F7)
val LightSurface = Color(0xFFFFFFFF)

// 状态色
val StatusGreen = Color(0xFF4CAF50)
val StatusOrange = Color(0xFFFF9800)
val StatusRed = Color(0xFFF44336)

// 文字颜色
val TextPrimary = Color(0xFF212121)
val TextSecondary = Color(0xFF757575)

private val DarkColorScheme = darkColorScheme(
    primary = BlueGreenStart,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = BlueGreenEnd,
    onPrimaryContainer = Color(0xFFFFFFFF),
    secondary = BlueGreenEnd,
    onSecondary = Color(0xFFFFFFFF),
    background = DarkBackground,
    onBackground = Color(0xFFFFFFFF),
    surface = DarkSurface,
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFCAC4D0),
    outline = Color(0xFF938F99)
)

private val LightColorScheme = lightColorScheme(
    primary = BlueGreenEnd,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = BlueGreenStart,
    onPrimaryContainer = Color(0xFFFFFFFF),
    secondary = BlueGreenStart,
    onSecondary = Color(0xFFFFFFFF),
    background = LightBackground,
    onBackground = Color(0xFF212121),
    surface = LightSurface,
    onSurface = Color(0xFF212121),
    surfaceVariant = Color(0xFFE7E0EC),
    onSurfaceVariant = Color(0xFF49454F),
    outline = Color(0xFF79747E)
)

@Composable
fun JDHelperTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // 设置状态栏透明
            window.statusBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}