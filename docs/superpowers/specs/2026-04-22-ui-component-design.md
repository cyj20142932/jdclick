# UI组件工程化设计规范

**项目**: JDHelper Android App
**日期**: 2026-04-22
**版本**: 1.0

---

## 1. 设计系统（Design Tokens）

### 1.1 颜色系统

#### 语义化颜色

```kotlin
// 语义颜色 - 在Theme.kt中定义
object JdColors {
    // 主色调
    val Primary = Color(0xFF00B4DB)        // 蓝绿渐变起点
    val PrimaryEnd = Color(0xFF0083B0)    // 蓝绿渐变终点
    val OnPrimary = Color(0xFFFFFFFF)

    // 次色调
    val Secondary = Color(0xFF0083B0)
    val OnSecondary = Color(0xFFFFFFFF)

    // 功能色
    val Success = Color(0xFF4CAF50)      // 成功/开启状态
    val Warning = Color(0xFFFF9800)     // 警告
    val Error = Color(0xFFF44336)       // 错误/关闭状态
    val Info = Color(0xFF2196F3)        // 信息

    // 中性色 - 浅色主题
    val BackgroundLight = Color(0xFFF5F5F7)
    val SurfaceLight = Color(0xFFFFFFFF)
    val OnSurfaceLight = Color(0xFF212121)
    val OnSurfaceVariantLight = Color(0xFF757575)

    // 中性色 - 深色主题
    val BackgroundDark = Color(0xFF1A1A2E)
    val SurfaceDark = Color(0xFF252540)
    val SurfaceVariantDark = Color(0xFF2D2D4A)
    val OnSurfaceDark = Color(0xFFFFFFFF)
    val OnSurfaceVariantDark = Color(0xFFCAC4D0)
}
```

#### 渐变定义

```kotlin
object JdGradients {
    val Primary = listOf(Color(0xFF00B4DB), Color(0xFF0083B0))
    val Surface = listOf(Color(0xFF252540), Color(0xFF1A1A2E))
}
```

### 1.2 尺寸系统

```kotlin
object JdSpacing {
    val xxs = 2.dp   // 极小间距
    val xs = 4.dp   // 较小间距
    val sm = 8.dp   // 小间距
    val md = 12.dp  // 中间距
    val lg = 16.dp  // 大间距
    val xl = 24.dp  // 较大间距
    val xxl = 32.dp // 大间距
    val xxxl = 48.dp // 极大间距
}

object JdRadius {
    val none = 0.dp
    val small = 8.dp      // 小圆角（按钮、小卡片）
    val medium = 12.dp   // 中圆角（卡片、输入框）
    val large = 16.dp    // 大圆角（对话框、大卡片）
    val extraLarge = 24.dp // 特大圆角（底部弹窗）
    val full = 9999.dp    // 圆形（头像、图标按钮）
}

object JdElevation {
    val level0 = 0.dp    // 无阴影
    val level1 = 2.dp   // 轻微
    val level2 = 4.dp   // 中等
    val level3 = 8.dp   // 较强
    val level4 = 16.dp  // 强
}
```

### 1.3 动画系统

```kotlin
object JdAnimation {
    val durationFast = 150ms   // 快速（点击反馈）
    val durationNormal = 300ms // 正常（页面过渡）
    val durationSlow = 500ms   // 慢（加载、状态切换）

    val easingFast = FastOutSlowIn
    val easingBounce = FastOutSlowIn
    val easingNormal = FastOutSlowIn
}
```

---

## 2. 组件库

### 2.1 JDButton - 统一按钮

**变体**:
- `Primary` - 主按钮（渐变背景）
- `Secondary` - 次按钮（边框+背景）
- `Text` - 文字按钮（无背景）
- `Ghost` - 幽灵按钮（透明背景）

**状态**: default / pressed / disabled / loading

**参数**:
```kotlin
@Composable
fun JDButton(
    text: String,
    onClick: () -> Unit,
    variant: ButtonVariant = ButtonVariant.Primary,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier
)
```

**实现要点**:
- 点击缩放动画: scale 1.0 → 0.96 → 1.0 (150ms)
- 加载状态显示圆形进度指示器
- 禁用状态降低透明度至50%

### 2.2 JDCard - 统一卡片

**变体**:
- `Elevated` - 带阴影（默认）
- `Outlined` - 带边框
- `Filled` - 纯色填充

**参数**:
```kotlin
@Composable
fun JDCard(
    modifier: Modifier = Modifier,
    variant: CardVariant = CardVariant.Elevated,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
)
```

