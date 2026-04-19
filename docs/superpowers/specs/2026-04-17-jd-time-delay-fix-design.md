---
name: jd-time-delay-fix
description: 修复京东时间服务中网络延迟计算方向错误的问题
type: project
---

# 京东时间服务延迟计算修复设计

## 问题描述

京东时间服务（`JdTimeService`）在计算网络延迟时存在逻辑错误，导致获取的京东时间明显快于实际时间。

## 根因分析

在 `JdTimeService.requestJdTime()` 方法中（第110行），延迟计算公式为：

```kotlin
val localTimeAtServer = requestTime + networkDelay
val timeDiff = localTimeAtServer - finalServerTime
```

**问题**：使用 `requestTime + networkDelay` 来估算服务器时间戳对应的本地时刻是不正确的。

- `requestTime` 是客户端**发出请求**的时刻
- `finalServerTime` 是服务器**处理完成并返回响应**的时刻
- 当客户端收到响应时（`responseTime`），服务器已经在 `networkDelay` 之前处理完请求了

因此，服务器时间戳对应的本地时刻应该是 `responseTime - networkDelay`，而不是 `requestTime + networkDelay`。

## 修复方案

修改 `JdTimeService.kt` 第110行：

```kotlin
// 修改前（错误）
val localTimeAtServer = requestTime + networkDelay

// 修改后（正确）
val localTimeAtServer = responseTime - networkDelay
```

## 影响范围

- 文件：`app/src/main/java/com/jdhelper/app/service/JdTimeService.kt`
- 方法：`requestJdTime()`
- 行号：第110行

## 测试建议

1. 修复前后对比日志输出中的 `localTimeAtServer` 和 `timeDiff` 值
2. 验证京东时间与实际时间的偏差是否在合理范围内（±500ms）
3. 可以对比 NTP 时间源的结果进行交叉验证

## 风险评估

- **风险等级**：低
- **影响范围**：仅影响京东时间源的计算结果
- **回滚方案**：简单撤销一行代码即可恢复