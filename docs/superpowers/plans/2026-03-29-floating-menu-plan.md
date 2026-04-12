# 左侧悬浮菜单实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在应用中新增一个左侧悬浮菜单，包含播放、停止、关闭三个图标按钮，支持拖拽定位，主界面可控制显示/隐藏

**Architecture:** 创建独立的 FloatingMenuService，使用 WindowManager 添加悬浮视图，实现拖拽功能。主界面通过服务控制悬浮菜单的显示/隐藏。

**Tech Stack:** Kotlin, Android Service, WindowManager, Jetpack Compose

---

### Task 1: 创建悬浮菜单布局文件

**Files:**
- Create: `app/src/main/res/layout/floating_menu.xml`

- [ ] **Step 1: 创建 floating_menu.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/menu_container"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:background="@drawable/floating_background"
    android:padding="8dp"
    android:gravity="center">

    <ImageButton
        android:id="@+id/btn_play"
        android:layout_width="48dp"
        android:layout_height="48dp"
        android:src="@android:drawable/ic_media_play"
        android:background="?attr/selectableItemBackgroundBorderless"
        android:contentDescription="启动" />

    <ImageButton
        android:id="@+id/btn_stop"
        android:layout_width="48dp"
        android:layout_height="48dp"
        android:src="@android:drawable/ic_media_pause"
        android:background="?attr/selectableItemBackgroundBorderless"
        android:contentDescription="停止" />

    <ImageButton
        android:id="@+id/btn_close"
        android:layout_width="48dp"
        android:layout_height="48dp"
        android:src="@android:drawable/ic_menu_close_clear_cancel"
        android:background="?attr/selectableItemBackgroundBorderless"
        android:contentDescription="关闭" />

</LinearLayout>
```

- [ ] **Step 2: 提交**

```bash
git add app/src/main/res/layout/floating_menu.xml
git commit -m "feat: add floating menu layout"
```

---

### Task 2: 创建 FloatingMenuService 服务

**Files:**
- Create: `app/src/main/java/com/jdhelper/app/service/FloatingMenuService.kt`

- [ ] **Step 1: 创建 FloatingMenuService.kt**

```kotlin
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
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.jdhelper.R
import com.jdhelper.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class FloatingMenuService : Service() {

    companion object {
        const val CHANNEL_ID = "floating_menu_channel"
        const val NOTIFICATION_ID = 1002
        const val ACTION_SHOW = "com.jdhelper.SHOW_MENU"
        const val ACTION_HIDE = "com.jdhelper.HIDE_MENU"

        private var instance: FloatingMenuService? = null

        fun getInstance(): FloatingMenuService? = instance

        fun startService(context: Context) {
            val intent = Intent(context, FloatingMenuService::class.java).apply {
                action = ACTION_SHOW
            }
            context.startForegroundService(intent)
        }

        fun stopService(context: Context) {
            val intent = Intent(context, FloatingMenuService::class.java)
            context.stopService(intent)
        }
    }

    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null
    private val handler = Handler(Looper.getMainLooper())

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    override fun onCreate() {
        super.onCreate()
        instance = this
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW -> showFloatingMenu()
            ACTION_HIDE -> hideFloatingMenu()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        hideFloatingMenu()
        instance = null
    }

    private fun createNotificationChannel() {
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
        if (floatingView != null) return

        startForeground(NOTIFICATION_ID, createNotification())

        val layoutInflater = LayoutInflater.from(this)
        floatingView = layoutInflater.inflate(R.layout.floating_menu, null)

        // 设置按钮点击事件（暂不实现功能，只做Toast提示）
        floatingView?.findViewById<ImageButton>(R.id.btn_play)?.setOnClickListener {
            Toast.makeText(this, "启动任务", Toast.LENGTH_SHORT).show()
        }

        floatingView?.findViewById<ImageButton>(R.id.btn_stop)?.setOnClickListener {
            Toast.makeText(this, "停止任务", Toast.LENGTH_SHORT).show()
        }

        floatingView?.findViewById<ImageButton>(R.id.btn_close)?.setOnClickListener {
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

        // 拖拽功能
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
    }

    private fun hideFloatingMenu() {
        floatingView?.let {
            windowManager.removeView(it)
        }
        floatingView = null
        stopForeground(STOP_FOREGROUND_REMOVE)
    }
}
```

- [ ] **Step 2: 提交**

```bash
git add app/src/main/java/com/jdhelper/app/service/FloatingMenuService.kt
git commit -m "feat: add FloatingMenuService"
```

---

### Task 3: 在 AndroidManifest.xml 注册服务

**Files:**
- Modify: `app/src/main/AndroidManifest.xml:67-73`

- [ ] **Step 1: 添加 FloatingMenuService 注册**

在 `</service>` (PositionFloatingService结束) 后添加:

```xml
        <!-- 悬浮菜单服务 -->
        <service
            android:name=".service.FloatingMenuService"
            android:exported="false"
            android:foregroundServiceType="specialUse">
            <property
                android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
                android:value="floating_menu" />
        </service>
```

- [ ] **Step 2: 提交**

```bash
git add app/src/main/AndroidManifest.xml
git commit -m "feat: register FloatingMenuService in manifest"
```

---

### Task 4: 在 HomeScreen 添加悬浮菜单控制

**Files:**
- Modify: `app/src/main/java/com/jdhelper/app/ui/screens/home/HomeScreen.kt`

- [ ] **Step 1: 添加导入和状态变量**

在文件头部添加导入:
```kotlin
import com.jdhelper.service.FloatingMenuService
```

在 `HomeScreen` 函数中添加状态:
```kotlin
var showFloatingMenu by remember { mutableStateOf(false) }
```

- [ ] **Step 2: 添加控制按钮到 TopAppBar**

在 TopAppBar 的 actions 中添加:
```kotlin
IconButton(onClick = {
    if (showFloatingMenu) {
        FloatingMenuService.stopService(context)
    } else {
        FloatingMenuService.startService(context)
    }
    showFloatingMenu = !showFloatingMenu
}) {
    Icon(
        if (showFloatingMenu) Icons.Default.MenuOpen else Icons.Default.Menu,
        contentDescription = "悬浮菜单"
    )
}
```

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/com/jdhelper/app/ui/screens/home/HomeScreen.kt
git commit -m "feat: add floating menu control in HomeScreen"
```

---

### Task 5: 验证构建

**Files:**
- Build verification

- [ ] **Step 1: 构建 debug APK**

```bash
./gradlew assembleDebug
```

- [ ] **Step 2: 确认构建成功**

预期输出: BUILD SUCCESSFUL

---

### Task 6: 最终提交

- [ ] **Step 1: 提交所有更改**

```bash
git add -A
git commit -m "feat: add floating menu with play/stop/close buttons"
```

---

## 执行选项

**Plan complete and saved to `docs/superpowers/plans/2026-03-29-floating-menu-plan.md`. Two execution options:**

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

**Which approach?**