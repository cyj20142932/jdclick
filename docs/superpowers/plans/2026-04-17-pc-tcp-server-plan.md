# PC端TCP服务器实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现PC端TCP服务器，管理多个Android设备的TCP连接，处理设备注册、心跳、消息收发

**Architecture:** 使用异步IO (async/await) + 策略模式消息分发 + 延迟任务调度心跳检测

**Tech Stack:** C# (.NET 9), System.Net.Sockets, System.Text.Json

---

## 文件结构

```
PC/
├── src/
│   └── JdHelperControl/
│       ├── JdHelperControl.csproj
│       ├── Program.cs
│       ├── Options/
│       │   └── TcpServerOptions.cs
│       ├── Models/
│       │   ├── DeviceConnection.cs
│       │   ├── DeviceStatus.cs
│       │   └── Messages/
│       │       ├── MessageBase.cs
│       │       ├── RegisterMessage.cs
│       │       └── ...
│       ├── Services/
│       │   ├── TcpServer.cs
│       │   ├── DeviceManager.cs
│       │   ├── MessageDispatcher.cs
│       │   └── HeartbeatMonitor.cs
│       └── Handlers/
│           ├── IMessageHandler.cs
│           ├── RegisterHandler.cs
│           ├── HeartbeatHandler.cs
│           ├── SearchResultHandler.cs
│           ├── ExecuteResultHandler.cs
│           ├── GiftResultHandler.cs
│           └── DisconnectHandler.cs
```

---

## 实现步骤

### Task 1: 创建项目结构和基础模型

**Files:**
- Create: `PC/src/JdHelperControl/JdHelperControl.csproj`
- Create: `PC/src/JdHelperControl/Program.cs`
- Create: `PC/src/JdHelperControl/Options/TcpServerOptions.cs`
- Create: `PC/src/JdHelperControl/Models/DeviceStatus.cs`

- [ ] **Step 1: 创建项目文件和基础模型**

```xml
<!-- PC/src/JdHelperControl/JdHelperControl.csproj -->
<Project Sdk="Microsoft.NET.Sdk">

  <PropertyGroup>
    <OutputType>Exe</OutputType>
    <TargetFramework>net9.0</TargetFramework>
    <ImplicitUsings>enable</ImplicitUsings>
    <Nullable>enable</Nullable>
  </PropertyGroup>

</Project>
```

```csharp
// PC/src/JdHelperControl/Options/TcpServerOptions.cs
namespace JdHelperControl.Options;

public class TcpServerOptions
{
    public int Port { get; set; } = 8765;
    public int HeartbeatInterval { get; set; } = 30;
    public int HeartbeatTimeout { get; set; } = 90;
    public int MaxConnections { get; set; } = 50;
}
```

```csharp
// PC/src/JdHelperControl/Models/DeviceStatus.cs
namespace JdHelperControl.Models;

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

- [ ] **Step 2: 创建 DeviceConnection 模型**

```csharp
// PC/src/JdHelperControl/Models/DeviceConnection.cs
using System.Net.Sockets;

namespace JdHelperControl.Models;

public class DeviceConnection
{
    public string DeviceId { get; set; } = string.Empty;
    public string DeviceName { get; set; } = string.Empty;
    public string IpAddress { get; set; } = string.Empty;
    public string AppVersion { get; set; } = string.Empty;
    public string AndroidVersion { get; set; } = string.Empty;
    public DeviceStatus Status { get; set; } = DeviceStatus.OFFLINE;
    public DateTime LastHeartbeat { get; set; }
    public DateTime ConnectedAt { get; set; }
    public TcpClient? Client { get; set; }
    public NetworkStream? Stream { get; set; }
}
```

- [ ] **Step 3: 创建消息基类和注册消息**

```csharp
// PC/src/JdHelperControl/Models/Messages/MessageBase.cs
using System.Text.Json.Serialization;

namespace JdHelperControl.Models.Messages;

public class MessageBase
{
    [JsonPropertyName("type")]
    public string Type { get; set; } = string.Empty;

    [JsonPropertyName("timestamp")]
    public long Timestamp { get; set; }

