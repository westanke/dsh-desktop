package io.github.westanke.dshdesktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import io.github.westanke.dshdesktop.AppState
import io.github.westanke.dshdesktop.ChatPanel
import io.github.westanke.dshdesktop.Nav
import io.github.westanke.dshdesktop.data.ChatItem
import io.github.westanke.dshdesktop.data.ModelSelection
import io.github.westanke.dshdesktop.chooseAgentPreset
import io.github.westanke.dshdesktop.data.JobStatus
import io.github.westanke.dshdesktop.loadAgentPresets
import io.github.westanke.dshdesktop.openAbsolutePath
import io.github.westanke.dshdesktop.refreshJobs
import io.github.westanke.dshdesktop.resyncHistory
import io.github.westanke.dshdesktop.seqGapHint
import io.github.westanke.dshdesktop.stats

/**
 * 会话页：顶部面包屑 + 居中正文列 + 底部输入区。
 *
 * 三条来自官方 Web 端的结构决定：
 * 1. **正文收窄居中**（最大 860dp）。整屏宽的开阔行，中文读起来眼睛要来回扫。
 * 2. **助手消息不挂"助手"标签**：左右对齐 + 间距已经说明了谁在说话，标签是冗余噪音。
 * 3. **统计一行压在输入框下面**，不占消息流的第一格——它描述的是"这一屏"，不是一条消息。
 */
@Composable
fun ChatScreen(state: AppState) {
    val session = state.selectedSession
    val listState = rememberLazyListState()
    val colors = LocalAppColors.current

    Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Column(Modifier.weight(1f).fillMaxHeight()) {
        ChatTopBar(state)
        if (session == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    title = "没有选中会话",
                    hint = "左侧列表里选一个，或点「新会话」用工作区新建一个。",
                )
            }
            return@Column
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            val streaming = state.streamingText.isNotEmpty() || state.streamingReasoning.isNotEmpty()
            val total = state.chatItems.size + if (streaming) 1 else 0
            // 列表前面可能还有"加载更早"一格，滚到底要带上前导偏移
            val leading = if (state.historyHasMore && state.chatItems.isNotEmpty()) 1 else 0

            LaunchedEffect(total, state.streamingText.length, state.streamingReasoning.length) {
                if (total > 0) listState.animateScrollToItem(leading + total - 1)
            }

            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxHeight().widthIn(max = Tokens.contentMaxWidth).fillMaxWidth(),
                    contentPadding = PaddingValues(start = 28.dp, end = 28.dp, top = 18.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (state.historyHasMore && state.chatItems.isNotEmpty()) {
                        item(key = "load-older") {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                if (state.loadingOlder) {
                                    Text(
                                        "正在加载更早的消息…",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = colors.textTertiary,
                                    )
                                } else {
                                    TextButton(onClick = { state.loadOlderHistory() }) {
                                        Text("加载更早的消息", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }
                    }
                    itemsIndexed(state.chatItems, key = { _, item -> item.id }) { _, item ->
                        MessageRow(state, item)
                    }
                    if (streaming) {
                        item(key = "streaming") {
                            StreamingBubble(state.streamingReasoning, state.streamingText)
                        }
                    }
                    if (state.approvals.isNotEmpty()) {
                        item(key = "approvals") {
                            ApprovalPanel(
                                items = state.approvals,
                                busy = state.approvalsBusy,
                                onDecide = { id, allow -> state.decideApproval(id, allow) },
                            )
                        }
                    } else if (state.pendingApprovals > 0) {
                        item(key = "approvals-count") {
                            NoticeBar("有 ${state.pendingApprovals} 个操作等待批准，但详情没能取到。点右上角刷新再试。")
                        }
                    }
                    if (state.seqGap > 0) {
                        item(key = "seq-gap") {
                            NoticeBar(
                                (state.seqGapHint() ?: "") + " 点这里补最新一页。",
                                onAction = { state.resyncHistory() },
                                actionLabel = "重取",
                            )
                        }
                    }
                    if (state.chatError != null) {
                        item(key = "error") {
                            NoticeBar(state.chatError.orEmpty(), tone = "error")
                        }
                    }
                }

                if (state.loadingHistory && state.chatItems.isEmpty()) {
                    LoadingBlock("正在读取历史…")
                } else if (state.chatItems.isEmpty() && state.chatError == null && !streaming) {
                    // 空会话 / 只有内部事件的会话：以前这里整片白，看起来像"坏了"
                    EmptyState(
                        title = "这个会话还没有可显示的消息",
                        hint = "历史里只有内部事件（步骤、任务、token 记录），这些不渲染成消息。发一条就会出现在这里。",
                    )
                }
            }
        }

        Composer(state)
        }
        ChatSidePanel(state)
    }
}

