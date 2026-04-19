# PC端TCP服务器设计文档

**版本**: 1.0
**日期**: 2026-04-17
**状态**: 草稿
**子设计编号**: 1/6

---

## 1. 概述

本文档描述 PC 局域网控制端 TCP 服务器模块的设计方案。该模块负责管理多个 Android 设备的 TCP 连接，处理设备注册、心跳、消息收发等核心通信功能。

---

## 2. 技术架构

### 2.1 技术选型

| 组件 | 技术选型 | 理由 |
|------|----------|------|
| 编程语言 | C# (.NET 9) | 用户指定 |
| 网络通信 | .NET Socket (TcpListener) | 原生支持，高性能 |
| 并发模型 | 异步IO (async/await) | 资源占用少，现代C#推荐 |
| 消息分发 | 策略模式 + 字典 | 根据type字段查表分发，可扩展 |
| 心跳检测 | 延迟任务调度 | Task.Delay + 独立监控任务 |

### 2.2 消息类型支持

| 消息类型 | 方向 | Handler |
|----------|------|---------|
| REGISTER | App→PC | RegisterHandler |
| HEARTBEAT | App→PC | HeartbeatHandler |
| SEARCH_RESULT | App→PC | SearchResultHandler |
| EXECUTE_RESULT | App→PC | ExecuteResultHandler |
| GIFT_RESULT | App→PC | GiftResultHandler |
| DISCONNECT | App→PC | DisconnectHandler |

---

## 3. 核心类设计

### 3.1 TcpServerOptions (配置类)

```csharp
public class TcpServerOptions
{
    public int Port { get; set; } = 8765;           // 监听端口
    public int HeartbeatInterval { get; set; } = 30;  // 心跳间隔(秒)
    public int HeartbeatTimeout { get; set; } = 90;   // 心跳超时(秒)
    public int MaxConnections { get; set; } = 50;     // 最大连接数
    public string NtpServer { get; set; } = "ntp.aliyun.com"; // NTP服务器
}
```

### 3.2 DeviceConnection (设备连接)

```csharp
public class DeviceConnection
{
    public string DeviceId { get; set; }          // 设备唯一ID
    public string DeviceName { get; set; }        // 设备名称
    public string IpAddress { get; set; }         // IP地址
    public string AppVersion { get; set; }        // App版本
    public string AndroidVersion { get; set; }    // Android版本
    public DeviceStatus Status { get; set; }      // 连接状态
    public DateTime LastHeartbeat { get; set; }   // 最后心跳时间
    public DateTime ConnectedAt { get; set; }     // 连接时间
    public TcpClient Client { get; set; }         // TCP客户端
    public NetworkStream Stream { get; set; }     // 网络流
}

public enum DeviceStatus
{
    ONLINE,
    IDLE,
    SEARCHING,
    READY,
    EXECUTING,
    OFFLINE
}
```

### 3.3 TcpServer (主服务器类)

```csharp
public class TcpServer : IAsyncDisposable
{
    private readonly TcpServerOptions _options;
    private readonly DeviceManager _deviceManager;
    private readonly MessageDispatcher _dispatcher;
    private TcpListener? _listener;
    private CancellationTokenSource? _cts;

    // 启动服务器
    public Task StartAsync(CancellationToken ct);

    // 停止服务器
    public Task StopAsync();

    // 广播消息到所有设备
    public Task BroadcastAsync<T>(T message) where T : class;

    // 发送到指定设备
    public Task SendToDeviceAsync<T>(string deviceId, T message) where T : class;
}
```

### 3.4 DeviceManager (设备管理器)

```csharp
public class DeviceManager
{
    private readonly ConcurrentDictionary<string, DeviceConnection> _devices;

    // 添加设备
    public bool AddDevice(DeviceConnection device);

    // 移除设备
    public bool RemoveDevice(string deviceId);

    // 获取设备
    public DeviceConnection? GetDevice(string deviceId);

    // 获取所有设备
    public IEnumerable<DeviceConnection> GetAllDevices();

    // 更新设备状态
    public void UpdateDeviceStatus(string deviceId, DeviceStatus status);

    // 更新心跳时间
    public void UpdateHeartbeat(string deviceId);

    // 获取超时设备列表
    public IEnumerable<string> GetTimedOutDevices(TimeSpan timeout);
}
```

### 3.5 MessageDispatcher (消息分发器)

```csharp
public class MessageDispatcher
{
    private readonly Dictionary<string, IMessageHandler> _handlers;

    // 注册处理器
    public void RegisterHandler(string messageType, IMessageHandler handler);

    // 分发消息
    public async Task HandleAsync(DeviceConnection device, JsonElement payload);
}

// 消息处理器接口
public interface IMessageHandler
{
    Task HandleAsync(DeviceConnection device, JsonElement payload);
}
```

---

## 4. 心跳超时检测机制

### 4.1 流程设计

```
1. 设备连接时，启动心跳监控任务
2. 每次收到HEARTBEAT，更新最后心跳时间（DeviceManager.UpdateHeartbeat）
3. 启动独立监控任务，定期检查所有设备：
   - 当前时间 - 最后心跳时间 > 超时阈值(90秒)
   - 超时则标记为OFFLINE，从设备列表移除
4. 设备断开后，取消其监控任务
```

### 4.2 实现代码框架

```csharp
public class HeartbeatMonitor
{
    private readonly DeviceManager _deviceManager;
    private readonly TimeSpan _timeout;
    private CancellationTokenSource? _cts;

    public Task StartAsync(CancellationToken ct)
    {
        _cts = CancellationTokenSource.CreateLinkedTokenSource(ct);
        return MonitorLoopAsync(_cts.Token);
    }

    private async Task MonitorLoopAsync(CancellationToken ct)
    {
        while (!ct.IsCancellationRequested)
        {
            await Task.Delay(5000, ct); // 每5秒检查一次

            var timedOut = _deviceManager.GetTimedOutDevices(_timeout);
            foreach (var deviceId in timedOut)
            {
                // 标记离线，触发重连逻辑
                await OnDeviceTimeoutAsync(deviceId);
            }
        }
    }
}
```

---

## 5. 错误处理

| 错误场景 | 处理方式 |
|----------|----------|
| JSON解析失败 | 返回错误响应 `{success: false, message: "JSON解析失败"}`，记录日志 |
| 设备ID无效 | 返回错误响应，关闭连接 |
| 设备未注册就发消息 | 返回错误响应，忽略消息 |
| 发送失败 | 标记连接异常，触发重连流程 |
| 端口被占用 | 抛出异常，提示用户更换端口 |

---

## 6. 对外接口

### 6.1 事件通知

```csharp
public class TcpServerEvents
{
    // 设备注册成功
    public event EventHandler<DeviceConnection>? DeviceRegistered;

    // 设备断开连接
    public event EventHandler<string>? DeviceDisconnected;

    // 设备心跳
    public event EventHandler<DeviceHeartbeatEventArgs>? DeviceHeartbeat;

    // 收到消息
    public event EventHandler<MessageReceivedEventArgs>? MessageReceived;

    // 设备超时
    public event EventHandler<string>? DeviceTimedOut;
}
```

---

## 7. 待定事项

- [ ] 安全验证机制（可选）
- [ ] 连接加密（TLS）