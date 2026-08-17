# 自动记账 App 设计文档

- 日期：2026-08-18
- 状态：待用户复核
- 交付形态：Android 手机 App（自用 / 小范围）

## 1. 目标

自动记录微信支付、支付宝的付款/收款，免手动记账。以**通知监听**为主通道，**账单 CSV 导入**为对账兜底，辅以手动补录、预算管理、统计图表、消费自动分类。

## 2. 范围与规模

- **用途**：自用 / 小范围，不上架应用商店，不做多用户、不做云端同步（第一阶段）。
- **明确不做（YAGNI）**：无障碍服务采集（有微信/支付宝封号风险，已排除）；服务端；账号体系；多设备同步；OCR 截图识别；跨币种。

## 3. 技术栈

- 语言：Kotlin
- UI：Jetpack Compose + Material 3
- 架构：MVVM + Repository + Room（本地 SQLite）
- 依赖注入：Hilt
- 网络：OkHttp（AI 兜底分类直连 DeepSeek API）
- 序列化：kotlinx.serialization（AI 请求/响应 JSON）
- 安全存储：Android Keystore + EncryptedSharedPreferences（存 API key）
- 构建：Gradle（Kotlin DSL），`minSdk 26`（Android 8.0），`targetSdk 35`
- 交付：GitHub 私有仓库 + GitHub Actions 云端编译 APK（固定签名，安装分发见 §12）

## 4. 架构总览

单模块应用，分层：

```
UI (Compose + ViewModel)
   ↓
Domain（纯 Kotlin，可 JVM 单测）
   ├─ NotificationParser   通知文本 → 金额/方向/商户
   ├─ CsvParser            微信/支付宝 CSV → 交易记录
   ├─ DedupGuard           去重
   └─ Categorizer          规则引擎 + AI 兜底
   ↓
Data (Repository + Room DAO)
   ↓
采集源：NotificationListenerService ｜ CSV 导入 ｜ 手动录入
```

**设计原则**：所有「解析 / 分类 / 去重」逻辑做成不依赖 Android 的纯 Kotlin 领域层，用 JVM 单元测试覆盖——这是本项目最容易出错、最需要测的部分。

## 5. 数据模型

### Transaction（交易记录）
| 字段 | 类型 | 说明 |
|---|---|---|
| id | Long | 主键，自增 |
| amountCents | Long | 金额，单位「分」，正数（避免浮点误差） |
| type | Enum | EXPENSE / INCOME / NEUTRAL（不计收支） |
| merchant | String? | 商户 / 交易对方 |
| note | String? | 备注 |
| categoryId | Long? | 分类外键，null = 未分类 |
| source | Enum | WECHAT_NOTIFICATION / ALIPAY_NOTIFICATION / WECHAT_CSV / ALIPAY_CSV / MANUAL |
| transactionTime | Long | 交易时间（epoch ms） |
| createdAt | Long | 入库时间（epoch ms） |
| dedupKey | String | 去重键，唯一索引 |
| needsReview | Boolean | 解析不确定，待人工确认 |

### Category（分类）
`id, name, icon, type(EXPENSE/INCOME), sortOrder`。内置常用分类：餐饮、交通、购物、日用、娱乐、医疗、教育、居住、通讯、转账红包、收入、其他。

### Rule（分类规则）
`id, categoryId, matchType(MERCHANT_EXACT/CONTAINS/REGEX), pattern, priority`。规则可在 App 内编辑。

### Budget（预算）
`id, categoryId?(null=总额), amountCents, month(YYYY-MM)`。按月按分类（或总额）预算。

### 应用设置（非 Room，EncryptedSharedPreferences）
`ai_base_url`、`ai_model`、`ai_api_key`（Keystore 加密）。默认 `base_url=https://api.deepseek.com`、`model=deepseek-v4-flash`。

## 6. 组件设计

### 6.1 通知监听（自动采集主通道）

`NotificationMonitorService : NotificationListenerService`

