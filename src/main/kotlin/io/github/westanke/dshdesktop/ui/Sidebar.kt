package io.github.westanke.dshdesktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.westanke.dshdesktop.AppState
import io.github.westanke.dshdesktop.ConnectionState
import io.github.westanke.dshdesktop.Nav
import io.github.westanke.dshdesktop.data.ChatSession
import io.github.westanke.dshdesktop.data.ChatWorkspace
import io.github.westanke.dshdesktop.data.resolveNewSessionPreset
import io.github.westanke.dshdesktop.deleteWorkspace
import io.github.westanke.dshdesktop.loadPermissionCatalog
import io.github.westanke.dshdesktop.renameWorkspace
import io.github.westanke.dshdesktop.forkSession
import java.text.SimpleDateFormat
import java.util.Date

/**
 * 左侧栏：品牌 + 新会话 + 导航 + 会话列表 + 身份卡。
 *
 * 布局照官方 Web 端（logo 行 → 通栏主按钮 → 图标导航 → 分组标题 → 带时间的会话行 → 底部身份），
 * 但**不抄它的假功能**：没有「轨迹」「上下文」这类我们拿不到数据的入口。
 *
 * 会话行右侧给相对时间而不是工作区名：官方也是这么做的，因为工作区在页头面包屑里
 * 已经写了一遍，列表里再写一遍是噪音。
 */
