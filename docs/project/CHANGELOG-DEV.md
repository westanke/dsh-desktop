# 开发日志（CHANGELOG-DEV）

> 每版三节：**基于什么**（触发/前提）→ **干了什么**（文件级）→ **测了什么**（门验结果）。
> 失败与回退也要写——那是后来者最需要的部分。

---

## v1.0.3 · 发版（2026-10-24）

### 基于什么

用户「打包 推+发」。1.0.2 之后积了 11 个提交、四批活儿一并打进：
新建会话预选权限档位（P3）、应用内检查更新（P2-2，六态 + 双渠道逐段比版本）、
schema 驱动的通用设置命名空间表单（P2-1，schemastery 摊平三层降级）、
第三批 X9 阶段一 DSH1 配置导入、第四批纯逻辑测试机制
（公共断言库 + `test.sh` 统一入口 + 接进 docs-check 第 8 节）、
设备身份上报头 `X-Dsh-Device`。

### 干了什么

- `version.txt` 1.0.2 → 1.0.3，`VERSION_FALLBACK` 与 README 产物名同步。
- 本机打 amd64 完整包 + 轻量包 + 运行时压缩包；推 Gitee 主源与 AtomGit 备源打 tag，
  镜像到 GitHub 后由 `release.yml` 出 arm64 deb / Windows zip + msi 并建 Release，
  发布当场同步回 Gitee 主源。
- **Gitee 附件 1 GB 配额**：只保留最新一个完整版本，不够就删最旧发行版
  （GitHub 留历史），详见 `RELEASE.md` ⑥′。
- **发行物落地**：GitHub Release v1.0.3 14 个资产齐全、非 draft；Gitee 主源 Linux x64
  三件套 + `SHA256SUMS` + 源码包已落地（哈希流式比对全部成功），剩余 9 个 CI 产物由
  `mirror-sync` 每 3 小时补缺（跑机→Gitee 大包上传 240s 写超时的老毛病，先保 Linux 主线）。
  Gitee 附件 1GB 配额：删最旧的 v1.0.1 发行版腾位（GitHub 留历史）。

### 测了什么

- 门禁：`build.sh` 0 error；`docs-check.sh` 32/32；`test.sh` 219 项
  （verify-files 49 + verify-markdown 37 内置 + verify-parity 133）。
- **未真机验证**：X9 三条路径（带令牌/无令牌/坏文本）、第二批三项
  （命名空间 409 + 密钥、检查更新六态、档位胶囊）、设备身份头（需新桥 2.6.1）
  均待真机验收；装机冒烟由用户执行。

---

## 开发中 · 设备身份上报头 X-Dsh-Device（桥投单，未打 tag）

### 基于什么

`docs/project/inbox/2026-10-23-device-identity-header.md`：桥 v2.6.1 起在认证路径上
解析 `X-Dsh-Device: <platform>/<model>`，把配对占位名「Mobile device」换成
「平台 · 型号」。起因是鸿蒙手机在桥的设备表里被登记成「Android 设备」，
8 条同名记录谁都不认识谁。

### 干了什么

- `data/DshApi.kt`（874→916）：两处加头——`DshWorkspaceClient.request()` 的
  带鉴权分支（REST 全走这里）与 `EventSubscription.connect()`（WebSocket）；
  新增顶层 `deviceIdentity()`：os.name → windows/macos/linux（**桥的白名单值，
  写错会被整条丢弃，静默丢身份**），model 取 os.version。
- `DeviceView` 加 `platform` / `model` / `nameSource` 三个可空字段（老桥没有，
  `ignoreUnknownKeys` 早已开着，向前兼容）；设置页设备卡多一行「平台 / 型号」，
  桥没回传就不画。
- **免鉴权请求刻意不加**：配对与 health 那一刻还没有"设备"可标识，桥也只认认证路径。
- OkHttp 默认 UA 是 `okhttp/x.y.z`，没有任何平台信息——桌面端想有平台名只能靠这个头。

### 测了什么

- `tools/verify-parity.sh` 133 项（+5）：platform 映射三态 + os.name 大小写不敏感 +
  版本全空白只给平台；System 属性改完 finally 还原。
- `build.sh`、`docs-check.sh` 32/32、`test.sh` 219 项全绿。
- **未真机验证**：需装新桥 2.6.1（桥侧改动 `2c5893b` 未发版）后连一次，
  在本机 WebUI 设备页看名字变成「平台 · 型号」+「自动识别」徽标。

---

## 开发中 · 第三批 X9 阶段一：DSH1 配置导入（未打 tag）

### 基于什么

`docs/project/inbox/2026-10-23-batch3-crosslearn-ui.md` 的 X9 原本标着「先评估，
勿直接动手」。lead 出了独立评估（`对齐台账/X9-桌面多地址与DSH1导入可行性评估.md`），
结论拆两阶段：**阶段一导入先做**（消灭手填 URL + 长 token 的痛，与多地址零耦合），
**阶段二多地址选路暂缓**（台式固定工位价值低，等笔记本漂移的真实需求背书）。
用户 2026-10-23 拍板：做阶段一；**真机测试通过后再推 + 打 tag**。

### 干了什么

- `data/ConnectionShare.kt`（新，331 行）：从安卓 `ConnectionShare.kt` 移植的
  编解码纯函数。线格式 `DSH1:<Base64URL(JSON)>` 三端一致（生成端在 companion 仓
  `tools/emit-config.mjs`）。容错逐项照搬：空白/零宽/BOM、前缀大小写与任意位置、
  URL-safe 与标准两种字母表、缺 `=` 填充、尾部粘连标点、JSON 外裹文字的花括号提取。
  **桌面侧裁剪**：只做导入不做生成（UI 不暴露 `encode()`，它只服务往返测试）；
  没有 Host/Endpoint 选路模型，`toImported()` 只取第一个地址，其余地址
  `extraEndpoints` 计数告知、不静默丢弃。任何不合法输入返回带**具体原因**的
  `DecodeResult.Failure`，绝不抛异常——用户粘贴时已经离开电脑，失败即整个方案失败。
- `AppStateConnectionImport.kt`（新，67 行）：导入状态机。有令牌 → 走既有
  `connect(endpoint, null, importedToken)` 完整通路（health 即连通性验证，通过才落盘，
  令牌仍走 AES-GCM，落地后补拉一次 `currentDevice()` 防 deviceId 串主机）；
  无令牌 → 只填地址等配对码，刻意不落盘（scopes 依附于令牌）。
- `AppState.kt`（987→1014）：`connect()` 加 `importedToken` / `importNotice`
  两个可选参数；连接前 `teardown()` 清 client 缓存（顺带修掉"换地址重连会拿旧
  凭据"的潜在坑）；新增 `importNotice` 字段（常驻提示，不用 toast——导入结果
  有两部分，一闪就没）。
- `ui/ConnectScreen.kt`（175→249）：② 之后加「③ 粘贴 DSH1 配置导入」折叠区，
  多行粘贴框 + 导入按钮 + 常驻提示条（mutedSurface）。粘贴框**用后即清**
  （文本等同于钥匙，不留着被截图带走）。
- 踩到并记进 PITFALLS 的语义坑：**DSH1 的 `deviceName` 是分享端设备名，
  桌面 `profile.deviceName` 是本机名**，照搬"填好 deviceName"会让服务端看到
  本机叫「Pixel 9」。`toImported()` 刻意不映射它。

### 测了什么

- `tools/verify-parity.sh` **128 项**（+46）：编解码往返、微信换行/零宽/前缀
  任意位置/两种字母表/缺填充/尾标点、五类拒绝原因逐条、归一化（协议大小写/
  `/api/v1`/尾斜杠/空白）、`toImported()` 字段集合（无 deviceName）。
- `build.sh` 编译通过；`docs-check.sh` 32/32；`test.sh` 214 项全绿。
- **未真机验证**：导入需连真桥 v2.6.0 + companion 仓生成端产文本，
  验连通性验证、令牌落盘、无令牌分支——按用户指示，**真机通过后再推 + 打 tag**。

### 基于什么

`docs/project/inbox/2026-10-23-batch4-purelogic-testing.md`：三端自动化家底实测
——鸿蒙 1013 条 node 断言、安卓 66 个 `.kt` 测试、桌面 **0**（DEBT-001）。桌面不是
没防线（`verify-*.sh` 是有效的纯函数断言），但覆盖面靠 shell 撑着，Kotlin 纯函数
回归没有**同语言、可调试、可结构化**的载体。约定路线 A：**零新依赖**，
Kotlin `main` + 手写断言，不引 Gradle、不引 junit。

### 干了什么

- `tools/VerifySupport.kt`（新，76 行）：四个套件共用的断言库。`vcheck`（通过不打印，
  门禁里不刷屏）/ `vexpect`（每条都打印，看清覆盖形状）/ `vsection` / `vsummary`。
  退出码语义**不统一**——verify-markdown 用 3/4、verify-history 用 2，公共库只负责
  人话统计，判红交给调用方。
- 三个套件（`VerifyFileKinds` / `VerifyMarkdown` / `VerifyParity`）迁移到公共库：
  删掉各自抄的 `failed/passed` 与 `check|expect` 助手，`sed` 批量改名后复跑，
  用例数与输出逐字不变（files 49 / markdown 37 / parity 82）。`VerifyHistory`
  **刻意不迁**——它用 stdlib `check(){}`（要 history.json 输入，语义是"回归即抛"），
  与回归收集式的两套退出码哲学不同。
- `tools/test.sh`（新）：统一入口，离线跑三个套件（约 30 秒）；传 `history.json`
  再跑 `verify-history` + 真实数据模式 `verify-markdown`。`set -uo pipefail`
  **不用 `-e`**——要收齐所有套件结果，不能第一个红就退。退出码 0/1/2，
  `--help` 从脚本头注释生成。
- 接进门禁：`tools/docs-check.sh` 新增第 8 节（`test.sh` 存在且可执行 + 实跑退出码 0），
  文档门禁 30 项 → **32 项**；`ci.yml` 去掉重复的 `verify-files` / `verify-markdown`
  两步（docs-check 第 8 节已含），CI 反而少两步。
- 文档同步：TESTING.md 补「纯逻辑测试机制」一节（怎么加新套件、退出码语义）；
  CODEMAP 加 `test.sh` / `VerifySupport.kt` 两行；PARITY / AGENTS / README /
  TECH-DEBT（DEBT-001 清账路径的第一步已落地）计数对齐。

### 测了什么

- `tools/test.sh` 全绿：files 49 + markdown 37 + parity 82 = **168 项**；
  故意写坏用例 / 坏 JSON 实测退出码 1，用法错误退出码 2，`--help` 正常。
- `docs-check.sh` 32/32（第 8 节实跑套件，约 30 秒）。
- 顺带纠错：文档里 verify-markdown 内置用例数一直写 22（实际 37，11a55b2 起就没更新过），
  CODEMAP / PARITY / AGENTS / TECH-DEBT / STATUS / test.sh 注释一并改实。

---

## 开发中 · 第二批对标 companion 2.2.52（P2-1 / P2-2 / P3，未打 tag）

