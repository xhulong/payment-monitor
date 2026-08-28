# Jenkins 端口化自动部署

该部署模式不在项目容器内签发或终止 HTTPS：

- 管理端入口默认监听 `0.0.0.0:18081`；
- 后端默认监听 `127.0.0.1:18082`；
- PostgreSQL、Redis、MinIO 仅在 Docker 内部网络开放；
- 后续域名反向代理直接指向管理端 `18081`；
- 若需要绕过管理端网关单独反代 API，可将 API 上游指向本机 `18082`。

## Jenkins 目录

```text
/opt/payment-monitor/
├── config/
│   ├── .env.production
│   └── secrets/
│       ├── api-crypto-v2-private.pem
│       └── api-crypto-v2-public.pem
├── git/
│   ├── payment-monitor-admin.git
│   └── payment-monitor-server.git
└── jenkins-workspace/
```

Jenkins 控制器需要挂载：

```yaml
- /opt/payment-monitor:/opt/payment-monitor
- /usr/libexec/docker/cli-plugins:/usr/libexec/docker/cli-plugins:ro
```

API 加密私钥建议保持为 `0640 root:root`。后端容器以
`10001:0` 运行：应用进程不是 root，但可通过只读 root 组权限读取部署密钥。

## HTTPS 反代切换

完成域名 HTTPS 反代后：

1. 将 `PAYMENT_PUBLIC_BASE_URL` 改成最终 `https://域名`；
2. 将 `AUTH_REFRESH_COOKIE_SECURE` 改成 `true`；
3. 在反代中保留 `Host`、`X-Real-IP`、`X-Forwarded-For` 和 `X-Forwarded-Proto`；
4. 重新执行 Jenkins 任务。

## 回滚

流水线构建前会将当前镜像标记为：

- `payment-monitor-backend:rollback`
- `payment-monitor-admin:rollback`

部署或健康检查失败时，流水线自动使用这两个镜像恢复上一版本。数据库与对象存储使用独立命名卷，不随应用容器替换。