@Composable
fun Sidebar(state: AppState) {
    val colors = LocalAppColors.current
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var showNew by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<ChatSession?>(null) }
    var renamingWs by remember { mutableStateOf<ChatWorkspace?>(null) }
    // 新建会话时预选的权限档位（P3）；null = 用部署默认。候选与输入区权限 Pill
    // 是同一份目录，不在这里硬编码第二套名单。
    var pendingPreset by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.workspaces.isEmpty()) {
        if (state.workspaces.isEmpty()) state.refreshWorkspaces()
    }

    val visible = remember(state.sessions, query) {
        if (query.isBlank()) state.sessions
        else state.sessions.filter {
            it.displayTitle.contains(query, ignoreCase = true) || it.cwd.contains(query, ignoreCase = true)
        }
    }

    Column(
        Modifier
            .width(Tokens.sidebarWidth)
            .fillMaxHeight()
            .background(colors.sidebar),
    ) {
        BrandRow()
        Spacer(Modifier.height(12.dp))

        // 通栏「新会话」：官方是整条按钮，比图标更像入口
        Box(Modifier.padding(horizontal = 12.dp)) {
            NewSessionButton(enabled = !state.showArchived, onClick = { showNew = true })
            // 菜单一开就把权限目录要过来：Pill 那边是按需拉的，这里等用户点开再要一次，
            // 老桥拿不到目录时下面那段选择器整块不画，零打扰
            LaunchedEffect(showNew) { if (showNew) state.loadPermissionCatalog() }
            DropdownMenu(expanded = showNew, onDismissRequest = { showNew = false }) {
                // P3：新建即选档位。只有"目录里有多个档位"时才值得选，
                // 单个档位画一排拨了没反应的开关纯属噪音
                val presetChoices = remember(state.permissionCatalog) {
                    state.permissionCatalog?.options
                        ?.map { it.value to it.name }
                        ?.takeIf { it.size > 1 }
                        ?: emptyList()
                }
                if (presetChoices.isNotEmpty()) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                        Text(
                            "新建后使用权限档位",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textTertiary,
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            presetChoices.forEach { (value, name) ->
                                val on = value == pendingPreset
                                FilterChip(
                                    selected = on,
                                    onClick = { pendingPreset = if (on) null else value },
                                    label = {
                                        Text(
                                            name,
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1,
                                        )
                                    },
                                )
                            }
                        }
                        Text(
                            "不选则用部署默认档",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textTertiary,
                        )
                    }
                    HorizontalDivider(color = colors.hairline)
                }
                if (state.workspaces.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text("没有可用的工作区", style = MaterialTheme.typography.bodySmall) },
                        onClick = { showNew = false },
                        enabled = false,
                    )
                }
                state.workspaces.forEach { workspace ->
                    // 自己拼行而不用 DropdownMenuItem：行尾要放两个次级动作，
                    // 而 DropdownMenuItem 的整行 onClick 会把它们的点击一起吃进去
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                showNew = false
                                state.createSession(
                                    workspace.id,
                                    resolveNewSessionPreset(state.permissionCatalog, pendingPreset),
                                )
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                workspace.title.ifBlank { workspace.path },
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                workspace.path,
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textTertiary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            "改名",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textTertiary,
                            modifier = Modifier
                                .clickable { showNew = false; renamingWs = workspace }
                                .padding(horizontal = 5.dp, vertical = 2.dp),
                        )
                        Text(
                            "移除",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.danger,
                            modifier = Modifier
                                .clickable { state.deleteWorkspace(workspace.id) }
                                .padding(horizontal = 5.dp, vertical = 2.dp),
                        )
                    }
                }
                HorizontalDivider(color = colors.hairline)
                Text(
                    "改名 / 移除只动 DSH 里的工作区记录，不碰磁盘目录。",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textTertiary,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        NavItem(Icons.Filled.ChatBubbleOutline, "会话", state.nav == Nav.Chat) { state.nav = Nav.Chat }
        NavItem(Icons.Filled.FolderOpen, "文件", state.nav == Nav.Files) { state.nav = Nav.Files }
        // 「模型」「插件」原来是 WebUI 才干得了的事，桌面端补成并列的页，不再让人来回切
        NavItem(Icons.Filled.Memory, "模型", state.nav == Nav.Models) { state.nav = Nav.Models }
        NavItem(Icons.Filled.Extension, "插件", state.nav == Nav.Plugins) { state.nav = Nav.Plugins }
        NavItem(Icons.Filled.Settings, "设置", state.nav == Nav.Settings) { state.nav = Nav.Settings }

        Spacer(Modifier.height(18.dp))
        SectionHeader(
            title = if (state.showArchived) "已归档" else "工作区",
            onSearch = { searching = !searching; if (!searching) query = "" },
            searching = searching,
            onRefresh = { state.refreshSessions() },
            onArchived = { state.showArchivedSessions(!state.showArchived) },
            archived = state.showArchived,
        )

        if (searching) {
            SearchField(query) { query = it }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (visible.isEmpty()) {
                EmptySessionList(state, query.isNotBlank())
            } else {
                LazyColumn(Modifier.fillMaxSize().padding(top = 2.dp)) {
                    items(visible, key = { it.id }) { session ->
                        SessionRow(
                            session = session,
                            selected = session.id == state.selectedSessionId,
                            archived = state.showArchived,
                            onClick = {
                                if (state.nav != Nav.Chat) state.nav = Nav.Chat
                                state.selectSession(session.id)
                            },
                            onRename = { renaming = session },
                            onFork = { state.forkSession(session.id) },
                            onArchive = { state.archiveSession(session.id) },
                            onCopyId = {
                                copyToClipboard(session.id)
                                state.showToast("已复制会话 ID")
                            },
                        )
                    }
                }
            }
        }

        IdentityCard(state)
    }

    renaming?.let { target ->
        RenameDialog(
            initial = target.title.orEmpty(),
            onDismiss = { renaming = null },
            onConfirm = { title ->
                state.renameSession(target.id, title)
                renaming = null
            },
        )
    }

    renamingWs?.let { target ->
        RenameDialog(
            title = "重命名工作区",
            initial = target.title,
            onDismiss = { renamingWs = null },
            onConfirm = { title ->
                state.renameWorkspace(target.id, title)
                renamingWs = null
            },
        )
    }
}

@Composable
private fun BrandRow() {
    val colors = LocalAppColors.current
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(26.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text("DS", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, fontSize = 10.sp)
        }
        Spacer(Modifier.width(9.dp))
        Text(
            "dsh",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            letterSpacing = 0.2.sp,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            "DESKTOP",
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.6.sp,
            color = colors.textTertiary,
        )
    }
}

