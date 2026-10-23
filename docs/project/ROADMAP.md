# 路线图（ROADMAP）

> 口径：`done` = 已随某个产物发布；`in-progress` = 实现已完成但没有真机验收；
> `planned` = 未开始。
> **「未真机验收」不等于「没实现」**，这类在验收条件里写明未验证项，不改状态。
> 能力面以 `PARITY.md` 为准，本表只记任务与验收条件。

---

## 一、平台与交付

| ID | 状态 | 验收条件 |
| --- | --- | --- |
| PLAT-001 | done | 单模块 Compose Desktop 工程，Kotlin 2.1.0 / JDK 17，Skia 直绘，无 WebView 依赖 |
| PLAT-002 | in-progress | UOS deb 可安装、菜单项+桌面图标+5 档图标齐全、可启动。**代码与包内容已验证，真机安装未验证**（沙箱 dpkg 不可用） |
| PLAT-003 | planned | Windows msi 在 Windows 机器上打出并安装成功（`build-windows.bat`，需 WiX 3.x） |
| PLAT-004 | planned | arm64 产物（换 skiko 原生库重打） |

## 二、底座

| ID | 状态 | 验收条件 |
| --- | --- | --- |
| CORE-001 | done | 健康检查、配对码换令牌、令牌 AES-GCM 落盘、启动自动重连 |
| CORE-002 | done | 设备身份、scope、授权根完整（配对不带 rootIds，由 `devices/self` 补齐） |
| CORE-003 | done | 网络边界单点化：`DshWorkspaceClient` + `ApiExt`，UI 无 OkHttp |
| CORE-004 | planned | 服务端能力位探测（`connection.ready` 的 `bridgeVersion` + 能力位），按能力降级而不是按版本号猜 |
| CORE-005 | planned | 事件断档检测（`seq` 跳号 / 本地落后基线时自动重取历史） |

## 三、会话与聊天

| ID | 状态 | 验收条件 |
| --- | --- | --- |
| CHAT-001 | done | 会话列表、搜索、新建（选工作区）、重命名、归档 |
| CHAT-002 | done | 历史投影：正文/思考/工具/提示四类，`step/*`、`job/*`、token 序列不渲染 |
| CHAT-003 | done | 历史解包 `{type:"event", event:{…}}`；真实会话 305 → 58 条可渲染 |
| CHAT-004 | done | 历史分页（`hasMore`/`beforeSeq`），顶部「加载更早」 |
| CHAT-005 | done | 相邻工具调用合并成组，可展开看参数与输出 |
| CHAT-006 | done | 发送、停止、流式正文与思考分栏 |
| CHAT-007 | in-progress | 插话（steer）：运行中发送走 `mode: steer`，按钮文案变「插话」。**真机交互未验证** |
| CHAT-008 | planned | 附件消息（图片 ≤8 MiB 内联、文件 ≤24 MiB 走 receiptId） |
| CHAT-009 | in-progress | 分叉：`POST …/fork`，会话行 `⋯` 菜单入口；**从指定 `atSeq` 分叉的 UI 未做**（当前用服务端默认） |
| CHAT-010 | in-progress | 已归档会话列表（`archived=true`）并可打开；**「取消归档」无端点，未做** |
| CHAT-011 | in-progress | 斜杠命令：`/` 列候选、选中即填或即执行。**`@` 路径补全未做** |
| CHAT-012 | planned | 当前 TODO / 任务模块（读历史 `projections.values.todos`） |
| CHAT-013 | in-progress | 会话统计已做（轮次/工具次数/失败数/思考段/产出数，消息区顶部一行）；**轨迹（按轮次时间轴）未做** |
| CHAT-014 | in-progress | 工具专用块：read 路径头、bash `$ cmd`+剥 ANSI 输出、edit/write `-`/`+` 对照；真实数据命中 30/37、21/24、40/42。**行号列未单独渲染**（内核已带 `N: ` 前缀，直接用） |
| CHAT-015 | in-progress | 交付物卡片（文件名 + 描述，点击经 `roots/resolve` 定位到文件页） |
| CHAT-016 | in-progress | 按轮折叠 TurnRun（「已完成，用时 X ⌄」）+ 用户气泡时间/复制/引用 + 统计居中 + 消息区细滚动条。**待真机验收** |
| CHAT-017 | in-progress | 正文 Markdown 图片（`MdBlock.Image` + `PathImage()`，http/未知/行内图降级链接）；自由选择复制 + 上下文注入显示开关（设置，默认关）。**待真机验收** |
| CHAT-018 | in-progress | 文件链接应用内定位 + 行号后缀（`:24`/`#L24`）+ `SourcePane` 按行高亮；刷新历史增量合并（`mergeLatestPage`）不抹更早页。**待真机验收** |

## 四、文件

| ID | 状态 | 验收条件 |
| --- | --- | --- |
| FILE-001 | done | 授权根、目录分页、文本预览（限 512 KiB） |
| FILE-002 | done | 新建文件夹/空文件、上传、删除、回收站恢复 |
| FILE-003 | planned | 文本编辑 + ETag 保存（冲突提示） |
| FILE-004 | planned | 移动 / 重命名 |
| FILE-005 | planned | 下载到本地 |
| FILE-006 | planned | Markdown 源码/渲染切换 |

## 五、设置

| ID | 状态 | 验收条件 |
| --- | --- | --- |
| SET-001 | done | 连接信息、设备名、scope 中文映射、主题切换、关于 |
| SET-002 | planned | Provider / 凭据 / 模型管理（对齐安卓端「模型」页） |
| SET-003 | planned | 会话权限预设切换（含完整访问二次确认） |
| SET-004 | planned | 推理等级（思考强度）选择 |
| SET-005 | planned | Agent Preset 选择（空白会话可切） |

## 六、工程

| ID | 状态 | 验收条件 |
| --- | --- | --- |
| ENG-001 | done | `tools/build.sh` / `package.sh` 可复现出包；桌面入口自动补齐并自检 |
| ENG-002 | done | `tools/docs-check.sh` 机检版本号、PARITY 统计、坑位同名、行数预算、代码地图双向一致 |
| ENG-003 | done | `tools/verify-history.sh` 用真实响应验证解析（防"整页空白"回归） |
| ENG-004 | planned | 引入最小 JVM 单元测试（需先解决 Gradle 在受限环境的启动问题，或在 CI 跑） |
| ENG-005 | in-progress | `parseHistory` / `toolBlockView` / `chatStats` 已是纯函数；`AppState` 只做了按域拆分（`AppStateChat` / `AppStateFiles`），尚未可测 |
| ENG-006 | planned | GitHub Actions：编译 + 解析验证 + docs-check（不出 msi/deb） |
