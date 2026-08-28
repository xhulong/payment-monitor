# api-crypto-v2 协议、密钥轮换与灰度说明

更新日期：2026 年 7 月 19 日

## 1. 定位与边界

`api-crypto-v2` 是管理端浏览器的接口级应用层加密能力，基础传输边界仍然是 HTTPS。它不替代 TLS，也不用于 Android、Webhook、设备心跳或支付事件上传。

固定算法：

```text
RSA-OAEP-256
AES-256-GCM
HKDF-SHA256
```

旧版 API 应用层加密协议已从代码库删除，v2 是唯一实现。浏览器不保存 RSA 私钥，每次请求使用新的 32 字节 `masterKey`、12 字节 GCM IV 和 UUID `jti`。

## 2. 接口启用方式

Controller 方法显式标记：

```java
@ApiCryptoV2(request = true, response = true)
```

规则：

- 无注解：普通 HTTPS JSON。
- `request=true,response=false`：只加密请求。
- `request=true,response=true`：请求和响应均加密。
- `request=false,response=true`：应用启动失败。
- 已标记接口必须同时携带正确的 v2 版本头和媒体类型。
- 未标记接口收到 v2 标记时返回 `API_CRYPTO_INVALID`。
- 协议错误不输出 RSA、AES、Tag、密钥路径或解析异常细节。

当前已启用：

```text
POST /auth/login
POST /auth/mfa/verify
POST /api/v1/public/accounts/password-reset/code
POST /api/v1/public/accounts/password-reset/confirm
POST /account/email-change/code
POST /account/email-change/confirm
PUT  /system/user/profile/updatePwd
PUT  /system/user/resetPwd
```

继续使用普通 HTTPS：

```text
POST /auth/refresh
POST /auth/logout-all
GET  /api/v2/crypto/jwks
普通查询、列表、详情和导出接口
Webhook、设备心跳和支付事件接口
```

## 3. 请求协议

请求头：

```text
Content-Type: application/vnd.paymentmonitor.crypto+json
X-Api-Crypto-Version: 2
```

请求体：

```json
{
  "v": 2,
  "kid": "payment-monitor-rsa-2026-01",
  "jti": "36f011dc-8bf6-453e-b981-9f3c4b84d6b1",
  "ts": 1784390000,
  "wrappedKey": "BASE64URL",
  "iv": "BASE64URL",
  "ciphertext": "BASE64URL",
  "tag": "BASE64URL"
}
```

请求 AAD：

```text
v2
REQUEST
HTTP_METHOD
REQUEST_PATH
KID
JTI
TIMESTAMP
```

实际拼接格式：

```text
2\nREQUEST\nPOST\n/auth/login\nKID\nJTI\nTIMESTAMP
```

服务端检查：

- Envelope 版本和必填字段。
- `kid` 存在且服务端可执行解包。
- RSA 密钥至少 2048 位。
- `jti` 是 UUID，且 Redis 中尚未消费。
- 时间戳在允许窗口内。
- HTTP 方法和不含网关前缀的请求路径与 AAD 一致。
- GCM IV 为 12 字节、Tag 为 16 字节且认证成功。
- 请求体不超过 `max-body-bytes`。

防重放键：

```text
api-crypto:v2:jti:{jti}
```

使用 Redis 原子 `SETNX + TTL`。相同 Envelope 再次提交时返回：

```json
{"code":400,"msg":"API_CRYPTO_INVALID","data":null}
```

## 4. 响应协议

响应密钥派生：

```text
salt = SHA256("payment-monitor/api-crypto-v2")
prk = HKDF-Extract(salt, masterKey)
responseKey = HKDF-Expand(
  prk,
  "response|JTI|HTTP_METHOD|REQUEST_PATH",
  32
)
```

响应体：

```json
{
  "v": 2,
  "jti": "36f011dc-8bf6-453e-b981-9f3c4b84d6b1",
  "ts": 1784390001,
  "status": 200,
  "iv": "BASE64URL",
  "ciphertext": "BASE64URL",
  "tag": "BASE64URL"
}
```

