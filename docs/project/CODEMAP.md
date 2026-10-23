# 代码地图（CODEMAP）

> **这份文件是给"要改代码的人/agent"看的**，不是给用户看的。
> 两件事必须与代码一致：**目录结构**（哪个文件干什么）与**接口**（哪个端点谁在用）。
>
> **改完代码必须回来改这里。** `tools/docs-check.sh` 会双向校验：
> 表格里写的文件必须真实存在；`src/` 下每个 `.kt` 必须在表里出现。
> 只在表里加、不在代码里做，或者反过来，都会让门禁变红。

---

## A. 目录结构

### A.1 仓库顶层

| 路径 | 作用 |
| --- | --- |
| `src/main/kotlin/io/github/westanke/dshdesktop/` | 全部源码（40 个文件，约 14 630 行） |
| `lib/` | 运行时依赖 50 个 jar（Compose、Skiko、OkHttp、序列化） |
| `tools/` | 构建、打包、门禁、图标、mock 服务端、**图标瘦身 / 运行时资产 / 启动引导器** |
| `docs/project/` | 制度文档（本文件在这） |
| `.github/workflows/` | CI、发布、镜像同步（见 A.4） |
| `version.txt` | 版本号**唯一来源**，`build.sh` / `package.sh` / 门禁都读它 |
| `build-windows.bat` | Windows 入口，只是 tools 下两个 shell 脚本的薄封装 |
| `LICENSE` / `NOTICE` | AGPL-3.0 全文 / 第三方组件与许可声明 |
| `build/` | 编译产物与中间目录（不进版本控制） |
| `dist/` | 给用户的安装包 |
| `AGENTS.md` | 工程宪法（先读它） |

### A.2 源码文件（新增/改名/删除都必须同步这张表）

路径前缀统一省略为 `src/`，完整路径是 `src/main/kotlin/io/github/westanke/dshdesktop/<文件名>`。

