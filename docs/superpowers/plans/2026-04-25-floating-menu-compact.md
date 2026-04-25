# 悬浮菜单紧凑布局实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将悬浮菜单从 2x2 网格布局重构为单列纵向紧凑布局，缩减屏幕占用面积

**Architecture:** 仅修改 XML 布局文件和对应的 Kotlin 视图引用代码，按钮点击事件逻辑和行为保持不变。布局从 GridLayout + 文本状态栏 + 底部操作栏改为纯图标单列排列，状态指示通过顶部颜色条和按钮角标实现。

**Tech Stack:** Android View XML (LinearLayout, ImageButton), Kotlin, WindowManager

---

### Task 1: 重写 floating_menu.xml 为单列布局

**Files:**
- Modify: `app/src/main/res/layout/floating_menu.xml` (全文重写)

- [ ] **Step 1: 将布局重写为单列纵向结构**

将当前布局（状态栏+2x2网格+分割线+底部操作栏）替换为单列布局：

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/menu_container"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:background="@drawable/floating_background"
    android:padding="5dp">

    <!-- 顶部状态指示条 -->
    <View
        android:id="@+id/status_indicator"
        android:layout_width="match_parent"
        android:layout_height="2dp"
        android:background="@drawable/status_indicator_green"
        android:layout_marginBottom="3dp" />

    <!-- 功能按钮区: 4个纵向排列 -->
    <!-- 时钟 -->
    <FrameLayout
        android:layout_width="36dp"
        android:layout_height="36dp"
        android:layout_marginBottom="2dp">

        <ImageButton
            android:id="@+id/btn_clock"
            android:layout_width="36dp"
            android:layout_height="36dp"
            android:src="@android:drawable/ic_menu_recent_history"
            android:background="@drawable/button_gradient_bg"
            android:scaleType="centerInside"
            android:padding="10dp"
            android:contentDescription="时钟" />

        <View
            android:id="@+id/indicator_clock"
            android:layout_width="4dp"
            android:layout_height="4dp"
            android:layout_gravity="top|end"
            android:layout_marginTop="4dp"
            android:layout_marginEnd="4dp"
            android:background="@drawable/status_dot_green"
            android:visibility="gone" />
    </FrameLayout>

    <!-- 循环 -->
    <FrameLayout
        android:layout_width="36dp"
        android:layout_height="36dp"
        android:layout_marginBottom="2dp">

        <ImageButton
            android:id="@+id/btn_lock"
            android:layout_width="36dp"
            android:layout_height="36dp"
            android:src="@android:drawable/ic_menu_manage"
            android:background="@drawable/button_gradient_bg"
            android:scaleType="centerInside"
            android:padding="10dp"
            android:contentDescription="循环" />

        <View
            android:id="@+id/indicator_loop"
            android:layout_width="4dp"
            android:layout_height="4dp"
            android:layout_gravity="top|end"
            android:layout_marginTop="4dp"
            android:layout_marginEnd="4dp"
            android:background="@drawable/status_dot_green"
            android:visibility="gone" />
    </FrameLayout>

    <!-- 礼物 -->
    <FrameLayout
        android:layout_width="36dp"
        android:layout_height="36dp"
        android:layout_marginBottom="2dp">

        <ImageButton
            android:id="@+id/btn_gift"
            android:layout_width="36dp"
            android:layout_height="36dp"
            android:src="@android:drawable/ic_menu_send"
            android:background="@drawable/button_gradient_bg"
            android:scaleType="centerInside"
            android:padding="10dp"
            android:contentDescription="礼物" />

        <View
            android:id="@+id/indicator_gift"
            android:layout_width="4dp"
            android:layout_height="4dp"
            android:layout_gravity="top|end"
            android:layout_marginTop="4dp"
            android:layout_marginEnd="4dp"
            android:background="@drawable/status_dot_green"
            android:visibility="gone" />
    </FrameLayout>

    <!-- 定时 -->
    <FrameLayout
        android:layout_width="36dp"
        android:layout_height="36dp"
        android:layout_marginBottom="2dp">

        <ImageButton
            android:id="@+id/btn_play"
            android:layout_width="36dp"
            android:layout_height="36dp"
            android:src="@android:drawable/ic_media_play"
            android:background="@drawable/button_gradient_bg"
            android:scaleType="centerInside"
            android:padding="10dp"
            android:contentDescription="定时" />

        <View
            android:id="@+id/indicator_timed"
            android:layout_width="4dp"
            android:layout_height="4dp"
            android:layout_gravity="top|end"
            android:layout_marginTop="4dp"
            android:layout_marginEnd="4dp"
            android:background="@drawable/status_dot_green"
            android:visibility="gone" />
    </FrameLayout>

    <!-- 分隔线 -->
    <View
        android:layout_width="24dp"
        android:layout_height="1dp"
        android:background="#444444"
        android:layout_marginVertical="4dp"
        android:layout_gravity="center_horizontal" />

    <!-- 操作按钮区 -->
    <!-- 停止 -->
    <ImageButton
        android:id="@+id/btn_stop"
        android:layout_width="30dp"
        android:layout_height="30dp"
        android:layout_marginBottom="2dp"
        android:layout_gravity="center_horizontal"
        android:src="@android:drawable/ic_media_pause"
        android:background="@drawable/button_gradient_bg"
        android:scaleType="centerInside"
        android:padding="7dp"
        android:contentDescription="停止" />

    <!-- 关闭 -->
    <ImageButton
        android:id="@+id/btn_close"
        android:layout_width="30dp"
        android:layout_height="30dp"
        android:layout_marginBottom="2dp"
        android:layout_gravity="center_horizontal"
        android:src="@android:drawable/ic_menu_close_clear_cancel"
        android:background="@drawable/button_gradient_bg"
        android:scaleType="centerInside"
        android:padding="7dp"
        android:contentDescription="关闭" />

    <!-- 拖拽 -->
    <ImageButton
        android:id="@+id/btn_drag"
        android:layout_width="30dp"
        android:layout_height="30dp"
        android:layout_gravity="center_horizontal"
        android:src="@android:drawable/ic_menu_more"
        android:background="@drawable/button_gradient_bg"
        android:scaleType="centerInside"
        android:padding="7dp"
        android:contentDescription="拖拽" />

