package io.github.westanke.dshdesktop.data

/**
 * 会话内链接的分类与行号后缀解析（P1-2，对标 companion 的 `ChatLinkTarget.classify`）。
 *
 * 会话正文里的链接三类：
 * - **http(s)** → 浏览器（`openExternal`）；
 * - **工作区文件**（服务端绝对路径）→ 应用内打开：跳文件页 + 预览，带上行号就滚动定位；
 * - **其余**（相对路径、mailto、认不出的 scheme）→ 浏览器兜底。
 *
 * 行号后缀两种写法都认：`:24` / `:24-30`（编辑器风格）与 `#L24` / `#L24-L30`（GitHub 风格）。
 */
data class ChatLinkTarget(
    /** 剥掉行号后缀后的目标（外链时是原 URL）。 */
    val path: String,
    val lineStart: Int? = null,
    val lineEnd: Int? = null,
    /** true = 交给浏览器；false = 应用内按工作区文件打开。 */
    val isExternal: Boolean,
)

/** `:24` `:24-30` `#L24` `#L24-L30` —— 行号后缀必须是路径的**最后一段**。 */
private val LINE_SUFFIX =
    Regex("""^(.*?)(?::(\d{1,7})(?:-(\d{1,7}))?|#L(\d{1,7})(?:-L(\d{1,7}))?)$""")

fun classifyChatLink(url: String): ChatLinkTarget {
    var s = url.trim()
    if (s.startsWith("http://") || s.startsWith("https://")) {
        return ChatLinkTarget(s, isExternal = true)
    }
    // Markdown autolink `<…>` 与 file:// 前缀都剥掉
    if (s.length > 1 && s.startsWith("<") && s.endsWith(">")) s = s.substring(1, s.length - 1).trim()
    s = s.removePrefix("file://")
    if (s.isBlank()) return ChatLinkTarget(url, isExternal = true)

    // 行号后缀只可能是这两种形态；解析失败就当没有，别把带冒号的普通路径拆坏
    val m = LINE_SUFFIX.matchEntire(s)
    var path = s
    var lineStart: Int? = null
    var lineEnd: Int? = null
    if (m != null) {
        val base = m.groupValues[1]
        val a = m.groupValues[2].ifEmpty { m.groupValues[4] }
        val b = m.groupValues[3].ifEmpty { m.groupValues[5] }
        // 后缀前的部分必须是"像路径的"：以 / 开头（服务端绝对路径）或至少不是纯数字
        if (base.startsWith("/") || (!base.contains("://") && base.contains('.'))) {
            path = base
            lineStart = a.toIntOrNull()
            lineEnd = b.toIntOrNull() ?: lineStart
            if (lineStart == null) path = s
        }
    }

    // 工作区文件的判定：服务端绝对路径。相对路径没有 cwd 上下文，猜错比打不开更糟
    return if (path.startsWith("/")) {
        ChatLinkTarget(path, lineStart, lineEnd, isExternal = false)
    } else {
        ChatLinkTarget(url, isExternal = true)
    }
}
