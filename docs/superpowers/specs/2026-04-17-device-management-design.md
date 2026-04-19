# 设备管理机制设计文档

**版本**: 1.0
**日期**: 2026-04-17
**状态**: 草稿
**子设计编号**: 6/6

---

## 1. 概述

本文档描述设备管理机制的设计方案。该模块负责管理设备注册、心跳、状态转换、功能互斥等功能。

---

## 2. 设备状态

| 状态 | 描述 | UI 展示 |
|------|------|---------|
| ONLINE | 已连接，正常通信 | 绿色 |
| IDLE | 空闲，等待指令 | 灰色 |
| SEARCHING | 正在查找按钮 | 黄色 |
| READY | 按钮已找到，待执行 | 蓝色 |
| EXECUTING | 正在执行点击 | 橙色 |
| OFFLINE | 断开连接 | 红色 |

---

## 3. 状态转换图

```
                                    ┌──────────────┐
                                    │   SEARCHING  │
                                    │  (查找中)     │
                                    └──────┬───────┘
                                           │ 找到
                   ┌──────────────┐         │         ┌──────────────┐
                   │    IDLE      │◄────────┴────────►│    READY     │
                   │   (空闲)      │                   │  (已找到)     │
                   └──────┬───────┘                   └──────┬───────┘
                          │                                     │ 执行
            ┌─────────────┼─────────────┐                      │
            │             │             │                      ▼
            │         心跳超时       断开连接              ┌──────────────┐
            │             │             │                   │  EXECUTING   │
            ▼             ▼             ▼                   │  (执行中)    │
      ┌───────────┐ ┌───────────┐ ┌───────────┐            └──────┬───────┘
      │  OFFLINE  │ │  OFFLINE  │ │  OFFLINE  │                    │ 完成
      │  (离线)   │ │  (离线)   │ │  (离线)   │                    ▼
      └───────────┘ └───────────┘ └───────────┘            ┌──────────────┐
                                                           │    IDLE      │
                                                           └──────────────┘
```

---

## 4. 核心类设计

### 4.1 DeviceStatus (设备状态枚举)

```kotlin
enum class DeviceStatus {
    ONLINE,      // 已连接
    IDLE,        // 空闲
    SEARCHING,   // 查找中
    READY,       // 已找到
    EXECUTING,   // 执行中
    OFFLINE      // 离线
}
```

### 4.2 DeviceInfo (设备信息)

```kotlin
data class DeviceInfo(
    val deviceId: String,           // 设备唯一ID
    val deviceName: String,         // 设备名称
    val ipAddress: String,          // IP地址
    val appVersion: String,         // App版本
    val androidVersion: String,     // Android版本
    var status: DeviceStatus = DeviceStatus.OFFLINE,
    var lastHeartbeat: Long = 0,    // 最后心跳时间戳
    var connectedAt: Long = 0       // 连接时间戳
)
```

### 4.3 DeviceManager (设备管理器)

```kotlin
class DeviceManager {
    private val devices = ConcurrentHashMap<String, DeviceInfo>()

    // 注册设备
    fun registerDevice(info: DeviceInfo): Boolean

    // 移除设备
    fun removeDevice(deviceId: String): Boolean

    // 获取设备
    fun getDevice(deviceId: String): DeviceInfo?

    // 获取所有设备
    fun getAllDevices(): List<DeviceInfo>

    // 更新设备状态
    fun updateStatus(deviceId: String, status: DeviceStatus)

    // 更新心跳时间
    fun updateHeartbeat(deviceId: String)

    // 获取超时设备
    fun getTimedOutDevices(timeoutMs: Long): List<String>
}
```

### 4.4 DeviceEventBus (事件总线)

```kotlin
sealed class DeviceEvent {
    data class DeviceRegistered(val device: DeviceInfo) : DeviceEvent()
    data class DeviceStatusChanged(val deviceId: String, val status: DeviceStatus) : DeviceEvent()
    data class DeviceDisconnected(val deviceId: String, val reason: String) : DeviceEvent()
    data class DeviceTimedOut(val deviceId: String) : DeviceEvent()
}

class DeviceEventBus {
    private val _events = MutableSharedFlow<DeviceEvent>()
    val events: SharedFlow<DeviceEvent> = _events.asSharedFlow()

    fun publish(event: DeviceEvent) {
        _events.tryEmit(event)
    }
}
```

---

## 5. 心跳机制

### 5.1 心跳参数

| 参数 | 默认值 | 说明 |
|------|--------|------|
| 间隔 | 30秒 | App 发送心跳间隔 |
| 超时 | 90秒 | PC 端判定超时时间 |

### 5.2 心跳处理流程

```
App 端:
  每 30 秒发送 HEARTBEAT { status: "IDLE" }

PC 端:
  1. 收到心跳，更新最后心跳时间
  2. 启动监控任务，每 5 秒检查
  3. 超时 90 秒 → 标记 OFFLINE，触发重连
```

---

## 6. 功能互斥

### 6.1 互斥逻辑

| 状态 | 悬浮菜单定时点击 | PC 控制点击 |
|------|-----------------|-------------|
| 未连接 PC | ✅ 可用 | ❌ 不可用 |
| 已连接 PC | ❌ 禁用 | ✅ 可用 |

### 6.2 实现方式

```kotlin
class FeatureMutexManager(
    private val floatingMenuService: FloatingMenuService,
    private val lanControlClient: LanControlClient
) {
    fun onPcConnected() {
        // 禁用本地定时点击
        floatingMenuService.disableTimedClick()
    }

    fun onPcDisconnected() {
        // 恢复本地定时点击
        floatingMenuService.enableTimedClick()
    }
}
```

### 6.3 UI 状态显示

| 状态 | 悬浮窗显示 |
|------|------------|
| 未连接 | 显示本地定时点击功能 |
| 已连接 PC | 显示"已连接PC"，禁用本地定时点击 |

---

## 7. 待定事项

- [ ] 重连时状态恢复
- [ ] 多设备批量操作