# 第三批对齐需求（横向互鉴）：dsh-ui 交互卡片 / @文件补全 / 消息重试 / 应用字号 / 会话统计 / 安全HTML / 分叉选点 / 连接诊断 / 多地址评估

- 日期：2026-10-23
- 提出方：lead（负责对标找差距、写清需求，**不写实现代码**）
- 执行方：workbuddy（负责写代码对齐）
- 状态：**X9 阶段一已实现，X1–X8 待排期**（回执见文末表格；X9 阶段二多地址按 lead 评估暂缓）
- 依据：[`../../../../对齐台账/三端功能互鉴清单.md`](../../../../对齐台账/三端功能互鉴清单.md)（lead 2026-10-23 逐端读码实证的横向互鉴矩阵）
- 本批性质：**不是追平安卓新增**，而是三端「互相抄作业」——下列能力安卓与鸿蒙都已有、桌面没有或更弱，大多有**两份现成参考实现**可对照。
- 对标源版本：dsh-companion **v2.2.52**（main），dsh-companion-harmony **1.0.3**，桥 **v2.6.0**
- 桌面端基线：**1.0.2**（HEAD `441963f`）。与第二批（inbox `2026-10-23-batch2-...md`，命名空间/检查更新/权限档）**不重叠**，可独立排期。

> 分工约定：lead 只对"差距是什么、参照实现在哪、验收口径"负责；类怎么拆、函数怎么命名由 workbuddy 按本仓 `AGENTS.md` 宪法决定。下面 APP/鸿蒙路径是**参照实现**，跨仓绝对路径，不必回头追问。

---

## 共同门禁（每一项都要过，再交）

1. `bash tools/build.sh` 编译通过；
2. `bash tools/docs-check.sh` 全绿（当前 30 项）；
3. `tools/verify-markdown.sh` / `verify-files.sh` / `verify-history.sh` 全过——本批新增**纯函数**（dsh-ui 切分与解析、@提及识别、重试文本选取、字号夹取、统计计数、HTML 提取、版本/配置解析）尽量加进对应 verify 脚本顶回归，本仓无 JUnit；
4. 遵守 **800 行/文件预算**，超了先拆；data 层保持纯函数、**禁 import Compose**；
5. 同提交回填 `CODEMAP.md`（A.2 文件表 + 相关 B 段）、`PARITY-VS-COMPANION-2.2.52.md` / 互鉴清单状态、必要时 `PITFALLS.md` + AGENTS §7 同名索引、`CHANGELOG-DEV.md`；
6. 真机装 deb 冒烟（连一台真 bridge v2.6.0）；
7. **不推送、不打 tag、不发 Release**——任何发版动作等用户明确同意。

---

## X1 · dsh-ui 交互卡片渲染（P0，本批最高优先）

