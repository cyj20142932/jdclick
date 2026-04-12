# JDHelper 界面重新设计实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将APP界面重新设计为仪表盘风格，采用蓝绿渐变配色，优化交互效率

**Architecture:** 采用分层设计 - Theme层定义配色 -> 顶部状态栏组件 -> 页面组件复用通用卡片

**Tech Stack:** Jetpack Compose, Material 3, Kotlin Coroutines/Flow

---

## 文件结构

| 文件 | 操作 | 职责 |
|------|------|------|
| `app/src/main/java/com/jdhelper/app/ui/theme/Theme.kt` | 修改 | 添加蓝绿渐变配色定义 |
| `app/src/main/java/com/jdhelper/app/ui/components/StatusCard.kt` | 新建 | 通用状态卡片组件 |
| `app/src/main/java/com/jdhelper/app/ui/components/TopStatusBar.kt` | 新建 | 顶部状态栏组件 |
| `app/src/main/java/com/jdhelper/app/ui/screens/home/HomeScreen.kt` | 修改 | 仪表盘式首页布局 |
| `app/src/main/java/com/jdhelper/app/ui/screens/home/HomeViewModel.kt` | 修改 | 添加时间流和倒计时逻辑 |
| `app/src/main/java/com/jdhelper/app/ui/screens/settings/SettingsScreen.kt` | 修改 | 设置页优化重排序 |
| `app/src/main/java/com/jdhelper/app/ui/screens/history/HistoryScreen.kt` | 修改 | 历史页样式优化 |
| `app/src/main/java/com/jdhelper/app/ui/navigation/NavHost.kt` | 修改 | 集成顶部状态栏 |

---

## Task 1: 更新主题配色

**Files:**
- Modify: `app/src/main/java/com/jdhelper/app/ui/theme/Theme.kt`

- [ ] **Step 1: 添加新的颜色定义**

在 Theme.kt 文件中，在现有颜色定义后添加：

```kotlin
// 蓝绿渐变配色
val BlueGreenStart = Color(0xFF00B4DB)
val BlueGreenEnd = Color(0xFF0083B0)

// 深色主题背景
val DarkBackground = Color(0xFF1A1A2E)
val DarkSurface = Color(0xFF252540)
val DarkSurfaceVariant = Color(0xFF2D2D4A)

// 浅色主题背景
val LightBackground = Color(0xFFF5F5F7)
val LightSurface = Color(0xFFFFFFFF)

// 状态色
val StatusGreen = Color(0xFF4CAF50)
val StatusOrange = Color(0xFFFF9800)
val StatusRed = Color(0xFFF44336)
```

- [ ] **Step 2: 修改深色配色方案**

将 DarkColorScheme 中的颜色替换为：

```kotlin
private val DarkColorScheme = darkColorScheme(
    primary = BlueGreenStart,
    onPrimary = Color.White,
    primaryContainer = BlueGreenEnd,
    onPrimaryContainer = Color.White,
    secondary = BlueGreenEnd,
    onSecondary = Color.White,
    background = DarkBackground,
    onBackground = Color.White,
    surface = DarkSurface,
    onSurface = Color.White,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFCAC4D0),
    outline = Color(0xFF938F99)
)
```

- [ ] **Step 3: 修改浅色配色方案**

将 LightColorScheme 中的主色调改为蓝绿渐变：

```kotlin
private val LightColorScheme = lightColorScheme(
    primary = BlueGreenEnd,
    onPrimary = Color.White,
    primaryContainer = BlueGreenStart,
    onPrimaryContainer = Color.White,
    secondary = BlueGreenStart,
    onSecondary = Color.White,
    background = LightBackground,
    onBackground = Color(0xFF212121),
    surface = LightSurface,
    onSurface = Color(0xFF212121),
    surfaceVariant = Color(0xFFE7E0EC),
    onSurfaceVariant = Color(0xFF49454F),
    outline = Color(0xFF79747E)
)
```

- [ ] **Step 4: 提交变更**

```bash
git add app/src/main/java/com/jdhelper/app/ui/theme/Theme.kt
git commit -m "feat: add blue-green gradient colors and dark theme"
```

---

## Task 2: 创建通用状态卡片组件

**Files:**
- Create: `app/src/main/java/com/jdhelper/app/ui/components/StatusCard.kt`

- [ ] **Step 1: 创建 StatusCard 组件文件**

```kotlin
package com.jdhelper.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.jdhelper.ui.theme.BlueGreenEnd
import com.jdhelper.ui.theme.BlueGreenStart
import com.jdhelper.ui.theme.DarkSurface
import com.jdhelper.ui.theme.StatusGreen

@Composable
fun StatusCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isEnabled: Boolean,
    onToggle: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        label = "card_scale"
    )

    Card(
        modifier = modifier
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                if (onToggle != null) {
                    onToggle()
                } else if (onClick != null) {
                    onClick()
                }
            },
        colors = CardDefaults.cardColors(
            containerColor = DarkSurface
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 图标
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(BlueGreenStart, BlueGreenEnd)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 标题
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(4.dp))

            // 状态文字
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (isEnabled) StatusGreen else MaterialTheme.colorScheme.outline
            )

            // 开关（如果有）
            if (onToggle != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Switch(
                    checked = isEnabled,
                    onCheckedChange = { onToggle() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = BlueGreenEnd
                    )
                )
            }
        }
    }
}
```

