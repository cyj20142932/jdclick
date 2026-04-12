# 时间同步修复与悬浮菜单优化实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复首页顶部状态栏与悬浮时钟时间不同步的问题，显示NTP时间偏差，优化悬浮菜单布局和样式

**Architecture:** 共享NTP时间源 - NtpTimeService作为单例供所有组件使用，首页和悬浮时钟显示同一时间

**Tech Stack:** Kotlin, Jetpack Compose, Android Services, Coroutines

---

## 文件结构

| 文件 | 操作 | 职责 |
|------|------|------|
| `app/src/main/java/com/jdhelper/app/ui/screens/home/HomeViewModel.kt` | 修改 | 时间更新改用ntpTimeService |
| `app/src/main/java/com/jdhelper/app/ui/components/TopStatusBar.kt` | 修改 | 增加偏差显示参数 |
| `app/src/main/java/com/jdhelper/app/ui/screens/home/HomeScreen.kt` | 修改 | 传递偏差值到状态栏 |
| `app/src/main/res/layout/floating_menu.xml` | 修改 | 重新设计布局结构 |
| `app/src/main/java/com/jdhelper/app/service/FloatingMenuService.kt` | 修改 | 添加状态指示逻辑 |

---

## Task 1: 修改 HomeViewModel 使用 NTP 时间源

**Files:**
- Modify: `app/src/main/java/com/jdhelper/app/ui/screens/home/HomeViewModel.kt`

- [ ] **Step 1: 添加 NtpTimeService 注入**

在 HomeViewModel 类中已有 `ntpTimeService: NtpTimeService`，确保已注入：

```kotlin
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val ntpTimeService: NtpTimeService,
    private val clickSettingsRepository: ClickSettingsRepository,
    private val application: Application
) : ViewModel() {
```

- [ ] **Step 2: 添加偏差状态 Flow**

在现有状态Flow后添加：

```kotlin
// NTP偏差状态
private val _ntpOffset = MutableStateFlow("--")
val ntpOffset: StateFlow<String> = _ntpOffset.asStateFlow()
```

- [ ] **Step 3: 修改 startTimeUpdates 使用 NTP 时间**

将原方法中的 `System.currentTimeMillis()` 替换为 `ntpTimeService.getCurrentTime()`：

```kotlin
fun startTimeUpdates() {
    viewModelScope.launch {
        while (true) {
            try {
                val time = ntpTimeService.getCurrentTime()  // 改用NTP时间
                val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                val millisFormat = SimpleDateFormat("SSS", Locale.getDefault())
                _ntpTime.value = sdf.format(Date(time))
                _millis.value = millisFormat.format(Date(time))
                
                // 更新偏差显示
                updateNtpOffset()
            } catch (e: Exception) {
                Log.e(TAG, "更新时间失败", e)
            }
            delay(10) // 每10ms更新一次毫秒
        }
    }
}

private fun updateNtpOffset() {
    if (ntpTimeService.isSynced()) {
        val offset = ntpTimeService.getTimeOffset()
        _ntpOffset.value = if (offset >= 0) "+${offset}ms" else "${offset}ms"
    } else {
        _ntpOffset.value = "--ms"
    }
}
```

- [ ] **Step 4: 在 syncNtpTime 后更新偏差**

修改 syncNtpTime 方法，在同步成功后更新偏差显示：

```kotlin
suspend fun syncNtpTime(): Boolean {
    Log.d(TAG, "开始同步NTP时间...")
    _uiState.update { it.copy(isNtpSyncing = true) }

    try {
        val success = ntpTimeService.syncTime()

        if (success) {
            val syncTime = ntpTimeService.getLastSyncTime()
            val format = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            val timeText = format.format(java.util.Date(syncTime))

            // 计算时间偏差
            val offset = ntpTimeService.getTimeOffset()
            val offsetText = if (offset >= 0) "+${offset}ms" else "${offset}ms"

            _uiState.update { it.copy(ntpLastSyncTime = timeText, ntpTimeOffset = offsetText, isNtpSyncing = false) }
            
            // 更新偏差显示
            _ntpOffset.value = offsetText
            
            Log.d(TAG, "NTP同步成功: $timeText, 偏差: $offsetText")
            // ...
        }
    }
}
```

- [ ] **Step 5: 提交变更**

```bash
git add app/src/main/java/com/jdhelper/app/ui/screens/home/HomeViewModel.kt
git commit -m "feat: use NTP time source for HomeViewModel time updates"
```

---

## Task 2: 修改 TopStatusBar 增加偏差显示

**Files:**
- Modify: `app/src/main/java/com/jdhelper/app/ui/components/TopStatusBar.kt`

- [ ] **Step 1: 添加 ntpOffset 参数**

修改函数签名，增加 ntpOffset 参数：

