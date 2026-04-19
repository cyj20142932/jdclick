# PC端NTP客户端设计文档

**版本**: 1.0
**日期**: 2026-04-17
**状态**: 草稿
**子设计编号**: 2/6

---

## 1. 概述

本文档描述 PC 局域网控制端 NTP 时间同步模块的设计方案。该模块独立为 PC 提供高精度 NTP 时间同步，直接修改系统时间。

---

## 2. 技术架构

### 2.1 技术选型

| 组件 | 技术选型 | 理由 |
|------|----------|------|
| 编程语言 | C# (.NET 9) | 用户指定 |
| NTP 客户端 | System.Net.Sockets | .NET 内置支持 |
| 时间修改 | Windows API (SetSystemTime) | 需要管理员权限 |
| 精度目标 | < 10ms | 满足抢购场景需求 |

### 2.2 同步策略

```
1. 启动时检测管理员权限
   - 非管理员→显示提示，退出应用
   - 管理员→继续
2. 启动时自动同步一次
3. 定时轮询（用户可开关，默认开启）
4. 提供手动同步按钮
5. 同步成功→直接修改系统时间
6. 同步失败→记录日志，保持上次同步时间
```

---

## 3. 核心类设计

### 3.1 NtpOptions (配置类)

```csharp
public class NtpOptions
{
    public string Server { get; set; } = "ntp.aliyun.com";  // NTP服务器
    public int IntervalMinutes { get; set; } = 5;          // 同步间隔
    public bool AutoSyncEnabled { get; set; } = true;      // 自动同步开关
    public int TimeoutSeconds { get; set; } = 10;          // 超时时间
}
```

### 3.2 NtpService (主服务类)

```csharp
public class NtpService : IAsyncDisposable
{
    private readonly NtpOptions _options;
    private CancellationTokenSource? _autoSyncCts;
    private DateTime _lastSyncTime;
    private TimeSpan _lastOffset;

    // 执行一次NTP同步
    public Task<NtpSyncResult> SyncAsync(CancellationToken ct = default);

    // 启动定时自动同步
    public Task StartAutoSyncAsync(CancellationToken ct);

    // 停止自动同步
    public Task StopAutoSyncAsync();

    // 获取最后同步时间
    public DateTime GetLastSyncTime();

    // 获取最后同步的偏移量
    public TimeSpan GetLastOffset();
}

public class NtpSyncResult
{
    public bool Success { get; set; }
    public DateTime? NtpTime { get; set; }
    public TimeSpan Offset { get; set; }
    public string? ErrorMessage { get; set; }
    public long RoundTripDelay { get; set; }  // 往返延迟
}
```

### 3.3 SystemTimeHelper (系统时间修改)

```csharp
public static class SystemTimeHelper
{
    // 检查是否具有管理员权限
    public static bool IsAdmin();

    // 请求管理员权限（以管理员身份重新启动）
    public static void RestartAsAdmin();

    // 修改系统时间（需要管理员权限）
    public static bool SetSystemTime(DateTime dateTime);

    // 获取系统时间
    public static DateTime GetSystemTime();
}
```

---

## 4. NTP 协议实现

### 4.1 NTP 数据包结构

```csharp
public struct NtpPacket
{
    // 第一字节：版本(3位) + 模式(3位)
    public byte Flags;

    // 时钟 stratum
    public byte Stratum;

    // 轮询间隔
    public sbyte Poll;

    // 精度
    public sbyte Precision;

    // 同步距离、根延迟
    public uint RootDelay;

    // 同步离散度、根离散度
    public uint RootDispersion;

    // 参考时钟标识符
    public uint ReferenceIdentifier;

    // 参考时间戳
    public ulong ReferenceTimestamp;

    // 原始时间戳
    public ulong OriginateTimestamp;

    // 接收时间戳
    public ulong ReceiveTimestamp;

    // 传输时间戳
    public ulong TransmitTimestamp;
}
```

### 4.2 同步算法

```
1. 构造 NTP 请求包
2. 记录发送时间 T1
3. 发送到 NTP 服务器
4. 记录接收时间 T2
5. 解析服务器响应，获取服务器时间 T3
6. 记录接收完成时间 T4
7. 计算偏移：
   offset = ((T2 - T1) + (T3 - T4)) / 2
8. 计算往返延迟：
   delay = (T4 - T1) - (T3 - T2)
9. 应用偏移到系统时间
```

---

## 5. 配置项

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| NtpServer | ntp.aliyun.com | NTP服务器地址 |
| IntervalMinutes | 5 | 同步间隔（分钟） |
| AutoSyncEnabled | true | 是否启用自动同步 |
| TimeoutSeconds | 10 | 超时时间（秒） |

---

## 6. 错误处理

| 错误场景 | 处理方式 |
|----------|----------|
| 无管理员权限 | 提示用户，退出应用 |
| NTP 服务器无响应 | 记录日志，保持当前时间 |
| JSON 解析失败 | 不适用（NTP不返回JSON） |
| 网络异常 | 记录日志，重试下一次 |

---

## 7. 待定事项

- [ ] 支持多个 NTP 服务器轮询（可选）
- [ ] 同步精度日志记录