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
 * 待办与轨迹**已经不在这儿了**——它们改成挂在会话标题下方（`ui/ChatShelves.kt`），
 * 因为那两样是"这一轮正在干什么"，属于正文的注脚，不是另起一列的旁证；
 * 官方也是贴着会话头放的。
 *
 * 抽屉里剩下的是**机器层面**的东西：后台任务说明它把什么挂到了后台，
 * `Server` 是桌面端多出来的——出问题时第一个要回答的是"服务端到底支不支持"，
 * 把能力位摊开比让人翻日志快。
 */
enum class ChatPanel(val label: String) {
    Jobs("任务"),
    Server("服务端"),
}
