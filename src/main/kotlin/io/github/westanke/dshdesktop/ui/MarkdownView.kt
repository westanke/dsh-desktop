package io.github.westanke.dshdesktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.westanke.dshdesktop.AppState
import io.github.westanke.dshdesktop.data.ColumnAlign
import io.github.westanke.dshdesktop.data.MdBlock
import io.github.westanke.dshdesktop.data.parseInline
import io.github.westanke.dshdesktop.data.parseMarkdown
import io.github.westanke.dshdesktop.openFileByPath
import kotlinx.coroutines.launch
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

/**
 * Markdown 渲染层。
 *
 * 解析在 `data/Markdown.kt`（纯函数，可被 `tools/verify-markdown.sh` 直接跑）；
 * 这里只负责把 [MdBlock] 画出来。
 *
 * 表格是这层的重点：**单元格内容必须再走一遍行内解析** —— 会话里的表格
 * 大量使用 `` `代码` `` 与 `**粗体**`，只画原文会让整张表看起来是"没渲染的竖线堆"。
 */

@Composable
fun MarkdownText(text: String, modifier: Modifier = Modifier, state: AppState? = null) {
    val blocks = remember(text) { parseMarkdown(text) }
    val colors = LocalAppColors.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Paragraph -> MarkdownParagraph(block.text, state)

                is MdBlock.Heading -> Text(
                    annotatedFrom(block.text),
                    style = headingStyle(block.level),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = if (block.level <= 2) 8.dp else 4.dp),
                )

                is MdBlock.Code -> CodeBlock(block.body, block.language)

                is MdBlock.Bullets -> Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    block.items.forEach { item ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(start = (item.level * 16).dp),
                        ) {
                            val markerText = when (item.checked) {
                                true -> "☑"
                                false -> "☐"
                                null -> item.marker
                            }
                            Text(
                                markerText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (item.checked == true) colors.success else colors.textSecondary,
                                modifier = Modifier.width(22.dp),
                            )
                            Text(
                                annotatedFrom(item.text),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (item.checked == true) colors.textTertiary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                is MdBlock.Quote -> Row(Modifier.fillMaxWidth()) {
                    Box(
                        Modifier
                            .width(3.dp)
                            .heightIn(min = 20.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(2.dp))
                    )
                    Box(Modifier.width(10.dp))
                    Text(
                        annotatedFrom(block.lines.joinToString("\n")),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                        modifier = Modifier.weight(1f),
                    )
                }

                MdBlock.Rule -> Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(colors.hairline)
                )

                // 正文图片：只渲染解析层判定过的（服务端绝对路径 + 位图扩展名）。
                // 没有 AppState（纯展示场景）降级成链接，跟 http 图一个待遇。
                is MdBlock.Image ->
                    if (state != null) {
                        PathImage(state, block.target, block.alt)
                    } else {
                        MarkdownParagraph("[${block.alt}](${block.target})", state)
                    }

                is MdBlock.Table -> MarkdownTable(block, state)
            }
        }
    }
}

@Composable
private fun headingStyle(level: Int) = when (level) {
    1 -> MaterialTheme.typography.headlineSmall.copy(fontSize = 20.sp, lineHeight = 28.sp)
    2 -> MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold)
    3 -> MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold)
    else -> MaterialTheme.typography.titleSmall.copy(fontSize = 13.5.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium)
}

/**
 * 会话内链接的统一出口（P1-2）：工作区文件（服务端绝对路径，可带 `:行号` /
 * `#L行号` 后缀）应用内打开并定位；其余交给浏览器。
 *
 * [state] 为 null（文件页预览等无状态场景）一律走浏览器兜底。
 */
internal fun openChatLink(state: AppState?, url: String) {
    val target = io.github.westanke.dshdesktop.data.classifyChatLink(url)
    if (state == null || target.isExternal) {
        openExternal(url)
    } else {
        state.openFileByPath(target.path, target.lineStart)
    }
}

