package io.github.westanke.dshdesktop.data

/**
 * Markdown 解析（**纯函数**，不依赖 Compose）。
 *
 * 为什么要单独一层：渲染在 `ui/`，判定在 `data/`，两者用 [MdBlock] / [InlineSpan] 交接。
 * 这样解析能被 `tools/verify-markdown.sh` 拿真实会话正文直接跑 —— 表格这种
 * "少一层判定就整块退化成一堆竖线"的语法，光靠肉眼看界面是守不住的。
 *
 * 覆盖范围按真实数据定：本机三个会话 92 段助手正文里，
 * 行内代码 97、`**粗体**` 77、表格行 54、标题 31、无序列表 32、有序列表 7、
 * 围栏代码 8、链接 4；引用 / 任务列表 / 分隔线当时为 0，但成本低，一并支持。
 *
 * **宁可不渲染，也不能吞内容**：识别不了的语法一律当普通文字，原文一个字符都不丢。
 */

sealed interface MdBlock {

    /** 普通段落，段内保留换行（中文排版里那是作者有意断行，不该合并成空格）。 */
    data class Paragraph(val text: String) : MdBlock

    data class Heading(val level: Int, val text: String) : MdBlock

    data class Code(val language: String?, val body: String) : MdBlock

    data class Bullets(val items: List<BulletItem>) : MdBlock

    /** 引用块，[lines] 保留分行（原来合并成一行会把作者的列举压成一句）。 */
    data class Quote(val lines: List<String>) : MdBlock

    data object Rule : MdBlock

    /**
     * GFM 管道表。
     *
     * [headers] / [rows] 里存的是**原始单元格文本**（`**x**`、`` `y` `` 原样保留），
     * 渲染时逐格走 [parseInline] —— 表格里的粗体与行内代码必须照常生效，
     * 这正是"表格样式不对"最直观的一半。
     */
    data class Table(
        val headers: List<String>,
        val aligns: List<ColumnAlign>,
        val rows: List<List<String>>,
    ) : MdBlock {
        val columns: Int get() = headers.size
    }
}

/** 列表项。[level] 是缩进层级（0 起），[checked] 非空表示任务列表。 */
data class BulletItem(
    val text: String,
    val level: Int,
    /** `•` 或 `1.` 这样的前缀，渲染时按原样画。 */
    val marker: String,
    val checked: Boolean? = null,
)

enum class ColumnAlign { Start, Center, End }

/**
 * 一段带样式的文本片段。
 *
 * 不做富文本树：真实语料里嵌套很少（`**含 `code` 的粗体**` 偶发），
 * 用"基础样式 + 可叠加标记"的组合足够，且渲染端拼 AnnotatedString 简单。
 */
data class InlineSpan(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val code: Boolean = false,
    val strike: Boolean = false,
    /** 非空表示这段是链接（URL 或相对路径）。 */
    val url: String? = null,
)

// ── 行级识别 ─────────────────────────────────────────────────────────────────

private val HEADING = Regex("^(#{1,6})\\s+(.*)$")
private val BULLET = Regex("^(\\s*)([-*+])\\s+(.*)$")
private val ORDERED = Regex("^(\\s*)(\\d{1,9})[.)]\\s+(.*)$")
private val SEP_CELL = Regex("^:?-{1,}:?$")
private val FENCE = Regex("^```(.*)$")

/** 缩进按 2 空格一档，tab 折成 2 空格（会话里的列表最多两层）。 */
private fun indentLevel(indent: String): Int {
    val width = indent.replace("\t", "  ").length
    return (width / 2).coerceIn(0, 3)
}

/**
 * 解析整段 Markdown。[source] 为空的返回空列表，不会造出一个空段落。
 */
