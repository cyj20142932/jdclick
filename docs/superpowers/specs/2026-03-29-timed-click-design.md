# 整分定时点击功能设计

## 概述

在悬浮菜单中添加启动按钮，点击后自动查找屏幕上包含"立即支付"或"提交订单"文字的按钮，记录位置后使用NTP校准时间，在下一个整分时刻（可配置延迟）自动点击该按钮。

## 功能流程

```
用户点击启动按钮
    ↓
查找目标按钮（"立即支付" 或 "提交订单"）
    ↓
记录按钮位置 (x, y)
    ↓
NTP时间校准
    ↓
显示悬浮时钟
    ↓
等待到下一个整分 + 延迟时间
    ↓
执行点击
    ↓
任务完成，关闭悬浮时钟
```

重复点击启动按钮时：关闭现有任务 → 重新查找按钮 → 重新校准时间 → 重新显示悬浮时钟 → 在新的整分时刻点击

## 数据模型

### ClickSettings (Room Entity)
| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 主键，默认1 |
| delayMillis | Double | 延迟时间(毫秒)，支持负数和小数 |

## 组件设计

### 1. ClickSettingsRepository
- 职责：管理延迟配置的CRUD
- 接口：
  - `getDelayMillis(): Flow<Double>` - 实时观察延迟值变化
  - `setDelayMillis(delay: Double)` - 保存延迟值

### 2. ButtonFinder (新增)
- 职责：使用AccessibilityService查找目标按钮
- 接口：
  - `findTargetButton(): Point?` - 查找"立即支付"或"提交订单"按钮，返回位置或null

### 3. TimedClickManager (新增)
- 职责：管理整分点击任务的调度
- 接口：
  - `start(targetX: Int, targetY: Int, delayMillis: Double)` - 启动定时点击
  - `stop()` - 停止当前任务
  - `isRunning(): Boolean` - 检查是否有运行中的任务

### 4. FloatingMenuService 扩展
- 修改 btn_play 点击逻辑：
  - 首次点击：执行完整流程（查找按钮→校准时间→显示时钟→定时点击）
  - 再次点击：先stop()现有任务，再执行完整流程

### 5. FloatingService 复用
- 已有功能：显示悬浮时钟，无需修改
- 启动方式：通过Intent传递显示/隐藏指令

## 核心逻辑

### NTP时间计算
```kotlin
fun calculateNextMinuteTime(ntpTime: Long, delayMillis: Double): Long {
    val calendar = Calendar.getInstance().apply {
        timeInMillis = ntpTime
    }

    // 设置为下一分钟的00秒000毫秒
    calendar.add(Calendar.MINUTE, 1)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)

    // 加上延迟
    return calendar.timeInMillis + delayMillis.toLong()
}
```

### 按钮查找逻辑
```kotlin
fun findTargetButton(root: AccessibilityNodeInfo): Point? {
    // 遍历所有节点，查找文字包含"立即支付"或"提交订单"的按钮
    // 返回第一个匹配项的中心坐标
}
```

## 错误处理

| 场景 | 处理方式 |
|------|----------|
| 找不到目标按钮 | 使用默认位置（屏幕右下角向左140px，向上100px），Toast提示"未找到目标按钮，使用默认位置" |
| NTP校准失败 | 使用本地时间，并提示用户"时间未校准" |
| 悬浮时钟启动失败 | Toast提示，继续执行点击任务 |
| 定时点击执行后 | Toast提示"已执行点击" |
| 定时点击失败 | 记录日志，任务结束 |

## 默认点击位置

当找不到目标按钮时，使用屏幕右下角作为参考点：
- X = 屏幕宽度 - 140
- Y = 屏幕高度 - 100

可通过 DisplayMetrics 获取屏幕尺寸后计算。

## 数据流

```
FloatingMenuService (启动按钮点击)
    ↓
ButtonFinder.findTargetButton()
    ↓ 位置信息
NtpTimeService.getCurrentTime()
    ↓ NTP时间
FloatingService.startService()
    ↓ 显示时钟
TimedClickManager.start(x, y, delay)
    ↓ 等待到整分时刻
AccessibilityClickService.performClick(x, y)
    ↓ 点击完成
TimedClickManager.stop()
FloatingService.stopService()
```

## 配置文件位置

- 设计文档：`docs/superpowers/specs/2026-03-29-timed-click-design.md`