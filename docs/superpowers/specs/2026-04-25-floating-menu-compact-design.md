# 悬浮菜单紧凑布局设计

日期: 2026-04-25

## 概述

将悬浮菜单从当前 2x2 网格 + 状态栏 + 底部操作栏布局（~170dp × ~124dp）重构为单列纵向紧凑布局（~180dp × ~48dp），大幅缩减水平空间占用，适合贴边放置。

## 设计要求

1. **纵向排列** — 单列纵向，适合贴在屏幕左右边缘
2. **紧凑尺寸** — 宽度控制在 50dp 以内
3. **纯图标操作** — 移除所有文字标签，按钮通过图标自解释
4. **状态指示** — 顶部颜色条 + 按钮角标点，无需文字状态栏

## 布局结构

```
┌─────────────────┐
│ ████████████████ │  ← 顶部状态条 2dp (绿/灰/红)
│                   │
│  [🔄]  ⬤  ←角标  │  ← 时钟按钮 36dp
│  [▢]             │  ← 循环按钮 36dp
│  [♥]             │  ← 礼物按钮 36dp
│  [▶]             │  ← 定时按钮 36dp
│                   │
│  ──── 分隔线 ──── │  ← 24dp宽, 1dp高
│                   │
│  [■]             │  ← 停止按钮 30dp
│  [✕]             │  ← 关闭按钮 30dp
│  [☰]             │  ← 拖拽按钮 30dp
└─────────────────┘
```

**总尺寸**: ~48dp 宽 × ~180dp 高

## 按钮定义

| ID | 图标 | 尺寸 | 功能 |
|----|------|------|------|
| `btn_clock` | 圆形 | 36dp | 同步时间 + 启动悬浮时钟 |
| `btn_lock` | 方框 | 36dp | 循环点击模式开关 |
| `btn_gift` | 心形 | 36dp | 礼物整分点击任务 |
| `btn_play` | 三角 | 36dp | 定时点击任务 |
| `btn_stop` | 红色方块 | 30dp | 停止所有任务 |
| `btn_close` | X | 30dp | 停止 + 关闭菜单 |
| `btn_drag` | 三条横线 | 30dp | 拖拽移动菜单 |

## 状态指示系统

### 顶部状态条 (2dp)
- **绿色** — 时间已同步 / 有任务运行中
- **灰色** — 未同步 / 空闲状态
- **红色** — 同步失败

### 按钮角标 (4dp 圆点)
- **绿色小点** — 该任务运行中（显示）
- **隐藏** — 该任务未运行（GONE）

## 组件尺寸

```
外容器 padding: 5dp
顶部状态条: 2dp 高, 与外容器同宽
功能按钮容器: 36dp × 36dp, 圆角 7dp
功能按钮图标: 16dp × 16dp (内边距 ~10dp)
操作按钮容器: 30dp × 30dp, 圆角 6dp
操作按钮图标: ~12dp × 12dp
角标点: 4dp 直径, 距右上角 3dp
按钮间距: 2dp
分隔线: 24dp 宽 × 1dp 高, 颜色 #444444
```

## 实现文件

### floating_menu.xml
- 改为 `LinearLayout` 纵向排列
- 移除 `text_clock_status`、`text_ntp_offset` 文本视图
- 移除两条分隔线（只保留功能区/操作区间一条细线）
- GridLayout 改为纵向按钮排列
- 按钮尺寸缩小
- 底部操作按钮合并到主列

### FloatingMenuService.kt
- 移除 `textClockStatus`、`textNtpOffset` 相关引用
- `statusIndicator` 保留，行为不变（颜色更新）
- 移除 `updateNtpStatusDisplay()` 中文本更新逻辑
- 保留所有按钮点击事件逻辑不变
- 保留 `updateTaskIndicator()` 角标显示逻辑
- 移除 `indicator_clock/loop/gift/timed` 引用需更新为对应新的按钮 ID（ID 保持不变）

### 需要移除的引用
- `R.id.text_clock_status`
- `R.id.text_ntp_offset`

### 需要保留的引用
- 所有 `btn_*` ID（`btn_clock`, `btn_lock`, `btn_gift`, `btn_play`, `btn_stop`, `btn_close`, `btn_drag`）
- `status_indicator`
- `indicator_*`（`indicator_clock`, `indicator_loop`, `indicator_gift`, `indicator_timed`）

## 行为

- 所有点击事件逻辑与当前实现完全一致
- 拖拽通过 btn_drag 和整个菜单区域实现
- 状态条颜色由 `updateOverallStatus()` 和 `updateNtpStatusDisplay()` 共同控制
- 角标由 `updateTaskIndicator()` 控制
