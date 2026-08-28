# 部署教程

本文档覆盖 **LuLuPay 码支付监控系统** 的完整部署流程：数据库初始化、环境变量、Docker Compose 部署、源码部署与生产环境建议。

## 1. 环境要求

| 组件 | 版本要求 | 说明 |
| --- | --- | --- |
| Docker | 24+（含 Docker Compose v2） | 推荐方式，一条命令拉起全部依赖 |
| JDK | 21+ | 后端编译 / 运行 |
| Maven | 3.9+ | 后端构建 |
| Node.js | 20+（含 pnpm 10） | 前端构建 |
| Android Studio | 最新稳定版 | Android 客户端构建 |

## 2. 架构与服务清单

```
┌─────────────┐      HTTPS      ┌──────────────────────────────────────────────┐
│  Android 端  │ ──────────────▶ │  Nginx / 网关（反向代理，可选）                 │
│ (通知监听)    │                └──────────────┬───────────────────────────────┘
└─────────────┘                               │
                                   ┌──────────▼──────────┐
                                   │  管理后台 (Web)       │
                                   │  payment-monitor-    │
                                   │  admin (Vite/Nginx)  │
                                   └──────────┬──────────┘
                                              │ /prod-api
                                   ┌──────────▼──────────┐      ┌─────────────┐
                                   │  后端服务             │ ────▶│ PostgreSQL  │
                                   │  ruoyi-admin:8080    │      └─────────────┘
                                   └──────────┬──────────┘      ┌─────────────┐
                                              │                  │ Redis        │
                                   ┌──────────▼──────────┐      └─────────────┘
                                   │ 扩展服务              │      ┌─────────────┐
                                   │  SnailJob 调度中心    │ ────▶│ MinIO (OSS)  │
                                   └─────────────────────┘      └─────────────┘
```

| 服务 | 容器 / 模块 | 默认端口 |
| --- | --- | --- |
| 数据库 | `postgres` | 5433（对外）/ 5432（容器内） |
| 缓存 | `redis` | 6380（对外）/ 6379（容器内） |
| 对象存储 | `minio` | 9000/9001 |
| 任务调度中心 | `snailjob-server` | 8800/17888 |
| 后端 | `backend`（ruoyi-admin） | 8080 |
| 管理后台 | `admin`（nginx 静态站点） | 80 |
| 系统监控 | `monitor-admin`（Spring Boot Admin） | 9090（可选） |

## 3. 数据库初始化

系统数据库由 **两部分** 组成：

### 3.1 基础库（平台 + 权限 + 工作流 + 调度）

初始化脚本位于 `payment-monitor-server/script/sql/`，按数据库方言选择：

| 方言 | 文件 |
| --- | --- |
| PostgreSQL（推荐） | `postgres/postgres_ry_vue.sql` + `postgres_ry_job.sql` + `postgres_ry_workflow.sql` |
| MySQL | `ry_vue.sql` + `ry_job.sql` + `ry_workflow.sql` |
| Oracle | `oracle/oracle_ry_vue.sql` + `oracle_ry_job.sql` + `oracle_ry_workflow.sql` |
| SQLServer | `sqlserver/sqlserver_ry_vue.sql` + `sqlserver_ry_job.sql` + `sqlserver_ry_workflow.sql` |

**方式 A：Docker Compose 自动初始化（推荐）**

`docker-compose.local.yml` 已挂载 PostgreSQL 初始化脚本，**首次启动空数据卷时自动执行**：

```bash
docker compose -f deploy/docker-compose.local.yml up -d postgres
# 等待 healthy 后检查：
docker exec payment-monitor-postgres psql -U payment_monitor -d payment_monitor -c "\dt" | head
```

**方式 B：手动执行**

```bash
# PostgreSQL 示例：先创建数据库与用户
psql -h localhost -U postgres -c "CREATE USER payment_monitor WITH PASSWORD 'your-password';"
psql -h localhost -U postgres -c "CREATE DATABASE payment_monitor OWNER payment_monitor;"
# 依次导入（注意顺序：vue → job → workflow）
psql -h localhost -U payment_monitor -d payment_monitor -f script/sql/postgres/postgres_ry_vue.sql
psql -h localhost -U payment_monitor -d payment_monitor -f script/sql/postgres/postgres_ry_job.sql
psql -h localhost -U payment_monitor -d payment_monitor -f script/sql/postgres/postgres_ry_workflow.sql
```

### 3.2 支付业务表（Flyway 自动迁移）

