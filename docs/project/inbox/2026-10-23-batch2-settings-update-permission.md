# 第二批对齐需求：通用设置命名空间表单 / 桌面检查更新 / 新建会话选权限档位

- 日期：2026-10-23
- 提出方：lead（负责对标找差距、写清需求，**不写实现代码**）
- 执行方：workbuddy（负责写代码对齐）
- 状态：**待实现**（本文件即交接单，做完请在文末「实现回执」回填提交号并同步文档）
- 依据：[`../PARITY-VS-COMPANION-2.2.52.md`](../PARITY-VS-COMPANION-2.2.52.md) 的 P2-1 / P2-2 / P3
- 对标源版本：dsh-companion **v2.2.52**（+未发版提交），dsh-remote-bridge **v2.6.0**
- 桌面端基线：**1.0.2**（`version.txt`），第一批已在 `11a55b2` 落地、`c16c410` bump 版本，**未打 tag、未发 Release**

> 分工约定：lead 只对"差距是什么、协议长什么样、验收口径"负责；具体类怎么拆、函数怎么命名由 workbuddy 按本仓 `AGENTS.md` 宪法决定。下面给的 APP 源路径是**参照实现**，不是要照抄的 API。

---

## 共同门禁（每一项都要过，再交）

1. `bash tools/build.sh` 编译通过；
2. `bash tools/docs-check.sh` 全绿（当前 30 项）；
3. `tools/verify-markdown.sh` / `verify-files.sh` / `verify-history.sh` 全过——本批新增的**纯函数**（schema 摊平、版本比较、SHA256SUMS 解析、链接/档位判定）尽量加进对应 verify 脚本，本仓无 JUnit，靠纯函数脚本顶回归；
4. 遵守 800 行/文件预算，超了先拆/入 `TECH-DEBT.md`，data 层保持纯函数、禁 import Compose；
5. 同提交回填 `CODEMAP.md`（A.2 文件表 + 相关 B 段）、`PARITY.md`、必要时 `PITFALLS.md` + AGENTS §7 同名索引、`CHANGELOG-DEV.md`；
6. 真机装 1.0.2 deb 冒烟（连一台真 bridge v2.6.0 验证）；
7. **不推送、不打 tag、不发 Release**——1.0.2 的 tag/Release 仍等用户明确同意。

---

## P2-1 · schema 驱动的通用设置命名空间表单

### 这是什么、为什么要做

桥 v2.6.0 新开放两个端点，让客户端**不必为每个插件写死页面**，按插件设置文档自带的 schema 自动渲染表单。装一个新插件就可能多一个命名空间，强类型化 = 退回"N 个插件 N 个页面"。APP 已实现（提交 `691b5d5`），桌面端 `data/` 目前 grep `namespaces` **零命中**。

### 协议（以 bridge OpenAPI 为准，已核对 `dsh-remote-bridge/docs/api/openapi.yaml:582-643`）

- `GET /api/v1/settings/namespaces` → 需 scope **`settings.read`**；200 返回：
  - 顶层 `writable: Boolean`（整份设置文档是否可写）、`hasDocument: Boolean`（false 时 `namespaces` 可为空，是**内核还没建文档**，给空态不报错）、`namespaces: [...]`；
  - 每项：`ns`、`revision`、`schema`、`value`、`user`、`writable`、`reason`（`HOST_READONLY` / `HIGH_RISK_NAMESPACE`）、`applies`、`autoGenerate`、`secrets: [{path:[...], set:Boolean}]`；
  - 密钥永不出宿主：内核已脱敏，桥还把键名匹配 `apiKey|secret|token|password|credential|apiKeyEnv` 的串值掩成 `"***"`。
- `PATCH /api/v1/settings/namespaces/{ns}` → 需 scope **`settings.write`**，单次最多 32 条 op；体：`{ops:[{op:"set", path:[...], value:<JsonElement>}], expectedRevision:Int?}`；成功返回**更新后的单个命名空间对象**（形状同 list 里一项）。
- 错误码（**以代码字符串判，不要只认 HTTP 码**）：
  - 冲突是 **HTTP 409 `SETTINGS_CONFLICT`**（⚠️ 早期需求口误成 412，以 OpenAPI 与 APP 实现为准）；
  - 只读命名空间 403 `SETTINGS_NAMESPACE_READONLY`；op 非法 400 `SETTINGS_OPS_INVALID`；宿主无设置服务 **503 `SETTINGS_UNAVAILABLE`**；缺 scope 403 `SCOPE_REQUIRED`。