### 差距
助手正文里 ```` ```dsh-ui ```` 围栏包的是 **gen-ui 官方通用卡片**。官方 Web 渲染成真组件，两个移动端都已渲染；**桌面 `data/Markdown.kt:24/124-133` 只有 `MdBlock.Code(language, body)`，不识别 `dsh-ui` 这个语言，直接画成一坨带 JSON 文本的代码块**。
注意：审批/提问钩子卡走的是**独立 projection**（桌面那条不缺）；本项专指**助手正文里 agent 主动产出的 dsh-ui 卡片**。

### 参照实现（两份，协议形态一致）
- 安卓：`/media/wangke/OFFICE/workspace/dsh-companion/app/src/main/java/io/github/westanke/dshcompanion/data/GenUiBlocks.kt`（341 行，切分+解析）与 `.../ui/GenUiCard.kt`（606 行，渲染）
- 鸿蒙：`/media/wangke/OFFICE/TRae_work/dsh-companion-harmony/entry/src/main/ets/core/GenUiBlocks.ts` 与 `.../ui/GenUiCard.ets`

### 实现要点（安卓已踩过的规矩，照抄）
- 正文切片段：安卓 `splitMessageBody(body): List<MessageSegment{Prose|Ui}>`（GenUiBlocks.kt:68），把整段正文切成普通文字与 dsh-ui 围栏交替；围栏识别看 info 串 `isGenUiInfo()`（:43），围栏损坏/半截（流式中）要安全降级回代码块。
- 解析 `parseGenUi(source): GenUiSpec?`（:132），JSON 用本仓已入库的 kotlinx.serialization，**不要新引 JSON 库**；解析失败返回 null → 降级普通代码块，**不白屏**。
- **未知组件整条丢弃、不留半截**（词表 40+ 种，桌面不必全实现）：安卓实现的子集已够覆盖——布局 `card/row/col/grid`、`button`、`radio/submit`(表单)、`list`、`key-value`、`table`(横向滚动套在表外)、`callout`(tone 配色)、`steps`、`stat`、文本。认不得的节点跳过，与网页「坏组件丢弃、其余照常」一致。
- **交互组件要能点、点了会回话**：radio 本地记选择，button/submit 把结果作为一条消息发回当前会话。桌面已有发送通路 `AppState.sendMessage(text, steer=false)`（`AppState.kt:740`），直接复用，**不要造第二套发送**。
- 单段文字不加 card 外皮（否则满屏圆角矩形）；只给并排项和数据对象套卡片。
- 流式：围栏可能只到一半，未闭合时按普通代码块显示，闭合后再升级为卡片（参考安卓/鸿蒙对流式半截的处理）。

### 验收
- 含 dsh-ui 围栏的会话在桌面渲染为可点卡片，点 button/提交 radio 能发出消息；
- 非 dsh-ui 代码块仍按原代码高亮，零影响；
- 围栏损坏、JSON 非法、未知组件三种情况都不白屏、不留半截；
- 纯函数（split/parse）进 verify 脚本，给正常/损坏/未知组件/流式半截用例。

---

## X2 · `@` 文件提及补全（P1）

### 差距
输入框打 `@` 可调取当前工作目录的文件/文件夹补全。安卓 `data/MentionQuery.kt` + `ui/chat/ChatSuggestions.kt`、鸿蒙 `core/MentionQuery.ts`(101 行) 都有；**桌面 `ui/Composer.kt` 只有 `/` 斜杠命令候选，没有 `@`**（全仓 grep `@` 命中的都是 `@Composable` 注解，假阳性）。

### 参照实现
- 安卓：`/media/wangke/OFFICE/workspace/dsh-companion/app/src/main/java/io/github/westanke/dshcompanion/data/MentionQuery.kt`
- 鸿蒙：`/media/wangke/OFFICE/TRae_work/dsh-companion-harmony/entry/src/main/ets/core/MentionQuery.ts` + `ui/ComposerSuggestions.ets`

### 实现要点
- 纯识别逻辑（务必进 data 层、可 verify）：
  - `activeStart(value)`：找当前应触发补全的 `@` 下标——`@` 前必须是**行首或空白**（`foo@bar.com` 邮箱不触发），`@` 在词中间不触发；
  - `query(value)`：`@` 之后到末尾的查询串；无活跃提及返回 null；
  - `applySelection(value, inserted)`：**只替换「@…末尾」那一段**，保留前面已写的半句（"帮我看看 @xx" 不能整行被吃）。
- 候选项来源：复用桌面**已有的文件列举/工作区接口**（FilesScreen 用的那套 + `resolvePath` 在 `data/ApiExt.kt:122`），按查询串过滤；不要新开网络通道。
- 路径拼接降级照搬安卓注释：列举返回的 entryPath 是相对根，老服务端不给 cwdAbsolute 时返回相对根路径，**不要硬拼错误绝对路径**。
- **两面板互斥、斜杠优先**（鸿蒙 `ComposerSuggestions.ets:9/166`、`ChatComposer.ets:262` 立的规矩）：`/` 只在整行行首出现，`@` 在光标落提及词时出现，同一时刻只弹一个；判据冲突时斜杠优先。
- 桌面加成：支持 **`Tab`/`Enter` 接受补全、方向键选择**（键盘场景体验应优于手机），但 `Enter` 接受补全时不要顺手把消息发出去。

### 验收
- `@` 出文件候选、能选中插入并保留前半句；邮箱/词中 `@` 不触发；
- 与 `/` 命令面板从不同时出现；
- 纯函数三态（无@/活跃@/选词替换）+ 邮箱反例进 verify。

---

## X3 · 消息失败重试 / 编辑重发（P1）

### 差距
一轮失败后，安卓可一键重发；桌面现在失败了只能用户手动重敲。桌面 grep 到的 retry 只有 `data/DshApi.kt` 的**网络层**重试，没有消息层重发。

### 参照实现
- 安卓：`/media/wangke/OFFICE/workspace/dsh-companion/app/src/main/java/io/github/westanke/dshcompanion/data/ChatRetry.kt`（核心是纯函数 `retryableUserText(items): String?`）

### 实现要点（判据很巧，照抄）
- 失败回调 `ChatTurnFailure` 里**没有** turn/seq/消息 id（内核只给 message/code/stopped），拿不到精确归属；
- 实际失败行永远紧跟**触发它的那条用户消息**（那一轮用户不会再插话，插话算新一轮），所以重发「历史里**最后一条有正文的用户消息**」即可，不需要额外字段；
- **两个刻意排除**：① 正文为空的用户消息不算（只发图片/附件那条 body 为空，重发会变成空消息）；② 找不到合格文本时**宁可什么都不做，也不发空消息**；
- UI：失败条/消息行上给「重试」，调 `sendMessage(retryableUserText(...))`；"编辑后重发"若工作量大可只做"重发原文"，编辑态列为可选。

### 验收
- 失败后一键重发最后一条有正文用户消息；纯图片轮、无合格文本时按钮禁用/无动作；
- `retryableUserText` 纯函数 + 空正文/多轮/无用户消息反例进 verify。

---

## X4 · 应用内字号缩放（P1，桌面高分屏反而最该有）

### 差距
安卓 `ui/theme/AppFontScale.kt`、鸿蒙 `core/AppPreferences.ts:116-139` 都有 0.8–1.5× 应用内字号；**桌面设置无此项**。

### 参照实现 / 要点
- 安卓做法：包一层 `Density`，只覆盖 `fontScale = base.fontScale * appScale`（**相乘不是覆盖**——`base.fontScale` 是系统无障碍字号，应用倍数在其上再乘一层，尊重系统设置），让 Compose 自己换算 sp→px；
- 取值 **0.8–1.5、步进 0.1**，非法值在偏好层 `normalizeFontScale` 夹取；
- **默认哲学抄鸿蒙**（AppPreferences.ts:137-139）：新增"是否跟随系统字号"，**默认 true**——新装应用不许擅自把用户调大的系统字号缩回去；
- 持久化进桌面已有的 `data/AppStorage.kt` UiPrefs（prefs.json），设置页加一行；全局在 `AppTheme`/Composition 根部套 ScaledDensity。

### 验收
- 0.8–1.5× 实时生效、重启保持；默认跟随系统；非法值夹取不崩；纯夹取函数进 verify。

---

## X5 · 会话执行统计面板（P2）

### 差距
安卓 `data/SessionStats.kt`、鸿蒙 `ui/SessionStatsSheet.ets` 有执行统计；桌面无。

### 参照实现
- 安卓：`/media/wangke/OFFICE/workspace/dsh-companion/app/src/main/java/io/github/westanke/dshcompanion/data/SessionStats.kt`
- 鸿蒙：`/media/wangke/OFFICE/TRae_work/dsh-companion-harmony/entry/src/main/ets/ui/SessionStatsSheet.ets`（注释明说移植自安卓 `ui/chat/ChatStatsSheet`）

### 实现要点
- 数据桌面**已现成**：`data/ChatHistory.kt` 有解包后的原始事件（`seq/type/time/data`，ChatHistory.kt:246）与 `TurnRun`，直接在其上计数；
- 展示五块：**轮次 turns / 助手消息数 assistantMessages / 工具调用数 toolCalls / 事件总数 events / lastSeq**；
- 轮次只数**真用户**消息——内核注入的上下文（runtime-context / time-context 等，SessionStats.kt:57-63 有判据）不算，保证"统计轮次"与屏幕上那几条用户消息对得上；
- **铁律：不做"按字符数估算 token"**（安卓/鸿蒙都刻意不做，注释：误差成倍、用户会拿它当真实占用做决策）。Token 用量/上下文占用两块等内核给 usage 字段再说，现在不画；
- **空态不显示一排 0**（全零看起来像"统计坏了"），给空态文案；
- 挂载位置建议用桌面已有的 `ui/ChatShelves.kt`（会话标题下注脚区），不必新开重型窗口。

### 验收
- 数字与会话可见消息/工具调用对得上；内核注入上下文不计轮次；空会话显空态不显零；计数纯函数进 verify。

---

## X6 · 安全 HTML 预览（P2）

### 差距
会话里出现 `.html` 文件/正文时，安卓用**非 WebView 的文本化结构提取**预览，鸿蒙有 `core/HtmlPreview.ts`；桌面无。
**安全要害**：读的是"会话里出现过的任意文件"，用嵌入式浏览器/WebView 渲染 = 执行其中 `<script>`、加载外链、发网络请求。桌面**不许**引入 WebView/JavaFX WebView 渲染会话 HTML。

### 参照实现
- 安卓：`/media/wangke/OFFICE/workspace/dsh-companion/app/src/main/java/io/github/westanke/dshcompanion/data/HtmlPreview.kt`：`HtmlBlock.Kind{HEADING,PARAGRAPH,LIST_ITEM,QUOTE,CODE,LINK}`、`extractHtmlBlocks(source)`（:37）、`decodeHtmlEntities()`（:194）、`skipToClose()`（:167）。
- 鸿蒙：`/media/wangke/OFFICE/TRae_work/dsh-companion-harmony/entry/src/main/ets/core/HtmlPreview.ts`

### 实现要点
- 只做**结构提取不执行脚本**：标题/段落/列表/引用/代码/链接六类块；`<script>`/`<style>` 内容整块跳过；外链只显示文本链接、不自动加载；HTML 实体解码；
- 接到桌面现有的文件预览通路（FilesScreen PreviewPane / 正文 Markdown 链接打开），按扩展名/内容类型识别 HTML；
- 提取器是纯函数，进 verify，给含 script、畸形标签、未闭合标签、实体混用用例。

### 验收
- HTML 显示为结构化文本而非源码一坨，也不执行任何脚本/外链；恶意/畸形 HTML 不崩；纯函数进 verify。

---

## X7 · 会话分叉支持「选消息点」（P2，几乎纯 UI）

### 差距
桌面数据层 `AppStateChat.kt:25 fun forkSession(sessionId, atSeq: Int? = null)` 与底层 `client().forkSession(sessionId, atSeq)` **已支持指定 seq**，但 UI 只有侧栏整条分叉：`ui/Sidebar.kt:517/551` 调 `state.forkSession(session.id)` 不传 atSeq。
安卓有 `ui/chat/ChatForkDialog.kt` + `data/ChatForkPoint.kt`、鸿蒙有 `core/ChatFork.ts` 可选"从这条消息分叉"。

### 需求 / 验收
- 在消息行（或其悬浮菜单）加「从这条分叉」，把该消息 seq 传给已有的 `forkSession(id, atSeq)`；不新增网络接口；
- 侧栏"整条分叉"保留；选点入口在拿不到 seq（折叠组/系统项）时禁用；
- 参照安卓 `ChatForkPoint.kt` 的 seq 取法（取该消息对应持久事件 seq，不要用折叠后随轮变化的组 id——这点与第一批 mergeLatestPage「按 seq 不按 id」同源）。

---

## X8 · 逐地址连接诊断（P3）

### 差距
连接失败时桌面只给一句 `connectionError = human(error)`（`AppState.kt:374/396`），地址/token/网络/DNS 问题混在一起靠猜。安卓 `debug/DiagProbe.kt` 有浮层探针、`ConnectionCoordinator` 并发探测多地址并携带**逐地址失败原因**；鸿蒙数据层也有逐地址原因。
桌面目前是**单地址**（见 X9），本项先做单地址版的"分项诊断"即可，多地址诊断等 X9 落地再扩。

### 参照实现
- 安卓：`/media/wangke/OFFICE/workspace/dsh-companion/app/src/main/java/io/github/westanke/dshcompanion/debug/DiagProbe.kt`（不遮挡正文的诊断浮层）、`.../data/ConnectionCoordinator.kt`（逐地址超时/原因）

### 需求 / 验收
- 连接失败页给一个「诊断」展开：分项探测并展示——URL 格式、TCP/DNS 可达、TLS、HTTP 状态、是否需要配对码、令牌是否 401/失效，各给独立结论而不是一句混合报错；
- 诊断工具**不得遮挡**主操作（安卓注释：连正文输入框都看不见就没意义了）；
- 不阻塞正常连接流程，只在用户主动展开时跑。

---

## X9 · 多地址 Host/Endpoint + 配置导入 ——【先评估，勿直接动手】

### 这是什么
安卓提交 `4cb6c89「连接层重构：一台电脑多个地址 + 配置导入取代扫码」`：把「一台电脑 Host」与「一个地址 Endpoint」拆开，一台机器挂局域网/虚拟网/公网多地址，`ConnectionCoordinator` 并发探测、延迟优先选路、失败回退；`ConnectionShare` 用 `DSH1:<Base64URL(JSON)>` 配置文本配对（容错：微信换行/零宽字符/带标签/缺填充/两种字母表）；`HostStore` 整份 Host 列表 Keystore AES-GCM 加密落盘。鸿蒙三件套已齐（`core/HostStore.ts`/`ConnectionCoordinator.ts`/`ConnectionShare.ts`）。
桌面现状：单一 `data/AppStorage.kt:23 ConnectionProfile` + `normalizeEndpoint()`（AppState.kt:325 只是单地址规范化）。

### 为什么标"先评估"
桌面多在固定工位、单地址常够用，多地址选路带来的复杂度（连接模型重构、加密存储改造、UI 改造）**未必值**。请 workbuddy 先产出一段**可行性/工作量评估**（投到本文件回执或单独 RFC），回答：
1. 桌面单地址用户占比假设下，多地址收益是否成立；
2. 即便不上多地址，**`DSH1:` 配置文本导入**是否应单独先做（手动填 URL+长 token 最反人类，且与多地址解耦）——lead 倾向至少先做导入；
3. 若做多地址，现有 token.enc/device.key 加密（AppStorage.kt:69-150）如何平滑迁移、是否影响在途第二批权限/设置单。
**结论与排期等用户拍板后再实现，本项不在本轮硬交付。**

> **【2026-10-23 lead 已完成可行性评估】** 见独立报告（跨仓绝对路径）：
> `/media/wangke/OFFICE/workspace/对齐台账/X9-桌面多地址与DSH1导入可行性评估.md`
> 评估结论（供你核对，可提异议）：**拆两阶段**——
> ① **阶段一 `DSH1:` 配置导入建议先做（约 1–2 人日）**：纯函数 `ConnectionShare.decode()` 同为 Kotlin/JVM 可近乎原样移植，桌面只取 `endpoints[0]` 落现有**单条** ConnectionProfile，不碰选路核心；唯一改动点集中在 `client()` 工厂之外的连接 UI/存储。只做导入不做生成（生成端已有 `dsh-companion/tools/emit-config.mjs`）。
> ② **阶段二多地址选路暂缓（约 4–6 人日）**：技术可行且桌面有结构性优势——全部网络调用都经唯一 `AppState.kt:423 client()` 工厂，业务 11+ 调用点零改；但台式固定工位价值低，等"笔记本多网络漂移"真实需求背书。另：AppState 已 972 行超预算，做阶段二时必须把连接逻辑抽到新 `data/Connection*`，不得再堆进 AppState；令牌是主机级，某地址 401 应判整 Host 失效而非换地址傻重试。
> **请你先就阶段一是否排期、阶段二判断是否成立给回执；lead 不把阶段二当本轮硬任务。**

---

## 实现回执（workbuddy 回填）

| 项 | 状态 | 提交 | 日期 | 备注 |
|---|---|---|---|---|
| X1 dsh-ui 卡片 | | | | |
| X2 @文件补全 | | | | |
| X3 消息重试 | | | | |
| X4 应用字号 | | | | |
| X5 会话统计 | | | | |
| X6 安全 HTML | | | | |
| X7 分叉选点 | | | | |
| X8 连接诊断 | | | | |
| X9 多地址/导入 | 阶段一已实现 | 见 CHANGELOG-DEV「第三批 X9 阶段一」 | 2026-10-23 | 只做 `DSH1:` 导入（`data/ConnectionShare.kt` 331 行 + `AppStateConnectionImport.kt` 67 行 + ConnectScreen ③区），只取第一个地址落现有单条 profile；多地址选路（阶段二）按 lead 评估暂缓；**真机未验**——按用户指示通过后再推+打 tag |
