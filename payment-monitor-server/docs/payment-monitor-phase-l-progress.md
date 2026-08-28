# 支付通知监控 Phase L 进度与缺口

盘点日期：2026 年 7 月 20 日
当前阶段：`payment-monitor-phase-l`
结论：Phase L 已完成主要代码骨架和多数安全/发布加固，但尚未达到阶段验收和打 Tag 条件。

## 1. 分支、标签与版本

已完成：

- 服务端、管理端、Android 均存在 `payment-monitor-phase-k-v2`。
- 三端均已创建本地分支 `payment-monitor-phase-l`。
- Android 已设置：
  - `applicationId=com.xhulong.paymentmonitor`
  - `versionCode=8`
  - `versionName=1.7.0-rc1`
  - `compileSdk=35`
  - `targetSdk=35`
  - `parserVersion=3`
  - `protocolVersion=1`
- Flyway 已包含 V14、V15、V15.1、V15.2、V15.3、V15.4 和 V15.5。

未完成：

- 三端 Phase L 分支尚未同步到远端。
- 三端 `main` 尚未同步 Phase L。
- `payment-monitor-phase-l-v1` 尚未创建。

## 2. 已完成的 Phase L 能力

### 2.1 账号与安全

- 可信网关客户端 IP 解析已统一用于注册、设备和商户 API。
- TOTP 替换已使用 pending secret，旧密钥在新密钥验证成功前保持有效。
- 恢复码可用于登录挑战，并在数据库行锁事务中单次消费。
- 密码重置和邮箱变更接口已实现。
- Access Token 仅保存在前端内存。
- Refresh Token 已改为 HttpOnly、SameSite=Strict Cookie，生产模板启用 Secure。
- 已实现 `/auth/refresh`、`/auth/logout-all` 和 `/auth/mfa/verify`。
- Refresh Session 已通过 Flyway V15.2 持久化；数据库仅保存 Token/User-Agent SHA-256，支持会话族、单次轮换、过期状态和旧 Token 重放检测。
- 登录、MFA 登录、刷新、退出、全部退出、密码重置、个人改密、管理员改密和邮箱变更已接入持久化 Refresh Session 撤销。
- Refresh Token 并发轮换时仅允许一个请求成功；旧 Token 重放会撤销会话族和该用户全部活动 Refresh Session。
- 密码重置和邮箱变更验证码状态已通过 Flyway V15.3 持久化，不再将验证码明文存入 Redis。
- 账号恢复验证码使用独立 Pepper 执行 HMAC-SHA256，绑定挑战 ID 和挑战类型；支持替换旧挑战、过期、尝试次数锁定和原子单次消费。
- 验证失败计数使用独立事务持久化；正确验证码只生成短暂校验授权，最终消费与密码/邮箱变更处于同一事务。
- Step-Up Token 已改为按用户建立带 TTL 的 Redis 索引，令牌值绑定用户、登录 Session 和操作类型，并通过分布式锁保证单次消费。
- 全部退出、密码重置、个人改密、管理员改密和邮箱变更会显式批量删除该用户的 Step-Up Token。
- 邮件发送已改为 Flyway V15.4 持久化 Outbox；收件人、标题和正文统一保存于 AES-256-GCM 加密载荷中。
- 密码重置、邮箱变更、注册验证码、邮箱变更通知和商户邀请均已接入事务 Outbox，不再由支付模块业务线程直接调用 SMTP。
- Mail Worker 支持 `PENDING/SENDING/RETRYING/SENT/DEAD/CANCELLED`、指数退避、过期阻断、锁超时回收和 `FOR UPDATE SKIP LOCKED` 并发领取。
- 已增加 Mail Outbox backlog/DEAD 指标和生产告警规则。
- 超级管理员、平台审核员和 OWNER、ADMIN、FINANCE、DEVELOPER 登录已进入强制 MFA 流程。
- 未启用 MFA 的必选角色只获得受限初始化会话；完成初始化后要求重新登录。
- 旧版 API 应用层加密协议及浏览器私钥依赖已完整删除，v2 为唯一实现，HTTPS 为基础传输加密边界。
- 已新增独立 `api-crypto-v2`：RSA-OAEP-256、AES-256-GCM、HKDF-SHA256、JWKS、Redis 防重放和接口级 `@ApiCryptoV2`。
- 登录、MFA、密码找回、邮箱变更、个人改密和管理员重置密码已标记为请求/响应加密接口。
- 管理端已使用 WebCrypto 完成 v2 请求和响应处理，临时主密钥只保存在请求内存上下文。
- 服务端已增加启动期注解组合校验、严格版本头/媒体类型校验、只读密钥文件挂载和 RSA 公私钥匹配检查。

