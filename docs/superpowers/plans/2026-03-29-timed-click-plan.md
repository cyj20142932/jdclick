# 整分定时点击功能实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在悬浮菜单中添加启动按钮，实现整分定时自动点击功能

**Architecture:** 使用Room存储延迟配置，通过AccessibilityService查找按钮和执行点击，使用NTP校准时间

**Tech Stack:** Kotlin, Jetpack Compose, Room, Hilt, Coroutines

---

## 文件结构

```
app/src/main/java/com/jdhelper/app/
├── data/
│   ├── local/
│   │   ├── ClickSettingsDao.kt      # 新增
│   │   └── ClickSettings.kt         # 新增 (Entity)
│   └── repository/
│       └── ClickSettingsRepository.kt  # 新增 (接口+实现)
├── domain/
│   └── repository/
│       └── ClickSettingsRepository.kt  # 新增 (接口定义)
├── service/
│   ├── ButtonFinder.kt              # 新增
│   ├── TimedClickManager.kt         # 新增
│   ├── FloatingMenuService.kt       # 修改
│   ├── FloatingService.kt           # 修改
│   └── AccessibilityClickService.kt # 修改
```

---

## 实现任务

### Task 1: 创建 ClickSettings Entity 和 DAO

**Files:**
- Create: `app/src/main/java/com/jdhelper/app/data/local/ClickSettings.kt`
- Create: `app/src/main/java/com/jdhelper/app/data/local/ClickSettingsDao.kt`
- Modify: `app/src/main/java/com/jdhelper/app/data/local/AutoClickerDatabase.kt:1-14`

- [ ] **Step 1: 创建 ClickSettings Entity**

```kotlin
package com.jdhelper.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "click_settings")
data class ClickSettings(
    @PrimaryKey
    val id: Long = 1,
    val delayMillis: Double = 0.0
)
```

- [ ] **Step 2: 创建 ClickSettingsDao**

```kotlin
package com.jdhelper.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ClickSettingsDao {
    @Query("SELECT * FROM click_settings WHERE id = 1")
    fun getSettings(): Flow<ClickSettings?>

    @Query("SELECT delayMillis FROM click_settings WHERE id = 1")
    fun getDelayMillis(): Flow<Double>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(settings: ClickSettings)

    @Query("UPDATE click_settings SET delayMillis = :delay WHERE id = 1")
    suspend fun updateDelay(delay: Double)
}
```

- [ ] **Step 3: 修改 AutoClickerDatabase 添加新表**

在 entities 数组中添加 ClickSettings::class

```kotlin
@Database(
    entities = [ClickTask::class, ClickSettings::class],
    version = 2,  // 增加版本号
    exportSchema = false
)
abstract class AutoClickerDatabase : RoomDatabase() {
    abstract fun clickTaskDao(): ClickTaskDao
    abstract fun clickSettingsDao(): ClickSettingsDao  // 新增
}
```

- [ ] **Step 4: 修改 DatabaseModule 添加 DAO 依赖**

```kotlin
@Provides
@Singleton
fun provideClickSettingsDao(database: AutoClickerDatabase): ClickSettingsDao {
    return database.clickSettingsDao()
}
```

- [ ] **Step 5: 提交**

---

### Task 2: 创建 ClickSettingsRepository

**Files:**
- Create: `app/src/main/java/com/jdhelper/app/domain/repository/ClickSettingsRepository.kt`
- Create: `app/src/main/java/com/jdhelper/app/data/repository/ClickSettingsRepositoryImpl.kt`

- [ ] **Step 1: 创建领域层接口**

```kotlin
package com.jdhelper.domain.repository

import kotlinx.coroutines.flow.Flow

interface ClickSettingsRepository {
    fun getDelayMillis(): Flow<Double>
    suspend fun setDelayMillis(delay: Double)
}
```

- [ ] **Step 2: 创建数据层实现**

```kotlin
package com.jdhelper.data.repository

import com.jdhelper.data.local.ClickSettings
import com.jdhelper.data.local.ClickSettingsDao
import com.jdhelper.domain.repository.ClickSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClickSettingsRepositoryImpl @Inject constructor(
    private val clickSettingsDao: ClickSettingsDao
) : ClickSettingsRepository {

    override fun getDelayMillis(): Flow<Double> {
        return clickSettingsDao.getDelayMillis().map { it ?: 0.0 }
    }

    override suspend fun setDelayMillis(delay: Double) {
        clickSettingsDao.insert(ClickSettings(id = 1, delayMillis = delay))
    }
}
```

- [ ] **Step 3: 在 DatabaseModule 中添加依赖**

