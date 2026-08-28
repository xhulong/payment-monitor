# LuLuPay 码支付

LuLuPay Android 码支付通知监听与服务端同步客户端，使用 Kotlin、Jetpack Compose、
Room、WorkManager、Retrofit/OkHttp 和 Android Keystore 实现。

## 当前功能

- 监听微信 `com.tencent.mm` 和支付宝 `com.eg.android.AlipayGphone` 的支付候选通知。
- 解析标题、正文、BigText、TextLines、Ticker、SubText、InfoText、
  SummaryText 及可字符串化的全部 Extras。
- 区分收入、支出和方向待确认事件，并提取人民币金额。
- Room V2 保存本地记录和上传队列，未同步记录不受 500 条终态历史限制。
- 支持二维码或手动输入配对信息。
- 使用 Android Keystore AES-256-GCM 保存设备凭据。
- 使用 HMAC-SHA256、时间戳、Nonce 和凭据版本调用设备 API。
- 使用 WorkManager 批量上传、断网补传、指数退避和逐条 ACK。
- 前台服务运行时发送心跳；停止监听后不再接收新通知，已有队列仍可补传。
- Debug 版本包含四类支付样本；Release 版本不包含 Debug Receiver 和样本入口。
- Debug 版本支持阶段 C 原始通知 JSONL 采集；文件仅保存在 App 私有目录。
- `parserVersion=2` 已覆盖微信店员收款和支付宝商家收款真实通知。
- Debug 允许局域网 HTTP；Release 禁止明文流量。

## 项目信息

```text
Android Studio: D:\toolbox\Android Studio\bin\studio64.exe
项目目录:       D:\desktop\免签app\PaymentNotificationMonitor
应用包名:       com.example.paymentmonitor
minSdk:         23
targetSdk:      34
```

## 构建

中文路径下运行 JVM 单测可能造成 Gradle 测试类加载失败。当前使用纯英文 Junction：

```powershell
New-Item -ItemType Junction `
  -Path D:\pmandroid `
  -Target 'D:\desktop\免签app\PaymentNotificationMonitor'

$env:JAVA_HOME='D:\toolbox\Android Studio\jbr'
Set-Location D:\pmandroid
.\gradlew.bat testDebugUnitTest assembleDebug assembleRelease assembleDebugAndroidTest
```

产物：

```text
D:\desktop\免签app\PaymentNotificationMonitor\app\build\outputs\apk\debug\app-debug.apk
D:\desktop\免签app\PaymentNotificationMonitor\app\build\outputs\apk\release\app-release-unsigned.apk
D:\desktop\免签app\PaymentNotificationMonitor\app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk
```

## 本地 Docker 与真机联调

先启动服务端、PostgreSQL、Redis 和管理端：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass `
  -File D:\desktop\免签app\PaymentMonitorServer\scripts\Start-LocalDockerStack.ps1
```

执行可重复真机联调：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass `
  -File D:\desktop\免签app\PaymentMonitorServer\scripts\Invoke-AndroidDeviceE2E.ps1 `
  -DeviceSerial DEVICE_SERIAL `
-ServerUrl http://localhost:8080
```

脚本会构建并安装 Debug/Test APK，运行 Room 迁移与保留策略测试，完成四类样本上传、
断网恢复、设备禁用、重新配对和凭据版本递增验证。小米真机全新安装时可能出现 USB
安装确认，需要在手机上允许安装。

## 手动使用

1. 安装 Debug APK。
2. 打开应用，授予通知使用权；Android 13 及以上还需允许通知权限。
3. 在“同步”页扫描管理端二维码，或手动输入服务地址和 8 位配对码。
4. 在“监听”页开始监听。
5. 在“记录”页查看解析结果、上传状态和错误信息。

真实通知文字可能随微信、支付宝版本或商户产品变化；后续可根据命中候选通知保存的
原始字段继续补充解析规则。

## 阶段 C 真机采集

```powershell
cd D:\desktop\免签app\PaymentNotificationMonitor
.\scripts\phase-c\Start-PhaseCCapture.ps1 `
  -DeviceSerial f6zh89or49vorgin `
  -SessionId wechat-clerk-01 `
  -Scenario WECHAT_CLERK_INCOME `
  -StartMonitoring
```

完成真实操作后使用 `Stop-And-Pull-PhaseCCapture.ps1` 拉取，再运行校验和脱敏脚本。
原始目录 `research\phase-c\raw` 和自动生成目录不会提交 Git。
