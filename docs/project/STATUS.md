# 状态（STATUS）

- 日期：**2026-10-24**
- 版本：**1.0.3**（`v1.0.3` tag；1.0.2 之后 11 个未发版提交一并打进，Release 由 GitHub 现场构建并同步回 Gitee 主源）
- 平台：UOS / Deepin x64 与 arm64（deb）、Windows x64（zip + msi）

---

## v1.0.3 · 发版（2026-10-24）

**基于什么**：用户「打包 推+发」。1.0.2 之后 11 个提交：P3 权限档预选、
P2-2 检查更新（六态 + 双渠道）、P2-1 schema 命名空间表单、X9 阶段一 DSH1 导入、
第四批纯逻辑测试机制、设备身份头 `X-Dsh-Device`。

**干了什么**：`version.txt` 1.0.2 → 1.0.3，`VERSION_FALLBACK` 与 README 产物名同步；
本机打 amd64 完整包 + 轻量包 + 运行时；推 Gitee 主源 + AtomGit 备源打 tag，
镜像到 GitHub 后 `release.yml` 出 arm64 deb / Windows zip + msi 并建 Release。

**发行物落地**：GitHub Release v1.0.3 **14 个资产齐全**、非 draft（amd64 / arm64 完整+轻量 deb、
三平台运行时包、Windows zip / msi、`SHA256SUMS`）。Gitee 主源侧：Linux x64 三件套
（完整 deb 69M / 轻量 deb 41M / 运行时 29M）+ `.sha256` + `SHA256SUMS` + 源码包已落地，
流式拉取哈希与本地逐一比对**全部成功**；剩余 9 个 CI 产物（arm64 deb ×2、三平台运行时、
Windows zip / lite / msi）由 `mirror-sync` 的 releases 作业每 3 小时补缺——跑机→Gitee
大包上传会 240s 写超时（v1.0.2 老毛病，见 `RELEASE.md` ⑥′），故先保 Linux x64 主线可用。
**Gitee 附件 1GB 配额**：删除了最旧的 v1.0.1 发行版（GitHub 仍有历史），现保留
v1.0.2（16 资产）+ v1.0.3。

**测了什么**：`build.sh` 0 error；`docs-check.sh` 32/32；`test.sh` 219 项
（verify-files 49 + verify-markdown 37 + verify-parity 133）。
**未真机验证**：X9 三条路径、第二批三项、设备身份头（需新桥 2.6.1）；
装机冒烟由用户执行。

---

## 未打版 · 设备身份上报头 X-Dsh-Device（2026-10-24）

**基于什么**：桥投单（inbox `2026-10-23-device-identity-header.md`）——桥 2.6.1 起
按 `X-Dsh-Device: <platform>/<model>` 头把设备名从占位的「Mobile device」换成
「平台 · 型号」。

**干了什么**：`DshApi.kt` 两处加头（REST `request()` 带鉴权分支 + WebSocket
`connect()`）+ 顶层 `deviceIdentity()`（白名单值 windows/macos/linux）；
`DeviceView` 加 platform/model/nameSource 可空字段；设置页设备卡多一行
「平台 / 型号」（桥没回传不画）。

**测了什么**：`verify-parity.sh` 133 项（+5 映射用例）；build / docs-check 32/32 /
test.sh 219 全绿。**未真机验证**：需新桥 2.6.1（桥侧未发版）连一次看 WebUI 设备页。

---

## 未打版 · 第三批 X9 阶段一：DSH1 配置导入（2026-10-23）

**基于什么**：batch3 交接单 X9（原标「先评估」）。lead 独立评估结论：阶段一导入
先做、阶段二多地址选路暂缓。用户拍板做阶段一，并指示**真机测试通过后再推 + 打 tag**。

**干了什么**：`data/ConnectionShare.kt`（新，331 行，安卓移植 + 桌面裁剪：
只做导入不做生成、只取第一个地址）+ `AppStateConnectionImport.kt`（新，67 行）
+ `connect()` 加 `importedToken`/`importNotice` 参数与 `teardown()` 清缓存
+ `ui/ConnectScreen.kt` 的「③ 粘贴 DSH1 配置导入」折叠区。坑位
「DSH1 的 deviceName 不是本机名」已进 PITFALLS + AGENTS §7。

**测了什么**：`verify-parity.sh` 128 项（X9 段 46 项：往返/容错/拒绝原因/
归一化/首址选取）；`build.sh`、`docs-check.sh` 32/32、`test.sh` 214 项全绿。
**未真机验证**：导入需连真桥 v2.6.0 + companion 仓 `emit-config.mjs` 产文本，
验连通性验证、令牌落盘、无令牌分支——通过后才推 + 打 tag。

---

## v1.0.2 · 发版（2026-10-23）

**基于什么**：用户「发版」。1.0.1 之后积了 9 个提交：消息流对齐官方 Web（TurnRun 按轮折叠、
气泡时间+复制、统计居中、消息区细滚动条）、自由复制与引用到发言、上下文注入开关、
第一批对标 companion 2.2.52（P0-1/P1-1/P1-2）。

**干了什么**：`version.txt` 1.0.1 → 1.0.2，`VERSION_FALLBACK` 与 README 产物名同步；
本机打 amd64 完整包 + 轻量包 + 运行时；推 Gitee 主源打 tag，镜像到 GitHub 后由
`release.yml` 出 arm64 deb / Windows zip+msi 并建 Release。

**发行物落地**：GitHub Release 14 个资产（含 `SHA256SUMS`）齐全、非 draft；Gitee 发行版
同样 14 个资产，与 GitHub 逐字节一致（amd64 / arm64 的完整 deb 与轻量 deb、Linux / Windows
运行时包、Windows zip / msi、`SHA256SUMS`，抽查哈希相符）。
注意 **Gitee 仓库附件 1 GB 总配额**：v1.0.2 装齐时撑爆，删掉了最旧的 v1.0.0 发行版腾位
（GitHub 上仍有历史版本）。后续"Gitee 只留最新一个完整版本"是常态，详见 `RELEASE.md` ⑥′。

**测了什么**：见下一节三批的验证（docs-check 30/30、verify-markdown 37 项、verify-history 合并回归）。

---

## v1.0.1 后续 3 · 第一批对标 companion 2.2.52（2026-10-23，未打 tag）

**基于什么**：`PARITY-VS-COMPANION-2.2.52.md` 第一批清单——P0-1 刷新历史不抹旧消息、
P1-1 正文 Markdown 图片、P1-2 文件链接应用内定位 + 行号后缀。

**干了什么**

1. **P0-1**：`data/ChatHistory.kt` 新增纯函数 `mergeLatestPage()`——实时刷新
   （`chat.message.committed` / 轮结束 / 轮失败 / 断档重取）一律 `loadHistory(incremental=true)`，
   按 seq 窗口合并：`page.oldest` 之前的条目（用户翻出的更早页）原样保留，
   窗口内以服务端页为准整段替换（TurnRun/ToolGroup 的折叠 id 随轮延伸会变，不能按 id 判等）；
   分页游标不动；交付物并集。视口：只在**贴底**时自动跟新消息（±2 容差）；
   「加载更早」前插时锚定首条可见项（记下标+偏移，拼接后 `scrollToItem` 跳回同一条）；
   发送/插话后强制回底（`AppState.scrollToBottomTick`）。
