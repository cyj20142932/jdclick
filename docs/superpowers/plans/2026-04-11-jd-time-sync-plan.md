# 京东时间同步功能实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现京东时间同步功能，用户可选择NTP或京东时间作为悬浮时钟时间源，支持启动自动获取京东时间，状态栏和历史记录显示时间源

**Architecture:** 新增JdTimeService通过京东API获取服务器时间，创建TimeSourceManager统一时间源管理，修改数据模型添加时间源字段

**Tech Stack:** Kotlin, Android ViewModel, Hilt DI, Room Database, OkHttp

---

## Task 1: 创建 JdTimeService 京东时间同步服务

**Files:**
- Create: `app/src/main/java/com/jdhelper/app/service/JdTimeService.kt`

- [ ] **Step 1: 创建 JdTimeService.kt**

```kotlin
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
            .build()
        
        val startTime = System.currentTimeMillis()
        
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            
            val endTime = System.currentTimeMillis()
            val roundTripTime = endTime - startTime
            val networkDelay = roundTripTime / 2
            
            // 从响应头获取服务器时间戳
            val requestIdHeader = response.header("x-api-request-id") ?: return null
            
            // 解析格式: "xxxxx-xxxxx-时间戳-xxxxx"
            val parts = requestIdHeader.split("-")
            if (parts.size < 3) return null
            
            val timestampStr = parts[2]
            val serverTimestamp = timestampStr.toLongOrNull() ?: return null
            
            // 计算时间差
            val localTimeAtMidpoint = startTime + networkDelay
            val timeDiff = localTimeAtMidpoint - serverTimestamp
            
            Log.d(TAG, "RTT=$roundTripTimems, 网络延迟=${networkDelay}ms, 时间差=${timeDiff}ms")
            return timeDiff
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
```

- [ ] **Step 2: 验证编译**

```bash
cd D:/Workspace/Auto/auto_clicker && ./gradlew assembleDebug 2>&1 | tail -20
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/com/jdhelper/app/service/JdTimeService.kt
git commit -m "feat: add JdTimeService for JD server time sync"
```

---

## Task 2: 修改数据模型添加时间源字段

**Files:**
- Modify: `app/src/main/java/com/jdhelper/app/data/local/ClickSettings.kt`
- Modify: `app/src/main/java/com/jdhelper/app/data/local/GiftClickHistory.kt`
- Modify: `app/src/main/java/com/jdhelper/app/data/local/ClickSettingsDao.kt`
- Modify: `app/src/main/java/com/jdhelper/app/domain/repository/ClickSettingsRepository.kt`
- Modify: `app/src/main/java/com/jdhelper/app/data/repository/ClickSettingsRepositoryImpl.kt`

- [ ] **Step 1: 修改 ClickSettings.kt 添加时间源枚举和字段**

```kotlin
package com.jdhelper.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class TimeSource {
    NTP,    // 阿里云NTP时间
    JD      // 京东服务器时间
}

@Entity(tableName = "click_settings")
data class ClickSettings(
    @PrimaryKey
    val id: Long = 1,
    val delayMillis: Double = 0.0,
    val millisecondDigits: Int = 1,
    val recordHistory: Boolean = false,
    val timeSource: TimeSource = TimeSource.NTP  // 新增：时间源
)
```

- [ ] **Step 2: 修改 ClickSettingsDao.kt 添加 timeSource 查询方法**

```kotlin
@Dao
interface ClickSettingsDao {
    // ... 现有方法
    
    @Query("SELECT timeSource FROM click_settings WHERE id = 1")
    fun getTimeSource(): Flow<TimeSource?>
    
    @Query("UPDATE click_settings SET timeSource = :source WHERE id = 1")
    suspend fun updateTimeSource(source: TimeSource)
}
```

- [ ] **Step 3: 修改 ClickSettingsRepository.kt 接口**

```kotlin
interface ClickSettingsRepository {
    // ... 现有方法
    fun getTimeSource(): Flow<TimeSource>
    suspend fun setTimeSource(source: TimeSource)
}
```

- [ ] **Step 4: 修改 ClickSettingsRepositoryImpl.kt 实现新方法**

