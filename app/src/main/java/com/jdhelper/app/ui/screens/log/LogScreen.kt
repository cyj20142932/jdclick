package com.jdhelper.ui.screens.log

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jdhelper.data.local.LogEntry
import com.jdhelper.app.service.LogConsole
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(
    viewModel: LogViewModel = hiltViewModel()
) {
    val logs by viewModel.logs.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("日志") },
                actions = {
                    IconButton(onClick = { viewModel.showClearDialog() }) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "清空")
                    }
                }
            )
        }
    ) { padding ->
        if (logs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("暂无日志", color = MaterialTheme.colorScheme.outline)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(logs, key = { it.id }) { log ->
                    LogItemCard(
                        log = log,
                        timeFormat = timeFormat,
                        onLongPress = { viewModel.showDeleteDialog(log.id) }
                    )
                }
            }
        }

        // 删除确认对话框
        if (uiState.showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.hideDeleteDialog() },
                title = { Text("确认删除") },
                text = { Text("确定要删除这条日志吗？") },
                confirmButton = {
                    TextButton(onClick = { viewModel.deleteLog() }) {
                        Text("删除", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.hideDeleteDialog() }) {
                        Text("取消")
                    }
                }
            )
        }

        // 清空确认对话框
        if (uiState.showClearDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.hideClearDialog() },
                title = { Text("确认清空") },
                text = { Text("确定要清空所有日志吗？") },
                confirmButton = {
                    TextButton(onClick = { viewModel.clearAllLogs() }) {
                        Text("清空", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.hideClearDialog() }) {
                        Text("取消")
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LogItemCard(
    log: LogEntry,
    timeFormat: SimpleDateFormat,
    onLongPress: () -> Unit
) {
    val levelColor = when(log.level) {
        LogConsole.VERBOSE -> Color.Gray
        LogConsole.DEBUG -> Color(0xFF2196F3)  // 蓝色
        LogConsole.INFO -> Color(0xFF4CAF50)   // 绿色
        LogConsole.WARN -> Color(0xFFFF9800)   // 橙色
        LogConsole.ERROR -> Color(0xFFF44336) // 红色
        else -> Color.Gray
    }

    val levelText = when(log.level) {
        LogConsole.VERBOSE -> "V"
        LogConsole.DEBUG -> "D"
        LogConsole.INFO -> "I"
        LogConsole.WARN -> "W"
        LogConsole.ERROR -> "E"
        else -> "?"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {},
                onLongClick = onLongPress
            )
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.Top
        ) {
            // 级别标签
            Surface(
                color = levelColor,
                shape = MaterialTheme.shapes.extraSmall,
                modifier = Modifier.width(20.dp)
            ) {
                Text(
                    text = levelText,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                // 时间戳 + 标签
                Text(
                    text = "${timeFormat.format(Date(log.timestamp))} ${log.tag}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
                // 日志内容
                Text(
                    text = log.message,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}