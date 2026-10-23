# 坑位索引（PITFALLS）

> **名字 = 触发词 = 锚点**。`AGENTS.md` 第 7 节只列名字，正文在这里。
> 动手前按名字读对应条目；不看就改 = 重踩。
>
> 每条四段：**现象**（用户/开发者看到什么）、**根因**、**修法**（含关键代码形态）、**防回归**（怎么防止改回去）。

---

### 历史事件有外层包装

**现象**：会话点开一片空白，一条消息都不显示，界面也没有任何报错。左栏列表正常。

**根因**：线上 `GET /chat/sessions/{id}/messages` 返回的每一条都多包了一层：

```json
{"type":"event","event":{"type":"assistant/message","seq":3269,"data":{…}}}
```

按裸事件解析时 `type` 读到字符串 `"event"`、`obj("data")` 为 null，于是 `continue` 掉每一条。
真实会话 305 条事件**全部丢弃**（2026-10-21 真机报障，最贵的一次）。

**修法**：`ChatHistory.kt` 的 `unwrapEvent()`，两趟循环（工具结果索引 + 主投影）都要过：

```kotlin
private fun unwrapEvent(element: JsonElement): JsonObject? {
    val outer = element as? JsonObject ?: return null
    val inner = outer.obj("event")
    return if (inner != null && outer.str("type") == "event") inner else outer
}
```

同时兼容早期插件/事件流的裸事件形状，不要只留包装分支。安卓端对应实现是
`HistoryEntry(event: SessionEvent)`——它显式建模了这层，桌面端当初漏了。

**防回归**：`.diag` 外的验证脚本（见 `TESTING.md`）拿真实响应跑 `parseHistory`，事件数 > 0 而条目数为 0 即判失败。

---

### turn/end 的正常收尾是 completed 不是 ok

**现象**：以为"正常收尾"会渲染出提示或断言失败，或反过来把正常收尾误报成异常。

**根因**：真实 `turn/end` 的 `reason.kind` 正常值是 **`completed`**；`error` / `max-tokens` / `aborted` 才需要渲染。
文档里曾写作 `ok|error|max-tokens|aborted`，与线上不符。

**修法**：只对 `error` / `max-tokens` / `aborted` 出提示，其余（含 `completed`）静默。

**防回归**：`parseHistory` 的 `when` 里不要给 `else` 加渲染逻辑。

---

### 历史默认只给最近一段

**现象**：用户说"往下翻不到更早的记录"，或以为会话被截断了。

**根因**：服务端默认返回最近约 305 条并带 `hasMore: true`，往回翻必须带 `beforeSeq`。

**修法**：`listMessagesPage(sessionId, beforeSeq, maxMessages)` + `loadOlderHistory()`，
用最小 `seq` 当游标，合并时按 `id` 去重（服务端会把边界那条再给一次）。

**防回归**：`historyHasMore` 为真时，消息区顶部必须有「加载更早的消息」入口。

---

### 会话列表加载失败被静默吞掉

**现象**：整页空白，连错误都没有，用户只能猜"是不是坏了"。

**根因**：`refreshSessions()` 的 `onFailure` 曾是空注释 `/* 列表刷新失败不打断聊天 */`。
这是"整页空白"最常见的一条路径，却被刻意静音。

**修法**：`sessionsError` 状态 + 左栏显示原因与「重试」。

**防回归**：新增任何 `runCatching` 时，`onFailure` 不允许留空——要么进状态，要么 `showToast`。

---

### 配对响应不带 rootIds

**现象**：设置页显示设备已授权，但 `profile.json` 里 `rootIds` 是 `[]`。

**根因**：`POST /pairings/exchange` 返回的 `device` 只有 id/name/scopes，没有 `rootIds`；
早先代码直接拿它覆盖 profile，空数组就把已有值冲掉。

**修法**：`adoptDevice(view)` 采用"有空就补、不拿空值覆盖"；`bootstrap()` 里 `refreshDevice()`
单独问一次 `GET /devices/self`。

**防回归**：任何 `profile.copy(rootIds = 新值)` 都要先过空值判定。

---

### 冷启动会话空白

**现象**：重开应用，上次的会话高亮着，但正文与模型目录都是空的。

**根因**：`selectedSessionId` 从 `prefs.json` 恢复了，但历史、模型、审批一次都没拉过。

**修法**：`refreshSessions()` 里必须有这个分支：

```kotlin
chatItems.isEmpty() -> {
    selectedSessionId?.let { id -> loadHistory(id); loadModels(id); loadApprovals(id) }
}
```

**防回归**：`selectSession()` 与冷启动恢复是两条路径，改任一条都要保证三者都拉。

---

### jpackage 的 .desktop 不装进 /usr/share/applications

**现象**：装完 deb，开始菜单里没有，桌面上也没有。包里其实"有" `.desktop` 文件。

**根因**：`jpackage --linux-shortcut` 只生成 `/opt/dsh-desktop/lib/dsh-desktop-dsh-desktop.desktop`，
**不写** `/usr/share/applications`。`dpkg -c` 里能看到它，容易误判为"已经装了"。

**修法**：`tools/package.sh` 第 5 步解包重打，补三样：菜单项、hicolor 图标、postinst 桌面拷贝。

**防回归**：`package.sh` 末尾有 `dpkg-deb -c | grep usr/share/applications`，缺了直接 `exit 1`。

---

### postinst 里 [ ] && continue 会掐断脚本

**现象**：`dpkg -i` 报安装失败，或 postinst 只跑了一半。

**根因**：`set -e` 下，`[ -e "$f" ] && continue` 在条件为假时整条返回 1，被当作失败命令。

**修法**：写成 `if [ -e "$f" ]; then continue; fi`。

