# 阶段 C 真机通知采集

原始通知只写入 Debug App 私有目录，不上传服务端，也不提交 Git。

```powershell
.\Start-PhaseCCapture.ps1 `
  -DeviceSerial f6zh89or49vorgin `
  -SessionId wechat-clerk-01 `
  -Scenario WECHAT_CLERK_INCOME `
  -StartMonitoring
```

在手机上完成对应真实操作后执行：

```powershell
.\Stop-And-Pull-PhaseCCapture.ps1 -DeviceSerial f6zh89or49vorgin
.\Test-PhaseCCapture.ps1 -InputPath CAPTURE.jsonl
.\Convert-PhaseCCapture.ps1 -InputPath CAPTURE.jsonl
```

转换结果默认写入 `research/phase-c/generated`。逐条核对预期结果并完成脱敏后，
将 `reviewed=true` 的样本复制到 `app/src/test/resources/payment-fixtures/v2`。
