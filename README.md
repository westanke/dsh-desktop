# DSH Desktop

连接 **DSH（DeepSeek Harness）** 的原生桌面客户端，支持 **UOS / Deepin（Linux x64）** 与 **Windows x64**。

界面由 Kotlin 的 Compose 运行时通过 **Skia 直接绘制**，不加载任何 HTML、不嵌入 WebView、不用 Electron / Tauri 套壳。网络侧只与 `dsh-remote-bridge` 暴露的公开 REST + WebSocket 接口通信——它跟手机端、网页端是同一套协议，只是换了一条渲染管线。

底层协议实现复用仓库自带的 `kotlin-sdk`（`DshWorkspaceClient`，AGPL-3.0），在此基础上补齐了会话归档、路径解析、提问应答、文件上传等端点。

---

## 1. 能做什么

| 模块 | 能力 |
| --- | --- |
| 连接 | 一次性配对码换设备令牌；令牌本地加密保存，下次启动自动重连；连接状态实时可见 |
| 会话 | 列表与搜索、新建（选工作区）、归档、**已归档列表**、**分叉**、重命名、模型切换、发送 / 停止、历史回放（可往回翻页） |
| 消息 | 流式正文与思考分栏、**Markdown 完整渲染**（表格 / 分级标题 / 任务列表 / 引用 / 分隔线 / 可点链接）、围栏代码块（语言标签 + 一键复制）、**工具专用块**（read 路径头 / bash `$ 命令` / edit `-`+` 对照 / 其余通用折叠）、**工具耗时**、**交付物卡片**、**会话统计一行**、`turn/end` 失败原因提示 |
| 审批 | 需要授权时在会话内列出**每一条**待批（工具名、风险等级、理由、详情），可直接**允许一次 / 拒绝** |
| 文件 | 授权根浏览、目录翻页、**按类型分流预览**（Markdown 自己渲染成文档 · 图片直接显示 · 源码等宽 · PDF/Office/压缩包交系统程序打开）、新建文件夹 / 空文件、上传、下载、编辑（ETag 防覆盖）、移动、删除、回收站 |
| 交互 | `Enter` 发送 / `Shift+Enter` 换行、**运行中发送即插话**、`/` 命令候选、`Ctrl+1/2/3` 切页、**记住上次停留的页** |
| 外观 | **5 套主题**（暖砂 / 冷瓷 / 苔原 / 深海 / 暮紫）＋跟随系统；配色以用途命名、按 WCAG AA 校对比度 |

---

## 2. 目录结构

```
dsh-desktop/
├── src/main/kotlin/io/github/westanke/dshdesktop/
│   ├── Main.kt                 入口：临时目录探测、窗口、全局快捷键
│   ├── AppState.kt             状态机本体：连接、会话列表、历史分页、事件合并
│   ├── AppStateChat.kt         聊天域低频动作：分叉、斜杠命令、提问应答、统计
│   ├── AppStateFiles.kt        文件域动作：浏览、预览、上传、删除、回收站、路径定位
│   ├── data/
│   │   ├── DshApi.kt           协议客户端（源自 kotlin-sdk，补了 internal 可见性）
│   │   ├── ApiExt.kt           补齐 SDK 缺的端点：归档列表、路径解析、提问、上传、历史分页
│   │   ├── AppStorage.kt       配置目录 + 令牌 AES-GCM 加密落盘
│   │   ├── ChatHistory.kt      历史事件 → 可渲染条目（工具配对、耗时、交付物、统计）
│   │   ├── Markdown.kt         Markdown 解析（纯函数：GFM 管道表、行内标记、任务列表）
│   │   └── ToolBlockView.kt    工具名 → 专用视图分发（read/终端/差异/通用，纯函数）
│   └── ui/
│       ├── Theme.kt            令牌 + 5 套主题预设（暖砂/冷瓷/苔原/深海/暮紫）与对比度校色
│       ├── Components.kt       通用零件：卡片、空态、状态点、加载块、外链
│       ├── MarkdownView.kt     Markdown 渲染：表格、标题分级、列表、引用、代码块复制
│       ├── App.kt              主壳：68dp 导航栏 + 内容区 + Toast
│       ├── ConnectScreen.kt    地址与配对码
│       ├── ChatScreen.kt       会话页：面包屑头 / 居中消息流 / 装配
│       ├── Sidebar.kt          侧栏：品牌、新会话、导航、会话列表（相对时间 + 菜单）、身份卡
│       ├── Composer.kt         输入区：发送 / 插话 / 命令候选
│       ├── ToolBlocks.kt       工具专用块渲染
│       ├── ChatPanels.kt       审批卡片、交付物卡片、统计行
│       ├── FilesScreen.kt      授权根 / 文件列表 / 预览
│       └── SettingsScreen.kt   连接信息、设备权限、主题、关于
├── lib/                        运行时依赖（50 个 jar，含 Compose 与 skiko）
├── tools/
│   ├── build.sh                编译（直调 K2JVMCompiler，按平台筛 skiko 运行时）
│   ├── package.sh              编译 + 打 jar + jlink 运行时 + jpackage → deb / zip（LITE=1 出轻量包）
│   ├── build-runtime.sh        运行时镜像 → tar.xz + sha256（轻量包首次启动下载它）
│   ├── slim-icons.py           material-icons-extended 瘦身（36 MB → 0.05 MB）
│   ├── launcher/               启动引导器（自带 runtime 优先，否则国内优先下载）
│   ├── fetch-platform.sh       按需拉取目标平台的 skiko 原生库
│   ├── docs-check.sh           文档一致性门禁（28 项）
│   ├── verify-markdown.sh      Markdown 解析用例（表格形状断言）
│   ├── verify-files.sh         文件类型判定与解码用例
│   ├── fetch-deps.py           按坐标补传递依赖
│   ├── mock-server.py          本地模拟服务端（端口 3091），用于离线联调
│   ├── portable/run.sh         免安装包启动脚本（打包时复制到包根目录）
│   └── platform-libs/          按平台下载的 skiko 原生库（不入库）
├── .github/workflows/          CI / 发布 / 镜像同步（见第 10 节）
├── version.txt                 版本号唯一来源
├── build-windows.bat           Windows 构建入口（bash 脚本的薄封装）
├── LICENSE / NOTICE            AGPL-3.0 与第三方组件声明
└── dist/                       产物
```

---

## 3. 安装与运行

### 3.0 两份包，按网络情况选

| 包 | 体积 | 适合谁 |
| --- | --- | --- |
| `dsh-desktop_<版本>_<架构>.deb`（完整） | ~68 MB | 默认选择。自带运行时，装完**完全不联网**也能用 |
| `dsh-desktop-lite_<版本>_<架构>.deb`（轻量） | ~40 MB | 网络稳定、想少下一半的用户。**首次启动**会从国内源拉一份 29 MB 运行时 |

轻量包第一次启动时按 **Gitee → GitHub** 的顺序拉运行时（国内 Gitee 有 CDN），
下完校验 sha256 再解压到 `~/.local/share/dsh-desktop/`，之后不再联网。
顺序可用 `DSH_RUNTIME_MIRROR=github` 对调；完全离线时
`DSH_RUNTIME_TARBALL=<本地 tar.xz>` 或 `DSH_RUNTIME_HOME=<已解压目录>` 直接喂给它。
Windows 侧同理（`dsh-desktop.bat` 引导器，用系统自带 curl + tar）。

**首次启动失败会弹窗**（不再"点了没反应"）：`.desktop` 是 `Terminal=false`，
没有终端可看，所以失败一律走弹窗 + 日志：

```bash
cat ~/.cache/dsh-desktop/launcher.log      # 每次启动都记：版本、APP_HOME、选中的 java、失败原因
DSH_LAUNCHER_DRY_RUN=1 /opt/dsh-desktop/bin/dsh-desktop   # 只算不启动：看清楚用的是哪个 java
```

Windows 日志在 `%LOCALAPPDATA%\dsh-desktop\launcher.log`（双击启动时控制台一闪而过，
失败会 `pause` 停住）。
最常见的失败是 **发行版里没有对应版本的运行时资产**（404）——轻包的运行时资产名带版本号，
必须和包本身成对发布；碰到就按上面的 `DSH_RUNTIME_*` 手动兜底，或换完整包。

### 3.1 UOS / Deepin：安装 deb（推荐）

```bash
sudo dpkg -i dist/dsh-desktop_1.0.2-1_amd64.deb
# 依赖不全时
sudo apt-get -f install
```

- 自带精简 JRE 17，目标机器无需另装 Java。
- 菜单里会出现「DSH Desktop」（分组 Development），带桌面快捷方式。
- 安装路径 `/opt/dsh-desktop`，数据在用户目录（见第 6 节）。

uninstall：

```bash
sudo dpkg -r dsh-desktop
```

#### 装完之后图标在哪

| 位置 | 路径 | 归属 |
| --- | --- | --- |
| 开始菜单 / 启动器 | `/usr/share/applications/dsh-desktop.desktop` | 包内自带 |
| 图标主题 | `/usr/share/icons/hicolor/{32,48,64,128,256}/apps/dsh-desktop.png` | 包内自带 |
| 桌面 | `~/Desktop/dsh-desktop.desktop`（或 `~/桌面/`） | `postinst` 按用户创建 |

桌面图标是 UOS 上**独立的一份拷贝**，不在包内（不能替所有用户做主）。`postinst` 会在
`/home/*` 的 `Desktop` / `桌面` 目录里各放一份，并标 `metadata::trusted`。
安装那一刻如果还没登录图形会话，`~/Desktop` 不存在，这次会落空——
应用**首次启动**会再补一次（只补一次，删掉不会反复长回来）。

> **从 1.0.0 早期包升级**：早期包的 `.desktop` 被 jpackage 落在 `/opt/dsh-desktop/lib/` 下，
> 没进 `/usr/share/applications`，所以菜单和桌面都没有。直接 `sudo dpkg -i` 覆盖安装新版即可。

### 3.2 免安装包（需本机有 JDK 17+）

```bash
mkdir -p ~/apps/dsh-desktop
tar -xzf dist/dsh-desktop-1.0.2-portable-linux-x64.tar.gz -C ~/apps/dsh-desktop
~/apps/dsh-desktop/run.sh
```

包内是扁平的：`run.sh`、`dsh-desktop.jar`、`lib/`（52 项，约 74 MB），一定要解到独立目录里再跑。

`run.sh` 优先用 `$JAVA_HOME/bin/java`，没有就退回 `PATH` 里的 `java`。

### 3.3 从源码构建（Linux / UOS）

```bash
./tools/build.sh          # 只编译到 build/classes
./tools/build.sh jar      # 再打 build/dsh-desktop.jar
./tools/build.sh run      # 编译后直接开窗口
./tools/package.sh        # 全流程 → dist/dsh-desktop_1.0.1-1_amd64.deb
```

arm64 机器上（或想交叉准备运行时）先拉一次对应平台的原生库，再打包：

```bash
./tools/fetch-platform.sh linux-arm64   # → dist/dsh-desktop_1.0.2-1_arm64.deb
```

> 本项目**不用 Gradle**：`tools/build.sh` 直接调 `K2JVMCompiler`，并把 Compose / 序列化编译器插件通过 `-Xplugin` 挂上。原因见 `AGENTS.md` 第 2 节——受限环境里 Gradle 在配置阶段就会耗尽地址空间，而且没有包管理器时"仓库里有什么就编译出什么"更可复现。

前置：JDK 17+、Python 3（打 jar）、`curl`（缺依赖时自动下载）。

### 3.4 Windows：构建免安装包

在 **Windows 机器**上执行（Linux 无法交叉产出 Windows 包）：

```bat
build-windows.bat            rem 编译 + 打包
build-windows.bat run        rem 编译后直接启动
```

前置条件：

- JDK 17+，`JAVA_HOME` 已设置（或 `java` 在 `PATH` 里）
- **Git for Windows**（提供 `bash`、`cygpath`、`curl`）——编译与打包逻辑与 UOS 共用同一份 bash 脚本，不维护第二套
- Python 3（命令行名 `python` / `py` 均可）
- WiX Toolset 3.x（可选，只有想出 msi 才需要）

产物：`dist\dsh-desktop-1.0.2-windows-x64.zip`（有 WiX 时另出同名 msi）。

---

## 4. 连接 DSH

1. 在 DSH 里打开「手机设置 → 远程访问」，生成一次性配对码（有效期很短，只存 SHA-256 哈希，无法反查）。
2. 启动客户端，填两项：
   - **服务器地址**：`http://<主机>:3090`，只填 `host:3090` 也行，客户端会自动补 `http://` 与 `/api/v1`。
   - **配对码**：6 位数字或字母码。
3. 点「连接」。成功后令牌加密落盘，下次启动自动重连。

批量发机 / 联调可以预置参数，跳过手工输入：

```bash
java -Ddsh.endpoint=host:3090 -Ddsh.paircode=123456 -cp ... io.github.westanke.dshdesktop.MainKt
```

### 离线自测

没连上真机时可以用自带 mock 服务端，端口 **3091**（避开被真实 DSH 占用的 3090）：

```bash
python3 tools/mock-server.py &
# 然后在客户端填 http://127.0.0.1:3091，配对码随便填
```

---

## 5. 协议对照

基址 `/api/v1`，Bearer 设备令牌，事件走 `/api/v1/events`。字段定义以 `dsh-remote-bridge` 的 `docs/api/openapi.yaml`、`docs/api/asyncapi.yaml`（v2.5.0）为准。

| 用途 | 端点 |
| --- | --- |
| 健康检查 | `GET /healthz`（免鉴权） |
| 配对 | `POST /pairings/exchange`（免鉴权） |
| 当前设备 | `GET /devices/self` |
| 授权根 | `GET /roots` |
| 目录列表 | `GET /roots/{rootId}/entries` |
| 路径解析 | `GET /roots/resolve?path=...` |
| 文件读写 | `GET/PUT /roots/{rootId}/content` |
| 上传 / 新建 / 删除 | `POST /roots/{rootId}/entries`、`DELETE`、`POST /trash/{id}/restore` |
| 会话 | `GET/POST /chat/sessions`、`POST .../archive` |
| 消息 | `GET/POST /chat/sessions/{id}/messages` |
| 运行控制 | `POST /chat/runs/{sessionId}/cancel` |
| 审批 | `GET .../approvals`、`POST .../approvals/{id}/decision` |
| 提问应答 | `GET/POST .../questions` |
| 模型目录 | `GET /settings/providers` |

订阅的关键事件：`connection.ready`、`chat.message.delta`（`kind: text|reasoning`，无 `seq`）、`chat.message.committed`、`chat.session.status`、`chat.turn.failed`、`chat.approval.requested/resolved`、`file.changed`。

---

## 6. 配置与数据

| 平台 | 目录 |
| --- | --- |
| UOS / Linux | `$XDG_CONFIG_HOME/dsh-desktop`，缺省 `~/.config/dsh-desktop` |
| Windows | `%APPDATA%\dsh-desktop` |

| 文件 | 内容 | 权限 |
| --- | --- | --- |
| `profile.json` | 端点、设备名、deviceId、scopes、rootIds | 0600 |
| `token.enc` | 设备令牌，AES-GCM 加密（12 字节 nonce + 密文，Base64） | 0600 |
| `device.key` | 32 字节 AES 密钥 | 0600 |
| `prefs.json` | 主题、语言、上次会话、上次页面 | 0600 |

Windows 没有 POSIX 权限位，`restrict()` 静默跳过，依赖用户目录自身的 ACL。

退出登录（设置页）会删除 `token.enc` 并断开连接，`profile.json` 保留，方便下次只补配对码。

---

## 7. 安全边界（务必先读）

- **AES-GCM 不是硬件密钥库。** 能读该用户主目录的程序，同样能读到 `device.key` 并解密令牌。它的作用是让令牌不以明文出现在配置文件和备份里，挡住「随手一看」。威胁模型更高时，请把端点放到 HTTPS 或 Tailscale 后面，并缩短令牌有效期。
- **默认走 HTTP。** 生产环境请在前面加 TLS 反代，或只在可信内网 / VPN 里用。
- **授权根即边界。** 客户端只能看到、只能改 DSH 授予的 root，权限由服务端二次校验，客户端不自己放行。
- **上传与写入不校验类型。** 服务端允许什么就传什么，不要用它往系统目录写东西。
- 客户端不含遥测，不向外发任何数据；`lib/` 里的依赖为 Compose / Kotlin / OkHttp / skiko。

---

## 8. 已知限制

1. **跨平台产物只能在本平台构建。** `jpackage` 不支持交叉出包：msi 要在 Windows 上打，arm64 deb 要在 arm64 机器上打。这两件事现在都由 `release.yml` 在 GitHub 的对应 runner 上完成，本机只负责 x64。
2. **本沙箱环境的特殊限制不影响真实用户机。** 开发容器把单进程虚拟地址空间压到 4GB，`/tmp` 只有 10 MB（tmpfs）、`/root` 不可写、`/var/log` 与 `/usr/share` 只读（连 `dpkg -i` 都跑不了），所以走了 `tools/build.sh` 直调编译器和 `ensureWritableDirs()` 双目录回退（skiko 原生库目录 + tmpdir）。普通桌面机上这些兜底逻辑不会被触发。
3. **文本预览限 512 KiB，图片限 8 MB。** 文本超过上限只显示前一段（且禁止编辑，免得改一个字把后文截掉）；图片超过上限不给内嵌，改走系统程序。
4. **能自己画的自己画，画不了的交给系统。** Markdown 用内置渲染器（与聊天里同一套）、图片用 Skia 解码、其余格式下载到缓存目录后用系统默认程序打开（Windows `rundll32`/`start`，UOS `xdg-open`/`gio open`，兜底 `Desktop.open`）。
5. **审批只支持「允许一次 / 拒绝」。** 「总是允许」这类持久化授权没做（服务端只有 `allowed-once|rejected` 两种决定）。
6. **架构支持 x64 与 arm64。** arm64 的 deb 由 CI 在 arm64 runner 上原生构建（`tools/fetch-platform.sh linux-arm64` 换掉 skiko 原生库）；本机开发与自测只覆盖 x64。

---

## 9. 功能对等

与安卓端（`dsh-companion` v2.2.44）的能力差距、已实现项、下一轮顺序，见 [`docs/project/PARITY.md`](docs/project/PARITY.md)。
当前：✓ 40 项 / △ 0 项 / ✗ 0 项。

---

## 10. 仓库、CI 与发布

### 10.1 三个远端，一个主源

照 `dsh-companion` 的做法：**Gitee 是主源**（国内推拉都顺），GitHub 是从站，AtomGit 是备源。

```bash
git remote -v
# gitee    https://gitee.com/westanke/dsh-desktop.git     ← 主源，日常推这里
# origin   https://github.com/westanke/dsh-desktop.git    ← 从站，CI 跑在这里
# atomgit  https://atomgit.com/westanke163/dsh-desktop.git ← 备源（可选）
```

GitHub 的 `mirror-sync.yml` 每 3 小时用 `git push --mirror` 把 Gitee 的 refs 单向搬过来，
所以**不需要手工往 GitHub 推代码**。它认退出码，不看输出文本。

> 该工作流需要仓库 secret `MIRROR_PAT`（带 `repo` + `workflow` scope 的 PAT）。
> `GITHUB_TOKEN` 按 GitHub 安全设计不能增改 workflow 文件，而 `--mirror` 会写 `.github/workflows/*.yml`。

### 10.2 三套工作流

| 文件 | 触发 | 干什么 |
| --- | --- | --- |
| `.github/workflows/ci.yml` | push 到 `main`、PR、手工 | 编译 + 文档门禁 + 两组纯函数用例（linux-x64），5 分钟内给结论 |
| `.github/workflows/release.yml` | push tag `v*`、手工 | 三平台现场构建，每个平台出**完整包 + 轻量包 + 运行时压缩包**：`ubuntu-22.04` 出 amd64 deb、`ubuntu-22.04-arm` 出 arm64 deb、`windows-latest` 出 zip + msi，tag 时自动建 Release 并附 `SHA256SUMS`。v1.0.0 实测：deb 91 MB → 瘦身后 68 MB，轻量 deb 40 MB，zip 96 MB（CI 侧图标同样瘦身） |
| `.github/workflows/mirror-sync.yml` | 每 3 小时、手工 | ① Gitee → AtomGit → GitHub 单向镜像 refs；② 把 GitHub 最新 Release 搬成 Gitee 发行版 |

手工触发 `release.yml` 只构建、不发布，用来在打 tag 前先验一遍三平台。

### 10.2.1 主源上的下载页

产物由 GitHub 的机器构建，Release 长在 GitHub 上；而 `push --mirror` **只搬 refs、不搬 Release**。
不做处理的话，国内用户打开 Gitee 仓库会看到"代码齐全、没有可下载的东西"。
所以 Release 有一条反方向的同步路径：

```bash
# 发版当场：release.yml 的 publish job 拿到三平台产物后立刻搬回 Gitee
python3 scripts/ci/sync-release-to-gitee.py --tag "$TAG" --local dist
# 漏了的话：mirror-sync.yml 每 3 小时从 GitHub 拉一份补上
python3 scripts/ci/sync-release-to-gitee.py --tag v1.0.0
```

同名附件跳过（幂等），缺 secret `GITEE_TOKEN` 时只警告不判红。

### 10.3 发版

```bash
# 1. 改 version.txt（唯一来源），同步 CHANGELOG-DEV / README / STATUS
# 2. 门禁与打包自测
tools/docs-check.sh && tools/package.sh
# 3. 提交 → 打 tag → 推主源
git commit -am "chore: bump to 1.0.2"
git tag -a v1.0.2 -m "DSH Desktop 1.0.2"
git push gitee main --tags
# 4. mirror-sync 把 tag 搬到 GitHub，release.yml 随即构建并发布
gh run watch --repo westanke/dsh-desktop
```

### 10.4 为什么 `lib/` 入库

项目没有包管理器，classpath 就是 `lib/` 下的 jar——仓库里是什么，就编译出什么。
唯一的例外是 skiko 的分平台原生库：体积大且平台相关，`linux-x64` 那份随库入库（开发机就是它），
其余平台由 `tools/fetch-platform.sh` 在需要时下载。

---

## 11. 许可

本项目为 `dsh-remote-bridge` 的桌面客户端，复用了其 `kotlin-sdk`（AGPL-3.0）。分发时须遵守 AGPL-3.0：提供对应源码，并保留原始版权声明。第三方依赖清单见 `NOTICE`。