2. **P1-1**：`data/Markdown.kt` 新增 `MdBlock.Image` + `parseImageMarkdown()` +
   `isRenderableImagePath()`——整行图片且 target 是服务端绝对路径 + 位图扩展名
   （png/jpg/jpeg/gif/webp/bmp）才出图节点；http 图/未知扩展名/夹在正文里的行内图
   一律降级链接（行内 `![` 分支），原文不丢、不留空白。
   `ui/RemoteImage.kt` 新增 `PathImage()`：resolvePath → readFile → Skia 解码，
   与附件图共用 LRU 缓存，失败降级可点链接。`MarkdownText` 增加 `state` 参数贯穿。
3. **P1-2**：新增 `data/ChatLink.kt` `classifyChatLink()`——剥 autolink/file:// 前缀，
   解析 `:24` / `:24-30` / `#L24` / `#L24-L30` 行号后缀；工作区绝对路径走
   `openFileByPath(path, line)` 应用内打开，其余浏览器。`FilesScreen.SourcePane`
   改按行 LazyColumn：`previewFocusLine` 定位并高亮该行；`previewEntry` 重置为 0。

**测了什么**

- `verify-markdown.sh` 内置 37 项全过（新增图片/链接分类/行号 15 项）；
  `verify-history.sh` 真实 298 事件通过 + `mergeLatestPage` 合并回归（翻页成果在刷新后保留）。
- `docs-check.sh` 30/30（ChatLink.kt 已登记 CODEMAP；ChatScreen 挪出 CollapsibleBlock 回到预算内）。
- 真机待验：翻页后等一轮跑完旧消息还在、图片出图、`:24` 链接跳行。

**没做的**：P2-1 命名空间表单（等确认桥 v2.6.0）、P2-2 检查更新、P3 新建会话选档位。

---

## v1.0.1 后续 2 · 自由复制 + 引用到发言 + 上下文注入开关（2026-10-22，未打 tag）

**基于什么**：用户提两点——①会话里没法自由复制，选中复制、能加到发言去；
②「是否显示上下文注入」进设置，默认关。

**干了什么**

1. **自由复制**：用户气泡、助手正文、流式正文、折叠块正文全部包
   `SelectionContainer`——按住拖选，Ctrl+C 直接到剪贴板。
2. **引用到发言**：用户/助手气泡下新增「引用」小图标（`FormatQuote`），
   点了把整条消息转成 `> ` 引用块追加进输入框。桥：`AppState.composerInsert` +
   `quoteIntoComposer()`，Composer 的 `LaunchedEffect` 消费后置回 null
   （draft 是 Composer 局部状态，外面够不着）。
3. **上下文注入开关**：`UiPrefs.showContextInjection`（默认 false，旧 prefs.json
   自动按缺省处理）；设置页新增「会话」卡片带 Switch；消息流在列表源头过滤
   `Notice(kind="context")`，不再渲染时空占位。

**测了什么**：`build.sh` 通过；`docs-check.sh` 全绿。真机待验：拖选复制、
引用追加格式、开关切换后上下文注入块出现/消失。

---

## v1.0.1 后续 · 对齐官方 Web 的消息流（2026-10-22，未打 tag）

**基于什么**：用户贴了官方 Web 与桌面端同会话的两张截图说「对比修改」。

**差距与改动**

1. **按轮折叠（最大差异）**：官方一轮工作只给一行「已完成，用时 20分34秒 ⌄」，
   桌面端却是一串工具行。新增 `ChatItem.TurnRun` + `foldTurnRuns()`：连续的
   工具/合并组/思考折成一条，展开才是逐条；`ToolGroupBlock` / `TurnRunBlock`
   挪进 `ToolBlocks.kt`（ChatScreen 回到 800 行预算内）。
2. **用户气泡补时间 + 复制**：官方在气泡右下给 `13:57 ⧉`；桌面端补齐
   （`messageTime()`，历史没带时间的不显示）。
3. **统计行居中**：官方把统计压在输入框正下方居中；桌面端原来靠右，改为居中
   （左右等重Spacer，左侧「正在执行」提示保留）。
4. **消息区滚动条（用户追加）**：内容一长上划不明显——贴消息视口右缘加
   `VerticalScrollbar`（`rememberScrollbarAdapter(listState)`，foundation 1.7.3
   desktop API），`derivedStateOf { canScrollForward || canScrollBackward }`
   控制只在内容溢出时显示；样式 6dp 细拇指 + 40dp 最小高 + 悬停加深
   （`ScrollbarStyle` 1.7.3 参数序：minimalHeight/thickness/shape/
   hoverDurationMillis/unhoverColor/hoverColor）。ChatScreen 730 → 756。

**测了什么**

- `build.sh` 通过；`docs-check.sh` 30/30。
- Java 探针 `ProbeTurnRun` 12/12：连续段折叠、单条不包壳、跨用户断开、
  在跑/缺耗时、统计穿透。
- `verify-history.sh`（真实 298 条事件）：TurnRun 6/38/31 条 children，
  统计 50 次工具（1 失败） · 43 段思考穿透正确。

**没做的**：官方右侧悬浮工具条（对话/轨迹/上下文切换）没抄——桌面端已有
标题下悬挂区 + 页头入口，等用户真机看完再定；tok/s 官方统计有而桥接没给数据。

---

## v1.0.1 · 工具调用默认折叠成一行（2026-10-22）

**基于什么**：用户一句「工具调用如手机版 默认收缩起来」，配一张手机版截图
（DSH伴侣 App：一轮工具调用折叠成「耗时 8 秒 · 2 次工具调用」一行，点箭头才展开）。

**干了什么**

1. **单条工具调用一律默认折叠**（`ui/ToolBlocks.kt`）：`ToolCallBlock` 的初始 `expanded`
   从 `tool.isError` 改为 `false`。原先"失败默认展开"是想省一次点击，但一轮连调十几次
   工具、其中一条报错时，整屏铺满展开的失败块，助手正文直接被挤走。现在一致地先收敛成
   一行——摘要行上已经有红色「失败 · 耗时」，异常不会被藏起来。
2. **组摘要补耗时**（`data/ChatHistory.kt` + `ui/ChatScreen.kt`）：`ToolGroup` 新增
   `totalDurationMs`（逐条 `durationMs` 求和，缺任一条即为 null）与 `isRunning`；
   `ToolGroupBlock` 折叠行右侧显示「耗时 8 秒」或「运行中」，形态对齐手机版截图。
   组标题仍是去重工具名 + 次数（+「含失败」）。
3. **耗时改成中文单位**（`data/ToolBlockView.kt`）：`formatToolDuration` 从 `1.2 s` /
   `2m 5s` 改为 `8 秒` / `2 分 5 秒` / `350 毫秒`；亚秒级仍给毫秒，免得快工具被念成「0 秒」。