```kotlin
class ClickSettingsRepositoryImpl @Inject constructor(
    private val clickSettingsDao: ClickSettingsDao,
    private val context: Context
) : ClickSettingsRepository {
    
    // ... 现有代码
    
    override fun getTimeSource(): Flow<TimeSource> {
        return clickSettingsDao.getTimeSource()
            .map { it ?: TimeSource.NTP }
            .stateIn(CoroutineScope(Dispatchers.Main), SharingStarted.Eagerly, TimeSource.NTP)
    }
    
    override suspend fun setTimeSource(source: TimeSource) {
        ensureSettingsExist()
        clickSettingsDao.updateTimeSource(source)
    }
}
```

- [ ] **Step 5: 修改 GiftClickHistory.kt 添加 timeSource 字段**

```kotlin
@Entity(tableName = "gift_click_history")
data class GiftClickHistory(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val stage: Int,
    val ntpClickTime: Long,
    val localClickTime: Long,
    val targetTime: Long,
    val delayMillis: Double,
    val actualDiff: Long,
    val success: Boolean? = null,
    val createTime: Long = System.currentTimeMillis(),
    val timeSource: String = "NTP"  // 新增: "NTP" 或 "JD"
)
```

- [ ] **Step 6: 验证编译**

```bash
cd D:/Workspace/Auto/auto_clicker && ./gradlew assembleDebug 2>&1 | tail -20
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: 提交**

```bash
git add app/src/main/java/com/jdhelper/app/data/local/ClickSettings.kt app/src/main/java/com/jdhelper/app/data/local/GiftClickHistory.kt app/src/main/java/com/jdhelper/app/data/local/ClickSettingsDao.kt app/src/main/java/com/jdhelper/app/domain/repository/ClickSettingsRepository.kt app/src/main/java/com/jdhelper/app/data/repository/ClickSettingsRepositoryImpl.kt
git commit -m "feat: add timeSource field to data models"
```

---

## Task 3: 修改 HomeViewModel 添加时间源状态

**Files:**
- Modify: `app/src/main/java/com/jdhelper/app/ui/screens/home/HomeViewModel.kt`
- Modify: `app/src/main/java/com/jdhelper/app/ui/components/TopStatusBar.kt`
- Modify: `app/src/main/java/com/jdhelper/app/ui/screens/home/HomeScreen.kt`

- [ ] **Step 1: 修改 HomeViewModel 添加时间源状态**

在 HomeViewModel 中添加：
```kotlin
// 时间源状态
val timeSource: StateFlow<TimeSource> = clickSettingsRepository.getTimeSource()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TimeSource.NTP)

// 京东时间偏移
val jdOffset: StateFlow<String> = MutableStateFlow("--ms").also { flow ->
    viewModelScope.launch {
        ntpTimeService.getJdOffset().let {
            flow.value = if (it >= 0) "+${it}ms" else "${it}ms"
        }
    }
}
```

修改 HomeUiState:
```kotlin
data class HomeUiState(
    // ... 现有字段
    val timeSource: TimeSource = TimeSource.NTP,
    val jdOffset: String = ""
)
```

添加切换时间源方法：
```kotlin
fun setTimeSource(source: TimeSource) {
    viewModelScope.launch {
        clickSettingsRepository.setTimeSource(source)
        _uiState.update { it.copy(timeSource = source) }
    }
}