### 2.2 Android 与 APK 供应链

- 服务端上传时解析并校验包名、版本号、版本名、签名证书和 APK SHA-256。
- 生产模板支持固定允许的签名证书 SHA-256。
- Android 下载后校验文件哈希、包名和签名证书。
- 新配对请求包含 `appVersionCode`，服务端可执行最低版本校验。
- 更新模式已支持 `OPTIONAL`、`REQUIRED` 和 `SECURITY_BLOCK`。
- Release 已启用 R8 和资源压缩，并补充主要框架保留规则。
- Release Manifest 禁用明文流量，不包含 Debug Receiver。
- 已建立本地 Release 签名流程和 API 35 Managed Device CI 配置。

### 2.3 Webhook、备份与监控

- Webhook 负载已统一为 schema v2，端点不再选择版本；历史负载通过 V15.5 补齐稳定 Event ID 并移除 `payload_version` 字段。
- Webhook 在 URL 解析和实际连接 DNS 阶段拒绝非公网目标，继续禁用重定向。
- Webhook 响应摘录已增加手机号、Token、Cookie、Authorization 和密钥脱敏。
- DEAD 任务已增加处理人、处理时间和解决状态。
- MinIO 备份已改为 Bucket 镜像，不再压缩运行中的数据目录。
- 单机生产模板已加入 Alertmanager 和支付相关告警规则。
- PostgreSQL、Redis、MinIO Testcontainers 基础设施启动检查已加入 CI 分组。

## 3. 验收状态与剩余项目

### 3.1 数据迁移与账号恢复模型

- 已增加 PostgreSQL 17 Testcontainers 自动化迁移测试：
  - 空支付业务库明确迁移到 V15.3，再连续迁移到 V15.4 和 V15.5。
  - 带代表性商户、设备、支付事件及旧 Webhook 负载的数据升级到 V15.5。
  - 验证历史数据、毫秒时间线、交易回填、邮件 Outbox 和 Webhook v2 统一结构。
  - 验证 V15.3 和 V15.5 各边界再次执行均为 0 个待执行迁移。

### 3.2 支付解析样本

当前自动化测试资源为：

```text
baseline.json：8 条
real-20260717.json：5 条
phase-l-desensitized.json：60 条
合计：73 条
```

- 新增样本按以下固定分组执行统计门禁：
  - 微信：25 条。
  - 支付宝：25 条。
  - 同金额、同毫秒、通知更新、金额缺失、方向歧义及负样本：10 条。
- `phase-l-desensitized.json` 已独立执行严格门禁：总数 60、全部 reviewed、微信 25、支付宝 25、边界/负样本 10，且至少包含 4 条不应匹配的负样本。
- 自动化测试逐条验证平台、方向、金额和解析状态，并验证同金额/同毫秒事件及通知更新不会产生相同指纹。
- 退款到账通知已加入负样本并修复误报，当前脱敏回归集负样本误报为 0。
- 本轮新增 60 条为脱敏结构化回归样本，不包含个人原始通知；Phase L 最终验收若严格要求 60 条均来自真实设备捕获，仍需由真机测试持续补充来源证据。

### 3.3 自动化测试

- 服务端 PostgreSQL Testcontainers 已覆盖注册、商户申请提交、审核认领、批准开通和角色切换。
- 订单集成测试已覆盖动态金额槽位、幂等创建、同商户金额隔离和跨商户二维码隔离。
- Webhook 集成测试已覆盖业务事件幂等入队、`PENDING/RETRYING/DELIVERING/DEAD` 领取流程、人工解决和多商户隔离。
- APK 发布集成测试已覆盖包名、版本、签名证书、降级拒绝、对象上传、草稿发布和数据库持久化。
- 服务端 CI 已改为通过 `test.groups` 显式执行 `dev | integration`，修复原 `-Dgroups` 参数被 Maven 插件固定配置覆盖、可能出现 0 测试仍成功的问题。
- TOTP 和可信代理 IP 回归已补充 `dev` 标签，确保进入 CI 自动执行。
- `api-crypto-v2` 已有密码学、Envelope、启动期注解、本地 Secret 密钥、密钥轮换和 Redis 并发防重放 Testcontainers 回归。
- MFA 当前已有单元回归和 Redis Step-Up Testcontainers；完整登录 MFA 的 PostgreSQL 端到端流程仍可继续补充。
- 管理端 Playwright 当前为 3 条认证/注册测试，其中登录用例已验证 v2 请求与响应加密互操作；仍未覆盖审核、开通、订单、审批、对账、Webhook 和 App 发布。
- Android Managed Device 配置已加入，但真机付款和 72 小时连续运行仍是人工门禁。

