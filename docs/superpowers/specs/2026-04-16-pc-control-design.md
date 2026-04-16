# PC 局域网控制端设计文档

**版本**: 1.0
**日期**: 2026-04-16
**状态**: 草稿

---

## 1. 概述

本文档描述 JDHelper PC 局域网控制端的设计方案。该控制端运行在 Windows 平台上，通过局域网与多个 Android 设备上的 JDHelper App 通信，实现集中控制和定时点击功能。

---

## 2. 系统架构

### 2.1 整体架构

```
┌─────────────────────────────────────────────────────────────┐
│                        PC Server                            │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐ │
│  │ TCP Server  │  │ NTP Client  │  │   Control UI        │ │
│  │ (端口 8765) │  │ (阿里云NTP) │  │ (C# WPF)           │ │
│  │             │  │ PC端独立使用  │  │                    │ │
│  └─────────────┘  └─────────────┘  └─────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
          │                    │                    │
          │ TCP 长连接         │ NTP 校准            │ HTTP
          │                    │                    │
    ┌─────┴─────┐        ┌─────┴─────┐        ┌─────┴─────┐
    │           │        │           │        │           │
┌───▼───┐   ┌───▼───┐ ┌───▼───┐   ┌───▼───┐ ┌───▼───┐
│App 1  │   │App 2  │ │App 3  │   │App N  │ │NTP   │
│手机1  │   │手机2  │ │手机3  │   │手机N  │ │阿里云 │
└───────┘   └───────┘ └───────┘   └───────┘ └───────┘
```

### 2.2 技术选型

| 组件 | 技术选型 | 理由 |
|------|----------|------|
| PC 端语言 | C# (.NET 9) | 用户指定 |
| UI 框架 | WPF | 用户指定，现代化 Windows UI |
| 网络通信 | .NET Socket (TcpListener) | 原生支持，高性能 |
| NTP 客户端 | System.Net.Sockets | .NET 内置支持 |
| JSON 处理 | System.Text.Json | .NET 内置，高性能 |

---

## 3. 通信协议

### 3.1 连接建立

1. App 作为 Client，主动连接 PC Server
2. 连接成功后，App 发送 REGISTER 消息进行注册
3. PC Server 维护设备列表，返回注册结果

### 3.2 消息格式

所有消息采用 JSON 格式，通过 TCP 通道传输。

**通用消息结构：**
```json
{
  "type": "消息类型",
  "timestamp": 1234567890,
  "payload": { }
}
```

### 3.3 消息类型

#### 3.3.1 注册消息 (App → PC)

```json
{
  "type": "REGISTER",
  "timestamp": 1234567890,
  "payload": {
    "deviceName": "我的手机1",
    "deviceId": "uuid-xxxx-xxxx",
    "appVersion": "1.2.3",
    "androidVersion": "11"
  }
}
```

#### 3.3.2 注册响应 (PC → App)

```json
{
  "type": "REGISTER_ACK",
  "timestamp": 1234567890,
  "payload": {
    "success": true,
    "serverTime": 1234567890000,
    "message": "注册成功"
  }
}
```

#### 3.3.3 查找按钮指令 (PC → App)

```json
{
  "type": "SEARCH",
  "timestamp": 1234567890,
  "payload": {
    "rule": {
      "keywords": ["点击浏览", "立即抢购", "提交订单"],
      "text": "立即抢购",
      "resourceId": "btn_confirm",
      "contentDescription": "抢购",
      "className": "android.widget.Button",
      "description": "抢购按钮"
    },
    "searchId": "search-001"
  }
}
```

**规则字段说明（与 App 现有 ButtonFinder 逻辑兼容）：**

| 字段 | 类型 | 说明 | 示例 |
|------|------|------|------|
| keywords | string[] | 关键词列表，按优先级匹配 | `["点击浏览", "立即抢购"]` |
| text | string | 按钮文字完全匹配 | `"立即抢购"` |
| resourceId | string | 资源 ID 匹配 | `"btn_confirm"` |
| contentDescription | string | 内容描述匹配 | `"抢购"` |
| className | string | 控件类型匹配 | `"android.widget.Button"` |