fun syncJdTime() {
    viewModelScope.launch {
        _uiState.update { it.copy(isNtpSyncing = true) }
        val success = jdTimeService.syncJdTime()
        if (success) {
            val offset = jdTimeService.getJdOffset()
            _uiState.update { it.copy(jdOffset = if (offset >= 0) "+${offset}ms" else "${offset}ms") }
        }
        _uiState.update { it.copy(isNtpSyncing = false) }
    }
}
```

- [ ] **Step 2: 修改 TopStatusBar 支持显示时间源**

```kotlin
@Composable
fun TopStatusBar(
    ntpTime: String,
    millis: String,
    ntpOffset: String = "",
    nextClickCountdown: String = "",
    millisecondDigits: Int = 1,
    timeSource: TimeSource = TimeSource.NTP,  // 新增参数
    jdOffset: String = "",                     // 新增参数
    modifier: Modifier = Modifier
) {
    // ... 现有代码 ...
    
    // 修改显示逻辑：根据时间源显示对应偏移
    val offsetDisplay = when (timeSource) {
        TimeSource.NTP -> ntpOffset
        TimeSource.JD -> jdOffset
    }
    val sourceLabel = when (timeSource) {
        TimeSource.NTP -> "NTP"
        TimeSource.JD -> "JD"
    }
}
```

- [ ] **Step 3: 修改 HomeScreen 传递时间源参数**

在 HomeScreen 中添加：
```kotlin
val timeSource by viewModel.timeSource.collectAsState()
val jdOffset by viewModel.jdOffset.collectAsState()
```

修改 TopStatusBar 调用：
```kotlin
TopStatusBar(
    ntpTime = ntpTime,
    millis = millis,
    ntpOffset = if (timeSource == TimeSource.NTP) ntpOffset else jdOffset,
    nextClickCountdown = nextClickCountdown,
    millisecondDigits = millisecondDigits,
    timeSource = timeSource,
    jdOffset = jdOffset
)
```

- [ ] **Step 4: 验证编译**

```bash
cd D:/Workspace/Auto/auto_clicker && ./gradlew assembleDebug 2>&1 | tail -20
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/jdhelper/app/ui/screens/home/HomeViewModel.kt app/src/main/java/com/jdhelper/app/ui/components/TopStatusBar.kt app/src/main/java/com/jdhelper/app/ui/screens/home/HomeScreen.kt
git commit -m "feat: add timeSource to HomeViewModel and UI"
```

---

## Task 4: 添加首页时间源切换UI

**Files:**
- Modify: `app/src/main/java/com/jdhelper/app/ui/screens/home/HomeScreen.kt`

- [ ] **Step 1: 添加时间源切换卡片**

在 HomeScreen 的状态卡片区域添加：
```kotlin
// 时间源切换卡片
Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = DarkCardBackground)
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "时间源",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
            Text(
                text = when (timeSource) {
                    TimeSource.NTP -> "阿里云NTP时间"
                    TimeSource.JD -> "京东服务器时间"
                },
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f)
            )
        }
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("NTP", color = if (timeSource == TimeSource.NTP) Color.White else Color.Gray)
            Switch(
                checked = timeSource == TimeSource.JD,
                onCheckedChange = { isJd ->
                    viewModel.setTimeSource(if (isJd) TimeSource.JD else TimeSource.NTP)
                },
                colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF67C23A))
            )
            Text("JD", color = if (timeSource == TimeSource.JD) Color.White else Color.Gray)
        }
    }
}

