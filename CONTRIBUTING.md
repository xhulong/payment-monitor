# 贡献指南

感谢你对 **LuLuPay 码支付监控系统** 的关注与贡献！以下指南帮助你快速上手。

## 项目结构

```
payment-monitor-server/          后端（Java 21 + Spring Boot 4.1）
payment-monitor-admin/           管理后台前端（Vue3 + TS + Element Plus + Vite）
payment-notification-monitor/    Android 客户端（Kotlin + Compose）
docs/                            文档（部署教程等）
```

## 开发环境

| 组件 | 版本 |
| --- | --- |
| JDK | 21+ |
| Maven | 3.9+ |
| Node.js + pnpm | Node 20+ / pnpm 10 |
| Android Studio | 最新稳定版 |
| PostgreSQL / Redis | 17 / 7+ |

## 提交流程

1. **Fork** 本仓库并创建特性分支：`git checkout -b feat/your-feature`
2. **本地验证**：
   - 后端：`mvn -pl ruoyi-admin -am package -DskipTests`
   - 前端：`pnpm install && pnpm build:prod`
   - Android：`./gradlew assembleDebug`
3. 提交时遵循 [Conventional Commits](https://www.conventionalcommits.org/) 规范：
   - `feat:` 新功能
   - `fix:` 修复
   - `docs:` 文档
   - `chore:` 杂项
   - `refactor:` 重构
   - `test:` 测试
4. 提交 PR，关联相关 Issue，填写 PR 模板。

## 代码规范

- **数据库变更**：新增迁移脚本 `ruoyi-modules/ruoyi-payment/src/main/resources/db/migration/payment/V{n+1}__描述.sql`，**禁止修改已执行的迁移**
- **敏感信息**：禁止提交任何真实密钥、域名、IP、token（使用占位符）
- 遵循各子项目既有代码风格（后端 JavaDoc、前端 ESLint、Kotlin 官方风格）

## 行为准则

参与本项目即表示你同意遵循 [Contributor Covenant](https://www.contributor-covenant.org/version/2/1/code_of_conduct/) 行为准则：尊重他人、建设性沟通、对事不对人。