**说明：**
- `keywords` 与 App 现有 `TARGET_KEYWORDS` 对应，支持模糊匹配
- 所有字段为可选，App 会组合匹配条件
- 匹配优先级：keywords > text > resourceId > contentDescription > className

#### 3.3.4 查找结果上报 (App → PC)

```json
{
  "type": "SEARCH_RESULT",
  "timestamp": 1234567890,
  "payload": {
    "searchId": "search-001",
    "found": true,
    "position": {
      "x": 540,
      "y": 1200
    },
    "elementInfo": {
      "text": "立即抢购",
      "resourceId": "btn_confirm"
    }
  }
}
```

**未找到的情况：**
```json
{
  "type": "SEARCH_RESULT",
  "timestamp": 1234567890,
  "payload": {
    "searchId": "search-001",
    "found": false,
    "reason": "未找到匹配元素"
  }
}
```

#### 3.3.5 执行点击指令 (PC → App)

```json
{
  "type": "EXECUTE",
  "timestamp": 1234567890,
  "payload": {
    "searchId": "search-001"
  }
}
```

#### 3.3.6 执行结果上报 (App → PC)

```json
{
  "type": "EXECUTE_RESULT",
  "timestamp": 1234567890,
  "payload": {
    "searchId": "search-001",
    "success": true,
    "clickTime": 1234567890123,
    "message": "点击成功"
  }
}
```

#### 3.3.7 心跳消息 (App → PC)

```json
{
  "type": "HEARTBEAT",
  "timestamp": 1234567890,
  "payload": {
    "status": "IDLE"
  }
}
```

**PC 响应心跳：**
```json
{
  "type": "HEARTBEAT_ACK",
  "timestamp": 1234567890,
  "payload": {}
}
```

#### 3.3.8 断开连接消息 (App → PC)

```json
{
  "type": "DISCONNECT",
  "timestamp": 1234567890,
  "payload": {
    "reason": "用户手动断开"
  }
}
```

#### 3.3.9 下发送礼指令 (PC → App)

```json
{
  "type": "GIFT",
  "timestamp": 1234567890,
  "payload": {
    "giftId": "gift-001",
    "targetUrl": "https://jd.com/gift/xxx",
    "buttonRule": {
      "text": "立即兑换"
    },
    "config": {
      "retryCount": 3,
      "retryInterval": 2000
    }
  }
}
```

#### 3.3.10 送礼结果上报 (App → PC)

```json
{
  "type": "GIFT_RESULT",
  "timestamp": 1234567890,
  "payload": {
    "giftId": "gift-001",
    "success": true,
    "message": "兑换成功"
  }
}
```

---

## 4. 时间同步机制

### 4.1 PC 端 NTP 同步（独立于 App）

PC 端独立使用阿里云 NTP 同步本地时间，用于计算整分时刻。

**NTP 服务器：**
- 默认：阿里云 NTP `ntp.aliyun.com`（可配置）
- 可配置为其他 NTP 服务器

**同步策略：**
- 启动时自动同步
- 后续每 5 分钟同步一次（可配置）
- 超时时间：10 秒
- 同步精度目标：< 10ms

### 4.2 整分触发机制

```
1. PC 端 NTP 校准本地时间
2. 计算下一个整分时刻（下一分钟的 00:00.000）
3. 使用高精度定时器等待到整分时刻
4. 发送 EXECUTE 指令到所有已连接 App
```

**注意：** App 端不参与时间计算，只负责执行点击动作。

### 4.3 App 端时间保持

- App 端保留现有的 NTP 时间同步（TimeService）
- 用于本地定时点击功能
- 与 PC 控制功能解耦

---

## 5. 客户端管理

### 5.1 设备状态

| 状态 | 描述 |
|------|------|
| ONLINE | 已连接，正常通信 |
| IDLE | 空闲，等待指令 |
| SEARCHING | 正在查找按钮 |
| READY | 按钮已找到，待执行 |
| EXECUTING | 正在执行点击 |
| OFFLINE | 断开连接 |

### 5.2 心跳机制