```kotlin
@Composable
fun TopStatusBar(
    ntpTime: String,
    millis: String,
    ntpOffset: String = "",  // 新增参数
    nextClickCountdown: String = "",
    modifier: Modifier = Modifier
) {
```

- [ ] **Step 2: 在毫秒后添加偏差显示**

找到显示毫秒的Text组件，在其后添加偏差显示：

```kotlin
// 原代码
Text(
    text = ".$millis",
    fontSize = 14.sp,
    fontFamily = FontFamily.Monospace,
    color = Color.White.copy(alpha = 0.8f)
)

// 修改为
Row(verticalAlignment = Alignment.CenterVertically) {
    Text(
        text = ".$millis",
        fontSize = 14.sp,
        fontFamily = FontFamily.Monospace,
        color = Color.White.copy(alpha = 0.8f)
    )
    if (ntpOffset.isNotEmpty()) {
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = ntpOffset,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            color = Color.White.copy(alpha = 0.7f)
        )
    }
}
```

- [ ] **Step 3: 提交变更**

```bash
git add app/src/main/java/com/jdhelper/app/ui/components/TopStatusBar.kt
git commit -m "feat: add NTP offset display to TopStatusBar"
```

---

## Task 3: 修改 HomeScreen 传递偏差值

**Files:**
- Modify: `app/src/main/java/com/jdhelper/app/ui/screens/home/HomeScreen.kt`

- [ ] **Step 1: 添加 ntpOffset 状态收集**

在现有状态收集后添加：

```kotlin
val ntpTime by viewModel.ntpTime.collectAsState()
val millis by viewModel.millis.collectAsState()
val nextClickCountdown by viewModel.nextClickCountdown.collectAsState()
val ntpOffset by viewModel.ntpOffset.collectAsState()  // 新增
```

- [ ] **Step 2: 传递 ntpOffset 到 TopStatusBar**

修改 TopStatusBar 调用：

```kotlin
TopStatusBar(
    ntpTime = ntpTime,
    millis = millis,
    ntpOffset = ntpOffset,  // 新增
    nextClickCountdown = nextClickCountdown
)
```

- [ ] **Step 3: 提交变更**

```bash
git add app/src/main/java/com/jdhelper/app/ui/screens/home/HomeScreen.kt
git commit -m "feat: pass NTP offset to TopStatusBar"
```

---

## Task 4: 重新设计悬浮菜单布局

**Files:**
- Modify: `app/src/main/res/layout/floating_menu.xml`

- [ ] **Step 1: 创建新布局结构**