/** 面包屑头：`会话标题 / 工作区`，右侧是模型、刷新、更多、设置。 */
@Composable
private fun ChatTopBar(state: AppState) {
    val colors = LocalAppColors.current
    val session = state.selectedSession
    var more by remember { mutableStateOf(false) }
    // 预设清单只在打开菜单时才要，进页面就拉等于每个会话多一次请求
    LaunchedEffect(more) { if (more) state.loadAgentPresets() }

    Row(
        Modifier.fillMaxWidth().height(56.dp).padding(start = 24.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(
                session?.displayTitle ?: "会话",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val crumb = session?.let { it.workspaceTitle ?: it.cwd.substringAfterLast('/') }?.takeIf { it.isNotBlank() }
            if (crumb != null) {
                Text(
                    "  /  ",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textTertiary,
                )
                Text(
                    crumb,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 220.dp),
                )
            }
        }
        if (session != null) {
            ChatPanelToggles(state)
            Spacer(Modifier.width(4.dp))
            ModelPicker(state)
            Spacer(Modifier.width(2.dp))
            IconButton(onClick = { state.loadHistory(session.id) }, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Filled.Refresh, "重新载入", tint = colors.textSecondary, modifier = Modifier.size(16.dp))
            }
            Box {
                IconButton(onClick = { more = true }, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Filled.MoreHoriz, "更多", tint = colors.textSecondary, modifier = Modifier.size(18.dp))
                }
                DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                    DropdownMenuItem(
                        text = { Text("复制会话 ID", style = MaterialTheme.typography.bodyMedium) },
                        onClick = {
                            copyToClipboard(session.id)
                            state.showToast("已复制会话 ID")
                            more = false
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("在文件页打开工作目录", style = MaterialTheme.typography.bodyMedium) },
                        onClick = {
                            state.openAbsolutePath(session.cwdAbsolute ?: session.cwd)
                            more = false
                        },
                    )
                    HorizontalDivider(color = colors.hairline)
                    Text(
                        "Agent 预设",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textTertiary,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
                    )
                    if (state.agentPresetsError != null) {
                        Text(
                            state.agentPresetsError.orEmpty(),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.danger,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                        )
                    }
                    if (state.agentPresets.isEmpty()) {
                        Text(
                            "没有读到预设清单（缺 agent-presets-v1 能力）",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textTertiary,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                        )
                    }
                    state.agentPresets.forEach { preset ->
                        val on = preset.id == session.agentPreset
                        DropdownMenuItem(
                            enabled = preset.available,
                            text = {
                                Column {
                                    Text(
                                        preset.name + if (preset.isDefault) "（默认）" else "",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (on) MaterialTheme.colorScheme.primary else Color.Unspecified,
                                    )
                                    preset.description?.takeIf { it.isNotBlank() }?.let {
                                        Text(
                                            it,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = colors.textTertiary,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    if (!preset.available) {
                                        Text(
                                            "不可用（trust: ${preset.trust}）",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = colors.warning,
                                        )
                                    }
                                }
                            },
                            onClick = {
                                more = false
                                if (!on) state.chooseAgentPreset(preset.id)
                            },
                        )
                    }
                    HorizontalDivider(color = colors.hairline)
                    DropdownMenuItem(
                        text = { Text("归档这个会话", style = MaterialTheme.typography.bodyMedium, color = colors.danger) },
                        onClick = {
                            state.archiveSession(session.id)
                            more = false
                        },
                    )
                }
            }
        }
        IconButton(onClick = { state.nav = Nav.Settings }, modifier = Modifier.size(30.dp)) {
            Icon(Icons.Filled.Settings, "设置", tint = colors.textSecondary, modifier = Modifier.size(16.dp))
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(colors.hairline))
}