@Composable
private fun NewSessionButton(enabled: Boolean, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(Tokens.radiusMd),
        color = Color.Transparent,
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.hairline),
        modifier = Modifier.fillMaxWidth().height(36.dp),
    ) {
        Row(
            Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Add, null, tint = colors.textSecondary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                if (enabled) "新会话" else "归档中不可新建",
                style = MaterialTheme.typography.labelLarge,
                color = if (enabled) colors.textSecondary else colors.textTertiary,
            )
        }
    }
}

@Composable
private fun NavItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalAppColors.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val tint = if (selected) MaterialTheme.colorScheme.primary else colors.textSecondary
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 1.dp)
            .background(
                when {
                    selected -> MaterialTheme.colorScheme.primaryContainer
                    hovered -> colors.mutedSurface
                    else -> Color.Transparent
                },
                RoundedCornerShape(Tokens.radiusSm),
            )
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = tint,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
        )
    }
}

@Composable
private fun SectionHeader(
    title: String,
    searching: Boolean,
    archived: Boolean,
    onSearch: () -> Unit,
    onRefresh: () -> Unit,
    onArchived: () -> Unit,
) {
    val colors = LocalAppColors.current
    Row(
        Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.labelSmall,
            color = colors.textTertiary,
            letterSpacing = 0.6.sp,
        )
        Spacer(Modifier.weight(1f))
        MiniAction(Icons.Filled.Search, if (searching) "收起搜索" else "搜索", active = searching, onClick = onSearch)
        MiniAction(Icons.Filled.Refresh, "刷新", active = false, onClick = onRefresh)
        MiniAction(Icons.Filled.Archive, if (archived) "返回活跃" else "已归档", active = archived, onClick = onArchived)
    }
}

@Composable
private fun MiniAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalAppColors.current
    IconButton(onClick = onClick, modifier = Modifier.size(24.dp)) {
        Icon(
            icon,
            label,
            tint = if (active) MaterialTheme.colorScheme.primary else colors.textTertiary,
            modifier = Modifier.size(14.dp),
        )
    }
}

@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        singleLine = true,
        placeholder = { Text("搜索标题或路径", style = MaterialTheme.typography.bodySmall) },
        textStyle = MaterialTheme.typography.bodySmall,
        shape = RoundedCornerShape(Tokens.radiusSm),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
    )
}

@Composable
private fun EmptySessionList(state: AppState, filtering: Boolean) {
    val colors = LocalAppColors.current
    val error = state.sessionsError
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        when {
            error != null -> {
                Text("会话列表加载失败", style = MaterialTheme.typography.labelMedium, color = colors.danger)
                Spacer(Modifier.height(4.dp))
                Text(error, style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
                TextButton(onClick = { state.refreshSessions() }) {
                    Text("重试", style = MaterialTheme.typography.labelMedium)
                }
            }
            filtering -> Text("没有匹配的会话", style = MaterialTheme.typography.bodySmall, color = colors.textTertiary)
            state.showArchived -> Text("没有归档的会话", style = MaterialTheme.typography.bodySmall, color = colors.textTertiary)
            else -> {
                Text("还没有会话", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                Spacer(Modifier.height(2.dp))
                Text("点上面的「新会话」选一个工作区开始", style = MaterialTheme.typography.labelSmall, color = colors.textTertiary)
            }
        }
    }
}

@Composable
private fun SessionRow(
    session: ChatSession,
    selected: Boolean,
    archived: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onFork: () -> Unit,
    onArchive: () -> Unit,
    onCopyId: () -> Unit,
) {
    val colors = LocalAppColors.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 1.dp)
            .background(
                when {
                    selected -> MaterialTheme.colorScheme.primaryContainer
                    hovered -> colors.mutedSurface
                    else -> Color.Transparent
                },
                RoundedCornerShape(Tokens.radiusSm),
            )
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(start = 10.dp, end = 4.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) {
            Box(
                Modifier
                    .size(width = 2.dp, height = 14.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
            )
            Spacer(Modifier.width(7.dp))
        }
        Text(
            session.displayTitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (session.running) {
            Spacer(Modifier.width(6.dp))
            StatusDot(colors.success, 5)
        }
        if (session.hasPendingApproval) {
            Spacer(Modifier.width(6.dp))
            StatusDot(colors.warning, 5)
        }
        if (hovered || menuOpen || selected) {
            Box {
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(22.dp)) {
                    Icon(Icons.Filled.MoreVert, "会话操作", tint = colors.textTertiary, modifier = Modifier.size(14.dp))
                }
                SessionMenu(
                    expanded = menuOpen,
                    archived = archived,
                    onDismiss = { menuOpen = false },
                    onRename = { menuOpen = false; onRename() },
                    onFork = { menuOpen = false; onFork() },
                    onCopyId = { menuOpen = false; onCopyId() },
                    onArchive = { menuOpen = false; onArchive() },
                )
            }
        } else {
            Text(
                relativeTime(session.updatedAt),
                style = MaterialTheme.typography.labelSmall,
                color = colors.textTertiary,
                modifier = Modifier.padding(start = 6.dp, end = 4.dp),
            )
        }
    }
}