**防回归**：`tools/linux/postinst-shortcut.sh` 里所有条件分支都用 `if`，不要用 `&&` 短路。

---

### 追加 postinst 必须摘掉末尾的 exit 0

**现象**：追加的桌面图标逻辑永远不执行，但安装"成功"。

**根因**：jpackage 生成的 postinst 末尾有 `exit 0`，追加内容在它之后。

**修法**：`package.sh` 用 Python 正则 `\n\s*exit\s+0\s*;?\s*$` 摘掉后再追加。

**防回归**：合并后打印「postinst / postrm 已合并」，并检查目标文件含自己的函数名。

---

### StartupWMClass 必须等于 AWT 实际 WM_CLASS

**现象**：任务栏出现两个图标；点图标不聚焦已开窗口。

**根因**：`.desktop` 里写的 `StartupWMClass=dsh-desktop`，而 AWT 实际给出的是
`io-github-westanke-dshdesktop-MainKt`。

**修法**：`xwininfo -root -children` 查实际值并写进 `tools/linux/dsh-desktop.desktop`。

**防回归**：改主类名/包名后必须重查一次 WM_CLASS。

---

### 图标只有 32×32

**现象**：桌面上图标发虚、边缘糊。

**根因**：jpackage 自带图标只有一张 32×32。

**修法**：`tools/IconGen.java` 在 1024 画布上画完再降采样，产出 32/48/64/128/256 五档，
装进 `/usr/share/icons/hicolor/`；`/opt` 里那张也换成 256。

**防回归**：`package.sh` 的校验行要能列出 5 个尺寸的 png。

---

### 折叠箭头用字符会退化成圆点

**现象**：`▸` / `▾` 显示成一个小圆点，看起来像装饰而不是可点。

**根因**：Linux 默认字体缺这两个字形，走 fallback 后渲染成替代符号。

**修法**：用 `Icons.AutoMirrored.Filled.KeyboardArrowRight` / `Icons.Filled.KeyboardArrowDown`。

**防回归**：界面里不出现 `Text("▸")` 这类字符图标。

---

### 窗口以 1×1 创建（本沙箱专属）

**现象**：进程活着、`xwininfo` 能看到窗口，但尺寸 1x1、`title` 也不生效。

**根因**：以 root 身份连 `DISPLAY=:0` 时，KWin 不接管非会话用户的窗口。**是沙箱限制**，
用户环境正常（用户能看到窗口，只是内容空）。别照着这个调。

**修法**：`Main.kt` 保留兜底——`LaunchedEffect` 里 `window.width < 400` 就 `setSize(1280, 820)`。
它的真实用途是防 UOS 上 DPI/缩放异常把 `1280.dp` 算成 0。

**能做什么，不能做什么**（2026-10-21 实测）：

- **能截图**：`xwininfo -root -tree | grep MainKt` 会看到三个窗口，其中
  `sun-awt-X11-XCanvasPeer`（1270×790）才是真正的内容画布，
  `import -window 0x…` 那张就能拿到真实界面。别截 `1x1` 那个。
- **不能交互**：`xdotool mousemove/click` 打不进去（KWin 没接管，事件不进队列）。
  展开态、下拉菜单、对话框这类**必须点一下才出现**的界面，本沙箱验证不了。

**防回归**：兜底代码不要删；但不要把"本沙箱窗口 1x1"当成用户 bug 去追。

---

### 沙箱 4GB 地址空间：Gradle 起不来

**现象**：`Native memory allocation (mmap) failed` / `Map failed` / `unable to create native thread`，
发生在 Gradle 的**配置阶段**。

**根因**：单进程虚拟地址空间硬限制 4GB（RLIMIT_AS，不可提升）。

**修法**：编译走 `tools/build.sh` 直调 `K2JVMCompiler`；打 jar 用 Python `zipfile`；
jpackage 靠 `JAVA_TOOL_OPTIONS` 压线程数。

**防回归**：`gradle.properties` 的门槛参数留给正常机器，不要在沙箱里反复试 Gradle。

**同一个限制也会打死"跑起来的客户端"**：`tools/build.sh run` 在沙箱里能起窗口，
但挂着真实会话跑十几秒到几十秒后会 `insufficient memory … Chunk::new` 崩掉
（`-Xmx768m` 也救不了，这是地址空间不是堆）。所以界面验证只能**抢在崩溃前截图**，
别指望"开着不动等我看"。反复启动会叠出多个僵尸 AWT 窗口（`xwininfo` 里多个
`Content window`，`ps` 却看不到进程），截图前先按窗口 id 从新到旧试。

---

### TMPDIR 对 jpackage 不生效

**现象**：`No space left on device`，但 `TMPDIR` 明显指向大盘。

**根因**：jpackage 是 JVM 工具，读的是 `java.io.tmpdir`，不是环境变量。

**修法**：`JAVA_TOOL_OPTIONS` 里同时给 `-Djava.io.tmpdir=$TMP`。

**防回归**：任何 JVM 工具的临时目录都要双写。

---

### skiko 解压原生库失败

**现象**：启动即 `ExceptionInInitializerError`，`Caused by: java.io.IOException: Permission denied`，
看不出跟渲染有关。栈顶是 `File.createTempFile` ← `skiko.Library.unpackIfNeeded`。

**根因**：两个都在坑里，只修一个不够。

1. skiko **不读 `java.io.tmpdir`**，它用自己的 `skiko.data.path`，默认 `$user.home/.skiko`。
   以 `sudo`/服务/包方式启动时 `user.home` 会落到 `/root`（本机 `/root` 是 `nobody:nogroup 700`），
   于是 `mkdirs` 失败，skiko 报的还是 `Permission denied`。
