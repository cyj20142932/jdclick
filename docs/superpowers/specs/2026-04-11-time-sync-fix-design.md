# JDHelper 时间同步修复与悬浮菜单优化规格文档

**日期：** 2026-04-11
**主题：** 时间同步修复 + 悬浮菜单优化
**问题修复：** 状态栏与悬浮时钟时间不同步、NTP偏差未显示、悬浮菜单布局优化

---

## 1. 问题分析

### 1.1 时间不同步问题

| 组件 | 当前使用时间源 | 问题 |
|------|----------------|------|
| TopStatusBar (首页) | `System.currentTimeMillis()` | 使用本地系统时间 |
| FloatingService (悬浮时钟) | `ntpTimeService.getCurrentTime()` | 使用NTP同步时间 |
| NtpTimeService | `@Singleton` 单例 | 已在使用，但UI层未接入 |

**根因**：首页顶部状态栏使用系统时间，而悬浮时钟使用NTP时间，两者时间源不一致。

### 1.2 NTP偏差未显示

- `HomeViewModel` 已计算 `ntpTimeOffset`（第196行）
- 但该值未传递给 `TopStatusBar` 组件显示
- 用户无法直观看到NTP同步的准确性

### 1.3 悬浮菜单问题

| 问题 | 当前状态 |
|------|----------|
| 无状态指示 | 按钮图标固定，无法区分任务运行/停止状态 |
| 缺少文字标签 | 只有图标（36dp），功能不直观 |
| 样式不统一 | 黑色背景+灰色按钮，与APP蓝绿渐变风格不搭 |

---

## 2. 解决方案

### 2.1 时间同步统一方案

```
┌─────────────────────────────────────────────────────────────┐
│                     NtpTimeService (Singleton)              │
│  - sharedCurrentTimeMillis: Long                            │
│  - sharedLastSyncTime: Long                                 │
│  - getCurrentTime(): Long  ← 返回NTP时间                    │
│  - getTimeOffset(): Long  ← 返回与系统时间的偏差            │
└─────────────────────────────────────────────────────────────┘
                    ↑                    ↑
                    │                    │
        ┌───────────┴───────────┐        │
        │                       │        │
   HomeViewModel          FloatingService
        │                       │
        ↓                       ↓
   TopStatusBar           悬浮时钟显示
   (首页顶部)              (悬浮窗)
```

**实现要点**：
- `NtpTimeService` 保持 `@Singleton` 注入
- `HomeViewModel.startTimeUpdates()` 改用 `ntpTimeService.getCurrentTime()`
- 状态栏和悬浮时钟显示完全同步的时间

### 2.2 偏差显示位置

| 位置 | 显示内容 | 格式 |
|------|----------|------|
| 顶部状态栏 | NTP时间 + 毫秒 + 偏差 | `14:25:30.5 +15ms` |
| 悬浮菜单 | 时钟区域显示状态 | 运行中/已停止 + 偏差 |

### 2.3 悬浮菜单重新设计

**布局结构**：
```
┌─────────────────────┐
│  [状态指示条]        │  ← 绿/红/灰 根据任务状态
├─────────────────────┤
│  ⏰ 时钟      [ON]   │  ← NTP状态 + 偏差
│  📍 循环      [●]    │  ← 运行指示点
│  🎁 礼物      [○]    │  
│  ▶️ 定时      [○]    │
├─────────────────────┤
│  [停止]  [关闭]      │
└─────────────────────┘
```

**设计改进**：
1. **顶部状态指示条**：绿色=运行中，灰色=空闲，红色=异常
2. **按钮文字标签**：每个按钮下方添加文字说明
3. **运行状态点**：圆形指示点显示各任务状态
4. **统一风格**：蓝绿渐变按钮背景，与APP主题一致

---

## 3. 技术实现

### 3.1 修改文件清单

| 文件 | 操作 | 改动内容 |
|------|------|----------|
| `HomeViewModel.kt` | 修改 | 时间更新改用ntpTimeService |
| `TopStatusBar.kt` | 修改 | 增加偏差显示参数 |
| `HomeScreen.kt` | 修改 | 传递偏差值到状态栏 |
| `FloatingService.kt` | 修改 | 无改动（已使用NTP） |
| `floating_menu.xml` | 修改 | 重新设计布局 |
| `FloatingMenuService.kt` | 修改 | 添加状态指示和文字标签 |

### 3.2 关键接口变更

**TopStatusBar 新增参数**：
```kotlin
@Composable
fun TopStatusBar(
    ntpTime: String,          // HH:mm:ss 格式
    millis: String,           // 毫秒部分
    ntpOffset: String = "",   // 新增：偏差显示 "+15ms"
    nextClickCountdown: String = ""
)
```

**HomeViewModel 时间更新**：
```kotlin
// 改为使用NTP时间
fun startTimeUpdates() {
    viewModelScope.launch {
        while (true) {
            val time = ntpTimeService.getCurrentTime()  // 改用NTP
            val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            _ntpTime.value = sdf.format(Date(time))
            _millis.value = String.format("%03d", time % 1000)
            delay(10)
        }
    }
}
```

---

## 4. 验收标准

### 4.1 时间同步验收

- [ ] 首页顶部状态栏显示的时间与悬浮时钟显示的时间完全一致（误差<10ms）
- [ ] NTP同步成功后，偏差值正确显示在状态栏（如 "+15ms" 或 "-8ms"）
- [ ] 未进行NTP同步时，显示 "--ms" 占位符

### 4.2 悬浮菜单验收

- [ ] 顶部有状态指示条，颜色根据任务状态变化
- [ ] 每个功能按钮下方有文字标签说明功能
- [ ] 按钮使用蓝绿渐变背景，与APP主题统一
- [ ] 任务运行时，对应按钮有运行状态点（绿色圆点）

### 4.3 功能验收

- [ ] 点击首页"时间同步"按钮后，偏差值正确更新
- [ ] 悬浮菜单点击时钟按钮后，时间偏差正确显示
- [ ] 切换Tab或返回首页，时间显示仍然同步
- [ ] 所有现有功能不受影响

---

## 5. 后续计划

实现分两个阶段：
1. **阶段一**：修复时间同步问题 + 偏差显示
2. **阶段二**：优化悬浮菜单布局和样式