```kotlin
@Provides
@Singleton
fun provideClickSettingsRepository(clickSettingsDao: ClickSettingsDao): ClickSettingsRepository {
    return ClickSettingsRepositoryImpl(clickSettingsDao)
}
```

- [ ] **Step 4: 提交**

---

### Task 3: 创建 ButtonFinder

**Files:**
- Create: `app/src/main/java/com/jdhelper/app/service/ButtonFinder.kt`

- [ ] **Step 1: 创建 ButtonFinder**

```kotlin
package com.jdhelper.service

import android.content.Context
import android.graphics.Point
import android.util.DisplayMetrics
import android.view.accessibility.AccessibilityNodeInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ButtonFinder @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "ButtonFinder"
        // 目标按钮文字关键词
        private val TARGET_KEYWORDS = listOf("立即支付", "提交订单")
        // 默认位置偏移
        private const val DEFAULT_OFFSET_X = 140
        private const val DEFAULT_OFFSET_Y = 100
    }

    /**
     * 查找目标按钮
     * @param root  AccessibilityNodeInfo 根节点
     * @return 按钮中心点坐标，如果未找到返回null
     */
    fun findTargetButton(root: AccessibilityNodeInfo?): Point? {
        if (root == null) return null

        val result = findButtonByKeywords(root)
        root.recycle()
        return result
    }

    /**
     * 通过关键词查找按钮
     */
    private fun findButtonByKeywords(node: AccessibilityNodeInfo): Point? {
        // 检查当前节点是否符合条件
        if (isTargetNode(node)) {
            return getNodeCenter(node)
        }

        // 递归遍历子节点
        for (i in 0 until childCount) {
            val child = getChild(node, i) ?: continue
            val result = findButtonByKeywords(child)
            if (result != null) {
                return result
            }
        }
        return null
    }

    /**
     * 判断节点是否符合目标条件
     */
    private fun isTargetNode(node: AccessibilityNodeInfo): Boolean {
        val text = node.text?.toString() ?: ""
        val contentDesc = node.contentDescription?.toString() ?: ""

        return TARGET_KEYWORDS.any { keyword ->
            text.contains(keyword) || contentDesc.contains(keyword)
        }
    }

    /**
     * 获取节点中心点坐标
     */
    private fun getNodeCenter(node: AccessibilityNodeInfo): Point? {
        val bounds = android.graphics.Rect()
        node.getBoundsInScreen(bounds)

        return Point(
            bounds.centerX(),
            bounds.centerY()
        )
    }

    /**
     * 获取默认点击位置（屏幕右下角向左140，向上100）
     */
    fun getDefaultPosition(): Point {
        val metrics = DisplayMetrics()
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getMetrics(metrics)

        return Point(
            metrics.widthPixels - DEFAULT_OFFSET_X,
            metrics.heightPixels - DEFAULT_OFFSET_Y
        )
    }
}
```

- [ ] **Step 2: 提交**

---

### Task 4: 创建 TimedClickManager

**Files:**
- Create: `app/src/main/java/com/jdhelper/app/service/TimedClickManager.kt`

- [ ] **Step 1: 创建 TimedClickManager**

```kotlin
package com.jdhelper.service

import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TimedClickManager @Inject constructor(
    private val ntpTimeService: NtpTimeService,
    private val accessibilityClickService: AccessibilityClickService
) {
    companion object {
        private const val TAG = "TimedClickManager"
    }

    private val handler = Handler(Looper.getMainLooper())
    private var isRunning = false
    private var targetX: Int = 0
    private var targetY: Int = 0
    private var delayMillis: Double = 0.0

    /**
     * 启动定时点击任务
     */
    fun start(targetX: Int, targetY: Int, delayMillis: Double) {
        if (isRunning) {
            stop()
        }

        this.targetX = targetX
        this.targetY = targetY
        this.delayMillis = delayMillis
        isRunning = true

        scheduleClick()
    }

    /**
     * 停止当前任务
     */
    fun stop() {
        isRunning = false
        handler.removeCallbacksAndMessages(null)
        Log.d(TAG, "定时点击任务已停止")
    }

    /**
     * 检查是否有运行中的任务
     */
    fun isRunning(): Boolean = isRunning

    /**
     * 调度点击任务
     */
    private fun scheduleClick() {
        val ntpTime = ntpTimeService.getCurrentTime()
        val clickTime = calculateNextMinuteTime(ntpTime, delayMillis)

        val delay = clickTime - System.currentTimeMillis()
        Log.d(TAG, "计划点击时间: $clickTime, 当前时间: ${System.currentTimeMillis()}, 等待: ${delay}ms")

        if (delay > 0) {
            handler.postDelayed({
                if (isRunning) {
                    performClick()
                }
            }, delay)
        } else {
            // 如果延迟时间已过，立即执行
            performClick()
        }
    }

    /**
     * 执行点击
     */
    private fun performClick() {
        accessibilityClickService.performGlobalClick(targetX, targetY)
        Log.d(TAG, "执行点击: ($targetX, $targetY)")
        isRunning = false
    }

    /**
     * 计算下一个整分时刻+延迟的时间
     */
    private fun calculateNextMinuteTime(ntpTime: Long, delayMillis: Double): Long {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = ntpTime
        }

        // 设置为下一分钟的00秒000毫秒
        calendar.add(Calendar.MINUTE, 1)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        // 加上延迟（支持负数）
        return calendar.timeInMillis + delayMillis.toLong()
    }
}
```

