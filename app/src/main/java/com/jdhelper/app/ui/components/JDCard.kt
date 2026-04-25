package com.jdhelper.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import com.jdhelper.app.ui.theme.JdAnimation

enum class CardVariant { ELEVATED, OUTLINED, FILLED }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JDCard(
    modifier: Modifier = Modifier,
    variant: CardVariant = CardVariant.ELEVATED,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && onClick != null) 0.98f else 1f,
        animationSpec = JdAnimation.Fast,
        label = "card_scale"
    )

    Card(
        modifier = modifier.scale(scale),
        shape = RoundedCornerShape(16.dp),
        onClick = onClick ?: {},
        interactionSource = interactionSource,
        enabled = onClick != null,
        colors = when (variant) {
            CardVariant.FILLED -> CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
            else -> CardDefaults.cardColors()
        },
        border = when (variant) {
            CardVariant.OUTLINED -> CardDefaults.outlinedCardBorder()
            else -> null
        },
        elevation = when (variant) {
            CardVariant.ELEVATED -> CardDefaults.cardElevation(defaultElevation = 4.dp)
            else -> CardDefaults.cardElevation(defaultElevation = 0.dp)
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            content = content
        )
    }
}
