# 首页自动同步时间 & 日志显示修复设计

**日期：** 2026-04-16
**状态：** 已批准

---

## 1. 问题描述

### 问题1：进入首页需要手动同步时间
- **现象：** 用户打开 App 后，需要手动点击"时间同步"按钮才能同步时间
- **期望：** 进入首页时立即自动触发时间同步

### 问题2：App 内置日志页面完全没有日志显示
- **现象：** 使用 LogConsole.d/i/w/e 打印的日志在日志页面完全看不到
- **根因：** LogConsoleInitializer 从未被 Hilt 注入，导致 LogConsole.setRepository() 永远不会被调用，logRepository 始终为 null

---

## 2. 解决方案

### 问题1：首页自动同步

**方案：** 在 HomeViewModel.init() 中调用时间同步

**修改文件：** `app/src/main/java/com/jdhelper/app/ui/screens/home/HomeViewModel.kt`

```kotlin
init {
    checkAllPermissions()
    startStatusRefreshTimer()
    // 新增：进入首页自动同步时间
    viewModelScope.launch {
        syncNtpTime()
    }
}
```

---

### 问题2：日志初始化修复

**方案：** 将 LogConsole 改为由 Hilt 管理的单例类，在构造时自动接收 Repository 注入

**修改文件：**

1. **LogConsole.kt** - 改为 class + @Inject 构造器
   ```kotlin
   @Singleton
   class LogConsole @Inject constructor(
       private val logRepository: LogRepository
   ) {
       companion object {
           @Volatile
           private var instance: LogConsole? = null
           fun get(): LogConsole = instance!!
       }

       init {
           instance = this
       }

       fun d(tag: String, msg: String) = log(DEBUG, tag, msg)
       // ... 其他方法
   }
   ```

2. **ServiceModule.kt** - 删除 LogConsoleInitializer
   - 删除 provideLogConsoleInitializer 方法
   - 删除 LogConsoleInitializer 类

3. **调用处修改** - 所有调用 LogConsole 的地方
   - 从 `LogConsole.d(TAG, msg)` 改为 `LogConsole.get().d(TAG, msg)`
   - 或者使用 Hilt 注入 LogConsole

---

## 3. 数据流

### 问题1 - 首页自动同步
```
HomeScreen composable
    → HomeViewModel init
    → timeManager.syncTime()
    → NtpTimeService / JdTimeService
    → 同步完成，更新 UI 状态
```

### 问题2 - 日志显示
```
任意组件调用 LogConsole.get().d(TAG, msg)
    → LogConsole.log()
    → 1. 输出到 Android Log
    → 2. 写入 Room 数据库 (logRepository 已知)
    → LogScreen 读取数据库显示
```

---

## 4. 测试要点

1. **问题1测试：**
   - 打开 App 进入首页
   - 验证时间同步是否自动触发（UI 显示同步状态）
   - 验证日志中是否有同步记录

2. **问题2测试：**
   - 任意操作触发 LogConsole 调用
   - 进入日志页面
   - 验证日志是否正确显示

---

## 5. 风险与回滚

- **风险低：** 修改范围明确，影响有限
- **回滚方案：** 通过 git revert 快速回滚