# 删除NTP时间源实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 删除NTP时间源及相关代码，保留京东时间源作为唯一时间源，精简app代码维护成本。

**Architecture:** 删除NtpTimeService类，移除所有NTP相关依赖和UI，将默认时间源改为JD，保持京东时间源功能完整。

**Tech Stack:** Kotlin, Android, Room, Jetpack Compose, Hilt

---

### Task 1: 删除NtpTimeService文件

**Files:**
- Delete: `app/src/main/java/com/jdhelper/app/service/NtpTimeService.kt`

- [ ] **Step 1: 删除文件**

```bash
rm app/src/main/java/com/jdhelper/app/service/NtpTimeService.kt
```

- [ ] **Step 2: 验证文件已删除**

```bash
ls app/src/main/java/com/jdhelper/app/service/NtpTimeService.kt
```
Expected: `No such file or directory`

- [ ] **Step 3: 提交删除**

```bash
git add app/src/main/java/com/jdhelper/app/service/NtpTimeService.kt
git commit -m "feat: 删除NtpTimeService文件"
```

### Task 2: 修改ServiceModule.kt

**Files:**
- Modify: `app/src/main/java/com/jdhelper/app/di/ServiceModule.kt`

- [ ] **Step 1: 查看当前文件内容**

```bash
cat app/src/main/java/com/jdhelper/app/di/ServiceModule.kt
```

- [ ] **Step 2: 删除NTP相关导入和方法**

编辑文件，删除以下内容：
```kotlin
import com.jdhelper.app.service.NtpTimeService
```

删除以下方法：
```kotlin
    @Provides
    @Singleton
    fun provideNtpTimeService(@ApplicationContext context: Context): NtpTimeService {
        return NtpTimeService(context)
    }
```

从DefaultTimeService的provide方法参数中删除 `ntpTimeService: NtpTimeService,`

- [ ] **Step 3: 验证修改**

```bash
grep -n "NtpTimeService" app/src/main/java/com/jdhelper/app/di/ServiceModule.kt
```
Expected: 无输出

- [ ] **Step 4: 提交修改**

```bash
git add app/src/main/java/com/jdhelper/app/di/ServiceModule.kt
git commit -m "feat: 删除ServiceModule中的NTP依赖注入"
```

### Task 3: 修改ClickSettings.kt

**Files:**
- Modify: `app/src/main/java/com/jdhelper/app/data/local/ClickSettings.kt:18`

- [ ] **Step 1: 查看当前内容**

```bash
cat app/src/main/java/com/jdhelper/app/data/local/ClickSettings.kt
```

- [ ] **Step 2: 修改默认值**

编辑第18行，将：
```kotlin
    val timeSource: TimeSource = TimeSource.NTP  // 新增：时间源
```
改为：
```kotlin
    val timeSource: TimeSource = TimeSource.JD  // 新增：时间源
```

- [ ] **Step 3: 验证修改**

```bash
grep -n "TimeSource.NTP" app/src/main/java/com/jdhelper/app/data/local/ClickSettings.kt
```
Expected: 无输出

```bash
grep -n "TimeSource.JD" app/src/main/java/com/jdhelper/app/data/local/ClickSettings.kt
```
Expected: `18:    val timeSource: TimeSource = TimeSource.JD  // 新增：时间源`

- [ ] **Step 4: 提交修改**

```bash
git add app/src/main/java/com/jdhelper/app/data/local/ClickSettings.kt
git commit -m "feat: 修改ClickSettings默认时间源为JD"
```

### Task 4: 修改DefaultTimeService.kt

**Files:**
- Modify: `app/src/main/java/com/jdhelper/app/service/DefaultTimeService.kt`

- [ ] **Step 1: 查看当前内容**

```bash
cat app/src/main/java/com/jdhelper/app/service/DefaultTimeService.kt
```

- [ ] **Step 2: 删除NTP相关导入和依赖**

删除以下导入：
```kotlin
import com.jdhelper.app.service.NtpTimeService
```

从构造函数参数中删除：
```kotlin
    private val ntpTimeService: NtpTimeService,
```

- [ ] **Step 3: 修改默认值**

将第30行：
```kotlin
    private var cachedTimeSource: TimeSource = TimeSource.NTP
```
改为：
```kotlin
    private var cachedTimeSource: TimeSource = TimeSource.JD
```

- [ ] **Step 4: 删除NTP分支逻辑**

删除 `getCurrentTime()` 方法中的NTP分支：
```kotlin
            TimeSource.NTP -> ntpTimeService.getCurrentTime()
```

