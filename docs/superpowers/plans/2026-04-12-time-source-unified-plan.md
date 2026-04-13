# 时间源统一管理实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 创建 TimeViewModel 集中管理时间状态，通过 Flow 观察变化，完全消除广播依赖，实现 NTP/京东时间源的统一管理。

**Architecture:** 引入 TimeViewModel 作为时间状态管理层，所有组件通过 StateFlow 观察时间变化。TimeService 接口保持不变，TimeViewModel 封装其上的观察层。

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, StateFlow, Coroutines

---

## 文件结构概览

```
app/src/main/java/com/jdhelper/app/
├── ui/screens/
│   └── time/
│       └── TimeViewModel.kt          # 新建: 时间管理 ViewModel
├── service/
│   ├── FloatingService.kt            # 修改: 使用 TimeViewModel
│   ├── FloatingMenuService.kt        # 修改: 使用 TimeViewModel
│   └── FloatingStateManager.kt       # 修改: 移除时间广播方法
├── ui/screens/home/
│   └── HomeViewModel.kt              # 修改: 使用 TimeViewModel
└── di/
    └── ServiceModule.kt              # 修改: 提供 TimeViewModel 依赖
```

---

## Task 1: 创建 TimeViewModel 类

**Files:**
- Create: `app/src/main/java/com/jdhelper/app/ui/screens/time/TimeViewModel.kt`
- Dependencies: `app/src/main/java/com/jdhelper/app/service/TimeService.kt`, `app/src/main/java/com/jdhelper/app/data/local/ClickSettings.kt`

- [ ] **Step 1: 创建 TimeViewModel 文件**

在 `app/src/main/java/com/jdhelper/app/ui/screens/time/` 目录下创建 `TimeViewModel.kt`：

```kotlin
package com.jdhelper.ui.screens.time

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jdhelper.app.service.TimeService
import com.jdhelper.data.local.TimeSource
import com.jdhelper.domain.repository.ClickSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 时间管理 ViewModel
 * 集中管理时间状态，通过 StateFlow 供其他组件观察
 */
@HiltViewModel
class TimeViewModel @Inject constructor(
    private val timeService: TimeService,
    private val clickSettingsRepository: ClickSettingsRepository
) : ViewModel() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // ==================== 公开的 StateFlow ====================

    /**
     * 当前使用的时间源 (NTP 或 JD)
     */
    val timeSource: StateFlow<TimeSource> = clickSettingsRepository.getTimeSource()
        .stateIn(scope, SharingStarted.Eagerly, TimeSource.NTP)

    /**
     * 当前时间 - 每 10ms 更新一次
     * 所有需要获取当前时间的组件都应该观察这个 Flow
     */
    val currentTime: StateFlow<Long>

    /**
     * 时间偏移（毫秒，相对于系统时间）
     */
    val timeOffset: StateFlow<Long>

    /**
     * 偏移显示文本，如 "+15ms" 或 "-23ms"
     */
    val offsetText: StateFlow<String>

    /**
     * 当前时间源是否已同步
     */
    val isSynced: StateFlow<Boolean>

    init {
        // 观察当前时间 - 使用 timer 每 10ms 更新
        val timeFlow = kotlinx.coroutines.flow.flow {
            while (true) {
                emit(timeService.getCurrentTime())
                delay(10)
            }
        }
        currentTime = timeFlow.stateIn(
            scope,
            SharingStarted.Eagerly,
            System.currentTimeMillis()
        )

        // 时间偏移
        timeOffset = timeSource.map { _ ->
            timeService.getTimeOffset()
        }.stateIn(scope, SharingStarted.Eagerly, 0L)

        // 偏移文本
        offsetText = timeOffset.map { offset ->
            if (offset >= 0) "+${offset}ms" else "${offset}ms"
        }.stateIn(scope, SharingStarted.Eagerly, "--ms")

        // 是否已同步
        isSynced = timeSource.map { _ ->
            timeService.isSynced()
        }.stateIn(scope, SharingStarted.Eagerly, false)
    }

    // ==================== 方法 ====================

    /**
     * 同步当前时间源的时间
     * @return 同步是否成功
     */
    suspend fun syncTime(): Boolean {
        return timeService.syncTime()
    }

    /**
     * 切换时间源并自动同步
     */
    suspend fun switchTimeSource(source: TimeSource): Boolean {
        clickSettingsRepository.setTimeSource(source)

        // 如果切换到京东时间，自动同步
        return if (source == TimeSource.JD) {
            timeService.syncTime()
        } else {
            true
        }
    }

    /**
     * 获取当前时间（同步调用）
     */
    fun getCurrentTime(): Long = timeService.getCurrentTime()

    /**
     * 获取偏移显示文本（同步调用）
     */
    fun getOffsetText(): String = timeService.getOffsetText()
}
```

- [ ] **Step 2: 确保 TimeSource 导入正确**

