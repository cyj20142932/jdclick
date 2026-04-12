# 京东时间同步功能设计方案

**日期：** 2026-04-11
**主题：** 添加京东时间同步功能，支持用户选择NTP或京东时间作为悬浮时钟时间源

---

## 1. 需求概述

### 1.1 功能目标

| 需求 | 描述 |
|------|------|
| 京东时间同步 | 通过京东API获取服务器时间，计算与本地时间差 |
| 时间源切换 | 全局开关，用户可选择NTP时间或京东时间作为显示时间 |
| 启动自动获取 | 每次打开悬浮时钟时自动请求京东时间差 |
| 状态栏显示 | 显示当前时间源（JD/NTP）及偏差 |
| 历史记录区分 | 记录每次点击使用的时间源类型 |

### 1.2 京东时间算法（来自参考JS）

```kotlin
// 1. 发起HTTP请求到京东API
// 2. 从响应头 x-api-request-id 提取时间戳
// 3. 解析格式: "xxxxx-xxxxx-时间戳-xxxxx"
// 4. 计算时间差: localTimeAtRequestMidpoint - serverTimeMillis
// 5. 网络延迟 = RTT / 2
// 6. 京东时间 = NTP时间 + 时间差
```

---

## 2. 技术设计

### 2.1 新增组件

| 文件 | 职责 |
|------|------|
| `JdTimeService.kt` | 京东时间同步服务，HTTP请求获取服务器时间 |
| `TimeSourceManager.kt` | 时间源管理器，提供统一的时间获取接口 |
| 修改 `ClickSettings` | 添加 timeSource 字段（enum: NTP, JD） |
| 修改 `GiftClickHistory` | 添加 timeSource 字段 |

### 2.2 API选择

使用京东移动端API：
```
https://api.m.jd.com/client.action?functionId=queryMaterialProducts
```

从响应头 `x-api-request-id` 解析服务器时间戳。

### 2.3 数据模型

**ClickSettings 新增字段：**
```kotlin
enum class TimeSource {
    NTP,    // 阿里云NTP时间
    JD      // 京东服务器时间
}

data class ClickSettings(
    // ... 现有字段
    val timeSource: TimeSource = TimeSource.NTP
)
```

**GiftClickHistory 新增字段：**
```kotlin
data class GiftClickHistory(
    // ... 现有字段
    val timeSource: String = "NTP"  // "NTP" 或 "JD"
)
```

---

## 3. UI设计

### 3.1 首页状态栏

```
┌────────────────────────────────────────┐
│  12:00:00.JD +25ms    ⏱ 00:30         │
└────────────────────────────────────────┘
```

显示格式：`时间.毫秒 时间源 偏差`

### 3.2 时间源切换开关

位置：首页状态卡片区域

```
┌─────────────────────────────┐
│  时间源: [NTP] ○ ─── ● JD   │
│  同步状态: JD +25ms         │
│  [同步京东时间]              │
└─────────────────────────────┘
```

### 3.3 悬浮时钟显示

根据设置的时间源显示对应时间，格式与设置中的毫秒位数一致。

---

## 4. 实现步骤

### Step 1: 创建 JdTimeService

- 新增 `JdTimeService.kt`
- 实现 HTTP 请求获取京东服务器时间
- 实现时间差计算逻辑
- 支持重试机制（3次）

### Step 2: 修改数据模型

- 修改 `ClickSettings` 添加 `timeSource` 字段
- 修改 `ClickSettingsRepository` 添加相关方法
- 修改 `GiftClickHistory` 添加 `timeSource` 字段

### Step 3: 创建 TimeSourceManager

- 统一时间获取接口
- 切换时间源逻辑
- 同时更新 NTP 和 JD 时间

### Step 4: 修改 UI 组件

- 修改 `HomeViewModel` 添加时间源状态
- 修改 `HomeScreen` 添加时间源切换UI
- 修改 `TopStatusBar` 显示时间源

### Step 5: 修改点击服务

- 修改 `AccessibilityClickService` 使用当前时间源
- 修改历史记录保存逻辑，记录时间源

---

## 5. 验收标准

### 5.1 功能验收

- [ ] 点击"同步京东时间"按钮能成功获取京东时间差
- [ ] 切换时间源开关，悬浮时钟显示对应时间
- [ ] 每次打开悬浮时钟自动重新获取京东时间差
- [ ] 状态栏显示当前时间源和偏差
- [ ] 历史记录区分显示NTP和JD时间源

### 5.2 性能验收

- [ ] 京东时间同步在3秒内完成
- [ ] 切换时间源即时生效，无延迟

---

## 6. 后续计划

- 添加手动刷新京东时间按钮（首页）
- 优化京东API请求频率限制
- 添加时间源切换的动画效果