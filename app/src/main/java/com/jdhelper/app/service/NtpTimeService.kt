package com.jdhelper.service

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.net.ntp.NTPUDPClient
import org.apache.commons.net.ntp.TimeInfo
import java.net.InetAddress
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "NtpTimeService"

/**
 * NTP时间同步服务
 * 使用 Apache Commons Net 库
 */
@Singleton
class NtpTimeService @Inject constructor(
    private val context: Context
) {
    companion object {
        // 阿里云NTP服务器列表
        val NTP_SERVERS = listOf(
            "ntp.aliyun.com",
            "ntp1.aliyun.com",
            "ntp2.aliyun.com",
            "ntp3.aliyun.com",
            "ntp4.aliyun.com"
        )

        // 静态变量用于跨服务共享状态
        @Volatile
        private var sharedCurrentTimeMillis: Long = System.currentTimeMillis()

        @Volatile
        private var sharedLastSyncTime: Long = 0L
    }

    // 当前使用的NTP服务器
    @Volatile
    private var currentServer: String = NTP_SERVERS[0]

    /**
     * 同步时间
     */
    suspend fun syncTime(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "开始请求NTP时间: $currentServer")
            val ntpTime = requestNtpTime(currentServer)
            Log.d(TAG, "请求结果 ntpTime: $ntpTime")
            if (ntpTime > 0) {
                sharedCurrentTimeMillis = ntpTime
                sharedLastSyncTime = System.currentTimeMillis()
                Log.d(TAG, "NTP同步成功，当前时间: $sharedCurrentTimeMillis")
                return@withContext true
            } else {
                Log.w(TAG, "NTP返回无效时间: $ntpTime")
            }
        } catch (e: Exception) {
            Log.e(TAG, "NTP同步异常", e)
        }
        false
    }

    /**
     * 使用 Apache Commons Net 请求NTP时间
     */
    private fun requestNtpTime(server: String): Long {
        return try {
            val client = NTPUDPClient()
            client.defaultTimeout = 5000

            val address = InetAddress.getByName(server)
            val timeInfo: TimeInfo = client.getTime(address, 123)

            // 获取校正后的时间
            val ntpTime = timeInfo.message.receiveTimeStamp.time
            Log.d(TAG, "NTP返回时间: $ntpTime")

            client.close()
            ntpTime
        } catch (e: Exception) {
            Log.e(TAG, "NTP请求异常: ${e.message}")
            -1
        }
    }

    /**
     * 获取当前时间（优先使用NTP时间）
     */
    fun getCurrentTime(): Long {
        val currentSystemTime = System.currentTimeMillis()
        val timeSinceLastSync = currentSystemTime - sharedLastSyncTime
        return if (sharedLastSyncTime > 0 && timeSinceLastSync < 300000) { // 5分钟内使用缓存
            sharedCurrentTimeMillis + timeSinceLastSync
        } else {
            Log.d(TAG, "getCurrentTime: 未同步或超过5分钟，使用系统时间")
            currentSystemTime
        }
    }

    /**
     * 获取上次同步时间
     */
    fun getLastSyncTime(): Long = sharedLastSyncTime

    /**
     * 检查是否已同步
     */
    fun isSynced(): Boolean = sharedLastSyncTime > 0

    /**
     * 获取时间偏差（NTP时间 - 本地时间）
     * 正数代表NTP时间快于本地时间，负数代表慢于本地时间
     */
    fun getTimeOffset(): Long {
        if (!isSynced()) return 0L
        return sharedCurrentTimeMillis - sharedLastSyncTime
    }

    fun getCurrentServer(): String = currentServer

    fun setServer(server: String) {
        if (NTP_SERVERS.contains(server)) {
            currentServer = server
            Log.d(TAG, "切换NTP服务器: $server")
        }
    }
}