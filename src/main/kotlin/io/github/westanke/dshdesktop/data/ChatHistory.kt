package io.github.westanke.dshdesktop.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/**
 * 会话历史 → 可渲染条目。
 *
 * 只做**投影**，不改写内容：拿不到的事件类型直接跳过，不猜测。
 * 事件结构取自桥接的 `GET /chat/sessions/{id}/messages`（`events[]`，每条带
 * `seq/type/time/data`），字段名与 dsh-companion 的原生解析口径一致。
 */
sealed interface ChatItem {
    val id: String
    val seq: Int

    data class User(
        override val id: String,
        override val seq: Int,
        val text: String,
        val time: Long,
        /**
         * 这条消息带的图片附件 id（形如 `sha256:…`），按顺序渲染成缩略图。
         *
         * 内核把图片存成**引用**（`{type:'image', attachment:{attachmentId}}`），
         * 字节要另走 `GET /attachments/{id}` 取。所以这里只留 id，
         * 渲染时才按需拉 —— 一屏里十张图不该在解析历史时就全部下载。
         */
        val attachments: List<String> = emptyList(),
    ) : ChatItem

    data class Assistant(
        override val id: String,
        override val seq: Int,
        val text: String,
        val time: Long,
    ) : ChatItem

    data class Reasoning(
        override val id: String,
        override val seq: Int,
        val text: String,
        val time: Long,
    ) : ChatItem

    /** 一次工具调用；[result] 为 null 表示还在跑（或服务端没给结果）。 */
    data class Tool(
        override val id: String,
        override val seq: Int,
        val callId: String,
        val name: String,
        val arguments: String,
        val result: String?,
        val isError: Boolean,
        /** 墙钟耗时（`tool/result.time - tool/call.time`）；时钟倒退或没结果时为 null。 */
        val durationMs: Long? = null,
    ) : ChatItem

    /**
     * **相邻**的若干次工具调用合成一组。
     *
     * 口径照抄 dsh-companion 的 `mergeToolRuns`：一轮里连着调十几次工具时，
     * 平铺出来会把助手的正文挤出屏幕，读者只能一路滚。合并后默认折叠，
     * 标题用去重后的工具名（最多 4 个 + "等"）交代"干了什么"。
     */
    data class ToolGroup(
        override val id: String,
        override val seq: Int,
        val tools: List<Tool>,
    ) : ChatItem {
        val title: String
            get() {
                val names = tools.map { it.name }.distinct()
                return when {
                    names.isEmpty() -> "${tools.size} 次工具调用"
                    names.size > 4 -> names.take(4).joinToString("、") + " 等 " + tools.size + " 次"
                    else -> names.joinToString("、") + " · " + tools.size + " 次"
                }
            }

        val hasError: Boolean get() = tools.any { it.isError }
    }

    data class Notice(
        override val id: String,
        override val seq: Int,
        /** info | warning | error */
        val level: String,
        val text: String,
        /**
         * `context` = 上下文注入（记忆、系统提示）。这类内容不是对话的一部分，
         * 官方 Web 把它们挪进「上下文」页签，客户端里默认**折叠成一行**，
         * 不占视觉重心。其它类型（失败原因、取消、超长）才直接显示。
         */
        val kind: String = "plain",
    ) : ChatItem

    /**
     * 一轮结束时端出来的产出文件（`deliverables/presented`）。
     *
     * 以前这里降级成一条「产出文件：…」提示——把结构化信息拍平成字符串，
     * 结果既点不开也没法带描述。现在保留结构，UI 画卡片，点了直接定位到文件页。
     */
    data class Deliverable(
        override val id: String,
        override val seq: Int,
        val files: List<DeliverableFile>,
    ) : ChatItem
}

/** 一个产出文件。[path] 是服务端侧的绝对路径，[description] 是模型自己写的一句话。 */
data class DeliverableFile(val path: String, val description: String? = null) {
    val name: String get() = path.substringAfterLast('/').ifBlank { path }
    val directory: String get() = path.substringBeforeLast('/', "")
}

private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull

/**
 * 从 content 块里挑出图片附件的 id。
 *
 * 两种形状都认：内核原始的 `{type:'image', attachment:{attachmentId}}`，
 * 以及某些版本把 id 直接摊平的 `{type:'image', attachmentId}`。
 * 一个都没有就返回空表 —— 纯文本消息走这条路径时不该有任何额外开销。
 */
private fun contentAttachments(content: JsonElement?): List<String> {
    val array = content as? JsonArray ?: return emptyList()
    return array.mapNotNull { block ->
        val value = block as? JsonObject ?: return@mapNotNull null
        if (value.str("type") != "image") return@mapNotNull null
        value.obj("attachment")?.str("attachmentId") ?: value.str("attachmentId")
    }
}