| 文件 | 行 | 职责 | 关键符号 | 谁用它 |
| --- | --- | --- | --- | --- |
| `src/Main.kt` | 210 | 入口：落盘目录探测（skiko 原生库目录 + tmpdir）、桌面图标兜底、窗口、全局快捷键、**`-Ddsh.openPath=` 启动参数定位文件** | `main()`、`ensureWritableDirs()`、`usableDir()`、`nativeDirCandidates()`、`tempDirCandidates()`、`ensureDesktopShortcut()`、`desktopDir()` | 无（JVM 入口） |
| `src/Enums.kt` | 29 | 三个口径固定的枚举（连接态 / 导航页 / 抽屉栏目） | `ConnectionState`、`Nav`、`ChatPanel` | 全部 |
| `src/AppState.kt` | 987 | 状态机本体：连接、会话列表、历史分页、事件分发、审批、发送；**产出文件回溯（按 seq 插回产出位置）**；文件页版面（`filesShowList` / `filesSplitRatio`）；**检查更新状态字段**（`updateState` / `updateDownloadJob` 必须留在类内——扩展函数存不了 `mutableStateOf`，逻辑在 `AppStateUpdate.kt`）；**通用设置命名空间状态字段**（`settingsNamespaces` / `settingsNamespacesBusy`，逻辑在 `AppStateSettingsNamespaces.kt`） | `AppState`、`connect()`、`autoConnect()`、`bootstrap()`、`refreshSessions()`、`reloadSessions()`、`createSession()`、`loadHistory()`、`loadOlderHistory()`、`scanDeliverables()`、`mergeScannedDeliverables()`、`sendMessage()`、`decideApproval()`、`handleEvent()`、`adoptDevice()`、`human()` | 全部 `ui/*` |
| `src/AppStateProjections.kt` | 355 | 会话内能力：投影同步、权限档位、Agent Preset、断档检测、附件发送、后台任务、工作区管理 | `applyProjections()`、`clearProjections()`、`noteBaseline()`、`resyncHistory()`、`adoptCapabilities()`、`lacksCapability()`、`loadPermissionCatalog()`、`applyPermissionPreset()`、`loadAgentPresets()`、`chooseAgentPreset()`、`addAttachments()`、`sendMessageWithAttachments()`、`refreshJobs()`、`loadJobOutput()`、`killJob()`、`renameWorkspace()`、`deleteWorkspace()`、`seqGapHint`、`hasTodos` | `AppState`、`ui/ChatScreen`、`ui/ChatSidePanel`、`ui/Composer`、`ui/Sidebar` |
| `src/AppStateSettings.kt` | 205 | 设置域：Provider / 凭据 / 模型发现、插件清单与启停 | `loadProviders()`、`saveProvider()`、`resetProviderModels()`、`deleteProvider()`、`createProvider()`、`discoverModels()`、`loadPlugins()`、`togglePlugin()` | `ui/ModelsScreen`、`ui/PluginsScreen` |
| `src/AppStateChat.kt` | 96 | 聊天域的低频动作（按域拆分，见 `AppState` 构造参数注释） | `forkSession()`、`loadCommands()`、`runCommand()`、`refreshQuestions()`、`answerQuestion()`、`stats` | `ui/Sidebar`、`ui/ChatScreen`、`ui/Composer` |
| `src/AppStateFiles.kt` | 470 | 文件域动作：浏览、**按类型分流预览**、上传、下载、删除、编辑保存（ETag）、移动/重命名、**下载后交系统打开**、**按绝对路径直接打开文件** | `refreshRoots()`、`previewEntry()`、`loadPreview()`、`textPreview()`、`openEntryExternally()`、`openAbsolutePath(preview=)`、`openFileByPath()`、`uploadFile()`、`downloadEntry()`、`deleteEntry()`、`createEntry()`、`refreshTrash()`、`restoreTrash()`、`openAbsolutePath()`、`startEditing()`、`saveDraft()`、`cancelEditing()`、`moveEntry()` | `ui/FilesScreen`、`ui/ChatScreen` |
| `src/AppStateUpdate.kt` | 235 | 检查更新状态机（按域拆分，见 `AppState` 构造参数注释）：六态、渠道分流、下载进度与取消、SHA-256 校验、安装指引、交系统打开 | `UpdateState`（`Idle`/`Checking`/`UpToDate`/`Available`/`Downloading`/`Ready`/`Failed`）、`updatesDir()`、`checkForUpdate()`、`downloadUpdate()`、`cancelUpdateDownload()`、`installHint()`、`openDownloadedPackage()`、`openReleasePage()` | `ui/SettingsScreen` |
| `src/AppStateSettingsNamespaces.kt` | 146 | 通用设置命名空间状态机（按域拆分）：**三层降级**——软加载失败退 null（入口不画）、无 `settings.read` 不发请求、用户刷新时 404/403 静默隐藏而 503 报错；保存走 409 就地重拉 + 成功替换同 ns 项 | `loadSettingsNamespaces()`、`refreshSettingsNamespaces()`、`saveSettingsNamespace()` | `ui/SettingsNamespaces` |
| `src/data/DshApi.kt` | 874 | **唯一网络边界**：上游 kotlin-sdk 的 `DshWorkspaceClient`（含 provider / plugin / job / workspace 管理） | `DshWorkspaceClient`、`execute()`、`executeJson()`、`ensureSuccess()`、`endpoint()`、`WorkspaceApiException` | `AppState*` |
| `src/data/ApiExt.kt` | 361 | SDK 没覆盖的端点：会话列表、历史分页、路径反查、提问、附件、插件清单、**通用设置命名空间** | `listChatSessions()`、`listMessagesPage()`、`resolvePath()`、`answerQuestion()`、`uploadEntry()`、`PromptPart`、`sendMessageParts()`、`uploadSessionAttachment()`、`readAttachment()`、`listPlugins()`、`setPluginLoaded()`、`settingsNamespaces()`、`updateSettingsNamespace()`、`isAbsentOnBridge()`、`ChatSession` | `AppState*` |
| `src/data/UpdateChecker.kt` | 300 | **应用内检查更新**（纯函数 + 网络层，不依赖 Compose/AWT）：双渠道（按界面语言分流）、版本逐段比数字、`SHA256SUMS` 解析、平台/架构/full-lite 探测与资产挑选、更新说明压平、分块下载与 SHA-256 | `UpdateChannel`、`channelForLanguage()`、`ReleaseAsset`、`ReleaseInfo`、`isNewerVersion()`、`parseSha256Sums()`、`summarizeReleaseNotes()`、`UpdatePlatform`、`detectPlatform()`、`detectArch()`、`runningLiteInstall()`、`pickUpdateAsset()`、`UpdateException`、`UpdateChecker`、`sha256OfFile()` | `AppStateUpdate`、`tools/VerifyParity.kt` |
| `src/data/SettingsNamespaces.kt` | 401 | **通用设置命名空间**（纯函数，不碰 Compose）：wire 模型 + schema→六类控件（`Enum/Boolean/Number/Text/Secret/ReadOnly`）映射；**schemastery 引用图摊平**（≤3 层，摊不出回只读，绝不猜类型）；密钥三判据只读；入口过滤（非 `WEBUI_ONLY_NAMESPACES` + `writable` + `hasEditableField`） | `SettingsNamespaceList`、`SettingsNamespace`、`SettingsSecret`、`SettingsOp`、`SettingsNamespaceUpdate`、`SettingsControl`、`SettingsFieldSpec`、`SettingsSchema`、`Schemastery`（私有）、`WEBUI_ONLY_NAMESPACES`、`forNativeApp()`、`hasEditableField()`、`canWriteSettingsNamespaces()`、`clampSettingsNumber()`、`humanizeFieldName()`、`prettySettingsJson()` | `AppStateSettingsNamespaces`、`ui/SettingsNamespaces`、`tools/VerifyParity.kt` |
| `src/data/Attachments.kt` | 54 | 待发送附件的内存表示与 MIME 猜测 | `PendingAttachment`、`MAX_ATTACHMENT_BYTES`、`guessMediaType()` | `AppStateProjections`、`ui/Composer` |
| `src/data/ChatHistory.kt` | 597 | **唯一历史投影出口**：事件 → 可渲染条目（含图片附件引用）；交付物抽取与**按 seq 插回产出位置**；工具组合并 + **按轮折叠成 TurnRun**（官方 Web「已完成，用时 X ⌄」的数据层） | `parseHistory()`、`unwrapEvent()`、`mergeToolRuns()`、`foldTurnRuns()`、`mergeDeliverableCards()`、**`mergeLatestPage()`（实时刷新合并，不抹更早页）**、`contentAttachments()`、`deliverablesIn()`、`deliverableFiles()`、`HistoryPage`、`ChatItem`（8 个子类，含 `TurnRun`）、`DeliverableFile`、`ChatStats`、`chatStats()` | `AppState`、`AppStateChat`、`ui/ChatScreen` |
| `src/data/ChatLink.kt` | 55 | 会话内链接分类（对标 companion `ChatLinkTarget.classify`）：http→浏览器、工作区绝对路径→应用内定位、`:24`/`:24-30`/`#L24`/`#L24-L30` 行号后缀解析 | `classifyChatLink()`、`ChatLinkTarget` | `ui/MarkdownView`、`tools/VerifyMarkdown.kt` |
| `src/data/SessionProjections.kt` | 268 | 投影解析：TODO、权限档位、断档 seq、按轮次轨迹（纯函数，无 Compose）；`TraceStep.label` 是一句话摘要 | `TodoItem`、`PermissionState`、`TraceTurn`、`TraceStep`、`parseTodos()`、`parsePermission()`、`parseMaxSeq()`、`buildTrace()`、`stepLabel()`、`resolveNewSessionPreset()` | `AppStateProjections`、`ui/ChatShelves` |
| `src/data/ToolBlockView.kt` | 269 | 工具调用的**专用视图分发**（纯函数，解析失败一律回落 Generic）；**耗时排版（中文单位、整秒）** | `ToolBlockView`、`toolBlockView()`、`toolActionPhrase()`、`extractReadBody()`、`stripAnsiEscapes()`、`toolDurationMs()`、`formatToolDuration()` | `ui/ToolBlocks`、`ui/ChatScreen`、`tools/VerifyHistory.kt` |
| `src/data/Markdown.kt` | 564 | Markdown **解析层**（纯函数，无 Compose 依赖）：独立图片行出 `MdBlock.Image`（可渲染判定在解析层做完，其余降级链接） | `parseMarkdown()`、`parseInline()`、`parseImageMarkdown()`、`isRenderableImagePath()`、`parseTableRow()`、`normalizeRow()`、`looksLikeTableRow()`、`MdBlock`、`InlineSpan` | `ui/MarkdownView`、`tools/VerifyMarkdown.kt` |
| `src/data/AppStorage.kt` | 197 | 本地配置、**缓存目录**、令牌加密 | `AppStorage`、`root()`、`cacheRoot()`、`ConnectionProfile`、`UiPrefs`、`appJson` | `Main`、`AppState`、`AppStateFiles` |
| `src/data/FileKinds.kt` | 267 | **文件类型判定与文本解码**（纯函数）：扩展名分类、内容降级、BOM/UTF-8/GBK 解码、图片魔数、体积格式化 | `FileKind`、`FileType`、`PreviewExtra`、`classifyFile()`、`refineByContent()`、`decodeText()`、`looksBinary()`、`imageFormatOf()`、`formatFileSize()`、`MAX_TEXT_PREVIEW_BYTES`、`MAX_IMAGE_BYTES` | `AppStateFiles`、`ui/FilesScreen`、`tools/VerifyFileKinds.kt` |
| `src/data/SystemOpen.kt` | 104 | **交给系统默认程序打开**（跨平台：Windows rundll32/start、Linux xdg-open/gio、兜底 Desktop.open） | `openWithSystem()` | `AppStateFiles` |
| `src/ui/App.kt` | 182 | 主壳：侧栏 + 内容区（`nav` 五页切换）、Toast、**大图预览层**、`PageHeader()` | `App()`、`MainShell()`、`ToastHost()`、`ImagePreviewHost()`、`PageHeader()` | `Main` |
| `src/ui/Theme.kt` | 345 | 设计令牌与**主题预设**：`Tokens`、5 套 `Palette`、`resolvePalette()`、`AppColors` | `Tokens`、`Palette`、5 个预设常量、`AllPalettes`、`ThemeChoices`、`resolvePalette()`、`LocalAppColors`、`AppTheme()` | 全部 `ui/*` |
| `src/ui/Components.kt` | 141 | 通用零件：卡片、空态、状态点、加载块、外链、单行省略 | `AppCard()`、`SectionLabel()`、`EmptyState()`、`StatusDot()`、`LoadingBlock()`、`ElidedText()` | 全部 `ui/*` |
| `src/ui/MarkdownView.kt` | 424 | Markdown **渲染层**：表格（横向滚动 + 列宽自适应 + 斑马纹）、标题、列表、引用、代码块、**正文图片（`PathImage`）**、**链接应用内路由** | `MarkdownText()`、`MarkdownTable()`、`openChatLink()`、`CodeBlock()`、`columnWidths()`、`copyToClipboard()` | `ui/ChatScreen` |
| `src/ui/RemoteImage.kt` | 182 | 会话图片附件 + **正文图片（按服务端绝对路径取字节）**：按需拉取 + LRU 缓存 + 大图预览状态 | `RemoteImage()`、`ImageStrip()`、**`PathImage()`**、`ImagePreview`、`clearImageCache()` | `ui/ChatScreen`、`ui/App` |
| `src/ui/ConnectScreen.kt` | 175 | 连接页：地址 + 一次性配对码 | `ConnectScreen()` | `ui/App` |
| `src/ui/ChatScreen.kt` | 781 | 会话页：页头（模型 / 推理等级 / Agent 预设 / 悬挂面板开关）、**标题下悬挂区**、消息流（**按轮折叠 TurnRun、上下文注入默认隐藏（设置可开）**、**用户气泡带时间 + 复制/引用**、**正文可选可复制（SelectionContainer）**、**右侧细滚动条（溢出才显示）**、产出文件卡按 seq 落在产出那一轮）、断档提示、装配 | `ChatScreen()`、`ChatTopBar()`、`ChatPanelToggles()`、`ShelfToggle()`、`ModelPicker()`、`MessageRow()`、`CollapsibleBlock()`、`NoticeBar()`；引用/复制小动作 `quoteText()`、`BubbleAction()` 在 ToolBlocks | `ui/App` |
| `src/ui/ChatShelves.kt` | 287 | 标题下悬挂的两块：**待办**（进度 + 列表）与**轨迹**（线性时间轴，一行一条：时间 · 类型 · 摘要） | `TodoShelf()`、`TraceShelf()`、`ShelfFrame()`、`TodoRow()`、`TraceStepRow()` | `ui/ChatScreen` |
| `src/ui/ChatSidePanel.kt` | 298 | 会话右侧抽屉：后台任务 / 服务端能力面（**待办与轨迹已挪到 `ChatShelves`**） | `ChatSidePanel()`、`PanelHeader()`、`JobsBody()`、`ServerBody()`、`PanelEmpty()` | `ui/ChatScreen` |
| `src/ui/Sidebar.kt` | 718 | 侧栏：品牌、通栏「新会话」（含权限档位预选、工作区改名/移除）、五页导航、工作区头、会话行菜单、身份卡 | `Sidebar()`、`NavItem()`、`SectionHeader()`、`SessionRow()`、`RenameDialog()`、`relativeTime()` | `ui/App` |
| `src/ui/Composer.kt` | 667 | 输入区：Enter 发送 / Shift+Enter 换行、插话、**消息「引用」追加进输入框（消费 `AppState.composerInsert`）**、`/` 命令候选、**附件托盘**、**权限档位下拉**、**上方悬挂的思考过程（滚动显示内容）** | `Composer()`、**`ReasoningShelf()`（思考悬挂条：折叠滚尾部、展开跟到底）**、`PermissionPill()`、`AttachmentTray()`、`ImageChip()`、`pickAttachments()` | `ui/ChatScreen` |
| `src/ui/ToolBlocks.kt` | 594 | 块渲染：**TurnRun（官方「已完成，用时 X ⌄」一行）**、工具组、**单条工具默认折叠**（动作短语 · 关键参数 + 状态/耗时），read / bash（**长输出折叠**）/ edit / 通用；`messageTime()`、`CollapsibleBlock()`（思考/折叠正文，自 ChatScreen 挪入）、`quoteText()`、`BubbleAction()` | `TurnRunBlock()`、`ToolGroupBlock()`、`ToolCallBlock()`、`CollapsibleBlock()`、`ReadBody()`、`TerminalBody()`、`DiffBody()`、`GenericBody()`、`messageTime()` | `ui/ChatScreen` |
| `src/ui/ChatPanels.kt` | 275 | 审批卡片（可代签）、**交付物卡片（一个文件一张，类型色块 + 主副标题，点击直接打开文件）**、统计一行 | `ApprovalPanel()`、**`DeliverableCard()`（一个文件一张卡）**、`fileBadge()`、`ChatStatsBar()` | `ui/ChatScreen` |
| `src/ui/FilesScreen.kt` | 780 | 文件页：授权根 chip / 列表 / **可拖动分栏（目录 ⇄ 内容，比例可左右调）** / **按类型分流的预览**（Markdown 渲染 · 图片 · 源码（**按行 LazyColumn，可定位高亮行号**）· 交系统）+ 编辑器 + 移动 | `FilesScreen()`、`RootChips()`、`FileListPane()`、`Splitter()`、`PreviewPane()`、`TypeBadge()`、`MiniAction()`、`MarkdownPreviewPane()`、`SourcePane()`、`ImagePane()`、`UnsupportedPane()` | `ui/App` |
| `src/ui/ModelsScreen.kt` | 486 | 模型页：供应商卡片、编辑（baseURL/密钥）、发现模型、自定义 provider、全局模型目录 | `ModelsScreen()`、`ProviderCard()`、`ProviderEditor()`、`CreateProviderDialog()` | `ui/App` |
| `src/ui/PluginsScreen.kt` | 246 | 插件页：清单、启停开关、检查更新、`available:false` 的解释 | `PluginsScreen()`、`PluginCard()` | `ui/App` |
| `src/ui/SettingsScreen.kt` | 527 | 设置页：连接信息、设备、主题、**会话（显示上下文注入开关）**、**通用设置命名空间入口（P2-1，可能整块不画）**、**关于（版本号 + 检查更新六态 UI）** | `SettingsScreen()`、`scopeLabel()`、`UpdateCheckSection()` | `ui/App` |
| `src/ui/SettingsNamespaces.kt` | 494 | 通用设置命名空间表单（P2-1）：入口卡片 + 每命名空间可展开表单；六类控件编辑器（enum 胶囊 / boolean 开关 / number 夹取 / text / secret 只读可重填 / 只读折叠 JSON）；脏值跟踪 + 按命名空间保存 | `SettingsNamespacesSection()`、`NamespaceCard()`、`FieldRow()`、`EnumEditor()`、`BooleanEditor()`、`NumberEditor()`、`TextEditor()`、`SecretEditor()`、`ReadOnlyEditor()` | `ui/SettingsScreen` |

