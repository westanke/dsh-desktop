# 设备身份上报：加一行请求头，让桥知道你是谁

> 来自 `dsh-remote-bridge` 的投单需求 · 2026-10-23
> 触发原因：鸿蒙手机在桥的设备表里被登记成「Android 设备」，桌面端同样拿不到型号

## 一句话

请在 HTTP 请求头里加一行 `X-Dsh-Device: <platform>/<model>`，桥就会自动把设备名
换成「平台 · 型号」，用户不必再手动改名。

## 桥这边已经做了什么（不需要你做）

- 桥在**认证路径**上顺带解析这个头，落库到 `devices.platform` / `devices.model`，
  并把配对时的占位名（`Mobile device`）自动改成「平台 · 型号」。
- 没有这个头时，桥会从 `User-Agent` 兜底判**平台**（认得 android / harmonyos /
  ios / ipados / web / windows / macos / linux）。
- 用户手工改过的名字永不被覆盖；自动生成的名字在设备上报新身份时仍会更新。
- 桥侧的 `devices/self` 会回传 `platform` / `model` / `nameSource` 三个字段。
- `PATCH /manage/devices/{id}` 新增只改名（body `{"name":"…"}`）—— 这是本机 WebUI
  管理面接口，不需要客户端实现。

## 你需要做的（客户端，约 1 行）

在所有**带鉴权**的请求上加一个头。桥的解析规则：

```
X-Dsh-Device: <platform>/<model>
```

- `platform` 必须是下列之一（大小写不敏感，桥会转小写）：
  `android` `harmonyos` `ios` `ipados` `web` `windows` `macos` `linux`
- `model` 可省。给了就原样保留（含空格），桥只做控制字符清洗与 60 字符截断。
- 不在白名单里的 `platform` 会被**整条丢弃**（自造的平台值会让界面显示一个
  不存在的标签，不如不显示）。

### 桌面端（Kotlin / OkHttp）—— 精确改动点

当前桌面端**已经**在 `UpdateChecker.kt` 给 GitHub API 设了 `User-Agent: dsh-desktop`，
但那个 UA 只发给更新服务器，**不发**给桥。给桥的请求在
`src/main/kotlin/io/github/westanke/dshdesktop/data/DshApi.kt`，两处：

**第一处**（约 649-653 行，所有 REST 请求的入口）：

```kotlin
private fun request(url: HttpUrl, authenticated: Boolean = true): Request.Builder {
    val builder = Request.Builder().url(url).header("Accept", "application/json")
    if (authenticated) {
        builder.header("Authorization", "Bearer ${requireNotNull(token) { "A device token is required." }}")
        // 新增：桥用它在认证路径上识别设备身份，把配对时的占位名「Mobile device」
        // 换成「Windows · 11」。platform 必须在桥的白名单里。
        builder.header("X-Dsh-Device", deviceIdentity())
    }
    return builder
}
```

**第二处**（约 741-743 行，WebSocket 连接）：

```kotlin
private fun connect() {
    if (closed.get()) return
    val request = Request.Builder()
        .url(url)
        .header("Authorization", "Bearer $token")
        .header("X-Dsh-Device", deviceIdentity())   // 新增
        .build()
    // ...
}
```

**辅助函数**（放 `DshApi.kt` 内或 `data/ApiExt.kt`）：

```kotlin
private fun deviceIdentity(): String {
    val os = System.getProperty("os.name").orEmpty()
    val platform = when {
        os.startsWith("Windows", ignoreCase = true) -> "windows"
        os.startsWith("Mac", ignoreCase = true) -> "macos"
        else -> "linux"
    }
    // 型号用发行版/版本号，比 "unknown" 有用：Windows 11 / macOS 15.1 / Linux 6.8
    val model = System.getProperty("os.version").orEmpty()
    return if (model.isBlank()) platform else "$platform/$model"
}
```

`os.version` 在 Windows 上是内核版本号（`10.0`），不是「11」；若想显示得更好看，
可以自己按 `os.version` 映射成营销名。**不映射也完全可以**，桥不会校验型号内容。

### 鸿蒙 / 安卓侧

已分别单独投单（`dsh-companion-harmony` 与 `dsh-companion` 的 `docs/inbox/`），
此处不重复。

## 验收方式

1. 装上新桥（2.6.1 起，含本改动），重启桥。
2. 桌面端连一次，然后在本机 WebUI 的「设备」页看：
   - 设备名变成 `Windows · 10.0` / `macos · 15.1` 这类形式；
   - 名字旁边有「自动识别」徽标；
   - 副标题里能看到平台与型号。
3. 也可以直接打 API 验：

```sh
curl -H "Authorization: Bearer <token>" \
     -H "X-Dsh-Device: windows/10.0" \
     http://127.0.0.1:3090/api/v1/devices/self
# → {"id":"…","name":"Windows · 10.0","platform":"windows","model":"10.0","nameSource":"detected",…}
```

## 兼容性

- **不加上这个头也完全能用**：桥的 UA 兜底仍能认出平台（OkHttp 默认 UA 是
  `okhttp/x.y.z`，里面没有平台信息，所以桌面端**只能靠这个头**才有平台名；
  认不出时名字保持占位名，不会瞎猜）。
- 老桥（< 2.6.1）会忽略这个未知头，不会报错。
- 桥的响应新增三个**可选**字段（`platform` / `model` / `nameSource`），
  Kotlin SDK 的 `DeviceView` 已同步为可空默认值，序列化兼容。

## 背景（为什么会有这个需求）

用户 2026-10-23 从鸿蒙侧反馈：手机在桥的设备表里显示成「Android 设备」，而且
表里躺着 8 条一模一样的「Android 设备」，谁也认不出谁。

根因不在客户端 —— 问题在桥的**配置文本路径**：生成 `DSH1:` 文本时 `deviceName`
缺省落硬编码 `'Android 设备'`，并立即登记入库。那时桥对「将来谁来用这个令牌」
一无所知 —— 这是时序问题，不是参数问题。所以桥改成「先有连接，再有身份」：
配对时给平台无关的占位名（`Mobile device`），设备第一次连上来时再识别。

桥侧改动已本地提交（`2c5893b`），未发版未部署。

## 实现回执（workbuddy 回填，2026-10-24）

- **改动**：`data/DshApi.kt`（874→916）——`request()` 带鉴权分支与
  `EventSubscription.connect()` 两处加 `X-Dsh-Device` 头；新增顶层
  `deviceIdentity()`（os.name → windows/macos/linux，model 取 os.version，
  与交接单给的原值一致，没做营销名映射）。免鉴权的配对/health **刻意不加**
  （那一刻没有设备可标识，桥也只认认证路径）。
- **顺带**：`DeviceView` 补 `platform` / `model` / `nameSource` 三个可空字段
  （`ignoreUnknownKeys` 早已开着，老桥无感）；设置页设备卡加一行「平台 / 型号」，
  桥没回传整行不画。
- **用例**：`tools/verify-parity.sh` +5（platform 三态映射 / os.name 大小写不敏感 /
  版本全空白只给平台），合计 133 项；build / docs-check 32/32 / test.sh 219 全绿。
- **提交**：见 git log（`feat(net): 设备身份头 X-Dsh-Device`，未推送未打 tag）。
- **未真机验证**：需装新桥 2.6.1（桥侧 `2c5893b` 未发版）连一次，在本机 WebUI
  设备页确认名字变「平台 · 型号」+「自动识别」徽标；或按交接单的 curl 直接打
  `devices/self` 看回传。
