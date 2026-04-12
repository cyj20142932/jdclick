# 统一时间服务设计

**日期**: 2026-04-12
**目标**: 修复京东时间源切换后的时间显示不一致和时间同步无反馈问题

## 1. 背景

当前项目中存在两套独立的时间服务：
- `NtpTimeService` - 处理 NTP 时间同步
- `JdTimeService` - 处理京东时间同步

各组件在获取当前时间时没有统一入口，导致：
1. 状态栏、悬浮时钟、抢购时间使用的时间源不统一
2. 切换到京东时间源后，部分组件仍使用 NTP 时间
3. 时间同步按钮在京东模式下无 UI 反馈

## 2. 目标

- 创建统一的 `TimeService`，根据当前设置的 `TimeSource` 自动返回对应时间
- 修复 UI 反馈问题，时间同步按钮在两种时间源下都有明确反馈
- 确保所有组件（状态栏、悬浮时钟、抢购逻辑）使用统一的当前时间

## 3. 方案：统一时间服务

### 3.1 创建 TimeService 接口和实现

```
TimeService (接口)
  └─ DefaultTimeService (实现)
       ├─ 依赖 NtpTimeService
       ├─ 依赖 JdTimeService
       ├─ 依赖 ClickSettingsRepository
       └─ getCurrentTime() → 根据 TimeSource 返回对应时间
```

### 3.2 核心方法设计

```kotlin
interface TimeService {
    // 获取当前时间（根据设置的 TimeSource 自动选择）
    fun getCurrentTime(): Long

    // 获取时间偏移（当前时间源相对于系统时间的偏移）
    fun getTimeOffset(): Long

    // 获取偏移显示文本
    fun getOffsetText(): String

    // 同步当前时间源的时间
    suspend fun syncTime(): Boolean

    // 获取当前使用的时间源
    fun getCurrentTimeSource(): TimeSource

    // 检查是否已同步
    fun isSynced(): Boolean
}
```

### 3.3 DefaultTimeService 实现逻辑

```kotlin
class DefaultTimeService @Inject constructor(
    private val ntpTimeService: NtpTimeService,
    private val jdTimeService: JdTimeService,
    private val clickSettingsRepository: ClickSettingsRepository
) : TimeService {

    override fun getCurrentTime(): Long {
        // 从 Repository 获取当前时间源设置
        val source = runBlocking { clickSettingsRepository.getTimeSource().first() }
        return when (source) {
            TimeSource.NTP -> ntpTimeService.getCurrentTime()
            TimeSource.JD -> jdTimeService.getCurrentJdTime()
        }
    }

    override fun getTimeOffset(): Long {
        val source = runBlocking { clickSettingsRepository.getTimeSource().first() }
        return when (source) {
            TimeSource.NTP -> ntpTimeService.getTimeOffset()
            TimeSource.JD -> jdTimeService.getJdOffset()
        }
    }

    // ... 其他方法类似
}
```

### 3.4 需要修改的文件

| 文件 | 修改内容 |
|------|---------|
| 新建 `TimeService.kt` | 接口定义 |
| 新建 `DefaultTimeService.kt` | 实现类 |
| 修改 `FloatingService.kt` | 使用 TimeService 替代直接调用 |
| 修改 `FloatingMenuService.kt` | 使用 TimeService 替代直接调用 |
| 修改 `TimedClickManager.kt` | 使用 TimeService 替代直接调用 |
| 修改 `HomeViewModel.kt` | 使用 TimeService，并添加 UI 反馈 |
| 修改 `ServiceModule.kt` | 添加 TimeService 的 DI 绑定 |

## 4. UI 反馈修复

### 4.1 问题描述
时间同步按钮在京东模式下点击后无任何反馈，用户不知道同步是否成功。

### 4.2 修复方案
在 `HomeViewModel.syncNtpTime()` 中根据当前时间源显示不同反馈：

```kotlin
suspend fun syncNtpTime(): Boolean {
    val currentSource = clickSettingsRepository.getTimeSource().first()
    return if (currentSource == TimeSource.JD) {
        // 京东时间同步
        val success = syncJdTime()
        if (success) {
            // 显示成功反馈
            _uiState.update { it.copy(ntpLastSyncTime = "JD: ${jdOffset.value}") }
        }
        success
    } else {
        // NTP 时间同步
        syncNtpTimeInternal()
    }
}
```

## 5. 实施步骤

1. **创建 TimeService 接口**
   - 定义统一的时间服务接口

2. **创建 DefaultTimeService 实现**
   - 实现 TimeService 接口
   - 根据 TimeSource 路由到对应服务

3. **添加依赖注入**
   - 在 ServiceModule 中添加 TimeService 绑定

4. **修改 FloatingService**
   - 注入 TimeService
   - 使用 TimeService.getCurrentTime() 替换原有调用

5. **修改 FloatingMenuService**
   - 注入 TimeService
   - 使用 TimeService 替换 ntpTimeService 调用
   - 同步时间逻辑也通过 TimeService 处理

6. **修改 TimedClickManager**
   - 注入 TimeService
   - 使用 TimeService.getCurrentTime() 替换原有调用

7. **修改 HomeViewModel**
   - 注入 TimeService
   - 更新 jdOffset 收集逻辑
   - 添加同步成功/失败 UI 反馈

## 6. 风险与注意事项

- 需要确保 TimeSource 设置变更能及时反映到 TimeService
- 需要处理两种时间源都未同步的边界情况
- 需要保留向后兼容性，不影响现有功能