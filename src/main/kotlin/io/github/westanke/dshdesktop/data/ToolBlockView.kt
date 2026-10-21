package io.github.westanke.dshdesktop.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/**
 * 工具调用的**专用渲染视图**（对标官方 Web 的 ReadBlock / TerminalBlock / DiffBlock）。
 *
 * 为什么在 data 层做分发而不是 UI 层嗅探字符串：分发规则（工具名 → 块类型、参数键 → 内容）
 * 是**可测的纯函数**，与 Compose 无关；UI 只认 [ToolBlockView] 这一个结构。
 *
 * 分发依据是安卓端实测的 3000 条 `tool/call` 分布：bash 71%、edit 9%、
 * read/read_image 8%、write 2% —— 四种视图覆盖约 88% 的调用，其余走
 * [ToolBlockView.Generic] 的通用渲染。
 *
 * **铁律：解析失败一律回落 [ToolBlockView.Generic]。** 专用块是增强，不是依赖 ——
 * arguments 缺字段、result 格式变了，都不能让详情块比改动前少显示任何东西。
 */
sealed interface ToolBlockView {

    /** 通用块：格式化后的输入 + 原样输出。 */
    data class Generic(val formattedArguments: String) : ToolBlockView

    /**
     * 文件读取块（`read` / `read_image`）：路径头 + 正文。
     *
     * [hasLineNumbers] 为真表示 result 自带 `N: ` 前缀（内核给的），此时 UI 不再重编号；
     * 为假（例如 `read_image` 或老格式）就只显示路径头，正文原样。
     */
    data class Read(
        val path: String,
        val offset: Int?,
        val limit: Int?,
        val body: String,
        val hasLineNumbers: Boolean,
    ) : ToolBlockView

    /** 终端块（`bash` / `pwsh`）：`$ command` 提示符 + 已剥 ANSI 转义的输出。 */
    data class Terminal(
        val command: String,
        val description: String?,
        val output: String,
        val running: Boolean,
    ) : ToolBlockView

    /**
     * 差异块（`edit` / `write`）。
     *
     * - `edit`：[oldText] / [newText] 都有，UI 做 `- ` / `+ ` 对照；
     * - `write`：[oldText] 为 null，[newText] 是即将写入的全文，UI 标「新建/覆盖」。
     */
    data class Diff(
        val path: String,
        val oldText: String?,
        val newText: String?,
        val replaceAll: Boolean,
    ) : ToolBlockView
}

private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull

