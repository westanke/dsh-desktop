# 状态（STATUS）

- 日期：**2026-10-22**
- 版本：**1.0.0**（`v1.0.0` tag 已发；Release 由 GitHub 现场构建，产物已**同步回 Gitee 主源**）
- 平台：UOS / Deepin x64 与 arm64（deb）、Windows x64（zip + msi）

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

- 三个产物**仍未真机安装验证**。
- Gitee 上放的是 GitHub 构建的那份 deb（与本机那份字节不同，jpackage 烤了时间戳）——
  刻意如此，否则 Gitee 里的包与随包校验和对不上。

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