### APP 参照实现（路径均在 dsh-companion 仓）

- wire 模型 + 纯函数映射：`/media/wangke/OFFICE/workspace/dsh-companion/app/src/main/java/io/github/westanke/dshcompanion/data/SettingsNamespaces.kt`（401 行，跨仓绝对路径）
  - `SettingsSchema.fields(ns)`：schema → 字段描述 `SettingsFieldSpec`，控件六类 `Enum/Boolean/Number/Text/Secret/ReadOnly`；
  - **关键坑：内核发的 schema 是 `schemastery` 引用图，不是标准 JSON Schema**（`{uid, refs:{id:{type,dict,list,value,meta}}}`，enum 是 `union`→多个 `const`）。必须先认 `properties`，取不到再用内置 `Schemastery.expand()` 摊平（最多三层），摊不出就回只读，**绝不猜类型**（猜密钥成输入框最坏）。对应坑位「内核 schema 是 schemastery，不是 JSON Schema」。
  - 密钥三判据任一命中即只读、**绝不提交**：`secrets[].path` 首段命中 / 值是 `"***"` / schema `meta.role=secret`（摊平成 `x-secret`）。
  - 数字认 `minimum/maximum` 也认简写 `min/max`，integer 区分；`clampSettingsNumber()` 夹取，min>max 原样返回不抛。
- 入口过滤（用户原话："不能在 App 上起到作用的都没必要显示"）：`forNativeApp()` 只留同时满足 ① 不在 `WEBUI_ONLY_NAMESPACES`（`ui-theme/ui-chat/ui-conversation/ui-settings/ui-settings-general`，纯 WebUI 键，Compose 自绘界面一个都不读，显示=给一排拨了没反应的开关）② `writable==true` ③ `hasEditableField()`（至少一个非 ReadOnly 字段；Secret 算可改）的命名空间。
- scope 判据：`canWriteSettingsNamespaces()` = `"settings.write" in scopes`。**配对默认五档（chat.read/chat.write/files.read/files.write/settings.read）里没有 `settings.write`**，所以"能看不能存"是常态不是故障；读侧没有 `settings.read` 时**直接不发请求**（发了必 403）。scopes 只能来自设备自身 `device.scopes`，**不许按桥版本号猜**。
- ViewModel（`/media/wangke/OFFICE/workspace/dsh-companion/app/src/main/java/io/github/westanke/dshcompanion/HarnessNamespacesViewModel.kt`，152 行，跨仓绝对路径）——**三层降级照搬**：
  1. 数据/快照层 `loadSettingsNamespaces()`：`runCatching` 全吞退 null（这条通道可选，挂了不能拖垮整次连接/快照）；
  2. 状态层：结果为 null 就**不画入口**（老桥零变化、零打扰）；
  3. 用户主动刷新：`isAbsentOnBridge()` = 404 或 403 或码在 `{ROUTE_NOT_FOUND,SCOPE_REQUIRED,SCOPE_INVALID,FORBIDDEN}` → 静默隐藏入口只留诊断日志；**刻意不含 503**（装了路由但不可用是真故障，要报错）。
  - 409 冲突：不把错误码丢用户，**就地重拉**整表最新值 + 提示"已在别处被修改，已刷新"；
  - 成功后用 PATCH 返回的单对象**替换列表里同 ns 那一项**，不整表重拉（避免详情闪烁）。

### 桌面端落点建议（workbuddy 自定，仅供定位）

- 协议模型：`src/main/kotlin/io/github/westanke/dshdesktop/data/` 下新增（如 `SettingsNamespaces.kt`），用仓里现成的 kotlinx.serialization + `JsonElement` 承接，别强类型化；网络调用挂到 `data/DshApi.kt` 的 client（注意它是上游移植，按 `@patch`/隔离规矩，优先在自己的扩展文件加，不污染源码）。
- scopes 已在 `AppState.kt:363/441` 从配对 profile 拿到（`scopes = paired?.device?.scopes ?: profile.scopes`），直接复用。
- 表单 UI：`ui/` 下新页面（参照 `ui/ModelsScreen.kt` / `ui/PluginsScreen.kt` 的设置页骨架），enum 下拉、boolean 开关、number 步进（带 min/max/integer）、text 输入、Secret 只读显示"已设置/未设置"可重填、object/array/摊平失败 → 只读折叠 JSON；入口挂 `ui/SettingsScreen.kt`。
- 纯函数部分（schema 摊平、字段映射、入口过滤、clamp、humanize）全部放 data 层不碰 Compose，喂样本进 verify 脚本。

