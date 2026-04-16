# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

JDHelper (京东助手) is an Android automation app featuring floating clock with millisecond precision, scheduled/interval clicking via AccessibilityService, and NTP time synchronization with Alibaba Cloud.

## Build Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK (auto-increments version)
./gradlew assembleRelease -PincrementVersion=true

# Clean build
./gradlew clean

# Run all tests
./gradlew test

# Run a single test class
./gradlew test --tests "com.jdhelper.app.data.local.ClickSettingsDaoTest"

# Run tests with coverage report
./gradlew testDebugUnitTest

# Lint check
./gradlew lint

# Build with info logs (for debugging build issues)
./gradlew assembleDebug --info
```

## Architecture

This project follows **Clean Architecture** with MVVM pattern:

```
com.jdhelper.app/
├── service/                 # Android Services
│   ├── FloatingService.kt           # 悬浮窗时钟，毫秒级精度显示
│   ├── PositionFloatingService.kt   # 定位专用悬浮窗
│   ├── FloatingMenuService.kt       # 悬浮菜单服务
│   ├── AccessibilityClickService.kt # 无障碍服务模拟点击
│   ├── NtpTimeService.kt            # 阿里云NTP时间同步
│   ├── JdTimeService.kt             # 京东时间同步服务
│   ├── TimedClickManager.kt         # 定时点击管理器
│   ├── ButtonFinder.kt              # 按钮查找器
│   ├── TimeService.kt               # 时间服务接口
│   ├── DefaultTimeService.kt        # 默认时间服务实现
│   ├── FloatingStateManager.kt      # 悬浮窗状态管理
│   ├── LogConsole.kt                # 日志工具
│   └── ToastUtils.kt                # Toast工具
├── data/
│   ├── local/               # Room数据库
│   │   ├── AutoClickerDatabase.kt   # 数据库主类
│   │   ├── ClickSettings.kt         # 点击配置实体
│   │   ├── ClickSettingsDao.kt      # 点击配置DAO
│   │   ├── GiftClickHistory.kt      # 礼品点击历史
│   │   ├── GiftClickHistoryDao.kt   # 历史记录DAO
│   │   ├── LogEntry.kt              # 日志实体
│   │   └── LogDao.kt                # 日志DAO
│   └── repository/          # Repository实现
│       ├── ClickSettingsRepositoryImpl.kt
│       └── LogRepositoryImpl.kt
├── domain/
│   └── repository/          # Repository接口
│       ├── ClickSettingsRepository.kt
│       └── LogRepository.kt
├── di/                      # Hilt依赖注入
│   ├── DatabaseModule.kt
│   └── ServiceModule.kt
├── ui/                      # Jetpack Compose UI
│   ├── MainActivity.kt
│   ├── navigation/
│   │   └── NavHost.kt
│   ├── components/
│   │   ├── StatusCard.kt
│   │   └── TopStatusBar.kt
│   ├── screens/
│   │   ├── home/            # 首页
│   │   ├── time/            # 时间同步页面
│   │   ├── history/         # 历史记录页面
│   │   ├── log/             # 日志页面
│   │   └── settings/        # 设置页面
│   └── theme/               # Material 3主题
├── receiver/
│   └── BootReceiver.kt      # 开机自启接收器
└── JDHelperApp.kt           # Application类
```

**Key Services:**
- `FloatingService` - 悬浮窗时钟，支持拖拽定位，毫秒级精度显示
- `PositionFloatingService` - 定位专用悬浮窗
- `FloatingMenuService` - 悬浮菜单服务（可折叠）
- `AccessibilityClickService` - 无障碍服务模拟点击，支持按钮查找、手势操作
- `NtpTimeService` - 阿里云NTP时间同步
- `JdTimeService` - 京东时间同步服务
- `TimedClickManager` - 定时点击管理器
- `ButtonFinder` - 按钮查找器，自动定位目标按钮
- `TimeService` - 时间服务接口
- `DefaultTimeService` - 默认时间服务实现（含延迟补偿）
- `FloatingStateManager` - 悬浮窗状态管理器
- `LogConsole` - 日志工具类

**Data Flow:**
UI → ViewModel → Repository → Room DAO → Database

**Key Data Models:**
- `ClickSettings` - 点击配置（时间间隔、位置等）
- `GiftClickHistory` - 礼品点击历史记录

## Tech Stack

- Kotlin 1.9.22
- Jetpack Compose + Material 3
- Hilt for DI
- Room for local persistence
- Coroutines + Flow for async
- Min SDK 24 / Target SDK 34

## Critical Files

- `app/build.gradle.kts` - Dependencies, build config, version management
- `app/src/main/AndroidManifest.xml` - Permissions and service declarations
- `app/src/main/java/com/jdhelper/app/service/FloatingService.kt` - Core floating window logic
- `app/src/main/java/com/jdhelper/app/service/AccessibilityClickService.kt` - Click automation via accessibility
- `app/src/main/java/com/jdhelper/app/service/TimedClickManager.kt` - Scheduled click logic

## Required Permissions

- `SYSTEM_ALERT_WINDOW` - 悬浮窗权限
- `BIND_ACCESSIBILITY_SERVICE` - 无障碍服务权限
- `FOREGROUND_SERVICE` - 前台服务权限
- `INTERNET` / `ACCESS_NETWORK_STATE` - NTP时间同步
- `RECEIVE_BOOT_COMPLETED` - 开机自启
- `POST_NOTIFICATIONS` - 通知权限 (Android 13+)