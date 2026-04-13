# 应用内日志系统设计

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在应用内实现类似AutoJS的控制台日志功能，日志同时输出到logcat和应用内日志页面，支持日志级别筛选、删除、清空等操作。

**Architecture:** 
- LogConsole工具类提供与android.util.Log兼容的静态方法，内部同时输出到Android Log和Room数据库
- LogScreen页面展示日志列表，支持按级别筛选、单条删除、清空全部
- 通过StateFlow实现日志实时推送和UI刷新
- 自动清理策略：超过1000条或超过7天自动删除旧日志

**Tech Stack:** Kotlin + Jetpack Compose + Room + Hilt + StateFlow

---

## 1. 数据层设计

### 1.1 LogEntry 实体

```kotlin
@Entity(tableName = "logs")
data class LogEntry(
    val id: Long = 0,
    val level: Int,                    // 0=VERBOSE, 1=DEBUG, 2=INFO, 3=WARN, 4=ERROR
    val tag: String,                   // 日志标签
    val message: String,               // 日志内容
    val timestamp: Long = System.currentTimeMillis(),
    val threadName: String = Thread.currentThread().name
)
```

### 1.2 日志级别常量

```kotlin
object LogLevel {
    const val VERBOSE = 0
    const val DEBUG = 1
    const val INFO = 2
    const val WARN = 3
    const val ERROR = 4
    
    const val MIN_LEVEL = VERBOSE
    const val MAX_LEVEL = ERROR
}
```

### 1.3 LogDao 接口

```kotlin
@Dao interface LogDao {
    @Query("SELECT * FROM logs ORDER BY timestamp DESC LIMIT :limit")
    fun getAllLogs(limit: Int = 1000): Flow<List<LogEntry>>
    
    @Query("SELECT * FROM logs WHERE level >= :minLevel AND level <= :maxLevel ORDER BY timestamp DESC LIMIT :limit")
    fun getLogsByLevel(minLevel: Int, maxLevel: Int, limit: Int = 1000): Flow<List<LogEntry>>
    
    @Query("SELECT * FROM logs WHERE level = :level ORDER BY timestamp DESC LIMIT :limit")
    fun getLogsBySingleLevel(level: Int, limit: Int = 1000): Flow<List<LogEntry>>
    
    @Insert suspend fun insert(log: LogEntry)
    
    @Query("DELETE FROM logs WHERE id = :id") suspend fun delete(id: Long)
    
    @Query("DELETE FROM logs") suspend fun clearAll()
    
    @Query("DELETE FROM logs WHERE timestamp < :beforeTime") suspend fun deleteOlderThan(beforeTime: Long)
    
    @Query("SELECT COUNT(*) FROM logs") suspend fun getCount(): Int
    
    @Query("DELETE FROM logs WHERE id IN (SELECT id FROM logs ORDER BY timestamp ASC LIMIT :count)")
    suspend fun deleteOldest(count: Int)
}
```

---

## 2. LogConsole 工具类设计

### 2.1 核心API

```kotlin
object LogConsole {
    const val VERBOSE = 0
    const val DEBUG = 1
    const val INFO = 2
    const val WARN = 3
    const val ERROR = 4
    
    fun v(tag: String, msg: String) = log(VERBOSE, tag, msg)
    fun d(tag: String, msg: String) = log(DEBUG, tag, msg)
    fun i(tag: String, msg: String) = log(INFO, tag, msg)
    fun w(tag: String, msg: String) = log(WARN, tag, msg)
    fun e(tag: String, msg: String, throwable: Throwable? = null) = log(ERROR, tag, msg, throwable)
    
    private fun log(level: Int, tag: String, msg: String, throwable: Throwable? = null) {
        // 1. 输出到Android Log（保持兼容）
        val androidLogMsg = if (throwable != null) "$msg\n${Log.getStackTraceString(throwable)}" else msg
        when(level) {
            VERBOSE -> android.util.Log.v(tag, androidLogMsg)
            DEBUG -> android.util.Log.d(tag, androidLogMsg)
            INFO -> android.util.Log.i(tag, androidLogMsg)
            WARN -> android.util.Log.w(tag, androidLogMsg)
            ERROR -> android.util.Log.e(tag, androidLogMsg)
        }
        
        // 2. 写入Room数据库
        // 3. 检查自动清理条件
    }
}
```

