# HomeViewModel 时间同步实时刷新实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现HomeScreen点击时间同步后，悬浮时钟和状态栏实时刷新，移除1.5秒延迟

**Architecture:** 使用FloatingStateManager广播机制，替代FloatingService.refreshService()的停止→重启流程

**Tech Stack:** Kotlin, Android ViewModel, Hilt DI, BroadcastReceiver

---

## Task 1: 修改 HomeViewModel 集成 FloatingStateManager

**Files:**
- Modify: `app/src/main/java/com/jdhelper/app/ui/screens/home/HomeViewModel.kt`

- [ ] **Step 1: 添加 FloatingStateManager 导入**

在文件顶部找到现有导入区域，在 `import com.jdhelper.service` 块中添加：

```kotlin
import com.jdhelper.app.service.FloatingStateManager
```

- [ ] **Step 2: 添加 FloatingStateManager 注入**

修改构造函数 (第43-48行)，在 `application: Application` 后添加：

```kotlin
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val ntpTimeService: NtpTimeService,
    private val clickSettingsRepository: ClickSettingsRepository,
    private val floatingStateManager: FloatingStateManager,
    application: Application
) : ViewModel() {
```

- [ ] **Step 3: 修改 syncNtpTime() 方法**

找到 `syncNtpTime()` 方法 (约第186-223行)，替换同步成功后的逻辑：

**原代码 (第207-211行):**
```kotlin
// 同步成功后刷新悬浮时钟
if (_uiState.value.isFloatingEnabled) {
    FloatingService.refreshService(application)
    Log.d(TAG, "悬浮时钟已刷新")
}
```

**替换为:**
```kotlin
// 新增：通知状态变化，广播给所有悬浮窗即时刷新
floatingStateManager.notifyNtpSyncChanged(success, offset)
floatingStateManager.requestRefreshTime()
Log.d(TAG, "已发送广播通知刷新")
```

- [ ] **Step 4: 验证并提交**

```bash
cd D:/Workspace/Auto/auto_clicker
./gradlew assembleDebug
```

预期结果：编译成功，无错误

- [ ] **Step 5: 提交变更**

```bash
git add app/src/main/java/com/jdhelper/app/ui/screens/home/HomeViewModel.kt
git commit -m "feat: add real-time refresh after NTP sync via broadcast"
```

---

## 验收清单

### 功能验收
- [ ] 点击HomeScreen"时间同步"按钮后，状态栏立即刷新（无1.5秒延迟）
- [ ] 悬浮时钟实时刷新显示
- [ ] 悬浮菜单实时刷新显示

### 性能验收
- [ ] 状态同步延迟 < 100ms
- [ ] 点击同步按钮到UI更新 < 200ms