确认 `app/src/main/java/com/jdhelper/app/data/local/ClickSettings.kt` 中有:
```kotlin
enum class TimeSource {
    NTP,
    JD
}
```

- [ ] **Step 3: 验证文件创建成功**

Run: `ls -la app/src/main/java/com/jdhelper/app/ui/screens/time/`
Expected: `TimeViewModel.kt` 文件存在

---

## Task 2: 修改 FloatingService 使用 TimeViewModel

**Files:**
- Modify: `app/src/main/java/com/jdhelper/app/service/FloatingService.kt:1-450`
- Dependencies: TimeViewModel, FloatingStateManager

- [ ] **Step 1: 添加 TimeViewModel 注入**

在 FloatingService 的属性注入部分添加：
```kotlin
@Inject
lateinit var timeViewModel: TimeViewModel
```

- [ ] **Step 2: 修改 updateTimeDisplay 方法**

将 updateTimeDisplay() 方法的参数改为接收时间参数：

修改前：
```kotlin
private fun updateTimeDisplay() {
    val displayTime = timeService.getCurrentTime()
    ...
}
```

修改后：
```kotlin
private fun updateTimeDisplay(displayTime: Long) {
    val date = Date(displayTime)
    ...
}
```

- [ ] **Step 3: 修改 startTimeUpdate 使用 Flow**

修改 startTimeUpdate() 方法：

修改前（约第382-388行）：
```kotlin
private fun startTimeUpdate() {
    timeUpdateJob = CoroutineScope(Dispatchers.Main).launch {
        while (isActive) {
            updateTimeDisplay()
            delay(10)
        }
    }
}
```

修改后：
```kotlin
private fun startTimeUpdate() {
    timeUpdateJob = CoroutineScope(Dispatchers.Main).launch {
        timeViewModel.currentTime.collect { time ->
            updateTimeDisplay(time)
        }
    }
}
```

- [ ] **Step 4: 移除广播接收器相关代码**

1. 移除 `stateReceiver` 变量的广播相关逻辑（约第335-372行）
2. 移除 `registerStateReceiver()` 方法
3. 移除在 onDestroy 中对 stateReceiver 的注销

保留 FloatingStateManager 的任务状态相关广播（`notifyTaskStateChanged`），只移除时间相关的广播。

- [ ] **Step 5: 清理不再需要的导入**

移除不再使用的导入：
```kotlin
// 这些可能不再需要
import android.content.BroadcastReceiver
import android.content.IntentFilter
```

- [ ] **Step 6: 编译验证**

Run: `./gradlew assembleDebug 2>&1 | head -50`
Expected: 编译成功，无错误

---

## Task 3: 修改 FloatingMenuService 使用 TimeViewModel

**Files:**
- Modify: `app/src/main/java/com/jdhelper/app/service/FloatingMenuService.kt`

- [ ] **Step 1: 检查 FloatingMenuService 当前实现**

Run: `grep -n "timeService\|BroadcastReceiver\|registerReceiver" app/src/main/java/com/jdhelper/app/service/FloatingMenuService.kt`

- [ ] **Step 2: 添加 TimeViewModel 注入**

```kotlin
@Inject
lateinit var timeViewModel: TimeViewModel
```

- [ ] **Step 3: 移除广播接收器相关代码**

参考 FloatingService 的修改，移除广播接收器相关代码。

- [ ] **Step 4: 使用 TimeViewModel 获取时间**

将 `timeService.getCurrentTime()` 调用替换为 `timeViewModel.currentTime.value` 或在 Flow 中收集。

- [ ] **Step 5: 编译验证**

Run: `./gradlew assembleDebug 2>&1 | head -50`
Expected: 编译成功，无错误

---

## Task 4: 修改 HomeViewModel 使用 TimeViewModel

**Files:**
- Modify: `app/src/main/java/com/jdhelper/app/ui/screens/home/HomeViewModel.kt:1-406`

- [ ] **Step 1: 添加 TimeViewModel 注入**

在 HomeViewModel 构造函数中添加：
```kotlin
@Inject
lateinit var timeViewModel: TimeViewModel
```

- [ ] **Step 2: 修改时间显示相关代码**

将原有的时间显示逻辑替换为使用 TimeViewModel：

```kotlin
// 修改后 - 时间显示
val currentTimeDisplay: StateFlow<String> = timeViewModel.currentTime
    .map { time ->
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(time))
    }
    .stateIn(viewModelScope, SharingStarted.Eagerly, "00:00:00")

val millisDisplay: StateFlow<String> = timeViewModel.currentTime
    .map { time ->
        SimpleDateFormat("SSS", Locale.getDefault()).format(Date(time))
    }
    .stateIn(viewModelScope, SharingStarted.Eagerly, "000")

val timeOffsetDisplay: StateFlow<String> = timeViewModel.offsetText

val isSyncedDisplay: StateFlow<Boolean> = timeViewModel.isSynced
```

