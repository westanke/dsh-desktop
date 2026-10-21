package io.github.westanke.dshdesktop

import io.github.westanke.dshdesktop.data.ChatStats
import io.github.westanke.dshdesktop.data.answerQuestion
import io.github.westanke.dshdesktop.data.chatStats
import io.github.westanke.dshdesktop.data.listSessionQuestions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 聊天域里"不那么高频"的动作：分叉、斜杠命令、提问应答、统计。
 *
 * 和 `AppStateFiles.kt` 拆出来的理由相同（800 行预算 + 无 partial class）。
 * 区别是这里只放**新补的**能力：`refreshSessions` / `sendMessage` / 审批这类
 * 与连接状态强耦合的仍在 `AppState` 本体里，避免状态散成两处。
 */

/**
 * 从某个位置分叉出一个新会话。
 *
 * [atSeq] 为空表示"从当前这一轮之后分叉"（服务端默认）。分叉出来的会话
 * 继承到那一条之前的全部上下文，用来"这条路走歪了，回到岔口重来"。
 */
fun AppState.forkSession(sessionId: String, atSeq: Int? = null) {
    scope.launch {
        runCatching { withContext(Dispatchers.IO) { client().forkSession(sessionId, atSeq) } }
            .onSuccess { newId ->
                showToast("已从这条分叉出新会话")
                refreshSessions()
                // 分叉完直接跳过去：用户要的就是"接着说"，不是回列表里再找
                if (newId.isNotBlank()) selectSession(newId)
            }
            .onFailure { showToast(AppState.human(it)) }
    }
}

/** 取这个会话可用的斜杠命令。失败时静默清空 —— 输入 `/` 没有候选不影响发消息。 */
fun AppState.loadCommands(sessionId: String) {
    scope.launch {
        runCatching { withContext(Dispatchers.IO) { client().listSessionCommands(sessionId) } }
            .onSuccess { if (selectedSessionId == sessionId) sessionCommands = it }
            .onFailure { if (selectedSessionId == sessionId) sessionCommands = emptyList() }
    }
}

/**
 * 执行一条斜杠命令（`/compact` 之类）。
 *
 * 命令结果分两种（服务端 `result.kind`）：`text` 给一段话，其余多半是
 * "已提交/已开始"，所以统一用 toast 交代，再刷一次历史让变化落到消息区。
 */
fun AppState.runCommand(line: String) {
    val sessionId = selectedSessionId ?: return
    val trimmed = line.trim()
    if (trimmed.isEmpty()) return
    scope.launch {
        runCatching {
            withContext(Dispatchers.IO) { client().executeSessionCommand(sessionId, trimmed) }
        }.onSuccess { execution ->
            val text = execution.result.text?.takeIf { it.isNotBlank() }
            showToast(text?.lineSequence()?.first()?.take(120) ?: "命令已执行")
            loadHistory(sessionId)
        }.onFailure { showToast(AppState.human(it)) }
    }
}

/** 这个会话里还没答的提问（用于在消息区底部给出应答入口）。 */
fun AppState.refreshQuestions(sessionId: String) {
    scope.launch {
        runCatching { withContext(Dispatchers.IO) { client().listSessionQuestions(sessionId) } }
            .onFailure { }
    }
}

fun AppState.answerQuestion(
    sessionId: String,
    callId: String,
    answers: List<Triple<String, List<String>, String?>>,
) {
    scope.launch {
        runCatching { withContext(Dispatchers.IO) { client().answerQuestion(sessionId, callId, answers) } }
            .onSuccess {
                showToast("已提交回答")
                loadHistory(sessionId)
            }
            .onFailure { showToast(AppState.human(it)) }
    }
}

/**
 * 当前会话的统计。纯投影，跟着 [AppState.chatItems] 走：
 * 翻页后数字会跟着变，所以文案只说"这一屏"，不宣称是权威总数。
 */
val AppState.stats: ChatStats
    get() = chatStats(chatItems)
