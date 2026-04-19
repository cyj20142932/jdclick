# PC端WPF控制界面设计文档

**版本**: 1.0
**日期**: 2026-04-17
**状态**: 草稿
**子设计编号**: 3/6

---

## 1. 概述

本文档描述 PC 局域网控制端 WPF 控制界面的设计方案。该模块负责展示设备列表、按钮查找规则编辑、整分触发控制等功能。

---

## 2. 技术选型

| 组件 | 技术选型 | 理由 |
|------|----------|------|
| UI 框架 | WPF + MaterialDesign | 用户指定，现代化 Windows UI |
| 日志展示 | 虚拟滚动 | 大数据量下优化性能 |
| 数据绑定 | MVVM 模式 | 清晰分离 UI 和业务逻辑 |
| 状态管理 | CommunityToolkit.Mvvm | 简化 MVVM 实现 |

---

## 3. 主界面布局

```
┌────────────────────────────────────────────────────────────────┐
│  JDHelper 局域网控制端                    NTP: 已同步  [设置]  │
├────────────────────────────────────────────────────────────────┤
│  整分触发                                          [开启/关闭] │
│  当前PC时间: 10:59:59.123                                      │
│  下次触发: 11:00:00.000                                        │
├────────────────────────────────────────────────────────────────┤
│  设备列表                                          [刷新]    │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │ ☑ 手机1    192.168.1.101   ONLINE    IDLE      [执行]   │  │
│  │ ☑ 手机2    192.168.1.102   ONLINE    READY     [执行]   │  │
│  │ ☑ 手机3    192.168.1.103   OFFLINE   -                  │  │
│  └──────────────────────────────────────────────────────────┘  │
│                                              [全部执行]        │
├────────────────────────────────────────────────────────────────┤
│  按钮查找规则                    [模板 ▼] [保存模板]          │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │ {                                                         │  │
│  │   "text": "立即抢购",                                     │  │
│  │   "resourceId": "btn_confirm"                            │  │
│  │ }                                                         │  │
│  └──────────────────────────────────────────────────────────┘  │
│                                                    [查找] [执行]│
├────────────────────────────────────────────────────────────────┤
│  执行日志                                                          │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │ (虚拟滚动区域)                                            │  │
│  │ 10:00:01.123  手机1  发送查找指令成功                      │  │
│  │ 10:00:01.456  手机1  找到按钮: (540, 1200)                 │  │
│  └──────────────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────────────┘
```

---

## 4. 核心组件设计

### 4.1 视图模型 (ViewModel)

```csharp
// 主窗口视图模型
public partial class MainViewModel : ObservableObject
{
    // NTP 状态
    [ObservableProperty]
    private bool _isNtpSynced;

    [ObservableProperty]
    private DateTime _currentPcTime;

    [ObservableProperty]
    private DateTime _nextTriggerTime;

    // 整分触发
    [ObservableProperty]
    private bool _isFullMinuteTriggerEnabled;

    // 设备列表
    [ObservableProperty]
    private ObservableCollection<DeviceItemViewModel> _devices;

    [ObservableProperty]
    private bool _isAllSelected;

    // 按钮查找规则
    [ObservableProperty]
    private string _ruleJson = "{}";

    [ObservableProperty]
    private ObservableCollection<CommandTemplate> _templates;

    [ObservableProperty]
    private CommandTemplate? _selectedTemplate;

    // 日志
    [ObservableProperty]
    private ObservableCollection<LogEntry> _logs;

    // 命令
    public IAsyncRelayCommand RefreshCommand { get; }
    public IAsyncRelayCommand ExecuteAllCommand { get; }
    public IAsyncRelayCommand SearchCommand { get; }
    public IAsyncRelayCommand ExecuteCommand { get; }
    public IAsyncRelayCommand SaveTemplateCommand { get; }
    public IAsyncRelayCommand ToggleTriggerCommand { get; }
    public IAsyncRelayCommand OpenSettingsCommand { get; }
}

// 设备项视图模型
public partial class DeviceItemViewModel : ObservableObject
{
    [ObservableProperty]
    private bool _isSelected;

    [ObservableProperty]
    private string _deviceId;

    [ObservableProperty]
    private string _deviceName;

    [ObservableProperty]
    private string _ipAddress;

    [ObservableProperty]
    private DeviceStatus _status;

    public IAsyncRelayCommand ExecuteCommand { get; }
}
```

### 4.2 设备列表交互

| 操作 | 功能 |
|------|------|
| 勾选设备 | 批量选择要执行的设备 |
| 全选/取消全选 | 快速切换选中状态 |
| 单设备执行按钮 | 对单台设备执行操作 |
| 全部执行按钮 | 对所有已勾选设备执行 |

### 4.3 指令模板

**预设模板：**

| 模板名称 | 规则 |
|----------|------|
| 立即支付 | `{"text": "立即支付"}` |
| 提交订单 | `{"text": "提交订单"}` |
| 普通支付 | `{"text": "普通支付"}` |
| 打白条 | `{"text": "打白条"}` |

**模板数据结构：**
```csharp
public class CommandTemplate
{
    public string Name { get; set; }
    public string RuleJson { get; set; }
}
```

### 4.4 整分触发组件

| 组件 | 功能 |
|------|------|
| NTP状态指示 | 显示"NTP: 已同步"或"NTP: 未同步" |
| 当前PC时间 | 毫秒级精度显示，实时更新 |
| 下次触发时间 | 倒计时显示"下次触发: HH:mm:ss.fff" |
| 开启/关闭按钮 | ToggleButton 控制整分触发是否启用 |

---

## 5. 设置界面

```
┌─────────────────────────────────────────┐
│              设置                       │
├─────────────────────────────────────────┤
│  服务器设置                              │
│  ├─ 监听端口: [8765      ]              │
│  └─ 自动启动: [☑]                       │
│                                         │
│  NTP 设置                               │
│  ├─ NTP 服务器: [ntp.aliyun.com]        │
│  ├─ 同步间隔: [5] 分钟                  │
│  └─ 自动同步: [☑]                       │
│                                         │
│  高级设置                               │
│  ├─ 心跳间隔: [30] 秒                   │
│  └─ 超时时间: [90] 秒                   │
│                              [保存]     │
└─────────────────────────────────────────┘
```

---

## 6. 配置项

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| server.port | 8765 | TCP 监听端口 |
| server.autoStart | true | 启动时自动开启服务 |
| ntp.server | ntp.aliyun.com | NTP 服务器地址 |
| ntp.interval | 5 | NTP 同步间隔（分钟） |
| ntp.autoSyncEnabled | true | 是否启用自动同步 |
| heartbeat.interval | 30 | 心跳间隔（秒） |
| heartbeat.timeout | 90 | 超时时间（秒） |

---

## 7. 待定事项

- [ ] 界面主题配色
- [ ] 托盘图标功能（可选）