# 易支付兼容接入自动化验收报告

验收日期：2026-07-21

## 分支与基线

### 服务端

- 仓库：`payment-monitor-server`
- 功能分支：`feature/easypay-classic-v1`
- `main` 基线：`5628d36d6638e3273f5709fe793fa68cf6b573e8`

### 管理端

- 仓库：`payment-monitor-admin`
- 功能分支：`feature/easypay-classic-v1`
- `main` 基线：`5cc8ebbe6020e91e2f0f42608838f46b0175a54a`

Android 未创建功能分支，协议和版本号均未修改。

## 已实现范围

- `EPAY_CLASSIC_V1` 三组公开兼容路径。
- 支付宝、微信的页面支付、API 下单和订单查询。
- MD5 UTF-8 经典签名、重复安全参数拒绝和金额精确转分。
- 复用内部动态金额订单、二维码、设备健康和通知匹配能力。
- 同外部订单幂等、参数冲突和并发下单保护。
- 通知匹配、人工确认、对账确认三种回调策略。
- GET、POST Form 异步通知、严格 `success` ACK、重试、DEAD 和人工重放。
- 同步支付页、状态轮询和服务端签名回跳。
- 独立业务密钥加密、密钥轮换、历史订单密钥版本快照。
- 回调域名白名单、HTTPS、SSRF、DNS 二次解析、禁止重定向和日志脱敏。
- 管理端“支付接入”页面：
  - 接入应用。
  - 支付路由。
  - 外部订单。
  - 回调记录。
- 创建、编辑、启停、密钥、路由、重试和重放均接入 MFA Step-Up。

## 自动化结果

### 服务端

- 开发测试：`117` 个通过，`0` 失败。
- 易支付定向测试：`26` 个通过，`0` 失败。
- Flyway Testcontainers：
  - 空库升级到 `V15.9`。
  - `V1` 升级到 `V15.9`。
  - 共 `2` 个通过，`0` 失败。
- PostgreSQL 并发锁映射回归测试：`1` 个通过，`0` 失败。
- Java 21 生产 JAR 构建成功：
  - `ruoyi-admin/target/ruoyi-admin.jar`

### 管理端

- TypeScript 类型检查通过。
- OXLint 通过。
- Vitest：`28` 个通过，`0` 失败。
- Playwright：`8` 个通过，`0` 失败。
- Vite 生产构建成功。
- 构建产物未发现：
  - `PAYMENT_INTEGRATION_MASTER_KEY`
  - `secretCiphertext`
  - `encryptionKeyId`
  - 本地或会话存储中的明文易支付 Key

## 独立 HTTP 闭环

已使用独立 Docker 环境完成协议闭环，测试环境包含独立 PostgreSQL、Redis、MinIO、Backend 和模拟回调服务，不访问或清理当前生产数据。

闭环结果：

- `POST /mapi.php` 使用经典 MD5 签名创建订单成功。
- `GET /pay/api.php?act=order` 在支付前返回 `status=0`。
- 易支付收银台页面和动态金额二维码可正常访问。
- 模拟到账通知确认后，订单查询返回 `status=1`。
- Callback Worker 投递状态为 `DELIVERED`。
- 模拟回调服务返回 HTTP `200` 和严格文本 `success`，服务端记录 `strict_acknowledged=true`。
- 异步通知包含 `TRADE_SUCCESS`、透传参数和有效 MD5 签名。
- 同步回跳返回 `302`，回跳参数和服务端签名验证通过。

闭环首次执行发现 `ExternalOrderBindingMapper.lockExternalOrder` 将 PostgreSQL `void` 结果映射到 Java `void` 时触发 MyBatis 构造器错误。现已改为返回固定标量值，并增加真实 PostgreSQL 容器回归测试。

## 真实客户端兼容回归

已使用 V2Board 仓库的原始 `app/Payments/EPay.php` 客户端进行兼容回归，客户端代码基线 Commit：

```text
0ca47622a50116d0ddd7ffb316b157afb57d25e8
```

该客户端的页面支付请求不发送 `type`。为保持经典客户端兼容，同时避免多支付方式下静默选错路由，服务端规则调整为：

- 请求明确提供 `type` 时，继续只接受 `alipay` 或 `wxpay`。
- 请求未提供 `type`，且应用只配置一种启用的支付类型时，从支付路由安全推导。
- 同一种支付类型配置多个优先级路由时，仍可正常推导。
- 应用配置多种支付类型时，明确拒绝并要求客户端传入 `type`。
- 应用没有启用路由时，明确返回配置错误。
- 签名始终按客户端原始请求参数校验，不将服务端推导的 `type` 注入验签参数。

真实客户端结果：

- V2Board 原始客户端生成的请求确认不包含 `type`。
- `GET /submit.php` 返回 `302` 并进入易支付收银台。
- 单一微信支付类型成功推导为 `wxpay`。
- 相同客户端请求重复提交返回同一支付地址。
- 异步通知达到严格 `success` ACK，状态为 `DELIVERED`。
- V2Board 原始 `notify` 方法成功验证服务端回调签名，并正确返回外部订单号和网关订单号。

## Git 与发布门禁

- 服务端和管理端 `main` 均没有易支付提交。
- 两端功能分支已推送到 `origin/feature/easypay-classic-v1`。
- 未合并 `main`。
- 未删除功能分支。
- 未创建 Tag。
- 未部署生产环境。

## 仍需完成的验收

以下项目需要在合并前继续执行：

1. 人工确认管理端 MFA 弹窗、一次性 Key 保存提示和路由编辑体验。

上述人工验收完成前，合并门禁保持关闭。
