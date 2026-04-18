# 删除NTP时间源设计文档

## 背景

为了精简app代码维护成本，决定删除NTP时间源，保留京东时间源作为唯一时间源。

## 设计方案

### 1. 删除的文件
- `app/src/main/java/com/jdhelper/app/service/NtpTimeService.kt`

### 2. 修改的文件

#### 2.1 ServiceModule.kt
- 删除 `provideNtpTimeService` 方法
- 删除 `NtpTimeService` 的导入

#### 2.2 ClickSettings.kt
- 默认值从 `TimeSource.NTP` 改为 `TimeSource.JD`

#### 2.3 DefaultTimeService.kt
- 删除 `NtpTimeService` 依赖注入
- 删除 `NTP` 分支的所有逻辑
- 默认值从 `TimeSource.NTP` 改为 `TimeSource.JD`

#### 2.4 FloatingService.kt
- 删除 `NtpTimeService` 成员变量
- 删除 NTP 相关初始化代码
- 默认值从 `TimeSource.NTP` 改为 `TimeSource.JD`

#### 2.5 FloatingMenuService.kt
- 删除 NTP 相关代码
- 默认值从 `TimeSource.NTP` 改为 `TimeSource.JD`

#### 2.6 HomeScreen.kt
- 删除 NTP 选项的UI（"阿里云NTP时间"按钮）
- 只保留京东时间源选项

#### 2.7 TopStatusBar.kt
- 删除 `TimeSource.NTP` 的显示分支
- 默认参数改为 `TimeSource.JD`

#### 2.8 HomeViewModel.kt
- 删除 `NtpTimeService` 依赖注入
- 默认值改为 `TimeSource.JD`

#### 2.9 SettingsViewModel.kt
- 删除 `NtpTimeService` 依赖注入
- 删除 `getNtpServers()` 方法

#### 2.10 ClickSettingsRepositoryImpl.kt
- 默认值改为 `TimeSource.JD`

#### 2.11 TimeViewModel.kt
- 默认值改为 `TimeSource.JD`

#### 2.12 GiftClickHistory.kt
- 默认值从 "NTP" 改为 "JD"

### 2.13 build.gradle.kts
- 删除 `commons-net:commons-net:3.11.1` 依赖

## 影响范围
- 悬浮窗时钟：不再显示NTP相关状态
- 时间同步页面：移除NTP相关功能
- 首页：移除时间源切换UI
- 设置页面：移除NTP服务器选择

## 测试要点
1. 启动app后默认使用京东时间源
2. 悬浮窗显示正确的时间
3. 时间同步功能正常工作
4. 旧数据中NTP相关状态能正确显示（兼容历史记录）