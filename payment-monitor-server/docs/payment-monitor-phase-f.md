# 支付通知监控阶段 F 实施报告

> 完成日期：2026-07-17
> 分支：`payment-monitor-phase-f`
> 协议版本：1

## 1. 完成范围

阶段 F 在阶段 E 的订单 `PAID` 事务基础上，实现可靠 Webhook 支付回调。Webhook 网络状态不会改变已经落库的支付订单状态。

### 服务端

- Flyway V7 新增：
  - `pm_webhook_endpoint`
  - `pm_webhook_outbox`
  - `pm_webhook_delivery_log`
  - Webhook 管理菜单与权限。
- 订单自动匹配或人工补单成功后，在同一数据库事务内为所有启用端点写入 Outbox。
- 每个端点、事件类型和订单只生成一条 Outbox，设备重复上传不会重复发布支付回调。
- 固定回调事件：
  - `type=payment.order.paid`
  - 固定 `deliveryId`
  - 订单号、平台、原金额、应付金额、支付时间和来源支付事件。
- HMAC-SHA256 签名：
  ```text
  signature = HMAC(secret, timestamp + "." + exactBody)
  ```
- 固定请求头：
  - `X-Delivery-Id`
  - `X-Webhook-Timestamp`
  - `X-Webhook-Signature: v1=...`
- 投递策略：
  - 2xx 视为成功。
  - 408、429、5xx 和网络错误进入重试。
  - `Retry-After` 优先。
  - 指数退避最高 6 小时。
  - 超过最大次数进入 `DEAD`。
  - 禁止 HTTP 重定向。
  - 限制连接时间、请求时间和响应体大小。
  - 每次尝试写入独立投递日志。
  - 后端重启后重新领取超时的 `DELIVERING` 锁。
- Webhook 地址校验：
  - 只允许 HTTP/HTTPS。
  - 生产默认只允许 HTTPS。
  - 生产默认阻止 loopback、link-local、私网、IPv6 ULA 和 multicast。
  - 禁止 userinfo、fragment、0 端口和非法端口。
  - 本地 Docker 通过显式配置允许 HTTP 和私网目标。
- Webhook 密钥使用现有主密钥以 AES-256-GCM 加密保存，管理端只在创建或轮换时返回一次明文。
- 管理 API 支持：
  - 端点列表、详情、新增、修改。
  - 密钥轮换。
  - Outbox 列表、详情和人工重试。

### 管理端

- 新增“Webhook”页面：
  - 端点查询、新增、编辑和启停。
  - 创建或轮换后一次性展示密钥并支持复制。
  - Outbox 状态、端点、订单和 deliveryId 查询。
  - 5 秒自动刷新。
  - 投递详情、响应摘要、错误和完整尝试日志。
  - `DEAD`、`RETRYING` 等任务人工重新入队。
- 页面明确展示签名公式和接收方按 `deliveryId` 幂等处理的要求。

### 本地联调工具

- `scripts/Invoke-PhaseFWebhookE2E.ps1`
  - 自动创建本地测试端点和订单。
  - 通过设备 HMAC 协议上传收入事件。
  - 验证在线投递、重复 ACK、断网重试、恢复投递和后端重启锁恢复。
- `scripts/webhook_receiver.py`
  - 本地 HTTP Receiver。
  - 校验精确请求体 HMAC。
  - 将不含密钥的结果写入 Git 忽略的 JSONL。
- `scripts/payment_secret_cipher.py`
  - 为本地 E2E 生成与服务端一致的 AES-GCM 密文。

## 2. 验证结果

- 服务端支付模块：20 项测试通过。
  - URL/SSRF 校验。
  - 精确 UTF-8 请求体 HMAC。
  - 2xx 成功投递。
  - 429 `Retry-After`。
  - 网络失败重试。
  - 重试保持相同 `deliveryId` 和负载。
  - Outbox 主键、订单匹配和回调入队。
- 管理端：
  - `vue-tsc --noEmit`：通过。
  - `pnpm lint`：通过。
  - `pnpm test:payment`：2 项通过。
  - 生产构建：通过。
- Docker：
  - PostgreSQL、Redis、后端和管理端均为 healthy。
  - Flyway V7 已应用。
  - 管理端模块脚本 MIME 为 `application/javascript`。
- 阶段 F 端到端验证：
  - 在线订单：事件 accepted，重复上传 duplicate，订单保持 `PAID`，Webhook `DELIVERED`。
  - 断网订单：订单先变为 `PAID`，Webhook 独立进入 `RETRYING`；Receiver 恢复后第二次尝试送达。
  - 重启恢复：Outbox 被模拟为超时 `DELIVERING` 锁，后端容器重启后重新领取并送达。
  - 三个接收记录签名全部校验成功。
  - 每个订单和端点只产生一条 Outbox。
- Android：
  - 15 项 JVM 测试通过。
  - Debug/Release 构建通过。

## 3. 构建产物

- Debug APK：
  `D:\desktop\免签app\PaymentNotificationMonitor\app\build\outputs\apk\debug\app-debug.apk`
- Debug SHA-256：
  `B016D13FE59BC40C2D6A2ABC1C9F2EF1087E01E74AE340949B19806187CA8BA0`
- Release unsigned SHA-256：
  `D20AA23DC5A2355EFAF321E4F37C1AA71C233CC1E0C2F075D0CA4CF513AC370A`

## 4. 固定边界

- 当前仍为单默认商户和本地 Docker 开发部署。
- 未增加公网 TLS、DNS 固定解析、生产备份策略、Prometheus 或 CI/CD。
- Android 设备协议继续使用版本 1，不需要阶段 F 客户端业务改动。
- Webhook 接收方必须校验时间戳、签名并按 `deliveryId` 幂等。
