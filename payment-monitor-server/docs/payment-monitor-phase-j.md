# Payment Monitor Phase J 实施报告

报告日期：2026-07-17

## 1. 结论

Phase J“支付核心可靠性与确认分层”已完成三端实现、构建、自动化测试、本地 Docker 联调和红米真机数据库验收。

当前订单确认链路为：

```text
收入通知
→ OBSERVED 交易
→ 自动匹配订单
→ PAID / NOTIFICATION
→ 人工确认
→ CONFIRMED / MANUAL
→ 内部对账
→ RECONCILED
```

`payment.order.paid` 仍表示通知级支付确认；需要更严格业务语义的商户应订阅 `payment.order.confirmed` 或 `payment.order.reconciled`。

## 2. 版本与分支

三端分支：

```text
payment-monitor-phase-j
```

Android：

```text
versionCode=6
versionName=1.5.0-dev
protocolVersion=1
parserVersion=2
Room version=3
```

## 3. 服务端完成内容

### 3.1 Flyway V11

新增并成功应用：

- `pm_payment_transaction`
- `pm_amount_slot_reservation`
- `pm_payment_approval`
- `pm_reconciliation_item`
- `pm_device_assignment`

同时扩展订单、支付事件、Webhook、对账运行和设备协议字段，并完成历史数据兼容回填。

### 3.2 支付交易与确认等级

- 有效收入事件创建 `OBSERVED/UNCONFIRMED` 交易。
- 自动匹配后进入 `MATCHED/NOTIFICATION`，订单兼容保持 `PAID`。
- 精确平台、精确金额的交易支持单人确认，升级为 `CONFIRMED/MANUAL`。
- 平台或金额不一致的交易禁止单人确认，必须进入双人审批。
- 双人强制补单审批完成后直接升级为 `MANUAL`，确认来源为 `DUAL_APPROVAL`。
- 内部对账无该订单相关未解决差异时升级为 `RECONCILED`。
- 撤销确认后交易进入 `REVERSED`，订单进入 `CONFLICT/UNCONFIRMED`。

### 3.3 金额槽位

- 槽位占用键为 `merchantId + platform + payableAmountMinor`。
- 每个原始金额最多提供 100 个分值。
- 支付、取消、过期后进入固定 10 分钟冷却。
- 迟到付款强制补单后，即使槽位原来已是 `COOLING` 或 `RELEASED`，也会重新开始完整冷却。
- 冲突、取消、过期等订单状态变化均递增订单版本。

### 3.4 审批和对账

- 强制金额或平台不一致补单、撤销确认使用双人审批。
- 申请人不能审批自己的申请。
- 已拒绝或取消的申请允许重新发起；待审批或已批准记录继续保持幂等。
- 审批执行时重新锁定并检查订单和事件，防止复核期间被其他订单抢占。
- 每次对账生成独立运行和不可覆盖的差异明细。
- 已覆盖未匹配收入、通知未人工确认、金额不一致、冲突订单、疑似重复、DEAD Webhook 和迟到付款。

### 3.5 Webhook 可靠事件负载

- 负载统一使用 `schemaVersion=2`，端点不再提供版本选择。
- 新增稳定 `eventId` 和独立 `deliveryId`。
- 同一业务事件在不同端点、重试和人工重放时保持相同 `eventId`。
- 新增请求头：
  - `X-Webhook-Event-Id`
  - `X-Webhook-Schema-Version`
- 新增事件：
  - `payment.transaction.observed`
  - `payment.order.confirmed`
  - `payment.order.reconciled`
  - `payment.order.confirmation_revoked`
- Flyway V15.5 将历史负载统一升级到 v2，并按业务聚合键重建稳定 `eventId`。

### 3.6 主备设备与设备序列

- 支持按商户和平台设置主设备及有优先级的备用设备。
- 主设备不健康时选择最低优先级编号的健康备用设备作为有效观察设备。
- 服务端接受可选 `deviceSequence`，并通过设备 ID 与设备序列防止重复入库。
- 旧协议客户端仍可继续使用协议版本 1。

## 4. 管理端完成内容

- 新增支付交易和金额槽位页面。
- 新增审批中心。
- 新增版本化对账中心。
- 订单页面展示交易、确认等级、槽位状态和待审批信息。
- Webhook 页面展示稳定 Event ID 和 Phase J 事件，不再暴露负载版本选择。
- 首页按角色展示通知确认、人工确认、已对账、待审批和差异指标。
- 设备页面支持主备角色和平台范围配置。
- 商户上下文和原有多商户隔离规则保持生效。

## 5. Android 完成内容

- Room V3 增加可空 `deviceSequence`。
- V1/V2 迁移到 V3 时，以稳定本地行 ID 回填历史序列。
- 新事件在数据库事务中写入并获得单调序列。
- 上传项目增加可选 `deviceSequence`。
- 配对和配置响应支持设备角色与平台范围。
- 同步页展示主设备、备用设备和平台范围。
- Debug 同毫秒样本可以指定相同毫秒时间，验证连续事件不被吞掉。
- Release 能力边界保持不变。

