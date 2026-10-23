# 桌面端 vs dsh-companion v2.2.52 对标与下步计划

- 日期：2026-10-23
- 对标基准：`dsh-companion` **v2.2.52**（+1 个未发版提交 `482e049`），桥侧 `dsh-remote-bridge` **v2.6.0**
- 桌面端现状：**1.0.1**（tag `v1.0.1` = `67146dd`），tag 后有 8 个未发版提交（最新 `7a71475`）
- 旧对照表 [`PARITY.md`](PARITY.md) 对齐的是 companion **v2.2.44**，本次补齐 v2.2.45 → v2.2.52 之间 APP 新增/修复的功能。
- 核对方式：逐项读两仓**实际源码**判定，不看数据层有没有接口，只看界面/链路是否真接通。

---

## 〇、落地进度（2026-10-23 更新）

第一批三项已落地（未打 tag）：

- **P0-1 ✓** `mergeLatestPage()` + 视口贴底跟随/翻页锚定；`verify-history.sh` 有合并回归。
- **P1-1 ✓** `MdBlock.Image` / `parseImageMarkdown()` / `PathImage()`；降级口径与本文一致（http 图、未知扩展名、行内图→链接；上下文注入不走 MarkdownText 天然隔离）。
- **P1-2 ✓** `data/ChatLink.kt` `classifyChatLink()` + `openFileByPath(path, line)`；`SourcePane` 按行渲染可定位高亮。
- **P2-1 / P2-2 / P3** 未动，排第二批。

发版时把下方「差距清单」对应项回填进 `PARITY.md` 矩阵（基准改 v2.2.52）并并表。

---

## 一、结论先行

v2.2.44 之后 APP 一共发了 8 个版本，与桌面端相关的变化可分三类：

- **桌面端本来就有 / 已领先**：后台任务面板、交付物卡片、主题（桌面 5 套 vs APP 4 套）、模型/插件设置页、表格无空行解析、回复截断提示、空助手消息归组、发送键三态、会话内切权限。
- **桌面端确实缺（真差距，4 项）**：① 正文 Markdown 图片显示；② 会话内文件链接点开定位（含 `:行号` / `#L行号` 后缀）；③ 应用内检查更新；④ schema 驱动的通用设置命名空间表单（依赖桥 v2.6.0 新端点）。
- **桌面端有同类隐患（1 项，P0）**：APP 在 v2.2.52 修的「刷新历史抹掉『加载更早』成果」，桌面端当前 `loadHistory()` 的写法存在同样的整包替换风险。

---

## 二、逐版本核对（v2.2.45 → v2.2.52 + 未发版）

| APP 版本/提交 | 功能 | 桌面端实况 | 判定 |
| --- | --- | --- | --- |
| v2.2.45 `a344e3f` | 会话页九条交互整改（权限 Pill 挪顶栏、状态文案、刷新/停止收进 ⋯、发送键三态、`ArrowUpward`、权限档位本地化、新建会话选档位） | 发送/停止/插话三态已做（`ui/Composer.kt`）；权限胶囊在**输入区**且可切（`PermissionPill`，设计取舍不同，非缺口）；**新建会话时不能预选权限档位**（只能建完再切，`ui/Sidebar.kt:136` 直接 `createSession(workspace.id)`） | △ 仅"新建即带档位"缺 |
| v2.2.45 `a344e3f` | **会话内文件链接可打开**（`ChatLinkTarget.classify`，剥 autolink 尖括号） | Markdown 链接点击统一走 `openExternal()`（浏览器，`ui/MarkdownView.kt:292`），**不识别工作区文件、不做应用内定位** | ✗ **缺** |
| v2.2.45 `f02c5b9` | 表格贴上一行（无空行）也能渲染 | 表格本就"以分隔行 `|---|` 为唯一判据，表头从上一行取回"（`data/Markdown.kt:134-156`），无空行天然支持 | ✓ 已有 |
| v2.2.46 `e609b04` | 后台任务面板（会话 ⋯ → 后台任务） | 抽屉「任务」：状态/进度/输出/终止都有（PARITY 第 67 行），未真机验 | ✓ 已有 |
| v2.2.49 `580479b` | 模型/插件设置页对标官方 Web | 桌面端有独立「模型」页（`ui/ModelsScreen.kt`，50 供应商）与「插件」页（`ui/PluginsScreen.kt`） | ✓ 已有 |
| v2.2.51 `ec3b857` | 配色主题四套 + 明暗 | 桌面端 **5 套**（暖砂/冷瓷/苔原/深海/暮紫）+ 跟随系统，WCAG AA 校色 | ✓ 领先 |
| v2.2.52 `1bdb274` | 空助手消息归入过程组，不再一整屏「耗时·N 次工具调用」 | 连续工具/组/思考折成 `TurnRun`（`data/ChatHistory.kt:503-529`，tag 后提交 `fe22bae`） | ✓ 已有 |
| v2.2.52 `ebe33a6` | 「加载更早」视口锚定 + 加载态 | 有加载态与前导偏移（`ui/ChatScreen.kt:128-154`），但**锚定不稳**（见 P0-1） | △ 半 |
| v2.2.52 `83b808d` | 刷新历史不再抹掉「加载更早」（屏幕空白） | **存在同类风险**（见 P0-1） | ✗ **隐患** |
| v2.2.52 `5c4e1f0` | **正文 Markdown 图片显示**（`![](/abs/path.png)` 经"绝对路径取字节"通路渲染；http 图降链接） | `data/Markdown.kt` 的 `MdBlock`/`InlineSpan` **根本没有图片节点**（grep `![` 无命中），正文图片被当普通文字，`ui/MarkdownView.kt` 无图片渲染；`ui/RemoteImage.kt` 只服务**附件**（按 id） | ✗ **缺** |
| v2.2.52 `9446c28` | 认不出的图片一律降级链接，不留空白 | 桌面端连识别都没有，原文 `![](…)` 直接显示成文本（不空白，但也不出图） | ✗ 随上一项 |
| v2.2.52 `18e542e` | 文档链接带位置后缀（`:行号` / `#L行号`）能打开 | 无后缀解析、无行号定位 | ✗ **缺**（并入文件链接项） |
| 未发版 `2aa6f37` | **应用内检查更新**（按界面语言 zh*→Gitee / 其他→GitHub，SHA256 校验后拉起安装） | 设置「关于」只有静态版本信息（`ui/SettingsScreen.kt:223-238`），无检查更新 | ✗ **缺**（桌面形态不同，见 P2） |
| 未发版 `691b5d5` | **schema 驱动通用设置命名空间表单**（桥 v2.6.0 `GET/PATCH /settings/namespaces`，装插件免改 App） | grep `namespaces` 在 `data/` **零命中**，未接 | ✗ **缺**（依赖桥 v2.6.0） |