/** content 可能是字符串，也可能是 `[{type:text,text:...}]` 块数组。 */
private fun contentText(content: JsonElement?): String = when (content) {
    is JsonPrimitive -> content.contentOrNull.orEmpty()
    is JsonArray -> content.mapNotNull { block ->
        val value = block as? JsonObject ?: return@mapNotNull null
        when (value.str("type")) {
            "text", null -> value.str("text")
            else -> null
        }
    }.joinToString("\n\n")
    else -> ""
}

/**
 * 历史接口的每条事件有**两种**形状，必须都认：
 *
 * ```json
 * {"type":"event","event":{"type":"assistant/message","seq":3269,"data":{...}}}   // 线上（historyFormatVersion ≥ 1）
 * {"type":"assistant/message","seq":3269,"data":{...}}                            // 早期插件 / 事件流
 * ```
 *
 * 线上接口把内层事件又包了一层 `{"type":"event","event":…}`。只按裸事件解析的话，
 * `type` 读到的是字符串 `"event"`，`data` 取不到，**整批事件被静默丢弃，会话页一片空白**
 * —— 真机 2026-10-21 实测踩到：305 条事件一条都没渲染。
 */
private fun unwrapEvent(element: JsonElement): JsonObject? {
    val outer = element as? JsonObject ?: return null
    val inner = outer.obj("event")
    return if (inner != null && outer.str("type") == "event") inner else outer
}

/** 一页历史 + 分页游标（`oldest` 是这一页里最小的 seq，下次向前翻要用）。 */
data class HistoryPage(
    val items: List<ChatItem>,
    val hasMore: Boolean,
    val oldest: Int?,
    /** 原始响应里的 `projections`（TODO / 权限当前值都从这里读），老服务端可能没有。 */
    val projections: JsonElement? = null,
    /** 这一页最大的持久 seq，断档检测拿它当基线。 */
    val maxSeq: Int? = null,
    /** 解包后的事件原文，轨迹要按 `turn/start|end` 自己分组。 */
    val rawEvents: List<JsonElement> = emptyList(),
)

/** 一条 `tool/result` 的可用信息（只在解析过程中流转）。 */
private data class ToolOutcome(val body: String, val isError: Boolean, val time: Long)

/**
 * 会话的粗粒度统计。
 *
 * 只数**已经渲染出来的条目**，所以是"这一屏能看到的量"，不是服务端的权威计数 ——
 * 文案里也不写成"共 N 次"，避免翻页前后数字变了让人以为出错。
 */
data class ChatStats(
    val turns: Int = 0,
    val tools: Int = 0,
    val failedTools: Int = 0,
    val reasoning: Int = 0,
    val deliverables: Int = 0,
) {
    val isEmpty: Boolean get() = turns == 0 && tools == 0 && reasoning == 0 && deliverables == 0

    fun summary(): String = buildList {
        if (turns > 0) add("$turns 轮")
        if (tools > 0) add(if (failedTools > 0) "$tools 次工具（$failedTools 失败）" else "$tools 次工具")
        if (reasoning > 0) add("$reasoning 段思考")
        if (deliverables > 0) add("$deliverables 个产出")
    }.joinToString(" · ")
}

fun chatStats(items: List<ChatItem>): ChatStats {
    var turns = 0
    var tools = 0
    var failed = 0
    var reasoning = 0
    var deliverables = 0
    for (item in items) {
        when (item) {
            is ChatItem.User -> turns++
            is ChatItem.Reasoning -> reasoning++
            is ChatItem.Deliverable -> deliverables += item.files.size
            is ChatItem.Tool -> {
                tools++
                if (item.isError) failed++
            }
            // 合并后的组要把组内的逐条也数上，否则统计会比实际少一大截
            is ChatItem.ToolGroup -> {
                tools += item.tools.size
                failed += item.tools.count { it.isError }
            }
            else -> Unit
        }
    }
    return ChatStats(turns, tools, failed, reasoning, deliverables)
}

fun parseHistoryOldestSeq(events: List<JsonElement>): Int? =
    events.mapNotNull { unwrapEvent(it)?.int("seq") }.minOrNull()

