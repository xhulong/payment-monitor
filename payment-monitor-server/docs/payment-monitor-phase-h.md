# 支付通知监控 Phase H 实施报告

实施日期：2026-07-17  
统一分支：`payment-monitor-phase-h`  
统一标签：`payment-monitor-phase-h-v1`  
设备协议：`protocolVersion=1`

## 1. 实施结论

Phase H 已完成管理后台首页支付运营化、Android 持续监听可靠性增强、Webhook 订阅与重放、商户 API 审计和支付对账。

本阶段仅使用本地开发 Docker 环境复验，没有执行生产发布脚本，也没有改造或发布生产 Compose。

## 2. 三端源码

| 端 | 路径 | 分支 |
| --- | --- | --- |
| 服务端 | `D:\desktop\免签app\PaymentMonitorServer` | `payment-monitor-phase-h` |
| 管理端 | `D:\desktop\免签app\PaymentMonitorAdmin` | `payment-monitor-phase-h` |
| Android | `D:\desktop\免签app\PaymentNotificationMonitor` | `payment-monitor-phase-h` |

## 3. 管理后台首页

原独立“监控概览”菜单已隐藏，支付监控和经营数据直接进入管理后台首页。

### 3.1 角色展示

| 角色 | 首页内容 |
| --- | --- |
| 平台超级管理员 | 全商户今日收款、订单、设备健康、平台异常、商户健康列表和同步延迟 |
| 支付商户管理员 | 当前商户今日收款、支付通知、未匹配收入、设备状态、Webhook、对账和业务快捷入口 |
| 无支付权限角色 | 普通欢迎页，不请求支付首页接口 |

支付首页接口：

```text
GET /payment/home-dashboard
```

前端使用 `payment:dashboard:view` 或超级管理员通配权限决定是否加载支付数据；超级管理员视图由服务端返回的 `superAdmin` 决定。

### 3.2 首页指标

- 今日支付通知数量。
- 今日已支付订单数量。
- 今日收入金额。
- 在线设备和异常设备。
- 未匹配收入、冲突订单和疑似重复事件。
- Webhook 积压和 DEAD 数量。
- 最近 24 小时商户 API 失败数量。
- 平均同步延迟和 P95 同步延迟。
- 24 小时收入趋势。
- 超级管理员的逐商户健康列表。
- 商户管理员的当日对账结果。

## 4. Android 持续运行可靠性

Android 版本：

```text
versionCode=4
versionName=1.3.0-dev
protocolVersion=1
parserVersion=2
```

已实现：

- 增加 `RECEIVE_BOOT_COMPLETED`。
- 开机完成后按原监听开关恢复前台服务。
- 应用更新完成后自动恢复前台服务。
- 应用启动时恢复已开启的监听状态。
- 移除旧版重启后自动关闭监听的 Boot Count 限制。
- Notification Listener 断开后延迟调用 `requestRebind()`。
- 前台服务循环检查 Listener 连接状态并自动重连。
- 常驻通知显示“已连接”或“正在自动重连”。
- 心跳上报监听开关、Listener、前台服务、通知使用权、电池优化和最近通知时间。
- 保存最近收到微信或支付宝通知的时间。
- 手机端显示后台保护状态、电池优化入口和红米后台运行提示。

设备健康字段：

```text
monitoringEnabled
listenerConnected
foregroundRunning
notificationAccessGranted
batteryOptimizationIgnored
lastNotificationAt
lastHealthIssue
```

## 5. Webhook 完善

Webhook 端点新增：

- 事件类型订阅。
- 微信、支付宝平台过滤。
- 测试回调。
- 人工重试原投递。
- 生成新 `deliveryId` 的人工重放。
- 重放来源和重放原因。

支持事件：

```text
payment.income.received
payment.order.paid
payment.order.expired
payment.order.cancelled
payment.order.conflict
webhook.test
```

重试与重放区别：

- 重试继续使用原 Outbox 和原 `deliveryId`。
- 重放创建新的 Outbox 和新的 `deliveryId`，保留来源投递 ID 和操作原因。

本地 Webhook E2E 已验证：

- 在线投递成功。
- Duplicate ACK 成功。
- 断网后自动重试成功。
- 后端重启后继续投递成功。
- HMAC 签名验证成功。

## 6. 商户 API 审计

商户 HMAC API 请求新增审计记录：

```text
GET /payment/merchant-api-audits/list
```

记录内容：

- 商户 ID。
- API Key ID。
- 请求方法和接口路径。
- 客户端 IP。
- HTTP 状态。
- 业务结果码。
- 是否成功。
- 请求耗时。
- 记录时间。

