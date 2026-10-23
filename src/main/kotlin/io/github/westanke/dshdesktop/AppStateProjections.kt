package io.github.westanke.dshdesktop

import io.github.westanke.dshdesktop.data.ChatWorkspace
import io.github.westanke.dshdesktop.data.HistoryPage
import io.github.westanke.dshdesktop.data.MAX_ATTACHMENT_BYTES
import io.github.westanke.dshdesktop.data.PendingAttachment
import io.github.westanke.dshdesktop.data.PromptPart
import io.github.westanke.dshdesktop.data.buildTrace
import io.github.westanke.dshdesktop.data.guessMediaType
import io.github.westanke.dshdesktop.data.parsePermission
import io.github.westanke.dshdesktop.data.parseTodos
import io.github.westanke.dshdesktop.data.sendMessageParts
import io.github.westanke.dshdesktop.data.uploadSessionAttachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.util.UUID

/**
 * 会话内能力：投影（待办 / 权限）、轨迹、Agent Preset、断档检测、附件、后台任务、工作区管理。
 *
 * 全部做成 `AppState` 的同包扩展，理由与 `AppStateFiles.kt` 相同：800 行预算 + Kotlin
 * 没有 partial class。状态字段本身仍在 `AppState` 里（扩展属性存不住 Compose 状态）。
 *
 * 这里有一条贯穿始终的取舍：**能力位缺了就明说，不假装"本来就没有"**。
 * 安卓端踩过两次"审批卡片不弹，看起来和没有待审批一模一样"的坑，
 * 桌面端每处探测不到能力的地方都要给出可见的解释（见 [AppState.lacksCapability]）。
 */

// ── 投影 ────────────────────────────────────────────────────────────────────

/** 换会话时清空一切按会话算出来的东西。留着上一条的待办比空着更误导。 */
internal fun AppState.clearProjections() {
    todoItems = emptyList()
    permissionState = null
    trace = emptyList()
    lastSeenSeq = null
    baselineCursor = null
    lastProjections = null
    seqGap = 0
}

/**
 * 历史加载完后同步投影。
 *
 * 权限这一项要两份数据：会话投影里的**当前值** + 目录接口的**候选项**。
 * 目录是全局的、只取一次，可能比历史晚到 —— 所以目录到货时会用
 * [AppState.lastProjections] 重算一遍，而不是留一个"档位未知"的胶囊在那。
 */
internal fun AppState.applyProjections(sessionId: String, page: HistoryPage) {
    if (selectedSessionId != sessionId) return
    lastProjections = page.projections
    todoItems = parseTodos(page.projections)
    permissionState = parsePermission(page.projections, permissionCatalog)
    trace = buildTrace(page.rawEvents)
    lastSeenSeq = page.maxSeq
    // 历史刚拉完，本地最大 seq 与订阅基线之间的洞就是这一刻的真实缺口
    seqGap = gapWith(baselineCursor)
    if (permissionCatalog == null) loadPermissionCatalog()
}

/** 本地历史落后基线多少条；没有基线或没有本地 seq 时算 0（不知道 ≠ 有问题）。 */
private fun AppState.gapWith(cursor: Int?): Int {
    if (cursor == null) return 0
    val local = lastSeenSeq ?: return cursor
    return (cursor - local).coerceAtLeast(0)
}

/** 收到订阅基线：返回 true 表示本地落后，调用方应重取历史。 */
internal fun AppState.noteBaseline(cursor: Int?): Boolean {
    if (cursor == null) return false
    baselineCursor = cursor
    val gap = gapWith(cursor)
    seqGap = gap
    // 本地一条都没有也算落后：服务端说有内容而这边是空的，正是最该立刻拉一次的时候
    return lastSeenSeq == null || gap > 0
}

/** 用户看到"漏了一段"之后点重取。 */
fun AppState.resyncHistory() {
    val sessionId = selectedSessionId ?: return
    seqGap = 0
    lastSeenSeq = null
    loadHistory(sessionId)
}

// ── 能力位 ──────────────────────────────────────────────────────────────────

internal fun AppState.adoptCapabilities(data: JsonObject) {
    val list = (data["capabilities"] as? kotlinx.serialization.json.JsonArray)
        ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        .orEmpty()
    if (list.isNotEmpty()) capabilities = list.toSet()
    bridgeVersion = (data["bridgeVersion"] as? JsonPrimitive)?.contentOrNull
}

fun AppState.hasCapability(name: String): Boolean = name in capabilities

/**
 * 服务端缺这项能力时的统一说法。
 *
 * `capabilities` 为空表示"还没收到 `connection.ready`"，那是未知、不是不支持 ——
 * 这两种情况必须分开说，否则老服务端会被冤枉，新服务端会被当成坏的。
 */
