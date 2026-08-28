# 安全说明

## 报告安全漏洞

如果你发现安全漏洞（如信息泄露、越权访问、注入等），**请勿公开提交 Issue**，请通过以下方式私下报告：

- 📧 邮件：在 GitHub 仓库主页查看维护者邮箱
- 🔒 GitHub Security Advisory：https://github.com/xhulong/payment-monitor/security/advisories

我们会在 **48 小时内** 确认并回复，修复后发布安全公告。

## 安全基线

本项目在生产环境默认启用的安全能力：

- **应用层 API 加密 v2**：RSA + AES 混合加密，按接口显式启用（`API_CRYPTO_V2_ENABLED=true`）
- **账号 MFA**：登录二次验证 + 敏感操作再次验证（TOTP）
- **Webhook / 回调安全**：默认拒绝 HTTP 与非私有网络回调（`PAYMENT_WEBHOOK_ALLOW_HTTP=false`）
- **设备绑定**：Android 端校验包名与签名证书 SHA256

## 部署安全建议

- 所有 `replace-with-*` 环境变量使用强随机值（`openssl rand -hex 32`）
- 生产环境启用 HTTPS（`AUTH_REFRESH_COOKIE_SECURE=true`）
- 定期轮换密钥与数据库密码
- 及时升级版本获取安全修复