2. 就算目录能建 0 字节文件也算不上能用：本机 `/tmp` 是 **10 MB 的 tmpfs**，
   原生库 `libskiko-linux-x64.so` 有 **28.9 MB**，写一半就 ENOSPC，报错文案照样误导。

**修法**：`ensureWritableDirs()` 在 UI 之前把**两处**都探一遍：`skiko.data.path`
（退路 `XDG_DATA_HOME` / `$HOME/.local/share` / `user.home/.skiko` / `user.dir`）和
`java.io.tmpdir`。判据是 `usableDir()`：建得出文件 **且** 剩余空间 ≥ 96 MB。

**防回归**：
- 认死理没用，实测才准。用 `-Dskiko.data.path=<可写目录>` 与默认值各跑一次，
  能起窗就证明命中的是那个属性；只看栈会误判成"权限问题"。
- 改完 `Main.kt` 只跑 `tools/build.sh` 是**测不到的**——它只编到 `build/classes`，
  不打 jar，起来的是旧 `build/app-input/dsh-desktop.jar`。必须 `tools/package.sh`。
- 以 `sudo`/`.desktop` 启动时 `user.home` 取的是 `getpwuid`，**不认 `$HOME`**，
  所以探测不能只看环境变量。

---

### root 与 tmp 在这台机器上都不可靠

**现象**：root 身份下 `mkdir /root/.skiko` 失败；`/tmp` 写着写着突然 `No space left`。

**根因**：`/root` 属主是 `nobody:nogroup` 且 700（uid 0 又缺 `CAP_DAC_OVERRIDE`）；
`/tmp` 是 10 MB 的 tmpfs（`findmnt /tmp` 看 `size=10240k`）。

**修法**：临时目录、原生库目录都往工作区或 `$HOME` 引，别信 `/tmp` 的容量，也别信 root 能写。

**防回归**：写文件前先看 `df`/`usableSpace`，不要用"能不能 touch"判断空间够不够。

---

### core-runtime 在 aar 里

**现象**：`NoClassDefFoundError: androidx.arch.core.internal.FastSafeIterableMap`。

**根因**：`core-runtime` 只发布 aar，直接当 jar 加进 classpath 无效。

**修法**：从 aar 里抽出 `classes.jar` 改名放进 `lib/`（`tools/fetch-deps.py` 已处理）。

**防回归**：报 `NoClassDefFoundError` 且类在 `androidx.*` 下，先怀疑 aar。

---

### rm 被 genie-trash 拦截

**现象**：`rm -f` 报 `genie-trash failed ... /home/.Trash-0: Permission denied`；
`rm -rf` 静默失败、文件还在。

**根因**：本机 `rm` 被 safe-delete 包装，回收站根路径按空 `HOME` 算错；fail-closed，不真正删除。

**修法**：用 `mv` 把文件/目录整体搬走（`_bak/`、`build/.xxx-old-<ts>` 之类），等效删除且可恢复。

**防回归**：脚本里不出现 `rm -rf`；批量删除阈值 50 个文件，超了会中断。

---

### 会话标题为空

**现象**：左栏出现空白行，点得到但不知道是哪个会话。

**根因**：新建会话 `title` 为 null。

**修法**：`displayTitle` 依次退 `title` → `cwd` 末段 → "新会话"。

**防回归**：列表渲染一律用 `displayTitle`，不要直接用 `title`。

---

### 同包不同目录的扩展函数要显式 import

**现象**：把 `AppState` 的动作拆到 `AppStateFiles.kt` / `AppStateChat.kt`（同包 `io.github.westanke.dshdesktop`）
之后，`ui/FilesScreen.kt` 里所有 `state.refreshDirectory()` 全变成
`error: unresolved reference`。

**根因**：Kotlin 的扩展函数**不继承同包可见性**。只有"同一个包里的调用点"才能免 import；
`ui` 是子包（`…dshdesktop.ui`），属于另一个包，必须 `import io.github.westanke.dshdesktop.refreshDirectory`
把**每个函数名**逐个引进来 —— import 接收者类没用。

**修法**：在调用文件里按名字 import 扩展函数（`import io.github.westanke.dshdesktop.forkSession` 这样）。
一次拆分会连带改 3 个 UI 文件的 import 段。

**防回归**：拆 `AppState` 后先跑 `tools/build.sh`，`unresolved reference` 就是漏了 import。

---

### 修沙箱里的 java 脚本报 unable to create native thread

**现象**：`java .diag/Decrypt.java out.txt` 直接死在 VM 初始化：
`java.lang.OutOfMemoryError: unable to create native thread`。

**根因**：沙箱对进程/线程数有限制，JDK 按 CPU 核数决定 GC 与 JIT 线程数，核多线程就多。
（同一个限制也是 `tools/build.sh` 里那一堆 `-XX:ActiveProcessorCount=1` 的由来。）

**修法**：给诊断脚本也压住线程数 —
`java -Xmx256m -Xss512k -XX:ActiveProcessorCount=1 -XX:+UseSerialGC -Djava.io.tmpdir=<workspace> Xxx.java`。

**防回归**：任何一次性 `java Xxx.java` 都带 `-XX:ActiveProcessorCount=1`；别用 `/tmp` 当 tmpdir。

---

### 表格只按行首管道判定会退化

**现象**：会话里 10 张表全没渲染，界面上就是一排排竖线；偶发还会把普通段落吞掉。

**根因**：GFM 管道表**允许省略首尾管道**（`a | b` 合法），只按"行首是 `|`"判定会漏。
反过来只按"含 `|`"判定，会把 `a | b` 这类正常文字误判成表。真正的唯一判据是
**紧跟表头的分隔行** `|---|---|`（允许 `:--`/`:-:`/`--:` 对齐）。