## 6. 可靠性收尾修复

- 单人确认前强制校验交易与订单的平台、金额完全一致。
- 双人强制补单按顺序生成：
  1. `payment.order.paid`
  2. `payment.order.confirmed`
- 审批复核时重新检查订单和事件是否已被其他记录匹配。
- 撤销确认后重新激活金额槽位。
- 迟到强制补单后重新开始十分钟冷却。
- 过期扫描不覆盖已经支付的订单。
- 重复内部对账保持幂等。
- 修复 Android 真机测试脚本的自定义 Instrumentation Runner。
- 本地环境初始化脚本默认使用 `localhost`，原文上传默认关闭。
- 运行时局域网地址继续只保存在忽略提交的 `.env.local` 或本地 Gradle 配置中。

## 7. 自动化测试结果

### 服务端

```text
支付模块测试：45
失败：0
错误：0
```

同一 Maven Reactor 中的公共模块测试另有 4 项通过。

新增重点覆盖：

- 金额不一致交易不能单人确认。
- 双人强制补单直接成为 `MANUAL`。
- 已拒绝审批可以重新申请。
- 迟到强制补单重新开始冷却。
- 重复对账幂等。
- Webhook 统一 v2、稳定 Event ID 和历史负载升级。
- 设备序列与重复事件处理。

### 管理端

```text
vue-tsc --noEmit：通过
lint：通过
payment Vitest：2/2 通过
production build：通过
```

本地 Docker 构建脚本使用工作区 Node 22 工具链完成管理端生产构建。

### Android

```text
JVM 测试：18/18 通过
Room V1/V2→V3 真机迁移：2/2 通过
Room 真机数据测试：6/6 通过
assembleDebug：通过
assembleRelease：通过
assembleDebugAndroidTest：通过
```

Release Manifest 验证：

- `usesCleartextTraffic=false`
- 不包含 `DebugFixtureReceiver`
- 保留开机恢复 Receiver

红米真机安装版本：

```text
versionCode=6
versionName=1.5.0-dev
```

## 8. Docker 与端到端验收

本地容器：

```text
payment-monitor-postgres   healthy
payment-monitor-redis      healthy
payment-monitor-backend    healthy
payment-monitor-admin      healthy
```

验证结果：

- Flyway 最新版本为 V11，执行成功。
- V11 五张核心表均存在。
- `PAYMENT_RAW_PAYLOAD_UPLOAD_ENABLED=false`。
- 管理端入口 HTTP 200。
- 管理端 ES Module MIME 为 `application/javascript`。
- 后端 8080 端口健康。

Phase J 全流程 E2E 已通过：

```text
创建订单
→ 设备 HMAC 上传收入事件
→ OBSERVED
→ PAID / NOTIFICATION
→ 单人精确确认为 MANUAL
→ 版本化内部对账
→ RECONCILED
→ Webhook 可靠事件投递
→ Webhook 人工重放保持 eventId、更新 deliveryId
```

本次证据：

```text
orderId=2078185608838152194
transactionId=2078185613057622018
matchedEventId=2078185612956958722
notificationStatus=NOTIFICATION
manualStatus=MANUAL
reconciledStatus=RECONCILED
acceptedCount=1
duplicateCount=1
replayPreservedEventId=true
```

证据目录：

```text
artifacts/phase-j/20260717-183030
```

该目录被 Git 忽略，不包含在源码提交中。

## 9. 输出物

Debug APK：

```text
D:\desktop\免签app\PaymentNotificationMonitor\app\build\outputs\apk\debug\app-debug.apk
```

大小：

```text
18,252,669 bytes
```

SHA-256：

```text
E715210ED170A95970433D7EDA0E373FCC1DD5F0303FC6A694F82A8EF9B0EEF2
```

可重复执行脚本：

```text
scripts/Start-LocalDockerStack.ps1
scripts/Test-LocalStackHealth.ps1
scripts/Invoke-AndroidDeviceE2E.ps1
scripts/Invoke-PhaseJConfirmationE2E.ps1
```

## 10. 固定边界与待补验收

- 未接入微信或支付宝官方支付 API、官方账单和最终资金确认。
- 未实现退款、余额、结算和双重记账。
- 真实第二台设备尚未准备，真实主备切换仍保留为待补验收；当前已完成单机和模拟健康状态逻辑。
- 当前 Docker 主机/容器业务时钟生成的商户业务日比本报告基准日期快一个自然日。E2E 状态转换和签名验证不依赖该日期值，但下一轮正式联调前应统一校准主机、容器和数据库时间源。
- 本阶段未发布到公网生产环境。
