# 支付通知监控阶段 A+B 实施报告

实施日期：2026-07-16

## 1. 交付目录

```text
Android:  D:\desktop\免签app\PaymentNotificationMonitor
服务端:   D:\desktop\免签app\PaymentMonitorServer
管理端:   D:\desktop\免签app\PaymentMonitorAdmin
```

## 2. 已完成范围

### 服务端

- 保留 RuoYi-Vue-Plus 6.X 全部现有模块，新增 `ruoyi-payment`。
- 设备 API 使用统一成功/错误响应、UTC ISO-8601 时间和协议版本 1。
- 实现 HMAC-SHA256 精确请求体签名、时间窗口、Nonce 防重放和凭据版本校验。
- 实现一次性配对码、设备禁用、凭据撤销、重新配对和密钥版本递增。
- 实现心跳、运行配置、支付事件批量上传、逐条 ACK 和幂等去重。
- Flyway V1～V4 已执行；V4 保存：
  - `event_time_ms`
  - `client_received_at/client_received_at_ms`
  - `client_sent_at/client_sent_at_ms`
  - 服务端 `received_at`
- 收款事件按毫秒时间线稳定排序。
- 首次插入收入事件时发布 `PaymentIncomeReceivedEvent`，duplicate 不重复发布。
- 兼容旧协议 1 客户端：缺少毫秒字段时从旧时间字段推导。
- 通知原文上传默认关闭；关闭时服务端丢弃客户端提交的 `rawPayload`。
- 设备 API、Web 参数和 MyBatis 日志不输出原文、签名、密钥或 SQL 参数。

### 管理端

- 新增支付看板、设备管理、配对二维码、事件列表和事件详情。
- 配对二维码固定包含 `schema`、`serverUrl` 和 8 位 `pairingCode`。
- 时间查询按 UTC 发送，页面按浏览器本地时区展示。
- 支持设备启用、禁用和凭据撤销。
- 事件详情显示通知时间、客户端收到时间、客户端发送时间、服务端收到时间和同步耗时。
- 修复生产资源路径和 Nginx 静态资源回退，JavaScript 返回 `application/javascript`。
- `pnpm test:payment` 使用独立 Vitest 配置，不再依赖生产 Vite 环境变量。

### Android

- 监听微信 `com.tencent.mm` 和支付宝 `com.eg.android.AlipayGphone` 支付候选通知。
- Room V2 保存上传队列；未同步记录不受 500 条终态历史限制。
- 支持扫码和手动配对、Android Keystore、HMAC、时钟校准、心跳和 WorkManager。
- 收款入库后立即同步，并保留 5 秒 WorkManager 兜底。
- 支出和方向待确认事件默认延迟 60 秒同步。
- 断网恢复时优先加速因网络错误等待重试的收款事件。
- 上传事件包含 `eventTimeMs`、`clientReceivedAtMs` 和 `clientSentAtMs`。
- Debug 四类样本与真实通知共用解析、Room 和上传流水线。
- Release 禁止明文 HTTP，且不包含 Debug Receiver。

## 3. 联调结果

### Pixel API 34

- 配对、四类样本、收款即时上传、支出延迟上传均通过。
- 飞行模式产生事件后，恢复网络可自动补传。
- 设备禁用后进入 `REPAIR_REQUIRED`，重新配对后凭据版本递增。
- 前台监听服务启动和停止通过。

### 红米真机

```text
ADB serial:   f6zh89or49vorgin
设备:         Xiaomi M2006J10C
Android:      12 / API 31
服务地址:     http://<LAN_IP>:8080
微信版本:     8.0.76
支付宝版本:   12.12.6.8000
```

- 通知监听服务和前台监听服务均已运行。
- 修复旧 APK 缺少毫秒字段导致的 `VALIDATION_FAILED`。
- Debug 收款样本端到端实测约 891 ms。
- 微信、支付宝均已安装，可进入阶段 C 真实通知采集。

## 4. 自动化验证

### Android

- JVM：11 项通过。
  - 通知解析：9 项。
  - UTC 毫秒格式：2 项。
- Instrumentation 现有套件：12 项。
  - Room V1→V2：1 项。
  - Room 数据策略：4 项。
  - 设备端到端：7 项。
- `testDebugUnitTest`、`assembleDebug`、`assembleRelease`、
  `assembleDebugAndroidTest` 构建成功。
- Release 合并 Manifest：
  - `usesCleartextTraffic=false`
  - 无 `DebugFixtureReceiver`

### 服务端

- 支付安全、设备和事件服务测试：11 项通过。
- Web 参数日志脱敏：2 项通过。
- MyBatis SQL 参数日志脱敏：2 项通过。

### 管理端

- 配对二维码 JSON：2 项通过。
- Oxlint、`vue-tsc --noEmit`、生产构建通过。
- `pnpm test:payment` 可直接执行。

### Docker

以下容器均为 `healthy`：

```text
payment-monitor-postgres  5433
payment-monitor-redis     6380
payment-monitor-backend   8080
payment-monitor-admin     5173
```

后端运行环境确认：

```text
PAYMENT_RAW_PAYLOAD_UPLOAD_ENABLED=false
```

## 5. APK 产物

```text
Debug:
D:\desktop\免签app\PaymentNotificationMonitor\app\build\outputs\apk\debug\app-debug.apk
SHA-256: E3155D4C38634A83C19CEFEF6FA29E9D01A537C328F28BBFCFC85E3A6385BDCC

Release unsigned:
D:\desktop\免签app\PaymentNotificationMonitor\app\build\outputs\apk\release\app-release-unsigned.apk
SHA-256: 3F866FFC505B5201E92ADA90009D4632FC92DC67FB16F715E667CFDD01343A6A
```

## 6. 阶段边界

- 阶段 A+B 已完成通知监听、设备安全、离线队列、秒级收款上传和管理端查询闭环。
- 真实微信店员/商业收款、支付宝店员通通知样本属于阶段 C。
- `PaymentIncomeReceivedEvent` 目前是内部观察事件，不是可靠支付回调。
- 订单、动态金额、二维码和事件匹配属于阶段 E。
- Outbox、可靠 Webhook、签名和重试属于阶段 F。
