# 架构

单进程 Compose Desktop 应用，界面由 Skia 直接绘制（`ui-desktop` + `skiko-awt`），
不加载任何 HTML、不嵌 WebView。**两栏是常态布局**：侧栏 252dp 固定（品牌、导航、会话列表、身份卡），
内容区自适应（会话正文再收窄到 860dp 居中）。文件页在内容区内再分「列表 + 预览」两栏，
授权根用顶部横向 chip 表示——不再竖一列，否则会和侧栏叠成四栏。

## 分层

```
ui/           只读 AppState 字段、只调 AppState 动作
AppState      唯一状态机：连接态、数据、事件合并、动作入口
data/         DshWorkspaceClient（唯一网络边界）+ 本地存储
```

`AppState` 用 Compose 的 `mutableStateOf` 持有全部界面状态，`scope.launch` 里发起请求，
阻塞 IO 一律 `withContext(Dispatchers.IO)`。UI 层不出现 OkHttp、不拼 URL、不做权限判断。

## 网络

`data/DshApi.kt` 是从 `dsh-remote-bridge` 的 `kotlin-sdk` 复制来的 `DshWorkspaceClient`，
只改了包名并把 `execute` / `executeJson` / `ensureSuccess` / `endpoint` 从 `private` 放宽到
`internal`，好让 `data/ApiExt.kt` 补 SDK 没覆盖的端点（归档列表、`roots/resolve`、
提问应答、上传、分页历史）。所以**网络只有这一个出口**。

基址固定 `/api/v1`，Bearer 设备令牌，事件走 WebSocket `/api/v1/events`。
端点计算用 `base.resolve(relative)`，不要手工拼字符串——`normalizeEndpoint()` 负责补
`http://` 与 `/api/v1`，重复补会被它自己吃掉。

## 实时事件

`subscribe()` 建一条 WebSocket，回调进 `handleEvent()`：

- `chat.message.delta`（`kind: text|reasoning`，**无 `seq`**）→ 直接追加到
  `streamingText` / `streamingReasoning`，界面即时看到逐字输出。
- `chat.message.committed` → 清空流式缓冲并重取历史。**REST 历史是唯一真相**，
  流式缓冲只是过渡显示，不能拿它当最终内容。
- `chat.session.status` → 同步 `running`；非 running 时收口历史。
- `chat.turn.failed` / `chat.approval.requested` / `file.changed` → 分别进提示、审批计数、文件刷新。
- `connection.ready` → 除置连接态外，把 `capabilities` / `bridgeVersion` 收进状态。
  **能力位是特性探测的唯一依据**，不比版本号；缺项时界面必须解释（`lacksCapability()`），
  并且把「还没收到清单」与「服务端不支持」分开说。
- `chat.session.snapshot` → 记下订阅基线 `cursor`。**WS 是订阅之后才收得到，REST 历史是发请求
  那一刻的快照**，两者的时间差里发生的事件谁都管不着；拿 `cursor` 与本地 `maxSeq` 一比
  就知道"我漏了一段"。口径照安卓端 `ChatSeqMonitor`：**只认严格跳号**，
  重复与倒退交给去重（判成断档会让每次重连都全量重取）。

## 历史投影

`data/ChatHistory.kt` 是唯一出口：`parseHistory(events)` 输出
`User` / `Assistant` / `Reasoning` / `Tool` / `ToolGroup` / `Notice` / `Deliverable` 七类。

两条投影规则必须守住：

1. **先解包**。线上每条是 `{type:"event", event:{…}}`，`unwrapEvent()` 统一处理，
   同时兼容裸事件形状（早期插件）。
2. **空文本不等于空消息**。`user/message` 的 `content[]` 里可能只有图片
   （`{type:'image', attachment:{attachmentId}}`）。按"文本为空就跳过"会把整条消息丢掉，
   所以判据是「文本与图片都为空」。图片只留 id，渲染时再按需拉（`ui/RemoteImage.kt`）。
