# 支付通知监控服务端 Phase 1

## 1. 交付范围

本阶段基于 RuoYi-Vue-Plus `6.X` 和 plus-ui `6.X-Vue` 增量开发，保留上游全部模块，不删除 `system`、`demo`、`gen`、`job`、`workflow`、`ai` 等模块。

已完成：

- PostgreSQL 数据模型与 Flyway 增量迁移。
- 管理端登录体系复用、支付监控菜单和权限。
- 设备配对码、设备启停、凭据吊销、在线状态和心跳。
- Android 设备 HMAC-SHA256 请求认证、时间戳校验和 Redis Nonce 防重放。
- 支付事件批量上传、事件幂等、查询、详情和统计概览。
- Vue 管理端概览、设备管理、配对二维码、事件筛选和原始数据详情。

暂不包含：

- 业务订单、收款二维码、金额匹配、补单。
- Webhook、HTTP 回调、签名通知和自动重试。
- Android App 与服务端的实际联网、离线队列和设备配对界面。
- 生产部署编排、网关、TLS、监控告警和数据库备份。

## 2. 目录和版本

```text
D:\desktop\免签app\PaymentMonitorServer
D:\desktop\免签app\PaymentMonitorAdmin
```

后端：

- 上游：RuoYi-Vue-Plus `6.X`
- 固定提交：`28c39d5b901beeddfccbb4c35c56032690b324a4`
- 开发分支：`payment-monitor-phase1`
- JDK：21
- Maven：3.9.9

前端：

- 上游：plus-ui `6.X-Vue`
- 固定提交：`21fc08193e58e1dcfdf5509604094413febebdfe`
- 开发分支：`payment-monitor-phase1`
- Node.js：22
- pnpm：10.28.1

## 3. 本地 Docker 基础环境

PostgreSQL 和 Redis 固定使用本机 Docker Desktop + Docker Compose 部署；后端和前端在本机 JDK/Node 环境运行，便于使用 IDE 调试。

容器：

| 服务 | 镜像 | 宿主端口 | 容器端口 |
| --- | --- | ---: | ---: |
| PostgreSQL | `postgres:17-alpine` | 5433 | 5432 |
| Redis | `redis:7-alpine` | 6380 | 6379 |

Docker Compose 文件：

```text
D:\desktop\免签app\PaymentMonitorServer\deploy\docker-compose.local.yml
```

首次启动：

```powershell
cd D:\desktop\免签app\PaymentMonitorServer
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\Initialize-LocalSecrets.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\Start-Infrastructure.ps1
```

`Initialize-LocalSecrets.ps1` 会生成：

```text
D:\desktop\免签app\PaymentMonitorServer\.env.local
D:\desktop\免签app\PaymentMonitorAdmin\.env.development.local
```

这两个文件均被 Git 忽略。不得提交或复制其中的数据库密码、Redis 密码、JWT 密钥、支付主密钥和 RSA 私钥。

查看容器：

```powershell
$env:DOCKER_CONFIG='D:\desktop\免签app\.tools\docker-config'
docker compose --env-file .\.env.local -f .\deploy\docker-compose.local.yml ps
```

停止容器但保留数据卷：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\Stop-Infrastructure.ps1
```

需要彻底重置本地数据库时，手动执行以下命令。该命令会删除本项目 PostgreSQL 和 Redis 数据卷：

```powershell
$env:DOCKER_CONFIG='D:\desktop\免签app\.tools\docker-config'
docker compose --env-file .\.env.local -f .\deploy\docker-compose.local.yml down -v
```

PostgreSQL 首次创建数据卷时会执行全部上游初始化脚本：

```text
postgres_ry_vue.sql
postgres_ry_job.sql
postgres_ry_workflow.sql
postgres_ry_ai.sql
```

支付模块表结构由后端启动时的 Flyway 迁移自动创建，迁移历史表为 `pm_flyway_schema_history`。

## 4. 启动后端

确认 Docker 中的 PostgreSQL 和 Redis 健康后执行：

```powershell
cd D:\desktop\免签app\PaymentMonitorServer
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\Start-Backend.ps1
```

后端默认地址：

```text
http://localhost:8080
```

上游初始化管理员：

```text
用户名：admin
密码：admin123
```

首次登录后应立即修改初始化密码。

单独构建：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\Build-Backend.ps1
```

构建产物：

```text
D:\desktop\免签app\PaymentMonitorServer\ruoyi-admin\target\ruoyi-admin.jar
```

## 5. 启动前端

```powershell
cd D:\desktop\免签app\PaymentMonitorAdmin
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\Start-Frontend.ps1
```

开发地址：

```text
http://localhost:5173
```

生产环境变量示例：

```text
D:\desktop\免签app\PaymentMonitorAdmin\.env.production.example
```

部署前复制为 `.env.production.local` 并替换 RSA 占位符；`.local` 文件已被 Git 忽略。

## 6. 设备 API

### 6.1 配对

```http
POST /api/v1/devices/pair
Content-Type: application/json

{
  "pairingCode": "12345678",
  "deviceName": "收银台手机",
  "androidIdHash": "64位SHA-256",
  "appVersion": "1.0.0",
  "parserVersion": "1.0.0"
}
```