### 3.4 发布与恢复

- 本机已有 Release keystore 和签名配置，但密钥离线备份、访问控制和证书指纹交接记录仍需人工验收。
- `api-crypto-v2` 当前内置 `local-secret` Provider 和只读 Secret 文件挂载；KMS、HSM 或 Vault Transit 远程解包 Provider 尚未实现。
- 最新备份脚本修改后，尚缺一次有证据的 PostgreSQL、Redis、MinIO 全新环境完整恢复。
- 外部对象存储加密复制和发布失败回滚演练属于 Phase M，尚未执行。

## 4. 本次继续完善的提交

```text
18246ae4e feat(security): add api-crypto-v2 server protocol
751d81058 refactor(security): remove legacy api crypto protocol
fb01f84d3 feat(security): isolate api-crypto-v2 replay guard
b2e65648a test(security): cover replay concurrency and key rotation
ad1572165 feat(auth): persist and rotate refresh sessions
5cbcafdec test(auth): cover persistent refresh session lifecycle
721e5ac19 feat(auth): persist account recovery challenges
2d5cfa5ea test(auth): cover persistent account recovery challenges
38103bcad feat(auth): index and revoke step-up tokens
9a25d7be7 test(auth): verify step-up token lifecycle in Redis
43f30aea0 feat(mail): add encrypted transactional outbox
29e61568e test(mail): cover outbox encryption retry and claiming
6009280fb test(migration): verify empty and v1 flyway upgrades
2ea21bdfa test(integration): cover registration and merchant review
c54d81fbb test(integration): cover orders and webhook delivery
8b0902c47 test(integration): cover apk publication
452309572 test(migration): verify explicit v15.3 upgrade boundary
edda441e2 ci(server): execute dev and integration test tags
ec74ee266 test(security): include totp and trusted ip in ci
```

Android：

```text
d46eba7 fix(parser): ignore refund arrival notifications
7eec48e test(parser): expand desensitized payment fixtures
9baba96 test(parser): enforce phase l fixture corpus gates
```

## 5. 本次验证结果

```text
服务端单元测试：53 passed
api-crypto-v2 单元回归：14 passed
api-crypto-v2 Redis Testcontainers：2 passed
api-crypto-v2 CORS 回归：2 passed
认证传输契约测试：1 passed
Refresh Session 单元回归：6 passed
Flyway V15.2 PostgreSQL 17 建表烟测：passed
账号恢复挑战单元回归：8 passed
Flyway V15.3 PostgreSQL 17 建表烟测：passed
Step-Up Token Redis Testcontainers：4 passed
Mail Outbox 单元回归：5 passed
Mail Outbox PostgreSQL 17 Testcontainers：1 passed
空库及 V1→V15.3→V15.5 Flyway PostgreSQL 17 Testcontainers：2 passed
注册与商户审核 PostgreSQL 17 Testcontainers：1 passed
订单与 Webhook PostgreSQL 17 Testcontainers：1 passed
APK 发布 PostgreSQL 17 Testcontainers：1 passed
服务端 integration 分组汇总：11 passed
服务端 ruoyi-payment dev + integration CI 分组：83 passed
Android JVM 单元回归：22 passed
支付解析资源：73 条，其中新增微信 25、支付宝 25、边界/负样本 10
生产/端口/本地 Docker Compose 配置校验：passed
Testcontainers 基础设施测试：1 passed
管理端 vue-tsc：passed
管理端 lint：passed
管理端 development build：passed
管理端 production build：passed
管理端 Playwright：3 passed
本地 Docker JWKS/加密响应/重放/明文拒绝烟测：passed
生产 Docker Compose config：passed
```

## 6. 下一步固定顺序

1. 用真机持续补充真实脱敏通知来源证据，逐步替换或补强当前结构化回归样本。
2. 扩展 Playwright 到审核、开通、订单、审批、对账、Webhook 和 App 发布。
3. 补充完整登录 MFA 的 PostgreSQL 端到端流程。
4. 执行并留存 PostgreSQL、Redis、MinIO 全新环境恢复证据。
5. 完成正式签名密钥离线交接、真实 SMTP、真机安装与真实付款门禁。
6. 全部通过后再同步三端 `main`、阶段分支并创建 `payment-monitor-phase-l-v1`。

Phase M 和 Phase N 目前不应启动验收计时；Phase M 的公网生产部署、7 天灰度和 72 小时 Android 连续运行均尚未执行，Phase N 的 V16、公开注册与 30 天稳定运行前置条件也尚未满足。
