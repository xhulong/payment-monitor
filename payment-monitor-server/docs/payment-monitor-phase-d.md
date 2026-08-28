# 支付通知监控阶段 D 实施报告

> 完成日期：2026-07-17  
> 分支：`payment-monitor-phase-d`  
> 协议版本：1

## 1. 完成范围

阶段 D 在不改变 Android 设备 API 协议版本的前提下，补齐了管理、审核、统计和本地运维能力。

### 服务端

- Flyway V5：
  - 新增设备心跳历史表。
  - 设备增加最后上传、最后同步及待传/重试/拒绝队列统计。
  - 事件增加审核时间、审核人和审核备注。
  - 新增不可变事件审核历史表和查询索引。
- Android 心跳可选上报队列统计及最后同步时间，旧客户端仍可继续发送原心跳结构。
- 设备详情返回最近 100 条心跳，用于定位离线、积压和版本问题。
- 事件支持：
  - `RECEIVED / REVIEWED / IGNORED / MATCHED / CONFLICT` 状态展示。
  - 人工确认、修正方向/金额、忽略。
  - 完整审核历史。
  - 状态、关键词和金额范围筛选。
  - 最多 10,000 条 Excel 导出。
- 原始通知不再随列表和普通详情返回：
  - `payment:event:raw` 仅查看脱敏原文。
  - `payment:event:raw:full` 查看完整原文。
- 仪表盘增加今日收入金额、平台收入、解析失败率、平均/P95 同步延迟、待审核数量和小时趋势。

### Android

- 心跳上报：
  - `pendingCount`
  - `retryingCount`
  - `rejectedCount`
  - `lastSyncAt`
- 上报字段均为协议 1 的可选字段，不影响当前配对、HMAC、秒级收款上传和离线队列。

### 管理端

- 重做支付概览，展示金额、延迟、解析质量和小时趋势。
- 设备页增加队列、最后上传、最后同步、详情抽屉和心跳历史。
- 事件页增加审核、修正、忽略、状态筛选、审核历史、分级原文查看和 Excel 导出。

### 本地运维

- Docker 四容器增加日志轮转限制。
- 新增 PostgreSQL 备份、恢复、全栈健康检查和三端检查脚本。
- Android JVM 测试通过 ASCII Junction 执行，规避 Windows 非 ASCII 工程路径导致的 Gradle 测试类加载问题。

## 2. 验证结果

- 服务端支付模块：11 项测试通过。
- 管理端：
  - `pnpm test:payment`：2 项通过。
  - `vue-tsc --noEmit`：通过。
  - `pnpm lint`：通过。
  - 生产构建：通过。
- Android：
  - JVM 测试：15 项通过。
  - Debug/Release 构建：通过。
  - 红米设备 Debug APK 覆盖安装成功。
  - Debug 心跳实测已将队列统计写入服务端。
- Docker：
  - PostgreSQL、Redis、后端、管理端均为 healthy。
  - Flyway V5 已应用。
  - 管理端模块脚本 MIME 为 JavaScript。
- 备份：
  - 已生成并通过 `pg_restore -l` 校验的自定义格式备份。

## 3. 构建产物

- Debug APK：
  `D:\desktop\免签app\PaymentNotificationMonitor\app\build\outputs\apk\debug\app-debug.apk`
- Debug SHA-256：
  `B016D13FE59BC40C2D6A2ABC1C9F2EF1087E01E74AE340949B19806187CA8BA0`
- Release unsigned SHA-256：
  `D20AA23DC5A2355EFAF321E4F37C1AA71C233CC1E0C2F075D0CA4CF513AC370A`

## 4. 阶段 E 入口条件

- 微信和支付宝真实收款均已在红米设备成功解析并同步。
- 收款即时上传与 5 秒兜底保持不变。
- 事件审核和状态流转已经具备，可作为订单自动匹配、冲突和人工补单的基础。
- 下一阶段开始实现订单、动态金额、二维码资产、到账匹配、冲突处理与补单审计。
