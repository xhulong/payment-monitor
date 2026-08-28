# LuLuPay 码支付监控系统 - 后端

本仓库为 **LuLuPay 码支付监控系统** 的后端服务（Java 21 + Spring Boot 4.1），基于 [RuoYi-Vue-Plus](https://gitee.com/dromara/RuoYi-Vue-Plus) 6.x 扩展。

## 模块说明

```
ruoyi-admin/        # 启动模块（Web 入口，端口 8080）
ruoyi-modules/      # 业务模块
  ├── ruoyi-payment/  # 支付监控核心（商户/订单/对账/告警/MFA/邮件/Webhook）
  ├── ruoyi-system/   # 系统管理（用户/角色/菜单）
  ├── ruoyi-workflow/ # 工作流（FlowLong）
  └── ruoyi-gen/      # 代码生成
ruoyi-common/       # 公共组件（核心/加密/推送/邮件/OSS/Redis 等）
ruoyi-extend/       # 扩展服务（Spring Boot Admin 监控 / SnailJob 调度中心）
deploy/             # Docker 编排、Nginx 配置、监控（Prometheus/Grafana）
script/sql/         # 数据库初始化脚本（PostgreSQL/MySQL/Oracle/SQLServer）
```

## 快速开始

```bash
mvn -pl ruoyi-admin -am package -DskipTests
cd ruoyi-admin
java -jar target/ruoyi-admin.jar --spring.profiles.active=dev
```

环境变量清单见 `.env.example`；数据库初始化与完整部署步骤见：

- 📖 [项目总览与快速开始](../README.md)
- 📖 [部署教程](../docs/DEPLOYMENT.md)

## License

[MIT](../README.md#license)