// 京东同步按钮
if (timeSource == TimeSource.JD) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkCardBackground)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "京东时间同步",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
                Text(
                    text = "当前偏移: $jdOffset",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
            
            Button(
                onClick = { viewModel.syncJdTime() },
                enabled = !uiState.isNtpSyncing,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF67C23A))
            ) {
                Text(if (uiState.isNtpSyncing) "同步中..." else "同步")
            }
        }
    }
}
```

- [ ] **Step 2: 验证编译**

```bash
cd D:/Workspace/Auto/auto_clicker && ./gradlew assembleDebug 2>&1 | tail -20
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/com/jdhelper/app/ui/screens/home/HomeScreen.kt
git commit -m "feat: add time source switch UI in HomeScreen"
```

---

## Task 5: 修改点击服务使用当前时间源

**Files:**
- Modify: `app/src/main/java/com/jdhelper/app/service/AccessibilityClickService.kt`

- [ ] **Step 1: 修改 AccessibilityClickService 使用时间源**

在 AccessibilityClickService 中注入 JdTimeService 和 ClickSettingsRepository，获取当前时间源并在点击时记录。

```kotlin
// 在保存历史记录的方法中添加 timeSource
private suspend fun saveClickHistory(...) {
    val timeSource = clickSettingsRepository.getTimeSource().first()
    val history = GiftClickHistory(
        // ... 现有字段
        timeSource = timeSource.name  // "NTP" 或 "JD"
    )
    giftClickHistoryDao.insert(history)
}
```

- [ ] **Step 2: 验证编译**

```bash
cd D:/Workspace/Auto/auto_clicker && ./gradlew assembleDebug 2>&1 | tail -20
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/com/jdhelper/app/service/AccessibilityClickService.kt
git commit -m "feat: record timeSource in click history"
```

---

## Task 6: 修改悬浮时钟使用当前时间源

**Files:**
- Modify: `app/src/main/java/com/jdhelper/app/service/FloatingService.kt`
- Modify: `app/src/main/java/com/jdhelper/app/service/FloatingMenuService.kt`

- [ ] **Step 1: 修改 FloatingService 使用时间源显示时间**

在 FloatingService 中注入 JdTimeService 和 ClickSettingsRepository，读取当前时间源：
- 如果是 NTP：使用 NtpTimeService.getCurrentTime()
- 如果是 JD：使用 JdTimeService.getCurrentJdTime()

```kotlin
// 获取当前时间
private fun getCurrentDisplayTime(): Long {
    return when (currentTimeSource) {
        TimeSource.NTP -> ntpTimeService?.getCurrentTime() ?: System.currentTimeMillis()
        TimeSource.JD -> jdTimeService?.getCurrentJdTime() ?: System.currentTimeMillis()
    }
}
```

- [ ] **Step 2: 启动时自动同步京东时间**

在 FloatingService 启动时自动调用 JdTimeService.syncJdTime()：
```kotlin
override fun onCreate() {
    super.onCreate()
    // ... 现有代码
    
    // 启动时同步京东时间（如果上次同步超过5分钟）
    CoroutineScope(Dispatchers.IO).launch {
        if (!jdTimeService.isSynced() || 
            System.currentTimeMillis() - lastJdSyncTime > 5 * 60 * 1000) {
            jdTimeService.syncJdTime()
        }
    }
}
```

- [ ] **Step 3: 修改 FloatingMenuService 同样逻辑**

- [ ] **Step 4: 验证编译**

```bash
cd D:/Workspace/Auto/auto_clicker && ./gradlew assembleDebug 2>&1 | tail -20
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/jdhelper/app/service/FloatingService.kt app/src/main/java/com/jdhelper/app/service/FloatingMenuService.kt
git commit -m "feat: floating clock uses timeSource setting"
```

---

## Task 7: 修改历史记录显示时间源

**Files:**
- Modify: `app/src/main/java/com/jdhelper/app/ui/screens/history/HistoryScreen.kt`

- [ ] **Step 1: 修改历史记录列表显示时间源**

在历史记录列表中添加时间源显示：
```kotlin
// 在每条记录中显示时间源
Row(
    // ...
) {
    Text(
        text = "时间源: ${item.timeSource}",
        color = when (item.timeSource) {
            "NTP" -> Color.Blue
            "JD" -> Color(0xFF67C23A)
            else -> Color.Gray
        }
    )
}
```

- [ ] **Step 2: 验证编译**

```bash
cd D:/Workspace/Auto/auto_clicker && ./gradlew assembleDebug 2>&1 | tail -20
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/com/jdhelper/app/ui/screens/history/HistoryScreen.kt
git commit -m "feat: display timeSource in history records"
```

---

## Task 8: 最终验证

- [ ] **Step 1: 完整编译**

```bash
cd D:/Workspace/Auto/auto_clicker && ./gradlew assembleDebug
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: 提交所有更改**

```bash
git add -A
git commit -m "feat: add JD time sync feature

- Add JdTimeService for JD server time sync
- Add timeSource field to ClickSettings and GiftClickHistory
- Add time source switch in HomeScreen
- Floating clocks use selected time source
- History records display time source

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

## 验收清单

### 功能验收
- [ ] 点击"同步京东时间"按钮能成功获取京东时间差
- [ ] 切换时间源开关，悬浮时钟显示对应时间
- [ ] 每次打开悬浮时钟自动重新获取京东时间差
- [ ] 状态栏显示当前时间源和偏差
- [ ] 历史记录区分显示NTP和JD时间源

### 性能验收
- [ ] 京东时间同步在3秒内完成
- [ ] 切换时间源即时生效，无延迟