/** 段落：整段一个 Text，链接可点（点空白处不做事）。 */
@Composable
private fun MarkdownParagraph(text: String, state: AppState? = null) {
    val annotated = annotatedFrom(text)
    var layout by remember(annotated) { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        annotated,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        onTextLayout = { layout = it },
        modifier = Modifier.pointerInput(annotated) {
            detectTapGestures { position ->
                val result = layout ?: return@detectTapGestures
                val offset = result.getOffsetForPosition(position)
                annotated.getStringAnnotations(URL_TAG, offset, offset)
                    .firstOrNull()
                    ?.let { openChatLink(state, it.item) }
            }
        },
    )
}

const val URL_TAG: String = "URL"

/**
 * 行内标记 → AnnotatedString。
 *
 * 链接用 `getStringAnnotations("URL")` 打标记，点击判定在调用方用 TextLayoutResult 做；
 * 不用 `LinkAnnotation` 是为了不依赖高版本 API —— 桌面端 1.7.3 上那条路的点击行为没验过。
 */
@Composable
fun annotatedFrom(text: String): AnnotatedString {
    val colors = LocalAppColors.current
    val linkColor = MaterialTheme.colorScheme.primary
    return remember(text, colors.isDark, linkColor) {
        val spans = parseInline(text)
        buildAnnotatedString {
            spans.forEach { span ->
                val start = length
                pushStyle(
                    SpanStyle(
                        fontWeight = if (span.bold) FontWeight.SemiBold else null,
                        fontStyle = if (span.italic) FontStyle.Italic else null,
                        fontFamily = if (span.code) FontFamily.Monospace else null,
                        fontSize = if (span.code) 12.5.sp else androidx.compose.ui.unit.TextUnit.Unspecified,
                        background = if (span.code) colors.codeBackground else androidx.compose.ui.graphics.Color.Unspecified,
                        textDecoration = when {
                            span.url != null -> TextDecoration.Underline
                            span.strike -> TextDecoration.LineThrough
                            else -> null
                        },
                        color = if (span.url != null) linkColor else androidx.compose.ui.graphics.Color.Unspecified,
                    )
                )
                append(span.text)
                pop()
                if (span.url != null) addStringAnnotation(URL_TAG, span.url, start, length)
            }
        }
    }
}

// ── 表格 ─────────────────────────────────────────────────────────────────────

/**
 * GFM 管道表。
 *
 * 列宽按内容估算（中文字宽 ≈ 2 个西文字符），整表包在横向滚动里：
 * 窄表自然铺开，宽表可以左右拖 —— 桌面端有鼠标，比压成竖条强。
 */
