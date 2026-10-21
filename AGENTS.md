# AGENTS.md — dsh-desktop 工程宪法

> 每次会话必然读到的约定。规矩写在这里，不靠记忆、不靠口头。
> 体例照搬姊妹仓 `dsh-companion/AGENTS.md`，按"桌面端 + 双平台打包 + 只读上游"裁剪。
>
> **体积纪律**：本文件只写**命令、契约、触发词与一两行的铁律**；
> 说不清、要摆证据的内容一律进 `docs/project/`（坑位 → `PITFALLS.md`，设计 → `ARCHITECTURE.md`）。
> 超长会被静默截尾，读者就丢了末尾的规矩。

---

## 0. 一分钟上手

| 我想… | 先读 |
|---|---|
| **要改某个功能，先看哪几个文件** | **`docs/project/CODEMAP.md`**（目录结构 + 接口 + 常见改动路线） |
| 知道现在到哪了 | `docs/project/STATUS.md` → `docs/project/ROADMAP.md` |
| 知道某能力有没有、差什么 | `docs/project/PARITY.md`（对齐安卓端 v2.2.44 的能力矩阵） |
| 知道为什么这么设计 | `docs/project/ARCHITECTURE.md` |
| 知道这次改了什么、测了什么 | `docs/project/CHANGELOG-DEV.md` |
| 改代码前避坑 | `docs/project/PITFALLS.md`（名字 = 触发词 = 锚点） |
| 发版 | `docs/project/RELEASE.md` |
| 看还有什么欠账 | `docs/project/TECH-DEBT.md` |

> **每轮开工先读 `STATUS.md`**；涉及界面时同时读 `src/.../ui/Theme.kt` 的令牌区。

---

## 1. 本项目是什么（一句话与边界）

- **定位**：DSH（DeepSeek Harness）的**桌面原生客户端**，Kotlin + Compose Desktop，
  界面由 Skia 直接绘制。**不加载 HTML、不嵌 WebView、不用 Electron/Tauri 套壳。**
- **双平台**：UOS / Deepin（deb，自带精简 JRE）+ Windows（msi，需在 Windows 上打）。
- **上游**：`dsh-remote-bridge` 的 `kotlin-sdk`（AGPL-3.0），复制到 `data/DshApi.kt` 并改包名。
  协议以它的 `docs/api/openapi.yaml`、`asyncapi.yaml` 为准。
- **只读上游**：`dsh-remote-bridge` 与 `dsh-companion` 两个仓库**全程只读**，
  不得创建/修改/删除其中任何文件。产出只落在本工作区。
- **不做什么**：不自带 DSH 能力、不代理 WebUI、不做手机端（那是 `dsh-companion` 的事）。
  能力缺口先在 `docs/project/PARITY.md` 登记，再决定补哪一项。

---

## 2. 命令（本地唯一可信口径）

```sh
tools/build.sh              # 编译到 build/classes（日常改完先跑这个）
tools/build.sh jar          # 再打 build/dsh-desktop.jar
tools/build.sh run          # 编译后直接开窗口
tools/package.sh            # 全流程 → dist/dsh-desktop_<ver>_amd64.deb（含桌面入口补齐）
tools/fetch-platform.sh <p> # 拉取目标平台 skiko 原生库（linux-x64 / linux-arm64 / windows-x64）
tools/docs-check.sh         # 文档一致性门禁（必须全绿）
tools/verify-files.sh       # 文件类型判定与解码用例（49 条）
tools/verify-history.sh <json>   # 用真实历史响应验证解析（防"整页空白"回归）
tools/verify-markdown.sh <json>  # 验证 Markdown 解析：内置 22 条用例 + 真实会话表形状断言
python3 tools/mock-server.py     # 离线服务端，端口 3091（别抢 3090）
```

Windows 侧（**同一个 bash 脚本**，不是第二套流程）：

```bat
build-windows.bat           rem = bash tools/fetch-platform.sh windows-x64 && bash tools/package.sh
```

前置：JDK 17+、Git for Windows（提供 `bash` / `cygpath` / `curl`）、Python 3、WiX 3.x（仅 msi 需要）。

