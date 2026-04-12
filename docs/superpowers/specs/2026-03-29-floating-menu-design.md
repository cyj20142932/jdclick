# 左侧悬浮菜单设计文档

## 概述

在应用中新增一个左侧悬浮菜单，包含三个图标按钮（播放、停止、关闭），支持拖拽定位。菜单可以独立显示和隐藏。

## 功能需求

1. **左侧悬浮菜单** - 显示在屏幕左侧的垂直排列的三个图标按钮
2. **三个按钮**:
   - 播放按钮 (▶) - 启动任务
   - 停止按钮 (⏹) - 关闭任务
   - 关闭按钮 (✕) - 隐藏整个悬浮菜单
3. **拖拽功能** - 用户可以拖拽整个菜单到屏幕任意位置
4. **主界面控制** - 主界面可以通过按钮显示/隐藏该悬浮菜单
5. **不实现功能** - 按钮点击只做UI反馈，暂不实现实际启动/停止任务

## 技术设计

### 1. 新增服务

创建 `FloatingMenuService.kt`：

```kotlin
@AndroidEntryPoint
class FloatingMenuService : Service() {
    companion object {
        const val ACTION_SHOW = "com.jdhelper.SHOW_MENU"
        const val ACTION_HIDE = "com.jdhelper.HIDE_MENU"

        fun startService(context: Context)
        fun stopService(context: Context)
    }
}
```

### 2. 布局设计

`res/layout/floating_menu.xml`:

```xml
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:background="@drawable/floating_background"
    android:padding="8dp">

    <ImageButton
        android:id="@+id/btn_play"
        android:src="@android:drawable/ic_media_play"
        android:background="?attr/selectableItemBackgroundBorderless" />

    <ImageButton
        android:id="@+id/btn_stop"
        android:src="@android:drawable/ic_media_pause"
        android:background="?attr/selectableItemBackgroundBorderless" />

    <ImageButton
        android:id="@+id/btn_close"
        android:src="@android:drawable/ic_menu_close_clear_cancel"
        android:background="?attr/selectableItemBackgroundBorderless" />
</LinearLayout>
```

### 3. WindowManager 配置

- `TYPE_APPLICATION_OVERLAY` (API 26+) / `TYPE_PHONE` (旧版本)
- `FLAG_NOT_FOCUSABLE` - 不获取焦点
- 初始位置：屏幕左侧垂直居中

### 4. 拖拽实现

与现有 `FloatingService` 相同的触摸事件处理：
- `ACTION_DOWN` - 记录初始位置
- `ACTION_MOVE` - 更新位置
- `ACTION_UP` - 完成拖拽

### 5. 主界面控制

在 `HomeScreen` 添加控制按钮，通过 `FloatingMenuService` 控制菜单显示/隐藏。

## 文件变更

| 文件 | 操作 |
|------|------|
| `service/FloatingMenuService.kt` | 新增 |
| `res/layout/floating_menu.xml` | 新增 |
| `ui/screens/home/HomeScreen.kt` | 修改 - 添加控制按钮 |
| `AndroidManifest.xml` | 修改 - 注册服务 |

## 依赖

无新增依赖，使用现有项目技术栈。