package com.jdhelper.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jdhelper.app.ui.theme.JdAnimation
import com.jdhelper.app.ui.theme.JdGradients

enum class ButtonVariant { PRIMARY, SECONDARY, TEXT, GHOST }

@Composable
fun JDButton(
    text: String,
    onClick: () -> Unit,
    variant: ButtonVariant = ButtonVariant.PRIMARY,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = JdAnimation.Fast,
        label = "btn_scale"
    )

    val shape = RoundedCornerShape(12.dp)

    Box(
        modifier = modifier.scale(scale)
    ) {
        when (variant) {
            ButtonVariant.TEXT -> {
                TextButton(
                    onClick = onClick,
                    enabled = enabled,
                    shape = shape
                ) {
                    ButtonContent(text = text, icon = icon, loading = loading)
                }
            }
            ButtonVariant.GHOST -> {
                OutlinedButton(
                    onClick = onClick,
                    enabled = enabled,
                    shape = shape
                ) {
                    ButtonContent(text = text, icon = icon, loading = loading)
                }
            }
            ButtonVariant.SECONDARY -> {
                OutlinedButton(
                    onClick = onClick,
                    enabled = enabled,
                    shape = shape
                ) {
                    ButtonContent(text = text, icon = icon, loading = loading)
                }
            }
            ButtonVariant.PRIMARY -> {
                val bgBrush = if (enabled) Brush.horizontalGradient(JdGradients.Primary)
                else Brush.horizontalGradient(listOf(Color.Gray, Color.Gray))

                Button(
                    onClick = onClick,
                    enabled = enabled,
                    shape = shape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        disabledContainerColor = Color.Gray
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(brush = bgBrush, shape = shape)
                    )
                    ButtonContent(text = text, icon = icon, loading = loading)
                }
            }
        }
    }
}

@Composable
private fun ButtonContent(
    text: String,
    icon: ImageVector?,
    loading: Boolean
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary
            )
            Spacer(modifier = Modifier.width(4.dp))
        } else if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(
            text = text,
            fontWeight = FontWeight.Medium
        )
    }
}