**实现要点**:
- 统一圆角: 16dp
- 点击反馈: 缩放 + 波纹
- 阴影使用elevation实现

### 2.3 JDListItem - 列表项

**参数**:
```kotlin
@Composable
fun JDListItem(
    title: String,
    subtitle: String? = null,
    leadingIcon: ImageVector? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
)
```

**实现要点**:
- 图标使用渐变圆形背景
- 右侧可放置Switch/箭头/文字
- 点击波纹效果

### 2.4 JDEmptyState - 空状态

**参数**:
```kotlin
@Composable
fun JDEmptyState(
    icon: ImageVector,
    title: String,
    description: String? = null,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
)
```

### 2.5 JDLoadingIndicator - 加载指示器

**变体**:
- `Circular` - 圆形（默认）
- `Dots` - 三个点动画
- `Shimmer` - 骨架屏闪烁

**参数**:
```kotlin
@Composable
fun JDLoadingIndicator(
    variant: LoadingVariant = LoadingVariant.Circular,
    modifier: Modifier = Modifier
)
```

### 2.6 JDDialog - 统一对话框

**参数**:
```kotlin
@Composable
fun JDDialog(
    title: String,
    text: String? = null,
    confirmText: String = "确定",
    dismissText: String = "取消",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit = {},
    icon: ImageVector? = null
)
```

### 2.7 JDBottomSheet - 底部弹窗

**参数**:
```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JDBottomSheet(
    title: String? = null,
    onDismiss: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(),
    content: @Composable ColumnScope.() -> Unit
)
```

---

## 3. 交互规范

### 3.1 页面过渡动画

```kotlin
// NavHost.kt 中配置
val enterTransition = fadeIn(animationSpec = tween(300)) +
    slideInHorizontally(initialOffsetX = { it / 4 }, animationSpec = tween(300))
val exitTransition = fadeOut(animationSpec = tween(300))
val popEnterTransition = fadeIn(animationSpec = tween(300))
val popExitTransition = fadeOut(animationSpec = tween(300)) +
    slideOutHorizontally(targetOffsetX = { it / 4 }, animationSpec = tween(300))
```

### 3.2 组件点击反馈

| 组件 | 反馈效果 |
|------|----------|
| Button | 缩放 0.96 + 颜色加深 |
| Card | 缩放 0.98 + 波纹 |
| ListItem | 波纹 + 背景色变化 |
| FAB | 缩放 0.9 + 波纹 |

### 3.3 状态切换动画

```kotlin
// 列表项展开/收起
val expandAnimation = animateContentSize(
    animationSpec = tween(300)
)

// 开关切换
val switchTransition = AnimatedVisibility(
    enter = fadeIn() + scaleIn(),
    exit = fadeOut() + scaleOut()
)
```

---

## 4. 文件结构

```
ui/
├── theme/
│   ├── Theme.kt          # 主题配置 + 语义颜色
│   ├── Typography.kt    # 字体规范
│   ├── Tokens.kt        # Design Tokens (新增)
│   └── Animation.kt     # 动画常量 (新增)
├── components/
│   ├── StatusCard.kt    # 已有
│   ├── TopStatusBar.kt  # 已有
│   ├── JDButton.kt      # 新增
│   ├── JDCard.kt       # 新增
│   ├── JDListItem.kt   # 新增
│   ├── JDEmptyState.kt # 新增
│   ├── JDLoadingIndicator.kt # 新增
│   ├── JDDialog.kt     # 新增
│   └── JDBottomSheet.kt # 新增
├── animation/
│   └── Animations.kt    # 通用动画扩展 (新增)
└── screens/
    └── [各屏幕使用新组件重构]
```

---

## 5. 实现优先级

### Phase 1: 设计系统
1. 创建 `Tokens.kt` - 定义所有设计令牌
2. 扩展 `Theme.kt` - 添加语义颜色支持浅色主题

### Phase 2: 基础组件
3. `JDButton` - 按钮是最高频组件
4. `JDCard` - 卡片次高频
5. `JDListItem` - 列表项

### Phase 3: 辅助组件
6. `JDEmptyState` - 空状态展示
7. `JDLoadingIndicator` - 加载状态
8. `JDDialog` / `JDBottomSheet` - 对话框

### Phase 4: 页面重构
9. 重构 HomeScreen 使用新组件
10. 重构其他 Screen 使用新组件

---

## 6. 验收标准

- [ ] 深色/浅色/跟随系统 三种模式正常工作
- [ ] 所有组件点击反馈动画流畅
- [ ] 页面过渡动画一致
- [ ] 组件参数符合设计规范
- [ ] 编译无错误无警告