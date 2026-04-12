# 统一时间服务实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 创建统一的 TimeService，根据设置的 TimeSource 自动返回 NTP 或京东时间，修复时间源切换后的显示不一致和 UI 反馈问题

**Architecture:** 创建 TimeService 接口和 DefaultTimeService 实现类，封装 NtpTimeService 和 JdTimeService，根据 TimeSource 路由到对应服务

**Tech Stack:** Kotlin, Hilt DI, Jetpack Compose

---

## 文件结构

| 文件 | 操作 |
|------|------|
| `app/src/main/java/com/jdhelper/app/service/TimeService.kt` | 创建 - 接口定义 |
| `app/src/main/java/com/jdhelper/app/service/DefaultTimeService.kt` | 创建 - 实现类 |
| `app/src/main/java/com/jdhelper/app/di/ServiceModule.kt` | 修改 - 添加 TimeService 绑定 |
| `app/src/main/java/com/jdhelper/app/service/FloatingService.kt` | 修改 - 使用 TimeService |
| `app/src/main/java/com/jdhelper/app/service/FloatingMenuService.kt` | 修改 - 使用 TimeService |
| `app/src/main/java/com/jdhelper/service/TimedClickManager.kt` | 修改 - 使用 TimeService |
| `app/src/main/java/com/jdhelper/app/ui/screens/home/HomeViewModel.kt` | 修改 - 使用 TimeService + UI 反馈 |

---

## Task 1: 创建 TimeService 接口

**Files:**
- Create: `app/src/main/java/com/jdhelper/app/service/TimeService.kt`

- [ ] **Step 1: 创建 TimeService 接口文件**

```kotlin
package com.jdhelper.app.service

import com.jdhelper.data.local.TimeSource
import kotlinx.coroutines.flow.Flow

/**
 * 统一时间服务接口
 * 根据设置的 TimeSource 自动返回对应时间
 */
interface TimeService {
    /**
     * 获取当前时间（根据设置的 TimeSource 自动选择）
     */
    fun getCurrentTime(): Long

    /**
     * 获取时间偏移（当前时间源相对于系统时间的偏移，毫秒）
     */
    fun getTimeOffset(): Long

    /**
     * 获取偏移显示文本，如 "+15ms" 或 "-23ms"
     */
    fun getOffsetText(): String

    /**
     * 同步当前时间源的时间
     * @return 同步是否成功
     */
    suspend fun syncTime(): Boolean

    /**
     * 获取当前使用的时间源
     */
    fun getCurrentTimeSource(): TimeSource

    /**
     * 检查当前时间源是否已同步
     */
    fun isSynced(): Boolean

    /**
     * 监听时间源变化
     */
    fun observeTimeSource(): Flow<TimeSource>
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/jdhelper/app/service/TimeService.kt
git commit -m "feat: add TimeService interface

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

## Task 2: 创建 DefaultTimeService 实现

**Files:**
- Create: `app/src/main/java/com/jdhelper/app/service/DefaultTimeService.kt`

- [ ] **Step 1: 创建 DefaultTimeService 实现类**

```kotlin
package com.jdhelper.app.service

import com.jdhelper.data.local.TimeSource
import com.jdhelper.domain.repository.ClickSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "DefaultTimeService"

/**
 * 默认时间服务实现
 * 根据 TimeSource 路由到 NTP 或京东时间服务
 */
