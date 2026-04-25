# Performance Optimization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 9 targeted performance fixes across the codebase — no functional changes to click timing, service lifecycle, or UI behavior.

**Architecture:** Each fix is a minimal, localized edit in a single file (or pair of related files). Fixes are independent and ordered by performance impact. No new abstractions, no architectural changes.

**Tech Stack:** Kotlin + Coroutines + Jetpack Compose + Room

---

### Task 1: `LogConsole` — Eliminate per-call `CoroutineScope` allocation

**Files:**
- Modify: `app/src/main/java/com/jdhelper/app/service/LogConsole.kt:20-63`

**Problem:** Every `log()` call creates `new CoroutineScope(Dispatchers.IO).launch{}`, allocating a scope object + job. High-frequency logging causes GC pressure.

- [ ] **Step 1: Add shared `scope` field and replace per-call scope**

Change at line 12 (after `object LogConsole {`):

```kotlin
object LogConsole {

    const val VERBOSE = 0
    const val DEBUG = 1
    const val INFO = 2
    const val WARN = 3
    const val ERROR = 4

    // Add: shared scope for all log writes
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
```

Change at lines 55-63 (the `logRepository?.let` block inside `private fun log`):

```kotlin
        logRepository?.let { repo ->
            scope.launch {
                try {
                    repo.addLog(logEntry)
                } catch (e: Exception) {
                    Log.e("LogConsole", "写入日志失败: ${e.message}")
                }
            }
        }
```

Add import at top of file (with existing kotlinx imports):

```kotlin
import kotlinx.coroutines.SupervisorJob
```