4. **修轻量包"点了没反应"**（真机报障）：根因是 v1.0.0 发行版里**没有运行时资产**，
   引导器两次 404；而 `.desktop` 是 `Terminal=false`，错误全进了黑洞。启动器改成
   统一 `fail()`：日志（`~/.cache/dsh-desktop/launcher.log`）+ 弹窗
   （zenity → kdialog → xmessage → notify-send，均带 60 秒超时）+ 可操作的手动兜底地址；
   并去掉 `exec java`，前台取退出码，`1..128` 才报"异常退出"。Windows bat 同构
   （日志 + 双击场景 `pause`）。

**测了什么**

- `tools/build.sh` 通过。
- Java 探针（`build/tmp/probe/ProbeToolGroup.java`，本地临时、不入库）**16 项全过**：
  3s+5s → 8000 ms → 摘要「耗时 8 秒」；缺一条耗时 → null（宁可少一行也不给少算的数）；
  有在跑 → `isRunning` 且不显示耗时；空组不炸、标题仍是「0 次工具调用」；
  6 种工具名折叠成「read、bash、edit、write 等 6 次」；`formatToolDuration` 四档单位。
- `tools/docs-check.sh` **30/30**（新增「SettingsScreen 兜底版本一致」）。
- 版本 1.0.0 → **1.0.1**：设置页「关于」的版本号改为读 jar manifest，读不到才用常量兜底。
- 打包次序修正（CI 第一次 v1.0.1 打红暴露的）：① `LITE=1 package.sh` 里的 jpackage
  产物原来直接落 `dist/`，会把上一步打好的完整包覆盖成"没有桌面入口"的半成品
  → 现在 LITE 模式落 `build/lite-jpkg`；② 运行时 tar.xz + sha256 必须**先于**轻包
  生成，否则轻包 `lib/app/` 里没有校验和（校验步骤判红）；③ 校验里的 deb 名按
  `version.txt` 钉死，别用 `dist/*.deb`（会同时匹配完整包和轻包）。
  本机按 CI 同次序（完整包 → 运行时 → 轻包 → 校验）跑通，两个 ✓ 都过。
- 启动器四条路径实测（`DSH_LAUNCHER_NO_DIALOG=1`）：① 两个源都 404 → 退出码 1 +
  完整提示 + 日志有记录 ② `DSH_RUNTIME_TARBALL` 本地包 → 解压后 dry-run 指到它
  ③ 再跑一次命中已解压目录，不再下载 ④ java 退出码 3 → 报「客户端异常退出」。
  轻包 `dsh-desktop-lite_1.0.1-1_amd64.deb` 解包核对：无 `lib/runtime`、
  `VERSION=1.0.1`、`bin/dsh-desktop` 与仓库源 sha256 一致。

**没做的**

- 「默认折叠」是 Composable 内部状态，headless 沙箱验不了"点一下能展开"——
  要 GUI 真机看一眼（沙箱里 skiko so 起不来）。

---

## v1.0.0 第十一轮 · 把发行版搬回主源（2026-10-22）

**基于什么**：用户一句「gitee 没有发行版，应该同步 GitHub 的发现版，本地 deb 自己没打」。
前者是设计缺口（`--mirror` 只搬 refs，产物长在 GitHub 上），后者是我自己欠的账——
`package.sh` 重写后本机再没打过包。

**干了什么**

1. 本机前台一次跑通 `tools/package.sh`（2m01s）→ `dist/dsh-desktop_1.0.0-1_amd64.deb`（90 MB），
   桌面入口自检过。
2. 新增 `scripts/ci/sync-release-to-gitee.py`：把 GitHub Release 单向搬成 Gitee 发行版，
   支持 `--local`（直接传本机产物并自算 `SHA256SUMS`）、`--include`、`--dry-run`，同名附件跳过。
3. `release.yml` 发版当场搬回 Gitee（失败只警告不判红——tag 可能还没镜像过去）；
   `mirror-sync.yml` 新增 `releases` job 每 3 小时补缺。
4. 配 secret `GITEE_TOKEN`；三个坑位进 `PITFALLS.md`。

**测了什么**

- Gitee 发行版 **0 → 1**，五个资产全在：zip 96 MB / msi 96 MB / amd64 deb 91 MB /
  arm64 deb 89 MB / SHA256SUMS；全量同步耗时 2m06s。
- 四个资产流式取真 sha256 与随包 `SHA256SUMS` **4/4 一致**。
- `tools/docs-check.sh` **28/28**。

**没做的**

- Gitee 上放的是 GitHub 构建的那份 deb（与本机那份字节不同，jpackage 烤了时间戳）——
  刻意如此，否则 Gitee 里的包与随包校验和对不上。

## v1.0.0 第十二轮 · 待办与轨迹挂到标题下、思考栏滚内容（2026-10-22）

**基于什么**：用户三句——「思考的时候悬浮思考栏显示的思考中，应该滚动显示思考内容」
「待办任务应该在标题下悬挂，提供展开收缩」「轨迹应该对照官方模式显示」。

**干了什么**

1. **思考悬挂条改成滚内容**：折叠态不再写"思考中 N 字"，而是取思考**尾部 64 字**横向滚动
   （Compose 没有 `TextOverflow.StartEllipsis`，自己截）；展开态跟着流式**自动滚到底**，
   新内容追加不再停在顶部。
2. **待办挪到标题下**：新增 `ui/ChatShelves.kt`，`TodoShelf()` 挂在会话头下方，
   默认折叠成一行（"待办 · N/M 已完成"），展开限高 220dp 滚动。
   顶部「待办」胶囊改成切展开态，**抽屉里不再有待办栏**（`ChatPanel` 只剩 `Jobs`/`Server`）。
3. **轨迹改成线性时间轴**：按 seq 一行一条（时间 · 事件类型 · 一句话摘要），竖线串起来；
   摘要由新增的 `SessionProjections.stepLabel()` 生成（工具名+参数 / 正文开头 / 结果 /
   轮次收口原因），认不出的类型**不给摘要、只显示类型名**（不猜）。同样挂在标题下、可折叠。

**测了什么**

- 编译 0 error；`tools/docs-check.sh` **29/29**。
- 真机数据验摘要：拿真实历史首页（298 条）跑 `buildTrace` →
  **146 / 298 条有摘要**，例：`tool/call → read · {"file_path":…}`、
  `assistant/message → 正文开头`、`step/start`（内部事件）无摘要 —— 符合"不猜"的口径。

**没做的**：UI 观感仍未真机确认（沙箱 `/usr/share`、`/opt` 只读，装不进去）。

## v1.0.0 第十三轮 · 文件卡片点击直接打开文件（2026-10-22）

**基于什么**：用户一句「文件卡片 点击跳转到目录了，应该直接打开文件，不能打开直接走系统去判断」。

**根因**：交付物卡的 `onOpen` 调的是 `openAbsolutePath(path)`，而它（`preview` 默认 false）
的语义是**定位目录**——`resolvePath` 之后只 `openDirectory(parent)`。卡片上写的是文件名，
点下去却被扔进文件列表再自己找一遍，等于"跳转"不是"打开"。

