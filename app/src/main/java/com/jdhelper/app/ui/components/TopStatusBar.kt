package com.jdhelper.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jdhelper.app.data.local.TimeSource
import com.jdhelper.app.ui.theme.BlueGreenEnd
import com.jdhelper.app.ui.theme.BlueGreenStart

@Composable
fun TopStatusBar(
    ntpTime: String,
    millis: String,
    ntpOffset: String = "",
    nextClickCountdown: String = "",
    millisecondDigits: Int = 1,
    timeSource: TimeSource = TimeSource.JD,
    jdOffset: String = "",
    modifier: Modifier = Modifier
) {
    // 根据设置格式化毫秒显示
    val formattedMillis = when (millisecondDigits) {
        0 -> ""  // 不显示毫秒
        1 -> ".${millis.take(1)}"
        2 -> ".${millis.take(2)}"
        3 -> ".${millis.padStart(3, '0')}"
        else -> ".${millis.take(1)}"
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(BlueGreenStart, BlueGreenEnd)
                )
            )
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 时间显示
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = ntpTime,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = Color.White
                )
                Spacer(modifier = Modifier.width(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 根据时间源显示对应偏移
                    val offsetDisplay = when (timeSource) {
                        TimeSource.JD -> jdOffset
                        else -> jdOffset
                    }
                    val sourceLabel = when (timeSource) {
                        TimeSource.JD -> "JD"
                        else -> "JD"
                    }

                    if (millisecondDigits > 0) {
                        Text(
                            text = formattedMillis,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }

                    if (offsetDisplay.isNotEmpty() && offsetDisplay != "--ms") {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "$sourceLabel $offsetDisplay",
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            // 下次点击倒计时
            if (nextClickCountdown.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.2f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "⏱",
                        color = Color.White,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = nextClickCountdown,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.Monospace,
                        color = Color.White
                    )
                }
            }
        }
    }
}