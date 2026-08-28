# Phase I Android 玻璃拟态 UI 实施报告

日期：2026-07-17

## 交付范围

- 完成“监听、同步、记录”三个页面的玻璃拟态改版。
- 增加 `SYSTEM / LIGHT / DARK` 三态主题和 DataStore 持久化。
- 增加统一玻璃背景、卡片、按钮、状态胶囊、悬浮导航和分段进入动画。
- 保留通知监听、设备配对、同步队列、记录筛选和 Debug 样本业务行为。
- Debug 服务地址通过本机 Gradle 属性 `PAYMENT_DEBUG_SERVER_URL` 注入；源码默认不再保存局域网固定 IP。
- Android 版本：`versionCode=5`，`versionName=1.4.0-dev`。

## 验证结果

执行：

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug assembleRelease assembleDebugAndroidTest
```

结果：`BUILD SUCCESSFUL`，Debug、Release、AndroidTest APK 均已生成。

Release 保持禁止明文流量，Debug 测试入口仍由独立 source set 隔离。

## 产物

- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`
- 大小：18,247,064 bytes
- SHA-256：`B5D492AF38C999FF56934DFF7EFEB5FFE9A8D4E7722EE253C8138416488B7699`

## 本地服务地址

可在用户级或项目级 Gradle 属性中配置：

```properties
PAYMENT_DEBUG_SERVER_URL=http://HOST:8080
```

未配置时 Debug 默认使用 `http://localhost:8080`；Release 默认值使用 HTTPS，且网络安全配置继续禁止明文流量。