fun parseMarkdown(source: String): List<MdBlock> {
    if (source.isBlank()) return emptyList()
    val lines = source.replace("\r\n", "\n").replace('\r', '\n').split('\n')
    val blocks = mutableListOf<MdBlock>()
    val paragraph = mutableListOf<String>()
    var index = 0

    fun flushParagraph() {
        val text = paragraph.joinToString("\n").trim()
        if (text.isNotEmpty()) blocks += MdBlock.Paragraph(text)
        paragraph.clear()
    }

    while (index < lines.size) {
        val raw = lines[index]
        val line = raw.trim()

        // 围栏代码块优先：里面的一切都是字面量
        val fence = FENCE.matchEntire(line)
        if (fence != null) {
            flushParagraph()
            val language = fence.groupValues[1].trim().ifEmpty { null }
            val body = StringBuilder()
            index++
            while (index < lines.size && !lines[index].trim().startsWith("```")) {
                body.appendLine(lines[index])
                index++
            }
            // 吃掉闭合围栏（未闭合时停在末尾，不算错误）
            if (index < lines.size) index++
            blocks += MdBlock.Code(language, body.toString())
            continue
        }

        if (line.isEmpty()) {
            flushParagraph()
            index++
            continue
        }

        // 表格：分隔行是唯一的判定依据，表头从"刚累积的那一行"里取回来
        if (looksLikeTableRow(line) && parseTableAlignments(line) != null) {
            val headerLine = paragraph.removeLastOrNull()
            val aligns = parseTableAlignments(line)
            if (headerLine == null || aligns == null) {
                paragraph += raw
                index++
                continue
            }
            flushParagraph()
            val headers = parseTableRow(headerLine)
            val rows = mutableListOf<List<String>>()
            index++
            while (index < lines.size && looksLikeTableRow(lines[index].trim())) {
                rows += parseTableRow(lines[index].trim())
                index++
            }
            // 行在解析层就归一到列数：让"表形状正确"成为 MdBlock.Table 的不变量，
            // 渲染端不用再做防御，验证脚本也能直接查 rows 的宽度。
            blocks += MdBlock.Table(
                headers = headers,
                aligns = normalizeAligns(aligns, headers.size),
                rows = rows.map { normalizeRow(it, headers.size) },
            )
            continue
        }

        val heading = HEADING.matchEntire(line)
        if (heading != null) {
            flushParagraph()
            blocks += MdBlock.Heading(heading.groupValues[1].length, heading.groupValues[2].trim())
            index++
            continue
        }

        if (line == "---" || line == "***" || line == "___") {
            flushParagraph()
            blocks += MdBlock.Rule
            index++
            continue
        }

        if (BULLET.matches(raw) || ORDERED.matches(line)) {
            flushParagraph()
            val items = mutableListOf<BulletItem>()
            while (index < lines.size) {
                val currentRaw = lines[index]
                val current = currentRaw.trim()
                val bullet = BULLET.matchEntire(currentRaw)
                val ordered = ORDERED.matchEntire(current)
                when {
                    bullet != null -> {
                        val content = bullet.groupValues[3]
                        val (text, checked) = taskState(content)
                        items += BulletItem(
                            text = text,
                            level = indentLevel(bullet.groupValues[1]),
                            marker = "•",
                            checked = checked,
                        )
                        index++
                    }

                    ordered != null -> {
                        val content = ordered.groupValues[3]
                        val (text, checked) = taskState(content)
                        items += BulletItem(
                            text = text,
                            level = indentLevel(ordered.groupValues[1]),
                            marker = ordered.groupValues[2] + ".",
                            checked = checked,
                        )
                        index++
                    }

                    // 续行：比当前项更深缩进、且不是空行 → 并进上一项
                    current.isNotEmpty() && items.isNotEmpty() && currentRaw.startsWith("  ") -> {
                        val last = items.removeAt(items.lastIndex)
                        items += last.copy(text = last.text + " " + current)
                        index++
                    }

                    else -> break
                }
            }
            blocks += MdBlock.Bullets(items)
            continue
        }

        if (line.startsWith(">")) {
            flushParagraph()
            val quoted = mutableListOf<String>()
            while (index < lines.size && lines[index].trim().startsWith(">")) {
                quoted += lines[index].trim().removePrefix(">").removePrefix(" ").trimEnd()
                index++
            }
            blocks += MdBlock.Quote(quoted)
            continue
        }

        paragraph += raw
        index++
    }
    flushParagraph()
    return blocks
}