**为什么构建脚本不用 Gradle**（三条理由，都在 `tools/build.sh` 头注释里）：
1. 本机单进程虚拟地址空间 4GB 硬限制，Gradle 在**配置阶段**就会被打死（`Map failed`）；
2. `jar`、`jpackage` 也要建线程，必须靠 `JAVA_TOOL_OPTIONS` 压住；
3. `TMPDIR` 对 jpackage 不生效，必须同时给 `-Djava.io.tmpdir`。

> Gradle 脚手架（`build.gradle.kts` / `gradlew.sh` / `gradle.properties`）**已移除**：
> 它在本机跑不起来、CI 也没法验证，留着只会让人误以为有第二条可信路径。
> 依赖坐标以 `NOTICE` 与 `lib/` 的实际文件为准。

---

## 3. 版本与文档同步（本项目最高频的失分点）

| 改动 | 必须同步 |
|---|---|
| 版本号 | `version.txt`（**唯一来源**），改完 `tools/docs-check.sh` 会校验 README / STATUS / CHANGELOG-DEV 跟它对得上 |
| **新增/删除/改名源码文件** | **`docs/project/CODEMAP.md` 的 A.2 文件表**（门禁双向比对，漏一个就红） |
| **新增端点、WebSocket 事件、历史事件渲染** | **`CODEMAP.md` 的 B.1 / B.3 / B.4** |
| 能力增减 | `docs/project/PARITY.md` 状态列 **与末尾统计数字**（`✓ N 项 · △ N 项 · ✗ N 项`）、`docs/project/ROADMAP.md` 任务行 |
| 新增坑位 | `PITFALLS.md` 正文 + 本文件第 7 节的名字（两边必须同名） |
| 产物变化 | `README.md` 安装章节的体积/项数、`RELEASE.md` 的产物清单 |
| 协议适配 | `ARCHITECTURE.md` 对应段落 + 源码里 `@patch` 四要素 |
| **新增/改动构建脚本或工作流** | **`CODEMAP.md` 的 A.3 / A.4**、`README.md` 第 10 节 |

**门禁**：`tools/docs-check.sh` 会机检上面 1–3 行的**数值一致性**与**坑位同名**，不过就是没做完。

**顺序（不可倒）**：改代码 → 改文档与版本号 → `tools/build.sh` → `tools/docs-check.sh` →
同步产物 → 打包。先打产物再改文档，等于让文档描述一个不存在的状态。

---

## 4. 架构纪律

### 4.1 分层与依赖方向

```
ui/             ← 只消费 AppState 字段、调 AppState 动作；不持有协议或权限决策
AppState        ← 状态机本体：连接、会话列表、历史分页、事件合并；网络调用一律经 client()
AppStateChat/Files/Projections/Settings ← 同包扩展函数，按域承载低频动作（800 行预算的唯一拆法）
data/           ← DshWorkspaceClient 是唯一网络边界；ChatHistory 是唯一历史投影出口
```

- **唯一网络边界**：`data/DshApi.kt`（含 `ApiExt.kt` 的扩展）。UI 里不许出现 OkHttp / URL 拼接。
- **阻塞 IO 只在 `Dispatchers.IO`**，`scope.launch` 里必须包 `withContext(Dispatchers.IO)`。
- **历史投影单一出口**是 `parseHistory()`，只输出 `User` / `Assistant` / `Reasoning` /
  `Tool` / `ToolGroup` / `Deliverable` / `Notice`。`step/*`、`job/*`、token 序列、`turn/start`、
  `session/end-seed` 与未知事件**一律不渲染**。
- **状态机按域拆分，别往 `AppState.kt` 里堆**：文件域动作进 `AppStateFiles.kt`，
  聊天低频动作进 `AppStateChat.kt`，两者都是同包扩展函数。新增动作先问"属于哪个域"，
  一律塞主文件会在下一轮直接破 800 行（`DEBT-003` 就是这么来的）。
  代价：`ui/` 调用这些扩展**必须按函数名显式 import**，见 `PITFALLS.md` §同包不同目录的扩展函数要显式 import。
- **工具调用怎么画是分发的，不是嗅探的**：工具名 → 专用视图（Read/Terminal/Diff/Generic）
  在 `data/ToolBlockView.kt`（纯函数）；UI 只认 `ToolBlockView`。解析失败必须回落 Generic，
  专用块是增强不是依赖。
