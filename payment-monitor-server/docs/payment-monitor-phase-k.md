# 支付通知监控 Phase K 实施报告

报告日期：2026 年 7 月 18 日
分支：`payment-monitor-phase-k`
范围：个人商户自助入驻、平台审核、TOTP 敏感操作保护、Android 版本发布、单机 Docker 生产模板。

## 1. 本阶段完成内容

### 1.1 个人商户入驻

- 保留若依通用注册关闭状态：`sys.account.registerUser=false`。
- 新增个人商户专用邮箱验证码注册接口。
- 邮箱统一转小写并使用唯一索引。
- 密码限制为 12–64 位并使用 BCrypt。
- 验证码具有 5 分钟有效期，成功使用后立即失效。
- 注册用户自动进入 `payment_merchant_applicant` 角色，只能访问入驻申请、申请进度和账号安全页面。
- 同一用户同时只能存在一个有效申请。
- 申请状态支持：

```text
DRAFT → SUBMITTED → UNDER_REVIEW → NEEDS_CHANGES
                                  ↘ APPROVED
                                  ↘ REJECTED
                                  ↘ WITHDRAWN
```

- 提交时保存不可覆盖的资料快照和历史记录。
- 拒绝后设置 7 天冷却期。
- 申请资料不包含身份证号或证件图片，也不显示“实名认证通过”。

### 1.2 平台审核与商户开通

- 平台审核员和超级管理员可认领、通过、退回或拒绝申请。
- 审核通过在同一事务中创建商户、绑定所有者、授予商户所有者角色、记录审核历史和审计信息。
- 商户生命周期为：

```text
ONBOARDING → ACTIVE → SUSPENDED → CLOSED
```

- 未完成开通向导的商户禁止创建订单、调用商户订单 API、发布生产 Webhook。
- 开通向导包含 TOTP、协议确认、二维码、设备配对、心跳和测试通知同步。

### 1.3 团队成员和岗位权限

固定岗位：

```text
OWNER / ADMIN / FINANCE / DEVELOPER / VIEWER
```

- 普通用户只能属于一个商户。
- 邀请链接有效期为 24 小时。
- 不允许删除最后一个所有者。
- 所有权变更必须通过原所有者邀请和新所有者接受完成。
- 交易双人审批继续要求申请人和复核人不能是同一账号。

### 1.4 TOTP 和 Step-Up

- TOTP 使用 6 位验证码、30 秒时间步，并允许前后各一个时间窗口。
- 同一个时间步不能重复用于确认或 Step-Up。
- 恢复码仅在生成时展示，服务端只保存 BCrypt 哈希。
- Step-Up Token 绑定用户、登录会话和操作类型，有效期 5 分钟。
- API Key、Webhook 密钥、成员变更、商户审核、支付审批、撤销确认和 App 发布均要求 Step-Up。

### 1.5 Android App 发布

- Android 版本为：

```text
versionCode=7
versionName=1.6.0-dev
protocolVersion=1
parserVersion=2
```

- 管理端支持上传和发布 Android APK。
- APK 保存于 MinIO 私有 Bucket。
- 公开下载地址由服务端生成短期 HMAC 签名 URL，服务端从 MinIO 流式下载，不暴露 MinIO 内部地址。
- 发布信息包括 versionCode、versionName、最低支持版本、强制升级时间、SHA-256、签名证书指纹和更新说明。
- App 下载后校验 SHA-256，再调起系统安装器。
- 设备在宽限期内继续监听和上传；强制升级时间到达后返回 `UPDATE_REQUIRED`。

### 1.6 单机 Docker 生产模板

生产 Compose 包含：

```text
Nginx Gateway
Backend
PostgreSQL 17
Redis 7
MinIO
Prometheus
Grafana
PostgreSQL Exporter
Redis Exporter
```

- 仅 Gateway 暴露 80/443。
- PostgreSQL、Redis、MinIO 管理端、Prometheus、Grafana 和后端不暴露公网端口。
- 原始通知上传默认关闭。
- Webhook 默认禁止明文和私网地址。
- 容器使用固定版本镜像标签，不使用 `latest` 作为生产服务标签。
- Nginx 提供 HTTPS、HSTS、安全响应头、请求体限制和注册/登录/下载限流模板。
- 生产密钥仅通过 Secret 文件或部署环境注入。

## 2. 备份与恢复

`scripts/Backup-Production.ps1` 和 `scripts/backup-production.sh` 已扩展为同时备份：

- PostgreSQL custom dump；
- Redis RDB；
- MinIO 数据卷；
- 生产环境配置快照；
- TLS 证书文件元数据和 SHA-256；
- 归档内容清单。

保留策略：

```text
日备份：7 个
周备份：4 个
季度恢复点：3 个
```

Windows PowerShell 使用 `PMBK1` 加密格式、PBKDF2-SHA256、AES-256-CBC 和 HMAC-SHA256；Linux 脚本使用 OpenSSL AES-256-CBC，并额外生成 HMAC 和 SHA-256 校验文件。密码通过 `BACKUP_ENCRYPTION_PASSWORD` 提供，不能写入 Git。