> 复查：回复截断提示桌面端已有（`data/ChatHistory.kt:477` 注入 warning 条"回复达到输出上限，内容被截断"），不列为差距。

---

## 三、差距清单（按优先级）

### P0 · 先修隐患

**P0-1 · 刷新历史会丢掉用户翻出来的"更早消息"**
- 现象：用户点「加载更早」往上翻后，只要来一个 `chat.message.committed` / `chat.session.status(非running)` / `chat.turn.failed`，就会调 `loadHistory(sessionId)`（`AppState.kt:839 / 851 / 863`）。
- 根因：`loadHistory()` 只取**最近一段**（`listMessagesPage(sessionId)` 不带 `beforeSeq`），成功后 `chatItems = page.items` **整包替换**（`AppState.kt:568` 一带），翻页累积的旧消息被抹掉；同时 `LaunchedEffect(total…)` 还会把视口拽到最底（`ui/ChatScreen.kt:131`）。这正是 APP v2.2.52 `83b808d` 修掉的同款。
- 修法（照 APP 口径）：实时刷新只做**增量合并**（按 seq/id 合并，不动已加载的更早页与 `oldestSeq/historyHasMore`），不整包替换；或刷新时沿用当前已加载的最深 `beforeSeq` 窗口。视口锚定：向上拼接时保持首条可见项不动（APP `ebe33a6` 做法）。
- 防回归：`tools/verify-history.sh` 加一条——"先翻页再注入 committed，断言旧条目仍在"。

### P1 · 补真功能（用户能直接感知）

**P1-1 · 正文 Markdown 图片显示**（用户真机点过名的诉求）
- 范围：解析层 `data/Markdown.kt` 增加图片节点（`![alt](target "title")`，允许路径含空格、剥 `<…>` 与 title）；target 是**服务端绝对路径 + 已知图片扩展名**时，走文件内容接口取字节用 Skia 解码（复用 `ui/RemoteImage.kt` 的取图/缓存骨架，但它现在按附件 id 取，需要补一条"按绝对路径取 content"的路）；http(s) 图**降级为普通链接**，不做远程加载（与 APP 一致，省安全/缓存坑）；认不出的图一律降级链接，**绝不留空白**。
- 隔离：上下文注入（Notice `kind=context`）里的 `![](…)` 不摘不渲染（全是路径示例，APP 踩过）。
- 纪律：`Markdown.kt` 是**纯函数禁 import Compose**，解析加在 data 层、渲染加在 `ui/MarkdownView.kt`；新增用例进 `tools/verify-markdown.sh`。
- 同步：`CODEMAP.md` B.4 历史/渲染表、`PARITY.md` 统计。