**修法**：`data/Markdown.kt` 里先攒段落，遇到分隔行才回头把上一行取出来当表头
（`paragraph.removeLastOrNull()`），解析不出对齐行就不进 Table 分支。三条配套：
1. 行列不齐在**解析层**归一（`normalizeRow`），UI 直接消费 `table.rows`；
2. 单元格内容必须再过一遍行内解析（`annotatedFrom`），否则表里的 `` `code` `` 与 `**粗体**` 全画成原文；
3. 表格用 `horizontalScroll` + 按视觉宽度算列宽（中文按 2 宽），别用 `weight()` 均分。

**防回归**：`tools/verify-markdown.sh <真实历史 json>` 对真实数据断言
**至少 1 张表、无空表、每行宽度等于列数**，任一不满足退出码 2。
另注意：解析层不许 import Compose，否则脚本加载类时直接炸。

### KDoc 里写 image 通配符会吃掉后面的注释

**现象**：编译报 `error: syntax error: Unclosed comment`，位置指向文件**末尾**，
报错行附近的代码看起来完全正常。

**根因**：Kotlin 的块注释**可以嵌套**。KDoc 里写 `图片走 \`image/*\`` 时那个 `/*`
被当成"再开一层注释"，于是后面第一个 `*/` 关掉的是**内层**，外层一直开着，
直到文件尾。**报错位置和真正原因差了几十行。**

**修法**：文档里要提到通配路径就换个写法（"是不是 image 前缀"、"`image/…`"），
别在注释里留 `/*`。看到 `Unclosed comment` 报在文件尾，先全文搜 `/*`。

---

### 截图坐标要按窗口真实像素算

**现象**：`xdotool mousemove` 点的位置总是偏，点"轨迹"按钮，结果悬停在侧栏第一行。

**根因**：`import -window` 拿到的 PNG 是**真实像素**（本机 1270×790），
而读图工具会把大图缩到 1080×686 再给你看。照"看到的位置"去点，坐标乘了 0.85。

**修法**：`eval "$(xdotool getwindowgeometry --shell $WID)"` 取真实 `X/Y/WIDTH/HEIGHT`，
坐标按真实尺寸换算。另外确认截的是**画布窗口**（`sun-awt-X11-XCanvasPeer`），不是那个 1×1。

---

### 后台进程在 Bash 调用返回后被回收

**现象**：`nohup java … &` 起了 GUI，下一个工具调用里窗口就没了，日志空白、也没有 `hs_err`。

**根因**：本会话的后台子进程在**工具调用结束时**被回收（不是 JVM 崩）。所以
"先启动，再截图"分两次调用必然失败，还会误判成 OOM。

**修法**：GUI 冒烟测试必须塞进**同一次** Bash 调用：启动 → 轮询等窗口 → 截图 → 结束。
`hs_err_pid*.log` 才是真 OOM 的证据，没有它就别往内存上归因。

---

### Windows 上的中文文本多半不是 UTF-8

**现象**：同一份 `.txt` / `.csv`，Linux 上看着正常，Windows 用户发过来就是乱码方块。

**根因**：记事本默认存 GBK（老版本）或 UTF-16LE（"Unicode"选项），Excel 导出的 CSV 常带
UTF-8 BOM。一律按 `String(bytes, UTF_8)` 解，坏字节会被**静默替换成 U+FFFD**，
不抛异常，于是"看起来只是乱码"，没人会想到是编码。

**修法**：`decodeText()` 三级回退 —— BOM（UTF-8/UTF-16LE/UTF-16BE）→ **严格模式** UTF-8
（`CodingErrorAction.REPORT`）→ GBK → 最后有损 UTF-8 兜底。解码结果带上 `charset`，
界面在徽标旁标出来。

**防回归**：
- 别用宽容模式 `String(bytes, charset)` 判断"是不是 UTF-8"——它永远成功。
- **换行不做归一**：预览文本同时是编辑草稿的底稿，把 CRLF 改成 LF 再保存，
  等于悄悄换了整个文件的行尾。归一放在渲染层（`MarkdownPreviewPane` 里 `replace("\r\n","\n")`）。

---

### Desktop.open 在 UOS 上经常静默失败

**现象**：点"用系统程序打开"没反应，也不报错。

**根因**：`Desktop.isDesktopSupported()` 在有 X11 时返回 true，但 `Desktop.open` 走的是
AWT 的 `gnome_url_show` / gvfs 桥，UOS 上这条链路常常是断的；调用不抛异常，就是什么都不发生。

**修法**：`data/SystemOpen.kt` 按平台给候选命令，从可靠到兜底依次试：
UOS/Linux `xdg-open` → `gio open` → `Desktop.open`；
Windows `rundll32 url.dll,FileProtocolHandler` → `cmd /c start ""` → `Desktop.open`。
每条命令失败都收集原因，全失败时把原因拼进错误里给人看。

**实测**（本机 UOS，2026-10-21）：

```
$ xdg-open ./说明文档.pdf
<warning> open failed: dbus: authentication failed      → rc=4
$ gio open ./说明文档.pdf                                → rc=0
```

`xdg-open` 明明是首选却在这台机器上直接失败，靠 `gio open` 兜住。**只实现一条路就会表现为
"点了没反应"**。

**防回归**：
- 只等 2 秒，**超时不算失败**——有的环境 `xdg-open` 会一直挂着等程序退出，
  杀掉它会把用户刚开的窗口一起带走。
- `isDesktopSupported() == true` 不能当作"能打开"的证据。
- 判据是**退出码**，不是"命令存在"。`xdg-open` 存在照样可以 rc=4。

---

### 服务端不保证遵守 Range 请求

**现象**：文本预览只想要前 512 KiB，结果还是把整份文件拉下来了。

**根因**：`Range: bytes=0-524287` 是**请求**不是命令。服务端可以忽略它照样返回 200 + 全量。

