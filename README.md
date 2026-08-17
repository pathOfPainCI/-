# 自动记账 App（AutoBookkeeping）

自动记录微信支付、支付宝的付款/收款。通知监听为主通道，CSV 导入对账兜底。
完整设计见 `docs/superpowers/specs/2026-08-18-auto-bookkeeping-design.md`。

## 构建（云端，零本地环境）

1. 推到 GitHub 私有仓库 `main` 分支 → GitHub Actions 自动编译；
2. 打 tag（`v0.1.0`）→ 自动发布 GitHub Release（含 APK）。

## 仓库 Secrets（必配，否则 CI 签名失败）

| Secret | 值 |
|---|---|
| `KEYSTORE_BASE64` | keystore.p12 的 base64 |
| `KEYSTORE_PASSWORD` | keystore 密码 |
| `KEY_ALIAS` | 别名（建议 `release`） |
| `KEY_PASSWORD` | 别名密码 |

生成 keystore（两种方式任选，仓库默认 PKCS12 格式）：

```bash
# 方式一：有 JDK
keytool -genkeypair -v -keystore keystore.p12 -storetype PKCS12 -alias release -keyalg RSA -keysize 2048 -validity 10950
# 方式二：无 JDK（Git for Windows 自带 openssl）
openssl req -x509 -newkey rsa:2048 -keyout key.pem -out cert.pem -days 10950 -nodes -subj "/CN=AutoBook"
openssl pkcs12 -export -out keystore.p12 -inkey key.pem -in cert.pem -passout pass:你的密码 -name release
```

Windows base64 编码（PowerShell）：

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("$PWD\keystore.p12"))
```

⚠️ keystore 一旦发布，升级安装依赖同一签名；**务必备份 keystore 文件**，丢失则无法覆盖升级。

## 本地构建（可选）

需要 JDK 17 + Android SDK（`local.properties` 指向 sdk.dir）。首次在项目根执行 `gradle wrapper` 生成 wrapper：

```bash
gradle wrapper --gradle-version 8.9
./gradlew :app:testDebugUnitTest   # 领域层单测（无需真机）
./gradlew :app:assembleDebug       # 构建 debug APK
```

## 目录结构

```
app/src/main/java/com/jizhang/app/
  domain/   纯 Kotlin 领域层（解析/去重/分类，可 JVM 单测，零 Android 依赖）
  data/     Room + Repository + DeepSeek 客户端
  service/  NotificationMonitorService + 开机自启
  ui/       Compose 界面（列表/统计/设置/引导页）
```