支付模块的表（前缀 `pm_`，如商户、订单、对账、MFA、邮件等）**不需要手工导入**——后端启动时由 [Flyway](https://flywaydb.org/) 自动执行：

```
ruoyi-modules/ruoyi-payment/src/main/resources/db/migration/payment/V*.sql
```

迁移记录表为 `pm_flyway_schema_history`。后端启动日志出现 `Successfully applied X migrations` 即完成。

> ⚠️ 不要手工修改或删除迁移脚本；升级版本时新增 `V{n+1}__*.sql` 即可。

## 4. 环境变量配置

后端通过环境变量注入配置。完整清单见 `.env.example` 与 `deploy/.env.ports.example`，**所有 `replace-with-*` 值必须替换**：

| 变量 | 必填 | 说明 |
| --- | --- | --- |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | ✅ | PostgreSQL 连接 |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` | ✅ | Redis 连接 |
| `SA_TOKEN_JWT_SECRET` | ✅ | 登录令牌签名密钥（≥32 字符随机串） |
| `PAYMENT_MASTER_KEY` | ✅ | 支付业务数据加密主密钥（≥32 字符） |
| `ACCOUNT_MFA_MASTER_KEY` | ✅ | MFA 密钥加密主密钥 |
| `ACCOUNT_RECOVERY_CODE_PEPPER` | ✅ | 账号恢复码 pepper |
| `MAIL_OUTBOX_MASTER_KEY` / `MAIL_OUTBOX_ACTIVE_KEY_ID` | ✅ | 邮件出站队列加密 |
| `APK_DOWNLOAD_SIGNING_SECRET` | ✅ | APK 下载签名 |
| `MINIO_ROOT_USER` / `MINIO_ROOT_PASSWORD` / `MINIO_BUCKET` | ✅ | 对象存储 |
| `API_CRYPTO_V2_ENABLED` | 否 | 应用层 API 加密开关（生产建议 `true`） |
| `API_CRYPTO_V2_PUBLIC_KEY_FILE` / `PRIVATE_KEY_FILE` | 否* | API 加密 RSA 密钥文件路径（启用时必填；开发可用临时密钥） |
| `SMTP_*` | 否 | 邮件发送（邮件中心功能） |
| `PAYMENT_WEBHOOK_*` / `PAYMENT_EASYPAY_*` | 否 | 商户回调/易支付通道安全开关（生产保持 `ALLOW_HTTP=false`） |
| `AUTH_REFRESH_COOKIE_SECURE` | 否 | HTTPS 反代后置 `true` |
| `ANDROID_EXPECTED_PACKAGE_NAME` / `SIGNING_CERTIFICATE_SHA256` | 否 | Android 设备绑定校验 |

生成随机密钥示例：

```bash
openssl rand -hex 32   # 用于各类 MASTER_KEY / SECRET
```

## 5. Docker Compose 部署

### 5.1 本地开发环境

```bash
cd payment-monitor-server

# 1. 准备环境变量
cp deploy/.env.ports.example deploy/.env
#   编辑 deploy/.env：替换所有 replace-with-* 为强随机值

# 2. 启动依赖
docker compose -f deploy/docker-compose.local.yml --env-file deploy/.env up -d postgres redis minio

# 3. 构建后端镜像
docker compose -f deploy/docker-compose.local.yml --env-file deploy/.env build backend

# 4. 启动后端
docker compose -f deploy/docker-compose.local.yml --env-file deploy/.env up -d backend
#   观察日志：Flyway 迁移成功 + Started RuoYiApplication

# 5. 前端
cd ../payment-monitor-admin
pnpm install
pnpm build:prod
cd ../payment-monitor-server
docker compose -f deploy/docker-compose.local.yml --env-file deploy/.env up -d admin
```

访问：http://localhost （`admin` / `admin123`，登录后请立即修改）。

### 5.2 生产环境要点

- 使用 `deploy/docker-compose.ports.yml`（端口分离：管理后台 18081、后端仅本机监听 18082），由外部 Nginx / 防火墙暴露；
- **务必**：强密码、独立密钥、`API_CRYPTO_V2_ENABLED=true` 并提供 RSA 密钥对、`AUTH_REFRESH_COOKIE_SECURE=true`；
- 反向代理 HTTPS 参考 `deploy/nginx-payment-monitor.conf`（`/` → admin、`/prod-api/` → backend）；
- 监控告警（可选）：`deploy/production/` 提供 Prometheus + Grafana + Alertmanager 配置；
- 数据库定期备份：
  ```bash
  docker exec payment-monitor-postgres pg_dump -U payment_monitor payment_monitor | gzip > backup-$(date +%F).sql.gz
  ```

## 6. 源码部署

### 6.1 后端

```bash
cd payment-monitor-server
# JDK 21 + Maven 3.9+
mvn -pl ruoyi-admin -am package -DskipTests

cd ruoyi-admin
export DB_PASSWORD=xxx REDIS_PASSWORD=xxx SA_TOKEN_JWT_SECRET=xxx PAYMENT_MASTER_KEY=xxx
java -jar target/ruoyi-admin.jar --spring.profiles.active=dev
```

### 6.2 前端

```bash
cd payment-monitor-admin
pnpm install
pnpm dev          # 开发模式 http://localhost:5173
pnpm build:prod   # 生产构建（产物在 dist/）
```

### 6.3 Android 客户端

```bash
cd payment-notification-monitor
```

1. 用 Android Studio 打开工程；
2. 在 `app/src/main/java/com/example/paymentmonitor/...` 中配置服务端地址（或通过构建参数注入）；
3. 构建安装到手机，授予「通知使用权」与「后台运行」权限；
4. 在管理后台将设备与商户账号绑定。

## 7. 常见问题（FAQ）

| 问题 | 处理 |
| --- | --- |
| 后端启动报 `Flyway` 迁移失败 | 检查数据库用户是否有建表权限；不要改动已执行的 `V*` 脚本 |
| 登录提示「验证码错误」 | Redis 未启动或 `REDIS_PASSWORD` 不匹配 |
| 登录后要求二次验证（MFA）但未设置 | 该账号启用了 MFA；用账号恢复码或管理员在数据库中关闭 `pm_account_mfa.enabled` |
| 接口返回 `API_CRYPTO_INVALID` | 前后端 `API_CRYPTO_V2` 开关不一致，或 RSA 密钥不匹配 |
| Android 端收不到通知 | 确认已开启通知使用权、系统省电策略放行、服务端设备已绑定 |
| 端口被占用 | 修改 `deploy/.env` 中 `*_PORT` 映射 |

## 8. 升级

1. 拉取新代码并重新构建镜像 / jar；
2. 后端启动时 Flyway 自动应用新增迁移；
3. 前端重新 `pnpm build:prod`；
4. 升级前备份数据库。