- [ ] **Step 2: 提交变更**

```bash
git add app/src/main/java/com/jdhelper/app/ui/components/StatusCard.kt
git commit -m "feat: add StatusCard reusable component"
```

---

## Task 3: 创建顶部状态栏组件

**Files:**
- Create: `app/src/main/java/com/jdhelper/app/ui/components/TopStatusBar.kt`

- [ ] **Step 1: 创建 TopStatusBar 组件**

```kotlin
package com.jdhelper.ui.components

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
import com.jdhelper.ui.theme.BlueGreenEnd
import com.jdhelper.ui.theme.BlueGreenStart

@Composable
fun TopStatusBar(
    ntpTime: String,
    millis: String,
    nextClickCountdown: String,
    modifier: Modifier = Modifier
) {
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
            // NTP时间
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
                Text(
                    text = ".$millis",
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color.White.copy(alpha = 0.8f)
                )
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
```

- [ ] **Step 2: 提交变更**

```bash
git add app/src/main/java/com/jdhelper/app/ui/components/TopStatusBar.kt
git commit -m "feat: add TopStatusBar component with gradient background"
```

---

## Task 4: 更新 HomeViewModel 添加时间流

**Files:**
- Modify: `app/src/main/java/com/jdhelper/app/ui/screens/home/HomeViewModel.kt`

- [ ] **Step 1: 查看现有 HomeViewModel 结构**

读取 HomeViewModel.kt 文件，了解当前的状态和数据结构。

- [ ] **Step 2: 添加时间状态和倒计时 Flow**

在 HomeViewModel 中添加：

```kotlin
// 时间状态
private val _ntpTime = MutableStateFlow("00:00:00")
val ntpTime: StateFlow<String> = _ntpTime.asStateFlow()

private val _millis = MutableStateFlow("000")
val millis: StateFlow<String> = _millis.asStateFlow()

private val _nextClickCountdown = MutableStateFlow("")
val nextClickCountdown: StateFlow<String> = _nextClickCountdown.asStateFlow()

// 启动时间更新
fun startTimeUpdates() {
    viewModelScope.launch {
        while (true) {
            val time = System.currentTimeMillis()
            val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            val millisFormat = SimpleDateFormat("SSS", Locale.getDefault())
            _ntpTime.value = sdf.format(Date(time))
            _millis.value = millisFormat.format(Date(time))
            delay(10) // 每10ms更新一次毫秒
        }
    }
}

// 设置下次点击倒计时
fun setNextClickCountdown(timeMillis: Long) {
    viewModelScope.launch {
        while (true) {
            val remaining = timeMillis - System.currentTimeMillis()
            if (remaining <= 0) {
                _nextClickCountdown.value = ""
                break
            }
            val seconds = remaining / 1000
            val minutes = seconds / 60
            val secs = seconds % 60
            _nextClickCountdown.value = String.format("%02d:%02d", minutes, secs)
            delay(1000)
        }
    }
}
```

- [ ] **Step 3: 提交变更**

```bash
git add app/src/main/java/com/jdhelper/app/ui/screens/home/HomeViewModel.kt
git commit -m "feat: add time flow and countdown to HomeViewModel"
```

---

## Task 5: 重新设计 HomeScreen 首页布局

**Files:**
- Modify: `app/src/main/java/com/jdhelper/app/ui/screens/home/HomeScreen.kt`

- [ ] **Step 1: 添加导入**

在 HomeScreen.kt 文件顶部添加：

```kotlin
import com.jdhelper.ui.components.StatusCard
import com.jdhelper.ui.components.TopStatusBar
import com.jdhelper.ui.theme.DarkSurface
```

- [ ] **Step 2: 修改 Scaffold 添加 topBar**

将原来的 Scaffold 修改为：

```kotlin
Scaffold(
    topBar = {
        TopStatusBar(
            ntpTime = ntpTime,
            millis = millis,
            nextClickCountdown = nextClickCountdown
        )
    },
    snackbarHost = { SnackbarHost(snackbarHostState) },
    containerColor = DarkBackground
) { paddingValues ->
    // ... 现有内容
}
```

- [ ] **Step 3: 在 LaunchedEffect 中启动时间更新**

添加：

```kotlin
LaunchedEffect(Unit) {
    viewModel.startTimeUpdates()
}
```

- [ ] **Step 4: 收集状态 Flow**

添加：

```kotlin
val ntpTime by viewModel.ntpTime.collectAsState()
val millis by viewModel.millis.collectAsState()
val nextClickCountdown by viewModel.nextClickCountdown.collectAsState()
```