### A.3 工具与资源

| 路径 | 作用 | 产出/副作用 |
| --- | --- | --- |
| `tools/build.sh` | 直调 `K2JVMCompiler` 编译（不用 Gradle）；按 `TARGET_PLATFORM` 过滤 skiko 运行时；缺编译器依赖自动从 Maven 下 | `build/classes`、`build/dsh-desktop.jar` |
| `tools/package.sh` | 编译 → 打 jar → jlink 精简运行时 → jpackage → **补桌面入口重打 deb**；Windows 分支出 app-image + zip（有 WiX 时另出 msi）；**`LITE=1` 出轻量包**（拿掉运行时、换成启动引导器） | `dist/dsh-desktop_<ver>-1_<arch>.deb` / `dist/dsh-desktop-lite_<ver>-1_<arch>.deb` / `dist/dsh-desktop-<ver>-windows-x64{,-lite}.zip` |
| `tools/slim-icons.py` | 把 36 MB 的 material-icons-extended 瘦成**源码里 import 到的那 23 个图标**（白名单现抓，漏抓就是编译期 unresolved reference） | `build/app-input/material-icons-extended-*.jar`（0.05 MB） |
| `tools/build-runtime.sh` | 把 jlink 出的运行时镜像打成 **tar.xz + sha256**（轻量包首次启动下载它；与完整包里同一份字节） | `dist/dsh-desktop-runtime-<ver>-<平台>.tar.xz{,.sha256}` |
| `tools/launcher/dsh-desktop.sh` | **启动引导器（Linux）**：自带 runtime 优先 → 用户目录已下载的复用 → 都没有就按国内优先下载（Gitee→GitHub）并校验 sha256，解压后启动；**失败必弹窗 + 写 `~/.cache/dsh-desktop/launcher.log`**（`Terminal=false` 下 stderr 看不见）；`DSH_RUNTIME_HOME` / `DSH_RUNTIME_TARBALL` / `DSH_LAUNCHER_DRY_RUN` / `DSH_LAUNCHER_NO_DIALOG` 兜底与诊断 | 装到 `<安装目录>/bin/dsh-desktop` |
| `tools/launcher/dsh-desktop.bat` | 同上，Windows 版（curl.exe + tar.exe，Win10 1803+ 自带）；日志 `%LOCALAPPDATA%\dsh-desktop\launcher.log`，双击启动失败会 `pause` | 装到 `<解压目录>/dsh-desktop.bat` |
| `tools/fetch-platform.sh` | 拉取目标平台的 skiko 原生库：先找本地种子（`lib/` 里那份、以及历史上放在 tools 下的 Windows 那份），再走 Maven，下完用 `zipfile` 校验不是 HTML | 落进平台库里那份 `skiko-awt-runtime-<平台>-0.8.18.jar` |
| `tools/docs-check.sh` | 文档门禁（32 项） | 退出码 0/1 |
| `tools/test.sh` | 纯逻辑测试套件统一入口：`verify-files` + `verify-markdown` + `verify-parity` 离线跑（约 30 秒）；传 `history.json` 再跑 `verify-history` 与真实数据模式 `verify-markdown` | 退出码 0 全过 / 1 有套件失败 / 2 用法错误 |
| `tools/VerifySupport.kt` | 四个套件共用的断言库：`vcheck`（通过不打印）/ `vexpect`（每条都打印）/ `vsection` / `vsummary` | — |
| `tools/verify-history.sh` | 真实历史解析验证（含专用块分布、耗时可算率） | 退出码 0 / 2（回归）/ 3（输入） |
| `tools/VerifyHistory.kt` | 上面脚本的验证主体 | — |
| `tools/verify-markdown.sh` | Markdown 解析验证：37 条内置用例 + 真实会话表格形状断言（**至少 1 张表、无空表、每行宽度等于列数**） | 退出码 0 / 2（真实数据形状不对）/ 3（输入不可用）/ 4（内置用例失败） |
| `tools/VerifyMarkdown.kt` | 上面脚本的验证主体（直接调 `data/Markdown.kt` 的纯函数） | — |
| `tools/verify-files.sh` | 文件类型判定与解码验证（49 条用例） | 退出码 0/1 |
| `tools/verify-parity.sh` | 第二批对标（P2-1/P2-2/P3）纯函数验证：新建会话档位判定 / 版本比较 / 渠道分流 / SHA256SUMS 解析 / 资产挑选 / 更新说明压平 / 平台与环境探测 / **schemastery 摊平与字段映射 / 入口过滤 / 密钥判据 / 夹取** | 退出码 0/1 |
| `tools/VerifyFileKinds.kt` | 上面脚本的验证主体（直接调 `data/FileKinds.kt` 的纯函数） | — |
| `tools/VerifyParity.kt` | 上面脚本的验证主体（调 `data/` 里各批纯函数） | — |
| `tools/IconGen.java` | 生成 5 档应用图标（Java2D） | `build/icons/hicolor/*` |
| `tools/linux/dsh-desktop.desktop` | 菜单项模板 | 装进 `/usr/share/applications` |
| `tools/linux/postinst-shortcut.sh` | 安装后给用户桌面临一份 | 拼接进 deb 的 postinst |
| `tools/linux/postrm-shortcut.sh` | 卸载时清理桌面副本 | 拼接进 deb 的 postrm |
| `tools/fetch-deps.py` | 按 Maven 坐标补传递依赖（`lib/` 的整理脚本） | 写入 `lib/` |
| `tools/mock-server.py` | 离线服务端，端口 **3091**（别抢 3090） | 本地 mock 数据 |
| `tools/portable/run.sh` | 便携包启动脚本 | 打进 tar.gz 根目录 |
| `build-windows.bat` | Windows 入口：`bash tools/fetch-platform.sh windows-x64` + `bash tools/package.sh` | `dist/dsh-desktop-<ver>-windows-x64.zip`（有 WiX 时另出 msi） |
| `scripts/ci/sync-release-to-gitee.py` | 把 GitHub Release 单向搬成 Gitee 发行版（含附件）；`--local` 模式直接传本机产物并自算 `SHA256SUMS` | 建/补 Gitee 发行版；重跑幂等 |

