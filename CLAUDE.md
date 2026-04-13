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
com.jdhelper/
├── app/                    # Main application module
│   ├── service/            # Android Services (FloatingService, AccessibilityClickService)
│   ├── data/               # Data layer (Room DB, Repository implementation)
│   │   ├── local/          # Room entities and DAOs
│   │   └── repository/     # Repository implementations
│   ├── domain/             # Domain layer (Repository interfaces)
│   ├── di/                 # Hilt dependency injection modules
│   └── ui/                 # Jetpack Compose UI layer
│       ├── screens/        # Screen composables and ViewModels
│       ├── navigation/     # Navigation setup
│       ├── components/     # Reusable UI components
│       └── theme/          # Material 3 theming
├── receiver/               # BroadcastReceivers
└── service/                # Core automation logic
```

**Key Services:**
- `FloatingService` - 悬浮窗时钟，支持拖拽定位，毫秒级精度显示
- `PositionFloatingService` - 定位专用悬浮窗
- `FloatingMenuService` - 悬浮菜单服务
- `AccessibilityClickService` - 无障碍服务模拟点击，支持按钮查找、手势操作
- `NtpTimeService` - 阿里云NTP时间同步
- `JdTimeService` - 京东时间同步服务
- `TimedClickManager` - 定时点击管理器

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