审计不保存：

- API 密钥。
- 完整签名。
- 请求正文。
- 支付通知原文。

管理端在商户 API Key 抽屉中显示最近调用审计。

## 7. 支付对账

接口：

```text
GET  /payment/reconciliation/latest
POST /payment/reconciliation/run
```

对账维度：

- 已支付订单数量和金额。
- 已匹配收入数量和金额。
- 未匹配收入数量和金额。
- 订单金额与已匹配收入金额差异。
- 冲突订单。
- 疑似重复事件。
- Webhook DEAD。

对账状态：

```text
BALANCED
ATTENTION_REQUIRED
```

支持：

- 商户管理员首页立即对账。
- 同一商户和业务日期幂等更新。
- 每日 UTC 定时任务按商户时区计算上一业务日。

管理接口联调时发现 `ATTENTION_REQUIRED` 超过 V9 的 `VARCHAR(16)`，已新增 Flyway V10 将状态字段扩展为 `VARCHAR(32)`。修复后手动对账和最新对账查询均返回成功。

## 8. 数据库迁移

### Flyway V9

`V9__payment_phase_h_operations.sql`：

- 扩展设备和心跳健康字段。
- 扩展 Webhook 订阅、平台过滤和重放字段。
- 新增商户 API 调用审计表。
- 新增支付对账结果表。
- 隐藏旧监控概览菜单。

### Flyway V10

`V10__payment_phase_h_reconciliation_status_width.sql`：

- 将 `pm_reconciliation_run.status` 从 `VARCHAR(16)` 扩展为 `VARCHAR(32)`。

本地开发数据库已成功迁移至版本 `10`。

## 9. 验证结果

### 9.1 服务端

```text
Tests:    31
Failures: 0
Errors:   0
Skipped:  0
```

管理 API 实际联调：

- 超级管理员支付首页：成功。
- 商户 API 审计列表：成功。
- Webhook 端点列表：成功。
- 默认商户手动对账：成功。
- 最新对账查询：成功。

### 9.2 管理端

已通过：

```text
pnpm test:payment
pnpm lint
pnpm exec vue-tsc --noEmit
pnpm build:prod
```

测试结果：

```text
Test files: 1 passed
Tests:      2 passed
```

当前本机 Node.js 为 `20.15.0`，低于 Vite 建议的 `20.19+`，但类型检查和生产构建均成功。后续建议切换到项目已准备的 Node 22 工具链。

管理端主模块 MIME：

```text
application/javascript
```

### 9.3 Android

```text
Tests:    15
Failures: 0
Errors:   0
Skipped:  0
```

已通过：

```text
testDebugUnitTest
assembleDebug
assembleRelease
```

真机：

```text
serial=f6zh89or49vorgin
model=M2006J10C
versionCode=4
versionName=1.3.0-dev
```

真机已确认：

- 通知监听使用权存在。
- Notification Listener 已连接。
- 前台服务正在运行。
- 应用更新后监听状态自动恢复。
- 心跳持续上报设备健康状态。
- 多行状态 Chip 不再横向裁切。

当前真机仍未忽略电池优化，因此后台保护显示异常并提供“关闭电池优化限制”入口；红米系统还建议手动开启自启动和锁定后台任务。

## 10. 本地开发环境

开发 Compose 服务：

```text
payment-monitor-postgres
payment-monitor-redis
payment-monitor-backend
payment-monitor-admin
```

四个服务均处于 healthy 状态：

```text
Backend: http://localhost:8080
Admin:   http://localhost:5173
```

本阶段没有执行 `Release-Production.ps1`。

## 11. 交付物

Debug APK：

```text
D:\desktop\免签app\PaymentNotificationMonitor\app\build\outputs\apk\debug\app-debug.apk
```

SHA-256：

```text
DDCA94406656C51ACF063CAB556D3C03606662C21D34EBFC8D709B719F9E1DD4
```

最新真机截图：

```text
D:\desktop\免签app\artifacts-phase-h-phone-latest.png
```

## 12. 后续建议

- 在红米系统中开启自启动、忽略电池优化并锁定后台任务后，执行 24 至 72 小时持续运行验收。
- 使用已知测试凭据分别登录商户管理员和无支付权限账号，补充三角色浏览器截图。
- 增加首页数据契约和角色模式的前端自动化测试。
- 下一阶段可继续实现 Webhook 事件级订阅策略、对账差异处理工作流和运营告警通知。