**修法**：`textPreview()` 在本地再截一刀（`copyOf(MAX_TEXT_PREVIEW_BYTES)`），
`truncated` 用 `entry.size` 判断，不依赖响应码是 206 还是 200。

**防回归**：凡是"为了省流量只取一段"的地方，本地都要有一道兜底截断。

---

### 同一个函数被拼上两遍 Composable 注解

**现象**：`error: this annotation is not repeatable`，指着一行看着完全正常的 `@Composable`。

**根因**：用脚本拼接文件时，"保留到注解之前"的边界算错了一位，新块自带的那行
`@Composable` 和保留下来的那行叠在了一起。

**修法**：拼完先 `grep -n -A1 "@Composable"` 看有没有连续两行；更好的做法是
**保留到注解之前**（切片不含注解），让新块的注解成为唯一一份。

**防回归**：脚本拼接代码后必须跑一次编译，不能只看"行数对不对"。

---

### 构建脚本在运行中被改写会读到半行

**现象**：脚本本身没改过，却报出莫名其妙的一句 `tools/package.sh:行227: -vE：未找到命令`，
或者某一段代码凭空被跳过、被重复执行。重跑一次又完全正常。

**根因**：bash 是**按字节偏移流式读取**脚本的，不预读全文。脚本跑到一半时文件被改写
（哪怕是删掉一行），后续读取偏移就全错位了——它可能从某个命令的中间开始解析，
把 `grep -vE` 里的 `-vE` 当成命令名。

**修法**：跑长脚本时不要动它。确实要改就先停下来，改完重跑。用 `git stash` 或
复制一份到新路径再改也行，但别改正在执行的那个文件。

**防回归**：报错行号与源码对不上、命令名像是参数片段时，先怀疑"脚本在跑的时候被改了"，
而不是去改脚本内容。

---

### Windows 上类路径分隔符是分号且路径要 cygpath

**现象**：Windows runner 上编译一上来就报
`Could not find or load main class org.jetbrains.kotlin.cli.jvm.K2JVMCompiler`，
或者 `-Xplugin` 指向的文件"不存在"，而同样的脚本在 Linux 上一切正常。

**根因**：两个 Windows 专属事实叠在一起：
1. Git Bash 里的路径是 `/d/a/b` 形式，`java.exe` 只认 `D:\a\b`；
2. JVM 的类路径分隔符在 Windows 上是 **`;`**，不是 `:`。

用 `:` 拼出来的 `a.jar:b.jar` 会被 `java.exe` 当作**一个**路径，所以主类当然找不到。

**修法**：脚本开头判定一次方言，之后所有路径都过同一个函数：

```bash
IS_WIN=0; case "$PLATFORM" in windows-*) IS_WIN=1 ;; esac
if [ "$IS_WIN" = 1 ]; then CPSEP=';'; else CPSEP=':'; fi
wp() { if [ "$IS_WIN" = 1 ] && command -v cygpath >/dev/null 2>&1; then cygpath -w "$1"; else printf '%s' "$1"; fi; }
RUNTIME_CP="$RUNTIME_CP$(wp "$f")$CPSEP"
```

Python 打 jar 时传给 `os.path.join` 的根目录也要过 `wp`；Windows runner 上解释器名是
`python` 而不是 `python3`，所以一律走"依次探测 `python3` / `python` / `py`"。

**防回归**：跨平台脚本只维护一份，平台差异集中在 `wp()` / `CPSEP` / `PY` 三处，
不许在 YAML 里为某个平台写第二套命令。

---

### lib 里的 skiko 运行时必须按平台剔除

**现象**：arm64 的 deb 装到 arm64 机器上，启动即崩，日志里是
`UnsatisfiedLinkError: .../libskiko-linux-x64.so`；或者 Windows 包装出来带着 `.so`。

**根因**：skiko 拆成两个 artifact：`skiko-awt`（跨平台 API）与
`skiko-awt-runtime-<platform>`（约 10 MB 原生库）。`lib/` 里入库的是开发机那份
（`linux-x64`），如果无条件全塞进 classpath，打出来的包就带着**跑不起来**的原生库。

**修法**：`tools/build.sh` 与 `tools/package.sh` 都按 `TARGET_PLATFORM` 过滤，
只保留文件名里含目标平台的那一份，其余从 classpath 与 `app-input` 剔除；
缺目标平台运行时就直接报错并提示：

```
先跑：tools/fetch-platform.sh linux-arm64
```

CI 的 arm64 job 里还要额外断言包里真的带了 arm64 那份：

```bash
dpkg-deb -c dist/*.deb | grep -q 'skiko-awt-runtime-linux-arm64'
```

**防回归**：新增平台时只加"下载 + 过滤 + 断言"三件事，不许改编译参数。

---

### GITHUB_TOKEN 改不了 workflow 文件

**现象**：`mirror-sync` 工作流 `git push --mirror` 报
`refusing to allow a GitHub App to create or update workflow`，退出码非 0。

**根因**：GitHub 的安全设计——`GITHUB_TOKEN` 永远不能创建或修改
`.github/workflows/*.yml`，而 `push --mirror` 会把主源历史里的 workflow 文件一并写入。
`permissions:` 里也**没有** `workflows` 这个键，加不了。

**修法**：推送凭证换成 PAT（secret `MIRROR_PAT`，需 `repo` + `workflow` scope），
把远端 URL 改成 `https://x-access-token:${MIRROR_PAT}@github.com/<owner>/<repo>.git`，
并把 secret 缺失显式判死（`${MIRROR_PAT:?...}`）。

**防回归**：镜像工作流里**不许**用 `${{ github.token }}` 做 push；判据看**退出码**，
不要 grep 输出文本（`git push` 的提示随版本变）。

