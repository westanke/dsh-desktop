package io.github.westanke.dshdesktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.westanke.dshdesktop.data.ChatItem
import io.github.westanke.dshdesktop.data.ToolBlockView
import io.github.westanke.dshdesktop.data.formatToolDuration
import io.github.westanke.dshdesktop.data.prettyArguments
import io.github.westanke.dshdesktop.data.toolActionPhrase
import io.github.westanke.dshdesktop.data.toolBlockView

/**
 * 一次工具调用的**成品块**。
 *
 * 默认折叠成一行（对齐手机版）：动作短语 · 关键参数 + 状态/耗时，点击才展开。
 * 展开后按工具类型换布局：`read` 给路径头，`bash` 给 `$ 命令` + 输出，
 * `edit`/`write` 给 `-`/`+` 对照，其余一律通用（格式化参数 + 原样结果）。
 *
 * 分发本身在 `data/ToolBlockView.kt` 里（纯函数，可测）；这里只负责画。
 */
@Composable
fun ToolCallBlock(tool: ChatItem.Tool) {
    val colors = LocalAppColors.current
    // 默认折叠，和手机版（dsh-companion）对齐：一轮十几次工具调用全展开会把
    // 助手正文挤出屏幕，读者只能一路滚。失败的也不自动展开——摘要行已有
    // 红色"失败 · 耗时"提示，想看清楚再点一下；否则一整屏失败块会把会话冲掉。
    var expanded by remember(tool.id) { mutableStateOf(false) }
    val view = remember(tool.id, tool.arguments, tool.result) {
        toolBlockView(tool.name, tool.arguments, tool.result)
    }
    val headline = remember(tool.id, tool.arguments) { toolActionPhrase(tool.name, tool.arguments) }
    val detail = remember(tool.id, tool.arguments, view) { headlineDetail(view) }

    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.mutedSurface, RoundedCornerShape(Tokens.radiusSm)),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (expanded) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = if (expanded) "收起" else "展开",
                tint = colors.textTertiary,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                headline,
                style = MaterialTheme.typography.labelMedium,
                color = if (tool.isError) colors.danger else colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (!detail.isNullOrBlank()) {
                Spacer(Modifier.width(8.dp))
                Text(
                    detail,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = colors.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                statusLabel(tool),
                style = MaterialTheme.typography.labelSmall,
                color = when {
                    tool.result == null -> colors.textTertiary
                    tool.isError -> colors.danger
                    else -> colors.success
                },
            )
        }
        if (expanded) {
            Column(Modifier.padding(start = 26.dp, end = 10.dp, bottom = 10.dp)) {
                when (view) {
                    is ToolBlockView.Read -> ReadBody(view)
                    is ToolBlockView.Terminal -> TerminalBody(view)
                    is ToolBlockView.Diff -> DiffBody(view)
                    is ToolBlockView.Generic -> GenericBody(view, tool)
                }
            }
        }
    }
}

private fun statusLabel(tool: ChatItem.Tool): String {
    val duration = tool.durationMs?.let { formatToolDuration(it) }
    return when {
        tool.result == null -> "运行中"
        tool.isError -> "失败" + (duration?.let { " · $it" } ?: "")
        else -> duration ?: "完成"
    }
}

/** 折叠行右侧那一小段等宽补充信息：路径/命令比动作短语更有辨识度。 */
private fun headlineDetail(view: ToolBlockView): String? = when (view) {
    is ToolBlockView.Read -> view.path.substringAfterLast('/').ifBlank { view.path }
    is ToolBlockView.Terminal -> view.command.lineSequence().firstOrNull()
    is ToolBlockView.Diff -> view.path.substringAfterLast('/').ifBlank { view.path }
    is ToolBlockView.Generic -> null
}

@Composable
private fun MonoScrollable(text: String, maxHeight: Int, tint: Color) {
    Box(Modifier.fillMaxWidth().heightIn(max = maxHeight.dp)) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                lineHeight = 18.sp,
            ),
            color = tint,
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(10.dp),
        )
    }
}