### 基于什么

`docs/project/inbox/2026-10-23-batch2-settings-update-permission.md` 交接单：lead 对着
`PARITY-VS-COMPANION-2.2.52.md` 找了三个差距——通用设置命名空间表单、应用内检查更新、
新建会话选权限档位。约定"只写代码对齐、不推送不打 tag"，每项过共同门禁。

### 干了什么（P3 · 新建会话即选权限档位）

- `data/SessionProjections.kt` 新增纯函数 `resolveNewSessionPreset()`：目录没到 /
  只有一个档位 / 选的值不在目录里 / 解析结果等于部署默认 —— 四种情况都返回 null
  （不多发一条 `/permission`，也不画一排拨了没反应的开关）。
- `AppState.createSession(workspaceId, preset)`：建完**顺序**等 `reloadSessions()`
  （从 `refreshSessions()` 拆出的 suspend 版），用**差集**认出新会话——
  响应体形状没在 OpenAPI 里写死，不猜。差集认到才对它发档位命令。
- `AppStateProjections.applyPermissionPreset(preset, sessionId)`：显式指定会话时
  **不重取历史**（会盖掉正在看的正文）；其余仍是原来那条 `/permission` 通路。
- `ui/Sidebar.kt`：「新会话」下拉顶部一排档位胶囊（候选 = `permissionCatalog`，
  与输入区权限 Pill 同源），菜单展开时按需拉目录；老桥 / 单档位整块不画。

### 测了什么

- `build.sh` 编译通过；新增 `tools/verify-parity.sh`（8 项档位判定用例）；
  `docs-check.sh` 30/30；CODEMAP / PARITY / ROADMAP 已同步。
- **未真机验证**：档位胶囊是否随目录刷新、建完真机权限是否即时为新档
  （要连真 bridge 看投影，不是只看 toast）。

### 干了什么（P2-2 · 应用内检查更新）

- `data/UpdateChecker.kt`（新，300 行）：纯函数 + 网络层，**不依赖 Compose/AWT**。
  渠道按界面语言分流（`zh*` → Gitee，其余 → GitHub）；版本**逐段比数字**——
  字符串比较会让 `2.2.9 > 2.2.47`（"9" > "4"），这是真实出过的事故；
  `parseSha256Sums()` 同时吃 `SHA256SUMS` 与单文件 `<包名>.sha256` 两种格式；
  `pickUpdateAsset()` 按后缀不按下标（Linux `_$arch.deb`、Windows full 优先 msi
  再 zip）；full/lite 判据是 `java.home` 含不含 `/lib/runtime`；下载 64 KiB 分块，
  `onProgress(received, total?)`——**Gitee 的 asset 没有 `size`**，total 为 null
  时界面显示"大小未知"而不是假 0%。
- `AppStateUpdate.kt`（新，235 行）：六态状态机 `Idle / Checking / UpToDate /
  Available / Downloading / Ready / Failed`；Compose 状态字段（`updateState` /
  `updateDownloadJob`）按纪律留在 `AppState.kt` 类内——扩展函数存不了
  `mutableStateOf`。下载落 `AppStorage.root()/updates/`；哈希不符当场删文件并明说。
- `ui/SettingsScreen.kt` 关于区插入 `UpdateCheckSection()`：六态 UI，下载中可取消。
- **边界写死**：只检查 + 下载 + 校验 + 安装指引，**不热自更新**——桌面替换自己
  正在运行的程序本来就得重启，热更新只会多一种"半新半旧"的死法；静默 `pkexec`
  装 deb 在 UOS 上只会弹一个用户看不懂的授权框。指引按平台给命令（Linux
  `sudo dpkg -i` / Windows msi），打开走 `Desktop.open` 不提权。
- 联调踩到的三个真坑：**`@SerialName("tag_name")`**——kotlinx 默认按 `tagName`
  找键，不标注会静默解析成空串，然后"永远不是新版"；GitHub `releases/latest`
  不带 `User-Agent` 直接 403；Gitee v5 API 不要令牌但 asset 无 `size`。

### 测了什么（P2-2）

- `verify-parity.sh` 从 8 项扩到 **36 项**（档位判定 8 + 版本比较 / 渠道分流 /
  清单解析 / 资产挑选 / 说明压平 / 平台探测 28）；`sha256OfFile` 用已知哈希值交叉验证；
  **双渠道真网联调**：Gitee / GitHub 均正确解析、挑选到本机适配包、判定不新于 1.0.2；
  下载 + 进度 + 清单读取 + 取消通路实测通过。
- **未真机验证**：六态 UI 在真机的实际表现（要连真 bridge 那台机器上点一遍）。

### 干了什么（P2-1 · schema 驱动的通用设置命名空间表单）

- `data/SettingsNamespaces.kt`（新，401 行，对标 companion `691b5d5`）：wire 模型 +
  schema→控件纯函数层。**关键坑：内核发的 schema 是 schemastery 引用图
  （`{uid, refs}`），不是标准 JSON Schema**——`enum` 是 `union` 节点指向若干
  `const`。先认 `properties`，取不到再现场摊平（最多三层），**摊不出就回只读，
  绝不猜类型**（把密钥猜成输入框最坏）。密钥三判据任一命中即只读：
  `secrets[].path` 首段 / 值是 `***` / schema `meta.role=secret`（摊平后落 `x-secret`）。
- 入口过滤 `forNativeApp()`：非 `WEBUI_ONLY_NAMESPACES`（纯 WebUI 键，Compose 自绘
  界面一个都不读，显示=给一排拨了没反应的开关）+ `writable` + `hasEditableField()`，
  三条全满足才画。
- `data/ApiExt.kt`：`GET settings/namespaces` / `PATCH settings/namespaces/{ns}`
  （带 `expectedRevision` 乐观锁）；`isAbsentOnBridge()` = 404/403 或码在
  `{ROUTE_NOT_FOUND, SCOPE_REQUIRED, SCOPE_INVALID, FORBIDDEN}`——**刻意不含 503**。
- `AppStateSettingsNamespaces.kt`（新，146 行）：三层降级照搬 companion——软加载
  失败退 null（入口不画，读失败不拖垮设置页）；无 `settings.read` **直接不发请求**
  （发了必 403）；用户刷新时 404/403 静默隐藏、503 如实报错；409 冲突不把错误码
  原文丢用户，就地重拉 + 「已在别处被修改，已刷新」；成功后用 PATCH 返回的单对象
  替换同 ns 项（不整表重拉，避免详情闪烁）。
- `ui/SettingsNamespaces.kt`（新，494 行）：设置页内嵌卡片 + 每命名空间可展开表单；
  六类控件编辑器（enum 胶囊 / boolean 开关 / number 夹取 / text / secret 只读可重填 /
  只读折叠 JSON）；脏值跟踪按命名空间保存；**提交体只在用户真的输入新值时才含
  secret path**，绝不回显、绝不明文提交。
- 无 `settings.write` 是常态（配对默认五档不含它）：表单照画，顶部明说
  「只能查看、不能保存」，保存请求直接不发。

### 测了什么（P2-1）

- `verify-parity.sh` 从 36 项扩到 **82 项**：schemastery 摊平（用**数字 ref id**
  的真实形态，顺带钉住 `contentOrNull` 对数字基元的语义）、标准 JSON Schema 形态、
  密钥三判据、入口过滤、夹取（min>max 脏 schema 原样返回）、人类化；
  `build.sh` 0 error。
- **未真机验证**：真桥 v2.6.0 的命名空间清单、schemastery 真样渲染、409 制造与
  自动重拉、密钥位抓包确认（提交体不含密钥 path）——见 inbox 验收项逐条。

---

## v1.0.2 · 消息流对齐官方 Web + 自由复制/引用 + 对标 companion 2.2.52 第一批（2026-10-23）

### 基于什么

1.0.1 发布后用户连续提了三轮需求（截图对比官方 Web、无法自由复制、滚动条不明显），
随后给了 `PARITY-VS-COMPANION-2.2.52.md` 要求对照实现第一批。本轮把 9 个提交收进一个版本。

### 包含什么

- **消息流对齐官方 Web**：`ChatItem.TurnRun` + `foldTurnRuns()` 按轮折叠（一行「已完成，用时 X」）；
  用户气泡时间 + 复制；统计行居中；消息区右侧细滚动条（内容溢出才显示）。
- **自由复制与引用**：正文包 `SelectionContainer`（拖选 Ctrl+C）；气泡「引用」按钮把整条消息
  转 `> 引用块` 追加进输入框（`AppState.composerInsert` 桥）；设置「会话」卡片加
  「显示上下文注入」开关（`UiPrefs.showContextInjection` 默认关）。
- **第一批对标 companion 2.2.52**：P0-1 `mergeLatestPage()` 增量合并 + 视口贴底跟随/翻页锚定；
  P1-1 `MdBlock.Image` + `PathImage()` 正文图片（http/未知/行内图降级链接）；
  P1-2 `classifyChatLink()` 链接分类 + 行号后缀 + `SourcePane` 按行定位高亮。

### 测了什么

- `build.sh`/`docs-check.sh` 30/30；`verify-markdown.sh` 37 项；`verify-history.sh`
  真实 298 事件 + `mergeLatestPage` 回归；`verify-ci.sh` 产物校验全绿。
- **未真机验证**：折叠手感、拖选复制、引用追加、开关切换、正文出图、`:24` 跳行。

### 发版动作

推 Gitee main + `v1.0.2` tag → mirror 到 GitHub → `release.yml` 三平台构建 →
GitHub Release + Gitee 发行版（`sync-release-to-gitee` / mirror-sync 补）。

**发版当场的红**：`publish` job 15 分钟到点被 cancel——GitHub 跑机往 Gitee 传附件整段卡死，
14 个资产一个没传上去（GitHub 侧 14 个齐全）。修法见 `RELEASE.md` ⑥′：
`sync-release-to-gitee.py` 改为单件超时（240s）+ 重试 3 次（4xx 不重试）、上传前先打日志，
`publish` 超时放到 40 分钟；补发走 `gh workflow run mirror-sync.yml --ref main`。

**补发时的第二个坑**：本机 `--local dist` 把 1.0.1 的三个包也传进了 v1.0.2 发行版
（dist 里新旧共存）。Gitee 附件没有 id、删不掉单个，只能 `DELETE /releases/{id}`（204）
后重建；脚本已按「文件名版本号 == tag」过滤，这是防止重犯的那道闸。

---

## v1.0.1 后续 3 · 第一批对标 companion 2.2.52（2026-10-23，未打 tag）

### 改了哪些文件