@Composable
private fun MarkdownTable(table: MdBlock.Table, state: AppState? = null) {
    if (table.columns == 0) return
    val colors = LocalAppColors.current
    // 行宽由 data 层保证已归一（MdBlock.Table 的不变量），这里直接用
    val rows = table.rows
    val widths = remember(table) { columnWidths(table, rows) }
    val totalWidth = widths.sum() + 2

    Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        Column(
            Modifier
                .width(totalWidth.dp)
                .border(1.dp, colors.hairline, RoundedCornerShape(Tokens.radiusSm))
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Tokens.radiusSm)),
        ) {
            Row(Modifier.background(colors.raisedSurface)) {
                table.headers.forEachIndexed { column, header ->
                    TableCell(
                        text = header,
                        width = widths[column],
                        align = table.aligns.getOrElse(column) { ColumnAlign.Start },
                        bold = true,
                        state = state,
                    )
                }
            }
            rows.forEachIndexed { index, row ->
                Box(Modifier.fillMaxWidth().height(1.dp).background(colors.hairline))
                Row(
                    Modifier.background(
                        if (index % 2 == 1) colors.mutedSurface else MaterialTheme.colorScheme.surface
                    )
                ) {
                    row.forEachIndexed { column, cell ->
                        TableCell(
                            text = cell,
                            width = widths[column],
                            align = table.aligns.getOrElse(column) { ColumnAlign.Start },
                            bold = false,
                            state = state,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TableCell(text: String, width: Int, align: ColumnAlign, bold: Boolean, state: AppState? = null) {
    val annotated = annotatedFrom(text)
    val colors = LocalAppColors.current
    var layout by remember(annotated) { mutableStateOf<TextLayoutResult?>(null) }
    Box(
        Modifier
            .width(width.dp)
            .padding(horizontal = 9.dp, vertical = 7.dp),
    ) {
        Text(
            annotated,
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color = if (bold) colors.textSecondary else MaterialTheme.colorScheme.onSurface,
            textAlign = when (align) {
                ColumnAlign.Start -> TextAlign.Start
                ColumnAlign.Center -> TextAlign.Center
                ColumnAlign.End -> TextAlign.End
            },
            onTextLayout = { layout = it },
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(annotated) {
                    detectTapGestures { position ->
                        val result = layout ?: return@detectTapGestures
                        val offset = result.getOffsetForPosition(position)
                        annotated.getStringAnnotations(URL_TAG, offset, offset)
                            .firstOrNull()
                            ?.let { openChatLink(state, it.item) }
                    }
                },
        )
    }
}

/**
 * 列宽估算：`min(320, max(80, 24 + 内容宽度))`。
 *
 * 中文按 2 个西文字符算宽度 —— 13.5sp 的字在 96dpi 下大约就是这个比例，
 * 估歪了也只是多几像素空白，不会把字挤掉（`Text` 会换行）。
 */
private fun columnWidths(table: MdBlock.Table, rows: List<List<String>>): List<Int> =
    List(table.columns) { column ->
        val header = visualWidth(table.headers.getOrElse(column) { "" })
        val body = rows.maxOfOrNull { visualWidth(it.getOrElse(column) { "" }) } ?: 0
        (24 + 7 * maxOf(header, body)).coerceIn(80, 320)
    }

/** 中文字算 2、西文算 1。用码点判断，避免把 emoji 的代理对拆坏。 */
private fun visualWidth(text: String): Int {
    var width = 0
    var cursor = 0
    while (cursor < text.length) {
        val code = text.codePointAt(cursor)
        width += if (code > 0x2E80) 2 else 1
        cursor += Character.charCount(code)
    }
    return width
}

// ── 代码块 ───────────────────────────────────────────────────────────────────

/**
 * 代码块：等宽字体 + 独立底色 + 横向滚动（不折行，避免把代码揉成一团）
 * + **纵向滚动**（长脚本以前会被直接裁掉，看不到后半截）。
 */
@Composable
fun CodeBlock(code: String, language: String? = null, maxHeight: Int = 420) {
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    var copied by remember(code) { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.codeBackground, RoundedCornerShape(Tokens.radiusSm))
            .border(1.dp, colors.hairline, RoundedCornerShape(Tokens.radiusSm)),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                language?.takeIf { it.isNotBlank() } ?: "code",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textTertiary,
                modifier = Modifier.weight(1f),
            )
            Surface(
                onClick = {
                    copyToClipboard(code.trimEnd())
                    copied = true
                    scope.launch {
                        kotlinx.coroutines.delay(1400)
                        copied = false
                    }
                },
                shape = RoundedCornerShape(Tokens.radiusSm),
                color = androidx.compose.ui.graphics.Color.Transparent,
            ) {
                Text(
                    if (copied) "已复制" else "复制",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (copied) colors.success else colors.textTertiary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
        Box(Modifier.fillMaxWidth().heightIn(max = maxHeight.dp)) {
            Box(Modifier.horizontalScroll(rememberScrollState()).verticalScroll(rememberScrollState())) {
                SelectionContainer {
                    Text(
                        text = code.trimEnd(),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.5.sp,
                            lineHeight = 19.sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 12.dp),
                    )
                }
            }
        }
    }
}

/** 复制到系统剪贴板；AWT 在无头环境会抛，这里尽力而为。 */
internal fun copyToClipboard(text: String) {
    runCatching {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
    }
}
