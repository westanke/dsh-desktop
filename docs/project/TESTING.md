# 测试记录（TESTING）

> 本项目**没有单元测试框架**（见 `TECH-DEBT.md` DEBT-001：Gradle 在本机起不来，
> `lib/` 里也没有 junit / kotlin-test）。第四批起了**路线 A**的替代机制：
> Kotlin `main` + 手写断言，零新依赖、离线可跑（见下文「纯逻辑测试机制」）。
> 这里记录的是替代门禁：编译、文档、纯函数回归、解析验证、真机冒烟。**编译绿 ≠ 界面对**。

## 门禁清单（按顺序跑）

| # | 命令 | 判据 |
| --- | --- | --- |
| 1 | `tools/build.sh` | 退出码 0，输出「编译完成」 |
| 2 | `tools/docs-check.sh` | 全绿（32 项）；**第 8 节会实跑 `tools/test.sh`**，任何一项 FAIL 都不算完成 |
| 3 | `tools/test.sh` | 纯逻辑套件全过：`verify-files` 49 + `verify-markdown` 37 + `verify-parity` 82（离线约 30 秒） |
| 4 | `tools/verify-history.sh <history.json>` | 事件数 > 0 时，可渲染条目数也必须 > 0 |
| 5 | 真机装 deb | 菜单有项、桌面有图标、会话能看到消息 |

## 纯逻辑测试机制（第四批 · 路线 A，2026-10-23）

**为什么不是 JUnit**：引入测试框架就要引入依赖解析，与"仓库即依赖"的取舍冲突；
本机 4GB 地址空间也起不来 Gradle。于是每个 `tools/Verify*.kt` 就是一个 `main`
函数 + 手写断言，由 `tools/verify-*.sh` 用**已入库的 K2JVMCompiler** 直编直跑
（每个套件 7–8 秒）。鸿蒙那 1013 条 node 断言本质也是这种"无框架断言"。

**统一入口**：`tools/test.sh` 跑全部离线套件（约 30 秒）。传一个 `history.json`
路径，再跑 `verify-history` 与真实数据模式的 `verify-markdown`（表格形状断言
在内置用例里没有，必须真实数据才测得到）。

**断言库**：`tools/VerifySupport.kt`，四个套件共用。

- `vcheck(name, ok)` —— 简洁式，通过不打印（门禁里不刷屏）；
- `vexpect(name, ok)` —— 明细式，每条都打印（要看清覆盖了哪些形状时用）；
- `vsection(title)` / `vsummary()` —— 分节标题与收尾统计（返回失败数，退出码交给调用方）。

**新增一个纯函数回归**：写 `tools/VerifyXxx.kt`（`main` 里调断言库，直接 import
`src/main/kotlin/...` 里的纯函数）→ 加一个 `tools/verify-xxx.sh`（照抄现有脚本的
编译行，把 `VerifySupport.kt` 与新的 `.kt` 一起编）→ 往 `tools/test.sh` 的
`OFFLINE_SUITES` 加一行。**data 层纯函数禁 import Compose**，否则脚本跑不起来。

**退出码语义**：`test.sh` 0 全过 / 1 有套件失败 / 2 用法错误；各套件另有自己的
退出码（`verify-markdown` 3 输入不可用 / 4 内置用例失败，`verify-history` 2 回归），
公共库不统一它们——调用方自己判。

## 3 的用法

```bash
# 取一份真实历史（令牌解出来之后，别提交这个文件）
curl -s -H "Authorization: Bearer $TOKEN" \
  http://127.0.0.1:3090/api/v1/chat/sessions/<sessionId>/messages > /tmp/h.json

tools/verify-history.sh /tmp/h.json
```

判据只看两条：**事件数 > 0 而条目数 = 0 就是回归**（`PITFALLS.md` §历史事件有外层包装）。
分布数字用来人工比对预期——比如纯工具轮次多的会话，`Assistant` 少、`ToolGroup` 多是正常的。

---

## 2026-10-21 · 第二轮（v1.0.0 修复）

| 项 | 结果 |
| --- | --- |
| `tools/build.sh` | ✅ 467 个类 |
| `tools/docs-check.sh` | ✅（本轮新增，首次全绿） |
| `tools/verify-history.sh` | ✅ 真实会话 305 条事件 → **58 条可渲染**：Tool 15 / ToolGroup 10 / Reasoning 23 / Notice 5 / Assistant 4 / User 1 |
| deb 包内容 | ✅ `dpkg-deb -c` 含 `usr/share/applications/dsh-desktop.desktop` + 5 档 hicolor png |
| 真机 `dpkg -i` | ❌ **未做**：沙箱 `/var/log`、`/usr/share` 只读，`chown` 被禁 |
| 界面截图 | ❌ **未做**：sandbox 内 root 窗口被 KWin 拒绝（1×1），无 Xvfb |

> `verify-history.sh` 后来单独复跑过一次（会话还在增长）：**324 条事件 → 52 条可渲染**，
> 退出码 0。数字每次都会变，判据只看"有事件却一条都渲染不出"。

**修复前后对照**（同一份真实数据）：

| | 事件数 | 可渲染条目 |
| --- | --- | --- |
| 修复前 | 305 | **0**（全被 `type == "event"` 过滤掉） |
| 修复后 | 305 | 58 |

## 2026-10-21 · 第一轮（v1.0.0 从零）

| 项 | 结果 |
| --- | --- |
| `tools/build.sh` | ✅ |
| `tools/package.sh` | ✅ deb 90 MB；`--type deb` 依赖自动解析为 12 个包 |
| mock 服务端端到端 | ✅ 端口 3091，连接页/会话页/文件页/设置页截图 10 张（工作区 `shots/`） |
| 真实 DSH 联调 | ⚠️ 只做了只读接口验证（healthz / sessions / roots / devices-self），发送与流式未在真机跑 |
| 便携包启动 | ✅ |

---

## 已知测不到的

| 场景 | 原因 | 谁补 |
| --- | --- | --- |
| 装 deb / 桌面图标 | 沙箱权限 | 用户真机 |
| 界面渲染与字体 | root 窗口不被 WM 接管、无 Xvfb | 用户真机 |
| Windows msi | 无 Windows 机器 | 用户在 Windows 上跑 `build-windows.bat` |
| 高分屏缩放（125% / 150%） | 无多显示器环境 | 用户真机 |
| 长时间流式（多轮工具） | 需真实模型跑 | 用户真机 |