**P1-2 · 会话内文件链接点开 → 定位文件页（含行号后缀）**
- 范围：链接分类（APP 叫 `ChatLinkTarget.classify`）——先剥 Markdown autolink 尖括号；判为工作区内文件路径时，调已有的 `resolvePath`（`data/ApiExt.kt:122`）跳到文件页并选中，而非 `openExternal` 丢给浏览器。
- 后缀：`:24`、`:24-30`、`#L24`、`#L24-L30` 解析成行号，预览滚动/高亮到该行（文本预览已有，差"带行号打开 + 滚动"）。
- http(s) 外链仍走浏览器。

### P2 · 依赖桥 v2.6.0 / 形态差异，排后

**P2-1 · schema 驱动通用设置命名空间表单**
- 依赖：桥 v2.6.0 的 `GET /settings/namespaces`、`PATCH /settings/namespaces/{ns}`（桌面端 `data/` 完全没接）。
- 价值：装任何插件都能自动出设置表单，不用改客户端。APP 已有 `SettingsNamespaces.kt`（enum/boolean/number/string/只读 JSON 五控件、密钥位只读、`expectedRevision` 412 就地重拉），桌面端可平移成 Compose 表单。
- 降级三层必须照搬：数据层 runCatching 静默、状态层 null 不画入口（老桥零变化）、手动刷新 404/403 静默但 503 报错。
- 建议：等真机确认桥已是 v2.6.0 再做；做时按 `@patch`/能力位规矩，老桥不显示入口。

**P2-2 · 应用内检查更新（桌面形态要重新设计，不能照抄 APK 那套）**
- APP：下 APK → 校验 SHA256 → FileProvider 拉起安装器。桌面端没有 APK 安装器：
  - Linux/deb：检查到新版（同样按语言 zh*→Gitee、其他→GitHub 查 Release）→ 提示 + 给出下载/`dpkg -i` 指引，或下载 deb 后调系统软件安装器（`xdg-open` deb / pkexec apt）。
  - Windows：下载 msi/zip 后引导安装。
  - jlink 轻量包还可考虑只更新"运行时 tar + jar"，但首版不做自更新，只做"检查 + 一键下载 + 明确指引"。
- 优先级低于 P1：桌面用户多为手动装 deb，收益不如 APP 直接，但三端体验一致建议做。

### P3 · 打磨项（可与上面同批顺手）

- **新建会话即选权限档位**：新建对话框工作区选择旁加档位选择，建完立刻 `/permission <preset>`（复用现有机能，不做第二套语义）。
- **权限胶囊位置**：APP 把权限 Pill 挪到顶栏状态行腾输入区空间；桌面端输入区较宽，非必须，真机看完再定，不盲目抄。
- **真机验收欠账**：PARITY 里一批 in-progress（deb 真机安装、插话、抽屉三栏、附件真发图、412 冲突、后台任务三栏）至今"仅编译通过"，按三态纪律应在本轮发版前用真机过一遍。

---

## 四、建议的执行顺序（下一轮）

1. **第一批（稳，先发一个 1.0.2）**
   - P0-1 刷新历史不抹旧消息 + 视口锚定（纯状态机/渲染，可脚本回归）。
   - P1-1 正文 Markdown 图片（data 解析 + 渲染 + 降级链接 + verify-markdown 用例）。
   - P1-2 文件链接应用内定位 + 行号后缀。
   - 这三项都在聊天主路径，用户感知最强，且都能加纯函数用例，符合本仓"无单测靠脚本顶"的现状。
2. **第二批（设置与更新）**
   - 真机确认桥版本后做 P2-1 命名空间表单（带能力位降级）。
   - P2-2 桌面版检查更新（先"检查+下载+指引"，不做热自更新）。
   - P3 新建会话选档位顺手做。
3. **发版前硬门禁（每批都走）**
   - `tools/build.sh` → `tools/docs-check.sh` 全绿 → `verify-markdown.sh` / `verify-files.sh` / `verify-history.sh` 全过 → 真机装 deb 冒烟。
   - 按宪法同步：`version.txt`、`CODEMAP.md`（A.2 文件表 + B.1/B.3/B.4）、`PARITY.md` 状态与末尾统计、新增坑位进 `PITFALLS.md` + AGENTS §7 同名索引、`CHANGELOG-DEV.md`。
4. **发版动作（需用户明确同意才执行）**
   - 当前 8 个未发版提交 + 本批改动一起进 1.0.2；推 Gitee 主源、打 tag、mirror 到 GitHub/AtomGit（AtomGit 现落后 8 提交）。**未经同意不推送、不发版。**

---

## 五、与旧 PARITY.md 的关系

`PARITY.md` 的"40 项全 ✓"结论在 **v2.2.44 基准下成立**，不用推翻；本文是 v2.2.45 之后的**增量差异表**。
落地 P1/P2 后，应把这 4 个真差距回填进 `PARITY.md` 矩阵并更新末尾统计（届时基准改写为 v2.2.52），两份表再并回一份。

---

*本内容由 AI 依据两仓源码与 git 历史核对生成，真机行为请以实机验收为准。*