- App 每 30 秒发送一次心跳
- PC 端超时 90 秒未收到心跳，标记为 OFFLINE
- 心跳消息携带当前设备状态

### 5.3 重连机制

- App 断开后自动重连，最大重试间隔 30 秒
- 重连成功后重新发送 REGISTER 消息

---

## 6. PC 端 UI 设计

### 6.1 主界面布局

```
┌────────────────────────────────────────────────────────────────┐
│  JDHelper 局域网控制端                              [设置]    │
├────────────────────────────────────────────────────────────────┤
│  ┌─────────────────────────────────────────────────────────┐  │
│  │ 设备列表                                    [刷新] [全部执行]│ │
│  ├─────────────────────────────────────────────────────────┤  │
│  │ ☑ 手机1    192.168.1.101   ONLINE    IDLE      [执行]   │  │
│  │ ☑ 手机2    192.168.1.102   ONLINE    READY     [执行]   │  │
│  │ ☑ 手机3    192.168.1.103   OFFLINE   -                  │  │
│  └─────────────────────────────────────────────────────────┘  │
├────────────────────────────────────────────────────────────────┤
│  按钮查找规则                    [模板 ▼] [保存模板]           │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │ {                                                         │  │
│  │   "text": "立即抢购",                                     │  │
│  │   "resourceId": "btn_confirm"                            │  │
│  │ }                                                         │  │
│  └─────────────────────────────────────────────────────────┘  │
│                                                 [查找] [执行]  │
├────────────────────────────────────────────────────────────────┤
│  执行日志                                                            │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │ 10:00:01.123  手机1  发送查找指令成功                              │  │
│  │ 10:00:01.456  手机1  找到按钮: (540, 1200)                       │  │
│  │ 10:00:01.789  手机2  找到按钮: (530, 1180)                       │  │
│  │ 10:00:02.000  发送执行指令到 2 台设备                            │  │
│  │ 10:00:02.100  手机1  点击成功                                     │  │
│  │ 10:00:02.105  手机2  点击成功                                     │  │
│  └─────────────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────────────┘
```

### 6.2 设置界面

```
┌─────────────────────────────────────────┐
│              设置                       │
├─────────────────────────────────────────┤
│  服务器设置                              │
│  ├─ 监听端口: [8765      ]              │
│  └─ 自动启动: [☑]                       │
│                                         │
│  NTP 设置                               │
│  ├─ NTP 服务器: [time.cloudflare.com ] │
│  └─ 同步间隔: [5] 分钟                  │
│                                         │
│  高级设置                               │
│  ├─ 心跳间隔: [30] 秒                   │
│  └─ 超时时间: [90] 秒                   │
│                              [保存]     │
└─────────────────────────────────────────┘
```

### 6.3 指令模板管理

**模板列表：**
- 抢购按钮
- 立即兑换
- 提交订单
- 确认支付

**模板编辑：**
```json
{
  "name": "抢购按钮",
  "rule": {
    "text": "立即抢购"
  }
}
```

---

## 7. App 端改造点

### 7.1 新增功能模块

| 模块 | 路径 | 功能 |
|------|------|------|
| TCP Client | `service/LanControlClient.kt` | 局域网 TCP 通信 |
| Command Handler | `service/LanCommandHandler.kt` | 指令解析和处理 |

**说明：**
- 使用现有 SharedPreferences 存储配置（与 PREFS_NAME 一致）
- 复用现有 `ButtonFinder` 的关键词匹配逻辑
- 复用现有 `AccessibilityClickService` 执行点击
- 复用现有 `TimeService` 获取 NTP 时间

### 7.2 App 端配置项

在现有 `click_settings` SharedPreferences 中增加：

| 配置项 | Key | 默认值 | 说明 |
|--------|-----|--------|------|
| PC 服务器地址 | `pc_server_address` | - | PC 的 IP 地址 |
| PC 服务器端口 | `pc_server_port` | 8765 | TCP 端口 |
| 设备名称 | `device_name` | 设备型号 | 设备显示名称 |
| 自动连接 | `auto_lan_connect` | true | 启动时自动连接 |

### 7.3 指令处理流程

