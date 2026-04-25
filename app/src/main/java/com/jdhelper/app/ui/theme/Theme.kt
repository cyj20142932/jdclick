package com.jdhelper.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
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

enum class ThemeMode { DARK, LIGHT, SYSTEM }

// 蓝绿渐变配色（保留对外兼容）
val BlueGreenStart = Color(0xFF00B4DB)
val BlueGreenEnd = Color(0xFF0083B0)
val DarkBackground = JdColors.BackgroundDark
val DarkSurface = JdColors.SurfaceDark
val DarkSurfaceVariant = JdColors.SurfaceVariantDark
val LightBackground = JdColors.BackgroundLight
val LightSurface = JdColors.SurfaceLight
val StatusGreen = JdColors.Success
val StatusOrange = JdColors.Warning
val StatusRed = JdColors.Error
val TextPrimary = JdColors.OnSurfaceLight
val TextSecondary = JdColors.OnSurfaceVariantLight

private val DarkColorScheme = darkColorScheme(
    primary = JdColors.Primary,
    onPrimary = JdColors.OnPrimary,
    primaryContainer = JdColors.PrimaryEnd,
    onPrimaryContainer = JdColors.OnPrimary,
    secondary = JdColors.PrimaryEnd,
    onSecondary = JdColors.OnSecondary,
    background = JdColors.BackgroundDark,
    onBackground = JdColors.OnSurfaceDark,
    surface = JdColors.SurfaceDark,
    onSurface = JdColors.OnSurfaceDark,
    surfaceVariant = JdColors.SurfaceVariantDark,
    onSurfaceVariant = JdColors.OnSurfaceVariantDark,
    outline = Color(0xFF938F99),
    error = JdColors.Error
)

private val LightColorScheme = lightColorScheme(
    primary = JdColors.PrimaryEnd,
    onPrimary = JdColors.OnPrimary,
    primaryContainer = JdColors.Primary,
    onPrimaryContainer = JdColors.OnPrimary,
    secondary = JdColors.Primary,
    onSecondary = JdColors.OnSecondary,
    background = JdColors.BackgroundLight,
    onBackground = JdColors.OnSurfaceLight,
    surface = JdColors.SurfaceLight,
    onSurface = JdColors.OnSurfaceLight,
    surfaceVariant = Color(0xFFE7E0EC),
    onSurfaceVariant = Color(0xFF49454F),
    outline = Color(0xFF79747E),
    error = JdColors.Error
)

@Composable
fun JDHelperTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> systemDark
    }

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