响应 AAD：

```text
2\nRESPONSE\nHTTP_STATUS\nREQUEST_PATH\nJTI\nTIMESTAMP
```

浏览器同时校验 Envelope 中的 `status` 与真实 HTTP 状态码。请求与响应使用不同 AES 密钥；请求完成或失败后，前后端会清零当前请求持有的 `masterKey` 字节数组。

## 5. JWKS

公开端点：

```text
GET /api/v2/crypto/jwks
```

响应只包含公钥：

```json
{
  "activeKid": "payment-monitor-rsa-2026-01",
  "keys": [
    {
      "kty": "RSA",
      "kid": "payment-monitor-rsa-2026-01",
      "use": "enc",
      "alg": "RSA-OAEP-256",
      "n": "...",
      "e": "AQAB"
    }
  ]
}
```

服务端返回 `ETag` 和五分钟 `Cache-Control`。管理端只在内存缓存导入后的公钥，不写入 `localStorage`、`sessionStorage`、IndexedDB、URL 或日志。

## 6. 配置

基础配置：

```yaml
api-crypto-v2:
  enabled: ${API_CRYPTO_V2_ENABLED:false}
  jwks-path: ${API_CRYPTO_V2_JWKS_PATH:/api/v2/crypto/jwks}
  active-kid: ${API_CRYPTO_V2_ACTIVE_KID:payment-monitor-rsa-2026-01}
  key-provider: ${API_CRYPTO_V2_KEY_PROVIDER:local-secret}
  public-key-file: ${API_CRYPTO_V2_PUBLIC_KEY_FILE:}
  private-key-file: ${API_CRYPTO_V2_PRIVATE_KEY_FILE:}
  allow-ephemeral-dev-key: ${API_CRYPTO_V2_ALLOW_EPHEMERAL_DEV_KEY:false}
  fail-on-plaintext: true
  max-body-bytes: ${API_CRYPTO_V2_MAX_BODY_BYTES:1048576}
  clock-skew-seconds: ${API_CRYPTO_V2_CLOCK_SKEW_SECONDS:120}
  replay-ttl-seconds: ${API_CRYPTO_V2_REPLAY_TTL_SECONDS:180}
```

管理端：

```text
VITE_APP_API_CRYPTO_V2=true
```

灰度启用时前后端开关必须同步。服务端先部署并保持关闭，随后部署具备 v2 能力的管理端，最后同时开启服务端和管理端开关。

## 7. 本地开发密钥与生产密钥

### 7.1 开发环境

开发配置允许：

```text
API_CRYPTO_V2_ALLOW_EPHEMERAL_DEV_KEY=true
```

服务每次启动生成新的临时 RSA 密钥，因此重启后旧 JWKS 和未完成请求立即失效。该模式只用于本地开发和自动化测试。

### 7.2 单机生产过渡

当前内置 Provider 是 `local-secret`。优先使用宿主机只读 Secret 挂载，不把私钥放入 Git、镜像、Compose 文件或应用普通配置目录。

生成示例：

```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out api-crypto-v2-private.pem
openssl pkey -in api-crypto-v2-private.pem -pubout -out api-crypto-v2-public.pem
chmod 0400 api-crypto-v2-private.pem
chmod 0444 api-crypto-v2-public.pem
```

容器只读挂载后设置：

```text
API_CRYPTO_V2_KEY_PROVIDER=local-secret
API_CRYPTO_V2_PUBLIC_KEY_FILE=/run/secrets/api-crypto-v2-public.pem
API_CRYPTO_V2_PRIVATE_KEY_FILE=/run/secrets/api-crypto-v2-private.pem
```

生产 Compose 模板已固定启用 v2，并将密钥挂载为：

```text
/run/secrets/api_crypto_v2_public_key
/run/secrets/api_crypto_v2_private_key
```

宿主机路径由以下变量指定：

