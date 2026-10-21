package io.github.westanke.dshdesktop

/**
 * 三个口径固定的枚举：连接态、导航页、会话抽屉栏目。
 *
 * 单独一个文件是为了把 `AppState.kt` 压在 800 行预算以内（TECH-DEBT DEBT-003）。
 * 它们本身没有逻辑，放在哪都不影响可读性；而状态机那块再挤就没法读了。
 */

enum class ConnectionState { Disconnected, Connecting, Connected, Failed }

enum class Nav { Chat, Files, Models, Plugins, Settings }

/**
 * 会话页右侧抽屉的栏目。
 *
 * 三样东西都是"看现场"的：待办说明这一轮在干什么，轨迹说明这一轮怎么干过来的，
 * 任务说明它把哪些东西挂到了后台。安卓端是三个底部弹层，桌面端竖屏空间够，
 * 做成同一列抽屉切着看，比反复开关弹层顺手。
 *
 * `Server` 是桌面端多出来的第四栏：出问题时第一个要回答的是"服务端到底支不支持"，
 * 把能力位摊开比让人翻日志快。
 */
enum class ChatPanel(val label: String) {
    Todos("待办"),
    Trace("轨迹"),
    Jobs("任务"),
    Server("服务端"),
}