删除 `getTimeOffset()` 方法中的NTP分支：
```kotlin
            TimeSource.NTP -> ntpTimeService.getTimeOffset()
```

删除 `syncTime()` 方法中的NTP分支：
```kotlin
            TimeSource.NTP -> ntpTimeService.syncTime()
```

删除 `isSynced()` 方法中的NTP分支：
```kotlin
            TimeSource.NTP -> ntpTimeService.isSynced()
```

- [ ] **Step 5: 验证修改**

```bash
grep -n "TimeSource.NTP" app/src/main/java/com/jdhelper/app/service/DefaultTimeService.kt
```
Expected: 无输出

```bash
grep -n "ntpTimeService" app/src/main/java/com/jdhelper/app/service/DefaultTimeService.kt
```
Expected: 无输出

- [ ] **Step 6: 提交修改**

```bash
git add app/src/main/java/com/jdhelper/app/service/DefaultTimeService.kt
git commit -m "feat: 删除DefaultTimeService中的NTP逻辑"
```

### Task 5: 修改FloatingService.kt

**Files:**
- Modify: `app/src/main/java/com/jdhelper/app/service/FloatingService.kt`

- [ ] **Step 1: 查看当前内容**

```bash
cat app/src/main/java/com/jdhelper/app/service/FloatingService.kt
```

- [ ] **Step 2: 删除NTP相关代码**

删除第103行：
```kotlin
    private var ntpTimeService: NtpTimeService? = null
```

删除第126行，将：
```kotlin
    private var currentTimeSource: TimeSource = TimeSource.NTP
```
改为：
```kotlin
    private var currentTimeSource: TimeSource = TimeSource.JD
```

删除第135行：
```kotlin
        ntpTimeService = NtpTimeService(this)
```

- [ ] **Step 3: 验证修改**

```bash
grep -n "NtpTimeService" app/src/main/java/com/jdhelper/app/service/FloatingService.kt
```
Expected: 无输出

```bash
grep -n "TimeSource.NTP" app/src/main/java/com/jdhelper/app/service/FloatingService.kt
```
Expected: 无输出

- [ ] **Step 4: 提交修改**

```bash
git add app/src/main/java/com/jdhelper/app/service/FloatingService.kt
git commit -m "feat: 删除FloatingService中的NTP相关代码"
```

### Task 6: 修改FloatingMenuService.kt

**Files:**
- Modify: `app/src/main/java/com/jdhelper/app/service/FloatingMenuService.kt:141`

- [ ] **Step 1: 查看当前内容**

```bash
cat app/src/main/java/com/jdhelper/app/service/FloatingMenuService.kt
```

- [ ] **Step 2: 修改默认值**

将第141行：
```kotlin
    private var currentTimeSource: TimeSource = TimeSource.NTP
```
改为：
```kotlin
    private var currentTimeSource: TimeSource = TimeSource.JD
```

- [ ] **Step 3: 验证修改**

```bash
grep -n "TimeSource.NTP" app/src/main/java/com/jdhelper/app/service/FloatingMenuService.kt
```
Expected: 无输出

- [ ] **Step 4: 提交修改**

```bash
git add app/src/main/java/com/jdhelper/app/service/FloatingMenuService.kt
git commit -m "feat: 修改FloatingMenuService默认时间源为JD"
```

### Task 7: 修改HomeScreen.kt

**Files:**
- Modify: `app/src/main/java/com/jdhelper/app/ui/screens/home/HomeScreen.kt`

- [ ] **Step 1: 查看当前内容**

```bash
cat app/src/main/java/com/jdhelper/app/ui/screens/home/HomeScreen.kt
```

- [ ] **Step 2: 删除NTP选项UI**

删除第220行：
```kotlin
                                TimeSource.NTP -> "阿里云NTP时间"
```

删除第231行：
```kotlin
                            color = if (timeSource == TimeSource.NTP) Color.White else Color.Gray
```

删除第236行中的NTP分支：
```kotlin
                                viewModel.setTimeSource(if (isJd) TimeSource.JD else TimeSource.NTP)
```
改为：
```kotlin
                                viewModel.setTimeSource(TimeSource.JD)
```

- [ ] **Step 3: 简化UI逻辑**

由于只剩京东时间源，可以简化相关UI逻辑，只显示京东时间源状态。

- [ ] **Step 4: 验证修改**

```bash
grep -n "TimeSource.NTP" app/src/main/java/com/jdhelper/app/ui/screens/home/HomeScreen.kt
```
Expected: 无输出

- [ ] **Step 5: 提交修改**

```bash
git add app/src/main/java/com/jdhelper/app/ui/screens/home/HomeScreen.kt
git commit -m "feat: 删除HomeScreen中的NTP选项UI"
```

