# App端指令处理设计文档

**版本**: 1.0
**日期**: 2026-04-17
**状态**: 草稿
**子设计编号**: 5/6

---

## 1. 概述

本文档描述 Android 端指令处理模块的设计方案。该模块负责处理 PC 发来的 SEARCH、EXECUTE、GIFT 等指令，实现按钮查找、点击执行、送礼流程等功能。

---

## 2. 技术架构

| 组件 | 技术选型 | 理由 |
|------|----------|------|
| 处理模式 | 异步处理 | 后台线程，不阻塞通信 |
| 查找方式 | 立即返回 | 找到或超时后立即返回结果 |
| 位置缓存 | 内存缓存 | searchId → 位置映射 |
| 点击执行 | 复用现有服务 | AccessibilityClickService |

---

## 3. 核心类设计

### 3.1 LanCommandHandler (指令处理器)

```kotlin
class LanCommandHandler(
    private val buttonFinder: ButtonFinder,
    private val clickService: AccessibilityClickService,
    private val positionCache: ButtonPositionCache
) {
    // 处理 SEARCH 指令
    suspend fun handleSearch(device: DeviceConnection, payload: JsonObject): SearchResult

    // 处理 EXECUTE 指令
    suspend fun handleExecute(device: DeviceConnection, payload: JsonObject): ExecuteResult

    // 处理 GIFT 指令
    suspend fun handleGift(device: DeviceConnection, payload: JsonObject): GiftResult
}
```

### 3.2 消息类型定义

```kotlin
// 接收的消息类型
enum class CommandType {
    SEARCH,
    EXECUTE,
    GIFT,
    DISCONNECT
}

// 发送的消息类型
enum class ResponseType {
    REGISTER_ACK,
    HEARTBEAT_ACK,
    SEARCH_RESULT,
    EXECUTE_RESULT,
    GIFT_RESULT
}
```

### 3.3 按钮位置缓存

```kotlin
class ButtonPositionCache(
    private val maxSize: Int = 100  // 最大缓存数量
) {
    private val cache = ConcurrentHashMap<String, Point>()

    fun put(searchId: String, position: Point) {
        // 超过最大容量时移除最老的条目
        if (cache.size >= maxSize) {
            val oldestKey = cache.keys().nextElement()
            cache.remove(oldestKey)
        }
        cache[searchId] = position
    }

    fun get(searchId: String): Point? {
        return cache[searchId]
    }

    fun remove(searchId: String) {
        cache.remove(searchId)
    }

    fun clear() {
        cache.clear()
    }

    fun contains(searchId: String): Boolean {
        return cache.containsKey(searchId)
    }
}
```

---

## 4. 指令处理流程

### 4.1 SEARCH 指令处理

```
PC → SEARCH
    ↓
解析 payload:
  - searchId: String
  - rule: SearchRule
    - keywords: List<String>
    - text: String
    - resourceId: String
    - contentDescription: String
    - className: String
    ↓
调用 ButtonFinder.findTargetButton(rule)
    ↓
找到按钮？
    ↓ 是
缓存位置：positionCache.put(searchId, position)
返回：SEARCH_RESULT { searchId, found: true, position: {x, y} }
    ↓ 否
返回：SEARCH_RESULT { searchId, found: false, reason: "未找到匹配元素" }
```

### 4.2 EXECUTE 指令处理

```
PC → EXECUTE
    ↓
解析 payload:
  - searchId: String
    ↓
从缓存获取位置：positionCache.get(searchId)
    ↓
位置存在？
    ↓ 是
调用 AccessibilityClickService.performGlobalClick(x, y)
    ↓
点击成功？
    ↓ 是
返回：EXECUTE_RESULT { searchId, success: true, clickTime: timestamp }
    ↓ 否
返回：EXECUTE_RESULT { searchId, success: false, message: "点击失败" }
    ↓ 否
返回：EXECUTE_RESULT { searchId, success: false, message: "按钮位置已失效" }
```

### 4.3 GIFT 指令处理

```
PC → GIFT
    ↓
解析 payload:
  - giftId: String
  - targetUrl: String
  - buttonRule: SearchRule
  - config: GiftConfig
    - retryCount: Int
    - retryInterval: Long
    ↓
打开目标 URL（通过AccessibilityService或Intent）
    ↓
等待页面加载
    ↓
根据 buttonRule 查找按钮 (SEARCH 逻辑)
    ↓
执行点击 (EXECUTE 逻辑)
    ↓
返回：GIFT_RESULT { giftId, success, message }
```

---

## 5. 超时配置

| 指令类型 | 超时时间 | 说明 |
|----------|----------|------|
| SEARCH | 10秒 | 按钮查找超时 |
| EXECUTE | 5秒 | 点击执行超时 |
| GIFT | 30秒 | 完整送礼流程超时 |

---

## 6. 与现有功能集成

| 现有组件 | 集成方式 |
|----------|----------|
| ButtonFinder | 复用 findTargetButton() 方法 |
| AccessibilityClickService | 复用 performGlobalClick() 方法 |
| TimeService | 保持独立，用于本地定时点击 |
| SharedPreferences | 使用现有 PREFS_NAME 存储配置 |
| GiftClickHistory | 送礼结果记录到现有历史表 |

---

## 7. 错误处理

| 错误场景 | 处理方式 |
|----------|----------|
| JSON 解析失败 | 返回错误响应，记录日志 |
| 规则无效 | 返回错误响应，说明原因 |
| 按钮未找到 | 返回 found: false |
| 点击失败 | 返回 success: false，说明原因 |
| 缓存失效 | 返回位置已失效，重新查找 |

---

## 8. 待定事项

- [ ] 指令执行日志记录
- [ ] 送礼流程页面跳转处理