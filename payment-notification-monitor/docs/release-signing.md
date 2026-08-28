# Android Release signing

- Production package: `com.xhulong.paymentmonitor`
- Alias: `payment-monitor`
- Keystore and passwords must stay outside Git.
- The local `signing.properties` file points to the offline keystore and is ignored.
- Back up the keystore and password in two encrypted, offline locations before publishing.
- Record the signing certificate SHA-256 in the server production secret/configuration.

Build and verify:

```powershell
$env:JAVA_HOME = 'D:\toolbox\Android Studio\jbr'
.\gradlew.bat :app:assembleRelease

& "$env:LOCALAPPDATA\Android\Sdk\build-tools\34.0.0\apksigner.bat" `
  verify --verbose --print-certs `
  .\app\build\outputs\apk\release\app-release.apk
```

The same keystore must sign every future production upgrade. Losing it prevents
Android from installing upgrades over existing installations.
