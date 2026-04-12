# 悬浮窗状态实时同步实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现悬浮菜单与悬浮时钟状态实时同步，消除刷新延迟

**Architecture:** 使用BroadcastReceiver实现跨服务状态同步，FloatingStateManager作为状态中心管理所有状态变更

**Tech Stack:** Kotlin, Android Services, BroadcastReceiver, Coroutines

---

## 文件结构

| 文件 | 操作 | 职责 |
|------|------|------|
| `app/src/main/java/com/jdhelper/app/service/FloatingStateManager.kt` | 新建 | 状态管理中心，单例模式 |
| `app/src/main/java/com/jdhelper/app/service/NtpTimeService.kt` | 修改 | 确保跨服务共享 |
| `app/src/main/java/com/jdhelper/app/service/FloatingService.kt` | 修改 | 添加广播接收器实时更新 |
| `app/src/main/java/com/jdhelper/app/service/FloatingMenuService.kt` | 修改 | 状态变更时通知管理器 |

---

## Task 1: 创建 FloatingStateManager 状态管理中心

**Files:**
- Create: `app/src/main/java/com/jdhelper/app/service/FloatingStateManager.kt`

- [ ] **Step 1: 创建状态管理器类**

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

/**
 * 悬浮窗状态管理器 - 单例模式
 * 负责管理所有悬浮窗的共享状态，并通过广播通知状态变化
 */
@Singleton
class FloatingStateManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "FloatingStateManager"
        
        // Broadcast Actions
        const val ACTION_NTP_SYNC_CHANGED = "com.jdhelper.NTP_SYNC_CHANGED"
        const val ACTION_TASK_STATE_CHANGED = "com.jdhelper.TASK_STATE_CHANGED"
        const val ACTION_REFRESH_TIME = "com.jdhelper.REFRESH_TIME"
        
        // Extra Keys
        const val EXTRA_NTP_SYNCED = "ntp_synced"
        const val EXTRA_NTP_OFFSET = "ntp_offset"
        const val EXTRA_TASK_TYPE = "task_type"
        const val EXTRA_TASK_RUNNING = "task_running"
        
        // Task Types
        const val TASK_TYPE_LOOP = "loop"
        const val TASK_TYPE_GIFT = "gift"
        const val TASK_TYPE_TIMED = "timed"
        
        @Volatile
        private var instance: FloatingStateManager? = null
        
        fun getInstance(): FloatingStateManager {
            return instance ?: throw IllegalStateException("FloatingStateManager not initialized")
        }
        
        fun setInstance(manager: FloatingStateManager) {
            instance = manager
        }
    }
    
    // NTP同步状态
    private val _ntpSynced = MutableStateFlow(false)
    val ntpSynced: StateFlow<Boolean> = _ntpSynced.asStateFlow()
    
    private val _ntpOffset = MutableStateFlow(0L)
    val ntpOffset: StateFlow<Long> = _ntpOffset.asStateFlow()
    
    // 任务运行状态
    private val _loopRunning = MutableStateFlow(false)
    val loopRunning: StateFlow<Boolean> = _loopRunning.asStateFlow()
    
    private val _giftRunning = MutableStateFlow(false)
    val giftRunning: StateFlow<Boolean> = _giftRunning.asStateFlow()
    
    private val _timedRunning = MutableStateFlow(false)
    val timedRunning: StateFlow<Boolean> = _timedRunning.asStateFlow()
    
    init {
        setInstance(this)
    }
    
    /**
     * 通知NTP同步状态变化
     */
    fun notifyNtpSyncChanged(synced: Boolean, offset: Long) {
        Log.d(TAG, "notifyNtpSyncChanged: synced=$synced, offset=$offset")
        _ntpSynced.value = synced
        _ntpOffset.value = offset
        
        val intent = Intent(ACTION_NTP_SYNC_CHANGED).apply {
            putExtra(EXTRA_NTP_SYNCED, synced)
            putExtra(EXTRA_NTP_OFFSET, offset)
            setPackage(context.packageName)
        }
        context.sendBroadcast(intent)
    }
    
    /**
     * 通知任务状态变化
     */
    fun notifyTaskStateChanged(taskType: String, running: Boolean) {
        Log.d(TAG, "notifyTaskStateChanged: taskType=$taskType, running=$running")
        
        when (taskType) {
            TASK_TYPE_LOOP -> _loopRunning.value = running
            TASK_TYPE_GIFT -> _giftRunning.value = running
            TASK_TYPE_TIMED -> _timedRunning.value = running
        }
        
        val intent = Intent(ACTION_TASK_STATE_CHANGED).apply {
            putExtra(EXTRA_TASK_TYPE, taskType)
            putExtra(EXTRA_TASK_RUNNING, running)
            setPackage(context.packageName)
        }
        context.sendBroadcast(intent)
    }
    
    /**
     * 请求刷新时间显示
     */
    fun requestRefreshTime() {
        Log.d(TAG, "requestRefreshTime")
        val intent = Intent(ACTION_REFRESH_TIME).apply {
            setPackage(context.packageName)
        }
        context.sendBroadcast(intent)
    }
}
```

- [ ] **Step 2: 提交变更**

```bash
git add app/src/main/java/com/jdhelper/app/service/FloatingStateManager.kt
git commit -m "feat: add FloatingStateManager for real-time state sync"
```

---

## Task 2: 修改 FloatingService 添加广播接收器

**Files:**
- Modify: `app/src/main/java/com/jdhelper/app/service/FloatingService.kt`

- [ ] **Step 1: 添加BroadcastReceiver导入和变量**

在文件顶部添加导入：
```kotlin
import android.content.BroadcastReceiver
import android.content.IntentFilter
```

找到现有变量声明区域，添加：
```kotlin
// 状态接收器
private var stateReceiver: BroadcastReceiver? = null
```

- [ ] **Step 2: 添加广播接收器实现**

在 `showFloatingWindow()` 方法中，`windowManager.addView(floatingView, params)` 之前添加：

```kotlin
// 注册广播接收器
registerStateReceiver()