---

### Windows 上 Python 打印中文会 UnicodeEncodeError

**现象**：Windows runner 上编译已经过了（`▸ 编译完成` 都打了出来），紧接着
`UnicodeEncodeError: 'charmap' codec can't encode characters in position 7-10`。
同样的脚本在 Linux 上好好的。

**根因**：Python 在非 UTF-8 控制台下把 stdout 编码成 `cp1252`（中文机器上可能是 GBK），
任何非 ASCII 输出——中文提示、`▸`、`✓`——都会抛异常。这不是 jar 打包本身的问题，
是"打完 jar 后那句提示"把进程带崩的。

**修法**：调用 Python 前开 UTF-8 模式，两个环境变量一起给：

```bash
export PYTHONUTF8=1
export PYTHONIOENCODING=utf-8
```

跨平台的构建脚本里，Python 输出、写文件（`open(..., encoding="utf-8")`）都要显式处理，
不能指望控制台是 UTF-8。

**防回归**：脚本里出现中文提示时，先确认这两行已经 export；**工作流里内联的 python 同样要设**（在 step 上加 env: PYTHONUTF8）——构建脚本 export 的变量不会传给后面单独起的一步，我在校验步骤上就这样二次踩过。

---

### Gitee 附件字段名是 file，文档写的 file[] 是错的

**现象**：按 Gitee OpenAPI 文档 `POST /v5/repos/{owner}/{repo}/releases/{id}/attach_files`
传附件，字段名用 `file[]`，不管 6 B 的文本还是 90 MB 的 deb，一律
`400 {"messages":["file is invalid"]}`。换 curl 重试也照样 400，
很容易误判成"附件超了 Gitee 的大小限制"。

**根因**：文档写 `file[]`，服务端只认 `file`。附件里还有个干扰项——
Gitee 会自动往发行版里塞 `v1.0.0.zip` / `v1.0.0.tar.gz` 两个源码包，
它们的 `assets` 条目不带 `id`，所以列出来时别当成用户附件去删。

**修法**：

```bash
curl -X POST ".../attach_files" -F "access_token=$TOKEN" -F "file=@dist/xxx.deb"
```

**防回归**：鉴别方法是拿一个几 KB 的 `hello.txt` 试——它要是也 400，
就一定是字段名/权限问题，不是体积问题。

---

### Gitee API 的 POST 不带 Content-Type 一律 400

**现象**：同一个建发行版的调用，`curl -d` 成功，`urllib` 用
`urlencode` 拼好表单体却得到 `400 Bad Request`，响应体里没有任何有效提示。

**根因**：`curl -d` 默认带 `Content-Type: application/x-www-form-urlencoded`，
而 `urllib.request.Request(data=...)` 不会自动补。Gitee 收到没有 Content-Type 的
POST 直接拒。

**修法**：POST/PATCH 时显式带上，GET 则把 `access_token` 拼进 query：

```python
headers = {"Accept": "application/json",
           "Content-Type": "application/x-www-form-urlencoded"}
```

**防回归**：另外注意 `PATCH /releases/{id}` **必须带 `tag_name`**（哪怕不改标签），
否则回 `400 {"messages":["tag_name is missing"]}`。
还有一点：给 `Request` 传了 `data` 但不传 `method`，urllib 会当成 POST——
想发 GET 就把参数拼到 URL 上，别塞进 data。

---

### push --mirror 搬不了 Release

**现象**：Gitee 是主源，refs 每 3 小时镜像到 GitHub，一切正常；
但打开 Gitee 仓库的发行版页，一个都没有。

**根因**：`--mirror` 只搬 refs。而安装包是 GitHub Actions 现场构建的，
Release 天然长在 GitHub 上——主源上自然就是空的。

**修法**：反方向再搬一次，见 `scripts/ci/sync-release-to-gitee.py`；
`release.yml` 发版当场搬（用 `--local` 直接传刚构建好的产物），
`mirror-sync.yml` 定时补缺（从 GitHub 拉）。

**防回归**：同一个 tag 的发行版附件按名字去重即可幂等——Gitee 没有
"覆盖同名附件"的接口（删除需要 attachment id，而发行版详情不返回 id），
所以新版本一定要发新 tag。

---

### 历史只给最近一段，早期交付物不翻页就不出现

**现象**：同一个会话，刚跑完那一轮**有文件卡**；关掉再打开（或会话继续跑长之后）
**卡片就没了**。消息、工具块、思考都在，唯独产出文件消失。

**根因**：`GET /chat/sessions/{id}/messages` 不带 `beforeSeq` 时只返回**最近约 300 条**
（`hasMore: true`）。真机实测：一个已到 seq 1934 的会话，首页只给 seq 1637→1934 共 298 条；
而 `deliverables/presented` 事件在 **seq 580，落在第 5 页**（每页 298 条）。
会话短的时候它还在窗口里 → 有卡；会话一长就滚出窗口 → 没卡。
（另一层是服务端的 2 MiB 字节预算，超了也从**最旧**开始丢整条，方向一致。）

**修法**：卡片不能只靠消息流里恰好有这一段。

- `data/ChatHistory.kt` 新增 `deliverablesIn(events)`：从一页原始事件里抽交付物、不做完整投影；
- `AppState.loadHistory` 拿到首页后，若 `hasMore` 就起 `scanDeliverables()` 后台往回翻
  （最多 6 页，不动 `chatItems`、不动 `oldestSeq`，与"加载更早"是两回事）；
- 结果存 `sessionDeliverables`，UI 用 `deliverablesOutsideStream()` 只画**消息流里没有的**那部分，
  避免与流里的卡重复。