```
SEARCH 指令
    ↓
解析 JSON 规则（keywords, text, resourceId 等）
    ↓
调用 ButtonFinder.findTargetButton() 查找按钮
    ↓
记录按钮位置（Map<searchId, Point>）
    ↓
返回 SEARCH_RESULT
```

```
EXECUTE 指令
    ↓
根据 searchId 查找已记录的按钮位置
    ↓
调用 AccessibilityClickService.performGlobalClick() 执行点击
    ↓
返回 EXECUTE_RESULT
```

### 7.4 与现有功能的集成

1. **ButtonFinder**: 复用现有关键词匹配逻辑，扩展支持自定义关键词
2. **AccessibilityClickService**: 复用现有 performGlobalClick()
3. **TimeService**: 保留现有 NTP 时间同步（用于本地定时点击）
4. **SharedPreferences**: 使用现有 PREFS_NAME 存储新配置
5. **GiftClickHistory**: 送礼结果记录到现有历史表

### 7.5 连接控制与功能互斥

**连接状态管理：**
- App 端提供"连接 PC"开关按钮
- 连接成功后，禁用悬浮菜单中的定时点击功能
- 断开连接后，恢复原有功能

**功能互斥逻辑：**

| 状态 | 悬浮菜单定时点击 | PC 控制点击 |
|------|-----------------|-------------|
| 未连接 PC | ✅ 可用 | ❌ 不可用 |
| 已连接 PC | ❌ 禁用 | ✅ 可用 |

**UI 修改：**
- 在设置页面或悬浮菜单中添加"连接 PC"开关
- 连接成功后，悬浮菜单显示"已连接 PC"状态
- 定时点击按钮在连接 PC 后自动禁用

---

## 8. 扩展性设计

### 8.1 指令类型扩展

新增指令只需：
1. 在消息类型枚举中添加新类型
2. PC 端实现发送逻辑
3. App 端实现处理逻辑

### 8.2 模块化设计

- **CommandRegistry**: 指令注册中心，支持动态注册新指令
- **DeviceManager**: 设备管理器，支持插件化设备适配

---

## 9. 错误处理

### 9.1 网络错误

| 错误类型 | 处理方式 |
|----------|----------|
| 连接失败 | 显示错误提示，支持重试 |
| 断开连接 | 自动重连，显示状态 |
| 超时 | 超时提示，记录日志 |

### 9.2 指令错误

| 错误类型 | 处理方式 |
|----------|----------|
| JSON 解析失败 | 返回错误响应 |
| 规则无效 | 返回错误响应并说明原因 |
| 按钮未找到 | 返回 found: false |

---

## 10. 配置项汇总

### 10.1 PC 端配置

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| server.port | 8765 | TCP 监听端口 |
| server.autoStart | true | 启动时自动开启服务 |
| ntp.server | time.cloudflare.com | NTP 服务器地址（可配置为阿里云 NTP） |
| ntp.interval | 5 | NTP 同步间隔（分钟） |
| heartbeat.interval | 30 | 心跳间隔（秒） |
| heartbeat.timeout | 90 | 超时时间（秒） |

### 10.2 App 端配置

| 配置项 | Key | 默认值 | 说明 |
|--------|-----|--------|------|
| server.address | `pc_server_address` | - | PC 服务器 IP 地址 |
| server.port | `pc_server_port` | 8765 | PC 服务器端口 |
| device.name | `device_name` | 设备型号 | 设备显示名称 |
| auto.connect | `auto_lan_connect` | true | 启动时自动连接 |

---

## 11. 实现优先级

### 第一阶段（核心功能）
1. PC 端 TCP Server 实现
2. App 端 TCP Client 实现
3. 基础通信协议（REGISTER/HEARTBEAT）
4. 设备列表展示

### 第二阶段（主要功能）
5. SEARCH / EXECUTE 指令实现
6. 按钮查找规则 JSON 解析
7. 执行结果日志

### 第三阶段（完善功能）
8. 指令模板管理
9. NTP 时间同步
10. 送礼功能

---

## 12. 待定事项

- [x] UI 框架已选型（C# WPF）
- [ ] App 端权限处理
- [ ] 安全验证机制（可选）