| 文件 | 变化 |
| --- | --- |
| `data/ChatHistory.kt`（570→597） | +`mergeLatestPage()`：按 seq 窗口合并最新页，保住更早页 |
| `AppState.kt`（919→953） | `loadHistory(incremental)`；`scrollToBottomTick`；事件驱动刷新改增量 |
| `ui/ChatScreen.kt`（781→757） | 贴底才自动跟随；「加载更早」视口锚定；`CollapsibleBlock` 挪去 ToolBlocks |
| `ui/Composer.kt`（667→669） | 提交后 `requestScrollBottom()` |
| `data/Markdown.kt`（489→564） | +`MdBlock.Image`、`parseImageMarkdown()`、`isRenderableImagePath()`；行内 `![` 降级链接 |
| `ui/RemoteImage.kt`（127→182） | +`PathImage()`：按绝对路径取字节渲染正文图，共用 LRU |
| `data/ChatLink.kt`（新，55） | `classifyChatLink()`：链接分类 + 行号后缀 |
| `ui/MarkdownView.kt`（396→424） | `state` 贯穿；`openChatLink()` 路由；Image 分支 |
| `ui/FilesScreen.kt`（754→780） | `SourcePane` 按行 LazyColumn + 行号定位高亮 |
| `AppStateFiles.kt` | `openFileByPath(path, line)`；`previewEntry` 重置 `previewFocusLine` |
| `tools/VerifyMarkdown.kt` | 内置用例 18→33（图片/降级/链接分类/行号） |
| `tools/VerifyHistory.kt` | +`verifyMergeLatestPage()`（P0-1 回归） |

### 没做的

- P2 两项与 P3 照旧排后；PARITY.md 全量矩阵等 1.0.2 发版前并表。

---

## v1.0.1 后续 2 · 自由复制 + 引用到发言 + 上下文注入开关（2026-10-22，未打 tag）

### 干了什么

1. **`ui/ChatScreen.kt`**（756 → 781）：`MessageRow` 用户/助手分支包
   `SelectionContainer`；气泡下加「引用」「复制」小图标（`BubbleAction()` 在
   ToolBlocks，13dp）；`visibleItems` 按 `prefs.showContextInjection` 过滤
   `Notice(kind="context")`；`StreamingBubble`/`CollapsibleBlock` 正文同样可选。
2. **`ui/ToolBlocks.kt`**（507 → 529）：新增 `quoteText()`（转 `> ` 引用块）与
   `BubbleAction()`。
3. **`ui/Composer.kt`**（659 → 667）：`LaunchedEffect(state.composerInsert)`
   消费引用，追加进 draft。
4. **`AppState.kt`**（907 → 919）：`composerInsert` + `quoteIntoComposer()`。
5. **`data/AppStorage.kt`**（192 → 197）：`UiPrefs.showContextInjection = false`。
6. **`ui/SettingsScreen.kt`**（321 → 366）：「会话」卡片 + Switch。

### 测了什么

- `build.sh` 通过；`docs-check.sh` 全绿。

### 没做的

- 「选中一段文字直接入输入框」没做：Compose 的选区状态要逐层上报，
  收益配不上复杂度；先用整条引用顶着，真有需求再上右键菜单。

---

## v1.0.1 后续 · 对齐官方 Web 的消息流（2026-10-22，未打 tag）

### 基于什么

用户贴两张截图（官方 Web vs 桌面端同一会话）说「对比修改」。

### 干了什么

1. **`data/ChatHistory.kt`**：新增 `ChatItem.TurnRun`（children + tools 聚合 +
   `totalDurationMs`/`isRunning`/`hasError`）与 `foldTurnRuns()`——连续的
   Tool/ToolGroup/Reasoning 折成一条，正文/提示/交付物断开；单条不包壳。
   `parseHistory()` 末尾统一跑，实时路径共用同一投影。`chatStats` 穿透 TurnRun 数。
2. **`ui/ToolBlocks.kt`**（364 → 507）：收编 `ToolGroupBlock()`（原 ChatScreen 私有，
   改 internal）、新增 `TurnRunBlock()`（折叠行 = 箭头 + 「已完成，用时 X」/「运行中…」
   + 「含失败」，展开渲染 children）与 `messageTime()`（HH:mm，0 不显示）。
3. **`ui/ChatScreen.kt`**（773 → 730，回到预算内）：`MessageRow` 加 TurnRun 分支；
   用户气泡从 `Row` 改 `Column(End)`，气泡下补时间 + 复制图标
   （`Icons.Filled.ContentCopy` + `copyToClipboard`）。
4. **`ui/Composer.kt`**：统计行居中（左右等重 Spacer）。
5. **`ui/ChatScreen.kt` 追加**（用户提「加一个侧边滚动条，内容过多的时候上划不明显，
   最好是内容长的时候能看见」）：消息区右缘挂 `VerticalScrollbar` +
   `rememberScrollbarAdapter(listState)`；`derivedStateOf` 判
   `canScrollForward || canScrollBackward`，没溢出不画；自定义
   `ScrollbarStyle`（6dp 细拇指、40dp 最小高、悬停加深、透明轨道）。文件 730 → 756。
6. **`tools/VerifyHistory.kt`**：preview 的 `when` 补 TurnRun 分支（穷举编译器会逼着改）。

### 测了什么

- `build.sh` / `docs-check.sh` 30/30 / `verify-history.sh`（真实 298 事件）全绿。
- 探针 `ProbeTurnRun` 12/12（见 STATUS）。

### 没做的

- 官方右侧悬浮工具条未抄；tok/s / 缓存命中统计无数据源。

---

## v1.0.1 · 工具调用默认折叠（2026-10-22）

### 基于什么

用户一句“工具调用如手机版 默认收缩起来”，附手机版截图：一轮工具调用折成
「耗时 8 秒 · 2 次工具调用」一行，箭头可展开。

### 现状盘点（没急着改）

`ChatItem.ToolGroup` 早就默认折叠（第十二轮起），但**单条**的 `ChatItem.Tool`
初始态是 `mutableStateOf(tool.isError)`——成功的工具调用一律**展开**，把
ReadBody / TerminalBody / DiffBody 全文铺在会话里。所以"手机版那样只有一行"
在桌面端根本不成立：只要这一轮只调了一次工具（没有相邻兄弟可合并），就是整块展开。

### 干了什么

1. **`ui/ToolBlocks.kt`**：`ToolCallBlock` 的 `expanded` 初始值
   `tool.isError` → `false`。失败也不自动展开：摘要行已有红色「失败 · 耗时」，
   而"失败就全展开"在一轮十几次调用出错时会把整场会话冲掉。
   注释里把这个取舍写清楚，避免以后有人当 bug 又改回去。
2. **`data/ChatHistory.kt`**：`ToolGroup` 增加 `isRunning` 与 `totalDurationMs`
   （`tools.mapNotNull{it.durationMs}.takeIf{it.size==tools.size}?.sum()`）。
   缺任一条就返回 null —— 少显示一行，好过显示一个少算的数。
   放数据层是因为它是纯函数，能被探针直接验；UI 层只负责拼字符串。
3. **`ui/ChatScreen.kt`**：`ToolGroupBlock` 折叠行右侧加时间标签
   （「运行中」/「耗时 8 秒」），与「含失败」并列；补 `formatToolDuration` import。
   行内顺序：箭头 → 工具名+次数 → 耗时 → 含失败。
4. **`data/ToolBlockView.kt`**：`formatToolDuration` 改中文整秒
   （`350 毫秒` / `8 秒` / `2 分 5 秒` / `1 小时 1 分`），顺带去掉了
   `String.format(Locale.US, "%.1f s")`。只有两处调用（单条状态行、组摘要），
   影响面可控；亚秒级仍给毫秒，避免快工具显示「0 秒」误导。
5. **版本号 1.0.0 → 1.0.1**：`version.txt`、README 产物名、本文与 `STATUS.md` 的标题一起改。
   顺手治了一个老毛病——设置页「关于」里 `DSH Desktop 1.0.0` 是**硬编码**的，升版本就得
   记得手改。现在改成先读 jar manifest 的 `Implementation-Version`（`build.sh` 打 jar 时
   从 `version.txt` 写入），读不到（直接跑 `build/classes`）才回落常量 `VERSION_FALLBACK`；
   `tools/docs-check.sh` 新增一条「SettingsScreen 兜底版本一致」，常量漂了 CI 直接红。
6. **`tools/launcher/dsh-desktop.sh`（真机报障后重写）**：用户装轻量包点图标
   "无法打开，无任何提示"。手动 dry-run 复现：v1.0.0 发行版里没有运行时资产，
   Gitee/GitHub 双双 404；而 `.desktop` 写的是 `Terminal=false`，`echo >&2` 全进黑洞。
   - 新增 `log()`：`~/.cache/dsh-desktop/launcher.log`，每次启动记版本 / APP_HOME / 选中的 java；
   - 新增 `dialog()`：`zenity → kdialog → xmessage → notify-send`，**都带 60 秒超时**
     （`xmessage` 没人点会一直不返回，第一次写没加超时，测试直接被吊死）；
     `[ -t 2 ]` 不弹；`DSH_LAUNCHER_NO_DIALOG=1` 完全静默给测试用；
   - 新增 `fail()`：stderr + 日志 + 弹窗，下载失败时把 `DSH_RUNTIME_HOME` /
     `DSH_RUNTIME_TARBALL` 两种手动兜底和发行版地址一起写进弹窗；
   - **不再 `exec java`**：前台跑拿退出码，`1..128` 才报「异常退出」（128+ 是信号，
     注销时不该弹"启动失败"）；无终端时 java 的 stderr 追加进日志。
7. **打包/CI 三个次序坑（v1.0.1 首次发版被抓出来的）**：
   - `LITE=1 bash tools/package.sh` 里 jpackage 的产物原来直接写 `dist/`，
     会把上一步打好的完整包**覆盖**成没有 `/usr/share/applications` 的半成品 →
     改落 `build/lite-jpkg`，`DEB` 从那里取骨架；
   - 运行时 tar.xz 与 `.sha256` 必须**先**造出来再打轻包，否则轻包 `lib/app/`
     里没有校验和（第 15 轮加了校验、次序没跟着调）；
   - CI 校验的 `dpkg-deb -I dist/*.deb` 在两包并存时把第二个当 control 组件名 →
     改成按 `version.txt` 拼文件名；`tar -tJf | grep -qx` 也改成先取 listing，
     避免 `grep -q` 提前关管道撞 `pipefail`。
8. **`tools/launcher/dsh-desktop.bat` 同构**：日志 `%LOCALAPPDATA%\dsh-desktop\launcher.log`，
   java 非零退出提示日志路径，双击启动（`cmdcmdline` 含 `/c`）时 `pause` 停住。

### 测了什么

- `tools/build.sh` 通过（含 `ChatHistory` 的 `Long?` 聚合表达式）。
- Java 探针对 `ChatItem.ToolGroup` + `formatToolDuration` 直接打桩 **16/16**：
  合计耗时、缺耗时置 null、`isRunning`、空组、`hasError`、>4 工具名标题、
  四档单位。探针只依赖 `build/classes` + kotlin-stdlib，跑在 `build/tmp/probe`（不入库）。
- `tools/docs-check.sh` **30/30**（新增版本兜底那条；CODEMAP 行数与接口表同步更新过）。
- 引导器四条路径（`DSH_LAUNCHER_NO_DIALOG=1`）：404 双源 → 退出 1 + 完整提示 + 日志；
  本地 tar 包 → 解压并命中；再跑一次 → 命中缓存不重下；java 退出码 3 → 报异常退出。
  日志开头能看到 `APP_HOME` / `java=` 两行，够定位"用了哪个运行时"。
