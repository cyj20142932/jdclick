# 时间源统一管理设计文档

**日期**: 2026-04-12
**主题**: NTP/京东时间源统一管理架构重构

## 1. 背景与目标

### 1.1 当前问题

当前实现存在以下问题：

1. **广播方式复杂**: 时间源切换时需要通过 `FloatingStateManager` 发送多个广播
   - `ACTION_NTP_SYNC_CHANGED`
   - `ACTION_TIME_SOURCE_CHANGED`
   - `ACTION_REFRESH_TIME`

2. **代码耦合严重**:
   - `FloatingService` 需要手动注册广播接收器
   - 每个组件都需要处理广播，逻辑分散

3. **维护困难**: 每次修改时间相关逻辑都要改多处代码

4. **容易出现 bug**: 广播可能丢失，导致状态不一致

### 1.2 目标

1. **NTP 和京东时间完全独立**: 两者分别维护自己的状态和同步逻辑
2. **统一接口对外暴露**: 通过 `TimeService` 接口提供一致的方法
3. **通过 Flow 观察变化**: 所有组件通过 StateFlow 观察时间变化，完全消除广播依赖
4. **引入 TimeViewModel**: 使用 MVVM 架构集中管理时间状态

## 2. 架构设计

### 2.1 整体架构

```
┌─────────────────────────────────────────────────────────────────┐
│                         TimeViewModel                          │
│  ┌─────────────────┐  ┌─────────────────┐  ┌────────────────┐  │
│  │ currentTimeFlow │  │  timeSourceFlow │  │  timeOffsetFlow│  │
│  │   (10ms 更新)    │  │                 │  │                │  │
│  └────────┬────────┘  └────────┬────────┘  └───────┬────────┘  │
└───────────│────────────────────│───────────────────│───────────┘
            │                    │                    │
            ▼                    ▼                    ▼
┌─────────────────────────────────────────────────────────────────┐
│                        TimeService                              │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │ getCurrentTime(): Long                                      ││
│  │ getTimeOffset(): Long                                       ││
│  │ getOffsetText(): String                                     ││
│  │ syncTime(): Boolean                                         ││
│  │ getCurrentTimeSource(): TimeSource                          ││
│  │ isSynced(): Boolean                                         ││
│  │ observeTimeSource(): Flow<TimeSource>                       ││
│  └─────────────────────────────────────────────────────────────┘│
└───────────│────────────────────│───────────────────│───────────┘
            │                    │                    │
            ▼                    ▼                    ▼
┌─────────────────────┐  ┌─────────────────────┐  ┌─────────────┐
│   NtpTimeService    │  │   JdTimeService     │  │ Repository  │
│   (独立实现)         │  │   (独立实现)        │  │ (设置存储)  │
└─────────────────────┘  └─────────────────────┘  └─────────────┘
```

### 2.2 组件职责

| 组件 | 职责 |
|------|------|
| `TimeViewModel` | 集中管理时间状态，暴露 StateFlow 供其他组件观察 |
| `TimeService` | 接口定义，统一获取时间的 API |
| `DefaultTimeService` | 接口实现，根据 TimeSource 路由到对应实现 |
| `NtpTimeService` | NTP 时间同步和维护 |
| `JdTimeService` | 京东时间同步和维护 |
| `ClickSettingsRepository` | 时间源设置存储 |

## 3. TimeViewModel 设计

### 3.1 类定义

```kotlin
@HiltViewModel
class TimeViewModel @Inject constructor(
    private val timeService: TimeService
) : ViewModel() {

    // ==================== 公开的 StateFlow ====================

    /**
     * 当前时间 - 每 10ms 更新一次
     * 所有需要获取当前时间的组件都应该观察这个 Flow
     */
    val currentTime: StateFlow<Long>

    /**
     * 当前使用的时间源 (NTP 或 JD)
     */
    val timeSource: StateFlow<TimeSource>

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

    // ==================== 方法 ====================

    /**
     * 同步当前时间源的时间
     * @return 同步是否成功
     */
    suspend fun syncTime(): Boolean

    /**
     * 切换时间源并自动同步
     */
    suspend fun switchTimeSource(source: TimeSource): Boolean
}
```

### 3.2 内部实现

```kotlin
@HiltViewModel
class TimeViewModel @Inject constructor(
    private val timeService: TimeService,
    private val clickSettingsRepository: ClickSettingsRepository
) : ViewModel() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // 观察时间源变化
    val timeSource: StateFlow<TimeSource> = clickSettingsRepository.getTimeSource()
        .stateIn(scope, SharingStarted.Eagerly, TimeSource.NTP)

    // 观察当前时间 - 使用 timer 每 10ms 更新
    val currentTime: StateFlow<Long> = flow {
        while (true) {
            emit(timeService.getCurrentTime())
            delay(10)
        }
    }.stateIn(scope, SharingStarted.Eagerly, System.currentTimeMillis())

    // 时间偏移
    val timeOffset: StateFlow<Long> = timeSource.map { source ->
        timeService.getTimeOffset()
    }.stateIn(scope, SharingStarted.Eagerly, 0L)

    // 偏移文本
    val offsetText: StateFlow<String> = timeOffset.map { offset ->
        if (offset >= 0) "+${offset}ms" else "${offset}ms"
    }.stateIn(scope, SharingStarted.Eagerly, "--ms")

    // 是否已同步
    val isSynced: StateFlow<Boolean> = combine(timeSource, timeOffset) { source, _ ->
        timeService.isSynced()
    }.stateIn(scope, SharingStarted.Eagerly, false)

    // ==================== 方法实现 ====================

    suspend fun syncTime(): Boolean {
        return timeService.syncTime()
    }

    suspend fun switchTimeSource(source: TimeSource): Boolean {
        clickSettingsRepository.setTimeSource(source)

        // 如果切换到京东时间，自动同步
        return if (source == TimeSource.JD) {
            timeService.syncTime()
        } else {
            true
        }
    }
}
```