- [ ] **Step 2: 提交**

---

### Task 5: 扩展 AccessibilityClickService

**Files:**
- Modify: `app/src/main/java/com/jdhelper/app/service/AccessibilityClickService.kt:1-38`

- [ ] **Step 1: 添加点击方法**

在 AccessibilityClickService 类中添加：

```kotlin
/**
 * 执行全局点击
 */
fun performGlobalClick(x: Int, y: Int): Boolean {
    return try {
        val dispatchResult = dispatchGesture(
            createClickGesture(x, y),
            null,
            null
        )
        Log.d(TAG, "执行点击 ($x, $y): $dispatchResult")
        dispatchResult
    } catch (e: Exception) {
        Log.e(TAG, "点击失败", e)
        false
    }
}

/**
 * 创建点击手势
 */
private fun createClickGesture(x: Int, y: Int): GestureDescription {
    val path = Path()
    path.moveTo(x.toFloat(), y.toFloat())

    val builder = GestureDescription.Builder()
    val strokeDescription = GestureDescription.StrokeDescription(
        path,
        0,
        100
    )
    builder.addStroke(strokeDescription)
    return builder.build()
}

/**
 * 查找目标按钮
 */
fun findTargetButton(): Point? {
    val rootNode = rootInActiveWindow ?: return null
    return ButtonFinder(applicationContext).findTargetButton(rootNode)
}
```

- [ ] **Step 2: 添加必要的 import**

```kotlin
import android.graphics.Path
import android.view.accessibility.AccessibilityNodeInfo
import android.graphics.Point
```

- [ ] **Step 3: 提交**

---

### Task 6: 扩展 FloatingService

**Files:**
- Modify: `app/src/main/java/com/jdhelper/app/service/FloatingService.kt`

- [ ] **Step 1: 查看现有代码**

先读取现有 FloatingService.kt 内容

- [ ] **Step 2: 添加启动/停止方法**

在 FloatingMenuService 中需要能够控制 FloatingService 的显示/隐藏。添加静态方法：

```kotlin
// 在 FloatingService companion object 中添加
fun startService(context: Context) {
    val intent = Intent(context, FloatingService::class.java).apply {
        action = ACTION_SHOW
    }
    context.startForegroundService(intent)
}

fun stopService(context: Context) {
    val intent = Intent(context, FloatingService::class.java).apply {
        action = ACTION_HIDE
    }
    context.startService(intent)
}
```

- [ ] **Step 3: 提交**

---

### Task 7: 修改 FloatingMenuService 实现启动/停止逻辑

**Files:**
- Modify: `app/src/main/java/com/jdhelper/app/service/FloatingMenuService.kt:120-140`

- [ ] **Step 1: 修改导入和声明**

添加必要依赖注入：

```kotlin
@AndroidEntryPoint
class FloatingMenuService : Service() {

    @Inject
    lateinit var ntpTimeService: NtpTimeService

    @Inject
    lateinit var timedClickManager: TimedClickManager

    @Inject
    lateinit var clickSettingsRepository: ClickSettingsRepository

    // ... 现有代码
}
```

- [ ] **Step 2: 修改 btn_play 点击事件**