- 轻包解包核对：`lib/runtime` 不存在、`VERSION=1.0.1`、`bin/dsh-desktop` 与
  `tools/launcher/dsh-desktop.sh` sha256 一致。

### 没做的 / 风险

- 点击展开是 Compose 内部状态，沙箱无 GUI 验不了；只能保证初始 `false`。
- 组耗时是"逐条相加"，并行工具（同一轮多个 tool/call 并发）会高估——目前
  内核按串行派发，暂不处理；真要精确得用首尾 seq 的 wall-clock span。

---

## v1.0.0 第十五轮 · 安装包瘦身：94 → 68 / 40 MB（2026-10-22）

### 基于什么

用户一句：“当前安装包有点大，如何让安装包小点，可否让自行在启动界面加一个自动下载
运行环境所需内容，国内外环境都要考虑。还要考虑打包的情况、CI情况。”

一句话里三件事：**砍体积**、**运行时按需下载**、**打包与 CI 要跟上**。

### 先量后砍

`dpkg-deb -c` 逐文件统计（不猜）：94 MB 里
material-icons-extended **36 MB**（2083 个 Filled 图标，工程只 import 23 个）、
JRE 运行时压缩后 **~28 MB**、skiko 12 MB（渲染引擎，动不了）。
于是动手的顺序就是“先砍图标、再砍运行时、最后才谈下载”。

### 干了什么

1. **`tools/slim-icons.py`（新增）**：按源码 import 现抓白名单，重写 extended jar，
   36 MB → 0.05 MB。白名单漏抓 = 编译期 unresolved reference，不静默少图标。
2. **运行时改由 `package.sh` 自己 jlink**：`--strip-debug --compress 2
   --no-header-files --no-man-pages`；模块集砍到 6 个
   （`java.base, java.desktop, java.logging, java.instrument, jdk.unsupported,
   jdk.crypto.ec`），95 MB → 53 MB。
   `jdk.crypto.ec` 必须留：TLS 的 ECDHE 靠 SunEC，jdeps 静态分析看不出来。
3. **轻量包（`LITE=1`）+ 启动引导器**：`tools/launcher/dsh-desktop.sh`（Linux）与
   `.bat`（Windows）。顺序：包内自带 runtime → 用户目录已下载 → 都没有才下载。
   下载源**国内优先**（Gitee 在前、GitHub 在后，`DSH_RUNTIME_MIRROR=github` 对调），
   下完验 sha256（校验和随包带）再解压启动。
   离线兜底：`DSH_RUNTIME_HOME` 指向已有运行时、`DSH_RUNTIME_TARBALL` 吃本地压缩包；
   诊断：`DSH_LAUNCHER_DRY_RUN=1` 只走流程不启动。
4. **`tools/build-runtime.sh`（新增）**：把 jlink 镜像打成 tar.xz + sha256。
   轻包与完整包**共用同一份字节**（都来自 `build/runtime-image`），不会两套行为。
5. **CI（`release.yml`）**：三个平台各加“打轻包 + 运行时资产”一步，
   artifact 与 release 资产、Gitee 同步全部带上；校验补了
   “轻包不带 runtime / 带 sha256 / tar 顶层是 `./bin/java`”。

### 体积

| 产物 | 之前 | 现在 |
| --- | --- | --- |
| deb 完整 | 94 MB | **68 MB** |
| deb-lite | — | **40 MB**（首次启动再拉 29 MB 运行时） |
| Windows zip | 96 MB | 预计 ~60 MB（图标同样瘦身，CI 验） |

### 测了什么

- 编译 0 error；docs-check 29/29。
- 精简 runtime + 瘦 jar：23/23 图标加载成功（headless 探针）；
  TLS 双通 gitee.com / baidu.com（HTTP 200）。
- 引导器四路实测：自带 runtime / 本地 tarball / 下载+校验+解压+启动 /
  全失败时报错与手动指引（退出码 1）。

### 踩到的

- `download … | sed` 管道吞 curl 退出码 → 404 当成功、拿空文件校验、报错对不上真因；
- 临时文件写 `/tmp` 被拦 → `curl (23) Failed writing body`；改用户缓存目录；
- 运行时 tar 多套一层顶层目录 → 引导器找不到 `bin/java` 白下一遍；
- `rm -rf` 在 build 下也被 safe-delete 拦（>50 文件）→ 统一 mv。

---

## v1.0.0 第十四轮 · 产出文件归位、文件页分栏可拖（2026-10-22）

### 基于什么

用户两句：“没必要一直显示更早轮次产生的文件，他在那就产出就放哪里”
“文件卡打开文件直接显示文件，没必要显示目录，且目录与文件的显示无法左右调整，
显示内容多的时候查看不方便”。

上一轮把卡片挂成会话末尾一块汇总区，是把**成果和产出它的那一轮拆开**——
文件卡永远堆在最下面，看的人不知道它是哪一轮产出的。这一轮改回"哪儿产出放哪儿"。

### 干了什么

- `DeliverableFile` 带 `seq`；`ChatItem.Deliverable` 加 `fromScan`。
- 新增 `ChatHistory.mergeDeliverableCards()`（纯函数，可单独验证）：只插入不重排，
  按 seq 插到第一个比它晚的条目之前；去重按 path；补的卡能被原装卡顶掉；缺 seq 挂末尾。
- `AppState` 四处调用（首页 / 回溯 / 翻更早页 / 实时流），幂等；
  顺带补上 `loadOlderHistory` 漏掉的"把该页交付物并入 `sessionDeliverables`"。
- `ui/ChatScreen.kt` 删掉汇总区，`deliverablesOutsideStream()` 删除。
- `ui/FilesScreen.kt`：`Splitter()` 可左右拖（拖比例 0.18~0.72，不是像素），
  右栏不再钉死 430dp；页头加目录栏开关；`openFileByPath` 进来时收起目录栏，
  收起时预览区补一行路径。
- 交系统打开的那份也先 `previewEntry()`，否则预览区会停在上一个文件上。

### 测了什么

- 编译 0 error；`tools/docs-check.sh` **29/29**。
- `mergeDeliverableCards` 临时跑了 8 个用例全过（按 seq 归位 / 不重复 / 原装顶掉补的 /
  幂等 / 多个各归各位 / 同轮合成一张 / 缺 seq 挂末尾 / 不留孤儿卡）。

### 踩到的

- `JsonPrimitive.intOrNull` 要单独 import（`kotlinx.serialization.json.intOrNull`），
  `AppState` 里没引；用 `contentOrNull?.toIntOrNull()` 绕开。
- 扩展函数/顶层函数跨包调用要**显式 import**（`mergeDeliverableCards` 在 `data` 包），
  这已经是第二次栽在同一个地方。

---

## v1.0.0 第十三轮 · 文件卡片点击直接打开文件（2026-10-22）

### 基于什么

用户一句：“文件卡片 点击跳转到目录了，应该直接打开文件，不能打开直接走系统去判断。”

一句话里两条要求：**点下去要打开这个文件**（不是跳到目录让人自己找），
**客户端打不开的就交给系统**去判断 —— 分流口径必须和文件页预览一致。

### 干了什么

- `AppStateFiles.kt` 新增 `openFileByPath()`：`resolvePath` → 列父目录取 `FileEntry`
  → `classifyFile()` 分流 → Markdown/文本/图片走 `previewEntry()`，其余走 `openEntryExternally()`。
  `openAbsolutePath()` 保持"定位目录"不动（页头 cwd、`-Ddsh.openPath=` 还要它）。
- 图片超 8 MiB 直接走系统；目录 / 文件已不在原位置 / 解析失败三个兜底各写清楚。
- `ui/ChatScreen.kt` 两处 `DeliverableCard` 改调新动作；箭头语义改成"打开这个文件"。
- 顺手清掉 `openAbsolutePath` 上一段悬空的孤 KDoc（两行注释叠在一起，只有后者生效）。

### 测了什么

- 编译通过（`tools/build.sh`，63s）。
- 真机点击未验：沙箱 `/usr/share`、`/opt` 只读，deb 装不进去，留给用户装包后确认。

---

## v1.0.0 第十二轮 · 待办与轨迹挂到标题下、思考栏滚内容（2026-10-22）

### 基于什么

用户三句：“思考的时候 悬浮思考栏显示的思考中，应该滚动显示思考内容”
“待办任务应该在标题下悬挂，提供展开收缩”“轨迹应该对照官方模式显示”。
（当时只写了 `STATUS.md`，这里补记，免得序号断档。）

### 干了什么

- `ui/Composer.kt`：思考悬挂条折叠态改取**尾部 64 字**横向滚动（Compose 1.7 没有
  `TextOverflow.StartEllipsis`，自己截），展开态随流式自动滚到底。
- 新增 `ui/ChatShelves.kt`：`TodoShelf()` / `TraceShelf()` 挂在会话头下方，可折叠、限高 220dp。
  抽屉里不再有待办栏（`ChatPanel` 只剩 `Jobs` / `Server`）。
- 轨迹改成线性时间轴：一行一条（时间 · 类型 · 一句话摘要），摘要由
  `SessionProjections.stepLabel()` 生成，认不出就只显示类型名（不猜）。

### 测了什么

- 编译 0 error；`tools/docs-check.sh` **29/29**。
- 真机数据验摘要：298 条历史跑 `buildTrace` → **146/298 条有摘要**。

---

## v1.0.0 第十一轮 · 把发行版搬回主源（2026-10-22）

### 基于什么

用户一句：“gitee 没有发行版，应该同步 GitHub 的发现版，本地 deb 自己没打。”

两个问题，其中第二个是我自己的锅：第九轮把出包交给 CI 之后，**本机再没打过包**——
`tools/package.sh` 重写后从没在本地跑通过完整流程，等于权威路径只剩 CI 一条。
第一个才是设计缺口：`--mirror` 只搬 refs，产物是 GitHub 的机器建的，
于是主源上"代码齐全、下载页空空"。

### 干了什么

**A. 本机把 deb 打出来（先证明这条路还活着）**

前台一次跑通 `tools/package.sh`，2m01s 出 `dist/dsh-desktop_1.0.0-1_amd64.deb`（90 MB），
包内桌面入口自检通过。刻意没有"边跑边改脚本"——那会读到半行（见坑位）。

**B. `scripts/ci/sync-release-to-gitee.py`（新建）**

把 GitHub Release 单向搬成 Gitee 发行版，两条喂法：

- 默认：从 GitHub 下载该 tag 的资产再上传（CI 上走这条，带宽由 GitHub 出）；
- `--local DIR`：直接传本机产物，顺手自算 `SHA256SUMS`。

同名附件跳过 → 重跑幂等；缺 `GITEE_TOKEN` 时只警告。

**C. 接进两条工作流**

- `release.yml` 的 `publish` job：拿到三平台产物后当场 `--local dist` 搬回 Gitee；
  搬不动**只警告不判红**（tag 可能还没镜像到 Gitee，GitHub 那边 Release 已经成了）。