fun AppState.lacksCapability(name: String, what: String): String? = when {
    capabilities.isEmpty() -> "还没收到服务端能力清单，$what 暂不可用"
    name !in capabilities -> "服务端不支持 $what（缺能力位 $name），请升级 bridge 插件"
    else -> null
}

// ── 权限档位 ────────────────────────────────────────────────────────────────

fun AppState.loadPermissionCatalog() {
    scope.launch {
        runCatching { withContext(Dispatchers.IO) { client().listPermissionPresets() } }
            .onSuccess { catalog ->
                permissionCatalog = catalog
                // 目录晚到时用上一份历史里的投影重算当前档位
                permissionState = parsePermission(lastProjections, catalog)
            }
            .onFailure {
                // 拿不到目录不影响发消息；当前档位仍能从投影读出来，只是列不出可选项
            }
    }
}

/**
 * 切权限档位。
 *
 * 走斜杠命令 `/permission <preset>` 而不是 PUT：内核把权限做成**会话级命令**，
 * 桥接没有单独的权限写入端点（`dsh-remote-bridge` 里也只有 `chat/permission-presets` 这个只读目录）。
 * 安卓端同样是发命令，两个客户端切档位的行为必须一致。
 *
 * [sessionId] 默认打当前选中的会话；新建会话预选档位（P3）时显式传新会话的 id——
 * 那会儿它还没被选中，不能顺手把正文切成它的。
 */
fun AppState.applyPermissionPreset(preset: String, sessionId: String? = null) {
    val target = sessionId ?: selectedSessionId ?: return
    if (preset.isBlank()) return
    scope.launch {
        runCatching {
            withContext(Dispatchers.IO) { client().executeSessionCommand(target, "/permission $preset") }
        }.onSuccess {
            val label = permissionState?.options?.firstOrNull { it.value == preset }?.name ?: preset
            showToast("权限已切到 $label")
            // 投影是命令执行后由内核改的，必须重取历史才看得见新值；
            // 但非当前会话不能重取——那会把正在看的正文盖掉
            if (target == selectedSessionId) loadHistory(target)
        }.onFailure { showToast("切换失败：${AppState.human(it)}") }
    }
}

// ── Agent Preset ────────────────────────────────────────────────────────────

fun AppState.loadAgentPresets() {
    if (agentPresets.isNotEmpty()) return
    scope.launch {
        runCatching { withContext(Dispatchers.IO) { client().listAgentPresets() } }
            .onSuccess { agentPresets = it; agentPresetsError = null }
            .onFailure { agentPresetsError = AppState.human(it) }
    }
}

fun AppState.chooseAgentPreset(presetId: String) {
    val sessionId = selectedSessionId ?: return
    scope.launch {
        runCatching {
            withContext(Dispatchers.IO) { client().selectSessionAgentPreset(sessionId, presetId) }
        }.onSuccess {
            showToast("Agent 预设已切换")
            refreshSessions()
        }.onFailure { showToast(AppState.human(it)) }
    }
}

// ── 附件 ────────────────────────────────────────────────────────────────────

/**
 * 把本地文件读成待发送附件。
 *
 * 逐个读、单个失败不连坐：选了五个文件其中一个没权限，其余四个照发才是用户要的。
 * 超过 24 MB 的直接跳过并计数 —— 传完再被服务端拒，白等一趟。
 */
fun AppState.addAttachments(files: List<java.io.File>) {
    if (files.isEmpty()) return
    val tooBig = mutableListOf<String>()
    val failed = mutableListOf<String>()
    val added = mutableListOf<PendingAttachment>()
    for (file in files) {
        when {
            !file.isFile -> failed += file.name
            file.length() > MAX_ATTACHMENT_BYTES -> tooBig += file.name
            else -> runCatching { file.readBytes() }
                .onSuccess { bytes ->
                    added += PendingAttachment(
                        id = UUID.randomUUID().toString(),
                        name = file.name,
                        mediaType = guessMediaType(file.name),
                        bytes = bytes,
                    )
                }
                .onFailure { failed += file.name }
        }
    }
    if (added.isNotEmpty()) attachments = attachments + added
    val notes = buildList {
        if (tooBig.isNotEmpty()) add("${tooBig.size} 个超过 24 MB 未加入：${tooBig.joinToString("、")}")
        if (failed.isNotEmpty()) add("${failed.size} 个读取失败：${failed.joinToString("、")}")
    }
    if (notes.isNotEmpty()) showToast(notes.joinToString("；"))
}

fun AppState.removeAttachment(id: String) {
    attachments = attachments.filterNot { it.id == id }
}

/**
 * 带附件发一条消息。
 *
 * 两类附件走两条路（协议决定的，不是选择）：
 * - **图片内联** base64，进 `content[]` 的 `image`；
 * - **其他文件**先 `POST …/attachments` 换 `receiptId`，再以 `file` 引用。
 *
 * 上传是**串行**的：一个文件失败时后面的不该继续白传，而且失败要能指出是哪个。
 */