### 验收

- [ ] 连真 bridge v2.6.0：入口只列"可写 + 有可编辑字段 + 非纯 WebUI"的命名空间；改一个 string/enum/boolean/number 值保存成功，列表就地更新不闪。
- [ ] 密钥位只读、显示已设置状态，编辑过的其它字段提交体里**不含**密钥 path（抓包/日志确认）。
- [ ] 制造 409（另一处先改）→ 自动重拉 + 友好提示，不出现裸 `SETTINGS_CONFLICT`。
- [ ] 老桥/404、缺 settings.read/settings.write(403) → 入口消失或置灰，**无红条/无 Snackbar 报错**；503 → 有明确错误提示。
- [ ] schemastery 形态的真实命名空间能渲染出可编辑控件（不是全只读）；标准 JSON Schema 形态也认。
- [ ] data 纯函数有 verify 脚本用例；docs-check 30 项全绿；CODEMAP/PARITY/CHANGELOG 同步。

---

## P2-2 · 桌面形态的"应用内检查更新"（只做检查 + 下载 + 指引，不做热自更新）

### 这是什么

APP 有应用内更新（提交 `2aa6f37`），桌面端设置「关于」目前只有静态版本（`ui/SettingsScreen.kt` 关于区）。**桌面不能照抄 APK 那套 FileProvider 安装器**，首版只做：查最新 Release → 有新版则展示版本号/更新说明/产物 → 一键下载到本地 + 校验 SHA-256 → 给出明确安装指引（Linux 调系统安装器/`dpkg`，Windows 引导 msi/zip），**不做进程内热自更新、不自动提权**。

### APP 参照实现（可平移的纯逻辑，UI 要重写）

- `/media/wangke/OFFICE/workspace/dsh-companion/app/src/main/java/io/github/westanke/dshcompanion/data/UpdateChecker.kt`（294 行，跨仓绝对路径）+ 同仓 `ui/UpdateCheckSection.kt`（394 行）+ 测试 `data/UpdateCheckerTest.kt`。
- **渠道按界面语言分流**（用户原话："按语言检查 中文 gitee。英文 github"）：`zh*` → Gitee，其余 → GitHub；
  - Gitee：`https://gitee.com/api/v5/repos/<owner>/<repo>/releases/latest`（无 header）；
  - GitHub：`https://api.github.com/repos/<owner>/<repo>/releases/latest`，**必须带 `User-Agent` 否则 403**（实测）。
  - ⚠️ **本仓 owner/repo 与 APP 不同**，用桌面仓自己的：Gitee `westanke/dsh-desktop`、GitHub `westanke/dsh-desktop`（已核对 `git remote -v`）。GitHub 是 3 小时镜像从站，可能比 Gitee 晚，英文界面查不到刚发的中文版属设计权衡。
- Release 解析：`tag_name` / `name` / `body`(Markdown) / `prerelease` / `assets[{name, browser_download_url, size?}]`，`ignoreUnknownKeys=true`（两端字段集不同，Gitee asset 无 `size`）。
- **版本比较必须逐段比数字** `isNewerVersion(remote,current)`：字符串比较会让 `2.2.9 > 2.2.47`（真实事故）。当前版本读 `version.txt`（**1.0.2**），前导 `v` 与非数字分隔符用正则 `\d+` 只取数字段。
- 产物选择：按文件名后缀匹配，**别按下标**。桌面要选的是当前平台产物——Linux `*.deb`（区分 full/lite 与 amd64/arm64，建议按 OS 架构 + 已装的 full/lite 选对应包），Windows `*.msi` 或 `*.zip`；runtime `*.tar.xz` 是高级用户项，可放折叠区不强推。
- 完整性：每个 Release 同时带校验清单（APP 是 `SHA256SUMS.txt`；本仓 `dist/` 现有 `*.sha256` 单文件形式，以实际上架 Release 的资产名为准，两种都建议兼容：先找 `SHA256SUMS.txt` 按文件名匹配，找不到再找 `<包名>.sha256`）。分块读文件算 SHA-256（64KB buffer，不整包进内存），不符则保留错误、删掉/ quarantine 文件并明确提示，**不许安装/指引一个哈希不符的包**。
- 更新说明压平：`summarizeReleaseNotes(body,280)` 去 `**`/换行、截断补省略号（桌面可调大上限）。