- `mirror-sync.yml` 新增 `releases` job（`needs: mirror`）：每 3 小时补缺，取最新 Release tag。

### 测了什么

- 本地打包 2m01s 通过，deb `dpkg-deb -I/-c` 自检过（含桌面入口）。
- 全量同步 2m06s：`zip 96 MB / msi 96 MB / amd64 deb 91 MB / arm64 deb 89 MB / SHA256SUMS`
  五个资产上传成功，**Gitee 发行版从 0 个变成 1 个**。
- 完整性核验：把四个资产流式取真 sha256 与随包 `SHA256SUMS` 逐条比对，**4/4 一致**。
- `tools/docs-check.sh` **28/28**（新增 3 条坑位、CODEMAP 登记新脚本后复跑）。

### 踩到的

1. Gitee 附件字段名是 **`file`**，不是文档写的 `file[]`——后者连 6 B 的文本都 400。
2. Gitee API 的 POST **必须带 `Content-Type`**，否则一律 400（`curl -d` 会自动带，手测能过、代码过不了）。
3. `PATCH` 更新发行版**必须带 `tag_name`**。
4. 本机 `dist/SHA256SUMS` 被我自己拼进了校验和清单 → 改成跳过自己（否则 `sha256sum -c` 必报不匹配）。

### 没做的

- Gitee 上那份 amd64 deb 是 GitHub 构建的（与本机那份字节不同，jpackage 会烤进时间戳）。
  刻意保持一致：否则 Gitee 里的 deb 与随包 `SHA256SUMS` 对不上，用户校验会失败。
- 三个产物仍**未真机安装验证**（沙箱只读 `/usr/share`、`/var/log`）。

---

## v1.0.0 第十轮 · 对齐官方 Web 的会话观感（2026-10-22）

### 基于什么

用户一句：“官方有卡片 文件、上下文注入 隐藏，折叠，命令 bash 模式，思考过程在对话框上悬挂显示。”

前一轮我确认过两张截图（官方 Web 与客户端）**内容、结构、位置都一样**，差别只在主题配色。
这一轮动的是剩下的 5 处**信息密度**差异，都是同一类问题：官方把"过程性内容"挪出消息流，
我们原先摊在正文里。

### 干了什么

| 官方做法 | 客户端原来 | 现在 |
|---|---|---|
| 产出文件一张卡片：类型色块 40dp + 主副两行 + 右侧箭头 | 一整块列表，N 个文件挤在一起 | **一个文件一张卡**，卡片 1px hairline / 圆角 10dp / 内距 10dp |
| 上下文注入放进「上下文」页签 | 摊在消息流里，灰底一大片 | `Notice.kind = "context"` → **默认折叠成一行 36dp**，点开才看 |
| 工具调用折叠 | 已折叠 ✓ | 保持，并让 bash 的输出也能折 |
| bash 命令模式：命令一行 + 输出一段 | 命令 + 全量输出（几百行照样全打） | 命令单独一行保留 `$` 前缀；**输出超过 6 行折叠**，展开限高 420dp |
| 思考过程悬挂在输入框上方 | 推理与正文并列在消息流里，推理一长正文被顶出屏外 | 新增 `ReasoningShelf()`：贴在输入卡**上方**，折叠态 36dp、展开限高 132dp，跟着这一轮走 |

类型色块用的是**固定品牌色**（docx 蓝 `#2B579A`、xlsx 绿 `#217346`、pdf 红 `#C0392B`…），
不跟主题变——这是用户对文件类型的既有认知，跟着主题走反而认不出。

### 测了什么

- 编译 0 error（51s）；用到 2 处漏导入（`Box`、`sp`）已补。
- `tools/docs-check.sh` **28/28**（CODEMAP 行数与关键符号同步）。
- **未真机验证**：悬挂条、卡片、折叠块都还没在真实会话里看过一屏。

---

## v1.0.0 第九轮 · 建仓与 CI（2026-10-21）

### 基于什么

用户指令：「git 建仓，对照安卓的模式，gitee 主，github 同步，CI 全绿，github 自己打包
Windows 和 ARM 架构的 deb」。

难点不在写 YAML，而在**这台机器上的一切都是写死的**：`build.sh` 里 `JAVA_HOME` 与
`KOTLINC_HOME` 是绝对路径，`package.sh` 的 `VERSION` 是字面量，验证脚本依赖 Gradle 缓存
（CI 上没有），Windows 上类路径分隔符是 `;` 而不是 `:`。不先把这些拆开，工作流写得再漂亮也红。

### 干了什么

**A. `tools/build.sh`（重写）**

- `JAVA_HOME`：环境变量 → 本机缓存 → `/usr/lib/jvm/*17*` → `PATH` 上的 `java`，四段回退。
- `KOTLINC_HOME`：默认跟着 `$HOME` 走；找不到就从 `repo1.maven.org` / 阿里云镜像下到
  `build/.kotlinc`（`resolve_jar()`）。
- 平台方言三件套：`PLATFORM`（`uname` 推断，可被 `TARGET_PLATFORM` 覆盖）、
  `CPSEP`（Windows 用 `;`）、`wp()`（Windows 走 `cygpath -w`）。classpath 每个条目都过 `wp`。
- classpath 按平台过滤 `skiko-awt-runtime-*`，缺目标平台运行时直接报错并指路 `fetch-platform.sh`。
- 打 jar 从 JDK `jar` 换成 Python `zipfile`（不建线程，本机地址空间紧张时也能跑）；
  清单里的版本号改为读 `version.txt`。

**B. `tools/package.sh`（重写）**

- 分支化：`windows-x64` → `jpackage --type app-image` + Python zip（有 WiX 时另试 msi）；
  其余 → deb，`DEB_ARCH` 由平台推导（`linux-arm64` → `arm64`，否则 `amd64`）。
- 公共参数抽成 `JP_COMMON` 数组，路径全部过 `wp`；`jpackage` 可执行名兼容 `.exe`。
- 去掉了 `--java-options -Djava.io.tmpdir=<构建机路径>`：那是把构建机的临时目录烤进安装包，
  用户机器上根本不存在。

**C. `tools/fetch-platform.sh`（新建）**

本地种子（`lib/`、`tools/windows-libs/`）→ Maven（官方 → 阿里云）→ `zipfile` 校验。
不校验的话，镜像返回 200 + HTML 错误页也会被当成依赖。

**D. 版本单一来源**

新增 `version.txt`；`build.sh`、`package.sh`、`docs-check.sh` 都读它。
`docs-check.sh` 第 2 节从「比对 `build.gradle.kts`」改成「比对 `version.txt`」，
检查项总数仍是 28。

**E. 移除 Gradle 脚手架**

`build.gradle.kts`、`settings.gradle.kts`、`gradle.properties`、`gradlew.sh`、`gradle/`
全部移出仓库（挪到 `_bak/gradle-scaffold/`）。`build-windows.bat` 改成调
`tools/fetch-platform.sh` + `tools/package.sh` 的薄封装。

**F. 仓库与 CI**

- `LICENSE`（AGPL-3.0，与 `dsh-companion` 一致）、`NOTICE`（逐项列出 50 个 jar 的来源与许可）。
- `.gitignore`：`build/`、`dist/`、`_bak/`、`hs_err_pid*.log`、平台运行时目录；**`lib/` 明确入库**。
- `.github/workflows/`：`ci.yml`、`release.yml`、`mirror-sync.yml`（口径照抄 `dsh-companion`，
  含"`GITHUB_TOKEN` 不能改 workflow 文件，故用 `MIRROR_PAT`"这条）。

### 测了什么

- `bash -n` 三个脚本通过；`tools/build.sh` 编译 **0 error**（57s）。
- `tools/package.sh` 全流程：jar 846 个类 → jpackage → 补桌面入口 → 重打 deb
  → `dpkg-deb -c` 自检通过，产物 `dsh-desktop_1.0.0-1_amd64.deb`（90 MB）。
- `tools/docs-check.sh` **28/28**。

### CI 实测（`release.yml`，三平台）

| job | runner | 耗时 | 结果 |
| --- | --- | --- | --- |
| `deb · amd64` | ubuntu-22.04 | 2m50s | ✓ 91 MB |
| `deb · arm64` | ubuntu-22.04-arm | 2m37s | ✓ 89 MB |
| `zip · windows-x64` | windows-latest | 1m54s | ✓ zip 96 MB + msi 96 MB |
| `发布 Release` | ubuntu-latest | 13s | ✓ 五份资产 + `SHA256SUMS` |

`ci.yml` 45s–1m04s 全绿；`mirror-sync.yml` 手工与定时各跑通两次。

### 踩到的坑（已进 `PITFALLS.md`）

1. 跑 `package.sh` 的同时我在编辑它。bash 按字节偏移流式读脚本，读到半行，报出
   `行227: -vE：未找到命令`——看着像脚本 bug，其实是"边跑边改"。重跑即正常。
2. Windows 打 jar 崩 `UnicodeEncodeError`：Python 在 Windows 控制台按 cp1252 输出，
   中文提示与 `▸` 编码不了。脚本里 export `PYTHONUTF8=1` / `PYTHONIOENCODING=utf-8`，
   **工作流里内联的 python 要再设一次**（构建脚本 export 的变量不跨 step）。
3. arm64 校验 `dpkg-deb -c … | grep -q`：grep 命中即关管道，上游 tar 收 EPIPE 报
   `tar: stdout: write error`，整条流水线判红。改成先取回再判断。
4. docs-check 原来按"本地文件在不在"判 `tools/…` 引用，本机有 `tools/windows-libs/`
   但 CI 检出后没有——外绿内红。改成用 `git ls-files` 判"是否入库"。

**未做真机验证**：三个产物只验到包内结构与架构正确，没在真实机器上装过。

---

## v1.0.0 第八轮 · 文件预览按类型分流（2026-10-21）

### 基于什么

用户指令：「文件渲染也要考虑，尤其 MD 自己要能渲染，其他的可以调用系统的，
这个客户端是 Windows 和 UOS 都要用的，基础必须考虑到位」。

原来的预览只有一条路：整份读回来 → 严格按 UTF-8 解 → 等宽显示。三个问题一起存在：
Markdown 看到的是原文（`|` 和 `#` 满天飞）；图片显示成一行"二进制文件"；PDF/Office
只能下载后自己去文件夹里找。而在 Windows 上，UTF-8 硬解还会把 GBK/UTF-16 的中文变成乱码。

### 干了什么

**A. `data/FileKinds.kt`（新建 267 行，纯函数）**

- `classifyFile()`：按扩展名分四类（Markdown / 文本 / 图片 / 其他），其他给中文人话标签
  （"PDF 文档"、"安卓安装包"），无扩展名的先当文本。
- `refineByContent()`：内容含 NUL 就降级成二进制 —— `.csv` 里其实是 xlsx、`.md` 里塞了压缩包都拦得住。
- `decodeText()`：BOM（UTF-8/UTF-16LE/UTF-16BE）→ **严格模式** UTF-8 → GBK → 有损兜底。
  宽容模式 `String(bytes, UTF_8)` 永远成功，判断编码必须用 `CodingErrorAction.REPORT`。
