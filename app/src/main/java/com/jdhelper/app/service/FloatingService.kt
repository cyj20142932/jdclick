package com.jdhelper.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.jdhelper.R
import com.jdhelper.app.data.local.TimeSource
import com.jdhelper.app.domain.repository.ClickSettingsRepository
import com.jdhelper.app.ui.MainActivity
import com.jdhelper.app.ui.screens.time.TimeManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class FloatingService : Service() {

    companion object {
        private const val TAG = "FloatingService"
        const val CHANNEL_ID = "floating_service_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_SHOW = "com.jdhelper.SHOW_FLOATING"
        const val ACTION_HIDE = "com.jdhelper.HIDE_FLOATING"
        const val ACTION_TOGGLE = "com.jdhelper.TOGGLE_FLOATING"
        const val ACTION_GET_POSITION = "com.jdhelper.GET_POSITION"
        const val EXTRA_POSITION_X = "position_x"
        const val EXTRA_POSITION_Y = "position_y"

        private var instance: FloatingService? = null

        fun getInstance(): FloatingService? = instance

        fun isRunning(): Boolean = instance != null

        @RequiresApi(Build.VERSION_CODES.O)
        fun startService(context: Context) {
            // 检查服务是否已经在运行
            if (instance != null) {
                LogConsole.d(TAG, "服务已在运行，跳过启动")
                return
            }
            val intent = Intent(context, FloatingService::class.java).apply {
                action = ACTION_SHOW
            }
            context.startForegroundService(intent)
        }

        fun stopService(context: Context) {
            val intent = Intent(context, FloatingService::class.java)
            context.stopService(intent)
        }

        /**
         * 刷新悬浮时钟（先停止再启动）
         */
        @RequiresApi(Build.VERSION_CODES.O)
        fun refreshService(context: Context) {
            val wasRunning = instance != null
            if (wasRunning) {
                stopService(context)
            }
            // 延迟重新启动，确保服务完全停止
            // 增加到 1500ms，确保 onDestroy 完全执行完成
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                if (wasRunning) {
                    startService(context)
                }
            }, 1500)
        }
    }

    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null
    private var timeTextView: TextView? = null
    private var timeUpdateJob: Job? = null
    @Volatile
    private var cachedFormat: SimpleDateFormat = SimpleDateFormat("HH:mm:ss.S", Locale.getDefault())
    private var cachedFontFamily: String = "monospace"
    private var cachedFontSize: Int = 22
    private var cachedFontColor: Int = -1
    private var cachedBgColor: Int = 0xCC333333.toInt()
    private var cachedAlpha: Int = 204
    private var cachedPadding: Int = 16
    private var cachedLetterSpacing: Float = 0f
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Inject
    lateinit var jdTimeService: JdTimeService

    @Inject
    lateinit var timeService: TimeService

    @Inject
    lateinit var clickSettingsRepository: ClickSettingsRepository

    @Inject
    lateinit var timeManager: TimeManager

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    private var isPositionMode = false
    private var onPositionCallback: ((Int, Int) -> Unit)? = null

    // 时间源相关
    private var currentTimeSource: TimeSource = TimeSource.JD
    private var lastJdSyncTime: Long = 0L

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate() {
        super.onCreate()
        instance = this
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()

        // 读取时间源设置
        serviceScope.launch {
            try {
                clickSettingsRepository.getTimeSource().collect { source ->
                    currentTimeSource = source
                    LogConsole.d(TAG, "时间源已设置为: $source")
                }
            } catch (e: Exception) {
                LogConsole.e(TAG, "读取时间源失败", e)
            }
        }

        // 启动时同步京东时间（如果上次同步超过5分钟或未同步）
        serviceScope.launch {
            val now = System.currentTimeMillis()
            if (!jdTimeService.isSynced() || now - lastJdSyncTime > 5 * 60 * 1000) {
                LogConsole.d(TAG, "启动时同步京东时间...")
                jdTimeService.syncJdTime()
                lastJdSyncTime = now
            }
        }

        // 初始化状态管理器
        try {
            FloatingStateManager.getInstance()
        } catch (e: Exception) {
            LogConsole.e(TAG, "FloatingStateManager not initialized yet", e)
        }

        // 缓存毫秒显示位数设置，避免每次更新都读 SharedPreferences
        serviceScope.launch {
            try {
                clickSettingsRepository.getMillisecondDigits().collect { digits ->
                    val pattern = when (digits) {
                        0 -> "HH:mm:ss"
                        1 -> "HH:mm:ss.S"
                        3 -> "HH:mm:ss.SSS"
                        else -> "HH:mm:ss.S"
                    }
                    cachedFormat = SimpleDateFormat(pattern, Locale.getDefault())
                }
            } catch (e: Exception) {
                LogConsole.e(TAG, "读取毫秒格式失败", e)
            }
        }

        // 收集时钟外观设置
        serviceScope.launch {
            try {
                clickSettingsRepository.getClockFontFamily().collect { family ->
                    cachedFontFamily = family
                    withContext(Dispatchers.Main) {
                        timeTextView?.typeface = android.graphics.Typeface.create(family, android.graphics.Typeface.BOLD)
                    }
                }
            } catch (e: Exception) {
                LogConsole.e(TAG, "读取字体设置失败", e)
            }
        }
        serviceScope.launch {
            try {
                clickSettingsRepository.getClockFontSize().collect { size ->
                    cachedFontSize = size
                    withContext(Dispatchers.Main) {
                        timeTextView?.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, size.toFloat())
                    }
                }
            } catch (e: Exception) {
                LogConsole.e(TAG, "读取字体大小设置失败", e)
            }
        }
        serviceScope.launch {
            try {
                clickSettingsRepository.getClockFontColor().collect { color ->
                    cachedFontColor = color
                    withContext(Dispatchers.Main) {
                        timeTextView?.setTextColor(color)
                    }
                }
            } catch (e: Exception) {
                LogConsole.e(TAG, "读取字体颜色设置失败", e)
            }
        }
        serviceScope.launch {
            try {
                clickSettingsRepository.getClockBgColor().collect { color ->
                    cachedBgColor = color
                    withContext(Dispatchers.Main) {
                        floatingView?.background?.setTint(color)
                    }
                }
            } catch (e: Exception) {
                LogConsole.e(TAG, "读取背景颜色设置失败", e)
            }
        }
        serviceScope.launch {
            try {
                clickSettingsRepository.getClockAlpha().collect { alpha ->
                    cachedAlpha = alpha
                    withContext(Dispatchers.Main) {
                        floatingView?.alpha = alpha / 255f
                    }
                }
            } catch (e: Exception) {
                LogConsole.e(TAG, "读取透明度设置失败", e)
            }
        }
        serviceScope.launch {
            try {
                clickSettingsRepository.getClockPadding().collect { padding ->
                    cachedPadding = padding
                    withContext(Dispatchers.Main) {
                        val paddingPx = (padding * resources.displayMetrics.density).toInt()
                        floatingView?.setPadding(paddingPx, paddingPx, paddingPx, paddingPx)
                    }
                }
            } catch (e: Exception) {
                LogConsole.e(TAG, "读取间距设置失败", e)
            }
        }
        serviceScope.launch {
            try {
                clickSettingsRepository.getClockLetterSpacing().collect { spacing ->
                    cachedLetterSpacing = spacing
                    withContext(Dispatchers.Main) {
                        timeTextView?.letterSpacing = spacing
                    }
                }
            } catch (e: Exception) {
                LogConsole.e(TAG, "读取字间距设置失败", e)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW -> showFloatingWindow()
            ACTION_HIDE -> hideFloatingWindow()
            ACTION_TOGGLE -> toggleFloatingWindow()
            ACTION_GET_POSITION -> {
                val x = intent.getIntExtra(EXTRA_POSITION_X, 0)
                val y = intent.getIntExtra(EXTRA_POSITION_Y, 0)
                onPositionCallback?.invoke(x, y)
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        hideFloatingWindow()
        timeUpdateJob?.cancel()
        instance = null
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_floating),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_floating_desc)
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

        val hideIntent = Intent(this, FloatingService::class.java).apply {
            action = ACTION_HIDE
        }
        val hidePendingIntent = PendingIntent.getService(
            this, 1, hideIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("悬浮时钟运行中")
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "隐藏", hidePendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun showFloatingWindow() {
        if (floatingView != null) return

        startForeground(NOTIFICATION_ID, createNotification())

        val layoutInflater = LayoutInflater.from(this)
        floatingView = layoutInflater.inflate(R.layout.floating_clock, null)
        timeTextView = floatingView?.findViewById(R.id.time_text)

        // 应用缓存的时钟外观设置
        timeTextView?.let { tv ->
            tv.typeface = android.graphics.Typeface.create(cachedFontFamily, android.graphics.Typeface.BOLD)
            tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, cachedFontSize.toFloat())
            tv.setTextColor(cachedFontColor)
            tv.letterSpacing = cachedLetterSpacing
        }
        floatingView?.let { v ->
            v.alpha = cachedAlpha / 255f
            val paddingPx = (cachedPadding * resources.displayMetrics.density).toInt()
            v.setPadding(paddingPx, paddingPx, paddingPx, paddingPx)
            v.background?.setTint(cachedBgColor)
        }

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
            x = 100
            y = 200
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
                    if (isPositionMode) {
                        val location = IntArray(2)
                        floatingView?.getLocationOnScreen(location)
                        onPositionCallback?.invoke(location[0] + floatingView!!.width / 2, location[1] + floatingView!!.height / 2)
                    }
                    true
                }
                else -> false
            }
        }

        floatingView?.setOnClickListener {
            if (!isPositionMode) {
                // 检查悬浮窗权限
                if (!Settings.canDrawOverlays(this)) {
                    ToastUtils.show(this, "请开启悬浮窗权限")
                    // 跳转到系统悬浮窗权限设置
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    startActivity(intent)
                    return@setOnClickListener
                }
            }
        }

        windowManager.addView(floatingView, params)
        startTimeUpdate()
    }

    private fun hideFloatingWindow() {
        timeUpdateJob?.cancel()
        floatingView?.let {
            windowManager.removeView(it)
        }
        floatingView = null
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun toggleFloatingWindow() {
        if (floatingView != null) {
            hideFloatingWindow()
        } else {
            showFloatingWindow()
        }
    }

    private fun startTimeUpdate() {
        timeUpdateJob = CoroutineScope(Dispatchers.Main).launch { timeManager.currentTime.collect { time ->
                updateTimeDisplay(time)
            }
        }
    }

    private fun updateTimeDisplay(displayTime: Long) {
        timeTextView?.text = cachedFormat.format(Date(displayTime))
    }

    fun setPositionMode(enabled: Boolean, callback: ((Int, Int) -> Unit)? = null) {
        isPositionMode = enabled
        onPositionCallback = callback
        floatingView?.setBackgroundColor(
            if (enabled) Color.argb(100, 0, 255, 0) else Color.TRANSPARENT
        )
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