</LinearLayout>
```

关键变更:
- 移除 `text_clock_status` 和 `text_ntp_offset` 两个 TextView
- 移除中间的分割线（只保留功能区/操作区间一条细分隔线）
- GridLayout 2x2 + 外层 LinearLayout 改为纯 LinearLayout 单列
- 按钮尺寸: 主按钮36dp, 操作按钮30dp, 角标4dp
- 按钮包装方式: 从 `LinearLayout(竖向,含FrameLayout+TextView)` 改为纯 `FrameLayout`
- 移除按钮下方的文字标签
- 角标位置: `layout_marginTop="4dp" layout_marginEnd="4dp"` 确保在右上角

- [ ] **Step 2: 验证 XML 格式正确**

运行 `./gradlew lint` 确保没有 XML 错误。

- [ ] **Step 3: 提交**

```bash
git add app/src/main/res/layout/floating_menu.xml
git commit -m "refactor: redesign floating menu to single-column compact layout"
```

### Task 2: 清理 FloatingMenuService.kt 中的文本视图引用

**Files:**
- Modify: `app/src/main/java/com/jdhelper/app/service/FloatingMenuService.kt`

- [ ] **Step 1: 移除字段声明**

移除以下不再需要的字段声明（约第140-143行）:

```kotlin
    // 移除: 文本视图引用
    private var textClockStatus: TextView? = null
    private var textNtpOffset: TextView? = null
```

同时移除 TextView 导入:
```kotlin
    // 移除此行
    import android.widget.TextView
```

- [ ] **Step 2: 简化 updateNtpStatusDisplay() 方法**

将 `updateNtpStatusDisplay()`（约第246-264行）替换为只更新状态条颜色的版本:

```kotlin
    /**
     * 更新状态指示条颜色
     */
    private fun updateNtpStatusDisplay() {
        statusIndicator?.setBackgroundColor(
            if (timeService.isSynced()) Color.parseColor("#4CAF50")
            else Color.parseColor("#888888")
        )
    }
```

- [ ] **Step 3: 移除 findViewById 中对文本视图的查找**

在 `showFloatingMenu()` 中找到约第420-421行:
```kotlin
    textClockStatus = floatingView?.findViewById(R.id.text_clock_status)
    textNtpOffset = floatingView?.findViewById(R.id.text_ntp_offset)
```
移除这两行。

- [ ] **Step 4: 提交**

```bash
git add app/src/main/java/com/jdhelper/app/service/FloatingMenuService.kt
git commit -m "refactor: remove text status views from FloatingMenuService"
```

### Task 3: 验证构建

- [ ] **Step 1: 构建检查**

运行:
```bash
./gradlew assembleDebug
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: 检查新布局效果**

确认以下要点:
- 按钮点击事件均能正常触发（btn_clock/btn_lock/btn_gift/btn_play/btn_stop/btn_close/btn_drag）
- 顶部状态条颜色变化正常
- 角标显示/隐藏正常
- 拖拽功能正常
- 所有按钮图标正确显示

- [ ] **Step 3: 提交最终验证**

```bash
git add -A
git commit -m "build: verify compact layout build"
```
