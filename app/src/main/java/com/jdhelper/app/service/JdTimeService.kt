package com.jdhelper.app.service

import android.util.Log
import com.jdhelper.app.service.LogConsole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "JdTimeService"

@Singleton
class JdTimeService @Inject constructor() {
    companion object {
        const val JD_API_URL = "https://api.m.jd.com/client.action?functionId=queryMaterialProducts"
        const val MAX_RETRIES = 3
        const val REQUEST_TIMEOUT = 2000L

        @Volatile
        private var sharedJdOffset: Long = 0L  // 京东时间差（本地时间 - 京东时间）
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(REQUEST_TIMEOUT, TimeUnit.MILLISECONDS)
        .readTimeout(REQUEST_TIMEOUT, TimeUnit.MILLISECONDS)
        .build()

    /**
     * 获取京东时间差
     * @return 时间差（毫秒），正值表示本地时间比京东服务器时间快
     */
    suspend fun syncJdTime(): Boolean = withContext(Dispatchers.IO) {
        for (attempt in 1..MAX_RETRIES) {
            try {
                val result = requestJdTime()
                if (result != null) {
                    sharedJdOffset = result
                    LogConsole.d(TAG, "京东时间同步成功: offset=${result}ms")
                    return@withContext true
                }
            } catch (e: Exception) {
                LogConsole.w(TAG, "第 $attempt/$MAX_RETRIES 次获取京东时间失败: ${e.message}")
            }
        }
        LogConsole.e(TAG, "京东时间同步失败，已达到最大重试次数")
        return@withContext false
    }

    /**
     * 请求京东服务器时间
     * 从 x-api-request-id 响应头获取精确到毫秒的时间戳
     * 格式：通过 "-" 分割后取最后一位
     */
    private fun requestJdTime(): Long? {
        val request = Request.Builder()
            .url(JD_API_URL)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36")
            .header("Accept", "application/json")
            .header("Cookie", "wskey=whatever")
            .build()

        val requestTime = System.currentTimeMillis()

        return try {
            val response = client.newCall(request).execute()
            val responseTime = System.currentTimeMillis()

            if (!response.isSuccessful) {
                LogConsole.w(TAG, "请求失败: ${response.code}")
                return null
            }

            // 从响应头 x-api-request-id 获取时间戳
            val requestId = response.header("x-api-request-id")
            if (requestId == null) {
                LogConsole.w(TAG, "响应头中未找到 x-api-request-id")
                return null
            }

            // 通过 "-" 分割，取最后一部分作为毫秒时间戳
            val parts = requestId.split("-")
            val timestampStr = parts.lastOrNull()
            if (timestampStr == null) {
                LogConsole.w(TAG, "x-api-request-id 格式异常: $requestId")
                return null
            }

            // 尝试解析时间戳（可能是纯数字或带有其他字符）
            val serverTimestamp = timestampStr.toLongOrNull()
            if (serverTimestamp == null) {
                LogConsole.w(TAG, "无法解析时间戳: $timestampStr, 原始: $requestId")
                return null
            }

            // 如果时间戳是13位毫秒级，直接使用；否则可能是秒级，需要转换
            val finalServerTime = if (serverTimestamp > 9999999999L) {
                serverTimestamp  // 已经是毫秒级时间戳
            } else {
                serverTimestamp * 1000  // 秒级转换为毫秒
            }

            // 计算网络延迟（往返时间 / 2）
            val roundTripTime = responseTime - requestTime
            val networkDelay = roundTripTime / 2

            // 计算服务器时间的本地等价时刻
            val localTimeAtServer = requestTime + networkDelay

            // 时间差 = 本地时间 - 服务器时间
            // 正值表示本地时间比京东服务器快
            val timeDiff = localTimeAtServer - finalServerTime

            LogConsole.d(TAG, "x-api-request-id: $requestId")
            LogConsole.d(TAG, "解析时间戳: $timestampStr -> $finalServerTime")
            LogConsole.d(TAG, "请求时间: $requestTime, 响应时间: $responseTime")
            LogConsole.d(TAG, "往返延迟: ${roundTripTime}ms, 单向延迟估算: ${networkDelay}ms")
            LogConsole.d(TAG, "服务器时间: $finalServerTime, 本地等价时刻: $localTimeAtServer")
            LogConsole.d(TAG, "时间差: ${timeDiff}ms")

            timeDiff
        } catch (e: Exception) {
            LogConsole.e(TAG, "请求京东时间异常: ${e.message}")
            null
        }
    }

    /**
     * 获取京东时间差
     */
    fun getJdOffset(): Long = sharedJdOffset

    /**
     * 获取当前京东时间
     */
    fun getCurrentJdTime(): Long {
        return System.currentTimeMillis() + sharedJdOffset
    }

    /**
     * 检查是否已同步
     */
    fun isSynced(): Boolean = sharedJdOffset != 0L
}