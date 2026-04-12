# 悬浮窗状态同步设计

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复时间源切换和时间同步时，状态栏、悬浮菜单、悬浮时钟不统一的问题

**Architecture:** 为 FloatingMenuService 添加广播接收器，与 FloatingService 保持一致的状态更新机制。添加时间源切换广播，确保所有组件同步响应状态变化。

**Tech Stack:** Android BroadcastReceiver, Kotlin Coroutines, Hilt DI

---

## 问题分析

### 当前状态
1. **FloatingService**（悬浮时钟）:
   - ✅ 使用 `timeService.getCurrentTime()` 获取时间
   - ✅ 注册了 `FloatingStateManager` 广播接收器
   - ✅ 响应 `ACTION_REFRESH_TIME` 和 `ACTION_NTP_SYNC_CHANGED`

2. **FloatingMenuService**（悬浮菜单）:
   - ✅ 使用 `timeService` 获取时间和偏移
   - ❌ **没有注册广播接收器** - 不会自动刷新状态显示

3. **HomeViewModel/UI**:
   - ✅ 正确使用 `timeService`
   - ✅ 同步后发送广播

### 问题根源
当时间源切换或同步时：
- HomeViewModel 发送广播
- FloatingService 收到广播后正确刷新
- **FloatingMenuService 没有注册接收器，不会刷新**

---

## 解决方案

### 1. FloatingStateManager - 添加时间源切换广播

在 `FloatingStateManager.kt` 中添加：
- 新增常量 `ACTION_TIME_SOURCE_CHANGED`
- 新增常量 `EXTRA_TIME_SOURCE`
- 新增方法 `notifyTimeSourceChanged(source: TimeSource)`

### 2. FloatingMenuService - 注册广播接收器

在 `FloatingMenuService.kt` 中添加：
- 注册 BroadcastReceiver
- 监听三个广播：`ACTION_REFRESH_TIME`, `ACTION_NTP_SYNC_CHANGED`, `ACTION_TIME_SOURCE_CHANGED`
- 收到广播后更新时间和状态显示

### 3. HomeViewModel - 时间源切换时发送广播

在 `HomeViewModel.kt` 的 `setTimeSource()` 方法中：
- 切换时间源后发送 `ACTION_TIME_SOURCE_CHANGED` 广播

---

## 实现步骤

### Task 1: 修改 FloatingStateManager 添加时间源切换广播

**Files:**
- Modify: `app/src/main/java/com/jdhelper/app/service/FloatingStateManager.kt`

- [ ] **Step 1: 添加时间源切换广播常量和方法**

在 `FloatingStateManager.kt` 中添加：
```kotlin
// 新增常量
const val ACTION_TIME_SOURCE_CHANGED = "com.jdhelper.TIME_SOURCE_CHANGED"
const val EXTRA_TIME_SOURCE = "time_source"

// 新增方法
fun notifyTimeSourceChanged(source: TimeSource) {
    Log.d(TAG, "notifyTimeSourceChanged: source=$source")
    val intent = Intent(ACTION_TIME_SOURCE_CHANGED).apply {
        putExtra(EXTRA_TIME_SOURCE, source.name)
        setPackage(context.packageName)
    }
    context.sendBroadcast(intent)
}
```

- [ ] **Step 2: 添加 TimeSource import**

在文件顶部添加 import：
```kotlin
import com.jdhelper.data.local.TimeSource
```

- [ ] **Step 3: 提交更改**

---

### Task 2: 修改 FloatingMenuService 注册广播接收器

**Files:**
- Modify: `app/src/main/java/com/jdhelper/app/service/FloatingMenuService.kt:1-100` (文件开头部分)

- [ ] **Step 1: 添加 BroadcastReceiver 变量**

在 FloatingMenuService 类中添加：
```kotlin
private var stateReceiver: BroadcastReceiver? = null
```

- [ ] **Step 2: 添加 registerStateReceiver 方法**