@Composable
private fun NoticeBar(
    text: String,
    tone: String = "warning",
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = LocalAppColors.current
    Surface(
        color = if (tone == "error") MaterialTheme.colorScheme.errorContainer else colors.mutedSurface,
        shape = RoundedCornerShape(Tokens.radiusMd),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = if (tone == "error") {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    colors.textSecondary
                },
                modifier = Modifier.weight(1f),
            )
            if (actionLabel != null && onAction != null) {
                TextButton(onClick = onAction) {
                    Text(actionLabel, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

/**
 * 抽屉开关：三个小胶囊，带计数。
 *
 * 不用图标是因为 material-icons-core 里没有"待办/轨迹"这两颗合适的图标，
 * 而为了两个图标引入整套 extended 图标库会让包大好几兆。胶囊还多带一个数字，
 * 比图标更能说明"这里现在有没有东西看"。
 */
@Composable
private fun ChatPanelToggles(state: AppState) {
    val colors = LocalAppColors.current
    val entries = listOf(
        ChatPanel.Todos to state.todoItems.count { !it.done },
        ChatPanel.Trace to state.trace.size,
        ChatPanel.Jobs to state.jobs.count { it.status == JobStatus.RUNNING },
    )
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        entries.forEach { (panel, count) ->
            val on = state.chatPanel == panel
            Surface(
                onClick = {
                    state.chatPanel = if (on) null else panel
                    if (panel == ChatPanel.Jobs && !on) state.refreshJobs()
                },
                shape = RoundedCornerShape(50),
                color = if (on) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
            ) {
                Text(
                    if (count > 0) "${panel.label} $count" else panel.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (on) MaterialTheme.colorScheme.primary else colors.textTertiary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun ModelPicker(state: AppState) {
    var expanded by remember { mutableStateOf(false) }
    val models = state.sessionModels
    val current = models?.current
    val colors = LocalAppColors.current

    Box {
        Surface(
            onClick = { expanded = true },
            shape = RoundedCornerShape(Tokens.radiusSm),
            color = colors.mutedSurface,
            modifier = Modifier.height(28.dp),
        ) {
            Row(
                Modifier.padding(horizontal = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    current?.let { "${it.model}${it.reasoningEffort?.let { e -> " · $e" } ?: ""}" } ?: "模型",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(Icons.Filled.KeyboardArrowDown, null, tint = colors.textTertiary, modifier = Modifier.size(15.dp))
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (models == null || models.groups.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("没有可用的模型目录", style = MaterialTheme.typography.bodySmall) },
                    onClick = { expanded = false },
                    enabled = false,
                )
            }
            models?.groups?.forEach { group ->
                Text(
                    group.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textTertiary,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                )
                group.models.forEach { model ->
                    val selected = current?.provider == group.id && current.model == model.id
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(
                                    model.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (selected) MaterialTheme.colorScheme.primary else Color.Unspecified,
                                )
                                if (model.description != null) {
                                    Text(
                                        model.description,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.textTertiary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                // 推理等级：不另开一层菜单，直接摊在模型下面。
                                // 内核只给"这个模型支持哪几档"，切档就是换 ModelSelection 的第三个字段。
                                val efforts = model.reasoning?.efforts.orEmpty()
                                if (efforts.isNotEmpty()) {
                                    Row(
                                        Modifier.padding(top = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        efforts.forEach { effort ->
                                            val on = selected && current?.reasoningEffort == effort.id
                                            Surface(
                                                onClick = {
                                                    expanded = false
                                                    state.selectModel(
                                                        ModelSelection(group.id, model.id, effort.id),
                                                    )
                                                },
                                                shape = RoundedCornerShape(50),
                                                color = if (on) {
                                                    MaterialTheme.colorScheme.primaryContainer
                                                } else {
                                                    colors.mutedSurface
                                                },
                                            ) {
                                                Text(
                                                    effort.name,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (on) {
                                                        MaterialTheme.colorScheme.primary
                                                    } else {
                                                        colors.textSecondary
                                                    },
                                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        onClick = {
                            expanded = false
                            state.selectModel(ModelSelection(group.id, model.id, model.reasoning?.defaultEffort))
                        },
                    )
                }
                HorizontalDivider(color = colors.hairline)
            }
        }
    }
}

@Composable
private fun StreamingBubble(reasoning: String, text: String) {
    val colors = LocalAppColors.current
    Column(Modifier.fillMaxWidth()) {
        if (reasoning.isNotEmpty()) {
            CollapsibleBlock(
                title = "思考中",
                body = reasoning,
                tone = colors.textSecondary,
                initiallyExpanded = false,
            )
            Spacer(Modifier.size(8.dp))
        }
        if (text.isNotEmpty()) {
            MarkdownText(text)
        }
        Spacer(Modifier.size(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StatusDot(MaterialTheme.colorScheme.primary, 5)
            Text("正在生成…", style = MaterialTheme.typography.labelSmall, color = colors.textTertiary)
        }
    }
}

/** 可折叠的等宽文本块：思考内容、工具分组都用它。 */
@Composable
internal fun CollapsibleBlock(
    title: String,
    body: String,
    tone: Color,
    initiallyExpanded: Boolean = false,
    trailing: String? = null,
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    val colors = LocalAppColors.current
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
            // 用图标而不是 ▸/▾ 字符：Linux 默认字体经常缺这两个字形，会退化成小圆点
            Icon(
                if (expanded) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = if (expanded) "收起" else "展开",
                tint = colors.textTertiary,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                color = tone,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (trailing != null) {
                Text(trailing, style = MaterialTheme.typography.labelSmall, color = colors.textTertiary)
            }
        }
        if (expanded) {
            Box(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                Text(
                    body.ifBlank { "（无内容）" },
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                    ),
                    color = colors.textSecondary,
                    modifier = Modifier.padding(start = 30.dp, end = 12.dp, bottom = 12.dp),
                )
            }
        }
    }
}

/**
 * 一组相邻的工具调用。
 *
 * 折叠态只给一行摘要（工具名 + 次数），展开后每条仍是各自的专用块
 * （read 给路径头、bash 给 `$ 命令`、edit 给 `-`/`+` 对照）。
 */
@Composable
private fun ToolGroupBlock(group: ChatItem.ToolGroup) {
    var expanded by remember { mutableStateOf(false) }
    val colors = LocalAppColors.current
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
            if (group.hasError) {
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

@Composable
private fun MessageRow(state: AppState, item: ChatItem) {
    val colors = LocalAppColors.current
    when (item) {
        is ChatItem.User -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Surface(
                color = colors.userBubble,
                shape = RoundedCornerShape(Tokens.radiusLg),
                modifier = Modifier.widthIn(max = 560.dp),
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    // 图片在正文之上：贴图时人先看图再看说了什么
                    if (item.attachments.isNotEmpty()) {
                        ImageStrip(state, state.selectedSessionId, item.attachments)
                        if (item.text.isNotBlank()) Spacer(Modifier.size(8.dp))
                    }
                    if (item.text.isNotBlank()) MarkdownText(item.text)
                }
            }
        }

        is ChatItem.Assistant -> Column(Modifier.fillMaxWidth()) {
            MarkdownText(item.text)
        }

        is ChatItem.Reasoning -> CollapsibleBlock(
            title = "思考",
            body = item.text,
            tone = colors.textSecondary,
        )

        is ChatItem.Tool -> ToolCallBlock(item)

        // 相邻工具调用合并后的一组：默认折叠，展开才是逐条。
        is ChatItem.ToolGroup -> ToolGroupBlock(item)

        is ChatItem.Deliverable -> DeliverableCard(item.files) { state.openAbsolutePath(it) }

        is ChatItem.Notice -> Row(
            Modifier
                .fillMaxWidth()
                .background(colors.mutedSurface, RoundedCornerShape(Tokens.radiusSm))
                .padding(12.dp),
        ) {
            val tone = when (item.level) {
                "error" -> colors.danger
                "warning" -> colors.warning
                else -> colors.textSecondary
            }
            Text(
                item.text,
                style = MaterialTheme.typography.bodySmall,
                color = tone,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
