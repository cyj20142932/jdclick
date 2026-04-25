# 送礼功能阶段参数化设计

日期: 2026-04-25

## 概述

将当前硬编码的两阶段送礼点击逻辑（`runGiftClickTaskOnce()`）重构为可配置的阶段化工作流，支持后续扩展任意数量阶段，每个阶段的关键词、点击策略、超时等参数可配置。

## 设计要求

1. 阶段参数化 — 每个阶段可独立配置：关键词、点击时机策略、超时、点击后等待
2. 顺序执行 — 阶段按顺序执行，任一阶段失败则终止后续
3. 点击策略 — 阶段1使用"整分定时"（等待下一个整分时间），阶段2+使用"轮询"（找到立即点击）
4. 时钟精度 — `LockSupport.parkNanos` 精确定时完全保留
5. 历史记录 — 各阶段分别记录点击历史
6. 零性能开销 — 抢购场景，配置读取 O(1)，执行仅多一次 `when` 分支
7. 先编译时配置，预留动态加载接口

## 数据模型

```kotlin
// domain/model/GiftClickStage.kt

data class GiftClickStage(
    val name: String,                    // 阶段名（日志/Toast用）
    val keywords: List<String>,          // 查找关键词列表
    val timing: StageTiming,             // 点击时机策略
    val delayAfterClickMs: Long = 1000,  // 点击后等待UI过渡
)

sealed class StageTiming {
    /** 等待整分时间后点击（阶段1用） */
    data object Timed : StageTiming()

    /** 轮询查找，找到即点（阶段2+用） */
    data class Poll(
        val timeoutMs: Long = 3000,
        val intervalMs: Long = 100,
    ) : StageTiming()
}
```

## 组件职责

### GiftClickStage (新增)
- 纯数据类，零运行时开销
- 描述一个阶段的所有参数
- 位于 `domain/model/` 层

### AccessibilityClickService
- 移除 `findFirstStageButton()` 和 `findSecondStageButton()` 两个方法
- 新增 `findButtonByKeywords(keywords: List<String>): Point?` 作为通用入口
- `searchButtonByKeywords()` / `searchButtonOptimized()` 保持不变

### FloatingMenuService
- 新增编译时配置列表 `giftClickStages: List<GiftClickStage>`
- 用 `executeGiftWorkflow()` 替代 `runGiftClickTaskOnce()`
- 用 `clickWithTiming()` 统一分发定时/轮询策略
- 原有精确等待（`LockSupport.parkNanos`）和轮询逻辑封装为独立方法

## 阶段配置示例

```kotlin
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
    // 未来可追加:
    // GiftClickStage(
    //     name = "确认订单",
    //     keywords = listOf("确认"),
    //     timing = StageTiming.Poll(timeoutMs = 3000, intervalMs = 100),
    //     delayAfterClickMs = 500,
    // ),
)
```

## 执行流程

```
executeGiftWorkflow(stages)
  │
  for each stage:
  ├─ findButtonByKeywords(keywords) → Point?
  │  └─ null → Toast("未找到: {name}") → return (终止)
  │
  ├─ clickWithTiming(button, timing, stageIndex)
  │  └─ when(timing):
  │     ├─ Timed  → executeTimedClick(button, stageIndex)   // LockSupport.parkNanos
  │     │            // 内部调用 recordHistory(stageIndex, clickTime, targetTime, ...)
  │     └─ Poll   → executePollClick(button, timing, stageIndex)  // 轮询+超时
  │                 // 内部调用 recordHistory(stageIndex, clickTime, ...)
  │
  └─ delay(delayAfterClickMs)
  
  Done → Toast("礼物任务已完成")
```

## 历史记录

保持现有 `GiftClickHistory` 实体不变，`stage` 字段从 1 开始递增（之前 stage=1 对应一键送礼，stage=2 对应付款并赠送，与新设计一致）。

## 实现文件

| 文件 | 变更 |
|------|------|
| `domain/model/GiftClickStage.kt` | **创建** — 数据模型 |
| `service/AccessibilityClickService.kt` | **修改** — 替换两个查找方法为通用方法 |
| `service/FloatingMenuService.kt` | **修改** — 替换 `runGiftClickTaskOnce()` 为 `executeGiftWorkflow()` |

## 向后兼容

- 行为零变化，仅重构
- `GiftClickHistory` 实体不变
- `giftClickHistoryDao` 不变
- `btn_gift` 按钮点击事件入口不变
