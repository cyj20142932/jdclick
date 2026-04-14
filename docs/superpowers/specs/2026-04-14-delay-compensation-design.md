# 延迟补偿统一到时间源设计

## 目标

将 `ClickSettings` 中存储的 `delayMillis` 延迟补偿集成到 `TimeService.getCurrentTime()` 方法中，使得所有使用该方法的地方都能自动获得延迟补偿，而无需在每个调用点单独处理。

## 背景

当前架构中，`delayMillis` 在 `TimedClickManager.calculateNextMinuteTime()` 中使用，将延迟加到下一个整分时刻上。这种方式存在以下问题：

1. **分散处理**：延迟逻辑只在定时点击时生效，其他使用 `getCurrentTime()` 的地方（如悬浮窗显示）无法获得延迟补偿
2. **维护困难**：如果未来有新的时间使用场景，需要重复添加延迟补偿逻辑
3. **语义不清晰**：时间服务返回的应该是"当前时间"，而延迟补偿应该由时间服务统一处理

## 设计方案

### 架构修改

```
┌─────────────────────────────────────────────────────────┐
│                  修改后的调用流程                        │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  调用方                                                │
│    │                                                   │
│    ▼                                                   │
│  timeService.getCurrentTime()  ──────────────────────┐ │
│    │                                                  │ │
│    ▼                                                  │ │
│  DefaultTimeService.getCurrentTime()                 │ │
│    │                                                  │ │
│    ├── 获取基础时间 (NTP/JD)                         │ │
│    ├── 加上 delayMillis 延迟补偿                     │ │
│    └── 返回补偿后的时间  ◄────────────────────────────┘ │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

### 修改点

#### 1. DefaultTimeService

修改 `DefaultTimeService`，在 `getCurrentTime()` 返回时加上延迟补偿：

```kotlin
class DefaultTimeService @Inject constructor(
    private val ntpTimeService: NtpTimeService,
    private val jdTimeService: JdTimeService,
    private val clickSettingsRepository: ClickSettingsRepository
) : TimeService {

    // 新增：缓存延迟补偿值
    @Volatile
    private var cachedDelayMillis: Double = 0.0

    init {
        // 观察延迟设置变化，更新缓存
        scope.launch {
            clickSettingsRepository.getClickSettings().collect { settings ->
                cachedDelayMillis = settings.delayMillis
                LogConsole.d(TAG, "延迟补偿已更新缓存: ${settings.delayMillis}ms")
            }
        }
    }

    override fun getCurrentTime(): Long {
        val source = cachedTimeSource
        val baseTime = when (source) {
            TimeSource.NTP -> ntpTimeService.getCurrentTime()
            TimeSource.JD -> jdTimeService.getCurrentJdTime()
        }
        // 加上延迟补偿
        return baseTime + cachedDelayMillis.toLong()
    }
}
```

#### 2. TimedClickManager

移除 `calculateNextMinuteTime()` 中的延迟逻辑，因为时间服务已经包含了延迟：

```kotlin
private fun calculateNextMinuteTime(ntpTime: Long): Long {
    val calendar = Calendar.getInstance().apply {
        timeInMillis = ntpTime
    }

    // 设置为下一分钟的00秒000毫秒
    calendar.add(Calendar.MINUTE, 1)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)

    // 不再在这里加上 delayMillis
    return calendar.timeInMillis
}
```

#### 3. ClickSettingsRepository

确保提供 `Flow<ClickSettings>` 用于观察设置变化：

```kotlin
interface ClickSettingsRepository {
    fun getClickSettings(): Flow<ClickSettings>
    // 现有方法...
}
```

## 影响范围

修改后，以下地方将自动获得延迟补偿：

| 文件 | 用途 | 影响 |
|------|------|------|
| `TimedClickManager` | 定时点击 | 延迟已在时间源中，无需额外处理 |
| `FloatingMenuService` | 悬浮窗时间显示 | 显示的时间将包含延迟补偿 |
| `TimeViewModel` | 时间页面 | 显示的时间将包含延迟补偿 |

## 兼容性考虑

- **向后兼容**：所有现有调用方无需修改，自动获得新行为
- **负数延迟**：支持负数延迟（即提前），系统时间会相应减少
- **时间源切换**：延迟补偿在切换时间源时仍然有效（缓存独立于时间源）

## 测试要点

1. 验证 NTP 时间源下 `getCurrentTime()` 返回值包含延迟
2. 验证京东时间源下 `getCurrentTime()` 返回值包含延迟
3. 验证切换时间源后延迟仍然生效
4. 验证负数延迟场景
5. 验证定时点击的精确度是否提升
6. 验证悬浮窗显示时间包含延迟补偿