@Composable
private fun ReadBody(view: ToolBlockView.Read) {
    val colors = LocalAppColors.current
    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            view.path,
            style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.primary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        val range = when {
            view.offset != null && view.limit != null -> "行 ${view.offset}–${view.offset + view.limit - 1}"
            view.limit != null -> "前 ${view.limit} 行"
            else -> null
        }
        if (range != null) {
            Text(range, style = MaterialTheme.typography.labelSmall, color = colors.textTertiary)
        }
    }
    when {
        view.body.isBlank() -> Text(
            "（还没有内容）",
            style = MaterialTheme.typography.labelSmall,
            color = colors.textTertiary,
        )

        // result 自带 "N: " 行号前缀时不要再加一列，否则会变成「1: 1: xxx」
        else -> MonoScrollable(view.body.trimEnd(), 420, colors.textSecondary)
    }
}

/**
 * bash 的专用视图：**命令一行 + 输出一段**，长输出默认折起来。
 *
 * 命令单独占一行并保留 `$` 前缀，是为了和它前面的`工具: bash`标题、后面的输出
 * 在视觉上分成三截——混在一起时，长输出会把"到底跑了什么命令"吞掉。
 * 输出超过 6 行就折叠：一条 `npm run build` 动辄几百行，全打印出来这一轮就没法看了。
 */
@Composable
private fun TerminalBody(view: ToolBlockView.Terminal) {
    val colors = LocalAppColors.current
    var expanded by remember { mutableStateOf(false) }
    Text(
        "$ " + view.command.ifBlank { "（没有命令）" },
        style = MaterialTheme.typography.bodySmall.copy(
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            lineHeight = 18.sp,
        ),
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.codeBackground, RoundedCornerShape(Tokens.radiusSm))
            .padding(10.dp),
    )
    view.description?.takeIf { it.isNotBlank() }?.let {
        Text(
            it,
            style = MaterialTheme.typography.labelSmall,
            color = colors.textTertiary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
    Spacer(Modifier.size(4.dp))
    when {
        view.running && view.output.isBlank() -> Text(
            "正在执行…",
            style = MaterialTheme.typography.labelSmall,
            color = colors.textTertiary,
        )

        view.output.isBlank() -> Text(
            "（没有输出）",
            style = MaterialTheme.typography.labelSmall,
            color = colors.textTertiary,
        )

        else -> {
            val lines = view.output.trimEnd().split('\n')
            val foldable = lines.size > 6
            val shown = if (foldable && !expanded) lines.take(6).joinToString("\n") else lines.joinToString("\n")
            Text(
                shown,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                ),
                color = colors.textSecondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = if (expanded) 420.dp else 132.dp)
                    .verticalScroll(rememberScrollState())
                    .background(colors.codeBackground, RoundedCornerShape(Tokens.radiusSm))
                    .padding(10.dp),
            )
            if (foldable) {
                Text(
                    if (expanded) "收起" else "展开全部 ${lines.size} 行",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .clickable { expanded = !expanded },
                )
            }
        }
    }
}