### 2.2 自动清理逻辑

```kotlin
object LogConsole {
    private const val MAX_LOG_COUNT = 1000
    private const val DELETE_COUNT = 200
    private const val RETAIN_DAYS = 7
    private const val RETAIN_MILLIS = RETAIN_DAYS * 24 * 60 * 60 * 1000L
    
    private fun log(...) {
        // ... 输出到Android Log
        
        // 写入数据库（需要通过Repository）
        // ...
        
        // 自动清理检查
        checkAndCleanup()
    }
    
    private fun checkAndCleanup() {
        // 1. 如果超过MAX_LOG_COUNT，删除最旧的DELETE_COUNT条
        // 2. 如果超过RETAIN_DAYS，删除旧日志
    }
}
```

---

## 3. Repository 设计

### 3.1 LogRepository 接口

```kotlin
interface LogRepository {
    fun getAllLogs(): Flow<List<LogEntry>>
    fun getLogsByLevel(level: Int): Flow<List<LogEntry>>
    suspend fun addLog(log: LogEntry)
    suspend fun deleteLog(id: Long)
    suspend fun clearAllLogs()
    suspend fun cleanupOldLogs()
}
```

### 3.2 LogRepositoryImpl 实现

```kotlin
@Singleton
class LogRepositoryImpl @Inject constructor(
    private val logDao: LogDao
) : LogRepository {
    
    override fun getAllLogs(): Flow<List<LogEntry>> = logDao.getAllLogs()
    
    override fun getLogsByLevel(level: Int): Flow<List<LogEntry>> = 
        logDao.getLogsBySingleLevel(level)
    
    override suspend fun addLog(log: LogEntry) {
        logDao.insert(log)
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
        
        val cutoffTime = System.currentTimeMillis() - RETAIN_MILLIS
        logDao.deleteOlderThan(cutoffTime)
    }
}
```

---

## 4. 日志页面UI设计

### 4.1 导航入口

在导航栏添加日志图标（`Icons.Default.Terminal` 或 `Icons.Default.List`），点击跳转LogScreen。

### 4.2 LogScreen 页面结构

```kotlin
@Composable
fun LogScreen(
    viewModel: LogViewModel = hiltViewModel()
) {
    val logs by viewModel.logs.collectAsState()
    val selectedLevels by viewModel.selectedLevels.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("日志") },
                actions = {
                    // 清空按钮
                    IconButton(onClick = { viewModel.showClearDialog() }) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "清空")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 级别筛选栏
            LevelFilterRow(
                selectedLevels = selectedLevels,
                onLevelToggle = { viewModel.toggleLevel(it) }
            )
            
            // 日志列表
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                reverseLayout = true  // 最新的在底部
            ) {
                items(logs, key = { it.id }) { log ->
                    LogItem(
                        log = log,
                        onLongPress = { viewModel.showDeleteDialog(log.id) }
                    )
                }
            }
        }
        
        // 删除确认对话框
        // 清空确认对话框
    }
}
```

### 4.3 日志项样式

