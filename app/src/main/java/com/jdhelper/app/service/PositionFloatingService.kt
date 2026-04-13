package com.jdhelper.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.util.Log
import com.jdhelper.app.service.LogConsole
import androidx.core.app.NotificationCompat
import com.jdhelper.R
import com.jdhelper.ui.MainActivity

class PositionFloatingService : Service() {

    companion object {
        private const val TAG = "PositionFloatingService"
        const val CHANNEL_ID = "position_floating_channel"
        const val NOTIFICATION_ID = 1002
        const val ACTION_SHOW = "com.jdhelper.SHOW_POSITION_FLOATING"
        const val ACTION_HIDE = "com.jdhelper.HIDE_POSITION_FLOATING"
        const val ACTION_GET_POSITION = "com.jdhelper.GET_POSITION"
        const val EXTRA_POSITION_X = "position_x"
        const val EXTRA_POSITION_Y = "position_y"

        private var instance: PositionFloatingService? = null

        fun getInstance(): PositionFloatingService? = instance

        fun startService(context: Context) {
            // 检查服务是否已经在运行
            if (instance != null) {
                LogConsole.d(TAG, "服务已在运行，跳过启动")
                return
            }
            val intent = Intent(context, PositionFloatingService::class.java).apply {
                action = ACTION_SHOW
            }
            context.startForegroundService(intent)
        }

        fun stopService(context: Context) {
            val intent = Intent(context, PositionFloatingService::class.java)
            context.stopService(intent)
        }
    }

    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    private var onPositionCallback: ((Int, Int) -> Unit)? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        LogConsole.d(TAG, "onCreate: instance set to this")
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        LogConsole.d(TAG, "onCreate: notification channel created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        LogConsole.d(TAG, "onStartCommand: action=${intent?.action}")
        when (intent?.action) {
            ACTION_SHOW -> {
                LogConsole.d(TAG, "onStartCommand: showing floating window")
                showFloatingWindow()
            }
            ACTION_HIDE -> hideFloatingWindow()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        hideFloatingWindow()
        instance = null
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_position),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_position_desc)
            setShowBadge(false)
        }
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val hideIntent = Intent(this, PositionFloatingService::class.java).apply {
            action = ACTION_HIDE
        }
        val hidePendingIntent = PendingIntent.getService(
            this, 1, hideIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("定位悬浮窗运行中")
            .setSmallIcon(android.R.drawable.ic_menu_myplaces)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "隐藏", hidePendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun showFloatingWindow() {
        LogConsole.d(TAG, "showFloatingWindow: started")
        if (floatingView != null) {
            LogConsole.d(TAG, "showFloatingWindow: already exists, returning")
            return
        }

        startForeground(NOTIFICATION_ID, createNotification())
        LogConsole.d(TAG, "showFloatingWindow: foreground started")

        val layoutInflater = LayoutInflater.from(this)
        floatingView = layoutInflater.inflate(R.layout.floating_position, null)
        LogConsole.d(TAG, "showFloatingWindow: view inflated")

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 200
            y = 400
        }

        floatingView?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(floatingView, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    // 检测是否发生了移动
                    val dx = kotlin.math.abs(event.rawX - initialTouchX)
                    val dy = kotlin.math.abs(event.rawY - initialTouchY)
                    // 如果移动距离小于10像素，认为是点击
                    if (dx < 10 && dy < 10) {
                        val location = IntArray(2)
                        floatingView?.getLocationOnScreen(location)
                        val centerX = location[0] + (floatingView?.width ?: 0) / 2
                        val centerY = location[1] + (floatingView?.height ?: 0) / 2
                        onPositionCallback?.invoke(centerX, centerY)
                    }
                    true
                }
                else -> false
            }
        }

        windowManager.addView(floatingView, params)
    }

    private fun hideFloatingWindow() {
        floatingView?.let {
            windowManager.removeView(it)
        }
        floatingView = null
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    fun setPositionCallback(callback: (Int, Int) -> Unit) {
        onPositionCallback = callback
    }

    fun clearCallback() {
        onPositionCallback = null
    }

    fun getPosition(): Pair<Int, Int>? {
        val view = floatingView ?: return null
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        return Pair(
            location[0] + view.width / 2,
            location[1] + view.height / 2
        )
    }
}