package com.jdhelper.ui.screens.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jdhelper.data.local.GiftClickHistory
import com.jdhelper.service.ToastUtils
import com.jdhelper.ui.theme.StatusGreen
import com.jdhelper.ui.theme.StatusRed
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val historyList by viewModel.historyList.collectAsState()

    val timeFormat = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }

    Scaffold(
    ) { paddingValues ->
        if (historyList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.History,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "暂无历史记录",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "点击任务执行后将显示在此处",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(historyList, key = { it.id }) { item ->
                    HistoryItemCard(
                        item = item,
                        timeFormat = timeFormat,
                        onSuccessChanged = { checked ->
                            viewModel.updateHistorySuccess(item.id, checked)
                        },
                        onDelete = {
                            viewModel.deleteHistoryItem(item.id)
                        },
                        onRestoreDelay = {
                            viewModel.restoreDelayFromHistory(item.delayMillis)
                            ToastUtils.show(context, "已恢复延迟设置: ${item.delayMillis.toLong()}ms")
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun HistoryItemCard(
    item: GiftClickHistory,
    timeFormat: SimpleDateFormat,
    onSuccessChanged: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onRestoreDelay: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    val ntpTimeStr = remember(item.ntpClickTime) { timeFormat.format(Date(item.ntpClickTime)) }
    val localTimeStr = remember(item.localClickTime) { timeFormat.format(Date(item.localClickTime)) }

    val stageText = when (item.stage) {
        0 -> "启动按钮:定时点击"
        1 -> "第一阶段:一键送礼"
        2 -> "第二阶段:付款并赠送"
        else -> "未知"
    }

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // 阶段标题 + 状态标记（紧凑排列）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stageText,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                // 状态标记：成功=绿色对勾，失败=红色叉
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { onSuccessChanged(true) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            if (item.success == true) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                            contentDescription = "标记成功",
                            tint = if (item.success == true) StatusGreen else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = { onSuccessChanged(false) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            if (item.success == false) Icons.Default.Cancel else Icons.Default.RadioButtonUnchecked,
                            contentDescription = "标记失败",
                            tint = if (item.success == false) StatusRed else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 时间、延迟、偏差一行显示（高信息密度）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 时间信息
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "NTP: $ntpTimeStr",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "本机: $localTimeStr",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                // 延迟和时间源信息
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    // 时间源显示
                    val timeSourceColor = when (item.timeSource) {
                        "JD" -> Color(0xFF67C23A)  // 绿色
                        else -> Color(0xFF409EFF)   // 蓝色
                    }
                    Text(
                        "时间源: ${item.timeSource}",
                        style = MaterialTheme.typography.bodySmall,
                        color = timeSourceColor
                    )
                    Text(
                        "延迟: ${item.delayMillis.toLong()}ms",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "偏差: ${item.actualDiff}ms",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (kotlin.math.abs(item.actualDiff) <= 50)
                            StatusGreen
                        else
                            StatusRed
                    )
                }

                // 操作按钮
                Row {
                    IconButton(
                        onClick = onRestoreDelay,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Restore,
                            contentDescription = "恢复延迟",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = { showDeleteDialog = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "删除",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("确认删除") },
            text = { Text("确定要删除这条历史记录吗？") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete()
                    showDeleteDialog = false
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}