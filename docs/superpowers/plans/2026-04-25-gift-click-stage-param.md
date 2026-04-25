# 送礼功能阶段参数化实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将送礼点击逻辑从硬编码两阶段重构为参数化阶段工作流，支持后续任意扩展

**Architecture:** 新增 `GiftClickStage` 数据类描述阶段参数，`AccessibilityClickService` 提供通用 `findButtonByKeywords()` 方法，`FloatingMenuService` 通过 `executeGiftWorkflow()` 循环执行阶段列表，clickWithTiming() 分发定时/轮询两种策略

**Tech Stack:** Kotlin, Android AccessibilityService, Room

---

### Task 1: 创建 GiftClickStage 领域模型

**Files:**
- Create: `app/src/main/java/com/jdhelper/app/domain/model/GiftClickStage.kt`

- [ ] **Step 1: 创建 GiftClickStage.kt**

```kotlin
package com.jdhelper.app.domain.model

/**
 * 送礼任务的单个阶段配置
 */
data class GiftClickStage(
    val name: String,
    val keywords: List<String>,
    val timing: StageTiming,
    val delayAfterClickMs: Long = 1000,
)

/**
 * 点击时机策略
 * - Timed: 等待整分时间后点击（阶段1用，需计算偏移）
 * - Poll: 轮询查找，找到即点（阶段2+用）
 */
sealed class StageTiming {
    data object Timed : StageTiming()

    data class Poll(
        val timeoutMs: Long = 3000,
        val intervalMs: Long = 100,
    ) : StageTiming()
}
```

- [ ] **Step 2: 提交**

```bash
git add app/src/main/java/com/jdhelper/app/domain/model/GiftClickStage.kt
git commit -m "feat: add GiftClickStage domain model for parameterized gift workflow"
```

### Task 2: AccessibilityClickService 通用查找方法

**Files:**
- Modify: `app/src/main/java/com/jdhelper/app/service/AccessibilityClickService.kt`

- [ ] **Step 1: 移除 findFirstStageButton() 方法**

删除以下代码块（约第297-308行）:

```kotlin
    /**
     * 查找第一阶段按钮（一键送礼）
     */
    @Suppress("DEPRECATION")
    fun findFirstStageButton(): Point? {
        val rootNode = rootInActiveWindow ?: return null
        return try {
            searchButtonByKeywords(rootNode, listOf("一键送礼", "送给"))
        } finally {
            rootNode.recycle()
        }
    }
```

- [ ] **Step 2: 移除 findSecondStageButton() 方法**

删除以下代码块（约第310-321行）:

```kotlin
    /**
     * 查找第二阶段按钮（付款并赠送）
     */
    @Suppress("DEPRECATION")
    fun findSecondStageButton(): Point? {
        val rootNode = rootInActiveWindow ?: return null
        return try {
            searchButtonByKeywords(rootNode, listOf("付款并赠送"))
        } finally {
            rootNode.recycle()
        }
    }
```

- [ ] **Step 3: 在原位置添加通用查找方法**

```kotlin
    /**
     * 通过关键词列表查找按钮（通用）
     */
    @Suppress("DEPRECATION")
    fun findButtonByKeywords(keywords: List<String>): Point? {
        val rootNode = rootInActiveWindow ?: return null
        return try {
            searchButtonByKeywords(rootNode, keywords)
        } finally {
            rootNode.recycle()
        }
    }
```

- [ ] **Step 4: 提交**

```bash
git add app/src/main/java/com/jdhelper/app/service/AccessibilityClickService.kt
git commit -m "refactor: replace findFirst/SecondStageButton with generic findButtonByKeywords"
```

### Task 3: 重构 FloatingMenuService 送礼执行逻辑

**Files:**
- Modify: `app/src/main/java/com/jdhelper/app/service/FloatingMenuService.kt`

- [ ] **Step 1: 添加阶段配置列表**

在 `companion object` 之后或类顶部附近添加配置列表:

```kotlin
    /** 送礼阶段配置（编译时参数化，后续可改为动态加载） */
    private val giftClickStages = listOf(
        GiftClickStage(
            name = "一键送礼",
            keywords = listOf("一键送礼", "送给"),
            timing = StageTiming.Timed,
            delayAfterClickMs = 1000,
        ),
        GiftClickStage(
            name = "付款并赠送",
            keywords = listOf("付款并赠送"),
            timing = StageTiming.Poll(timeoutMs = 3000, intervalMs = 100),
            delayAfterClickMs = 1000,
        ),
    )
```

