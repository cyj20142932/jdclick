package com.jdhelper.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jdhelper.app.ui.theme.JdAnimation
import com.jdhelper.app.ui.theme.JdRadius
import com.jdhelper.app.ui.theme.JdSpacing

enum class LoadingVariant { CIRCULAR, DOTS }

@Composable
fun JDLoadingIndicator(
    variant: LoadingVariant = LoadingVariant.CIRCULAR,
    modifier: Modifier = Modifier
) {
    when (variant) {
        LoadingVariant.CIRCULAR -> {
            Box(
                modifier = modifier.fillMaxWidth().padding(JdSpacing.xxl),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        LoadingVariant.DOTS -> {
            Box(
                modifier = modifier.fillMaxWidth().padding(JdSpacing.xxl),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(JdSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(3) { index ->
                        Dot(
                            delay = index * 150,
                            modifier = Modifier.size(10.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Dot(delay: Int, modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "dot_transition")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = delay, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot_alpha"
    )

    Box(
        modifier = modifier
            .alpha(alpha)
            .clip(RoundedCornerShape(9999.dp))
            .background(MaterialTheme.colorScheme.primary)
    )
}

@Composable
fun JDShimmerPlaceholder(
    width: Dp = 200.dp,
    height: Dp = 16.dp,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val translation by infiniteTransition.animateFloat(
        initialValue = -300f,
        targetValue = 300f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_translate"
    )

    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(4.dp))
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.LightGray.copy(alpha = 0.3f),
                        Color.LightGray.copy(alpha = 0.6f),
                        Color.LightGray.copy(alpha = 0.3f)
                    ),
                    start = Offset(translation, 0f),
                    end = Offset(translation + 300f, 0f)
                )
            )
    )
}