- **Markdown 解析与渲染必须分居两个文件**：解析在 `data/Markdown.kt`（纯函数，**禁止 import
  Compose**，否则 `tools/verify-markdown.sh` 跑不起来），渲染在 `ui/MarkdownView.kt`。
  表头判定靠分隔行 `|---|`，**不靠行首管道**；表格行列不齐在解析层归一，UI 直接消费。
  见 `PITFALLS.md` §表格只按行首管道判定会退化。
- **颜色只有一处来源**：组件里只准从 `LocalAppColors`（语义色）或 `MaterialTheme.colorScheme`
  （Material 组件）取色，**不许出现 `Color(0x…)` / `Color.White`**。新增主题 = 在
  `ui/Theme.kt` 加一个 `Palette` 常量并挂进 `AllPalettes`，设置页的色卡会自动列出来；
  改配色必须重算对比度（正文 ~15:1、次要 ≥5.8:1、三级 ≥4.3:1），别为了"轻"调浅。
- **事件必须解包**：历史每条是 `{type:"event", event:{…}}`。见 `PITFALLS.md` §历史事件有外层包装。
- **任何 `onFailure` 不许留空**：要么进状态字段，要么 `showToast`。静默失败等于把用户扔进空白页。
- **能力位缺了要明说，不许"静默少一个功能"**：服务端没声明某项能力时，界面必须给出可见解释
  （`lacksCapability()`），并且把「还没收到 `connection.ready`」与「服务端不支持」分开说——
  前者是未知，后者是结论，混为一谈会冤枉老服务端。安卓端在这上面踩过两次
  （审批卡片不弹，看起来和"本来没有待审批"一模一样）。
- **状态字段要能被扩展函数写**：`AppState` 里由 `AppState*.kt` 赋值的字段用 `internal set`，
  只有类内赋值的才用 `private set`。Kotlin 的 `private set` 对同包扩展函数**不可见**，
  写成 `private` 会在扩展里报"无法赋值"。
- **乐观并发的写操作必须带版本号**：provider/插件这类设置走 `expectedRevision`；
  服务端回 412/CONFLICT 时**不许自动重试覆盖** —— 那等于替用户决定"我的更对"。
  正确做法是把最新内容读回来、把用户的草稿留着，让他自己核对。
- **一次性凭证不许存**：附件上传换来的 `receiptId` 只对同一次 prompt 有效。
  把字节留在本地、发送那一刻才上传，别在"选文件"时就换凭证（会在服务端留下孤儿记录）。

### 4.2 行数预算与债务台账

- 预算 **800 行/文件**，由 `tools/docs-check.sh` 机检。
- 超预算必须先在 `docs/project/TECH-DEBT.md` 的例外表登记（含理由与清账路径），否则门禁失败。
- 当前唯一例外：`data/DshApi.kt`（874 行，上游 SDK 原样移植，拆分会让后续同步上游变难）。
- `AppState.kt` 贴在预算线上下（781 行）。腾地方的办法是**把常量/枚举搬出去**
  （`Enums.kt` 就是这么来的），不是把状态搬出去——状态搬到别的文件也还是那一个类。
- **只许收紧**：债务消失时，同一提交里删掉台账条目。

### 4.3 适配补丁四要素（`@patch`）

凡因上游/内核/bridge 的行为而写的适配代码，必须在源码里自报：

```kotlin
// @patch
// id: short-kebab-id
// for: 适配什么（哪个接口、哪个版本、哪种形状）
// why: 为什么不能用正规写法（没理由 = 债务）
// until: 什么条件达成就能删
// test: 哪个验证盯着它（`tools/verify-history.sh`、`docs-check`，或 PITFALLS 条目名）
```

没有 `@patch` 的适配代码 = 没人认领的补丁，后来者不敢删，只会在上面继续堆。

### 4.4 代码地图（`docs/project/CODEMAP.md`）是硬约束

`CODEMAP.md` 只记**两份事实**：**目录结构**（哪个文件干什么、关键符号、谁调它）与
**接口**（端点、WebSocket 事件、历史事件，以及哪些 SDK 能力**尚未接入**）。
设计理由不写在这里，写 `ARCHITECTURE.md`。

