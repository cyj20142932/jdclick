# 主页时间同步实时刷新设计方案

**日期：** 2026-04-11
**主题：** HomeScreen时间同步后实时刷新所有组件
**问题修复：** 点击时间同步后延迟1.5秒以上的问题

---

## 1. 问题分析

### 1.1 当前问题

| 问题 | 根因 | 影响 |
|------|------|------|
| 时间同步延迟 | `FloatingService.refreshService()` 需停止→等待1.5秒→重启 | 点击后延迟1.5秒以上 |
| 状态不同步 | HomeViewModel 未使用 FloatingStateManager | 各组件无法实时响应 |

### 1.2 当前架构

```
HomeViewModel.syncNtpTime()
    ↓
FloatingService.refreshService()  ← 问题：停止→等待1.5秒→重启
    ↓
延迟 1500ms+
```

### 1.3 已有基础设施

- `FloatingStateManager` - 状态管理中心，支持广播通知
- `FloatingService` - 已注册广播接收器，收到 `ACTION_REFRESH_TIME` 会即时刷新
- `FloatingMenuService` - 已集成 FloatingStateManager

---

## 2. 解决方案

### 2.1 设计目标

- **即时刷新** - 时间同步后，所有组件在100ms内更新显示
- **无重启** - 不再调用 `refreshService()`，改用广播通知
- **统一状态** - HomeViewModel、FloatingService、FloatingMenuService 状态实时同步

### 2.2 架构设计

```
┌─────────────────────────────────────────────────────────────────┐
│                    HomeViewModel                                │
│  1. NTP同步                                                      │
│  2. FloatingStateManager.notifyNtpSyncChanged()                │
│  3. FloatingStateManager.requestRefreshTime()                 │
└─────────────────────────────────────────────────────────────────┘
                              ↓ 广播
              ┌───────────────┼───────────────┐
              ↓               ↓               ↓
    FloatingService    FloatingMenuService    (HomeViewModel已更新)
    即时刷新UI          即时刷新UI
```

### 2.3 Broadcast Action

| Action | 用途 | Extra参数 |
|--------|------|-----------|
| `com.jdhelper.NTP_SYNC_CHANGED` | NTP同步状态变化 | `synced` (Boolean), `offset` (Long) |
| `com.jdhelper.REFRESH_TIME` | 刷新时间显示 | (无) |

---

## 3. 技术实现

### 3.1 修改文件

| 文件 | 改动 |
|------|------|
| `HomeViewModel.kt` | 注入 FloatingStateManager，修改 syncNtpTime() 使用广播刷新 |

### 3.2 关键代码

**HomeViewModel 修改：**

```kotlin
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val ntpTimeService: NtpTimeService,
    private val clickSettingsRepository: ClickSettingsRepository,
    private val floatingStateManager: FloatingStateManager,  // 新增
    application: Application
) : ViewModel() {
    // ...
    
    suspend fun syncNtpTime(): Boolean {
        Log.d(TAG, "开始同步NTP时间...")
        _uiState.update { it.copy(isNtpSyncing = true) }

        try {
            val success = ntpTimeService.syncTime()

            if (success) {
                val syncTime = ntpTimeService.getLastSyncTime()
                val format = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                val timeText = format.format(Date(syncTime))

                // 计算时间偏差
                val offset = ntpTimeService.getTimeOffset()
                val offsetText = if (offset >= 0) "+${offset}ms" else "${offset}ms"

                _uiState.update { it.copy(ntpLastSyncTime = timeText, ntpTimeOffset = offsetText, isNtpSyncing = false) }
                // 更新偏差显示
                _ntpOffset.value = offsetText
                Log.d(TAG, "NTP同步成功: $timeText, 偏差: $offsetText")

                // 新增：通知状态变化，广播给所有悬浮窗
                floatingStateManager.notifyNtpSyncChanged(success, offset)
                floatingStateManager.requestRefreshTime()
                
                Log.d(TAG, "已发送广播通知刷新")
            } else {
                _uiState.update { it.copy(ntpLastSyncTime = "同步失败", isNtpSyncing = false) }
                Log.w(TAG, "NTP同步失败")
            }

            return success
        } catch (e: Exception) {
            Log.e(TAG, "NTP同步异常", e)
            _uiState.update { it.copy(ntpLastSyncTime = "同步失败", isNtpSyncing = false) }
            return false
        }
    }
}
```

---

## 4. 验收标准

### 4.1 功能验收

- [ ] 点击HomeScreen"时间同步"按钮后，状态栏立即刷新（无1.5秒延迟）
- [ ] 悬浮时钟实时刷新显示
- [ ] 悬浮菜单实时刷新显示
- [ ] NTP同步状态在所有组件同步显示

### 4.2 性能验收

- [ ] 状态同步延迟 < 100ms
- [ ] 点击同步按钮到UI更新 < 200ms

---

## 5. 后续计划

本方案解决同步刷新问题后，可进一步优化：
- 添加自动NTP同步定时任务（可选）
- 优化FloatingStateManager的内存占用（可选）