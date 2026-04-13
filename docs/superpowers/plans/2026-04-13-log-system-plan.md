# 应用内日志系统实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在应用内实现类似AutoJS的控制台日志功能，日志同时输出到logcat和应用内日志页面

**Architecture:** LogConsole工具类提供与android.util.Log兼容的API，内部同时写入Room数据库和Android Log，日志页面通过StateFlow实时展示

**Tech Stack:** Kotlin + Jetpack Compose + Room + Hilt

---

## Task 1: 创建数据层 - LogEntry实体、LogDao

**Files:**
- Create: `app/src/main/java/com/jdhelper/app/data/local/LogEntry.kt`
- Modify: `app/src/main/java/com/jdhelper/app/data/local/AutoClickerDatabase.kt:7,13`
- Modify: `app/src/main/java/com/jdhelper/app/di/DatabaseModule.kt`

- [ ] **Step 1: 创建LogEntry实体**

```kotlin
package com.jdhelper.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "logs")
data class LogEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val level: Int,                    // 0=VERBOSE, 1=DEBUG, 2=INFO, 3=WARN, 4=ERROR
    val tag: String,                   // 日志标签
    val message: String,               // 日志内容
    val timestamp: Long = System.currentTimeMillis(),
    val threadName: String = Thread.currentThread().name
)
```

- [ ] **Step 2: 创建LogDao接口**

```kotlin
package com.jdhelper.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LogDao {
    @Query("SELECT * FROM logs ORDER BY timestamp DESC LIMIT :limit")
    fun getAllLogs(limit: Int = 1000): Flow<List<LogEntry>>
    
    @Query("SELECT * FROM logs WHERE level = :level ORDER BY timestamp DESC LIMIT :limit")
    fun getLogsBySingleLevel(level: Int, limit: Int = 1000): Flow<List<LogEntry>>
    
    @Query("SELECT * FROM logs WHERE level >= :minLevel AND level <= :maxLevel ORDER BY timestamp DESC LIMIT :limit")
    fun getLogsByLevelRange(minLevel: Int, maxLevel: Int, limit: Int = 1000): Flow<List<LogEntry>>
    
    @Insert
    suspend fun insert(log: LogEntry)
    
    @Query("DELETE FROM logs WHERE id = :id")
    suspend fun delete(id: Long)
    
    @Query("DELETE FROM logs")
    suspend fun clearAll()
    
    @Query("DELETE FROM logs WHERE timestamp < :beforeTime")
    suspend fun deleteOlderThan(beforeTime: Long)
    
    @Query("SELECT COUNT(*) FROM logs")
    suspend fun getCount(): Int
    
    @Query("DELETE FROM logs WHERE id IN (SELECT id FROM logs ORDER BY timestamp ASC LIMIT :count)")
    suspend fun deleteOldest(count: Int)
}
```

- [ ] **Step 3: 修改AutoClickerDatabase添加LogEntry和LogDao**

```kotlin
@Database(
    entities = [ClickSettings::class, GiftClickHistory::class, LogEntry::class],
    version = 4,
    exportSchema = false
)
abstract class AutoClickerDatabase : RoomDatabase() {
    abstract fun clickSettingsDao(): ClickSettingsDao
    abstract fun giftClickHistoryDao(): GiftClickHistoryDao
    abstract fun logDao(): LogDao  // 添加这行
}
```

- [ ] **Step 4: 修改DatabaseModule添加LogDao注入**

```kotlin
@Provides
@Singleton
fun provideLogDao(database: AutoClickerDatabase): LogDao {
    return database.logDao()
}
```

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/jdhelper/app/data/local/LogEntry.kt app/src/main/java/com/jdhelper/app/data/local/LogDao.kt app/src/main/java/com/jdhelper/app/data/local/AutoClickerDatabase.kt app/src/main/java/com/jdhelper/app/di/DatabaseModule.kt
git commit -m "feat: 添加LogEntry实体和LogDao"
```

---

## Task 2: 创建LogRepository接口和实现

**Files:**
- Create: `app/src/main/java/com/jdhelper/app/domain/repository/LogRepository.kt`
- Create: `app/src/main/java/com/jdhelper/app/data/repository/LogRepositoryImpl.kt`

- [ ] **Step 1: 创建LogRepository接口**

```kotlin
package com.jdhelper.domain.repository

import com.jdhelper.data.local.LogEntry
import kotlinx.coroutines.flow.Flow

interface LogRepository {
    fun getAllLogs(): Flow<List<LogEntry>>
    fun getLogsByLevel(level: Int): Flow<List<LogEntry>>
    suspend fun addLog(log: LogEntry)
    suspend fun deleteLog(id: Long)
    suspend fun clearAllLogs()
    suspend fun cleanupOldLogs()
}
```

- [ ] **Step 2: 创建LogRepositoryImpl实现**

```kotlin
package com.jdhelper.app.data.repository

