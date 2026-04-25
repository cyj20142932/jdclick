package com.jdhelper.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// ==================== 颜色系统 ====================

object JdColors {
    val Primary = Color(0xFF00B4DB)
    val PrimaryEnd = Color(0xFF0083B0)
    val OnPrimary = Color(0xFFFFFFFF)

    val Secondary = Color(0xFF0083B0)
    val OnSecondary = Color(0xFFFFFFFF)

    val Success = Color(0xFF4CAF50)
    val Warning = Color(0xFFFF9800)
    val Error = Color(0xFFF44336)
    val Info = Color(0xFF2196F3)

    val BackgroundLight = Color(0xFFF5F5F7)
    val SurfaceLight = Color(0xFFFFFFFF)
    val OnSurfaceLight = Color(0xFF212121)
    val OnSurfaceVariantLight = Color(0xFF757575)

    val BackgroundDark = Color(0xFF1A1A2E)
    val SurfaceDark = Color(0xFF252540)
    val SurfaceVariantDark = Color(0xFF2D2D4A)
    val OnSurfaceDark = Color(0xFFFFFFFF)
    val OnSurfaceVariantDark = Color(0xFFCAC4D0)
}

object JdGradients {
    val Primary = listOf(Color(0xFF00B4DB), Color(0xFF0083B0))
    val Surface = listOf(Color(0xFF252540), Color(0xFF1A1A2E))
}

// ==================== 尺寸系统 ====================

object JdSpacing {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 48.dp
}

object JdRadius {
    val small = RoundedCornerShape(8.dp)
    val medium = RoundedCornerShape(12.dp)
    val large = RoundedCornerShape(16.dp)
    val extraLarge = RoundedCornerShape(24.dp)
    val full = RoundedCornerShape(9999.dp)

    object Size {
        val small = 8.dp
        val medium = 12.dp
        val large = 16.dp
        val extraLarge = 24.dp
    }
}

object JdElevation {
    val level0 = 0.dp
    val level1 = 2.dp
    val level2 = 4.dp
    val level3 = 8.dp
    val level4 = 16.dp
}
