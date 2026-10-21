# 技术债务台账（TECH-DEBT）

> **只许收紧**：条目对应的债务消失时，同一提交里删掉该条目（自证非空转）。
> 新增债务必须先登记再写码，尤其是超预算文件。

## 行数预算

**800 行/文件**，由 `tools/docs-check.sh` 机检。

### 超预算例外表

| 文件 | 行数 | 理由 | 清账路径 |
| --- | --- | --- | --- |
| `src/main/kotlin/io/github/westanke/dshdesktop/data/DshApi.kt` | 874 | 上游 `kotlin-sdk` 的 `DshWorkspaceClient` 原样移植，只改包名与可见性。拆开会让后续同步上游变成手工 diff | 上游若自行拆分则跟随；否则维持原样，把它当"第三方代码"看待 |

> 例外只此一条。新增例外必须在同一提交里写清理由与清账路径，否则门禁失败。

---

## 债务条目

### DEBT-001 · 没有单元测试框架

**位置**：全仓。
**为什么是债**：`parseHistory`、`prettyArguments`、`endpoint` 归一化、`human()` 错误翻译
都是纯函数，本应有回归用例。现在只能靠 `tools/verify-history.sh` 做端到端形状验证，
状态机（`AppState`）完全裸奔。
**根因**：Gradle 在本机 4GB 地址空间限制下起不来（见 `PITFALLS.md`），JVM 测试无处安放；
Gradle 脚手架已从仓库移除，也不再指望它。
**清账路径**：走「`tools/build.sh` 的编译器 classpath + 独立 main + 断言退出码」这条路，
把纯函数测试当主程序跑（`tools/verify-*.sh` 已经是这个形态）。下一步是把散落的
`Verify*.kt` 收成一个目录、让 CI 一次跑完所有用例；真正的 JUnit 仍不做（引入测试框架
就要引入依赖解析，与本项目"仓库即依赖"的取舍冲突）。
**当前替代**：`docs-check.sh`（文档/预算）+ `verify-history.sh`（解析形状）+
`verify-markdown.sh`（Markdown 解析 22 条用例 + 真实数据形状）+
`verify-files.sh`（文件类型与解码 49 条）+ CI 每 push 必跑前三项中的两项 + 真机冒烟。
**收紧记录**：`data/Markdown.kt` 是纯函数且不依赖 Compose，已能用脚本直接跑用例——
这是全仓第一块"事实上有单测"的代码，后续新纯函数照此办理。

### DEBT-002 · 没有 CI

**位置**：`.github/workflows/` 不存在。
**为什么是债**：`tools/docs-check.sh` 只能靠人记得跑，等于没有门禁。
**清账路径**：GitHub Actions 跑 `build.sh` + `docs-check.sh` + `verify-history.sh`（不出安装包）。

### DEBT-003 · AppState 仍未拆成可测的 Store

**位置**：`AppState.kt`（781 行，贴到 800 预算）+ 四个扩展文件
（`AppStateChat.kt` 96 / `AppStateFiles.kt` 262 / `AppStateProjections.kt` 351 / `AppStateSettings.kt` 205）。
**为什么是债**：**行数预算这一半已经解决** —— 文件域与聊天低频动作按域拆出去了，
`scope` / `client()` 因此改成 `internal`。但"拆"只是把代码搬到别的文件，
状态仍然是一个类里的 30 多个 `mutableStateOf`，依然无法单测（依赖 CoroutineScope 与真实客户端）。
**清账路径**：引入 `ChatStore` / `FileStore` / `ConnectionStore` 三个持有状态的类，
由 `AppState` 组合；判据是"能用一个假客户端构造出 ChatStore 并断言状态迁移"。
顺手把 `internal` 的 `scope` / `client()` 收回 `private`。
**收紧记录（2026-10-21 第六轮）**：为了给新增的会话能力腾地方，枚举（`ConnectionState` /
`Nav` / `ChatPanel`）移进 `src/Enums.kt`，`AppState.kt` 从 799 压回 781。
**下一刀应该砍状态本身**（30 多个 `mutableStateOf` 挤在一个类里），而不是继续往外搬常量。

### DEBT-005 · 大文件仍然只有前 512 KiB

**位置**：`AppStateFiles.previewEntry()` / `ui/FilesScreen.kt`。
**为什么是债**：**"只读"这一半已清** —— 现在能编辑并按 ETag 保存。剩下的债是大文件：
超过 512 KiB 只读前一段，而且**明确禁止编辑**（在截断内容上整份写回会毁掉后半段），
也没有"继续加载"或分页查看。
**清账路径**：`readFile(range)` 已经支持 Range，接一个"加载更多"把区间往外扩；
编辑大文件则要改成服务端流式写或分段 diff。

### DEBT-006 · 没有自动更新

**位置**：无。
**为什么是债**：安卓端有 `UpdateChecker`（校验 SHA-256 后引导安装），桌面端只能手动下 deb。
**清账路径**：读发布源版本号 + 下载 deb 后交给 `dpkg -i`（需用户确认）。

### DEBT-008 · UI 交互没有自动化验证手段

**位置**：全部 `ui/*`。
**为什么是债**：本沙箱的合成点击打不进 Compose 窗口（`PITFALLS.md` 的「窗口以 1×1 创建」），
于是抽屉、下拉菜单、对话框这类"点一下才出现"的界面只能靠读代码确认。
第六轮新增的会话抽屉（待办 / 轨迹 / 任务 / 服务端四栏）、权限下拉、附件托盘都在这个盲区里。
**清账路径**：两条路——① 在有窗管的会话里用 `xdotool` 补一轮交互截图；
② 给 Compose 加可编程入口（如 `--ui-smoke` 参数按脚本设状态再截图），把"展开态"变成可复现的截图。

---

## 账怎么记

新条目按 `DEBT-00N · 一句话标题` 追加，正文四段：**位置**、**为什么是债**、**根因/背景**、**清账路径**。
清掉就删整条，不留"已解决"的历史——历史在 `CHANGELOG-DEV.md` 里。
