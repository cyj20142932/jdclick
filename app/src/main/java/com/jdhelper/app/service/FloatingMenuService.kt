package com.jdhelper.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import androidx.core.app.NotificationCompat
import com.jdhelper.R
import com.jdhelper.app.data.local.TimeSource
import com.jdhelper.app.data.local.GiftClickHistory
import com.jdhelper.app.data.local.GiftClickHistoryDao
import com.jdhelper.app.domain.model.GiftClickStage
import com.jdhelper.app.domain.model.StageTiming
import com.jdhelper.app.domain.repository.ClickSettingsRepository
import com.jdhelper.app.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.concurrent.locks.LockSupport
import javax.inject.Inject

@AndroidEntryPoint
class FloatingMenuService : Service() {

    @Inject
    lateinit var timedClickManager: TimedClickManager

    @Inject
    lateinit var clickSettingsRepository: ClickSettingsRepository

    @Inject
    lateinit var giftClickHistoryDao: GiftClickHistoryDao

    @Inject
    lateinit var floatingStateManager: FloatingStateManager

    @Inject
    lateinit var jdTimeService: JdTimeService

    @Inject
    lateinit var timeService: TimeService

    companion object {
        private const val TAG = "FloatingMenuService"
        const val CHANNEL_ID = "floating_menu_channel"
        const val NOTIFICATION_ID = 1002
        const val ACTION_SHOW = "com.jdhelper.SHOW_MENU"
        const val ACTION_HIDE = "com.jdhelper.HIDE_MENU"
        const val ACTION_TIME_SYNCED = "com.jdhelper.TIME_SYNCED"

        private var instance: FloatingMenuService? = null

        fun getInstance(): FloatingMenuService? = instance

        fun isRunning(): Boolean = instance != null

        /**
         * 通知所有悬浮菜单时间已同步
         */
        fun notifyTimeSynced(context: Context) {
            val intent = Intent(ACTION_TIME_SYNCED).apply {
                setPackage(context.packageName)
            }
            context.sendBroadcast(intent)
        }

        @JvmStatic
        fun startService(context: Context) {
            // 检查服务是否已经在运行
            if (instance != null) {
                LogConsole.d(TAG, "服务已在运行，跳过启动")
                return
            }
            val intent = Intent(context, FloatingMenuService::class.java).apply {
                action = ACTION_SHOW
            }
            // Android 8.0 (API 26) 及以上使用 startForegroundService
            // 低版本使用 startService（minSdk=24 理论上不需要，但为安全起见）
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                @Suppress("DEPRECATION")
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, FloatingMenuService::class.java)
            context.stopService(intent)
        }
    }

    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    // 任务状态变量（供停止按钮使用）
    private var isLoopRunning = false
    private var loopJob: Job? = null
    private var isGiftRunning = false
    private var giftJob: Job? = null
    private var isClockRunning = false  // 时钟是否在运行

    // 按钮引用，用于状态切换
    private var btnClock: ImageButton? = null
    private var btnLock: ImageButton? = null
    private var btnGift: ImageButton? = null
    private var btnPlay: ImageButton? = null

    // 新增视图引用 - 状态指示
    private var statusIndicator: View? = null

    // 状态指示点
    private var indicatorClock: View? = null
    private var indicatorLoop: View? = null
    private var indicatorGift: View? = null
    private var indicatorTimed: View? = null

    // 使用类级别的协程作用域，避免重复创建
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private val serviceIoScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // 时间源相关
    private var currentTimeSource: TimeSource = TimeSource.JD
    private var lastJdSyncTime: Long = 0L

    /** 送礼阶段配置（编译时参数化，后续可改为动态加载） */
    private val giftClickStages = listOf(
        GiftClickStage(
            name = "一键送礼",
            keywords = listOf("一键送礼", "送给"),
            timing = StageTiming.Timed,
            delayAfterClickMs = 1000,
        ),
        GiftClickStage(
            name = "付款并赠送",
            keywords = listOf("付款并赠送"),
            timing = StageTiming.Poll(timeoutMs = 3000, intervalMs = 100),
            delayAfterClickMs = 1000,
        ),
    )

    @Suppress("DEPRECATION")
    override fun onCreate() {
        super.onCreate()
        instance = this
        LogConsole.d(TAG, "onCreate: 开始初始化")
        try {
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            createNotificationChannel()

            // 注册时间同步广播接收器
            val timeSyncReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent?.action == ACTION_TIME_SYNCED) {
                        LogConsole.d(TAG, "收到时间同步广播，更新显示")
                        updateNtpStatusDisplay()
                    }
                }
            }
            val filter = IntentFilter(ACTION_TIME_SYNCED)
            registerReceiver(timeSyncReceiver, filter)

            // 读取时间源设置
            serviceIoScope.launch {
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
            serviceIoScope.launch {
                val now = System.currentTimeMillis()
                if (!jdTimeService.isSynced() || now - lastJdSyncTime > 5 * 60 * 1000) {
                    LogConsole.d(TAG, "启动时同步京东时间...")
                    jdTimeService.syncJdTime()
                    lastJdSyncTime = now
                }
            }

            LogConsole.d(TAG, "onCreate: 初始化完成")
        } catch (e: Exception) {
            LogConsole.e(TAG, "onCreate: 初始化失败", e)
        }
    }

    /**
     * 更新所有按钮状态图标
     */
    private fun updateAllButtonStates() {
        // 更新时钟按钮图标（运行中用不同颜色/样式表示）
        btnClock?.setImageResource(
            if (FloatingService.isRunning())
                android.R.drawable.ic_menu_recent_history
            else
                android.R.drawable.ic_menu_recent_history
        )

        // 更新锁按钮图标（循环任务）
        btnLock?.setImageResource(
            if (isLoopRunning)
                android.R.drawable.ic_lock_lock
            else
                android.R.drawable.ic_menu_manage
        )

        // 更新礼物按钮图标
        btnGift?.setImageResource(
            if (isGiftRunning)
                android.R.drawable.ic_popup_sync
            else
                android.R.drawable.ic_menu_send
        )

        // 更新播放按钮图标（定时点击）
        btnPlay?.setImageResource(
            if (timedClickManager.isRunning())
                android.R.drawable.ic_media_pause
            else
                android.R.drawable.ic_media_play
        )
    }

    /**
     * 更新状态指示条颜色
     */
    private fun updateNtpStatusDisplay() {
        statusIndicator?.setBackgroundColor(
            if (timeService.isSynced()) Color.parseColor("#4CAF50")
            else Color.parseColor("#888888")
        )
    }

    /**
     * 更新指定任务的状态指示点
     */
    private fun updateTaskIndicator(indicator: View?, isRunning: Boolean) {
        indicator?.visibility = if (isRunning) View.VISIBLE else View.GONE
    }

    /**
     * 更新整体状态指示条
     */
    private fun updateOverallStatus() {
        val isAnyRunning = isLoopRunning || isGiftRunning || timedClickManager.isRunning()
        statusIndicator?.setBackgroundColor(
            if (isAnyRunning) Color.parseColor("#4CAF50")
            else Color.parseColor("#888888")
        )
    }

    /**
     * 更新时钟按钮图标
     */
    private fun updateClockButtonState() {
        isClockRunning = FloatingService.isRunning()
        btnClock?.setImageResource(
            if (isClockRunning)
                android.R.drawable.ic_menu_recent_history
            else
                android.R.drawable.ic_menu_recent_history
        )
    }

    /**
     * 更新锁按钮图标
     */
    private fun updateLockButtonState() {
        btnLock?.setImageResource(
            if (isLoopRunning)
                android.R.drawable.ic_lock_lock
            else
                android.R.drawable.ic_menu_manage
        )
    }

    /**
     * 更新礼物按钮图标
     */
    private fun updateGiftButtonState() {
        btnGift?.setImageResource(
            if (isGiftRunning)
                android.R.drawable.ic_popup_sync
            else
                android.R.drawable.ic_menu_send
        )
    }

    /**
     * 更新播放按钮图标
     */
    private fun updatePlayButtonState() {
        btnPlay?.setImageResource(
            if (timedClickManager.isRunning())
                android.R.drawable.ic_media_pause
            else
                android.R.drawable.ic_media_play
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        LogConsole.d(TAG, "onStartCommand: action=${intent?.action}, startId=$startId")
        try {
            when (intent?.action) {
                ACTION_SHOW -> showFloatingMenu()
                ACTION_HIDE -> hideFloatingMenu()
            }
        } catch (e: Exception) {
            LogConsole.e(TAG, "onStartCommand: 处理失败", e)
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        serviceIoScope.cancel()
        hideFloatingMenu()
        instance = null
    }

    @Suppress("DEPRECATION")
    private fun createNotificationChannel() {
        // Android 8.0 (API 26) 及以上需要创建 NotificationChannel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_floating),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "悬浮菜单服务"
                setShowBadge(false)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val hideIntent = Intent(this, FloatingMenuService::class.java).apply {
            action = ACTION_HIDE
        }
        val hidePendingIntent = PendingIntent.getService(
            this, 1, hideIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("悬浮菜单运行中")
            .setSmallIcon(android.R.drawable.ic_menu_sort_by_size)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "隐藏", hidePendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun showFloatingMenu() {
        try {
            LogConsole.d(TAG, "showFloatingMenu: 开始显示悬浮菜单")
            if (floatingView != null) {
                LogConsole.d(TAG, "showFloatingMenu: 视图已存在，跳过")
                return
            }

            startForeground(NOTIFICATION_ID, createNotification())
            LogConsole.d(TAG, "showFloatingMenu: 前台服务已启动")

            val layoutInflater = LayoutInflater.from(this)
            floatingView = layoutInflater.inflate(R.layout.floating_menu, null)
            LogConsole.d(TAG, "showFloatingMenu: 布局已填充")

            // 获取按钮引用
            btnClock = floatingView?.findViewById(R.id.btn_clock)
            btnLock = floatingView?.findViewById(R.id.btn_lock)
            btnGift = floatingView?.findViewById(R.id.btn_gift)
            btnPlay = floatingView?.findViewById(R.id.btn_play)

            // 获取新视图引用 - 状态指示
            statusIndicator = floatingView?.findViewById(R.id.status_indicator)

            // 状态指示点
            indicatorClock = floatingView?.findViewById(R.id.indicator_clock)
            indicatorLoop = floatingView?.findViewById(R.id.indicator_loop)
            indicatorGift = floatingView?.findViewById(R.id.indicator_gift)
            indicatorTimed = floatingView?.findViewById(R.id.indicator_timed)

            // 初始化显示NTP状态
            updateNtpStatusDisplay()

            // 初始化按钮状态
            updateAllButtonStates()

            // 时钟按钮 - 根据当前时间源同步
        floatingView?.findViewById<ImageButton>(R.id.btn_clock)?.setOnClickListener {
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    // 获取当前时间源
                    val currentSource = timeService.getCurrentTimeSource()

                    // 同步当前时间源的时间
                    val synced = withContext(Dispatchers.IO) {
                        timeService.syncTime()
                    }

                    if (synced) {
                        val offset = timeService.getTimeOffset()
                        val offsetText = if (offset >= 0) "+${offset}ms" else "${offset}ms"
                        if (currentSource == TimeSource.JD) {
                            ToastUtils.show(this@FloatingMenuService, "京东时间同步成功: $offsetText")
                        } else {
                            ToastUtils.show(this@FloatingMenuService, "NTP时间同步成功: $offsetText")
                        }
                    } else {
                        if (currentSource == TimeSource.JD) {
                            ToastUtils.show(this@FloatingMenuService, "京东时间同步失败，请检查网络")
                        } else {
                            ToastUtils.show(this@FloatingMenuService, "NTP时间同步失败，请检查网络")
                        }
                    }

                    // 同步后更新NTP状态显示
                    updateNtpStatusDisplay()

                    // 2. 检查悬浮时钟是否已显示，如果是则刷新，否则启动
                    if (FloatingService.isRunning()) {
                        // 刷新悬浮时钟
                    } else {
                        FloatingService.startService(this@FloatingMenuService)
                    }

                    // 3. 更新时钟按钮图标（立即切换）
                    updateClockButtonState()

                    ToastUtils.show(this@FloatingMenuService, "时间已刷新")
                } catch (e: Exception) {
                    LogConsole.e(TAG, "时钟控制失败", e)
                    ToastUtils.show(this@FloatingMenuService, "操作失败: ${e.message}")
                }
            }
        }

        // 锁按钮 - 循环点击模式开关
        floatingView?.findViewById<ImageButton>(R.id.btn_lock)?.setOnClickListener {
            if (isLoopRunning) {
                // 停止循环任务
                loopJob?.cancel()
                loopJob = null
                isLoopRunning = false
                floatingStateManager.notifyTaskStateChanged(FloatingStateManager.TASK_TYPE_LOOP, false)
                FloatingService.stopService(this@FloatingMenuService)
                // 更新锁按钮图标
                updateLockButtonState()
                // 更新状态指示点
                updateTaskIndicator(indicatorLoop, false)
                updateOverallStatus()
                ToastUtils.show(this@FloatingMenuService, "循环任务已停止")
            } else {
                // 启动循环任务
                CoroutineScope(Dispatchers.Main).launch {
                    isLoopRunning = true
                    floatingStateManager.notifyTaskStateChanged(FloatingStateManager.TASK_TYPE_LOOP, true)
                    // 更新锁按钮图标
                    updateLockButtonState()
                    // 更新状态指示点
                    updateTaskIndicator(indicatorLoop, true)
                    updateOverallStatus()
                    ToastUtils.show(this@FloatingMenuService, "循环任务已启动")

                    // 启动循环点击
                    loopJob = launch {
                        while (isLoopRunning) {
                            try {
                                val accessibilityService = AccessibilityClickService.getInstance()
                                if (accessibilityService == null) {
                                    LogConsole.w(TAG, "无障碍服务未连接")
                                    delay(1000)
                                    return@launch
                                }

                                // 1. 先检查是否有弹窗（客服/店铺/分类）
                                val hasPopup = withContext(Dispatchers.IO) {
                                    accessibilityService.hasPopup()
                                }

                                if (hasPopup) {
                                    // 有弹窗，点击返回
                                    LogConsole.d(TAG, "检测到弹窗，执行返回")
                                    accessibilityService.performBack()
                                    delay(6000) // 等待6秒
                                }

                                // 2. 查找并点击目标按钮
                                val clickResult = withContext(Dispatchers.IO) {
                                    accessibilityService.findAndClickTarget()
                                }

                                if (clickResult == null) {
                                    // 没找到按钮，执行上滑刷新
                                    LogConsole.d(TAG, "未找到目标按钮，执行上滑")
                                    accessibilityService.performSwipeUp()
                                    delay(1000)
                                } else if (clickResult) {
                                    // 如果按钮包含"浏览"，等待9秒后返回
                                    LogConsole.d(TAG, "点击了浏览按钮，等待9秒")
                                    delay(9000)
                                    accessibilityService.performBack()
                                    delay(800)

                                    // 检查是否有弹窗
                                    val popupAfterBrowse = withContext(Dispatchers.IO) {
                                        accessibilityService.hasPopup()
                                    }
                                    if (popupAfterBrowse) {
                                        accessibilityService.performBack()
                                    }
                                }

                                // 3. 等待2秒后继续循环
                                delay(2000)

                            } catch (e: Exception) {
                                LogConsole.e(TAG, "循环任务异常", e)
                                delay(1000)
                            }
                        }
                    }
                }
            }
        }

        // 礼物按钮 - 整分两阶段点击模式
        // 第一阶段：点击后立即查找"一键送礼"按钮，找到后等待整分时间点击
        // 第二阶段：点击后每100ms查找"付款并赠送"，超时3秒退出

        floatingView?.findViewById<ImageButton>(R.id.btn_gift)?.setOnClickListener {
            if (isGiftRunning) {
                // 停止现有任务
                giftJob?.cancel()
                giftJob = null
                isGiftRunning = false
                floatingStateManager.notifyTaskStateChanged(FloatingStateManager.TASK_TYPE_GIFT, false)
                FloatingService.stopService(this)
                // 更新礼物按钮图标
                updateGiftButtonState()
                // 更新状态指示点
                updateTaskIndicator(indicatorGift, false)
                updateOverallStatus()
                ToastUtils.show(this, "礼物任务已停止")
            } else {
                // 启动礼物任务
                CoroutineScope(Dispatchers.Main).launch {
                    isGiftRunning = true
                    floatingStateManager.notifyTaskStateChanged(FloatingStateManager.TASK_TYPE_GIFT, true)
                    // 更新礼物按钮图标
                    updateGiftButtonState()
                    // 更新状态指示点
                    updateTaskIndicator(indicatorGift, true)
                    updateOverallStatus()
                    ToastUtils.show(this@FloatingMenuService, "礼物任务已启动")

                    // 1. NTP 同步
                    val synced = withContext(Dispatchers.IO) {
                        timeService.syncTime()
                    }
                    if (!synced) {
                        ToastUtils.show(this@FloatingMenuService, "时间同步失败，使用本地时间")
                    }

                    // 同步后更新状态显示
                    updateNtpStatusDisplay()

                    // 2. 复用已打开的悬浮时钟，如果没有打开则启动
                    if (!FloatingService.isRunning()) {
                        FloatingService.startService(this@FloatingMenuService)
                    }

                    // 3. 启动礼物点击逻辑
                    giftJob = launch {
                        try {
                            executeGiftWorkflow(giftClickStages)
                            isGiftRunning = false
                            floatingStateManager.notifyTaskStateChanged(FloatingStateManager.TASK_TYPE_GIFT, false)
                            // 更新礼物按钮图标
                            withContext(Dispatchers.Main) {
                                updateGiftButtonState()
                                // 更新状态指示点
                                updateTaskIndicator(indicatorGift, false)
                                updateOverallStatus()
                            }
                            FloatingService.stopService(this@FloatingMenuService)
                        } catch (e: Exception) {
                            LogConsole.e(TAG, "礼物任务异常", e)
                            isGiftRunning = false
                            floatingStateManager.notifyTaskStateChanged(FloatingStateManager.TASK_TYPE_GIFT, false)
                            // 更新礼物按钮图标
                            withContext(Dispatchers.Main) {
                                updateGiftButtonState()
                                // 更新状态指示点
                                updateTaskIndicator(indicatorGift, false)
                                updateOverallStatus()
                            }
                            FloatingService.stopService(this@FloatingMenuService)
                            withContext(Dispatchers.Main) {
                                ToastUtils.show(this@FloatingMenuService, "礼物任务终止: ${e.message}")
                            }
                        }
                    }
                }
            }
        }

        // 设置按钮点击事件
        floatingView?.findViewById<ImageButton>(R.id.btn_play)?.setOnClickListener {
            // 先停止现有任务
            timedClickManager.stop()
            // 如果没有运行悬浮时钟，则记录需要启动
            val shouldStartClock = !FloatingService.isRunning()
            if (shouldStartClock) {
                FloatingService.stopService(this)
            }
            // 更新播放按钮图标
            updatePlayButtonState()

            // 检查无障碍服务是否已开启
            val accessibilityService = AccessibilityClickService.getInstance()
            if (accessibilityService == null) {
                ToastUtils.show(this@FloatingMenuService, "请先开启无障碍服务")
                // 跳转到无障碍服务设置页面
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
                return@setOnClickListener
            }

            serviceScope.launch {
                try {
                    // 1. 查找目标按钮
                    val buttonFinder = ButtonFinder(applicationContext)
                    val position = withContext(Dispatchers.IO) {
                        accessibilityService.findTargetButton()
                    }

                    val targetX: Int
                    val targetY: Int

                    if (position != null) {
                        targetX = position.x
                        targetY = position.y
                        ToastUtils.show(this@FloatingMenuService, "已找到目标按钮，位置: ($targetX, $targetY)")
                    } else {
                        val defaultPos = buttonFinder.getDefaultPosition()
                        targetX = defaultPos.x
                        targetY = defaultPos.y
                        ToastUtils.show(this@FloatingMenuService, "未找到目标按钮，使用默认位置: ($targetX, $targetY)")
                    }

                    // 2. NTP校准时间
                    val synced = withContext(Dispatchers.IO) {
                        timeService.syncTime()
                    }
                    if (!synced) {
                        ToastUtils.show(this@FloatingMenuService, "时间未校准，使用本地时间")
                    }

                    // 同步后更新状态显示
                    updateNtpStatusDisplay()

                    // 3. 复用已打开的悬浮时钟，如果没有打开则启动
                    if (shouldStartClock) {
                        FloatingService.startService(this@FloatingMenuService)
                    }

                    // 4. 更新播放按钮图标为暂停状态
                    updatePlayButtonState()

                    // 更新状态指示点
                    updateTaskIndicator(indicatorTimed, true)
                    updateOverallStatus()
                    floatingStateManager.notifyTaskStateChanged(FloatingStateManager.TASK_TYPE_TIMED, true)

                    // 5. 获取延迟设置并启动定时点击
                    val delay = clickSettingsRepository.getDelayMillis().first()
                    timedClickManager.start(targetX, targetY, delay)
                    timedClickManager.setCallback(object : TimedClickManager.ClickCallback {
                        override fun onClickComplete(actualClickTime: Long, plannedClickTime: Long, delayMillis: Double, actualDiff: Long) {
                            // 记录历史
                            serviceIoScope.launch {
                                val shouldRecord = clickSettingsRepository.getRecordHistory().first()
                                if (shouldRecord) {
                                    val localClickTime = System.currentTimeMillis()
                                    val timeSource = clickSettingsRepository.getTimeSource().first()
                                    LogConsole.d(TAG, "保存历史记录: stage=0, timeSource=${timeSource.name}")
                                    giftClickHistoryDao.insert(
                                        GiftClickHistory(
                                            stage = 0, // 0 表示启动按钮的定时点击
                                            ntpClickTime = actualClickTime,
                                            localClickTime = localClickTime,
                                            targetTime = plannedClickTime,
                                            delayMillis = delayMillis,
                                            actualDiff = actualDiff,
                                            timeSource = timeSource.name
                                        )
                                    )
                                }
                            }

                            // 使用 Handler 切换到主线程执行
                            Handler(Looper.getMainLooper()).post {
                                ToastUtils.show(this@FloatingMenuService, "已执行点击")
                                floatingStateManager.notifyTaskStateChanged(FloatingStateManager.TASK_TYPE_TIMED, false)
                                // 点击完成后停止悬浮时钟
                                FloatingService.stopService(this@FloatingMenuService)
                                // 更新播放按钮图标为播放状态
                                updatePlayButtonState()
                                // 更新状态指示点
                                updateTaskIndicator(indicatorTimed, false)
                                updateOverallStatus()
                            }
                        }
                    })

                } catch (e: Exception) {
                    LogConsole.e(TAG, "启动任务失败", e)
                    ToastUtils.show(this@FloatingMenuService, "启动任务失败: ${e.message}")
                }
            }
        }

        floatingView?.findViewById<ImageButton>(R.id.btn_stop)?.setOnClickListener {
            // 停止循环任务
            loopJob?.cancel()
            loopJob = null
            isLoopRunning = false

            // 停止礼物任务
            giftJob?.cancel()
            giftJob = null
            isGiftRunning = false

            // 停止定时点击任务
            timedClickManager.stop()

            // 停止悬浮时钟
            FloatingService.stopService(this)

            // 通知状态管理器所有任务已停止
            floatingStateManager.notifyTaskStateChanged(FloatingStateManager.TASK_TYPE_LOOP, false)
            floatingStateManager.notifyTaskStateChanged(FloatingStateManager.TASK_TYPE_GIFT, false)
            floatingStateManager.notifyTaskStateChanged(FloatingStateManager.TASK_TYPE_TIMED, false)

            // 更新所有状态指示点
            updateTaskIndicator(indicatorLoop, false)
            updateTaskIndicator(indicatorGift, false)
            updateTaskIndicator(indicatorTimed, false)
            updateOverallStatus()
            updateAllButtonStates()

            ToastUtils.show(this, "所有任务已停止")
        }

        floatingView?.findViewById<ImageButton>(R.id.btn_close)?.setOnClickListener {
            // 先停止所有任务
            loopJob?.cancel()
            loopJob = null
            isLoopRunning = false

            giftJob?.cancel()
            giftJob = null
            isGiftRunning = false

            timedClickManager.stop()

            // 停止悬浮时钟
            FloatingService.stopService(this)

            // 更新所有状态指示点
            updateTaskIndicator(indicatorLoop, false)
            updateTaskIndicator(indicatorGift, false)
            updateTaskIndicator(indicatorTimed, false)
            updateOverallStatus()

            // 然后关闭悬浮菜单
            hideFloatingMenu()
            stopService(this)
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
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            x = 0
            y = 0
        }

        // 拖拽功能 - 通过拖拽按钮实现
        floatingView?.findViewById<ImageButton>(R.id.btn_drag)?.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    view.parent.requestDisallowInterceptTouchEvent(true)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(floatingView, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    view.parent.requestDisallowInterceptTouchEvent(false)
                    true
                }
                else -> false
            }
        }

        // 整个菜单也可以拖拽
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
                MotionEvent.ACTION_UP -> true
                else -> false
            }
        }

        windowManager.addView(floatingView, params)
        LogConsole.d(TAG, "showFloatingMenu: 完成")
    } catch (e: Exception) {
            LogConsole.e(TAG, "showFloatingMenu: 失败", e)
        }
    }

    private fun hideFloatingMenu() {
        floatingView?.let {
            windowManager.removeView(it)
        }
        floatingView = null
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    /**
     * 执行送礼工作流 — 按阶段列表依次执行
     * 任一阶段失败（未找到按钮 / 超时）则终止后续
     */
    private suspend fun CoroutineScope.executeGiftWorkflow(stages: List<GiftClickStage>) {
        LogConsole.d(TAG, "=== 礼物工作流开始，共 ${stages.size} 个阶段 ===")

        val clickDelay = clickSettingsRepository.getDelayMillis().first().toLong()

        for ((index, stage) in stages.withIndex()) {
            val stageIndex = index + 1
            LogConsole.d(TAG, "阶段 $stageIndex/${stages.size}: ${stage.name}")

            val success = executeStage(stage, clickDelay, stageIndex)
            if (!success) {
                LogConsole.w(TAG, "阶段 $stageIndex 失败: ${stage.name}，终止工作流")
                return@executeGiftWorkflow
            }

            delay(stage.delayAfterClickMs)
        }

        withContext(Dispatchers.Main) {
            ToastUtils.show(this@FloatingMenuService, "礼物任务已完成")
        }
        LogConsole.d(TAG, "=== 礼物工作流成功完成 ===")
    }

    /**
     * 执行单个阶段，返回是否成功
     */
    private suspend fun CoroutineScope.executeStage(
        stage: GiftClickStage,
        clickDelay: Long,
        stageIndex: Int,
    ): Boolean {
        return when (stage.timing) {
            is StageTiming.Timed -> executeTimedStage(stage.keywords, clickDelay, stageIndex)
            is StageTiming.Poll -> executePollStage(stage.keywords, stage.timing, stageIndex)
        }
    }

    /**
     * 定时阶段 — 先找到按钮，再等待整分时间后点击
     */
    private suspend fun CoroutineScope.executeTimedStage(
        keywords: List<String>,
        clickDelay: Long,
        stageIndex: Int,
    ): Boolean {
        val button = withContext(Dispatchers.IO) {
            AccessibilityClickService.getInstance()?.findButtonByKeywords(keywords)
        } ?: run {
            LogConsole.w(TAG, "阶段 $stageIndex 未找到按钮")
            withContext(Dispatchers.Main) {
                ToastUtils.show(this@FloatingMenuService, "未找到: 阶段${stageIndex}")
            }
            return false
        }

        withContext(Dispatchers.Main) {
            ToastUtils.show(this@FloatingMenuService, "找到${stageIndex}阶段按钮，等待整分点击")
        }

        delay(500)

        val ntpTime = timeService.getCurrentTime()
        val calendar = Calendar.getInstance().apply {
            timeInMillis = ntpTime
            add(Calendar.MINUTE, 1)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val targetTime = calendar.timeInMillis + clickDelay.toLong()
        val timeToTarget = targetTime - ntpTime

        if (timeToTarget <= 0) {
            LogConsole.w(TAG, "阶段 $stageIndex 目标时间已过，跳过")
            return false
        }

        // 分段等待：协程 delay + LockSupport.parkNanos 精确等待
        val spinThreshold = 50L
        if (timeToTarget > spinThreshold) {
            delay(timeToTarget - spinThreshold)
        }

        var currentTime = timeService.getCurrentTime()
        while (isActive && currentTime < targetTime) {
            val remaining = targetTime - currentTime
            if (remaining > 0) {
                val parkTime = if (remaining > 5) minOf(remaining, 10_000_000L) else 0
                if (parkTime > 0) LockSupport.parkNanos(parkTime)
            }
            currentTime = timeService.getCurrentTime()
        }

        // 执行点击
        withContext(Dispatchers.IO) {
            AccessibilityClickService.getInstance()?.performGlobalClick(button.x, button.y)
        }
        val clickTime = timeService.getCurrentTime()

        recordHistory(
            stage = stageIndex,
            ntpClickTime = clickTime,
            localClickTime = System.currentTimeMillis(),
            targetTime = targetTime,
            clickDelay = clickDelay.toDouble(),
            actualDiff = clickTime - targetTime,
        )
        return true
    }

    /**
     * 轮询阶段 — 循环查找按钮直到超时，找到即点
     */
    private suspend fun CoroutineScope.executePollStage(
        keywords: List<String>,
        timing: StageTiming.Poll,
        stageIndex: Int,
    ): Boolean {
        val startTime = timeService.getCurrentTime()

        // 初始等待（UI需要时间过渡）
        delay(300)

        while (timeService.getCurrentTime() - startTime < timing.timeoutMs) {
            val button = withContext(Dispatchers.IO) {
                AccessibilityClickService.getInstance()?.findButtonByKeywords(keywords)
            }

            if (button != null) {
                LogConsole.d(TAG, "阶段 $stageIndex 找到按钮 (${button.x}, ${button.y})")
                withContext(Dispatchers.IO) {
                    AccessibilityClickService.getInstance()?.performGlobalClick(button.x, button.y)
                }
                val clickTime = timeService.getCurrentTime()

                recordHistory(
                    stage = stageIndex,
                    ntpClickTime = clickTime,
                    localClickTime = System.currentTimeMillis(),
                    targetTime = clickTime,
                    clickDelay = 0.0,
                    actualDiff = 0,
                )
                return true
            }

            delay(timing.intervalMs)
        }

        // 超时未找到
        LogConsole.w(TAG, "阶段 $stageIndex 超时未找到按钮")
        withContext(Dispatchers.Main) {
            ToastUtils.show(this@FloatingMenuService, "未找到: 阶段${stageIndex}")
        }
        return false
    }

    /**
     * 记录历史
     */
    private suspend fun recordHistory(
        stage: Int,
        ntpClickTime: Long,
        localClickTime: Long,
        targetTime: Long,
        clickDelay: Double,
        actualDiff: Long,
    ) {
        val shouldRecord = clickSettingsRepository.getRecordHistory().first()
        if (!shouldRecord) return

        val timeSource = clickSettingsRepository.getTimeSource().first()
        LogConsole.d(TAG, "保存历史记录: stage=$stage, timeSource=${timeSource.name}")
        giftClickHistoryDao.insert(
            GiftClickHistory(
                stage = stage,
                ntpClickTime = ntpClickTime,
                localClickTime = localClickTime,
                targetTime = targetTime,
                delayMillis = clickDelay,
                actualDiff = actualDiff,
                timeSource = timeSource.name,
            )
        )
    }
}