### Task 8: 修改TopStatusBar.kt

**Files:**
- Modify: `app/src/main/java/com/jdhelper/app/ui/components/TopStatusBar.kt`

- [ ] **Step 1: 查看当前内容**

```bash
cat app/src/main/java/com/jdhelper/app/ui/components/TopStatusBar.kt
```

- [ ] **Step 2: 修改默认参数**

将第28行：
```kotlin
    timeSource: TimeSource = TimeSource.NTP,
```
改为：
```kotlin
    timeSource: TimeSource = TimeSource.JD,
```

- [ ] **Step 3: 删除NTP显示分支**

删除第72行：
```kotlin
                        TimeSource.NTP -> ntpOffset
```

删除第76行：
```kotlin
                        TimeSource.NTP -> "NTP"
```

- [ ] **Step 4: 验证修改**

```bash
grep -n "TimeSource.NTP" app/src/main/java/com/jdhelper/app/ui/components/TopStatusBar.kt
```
Expected: 无输出

- [ ] **Step 5: 提交修改**

```bash
git add app/src/main/java/com/jdhelper/app/ui/components/TopStatusBar.kt
git commit -m "feat: 删除TopStatusBar中的NTP显示逻辑"
```

### Task 9: 修改HomeViewModel.kt

**Files:**
- Modify: `app/src/main/java/com/jdhelper/app/ui/screens/home/HomeViewModel.kt`

- [ ] **Step 1: 查看当前内容**

```bash
cat app/src/main/java/com/jdhelper/app/ui/screens/home/HomeViewModel.kt
```

- [ ] **Step 2: 删除NTP相关导入和依赖**

删除第19行：
```kotlin
import com.jdhelper.app.service.NtpTimeService
```

从构造函数参数中删除：
```kotlin
    private val ntpTimeService: NtpTimeService,
```

- [ ] **Step 3: 修改默认值**

将第75行：
```kotlin
        .stateIn(viewModelScope, SharingStarted.Eagerly, TimeSource.NTP)
```
改为：
```kotlin
        .stateIn(viewModelScope, SharingStarted.Eagerly, TimeSource.JD)
```

- [ ] **Step 4: 验证修改**

```bash
grep -n "NtpTimeService" app/src/main/java/com/jdhelper/app/ui/screens/home/HomeViewModel.kt
```
Expected: 无输出

```bash
grep -n "TimeSource.NTP" app/src/main/java/com/jdhelper/app/ui/screens/home/HomeViewModel.kt
```
Expected: 无输出

- [ ] **Step 5: 提交修改**

```bash
git add app/src/main/java/com/jdhelper/app/ui/screens/home/HomeViewModel.kt
git commit -m "feat: 删除HomeViewModel中的NTP依赖"
```

### Task 10: 修改SettingsViewModel.kt

**Files:**
- Modify: `app/src/main/java/com/jdhelper/app/ui/screens/settings/SettingsViewModel.kt`

- [ ] **Step 1: 查看当前内容**

```bash
cat app/src/main/java/com/jdhelper/app/ui/screens/settings/SettingsViewModel.kt
```

- [ ] **Step 2: 删除NTP相关导入和依赖**

删除第14行：
```kotlin
import com.jdhelper.app.service.NtpTimeService
```

从构造函数参数中删除：
```kotlin
    private val ntpTimeService: NtpTimeService,
```

- [ ] **Step 3: 删除getNtpServers方法**

删除第70行的方法：
```kotlin
    fun getNtpServers(): List<String> = NtpTimeService.NTP_SERVERS
```

- [ ] **Step 4: 验证修改**

```bash
grep -n "NtpTimeService" app/src/main/java/com/jdhelper/app/ui/screens/settings/SettingsViewModel.kt
```
Expected: 无输出

- [ ] **Step 5: 提交修改**

```bash
git add app/src/main/java/com/jdhelper/app/ui/screens/settings/SettingsViewModel.kt
git commit -m "feat: 删除SettingsViewModel中的NTP相关代码"
```

### Task 11: 修改ClickSettingsRepositoryImpl.kt

**Files:**
- Modify: `app/src/main/java/com/jdhelper/app/data/repository/ClickSettingsRepositoryImpl.kt`

- [ ] **Step 1: 查看当前内容**

```bash
cat app/src/main/java/com/jdhelper/app/data/repository/ClickSettingsRepositoryImpl.kt
```

- [ ] **Step 2: 修改默认值**

将第85行：
```kotlin
            .map { it ?: TimeSource.NTP }
```
改为：
```kotlin
            .map { it ?: TimeSource.JD }
```