3. **只投影能给人看的**。`step/start`、`step/end`、`job/registered`、`job/settled`、
   `turn/start`、`agent/inbox/spliced`、`session/end-seed` 一律跳过；
   `turn/end` 只在 `error` / `max-tokens` / `aborted` 时出提示（正常是 `completed`）。

`tool/result` 与 `tool/call` 是两条事件，先建 `callId → (body,isError)` 索引再配对。
最后 `mergeToolRuns()` 把**相邻**工具行合成组，中间隔了正文就断开。

## 会话投影与轨迹

`data/SessionProjections.kt` 是**纯函数层**（不 import Compose），从同一份历史里算出三样东西：

- **待办**：`projections.values.todos` → `TodoItem`。投影不存在时返回空表，
  由 UI 决定显示"没有待办"还是"这份历史没有投影"——两者不该混为一谈。
- **权限档位**：`projections.values.permissions` 的当前值（`currentValue → preset → sandbox`
  依次降级，内核形状漂移过）+ `GET chat/permission-presets` 的候选项。候选项为空时**仍返回当前值**：
  显示"我不知道能切到哪"比整块消失诚实。切换走 **`/permission <preset>` 命令**——
  内核把权限做成会话级命令，桥接没有写端点。
- **轨迹**：`turn/start` 开一轮、`turn/end` 收口，其余事件归当前轮。**不认得的事件也保留**
  （`kind` 原文照抄）——轨迹的价值就在于"看见没被渲染的东西"，过滤掉未知事件等于删掉它最有用的部分。
  历史分页导致最早一轮可能没有 `turn/start`，那种"无主事件"归到 `index = 0` 的伪轮次。

## 附件

协议是两条路（`src/host/router.ts` 的 `readPromptParts`）：

- **图片内联**：`content[]` 里的 `{type:'image', mediaType, data(base64)}`；
- **文件换凭证**：先 `POST chat/sessions/{id}/attachments` 拿 `receiptId`，再以 `{type:'file', receiptId}` 引用。

`receiptId` **只在同一次 prompt 里有效**，所以选文件时只把字节读进内存（`PendingAttachment`），
点发送那一刻才上传 —— 否则用户挑完又删掉，服务端会留一条没人引用的记录。
取回图片走 `GET attachments/{id}?sessionId=`，id 形如 `sha256:…`（含冒号，必须 URL 编码）。

## 文件预览

文件页只有一条原则：**能自己画的自己画，画不了的交给系统**。分流的判据在
`data/FileKinds.kt`（纯函数），执行在 `AppStateFiles.previewEntry()`，画在 `ui/FilesScreen.kt`。

| 类型 | 怎么显示 | 读多少 | 能编辑 |
| --- | --- | --- | --- |
| Markdown（`.md` / `.markdown` / `.mdx` …） | 复用聊天里的 `MarkdownText` 画成文档，可切「源码」 | 前 512 KiB（Range + 本地二次截断） | 是（未截断时） |
| 文本（代码 / 配置 / 日志 / CSV …） | 等宽显示 | 同上 | 是（未截断时） |
| 图片（png / jpeg / gif / webp / bmp） | Skia 解码后等比缩放，点一下看原图 | 整份，上限 8 MB | 否 |
| 其余（PDF / Office / 压缩包 / 音视频 …） | 说明原因 + 「用系统程序打开」 | **不读内容** | 否 |

三个判定层次，顺序不能换：

1. **看扩展名**（`classifyFile`）——先给徽标和分流方向，内容还没到就能显示"这是什么"；
2. **看内容**（`refineByContent`）——扩展名说文本、内容含 NUL 字节就降级成二进制；
3. **看编码**（`decodeText`）——BOM → 严格 UTF-8 → GBK → 有损兜底。**顺序也不能换**：
   UTF-16 文本含大量 NUL，先判二进制会把它误杀。

