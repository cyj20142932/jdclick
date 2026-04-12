package com.jdhelper.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class TimeSource {
    NTP,    // 阿里云NTP时间
    JD      // 京东服务器时间
}

@Entity(tableName = "click_settings")
data class ClickSettings(
    @PrimaryKey
    val id: Long = 1,
    val delayMillis: Double = 0.0,
    val millisecondDigits: Int = 1, // 0=不显示毫秒, 1=显示1位, 3=显示3位
    val recordHistory: Boolean = false, // 是否记录点击历史
    val timeSource: TimeSource = TimeSource.NTP  // 新增：时间源
)