1. **Manifest 声明**：`BIND_NOTIFICATION_LISTENER_SERVICE` 权限 + `android.service.notification.NotificationListenerService` intent-filter，`android:exported="true"`（Android 12+ 强制）。
2. **授权引导**：启动时检测是否已授权，未授权跳 `ACTION_NOTIFICATION_LISTENER_SETTINGS`（该权限是系统级，只能用户手动开关，不能运行时申请）。
3. **解析流程**（`onNotificationPosted`）：
   - 包名过滤：`com.tencent.mm`（微信）、`com.eg.android.AlipayGphone`（支付宝）；
   - 合并 `EXTRA_TITLE / EXTRA_TEXT / EXTRA_BIG_TEXT / EXTRA_SUB_TEXT / EXTRA_SUMMARY_TEXT / EXTRA_TEXT_LINES` 全部文本字段（金额/商户常散落在不同字段）；
   - **归一化**：全角→半角（`￥→¥`、`，→,`、`．→.`）后再匹配；
   - **金额提取**多级降级：`[¥￥]\s*(\d+(\.\d{1,2})?)` → `(\d+(\.\d{1,2})?)\s*元` → `(?:金额|收款|付款|转账|红包|到账|成功)[：:\s]*(\d+\.?\d*)`；
   - **收支方向**关键词判定：收入 `收款|到账|转账|红包|已收钱|收到|转入`，支出 `已支付|支付成功|付款|消费|转出|扣费`；
   - 跨行文本用 DOTALL 匹配；
   - 排除干扰：`积分`/`商家积分`/`花呗还款`/`账单提醒` 等非支付类通知。
4. **去重**：同一笔微信会推多条通知。用 `sbn.key` + 复合 key（包名 + 时间窗口 5s + 金额）双保险；`dedupKey` 唯一索引兜底。
5. **失败兜底**：解析不出金额时不丢弃，置 `needsReview=true`，在 App 内提示用户补录。

### 6.2 账单导入（CSV 对账兜底）

两个独立解析器，编码/表头按调研结论处理：

| 维度 | 微信 | 支付宝 |
|---|---|---|
| 编码 | UTF-8 **带 BOM**（`utf-8-sig`，首字段残留 `﻿` 需去除） | **GBK** |
| 表头定位 | 前若干行是元数据，扫描到首列 `交易时间` 的行作为表头（不硬编码行号） | 前 ~24 行元数据跳过，`#` 开头行为注释 |
| 金额 | `¥1,200.50`，去 `¥`/`￥` 前缀 + 千分位逗号 | 纯正数 `50.00` |
| 收支 | `收/支` 列：收入 / 支出 / 中性交易 | `收/支` 列：支出 / 收入 / 不计收支 |
| 关键列 | 交易时间、交易类型、交易对方、商品、收/支、金额、支付方式、交易单号（按字符串处理） | 交易时间、交易分类、交易对方、商品说明、收/支、金额、收/付款方式、交易订单号 |

导入后按 `dedupKey` 与库内记录去重，新增记录走同一分类引擎。CSV 是对账锚点——保证通知漏记时月底导入能补全。

### 6.3 分类引擎（规则优先 + AI 兜底）

1. **规则引擎（离线、免费、快）**：按 `Rule` 表匹配（商户名精确/包含/正则），命中即归类，覆盖高频场景（如「瑞幸/星巴克→餐饮」）。
2. **AI 兜底（仅未命中时）**：
   - 端点：`POST https://api.deepseek.com/chat/completions`（OpenAI 兼容），`model=deepseek-v4-flash`；
   - 结构化输出：`response_format:{"type":"json_object"}`，提示词含「json」关键词 + 固定枚举示例（如 `{"category":"餐饮"}`）；
   - DeepSeek 仅支持 json_object（不做严格 schema），**分类枚举在客户端校验**；返回非法值重试一次，再失败降级为未分类；
   - 可能偶发返回空 content，重试后仍空则降级；
   - 降级链：规则命中 → 不调 AI；未命中 → 调 DeepSeek；无 key / 断网 / 超时 → 置 `needsReview`，绝不阻塞记账。

