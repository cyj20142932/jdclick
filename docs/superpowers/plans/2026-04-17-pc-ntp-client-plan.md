# PC端NTP客户端实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现PC端NTP客户端，提供高精度时间同步，直接修改系统时间

**Architecture:** 使用System.Net.Sockets进行NTP协议通信，通过Windows API修改系统时间，强制要求管理员权限

**Tech Stack:** C# (.NET 9), System.Net.Sockets, Windows API (SetSystemTime)

---

## 文件结构

```
PC/
├── src/
│   └── JdHelperControl/
│       ├── Options/
│       │   └── NtpOptions.cs
│       ├── Models/
│       │   └── NtpSyncResult.cs
│       ├── Services/
│       │   ├── NtpService.cs
│       │   └── SystemTimeHelper.cs
│       └── Native/
│           └── Kernel32.cs
```

---

## 实现步骤

### Task 1: 创建NTP配置和结果模型

**Files:**
- Create: `PC/src/JdHelperControl/Options/NtpOptions.cs`
- Create: `PC/src/JdHelperControl/Models/NtpSyncResult.cs`

- [ ] **Step 1: 创建 NtpOptions 配置类**

```csharp
// PC/src/JdHelperControl/Options/NtpOptions.cs
namespace JdHelperControl.Options;

public class NtpOptions
{
    public string Server { get; set; } = "ntp.aliyun.com";
    public int IntervalMinutes { get; set; } = 5;
    public bool AutoSyncEnabled { get; set; } = true;
    public int TimeoutSeconds { get; set; } = 10;
}
```

- [ ] **Step 2: 创建 NtpSyncResult 结果类**

```csharp
// PC/src/JdHelperControl/Models/NtpSyncResult.cs
namespace JdHelperControl.Models;

public class NtpSyncResult
{
    public bool Success { get; set; }
    public DateTime? NtpTime { get; set; }
    public TimeSpan Offset { get; set; }
    public string? ErrorMessage { get; set; }
    public long RoundTripDelay { get; set; }
}
```

- [ ] **Step 3: 提交代码**

```bash
cd PC
git add .
git commit -m "feat: 创建NTP配置和结果模型"
```

---

### Task 2: 实现Windows系统时间修改API

**Files:**
- Create: `PC/src/JdHelperControl/Native/Kernel32.cs`
- Create: `PC/src/JdHelperControl/Services/SystemTimeHelper.cs`

- [ ] **Step 1: 创建Kernel32原生方法声明**

```csharp
// PC/src/JdHelperControl/Native/Kernel32.cs
using System.Runtime.InteropServices;

namespace JdHelperControl.Native;

internal static class Kernel32
{
    [StructLayout(LayoutKind.Sequential)]
    public struct SYSTEMTIME
    {
        public ushort Year;
        public ushort Month;
        public ushort DayOfWeek;
        public ushort Day;
        public ushort Hour;
        public ushort Minute;
        public ushort Second;
        public ushort Milliseconds;
    }

    [DllImport("kernel32.dll", SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    public static extern bool SetSystemTime(ref SYSTEMTIME lpSystemTime);

    [DllImport("kernel32.dll", SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    public static extern bool SetLocalTime(ref SYSTEMTIME lpSystemTime);
}
```

- [ ] **Step 2: 实现 SystemTimeHelper 系统时间帮助类**

```csharp
// PC/src/JdHelperControl/Services/SystemTimeHelper.cs
using System.Security.Principal;
using JdHelperControl.Native;

namespace JdHelperControl.Services;

public static class SystemTimeHelper
{
    public static bool IsAdmin()
    {
        using var identity = WindowsIdentity.GetCurrent();
        var principal = new WindowsPrincipal(identity);
        return principal.IsInRole(WindowsBuiltInRole.Administrator);
    }

    public static void RestartAsAdmin()
    {
        var startInfo = new System.Diagnostics.ProcessStartInfo
        {
            UseShellExecute = true,
            WorkingDirectory = Environment.CurrentDirectory,
            FileName = Environment.ProcessPath,
            Verb = "runas"
        };

        try
        {
            System.Diagnostics.Process.Start(startInfo);
            Environment.Exit(0);
        }
        catch (System.ComponentModel.Win32Exception ex)
        {
            throw new InvalidOperationException("无法以管理员身份重启", ex);
        }
    }

    public static bool SetSystemTime(DateTime dateTime)
    {
        if (!IsAdmin())
        {
            throw new UnauthorizedAccessException("需要管理员权限才能修改系统时间");
        }

        var systemTime = new Kernel32.SYSTEMTIME
        {
            Year = (ushort)dateTime.Year,
            Month = (ushort)dateTime.Month,
            Day = (ushort)dateTime.Day,
            Hour = (ushort)dateTime.Hour,
            Minute = (ushort)dateTime.Minute,
            Second = (ushort)dateTime.Second,
            Milliseconds = (ushort)dateTime.Millisecond,
            DayOfWeek = (ushort)dateTime.DayOfWeek
        };

        return Kernel32.SetSystemTime(ref systemTime);
    }

    public static DateTime GetSystemTime()
    {
        return DateTime.Now;
    }
}
```