fun parseHistory(events: List<JsonElement>): List<ChatItem> {
    // 工具结果单独一趟：tool/result 与 tool/call 是两条事件，靠 toolCallId 配对。
    // 时间一并留下，用来算墙钟耗时（结果那儿没有可用的时延字段）。
    val results = HashMap<String, ToolOutcome>()
    for (element in events) {
        val event = unwrapEvent(element) ?: continue
        if (event.str("type") != "tool/result") continue
        val data = event.obj("data") ?: continue
        val message = data.obj("message") ?: data
        val callId = message.str("toolCallId") ?: data.str("toolCallId") ?: continue
        val body = contentText(message["content"] ?: data["content"])
        val isError = (message["isError"] as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull() == true
        val time = event["time"]?.let { (it as? JsonPrimitive)?.contentOrNull?.toLongOrNull() } ?: 0L
        results[callId] = ToolOutcome(body, isError, time)
    }

    val items = mutableListOf<ChatItem>()
    var anonymous = 0
    for (element in events) {
        val event = unwrapEvent(element) ?: continue
        val type = event.str("type").orEmpty()
        val seq = event.int("seq") ?: anonymous++
        val time = event["time"]?.let { (it as? JsonPrimitive)?.contentOrNull?.toLongOrNull() } ?: 0L
        val data = event.obj("data") ?: continue

        when (type) {
            "user/message" -> {
                val text = contentText(data["content"]).trim()
                val images = contentAttachments(data["content"])
                // 只带图不带字的消息是合法的：早先按"文本为空就丢"处理，整条消息会消失
                if (text.isEmpty() && images.isEmpty()) continue
                val sourceKind = data.obj("source")?.str("kind")
                items += if (sourceKind == "user") {
                    ChatItem.User("$seq-user", seq, text, time, images)
                } else {
                    // 上下文注入（记忆、系统提示）不是用户说的话，但也不能丢——降级为提示行。
                    ChatItem.Notice("$seq-context", seq, "info", "上下文注入\n$text", kind = "context")
                }
            }

            "assistant/message" -> {
                val message = data.obj("message") ?: data
                val content = message["content"]
                val blocks = content as? JsonArray
                if (blocks == null) {
                    val text = contentText(content).trim()
                    if (text.isNotEmpty()) items += ChatItem.Assistant("$seq-assistant", seq, text, time)
                    continue
                }
                var index = 0
                for (block in blocks) {
                    val value = block as? JsonObject ?: continue
                    val text = value.str("text")?.takeIf { it.isNotBlank() } ?: continue
                    items += when (value.str("type")) {
                        "reasoning" -> ChatItem.Reasoning("$seq-reason-${index++}", seq, text, time)
                        else -> ChatItem.Assistant("$seq-assistant-${index++}", seq, text, time)
                    }
                }
            }

            "tool/call" -> {
                val callId = data.str("callId") ?: data.str("id") ?: "$seq-${items.size}"
                val name = data.str("name") ?: "tool"
                val arguments = data.str("arguments").orEmpty()
                val paired = results[callId]
                items += ChatItem.Tool(
                    id = "$seq-tool-$callId",
                    seq = seq,
                    callId = callId,
                    name = name,
                    arguments = arguments,
                    result = paired?.body,
                    isError = paired?.isError ?: false,
                    durationMs = toolDurationMs(time, paired?.time),
                )
            }

            "turn/end" -> {
                val reason = data.obj("reason") ?: continue
                when (reason.str("kind")) {
                    "error" -> {
                        val message = reason.obj("error")?.str("message") ?: "本轮执行失败"
                        items += ChatItem.Notice("$seq-failure", seq, "error", message)
                    }
                    "max-tokens" -> items += ChatItem.Notice(
                        "$seq-truncated", seq, "warning", "回复达到输出上限，内容被截断（不是失败）。",
                    )
                    "aborted" -> items += ChatItem.Notice("$seq-aborted", seq, "info", "本轮已被取消。")
                    else -> Unit
                }
            }

            "deliverables/presented" -> {
                val files = (data["files"] as? JsonArray).orEmpty().mapNotNull { raw ->
                    val value = raw as? JsonObject ?: return@mapNotNull null
                    val path = value.str("path")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    DeliverableFile(path, value.str("description"))
                }
                if (files.isNotEmpty()) {
                    items += ChatItem.Deliverable("$seq-deliverable", seq, files)
                }
            }
        }
    }
    return mergeToolRuns(items)
}

/**
 * 把**相邻**的 [ChatItem.Tool] 合成 [ChatItem.ToolGroup]；单个工具不合并。
 *
 * 只合并真的挨着的：中间隔了助手正文、提示或失败行就断开——那些是"阶段"的分界，
 * 跨越它们合并会让读者误以为一整段只是"跑了个工具"。
 */
fun mergeToolRuns(items: List<ChatItem>): List<ChatItem> {
    val out = mutableListOf<ChatItem>()
    val run = mutableListOf<ChatItem.Tool>()

    fun flush() {
        when {
            run.isEmpty() -> Unit
            run.size == 1 -> out += run[0]
            else -> out += ChatItem.ToolGroup(
                id = "group-${run.first().seq}-${run.last().seq}",
                seq = run.first().seq,
                tools = run.toList(),
            )
        }
        run.clear()
    }

    for (item in items) {
        if (item is ChatItem.Tool) run += item else { flush(); out += item }
    }
    flush()
    return out
}

/** 把一段 JSON 字符串参数缩进展示；解析失败就原样给出（不隐藏信息）。 */
fun prettyArguments(raw: String): String {
    if (raw.isBlank()) return ""
    return runCatching {
        appJson.encodeToString(
            JsonElement.serializer(),
            appJson.parseToJsonElement(raw),
        )
    }.getOrDefault(raw)
}
