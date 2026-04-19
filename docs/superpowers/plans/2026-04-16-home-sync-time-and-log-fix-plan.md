# 首页自动同步时间 & 日志显示修复实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复两个问题：1) 进入首页自动同步时间 2) 修复 LogConsole 日志不显示问题

**Architecture:** 
- 问题1：在 HomeViewModel.init() 中添加同步调用
- 问题2：通过在 Application 中主动获取 LogConsoleInitializer 来触发 Hilt 注入，确保 LogConsole.setRepository() 被调用

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, Room

---

## 文件结构

- Modify: `app/src/main/java/com/jdhelper/app/ui/screens/home/HomeViewModel.kt`
- Modify: `app/src/main/java/com/jdhelper/app/JDHelperApp.kt`
- Modify: `app/src/main/java/com/jdhelper/app/di/ServiceModule.kt`

---

## Task 1: 首页自动同步时间

**Files:**
- Modify: `app/src/main/java/com/jdhelper/app/ui/screens/home/HomeViewModel.kt:95-99`

- [ ] **Step 1: 在 HomeViewModel.init() 中添加自动同步调用**

修改 `HomeViewModel.kt` 第 95-99 行：

```kotlin
init {
    checkAllPermissions()
    startStatusRefreshTimer()
    // 进入首页自动同步时间
    viewModelScope.launch {
        syncNtpTime()
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `./gradlew assembleDebug 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/com/jdhelper/app/ui/screens/home/HomeViewModel.kt
git commit -m "feat: 进入首页自动同步时间"
```

---

## Task 2: 修复日志初始化问题

**Files:**
- Modify: `app/src/main/java/com/jdhelper/app/di/ServiceModule.kt:40-46`
- Modify: `app/src/main/java/com/jdhelper/app/JDHelperApp.kt:14-19`

- [ ] **Step 1: 修改 ServiceModule，添加 @Inject 注解的 LogConsoleInitializer**

修改 `ServiceModule.kt`，将 LogConsoleInitializer 改为需要被注入的类：

```kotlin
@Singleton
class LogConsoleInitializer @Inject constructor(
    private val logRepository: LogRepository
) {
    init {
        LogConsole.setRepository(logRepository)
    }
}
```

- [ ] **Step 2: 在 JDHelperApp 中主动请求 LogConsoleInitializer 注入**

修改 `JDHelperApp.kt` 第 14-19 行：

```kotlin
@HiltAndroidApp
class JDHelperApp : Application() {

    @Inject
    lateinit var logConsoleInitializer: LogConsoleInitializer

    override fun onCreate() {
        super.onCreate()
        // 加载点击持续时间配置
        loadClickDuration(this)
    }

    private fun loadClickDuration(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val duration = prefs.getLong(KEY_CLICK_DURATION, DEFAULT_CLICK_DURATION)
        // 确保最小值为50ms
        AccessibilityClickService.clickDuration = duration.coerceAtLeast(MIN_CLICK_DURATION)
    }
}
```

- [ ] **Step 3: 编译验证**

Run: `./gradlew assembleDebug 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 提交**

```bash
git add app/src/main/java/com/jdhelper/app/di/ServiceModule.kt app/src/main/java/com/jdhelper/app/JDHelperApp.kt
git commit -m "fix: 修复 LogConsole 日志不显示问题"
```

---

## Task 3: 整体验证

- [ ] **Step 1: 编译并安装测试**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: 测试问题1 - 首页自动同步**
- 运行 App，进入首页
- 观察时间是否自动同步（UI 显示同步状态变化）

- [ ] **Step 3: 测试问题2 - 日志显示**
- 进行任意操作（如同步时间）
- 进入日志页面
- 验证日志是否正确显示

- [ ] **Step 4: 最终提交**

```bash
git add .
git commit -m "fix: 修复首页自动同步和日志显示问题"
```