恢复演练脚本会解密归档、校验 PostgreSQL/Redis/MinIO 备份文件，并将 PostgreSQL 恢复到临时数据库后验证 Flyway 版本。

本地演练结果：

```text
PostgreSQL / Redis / MinIO 备份：通过
加密归档生成：通过
独立 PostgreSQL 恢复：通过
恢复后的 Flyway 版本：12
```

## 3. 验证结果

### 3.1 服务端

命令：

```powershell
$env:JAVA_HOME='D:\java\jdk-21'
& .\mvnw.cmd '-pl' 'ruoyi-modules\ruoyi-payment' '-am' '-Pdev' `
  '-Dmaven.test.skip=false' '-DskipTests=false' '-Dprofiles.active=dev' 'test'
```

结果：

```text
ruoyi-payment：45 tests run, 0 failures, 0 errors, 0 skipped
BUILD SUCCESS
Flyway：12
```

覆盖内容包括邮箱归一化相关模型、TOTP 时间窗口和重放、Step-Up、商户隔离、订单权限、设备协议、金额槽位、支付交易、审批、对账、Webhook 和设备序列。

### 3.2 管理端

已通过：

```text
pnpm exec vue-tsc --noEmit
pnpm lint
pnpm test:payment
pnpm build:prod
```

支付 Vitest：

```text
Test Files：1 passed
Tests：2 passed
```

本地管理端：

```text
HTTP：200
HTML MIME：text/html
```

### 3.3 Android

已通过：

```text
testDebugUnitTest
assembleDebug
assembleRelease
assembleAndroidTest
```

Release Manifest 验证：

```text
usesCleartextTraffic=false
保留 MonitorBootReceiver
不包含 DebugFixtureReceiver
包含 REQUEST_INSTALL_PACKAGES
```

最新构建产物：

```text
D:\desktop\免签app\PaymentNotificationMonitor\app\build\outputs\apk\debug\app-debug.apk
D:\desktop\免签app\PaymentNotificationMonitor\app\build\outputs\apk\release\app-release.apk
D:\desktop\免签app\PaymentNotificationMonitor\app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk
```

当前本机用于联调的 Release APK 使用本地测试签名证书，仅用于内部环境；生产发布必须替换为正式且受控的签名密钥。

### 3.4 本地 Docker 与 APK 下载

当前本地容器：

```text
payment-monitor-postgres：healthy
payment-monitor-redis：healthy
payment-monitor-minio：healthy
payment-monitor-backend：healthy
payment-monitor-admin：healthy
```

已执行：

1. 构建后端 JAR 和管理端生产资源；
2. 重建并重启本地 Docker；
3. 将已签名内部测试 APK 放入 MinIO；
4. 写入本地发布记录并标记为 `PUBLISHED`；
5. 访问公开 latest API；
6. 通过短期签名地址下载 APK；
7. 对下载文件重新计算 SHA-256。

下载校验结果：

```text
文件大小：12,213,853 bytes
APK SHA-256：4435dd4b02ab482bb4462370d407b6d96138735c1feb9707ec9ad93c4d0b0b75
签名证书 SHA-256：73a5a43d47aca834d7420adf559d8e66e337708d9858bf6dd99b09e12b3ce95d
下载后哈希校验：通过
```

这次本地下载链路使用数据库种子记录验证服务端与 MinIO 的传输闭环；管理端上传页面的 Step-Up 交互仍应在浏览器中用真实平台管理员账号再执行一次。

## 4. 当前未完成与上线前事项

- 还没有把本地测试签名证书用于生产，生产必须替换正式签名密钥并记录受控指纹。
- SMTP 尚未接入真实外部邮件服务，邮箱验证码和审核通知仍需使用真实 SMTP 做联调。
- 单机生产 Compose 已完成模板和本地配置校验，但没有发布到公网服务器。
- 备份脚本已经支持加密和恢复演练；服务器外部对象存储复制、月度恢复排程需要在实际服务器上配置。
- 仍未接入微信或支付宝官方商户 API、官方账单、退款、清算、结算和支付牌照能力。
- App 内系统安装器行为需要在目标 Android 设备上最终验证，尤其是系统“允许安装未知来源”设置。
- 单机故障仍会导致服务不可用，本阶段只提供恢复能力，不宣称高可用。

## 5. 推荐验收顺序

1. 用正式测试邮箱完成注册和邮箱验证；
2. 平台审核员认领并审核申请；
3. 所有者启用 TOTP 并接受协议；
4. 添加二维码、生成配对码、安装最新 Debug APK；
5. 真机配对并完成微信/支付宝收款通知测试；
6. 创建订单，验证通知确认、人工确认、对账和 Webhook；
7. 管理端上传正式签名 APK，发布最低版本和升级宽限期；
8. 在 Linux 服务器执行配置预检、备份、发布和恢复演练；
9. 确认 SMTP、域名、证书、密钥和服务器外部备份后，再进行公网发布。
