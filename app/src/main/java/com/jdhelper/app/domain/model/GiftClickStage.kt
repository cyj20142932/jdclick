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