在 FloatingMenuService 中添加方法：
```kotlin
private fun registerStateReceiver() {
    stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                FloatingStateManager.ACTION_NTP_SYNC_CHANGED -> {
                    val offset = intent.getLongExtra(FloatingStateManager.EXTRA_NTP_OFFSET, 0)
                    val synced = intent.getBooleanExtra(FloatingStateManager.EXTRA_NTP_SYNCED, false)
                    Log.d(TAG, "Received NTP sync changed: synced=$synced, offset=$offset")
                    updateNtpStatusDisplay()
                }
                FloatingStateManager.ACTION_REFRESH_TIME -> {
                    Log.d(TAG, "Received refresh time request")
                    updateNtpStatusDisplay()
                }
                FloatingStateManager.ACTION_TIME_SOURCE_CHANGED -> {
                    Log.d(TAG, "Received time source changed")
                    updateNtpStatusDisplay()
                }
            }
        }
    }

    val filter = IntentFilter().apply {
        addAction(FloatingStateManager.ACTION_NTP_SYNC_CHANGED)
        addAction(FloatingStateManager.ACTION_REFRESH_TIME)
        addAction(FloatingStateManager.ACTION_TIME_SOURCE_CHANGED)
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        registerReceiver(stateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
    } else {
        registerReceiver(stateReceiver, filter)
    }
}
```

- [ ] **Step 3: 在 onCreate 中调用 registerStateReceiver**

在 `onCreate()` 方法中添加调用：
```kotlin
// 注册广播接收器
registerStateReceiver()
```

- [ ] **Step 4: 在 onDestroy 中注销广播接收器**

在 `onDestroy()` 方法中添加：
```kotlin
// 注销广播接收器
stateReceiver?.let {
    try {
        unregisterReceiver(it)
    } catch (e: Exception) {
        Log.e(TAG, "注销广播接收器失败", e)
    }
}
```

- [ ] **Step 5: 添加必要的 import**

确保有以下 import：
```kotlin
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.os.Build
```

- [ ] **Step 6: 提交更改**

---

### Task 3: 修改 HomeViewModel 时间源切换时发送广播

**Files:**
- Modify: `app/src/main/java/com/jdhelper/app/ui/screens/home/HomeViewModel.kt:272-283`

- [ ] **Step 1: 修改 setTimeSource 方法添加广播**

在 `setTimeSource()` 方法中添加广播调用：
```kotlin
fun setTimeSource(source: TimeSource) {
    viewModelScope.launch {
        clickSettingsRepository.setTimeSource(source)
        _uiState.update { it.copy() }
        Log.d(TAG, "时间源已切换为: $source")

        // 切换到京东时间源时，自动同步京东时间
        if (source == TimeSource.JD) {
            syncJdTime()
        }

        // 通知所有悬浮窗时间源已切换
        floatingStateManager.notifyTimeSourceChanged(source)
        floatingStateManager.requestRefreshTime()
    }
}
```

- [ ] **Step 2: 提交更改**

---

### Task 4: 测试验证

- [ ] **Step 1: 构建项目验证无编译错误**

```bash
./gradlew assembleDebug
```

- [ ] **Step 2: 测试时间源切换**

1. 启动应用，确保悬浮时钟和悬浮菜单都显示
2. 切换时间源从 NTP 到 JD
3. 验证所有组件时间显示是否统一更新

- [ ] **Step 3: 测试时间同步**

1. 在 NTP 时间源下点击时间同步
2. 验证状态栏、悬浮菜单、悬浮时钟的偏移是否统一更新
3. 切换到 JD 时间源后点击时间同步
4. 验证所有组件偏移是否统一更新

---

## 验收标准

1. ✅ 时间源切换后，状态栏、悬浮菜单、悬浮时钟同时更新
2. ✅ NTP 时间同步后，所有组件显示的偏移值一致
3. ✅ JD 时间同步后，所有组件显示的偏移值一致
4. ✅ 广播接收器正确注册和注销，无内存泄漏

---

## 变更文件清单

| 文件 | 变更类型 |
|------|----------|
| `FloatingStateManager.kt` | 新增常量和方法 |
| `FloatingMenuService.kt` | 新增广播接收器注册 |
| `HomeViewModel.kt` | 新增时间源切换广播调用 |