- [ ] **Step 3: 修改时间源切换方法**

将 setTimeSource 方法修改为使用 TimeViewModel：

```kotlin
fun setTimeSource(source: TimeSource) {
    viewModelScope.launch {
        timeViewModel.switchTimeSource(source)
    }
}
```

- [ ] **Step 4: 修改同步方法**

将 syncNtpTime 和 syncJdTime 方法统一为使用 TimeViewModel：

```kotlin
fun syncTime() {
    viewModelScope.launch {
        timeViewModel.syncTime()
    }
}
```

- [ ] **Step 5: 删除不再需要的代码**

删除以下不再需要的导入和变量：
- `NtpTimeService` 注入（如果不再使用）
- `JdTimeService` 注入（如果不再使用）
- `floatingStateManager` 注入（如果只用于时间相关广播）
- 时间相关的 `_ntpTime`, `_millis`, `_ntpOffset` 等 MutableStateFlow

- [ ] **Step 6: 编译验证**

Run: `./gradlew assembleDebug 2>&1 | head -50`
Expected: 编译成功，无错误

---

## Task 5: 清理 FloatingStateManager 中的广播代码

**Files:**
- Modify: `app/src/main/java/com/jdhelper/app/service/FloatingStateManager.kt`

- [ ] **Step 1: 移除时间相关的广播常量**

移除以下常量定义：
```kotlin
const val ACTION_NTP_SYNC_CHANGED = "com.jdhelper.NTP_SYNC_CHANGED"
const val ACTION_TIME_SOURCE_CHANGED = "com.jdhelper.TIME_SOURCE_CHANGED"
const val ACTION_REFRESH_TIME = "com.jdhelper.REFRESH_TIME"

const val EXTRA_NTP_SYNCED = "ntp_synced"
const val EXTRA_NTP_OFFSET = "ntp_offset"
const val EXTRA_TIME_SOURCE = "time_source"
```

- [ ] **Step 2: 移除时间相关的 StateFlow**

移除以下 StateFlow：
```kotlin
private val _ntpSynced = MutableStateFlow(false)
val ntpSynced: StateFlow<Boolean> = _ntpSynced.asStateFlow()

private val _ntpOffset = MutableStateFlow(0L)
val ntpOffset: StateFlow<Long> = _ntpOffset.asStateFlow()
```

- [ ] **Step 3: 移除时间相关的通知方法**

移除以下方法：
```kotlin
fun notifyNtpSyncChanged(synced: Boolean, offset: Long)
fun notifyTimeSourceChanged(source: TimeSource)
fun requestRefreshTime()
```

- [ ] **Step 4: 保留任务状态相关方法**

保留 `notifyTaskStateChanged()` 方法，因为任务状态变化仍需要广播。

- [ ] **Step 5: 编译验证**

Run: `./gradlew assembleDebug 2>&1 | head -50`
Expected: 编译成功，无错误

---

## Task 6: 测试验证时间源切换功能

**Files:**
- Test: 手动测试所有时间相关功能

- [ ] **Step 1: 编译 Debug APK**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: 安装 APK 并测试基本功能**

1. 启动应用
2. 检查主界面时间显示是否正常（应该每10ms更新毫秒）
3. 切换时间源到 NTP
4. 切换时间源到京东
5. 验证时间显示是否随时间源切换而变化

- [ ] **Step 3: 测试悬浮窗**

1. 启动悬浮窗服务
2. 验证悬浮窗时间显示是否正确
3. 切换时间源，验证悬浮窗是否自动更新

- [ ] **Step 4: 测试定时点击功能**

1. 设置定时点击任务
2. 验证点击时间准确度

- [ ] **Step 5: 测试数据持久化**

1. 切换时间源
2. 杀死应用
3. 重新打开应用
4. 验证时间源设置是否保留

---

## 实施检查清单

- [ ] Task 1: 创建 TimeViewModel 类
- [ ] Task 2: 修改 FloatingService 使用 TimeViewModel
- [ ] Task 3: 修改 FloatingMenuService 使用 TimeViewModel
- [ ] Task 4: 修改 HomeViewModel 使用 TimeViewModel
- [ ] Task 5: 清理 FloatingStateManager 中的广播代码
- [ ] Task 6: 测试验证时间源切换功能

---

## 常见问题与解决方案

| 问题 | 解决方案 |
|------|----------|
| 编译错误：TimeViewModel 未找到 | 确保 Hilt 正确生成依赖，可在 build 后检查 |
| 时间更新不流畅 | 检查 Flow 的 SharingStarted 模式，确保使用 Eagerly |
| 广播相关错误 | 确保已移除所有广播相关代码，或保留必要的任务广播 |
| 时间源切换不生效 | 检查 ClickSettingsRepository 是否正确保存设置 |

---

**Plan complete and saved to `docs/superpowers/plans/2026-04-12-time-source-unified-plan.md`.**

**Two execution options:**

1. **Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

2. **Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

Which approach?