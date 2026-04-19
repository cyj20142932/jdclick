# App端TCP客户端设计文档

**版本**: 1.0
**日期**: 2026-04-17
**状态**: 草稿
**子设计编号**: 4/6

---

## 1. 概述

本文档描述 Android 端 TCP 客户端模块的设计方案。该模块负责与 PC 服务器建立和管理长连接，处理重连机制、连接控制等功能。

---

## 2. 技术架构

| 组件 | 技术选型 | 理由 |
|------|----------|------|
| 网络通信 | 原始 Socket | 与 PC 端协议一致 |
| 连接方式 | 长连接 | 保持 TCP 连接，消息复用 |
| 重连机制 | 指数退避 | 首次快速，后续逐渐延长 |
| 连接控制 | 开关 + 自动连接 | 用户可控，启东时自动 |

---

## 3. 核心类设计

### 3.1 LanClientOptions (配置类)

```kotlin
data class LanClientOptions(
    val serverAddress: String,      // PC 服务器 IP 地址
    val serverPort: Int = 8765,     // TCP 端口
    val autoConnect: Boolean = true, // 启动时自动连接
    val heartbeatInterval: Long = 30_000L, // 心跳间隔(ms)
    val reconnectMaxInterval: Long = 30_000L, // 最大重连间隔
    val reconnectBaseInterval: Long = 1000L  // 初始重连间隔
)
```

### 3.2 ConnectionState (连接状态)

```kotlin
enum class ConnectionState {
    DISCONNECTED,  // 未连接
    CONNECTING,    // 连接中
    CONNECTED,     // 已连接
    RECONNECTING   // 重连中
}
```

### 3.3 LanControlClient (主客户端类)

```kotlin
class LanControlClient(
    private val options: LanClientOptions,
    private val commandHandler: LanCommandHandler
) : Closeable {

    private var socket: Socket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private var connectionState = ConnectionState.DISCONNECTED

    // 连接到 PC 服务器
    suspend fun connect(): Result<Unit>

    // 断开连接
    suspend fun disconnect()

    // 发送消息
    suspend fun sendMessage(message: JsonObject): Result<Unit>

    // 启动心跳
    suspend fun startHeartbeat()

    // 启动重连机制
    suspend fun startReconnect()

    // 获取连接状态
    fun getConnectionState(): ConnectionState

    // 设置连接监听器
    fun setConnectionListener(listener: ConnectionListener)
}

interface ConnectionListener {
    fun onConnected()
    fun onDisconnected(reason: String)
    fun onConnectionError(error: String)
}
```

### 3.4 重连策略 (指数退避)

```kotlin
class ReconnectManager(
    private val baseInterval: Long = 1000L,
    private val maxInterval: Long = 30_000L,
    private val multiplier: Double = 2.0
) {
    private var currentInterval = baseInterval

    fun getNextInterval(): Long {
        val interval = currentInterval
        currentInterval = (currentInterval * multiplier).toLong().coerceAtMost(maxInterval)
        return interval
    }

    fun reset() {
        currentInterval = baseInterval
    }
}
```

---

## 4. 消息格式

### 4.1 JSON 消息结构

```json
{
  "type": "消息类型",
  "timestamp": 1234567890,
  "payload": { }
}
```

### 4.2 发送消息流程

```
1. 构造 JSON 消息
2. 添加换行符作为消息分隔符 (\n)
3. 写入输出流
4. 刷新缓冲区
```

### 4.3 接收消息流程

```
1. 从输入流读取一行
2. 解析 JSON
3. 根据 type 分发给对应 Handler
4. 处理完成后发送响应
```

---

## 5. 连接控制

### 5.1 开关按钮

在设置页面或悬浮菜单中添加"连接 PC"开关：

| 位置 | 功能 |
|------|------|
| 设置页面 | "连接 PC" 开关 ToggleButton |
| 悬浮菜单 | 显示连接状态，点击可开关 |

### 5.2 自动连接

- App 启动时检测 `auto_lan_connect` 配置项
- 若为 true，自动尝试连接 PC 服务器
- 连接成功后记录连接状态

### 5.3 连接状态显示

| 状态 | 显示 |
|------|------|
| 未连接 | 悬浮窗显示"未连接PC" |
| 连接中 | 悬浮窗显示"连接中..." |
| 已连接 | 悬浮窗显示"已连接PC" |
| 重连中 | 悬浮窗显示"重连中..." |

---

## 6. App 端配置项

| 配置项 | Key | 默认值 | 说明 |
|--------|-----|--------|------|
| PC 服务器地址 | `pc_server_address` | - | PC 的 IP 地址 |
| PC 服务器端口 | `pc_server_port` | 8765 | TCP 端口 |
| 设备名称 | `device_name` | 设备型号 | 设备显示名称 |
| 自动连接 | `auto_lan_connect` | true | 启动时自动连接 |

---

## 7. 错误处理

| 错误场景 | 处理方式 |
|----------|----------|
| 连接失败 | 触发重连机制 |
| 发送失败 | 关闭连接，触发重连 |
| 接收异常 | 记录日志，触发重连 |
| JSON 解析失败 | 忽略该消息，记录日志 |

---

## 8. 待定事项

- [ ] TLS 加密连接（可选）
- [ ] 证书校验（可选）