将原布局替换为带状态指示和标签的新布局：

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/menu_container"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:background="@drawable/floating_background"
    android:padding="8dp"
    android:gravity="center">

    <!-- 状态指示条 -->
    <View
        android:id="@+id/status_indicator"
        android:layout_width="match_parent"
        android:layout_height="4dp"
        android:background="#4CAF50" />

    <!-- 时钟状态区域 -->
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:gravity="center_vertical"
        android:padding="8dp">

        <TextView
            android:id="@+id/text_clock_status"
            android:layout_width="0dp"
            android:layout_weight="1"
            android:text="时钟: 同步中..."
            android:textColor="#FFFFFF"
            android:textSize="12sp" />

        <TextView
            android:id="@+id/text_ntp_offset"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="--ms"
            android:textColor="#AAAAAA"
            android:textSize="11sp"
            android:fontFamily="monospace" />
    </LinearLayout>

    <!-- 分割线 -->
    <View
        android:layout_width="match_parent"
        android:layout_height="1dp"
        android:background="#444444" />

    <!-- 按钮区域 -->
    <LinearLayout
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:gravity="center"
        android:paddingTop="4dp">

        <!-- 时钟按钮 + 标签 -->
        <LinearLayout
            android:layout_width="64dp"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:gravity="center"
            android:padding="4dp">

            <FrameLayout
                android:layout_width="40dp"
                android:layout_height="40dp">

                <ImageButton
                    android:id="@+id/btn_clock"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent"
                    android:src="@android:drawable/ic_menu_recent_history"
                    android:background="@drawable/button_gradient_bg"
                    android:contentDescription="时钟" />

                <View
                    android:id="@+id/indicator_clock"
                    android:layout_width="8dp"
                    android:layout_height="8dp"
                    android:layout_gravity="top|end"
                    android:background="@drawable/status_dot_green"
                    android:visibility="gone" />
            </FrameLayout>

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="时钟"
                android:textColor="#CCCCCC"
                android:textSize="10sp"
                android:layout_marginTop="2dp" />
        </LinearLayout>

        <!-- 循环按钮 + 标签 -->
        <LinearLayout
            android:layout_width="64dp"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:gravity="center"
            android:padding="4dp">

            <FrameLayout
                android:layout_width="40dp"
                android:layout_height="40dp">

                <ImageButton
                    android:id="@+id/btn_lock"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent"
                    android:src="@android:drawable/ic_menu_manage"
                    android:background="@drawable/button_gradient_bg"
                    android:contentDescription="循环" />

                <View
                    android:id="@+id/indicator_loop"
                    android:layout_width="8dp"
                    android:layout_height="8dp"
                    android:layout_gravity="top|end"
                    android:background="@drawable/status_dot_green"
                    android:visibility="gone" />
            </FrameLayout>

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="循环"
                android:textColor="#CCCCCC"
                android:textSize="10sp"
                android:layout_marginTop="2dp" />
        </LinearLayout>

        <!-- 礼物按钮 + 标签 -->
        <LinearLayout
            android:layout_width="64dp"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:gravity="center"
            android:padding="4dp">

            <FrameLayout
                android:layout_width="40dp"
                android:layout_height="40dp">

                <ImageButton
                    android:id="@+id/btn_gift"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent"
                    android:src="@android:drawable/ic_menu_send"
                    android:background="@drawable/button_gradient_bg"
                    android:contentDescription="礼物" />

                <View
                    android:id="@+id/indicator_gift"
                    android:layout_width="8dp"
                    android:layout_height="8dp"
                    android:layout_gravity="top|end"
                    android:background="@drawable/status_dot_green"
                    android:visibility="gone" />
            </FrameLayout>

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="礼物"
                android:textColor="#CCCCCC"
                android:textSize="10sp"
                android:layout_marginTop="2dp" />
        </LinearLayout>

        <!-- 定时按钮 + 标签 -->
        <LinearLayout
            android:layout_width="64dp"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:gravity="center"
            android:padding="4dp">

            <FrameLayout
                android:layout_width="40dp"
                android:layout_height="40dp">

                <ImageButton
                    android:id="@+id/btn_play"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent"
                    android:src="@android:drawable/ic_media_play"
                    android:background="@drawable/button_gradient_bg"
                    android:contentDescription="定时" />

                <View
                    android:id="@+id/indicator_timed"
                    android:layout_width="8dp"
                    android:layout_height="8dp"
                    android:layout_gravity="top|end"
                    android:background="@drawable/status_dot_green"
                    android:visibility="gone" />
            </FrameLayout>

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="定时"
                android:textColor="#CCCCCC"
                android:textSize="10sp"
                android:layout_marginTop="2dp" />
        </LinearLayout>
    </LinearLayout>

    <!-- 分割线 -->
    <View
        android:layout_width="match_parent"
        android:layout_height="1dp"
        android:background="#444444"
        android:layout_marginVertical="4dp" />

    <!-- 底部按钮 -->
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:gravity="center">

        <ImageButton
            android:id="@+id/btn_stop"
            android:layout_width="36dp"
            android:layout_height="36dp"
            android:src="@android:drawable/ic_media_pause"
            android:background="?android:attr/selectableItemBackgroundBorderless"
            android:contentDescription="停止" />

        <ImageButton
            android:id="@+id/btn_close"
            android:layout_width="36dp"
            android:layout_height="36dp"
            android:src="@android:drawable/ic_menu_close_clear_cancel"
            android:background="?android:attr/selectableItemBackgroundBorderless"
            android:contentDescription="关闭" />

        <ImageButton
            android:id="@+id/btn_drag"
            android:layout_width="36dp"
            android:layout_height="36dp"
            android:src="@android:drawable/ic_menu_more"
            android:background="?android:attr/selectableItemBackgroundBorderless"
            android:contentDescription="拖拽" />
    </LinearLayout>

</LinearLayout>
```

- [ ] **Step 2: 创建按钮渐变背景 drawable**

新建 `app/src/main/res/drawable/button_gradient_bg.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <gradient
        android:type="linear"
        android:angle="135"
        android:startColor="#00B4DB"
        android:endColor="#0083B0" />
    <corners android:radius="8dp" />
</shape>
```

- [ ] **Step 3: 创建状态点 drawable**

新建 `app/src/main/res/drawable/status_dot_green.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="oval">
    <solid android:color="#4CAF50" />
    <size android:width="8dp" android:height="8dp" />
</shape>
```

- [ ] **Step 4: 提交变更**

```bash
git add app/src/main/res/layout/floating_menu.xml app/src/main/res/drawable/button_gradient_bg.xml app/src/main/res/drawable/status_dot_green.xml
git commit -m "feat: redesign floating menu with status indicators and labels"
```

---

## Task 5: 修改 FloatingMenuService 添加状态指示逻辑

**Files:**
- Modify: `app/src/main/java/com/jdhelper/app/service/FloatingMenuService.kt`

- [ ] **Step 1: 添加视图引用**

在类顶部添加新视图变量：

```kotlin
// 新增视图引用
private var statusIndicator: View? = null
private var textClockStatus: TextView? = null
private var textNtpOffset: TextView? = null