    [JsonPropertyName("payload")]
    public JsonElement Payload { get; set; }
}
```

```csharp
// PC/src/JdHelperControl/Models/Messages/RegisterMessage.cs
using System.Text.Json.Serialization;

namespace JdHelperControl.Models.Messages;

public class RegisterPayload
{
    [JsonPropertyName("deviceName")]
    public string DeviceName { get; set; } = string.Empty;

    [JsonPropertyName("deviceId")]
    public string DeviceId { get; set; } = string.Empty;

    [JsonPropertyName("appVersion")]
    public string AppVersion { get; set; } = string.Empty;

    [JsonPropertyName("androidVersion")]
    public string AndroidVersion { get; set; } = string.Empty;
}
```

- [ ] **Step 4: 提交代码**

```bash
cd PC/src/JdHelperControl
git init
git add .
git commit -m "feat: 创建TCP服务器项目结构和基础模型"
```

---

### Task 2: 实现 DeviceManager

**Files:**
- Create: `PC/src/JdHelperControl/Services/DeviceManager.cs`
- Test: `PC/tests/JdHelperControl.Tests/DeviceManagerTests.cs`

- [ ] **Step 1: 编写 DeviceManager 单元测试**

```csharp
// PC/tests/JdHelperControl.Tests/DeviceManagerTests.cs
using JdHelperControl.Models;
using JdHelperControl.Services;

namespace JdHelperControl.Tests;

public class DeviceManagerTests
{
    private readonly DeviceManager _manager = new();

    [Fact]
    public void AddDevice_ShouldReturnTrue()
    {
        var device = new DeviceConnection
        {
            DeviceId = "test-001",
            DeviceName = "测试设备",
            Status = DeviceStatus.ONLINE
        };

        var result = _manager.AddDevice(device);
        
        Assert.True(result);
        Assert.NotNull(_manager.GetDevice("test-001"));
    }

    [Fact]
    public void AddDevice_DuplicateId_ShouldReturnFalse()
    {
        var device1 = new DeviceConnection { DeviceId = "test-001", DeviceName = "设备1" };
        var device2 = new DeviceConnection { DeviceId = "test-001", DeviceName = "设备2" };

        _manager.AddDevice(device1);
        var result = _manager.AddDevice(device2);

        Assert.False(result);
    }

    [Fact]
    public void RemoveDevice_ShouldReturnTrue()
    {
        var device = new DeviceConnection { DeviceId = "test-001" };
        _manager.AddDevice(device);

        var result = _manager.RemoveDevice("test-001");

        Assert.True(result);
        Assert.Null(_manager.GetDevice("test-001"));
    }

    [Fact]
    public void UpdateHeartbeat_ShouldUpdateTimestamp()
    {
        var device = new DeviceConnection { DeviceId = "test-001" };
        _manager.AddDevice(device);

        _manager.UpdateHeartbeat("test-001");

        var updatedDevice = _manager.GetDevice("test-001");
        Assert.True((DateTime.UtcNow - updatedDevice!.LastHeartbeat).TotalSeconds < 1);
    }