将第86行：
```kotlin
            .stateIn(CoroutineScope(Dispatchers.Main), SharingStarted.Eagerly, TimeSource.NTP)
```
改为：
```kotlin
            .stateIn(CoroutineScope(Dispatchers.Main), SharingStarted.Eagerly, TimeSource.JD)
```

- [ ] **Step 3: 验证修改**

```bash
grep -n "TimeSource.NTP" app/src/main/java/com/jdhelper/app/data/repository/ClickSettingsRepositoryImpl.kt
```
Expected: 无输出

- [ ] **Step 4: 提交修改**

```bash
git add app/src/main/java/com/jdhelper/app/data/repository/ClickSettingsRepositoryImpl.kt
git commit -m "feat: 修改ClickSettingsRepositoryImpl默认时间源为JD"
```

### Task 12: 修改TimeViewModel.kt

**Files:**
- Modify: `app/src/main/java/com/jdhelper/app/ui/screens/time/TimeViewModel.kt:39`

- [ ] **Step 1: 查看当前内容**

```bash
cat app/src/main/java/com/jdhelper/app/ui/screens/time/TimeViewModel.kt
```

- [ ] **Step 2: 修改默认值**

将第39行：
```kotlin
        .stateIn(scope, SharingStarted.Eagerly, TimeSource.NTP)
```
改为：
```kotlin
        .stateIn(scope, SharingStarted.Eagerly, TimeSource.JD)
```

- [ ] **Step 3: 验证修改**

```bash
grep -n "TimeSource.NTP" app/src/main/java/com/jdhelper/app/ui/screens/time/TimeViewModel.kt
```
Expected: 无输出

- [ ] **Step 4: 提交修改**

```bash
git add app/src/main/java/com/jdhelper/app/ui/screens/time/TimeViewModel.kt
git commit -m "feat: 修改TimeViewModel默认时间源为JD"
```

### Task 13: 修改GiftClickHistory.kt

**Files:**
- Modify: `app/src/main/java/com/jdhelper/app/data/local/GiftClickHistory.kt:18`

- [ ] **Step 1: 查看当前内容**

```bash
cat app/src/main/java/com/jdhelper/app/data/local/GiftClickHistory.kt
```

- [ ] **Step 2: 修改默认值**

将第18行：
```kotlin
    val timeSource: String = "NTP"  // 新增: "NTP" 或 "JD"
```
改为：
```kotlin
    val timeSource: String = "JD"  // 新增: "NTP" 或 "JD"
```

- [ ] **Step 3: 验证修改**

```bash
grep -n '"NTP"' app/src/main/java/com/jdhelper/app/data/local/GiftClickHistory.kt
```
Expected: 无输出

- [ ] **Step 4: 提交修改**

```bash
git add app/src/main/java/com/jdhelper/app/data/local/GiftClickHistory.kt
git commit -m "feat: 修改GiftClickHistory默认时间源为JD"
```

### Task 14: 删除build.gradle.kts中的依赖

**Files:**
- Modify: `app/build.gradle.kts:123`

- [ ] **Step 1: 查看当前内容**

```bash
cat app/build.gradle.kts | grep -n "commons-net"
```

- [ ] **Step 2: 删除依赖**

编辑第123行，删除：
```kotlin
    implementation("commons-net:commons-net:3.11.1")
```

- [ ] **Step 3: 验证修改**

```bash
grep -n "commons-net" app/build.gradle.kts
```
Expected: 无输出

- [ ] **Step 4: 提交修改**

```bash
git add app/build.gradle.kts
git commit -m "feat: 删除commons-net依赖"
```

### Task 15: 编译测试

**Files:**
- Test: 整个项目编译

- [ ] **Step 1: 同步Gradle**

```bash
./gradlew sync
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: 编译Debug版本**

```bash
./gradlew assembleDebug
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 运行测试**

```bash
./gradlew test
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 提交最终状态**

```bash
git add .
git commit -m "feat: 完成NTP时间源删除，编译测试通过"
```

### Task 16: 合并回主分支

**Files:**
- Git操作

- [ ] **Step 1: 切换到主分支**

```bash
git checkout main
```

- [ ] **Step 2: 合并工作树分支**

```bash
git merge worktree-remove-ntp-time-source
```

- [ ] **Step 3: 验证合并**

```bash
git log --oneline -5
```
Expected: 看到所有提交记录

- [ ] **Step 4: 推送更改**

```bash
git push origin main
```

- [ ] **Step 5: 清理工作树**

```bash
git worktree remove .claude/worktrees/remove-ntp-time-source
```