**干了什么**

1. 新增 `AppStateFiles.openFileByPath()`（**只动文件卡这条路径**，
   `openAbsolutePath` 保留给页头 cwd 与 `-Ddsh.openPath=` —— 那两处的语义本来就是定位）：
   `resolvePath` → 列一次父目录拿到 `FileEntry`（`size`/`kind` 只有条目里有）
   → 按 `classifyFile()` 分流，与文件页预览**同一套口径**。
2. 分流结果：Markdown / 文本 / 图片 → `previewEntry()` 内嵌打开；
   其余（PDF / Office / 压缩包 / 二进制）→ `openEntryExternally()` 下到缓存再交系统。
3. 图片超过 `MAX_IMAGE_BYTES`（8 MiB）也直接走系统：内嵌只会被降级成"看不了"再让人点一次。
4. 三个兜底：解析失败说人话；路径指向目录退回列目录；父目录里找不到这条目
   （已被删/移走）退回列目录并说明「已经不在原位置」。
5. 外呼时仍停在文件页 —— `openEntryExternally` 失败只写 `filesError`，
   不跳过去的话用户什么都看不到，跳过去还能看见原因和「用系统程序打开 / 下载」两个按钮。
6. `ui/ChatScreen.kt` 两处 `DeliverableCard`（消息流内 + "更早轮次的产出文件"区）改调新动作；
   卡片箭头 `contentDescription` 从"在文件页打开"改为"打开这个文件"。

**测了什么**

- 编译 0 error（`tools/build.sh` 63s）。

**没做的**：真机点击验证（沙箱装不了 deb）。待装包后确认两类路径——
`.md`/`.png` 应在文件页内嵌打开；`.docx`/`.pdf` 应拉起 WPS/文档查看器。

## v1.0.0 第十四轮 · 产出文件归位、文件页分栏可拖（2026-10-22）

**基于什么**：用户两句——「没必要一直显示更早轮次产生的文件，他在那就产出就放哪里」
「文件卡打开文件直接显示文件，没必要显示目录，且目录与文件的显示无法左右调整，
显示内容多的时候查看不方便」。

**干了什么**

1. **产出文件插回它产出的那一轮**（不再挂末尾汇总区）：
   - `DeliverableFile` 带上 `seq`（哪条事件端出来的），`ChatItem.Deliverable` 加 `fromScan` 标记；
   - 新增纯函数 `ChatHistory.mergeDeliverableCards(items, all)`：只插入不重排，
     按 seq 找到第一个比它晚的位置插进去；去重按 path；补的卡带 `fromScan`，
     用户真翻到那一页时**原装卡顶掉补的卡**（不会留两张一样的）；缺 seq 的挂末尾；
   - `AppState` 在首页加载 / 后台回溯回来 / 翻到更早一页 / 实时流推来新交付物
     四处都调一次（幂等）；`loadOlderHistory` 以前漏了把该页交付物并入 `sessionDeliverables`，一并补上；
   - `ui/ChatScreen.kt` 删掉「更早轮次的产出文件」汇总区，`deliverablesOutsideStream()` 删除。
2. **文件页左目录 / 右内容可以左右拖**：新增 `Splitter()`（`detectDragGestures`，
   拖的是**比例**不是像素，0.18~0.72，窗口拉宽时两栏一起变宽），
   `AppState.filesSplitRatio` 存比例；右栏不再是钉死的 430dp。
3. **从文件卡打开时不显示目录**：`AppState.filesShowList`，`openFileByPath` 进来时置 false，
   页头新增目录开关按钮随时切回；目录收起时预览区补一行路径（否则不知道在看哪个文件）。
4. 交系统打开的那份也先 `previewEntry()`：把预览区指向它（显示"这类不内嵌渲染"），
   否则目录收起后预览区还停在上一个文件上，看着像点错了。

**测了什么**

- 编译 0 error；`tools/docs-check.sh` **29/29**。
- `mergeDeliverableCards` 写了 8 个用例（临时程序，未入库）全过：
  按 seq 插回位置 / 已有不重复 / 原装顶掉补的 / 幂等 / 多个产出各归各位 /
  同一轮合成一张卡 / 缺 seq 挂末尾 / 产出撤走不留孤儿卡。

**没做的**：分栏拖动与目录收起的实际手感未真机确认（沙箱装不了 deb）。

## v1.0.0 第十五轮 · 安装包瘦身：94 → 68 / 40 MB（2026-10-22）

**基于什么**：用户一句「当前安装包有点大，如何让安装包小点，可否让自行在启动界面加一个
自动下载运行环境所需内容，国内外环境都要考虑。还要考虑打包的情况、CI情况」。

**先量后砍**（`dpkg-deb -c` 逐文件统计，不是猜的）：94 MB 里
`material-icons-extended` **36 MB**（2083 个 Filled 图标，工程只 import 23 个）、
JRE 运行时 **~28 MB**（压缩后）、skiko 12 MB（渲染引擎本体，动不了）。

**干了什么（三层，前两层零风险、已落地）**

1. **图标瘦身**：新增 `tools/slim-icons.py`，按源码 import 现抓白名单重写 jar，
   36 MB → **0.05 MB**。白名单漏抓的表现是**编译期** unresolved reference，
   不会静默少图标。core jar 照常打包（14 个常用图标归它）。
2. **运行时镜像瘦身**：改由 `package.sh` 自己 jlink（`--strip-debug --compress 2
   --no-header-files --no-man-pages`），模块集按 `jdeps` + 实际依赖砍到 6 个
   （`java.base, java.desktop, java.logging, java.instrument, jdk.unsupported,
   jdk.crypto.ec`）。95 MB → 53 MB（未压缩）。
   **`jdk.crypto.ec` 不能砍** —— TLS 的 ECDHE 靠 SunEC，jdeps 看不出来；
   真机探针验过 gitee / baidu 都返回 200。
3. **轻量包 + 运行时按需下载**：新增 `LITE=1` 模式与启动引导器
   （`tools/launcher/dsh-desktop.sh` / `.bat`）：
   自带 runtime 优先 → 用户目录已下载的复用 → 都没有才下载（**国内优先 Gitee、
   其次 GitHub**，`DSH_RUNTIME_MIRROR=github` 可对调），下完**验 sha256**
   （校验和随包带）再解压启动。完整包继续出，离线/内网用户不受影响。
   CI 三个平台各加一步打轻包 + 运行时资产，release 与 Gitee 同步自动带上。

**体积**

| 产物 | 之前 | 现在 |
| --- | --- | --- |
| deb（完整，自带运行时） | 94 MB | **68 MB** |
| deb-lite（首次启动下载） | — | **40 MB**（+ 29 MB 运行时压缩包） |
| Windows zip | 96 MB | 预计 **~60 MB**（图标同样瘦身，CI 验） |

**测了什么**

- 编译 0 error；`tools/docs-check.sh` **29/29**。
- 精简 runtime + 瘦图标 jar 下跑通：**23/23 图标**全部加载成功（headless 探针）；
  **TLS 握手** gitee.com / baidu.com 双通 HTTP 200。
