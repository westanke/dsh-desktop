# 第四批对齐需求（工程互鉴）：纯逻辑层 + 脱离平台的轻量回归测试

- 日期：2026-10-23
- 提出方：lead（对标找差距，不写实现）
- 执行方：workbuddy
- 状态：**待评估/待实现**（本项是工程改造，先看 workbuddy 的落地方案，再定范围）
- 依据：[`../../../../对齐台账/三端功能互鉴清单.md`](../../../../对齐台账/三端功能互鉴清单.md) 第五节「跨端工程纪律互鉴」
- 本批性质：**横向互鉴**，不是功能追平。

---

## 为什么提这单

三端自动化测试家底（2026-10-23 实测）：

| 端 | 测试数 | 形态 |
|---|---|---|
| 鸿蒙 trae | **1013 条** node 断言 | 业务逻辑全沉在纯 TS `core/`，不依赖 ArkUI/设备，`cd test && npm test` 脱离真机跑 |
| 安卓 | 66 个 `.kt` 测试 | 部分纯 JVM 单测 + 部分仪器测试 |
| 桌面 workbuddy | **0** | TECH-DEBT DEBT-001，无 JUnit；目前靠 `tools/verify-*.sh` 纯函数脚本顶回归 |

桌面现状不是没防线——`verify-markdown.sh`（已内置 33 项）、`verify-files.sh`、`verify-history.sh` 是有效的纯函数断言，第一批 `mergeLatestPage` 也进了 VerifyHistory。问题是覆盖面靠 shell，**Kotlin 纯函数的回归没有同语言、可调试、可结构化断言的载体**，随着 dsh-ui 解析（第三批 X1）、@提及（X2）、统计（X5）、HTML 提取（X6）等解析逻辑增多，shell 断言会越来越难写难维护。

## 鸿蒙可抄的核心思想（不是抄技术栈）

1. **逻辑沉底、UI 只消费状态**：可测的纯逻辑不依赖任何 UI/平台类型，输入→输出、无副作用；网络/加密/文件 IO 抽接口，平台实现与测试实现分离。
   - 范例：鸿蒙 `core/HostStore.ts:43` 的 `SecretBox` 加密抽象——生产用 HUKS，单测里换成 XOR 可逆变换，于是加密编排逻辑能在 node 里测。
2. **测试脱离设备/模拟器**：毫秒级、可在 `build.sh`/docs-check 门禁里直接跑。

## 关键约束（必须先确认，别踩构建红线）

- 本仓**刻意不用 Gradle**（本机 4GB 地址空间会打死 Gradle），构建是 `tools/build.sh` 直调 K2JVMCompiler，50 个 jar 入库 `lib/`。
- 已核实 **`lib/` 里没有 junit / kotlin-test / assertj 任何测试框架 jar**。
- 所以**不要**为了测试引入 Gradle，也不要贸然往 `lib/` 塞一大套测试框架。先评估两条路线：
  - **路线 A（推荐，零新依赖）**：写一个极小的 Kotlin 断言入口（`main` 函数 + 手写 `check/assertEquals` 风格帮助函数），由 `tools/build.sh` 或新增 `tools/test.sh` 用已入库的 K2JVMCompiler 编译 `src/test/...` 并直接 `java` 运行，失败退出非零，接进 docs-check 门禁。鸿蒙的 1013 条本质也就是这种"无框架断言"。
  - **路线 B**：入库一个轻量 `kotlin-test` 或 `junit4` 单 jar（核对其传递依赖能否离线满足），同样走 build.sh 直编直跑，不走 Gradle。
- 由 workbuddy 判断哪条与现有 `verify-*.sh` 最契合；**允许保留并逐步把 shell 里成熟的断言迁过来，不要求一步到位**。

## 建议第一批盖的纯函数（优先级从高到低）

这些都是已存在/第三批将新增、纯 Kotlin、不依赖 Compose、bug 代价高的解析逻辑：

1. `data/Markdown.kt`：块/行内解析、表格分隔行判据（:134-156）、图片（第一批新增 MdBlock.Image/parseImageMarkdown）；
2. 消息合并：`mergeLatestPage`（按 seq 窗口合并不按 id）——已在 VerifyHistory，迁移并补边界；
3. `data/ChatLink.kt`：classifyChatLink + `:行号`/`#L` 后缀解析（第一批）；
4. 第三批新增：dsh-ui `splitMessageBody`/`parseGenUi`（损坏/未知组件/流式半截）、@提及 activeStart/query/applySelection（含邮箱反例）、`retryableUserText`（空正文/多轮）、字号 normalizeFontScale、会话统计计数（内核注入上下文不计轮）、HTML extractHtmlBlocks/decodeHtmlEntities（含 script 跳过/畸形）。

## 架构纪律（与 AGENTS.md 一致）

- data 层纯函数**禁 import Compose**；平台 IO（网络/加密/文件）走接口，便于测试替身；
- 遵守 800 行/文件预算，测试文件同样别失控；
- 这套机制要写进 `docs/project/TESTING.md`（当前 TESTING 文档主要讲真机/手测）与 CODEMAP，AGENTS §7 / PITFALLS 视情况补坑位。

## 验收

1. 有一条**不依赖 Gradle、不依赖真机/模拟器、离线可跑**的测试命令（建议 `bash tools/test.sh`），失败时退出码非零；
2. 至少覆盖上面第 1–3 项既有纯函数（证明机制可用），第三批 X1–X6 落地时各自带测；
3. 接进 `tools/docs-check.sh` 门禁项（新增门禁记得同步其白名单计数与文档）；
4. `TESTING.md` / CODEMAP 同步；build.sh + docs-check + 三个 verify 全绿；
5. 不推送、不打 tag、不发 Release。

## 实现回执（workbuddy 回填）

- 选定路线（A/B 及理由）：
- 测试命令与用例数：
- 提交 / 日期：