### 6.4 手动记账

收支类型 + 金额 + 分类 + 备注，走同一分类引擎。

### 6.5 预算

按月按分类（或总额）设定预算，超支提醒。

### 6.6 统计图表

分类占比饼图 + 月度收支趋势折线图（Compose Canvas 自绘，不引重型图表库）。

## 7. 数据流

```
微信/支付宝支付 → 通知 → 过滤包名 → 归一化+正则解析 → 去重 → 分类(规则→AI) → 入库
CSV 文件 → 选择 → 探测编码/定位表头 → 逐行解析 → 去重 → 分类 → 批量入库
手动录入 → 表单校验 → 分类 → 入库
```

## 8. 错误处理

- 通知解析失败 → `needsReview=true` + App 内补录入口，不静默丢弃；
- CSV 编码/表头探测失败 → 逐行容错，跳过坏行并报告跳过条数；
- AI 超时/限流 → 退避重试，最终失败降级为未分类，不阻塞；
- ROM 杀后台 → `onListenerDisconnected()` 里 `requestRebind()` + 组件 enabled 状态切换触发重绑 + 引导页授权清单（见 9）。

## 9. 安全

- **API key 绝不写进 APK**（会被反编译提取）。用户在「设置」里填自己的 DeepSeek key，用 Android Keystore 加密后本地存储；
- 通知监听是高危权限，数据全部本地存储，明确告知用途，不上传任何账单数据到第三方。

## 10. 已知限制（必须如实告知用户）

国产 ROM（小米 MIUI/HyperOS、华为 EMUI/HarmonyOS、OPPO ColorOS、vivo OriginOS）激进杀后台会让通知监听静默失效。应对分三层：

1. 引导页一次性走完「通知使用权 + 自启动 + 电池优化白名单」三项授权；
2. `onListenerDisconnected()` 里 `requestRebind()` + 组件 enabled 切换触发重绑；
3. 前台服务保活（仅提升优先级，不保证）。

**正因通知不可靠，才必须配 CSV 导入兜底对账**。微信通知文案无官方公开资料，随版本/ROM 变化，需在目标真机上 dump 一条真实通知做解析基线。

## 11. 测试策略

- **单元测试（重点）**：通知正则解析器（用真实文案样本）、CSV 解析器（两种编码样例文件）、去重逻辑、规则引擎、AI 结构化输出解析（mock HTTP 层）；
- Room DAO 测试（Robolectric）；
- CI：GitHub Actions 每次 push 跑 `./gradlew test`。

## 12. 构建、签名与安装分发

### 12.1 签名（最关键，否则升级装不上）

- 一次性生成 **release keystore**（keytool，RSA 2048，有效期 ≥ 30 年），keystore 文件 base64 编码 + 全部密码写入 GitHub Secrets（仓库为私库，可控）；
- Actions 每次编译用**同一把 keystore** 签名 → 签名固定，后续升级安装无缝覆盖；
- ⚠️ **绝不能**用 runner 每次自动生成的 debug keystore：每次编译签名都不同，升级时报 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`，只能卸载重装，**本地记账数据全丢**；
- 启用 v1 + v2 签名（apksigner 默认行为），兼容国产 ROM 安装器。

**Gradle 签名配置要点**（`app/build.gradle.kts`，密钥从环境变量读取——CI 由 Secrets 注入，本地构建时手动设置同样变量即可）：

```kotlin
android {
    signingConfigs {
        create("release") {
            storeFile = rootProject.file(System.getenv("KEYSTORE_FILE") ?: "keystore.jks")
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
            keyAlias = System.getenv("KEY_ALIAS") ?: ""
            keyPassword = System.getenv("KEY_PASSWORD") ?: ""
        }
    }
    buildTypes {
        release { signingConfig = signingConfigs.getByName("release") }
    }
}
```

⚠️ release 未配置签名时 Gradle **不会报错**，只会输出 `app-release-unsigned.apk`（装不上）。CI 里必须：注入上面 4 个变量 + 构建后 `apksigner verify` 校验签名，防止发布未签名产物。

### 12.2 产物与分发渠道

Actions 编译产物双通道：
1. workflow artifact —— 调试用（注意 **90 天过期**）；
2. **GitHub Releases** —— 正式版（版本号 + 更新说明），永久保留。

国内下载 GitHub 慢/失败，安装按可靠性排序：

| 方式 | 场景 | 说明 |
|---|---|---|
| 局域网直装 | 家里/办公室（首选） | 电脑 `python -m http.server 8000`，手机同 WiFi 浏览器访问 `http://电脑IP:8000` 下载安装，零依赖、私密 |
| USB `adb install` | 开发调试 | 需开 USB 调试，最可靠但每次插线 |
| 蒲公英/网盘扫码 | 外网远程 | 上传 APK 生成二维码，手机扫码安装 |

