# 开发日志（CHANGELOG-DEV）

> 每版三节：**基于什么**（触发/前提）→ **干了什么**（文件级）→ **测了什么**（门验结果）。
> 失败与回退也要写——那是后来者最需要的部分。

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
