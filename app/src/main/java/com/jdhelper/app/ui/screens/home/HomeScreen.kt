package com.jdhelper.app.ui.screens.home

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.jdhelper.app.ui.components.JDCard
import com.jdhelper.app.ui.components.CardVariant
import com.jdhelper.app.ui.components.StatusCard
import com.jdhelper.app.ui.components.TopStatusBar
import com.jdhelper.app.ui.navigation.Screen
import com.jdhelper.app.ui.theme.DarkBackground
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController? = null,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsState()
    val delayMillis by viewModel.delayMillis.collectAsState()
    val ntpTime by viewModel.ntpTime.collectAsState()
    val millis by viewModel.millis.collectAsState()
    val nextClickCountdown by viewModel.nextClickCountdown.collectAsState()
    val ntpOffset by viewModel.ntpOffset.collectAsState()
    val millisecondDigits by viewModel.millisecondDigits.collectAsState()
    val jdOffset by viewModel.jdOffset.collectAsState()
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    var showDelayDialog by remember { mutableStateOf(false) }

    // 页面恢复时检查状态
    DisposableEffect(
        lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.checkAllPermissions()
                viewModel.checkServiceStatus(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.startTimeUpdates()
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
                        scope.launch {
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

    Scaffold(
        topBar = {
            TopStatusBar(
                ntpTime = ntpTime,
                millis = millis,
                ntpOffset = ntpOffset,
                nextClickCountdown = nextClickCountdown,
                millisecondDigits = millisecondDigits,
                jdOffset = jdOffset
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = DarkBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
        ) {
            // 状态卡片网格 - 2x2 布局
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 第一行：无障碍 + 悬浮窗
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatusCard(
                        title = "无障碍",
                        subtitle = if (uiState.isAccessibilityEnabled) "已开启" else "未开启",
                        icon = Icons.Default.Accessibility,
                        isEnabled = uiState.isAccessibilityEnabled,
                        onClick = {
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        },
                        modifier = Modifier.weight(1f)
                    )

                    StatusCard(
                        title = "悬浮窗",
                        subtitle = if (uiState.isOverlayEnabled) "已开启" else "未开启",
                        icon = Icons.Default.Layers,
                        isEnabled = uiState.isOverlayEnabled,
                        onClick = {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(intent)
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                // 第二行：悬浮时钟 + 悬浮菜单
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatusCard(
                        title = "悬浮时钟",
                        subtitle = if (uiState.isFloatingEnabled) "已显示" else "已隐藏",
                        icon = Icons.Default.Schedule,
                        isEnabled = uiState.isFloatingEnabled,
                        onToggle = { viewModel.toggleFloatingService(context) },
                        modifier = Modifier.weight(1f)
                    )

                    StatusCard(
                        title = "悬浮菜单",
                        subtitle = if (uiState.isFloatingMenuEnabled) "已显示" else "已隐藏",
                        icon = Icons.Default.Menu,
                        isEnabled = uiState.isFloatingMenuEnabled,
                        onToggle = { viewModel.toggleFloatingMenuService(context) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // 时间源切换卡片
            JDCard(
                variant = CardVariant.FILLED,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 12.dp)
            ) {
                Text(
                    text = "时间源",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "京东服务器时间",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 快捷操作区
            JDCard(
                variant = CardVariant.FILLED,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // 时间同步按钮
                    TextButton(
                        onClick = {
                            scope.launch {
                                viewModel.syncNtpTime()
                                viewModel.showSyncMessage(context)
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.Sync,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            "同步",
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1
                        )
                    }

                    // 点击延迟按钮
                    TextButton(
                        onClick = { showDelayDialog = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.Timer,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            "${delayMillis.toLong()}ms",
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1
                        )
                    }

                    // 检查权限按钮
                    TextButton(
                        onClick = {
                            viewModel.checkAllPermissions()
                            viewModel.checkServiceStatus(context)
                            val a11y = if (viewModel.isAccessibilityEnabled()) "✓" else "✗"
                            val overlay = if (viewModel.isOverlayEnabled()) "✓" else "✗"
                            scope.launch {
                                snackbarHostState.showSnackbar("无障碍: $a11y  悬浮窗: $overlay")
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            "检查",
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}