- [ ] **Step 2: Build to verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/jdhelper/app/service/LogConsole.kt
git commit -m "perf: reuse CoroutineScope in LogConsole instead of per-call allocation"
```

---

### Task 2: `FloatingService` — Cache `SimpleDateFormat` + add `serviceScope`

**Files:**
- Modify: `app/src/main/java/com/jdhelper/app/service/FloatingService.kt:99-155`

**Problem:** `updateTimeDisplay()` reads SharedPreferences and creates `SimpleDateFormat` every 10ms in the UI update hot path. Also: `onCreate()` uses standalone `CoroutineScope(Dispatchers.IO).launch{}` that can't be cancelled.

- [ ] **Step 1: Add `cachedFormat` field and `serviceScope` field**

After line 102 (`private var timeUpdateJob: Job? = null`), add:

```kotlin
    private var cachedFormat: SimpleDateFormat = SimpleDateFormat("HH:mm:ss.S", Locale.getDefault())
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
```

- [ ] **Step 2: Collect millisecondDigits setting via Flow in `onCreate`**

In `onCreate()`, after the existing time source collection block (after line 145 `}`), add:

```kotlin
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
```

- [ ] **Step 3: Replace standalone `CoroutineScope(Dispatchers.IO)` with `serviceScope`**

Change line 136 `CoroutineScope(Dispatchers.IO).launch {` → `serviceScope.launch {`
Change line 148 `CoroutineScope(Dispatchers.IO).launch {` → `serviceScope.launch {`

- [ ] **Step 4: Simplify `updateTimeDisplay` to use cached format**

Replace lines 325-341 (`private fun updateTimeDisplay`) with:

```kotlin
    private fun updateTimeDisplay(displayTime: Long) {
        timeTextView?.text = cachedFormat.format(Date(displayTime))
    }
```

- [ ] **Step 5: Add required imports**

Add with the other kotlinx imports:

```kotlin
import kotlinx.coroutines.SupervisorJob
```

- [ ] **Step 6: Build to verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/jdhelper/app/service/FloatingService.kt
git commit -m "perf: cache SimpleDateFormat in FloatingService, add serviceScope"
```

---

### Task 3: `HomeViewModel` — Fix coroutine leak + remove polling timer

**Files:**
- Modify: `app/src/main/java/com/jdhelper/app/ui/screens/home/HomeViewModel.kt:93-114, 364-383`

**Problems:** (A) `setNextClickCountdown` launches a new `while(true)` loop each call without cancelling the previous one. (B) `startStatusRefreshTimer` polls accessibility service every 2s forever via IPC.

- [ ] **Step 1: Remove `startStatusRefreshTimer` call from `init`**

At line 97, remove the line `startStatusRefreshTimer()`. The block becomes:

```kotlin
    init {
        checkAllPermissions()
        // 进入首页自动同步时间
        viewModelScope.launch {
            syncNtpTime()
        }
    }
```

- [ ] **Step 2: Remove the entire `startStatusRefreshTimer` method**

Remove lines 103-114:

```kotlin
    // DELETE the entire method below:
    private fun startStatusRefreshTimer() {
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(2000)
                try {
                    checkServiceStatus(context)
                } catch (e: Exception) {
                    LogConsole.e(TAG, "刷新服务状态失败", e)
                }
            }
        }
    }
```

- [ ] **Step 3: Add `countdownJob` field and fix `setNextClickCountdown`**

After line 88 (`private val _ntpOffset = MutableStateFlow("--")`), add:

```kotlin
    private var countdownJob: Job? = null
```

Add import at top of file (with existing kotlinx imports):

```kotlin
import kotlinx.coroutines.Job
```

Replace lines 364-383 (`fun setNextClickCountdown`) with:

```kotlin
    fun setNextClickCountdown(timeMillis: Long) {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            while (true) {
                try {
                    val remaining = timeMillis - System.currentTimeMillis()
                    if (remaining <= 0) {
                        _nextClickCountdown.value = ""
                        break
                    }
                    val seconds = remaining / 1000
                    val minutes = seconds / 60
                    val secs = seconds % 60
                    _nextClickCountdown.value = String.format("%02d:%02d", minutes, secs)
                } catch (e: Exception) {
                    LogConsole.e(TAG, "更新倒计时失败", e)
                }
                delay(1000)
            }
        }
    }
```

- [ ] **Step 4: Build to verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/jdhelper/app/ui/screens/home/HomeViewModel.kt
git commit -m "perf: fix coroutine leak in setNextClickCountdown, remove 2s polling timer"
```

---

### Task 4: `FloatingMenuService` — Add `serviceScope` for proper coroutine lifecycle

**Files:**
- Modify: `app/src/main/java/com/jdhelper/app/service/FloatingMenuService.kt:149-213`

**Problem:** `onCreate()` uses standalone `CoroutineScope(Dispatchers.IO).launch{}` for time source reading and JD sync — these can't be cancelled. Service already has a `serviceScope` field at line 150 using `Dispatchers.Main`, need a separate IO scope for these operations.

- [ ] **Step 1: Add IO `serviceIoScope` field and cancel in `onDestroy`**

After line 150 (`private val serviceScope = CoroutineScope(Dispatchers.Main + Job())`), add:

```kotlin
    private val serviceIoScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
```

After line 350 (`override fun onDestroy()`), update the method to add scope cancellation:

```kotlin
    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        serviceIoScope.cancel()
        hideFloatingMenu()
        instance = null
    }
```

- [ ] **Step 2: Replace standalone `CoroutineScope(Dispatchers.IO)` with `serviceIoScope`**

Line 194: `CoroutineScope(Dispatchers.IO).launch {` → `serviceIoScope.launch {`
Line 206: `CoroutineScope(Dispatchers.IO).launch {` → `serviceIoScope.launch {`

- [ ] **Step 3: Add required imports**

Add with other kotlinx imports:

```kotlin
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
```

- [ ] **Step 4: Build to verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/jdhelper/app/service/FloatingMenuService.kt
git commit -m "perf: add serviceIoScope to FloatingMenuService for proper coroutine lifecycle"
```

---

### Task 5: `DefaultTimeService` + `TopStatusBar` — Remove dead `when` branches

**Files:**
- Modify: `app/src/main/java/com/jdhelper/app/service/DefaultTimeService.kt:51-67, 74-83, 89-95`
- Modify: `app/src/main/java/com/jdhelper/app/ui/components/TopStatusBar.kt:71-78`

**Problem:** Both `when` branches in every method return `jdTimeService.xxx` — the NTP time source has been removed. Redundant branches add cognitive load and a tiny branch overhead.

- [ ] **Step 1: Simplify `DefaultTimeService` methods**

Replace lines 51-59 (`getCurrentTime`):

```kotlin
    override fun getCurrentTime(): Long {
        return jdTimeService.getCurrentJdTime() + cachedDelayMillis.toLong()
    }
```

Replace lines 61-67 (`getTimeOffset`):

```kotlin
    override fun getTimeOffset(): Long = jdTimeService.getJdOffset()
```

Replace lines 74-83 (`syncTime`):

```kotlin
    override suspend fun syncTime(): Boolean {
        LogConsole.d(TAG, "syncTime: 当前时间源 = JD")
        return jdTimeService.syncJdTime()
    }
```

Replace lines 89-95 (`isSynced`):

```kotlin
    override fun isSynced(): Boolean = jdTimeService.isSynced()
```

- [ ] **Step 2: Simplify `TopStatusBar`**

Replace lines 71-78 in `TopStatusBar.kt`:

```kotlin
                    val offsetDisplay = jdOffset
                    val sourceLabel = "JD"
```

- [ ] **Step 3: Build to verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/jdhelper/app/service/DefaultTimeService.kt
git add app/src/main/java/com/jdhelper/app/ui/components/TopStatusBar.kt
git commit -m "refactor: remove dead when branches in DefaultTimeService and TopStatusBar"
```

---

### Task 6: `JdTimeService` — Add 5-second debounce to prevent duplicate sync

**Files:**
- Modify: `app/src/main/java/com/jdhelper/app/service/JdTimeService.kt:38-53`

**Problem:** `FloatingService.onCreate()` and `FloatingMenuService.onCreate()` both call `jdTimeService.syncJdTime()` independently. When both services start (normal case), JD time is synced twice, wasting one network request.

- [ ] **Step 1: Add debounce to `syncJdTime`**

Add import at top:

```kotlin
import java.util.concurrent.atomic.AtomicLong
```

Add field in companion object:

```kotlin
    companion object {
        const val JD_API_URL = "..."
        // ... existing fields ...
        private val lastSyncAttempt = AtomicLong(0L)
    }
```

Modify `syncJdTime` (at line 38):

```kotlin
    suspend fun syncJdTime(): Boolean = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (now - lastSyncAttempt.get() < 5000) {
            LogConsole.d(TAG, "5秒内已同步过，跳过")
            return@withContext hasSyncedAtLeastOnce
        }
        lastSyncAttempt.set(now)

        for (attempt in 1..MAX_RETRIES) {
            // ... existing retry logic unchanged ...
        }
        LogConsole.e(TAG, "京东时间同步失败，已达到最大重试次数")
        return@withContext false
    }
```

- [ ] **Step 2: Build to verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/jdhelper/app/service/JdTimeService.kt
git commit -m "perf: add 5s debounce to JdTimeService.syncJdTime to prevent duplicate sync"
```

---

### Task 7: `FloatingMenuService` — Cache `recordHistory` flag

**Files:**
- Modify: `app/src/main/java/com/jdhelper/app/service/FloatingMenuService.kt:148-155, 1078-1093`

**Problem:** `recordHistory()` calls `clickSettingsRepository.getRecordHistory().first()` and `getTimeSource().first()` before every history write — each is a Room DB query.

- [ ] **Step 1: Add cached flag and collect it**

After line 154 (`private var lastJdSyncTime: Long = 0L`), add:

```kotlin
    @Volatile
    private var cachedRecordHistory: Boolean = true
```

In `onCreate()`, after the time source collection block (after line 203 `}`), add:

```kotlin
        // 缓存记录历史设置，避免每次写操作前读数据库
        serviceIoScope.launch {
            try {
                clickSettingsRepository.getRecordHistory().collect { enabled ->
                    cachedRecordHistory = enabled
                }
            } catch (e: Exception) {
                LogConsole.e(TAG, "读取记录设置失败", e)
            }
        }
```

- [ ] **Step 2: Use cached flag in `recordHistory`**

Replace lines 1078-1081 inside `recordHistory`:

```kotlin
        if (!cachedRecordHistory) return

        val timeSource = clickSettingsRepository.getTimeSource().first()
```

- [ ] **Step 3: Build to verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/jdhelper/app/service/FloatingMenuService.kt
git commit -m "perf: cache recordHistory flag to avoid Room query before every write"
```

---

### Task 8: `FloatingMenuService` — Remove redundant `withContext(Dispatchers.Main)` in gift workflow

**Files:**
- Modify: `app/src/main/java/com/jdhelper/app/service/FloatingMenuService.kt:960-968, 1004-1008, 1061-1063`

**Problem:** Gift workflow methods (`executeTimedStage`, `executePollStage`, giftJob catch block) use `withContext(Dispatchers.Main)` to show Toasts and update button states — but they're already running on `Dispatchers.Main` (inherited from `serviceScope`). Each call adds unnecessary coroutine context switching overhead.

- [ ] **Step 1: Remove redundant `withContext` in `executeTimedStage`**

Lines 960-968 — change:

```kotlin
        } ?: run {
            LogConsole.w(TAG, "阶段 $stageIndex 未找到按钮")
            ToastUtils.show(this@FloatingMenuService, "未找到: 阶段${stageIndex}")
            return false
        }
```

Lines 966-968 `withContext(Dispatchers.Main) { ToastUtils.show(...) }` — already removed above.

Line 1004-1006 — change:

```kotlin
        AccessibilityClickService.getInstance()?.performGlobalClick(button.x, button.y)
```

- [ ] **Step 2: Remove redundant `withContext` in `executePollStage`**

Lines 1061-1063 — change:

```kotlin
        LogConsole.w(TAG, "阶段 $stageIndex 超时未找到按钮")
        ToastUtils.show(this@FloatingMenuService, "未找到: 阶段${stageIndex}")
```

- [ ] **Step 3: Remove redundant `withContext(Dispatchers.Main)` in gift workflow error handler**

Lines 623-646 in the giftJob catch block — the existing code uses:
```kotlin
                            withContext(Dispatchers.Main) {
                                updateGiftButtonState()
                                updateTaskIndicator(indicatorGift, false)
                                updateOverallStatus()
                            }
```
and later:
```kotlin
                            withContext(Dispatchers.Main) {
                                ToastUtils.show(this@FloatingMenuService, "礼物任务终止: ${e.message}")
                            }
```

Replace both blocks to call directly without `withContext`:

```kotlin
                            updateGiftButtonState()
                            updateTaskIndicator(indicatorGift, false)
                            updateOverallStatus()
```

```kotlin
                            ToastUtils.show(this@FloatingMenuService, "礼物任务终止: ${e.message}")
```

(These are inside `serviceScope.launch` which uses `Dispatchers.Main`.)

- [ ] **Step 4: Build to verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/jdhelper/app/service/FloatingMenuService.kt
git commit -m "perf: remove redundant withContext(Main) in gift workflow (already on Main dispatcher)"
```

---

### Task 9: Verify builds clean

- [ ] **Step 1: Full clean build**

```bash
./gradlew clean assembleDebug
```

Expected: BUILD SUCCESSFUL, no warnings related to our changes.

- [ ] **Step 2: Final commit summary**

```bash
git add -A
git status
```

Review that only the 5 files we touched are modified, then commit any remaining uncommitted changes.

## Files Changed Summary

| File | Tasks |
|------|-------|
| `app/.../LogConsole.kt` | 1 |
| `app/.../FloatingService.kt` | 2 |
| `app/.../HomeViewModel.kt` | 3 |
| `app/.../FloatingMenuService.kt` | 4, 7, 8 |
| `app/.../DefaultTimeService.kt` | 5 |
| `app/.../TopStatusBar.kt` | 5 |
| `app/.../JdTimeService.kt` | 6 |