### A.4 CI 与发布（`.github/workflows/`）

| 文件 | 触发 | 干什么 | 关键约束 |
| --- | --- | --- | --- |
| `ci.yml` | push `main`、PR、手工 | 编译 + `docs-check`（第 8 节实跑 `tools/test.sh`，linux-x64） | **不产安装包**，必须快；红了不许合并 |
| `release.yml` | push tag `v*`、手工 | 三 job 现场构建（amd64 / arm64 / windows-x64），tag 时建 Release + `SHA256SUMS`，再用 `sync-release-to-gitee.py --local dist` 把产物搬回 Gitee | 手工触发只构建不发布；arm64 必须断言包里带 arm64 skiko；Gitee 搬不动只警告不判红（tag 可能还没镜像过去） |
| `mirror-sync.yml` | 每 3 小时、手工 | `mirror` job：Gitee（主）→ AtomGit（备）→ GitHub（从）单向 `push --mirror` refs；`releases` job：把 GitHub 最新 Release 搬到 Gitee 发行版（定时补缺） | refs 与 Release 是两码事——`push --mirror` **不搬 Release**，所以必须有第二个 job；用 secret `MIRROR_PAT`，不能用 `github.token`；判据是退出码 |

> 需要的 secret：`MIRROR_PAT`（repo + workflow scope，推 refs 用）、`GITEE_TOKEN`（Gitee 私人令牌，建发行版与传附件用）。
> 缺 `GITEE_TOKEN` 时两个工作流都只警告跳过，不会红。