- `looksBinary()`：BOM 判定**先于** NUL 判定，否则 UTF-16 文本会被当成二进制。
- `imageFormatOf()`：按魔数认 png/jpeg/gif/bmp/webp，不信扩展名。
- `formatFileSize()`：列表与预览共用一份，原来两处各写一个迟早对不上。

**B. `data/SystemOpen.kt`（新建 104 行）**

按平台给候选命令：Windows `rundll32 url.dll,FileProtocolHandler` → `cmd /c start ""`；
UOS `xdg-open` → `gio open`；最后都兜到 `Desktop.open`。每条失败都收集原因，
全失败时拼进错误给人看。**只等 2 秒，超时不算失败**（有的 `xdg-open` 会挂着等程序退出，
杀掉会带走用户刚开的窗口）。

**C. `AppStateFiles.kt`（262 → 378 行）**

- `previewEntry()` 改成三步：看名字 → 分派 → 读需要的部分。图片整读（限 8 MB），
  文本用 Range 只取前 512 KiB，**其他根本不读**（反正渲染不了）。
- `textPreview()` 本地再截一刀：服务端可以不认 Range 照样返回全量。
- `openEntryExternally()`：下载到 `cacheRoot()/opened/<path 哈希>/` 再交系统。
- `startEditing()` 加第二道闸：非文本类不许进编辑态。
- `AppStorage.cacheRoot()`：Linux `$XDG_CACHE_HOME`、Windows `%LOCALAPPDATA%`。
  几十兆的 PDF 不该塞进会被备份同步的配置目录。

**D. `ui/FilesScreen.kt`（477 → 679 行）**

- 头部重排：文件名 + 类型徽标 + 体积 + （非 UTF-8 时才显示的）编码。
- 动作条只放当前文件真能做的事：Markdown 多一个「预览 / 源码」切换，图片与其他格式多一个
  「用系统程序打开」，其余保持编辑/移动/下载/删除。
- 正文四路分流：`MarkdownPreviewPane`（渲染前把 CRLF 归一）/ `SourcePane`（等宽）/
  `ImagePane`（解码放 `Dispatchers.Default`，等比缩放，点开看原图）/ `UnsupportedPane`
  （说清原因 + 一个明确的按钮，不留"点了没反应"）。

**E. 验证与文档**

- `tools/verify-files.sh` + `tools/VerifyFileKinds.kt`：**49 条用例**，覆盖大写扩展名、
  双扩展名、无扩展名、Windows 反斜杠路径、冒充文本的二进制、UTF-8 BOM、UTF-16LE、
  GBK、CRLF 保真、五种图片魔数、体积格式化。

### 测了什么

- `tools/build.sh` 0 error；`tools/verify-files.sh` **49 项全过**；`tools/docs-check.sh` 28/28。
- 真实服务端（192.168.1.126:3090，根 `/media/wangke/OFFICE/workspace`）放五类测试文件后截图：
  `r8-md.png` 标题/表格/任务项/引用/代码块全对；`r8-image.png` PNG 等比显示 + 底部 `PNG · 1270×790`；
  `r8-binary.png` PDF 给出原因与按钮；`r8-gbk.png` 中文零乱码且标出 `GBK`；
  `r8-fake-md.png` 伪装成 md 的二进制被降级成「二进制（扩展名像 Markdown）」。

### 失败与回退

- 脚本拼接 `PreviewPane` 时保留边界算错一位，`@Composable` 叠了两层，
  报 `this annotation is not repeatable`（已进 `PITFALLS.md`）。
- 第一版用例里有一条 `pngAsText` 算了没用、断言测的是另一件事，属于"假通过"，已改成
  真正测「图片不会被 NUL 误降级」。
- `formatFileSize` 原本在 `FilesScreen.kt` 里叫 `humanSize`，与预览提示各写一份，
  这轮合并到 data 层。

---

## v1.0.0 第七轮 · 启动自愈（2026-10-21）

### 基于什么

第六轮打完包做启动冒烟，把 `dist/` 里的 tar 解压后直接起不来：

```
Exception in thread "main" java.lang.ExceptionInInitializerError
Caused by: java.io.IOException: Permission denied
	at java.io.File.createTempFile(File.java:2170)
	at org.jetbrains.skiko.Library.unpackIfNeeded(Library.kt:34)
```

**这不是新引入的回归**，是上一轮"能力对齐"改不到启动路径时被掩盖的老问题——之前所有截图都是
`tools/build.sh` 编完直接跑的。真实用户拿到 tar/deb 双击才会撞上。

### 干了什么

**`src/Main.kt`：`ensureWritableTemp()` → `ensureWritableDirs()`**

探两个目录，而不是一个：

| 目录 | 谁用 | 退路 |
| --- | --- | --- |
| `skiko.data.path`（默认 `$user.home/.skiko`） | skiko 解压 `libskiko-linux-x64.so`（28.9 MB） | `XDG_DATA_HOME` → `$HOME/.local/share` → `user.home/.skiko` → `user.dir` |
| `java.io.tmpdir` | JVM / AWT 的临时文件 | `XDG_CACHE_HOME` → `$HOME/.cache` → `user.home/.cache` → `user.dir` |

判据 `usableDir()` 也从"建得出文件"升级成"建得出文件 **且** 剩余空间 ≥ 96 MB"。
用户显式传了 `-Dskiko.data.path` 就不覆盖。

**文档**：`PITFALLS.md` 重写「skiko 解压原生库失败」并新增「root 与 tmp 在这台机器上都不可靠」；
`AGENTS.md` §7 同步索引；`CODEMAP.md` 更新 `Main.kt` 行数与符号；`README.md` 第 2 条改口。

### 测了什么

- **对照实验**（决定性）：`-Dskiko.data.path=<可写目录>` 起窗 ✓、`-Dskiko.library.path=<已解压目录>` 起窗 ✓、什么都不加 ✗。
  三个一跑就知道命中的是 skiko 自己的属性，而不是 `java.io.tmpdir`。
- **修后不带任何参数**：1–2 s 起窗 1270×790，原生库自动落到
  `~/.local/share/dsh-desktop/skiko/<hash>/`，日志零报错；接真实服务端
  （192.168.1.126:3090）的会话页截图正常（`shots/parity/r7-startup.png`）。
- `tools/docs-check.sh` 28/28（中途因标题里带反引号挂过一次，见下）。

### 失败与回退

- 第一轮修完还是失败：`tools/build.sh` **只编译到 `build/classes`，不打 jar**，
  起来的一直是 23:23 的旧 jar。必须 `tools/package.sh` 才有新包。白跑一轮，已写进 `PITFALLS.md`。
- `PITFALLS.md` 新条目标题写成 ``### `/root` 与 `/tmp` …``，与 AGENTS 索引比对时
  反引号被 `^- \`([^\`]+)\`` 规则截断，门禁报"正文/索引互为缺失"。标题里不许再放反引号。

---

## v1.0.0 第六轮 · 对齐安卓端能力面（2026-10-21）

### 基于什么

用户指令：「界面实现了，功能也对照实现了，最低限度对照安卓的功能要保证都有」。
`docs/project/PARITY.md` 当时还剩 12 项 ✗（附件、文件写入、推理等级、权限预设、
Agent Preset、TODO、轨迹、工作区管理、能力探测、Provider 页、后台任务）。
安卓端 `dsh-companion` 是同一套协议的成熟实现，按它的能力面当下限。

### 干了什么

**A. 数据与状态层（新增 5 个文件）**

- `data/SessionProjections.kt`：投影解析的纯函数——`parseTodos()`、`parsePermission()`
  （`currentValue → preset → sandbox` 依次降级）、`parseMaxSeq()`、`buildTrace()`（按轮次分组，
  **未知事件原样保留**，无主事件归 `index = 0` 的伪轮次）。
- `data/Attachments.kt`：`PendingAttachment`（只持有字节，不发）、`guessMediaType()`、24 MB 上限。
- `AppStateProjections.kt`：投影同步、权限档位、Agent Preset、断档检测、附件发送、
  后台任务、工作区管理。`AppState.kt` 里补 30 余个状态字段（写入口 `internal set`）。
- `AppStateSettings.kt`：provider / 插件的读写动作。
- `src/Enums.kt`：`ConnectionState` / `Nav` / `ChatPanel` 搬出来，`AppState.kt` 从 799 压回 781。

**B. 会话内（`ui/ChatSidePanel.kt` 461 行 + `ui/ChatScreen.kt` 改造）**

- 右侧 316dp 抽屉四栏：待办 / 轨迹 / 任务 / 服务端。页头三个带计数的胶囊开关。
- 断档：`chat.session.snapshot` 的 `cursor` 作基线，与本地 `maxSeq` 比对；
  缺口 > 0 时消息区顶部提示条 + 一键重取。口径照 `ChatSeqMonitor`：**只认严格跳号**。
- 权限胶囊：原来显示设备 scope 且只读（点击只弹「由电脑端分配」），把「会话权限档位」也一起锁死了。
  现在优先显示档位、点开可切，走 `/permission <preset>`；目录缺货时用内置中文名兜底
  （否则胶囊上写着 `danger-full-access`）。
- 推理等级：模型下拉里每个模型的 `reasoning.efforts` 直接摊成胶囊，不另开一层菜单。
- Agent Preset：页头 `⋯` 菜单里的「Agent 预设」，标出当前项与不可用项（附 `trust` 原因）。

**C. 附件（发与收）**

- 发送：输入区「附件」多选 → 图片内联 base64、其他文件先 `POST …/attachments` 换 `receiptId`。
  字节只留在本地，**发送那一刻才上传**（`receiptId` 只对同一次 prompt 有效）。
- 接收：`ChatItem.User.attachments` 收 `attachmentId`，`ui/RemoteImage.kt` 按需拉取 +
  LRU 24 张 + 点开看大图。修掉「只带图不带字的消息整条消失」——原来 `contentText` 为空就 `continue`。

**D. 文件（写与移）**

- 预览页「编辑」：草稿进 `fileDraft`，保存走 `writeFile(..., etag=filePreviewEtag)`。
  **412 不覆盖**：把最新内容读回来、草稿留在编辑器里，并在提示里说清"你的编辑还在"。
- 「移动」对话框：一个目标路径同时承担改名与换目录（协议里 `move` 就是这么定义的）。
- 截断过的文件（>512 KiB）**禁止编辑**——在截断内容上整份写回会毁掉后半段。

**E. 两个新页**

- `ui/ModelsScreen.kt`：供应商卡片（凭据来源、模型数、是否继承默认目录）、编辑 baseURL/密钥
  （**只写不读**）、发现模型、恢复默认目录、删除、新建自定义 provider、全局模型目录。
- `ui/PluginsScreen.kt`：清单/版本/更新徽标/启停开关；`available:false` 时显示 `reason`
  而不是假列表；每次切换如实提示「重启 DSH 后生效」。
- 侧栏 3 项 → 5 项，`prefs.lastNav` 与 `Nav` 同步（漏了会"重启后回会话页"）。

