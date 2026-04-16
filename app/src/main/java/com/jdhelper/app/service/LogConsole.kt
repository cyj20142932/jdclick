package com.jdhelper.app.service

import android.util.Log
import com.jdhelper.app.data.local.LogEntry
import com.jdhelper.app.domain.repository.LogRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

object LogConsole {

    const val VERBOSE = 0
    const val DEBUG = 1
    const val INFO = 2
    const val WARN = 3
    const val ERROR = 4

    // 通过lateinit延迟注入Repository
    @Volatile
    private var logRepository: LogRepository? = null

    fun setRepository(repository: LogRepository) {
        logRepository = repository
    }

    fun v(tag: String, msg: String) = log(VERBOSE, tag, msg)
    fun d(tag: String, msg: String) = log(DEBUG, tag, msg)
    fun i(tag: String, msg: String) = log(INFO, tag, msg)
    fun w(tag: String, msg: String) = log(WARN, tag, msg)
    fun e(tag: String, msg: String, throwable: Throwable? = null) =
        log(ERROR, tag, msg, throwable)

    private fun log(level: Int, tag: String, msg: String, throwable: Throwable? = null) {
        // 1. 输出到Android Log（保持兼容）
        val androidLogMsg = if (throwable != null) "$msg\n${Log.getStackTraceString(throwable)}" else msg
        when(level) {
            VERBOSE -> Log.v(tag, androidLogMsg)
            DEBUG -> Log.d(tag, androidLogMsg)
            INFO -> Log.i(tag, androidLogMsg)
            WARN -> Log.w(tag, androidLogMsg)
            ERROR -> Log.e(tag, androidLogMsg)
        }

        // 2. 写入Room数据库
        val logEntry = LogEntry(
            level = level,
            tag = tag,
            message = msg,
            timestamp = System.currentTimeMillis(),
            threadName = Thread.currentThread().name
        )

        logRepository?.let { repo ->
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    repo.addLog(logEntry)
                } catch (e: Exception) {
                    Log.e("LogConsole", "写入日志失败: ${e.message}")
                }
            }
        }
    }
}