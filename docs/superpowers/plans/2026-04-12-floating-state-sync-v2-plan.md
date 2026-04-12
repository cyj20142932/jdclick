# 悬浮窗状态同步 V2 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复时间源切换和时间同步时，状态栏、悬浮菜单、悬浮时钟不统一的问题

**Architecture:** 为 FloatingMenuService 添加广播接收器，与 FloatingService 保持一致的状态更新机制。添加时间源切换广播，确保所有组件同步响应状态变化。

**Tech Stack:** Android BroadcastReceiver, Kotlin Coroutines, Hilt DI

---

## 变更文件清单

| 文件 | 变更类型 |
|------|----------|
| `FloatingStateManager.kt` | 新增常量和方法 |
| `FloatingMenuService.kt` | 新增广播接收器注册 |
| `HomeViewModel.kt` | 新增时间源切换广播调用 |

---

### Task 1: 修改 FloatingStateManager 添加时间源切换广播

**Files:**
- Modify: `app/src/main/java/com/jdhelper/app/service/FloatingStateManager.kt:24-28` (companion object 中的常量定义区域)
- Modify: `app/src/main/java/com/jdhelper/app/service/FloatingStateManager.kt:1-15` (import 区域)

- [ ] **Step 1: 添加 import**

在 `FloatingStateManager.kt` 文件顶部添加 import：
```kotlin
import com.jdhelper.data.local.TimeSource
```

查找文件开头，确认当前 import 区域：
```kotlin
package com.jdhelper.app.service

import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
```

在 `import android.util.Log` 后添加 `import com.jdhelper.data.local.TimeSource`

Run: 
```bash
# 读取当前 import 区域
head -20 app/src/main/java/com/jdhelper/app/service/FloatingStateManager.kt
```
Expected: 显示当前文件开头，确认 import 位置

- [ ] **Step 2: 添加时间源切换广播常量**

在 companion object 中添加常量。查找第 24-28 行：
```kotlin
        // Broadcast Actions
        const val ACTION_NTP_SYNC_CHANGED = "com.jdhelper.NTP_SYNC_CHANGED"
        const val ACTION_TASK_STATE_CHANGED = "com.jdhelper.TASK_STATE_CHANGED"
        const val ACTION_REFRESH_TIME = "com.jdhelper.REFRESH_TIME"
```

在 `ACTION_REFRESH_TIME` 后添加：
```kotlin
        const val ACTION_TIME_SOURCE_CHANGED = "com.jdhelper.TIME_SOURCE_CHANGED"
        const val EXTRA_TIME_SOURCE = "time_source"
```

- [ ] **Step 3: 添加 notifyTimeSourceChanged 方法**

查找文件末尾，在现有方法后添加新方法：
```kotlin
    /**
     * 通知时间源变化
     */
    fun notifyTimeSourceChanged(source: TimeSource) {
        Log.d(TAG, "notifyTimeSourceChanged: source=$source")
        val intent = Intent(ACTION_TIME_SOURCE_CHANGED).apply {
            putExtra(EXTRA_TIME_SOURCE, source.name)
            setPackage(context.packageName)
        }
        context.sendBroadcast(intent)
    }
```

- [ ] **Step 4: 验证编译**

Run:
```bash
./gradlew :app:compileDebugKotlin 2>&1 | tail -20
```
Expected: 编译成功，无错误

- [ ] **Step 5: 提交更改**

Run:
```bash
git add app/src/main/java/com/jdhelper/app/service/FloatingStateManager.kt
git commit -m "feat: add time source changed broadcast in FloatingStateManager"
```

---

### Task 2: 修改 FloatingMenuService 注册广播接收器

**Files:**
- Modify: `app/src/main/java/com/jdhelper/app/service/FloatingMenuService.kt:50-70` (类变量区域)
- Modify: `app/src/main/java/com/jdhelper/app/service/FloatingMenuService.kt:180-200` (onCreate 区域)
- Modify: `app/src/main/java/com/jdhelper/app/service/FloatingMenuService.kt` (onDestroy 区域)

- [ ] **Step 1: 添加 BroadcastReceiver 变量**

在 FloatingMenuService 类中查找变量声明区域（第 50-70 行附近），添加：
```kotlin
private var stateReceiver: BroadcastReceiver? = null
```

查找位置：在 `companion object` 之后，类的主体变量区域

- [ ] **Step 2: 添加 registerStateReceiver 方法**

在 FloatingMenuService 类中添加新方法。查找合适位置（在现有方法附近）：
```kotlin
    /**
     * 注册状态广播接收器
     */
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

查找 onCreate 方法，在合适位置添加调用：
```kotlin
// 注册广播接收器
registerStateReceiver()
```

在 onCreate 中找到 `Log.d(TAG, "onCreate: 初始化完成")` 之前添加

- [ ] **Step 4: 在 onDestroy 中注销广播接收器**

查找 onDestroy 方法，添加注销代码：
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

确保有以下 import（查找文件顶部）：
```kotlin
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.os.Build
```

如果缺少，添加它们

- [ ] **Step 6: 验证编译**

Run:
```bash
./gradlew :app:compileDebugKotlin 2>&1 | tail -30
```
Expected: 编译成功，无错误

- [ ] **Step 7: 提交更改**

Run:
```bash
git add app/src/main/java/com/jdhelper/app/service/FloatingMenuService.kt
git commit -m "feat: add broadcast receiver in FloatingMenuService for state sync"
```

---

### Task 3: 修改 HomeViewModel 时间源切换时发送广播

**Files:**
- Modify: `app/src/main/java/com/jdhelper/app/ui/screens/home/HomeViewModel.kt:272-290` (setTimeSource 方法区域)

- [ ] **Step 1: 修改 setTimeSource 方法添加广播**

查找 `setTimeSource` 方法，当前代码：
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
    }
}
```

修改为：
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

- [ ] **Step 2: 验证编译**

Run:
```bash
./gradlew :app:compileDebugKotlin 2>&1 | tail -30
```
Expected: 编译成功，无错误

- [ ] **Step 3: 提交更改**

Run:
```bash
git add app/src/main/java/com/jdhelper/app/ui/screens/home/HomeViewModel.kt
git commit -m "feat: send broadcast when time source changed in HomeViewModel"
```

---

### Task 4: 构建并验证

- [ ] **Step 1: 完整构建**

Run:
```bash
./gradlew assembleDebug 2>&1 | tail -50
```
Expected: 构建成功

- [ ] **Step 2: 测试场景**

测试时间源切换同步：
1. 启动应用，确保悬浮时钟和悬浮菜单都已显示
2. 在主界面点击 NTP/JD 开关切换时间源
3. 验证所有组件（状态栏、悬浮菜单、悬浮时钟）是否同时更新

测试时间同步同步：
1. 在 NTP 时间源下点击"时间同步"按钮
2. 验证状态栏、悬浮菜单、悬浮时钟的偏移是否统一更新
3. 切换到 JD 时间源后点击"时间同步"按钮
4. 验证所有组件偏移是否统一更新

---

## 验收标准

1. ✅ 时间源切换后，状态栏、悬浮菜单、悬浮时钟同时更新
2. ✅ NTP 时间同步后，所有组件显示的偏移值一致
3. ✅ JD 时间同步后，所有组件显示的偏移值一致
4. ✅ 广播接收器正确注册和注销，无内存泄漏