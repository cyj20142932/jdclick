# JDHelper 悬浮窗状态实时同步设计方案

**日期：** 2026-04-11
**主题：** 悬浮菜单与悬浮时钟状态实时同步
**问题修复：** 点击悬浮菜单时钟按钮延迟大、NTP状态不同步、任务状态不同步

---

## 1. 问题分析

### 1.1 当前问题

| 问题 | 根因 | 影响 |
|------|------|------|
| 时钟刷新延迟 | `FloatingService.refreshService()` 需停止→等待1.5秒→重启 | 点击后延迟1.5秒以上 |
| NTP状态不同步 | `FloatingService` 创建独立 `NtpTimeService` 实例 | 两个悬浮窗时间源不同 |
| 任务状态不同步 | 两个服务独立运行，无状态共享机制 | 悬浮菜单任务状态变化不实时反映到悬浮时钟 |

### 1.2 架构现状

```
FloatingMenuService          FloatingService
       ↓                            ↓
注入的 NtpTimeService    →    独立创建的 NtpTimeService
(NTP单例，共享状态)           (独立实例，状态隔离)
```

---

## 2. 解决方案

### 2.1 设计目标

- **NTP时间同步** - 两个悬浮窗使用同一个NTP时间源
- **状态实时同步** - NTP同步状态、任务运行状态实时同步到所有悬浮窗
- **无延迟刷新** - 通过广播机制通知刷新，无需重启服务

### 2.2 架构设计

```
┌─────────────────────────────────────────────────────────────────┐
│                    FloatingStateManager (单例)                  │
│  - isNtpSynced: Boolean                                         │
│  - ntpOffset: Long                                              │
│  - loopRunning: Boolean                                         │
│  - giftRunning: Boolean                                         │
│  - timedRunning: Boolean                                        │
│                                                                 │
│  notifyNtpSyncChanged(synced: Boolean, offset: Long)          │
│  notifyTaskStateChanged(task: TaskType, running: Boolean)      │
└─────────────────────────────────────────────────────────────────┘
          ↑                                           ↑
          │              BroadcastReceiver            │
    ┌─────┴─────┐         (实时状态更新)         ┌─────┴─────┐
    │           │                               │           │
 FloatingMenuService                      FloatingService
    │           │                               │           │
 更新状态 → 广播 ──────────────────────────────→ 接收广播 → 更新UI
```

### 2.3 Broadcast Action 定义

| Action | 用途 | Extra参数 |
|--------|------|-----------|
| `com.jdhelper.NTP_SYNC_CHANGED` | NTP同步状态变化 | `synced` (Boolean), `offset` (Long) |
| `com.jdhelper.TASK_STATE_CHANGED` | 任务状态变化 | `taskType` (String), `running` (Boolean) |
| `com.jdhelper.REFRESH_TIME` | 刷新时间显示 | (无) |

---

## 3. 技术实现

### 3.1 新增文件

| 文件 | 职责 |
|------|------|
| `FloatingStateManager.kt` | 状态管理中心，单例模式 |
| `FloatingStateReceiver.kt` | 广播接收器基类 |

### 3.2 修改文件

| 文件 | 改动 |
|------|------|
| `FloatingService.kt` | 使用共享NTP单例 + 注册广播接收器实时更新 |
| `FloatingMenuService.kt` | 状态变化时调用状态管理器通知 |
| `NtpTimeService.kt` | 改为单例注入，确保跨服务共享 |

### 3.3 关键代码

**FloatingStateManager：**
```kotlin
@Singleton
class FloatingStateManager @Inject constructor() {
    
    // NTP同步状态
    private val _ntpSynced = MutableStateFlow(false)
    val ntpSynced: StateFlow<Boolean> = _ntpSynced.asStateFlow()
    
    private val _ntpOffset = MutableStateFlow(0L)
    val ntpOffset: StateFlow<Long> = _ntpOffset.asStateFlow()
    
    // 任务运行状态
    private val _taskStates = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val taskStates: StateFlow<Map<String, Boolean>> = _taskStates.asStateFlow()
    
    fun notifyNtpSyncChanged(context: Context, synced: Boolean, offset: Long) {
        _ntpSynced.value = synced
        _ntpOffset.value = offset
        
        // 发送广播通知所有悬浮窗
        val intent = Intent(ACTION_NTP_SYNC_CHANGED).apply {
            putExtra(EXTRA_NTP_SYNCED, synced)
            putExtra(EXTRA_NTP_OFFSET, offset)
            setPackage(context.packageName)
        }
        context.sendBroadcast(intent)
    }
    
    fun notifyTaskStateChanged(context: Context, taskType: String, running: Boolean) {
        val current = _taskStates.value.toMutableMap()
        current[taskType] = running
        _taskStates.value = current
        
        val intent = Intent(ACTION_TASK_STATE_CHANGED).apply {
            putExtra(EXTRA_TASK_TYPE, taskType)
            putExtra(EXTRA_TASK_RUNNING, running)
            setPackage(context.packageName)
        }
        context.sendBroadcast(intent)
    }
}
```

**FloatingService 广播接收器：**
```kotlin
private val stateReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        when (intent?.action) {
            ACTION_NTP_SYNC_CHANGED -> {
                val offset = intent.getLongExtra(EXTRA_NTP_OFFSET, 0)
                updateNtpOffsetDisplay(offset)
            }
            ACTION_TASK_STATE_CHANGED -> {
                // 更新任务状态指示
            }
            ACTION_REFRESH_TIME -> {
                // 立即刷新时间显示
                updateTimeDisplay()
            }
        }
    }
}
```

---

## 4. 验收标准

### 4.1 NTP同步验收

- [ ] 点击悬浮菜单时钟按钮后，悬浮时钟立即刷新（无1.5秒延迟）
- [ ] NTP同步状态在两个悬浮窗同时更新
- [ ] 时间偏差(offset)同时显示在两个悬浮窗

### 4.2 任务状态同步验收

- [ ] 启动循环任务后，悬浮菜单和悬浮时钟的状态指示同时变化
- [ ] 启动礼物任务后，状态实时同步
- [ ] 启动定时任务后，状态实时同步

### 4.3 性能验收

- [ ] 状态同步延迟 < 100ms
- [ ] 点击时钟按钮到UI更新 < 200ms
- [ ] 无明显卡顿或ANR

---

## 5. 后续计划

本方案解决实时同步问题后，可进一步优化：
- 悬浮菜单和悬浮时钟合并为单一悬浮窗（可选）
- 添加省电模式（减少刷新频率）