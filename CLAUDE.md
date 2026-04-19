# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

JDHelper (京东助手) is an Android automation app featuring floating clock with millisecond precision, scheduled/interval clicking via AccessibilityService, and JD time synchronization.

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

This project follows **Clean Architecture** with MVVM pattern, organized into these key layers:

```
com.jdhelper.app/
├── service/           # Android Services (核心业务逻辑)
├── data/              # Data layer (Room + Repository)
├── domain/            # Domain layer (Repository interfaces)
├── di/                # Hilt dependency injection
├── ui/                # Jetpack Compose UI (screens, components, theme)
└── receiver/          # Broadcast receivers (boot, etc.)
```

**核心服务** (在 `service/` 目录下):
- `FloatingService` / `PositionFloatingService` - 悬浮窗时钟，毫秒级精度，支持拖拽
- `FloatingMenuService` - 悬浮菜单（可折叠）
- `AccessibilityClickService` - 无障碍服务模拟点击
- `JdTimeService` - 京东时间同步服务
- `TimedClickManager` - 定时点击管理器
- `ButtonFinder` - 按钮查找器
- `TimeService` 接口 + `DefaultTimeService` 实现 - 时间服务抽象

**数据流:** UI → ViewModel → Repository → Room DAO → Database

## Tech Stack

- Kotlin 1.9.22 + Jetpack Compose + Material 3
- Hilt (DI) + Room (Database) + Coroutines + Flow
- Min SDK 24 / Target SDK 34

## Version Management

- `app/version.txt` - versionCode (integer, auto-incremented on release)
- `app/version_name.txt` - versionName (semver, patch auto-incremented on release)
- Release build: `./gradlew assembleRelease -PincrementVersion=true`

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
- `INTERNET` / `ACCESS_NETWORK_STATE` - 京东时间同步
- `RECEIVE_BOOT_COMPLETED` - 开机自启
- `POST_NOTIFICATIONS` - 通知权限 (Android 13+)