**F. 工作区管理**

- 「新会话」下拉里每个工作区带「改名 / 移除」；只动 DSH 记录，不碰磁盘目录。

### 测了什么

- `tools/build.sh` 0 error（多次中途失败：`private set` 不可被扩展函数写、KDoc 里 `image/*`
  触发注释嵌套、`readAttachment` 未 import——都已进 `PITFALLS.md`）。
- `tools/docs-check.sh` 28/28；`tools/verify-markdown.sh` 22 条内置用例全过。
- 真实服务端截图（192.168.1.126:3090，1270×790）：会话页、模型页（50 个供应商）、插件页
  （已加载 14 · 异常 2）。权限胶囊由 `danger-full-access` 改成中文「完全访问」。

### 没验证什么（明账）

- **抽屉内部三栏**：合成点击打不进 Compose 窗口（`PITFALLS.md` §窗口以 1×1 创建），
  只验证了开关渲染。已登记 `DEBT-008`。
- 附件真发一张图、412 冲突、任务三栏、Agent 预设切换：未实测。

### 失败与回退

- 第一次 GUI 冒烟：分两次工具调用"先启动再截图"，窗口第二次就没了，误判成 OOM。
  实际是**后台进程在工具调用结束时被回收**；`hs_err_pid3.log` 才是真 OOM 的证据。
  改为同一次调用内启动→轮询→截图→结束。
- 截图点击总偏：`import` 出的是真实像素（1270×790），读图工具缩到 1080×686，
  按看到的坐标点会乘 0.85。改为 `xdotool getwindowgeometry --shell` 取真实尺寸换算。

---

## v1.0.0 第五轮 · 按官方 Web 端重排界面 + 多主题（2026-10-21）

### 基于什么

用户指令：「对照官方的 web 界面改改界面，颜色可以使用你的配方，顺便多加几个主题」。
参考对象是官方 DSH Web 端（1280×860 实拍）：左侧单栏（logo → 通栏新会话 → 图标导航 →
工作区分组 → 带相对时间的会话行 → 底部身份）、右侧面包屑头 + 收窄正文 + 悬浮输入卡。

### 干了什么

**A. 布局：两栏并排 → 一条侧栏**

- 旧结构是 68dp 图标竖条 + 会话页自带 282dp 左栏，两层左栏把正文挤到 1270 屏宽的中段；
  改成**一条 252dp 侧栏**（`ui/Sidebar.kt`，退役 `ui/SessionListPane.kt`），
  品牌行、通栏「新会话」、图标导航、工作区分组头、会话行、底部身份卡全在里面。
- 会话行右侧改显示**相对时间**（官方同款），工作区名移到页头面包屑——同一信息不写两遍。
  时间单位做过毫秒/秒兜底判断（`relativeTime()`）。
- 会话页去掉左栏：面包屑头（标题 / 工作区）+ 居中正文列（最大 860dp）+ 悬浮输入卡。
- 文件页原来是「授权根 222dp + 列表 + 预览 430dp」三栏，加侧栏后成四栏，
  授权根改为顶部**横向 chip**（`RootChips()`）。

**B. 输入区（对齐官方形态）**

- 圆角 18dp 卡片：无边框输入框 + 工具行（`＋ 命令` / 权限胶囊 / 圆形发送键 / 运行中显示停止）。
- 发送按钮从"带文字的长条"改成圆形图标键，运行中左侧出现停止键——运行中最常做的是"别跑了"。
- 统计一行从消息流第一格移到**输入框下面**（官方位置）：它描述"这一屏"，不是一条消息。
- 权限胶囊是**只读投影**（完全/受限/未读到），点一下解释"权限在电脑端分配"，不做假开关。

**C. 主题：明暗开关 → 5 套预设**

- `ui/Theme.kt` 重写：`Palette` 数据类 + 5 套预设——**暖砂**（默认，暖白底 + 赭石）、
  **冷瓷**（近官方冷白 + 靛蓝）、**苔原**（淡绿灰 + 松绿）、**深海**（深灰蓝 + 青）、
  **暮紫**（深紫灰 + 紫）。`system` 在暖砂 / 深海之间跟随系统明暗。
- 兼容旧配置：`prefs.json` 里的 `light`/`dark` 由 `resolvePalette()` 映射，老用户不会回到默认。
- 设置页改成**带色卡的卡片**（底 + 主色点 + 文字条），一排两张，选中有勾。
- 顺带把三级文字统一压深：原来浅底上只有 3.0:1，低于 AA 线；现在实测
  正文 ~15:1、次要 5.8–8.3:1、三级 4.3–5.1:1。
- 修掉连接页漏网的一处 `Color.White`（深色主题下 DS 徽标会白底白字）。

**D. 顺手**

- `prefs.lastNav` 真正生效：换页回写、启动恢复上次停留的页。

### 测了什么

- `tools/build.sh` 通过，0 error。
- 截图核对（真实 DSH 会话，1270×830）：暖砂下的会话页、文件页、设置页；
  表头底色/斑马纹、单元格行内代码、侧栏选中态与相对时间、授权根 chip、设置页色卡均正确。
- 五套主题对比度用脚本实算（正文 / 次要 / 三级 / 主色），全部达到 AA。
- `tools/docs-check.sh` 28 项全绿。

### 没验证什么（明账）

- **深色只截到「深海」一套**（会话页）：背景、青色主色、表格底纹、输入卡、权限胶囊都正确。
  「暮紫」以及深色下的文件页 / 设置页没截到——沙箱 GUI 多次被地址空间耗尽打断，
  只能在内存干净时抢一张。
- 主题卡片的第二排（苔原/深海/暮紫）在 830px 高的窗口里被折到屏外，没截到。
- 鼠标点击仍进不去（KWin 不接管 root 窗口），所以悬停态、菜单展开、切主题的交互没实测。

---

## v1.0.0 第四轮 · Markdown 渲染修复（2026-10-21）

### 基于什么

用户报障：「会话中的 MD 格式没渲染，已看到表格的样式都不对，其他的你自己检查」。

先取真实语料定量，不靠猜：解出本机令牌拉三个会话（631 / 1112 / 650 事件），
统计 92 段助手正文、16368 字符的语法分布 —— 行内代码 97、`**粗体**` 77、
**表格行 54（10 张表）**、标题 31（`##` 28 / `#` 3）、无序列表 32、有序列表 7、
围栏代码 8（bash 3 / 无语言 5）、链接 4。

结论：旧 `ui/Components.kt` 的 `parseMarkdown` **根本没有表格分支**，
`| a | b |` 与 `|---|---|` 全落进 `Paragraph`，界面上就是一排竖线。

### 干了什么

**A. 拆层：解析与渲染分居两个文件（可被脚本直接跑）**

- 新增 `data/Markdown.kt`（489 行，纯函数，**不 import Compose**）：
  `parseMarkdown()` 输出 `MdBlock`（Paragraph / Heading / Code / Bullets / Quote / Rule / Table），
  行内 `parseInline()` 输出 `InlineSpan(text, bold, italic, code, strike, url)`。
- 新增 `ui/MarkdownView.kt`（396 行）：`MarkdownText()` 按块分支；
  `Components.kt` 从 420 行瘦到 141 行，只留卡片/空态/状态点/加载块/外链。

**B. 表格（报障主体）**

- 判据改成**分隔行**：先攒段落，遇到 `|---|`（含 `:--`/`:-:`/`--:`）才回头把上一行取回当表头；
  解析不出对齐行就绝不进 Table 分支（否则 `a | b` 这类正常文字会被误判）。
- `parseTableRow` 处理 **首尾管道可省**与 `\|` 转义。
- 行列不齐在**解析层**归一（`normalizeRow`）：少格子补空、多格子并入最后一列。
- 渲染：外层 `horizontalScroll`，列宽按视觉宽度算（中文按 2 宽，夹在 80–320）；
  表头 `raisedSurface` 底色 + 斑马纹 + 1dp 分隔线；单元格内容过 `annotatedFrom()` 二次解析行内标记
  （会话表里大量 `` `code` `` 与 `**粗体**`，只画原文就是"竖线堆"）。

**C. 顺带修的其他 Markdown**

- 标题 1–6 级分级（`headingStyle(level)`，必须 `@Composable` 才能读 `MaterialTheme.typography`）。
- 列表：无序 `-`/`*`/`+`、有序 `1.`、任务 `[ ]`/`[x]`、两级缩进、圆点/数字标记分离。
- 引用块左侧竖线 + 弱化底色；`---` 单独成 `Rule`。
- 代码块：语言标签 + 「复制」按钮（1.4s 后回「已复制」）+ 横向与纵向滚动 + 可选中。
- 链接：`[文字](url)` 与裸 URL，`TextLayoutResult.getOffsetForPosition` 命中后 `openExternal`。
- 未闭合标记（`**abc`）按字面保留，不吞掉半行。

**D. 验证脚本（把"能不能渲染"变成可机检）**

- `tools/verify-markdown.sh` + `tools/VerifyMarkdown.kt`：22 条内置用例
  （标准表 / 省首尾管道 / 转义竖线 / 对齐 / `---` 仍为分隔线 / 无分隔行仍是段落 /
  表格前段落不丢 / 少多格子归一 / 行内标记 / 未闭合保字面 / 围栏内表格语法不外泄 / 列表任务 / 1-2-6 级标题）。
- **真实数据断言**：至少解析出 1 张表 + 无空表 + 每行宽度等于列数，任一失败退出码 2。

### 测了什么

- `tools/build.sh` 通过，0 error。
- `tools/verify-markdown.sh` 内置 22 项全过；三份真实会话表格 **4 / 4 / 2** 张，
  行数不齐 **0** 行，`h4` 无 `Code` 块。
- 截图核对（`Content window` 1270×830）：表头灰底、斑马纹、列分隔、
  单元格内行内代码与链接样式、`##` 标题分级、`- ` 圆点列表均已正确渲染。
- `tools/docs-check.sh` 28 项全绿。

### 没验证什么（明账）

- 表格**横向滚动到底**与超长表格的性能未在真机看过（沙箱只能截首屏）。
- 真机 `dpkg -i` 仍未验证（沙箱限制不变）。

---

## v1.0.0 第三轮 · 按安卓版缺口实现（2026-10-21）

### 基于什么

用户指令：「按对照安卓版的缺口进行实现」。`PARITY.md` 第三节排的顺序是
会话侧栏 → 聊天可用性 → 阅读体验 → 输入效率，这一轮把前三组的九项清掉了。

### 干了什么

**A. 会话侧栏（左栏从 `ChatScreen.kt` 拆成 `ui/SessionListPane.kt`）**

- 会话行加悬停 `⋯` 菜单：重命名 / 分叉出新会话 / 复制会话 ID / 归档。
- 左栏标题旁加「已归档」开关，切 `archived=true` 重取列表；归档态下隐藏「新建」。
- 新增 `AppState.showArchivedSessions()`：切换时清空选中、审批与历史，避免串台。
- 新增 `AppStateChat.forkSession()`：`POST …/fork`，成功后 `selectSession()` 直接跳过去。