配对码为 8 位数字、有效期 5 分钟且只能使用一次。配对成功后返回 `deviceId` 和 `deviceSecret`，Android 端必须存入系统安全存储。

### 6.2 HMAC 请求认证

除配对接口外，设备 API 需要以下请求头：

```text
X-Device-Id
X-Timestamp
X-Nonce
X-Signature
```

签名原文：

```text
METHOD
PATH
TIMESTAMP
NONCE
SHA256(BODY原始字节)
```

签名算法：

```text
hexLower(HMAC-SHA256(deviceSecret, canonicalRequest))
```

约束：

- 时间戳允许偏差：300 秒。
- Redis Nonce 有效期：600 秒。
- 请求体上限：1 MiB。
- 单次事件批量上限：100 条。

### 6.3 心跳和配置

```text
POST /api/v1/device/heartbeat
GET  /api/v1/device/config
```

默认心跳间隔为 60 秒，超过 180 秒未上报视为离线。

### 6.4 支付事件批量上传

```http
POST /api/v1/payment-events/batch
Content-Type: application/json
```

事件以 `(merchant_id, client_event_id)` 建立唯一约束。客户端重试相同事件时不会重复入库。

完整的 PowerShell 配对、签名、心跳和上传样例：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass `
  -File .\scripts\Invoke-DeviceFixture.ps1 `
  -PairingCode 12345678
```

## 7. 管理端 API 和权限

| API | 权限 |
| --- | --- |
| `POST /payment/pairing-codes` | `payment:device:pair` |
| `GET /payment/devices/list` | `payment:device:list` |
| `GET /payment/devices/{id}` | `payment:device:list` |
| `PUT /payment/devices/{id}/status` | `payment:device:edit` |
| `GET /payment/events/list` | `payment:event:list` |
| `GET /payment/events/{id}` | `payment:event:query` |
| `GET /payment/dashboard` | `payment:dashboard:view` |

## 8. 验证命令

后端模块测试：

```powershell
$env:JAVA_HOME='D:\java\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
mvn.cmd '-Dmaven.repo.local=D:/desktop/免签app/.tools/m2-repository' `
  '-DskipTests=false' '-Dmaven.test.skip=false' `
  -pl ruoyi-modules/ruoyi-payment -am test
```

前端：

```powershell
$env:COREPACK_HOME='D:\desktop\免签app\.tools\corepack'
$env:PNPM_HOME='D:\desktop\免签app\.tools\pnpm-home'
$env:Path="D:\desktop\免签app\.tools\node-v22;$env:PNPM_HOME;$env:Path"
pnpm lint
pnpm build
```

## 9. 下一阶段

下一阶段优先完成 Android App 到服务端的闭环：

1. 增加服务端地址和 8 位配对码输入界面。
2. 配对后安全保存 `deviceId`、`deviceSecret`。
3. 实现 HMAC 请求拦截器、时钟偏差处理和唯一 Nonce。
4. Room 增加上传状态、失败次数和下次重试时间。
5. 实现心跳、服务端配置拉取和最多 100 条的批量上传。
6. 使用 `clientEventId` 做幂等重试，成功后标记本地记录已上传。
7. 在真实安装微信、支付宝的设备上完成到账通知联调。

完成上述闭环后，再进入订单、二维码、金额匹配和 Webhook 阶段。

## 10. 本地部署联调结果

截至 2026 年 7 月 16 日，本机 Docker Desktop、后端和前端已经完成运行时联调：

- Docker Desktop Engine `27.5.1` 可正常连接。
- 宿主机 `5432` 已被其他 PostgreSQL 容器占用，因此本项目 PostgreSQL 使用可配置的宿主端口 `5433`。
- `payment-monitor-postgres` 和 `payment-monitor-redis` 均通过健康检查。
- PostgreSQL 上游初始化脚本成功创建基础表和初始化管理员。
- Flyway 成功执行 Baseline、V1 支付中心建表和 V2 `LocalDateTime` 时间列兼容迁移。
- RSA 加密登录、验证码校验和管理员 Token 获取成功。
- 管理端生成 8 位配对码成功。
- 设备配对、HMAC 心跳和支付事件批量上传成功。
- 同一 `clientEventId` 再次上传被正确识别为重复事件，没有重复入库。
- 相同时间戳、Nonce 和签名重放返回 HTTP 401。
- 管理端停用设备并吊销凭据后，新签名心跳返回 HTTP 401。
- Vue 开发服务器 `http://localhost:5173` 可访问，`/dev-api` 到后端的代理请求成功。
- 支付模块 OpenAPI 分组成功生成，包含 11 条支付管理和设备 API 路径。

联调完成时的数据库结果：

```text
Flyway 版本：2
设备数：2
有效设备凭据：1
支付事件数：1
```

其中第二台设备专用于重放与吊销测试，测试结束后已停用并吊销凭据。当前 PostgreSQL、Redis、后端和前端进程均保持运行，便于继续进行 Android App 联调。