@Singleton
class DefaultTimeService @Inject constructor(
    private val ntpTimeService: NtpTimeService,
    private val jdTimeService: JdTimeService,
    private val clickSettingsRepository: ClickSettingsRepository
) : TimeService {

    private val scope = CoroutineScope(Dispatchers.Main)

    override fun getCurrentTime(): Long {
        val source = getCurrentTimeSourceSync()
        return when (source) {
            TimeSource.NTP -> ntpTimeService.getCurrentTime()
            TimeSource.JD -> jdTimeService.getCurrentJdTime()
        }
    }

    override fun getTimeOffset(): Long {
        val source = getCurrentTimeSourceSync()
        return when (source) {
            TimeSource.NTP -> ntpTimeService.getTimeOffset()
            TimeSource.JD -> jdTimeService.getJdOffset()
        }
    }

    override fun getOffsetText(): String {
        val offset = getTimeOffset()
        return if (offset >= 0) "+${offset}ms" else "${offset}ms"
    }

    override suspend fun syncTime(): Boolean {
        val source = getCurrentTimeSourceSync()
        return when (source) {
            TimeSource.NTP -> ntpTimeService.syncTime()
            TimeSource.JD -> jdTimeService.syncJdTime()
        }
    }

    override fun getCurrentTimeSource(): TimeSource {
        return getCurrentTimeSourceSync()
    }

    private fun getCurrentTimeSourceSync(): TimeSource {
        return try {
            runBlocking {
                clickSettingsRepository.getTimeSource().first()
            }
        } catch (e: Exception) {
            TimeSource.NTP // 默认使用 NTP
        }
    }

    override fun isSynced(): Boolean {
        val source = getCurrentTimeSourceSync()
        return when (source) {
            TimeSource.NTP -> ntpTimeService.isSynced()
            TimeSource.JD -> jdTimeService.isSynced()
        }
    }

    override fun observeTimeSource(): Flow<TimeSource> {
        return clickSettingsRepository.getTimeSource()
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/jdhelper/app/service/DefaultTimeService.kt
git commit -m "feat: add DefaultTimeService implementation

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

## Task 3: 添加 TimeService 依赖注入

**Files:**
- Modify: `app/src/main/java/com/jdhelper/app/di/ServiceModule.kt:1-21`

- [ ] **Step 1: 修改 ServiceModule，添加 TimeService 绑定**

查看当前 ServiceModule 内容，然后修改：

```kotlin
package com.jdhelper.di

import android.content.Context
import com.jdhelper.app.service.DefaultTimeService
import com.jdhelper.app.service.NtpTimeService
import com.jdhelper.app.service.TimeService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ServiceModule {

    @Provides
    @Singleton
    fun provideNtpTimeService(@ApplicationContext context: Context): NtpTimeService {
        return NtpTimeService(context)
    }

    @Provides
    @Singleton
    fun provideTimeService(
        ntpTimeService: NtpTimeService,
        jdTimeService: JdTimeService,
        clickSettingsRepository: com.jdhelper.domain.repository.ClickSettingsRepository
    ): TimeService {
        return DefaultTimeService(ntpTimeService, jdTimeService, clickSettingsRepository)
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/jdhelper/app/di/ServiceModule.kt
git commit -m "feat: add TimeService dependency injection

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

## Task 4: 修改 FloatingService 使用 TimeService

**Files:**
- Modify: `app/src/main/java/com/jdhelper/app/service/FloatingService.kt`

需要修改的部分：
1. 注入 TimeService
2. 在 updateTimeDisplay() 中使用 timeService.getCurrentTime()

- [ ] **Step 1: 查看 FloatingService 的当前结构**

找到以下位置：
- 注入字段区域 (约第 103-110 行)
- updateTimeDisplay() 方法 (约第 387-392 行)

- [ ] **Step 2: 添加 TimeService 注入**

在现有的注入字段后添加：

```kotlin
@Inject
lateinit var timeService: TimeService
```

- [ ] **Step 3: 修改 updateTimeDisplay() 使用 TimeService**

将原有的时间获取逻辑：
```kotlin
val displayTime = when (currentTimeSource) {
    TimeSource.NTP -> ntpTimeService?.getCurrentTime() ?: System.currentTimeMillis()
    TimeSource.JD -> jdTimeService.getCurrentJdTime()
}
```

替换为：
```kotlin
val displayTime = timeService.getCurrentTime()
```

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/jdhelper/app/service/FloatingService.kt
git commit -m "refactor: FloatingService uses TimeService

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

## Task 5: 修改 FloatingMenuService 使用 TimeService

**Files:**
- Modify: `app/src/main/java/com/jdhelper/app/service/FloatingMenuService.kt`

需要修改的部分：
1. 注入 TimeService
2. 将所有 ntpTimeService.getCurrentTime() 替换为 timeService.getCurrentTime()
3. 同步时间逻辑使用 timeService.syncTime()

- [ ] **Step 1: 添加 TimeService 注入**

在现有的 @Inject 字段区域添加：

```kotlin
@Inject
lateinit var timeService: TimeService
```

- [ ] **Step 2: 修改礼物整分点击任务中的时间获取**

找到 `runGiftClickTaskOnce()` 方法（约第 893 行）：

将：
```kotlin
val ntpTime = ntpTimeService.getCurrentTime()
```

替换为：
```kotlin
val currentTime = timeService.getCurrentTime()
```

同样修改该方法中其他使用 `ntpTimeService.getCurrentTime()` 的地方。

- [ ] **Step 3: 修改同步时间逻辑**

找到同步时间的方法（约第 413-428 行，约第 567-580 行，约第 654-670 行），将：
```kotlin
val synced = withContext(Dispatchers.IO) {
    ntpTimeService.syncTime()
}
```

替换为：
```kotlin
val synced = withContext(Dispatchers.IO) {
    timeService.syncTime()
}
```

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/jdhelper/app/service/FloatingMenuService.kt
git commit -m "refactor: FloatingMenuService uses TimeService

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

## Task 6: 修改 TimedClickManager 使用 TimeService

**Files:**
- Modify: `app/src/main/java/com/jdhelper/service/TimedClickManager.kt`

需要修改的部分：
1. 注入 TimeService
2. 将所有 ntpTimeService.getCurrentTime() 替换为 timeService.getCurrentTime()

- [ ] **Step 1: 查看 TimedClickManager 的构造函数**

找到构造函数区域，确认现有的注入方式。

- [ ] **Step 2: 添加 TimeService 注入**

在构造函数参数中添加 TimeService，并在内部保存引用。

- [ ] **Step 3: 修改时间获取调用**

将所有：
```kotlin
ntpTimeService.getCurrentTime()
```

替换为：
```kotlin
timeService.getCurrentTime()
```

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/jdhelper/service/TimedClickManager.kt
git commit -m "refactor: TimedClickManager uses TimeService

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

## Task 7: 修改 HomeViewModel 使用 TimeService 并添加 UI 反馈

**Files:**
- Modify: `app/src/main/java/com/jdhelper/app/ui/screens/home/HomeViewModel.kt`

需要修改的部分：
1. 注入 TimeService
2. 简化 ntpOffset 的获取逻辑，使用 timeService
3. 在 syncNtpTime() 中添加 UI 反馈

- [ ] **Step 1: 添加 TimeService 注入**

在现有的注入字段后添加：

```kotlin
@Inject
lateinit var timeService: TimeService
```

- [ ] **Step 2: 修改 ntpOffset 的收集**

找到 ntpOffset 的定义（约第 72-74 行），添加对 jdOffset 的监听：

```kotlin
// 时间偏移显示 - 合并 NTP 和 JD
val offset: StateFlow<String> = timeService.observeTimeSource()
    .flatMapLatest { source ->
        kotlinx.coroutines.flow.flow {
            while (true) {
                val offsetValue = timeService.getTimeOffset()
                val text = if (offsetValue >= 0) "+${offsetValue}ms" else "${offsetValue}ms"
                emit(text)
                delay(1000) // 每秒更新一次偏移显示
            }
        }
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "--ms")
```

注意：如果上述方式复杂，可以直接在 timeUpdates 中更新 offset。

- [ ] **Step 3: 修改 syncNtpTime() 添加 UI 反馈**

找到 syncNtpTime() 方法（约第 205-212 行），修改为：

```kotlin
suspend fun syncNtpTime(): Boolean {
    val currentSource = timeService.getCurrentTimeSource()
    
    // 根据时间源显示不同的反馈
    return if (currentSource == TimeSource.JD) {
        _uiState.update { it.copy(isNtpSyncing = true) }
        val success = timeService.syncTime()
        if (success) {
            val offset = timeService.getTimeOffset()
            val offsetText = if (offset >= 0) "+${offset}ms" else "${offset}ms"
            _uiState.update { it.copy(isNtpSyncing = false, ntpLastSyncTime = "JD: $offsetText") }
        } else {
            _uiState.update { it.copy(isNtpSyncing = false, ntpLastSyncTime = "JD: 同步失败") }
        }
        success
    } else {
        syncNtpTimeInternal()
    }
}
```

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/jdhelper/app/ui/screens/home/HomeViewModel.kt
git commit -m "feat: HomeViewModel uses TimeService with UI feedback

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

## Task 8: 构建验证

**Files:**
- Build: `app/build.gradle.kts`

- [ ] **Step 1: 构建 Debug APK**

```bash
./gradlew assembleDebug --no-daemon
```

预期：BUILD SUCCESSFUL

- [ ] **Step 2: 如果有编译错误，修复并重新构建**

- [ ] **Step 3: Commit 最终变更**

```bash
git add .
git commit -m "feat: unified time service implementation complete

- Add TimeService interface and DefaultTimeService
- Update FloatingService, FloatingMenuService, TimedClickManager to use TimeService
- Add UI feedback for JD time sync

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

## 验证清单

完成所有任务后，请验证：

- [ ] 切换到京东时间源后，状态栏显示京东时间
- [ ] 切换到京东时间源后，悬浮时钟显示京东时间
- [ ] 切换到 NTP 时间源后，所有组件显示 NTP 时间
- [ ] 点击时间同步按钮，在 NTP 模式下有反馈
- [ ] 点击时间同步按钮，在京东模式下有反馈（显示 JD: +XXms 或 JD: 同步失败）
- [ ] 抢购功能在不同时间源下都正常工作

---

**Plan complete and saved to `docs/superpowers/plans/2026-04-12-unified-time-service-plan.md`**

Two execution options:

1. **Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

2. **Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

Which approach?