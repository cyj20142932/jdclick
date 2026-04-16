# 时间源缓存不一致问题修复实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 FloatingMenuService 中 3 处直接调用数据库获取时间源的地方，改为使用已缓存的 currentTimeSource 变量，解决切换为京东时间源后日志显示 ntp 的问题。

**Architecture:** 在 FloatingMenuService 中，将所有 `clickSettingsRepository.getTimeSource().first()` 调用替换为使用已缓存的 `currentTimeSource` 变量。该变量通过 collect 监听数据库变化并保持更新。

**Tech Stack:** Kotlin, Android, Room Database, Hilt DI

---

## 文件结构

修改文件：
- `app/src/main/java/com/jdhelper/app/service/FloatingMenuService.kt` - 修改 3 处时间源获取逻辑

---

## Task 1: 修改 stage=0 记录处的时间源获取

**Files:**
- Modify: `app/src/main/java/com/jdhelper/app/service/FloatingMenuService.kt:703`

- [ ] **Step 1: 修改第 703 行代码**

将：
```kotlin
val timeSource = clickSettingsRepository.getTimeSource().first()
```

改为：
```kotlin
val timeSource = currentTimeSource
```

- [ ] **Step 2: 验证修改**

确认修改后的代码在 ~703 行附近

---

## Task 2: 修改 stage=1 记录处的时间源获取

**Files:**
- Modify: `app/src/main/java/com/jdhelper/app/service/FloatingMenuService.kt:967`

- [ ] **Step 1: 修改第 967 行代码**

将：
```kotlin
val timeSource = clickSettingsRepository.getTimeSource().first()
```

改为：
```kotlin
val timeSource = currentTimeSource
```

- [ ] **Step 2: 验证修改**

确认修改后的代码在 ~967 行附近

---

## Task 3: 修改 stage=2 记录处的时间源获取

**Files:**
- Modify: `app/src/main/java/com/jdhelper/app/service/FloatingMenuService.kt:1008`

- [ ] **Step 1: 修改第 1008 行代码**

将：
```kotlin
val timeSource = clickSettingsRepository.getTimeSource().first()
```

改为：
```kotlin
val timeSource = currentTimeSource
```

- [ ] **Step 2: 验证修改**

确认修改后的代码在 ~1008 行附近

---

## Task 4: 构建验证

**Files:**
- Build: `app/build.gradle.kts`

- [ ] **Step 1: 运行 assembleDebug 验证代码编译通过**

```bash
cd D:/Workspace/Auto/auto_clicker && ./gradlew assembleDebug --no-daemon
```

Expected: BUILD SUCCESSFUL

---

## Task 5: 测试验证

**Files:**
- Manual test: 在手机上测试时间源切换功能

- [ ] **Step 1: 切换时间源为 JD**
- [ ] **Step 2: 执行自动点击任务**
- [ ] **Step 3: 检查日志中 timeSource 返回值应为 JD**
- [ ] **Step 4: 检查数据库历史记录中 timeSource 字段应为 JD**

---

## 验证清单

修改完成后，确认以下内容：
- [ ] FloatingMenuService.kt 中不再有 `clickSettingsRepository.getTimeSource().first()` 调用
- [ ] 所有 3 处时间源获取都改为使用 `currentTimeSource` 变量
- [ ] 代码编译成功
- [ ] 手动测试验证时间源记录正确