需要添加 import:
```kotlin
import com.jdhelper.app.domain.model.GiftClickStage
import com.jdhelper.app.domain.model.StageTiming
```

- [ ] **Step 2: 替换 runGiftClickTaskOnce() 为 executeGiftWorkflow()**

找到 `runGiftClickTaskOnce()` 方法（约第912行），整体替换为:

```kotlin
    /**
     * 执行送礼工作流 — 按阶段列表依次执行
     * 任一阶段失败（未找到按钮 / 超时）则终止后续
     */
    private suspend fun CoroutineScope.executeGiftWorkflow(stages: List<GiftClickStage>) {
        LogConsole.d(TAG, "=== 礼物工作流开始，共 ${stages.size} 个阶段 ===")

        val clickDelay = clickSettingsRepository.getDelayMillis().first()

        for ((index, stage) in stages.withIndex()) {
            val stageIndex = index + 1
            LogConsole.d(TAG, "阶段 $stageIndex/${stages.size}: ${stage.name}")

            val success = executeStage(stage, clickDelay, stageIndex)
            if (!success) {
                LogConsole.w(TAG, "阶段 $stageIndex 失败: ${stage.name}，终止工作流")
                return@executeGiftWorkflow
            }

            delay(stage.delayAfterClickMs)
        }

        withContext(Dispatchers.Main) {
            ToastUtils.show(this@FloatingMenuService, "礼物任务已完成")
        }
        LogConsole.d(TAG, "=== 礼物工作流成功完成 ===")
    }

    /**
     * 执行单个阶段，返回是否成功
     */
    private suspend fun CoroutineScope.executeStage(
        stage: GiftClickStage,
        clickDelay: Long,
        stageIndex: Int,
    ): Boolean {
        return when (stage.timing) {
            is StageTiming.Timed -> executeTimedStage(stage.keywords, clickDelay, stageIndex)
            is StageTiming.Poll -> executePollStage(stage.keywords, stage.timing, stageIndex)
        }
    }

    /**
     * 定时阶段 — 先找到按钮，再等待整分时间后点击
     */
    private suspend fun CoroutineScope.executeTimedStage(
        keywords: List<String>,
        clickDelay: Long,
        stageIndex: Int,
    ): Boolean {
        val button = withContext(Dispatchers.IO) {
            AccessibilityClickService.getInstance()?.findButtonByKeywords(keywords)
        } ?: return false

        withContext(Dispatchers.Main) {
            ToastUtils.show(this@FloatingMenuService, "找到${stageIndex}阶段按钮，等待整分点击")
        }

        delay(500)

        val ntpTime = timeService.getCurrentTime()
        val calendar = Calendar.getInstance().apply {
            timeInMillis = ntpTime
            add(Calendar.MINUTE, 1)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val targetTime = calendar.timeInMillis + clickDelay.toLong()
        val timeToTarget = targetTime - ntpTime

        if (timeToTarget <= 0) return false

        // 分段等待：协程 delay + LockSupport.parkNanos 精确等待
        val spinThreshold = 50L
        if (timeToTarget > spinThreshold) {
            delay(timeToTarget - spinThreshold)
        }

        var currentTime = timeService.getCurrentTime()
        while (isActive && currentTime < targetTime) {
            val remaining = targetTime - currentTime
            if (remaining > 0) {
                val parkTime = if (remaining > 5) minOf(remaining, 10_000_000L) else 0
                if (parkTime > 0) LockSupport.parkNanos(parkTime)
            }
            currentTime = timeService.getCurrentTime()
        }

        // 执行点击
        withContext(Dispatchers.IO) {
            AccessibilityClickService.getInstance()?.performGlobalClick(button.x, button.y)
        }
        val clickTime = timeService.getCurrentTime()

        recordHistory(
            stage = stageIndex,
            ntpClickTime = clickTime,
            localClickTime = System.currentTimeMillis(),
            targetTime = targetTime,
            clickDelay = clickDelay.toDouble(),
            actualDiff = clickTime - targetTime,
        )
        return true
    }

    /**
     * 轮询阶段 — 循环查找按钮直到超时，找到即点
     */
    private suspend fun CoroutineScope.executePollStage(
        keywords: List<String>,
        timing: StageTiming.Poll,
        stageIndex: Int,
    ): Boolean {
        val startTime = timeService.getCurrentTime()

        // 初始等待（UI需要时间过渡）
        delay(300)

        while (timeService.getCurrentTime() - startTime < timing.timeoutMs) {
            val button = withContext(Dispatchers.IO) {
                AccessibilityClickService.getInstance()?.findButtonByKeywords(keywords)
            }

            if (button != null) {
                LogConsole.d(TAG, "阶段 $stageIndex 找到按钮 (${button.x}, ${button.y})")
                withContext(Dispatchers.IO) {
                    AccessibilityClickService.getInstance()?.performGlobalClick(button.x, button.y)
                }
                val clickTime = timeService.getCurrentTime()

                recordHistory(
                    stage = stageIndex,
                    ntpClickTime = clickTime,
                    localClickTime = System.currentTimeMillis(),
                    targetTime = clickTime,
                    clickDelay = 0.0,
                    actualDiff = 0,
                )
                return true
            }

            delay(timing.intervalMs)
        }

        // 超时未找到
        LogConsole.w(TAG, "阶段 $stageIndex 超时未找到按钮")
        withContext(Dispatchers.Main) {
            ToastUtils.show(this@FloatingMenuService, "未找到: 阶段${stageIndex}")
        }
        return false
    }

    /**
     * 记录历史
     */
    private suspend fun recordHistory(
        stage: Int,
        ntpClickTime: Long,
        localClickTime: Long,
        targetTime: Long,
        clickDelay: Double,
        actualDiff: Long,
    ) {
        val shouldRecord = clickSettingsRepository.getRecordHistory().first()
        if (!shouldRecord) return

        val timeSource = clickSettingsRepository.getTimeSource().first()
        LogConsole.d(TAG, "保存历史记录: stage=$stage, timeSource=${timeSource.name}")
        giftClickHistoryDao.insert(
            GiftClickHistory(
                stage = stage,
                ntpClickTime = ntpClickTime,
                localClickTime = localClickTime,
                targetTime = targetTime,
                delayMillis = clickDelay,
                actualDiff = actualDiff,
                timeSource = timeSource.name,
            )
        )
    }
```