```kotlin
floatingView?.findViewById<ImageButton>(R.id.btn_play)?.setOnClickListener {
    // 先停止现有任务
    timedClickManager.stop()
    FloatingService.stopService(this)

    CoroutineScope(Dispatchers.Main).launch {
        try {
            // 1. 查找目标按钮
            val buttonFinder = ButtonFinder(this@FloatingMenuService)
            val position = AccessibilityClickService.getInstance()?.findTargetButton()

            val targetX: Int
            val targetY: Int

            if (position != null) {
                targetX = position.x
                targetY = position.y
                Toast.makeText(this@FloatingMenuService, "已找到目标按钮，位置: ($targetX, $targetY)", Toast.LENGTH_SHORT).show()
            } else {
                val defaultPos = buttonFinder.getDefaultPosition()
                targetX = defaultPos.x
                targetY = defaultPos.y
                Toast.makeText(this@FloatingMenuService, "未找到目标按钮，使用默认位置: ($targetX, $targetY)", Toast.LENGTH_SHORT).show()
            }

            // 2. NTP校准时间
            val synced = withContext(Dispatchers.IO) {
                ntpTimeService.syncTime()
            }
            if (!synced) {
                Toast.makeText(this@FloatingMenuService, "时间未校准，使用本地时间", Toast.LENGTH_SHORT).show()
            }

            // 3. 显示悬浮时钟
            FloatingService.startService(this@FloatingMenuService)

            // 4. 获取延迟设置并启动定时点击
            val delay = clickSettingsRepository.getDelayMillis().first()
            timedClickManager.start(targetX, targetY, delay)

        } catch (e: Exception) {
            Log.e(TAG, "启动任务失败", e)
            Toast.makeText(this@FloatingMenuService, "启动任务失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
```

- [ ] **Step 3: 修改 btn_stop 点击事件**

```kotlin
floatingView?.findViewById<ImageButton>(R.id.btn_stop)?.setOnClickListener {
    timedClickManager.stop()
    FloatingService.stopService(this)
    Toast.makeText(this, "任务已停止", Toast.LENGTH_SHORT).show()
}
```

- [ ] **Step 4: 添加必要的 import**

```kotlin
import android.graphics.Point
import com.jdhelper.domain.repository.ClickSettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import android.widget.ImageButton
import android.widget.Toast
```

- [ ] **Step 5: 提交**

---

### Task 8: 在设置界面添加延迟配置

**Files:**
- Modify: `app/src/main/java/com/jdhelper/app/ui/screens/settings/SettingsScreen.kt`
- Modify: `app/src/main/java/com/jdhelper/app/ui/screens/settings/SettingsViewModel.kt`

- [ ] **Step 1: 修改 SettingsViewModel**

```kotlin
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val ntpTimeService: NtpTimeService,
    private val clickSettingsRepository: ClickSettingsRepository  // 新增
) : ViewModel() {

    // ... 现有代码

    val delayMillis: StateFlow<Double> = clickSettingsRepository.getDelayMillis()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    suspend fun setDelayMillis(delay: Double) {
        clickSettingsRepository.setDelayMillis(delay)
    }

    // ... 现有代码
}
```

- [ ] **Step 2: 在 SettingsScreen 添加延迟输入 UI**

在设置界面添加一个新的设置项：

```kotlin
SettingsItem(
    icon = Icons.Default.Timer,
    title = "点击延迟",
    subtitle = "${uiState.delayMillis} ms",
    onClick = { showDelayDialog = true }
)

// 添加对话框
if (showDelayDialog) {
    var delayText by remember { mutableStateOf(uiState.delayMillis.toString()) }

    AlertDialog(
        onDismissRequest = { showDelayDialog = false },
        title = { Text("设置点击延迟") },
        text = {
            OutlinedTextField(
                value = delayText,
                onValueChange = { delayText = it },
                label = { Text("延迟 (毫秒，支持负数)") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = {
                delayText.toDoubleOrNull()?.let { viewModel.setDelayMillis(it) }
                showDelayDialog = false
            }) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = { showDelayDialog = false }) {
                Text("取消")
            }
        }
    )
}
```

- [ ] **Step 3: 更新 UI 状态**

在 SettingsUiState 中添加 delayMillis 字段

- [ ] **Step 4: 提交**

---

### Task 9: 添加点击完成回调提示

**Files:**
- Modify: `app/src/main/java/com/jdhelper/app/service/TimedClickManager.kt`

- [ ] **Step 1: 添加回调接口**

```kotlin
interface ClickCallback {
    fun onClickComplete()
}
```

- [ ] **Step 2: 修改 TimedClickManager**

```kotlin
class TimedClickManager ... {
    private var callback: ClickCallback? = null

    fun setCallback(callback: ClickCallback?) {
        this.callback = callback
    }

    private fun performClick() {
        accessibilityClickService.performGlobalClick(targetX, targetY)
        Log.d(TAG, "执行点击: ($targetX, $targetY)")
        callback?.onClickComplete()
        isRunning = false
    }
}
```

- [ ] **Step 3: 在 FloatingMenuService 中设置回调**

在点击启动按钮后设置回调，显示Toast提示

- [ ] **Step 4: 提交**

---

## 执行选项

**Plan complete and saved to `docs/superpowers/plans/2026-03-29-timed-click-plan.md`. Two execution options:**

1. **Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

2. **Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

Which approach?