    [Fact]
    public void GetTimedOutDevices_ShouldReturnTimedOutDevices()
    {
        var device = new DeviceConnection
        {
            DeviceId = "test-001",
            LastHeartbeat = DateTime.UtcNow.AddSeconds(-100)
        };
        _manager.AddDevice(device);

        var timedOut = _manager.GetTimedOutDevices(TimeSpan.FromSeconds(90));

        Assert.Contains("test-001", timedOut);
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

```bash
cd PC
dotnet test --filter "DeviceManagerTests" -v
# Expected: FAIL (class not found)
```

- [ ] **Step 3: 实现 DeviceManager**

```csharp
// PC/src/JdHelperControl/Services/DeviceManager.cs
using System.Collections.Concurrent;
using JdHelperControl.Models;

namespace JdHelperControl.Services;

public class DeviceManager
{
    private readonly ConcurrentDictionary<string, DeviceConnection> _devices = new();

    public bool AddDevice(DeviceConnection device)
    {
        return _devices.TryAdd(device.DeviceId, device);
    }

    public bool RemoveDevice(string deviceId)
    {
        return _devices.TryRemove(deviceId, out _);
    }

    public DeviceConnection? GetDevice(string deviceId)
    {
        _devices.TryGetValue(deviceId, out var device);
        return device;
    }

    public IEnumerable<DeviceConnection> GetAllDevices()
    {
        return _devices.Values.ToList();
    }

    public void UpdateDeviceStatus(string deviceId, DeviceStatus status)
    {
        if (_devices.TryGetValue(deviceId, out var device))
        {
            device.Status = status;
        }
    }

    public void UpdateHeartbeat(string deviceId)
    {
        if (_devices.TryGetValue(deviceId, out var device))
        {
            device.LastHeartbeat = DateTime.UtcNow;
        }
    }

    public IEnumerable<string> GetTimedOutDevices(TimeSpan timeout)
    {
        var cutoff = DateTime.UtcNow - timeout;
        return _devices.Values
            .Where(d => d.LastHeartbeat != default && d.LastHeartbeat < cutoff)
            .Select(d => d.DeviceId)
            .ToList();
    }
}
```

- [ ] **Step 4: 运行测试验证通过**

```bash
cd PC
dotnet test --filter "DeviceManagerTests" -v
# Expected: PASS
```

- [ ] **Step 5: 提交代码**

```bash
cd PC
git add .
git commit -m "feat: 实现DeviceManager设备管理器"
```

---

### Task 3: 实现消息处理器接口和注册处理器

**Files:**
- Create: `PC/src/JdHelperControl/Handlers/IMessageHandler.cs`
- Create: `PC/src/JdHelperControl/Handlers/RegisterHandler.cs`
- Create: `PC/src/JdHelperControl/Handlers/HeartbeatHandler.cs`

- [ ] **Step 1: 创建消息处理器接口**

```csharp
// PC/src/JdHelperControl/Handlers/IMessageHandler.cs
using System.Text.Json;
using JdHelperControl.Models;

namespace JdHelperControl.Handlers;

public interface IMessageHandler
{
    Task HandleAsync(DeviceConnection device, JsonElement payload);
}
```

- [ ] **Step 2: 实现 RegisterHandler**

```csharp
// PC/src/JdHelperControl/Handlers/RegisterHandler.cs
using System.Text.Json;
using System.Text.Json.Serialization;
using JdHelperControl.Models;
using JdHelperControl.Services;

namespace JdHelperControl.Handlers;

public class RegisterHandler : IMessageHandler
{
    private readonly DeviceManager _deviceManager;

    public RegisterHandler(DeviceManager deviceManager)
    {
        _deviceManager = deviceManager;
    }

    public async Task HandleAsync(DeviceConnection device, JsonElement payload)
    {
        var deviceName = payload.GetProperty("deviceName").GetString() ?? "未知设备";
        var deviceId = payload.GetProperty("deviceId").GetString() ?? "";
        var appVersion = payload.GetProperty("appVersion").GetString() ?? "";
        var androidVersion = payload.GetProperty("androidVersion").GetString() ?? "";

        device.DeviceName = deviceName;
        device.DeviceId = deviceId;
        device.AppVersion = appVersion;
        device.AndroidVersion = androidVersion;
        device.Status = DeviceStatus.IDLE;
        device.ConnectedAt = DateTime.UtcNow;
        device.LastHeartbeat = DateTime.UtcNow;

        _deviceManager.AddDevice(device);

        var response = new
        {
            type = "REGISTER_ACK",
            timestamp = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds(),
            payload = new
            {
                success = true,
                serverTime = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds(),
                message = "注册成功"
            }
        };

        var json = JsonSerializer.Serialize(response);
        await device.Stream!.WriteAsync(System.Text.Encoding.UTF8.GetBytes(json + "\n"));
    }
}
```

- [ ] **Step 3: 实现 HeartbeatHandler**

```csharp
// PC/src/JdHelperControl/Handlers/HeartbeatHandler.cs
using System.Text.Json;
using JdHelperControl.Models;
using JdHelperControl.Services;

namespace JdHelperControl.Handlers;

public class HeartbeatHandler : IMessageHandler
{
    private readonly DeviceManager _deviceManager;

    public HeartbeatHandler(DeviceManager deviceManager)
    {
        _deviceManager = deviceManager;
    }

    public async Task HandleAsync(DeviceConnection device, JsonElement payload)
    {
        _deviceManager.UpdateHeartbeat(device.DeviceId);

        if (payload.TryGetProperty("status", out var statusElement))
        {
            var statusStr = statusElement.GetString();
            if (Enum.TryParse<DeviceStatus>(statusStr, out var status))
            {
                _deviceManager.UpdateDeviceStatus(device.DeviceId, status);
            }
        }

        var response = new
        {
            type = "HEARTBEAT_ACK",
            timestamp = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds(),
            payload = new { }
        };

        var json = JsonSerializer.Serialize(response);
        await device.Stream!.WriteAsync(System.Text.Encoding.UTF8.GetBytes(json + "\n"));
    }
}
```

- [ ] **Step 4: 提交代码**

```bash
cd PC
git add .
git commit -m "feat: 实现消息处理器接口和RegisterHandler、HeartbeatHandler"
```

---

### Task 4: 实现 MessageDispatcher

**Files:**
- Create: `PC/src/JdHelperControl/Services/MessageDispatcher.cs`

- [ ] **Step 1: 实现 MessageDispatcher**

```csharp
// PC/src/JdHelperControl/Services/MessageDispatcher.cs
using System.Text.Json;
using JdHelperControl.Handlers;
using JdHelperControl.Models;

namespace JdHelperControl.Services;

public class MessageDispatcher
{
    private readonly Dictionary<string, IMessageHandler> _handlers = new();
    private readonly DeviceManager _deviceManager;

    public MessageDispatcher(DeviceManager deviceManager)
    {
        _deviceManager = deviceManager;
        RegisterDefaultHandlers();
    }

    private void RegisterDefaultHandlers()
    {
        RegisterHandler("REGISTER", new RegisterHandler(_deviceManager));
        RegisterHandler("HEARTBEAT", new HeartbeatHandler(_deviceManager));
        // 其他handler将在后续task中添加
    }

    public void RegisterHandler(string messageType, IMessageHandler handler)
    {
        _handlers[messageType] = handler;
    }

    public async Task HandleAsync(DeviceConnection device, string messageJson)
    {
        try
        {
            using var document = JsonDocument.Parse(messageJson);
            var root = document.RootElement;

            var messageType = root.GetProperty("Type").GetString();
            var payload = root.GetProperty("Payload");

            if (messageType != null && _handlers.TryGetValue(messageType, out var handler))
            {
                await handler.HandleAsync(device, payload);
            }
            else
            {
                Console.WriteLine($"Unknown message type: {messageType}");
            }
        }
        catch (Exception ex)
        {
            Console.WriteLine($"Error handling message: {ex.Message}");
        }
    }
}
```

- [ ] **Step 2: 提交代码**

```bash
cd PC
git add .
git commit -m "feat: 实现MessageDispatcher消息分发器"
```

---

### Task 5: 实现心跳监控

**Files:**
- Create: `PC/src/JdHelperControl/Services/HeartbeatMonitor.cs`

- [ ] **Step 1: 实现 HeartbeatMonitor**

```csharp
// PC/src/JdHelperControl/Services/HeartbeatMonitor.cs
namespace JdHelperControl.Services;

public class HeartbeatMonitor
{
    private readonly DeviceManager _deviceManager;
    private readonly TimeSpan _timeout;
    private CancellationTokenSource? _cts;
    private Task? _monitorTask;

    public event EventHandler<string>? DeviceTimedOut;

    public HeartbeatMonitor(DeviceManager deviceManager, TimeSpan timeout)
    {
        _deviceManager = deviceManager;
        _timeout = timeout;
    }

    public Task StartAsync(CancellationToken cancellationToken)
    {
        _cts = CancellationTokenSource.CreateLinkedTokenSource(cancellationToken);
        _monitorTask = MonitorLoopAsync(_cts.Token);
        return Task.CompletedTask;
    }

    private async Task MonitorLoopAsync(CancellationToken ct)
    {
        while (!ct.IsCancellationRequested)
        {
            try
            {
                await Task.Delay(5000, ct);

                var timedOutDevices = _deviceManager.GetTimedOutDevices(_timeout);
                foreach (var deviceId in timedOutDevices)
                {
                    _deviceManager.UpdateDeviceStatus(deviceId, Models.DeviceStatus.OFFLINE);
                    DeviceTimedOut?.Invoke(this, deviceId);
                }
            }
            catch (OperationCanceledException)
            {
                break;
            }
            catch (Exception ex)
            {
                Console.WriteLine($"Heartbeat monitor error: {ex.Message}");
            }
        }
    }

    public async Task StopAsync()
    {
        _cts?.Cancel();
        if (_monitorTask != null)
        {
            try
            {
                await _monitorTask;
            }
            catch (OperationCanceledException) { }
        }
    }
}
```

- [ ] **Step 2: 提交代码**

```bash
cd PC
git add .
git commit -m "feat: 实现HeartbeatMonitor心跳监控"
```

---

### Task 6: 实现核心 TcpServer

**Files:**
- Create: `PC/src/JdHelperControl/Services/TcpServer.cs`
- Modify: `PC/src/JdHelperControl/Program.cs`

- [ ] **Step 1: 实现 TcpServer**

```csharp
// PC/src/JdHelperControl/Services/TcpServer.cs
using System.Net;
using System.Net.Sockets;
using System.Text;
using JdHelperControl.Models;
using JdHelperControl.Options;

namespace JdHelperControl.Services;

public class TcpServer : IAsyncDisposable
{
    private readonly TcpServerOptions _options;
    private readonly DeviceManager _deviceManager;
    private readonly MessageDispatcher _dispatcher;
    private readonly HeartbeatMonitor _heartbeatMonitor;
    private TcpListener? _listener;
    private CancellationTokenSource? _cts;
    private readonly List<Task> _connectionTasks = new();

    public event EventHandler<DeviceConnection>? DeviceRegistered;
    public event EventHandler<string>? DeviceDisconnected;
    public event EventHandler<string>? DeviceTimedOut;

    public TcpServer(TcpServerOptions options)
    {
        _options = options;
        _deviceManager = new DeviceManager();
        _dispatcher = new MessageDispatcher(_deviceManager);
        _heartbeatMonitor = new HeartbeatMonitor(
            _deviceManager,
            TimeSpan.FromSeconds(options.HeartbeatTimeout));

        _heartbeatMonitor.DeviceTimedOut += (s, deviceId) => DeviceTimedOut?.Invoke(this, deviceId);
    }

    public async Task StartAsync(CancellationToken cancellationToken)
    {
        _cts = CancellationTokenSource.CreateLinkedTokenSource(cancellationToken);
        _listener = new TcpListener(IPAddress.Any, _options.Port);
        _listener.Start();

        Console.WriteLine($"TCP Server started on port {_options.Port}");

        await _heartbeatMonitor.StartAsync(_cts.Token);

        _ = AcceptConnectionsAsync(_cts.Token);
    }

    private async Task AcceptConnectionsAsync(CancellationToken ct)
    {
        while (!ct.IsCancellationRequested && _listener != null)
        {
            try
            {
                var client = await _listener.AcceptTcpClientAsync(ct);
                _ = HandleConnectionAsync(client, ct);
            }
            catch (OperationCanceledException)
            {
                break;
            }
            catch (Exception ex)
            {
                Console.WriteLine($"Accept error: {ex.Message}");
            }
        }
    }

    private async Task HandleConnectionAsync(TcpClient client, CancellationToken ct)
    {
        var stream = client.GetStream();
        var buffer = new byte[8192];
        var ipAddress = ((IPEndPoint)client.Client.RemoteEndPoint!).Address.ToString();

        DeviceConnection? device = null;

        try
        {
            while (!ct.IsCancellationRequested && client.Connected)
            {
                var bytesRead = await stream.ReadAsync(buffer, ct);
                if (bytesRead == 0) break;

                var message = Encoding.UTF8.GetString(buffer, 0, bytesRead);
                var messages = message.Split('\n', StringSplitOptions.RemoveEmptyEntries);

                foreach (var msg in messages)
                {
                    if (device == null)
                    {
                        device = new DeviceConnection
                        {
                            Client = client,
                            Stream = stream,
                            IpAddress = ipAddress
                        };
                    }

                    await _dispatcher.HandleAsync(device, msg);

                    if (device.DeviceId != string.Empty && device.Status == DeviceStatus.IDLE)
                    {
                        DeviceRegistered?.Invoke(this, device);
                    }
                }
            }
        }
        catch (Exception ex)
        {
            Console.WriteLine($"Connection error: {ex.Message}");
        }
        finally
        {
            if (device != null && device.DeviceId != string.Empty)
            {
                _deviceManager.RemoveDevice(device.DeviceId);
                DeviceDisconnected?.Invoke(this, device.DeviceId);
            }
            client.Close();
        }
    }

    public async Task BroadcastAsync<T>(T message) where T : class
    {
        var json = JsonSerializer.Serialize(message) + "\n";
        var bytes = Encoding.UTF8.GetBytes(json);

        foreach (var device in _deviceManager.GetAllDevices())
        {
            try
            {
                if (device.Stream != null && device.Client?.Connected == true)
                {
                    await device.Stream.WriteAsync(bytes);
                }
            }
            catch (Exception ex)
            {
                Console.WriteLine($"Broadcast error to {device.DeviceId}: {ex.Message}");
            }
        }
    }

    public async Task SendToDeviceAsync<T>(string deviceId, T message) where T : class
    {
        var device = _deviceManager.GetDevice(deviceId);
        if (device?.Stream != null && device.Client?.Connected == true)
        {
            var json = JsonSerializer.Serialize(message) + "\n";
            var bytes = Encoding.UTF8.GetBytes(json);
            await device.Stream.WriteAsync(bytes);
        }
    }

    public IEnumerable<DeviceConnection> GetAllDevices() => _deviceManager.GetAllDevices();

    public async ValueTask DisposeAsync()
    {
        _cts?.Cancel();
        await _heartbeatMonitor.StopAsync();
        _listener?.Stop();
        _cts?.Dispose();
    }
}
```

- [ ] **Step 2: 更新 Program.cs**

```csharp
// PC/src/JdHelperControl/Program.cs
using JdHelperControl.Options;
using JdHelperControl.Services;

var options = new TcpServerOptions
{
    Port = 8765,
    HeartbeatInterval = 30,
    HeartbeatTimeout = 90
};

using var server = new TcpServer(options);

server.DeviceRegistered += (s, device) => 
    Console.WriteLine($"Device registered: {device.DeviceName} ({device.DeviceId})");

server.DeviceDisconnected += (s, deviceId) => 
    Console.WriteLine($"Device disconnected: {deviceId}");

server.DeviceTimedOut += (s, deviceId) => 
    Console.WriteLine($"Device timed out: {deviceId}");

await server.StartAsync(CancellationToken.None);

Console.WriteLine("Press Enter to exit...");
Console.ReadLine();
```

- [ ] **Step 3: 提交代码**

```bash
cd PC
git add .
git commit -m "feat: 实现核心TcpServer服务器"
```

---

## 实现完成检查

- [x] Task 1: 项目结构和基础模型
- [x] Task 2: DeviceManager 设备管理器
- [x] Task 3: 消息处理器接口和处理器
- [x] Task 4: MessageDispatcher 消息分发器
- [x] Task 5: HeartbeatMonitor 心跳监控
- [x] Task 6: TcpServer 核心服务器

---

## 待添加处理器

以下处理器需要在后续实现中添加到 MessageDispatcher：

- SearchResultHandler: 处理 SEARCH_RESULT 消息
- ExecuteResultHandler: 处理 EXECUTE_RESULT 消息  
- GiftResultHandler: 处理 GIFT_RESULT 消息
- DisconnectHandler: 处理 DISCONNECT 消息

这些将在后续的子设计实现中添加。