// 注册广播接收器
private fun registerStateReceiver() {
    stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                FloatingStateManager.ACTION_NTP_SYNC_CHANGED -> {
                    val offset = intent.getLongExtra(FloatingStateManager.EXTRA_NTP_OFFSET, 0)
                    val synced = intent.getBooleanExtra(FloatingStateManager.EXTRA_NTP_SYNCED, false)
                    Log.d(TAG, "Received NTP sync changed: synced=$synced, offset=$offset")
                    // 立即刷新时间显示
                    updateTimeDisplay()
                }
                FloatingStateManager.ACTION_TASK_STATE_CHANGED -> {
                    val taskType = intent.getStringExtra(FloatingStateManager.EXTRA_TASK_TYPE) ?: return
                    val running = intent.getBooleanExtra(FloatingStateManager.EXTRA_TASK_RUNNING, false)
                    Log.d(TAG, "Received task state changed: taskType=$taskType, running=$running")
                    // 可以添加任务状态指示更新
                }
                FloatingStateManager.ACTION_REFRESH_TIME -> {
                    Log.d(TAG, "Received refresh time request")
                    updateTimeDisplay()
                }
            }
        }
    }
    
    val filter = IntentFilter().apply {
        addAction(FloatingStateManager.ACTION_NTP_SYNC_CHANGED)
        addAction(FloatingStateManager.ACTION_TASK_STATE_CHANGED)
        addAction(FloatingStateManager.ACTION_REFRESH_TIME)
    }
    
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        registerReceiver(stateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
    } else {
        @Suppress("UnspecifiedRegisterReceiverFlag")
        registerReceiver(stateReceiver, filter)
    }
}
```

- [ ] **Step 3: 在 onDestroy 中取消注册**

修改 `onDestroy()` 方法：
```kotlin
override fun onDestroy() {
    super.onDestroy()
    // 取消注册广播接收器
    stateReceiver?.let {
        try {
            unregisterReceiver(it)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister receiver", e)
        }
    }
    stateReceiver = null
    
    hideFloatingWindow()
    timeUpdateJob?.cancel()
    instance = null
}
```

- [ ] **Step 4: 在 onCreate 中初始化 FloatingStateManager**

修改 `onCreate()` 方法，添加：
```kotlin
override fun onCreate() {
    super.onCreate()
    instance = this
    windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    createNotificationChannel()
    
    // 初始化状态管理器
    try {
        FloatingStateManager.getInstance()
    } catch (e: Exception) {
        Log.e(TAG, "FloatingStateManager not initialized yet", e)
    }
    
    // 这里不再创建独立的NtpTimeService，而是使用共享的单例
    ntpTimeService = NtpTimeService(this)  // 保持兼容，但时间源会被共享
}
```

- [ ] **Step 5: 提交变更**

```bash
git add app/src/main/java/com/jdhelper/app/service/FloatingService.kt
git commit -m "feat: add broadcast receiver to FloatingService for real-time state sync"
```

---

## Task 3: 修改 FloatingMenuService 通知状态变化

**Files:**
- Modify: `app/src/main/java/com/jdhelper/app/service/FloatingMenuService.kt`

- [ ] **Step 1: 添加 FloatingStateManager 注入**

找到现有的 `@Inject` 字段区域，添加：
```kotlin
@Inject
lateinit var floatingStateManager: FloatingStateManager
```

- [ ] **Step 2: 修改时钟按钮点击事件**

找到 `btn_clock` 的点击事件（大约第375行），修改同步成功后添加状态通知：

```kotlin
// 时钟按钮 - NTP同步并显示/刷新悬浮时钟
floatingView?.findViewById<ImageButton>(R.id.btn_clock)?.setOnClickListener {
    CoroutineScope(Dispatchers.Main).launch {
        try {
            // 1. NTP校准时间
            val synced = withContext(Dispatchers.IO) {
                ntpTimeService.syncTime()
            }
            if (!synced) {
                ToastUtils.show(this@FloatingMenuService, "时间同步失败，使用本地时间")
            }

            // 同步后更新NTP状态显示（原有逻辑）
            updateNtpStatusDisplay()
            
            // 通知状态管理器刷新（新增）
            val offset = ntpTimeService.getTimeOffset()
            floatingStateManager.notifyNtpSyncChanged(synced, offset)
            floatingStateManager.requestRefreshTime()

            // 2. 检查悬浮时钟是否已显示，如果是则刷新，否则启动
            if (com.jdhelper.service.FloatingService.isRunning()) {
                // 直接发送刷新请求，不再停止再启动
                floatingStateManager.requestRefreshTime()
            } else {
                com.jdhelper.service.FloatingService.startService(this@FloatingMenuService)
            }

            // 3. 更新时钟按钮图标（立即切换）
            updateClockButtonState()

            ToastUtils.show(this@FloatingMenuService, "时间已刷新")
        } catch (e: Exception) {
            Log.e(TAG, "时钟控制失败", e)
            ToastUtils.show(this@FloatingMenuService, "操作失败: ${e.message}")
        }
    }
}
```

- [ ] **Step 3: 修改锁按钮（循环任务）状态通知**

找到 `btn_lock` 点击事件中，启动任务后添加：

```kotlin
// 在 isLoopRunning = true 之后添加
floatingStateManager.notifyTaskStateChanged(FloatingStateManager.TASK_TYPE_LOOP, true)
```

停止任务后添加：
```kotlin
// 在 isLoopRunning = false 之后添加
floatingStateManager.notifyTaskStateChanged(FloatingStateManager.TASK_TYPE_LOOP, false)
```

- [ ] **Step 4: 修改礼物按钮状态通知**

找到 `btn_gift` 点击事件中：
- 启动后添加：`floatingStateManager.notifyTaskStateChanged(FloatingStateManager.TASK_TYPE_GIFT, true)`
- 停止后添加：`floatingStateManager.notifyTaskStateChanged(FloatingStateManager.TASK_TYPE_GIFT, false)`

- [ ] **Step 5: 修改定时按钮状态通知**

找到 `btn_play` 点击事件中：
- 启动后添加：`floatingStateManager.notifyTaskStateChanged(FloatingStateManager.TASK_TYPE_TIMED, true)`
- 任务完成后添加：`floatingStateManager.notifyTaskStateChanged(FloatingStateManager.TASK_TYPE_TIMED, false)`

- [ ] **Step 6: 修改停止按钮状态通知**

找到 `btn_stop` 点击事件，停止所有任务后添加：
```kotlin
floatingStateManager.notifyTaskStateChanged(FloatingStateManager.TASK_TYPE_LOOP, false)
floatingStateManager.notifyTaskStateChanged(FloatingStateManager.TASK_TYPE_GIFT, false)
floatingStateManager.notifyTaskStateChanged(FloatingStateManager.TASK_TYPE_TIMED, false)
```

- [ ] **Step 7: 提交变更**

```bash
git add app/src/main/java/com/jdhelper/app/service/FloatingMenuService.kt
git commit -m "feat: add state change notifications to FloatingMenuService"
```

---

## Task 4: 构建验证

**Files:**
- (无新文件)

- [ ] **Step 1: 清理并构建**

```bash
cd D:/Workspace/Auto/auto_clicker
./gradlew clean assembleDebug
```

预期结果：编译成功，无错误

- [ ] **Step 2: 提交变更**

```bash
git add .
git commit -m "feat: complete floating state real-time sync implementation"
```

---

## 验收清单

### 时间同步验收
- [ ] 点击悬浮菜单时钟按钮后，悬浮时钟立即刷新（无1.5秒延迟）
- [ ] NTP同步状态在两个悬浮窗同时更新

### 任务状态同步验收
- [ ] 启动循环任务后，状态实时同步
- [ ] 启动礼物任务后，状态实时同步
- [ ] 启动定时任务后，状态实时同步

### 性能验收
- [ ] 状态同步延迟 < 100ms
- [ ] 点击时钟按钮到UI更新 < 200ms