**改代码的标准动作是三步，不是两步**：

```
看 CODEMAP 定位要动哪几个文件 → 改 → 回 CODEMAP 改对应的表
```

- **双向机检**：`tools/docs-check.sh` 既查"表里的文件必须存在"，也查"`src/` 下每个 `.kt` 必须在表里"。
  只加文件不改表、或只在表里写不写代码，都会让门禁变红。
- **为什么要这么较真**：这个仓库最常被 agent 接手，而接手者最贵的动作是"找到该改哪"。
  代码地图一旦和代码脱节，它比没有更坏——会把人引到已删除的文件上。
- **新增接口时先判位置**：桌面端真在用的写 B.1，只是 SDK 自带、界面没接的写 B.2。
  混在一起会让下一个人以为功能已经做完了。
- **新增一个页面要改五处**：`src/Enums.kt` 的 `Nav`、`ui/App.kt` 的 `MainShell` 分支与
  `prefs.lastNav` 名字映射、`ui/Sidebar.kt` 的 `NavItem`、`CODEMAP.md` A.2。
  漏掉 `lastNav` 映射的表现是"重启后回到会话页"，很难联想到原因。

---

## 5. 测试纪律

**现状（诚实交代）**：本项目**没有 JVM 单元测试框架**——本机连 Gradle 都起不来，
JUnit + Gradle 那套无处安放。这是 `TECH-DEBT.md` 的 **DEBT-001**，不是"不重要的意思"。
现有的验证走"纯函数 + 独立 main + 断言退出码"这条路（`tools/Verify*.kt`），CI 上真跑。

替代门禁，按此顺序：

| 层 | 手段 | 说明 |
|---|---|---|
| 编译 | `tools/build.sh` | 每个改动都要过；CI 也跑 |
| 文档 | `tools/docs-check.sh` | 数值一致、坑位同名、行数预算 |
| 用例 | `tools/verify-files.sh` / `tools/verify-markdown.sh` | 纯函数断言，CI 上必跑（linux-x64） |
| 解析 | `tools/verify-history.sh` | 拿真实历史 JSON 跑 `parseHistory`，条目数为 0 即失败（需自备输入，CI 不跑） |
| 打包 | `tools/package.sh` | 只在发版前跑；三平台由 `release.yml` 覆盖 |
| 冒烟 | 真机装 deb | **编译绿 ≠ 界面对**。涉及窗口、字体、图标、DPI 的一律要真机看 |

**CI 纪律**（`.github/workflows/` 三套，分工不许混）：
- `ci.yml` 只做编译与门禁，**不产安装包**，必须快；红了就是不许合并。
- `release.yml` 才产产物，按平台分 job（`ubuntu-22.04` / `ubuntu-22.04-arm` / `windows-latest`），
  tag 推送时才发布 Release。
- `mirror-sync.yml` 只搬 refs，**不搬 Release**（产物本来就是 GitHub 原生的）。
- 工作流里**不许写本机绝对路径**；脚本靠环境变量与 `uname` 自适应，CI 只负责给 `JAVA_HOME`。
- 平台相关的坑（类路径分隔符、`cygpath`、Python 命令名）**只允许在脚本里解决一次**，
  不许在 YAML 里为某个平台开小灶。

**铁律**：
- 修 bug 必须在 `PITFALLS.md` 留一条（现象/根因/修法/防回归）——这是当前成本最低的回归保护。
- 能写成脚本验证的（解析、协议形状）不要只写文档。
- 不许为了"看起来有测试"加空壳。

---

## 6. 对外表述纪律

- **「已实现」≠「已打包」≠「用户装上了」**。三态必须分清；没真机验证就明说"仅编译通过"。
- 公开文本只写事实：改了什么、为什么、门验结果。不写私聊口语与自封头衔。
- **禁止**：未经用户明确同意就推送、发版、上传产物。本地编译、测试、写文档可直接做。
- 版本号只在 `version.txt` 一处定义（两个构建脚本与文档都读它），不要各写各的。

---

## 7. 坑位索引（名字 = 触发词 = 锚点）

正文在 `docs/project/PITFALLS.md`，动手前按名字读。**不看就改 = 重踩。**