private fun JsonObject.bool(key: String): Boolean? =
    (this[key] as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull()

/**
 * 把一次工具调用分发为专用视图。
 *
 * [arguments] 是内核原样的 JSON **字符串**；[result] 为 null 表示仍在运行。
 * 任何一步解析失败都退回 [ToolBlockView.Generic]。
 */
fun toolBlockView(name: String, arguments: String, result: String?): ToolBlockView {
    val args = runCatching {
        appJson.parseToJsonElement(arguments.ifBlank { "{}" })
    }.getOrNull() as? JsonObject ?: return ToolBlockView.Generic(prettyArguments(arguments))

    return when (name) {
        "read", "read_image" -> {
            val (body, hasNumbers) = extractReadBody(result)
            ToolBlockView.Read(
                path = args.str("file_path") ?: args.str("path").orEmpty(),
                offset = args.int("offset"),
                limit = args.int("limit"),
                body = body,
                hasLineNumbers = hasNumbers,
            )
        }

        "bash", "pwsh" -> ToolBlockView.Terminal(
            command = args.str("command").orEmpty(),
            description = args.str("description"),
            output = stripAnsiEscapes(result ?: ""),
            running = result == null,
        )

        "edit" -> ToolBlockView.Diff(
            path = args.str("file_path") ?: args.str("path").orEmpty(),
            oldText = args.str("old_string"),
            newText = args.str("new_string"),
            replaceAll = args.bool("replace_all") == true,
        )

        "write" -> ToolBlockView.Diff(
            path = args.str("file_path") ?: args.str("path").orEmpty(),
            oldText = null,
            newText = args.str("content"),
            replaceAll = false,
        )

        else -> ToolBlockView.Generic(prettyArguments(arguments))
    }
}

/**
 * read 结果的固定包装：`<path>…</path><type>file</type><content>\n正文\n</content>`。
 * 包装不存在时正文退回原样 result —— 宁可多显示包装标签，也不能把正文吞掉。
 */
fun extractReadBody(result: String?): Pair<String, Boolean> {
    if (result.isNullOrEmpty()) return "" to false
    val content = result
        .substringAfter("<content>", result)
        .substringBefore("</content>")
        .trimStart('\n')
    if (content.isEmpty()) return result to false
    val hasNumbers = content.lineSequence().firstOrNull { it.isNotBlank() }
        ?.contains(Regex("""^\s*\d+: """)) == true
    return content to hasNumbers
}

/**
 * 折叠态的一行摘要：`动作短语 · 关键参数`。
 *
 * 动作短语照官方 Web 界面的措辞还原（安卓端从真实会话里核过），不是我们自己编的词。
 */
fun toolHeadline(name: String, arguments: String, result: String?): String {
    val phrase = toolActionPhrase(name, arguments)
    val detail = argumentsSummary(arguments)
    return if (detail.isNullOrBlank()) phrase else "$phrase · $detail"
}

/** 工具名 → 动作短语。`run_code` 里包着内层工具，扫参数补全动作（安卓端实测口径）。 */
fun toolActionPhrase(name: String, arguments: String): String {
    val raw = arguments.orEmpty()
    if (name == "run_code") {
        val inner = listOf(
            "tools.edit" to "修改了文件",
            "tools.write" to "已写入文件",
            "tools.read" to "读取了文件",
            "tools.bash" to "执行了命令",
            "tools.grep" to "搜索了内容",
            "tools.glob" to "搜索了文件",
        ).filter { (needle, _) -> needle in raw }.map { it.second }
        return (listOf("运行了代码") + inner).joinToString("，")
    }
    val byName = mapOf(
        "bash" to "执行了命令",
        "pwsh" to "执行了命令",
        "read" to "读取了文件",
        "read_image" to "查看了图片",
        "write" to "已写入文件",
        "edit" to "修改了文件",
        "grep" to "搜索了内容",
        "glob" to "搜索了文件",
        "web_fetch" to "已访问网页",
        "web_search" to "已搜索网页",
    )
    byName[name]?.let { return it }
    if (name.startsWith("mcp__")) return "调用了 " + name.removePrefix("mcp__").replace("__", " · ")
    return name.ifBlank { "工具" }
}

/** 从参数里挑一个最适合当副标题的字段；都没有就返回 null（不硬凑）。 */
fun argumentsSummary(raw: String): String? {
    if (raw.isBlank()) return null
    val parsed = runCatching { appJson.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: return null
    for (key in listOf(
        "description", "command", "file_path", "path", "query", "pattern", "url", "name", "package",
    )) {
        parsed.str(key)?.takeIf { it.isNotBlank() }?.let { return it.lineSequence().first() }
    }
    return null
}

/**
 * 剥掉 ANSI 转义：内核透传的终端输出常带颜色码（`ESC[32m…ESC[0m`），
 * Compose 的 Text 只会把它显示成一串方块乱码。只做「干净显示」，不做 ANSI→颜色的映射。
 */
fun stripAnsiEscapes(text: String): String {
    if (text.indexOf('\u001B') < 0) return text
    val sb = StringBuilder(text.length)
    var i = 0
    while (i < text.length) {
        val c = text[i]
        if (c == '\u001B' && i + 1 < text.length) {
            when (text[i + 1]) {
                '[' -> {
                    // CSI：ESC [ 参数字节 中间字节* 终止字节
                    var j = i + 2
                    while (j < text.length && text[j] in '\u0020'..'\u003F') j++
                    if (j < text.length && text[j] in '\u0040'..'\u007E') {
                        i = j + 1
                        continue
                    }
                    i = j
                    continue
                }

                ']' -> {
                    // OSC：到 BEL 或 ST 结束；未闭合就吞到尾
                    val bel = text.indexOf('\u0007', i + 2)
                    val st = text.indexOf("\u001B\\", i + 2)
                    when {
                        bel >= 0 && (st < 0 || bel < st) -> {
                            i = bel + 1
                            continue
                        }

                        st >= 0 -> {
                            i = st + 2
                            continue
                        }

                        else -> {
                            i = text.length
                            continue
                        }
                    }
                }

                else -> {
                    i += 2
                    continue
                }
            }
        }
        sb.append(c)
        i++
    }
    return sb.toString()
}

/** 耗时的可读格式。负值已在 [toolDurationMs] 里判掉，这里只负责排版。 */
fun formatToolDuration(ms: Long): String = when {
    ms < 1_000 -> "$ms ms"
    ms < 60_000 -> String.format(java.util.Locale.US, "%.1f s", ms / 1000.0)
    ms < 3_600_000 -> "${ms / 60_000}m ${(ms % 60_000) / 1_000}s"
    else -> "${ms / 3_600_000}h ${(ms % 3_600_000) / 60_000}m"
}

/**
 * 耗时 = `tool/result.time - tool/call.time`。
 *
 * 结果为负说明时钟倒退（休眠恢复、NTP 校时）——那种情况**不显示**，
 * 而不是显示 0 或负数：宁可少一行，也不要瞎编。
 */
fun toolDurationMs(callTime: Long, resultTime: Long?): Long? {
    if (resultTime == null || callTime <= 0 || resultTime <= 0) return null
    val delta = resultTime - callTime
    return if (delta < 0) null else delta
}