```text
API_CRYPTO_V2_PUBLIC_KEY_HOST_FILE
API_CRYPTO_V2_PRIVATE_KEY_HOST_FILE
```

生产管理端镜像也会在构建阶段固定注入：

```text
VITE_APP_API_CRYPTO_V2=true
```

启动时会验证：

- 公私钥可读取。
- RSA 位数不少于 2048。
- 公钥与私钥匹配。
- 只有一个活动 `kid`。
- 活动密钥不是 `decryptOnly`，且具备私钥解包能力。

仍保留 `API_CRYPTO_V2_PUBLIC_KEY` 和 `API_CRYPTO_V2_PRIVATE_KEY` 内联配置，主要用于受控 CI；生产优先使用文件挂载。

### 7.3 KMS、HSM 与 Vault

本轮代码没有伪造 KMS/HSM/Vault Transit 实现。当前生产可用路径是只读 Secret 挂载；将 RSA 解包操作移入 KMS、HSM 或 Vault Transit 的远程 Provider 仍是后续上线阻断项。完成远程 Provider 前，不应把 `key-provider` 配成未实现的值，应用会在启动时失败。

## 8. 密钥轮换

轮换期间同时配置新旧密钥：

```yaml
api-crypto-v2:
  active-kid: payment-monitor-rsa-2026-02
  keys:
    - kid: payment-monitor-rsa-2026-01
      public-key-file: /run/secrets/api-crypto-v2-2026-01-public.pem
      private-key-file: /run/secrets/api-crypto-v2-2026-01-private.pem
      decrypt-only: true
    - kid: payment-monitor-rsa-2026-02
      public-key-file: /run/secrets/api-crypto-v2-2026-02-public.pem
      private-key-file: /run/secrets/api-crypto-v2-2026-02-private.pem
      active: true
```

顺序：

1. 生成并离线备份新密钥，记录 `kid` 和证书/公钥指纹。
2. 服务端同时装载旧密钥和新密钥，新密钥设为活动密钥。
3. 确认 JWKS 的 `activeKid` 已更新，旧公钥仍在兼容集合中。
4. 观察至少一个 JWKS 缓存周期、请求时间窗口和防重放 TTL。
5. 确认不存在旧 `kid` 请求后，删除旧私钥挂载和旧 JWKS 项。
6. 销毁不再需要的在线旧私钥副本，保留受控离线备份和轮换审计记录。

## 9. 回滚

协议回滚只控制 v2 的前后端开关，不恢复已经删除的 v1 协议。

```text
1. 将管理端 VITE_APP_API_CRYPTO_V2 设为 false 并重新构建。
2. 将服务端 API_CRYPTO_V2_ENABLED 设为 false。
3. 验证认证接口恢复为普通 HTTPS JSON。
4. 保留密钥 Secret 和 Redis 监控，确认没有旧管理端仍发送 v2 请求。
5. 修复后按“服务端代码就绪 → 管理端代码就绪 → 同步开关”重新灰度。
```

不能只关闭一端：服务端开启而前端关闭时，已标记接口会严格拒绝明文；前端开启而服务端关闭时，Controller 会收到无法绑定的 Envelope。

## 10. 日志、监控与验收

允许记录：

- 协议版本。
- Controller 路由模板。
- 结果分类，如 `INVALID_ENVELOPE`、`REPLAY` 的内部指标标签。
- `kid` 的非敏感版本标识。
- 成功/失败计数与耗时。

禁止记录：

- 解密后的请求体。
- `masterKey`、响应派生密钥或 RSA 私钥。
- `wrappedKey`、完整 Envelope、密码、TOTP、恢复码和 Step-Up Token。

回归范围：

```text
服务端：RSA/AES/HKDF、篡改、路径绑定、启动期注解校验、密钥文件挂载和密钥匹配
管理端：请求头、Envelope、请求解密互操作、响应加密互操作和 MFA 页面切换
集成：JWKS、Redis 并发防重放、明文拒绝、Docker 代理、CORS 预检和响应头暴露
```