/** 任务列表：`[ ] 待办` / `[x] 已办`。不是任务就原样返回。 */
private fun taskState(content: String): Pair<String, Boolean?> = when {
    content.startsWith("[ ] ") -> content.removePrefix("[ ] ") to false
    content.startsWith("[x] ", ignoreCase = true) -> content.substring(4) to true
    else -> content to null
}

/**
 * 表格数据行：至少有一个 `|` 就算。
 *
 * 不能只看"以 `|` 开头"：GFM 允许省略首尾管道（`a | b`），真有人那么写。
 */
fun looksLikeTableRow(line: String): Boolean = line.contains('|') && line.isNotEmpty()

/**
 * 拆一行表格。
 *
 * `\|` 是转义的竖线，属于单元格内容（`a \| b` → 一个格子 `a | b`），不能当分隔符。
 * 首尾管道的处理要和转义一起看，否则 `…\|` 结尾会被误砍一个字符。
 */
fun parseTableRow(line: String): List<String> {
    var body = line.trim()
    if (body.startsWith("|")) body = body.substring(1)
    if (body.endsWith("|") && !body.endsWith("\\|")) body = body.substring(0, body.length - 1)

    val cells = mutableListOf<String>()
    val buffer = StringBuilder()
    var cursor = 0
    while (cursor < body.length) {
        val ch = body[cursor]
        if (ch == '\\' && cursor + 1 < body.length && body[cursor + 1] == '|') {
            buffer.append('|')
            cursor += 2
            continue
        }
        if (ch == '|') {
            cells += buffer.toString().trim()
            buffer.clear()
            cursor++
            continue
        }
        buffer.append(ch)
        cursor++
    }
    cells += buffer.toString().trim()
    return cells
}

/**
 * 解析分隔行（`|---|---|`、`|:--:|--:|`）为列对齐。
 *
 * 返回 null 表示"这不是分隔行" —— 调用方据此区分表格与普通段落。
 * 单元格至少要有一个 `-`，且整行必须含 `|`：否则 `---` 会被当成单列表格，把正文吃掉。
 */
fun parseTableAlignments(line: String): List<ColumnAlign>? {
    val text = line.trim()
    if (!text.contains('|') || !text.contains('-')) return null
    val cells = parseTableRow(text)
    if (cells.isEmpty()) return null
    if (!cells.all { SEP_CELL.matches(it.replace(" ", "")) }) return null
    return cells.map { cell ->
        val value = cell.replace(" ", "")
        when {
            value.startsWith(":") && value.endsWith(":") -> ColumnAlign.Center
            value.endsWith(":") -> ColumnAlign.End
            else -> ColumnAlign.Start
        }
    }
}

/** 对齐数量补齐/截断到表头列数：分隔行少写一格不该让整表错位。 */
private fun normalizeAligns(aligns: List<ColumnAlign>, columns: Int): List<ColumnAlign> {
    if (aligns.size == columns) return aligns
    return List(columns) { aligns.getOrElse(it) { ColumnAlign.Start } }
}

/**
 * 把数据行补齐到表头列数。
 *
 * 少格子补空串，多格子并进最后一列 —— 两种情况都实际出现过（模型偶尔多写一个 `|`），
 * 与其让整行错位，不如多出来的内容留在最后一格里可见。
 */
fun normalizeRow(cells: List<String>, columns: Int): List<String> = when {
    columns <= 0 -> emptyList()
    cells.size == columns -> cells
    cells.size < columns -> cells + List(columns - cells.size) { "" }
    else -> cells.take(columns - 1) + cells.drop(columns - 1).joinToString(" | ")
}

// ── 行内解析 ─────────────────────────────────────────────────────────────────

/**
 * 把一行文本拆成 [InlineSpan] 序列。
 *
 * 支持的标记：`` `code` ``、`**粗**`、`__粗__`、`*斜*`、`_斜_`、`~~删除~~`、
 * `[文字](链接)`、裸 URL，以及反斜杠转义。
 *
 * 标记未闭合一律按字面处理（`**没闭合` 照样显示两个星号）：
 * 宁可多显示符号，也不能把用户的内容当作语法吃掉。
 */
fun parseInline(source: String): List<InlineSpan> {
    if (source.isEmpty()) return emptyList()
    val out = mutableListOf<InlineSpan>()
    scanInline(source, InlineStyle(), out, depth = 0)
    return mergeInline(out)
}