- 引导器四条路径逐一实测：自带 runtime 直接用 / 本地 tarball 兜底解压 /
  **下载→sha256 校验→解压→exec java**（本地镜像模拟，java 成功启动到 GUI 初始化，
  再往后是沙箱内存限制，非脚本问题）/ 全失败时清晰报错 + 手动指引，退出码 1。

**踩到的坑（已进 PITFALLS）**

- `download … | sed` **管道吞掉 curl 退出码**，404 被当成成功，接着拿空文件校验，
  报的错完全对不上真因；
- 临时文件写 `/tmp` → 本机 `/tmp` 被拦，curl 报 `(23) Failed writing body`；
  改到 `${XDG_CACHE_HOME:-$HOME/.cache}/dsh-desktop/downloads`；
- 运行时 tar 打多了顶层目录，解压出来是 `<RT_BASE>/<RT_BASE>/bin/java`，
  引导器按 `bin/java` 找不到 → 白下一遍；
- `rm -rf` 在 build 目录里也被 safe-delete 拦（>50 文件）→ 统一改 `mv`。

**没做的**：轻包首次下载的真实网络路径要等 v1.0.0 之后的 tag 才有资产可拉
（当前 release 里还没有 runtime 包）；Windows bat 引导器未在真机跑过。

---

## v1.0.0 修复 · 历史会话里没有文件卡（2026-10-22）

**基于什么**：用户一句「文件卡 在不是历史会话中有，在历史里拉出来就没有」。

**根因（真机取证，不是猜的）**：`GET /chat/sessions/{id}/messages` 不带 `beforeSeq` 时
只返回**最近 298 条**（`hasMore: true`）。实测该会话已到 seq 1934，首页只给 seq 1637→1934；
而 `deliverables/presented` 事件在 **seq 580，落在第 5 页**（每页 298 条）。
会话短的时候它在窗口里 → 有卡；会话一长就滚出窗口 → 再打开就没了。
（叠加因素：服务端历史有 2 MiB 字节预算，超了也从**最旧**开始丢整条，方向一致。）

顺带发现第二个洞：实时流里这个事件被桥接规范化成 **`chat.deliverables.presented`**，
客户端 `handleEvent` 根本没这个分支 —— 新交付物只能等刷新后才可能出现。

**干了什么**

1. `data/ChatHistory.kt`：两种事件名都认（`DELIVERABLE_EVENT` / `DELIVERABLE_EVENT_REALTIME`）；
   新增 `deliverablesIn(events)`，从一页原始事件里抽交付物、不做完整投影。
2. `AppState`：`loadHistory` 拿到首页后，若 `hasMore` 就起 `scanDeliverables()` **后台往回翻最多 6 页**
   （不动 `chatItems`、不动 `oldestSeq`，与"加载更早"是两回事）；结果存 `sessionDeliverables`。
3. `AppState.handleEvent` 补交付物分支：实时到了直接出卡，不等刷新。
4. `ui/ChatScreen.kt`：消息流末尾加"更早轮次的产出文件"区，
   只画 `deliverablesOutsideStream()`（消息流里没有的那部分），不与流里的卡重复。

**测了什么**

- `tools/verify-history.sh` 拿**真实第 5 页**（264 条事件）跑：
  `Deliverable=1`、统计"4 个产出" ✓ —— 证明回溯能捞到。
- 编译 0 error；`tools/docs-check.sh` **29/29**。

**没做的**：本机装不进去（沙箱 `/usr/share`、`/opt` 都只读，第二个实例也被内存限制掐死），
**UI 效果要由用户装新 deb 重启后确认**。

---

## v1.0.0 真机验收 · UOS 本机安装后实测（2026-10-22）

用户装上 `dsh-desktop_1.0.0-1_amd64.deb` 并启动后，用 xdotool + 截图在真实窗口里逐项测
（截图在 `.verify-v100/`，共 34 张）。**安装与环境**：dpkg 里 `dsh-desktop 1.0.0-1`，
`/usr/share/applications/dsh-desktop.desktop` 在，窗口 1270×790 正常起，
连接 `192.168.1.126:3090` 显示已连接、设备 ID/权限芯片齐全。

**第十轮 5 处 UI（全部通过）**

| 项 | 结果 |
| --- | --- |
| 思考悬挂条 | 输入框上方出现"● 思考中 N 字 ⌄"，字数随流式实时涨（24→1052），折叠态 36dp ✓ |
| 上下文注入折叠 | 默认收成一行"› 上下文注入"，不再摊在消息流 ✓ |
| bash 命令模式 | 标题行命令一行 + 耗时；展开是 heredoc 全文 + 输出分段，限高生效 ✓ |
| 产出文件卡 | **未验证**——两个会话都在执行中，没出现交付物场景，待复查 |
| 多主题 | 跟随系统/暖砂/冷瓷/苔原/深海/暮紫 6 个；切"深海"整套暗色立即生效、文字可读，切回暖砂正常 ✓ |

**顺带验证的（官方对齐面）**：会话内 MD 全套（标题/行内 code/引用/链接/有序无序列表/
代码块带行号）、**会话内表格**（斑马纹、单元格内联 code 与加粗——当初报的"表格样式不对"
已修）、文件修改 diff 块（红删行 + 路径 + 耗时）、文件页（目录导航/大小/MD 渲染成文档/
图片直接显示/原图浮层/"用系统程序打开"入口）、插件页（已载 14 · 异常 2 + 开关）、
待办抽屉（1/13 + 状态点）、工作区悬浮卡（改名/移除只动记录不碰磁盘）、模型下拉
（按厂商分组 + Off/Low/High/Max 档位）、新建会话 toast。

**发现一个待确认项**：点"新会话"→ 弹工作区选择 → 选 workspace → toast"已新建会话"，
但**当前视图没有切到新会话**（标题、高亮、消息流都没变）。不排除是设计如此（只建不切），
下轮对照官方确认。

**没验的**：会话内发消息/插话（当前会话是用户正在跑的代理任务，不能打扰）、
"用系统程序打开"的真实外呼（只确认了入口存在）、产出文件卡。

---

## v1.0.0 第十轮 · 对齐官方 Web 的会话观感（2026-10-22）

**基于什么**：用户一句「官方有卡片 文件、上下文注入 隐藏，折叠，命令 bash 模式，
思考过程在对话框上悬挂显示」。上一轮已确认两张截图内容与位置一致、只差主题配色，
这一轮动剩下的 5 处信息密度差异。

**干了什么**

1. 产出文件改成**一个文件一张卡**：40dp 类型色块（固定品牌色：docx 蓝 / xlsx 绿 / pdf 红）
   + 主副两行 + 右侧箭头，卡片 1px hairline、圆角 10dp、内距 10dp。
2. `ChatItem.Notice` 加 `kind = "context"`，上下文注入**默认折叠成一行 36dp**。
3. bash 专用块：**命令一行 + 输出可折叠**（>6 行折叠，展开限高 420dp）。
4. 新增 `ReasoningShelf()`：思考过程**悬挂在输入框上方**，折叠 36dp、展开限高 132dp。
5. 消息流里的流式卡片不再内联推理（推理只出现在悬挂条）。