// 状态指示点
private var indicatorClock: View? = null
private var indicatorLoop: View? = null
private var indicatorGift: View? = null
private var indicatorTimed: View? = null
```

- [ ] **Step 2: 获取视图引用**

在 showFloatingMenu 方法中，获取新添加的视图：

```kotlin
// 获取新视图引用
statusIndicator = floatingView?.findViewById(R.id.status_indicator)
textClockStatus = floatingView?.findViewById(R.id.text_clock_status)
textNtpOffset = floatingView?.findViewById(R.id.text_ntp_offset)

// 状态指示点
indicatorClock = floatingView?.findViewById(R.id.indicator_clock)
indicatorLoop = floatingView?.findViewById(R.id.indicator_loop)
indicatorGift = floatingView?.findViewById(R.id.indicator_gift)
indicatorTimed = floatingView?.findViewById(R.id.indicator_timed)

// 初始化显示NTP状态
updateNtpStatusDisplay()
```

- [ ] **Step 3: 添加状态更新方法**

在类中添加新方法：

```kotlin
/**
 * 更新NTP状态显示
 */
private fun updateNtpStatusDisplay() {
    if (ntpTimeService.isSynced()) {
        val offset = ntpTimeService.getTimeOffset()
        val offsetText = if (offset >= 0) "+${offset}ms" else "${offset}ms"
        textNtpOffset?.text = offsetText
        textClockStatus?.text = "时钟: 已同步"
        
        // 状态指示条设为绿色
        statusIndicator?.setBackgroundColor(android.graphics.Color.parseColor("#4CAF50"))
    } else {
        textNtpOffset?.text = "--ms"
        textClockStatus?.text = "时钟: 未同步"
        
        // 状态指示条设为灰色
        statusIndicator?.setBackgroundColor(android.graphics.Color.parseColor("#888888"))
    }
}

/**
 * 更新指定任务的状态指示点
 */
private fun updateTaskIndicator(indicator: View?, isRunning: Boolean) {
    indicator?.visibility = if (isRunning) View.VISIBLE else View.GONE
}

/**
 * 更新整体状态指示条
 */
private fun updateOverallStatus() {
    val isAnyRunning = isLoopRunning || isGiftRunning || timedClickManager.isRunning()
    statusIndicator?.setBackgroundColor(
        if (isAnyRunning) android.graphics.Color.parseColor("#4CAF50")
        else android.graphics.Color.parseColor("#888888")
    )
}
```

- [ ] **Step 4: 在各任务状态变化时更新指示器**

修改 btnClock 点击事件，在同步后更新显示：

```kotlin
// 时钟按钮点击事件中，同步后添加：
CoroutineScope(Dispatchers.Main).launch {
    // ... 现有同步逻辑 ...
    
    // 更新NTP状态显示
    updateNtpStatusDisplay()
}
```

修改 btnLock 点击事件，添加状态指示更新：

```kotlin
// 启动循环任务后
isLoopRunning = true
updateTaskIndicator(indicatorLoop, true)
updateOverallStatus()

// 停止循环任务后
isLoopRunning = false
updateTaskIndicator(indicatorLoop, false)
updateOverallStatus()
```

同样修改 btnGift 和 btn_play 点击事件。

- [ ] **Step 5: 提交变更**

```bash
git add app/src/main/java/com/jdhelper/app/service/FloatingMenuService.kt
git commit -m "feat: add status indicator logic to FloatingMenuService"
```

---

## Task 6: 构建验证

**Files:**
- (无新文件)

- [ ] **Step 1: 清理并构建**

```bash
./gradlew clean assembleDebug
```

预期结果：编译成功，无错误

- [ ] **Step 2: 提交变更**

```bash
git add .
git commit -m "feat: complete time sync fix and floating menu optimization"
```

---

## 验收清单

### 时间同步验收
- [ ] 首页顶部状态栏显示的时间与悬浮时钟显示的时间完全一致
- [ ] NTP同步成功后，偏差值正确显示在状态栏
- [ ] 未进行NTP同步时，显示 "--ms" 占位符

### 悬浮菜单验收
- [ ] 顶部有状态指示条，颜色根据任务状态变化
- [ ] 每个功能按钮下方有文字标签说明功能
- [ ] 按钮使用蓝绿渐变背景，与APP主题统一
- [ ] 任务运行时，对应按钮有运行状态点

### 功能验收
- [ ] 点击首页"时间同步"按钮后，偏差值正确更新
- [ ] 悬浮菜单点击时钟按钮后，时间偏差正确显示
- [ ] 所有现有功能不受影响