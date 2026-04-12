package com.jdhelper.app.service

import android.util.Log
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
        private var sharedJdOffset: Long = 0L  // 京东时间差
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
                    Log.d(TAG, "京东时间同步成功: offset=${result}ms")
                    return@withContext true
                }
            } catch (e: Exception) {
                Log.w(TAG, "第 $attempt/$MAX_RETRIES 次获取京东时间失败: ${e.message}")
            }
        }
        Log.e(TAG, "京东时间同步失败，已达到最大重试次数")
        return@withContext false
    }

    /**
     * 请求京东服务器时间
     */
    private fun requestJdTime(): Long? {
        val request = Request.Builder()
            .url(JD_API_URL)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36")
            .header("Accept", "application/json")
            .header("Cookie", "wskey=whatever") // 模拟登录态
            .build()

        val startTime = System.currentTimeMillis()

        return try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.w(TAG, "请求失败: ${response.code}")
                return null
            }

            val endTime = System.currentTimeMillis()
            val roundTripTime = endTime - startTime
            val networkDelay = roundTripTime / 2

            // 方案1: 从响应头获取服务器时间戳
            var serverTimestamp: Long? = null

            // 尝试从 Date 头获取
            val dateHeader = response.header("Date")
            if (dateHeader != null) {
                try {
                    val df = java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", java.util.Locale.US)
                    df.timeZone = java.util.TimeZone.getTimeZone("GMT")
                    val parsedDate = df.parse(dateHeader)
                    serverTimestamp = parsedDate?.time
                    Log.d(TAG, "从Date头获取到时间: $dateHeader -> $serverTimestamp")
                } catch (e: Exception) {
                    Log.w(TAG, "解析Date头失败: ${e.message}")
                }
            }

            // 方案2: 尝试从响应体获取服务器时间
            if (serverTimestamp == null) {
                val body = response.body?.string()
                if (body != null) {
                    // 尝试查找时间戳字段
                    val timestampRegex = Regex("\"(serverTime|serverTime|timestamp)\":?\\s*(\\d+)")
                    timestampRegex.find(body)?.let { match ->
                        match.groupValues.getOrNull(2)?.toLongOrNull()?.let { ts ->
                            serverTimestamp = ts
                            Log.d(TAG, "从响应体获取到时间戳: $ts")
                        }
                    }
                }
            }

            if (serverTimestamp == null) {
                // 方案3: 使用响应头的 Last-Modified 或其他时间
                val lastModified = response.header("Last-Modified")
                if (lastModified != null) {
                    try {
                        val df = java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", java.util.Locale.US)
                        df.timeZone = java.util.TimeZone.getTimeZone("GMT")
                        val parsedDate = df.parse(lastModified)
                        serverTimestamp = parsedDate?.time
                        Log.d(TAG, "从Last-Modified头获取到时间: $lastModified -> $serverTimestamp")
                    } catch (e: Exception) {
                        Log.w(TAG, "解析Last-Modified头失败: ${e.message}")
                    }
                }
            }

            // 如果仍然无法获取服务器时间，使用本地时间但标记为同步失败
            if (serverTimestamp == null) {
                Log.w(TAG, "无法从响应中提取服务器时间")
                return null
            }

            // 计算时间差
            val ts = serverTimestamp ?: return null
            val localTimeAtMidpoint = startTime + networkDelay
            val timeDiff = localTimeAtMidpoint - ts

            Log.d(TAG, "RTT=$roundTripTime ms, 网络延迟=${networkDelay}ms, 服务器时间=${ts}, 时间差=${timeDiff}ms")
            timeDiff
        } catch (e: Exception) {
            Log.e(TAG, "请求京东时间异常: ${e.message}")
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