import com.jdhelper.data.local.LogDao
import com.jdhelper.data.local.LogEntry
import com.jdhelper.domain.repository.LogRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LogRepositoryImpl @Inject constructor(
    private val logDao: LogDao
) : LogRepository {
    
    companion object {
        private const val MAX_LOG_COUNT = 1000
        private const val DELETE_COUNT = 200
    }
    
    override fun getAllLogs(): Flow<List<LogEntry>> = logDao.getAllLogs()
    
    override fun getLogsByLevel(level: Int): Flow<List<LogEntry>> = 
        logDao.getLogsBySingleLevel(level)
    
    override suspend fun addLog(log: LogEntry) {
        logDao.insert(log)
        // 每次添加后检查是否需要清理
        cleanupOldLogs()
    }
    
    override suspend fun deleteLog(id: Long) {
        logDao.delete(id)
    }
    
    override suspend fun clearAllLogs() {
        logDao.clearAll()
    }
    
    override suspend fun cleanupOldLogs() {
        val count = logDao.getCount()
        if (count > MAX_LOG_COUNT) {
            logDao.deleteOldest(count - MAX_LOG_COUNT + DELETE_COUNT)
        }
    }
}
```

- [ ] **Step 3: 添加RepositoryModule或修改DatabaseModule提供LogRepository**

在DatabaseModule末尾添加：

```kotlin
@Provides
@Singleton
fun provideLogRepository(logDao: LogDao): LogRepository {
    return LogRepositoryImpl(logDao)
}
```

- [ ] **Step 4: 提交**

```bash
git add app/src/main/java/com/jdhelper/app/domain/repository/LogRepository.kt app/src/main/java/com/jdhelper/app/data/repository/LogRepositoryImpl.kt app/src/main/java/com/jdhelper/app/di/DatabaseModule.kt
git commit -m "feat: 添加LogRepository接口和实现"
```

---

## Task 3: 创建LogConsole工具类

**Files:**
- Create: `app/src/main/java/com/jdhelper/app/service/LogConsole.kt`

- [ ] **Step 1: 创建LogConsole工具类**

```kotlin
package com.jdhelper.app.service

import android.util.Log
import com.jdhelper.data.local.LogEntry
import com.jdhelper.domain.repository.LogRepository
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
```

- [ ] **Step 2: 在Hilt模块中初始化LogConsole的Repository**

创建或修改ServiceModule：

```kotlin
package com.jdhelper.app.di

import com.jdhelper.app.service.LogConsole
import com.jdhelper.domain.repository.LogRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object LogModule {
    
    @Provides
    @Singleton
    fun provideLogConsoleInitializer(logRepository: LogRepository): LogConsoleInitializer {
        LogConsole.setRepository(logRepository)
        return LogConsoleInitializer()
    }
}

class LogConsoleInitializer
```

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/com/jdhelper/app/service/LogConsole.kt app/src/main/java/com/jdhelper/app/di/ServiceModule.kt
git commit -m "feat: 添加LogConsole日志工具类"
```

---

## Task 4: 创建日志页面UI - LogViewModel和LogScreen

**Files:**
- Create: `app/src/main/java/com/jdhelper/app/ui/screens/log/LogViewModel.kt`
- Create: `app/src/main/java/com/jdhelper/app/ui/screens/log/LogScreen.kt`

- [ ] **Step 1: 创建LogViewModel**

```kotlin
package com.jdhelper.ui.screens.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jdhelper.app.data.local.LogEntry
import com.jdhelper.app.service.LogConsole
import com.jdhelper.domain.repository.LogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LogUiState(
    val showDeleteDialog: Boolean = false,
    val showClearDialog: Boolean = false,
    val selectedLogId: Long? = null
)

@HiltViewModel
class LogViewModel @Inject constructor(
    private val logRepository: LogRepository
) : ViewModel() {
    
    // 所有日志
    val logs: StateFlow<List<LogEntry>> = logRepository.getAllLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
    // UI状态
    private val _uiState = MutableStateFlow(LogUiState())
    val uiState: StateFlow<LogUiState> = _uiState.asStateFlow()
    
    // 显示删除确认对话框
    fun showDeleteDialog(logId: Long) {
        _uiState.value = _uiState.value.copy(
            showDeleteDialog = true,
            selectedLogId = logId
        )
    }
    
    // 隐藏删除对话框
    fun hideDeleteDialog() {
        _uiState.value = _uiState.value.copy(
            showDeleteDialog = false,
            selectedLogId = null
        )
    }
    
    // 删除单条日志
    fun deleteLog() {
        val logId = _uiState.value.selectedLogId ?: return
        viewModelScope.launch {
            logRepository.deleteLog(logId)
            hideDeleteDialog()
        }
    }
    
    // 显示清空确认对话框
    fun showClearDialog() {
        _uiState.value = _uiState.value.copy(showClearDialog = true)
    }
    
    // 隐藏清空对话框
    fun hideClearDialog() {
        _uiState.value = _uiState.value.copy(showClearDialog = false)
    }
    
    // 清空所有日志
    fun clearAllLogs() {
        viewModelScope.launch {
            logRepository.clearAllLogs()
            hideClearDialog()
        }
    }
}
```