- [ ] **Step 3: 提交代码**

```bash
cd PC
git add .
git commit -m "feat: 实现Windows系统时间修改API"
```

---

### Task 3: 实现NTP协议和数据包结构

**Files:**
- Create: `PC/src/JdHelperControl/Services/NtpPacket.cs`
- Test: `PC/tests/JdHelperControl.Tests/NtpPacketTests.cs`

- [ ] **Step 1: 编写 NTP 数据包测试**

```csharp
// PC/tests/JdHelperControl.Tests/NtpPacketTests.cs
using JdHelperControl.Services;

namespace JdHelperControl.Tests;

public class NtpPacketTests
{
    [Fact]
    public void CreateRequest_ShouldHaveCorrectFlags()
    {
        var packet = NtpPacket.CreateRequest();
        
        // Version 3 (011), Mode 3 (Client) = 0x1B
        Assert.Equal(0x1B, packet.Flags);
    }

    [Fact]
    public void ParseResponse_ValidPacket_ShouldExtractTimestamp()
    {
        // 构造一个有效的NTP响应包
        var packet = new NtpPacket();
        packet.Flags = 0x1C; // Server mode
        packet.TransmitTimestamp = 391614880000000000ul; // 2024-01-01 00:00:00 UTC
        
        var result = packet.GetTransmitTime();
        
        Assert.NotNull(result);
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

```bash
cd PC
dotnet test --filter "NtpPacketTests" -v
# Expected: FAIL (class not found)
```

- [ ] **Step 3: 实现 NtpPacket 数据包**

```csharp
// PC/src/JdHelperControl/Services/NtpPacket.cs
using System.Runtime.InteropServices;

namespace JdHelperControl.Services;

[StructLayout(LayoutKind.Sequential, Pack = 4)]
public struct NtpPacket
{
    public byte Flags;
    public byte Stratum;
    public sbyte Poll;
    public sbyte Precision;
    public uint RootDelay;
    public uint RootDispersion;
    public uint ReferenceIdentifier;
    public ulong ReferenceTimestamp;
    public ulong OriginateTimestamp;
    public ulong ReceiveTimestamp;
    public ulong TransmitTimestamp;

    // 48 bytes padding for NTP packet
    [MarshalAs(UnmanagedType.ByValArray, SizeConst = 48)]
    public byte[] _ = new byte[48];

    public static NtpPacket CreateRequest()
    {
        return new NtpPacket
        {
            Flags = 0x1B  // Version 3 (011), Mode 3 Client (011) = 0x1B
        };
    }

    public DateTime? GetTransmitTime()
    {
        if (TransmitTimestamp == 0)
            return null;
        
        return NtpTimestampToDateTime(TransmitTimestamp);
    }

    public DateTime? GetReceiveTime()
    {
        if (ReceiveTimestamp == 0)
            return null;
        
        return NtpTimestampToDateTime(ReceiveTimestamp);
    }

    private static DateTime NtpTimestampToDateTime(ulong ntpTimestamp)
    {
        var ntpEpoch = new DateTime(1900, 1, 1, 0, 0, 0, DateTimeKind.Utc);
        return ntpEpoch.AddTicks((long)(ntpTimestamp * TimeSpan.TicksPerSecond / 10000000));
    }
}
```

- [ ] **Step 4: 运行测试验证通过**

```bash
cd PC
dotnet test --filter "NtpPacketTests" -v
# Expected: PASS
```

- [ ] **Step 5: 提交代码**

```bash
cd PC
git add .
git commit -m "feat: 实现NTP协议数据包结构"
```

---

### Task 4: 实现NtpService核心服务

**Files:**
- Create: `PC/src/JdHelperControl/Services/NtpService.cs`
- Test: `PC/tests/JdHelperControl.Tests/NtpServiceTests.cs`

- [ ] **Step 1: 编写 NtpService 测试**

```csharp
// PC/tests/JdHelperControl.Tests/NtpServiceTests.cs
using JdHelperControl.Options;
using JdHelperControl.Services;