### 桌面端特殊点

- 网络：桌面是普通 JVM + 仓内 OkHttp（确认 `lib/` 已有 okhttp，第一批图片已用取字节通路）；同样遵循"单一网络边界"，HTTP 客户端复用仓里现成实例，别新开一套。
- 安装动作（**只指引/调系统，不自行提权替换运行中的程序**）：
  - Linux：下载完用 `java.awt.Desktop.open(file)` 或 `xdg-open <deb>` 交系统软件中心；或展示 `sudo dpkg -i <file>` 命令让用户自己跑。**不要**静默 `pkexec`/`apt`。
  - Windows：下载完 `Desktop.open(msi)` 引导安装；zip 给"解压覆盖"指引。
  - 失败项（无 Desktop 支持、找不到对应架构产物）要明确文字提示 + 给 Release 网页链接兜底。
- 放置：设置「关于」版本号下加"检查更新"行，手动触发为主（首版可不做启动自动查，避免国内 Gitee API 频控/打扰）；检查中、已是最新、有新版、下载中(带进度，`Content-Length` 缺失时显示不确定进度而非假 0%)、校验失败、网络失败（含 GitHub 403/限流可读提示）各态要齐。
- 纯函数（channelForLanguage/isNewerVersion/parseSha256Sums/资产挑选/summarize）进 data 层 + verify 脚本，不依赖 AWT/Compose。

### 验收

- [ ] 中文界面查 Gitee、英文界面查 GitHub（切语言实测）；带 UA，GitHub 不 403。
- [ ] 造一个更高 tag（或临时夹具）→ 提示有新版并正确比较；当前 1.0.2 vs `1.0.2` 不报更新；`1.0.10` 正确判定新于 `1.0.2`（逐段比较用例）。
- [ ] 选对当前平台/架构/full-lite 的产物；下载后 SHA-256 校验通过才给安装指引；故意改错哈希 → 明确报错且不引导安装。
- [ ] Linux 下载 deb 后能 `xdg-open`/展示 dpkg 指引；无自动提权、无热替换。
- [ ] 网络失败、403 限流、无对应架构资产都有明确提示与 Release 页兜底链接。
- [ ] 纯函数有 verify 用例；docs-check 全绿；CODEMAP/PARITY/CHANGELOG 同步。

---

## P3 · 新建会话即选权限档位（顺手项，复用现有机能，不做第二套语义）

### 差距

APP v2.2.45（提交 `a344e3f`）起新建会话时可预选权限档位。桌面端 `ui/Sidebar.kt:136` 直接 `state.createSession(workspace.id)`，`AppState.kt:791` `createSession(workspaceId)` 建完是默认档，用户只能建完再去切输入区的 `PermissionPill`（权限状态已有：`AppState.kt:182 permissionState` / `184 permissionCatalog`）。

### 要求

- 在"新建会话"入口（工作区选择旁/新建对话框）加一个**档位选择**，候选来自现有 `permissionCatalog`（与 `PermissionPill` 同一份数据源，**不要硬编码第二套档位名单**）。
- 建会话成功后，若用户选了非默认档，走**现有的切换权限通路**应用该档（等价于建完执行一次权限切换/`/permission <preset>`），复用现成方法，不新造语义、不绕过 catalog。
- 默认档保持预选默认值，老用户无感；选择器要本地化文案（与 PermissionPill 现有文案一致）。
- APP 还把权限 Pill 挪到了顶栏——**这项不抄**（桌面输入区宽，属设计取舍，真机看完再说，不在本批范围）。

### 验收

- [ ] 新建会话时能选档位，建完会话权限即为所选（真机连真 bridge 确认生效，不只是 UI 选中）。
- [ ] 不选 = 默认档，行为与现在一致；档位候选与 PermissionPill 完全同源。
- [ ] docs-check 全绿；CODEMAP/CHANGELOG（PARITY 此项可记为打磨项）同步。

---

## 实现回执（workbuddy 完成后回填）

- P2-1：提交 ______，新增/改动文件 ______，真机 bridge 版本 ______，验收逐项 ☐→☑
- P2-2：提交 ______，产物/校验方案 ______，真机平台 ______
- P3：提交 ______
- 门禁结果：build ____ / docs-check ____ / verify-* ____ / 真机冒烟 ____
- 文档同步：CODEMAP ☐ PARITY ☐ PITFALLS ☐ CHANGELOG-DEV ☐
- 遗留问题 / 需 lead 再对协议的点：______