`openEntryExternally()` 负责"交给系统"：下载到 `AppStorage.cacheRoot()/opened/<path 哈希>/`，
再用 `data/SystemOpen.kt` 按平台试命令（Windows `rundll32` → `start`；UOS `xdg-open` → `gio open`；
最后都兜到 `Desktop.open`）。缓存目录按路径哈希分桶，同名不同目录的两个 `报告.pdf` 不会互相覆盖。

范围与代价说明白：客户端**不内嵌 PDF 引擎、不做 Office 转换、不渲染 SVG**（要光栅化）。
这些格式一律走系统默认程序 —— 桌面端的用户本来就有能打开它们的软件。

## 界面

- `ui/Theme.kt`：尺寸令牌（4pt 栅格、3 档圆角、侧栏 252dp、正文 860dp）+ **5 套主题预设**
  （`Palette`：暖砂/冷瓷/苔原/深海/暮紫，各含 21 个语义色位）+ 派生 `ColorScheme`，
  外加 `LocalAppColors` 提供语义色（`textSecondary`、`mutedSurface`、`danger`、`userBubble`）。
  新增颜色先进令牌，不要在组件里写死 `Color(0x…)`。
- `ui/Components.kt`：通用零件（卡片、空态、状态点、加载块、单行省略）。
- `ui/Sidebar.kt`：**一条 252dp 侧栏**（品牌 / 通栏新会话 / 五页导航 / 工作区分组 / 相对时间会话行 /
  身份卡）。以前是 68dp 图标条 + 会话页自带左栏，两层左栏把正文挤成一条缝，这是改成单侧栏的直接原因。
- `ui/ChatScreen.kt`：面包屑头 + 居中正文列（最大 860dp）+ 输入卡 + 右侧**可收起的抽屉**
  （待办 / 轨迹 / 任务 / 服务端）；正文用 `LazyColumn`，自动滚底由 `LaunchedEffect(total)` 驱动。
- `ui/FilesScreen.kt`：授权根**横向 chip** + 文件列表 + 预览 430dp（标题区带类型徽标与编码、
  动作条只放当前文件真能做的事、正文按类型分流，详见上一节；另含编辑器与移动对话框），
  右上单个 `+` 收口三种新建动作。授权根从竖列改成 chip，是因为加侧栏后竖列会变成四栏。
- `ui/ModelsScreen.kt` / `ui/PluginsScreen.kt`：provider 与插件的独立页——原来这些只能开 WebUI 改。
- `ui/App.kt`：`Sidebar` + 内容区 + Toast 宿主 + 大图预览层；`prefs.lastNav` 记住上次的页。

窗口最小 1040×660，默认 1280×820。`Main.kt` 里有尺寸兜底（DPI 异常时 `1280.dp` 可能算成 0）。

## 本地存储

`data/AppStorage.kt`：Linux 用 `$XDG_CONFIG_HOME/dsh-desktop`，Windows 用 `%APPDATA%\dsh-desktop`。
`profile.json` / `prefs.json` 明文（0600），`token.enc` 用 AES-GCM 加密，密钥是同目录 0600 的
`device.key`。**这不是硬件密钥库**：能读主目录的程序同样解得开，作用是挡住"随手一看"。

## 打包

`jpackage` 产 deb（自带精简 JRE），`/opt/dsh-desktop` 落地。jpackage 生成的 `.desktop`
**不会**装进 `/usr/share/applications`，由 `tools/package.sh` 第 5 步解包重打补齐，
并追加 `postinst` / `postrm`（桌面图标拷贝与清理）。Windows 侧走 Gradle `packageMsi`。

## 与安卓端的结构差异

安卓端是「Activity + ViewModel + 大量单文件拆分」，桌面端是「AppState + 按域拆的扩展文件」。
刻意不同的是：桌面端**没有单测框架**（见 `TECH-DEBT.md` DEBT-001），
所以把"能机检的约束"压到 `tools/docs-check.sh` 与 `tools/verify-history.sh` 上。