- [ ] **Step 2: 创建LogScreen页面**

```kotlin
package com.jdhelper.ui.screens.log

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jdhelper.app.data.local.LogEntry
import com.jdhelper.app.service.LogConsole
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(
    onNavigateBack: () -> Unit,
    viewModel: LogViewModel = hiltViewModel()
) {
    val logs by viewModel.logs.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("日志") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.showClearDialog() }) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "清空")
                    }
                }
            )
        }
    ) { padding ->
        if (logs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("暂无日志", color = MaterialTheme.colorScheme.outline)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(logs, key = { it.id }) { log ->
                    LogItemCard(
                        log = log,
                        timeFormat = timeFormat,
                        onLongPress = { viewModel.showDeleteDialog(log.id) }
                    )
                }
            }
        }
        
        // 删除确认对话框
        if (uiState.showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.hideDeleteDialog() },
                title = { Text("确认删除") },
                text = { Text("确定要删除这条日志吗？") },
                confirmButton = {
                    TextButton(onClick = { viewModel.deleteLog() }) {
                        Text("删除", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.hideDeleteDialog() }) {
                        Text("取消")
                    }
                }
            )
        }
        
        // 清空确认对话框
        if (uiState.showClearDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.hideClearDialog() },
                title = { Text("确认清空") },
                text = { Text("确定要清空所有日志吗？") },
                confirmButton = {
                    TextButton(onClick = { viewModel.clearAllLogs() }) {
                        Text("清空", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.hideClearDialog() }) {
                        Text("取消")
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LogItemCard(
    log: LogEntry,
    timeFormat: SimpleDateFormat,
    onLongPress: () -> Unit
) {
    val levelColor = when(log.level) {
        LogConsole.VERBOSE -> Color.Gray
        LogConsole.DEBUG -> Color(0xFF2196F3)  // 蓝色
        LogConsole.INFO -> Color(0xFF4CAF50)   // 绿色
        LogConsole.WARN -> Color(0xFFFF9800)   // 橙色
        LogConsole.ERROR -> Color(0xFFF44336) // 红色
        else -> Color.Gray
    }
    
    val levelText = when(log.level) {
        LogConsole.VERBOSE -> "V"
        LogConsole.DEBUG -> "D"
        LogConsole.INFO -> "I"
        LogConsole.WARN -> "W"
        LogConsole.ERROR -> "E"
        else -> "?"
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {},
                onLongClick = onLongPress
            )
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.Top
        ) {
            // 级别标签
            Surface(
                color = levelColor,
                shape = MaterialTheme.shapes.extraSmall,
                modifier = Modifier.width(20.dp)
            ) {
                Text(
                    text = levelText,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall
                )
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                // 时间戳 + 标签
                Text(
                    text = "${timeFormat.format(Date(log.timestamp))} ${log.tag}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
                // 日志内容
                Text(
                    text = log.message,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}
```

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/com/jdhelper/app/ui/screens/log/LogViewModel.kt app/src/main/java/com/jdhelper/app/ui/screens/log/LogScreen.kt
git commit -m "feat: 添加日志页面UI"
```

---

## Task 5: 添加日志页面导航

**Files:**
- Modify: `app/src/main/java/com/jdhelper/app/ui/navigation/NavHost.kt`

- [ ] **Step 1: 修改NavHost添加日志页面路由**

在文件顶部添加导入：
```kotlin
import com.jdhelper.ui.screens.log.LogScreen
import androidx.compose.material.icons.filled.Terminal
```

添加Screen定义（在Screen密封类中追加）：
```kotlin
data object Log : Screen("log", "日志", Icons.Default.Terminal)
```

修改bottomNavItems：
```kotlin
val bottomNavItems = listOf(
    Screen.Home,
    Screen.Settings,
    Screen.History
    // 暂时不添加到底部导航，通过其他方式进入
)
```

添加路由：
```kotlin
composable(Screen.Log.route) {
    LogScreen(
        onNavigateBack = { navController.popBackStack() }
    )
}
```

- [ ] **Step 2: 在HomeScreen添加入口按钮**

读取HomeScreen.kt，在合适位置添加入口（可选，后续可以添加）

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/com/jdhelper/app/ui/navigation/NavHost.kt
git commit -m "feat: 添加日志页面导航"
```