@Composable
private fun SessionMenu(
    expanded: Boolean,
    archived: Boolean,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onFork: () -> Unit,
    onCopyId: () -> Unit,
    onArchive: () -> Unit,
) {
    val colors = LocalAppColors.current
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text("重命名", style = MaterialTheme.typography.bodyMedium) },
            onClick = onRename,
        )
        DropdownMenuItem(
            text = { Text("分叉出新会话", style = MaterialTheme.typography.bodyMedium) },
            onClick = onFork,
        )
        DropdownMenuItem(
            text = { Text("复制会话 ID", style = MaterialTheme.typography.bodyMedium) },
            onClick = onCopyId,
        )
        if (!archived) {
            HorizontalDivider(color = colors.hairline)
            DropdownMenuItem(
                text = { Text("归档", style = MaterialTheme.typography.bodyMedium, color = colors.danger) },
                onClick = onArchive,
            )
        }
    }
}

@Composable
private fun IdentityCard(state: AppState) {
    val colors = LocalAppColors.current
    val (tone, label) = when (state.connection) {
        ConnectionState.Connected -> colors.success to "在线"
        ConnectionState.Connecting -> colors.warning to "连接中"
        ConnectionState.Failed -> colors.danger to "已断开"
        ConnectionState.Disconnected -> colors.textTertiary to "未连接"
    }
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 10.dp)
            .background(colors.mutedSurface, RoundedCornerShape(Tokens.radiusMd))
            .clickable { state.nav = Nav.Settings }
            .padding(horizontal = 10.dp, vertical = 9.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(24.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    (state.profile.deviceName.firstOrNull() ?: 'D').uppercase(),
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    state.profile.deviceName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(tone, 5)
                    Spacer(Modifier.width(5.dp))
                    Text(label, style = MaterialTheme.typography.labelSmall, color = colors.textTertiary)
                }
            }
        }
    }
}

@Composable
private fun RenameDialog(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    title: String = "重命名会话",
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        text = {
            TextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                placeholder = { Text("留空则跟随工作目录", style = MaterialTheme.typography.bodySmall) },
                shape = RoundedCornerShape(Tokens.radiusSm),
                textStyle = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) {
                        onConfirm(text.trim())
                        true
                    } else false
                },
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text.trim()) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/**
 * 相对时间：列表里只需要"多久以前"，精确到分就够了。
 *
 * `updatedAt` 的**单位是毫秒还是秒**，不同版本的上游服务端都出现过；
 * 用 1e12 做一次兜底判断，比在某处硬编码 `/1000` 稳。
 */
internal fun relativeTime(updatedAt: Long): String {
    if (updatedAt <= 0) return ""
    val ms = if (updatedAt < 1_000_000_000_000L) updatedAt * 1000 else updatedAt
    val diff = System.currentTimeMillis() - ms
    if (diff < 0) return "刚刚"
    val minutes = diff / 60_000
    return when {
        minutes < 1 -> "刚刚"
        minutes < 60 -> "${minutes}分钟"
        minutes < 60 * 24 -> "${minutes / 60}小时"
        minutes < 60 * 24 * 30 -> "${minutes / (60 * 24)}天"
        else -> SimpleDateFormat("M月d日", java.util.Locale.CHINA).format(Date(ms))
    }
}
