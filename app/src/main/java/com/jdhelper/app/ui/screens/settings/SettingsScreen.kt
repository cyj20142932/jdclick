package com.jdhelper.app.ui.screens.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
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

    val clockFontFamily by viewModel.clockFontFamily.collectAsState()
    val clockFontSize by viewModel.clockFontSize.collectAsState()
    val clockFontColor by viewModel.clockFontColor.collectAsState()
    val clockBgColor by viewModel.clockBgColor.collectAsState()
    val clockAlpha by viewModel.clockAlpha.collectAsState()
    val clockPadding by viewModel.clockPadding.collectAsState()
    val clockLetterSpacing by viewModel.clockLetterSpacing.collectAsState()

    var showMillisecondDialog by remember { mutableStateOf(false) }
    var showClickDurationDialog by remember { mutableStateOf(false) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var showFontFamilyDialog by remember { mutableStateOf(false) }
    var showFontSizeDialog by remember { mutableStateOf(false) }
    var showFontColorDialog by remember { mutableStateOf(false) }
    var showBgColorDialog by remember { mutableStateOf(false) }
    var showAlphaDialog by remember { mutableStateOf(false) }
    var showPaddingDialog by remember { mutableStateOf(false) }
    var showLetterSpacingDialog by remember { mutableStateOf(false) }

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

    // 字体选择对话框
    if (showFontFamilyDialog) {
        val fontOptions = listOf(
            "monospace" to "等宽 (monospace)",
            "sans-serif" to "无衬线 (sans-serif)",
            "sans-serif-light" to "无衬线细体",
            "sans-serif-medium" to "无衬线中粗",
            "serif" to "衬线 (serif)"
        )
        AlertDialog(
            onDismissRequest = { showFontFamilyDialog = false },
            title = { Text("时钟字体") },
            text = {
                Column {
                    fontOptions.forEach { (value, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setClockFontFamily(value)
                                    showFontFamilyDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = value == clockFontFamily,
                                onClick = {
                                    viewModel.setClockFontFamily(value)
                                    showFontFamilyDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showFontFamilyDialog = false }) { Text("取消") }
            }
        )
    }

    // 字体大小对话框
    if (showFontSizeDialog) {
        var sliderValue by remember { mutableFloatStateOf(clockFontSize.toFloat()) }
        AlertDialog(
            onDismissRequest = { showFontSizeDialog = false },
            title = { Text("字体大小") },
            text = {
                Column {
                    Text("${sliderValue.toInt()} sp", style = MaterialTheme.typography.headlineMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Slider(
                        value = sliderValue,
                        onValueChange = { sliderValue = it },
                        valueRange = 12f..48f,
                        steps = 35,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setClockFontSize(sliderValue.toInt())
                    showFontSizeDialog = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showFontSizeDialog = false }) { Text("取消") }
            }
        )
    }

    // 字体颜色对话框
    if (showFontColorDialog) {
        val colorOptions = listOf(
            -1 to "白",
            0xFFF44336.toInt() to "红",
            0xFFE91E63.toInt() to "粉",
            0xFFFF9800.toInt() to "橙",
            0xFFFFEB3B.toInt() to "黄",
            0xFF4CAF50.toInt() to "绿",
            0xFF009688.toInt() to "青",
            0xFF2196F3.toInt() to "蓝",
            0xFF3F51B5.toInt() to "靛",
            0xFF9C27B0.toInt() to "紫",
            0xFF00BCD4.toInt() to "青蓝",
            0xFF9E9E9E.toInt() to "灰",
        )
        AlertDialog(
            onDismissRequest = { showFontColorDialog = false },
            title = { Text("字体颜色") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    colorOptions.chunked(4).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            row.forEach { (colorInt, _) ->
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(androidx.compose.foundation.shape.CircleShape)
                                        .background(androidx.compose.ui.graphics.Color(colorInt))
                                        .border(
                                            width = if (colorInt == clockFontColor) 3.dp else 1.dp,
                                            color = if (colorInt == clockFontColor)
                                                MaterialTheme.colorScheme.primary
                                            else
                                                MaterialTheme.colorScheme.outline,
                                            shape = androidx.compose.foundation.shape.CircleShape
                                        )
                                        .clickable {
                                            viewModel.setClockFontColor(colorInt)
                                            showFontColorDialog = false
                                        }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showFontColorDialog = false }) { Text("取消") }
            }
        )
    }

    // 背景颜色对话框
    if (showBgColorDialog) {
        val bgColorOptions = listOf(
            0xCC333333.toInt() to "深灰",
            0x00000000.toInt() to "透明",
            -0x1 to "白",
            0xCC000000.toInt() to "黑",
            0xCCF44336.toInt() to "红",
            0xCC4CAF50.toInt() to "绿",
            0xCC2196F3.toInt() to "蓝",
            0xCCFF9800.toInt() to "橙",
            0xCC9C27B0.toInt() to "紫",
            0xCC009688.toInt() to "青",
            0xCC607D8B.toInt() to "灰蓝",
            0xCC795548.toInt() to "棕",
        )
        AlertDialog(
            onDismissRequest = { showBgColorDialog = false },
            title = { Text("背景颜色") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    bgColorOptions.chunked(4).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            row.forEach { (colorInt, _) ->
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(androidx.compose.foundation.shape.CircleShape)
                                        .background(androidx.compose.ui.graphics.Color(colorInt))
                                        .border(
                                            width = if (colorInt == clockBgColor) 3.dp else 1.dp,
                                            color = if (colorInt == clockBgColor)
                                                MaterialTheme.colorScheme.primary
                                            else
                                                MaterialTheme.colorScheme.outline,
                                            shape = androidx.compose.foundation.shape.CircleShape
                                        )
                                        .clickable {
                                            viewModel.setClockBgColor(colorInt)
                                            showBgColorDialog = false
                                        }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBgColorDialog = false }) { Text("取消") }
            }
        )
    }

    // 透明度对话框
    if (showAlphaDialog) {
        var sliderValue by remember { mutableFloatStateOf(clockAlpha.toFloat()) }
        AlertDialog(
            onDismissRequest = { showAlphaDialog = false },
            title = { Text("透明度") },
            text = {
                Column {
                    Text("${(sliderValue / 255f * 100).toInt()}%", style = MaterialTheme.typography.headlineMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Slider(
                        value = sliderValue,
                        onValueChange = { sliderValue = it },
                        valueRange = 0f..255f,
                        steps = 50,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setClockAlpha(sliderValue.toInt())
                    showAlphaDialog = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showAlphaDialog = false }) { Text("取消") }
            }
        )
    }

    // 间距对话框
    if (showPaddingDialog) {
        var sliderValue by remember { mutableFloatStateOf(clockPadding.toFloat()) }
        AlertDialog(
            onDismissRequest = { showPaddingDialog = false },
            title = { Text("间距 (Padding)") },
            text = {
                Column {
                    Text("${sliderValue.toInt()} dp", style = MaterialTheme.typography.headlineMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Slider(
                        value = sliderValue,
                        onValueChange = { sliderValue = it },
                        valueRange = 4f..32f,
                        steps = 27,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setClockPadding(sliderValue.toInt())
                    showPaddingDialog = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showPaddingDialog = false }) { Text("取消") }
            }
        )
    }

    // 字间距对话框
    if (showLetterSpacingDialog) {
        var sliderValue by remember { mutableFloatStateOf(clockLetterSpacing) }
        AlertDialog(
            onDismissRequest = { showLetterSpacingDialog = false },
            title = { Text("字间距") },
            text = {
                Column {
                    Text(String.format("%.2f", sliderValue), style = MaterialTheme.typography.headlineMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Slider(
                        value = sliderValue,
                        onValueChange = { sliderValue = it },
                        valueRange = -0.05f..0.20f,
                        steps = 24,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setClockLetterSpacing(sliderValue)
                    showLetterSpacingDialog = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showLetterSpacingDialog = false }) { Text("取消") }
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

            // 外观设置
            SettingsSection(title = "外观设置") {
                Divider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))

                Text(
                    "悬浮时钟外观",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                SettingsItem(
                    icon = Icons.Default.TextFields,
                    title = "字体",
                    subtitle = when (clockFontFamily) {
                        "monospace" -> "等宽 (monospace)"
                        "sans-serif" -> "无衬线 (sans-serif)"
                        "sans-serif-light" -> "无衬线细体"
                        "sans-serif-medium" -> "无衬线中粗"
                        "serif" -> "衬线 (serif)"
                        else -> clockFontFamily
                    },
                    onClick = { showFontFamilyDialog = true }
                )

                SettingsItem(
                    icon = Icons.Default.FormatSize,
                    title = "字体大小",
                    subtitle = "${clockFontSize} sp",
                    onClick = { showFontSizeDialog = true }
                )

                SettingsItem(
                    icon = Icons.Default.Palette,
                    title = "字体颜色",
                    subtitle = "点击选择",
                    onClick = { showFontColorDialog = true }
                )

                SettingsItem(
                    icon = Icons.Default.FormatColorFill,
                    title = "背景颜色",
                    subtitle = "点击选择",
                    onClick = { showBgColorDialog = true }
                )

                SettingsItem(
                    icon = Icons.Default.Opacity,
                    title = "透明度",
                    subtitle = "${(clockAlpha / 255f * 100).toInt()}%",
                    onClick = { showAlphaDialog = true }
                )

                SettingsItem(
                    icon = Icons.Default.SpaceBar,
                    title = "间距 (Padding)",
                    subtitle = "${clockPadding} dp",
                    onClick = { showPaddingDialog = true }
                )

                SettingsItem(
                    icon = Icons.Default.TextFields,
                    title = "字间距",
                    subtitle = String.format("%.2f", clockLetterSpacing),
                    onClick = { showLetterSpacingDialog = true }
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