fun AppState.sendMessageWithAttachments(text: String, steer: Boolean = false) {
    val sessionId = selectedSessionId ?: return
    val pending = attachments
    if (text.isBlank() && pending.isEmpty()) return
    if (pending.isEmpty()) {
        sendMessage(text, steer)
        return
    }
    chatError = null
    scope.launch {
        val parts = mutableListOf<PromptPart>()
        if (text.isNotBlank()) parts += PromptPart.Text(text)
        val result = runCatching {
            withContext(Dispatchers.IO) {
                for (item in pending) {
                    if (item.isImage) {
                        parts += PromptPart.Image(item.mediaType, item.base64, item.name)
                    } else {
                        val receipt = client().uploadSessionAttachment(sessionId, item.base64, item.name)
                        parts += PromptPart.FileRef(receipt.receiptId)
                    }
                }
                client().sendMessageParts(sessionId, parts, UUID.randomUUID().toString(), steer)
            }
        }
        result.onSuccess {
            if (!steer) running = true
            streamingText = ""
            streamingReasoning = ""
            val shown = buildString {
                append(text)
                val names = pending.joinToString("、") { it.name }
                if (names.isNotEmpty()) {
                    if (isNotEmpty()) append("\n")
                    append("📎 ").append(names)
                }
            }
            chatItems = chatItems + io.github.westanke.dshdesktop.data.ChatItem.User(
                id = "local-${UUID.randomUUID()}",
                seq = Int.MAX_VALUE,
                text = shown,
                time = System.currentTimeMillis(),
            )
            attachments = emptyList()
        }.onFailure { chatError = AppState.human(it) }
    }
}

// ── 后台任务 ────────────────────────────────────────────────────────────────

fun AppState.refreshJobs() {
    val sessionId = selectedSessionId ?: return
    if (jobsBusy) return
    jobsBusy = true
    scope.launch {
        runCatching { withContext(Dispatchers.IO) { client().listSessionJobs(sessionId) } }
            .onSuccess { if (selectedSessionId == sessionId) jobs = it }
            .onFailure { if (selectedSessionId == sessionId) jobs = emptyList() }
        jobsBusy = false
    }
}

fun AppState.loadJobOutput(jobId: String) {
    val sessionId = selectedSessionId ?: return
    scope.launch {
        runCatching { withContext(Dispatchers.IO) { client().readJobOutput(sessionId, jobId) } }
            .onSuccess { out ->
                val note = if (out.lossy) "（前面有一段被留存策略截掉了）" else ""
                jobOutput = jobId to (out.text + note)
            }
            .onFailure { showToast(AppState.human(it)) }
    }
}

/**
 * 终止一个后台任务。
 *
 * `already-finished` **不是**"终止成功"的另一种说法：它意味着这个任务早就结束了，
 * 用户看到的是一条过期的状态。分别提示，否则会以为自己的操作起了作用。
 */
fun AppState.killJob(jobId: String) {
    val sessionId = selectedSessionId ?: return
    scope.launch {
        runCatching { withContext(Dispatchers.IO) { client().killSessionJob(sessionId, jobId) } }
            .onSuccess { result ->
                showToast(if (result.outcome == "requested") "已请求终止" else "这个任务已经结束了")
                refreshJobs()
            }
            .onFailure { showToast(AppState.human(it)) }
    }
}

// ── 工作区管理 ──────────────────────────────────────────────────────────────

fun AppState.renameWorkspace(workspaceId: String, title: String) {
    if (title.isBlank()) return
    scope.launch {
        runCatching { withContext(Dispatchers.IO) { client().renameChatWorkspace(workspaceId, title) } }
            .onSuccess { renamed: ChatWorkspace ->
                workspaces = workspaces.map { if (it.id == renamed.id) renamed else it }
                refreshSessions()
                showToast("工作区已重命名")
            }
            .onFailure { showToast(AppState.human(it)) }
    }
}

fun AppState.deleteWorkspace(workspaceId: String) {
    scope.launch {
        runCatching { withContext(Dispatchers.IO) { client().deleteChatWorkspace(workspaceId) } }
            .onSuccess {
                workspaces = workspaces.filterNot { it.id == workspaceId }
                showToast("工作区已移除（目录与文件未删除）")
            }
            .onFailure { showToast(AppState.human(it)) }
    }
}

/** 抽屉里的"漏了一段"提示：把当前历史最大 seq 与服务端基线摆在一起说清差多少。 */
fun AppState.seqGapHint(): String? {
    if (seqGap <= 0) return null
    val local = lastSeenSeq?.toString() ?: "空"
    return "本地历史停在 seq $local，服务端订阅基线在 ${baselineCursor ?: "?"} —— 中间 $seqGap 条没有拿到。"
}

/** 供界面判断"这一屏有没有待办可看"。 */
val AppState.hasTodos: Boolean get() = todoItems.isNotEmpty()