**B. 聊天可用性**

- **插话**：`AppState.sendMessage(text, steer)` 透传 `mode: steer`；运行中发送按钮文案变「插话」，
  回车同义（`ui/Composer.kt`）。
- **审批代签**：`loadApprovals()` 除计数外还存 `approvals` 列表；新增 `ui/ChatPanels.kt` 的
  `ApprovalPanel()` 逐条列出工具名/风险/理由/详情，可「允许一次」或「拒绝」，
  带 `approvalsBusy` 防连点；本地先摘条目再等 REST。原来那句"请在电脑上处理（桌面端不代签）"删掉。
- **交付物卡片**：`ChatItem.Deliverable`（含 `DeliverableFile{path, description}`）替代原来的文本提示；
  `DeliverableCard()` 点一下走 `roots/resolve` 定位到文件页。

**C. 阅读体验**

- **工具专用块**：新增 `data/ToolBlockView.kt`（纯函数分发，解析失败一律回落 Generic）与
  `ui/ToolBlocks.kt`（read 路径头 / bash `$ 命令` + 剥 ANSI / edit `-`/`+` 对照 / 通用）。
  动作短语口径照安卓端 `toolActionsFor`，`run_code` 扫内层 `tools.*` 补全。
- **耗时**：`ChatItem.Tool.durationMs = result.time - call.time`，负值（时钟倒退）丢弃不显示。
- **会话统计**：`chatStats()` 投影出轮次/工具次数/失败数/思考段/产出数，消息区顶部一行。

**D. 输入效率**

- 斜杠命令：`loadCommands()` 取会话命令清单，输入 `/` 给候选，选中即填（带参数）或即执行。
- 输入框 placeholder 补上「/ 看命令」。

**E. 结构（DEBT-003 的一半）**

- `AppState.kt` 793 → 667 行：文件域动作拆到 `AppStateFiles.kt`，聊天低频动作拆到 `AppStateChat.kt`。
  `scope` 与 `client()` 改为 `internal` 供同包扩展使用。**这是为了让 800 行预算继续成立，不是为了好看。**

### 测了什么

- `tools/build.sh` 通过，0 error。
- **真实数据三轮验证**（本机 DSH 2.x，会话 `session-af1167fb` / `0cc8b08a` / `3093c309`）：
  - 事件 259/227/289 → 可渲染 **96 / 88 / 103** 条；
  - 工具专用块命中 **30/37、21/24、40/42**（Terminal 为主，Read/Diff 都有命中的真实样本）；
  - 耗时可算 **100%**（37/37、24/24、42/42）；
  - `Deliverable` 真实解析出 1 条（`data.files[].path + description`）。
- `tools/docs-check.sh` 28 项全绿。

### 没验证什么（明账）

- **界面交互**：本沙箱窗口能起来（1270×790），但以 root 连 `DISPLAY=:0` 时 KWin 不接管，
  鼠标点击进不去，只能截到首屏静态图，**展开态、菜单、审批卡片都没在真机上看过**。
- 真机 `dpkg -i` 仍未验证（沙箱限制不变）。

---

## v1.0.0 第二轮 · 真机报障收口（2026-10-21）

### 基于什么

用户在 UOS 上装完第一版 deb，两个报障：

1. 「安装无桌面快捷方式」
2. 「会话打开无任何显示」

追加要求：对照只读仓库 `dsh-companion`（安卓端 v2.2.44）补齐能力、并参照其工程制度。

### 干了什么

**A. 快捷方式（打包链路）**

- `tools/package.sh` 新增第 5 步：`dpkg-deb -R` 解包 → 补文件 → `dpkg-deb --build` 重打。
- 新增 `tools/linux/dsh-desktop.desktop`（中文名「DSH 桌面」、`StartupWMClass` 用实测值）。
- 新增 `tools/IconGen.java`：1024 画布画青绿渐变圆角底 + 白色 DS 字标，降到 32/48/64/128/256。
- 新增 `tools/linux/postinst-shortcut.sh` / `postrm-shortcut.sh`：用户桌面拷贝与清理。
  合并时用 Python 摘掉原 postinst 末尾的 `exit 0`。
- `Main.kt` 新增 `ensureDesktopShortcut()`：安装时若无图形会话则首次启动补一次（写标记，只补一次）。

**B. 会话空白（解析链路，主因）**

- `data/ChatHistory.kt` 新增 `unwrapEvent()`，解开 `{type:"event", event:{…}}`；
  两趟循环（工具结果索引 + 主投影）都改用它；兼容裸事件形状。
- 同文件新增 `HistoryPage` / `parseHistoryOldestSeq()` / `mergeToolRuns()` / `ChatItem.ToolGroup`。
- `data/ApiExt.kt` 新增 `listMessagesPage()`（`beforeSeq` / `maxMessages`）。

**C. 可见性（不再静默失败）**

- `AppState` 新增 `sessionsError` / `historyHasMore` / `loadingOlder` / `oldestSeq`。
- `refreshSessions()` 的 `onFailure` 从空注释改为写入错误状态。
- `ChatScreen` 左栏显示失败原因 + 重试；消息区为空且无错误时给出解释性空态；
  顶部加「加载更早的消息」。

**D. 授权根补齐**

- `AppState.adoptDevice()`：不拿空值覆盖已有 `scopes` / `rootIds`；`bootstrap()` 增 `refreshDevice()`。

**E. 窗口尺寸兜底**

- `Main.kt` 的 `LaunchedEffect` 里 `window.width < 400` 时 `setSize(1280, 820)`，
  防 UOS 上 DPI 异常把 `1280.dp` 算成 0。

**F. 工程宪法**

- `AGENTS.md` 重写为宪法体例（照 `dsh-companion/AGENTS.md` 裁剪）：路由表、命令、同步表、
  分层、行数预算、`@patch`、测试纪律、坑位索引、制度差异。
- 新建 `docs/project/` 八份（后补第九份 `CODEMAP`）：`STATUS` / `ROADMAP` / `ARCHITECTURE` / `PITFALLS` /
  `TECH-DEBT` / `TESTING` / `RELEASE` / `CHANGELOG-DEV`，`PARITY.md` 一并移入。
- 新增 `tools/docs-check.sh`（版本号、PARITY 统计、坑位同名、行数预算、代码地图双向一致）与
  `tools/verify-history.sh`（真实历史解析验证）。

**G. 代码地图（后补，用户要求"方便 AI 读代码"）**

- 新增 `docs/project/CODEMAP.md`，只记两份**事实**：
  - **A. 目录结构**：13 个源码文件逐个列职责、关键符号、谁调它；工具脚本逐个列作用与产出。
  - **B. 接口**：REST 分「已接入 25 个」与「SDK 有但未接入 7 组」两栏，WebSocket 事件 9 个分支，
    历史事件 6 渲染 + 6 忽略。
  - **C. 常见改动路线**：改某功能要动哪几个文件、联动哪份文档。
- `AGENTS.md` 增第 4.4 节「代码地图是硬约束」：改代码三步（看 CODEMAP 定位 → 改 → 回改 CODEMAP），
  并说明"地图脱节比没有更坏"的理由。
- `docs-check.sh` 新增第 5 节，四项双向校验：`src` 下每个 `.kt` 必须登记、登记的必须还在、
  引用的 `tools/` 路径必须存在、接口表里的 `DshApi.xxx()` / `ApiExt.xxx()` 必须真有该方法。
- **门禁自证有牙**：故意把 `SettingsScreen.kt` 改名、把 `DshApi.health()` 改成 `healthzz()`，
  三项同时报警；恢复后退出码 0。门禁从 22 项扩到 **28 项**。

### 测了什么

- `tools/build.sh`：通过，467 个类。
- **真实数据**：解出本机设备令牌 → 取真实会话（305 条、`hasMore: true`）→ 修复后
  **58 条可渲染**（Tool 15 / ToolGroup 10 / Reasoning 23 / Notice 5 / Assistant 4 / User 1）。
- `dpkg-deb -c`：新包含 `usr/share/applications/dsh-desktop.desktop` 与 5 档 png。
- `tools/docs-check.sh`：全绿（首次）。
- **未做**：真机 `dpkg -i`（沙箱权限）、界面截图（root 窗口 1×1、无 Xvfb）、Windows msi（无机器）。

### 踩过的坑（都在同一次修复里）

历史事件外层包装、`turn/end` 的 `completed`、`postinst` 的 `&&` 短路、
追加 postinst 被 `exit 0` 挡住、`StartupWMClass` 与实际 WM_CLASS 不符、
图标只有 32×32。全部已进 `PITFALLS.md`。

---

## v1.0.0 第一轮 · 从零到可运行（2026-10-21）

### 基于什么

需求：基于 `dsh-remote-bridge` 的功能，做一个支持 Windows + UOS 的**非网页**客户端。
两个上游仓库全程只读，产出落在本工作区。

### 干了什么

- 选型：Kotlin 2.1.0 + Compose Multiplatform Desktop 1.7.3（Skia 直绘）+ JDK 17。
- `data/DshApi.kt`：复制上游 `kotlin-sdk`，改包名，把 `execute` / `executeJson` /
  `ensureSuccess` / `endpoint` 放宽为 `internal`。
- `data/ApiExt.kt`：补会话归档列表、`roots/resolve`、提问应答、上传。
- `data/ChatHistory.kt`：历史事件 → `ChatItem` 六类投影。
- `data/AppStorage.kt`：配置目录 + 令牌 AES-GCM（`device.key` 0600）。
- `AppState.kt`：连接、bootstrap、事件订阅、动作。
- `ui/`：`Theme` / `Components`（含自写轻量 Markdown）/ `App` / `ConnectScreen` /
  `ChatScreen` / `FilesScreen` / `SettingsScreen`。
- 构建链：`tools/build.sh` 直调 `K2JVMCompiler`；`tools/package.sh` 用 Python `zipfile` 打 jar
  再 `jpackage`；`tools/mock-server.py`（端口 3091）；`tools/portable/run.sh`；
  `tools/windows-libs/` 放 Windows 用 skiko。

### 测了什么

- 编译通过；用 mock 服务端跑通连接/会话/文件/设置四页，截图 10 张（`shots/`）。
- deb 90 MB、便携包 74 MB 均可启动。
- 真实 DSH 只做了只读接口验证（healthz / sessions / roots / devices-self）。

### 踩过的坑

4GB 地址空间导致 Gradle 起不来、`jar` 建线程失败、`TMPDIR` 对 jpackage 无效、
skiko 解压原生库失败、`core-runtime` 在 aar 里、`rm` 被 genie-trash 拦截、
冷启动会话空白、折叠箭头字符退化成圆点。全部已进 `PITFALLS.md`。

**收尾结果**：Gitee 发行版最终 14 个资产齐了，且与 GitHub 逐字节一致（SHA256SUMS 可用）。
路上还撞到 **Gitee 仓库附件 1 GB 总配额**（装到 arm64 deb 时 HTTP 400 报配额），
删掉最旧的 v1.0.0 发行版腾位——历史版本以 GitHub 为准，Gitee 只保留最新一个完整版本。
