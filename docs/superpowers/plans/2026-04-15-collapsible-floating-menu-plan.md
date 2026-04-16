# 可收缩展开的悬浮菜单实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现可收缩/展开的悬浮菜单，点击图标展开完整菜单，点击收起按钮收缩为小图标

**Architecture:** 使用两个布局文件（展开/收缩）动态切换，配合属性动画实现缩放效果

**Tech Stack:** Android View, ValueAnimator, WindowManager

---

## 文件结构

```
修改文件:
- app/src/main/res/layout/floating_menu.xml         # 添加收起按钮
- app/src/main/res/layout/floating_menu_collapsed.xml # 新建：收缩状态布局
- app/src/main/java/com/jdhelper/app/service/FloatingMenuService.kt # 添加展开/收起逻辑

新增文件:
- app/src/main/res/layout/floating_menu_collapsed.xml
```

---

### Task 1: 创建收缩状态布局文件

**Files:**
- Create: `app/src/main/res/layout/floating_menu_collapsed.xml`
- Test: N/A (布局文件，编译验证)

- [ ] **Step 1: 创建收缩状态布局文件**

```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/collapsed_container"
    android:layout_width="56dp"
    android:layout_height="56dp">

    <!-- 圆形背景按钮 -->
    <ImageButton
        android:id="@+id/btn_expand"
        android:layout_width="48dp"
        android:layout_height="48dp"
        android:layout_gravity="center"
        android:src="@android:drawable/ic_menu_sort_by_size"
        android:background="@drawable/circle_button_bg"
        android:scaleType="centerInside"
        android:padding="10dp"
        android:contentDescription="展开菜单" />

    <!-- 任务运行指示点（默认隐藏） -->
    <View
        android:id="@+id/indicator_task_running"
        android:layout_width="10dp"
        android:layout_height="10dp"
        android:layout_gravity="top|end"
        android:background="@drawable/status_dot_green"
        android:visibility="gone" />

</FrameLayout>
```

- [ ] **Step 2: 创建圆形按钮背景 drawable**

```xml
<!-- app/src/main/res/drawable/circle_button_bg.xml -->
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="oval">
    <solid android:color="#CC333333" />
    <stroke
        android:width="1dp"
        android:color="#555555" />
    <size
        android:width="48dp"
        android:height="48dp" />
</shape>
```

- [ ] **Step 3: 提交变更**

```bash
git add app/src/main/res/layout/floating_menu_collapsed.xml app/src/main/res/drawable/circle_button_bg.xml
git commit -m "feat: 创建收缩状态布局文件"
```

---

### Task 2: 修改展开状态布局添加收起按钮

**Files:**
- Modify: `app/src/main/res/layout/floating_menu.xml`
- Test: N/A

- [ ] **Step 1: 在现有菜单顶部添加收起按钮**

在 `floating_menu.xml` 的最外层 LinearLayout 中，添加一个收起按钮：

```xml
<!-- 在 status_indicator 之前添加收起按钮 -->
<ImageButton
    android:id="@+id/btn_collapse"
    android:layout_width="24dp"
    android:layout_height="24dp"
    android:layout_gravity="end"
    android:src="@android:drawable/ic_menu_close_clear_cancel"
    android:background="@android:color/transparent"
    android:scaleType="centerInside"
    android:padding="2dp"
    android:contentDescription="收起菜单" />
```

- [ ] **Step 2: 提交变更**

```bash
git add app/src/main/res/layout/floating_menu.xml
git commit -m "feat: 在展开菜单中添加收起按钮"
```

---

### Task 3: 修改 FloatingMenuService 实现展开/收起逻辑

**Files:**
- Modify: `app/src/main/java/com/jdhelper/app/service/FloatingMenuService.kt`
- Test: 编译并运行应用测试

- [ ] **Step 1: 添加状态变量**

在 FloatingMenuService 类中添加：

```kotlin
// 展开/收起状态
private var isExpanded = true  // 默认展开
private var collapsedView: View? = null
private var expandedView: View? = null

// 位置记忆
private var collapsedX = 0
private var collapsedY = 0
```

- [ ] **Step 2: 修改 showFloatingMenu() 方法**

将原方法拆分为两个方法：

```kotlin
private fun showFloatingMenu() {
    // 默认显示展开状态
    showExpandedMenu()
}

private fun showExpandedMenu() {
    // 原有逻辑，添加收起按钮点击事件
    // ... (原有代码)

    // 添加收起按钮点击事件
    floatingView?.findViewById<ImageButton>(R.id.btn_collapse)?.setOnClickListener {
        collapseMenu()
    }
}

private fun showCollapsedMenu() {
    // 创建收缩状态布局
    val layoutInflater = LayoutInflater.from(this)
    collapsedView = layoutInflater.inflate(R.layout.floating_menu_collapsed, null)

    // 设置点击展开事件
    collapsedView?.findViewById<ImageButton>(R.id.btn_expand)?.setOnClickListener {
        expandMenu()
    }

    // 添加拖拽功能
    setupDragListener(collapsedView)

    // 添加到窗口
    val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.START or Gravity.CENTER_VERTICAL
        x = collapsedX
        y = collapsedY
    }

    windowManager.addView(collapsedView, params)
}
```