> 开发机上还有两个不入库的下载种子目录：Windows 原生库的历史落点、以及按平台拉取的
> 平台库目录。文档里引用路径时用 `git ls-files` 判存在——按"本地在不在"判会外绿内红。

---

## B. 接口说明

基址 `/api/v1`，Bearer 设备令牌。**所有调用都必须经过 `data/`**，UI 里不许出现 URL。

### B.1 REST：桌面端**已接入**的

| 方法 | 端点 | 用途 | 代码位置 |
| --- | --- | --- | --- |
| GET | `healthz` | 连通性与插件版本（免鉴权） | `DshApi.health()` |
| POST | `pairings/exchange` | 配对码换设备令牌（免鉴权） | `DshApi.exchangePairing()` |
| GET | `devices/self` | 设备身份、scope、授权根（补齐 `rootIds`） | `DshApi.currentDevice()` |
| GET | `roots` | 授权根列表 | `DshApi.listRoots()` |
| GET | `roots/{rootId}/entries` | 目录分页列表 | `DshApi.listEntries()` |
| GET | `roots/{rootId}/content` | 读文件（预览） | `DshApi.readFile()` |
| GET | `roots/resolve?path=` | 绝对路径 → rootId + 相对路径 | `ApiExt.resolvePath()` |
| POST | `roots/{rootId}/entries` | 新建目录 / 空文件；上传走同端点 | `DshApi.createEntry()`、`ApiExt.uploadEntry()` |
| DELETE | `roots/{rootId}/entries` | 软删除进回收站 | `DshApi.trashEntry()` |
| GET | `trash` | 回收站列表 | `DshApi.listTrash()` |
| POST | `trash/{trashId}/restore` | 从回收站恢复 | `DshApi.restoreTrash()` |
| GET | `chat/sessions` | 会话列表（`archived=true` 取归档） | `ApiExt.listChatSessions()` |
| POST | `chat/sessions` | 新建会话 | `DshApi.createSessionInWorkspace()` |
| PATCH | `chat/sessions/{id}` | 重命名 | `DshApi.renameSession()` |
| POST | `chat/sessions/{id}/archive` | 归档 | `DshApi.archiveSession()` |
| POST | `chat/sessions/{id}/fork` | 分叉（体 `{atSeq}`，201 返回 `{sessionId}`） | `DshApi.forkSession()` |
| GET | `chat/sessions/{id}/messages` | 历史（支持 `beforeSeq`、`maxMessages`） | `ApiExt.listMessagesPage()` |
| POST | `chat/sessions/{id}/messages` | 发送消息 | `DshApi.sendMessage()` |
| GET | `chat/sessions/{id}/models` | 会话可用模型 | `DshApi.listSessionModels()` |
| POST | `chat/sessions/{id}/model` | 切换模型 | `DshApi.selectSessionModel()` |
| GET | `chat/sessions/{id}/approvals` | 待审批列表 | `DshApi.listPendingApprovals()` |
| POST | `chat/sessions/{id}/approvals/{aid}/decision` | 审批决定 | `DshApi.decideApproval()` |
| GET | `chat/sessions/{id}/questions` | 待回答提问 | `ApiExt.listSessionQuestions()` |
| POST | `chat/sessions/{id}/questions/{cid}/answer` | 提交回答 | `ApiExt.answerQuestion()` |
| GET | `chat/sessions/{id}/commands` | 这个会话的斜杠命令清单 | `DshApi.listSessionCommands()` |
| POST | `chat/sessions/{id}/commands` | 执行一条斜杠命令 | `DshApi.executeSessionCommand()` |
| POST | `chat/runs/{sessionId}/cancel` | 停止运行 | `DshApi.cancel()` |
| GET | `chat/workspaces` | 工作区列表（新建会话时选） | `DshApi.listChatWorkspaces()` |
| PATCH | `chat/workspaces/{id}` | 工作区重命名 | `DshApi.renameChatWorkspace()` |
| DELETE | `chat/workspaces/{id}` | 移除工作区（不删目录） | `DshApi.deleteChatWorkspace()` |
| GET | `chat/agent-presets` | Agent 预设清单 | `DshApi.listAgentPresets()` |
| PUT | `chat/sessions/{id}/agent-preset` | 切换 Agent 预设 | `DshApi.selectSessionAgentPreset()` |
| GET | `chat/permission-presets` | 权限档位**候选**清单（当前值只在投影里） | `DshApi.listPermissionPresets()` |
| POST | `chat/sessions/{id}/messages` | 带 `content[]` 片段发送（text / image / file） | `ApiExt.sendMessageParts()` |
| POST | `chat/sessions/{id}/attachments` | 上传文件换 `receiptId`（24 MB 上限） | `ApiExt.uploadSessionAttachment()` |
| GET | `attachments/{id}?sessionId=` | 取图片字节（id 含冒号，须 URL 编码） | `ApiExt.readAttachment()` |
| GET | `chat/sessions/{id}/jobs` | 后台任务列表 | `DshApi.listSessionJobs()` |
| GET | `chat/sessions/{id}/jobs/{jid}/output` | 任务输出（`from` 增量续读，`lossy` 提示截断） | `DshApi.readJobOutput()` |
| POST | `chat/sessions/{id}/jobs/{jid}/kill` | 终止任务（`requested` / `already-finished`） | `DshApi.killSessionJob()` |
| PUT | `roots/{rootId}/content?path=` | 写文件（`If-Match: <etag>`，缺省 `If-None-Match: *`） | `DshApi.writeFile()`、`AppStateFiles.saveDraft()` |
| PATCH | `roots/{rootId}/entries` | 移动 / 重命名（体 `{path, destinationPath}`） | `DshApi.moveEntry()`、`AppStateFiles.moveEntry()` |
| GET | `settings/providers` | 供应商、凭据状态、`revisionByNamespace` | `DshApi.listProviderSettings()` |
| PATCH | `settings/providers/{id}` | 改配置（带 `expectedRevision`，412 = 别处改过） | `DshApi.updateProvider()` |
| POST | `settings/providers` | 新建自定义供应商 | `DshApi.createCustomProvider()` |
| DELETE | `settings/providers/{id}` | 删除自定义供应商 | `DshApi.deleteProvider()` |
| POST | `settings/providers/{id}/discover` | 拉供应商的可用模型（只是候选） | `DshApi.discoverProviderModels()` |
| GET | `settings/models` | 全局模型目录（分组 + 失败项） | `DshApi.listModelCatalog()` |
| GET | `settings/plugins` | 插件清单（`checkUpdates=1` 才查 npm） | `ApiExt.listPlugins()` |
| PATCH | `settings/plugins` | 启停插件（只改 `dsh.profile.bundles`，返回 `restartRequired`） | `ApiExt.setPluginLoaded()` |
| GET | `settings/namespaces` | 通用设置命名空间列表（需 `settings.read`；`hasDocument=false` 是内核没建文档，给空态不报错） | `ApiExt.settingsNamespaces()` |
| PATCH | `settings/namespaces/{ns}` | 改一个命名空间（需 `settings.write`，≤32 条 op，`expectedRevision` 乐观锁；409 `SETTINGS_CONFLICT` 就地重拉） | `ApiExt.updateSettingsNamespace()` |