- `历史事件有外层包装`
- `turn/end 的正常收尾是 completed 不是 ok`
- `历史默认只给最近一段`
- `会话列表加载失败被静默吞掉`
- `配对响应不带 rootIds`
- `冷启动会话空白`
- `jpackage 的 .desktop 不装进 /usr/share/applications`
- `postinst 里 [ ] && continue 会掐断脚本`
- `追加 postinst 必须摘掉末尾的 exit 0`
- `StartupWMClass 必须等于 AWT 实际 WM_CLASS`
- `图标只有 32×32`
- `折叠箭头用字符会退化成圆点`
- `窗口以 1×1 创建（本沙箱专属）`
- `沙箱 4GB 地址空间：Gradle 起不来`
- `TMPDIR 对 jpackage 不生效`
- `skiko 解压原生库失败`
- `root 与 tmp 在这台机器上都不可靠`
- `Windows 上的中文文本多半不是 UTF-8`
- `Desktop.open 在 UOS 上经常静默失败`
- `服务端不保证遵守 Range 请求`
- `同一个函数被拼上两遍 Composable 注解`
- `core-runtime 在 aar 里`
- `rm 被 genie-trash 拦截`
- `会话标题为空`
- `同包不同目录的扩展函数要显式 import`
- `修沙箱里的 java 脚本报 unable to create native thread`
- `表格只按行首管道判定会退化`
- `KDoc 里写 image 通配符会吃掉后面的注释`
- `截图坐标要按窗口真实像素算`
- `后台进程在 Bash 调用返回后被回收`
- `构建脚本在运行中被改写会读到半行`
- `Windows 上类路径分隔符是分号且路径要 cygpath`
- `lib 里的 skiko 运行时必须按平台剔除`
- `GITHUB_TOKEN 改不了 workflow 文件`

> 新坑位：名字加进上面清单 + 原文写进 `PITFALLS.md` 并补 `### 同名` 锚点。
> `tools/docs-check.sh` 会检查两边是否一一对应。

---

## 8. 制度来源与差异说明

借鉴 `dsh-companion/AGENTS.md` 的五项制度，按桌面端特色裁剪：

| 借鉴项 | 原样 | 本项目的裁剪 |
|---|---|---|
| 一分钟上手路由表 | 6 条场景路由 | 收敛为第 0 节一张表，多一条"看欠账" |
| 命令唯一口径 | `scripts/build.sh`、`docsCheck`、`release.sh` 两阶段 | 改为 `tools/*.sh`；`package.sh` 一步到底（没有回填环节，不需要分阶段） |
| 债务台账只许收紧 | 800 行预算 + `ArchitectureGuardTest` 机检 | 保留预算与台账；机检改由 `tools/docs-check.sh` 承担（**没有 JUnit，只能靠脚本**） |
| `@patch` 四要素 | 五字段 + 测试真实存在 | 保留五字段；`test` 字段允许指向脚本或 PITFALLS 条目（没有单测可指） |
| 版本与文档同步 | 9 份 docs/project + README 双站对账 | 保留同步表；`docs-check.sh` 覆盖版本号与 PARITY 统计（README 只有中文一份，无"双站漂移"问题） |
| 坑位索引 | 名字 = 锚点 | 完全保留，并增加"两边同名"机检 |

**刻意没抄的**：
- CDP 探针体系、WebView 与原生双实现、熄屏断流、IME 注入——桌面端没有对应场景。
- 多模块依赖方向机检——本项目单模块 4800 行，分层靠人守 + 代码评审。
- README 中英双站——只有一份中文 README。

**本项目特有的三条**：
1. **双平台打包矩阵**：deb 在 Linux 打、msi 只能在 Windows 打，`RELEASE.md` 分两条线写。
2. **只读上游依赖**：`dsh-remote-bridge` / `dsh-companion` 只读，协议变更要先看它的 openapi。
3. **没有单测是明账**（DEBT-001）：不假装有，用脚本 + 真机冒烟顶住。

> 与 `dsh-companion/AGENTS.md` 共有的三条措辞保持一致：**文档漂移**、
> **端口 3090 归属**（真实 DSH，mock 用 3091）、**对外表述三态**。改一处记得改另一处。
