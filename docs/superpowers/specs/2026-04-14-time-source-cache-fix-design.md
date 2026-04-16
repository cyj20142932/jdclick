# 时间源缓存不一致问题修复设计

## 问题描述

在切换为京东时间源后，执行自动点击时，日志中通过 `clickSettingsRepository.getTimeSource()` 获取的时间源返回的是 ntp，而不是预期的 jd。

但 UI 显示正确，使用的是 `DefaultTimeService.getCurrentTimeSource()` 方法。

## 问题根因

代码中存在两套获取时间源的方式：

1. **缓存变量方式**（正确）：
   - `FloatingMenuService.currentTimeSource` 变量
   - `DefaultTimeService.cachedTimeSource` 变量
   - 通过 `collect` 监听数据库变化并更新

2. **直接数据库查询方式**（有 bug）：
   - 多处直接调用 `clickSettingsRepository.getTimeSource().first()` 从数据库读取

当使用直接数据库查询时，可能因为 Room Flow 的时序问题或默认值设置，导致返回旧的或默认的 NTP 值，而不是当前已设置的 JD。

## 修复方案

统一使用 `FloatingMenuService` 中已缓存的 `currentTimeSource` 变量，替换所有直接调用数据库的方法。

### 需要修改的位置

| 位置 | 行号 | 当前代码 | 修改后 |
|------|------|----------|--------|
| stage=0 记录 | ~703 | `clickSettingsRepository.getTimeSource().first()` | `currentTimeSource` |
| stage=1 记录 | ~967 | `clickSettingsRepository.getTimeSource().first()` | `currentTimeSource` |
| stage=2 记录 | ~1008 | `clickSettingsRepository.getTimeSource().first()` | `currentTimeSource` |

### 修改示例

```kotlin
// 修改前
val timeSource = clickSettingsRepository.getTimeSource().first()

// 修改后
val timeSource = currentTimeSource
```

## 架构设计

```
┌─────────────────────────────────────────────────────────┐
│                     ClickSettingsDao                     │
│  getTimeSource(): Flow<TimeSource?>                      │
└─────────────────────────┬───────────────────────────────┘
                          │ collect 监听
                          ▼
┌─────────────────────────────────────────────────────────┐
│               FloatingMenuService                        │
│  currentTimeSource: TimeSource (缓存变量)                 │
└─────────────────────────┬───────────────────────────────┘
                          │
        ┌─────────────────┼─────────────────┐
        ▼                 ▼                 ▼
   stage=0 记录      stage=1 记录      stage=2 记录
   使用缓存变量      使用缓存变量      使用缓存变量
```

## 测试验证

1. 切换时间源为 JD
2. 执行自动点击任务
3. 检查日志中 `getTimeSource()` 返回的值应为 JD
4. 检查历史记录中 timeSource 字段应为 JD

## 影响范围

- 仅修改 `FloatingMenuService.kt` 中的 3 处调用
- 不影响其他服务或 UI 层
- 向后兼容，无 breaking change