### B.2 REST：已知但**仍未接入**

| 方法 | 端点 | 说明 |
| --- | --- | --- |
| GET | `chat/sessions/{id}/messages`（POST 其他形态） | 目前只用「发一条消息」这一形态；批量/编辑形态没有入口 |
| PATCH | `settings/providers/{id}`（`models: null`） | 已接（`resetProviderModels()`），但只从模型页的「恢复默认目录」进入 |

> 上一轮列在这里的 5 项（jobs / providers / models / agent-presets / permission-presets /
> workspaces 管理）**已经全部接入**，迁移到 B.1。

### B.3 WebSocket 事件（`AppState.handleEvent()`，共 10 个分支）

| 事件 | 行为 |
| --- | --- |
| `connection.ready` | 置为已连接 + **解析 `capabilities` / `bridgeVersion`**（能力位探测） |
| `chat.session.snapshot` | 记下订阅基线 `cursor`；本地历史落后时重取（断档补偿） |
| `chat.message.delta` | 按 `kind: text\|reasoning` 追加流式缓冲（**无 `seq`**） |
| `chat.message.committed` | 清缓冲 + 重取历史 + 刷列表（REST 才是真相） |
| `chat.session.status` | 同步 `running`；非 running 时收口历史 |
| `chat.turn.failed` | 转成错误提示 |
| `chat.approval.requested` / `.resolved` | 刷新待审批计数 |
| `file.changed` / `file.trashed` | 刷新文件列表 |