**防回归**：`tools/verify-history.sh <那页 json>` 会打出 `Deliverable=1` 与"4 个产出"；
它变 0 就是回归。判据的本质是——**交付物事件在磁盘上有、在首页里没有**，
所以任何"只在首页里找卡片"的写法都必然丢。

---

### 交付物事件有两个名字

**现象**：新交付物要等下一次刷新才出现；或者反过来，历史里有卡、实时没有。

**根因**：事件名不统一——历史里内核原样透传的是 `deliverables/presented`，
实时流被桥接的 `normalizeDshEvent` 规范化成 **`chat.deliverables.presented`**。
只认其中一个，另一条路就整条断掉。

**修法**：`ChatHistory.kt` 里两个名字都收（`DELIVERABLE_EVENT` / `DELIVERABLE_EVENT_REALTIME`），
`AppState.handleEvent` 也补上这两个分支——实时到了直接插卡片，不等刷新。

**防回归**：新增事件分支时先确认"历史和实时叫同一个名字吗"，
桥接的 `src/host/chat-adapter.ts` 里 `normalizeDshEvent` 加了前缀的实时名，客户端要跟上。

---

### 管道会吞掉 curl 的退出码

**现象**：启动引导器"下载失败"，但报的错是 sha256 校验不过、或者拿一个**空文件**去解压；
把同一个 URL 手敲一遍明明下得来。

**根因**：写成了

```sh
if download "$url" "$TMP_TAR" 2>&1 | sed 's/^/    /'; then
```

管道的退出码是**最后一个命令**（`sed`）的，恒为 0。于是 curl 404 / 超时也被当成成功，
继续拿空文件或半截文件往下走，错误信息完全对不上真因。

**修法**：`if` 里不要接管道，直接判断命令本身：

```sh
if download "$url" "$TMP_TAR"; then
```

要缩进就在 `download()` 内部重定向，或者干脆不缩进——正确比好看重要。

**防回归**：引导器（`tools/launcher/dsh-desktop.sh`）的下载循环没有任何管道；
真机用 404 的 URL 跑一遍，应当**直接**进"换源"而不是走到校验。

---

### 临时文件不要写 /tmp

**现象**：下载运行环境时报 `curl: (23) Failed writing body (8192 != 16384)`，
HTTP 状态码是 200，服务端明明在正常发字节。

**根因**：`/tmp` 在这台机器上被拦（只读 / noexec / 空间受限，视环境而定），
curl 写不进去。错误出在**写本地文件**这一步，跟网络、跟 URL 都没关系。

**修法**：落点改用户缓存目录，失败再退系统临时目录：

```sh
DL_DIR="${XDG_CACHE_HOME:-$HOME/.cache}/dsh-desktop/downloads"
mkdir -p "$DL_DIR" 2>/dev/null || DL_DIR="${TMPDIR:-/tmp}"
```

**防回归**：任何"下载到本地再解压"的流程，落点一律先想清楚是不是用户可写目录。

---

### 运行时 tar 不要多套一层顶层目录

**现象**：轻量包引导器下载、解压都"成功"，但下一次启动又去下一遍；
手动去看，解压出来是 `<RT_BASE>/<RT_BASE>/bin/java`。

**根因**：打包时 `tar -cJf out.tar.xz -C dist "$RT_BASE"`（带着目录名打），
而引导器解压目标是 `RT_HOME`，于是内容落到 `RT_HOME/<RT_BASE>/` 下面，
引导器按 `RT_HOME/bin/java` 找，自然找不到——于是判断"没有运行时"，重新下载。

**修法**：打包时 `-C "$STAGE" .`（内容直接进 tar，不带顶层目录），
与 `jpackage` 的 `lib/runtime/` 布局一致。

**防回归**：CI 里断言 `tar -tJf <资产> | grep -qx './bin/java'`——
既不能是 `dsh-desktop-runtime-*/bin/java`，也不能缺这一行。

---

### jlink 的 OOM 与"内存参数要早于所有 JVM 工具"

**现象**：`package.sh` 在 jlink 那步报
`OutOfMemoryError: unable to create native thread`，而同一台机器上
手动敲同样的 jlink 命令是好的。

**根因**：`export JAVA_TOOL_OPTIONS="-XX:ActiveProcessorCount=1 …"` 写在脚本
**后面**（jpackage 那段），jlink 跑在它之前，没被限制线程数——
开发机地址空间就 4GB，JVM 默认按核数建 GC 线程，直接起不来。

**修法**：`package.sh` 里 `export TMPDIR` / `JAVA_TOOL_OPTIONS` 放在
**任何 JVM 工具之前**（现在在第 87 行，jlink、IconGen、jpackage 共用一份）。

**防回归**：往 `package.sh` 里加新的 JVM 工具调用时，确认它排在那两行 export 之后。

---

### 轻量包的运行时必须与它同版本，发行版里没挂就是 404

**现象**：装 `dsh-desktop-lite_1.0.0-1_amd64.deb`，点图标**什么都不发生**
（2026-10-22 真机报障）。手动跑 `DRY_RUN=1 /opt/dsh-desktop/bin/dsh-desktop` 才看到：

```
尝试 https://gitee.com/.../v1.0.0/dsh-desktop-runtime-1.0.0-linux-x64.tar.xz
curl: (22) The requested URL returned error: 404 Not Found
尝试 https://github.com/.../v1.0.0/dsh-desktop-runtime-1.0.0-linux-x64.tar.xz
curl: (22) The requested URL returned error: 404
```

**根因**：轻包的运行时资产名带 `$VERSION`（`dsh-desktop-runtime-<ver>-<平台>.tar.xz`），
而 v1.0.0 的发行版只有 zip/msi/两个 deb——**没有**运行时资产（那是第 15 轮才加的，
tag 早就打完了）。两处都对：URL 拼得没错，资产确实不存在。