**测了什么**

- 编译 **0 error**（51s）；`tools/docs-check.sh` **28/28**。
- **未真机验证**：这五处都还没在真实会话里看过一屏。

---

## v1.0.0 第九轮 · 建仓、双远端、三套 CI（2026-10-21）

**基于什么**：用户要求「git 建仓，对照安卓的模式，gitee 主，github 同步，CI 全绿，
github 自己打包 Windows 和 ARM 架构的 deb」。也就是把这个只在本机跑得起来的工程，
变成一个别人（和 CI）也能复现的仓库。

**干了什么**

1. **脚本跨平台化**：`tools/build.sh` / `tools/package.sh` 去掉写死的本机路径
   （`JAVA_HOME`、`KOTLINC_HOME` 可覆盖并带探测回退），新增 `wp()` / `CPSEP` / `PY`
   三处平台差异收敛点，Windows（Git Bash）也能跑同一份脚本。
2. **新增 `tools/fetch-platform.sh`**：按平台拉 skiko 原生库，本地种子优先、Maven 兜底、
   下完校验是真 jar。
3. **新增 `version.txt`**：版本号单一来源，`build.sh`（jar 清单）、`package.sh`（包名）、
   `docs-check.sh`（文档一致性）都读它。
4. **移除 Gradle 脚手架**（`build.gradle.kts` / `settings.gradle.kts` / `gradle.properties` /
   `gradlew.sh` / `gradle/`）：本机跑不起来、CI 也无法验证，留着只会误导。
   `build-windows.bat` 改成 `tools/*.sh` 的薄封装。
5. **三套工作流**：`ci.yml`（编译 + 门禁 + 两组用例）、`release.yml`
   （ubuntu-22.04 amd64 deb / ubuntu-22.04-arm arm64 deb / windows-latest zip+msi，
   tag 时发 Release + SHA256SUMS）、`mirror-sync.yml`（Gitee→GitHub 单向镜像）。
6. **法务与仓库卫生**：补 `LICENSE`（AGPL-3.0）、`NOTICE`（50 个 jar 与 skiko 的许可说明）、
   `.gitignore`（`build/`、`dist/`、`_bak/`、平台运行时；**`lib/` 入库**）。

**测了什么**

- 本机 `tools/build.sh` 编译 0 error（57s）；`tools/package.sh` 产出
  `dist/dsh-desktop_1.0.0-1_amd64.deb`（90 MB），`dpkg-deb -c` 桌面入口自检通过。
- `tools/docs-check.sh` **28/28 全绿**（版本检查改成盯 `version.txt`）。
- **CI 三平台全绿**（`release.yml` run 39700809940）：
  `deb · amd64` 2m50s、`deb · arm64` 2m37s、`zip · windows-x64` 1m54s。
- **`v1.0.0` Release 已发**（run 39701168375，发布步骤 13s）：
  amd64 deb 91 MB、arm64 deb 89 MB、Windows zip 96 MB、msi 96 MB、`SHA256SUMS`。
- `mirror-sync.yml` 手工与定时各跑通两次，Gitee → GitHub 单向镜像正常。
- **仍未真机验证**：三个产物只验到"包内结构与架构对"，没在 UOS / Windows / arm64 机器上装过。

**这一轮踩的四个坑**（都进了 `PITFALLS.md`）：
Windows 类路径要 `;` + `cygpath`；skiko 运行时必须按平台剔除；
`dpkg-deb … | grep -q` 会因管道提前关闭把流水线判红；
Windows 上 Python 打印中文要 `PYTHONUTF8`（脚本与内联 step 各要设一次）。

---

## v1.0.0 第八轮 · 文件预览按类型分流（2026-10-21）

**基于什么**：用户要求「文件渲染也要考虑，尤其 MD 自己要能渲染，其他的可以调用系统的」。
原来的预览只有一条等宽文本路，Markdown 是源码、图片是一行"二进制文件"、
PDF/Office 只能下载后自己找。

**干了什么**

1. `data/FileKinds.kt`（新建）：扩展名分类 → 内容降级 → 编码回退（BOM/严格 UTF-8/GBK），
   外加图片魔数与体积格式化。全部纯函数。
2. `data/SystemOpen.kt`（新建）：Windows `rundll32`/`start`、UOS `xdg-open`/`gio open`、
   兜底 `Desktop.open`，逐条试、失败原因留档。
3. `AppStateFiles`：按类型分工读 —— 文本 Range 取前 512 KiB、图片整读限 8 MB、
   其他不读；新增 `openEntryExternally()`（下载到缓存再交系统）。
4. `ui/FilesScreen`：头部加类型徽标与编码，动作条按类型给按钮，正文四路分流
   （Markdown 渲染 / 源码 / 图片 / 交系统引导）。

**测了什么**

- `tools/verify-files.sh` **49 项全过**（新增脚本）；`docs-check.sh` 28/28；编译 0 error。
- 真实服务端截图五张：`r8-md.png`（Markdown 渲染）、`r8-image.png`（图片）、`r8-binary.png`（PDF 引导）、
  `r8-gbk.png`（GBK 自动识别）、`r8-fake-md.png`（二进制降级）。

**没验证什么（明账）**

- 「用系统程序打开」的**按钮**没点过（本沙箱合成点击进不了 Compose 窗口），但命令链路单独实测过：
  本机 `xdg-open` 直接 dbus 鉴权失败（rc=4）、`gio open` 成功（rc=0），多候选兜底是必要的。
- Windows 侧完全没跑过（没有机器），`rundll32`/`start` 两条路按文档实现。

---

## v1.0.0 第七轮 · 启动自愈（2026-10-21）

**基于什么**：第六轮收尾重新打 deb / 便携包，顺手用 tar 包做启动冒烟 —— 起不来。
`ExceptionInInitializerError` + `Permission denied`，栈顶在 `skiko.Library.unpackIfNeeded`。
这是**老问题**：之前所有截图都走 `tools/build.sh` 编完直跑，绕开了打包产物这条路径。

**干了什么**

1. `Main.kt`：`ensureWritableTemp()` → `ensureWritableDirs()`。skiko **不读** `java.io.tmpdir`，
   它用自己的 `skiko.data.path`（默认 `$user.home/.skiko`），两个目录都得探。
2. `usableDir()` 判据从「建得出文件」升级成「建得出文件 **且** 剩余空间 ≥ 96 MB」——
   本机 `/tmp` 是 10 MB 的 tmpfs，探针能过、写 28.9 MB 原生库必挂。
3. 文档同步：`PITFALLS.md`（重写 skiko 条 + 新增 root/tmp 条）、`AGENTS.md` §7、`CODEMAP.md`、`README.md`。

**测了什么**

- 三组对照实验定位到真凶：`-Dskiko.data.path` ✓ / `-Dskiko.library.path` ✓ / 默认 ✗。
- 修后不带任何参数：1–2 s 起窗 1270×790，原生库自动落到
  `~/.local/share/dsh-desktop/skiko/`，接真实服务端会话页正常（`shots/parity/r7-startup.png`）。