注意: 原 `runGiftClickTaskOnce()` 中阶段2有 300ms 初始等待和一秒轮询间隔 + 3秒超时的循环逻辑。在设计讨论中已确定，对于 `Poll` 策略，首次调用时已通过 `findButtonByKeywords` 找到按钮，所以直接点击即可（类似原阶段2找到后立即点击的行为）。如需实际轮询等待按钮出现（而非首次查找），后续可通过修改 `findButtonByKeywords` 调用时机来调整。

- [ ] **Step 3: 更新 btn_gift 点击事件**

在 `btn_gift` 的点击监听器中，找到调用 `runGiftClickTaskOnce()` 的位置（约第619行），替换为:

```kotlin
                    // 3. 启动送礼工作流
                    giftJob = launch {
                        try {
                            executeGiftWorkflow(giftClickStages)
                            isGiftRunning = false
                            floatingStateManager.notifyTaskStateChanged(FloatingStateManager.TASK_TYPE_GIFT, false)
                            // 更新礼物按钮图标
                            withContext(Dispatchers.Main) {
                                updateGiftButtonState()
                                updateTaskIndicator(indicatorGift, false)
                                updateOverallStatus()
                            }
                            FloatingService.stopService(this@FloatingMenuService)
                        } catch (e: Exception) {
                            LogConsole.e(TAG, "礼物任务异常", e)
                            isGiftRunning = false
                            floatingStateManager.notifyTaskStateChanged(FloatingStateManager.TASK_TYPE_GIFT, false)
                            withContext(Dispatchers.Main) {
                                updateGiftButtonState()
                                updateTaskIndicator(indicatorGift, false)
                                updateOverallStatus()
                            }
                            FloatingService.stopService(this@FloatingMenuService)
                            withContext(Dispatchers.Main) {
                                ToastUtils.show(this@FloatingMenuService, "礼物任务终止: ${e.message}")
                            }
                        }
                    }
```

- [ ] **Step 4: 清理未使用的 import**

保留以下已有的 import（被新代码使用）:
- `java.util.Calendar`
- `java.util.concurrent.locks.LockSupport`

添加新的 import:
```kotlin
import com.jdhelper.app.domain.model.GiftClickStage
import com.jdhelper.app.domain.model.StageTiming
```

移除不再需要的 import:
```kotlin
// 移除 — 已不再使用 Point（由通用方法内部处理）
// 注意: Point 仍被 AccessibilityService 的 performGlobalClick 使用，确认后决定
```

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/jdhelper/app/service/FloatingMenuService.kt
git commit -m "refactor: replace runGiftClickTaskOnce with parameterized gift workflow"
```

### Task 4: 验证构建

- [ ] **Step 1: 编译检查**

```bash
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 2: 提交最终验证**

```bash
git add -A
git commit -m "build: verify gift click stage refactor build"
```
