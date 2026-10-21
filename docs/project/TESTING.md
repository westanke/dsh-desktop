# 测试记录（TESTING）

> 本项目**没有单元测试框架**（见 `TECH-DEBT.md` DEBT-001）。这里记录的是替代门禁：
> 编译、文档、解析验证、真机冒烟。**编译绿 ≠ 界面对**。

## 门禁清单（按顺序跑）

| # | 命令 | 判据 |
| --- | --- | --- |
| 1 | `tools/build.sh` | 退出码 0，输出「编译完成」 |
| 2 | `tools/docs-check.sh` | 全绿；任何一项 FAIL 都不算完成 |
| 3 | `tools/verify-history.sh <history.json>` | 事件数 > 0 时，可渲染条目数也必须 > 0 |
| 4 | 真机装 deb | 菜单有项、桌面有图标、会话能看到消息 |

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
