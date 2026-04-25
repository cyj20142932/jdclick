package com.jdhelper.app.ui.animation

import androidx.compose.animation.core.tween
import androidx.compose.animation.*
import com.jdhelper.app.ui.theme.JdAnimation

object PageTransitions {
    val fadeIn = fadeIn(animationSpec = tween(JdAnimation.DurationFast))
    val fadeOut = fadeOut(animationSpec = tween(JdAnimation.DurationFast))
    val slideInLeft = slideInHorizontally(
        initialOffsetX = { it / 4 },
        animationSpec = tween(JdAnimation.DurationNormal)
    )
    val slideOutLeft = slideOutHorizontally(
        targetOffsetX = { -it / 4 },
        animationSpec = tween(JdAnimation.DurationNormal)
    )
    val slideInRight = slideInHorizontally(
        initialOffsetX = { -it / 4 },
        animationSpec = tween(JdAnimation.DurationNormal)
    )
    val slideOutRight = slideOutHorizontally(
        targetOffsetX = { it / 4 },
        animationSpec = tween(JdAnimation.DurationNormal)
    )

    val enter = slideInLeft + fadeIn
    val exit = slideOutLeft + fadeOut
    val popEnter = slideInRight + fadeIn
    val popExit = slideOutRight + fadeOut
}
