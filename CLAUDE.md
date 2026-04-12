# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

JDHelper (京东助手) is an Android automation app featuring floating clock with millisecond precision, scheduled/interval clicking via AccessibilityService, and NTP time synchronization with Alibaba Cloud.

## Build Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Clean build
./gradlew clean

# Run tests
./gradlew test

# Build with info logs
./gradlew assembleDebug --info
```

## Architecture

This project follows **Clean Architecture** with MVVM pattern:

```
com.jdhelper/
├── service/           # Android Services (FloatingService, AccessibilityClickService, NtpTimeService)
├── data/              # Data layer (Room DB, Repository implementation)
├── domain/            # Domain layer (Entities, UseCases, Repository interfaces)
├── di/                # Hilt dependency injection modules
├── ui/                # Jetpack Compose UI layer
│   ├── screens/       # Screen composables (home, task, settings)
│   ├── navigation/    # Navigation setup
│   └── theme/         # Material 3 theming
└── receiver/          # BroadcastReceivers
```

**Key Services:**
- `FloatingService` -悬浮窗时钟，拖拽定位点击位置
- `AccessibilityClickService` - 无障碍服务模拟点击
- `NtpTimeService` - 阿里云NTP时间同步

**Data Flow:**
UI → ViewModel → UseCase → Repository → Room DAO → Database

## Tech Stack

- Kotlin 1.9.22
- Jetpack Compose + Material 3
- Hilt for DI
- Room for local persistence
- Coroutines + Flow for async
- Min SDK 24 / Target SDK 34

## Critical Files

- `app/build.gradle.kts` - Dependencies and build config
- `app/src/main/AndroidManifest.xml` - Permissions and service declarations
- `service/FloatingService.kt` - Core floating window logic
- `service/AccessibilityClickService.kt` - Click automation via accessibility