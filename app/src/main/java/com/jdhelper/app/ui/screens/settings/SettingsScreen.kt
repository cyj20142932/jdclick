package com.jdhelper.ui.screens.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scrollState = rememberScrollState()
    val uiState by viewModel.uiState.collectAsState()

    var showServerDialog by remember { mutableStateOf(false) }
    var showDelayDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // 收集 delayMillis
    val delayMillis by viewModel.delayMillis.collectAsState()

    // 收集 millisecondDigits
    val millisecondDigits by viewModel.millisecondDigits.collectAsState()

    // 收集 recordHistory
    val recordHistory by viewModel.recordHistory.collectAsState()

    // 收集点击持续时间
    val clickDuration by viewModel.clickDuration.collectAsState()

    // 收集历史记录数量
    val historyCount by viewModel.historyCount.collectAsState()

    var showMillisecondDialog by remember { mutableStateOf(false) }
    var showClickDurationDialog by remember { mutableStateOf(false) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }

    // 页面恢复时检查权限状态
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.checkAccessibilityService()
                viewModel.checkOverlayPermission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // NTP服务器选择对话框
    if (showServerDialog) {
        AlertDialog(
            onDismissRequest = { showServerDialog = false },
            title = { Text("选择NTP服务器") },
            text = {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 300.dp)
                ) {
                    items(viewModel.getNtpServers().size) { index ->
                        val server = viewModel.getNtpServers()[index]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setNtpServer(server)
                                    showServerDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = server == uiState.ntpServer,
                                onClick = {
                                    viewModel.setNtpServer(server)
                                    showServerDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(server)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showServerDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // 延迟设置对话框
    if (showDelayDialog) {
        var delayText by remember { mutableStateOf(delayMillis.toString()) }

        AlertDialog(
            onDismissRequest = { showDelayDialog = false },
            title = { Text("设置点击延迟") },
            text = {
                Column {
                    Text("输入延迟时间（毫秒），支持负数和小数", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = delayText,
                        onValueChange = { delayText = it },
                        label = { Text("延迟 (ms)") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    delayText.toDoubleOrNull()?.let { delay ->
                        CoroutineScope(Dispatchers.Main).launch {
                            viewModel.setDelayMillis(delay)
                        }
                    }
                    showDelayDialog = false
                }) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDelayDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // 点击持续时间设置对话框
    if (showClickDurationDialog) {
        var durationText by remember { mutableStateOf(clickDuration.toString()) }

        AlertDialog(
            onDismissRequest = { showClickDurationDialog = false },
            title = { Text("点击持续时间") },
            text = {
                Column {
                    Text(
                        "设置点击按钮持续的时间（毫秒）",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "最小值为50ms，低于此值将自动调整为50ms",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = durationText,
                        onValueChange = { durationText = it },
                        label = { Text("持续时间 (ms)") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    durationText.toLongOrNull()?.let { duration ->
                        CoroutineScope(Dispatchers.Main).launch {
                            viewModel.setClickDuration(duration)
                        }
                    }
                    showClickDurationDialog = false
                }) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClickDurationDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // 毫秒格式选择对话框
    if (showMillisecondDialog) {
        val options = listOf(
            0 to "不显示毫秒",
            1 to "显示1位",
            3 to "显示3位"
        )

        AlertDialog(
            onDismissRequest = { showMillisecondDialog = false },
            title = { Text("毫秒显示格式") },
            text = {
                Column {
                    options.forEach { (digits, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    CoroutineScope(Dispatchers.Main).launch {
                                        viewModel.setMillisecondDigits(digits)
                                    }
                                    showMillisecondDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = digits == millisecondDigits,
                                onClick = {
                                    CoroutineScope(Dispatchers.Main).launch {
                                        viewModel.setMillisecondDigits(digits)
                                    }
                                    showMillisecondDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMillisecondDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // 清除历史记录确认对话框
    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { Text("确认清除") },
            text = { Text("确定要清除所有历史记录吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    CoroutineScope(Dispatchers.Main).launch {
                        viewModel.clearHistory()
                    }
                    showClearHistoryDialog = false
                }) {
                    Text("清除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
        ) {
            // 权限管理（最高频）
            SettingsSection(title = "权限管理") {
                // 权限状态卡片
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "权限状态检查",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Button(
                                onClick = {
                                    viewModel.checkAccessibilityService()
                                    viewModel.checkOverlayPermission()
                                    val a11y = if (viewModel.isAccessibilityEnabled()) "✓" else "✗"
                                    val overlay = if (viewModel.isOverlayEnabled()) "✓" else "✗"
                                    CoroutineScope(Dispatchers.Main).launch {
                                        snackbarHostState.showSnackbar("无障碍: $a11y  悬浮窗: $overlay")
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("检查")
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // 无障碍服务状态
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (uiState.isAccessibilityEnabled) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                contentDescription = null,
                                tint = if (uiState.isAccessibilityEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("无障碍服务: ")
                            Text(
                                if (uiState.isAccessibilityEnabled) "已开启" else "未开启",
                                color = if (uiState.isAccessibilityEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // 悬浮窗权限状态
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (uiState.isOverlayEnabled) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                contentDescription = null,
                                tint = if (uiState.isOverlayEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("悬浮窗权限: ")
                            Text(
                                if (uiState.isOverlayEnabled) "已开启" else "未开启",
                                color = if (uiState.isOverlayEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                SettingsItem(
                    icon = Icons.Default.Accessibility,
                    title = "无障碍服务",
                    subtitle = if (uiState.isAccessibilityEnabled) "已开启" else "未开启",
                    onClick = {
                        viewModel.checkAccessibilityService()
                        val statusText = if (viewModel.isAccessibilityEnabled()) "无障碍服务已开启" else "无障碍服务未开启"
                        CoroutineScope(Dispatchers.Main).launch {
                            snackbarHostState.showSnackbar(statusText)
                        }
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                )

                SettingsItem(
                    icon = Icons.Default.Layers,
                    title = "悬浮窗权限",
                    subtitle = if (uiState.isOverlayEnabled) "已开启" else "未开启",
                    onClick = {
                        viewModel.checkOverlayPermission()
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                        context.startActivity(intent)
                    }
                )

                SettingsItem(
                    icon = Icons.Default.Notifications,
                    title = "通知权限",
                    subtitle = "用于后台服务通知",
                    onClick = {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                            context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            })
                        }
                    }
                )
            }

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            // 点击与时间设置
            SettingsSection(title = "点击与时间设置") {
                SettingsItem(
                    icon = Icons.Default.CloudSync,
                    title = "NTP服务器",
                    subtitle = uiState.ntpServer,
                    onClick = { showServerDialog = true }
                )

                SettingsItem(
                    icon = Icons.Default.Timer,
                    title = "点击延迟",
                    subtitle = "${delayMillis} ms",
                    onClick = { showDelayDialog = true }
                )

                SettingsItem(
                    icon = Icons.Default.TouchApp,
                    title = "点击持续时间",
                    subtitle = "${clickDuration} ms (最小50ms)",
                    onClick = { showClickDurationDialog = true }
                )

                SettingsItem(
                    icon = Icons.Default.Schedule,
                    title = "毫秒格式",
                    subtitle = when (millisecondDigits) {
                        0 -> "不显示毫秒"
                        1 -> "显示1位"
                        3 -> "显示3位"
                        else -> "显示1位"
                    },
                    onClick = { showMillisecondDialog = true }
                )
            }

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            // 历史记录
            SettingsSection(title = "历史记录") {
                SettingsItem(
                    icon = Icons.Default.History,
                    title = "记录点击历史",
                    subtitle = if (recordHistory) "开启" else "关闭",
                    trailing = {
                        Switch(
                            checked = recordHistory,
                            onCheckedChange = { enabled ->
                                CoroutineScope(Dispatchers.Main).launch {
                                    viewModel.setRecordHistory(enabled)
                                }
                            }
                        )
                    }
                )

                SettingsItem(
                    icon = Icons.Default.ListAlt,
                    title = "历史记录数量",
                    subtitle = "$historyCount 条记录"
                )

                SettingsItem(
                    icon = Icons.Default.DeleteSweep,
                    title = "清除历史数据",
                    subtitle = "删除所有点击历史记录",
                    onClick = { showClearHistoryDialog = true }
                )
            }

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            // 关于
            SettingsSection(title = "关于") {
                SettingsItem(
                    icon = Icons.Default.Info,
                    title = "关于",
                    subtitle = "版本 1.0.0"
                )

                SettingsItem(
                    icon = Icons.Default.Help,
                    title = "使用帮助",
                    subtitle = "了解如何正确配置"
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 使用说明
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Lightbulb,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "使用说明",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    val instructions = listOf(
                        "1. 开启无障碍服务和悬浮窗权限",
                        "2. 创建点击任务，设置目标位置",
                        "3. 使用定位悬浮窗选择点击坐标",
                        "4. 开启任务即可自动执行"
                    )

                    instructions.forEach { instruction ->
                        Text(
                            instruction,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        content()
    }
}

@Composable
fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.clickable { onClick() }
                else Modifier
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
        trailing?.invoke() ?: run {
            if (onClick != null) {
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}