- [ ] **Step 3: 添加展开/收起方法**

```kotlin
private fun collapseMenu() {
    expandedView?.let { view ->
        // 记录当前位置
        val params = windowManager.getLayoutParams(view) as WindowManager.LayoutParams
        collapsedX = params.x
        collapsedY = params.y

        // 移除展开视图
        windowManager.removeView(view)
        expandedView = null

        // 显示收缩视图
        showCollapsedMenu()
    }
    isExpanded = false
}

private fun expandMenu() {
    collapsedView?.let { view ->
        // 记录当前位置
        val params = windowManager.getLayoutParams(view) as WindowManager.LayoutParams
        collapsedX = params.x
        collapsedY = params.y

        // 移除收缩视图
        windowManager.removeView(view)
        collapsedView = null

        // 显示展开视图
        showExpandedMenu()
    }
    isExpanded = true
}
```

- [ ] **Step 4: 编译验证**

```bash
./gradlew assembleDebug
```

预期：编译成功

- [ ] **Step 5: 提交变更**

```bash
git add app/src/main/java/com/jdhelper/app/service/FloatingMenuService.kt
git commit -m "feat: 实现悬浮菜单展开/收起功能"
```

---

### Task 4: 添加动画效果

**Files:**
- Modify: `app/src/main/java/com/jdhelper/app/service/FloatingMenuService.kt`
- Test: 编译并运行应用测试

- [ ] **Step 1: 添加动画工具方法**

```kotlin
/**
 * 收缩动画：从展开视图缩小并消失
 */
private fun animateCollapse(expandedView: View, onComplete: () -> Unit) {
    val params = windowManager.getLayoutParams(expandedView) as WindowManager.LayoutParams
    
    // 记录位置
    collapsedX = params.x
    collapsedY = params.y

    val animator = ValueAnimator.ofFloat(1f, 0f).apply {
        duration = 200
        addUpdateListener { animation ->
            val scale = animation.animatedValue as Float
            expandedView.scaleX = scale
            expandedView.scaleY = scale
            expandedView.alpha = scale
        }
    }
    
    animator.doOnEnd {
        windowManager.removeView(expandedView)
        onComplete()
    }
    
    animator.start()
}

/**
 * 展开动画：从收缩视图放大并显示
 */
private fun animateExpand(collapsedView: View, onComplete: () -> Unit) {
    collapsedView.scaleX = 0f
    collapsedView.scaleY = 0f
    collapsedView.alpha = 0f
    
    val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 200
        addUpdateListener { animation ->
            val scale = animation.animatedValue as Float
            collapsedView.scaleX = scale
            collapsedView.scaleY = scale
            collapsedView.alpha = scale
        }
    }
    
    animator.doOnEnd {
        onComplete()
    }
    
    animator.start()
}
```

- [ ] **Step 2: 修改展开/收起方法使用动画**

```kotlin
private fun collapseMenu() {
    expandedView?.let { view ->
        animateCollapse(view) {
            showCollapsedMenu()
        }
    }
    isExpanded = false
}

private fun expandMenu() {
    collapsedView?.let { view ->
        // 先显示出来（透明状态）
        view.visibility = View.VISIBLE
        animateExpand(view) {
            // 动画完成后移除收缩视图，显示展开视图
            windowManager.removeView(view)
            showExpandedMenu()
        }
    }
    isExpanded = true
}
```

- [ ] **Step 3: 编译验证**

```bash
./gradlew assembleDebug
```

预期：编译成功

- [ ] **Step 4: 提交变更**

```bash
git add app/src/main/java/com/jdhelper/app/service/FloatingMenuService.kt
git commit -m "feat: 添加展开/收起动画效果"
```

---

### Task 5: 测试与验证

**Files:**
- Test: 手动测试
- Test: `./gradlew assembleDebug`

- [ ] **Step 1: 构建 Debug APK**

```bash
./gradlew assembleDebug
```

预期：编译成功，生成 APK

- [ ] **Step 2: 手动测试流程**

1. 启动应用，显示悬浮菜单（应为展开状态）
2. 点击"收起"按钮，菜单收缩为小图标
3. 点击小图标，菜单展开为完整菜单
4. 测试拖拽功能在两种状态下正常工作
5. 测试任务运行时指示点是否正确显示

- [ ] **Step 3: 提交最终变更**

```bash
git add -A
git commit -m "feat: 完成可收缩展开的悬浮菜单功能"
```

---

## 验收标准

- [ ] 点击收起按钮，菜单收缩为圆形小图标
- [ ] 点击小图标，菜单展开为完整菜单
- [ ] 两种状态下都可以拖拽移动
- [ ] 展开/收起有平滑的动画效果
- [ ] 任务运行时，收缩状态显示绿色指示点
- [ ] 编译成功，无错误