private data class InlineStyle(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val code: Boolean = false,
    val strike: Boolean = false,
    val url: String? = null,
)

private fun scanInline(
    source: String,
    style: InlineStyle,
    out: MutableList<InlineSpan>,
    depth: Int,
) {
    fun emit(text: String) {
        if (text.isNotEmpty()) out += InlineSpan(text, style.bold, style.italic, style.code, style.strike, style.url)
    }

    val buffer = StringBuilder()
    fun flush() {
        emit(buffer.toString())
        buffer.clear()
    }

    var i = 0
    while (i < source.length) {
        val ch = source[i]

        // 反斜杠转义：下一个字符原样输出
        if (ch == '\\' && i + 1 < source.length) {
            buffer.append(source[i + 1])
            i += 2
            continue
        }

        if (ch == '`') {
            val end = source.indexOf('`', i + 1)
            if (end > i + 1) {
                flush()
                emitInline(source.substring(i + 1, end), style, out, depth, code = true)
                i = end + 1
                continue
            }
            buffer.append(ch)
            i++
            continue
        }

        if (!style.code && depth < 3) {
            val double = when {
                source.startsWith("**", i) -> "**"
                source.startsWith("__", i) -> "__"
                source.startsWith("~~", i) -> "~~"
                else -> null
            }
            if (double != null) {
                val end = source.indexOf(double, i + 2)
                if (end > i + 2) {
                    flush()
                    val inner = source.substring(i + 2, end)
                    val next = when (double) {
                        "~~" -> style.copy(strike = true)
                        else -> style.copy(bold = true)
                    }
                    scanInline(inner, next, out, depth + 1)
                    i = end + 2
                    continue
                }
            }

            if ((ch == '*' || ch == '_') && i + 1 < source.length && source[i + 1] != ' ') {
                val end = source.indexOf(ch, i + 1)
                if (end > i + 1) {
                    flush()
                    scanInline(source.substring(i + 1, end), style.copy(italic = true), out, depth + 1)
                    i = end + 1
                    continue
                }
            }

            if (ch == '[') {
                val close = source.indexOf("](", i)
                val end = if (close > 0) source.indexOf(')', close) else -1
                if (close > 0 && end > close) {
                    flush()
                    val label = source.substring(i + 1, close)
                    val url = source.substring(close + 2, end).trim()
                    emitInline(label, style.copy(url = url.ifBlank { null }), out, depth)
                    i = end + 1
                    continue
                }
            }

            if (ch == 'h' && (source.startsWith("http://", i) || source.startsWith("https://", i))) {
                val end = source.indexOfAny(charArrayOf(' ', '\t', '）', ')', '。', '，', '、'), i)
                val stop = if (end < 0) source.length else end
                val url = source.substring(i, stop).trimEnd('.', ',', ';', ':')
                if (url.length > 8) {
                    flush()
                    out += InlineSpan(url, style.bold, style.italic, false, style.strike, url)
                    i += url.length
                    continue
                }
            }
        }

        buffer.append(ch)
        i++
    }
    flush()
}

/** 链接文字 / 行内代码的内容单独扫一遍，继承外层样式（`[\`a\`](url)` 里的代码要保留）。 */
private fun emitInline(
    text: String,
    style: InlineStyle,
    out: MutableList<InlineSpan>,
    depth: Int,
    code: Boolean = false,
) {
    scanInline(text, if (code) style.copy(code = true) else style, out, depth + 1)
}

/** 合并相邻同样式片段，减少 AnnotatedString 的 span 数量。 */
private fun mergeInline(spans: List<InlineSpan>): List<InlineSpan> {
    val out = mutableListOf<InlineSpan>()
    for (span in spans) {
        if (span.text.isEmpty()) continue
        val last = out.lastOrNull()
        if (last != null &&
            last.bold == span.bold &&
            last.italic == span.italic &&
            last.code == span.code &&
            last.strike == span.strike &&
            last.url == span.url
        ) {
            out[out.lastIndex] = last.copy(text = last.text + span.text)
        } else {
            out += span
        }
    }
    return out
}