```kotlin
@Composable
fun LogItem(log: LogEntry, onLongPress: () -> Unit) {
    val levelColor = when(log.level) {
        0 -> Color.Gray       // VERBOSE
        1 -> Color.Blue       // DEBUG
        2 -> Color.Green      // INFO
        3 -> Color(0xFFFFA500) // WARN
        4 -> Color.Red        // ERROR
        else -> Color.Gray
    }
    
    val levelText = when(log.level) {
        0 -> "V"
        1 -> "D"
        2 -> "I"
        3 -> "W"
        4 -> "E"
        else -> "?"
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.Top
        ) {
            // 级别标签
            Text(
                text = levelText,
                color = levelColor,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(16.dp)
            )
            
            Column(modifier = Modifier.weight(1f)) {
                // 时间戳 + 标签
                Text(
                    text = "${formatTime(log.timestamp)} ${log.tag}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
                // 日志内容
                Text(
                    text = log.message,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
```

### 4.4 级别筛选栏

```kotlin
@Composable
fun LevelFilterRow(
    selectedLevels: Set<Int>,
    onLevelToggle: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // 全部按钮
        FilterChip(
            selected = selectedLevels.size == 5,
            onClick = { onLevelToggle(-1) }, // -1 表示全部
            label = { Text("全部") }
        )
        
        // V D I W E 筛选按钮
        listOf("V" to 0, "D" to 1, "I" to 2, "W" to 3, "E" to 4).forEach { (label, level) ->
            FilterChip(
                selected = level in selectedLevels,
                onClick = { onLevelToggle(level) },
                label = { Text(label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = getLevelColor(level).copy(alpha = 0.2f)
                )
            )
        }
    }
}
```

---

## 5. 自动清理策略

| 策略 | 阈值 | 行为 |
|------|------|------|
| 数量限制 | >1000条 | 删除最旧的200条 |
| 时间限制 | >7天 | 删除超过7天的日志 |
| 手动触发 | 用户点击清空 | 删除所有日志 |

---

## 6. 需要迁移的现有代码

将以下文件中的 `Log.*` 调用替换为 `LogConsole.*`：

| 文件 | Log调用数量 |
|------|-------------|
| FloatingMenuService.kt | 较多 |
| JdTimeService.kt | 较多 |
| NtpTimeService.kt | 较多 |
| DefaultTimeService.kt | 较少 |
| HomeViewModel.kt | 较多 |
| TimeViewModel.kt | 较少 |
| FloatingService.kt | 较多 |
| FloatingStateManager.kt | 较少 |
| TimedClickManager.kt | 较多 |
| SettingsViewModel.kt | 较少 |
| AccessibilityClickService.kt | 较多 |
| PositionFloatingService.kt | 较少 |

---

## 7. 实现优先级

1. **Phase 1: 核心功能**
   - LogEntry 实体 + LogDao
   - LogRepository
   - LogConsole 工具类
   - 自动清理逻辑

2. **Phase 2: UI展示**
   - LogScreen 页面
   - 级别筛选功能
   - 删除/清空功能

3. **Phase 3: 代码迁移**
   - 逐步替换现有 Log.* 调用为 LogConsole.*
   - 验证功能正常

---

## 8. 文件变更清单

**需要创建的文件：**
- `app/src/main/java/com/jdhelper/app/data/local/LogEntry.kt` - 日志实体
- `app/src/main/java/com/jdhelper/app/data/local/LogDao.kt` - 日志DAO
- `app/src/main/java/com/jdhelper/app/data/repository/LogRepositoryImpl.kt` - Repository实现
- `app/src/main/java/com/jdhelper/app/domain/repository/LogRepository.kt` - Repository接口
- `app/src/main/java/com/jdhelper/app/service/LogConsole.kt` - 日志工具类
- `app/src/main/java/com/jdhelper/app/ui/screens/log/LogScreen.kt` - 日志页面
- `app/src/main/java/com/jdhelper/app/ui/screens/log/LogViewModel.kt` - 日志ViewModel
- `app/src/main/java/com/jdhelper/app/di/DatabaseModule.kt` - 添加LogDao注入

**需要修改的文件：**
- `app/src/main/java/com/jdhelper/app/ui/navigation/NavHost.kt` - 添加日志页面导航
- 12个现有文件 - 将Log.*替换为LogConsole.*