### B.4 历史事件（`data/ChatHistory.kt`）

**必须先解包**：每条是 `{type:"event", event:{…}}`，`unwrapEvent()` 负责。

| 内层 `type` | 渲染成 | 备注 |
| --- | --- | --- |
| `user/message` | `ChatItem.User`；`source.kind != "user"` 时降级为 `Notice` | 上下文注入不是用户说的话；`content[]` 里的 `image` 块带 `attachmentId`，只留 id、渲染时再拉（`contentAttachments()`） |
| `assistant/message` | `ChatItem.Assistant` / `ChatItem.Reasoning` | `content` 是块数组，`text` 与 `reasoning` 分开 |
| `tool/call` | `ChatItem.Tool` → 相邻合并为 `ToolGroup` | 结果靠 `callId` 与 `tool/result` 配对 |
| `tool/result` | 不单独渲染 | 只作为 `Tool` 的 `result`；顺带算 `durationMs`（`result.time - call.time`，负值丢弃） |
| `turn/end` | `Notice` | 只在 `error` / `max-tokens` / `aborted`；正常是 `completed` |
| `deliverables/presented` | `ChatItem.Deliverable`（卡片） | `data.files[] = {path, description}`；点卡片走 `roots/resolve` 定位到文件页 |
| `step/start`、`step/end`、`job/*`、`turn/start`、`agent/inbox/spliced`、`session/end-seed` | **不渲染** | 改了会污染消息流 |

### B.5 外部发布源（应用内检查更新，`data/UpdateChecker.kt`）

**不走桥、不带令牌**，全部收在 data 层，UI 不许出现 URL：

| 渠道 | 地址 | 关键事实 |
| --- | --- | --- |
| Gitee（界面语言 `zh*`） | `https://gitee.com/api/v5/repos/westanke/dsh-desktop/releases/latest` | 不需要令牌；**asset 没有 `size`**（进度条只能不确定态） |
| GitHub（其余语言） | `https://api.github.com/repos/westanke/dsh-desktop/releases/latest` | **必须带 `User-Agent`**，否则 403 |

资产挑选按**文件名后缀**（对齐 `tools/package.sh` 的命名），不按下标：Linux
`_$arch.deb`（`java.home` 含 `/lib/runtime` 判 full，否则 lite）；Windows full 优先
msi 再 zip。校验先读 `<包名>.sha256`、再读 `SHA256SUMS`；**哈希不符当场删文件**并明说，
不留半包在 `updates/` 里。错误码分流：404 = 渠道没 Release、403/429 = 限流，
其余按网络错。安装只给指引 + `Desktop.open`，**不提权**。

---

## C. 常见改动路线

