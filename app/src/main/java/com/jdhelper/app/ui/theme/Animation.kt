package com.jdhelper.app.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing

object JdAnimation {
    const val DurationFast = 150
    const val DurationNormal = 300
    const val DurationSlow = 500

    val Fast = tween<Float>(durationMillis = DurationFast, easing = FastOutSlowInEasing)
    val Normal = tween<Float>(durationMillis = DurationNormal, easing = FastOutSlowInEasing)
    val Slow = tween<Float>(durationMillis = DurationSlow, easing = FastOutSlowInEasing)

    object Easing {
        val fastOutSlowIn = FastOutSlowInEasing
        val fastOutLinearIn = FastOutLinearInEasing
        val linearOutSlowIn = LinearOutSlowInEasing
    }
}
