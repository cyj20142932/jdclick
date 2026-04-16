package com.jdhelper.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "gift_click_history")
data class GiftClickHistory(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val stage: Int, // 0=启动按钮定时点击, 1=第一阶段一键送礼, 2=第二阶段付款并赠送
    val ntpClickTime: Long, // 点击后的NTP时间戳
    val localClickTime: Long, // 点击后的本机时间戳
    val targetTime: Long, // 目标时间（整分时间+延迟）
    val delayMillis: Double, // 延迟设置
    val actualDiff: Long, // 实际偏差 = ntpClickTime - targetTime
    val success: Boolean? = null, // 是否成功抢购 null=未设置, true=成功, false=失败
    val createTime: Long = System.currentTimeMillis(),
    val timeSource: String = "NTP"  // 新增: "NTP" 或 "JD"
)