---

## Task 6: 迁移现有代码中的Log.*调用

**Files:**（逐一修改以下12个文件）
- Modify: `app/src/main/java/com/jdhelper/app/service/FloatingMenuService.kt`
- Modify: `app/src/main/java/com/jdhelper/app/service/JdTimeService.kt`
- Modify: `app/src/main/java/com/jdhelper/app/service/NtpTimeService.kt`
- Modify: `app/src/main/java/com/jdhelper/app/service/DefaultTimeService.kt`
- Modify: `app/src/main/java/com/jdhelper/app/ui/screens/home/HomeViewModel.kt`
- Modify: `app/src/main/java/com/jdhelper/ui/screens/time/TimeViewModel.kt`
- Modify: `app/src/main/java/com/jdhelper/app/service/FloatingService.kt`
- Modify: `app/src/main/java/com/jdhelper/app/service/FloatingStateManager.kt`
- Modify: `app/src/main/java/com/jdhelper/service/TimedClickManager.kt`
- Modify: `app/src/main/java/com/jdhelper/app/ui/screens/settings/SettingsViewModel.kt`
- Modify: `app/src/main/java/com/jdhelper/app/service/AccessibilityClickService.kt`
- Modify: `app/src/main/java/com/jdhelper/app/service/PositionFloatingService.kt`

- [ ] **Step 1: 修改FloatingMenuService.kt**

将文件顶部的:
```kotlin
import android.util.Log
```

替换为:
```kotlin
import com.jdhelper.app.service.LogConsole
```

将所有 `Log.v(` 替换为 `LogConsole.v(`
将所有 `Log.d(` 替换为 `LogConsole.d(`
将所有 `Log.i(` 替换为 `LogConsole.i(`
将所有 `Log.w(` 替换为 `LogConsole.w(`
将所有 `Log.e(` 替换为 `LogConsole.e(`

- [ ] **Step 2: 同样方式修改JdTimeService.kt**

- [ ] **Step 3: 同样方式修改NtpTimeService.kt**

- [ ] **Step 4: 同样方式修改DefaultTimeService.kt**

- [ ] **Step 5: 同样方式修改HomeViewModel.kt**

- [ ] **Step 6: 同样方式修改TimeViewModel.kt**

- [ ] **Step 7: 同样方式修改FloatingService.kt**

- [ ] **Step 8: 同样方式修改FloatingStateManager.kt**

- [ ] **Step 9: 同样方式修改TimedClickManager.kt**

- [ ] **Step 10: 同样方式修改SettingsViewModel.kt**

- [ ] **Step 11: 同样方式修改AccessibilityClickService.kt**

- [ ] **Step 12: 同样方式修改PositionFloatingService.kt**

- [ ] **Step 13: 验证编译**

```bash
./gradlew assembleDebug
```

- [ ] **Step 14: 提交**

```bash
git add app/src/main/java/com/jdhelper/app/service/FloatingMenuService.kt app/src/main/java/com/jdhelper/app/service/JdTimeService.kt app/src/main/java/com/jdhelper/app/service/NtpTimeService.kt app/src/main/java/com/jdhelper/app/service/DefaultTimeService.kt app/src/main/java/com/jdhelper/app/ui/screens/home/HomeViewModel.kt app/src/main/java/com/jdhelper/ui/screens/time/TimeViewModel.kt app/src/main/java/com/jdhelper/app/service/FloatingService.kt app/src/main/java/com/jdhelper/app/service/FloatingStateManager.kt app/src/main/java/com/jdhelper/service/TimedClickManager.kt app/src/main/java/com/jdhelper/app/ui/screens/settings/SettingsViewModel.kt app/src/main/java/com/jdhelper/app/service/AccessibilityClickService.kt app/src/main/java/com/jdhelper/app/service/PositionFloatingService.kt
git commit -m "refactor: 迁移Log调用到LogConsole"
```

---

## 实施完成总结

完成所有任务后，你将拥有：

1. ✅ 数据层：LogEntry实体、LogDao、LogRepository
2. ✅ LogConsole工具类：与Log.* API兼容，同时输出到logcat和数据库
3. ✅ 日志页面UI：LogScreen + LogViewModel，支持查看、筛选、删除
4. ✅ 导航入口：通过NavHost添加日志页面路由
5. ✅ 代码迁移：12个文件中的Log.*调用已迁移到LogConsole.*

**测试验证：**
1. 打开应用，进入日志页面（空状态）
2. 在首页进行时间同步操作
3. 切换回日志页面，查看是否有新的日志记录
4. 测试筛选功能、清空功能
5. 测试ADB命令 `adb logcat | grep "LogConsole"` 验证logcat输出正常