- [ ] **Step 5: 替换服务控制卡片为网格布局**

将原有的 `ServiceControlCard` 替换为 2x2 网格的 `StatusCard`：

```kotlin
// 状态卡片网格
Row(
    modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp),
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

Spacer(modifier = Modifier.height(12.dp))

Row(
    modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp),
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
        isEnabled =uiState.isFloatingMenuEnabled,
        onToggle = { viewModel.toggleFloatingMenuService(context) },
        modifier = Modifier.weight(1f)
    )
}
```

- [ ] **Step 6: 添加快捷操作区**

在卡片网格后面添加：

```kotlin
// 快捷操作区
Card(
    modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
    colors = CardDefaults.cardColors(
        containerColor = DarkSurface
    )
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        // 时间同步按钮
        TextButton(onClick = {
            CoroutineScope(Dispatchers.Main).launch {
                viewModel.syncNtpTime()
            }
        }) {
            Icon(Icons.Default.Sync, contentDescription = null)
            Spacer(modifier = Modifier.width(4.dp))
            Text("时间同步")
        }
        
        // 点击延迟按钮
        TextButton(onClick = { showDelayDialog = true }) {
            Icon(Icons.Default.Timer, contentDescription = null)
            Spacer(modifier = Modifier.width(4.dp))
            Text("点击延迟")
        }
        
        // 检查权限按钮
        TextButton(onClick = {
            viewModel.checkAllPermissions()
            val a11y = if (viewModel.isAccessibilityEnabled()) "✓" else "✗"
            val overlay = if (viewModel.isOverlayEnabled()) "✓" else "✗"
            CoroutineScope(Dispatchers.Main).launch {
                snackbarHostState.showSnackbar("无障碍: $a11y  悬浮窗: $overlay")
            }
        }) {
            Icon(Icons.Default.Check, contentDescription = null)
            Spacer(modifier = Modifier.width(4.dp))
            Text("检查")
        }
    }
}
```

- [ ] **Step 7: 提交变更**

```bash
git add app/src/main/java/com/jdhelper/app/ui/screens/home/HomeScreen.kt
git commit -m "feat: redesign HomeScreen with dashboard layout"
```

---

## Task 6: 优化设置页布局

**Files:**
- Modify: `app/src/main/java/com/jdhelper/app/ui/screens/settings/SettingsScreen.kt`

- [ ] **Step 1: 调整设置项顺序**

将设置页的顺序调整为：
1. 权限管理（无障碍服务、悬浮窗权限、通知权限）
2. 点击设置（点击延迟、点击持续时间、毫秒格式）
3. 历史记录（记录开关）
4. 关于

- [ ] **Step 2: 添加分组可折叠功能**

使用 `ExposedDropdownMenuBox` 或自定义折叠组件来实现分组折叠。

- [ ] **Step 3: 简化开关操作**

将 Switch 组件的 onCheckedChange 直接绑定到 ViewModel 的保存方法，无需额外确认按钮。

- [ ] **Step 4: 提交变更**

```bash
git add app/src/main/java/com/jdhelper/app/ui/screens/settings/SettingsScreen.kt
git commit -m "feat: optimize SettingsScreen layout and interactions"
```

---

## Task 7: 优化历史页样式

**Files:**
- Modify: `app/src/main/java/com/jdhelper/app/ui/screens/history/HistoryScreen.kt`

- [ ] **Step 1: 更新卡片样式**

- 增大信息密度，时间、延迟、状态一行显示
- 使用更醒目的状态颜色标记

- [ ] **Step 2: 提交变更**

```bash
git add app/src/main/java/com/jdhelper/app/ui/screens/history/HistoryScreen.kt
git commit -m "feat: optimize HistoryScreen card styles"
```

---

## Task 8: 整体集成和验收

**Files:**
- Modify: `app/src/main/java/com/jdhelper/app/ui/theme/Theme.kt` (补充 DarkBackground 导入)
- Modify: `app/src/main/java/com/jdhelper/app/ui/screens/home/HomeViewModel.kt` (补充导入)

- [ ] **Step 1: 修复缺失的导入**

确保所有组件能正确引用新添加的颜色和组件。

- [ ] **Step 2: 构建测试**

```bash
./gradlew assembleDebug
```

预期结果：编译成功，无错误

- [ ] **Step 3: 最终提交**

```bash
git add .
git commit -m "feat: complete UI redesign with dashboard style"
```

---

## 验收清单

### 视觉验收
- [ ] 顶部状态栏显示NTP时间和倒计时
- [ ] 蓝绿渐变配色正确应用
- [ ] 深色主题正确应用
- [ ] 卡片网格布局正确显示 (2x2)

### 功能验收
- [ ] 点击状态卡片跳转正确页面
- [ ] 开关直接Toggle生效
- [ ] 时间同步按钮正常工作
- [ ] 延迟设置对话框正常弹出和保存

### 交互验收
- [ ] 卡片点击有缩放反馈
- [ ] 删除确认对话框保留
- [ ] 页面切换流畅