**修法**：发版必须让 **轻包与运行时资产同时出现**（`release.yml` 现在每个平台都出
完整包 + 轻包 + 运行时 tar.xz + sha256）。已经装错版本的机器有两种救法：
① 把运行时解压到 `~/.local/share/dsh-desktop/dsh-desktop-runtime-<ver>-<平台>/`；
② `DSH_RUNTIME_TARBALL=<tar.xz> DSH_RUNTIME_HOME=<目录>` 手动喂给它。

**防回归**：CI 校验里已经有「轻包不带 runtime / 带 sha256 / tar 里有 `./bin/java`」；
**再加一条人为检查**：发完 tag 去 release 页面数资产——`lite deb` 和
`runtime tar.xz` 必须成对出现，版本号必须一致。

---

### 启动器失败必须让人看见（Terminal=false 也得弹窗）

**现象**：用户的原话是"无法打开，无任何提示"。没有弹窗、没有闪一下、没有日志，
能查的东西全都没有。

**根因**：`.desktop` 里写的是 `Terminal=false`，从图标/菜单启动时**没有终端**，
启动器的 `echo … >&2` 全部进了黑洞。启动器本身逻辑是对的（下载、校验、解压、错误码），
但**看不见的失败等于没报错**。

**修法**：`tools/launcher/dsh-desktop.sh` 统一走 `fail()`：stderr + 日志 + 弹窗。
日志固定 `~/.cache/dsh-desktop/launcher.log`（每次启动先记版本、APP_HOME、选中的 java）；
弹窗按 `zenity → kdialog → xmessage → notify-send` 依次探测。
**弹窗一律带 60 秒超时**：`zenity --timeout=60`、`xmessage -timeout 60`——
否则 `xmessage` 没人点就永远不返回，自动化测试里直接吊死（踩过，超时被杀）。
`[ -t 2 ]` 时**不弹**（终端里跑不该蹦窗口）；自动化要完全静默用
`DSH_LAUNCHER_NO_DIALOG=1`。

另外**不要 `exec java`**：`exec` 之后 java 起不来（缺库、线程限制、参数写错）
脚本就没有机会报错了。现在前台跑、拿退出码，`1..128` 才算异常退出
（128+ 是信号，注销/被杀不该弹"启动失败"吓人）。没有终端时 java 的 stderr
也追加进日志，崩溃现场不至于彻底消失。

Windows 侧同理：`dsh-desktop.bat` 双击时控制台一闪而过，失败分支要
`echo %cmdcmdline% | find /i "/c" && pause`，日志写 `%LOCALAPPDATA%\dsh-desktop\launcher.log`。

**防回归**：本地用 `DSH_LAUNCHER_NO_DIALOG=1` 跑四条路径——① 源不存在（404）
② 本地 tar 包 ③ 已解压缓存 ④ java 退出码非 0；每条都要有非零退出码 + 日志有记录。

---

### 内核 schema 是 schemastery，不是 JSON Schema

**现象**：通用设置命名空间表单（P2-1）里所有字段都退化成**只读行**——
字号那栏孤零零显示「14」，点不动、改不了，保存键永远灰。
在开发机上看样例数据毫无问题，连真桥才复现。

**根因**：内核 `settingsController.describe()` 发回来的 schema 不是标准 JSON Schema，
而是 cordis 的 **schemastery 引用图**：`{uid: 12427, refs: {"12427": {type: "object",
dict: {key: <refId>}}}}`，`enum` 也不是数组，而是一个 `union` 节点 `list` 里放若干
`const` 节点的 id。只认 `properties` 的写法在真机上**永远取不到字段表**。
（另有一个隐蔽处：ref id 是**数字**基元，取 id 必须用能吃数字的读法。）

**修法**：`data/SettingsNamespaces.kt` 的 `SettingsSchema.fields()` 两代格式都认——
先按 JSON Schema 取 `properties`，取不到再用 `Schemastery.expand()` 现场把引用图
摊平成 JSON Schema 子集（**最多三层**，`union`→enum、`const`→单选项、`meta.role=secret`
→`x-secret`）。**摊不出来就回只读兜底，绝不猜类型**——把密钥猜成输入框、
把对象猜成字符串，都比只读更坏。

**防回归**：`tools/verify-parity.sh` 的 P2-1 段用**数字 ref id** 的 schemastery
样本钉住整条摊平链（enum/min-max/secret/循环引用/未知类型丢弃）。
新增控件类型时，先问"这格在 schemastery 里对应什么节点"。

### DSH1 的 deviceName 不是本机名

**现象**：导入一份别人分享的 `DSH1:` 配置后，服务端设备列表里本机的名字
变成了「Pixel 9」——那是分享端手机的名字；或者拿配置里的名字当了令牌所属设备。

**根因**：`DSH1` JSON 里有两个名字，语义跨端不同：`displayName` 是**电脑**的名字
（只配提示用），`deviceName` 是**分享端设备**的名字（安卓 Payload 直接搬
HostStore 的字段）。而桌面 `ConnectionProfile.deviceName` 是**本机**的名字——
配对时 `exchangePairing(code, profile.deviceName)` 上报给服务端。
按"填好 endpoint/token/deviceName/scopes"的字面意思照搬，就会拿分享端的名字
覆盖本机名。

**修法**：`data/ConnectionShare.kt` 的 `toImported()` **刻意不映射 deviceName**；
`displayName` 只进导入提示（「已导入『家里的电脑』」），不落 profile。
桌面始终用 `defaultDeviceName()` 的本机名。

**防回归**：`tools/verify-parity.sh` 的 X9 段逐字段断言 `toImported()` 的结果集合
（baseUrl / label / token / scopes / displayName / extraEndpoints），
**没有 deviceName 这一项**——谁再加回来，先回来读这条。