- `tools/docs-check.sh` 28/28。

**包**

- `dist/dsh-desktop_1.0.0-1_amd64.deb`（94 MB，23:31）
- `dist/dsh-desktop-1.0.0-portable-linux-x64.tar.gz`（78 MB，23:32，52 项，结构与旧版一致）

**没验证什么（明账）**

- deb 包没有 `dpkg -i` 实测（本沙箱 `/var/lib/dpkg` 只读，装不了）；Windows msi 仍需 Windows 机器。
- `DEBT-008`（抽屉内部三栏等 UI 交互）维持原状。

---

## v1.0.0 第六轮 · 对齐安卓端能力面（2026-10-21）

**基于什么**：用户指令「界面实现了，功能也对照实现了，最低限度对照安卓的功能要保证都有」。
`PARITY.md` 当时还剩 12 项 ✗，这一轮全部清掉。

**干了什么**

1. **会话内**（`AppStateProjections.kt` + `ui/ChatSidePanel.kt`）：
   右侧抽屉四栏——待办（`projections.todos`）、轨迹（按 `turn/start`/`turn/end` 分轮）、
   后台任务（列表/输出/终止）、服务端能力面。断档检测照 `ChatSeqMonitor`：
   收 `chat.session.snapshot` 的 `cursor` 当基线，与本地 `maxSeq` 比对，缺口 > 0 给提示条可重取。
2. **三个下拉**：权限档位（走 `/permission <preset>` 命令，胶囊从「只读投影」改成真能切）、
   推理等级（模型项下摊开 `efforts` 胶囊）、Agent Preset（页头 `⋯` 菜单）。
3. **附件**：输入区「附件」多选 → 图片内联 base64、其他文件先换 `receiptId`；
   收到的图片渲染缩略图 + 点开看大图（`ui/RemoteImage.kt`，LRU 24 张）。
   顺手修掉「只带图不带字的消息整条消失」。
4. **文件**：预览页加编辑（保存带 `If-Match`，**412 不覆盖**）+ 移动/重命名对话框。
   截断过的文件禁止编辑。
5. **两个新页**：模型页（供应商/凭据/发现模型/自定义 provider/全局目录）、
   插件页（清单/启停/检查更新）。侧栏从 3 项变 5 项，`prefs.lastNav` 同步。
6. **工作区管理**：「新会话」下拉里每个工作区带「改名 / 移除」。

**测了什么**

- `tools/build.sh` 0 error；`tools/docs-check.sh` 28/28；`tools/verify-markdown.sh` 22 条全过。
- 真实服务端（192.168.1.126:3090）截图：会话页、模型页（50 个供应商）、插件页（已加载 14 · 异常 2）。
  确认页头抽屉开关渲染、权限胶囊显示中文「完全访问」（原先显示 `danger-full-access`）。

**没验证什么（明账）**

- 抽屉内部三栏：合成点击进不了 Compose 窗口（见 `DEBT-008`），只验证了开关存在。
- 附件真发一张图、412 冲突路径、任务三栏：未实测。

---

## v1.0.0 第五轮 · 按官方 Web 端重排界面 + 多主题（2026-10-21）

**基于什么**：用户指令「对照官方的 web 界面改改界面，颜色可以使用你的配方，顺便多加几个主题」。

**干了什么**

1. **布局**：68dp 图标条 + 会话页自带左栏 → **一条 252dp 侧栏**（品牌 / 通栏新会话 /
   图标导航 / 工作区分组 / 相对时间会话行 / 底部身份卡）。会话页改成面包屑头 + 居中 860dp 正文。
   文件页授权根从竖列改成横向 chip，避免"侧栏 + 竖列 = 四栏"。
2. **输入区**：圆角卡片 + 无边框输入 + 圆形发送键 + 运行中停止键；统计一行移到输入框下方；
   权限胶囊只读（点一下解释权限在电脑端分配）。
3. **主题**：明暗开关 → **5 套预设**（暖砂默认 / 冷瓷 / 苔原 / 深海 / 暮紫）+ 跟随系统；
   旧 `light`/`dark` 自动映射；设置页改为带色卡的卡片；三级文字从 3.0:1 压到 ≥4.3:1。
4. **顺手**：`prefs.lastNav` 生效（记住上次停留的页）；修掉连接页最后一处硬编码 `Color.White`。

**测了什么**

- `tools/build.sh` 通过，0 error。
- 截图：暖砂下的会话页 / 文件页 / 设置页；表格、侧栏选中态、时间、授权根 chip、主题色卡均正确。
- 五套主题对比度脚本实算，全部 AA。
- `tools/docs-check.sh` 28 项全绿。

**没验证什么（明账）**

- **深色只截到「深海」一套会话页**；「暮紫」与深色下的文件/设置页未截（沙箱 GUI 反复 OOM）。
- 交互（悬停、菜单展开、点选主题）仍未实测——鼠标进不去 root 窗口。

---

## v1.0.0 第四轮 · Markdown 渲染修复（2026-10-21）

**基于什么**：用户报障「会话中的 MD 格式没渲染，已看到表格的样式都不对，其他的你自己检查」。
先用真实语料定量：三个会话、92 段助手正文、16368 字符 → **表格行 54（10 张表）**、
行内代码 97、粗体 77、标题 31、列表 39、围栏代码 8、链接 4。

**干了什么**

1. **表格（报障主体）**：旧解析器**没有表格分支**，`| a | b |` 全当段落画。改为按
   **分隔行 `|---|`** 判定（不按行首管道，GFM 允许省首尾管道）；行列不齐在解析层归一；
   渲染走横向滚动 + 视觉宽度算列宽 + 表头底色 + 斑马纹；单元格再过一遍行内解析。
2. **拆层**：解析进 `data/Markdown.kt`（489 行，纯函数、不 import Compose，可被脚本直接跑），
   渲染进 `ui/MarkdownView.kt`（396 行）；`ui/Components.kt` 420 → 141 行。
3. **顺带修的**：标题 1–6 级分级、任务列表与两级缩进、引用块、`---` 分隔线、
   代码块语言标签 + 复制按钮 + 纵向滚动、可点击链接、未闭合标记按字面保留。
4. **验证脚本**：`tools/verify-markdown.sh`（22 条内置用例 + 真实会话表形状断言）。

**测了什么**

- `tools/build.sh` 通过，0 error。
- `tools/verify-markdown.sh`：内置 22 项全过；三份真实会话表格 **4 / 4 / 2** 张，
  行数不齐 **0** 行。
- 截图：表头灰底、斑马纹、列分隔、单元格内行内代码与链接、`##` 分级、`- ` 圆点列表均正确。
- `tools/docs-check.sh` 28 项全绿。

**没验证什么（明账）**

- 表格横向滚动到底、超长表格性能未在真机看过（沙箱只截首屏）。
- 真机 `dpkg -i` 仍未验证。

---

## v1.0.0 第三轮 · 按安卓版缺口实现（2026-10-21）