### 12.3 手机端首次安装（国产 ROM 拦截清单）

1. **未知来源授权**：Android 8+ 按来源应用单独授权——浏览器、文件管理器、微信各自开启「允许安装未知应用」；
2. **国产 ROM 额外拦截**：
   - 小米 MIUI/HyperOS：部分机型要求**登录小米账号**才能安装未知来源应用；
   - 华为 EMUI/HarmonyOS：需关闭「**纯净模式**」（鸿蒙 NEXT 机器直接不兼容，见 §10 前置确认）；
   - OPPO ColorOS / vivo OriginOS：风险提示，确认即可；
3. 装完打开 → 引导页走完通知使用权 + 自启动 + 电池白名单（§9）。

### 12.4 App 内更新（可选增强，Phase 6）

- 设置页「检查更新」：请求 GitHub Releases API → 对比 VersionCode → 下载到 app 私有目录 → FileProvider + `REQUEST_INSTALL_PACKAGES` 安装；
- 下载存私有目录，**无需存储权限**（Android 10+）；
- 更新源 URL 可配置（GitHub 慢时指向网盘直链）。

## 13. 首次启动引导与 ROM 授权清单

### 13.1 引导页流程（四步，可跳过、可重开）

1. **用途与隐私声明**：数据全本地存储；通知仅用于解析记账、不上传；AI 分类会把商户名发给 DeepSeek，可关闭（纯本地规则模式）；
2. **通知监听授权**：检测 `NotificationManager.getEnabledListenerPackages()` 是否包含本应用包名 → 未授权跳 `ACTION_NOTIFICATION_LISTENER_SETTINGS` → 返回后自动复检（授权后服务可能需重启应用才生效，页面需提示）；
3. **自启动 + 电池白名单**：按下方 ROM 路径表逐项引导，每项配「我已完成」勾选（这两项**无通用 API 可检测**，只能用户确认）；
4. **完成** → 主界面。设置页保留「重新打开引导」入口（ROM 升级/恢复出厂可能重置授权）。

### 13.2 各 ROM 授权路径（随系统版本略有差异，以实际为准）

| ROM | 自启动 | 电池 / 后台 |
|---|---|---|
| 小米 MIUI/HyperOS | 设置 → 应用设置 → 授权管理 → 自启动 | 安全中心 → 应用电量 → 省电策略 → 无限制 |
| 华为 EMUI/HarmonyOS | 设置 → 应用 → 应用启动管理 → 手动管理（三项全开） | 设置 → 电池 → 更多电池设置 → 休眠时始终保持网络连接 |
| OPPO ColorOS | 设置 → 应用管理 → 应用列表 → 自启动 | 设置 → 电池 → 更多设置 → 关闭「睡眠待机优化」 |
| vivo OriginOS | i管家 → 应用管理 → 权限管理 → 自启动 | 设置 → 电池 → 后台耗电管理 → 允许后台高耗电 |
| 三星 One UI | 设置 → 电池 → 后台使用限制 | 设置 → 电池 → 后台使用限制 |
| 原生 Android | 设置 → 应用 → 电池 → 无限制 | — |
