package com.jdhelper.app.service

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.jdhelper.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.widget.Toast as SystemToast

/**
 * 自定义Toast工具类，使用WindowManager实现
 * 解决Android 10+后台Toast不显示的问题
 */
object ToastUtils {

    private var windowManager: WindowManager? = null
    private var toastView: View? = null
    private var params: WindowManager.LayoutParams? = null
    private var hideJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /**
     * 显示Toast提示
     * @param context 上下文
     * @param message 显示的消息
     * @param duration 显示时长（毫秒），默认2000ms
     */
    fun show(context: Context, message: String, duration: Long = 2000L) {
        // 取消之前的隐藏任务
        hideJob?.cancel()

        scope.launch {
            try {
                // 隐藏之前的Toast（如果有）
                hideQuietly()

                // 获取WindowManager
                val appContext = context.applicationContext
                windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager

                // 创建Toast视图
                val inflater = LayoutInflater.from(appContext)
                toastView = inflater.inflate(R.layout.custom_toast, null)

                // 设置文本
                toastView?.findViewById<TextView>(R.id.tv_toast_message)?.text = message

                // 创建布局参数 - 使用更稳定的配置
                val layoutParams = WindowManager.LayoutParams().apply {
                    width = WindowManager.LayoutParams.WRAP_CONTENT
                    height = WindowManager.LayoutParams.WRAP_CONTENT
                    type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    } else {
                        @Suppress("DEPRECATION")
                        WindowManager.LayoutParams.TYPE_PHONE
                    }
                    format = PixelFormat.TRANSLUCENT
                    flags = (
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                    )
                    gravity = Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
                    y = 300 // 距离底部300像素
                }
                params = layoutParams

                // 添加视图
                try {
                    windowManager?.addView(toastView, params)
                } catch (e: Exception) {
                    // 如果添加失败，使用系统Toast
                    SystemToast.makeText(appContext, message, SystemToast.LENGTH_SHORT).show()
                    return@launch
                }

                // 延迟隐藏
                hideJob = scope.launch {
                    delay(duration)
                    withContext(Dispatchers.Main) {
                        hideQuietly()
                    }
                }

            } catch (e: Exception) {
                // 如果出现异常，使用系统Toast作为后备
                try {
                    SystemToast.makeText(context.applicationContext, message, SystemToast.LENGTH_SHORT).show()
                } catch (ignored: Exception) {}
            }
        }
    }

    /**
     * 隐藏Toast（静默版本，不抛出异常）
     */
    private fun hideQuietly() {
        try {
            toastView?.let { view ->
                windowManager?.removeView(view)
            }
        } catch (e: Exception) {
            // 忽略移除异常
        } finally {
            toastView = null
            windowManager = null
            params = null
        }
    }

    /**
     * 隐藏Toast（公开方法）
     */
    fun hide() {
        hideJob?.cancel()
        scope.launch {
            hideQuietly()
        }
    }

    /**
     * 清理资源
     */
    fun cleanup() {
        hideJob?.cancel()
        scope.cancel()
    }
}