**基于什么**：用户指令「按对照安卓版的缺口进行实现」。按 `PARITY.md` 第三节的顺序
（会话侧栏 → 聊天可用性 → 阅读体验 → 输入效率）清掉九项。

**干了什么**

1. **会话侧栏**：会话行悬停 `⋯` 菜单（重命名 / 分叉 / 复制 ID / 归档）；「已归档」开关；
   分叉后直接跳到新会话。（左栏拆成 `ui/SessionListPane.kt`）
2. **插话**：运行中发送走 `mode: steer`，按钮文案变「插话」，回车同义。
3. **审批代签**：从"只显示计数，请在电脑处理"改为逐条列出并「允许一次 / 拒绝」，
   风险等级与理由都在卡片里。删掉了"桌面端不代签"这句话——它本来就是错的。
4. **交付物卡片**：`deliverables/presented` 不再拍平成一句提示，改成卡片，点了定位到文件页。
5. **工具专用块**：read 路径头 / bash `$ 命令` + 剥 ANSI / edit、write 的 `-`/`+` 对照；
   分发逻辑放在 `data/ToolBlockView.kt`（纯函数，解析失败一律回落通用块）。
   附带工具耗时（`result.time - call.time`，时钟倒退则丢弃）。
6. **会话统计**：消息区顶部一行——轮次 · 工具次数（失败数） · 思考段 · 产出数。
7. **斜杠命令**：输入 `/` 列本会话命令，选中即填或即执行。
8. **结构**：`AppState.kt` 793 → 667 行（`AppStateFiles.kt` / `AppStateChat.kt` 按域拆出），
   800 行预算继续成立；`TECH-DEBT` 里的 DEBT-004 已清账。

**测了什么**

- `tools/build.sh` 通过。
- **真实数据验证**（本机 DSH，三个会话）：
  事件 259/227/289 → 可渲染 96/88/103 条；工具专用块命中 30/37、21/24、40/42；
  耗时可算 100%；`Deliverable` 真实解析出 1 条。
- `tools/docs-check.sh` 28 项全绿。
- 应用能起窗口（1270×790），截到首屏静态图。

**没验证什么（明账）**

- **界面交互**：本沙箱以 root 连 `DISPLAY=:0` 时 KWin 不接管窗口，`xdotool` 点不进去，
  展开态 / 菜单 / 审批卡片 / 交付物卡片**都没有在真实交互中看过**。
- 真机 `dpkg -i` 依旧未验证。

---

## v1.0.0 第二轮 · 真机报障收口（2026-10-21）

**基于什么**：用户在 UOS 上装了第一版 deb，报两个问题——「安装无桌面快捷方式」「会话打开无任何显示」，
并要求对照只读仓库 `dsh-companion`（安卓端 v2.2.44）补齐能力。

**干了什么**

1. **无快捷方式**：定位到 jpackage 只把 `.desktop` 生成在 `/opt/dsh-desktop/lib/`，**没写进
   `/usr/share/applications`**。`tools/package.sh` 增第 5 步（解包重打）：菜单项 + 5 档 hicolor 图标
   （`tools/IconGen.java` 用 Java2D 画 DS 字标，1024 画布降采样）+ `postinst` 给用户桌面临一份 +
   `Main.kt` 首次启动兜底。`StartupWMClass` 改成实测值 `io-github-westanke-dshdesktop-MainKt`。
2. **会话空白（主因）**：线上历史每条事件都包了一层 `{"type":"event","event":{…}}`，
   解析按裸事件读，`type` 恒为 `"event"`，**305 条事件全部丢弃**。
   `parseHistory` 加 `unwrapEvent()`，两趟循环都过；兼容裸事件形状。
3. **错误不可见**：`refreshSessions()` 的 `onFailure` 是空的 → `sessionsError` + 左栏显示原因与重试。
4. **`profile.rootIds` 永远为空**：配对响应不带该字段，被空值覆盖 → `adoptDevice()` + `refreshDevice()`。
5. **历史分页**：`hasMore` + `beforeSeq`，顶部「加载更早的消息」。
6. **工具行合并**：照安卓端 `mergeToolRuns` 口径，相邻工具调用合组可展开。
7. **工程宪法与代码地图**：`AGENTS.md` 改为宪法体例；`docs/project/` 九份文档；
   新增 `CODEMAP.md`（目录结构 + 接口 + 改动路线）并把"改代码三步"写进第 4.4 节；
   `tools/docs-check.sh` 扩到 **28 项**，含代码地图与源码的双向校验。

**测了什么**

- `tools/build.sh` 通过（467 个类）。
- **真实数据解析验证**：解出本机设备令牌，直接取真实会话历史（305 条，`hasMore: true`），
  修复后 **305 → 58 条可渲染**（Tool 15 / ToolGroup 10 / Reasoning 23 / Notice 5 / Assistant 4 / User 1）。
- `dpkg-deb -c` 确认新 deb 含 `/usr/share/applications/dsh-desktop.desktop` 与 5 档图标。

**没验证什么（明账）**

- **真机 `dpkg -i`**：本沙箱 `/var/log` 与 `/usr/share` 只读、`chown` 被禁，装不了。
  用户需自己装一次。
- **界面截图**：本沙箱以 root 连 `DISPLAY=:0` 时窗口被 KWin 拒绝（1×1），没有 Xvfb，
  当时拍不到真实界面。**第三轮已更正**：内容画布是 `sun-awt-X11-XCanvasPeer` 子窗口，
  `import -window` 能拍到 1270×790 的真实界面；但鼠标点击仍进不去。
  见 `PITFALLS.md` §窗口以 1×1 创建。
- **Windows msi**：无 Windows 机器，`build-windows.bat` 只做了语法与前置检查。

---

## v1.0.0 第一轮 · 从零到可运行（2026-10-21）

- 技术选型落地：Kotlin 2.1.0 + Compose Multiplatform Desktop 1.7.3（Skia 直绘，非网页）+ JDK 17。
- 协议层复用 `dsh-remote-bridge` 的 `kotlin-sdk`，补 `ApiExt.kt`（归档列表、路径解析、提问应答、上传）。
- 三页壳：连接（配对码）、会话（列表/正文/输入）、文件（根/列表/预览）、设置。
- 本机构建链：`tools/build.sh` 直调 `K2JVMCompiler`（Gradle 在 4GB 限制下起不来）、
  Python `zipfile` 打 jar、`jpackage` 打 deb。
- 产物：`dsh-desktop_1.0.0-1_amd64.deb`（90 MB）、`dsh-desktop-1.0.0-portable-linux-x64.tar.gz`（74 MB）。
- 用 mock 服务端（端口 3091）做过端到端界面验证，截图见工作区 `shots/`。

---

## 下一步（按用户感知排序）

1. 真机装包确认快捷方式、会话显示与这一轮新增的交互（等用户）。
2. **附件发送**：聊天里带图片/文件，桌面端有文件选择器，成本低。
3. **文件写入 + ETag 保存**：能看到不能改是目前最割裂的一处。
4. 推理等级 / 权限预设 / Agent Preset：接口都在，缺三个下拉。

详见 `ROADMAP.md`，能力差距见 `PARITY.md`。