## 4. 需要修改的组件

### 4.1 FloatingService

**当前问题**:
- 手动注册广播接收器处理时间变化
- 内部维护 `currentTimeSource` 和 `lastJdSyncTime` 状态

**修改方案**:
1. 移除广播接收器 `registerStateReceiver()`
2. 注入 `TimeViewModel`
3. 通过 `timeViewModel.currentTime` 观察时间变化

```kotlin
// 修改后
@Inject
lateinit var timeViewModel: TimeViewModel

// 在 startTimeUpdate() 中
private fun startTimeUpdate() {
    timeUpdateJob = CoroutineScope(Dispatchers.Main).launch {
        timeViewModel.currentTime.collect { time ->
            updateTimeDisplay(time)
        }
    }
}
```

### 4.2 FloatingMenuService

同样修改：
1. 移除广播接收器
2. 使用 `TimeViewModel` 获取时间

### 4.3 HomeViewModel

**当前问题**:
- 同时注入 `NtpTimeService`、`JdTimeService`、`TimeService`
- 手动处理时间同步逻辑

**修改方案**:
1. 只注入 `TimeViewModel`
2. 时间显示直接使用 `TimeViewModel.currentTime`

```kotlin
// 修改后
@Inject
lateinit var timeViewModel: TimeViewModel

// 时间显示
val currentTime: StateFlow<String> = timeViewModel.currentTime
    .map { time ->
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        sdf.format(Date(time))
    }
    .stateIn(viewModelScope, SharingStarted.Eagerly, "00:00:00")

val millis: StateFlow<String> = timeViewModel.currentTime
    .map { time ->
        SimpleDateFormat("SSS", Locale.getDefault()).format(Date(time))
    }
    .stateIn(viewModelScope, SharingStarted.Eagerly, "000")
```

### 4.4 TimedClickManager

**当前问题**:
- 直接注入 `TimeService`
- 每次需要时间时调用 `timeService.getCurrentTime()`

**修改方案**:
- 可以继续使用 `TimeService`（因为是同步调用，要求低延迟）
- 或者使用 `TimeViewModel.currentTime.value` 获取最新时间

### 4.5 SettingsViewModel

修改为使用 `TimeViewModel`：
- 时间源设置通过 `timeViewModel.switchTimeSource()` 处理

## 5. 广播清理

### 5.1 待移除的广播

| 广播 Action | 用途 | 替代方案 |
|------------|------|----------|
| `ACTION_NTP_SYNC_CHANGED` | NTP 同步状态变化 | `TimeViewModel.isSynced` Flow |
| `ACTION_TIME_SOURCE_CHANGED` | 时间源变化 | `TimeViewModel.timeSource` Flow |
| `ACTION_REFRESH_TIME` | 刷新时间显示 | `TimeViewModel.currentTime` Flow |

### 5.2 FloatingStateManager 清理

`FloatingStateManager` 中移除以下方法：
- `notifyNtpSyncChanged()`
- `notifyTimeSourceChanged()`
- `requestRefreshTime()`

保留方法：
- `notifyTaskStateChanged()` - 任务状态变化仍然需要广播

## 6. 数据流说明

### 6.1 时间获取流程

```
1. 组件调用 timeViewModel.currentTime.collect { time ->
2.     // 收到每 10ms 更新的时间
3. }
```

### 6.2 时间源切换流程

```
1. 用户在设置中选择时间源
2. 调用 timeViewModel.switchTimeSource(TimeSource.JD)
3. TimeViewModel 更新 ClickSettingsRepository
4. 所有观察 timeViewModel.timeSource 的组件自动收到通知
5. TimeViewModel 自动同步新时间源
```

### 6.3 时间同步流程

```
1. 用户点击同步按钮
2. 调用 timeViewModel.syncTime()
3. TimeService 根据当前时间源调用对应服务的 sync 方法
4. 同步完成后，TimeViewModel 的 currentTime 会自动更新
5. 所有观察 currentTime 的组件自动收到通知
```

## 7. 测试计划

### 7.1 单元测试

- `TimeViewModel` 状态流测试
- `TimeService` 路由逻辑测试
- `NtpTimeService` / `JdTimeService` 独立测试

### 7.2 集成测试

- 时间源切换后，所有组件是否正确使用新时间源
- 精度测试：定时点击是否准确

### 7.3 手动测试

- 切换到 NTP 时间，验证悬浮窗显示 NTP 时间
- 切换到京东时间，验证悬浮窗显示京东时间
- 杀死应用后重新打开，时间源设置是否保留

## 8. 风险与缓解

| 风险 | 缓解措施 |
|------|----------|
| 10ms 更新频率可能导致性能问题 | 使用 `stateIn` 共享flow，避免重复创建 |
| ViewModel 生命周期管理 | 使用 `@HiltViewModel` 依赖注入，确保正确销毁 |
| 多实例问题 | `TimeViewModel` 使用 `@Singleton` 或通过 Activity 作用域管理 |

## 9. 实施步骤

1. 创建 `TimeViewModel` 类
2. 修改 `DefaultTimeService` 添加必要的方法（如需要）
3. 修改 `FloatingService` 使用 `TimeViewModel`
4. 修改 `FloatingMenuService` 使用 `TimeViewModel`
5. 修改 `HomeViewModel` 使用 `TimeViewModel`
6. 清理 `FloatingStateManager` 中的广播方法
7. 测试验证

---

**设计完成，等待用户审批后开始实施。**