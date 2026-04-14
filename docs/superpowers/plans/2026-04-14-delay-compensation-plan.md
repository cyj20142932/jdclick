# 延迟补偿统一到时间源实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 `delayMillis` 延迟补偿集成到 `TimeService.getCurrentTime()` 方法中，使所有调用方自动获得延迟补偿

**Architecture:** 修改 `DefaultTimeService` 在返回时间时加上延迟补偿，移除 `TimedClickManager` 中的延迟重复计算

**Tech Stack:** Kotlin, Jetpack Compose, Hilt DI, Room

---

## 文件结构

- 修改: `app/src/main/java/com/jdhelper/app/service/DefaultTimeService.kt`
- 修改: `app/src/main/java/com/jdhelper/service/TimedClickManager.kt`

---

### Task 1: 修改 DefaultTimeService 添加延迟补偿

**Files:**
- Modify: `app/src/main/java/com/jdhelper/app/service/DefaultTimeService.kt`

- [ ] **Step 1: 添加延迟补偿缓存变量**

在 `DefaultTimeService` 类中添加 `@Volatile private var cachedDelayMillis: Double = 0.0` 变量

- [ ] **Step 2: 添加 SharedPreferences 读取延迟**

由于 `ClickSettingsRepository` 使用 SharedPreferences 存储延迟，需要确保初始值正确读取。查看现有代码，`getDelayMillis()` 返回 Flow，直接在 init 块中观察即可。

```kotlin
// 在 init 块中添加延迟补偿的观察
scope.launch {
    clickSettingsRepository.getDelayMillis().collect { delay ->
        cachedDelayMillis = delay
        LogConsole.d(TAG, "延迟补偿已更新缓存: ${delay}ms")
    }
}
```

- [ ] **Step 3: 修改 getCurrentTime() 方法**

在 `getCurrentTime()` 返回时加上延迟补偿：

```kotlin
override fun getCurrentTime(): Long {
    val source = cachedTimeSource
    val baseTime = when (source) {
        TimeSource.NTP -> ntpTimeService.getCurrentTime()
        TimeSource.JD -> jdTimeService.getCurrentJdTime()
    }
    // 加上延迟补偿
    return baseTime + cachedDelayMillis.toLong()
}
```

- [ ] **Step 4: 编译验证**

Run: `./gradlew :app:compileDebugKotlin`
Expected: 编译成功，无错误

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/jdhelper/app/service/DefaultTimeService.kt
git commit -m "feat: 将延迟补偿集成到 TimeService.getCurrentTime() 方法中"
```

---

### Task 2: 修改 TimedClickManager 移除重复延迟逻辑

**Files:**
- Modify: `app/src/main/java/com/jdhelper/service/TimedClickManager.kt`

- [ ] **Step 1: 修改 calculateNextMinuteTime() 方法**

移除方法中的延迟补偿逻辑，因为时间服务已经包含了延迟：

```kotlin
/**
 * 计算下一个整分时刻的时间
 */
private fun calculateNextMinuteTime(ntpTime: Long): Long {
    val calendar = Calendar.getInstance().apply {
        timeInMillis = ntpTime
    }

    // 设置为下一分钟的00秒000毫秒
    calendar.add(Calendar.MINUTE, 1)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)

    // 不再在这里加上 delayMillis，时间服务已包含延迟补偿
    return calendar.timeInMillis
}
```

- [ ] **Step 2: 修改 scheduleClick() 方法**

更新 `scheduleClick()` 方法调用，简化参数传递：

```kotlin
private fun scheduleClick() {
    val ntpTime = timeService.getCurrentTime()
    // 计划点击时间：下一个整分时刻（时间服务已包含延迟）
    plannedClickTime = calculateNextMinuteTime(ntpTime)

    // 使用NTP时间计算延迟
    val delay = plannedClickTime - ntpTime
    LogConsole.d(TAG, "计划点击时间: $plannedClickTime, 当前时间: $ntpTime, 等待: ${delay}ms")
    // ... 后续代码不变
}
```

- [ ] **Step 3: 编译验证**

Run: `./gradlew :app:compileDebugKotlin`
Expected: 编译成功，无错误

- [ ] **Step 4: 提交**

```bash
git add app/src/main/java/com/jdhelper/service/TimedClickManager.kt
git commit -m "refactor: 移除 TimedClickManager 中的延迟补偿逻辑，统一由 TimeService 处理"
```

---

## 验证清单

实现完成后，验证以下场景：

1. [ ] NTP 时间源下 `getCurrentTime()` 返回值包含延迟
2. [ ] 京东时间源下 `getCurrentTime()` 返回值包含延迟
3. [ ] 切换时间源后延迟仍然生效
4. [ ] 负数延迟场景正常工作
5. [ ] 定时点击的精确度是否提升
6. [ ] 悬浮窗显示时间包含延迟补偿