| 我要改… | 先动 | 联动 |
| --- | --- | --- |
| 消息怎么渲染 | `ui/ChatScreen.kt` 的 `MessageRow()` | 新增 `ChatItem` 子类要改 `data/ChatHistory.kt` 的 `parseHistory`、`chatStats`、`ui/ChatScreen` 的 `when` 三处（穷举，编译会提醒） |
| Markdown 怎么解析 | `data/Markdown.kt` 的 `parseMarkdown()` | 改完跑 `tools/verify-markdown.sh`；**解析与渲染必须分居两个文件**，解析层不许 import Compose（否则脚本跑不起来） |
| Markdown 长什么样 | `ui/MarkdownView.kt` 的 `MarkdownText()` / `MarkdownTable()` | 表格列宽在 `columnWidths()`，着色统一走 `Tokens`；单元格内容必须过 `annotatedFrom()` 二次解析行内标记 |
| 工具块长什么样 | `ui/ToolBlocks.kt` | 分发规则在 `data/ToolBlockView.kt`（纯函数，别在 UI 里嗅探字符串） |
| 工具名口径变了 | `data/ToolBlockView.kt` 的 `toolActionPhrase()` 与 `toolBlockView()` | 跑 `tools/verify-history.sh` 看「工具专用块」分布是否掉到 Generic |
| 历史事件解析 | `data/ChatHistory.kt` | 跑 `tools/verify-history.sh`；同步本文 B.4 |
| 新增一个接口 | `data/ApiExt.kt`（优先）或 `DshApi.kt` | 同步本文 B.1；`AppState` 加动作；`docs-check` 会检查文件表 |
| 连接 / 重连逻辑 | `AppState.connect()`、`autoConnect()`、`bootstrap()` | `data/AppStorage` 的 profile 字段 |
| 文件页 | `ui/FilesScreen.kt` + `AppState` 的 `refreshDirectory()` 段 | 预览上限 512 KiB |
| 新页面 | `ui/App.kt` 的 `Nav` 枚举 + `MainShell` 分支 + `ui/Sidebar.kt` 的 `NavItem` | `Ctrl+1/2/3` 在 `Main.kt`；换页会自动记住（`prefs.lastNav`） |
| 会话抽屉（任务 / 能力面） | `ui/ChatSidePanel.kt` + `ChatPanel` 枚举（只剩 `Jobs`/`Server`） | 投影算法在 `data/SessionProjections.kt`，纯函数，改完直接跑编译即可（没有独立测试） |
| 标题下悬挂（待办 / 轨迹） | `ui/ChatShelves.kt` + `AppState.todosShelfOpen` / `traceShelfOpen` | 顶部胶囊只切展开态；轨迹是线性时间轴，摘要来自 `stepLabel()` |
| 权限档位 / 推理等级 / Agent 预设 | `ui/Composer.kt` 的 `PermissionPill()`、`ui/ChatScreen.kt` 的 `ModelPicker()` 与 `more` 菜单 | 权限切换**走 `/permission <preset>` 命令**（内核没有写端点）；目录候选来自 `chat/permission-presets` |
| 附件发送与图片显示 | `data/Attachments.kt` + `AppStateProjections.sendMessageWithAttachments()` + `ui/RemoteImage.kt` | 图片内联 base64、文件先换 `receiptId`；`receiptId` 只对同一次 prompt 有效，**不能存起来复用** |
| Provider / 插件 | `ui/ModelsScreen.kt`、`ui/PluginsScreen.kt` + `AppStateSettings.kt` | 写操作必须带 `expectedRevision`；密钥只写不读 |
| 通用设置命名空间 | `data/SettingsNamespaces.kt`（schema 摊平纯函数）→ `AppStateSettingsNamespaces.kt`（三层降级）→ `ui/SettingsNamespaces.kt`（表单） | 坑位见 PITFALLS「内核 schema 是 schemastery」；改映射先跑 `tools/verify-parity.sh` |
| 检查更新 | `data/UpdateChecker.kt`（纯函数 + 网络）→ `AppStateUpdate.kt`（状态机）→ `ui/SettingsScreen.kt` 的 `UpdateCheckSection()` | 只检查 + 下载 + 校验 + 指引，**不热自更新**；改挑选/解析先跑 `tools/verify-parity.sh` |
| 换主题 / 加主题 | `ui/Theme.kt`：加一个 `Palette` 常量，挂进 `AllPalettes` | 设置页的色卡由 `ThemeChoices` 自动列出来，UI 不用改 |
| 颜色 / 圆角 / 间距 | 颜色只从 `LocalAppColors` 或 `MaterialTheme.colorScheme` 取；尺寸只从 `Tokens` 取 | **组件里不许出现 `Color(0x…)`**（`docs-check` 不查，靠评审；连接页曾漏掉一处 `Color.White`） |
| 侧栏 / 会话行 | `ui/Sidebar.kt` | 相对时间口径在 `relativeTime()`（毫秒/秒兜底判断） |
| 文件页授权根 | `ui/FilesScreen.kt` 的 `RootChips()` | 横向 chip，别再改回竖列（会挤掉文件列表） |
| 安装包 / 图标 / 桌面入口 | `tools/package.sh`、`tools/linux/`、`tools/IconGen.java` | 见 `PITFALLS.md` 三条打包坑 |
| 版本号 | `tools/package.sh` 的 `VERSION` | 按 `AGENTS.md` 第 3 节同步五处 |

---

## D. 维护纪律（门禁会查）

1. **新增 / 删除 / 改名 `.kt`** → 同步 A.2 表格。`docs-check.sh` 会双向比对：表里有而文件不存在、文件存在而表里没有，**都算失败**。
2. **新增一个已在用的端点** → 同步 B.1；只是 SDK 有但没用，放 B.2。
3. **新增 WebSocket 事件分支** → 同步 B.3；**新增历史事件渲染** → 同步 B.4 并补 `PITFALLS.md`。
4. **改了某文件的行数** → A.2 的"行"列允许滞后，但超过 800 行必须去 `TECH-DEBT.md` 登记。
5. 本文只写**事实**（哪个文件、哪个端点、谁在调），不写"建议""可以考虑"。设计理由写 `ARCHITECTURE.md`。