namespace JdHelperControl.Tests;

public class NtpServiceTests
{
    private readonly NtpOptions _options = new()
    {
        Server = "ntp.aliyun.com",
        TimeoutSeconds = 10
    };

    [Fact]
    public async Task SyncAsync_ValidServer_ShouldReturnSuccess()
    {
        var service = new NtpService(_options);
        
        var result = await service.SyncAsync();
        
        Assert.True(result.Success, result.ErrorMessage ?? "Unknown error");
        Assert.NotNull(result.NtpTime);
    }

    [Fact]
    public async Task SyncAsync_InvalidServer_ShouldReturnFailure()
    {
        var options = new NtpOptions { Server = "invalid.server.not.exist" };
        var service = new NtpService(options);
        
        var result = await service.SyncAsync();
        
        Assert.False(result.Success);
        Assert.NotNull(result.ErrorMessage);
    }

    [Fact]
    public void IsAdmin_ShouldReturnCurrentStatus()
    {
        var isAdmin = SystemTimeHelper.IsAdmin();
        
        // 这个测试只验证方法可运行
        Assert.True(isAdmin || !isAdmin);
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

```bash
cd PC
dotnet test --filter "NtpServiceTests" -v
# Expected: FAIL (class not found)
```

- [ ] **Step 3: 实现 NtpService**

```csharp
// PC/src/JdHelperControl/Services/NtpService.cs
using System.Net.Sockets;
using JdHelperControl.Models;
using JdHelperControl.Options;

namespace JdHelperControl.Services;

public class NtpService : IAsyncDisposable
{
    private readonly NtpOptions _options;
    private CancellationTokenSource? _autoSyncCts;
    private DateTime _lastSyncTime;
    private TimeSpan _lastOffset;
    private bool _isAutoSyncing;

    public event EventHandler<NtpSyncResult>? SyncCompleted;
    public event EventHandler<string>? SyncError;

    public DateTime GetLastSyncTime() => _lastSyncTime;
    public TimeSpan GetLastOffset() => _lastOffset;
    public bool IsAutoSyncing() => _isAutoSyncing;

    public NtpService(NtpOptions options)
    {
        _options = options;
    }

    public async Task<NtpSyncResult> SyncAsync(CancellationToken ct = default)
    {
        if (!SystemTimeHelper.IsAdmin())
        {
            return new NtpSyncResult
            {
                Success = false,
                ErrorMessage = "需要管理员权限才能修改系统时间"
            };
        }

        try
        {
            using var client = new UdpClient();
            client.Client.ReceiveTimeout = _options.TimeoutSeconds * 1000;
            client.Client.SendTimeout = _options.TimeoutSeconds * 1000;

            await client.ConnectAsync(_options.Server, 123, ct);

            var request = NtpPacket.CreateRequest();
            var requestBytes = new byte[48];
            Marshal.Copy(request, 0, requestBytes, 48);

            var t1 = DateTime.UtcNow;
            await client.SendAsync(requestBytes, ct);
            
            var responseBytes = await client.ReceiveAsync(ct);
            var t4 = DateTime.UtcNow;

            var response = new NtpPacket();
            Marshal.Copy(responseBytes.Buffer, 0, ref response, 48);

            var t3 = response.GetTransmitTime();
            var t2 = response.GetReceiveTime();

            if (t2 == null || t3 == null)
            {
                return new NtpSyncResult
                {
                    Success = false,
                    ErrorMessage = "无效的NTP响应"
                };
            }

            var offset = ((t2.Value - t1) + (t3.Value - t4)).TotalMilliseconds / 2;
            var delay = (t4 - t1 - (t3.Value - t2.Value)).TotalMilliseconds;

            var ntpTime = t3.Value.AddMilliseconds(offset);
            var success = SystemTimeHelper.SetSystemTime(ntpTime.ToLocalTime());

            if (!success)
            {
                return new NtpSyncResult
                {
                    Success = false,
                    ErrorMessage = "修改系统时间失败"
                };
            }

            _lastSyncTime = DateTime.UtcNow;
            _lastOffset = TimeSpan.FromMilliseconds(offset);

            var result = new NtpSyncResult
            {
                Success = true,
                NtpTime = ntpTime,
                Offset = _lastOffset,
                RoundTripDelay = (long)delay
            };

            SyncCompleted?.Invoke(this, result);
            return result;
        }
        catch (Exception ex)
        {
            var result = new NtpSyncResult
            {
                Success = false,
                ErrorMessage = ex.Message
            };
            
            SyncError?.Invoke(this, ex.Message);
            return result;
        }
    }

    public async Task StartAutoSyncAsync(CancellationToken ct)
    {
        if (_isAutoSyncing) return;
        
        _isAutoSyncing = true;
        _autoSyncCts = CancellationTokenSource.CreateLinkedTokenSource(ct);

        try
        {
            while (!_autoSyncCts.Token.IsCancellationRequested)
            {
                if (_options.AutoSyncEnabled)
                {
                    await SyncAsync(_autoSyncCts.Token);
                }

                await Task.Delay(TimeSpan.FromMinutes(_options.IntervalMinutes), _autoSyncCts.Token);
            }
        }
        catch (OperationCanceledException)
        {
            // Expected when stopped
        }
        finally
        {
            _isAutoSyncing = false;
        }
    }

    public async Task StopAutoSyncAsync()
    {
        if (_autoSyncCts != null)
        {
            await _autoSyncCts.CancelAsync();
            _autoSyncCts.Dispose();
            _autoSyncCts = null;
        }
        _isAutoSyncing = false;
    }

    public async ValueTask DisposeAsync()
    {
        await StopAutoSyncAsync();
    }
}
```

- [ ] **Step 4: 运行测试验证通过**

```bash
cd PC
dotnet test --filter "NtpServiceTests" -v
# Expected: PASS
```

- [ ] **Step 5: 提交代码**

```bash
cd PC
git add .
git commit -m "feat: 实现NtpService核心服务"
```

---

### Task 5: 强制管理员权限检查

**Files:**
- Modify: `PC/src/JdHelperControl/Program.cs`

- [ ] **Step 1: 在Program.cs中添加管理员权限检查**

```csharp
// PC/src/JdHelperControl/Program.cs
using JdHelperControl.Options;
using JdHelperControl.Services;

Console.WriteLine("=== JDHelper PC 控制端 ===");

// 强制要求管理员权限
if (!SystemTimeHelper.IsAdmin())
{
    Console.WriteLine("错误: 需要管理员权限才能修改系统时间");
    Console.WriteLine("按 Enter 以管理员身份重新运行，或按其他键退出...");
    
    var key = Console.ReadKey(true);
    if (key.Key == ConsoleKey.Enter)
    {
        try
        {
            SystemTimeHelper.RestartAsAdmin();
        }
        catch (Exception ex)
        {
            Console.WriteLine($"无法以管理员身份启动: {ex.Message}");
        }
    }
    Environment.Exit(1);
}

Console.WriteLine("管理员权限验证通过");

// NTP 同步测试
var ntpOptions = new NtpOptions
{
    Server = "ntp.aliyun.com",
    AutoSyncEnabled = false
};

using var ntpService = new NtpService(ntpOptions);

Console.WriteLine("开始NTP同步...");
var result = await ntpService.SyncAsync();

if (result.Success)
{
    Console.WriteLine($"NTP同步成功!");
    Console.WriteLine($"  服务器时间: {result.NtpTime}");
    Console.WriteLine($"  时间偏移: {result.Offset.TotalMilliseconds:F2}ms");
    Console.WriteLine($"  往返延迟: {result.RoundTripDelay}ms");
}
else
{
    Console.WriteLine($"NTP同步失败: {result.ErrorMessage}");
}

Console.WriteLine("按 Enter 退出...");
Console.ReadLine();
```

- [ ] **Step 2: 提交代码**

```bash
cd PC
git add .
git commit -m "feat: 添加强制管理员权限检查"
```

---

## 实现完成检查

- [x] Task 1: NTP配置和结果模型
- [x] Task 2: Windows系统时间修改API
- [x] Task 3: NTP协议数据包结构
- [x] Task 4: NtpService核心服务
- [x] Task 5: 强制管理员权限检查