@Composable
private fun DiffBody(view: ToolBlockView.Diff) {
    val colors = LocalAppColors.current
    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            view.path,
            style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (view.replaceAll) {
            Text("全部替换", style = MaterialTheme.typography.labelSmall, color = colors.warning)
        }
        if (view.oldText == null) {
            Text("整文件写入", style = MaterialTheme.typography.labelSmall, color = colors.textTertiary)
        }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 420.dp)
            .verticalScroll(rememberScrollState())
            .background(colors.codeBackground, RoundedCornerShape(Tokens.radiusSm))
            .padding(vertical = 6.dp),
    ) {
        val limit = 200
        val removed = view.oldText?.lines().orEmpty()
        val added = view.newText?.lines().orEmpty()
        DiffLines(removed, "-", colors.danger, limit)
        DiffLines(added, "+", colors.success, limit)
        val hidden = (removed.size - limit).coerceAtLeast(0) + (added.size - limit).coerceAtLeast(0)
        if (hidden > 0) {
            Text(
                "…还有 $hidden 行未显示",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textTertiary,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun DiffLines(lines: List<String>, prefix: String, tint: Color, limit: Int) {
    if (lines.isEmpty()) return
    Column {
        lines.take(limit).forEach { line ->
            Text(
                "$prefix $line",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                ),
                color = tint,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(tint.copy(alpha = 0.07f))
                    .padding(horizontal = 10.dp),
            )
        }
    }
}

@Composable
private fun GenericBody(view: ToolBlockView.Generic, tool: ChatItem.Tool) {
    val colors = LocalAppColors.current
    val args = remember(tool.arguments) { prettyArguments(tool.arguments) }
    if (args.isNotBlank()) {
        MonoScrollable(args, 240, colors.textTertiary)
    }
    Spacer(Modifier.size(4.dp))
    Box(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
        Text(
            tool.result ?: "（还没有返回结果）",
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                lineHeight = 18.sp,
            ),
            color = if (tool.isError) colors.danger else colors.textSecondary,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
    }
}

/**
 * 一组相邻的工具调用。
 *
 * 折叠态只给一行摘要（工具名 + 次数 + 耗时，形态照手机版"耗时 8 秒 · 2 次工具调用"），
 * 展开后每条仍是各自的专用块（read 给路径头、bash 给 `$ 命令`、edit 给 `-`/`+` 对照）。
 */
@Composable
internal fun ToolGroupBlock(group: ChatItem.ToolGroup) {
    var expanded by remember { mutableStateOf(false) }
    val colors = LocalAppColors.current
    // 组内墙钟耗时：逐条 durationMs 求和（串行执行时≈整组耗时）；还有在跑的就标"运行中"
    val durationLabel = when {
        group.tools.isEmpty() -> null
        group.isRunning -> "运行中"
        else -> group.totalDurationMs?.let { "耗时 " + formatToolDuration(it) }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.mutedSurface, RoundedCornerShape(Tokens.radiusSm)),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (expanded) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = if (expanded) "收起" else "展开",
                tint = colors.textTertiary,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                group.title,
                style = MaterialTheme.typography.labelMedium,
                color = if (group.hasError) colors.danger else colors.textSecondary,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (durationLabel != null) {
                Spacer(Modifier.width(8.dp))
                Text(
                    durationLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textTertiary,
                    maxLines = 1,
                )
            }
            if (group.hasError) {
                Spacer(Modifier.width(8.dp))
                Text("含失败", style = MaterialTheme.typography.labelSmall, color = colors.danger)
            }
        }
        if (expanded) {
            Column(
                Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 14.dp, end = 8.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                group.tools.forEach { tool -> ToolCallBlock(tool) }
            }
        }
    }
}

/**
 * 一轮的连续工作段（工具 + 思考），折叠形态照官方 Web：「已完成，用时 20分34秒 ⌄」。
 *
 * 与 [ToolGroupBlock] 的区别：官方不给"工具名 + 次数"，就一行状态——做了什么点开看，
 * 做了多久一眼知道。展开后 children 仍按各自的样子渲染（工具专用块 / 思考折叠）。
 */
@Composable
internal fun TurnRunBlock(run: ChatItem.TurnRun) {
    val colors = LocalAppColors.current
    var expanded by remember(run.id) { mutableStateOf(false) }
    val duration = run.totalDurationMs
    val label = when {
        run.isRunning -> "运行中…"
        duration != null -> "已完成，用时 " + formatToolDuration(duration)
        else -> "已完成"
    }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 2.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = if (expanded) "收起这轮的工作" else "展开这轮的工作",
                tint = colors.textTertiary,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = if (run.hasError) colors.danger else colors.textTertiary,
            )
            if (run.hasError) {
                Spacer(Modifier.width(8.dp))
                Text("含失败", style = MaterialTheme.typography.labelSmall, color = colors.danger)
            }
        }
        if (expanded) {
            Column(
                Modifier.padding(start = 21.dp, top = 2.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                run.children.forEach { child ->
                    when (child) {
                        is ChatItem.Tool -> ToolCallBlock(child)
                        is ChatItem.ToolGroup -> ToolGroupBlock(child)
                        is ChatItem.Reasoning -> CollapsibleBlock(
                            title = "思考",
                            body = child.text,
                            tone = colors.textSecondary,
                        )
                        else -> Unit
                    }
                }
            }
        }
    }
}

/** 用户气泡下的时间戳（HH:mm）；历史里没带时间的（0）就不显示。 */
internal fun messageTime(time: Long): String =
    if (time <= 0) ""
    else java.time.format.DateTimeFormatter.ofPattern("HH:mm")
        .format(java.time.Instant.ofEpochMilli(time).atZone(java.time.ZoneId.systemDefault()))

