package io.github.westanke.dshdesktop.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.westanke.dshdesktop.AppState
import io.github.westanke.dshdesktop.createEntry
import io.github.westanke.dshdesktop.deleteEntry
import io.github.westanke.dshdesktop.downloadEntry
import io.github.westanke.dshdesktop.openDirectory
import io.github.westanke.dshdesktop.openEntryExternally
import io.github.westanke.dshdesktop.moveEntry
import io.github.westanke.dshdesktop.previewEntry
import io.github.westanke.dshdesktop.refreshDirectory
import io.github.westanke.dshdesktop.refreshTrash
import io.github.westanke.dshdesktop.restoreTrash
import io.github.westanke.dshdesktop.saveDraft
import io.github.westanke.dshdesktop.startEditing
import io.github.westanke.dshdesktop.cancelEditing
import io.github.westanke.dshdesktop.uploadFile
import io.github.westanke.dshdesktop.data.FileEntry
import io.github.westanke.dshdesktop.data.FileKind
import io.github.westanke.dshdesktop.data.FileType
import io.github.westanke.dshdesktop.data.PreviewExtra
import io.github.westanke.dshdesktop.data.classifyFile
import io.github.westanke.dshdesktop.data.formatFileSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Cursor
import java.io.File
import javax.swing.JFileChooser

/**
 * 文件页。
 *
 * 桥接的 2.2.0 起桌面端不再带文件界面（独立页被删），所有文件操作都在手机 App 里。
 * 这个客户端把同一套 REST 能力接回桌面：浏览、预览、上传、下载、移入回收站。
 * 注意所有路径都是 `rootId + 相对路径`，客户端永远拿不到、也不该拿服务器绝对路径。
 */
@Composable
fun FilesScreen(state: AppState) {
    val colors = LocalAppColors.current
    var showNewDialog by remember { mutableStateOf(false) }
    var newIsDir by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var showAddMenu by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        PageHeader(
            title = "文件",
            subtitle = state.roots.firstOrNull { it.id == state.activeRootId }?.let {
                "${it.label} · /${state.currentPath.trimStart('/')}".trimEnd('/')
            } ?: "未获得任何文件根授权",
        ) {
            // 目录栏开关：从文件卡跳过来时它是收起的，想回目录里翻就点这里
            IconButton(
                onClick = { state.filesShowList = !state.filesShowList },
                modifier = Modifier.size(30.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.List,
                    if (state.filesShowList) "收起目录" else "显示目录",
                    tint = if (state.filesShowList) MaterialTheme.colorScheme.primary else colors.textSecondary,
                    modifier = Modifier.size(16.dp),
                )
            }
            IconButton(onClick = { state.refreshDirectory() }, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Filled.Refresh, "刷新", tint = colors.textSecondary, modifier = Modifier.size(16.dp))
            }
            Box {
                IconButton(
                    onClick = { showAddMenu = true },
                    enabled = state.activeRootId != null,
                    modifier = Modifier.size(30.dp),
                ) {
                    Icon(Icons.Filled.Add, "新建或上传", tint = colors.textSecondary, modifier = Modifier.size(18.dp))
                }
                DropdownMenu(expanded = showAddMenu, onDismissRequest = { showAddMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("新建文件夹", style = MaterialTheme.typography.bodyMedium) },
                        onClick = {
                            showAddMenu = false
                            newIsDir = true; newName = ""; showNewDialog = true
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("新建空文件", style = MaterialTheme.typography.bodyMedium) },
                        onClick = {
                            showAddMenu = false
                            newIsDir = false; newName = ""; showNewDialog = true
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("上传本地文件…", style = MaterialTheme.typography.bodyMedium) },
                        onClick = {
                            showAddMenu = false
                            val chooser = JFileChooser()
                            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                                chooser.selectedFile?.let { state.uploadFile(it) }
                            }
                        },
                    )
                }
            }
        }

        // 授权根改成顶部横向 chip：侧栏已经把左边占掉了，再竖一列根目录会把列表挤成一条缝
        RootChips(state)
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.hairline))

        // 左目录 / 右内容两栏，中间一条**能拖**的分隔条。
        // 以前右边钉死 430dp：窗口一宽就左宽右窄，长表格和宽代码看不全，
        // 而"左右调不动"正是长内容最难熬的地方。
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val totalPx = with(LocalDensity.current) { maxWidth.toPx() }.takeIf { it > 0f }
            Row(Modifier.fillMaxSize()) {
                if (state.filesShowList) {
                    FileListPane(state, Modifier.weight(state.filesSplitRatio))
                    Splitter(
                        onDrag = { dxPx ->
                            val total = totalPx ?: return@Splitter
                            state.filesSplitRatio =
                                (state.filesSplitRatio + dxPx / total).coerceIn(0.18f, 0.72f)
                        },
                    )
                }
                PreviewPane(state, Modifier.weight(1f))
            }
        }
    }

    if (showNewDialog) {
        AlertDialog(
            onDismissRequest = { showNewDialog = false },
            title = { Text(if (newIsDir) "新建文件夹" else "新建文件") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    label = { Text("名称") },
                    shape = RoundedCornerShape(Tokens.radiusSm),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newName.isNotBlank()) state.createEntry(newName.trim(), newIsDir)
                        showNewDialog = false
                    },
                ) { Text("创建") }
            },
            dismissButton = { TextButton(onClick = { showNewDialog = false }) { Text("取消") } },
        )
    }
}

/**
 * 左右两栏之间的可拖动分隔条。
 *
 * 拖的是**比例**不是像素：窗口拉宽时两栏一起变宽，比例保持，不用重新调。
 * 上下限卡在 0.18 / 0.72 —— 拖到 0 会让目录栏消失后拉不回来，拖到 1 则反过来看不见内容。
 */
@Composable
private fun Splitter(onDrag: (Float) -> Unit) {
    val colors = LocalAppColors.current
    var dragging by remember { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxHeight()
            .width(10.dp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false },
                ) { _, amount -> onDrag(amount.x) }
            }
            .pointerHoverIcon(PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR))),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .width(if (dragging) 2.dp else 1.dp)
                .background(if (dragging) MaterialTheme.colorScheme.primary else colors.hairline),
        )
    }
}

/**
 * 授权根：横向 chip 条。
 *
 * 竖排根列表在单侧栏布局下没地方放（侧栏 252 + 根 222 + 列表 + 预览 = 四列），
 * 而根目录通常只有一两个，横排一行就够，且能把宽度留给文件列表。
 */
@Composable
private fun RootChips(state: AppState) {
    val colors = LocalAppColors.current
    if (state.roots.isEmpty()) {
        Text(
            "这台设备还没有被授权任何目录。请在电脑的「手机设置 → 设备」里勾选。",
            style = MaterialTheme.typography.labelSmall,
            color = colors.textTertiary,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
        )
        return
    }
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        state.roots.forEach { root ->
            val selected = root.id == state.activeRootId
            Row(
                Modifier
                    .background(
                        if (selected) MaterialTheme.colorScheme.primaryContainer else colors.mutedSurface,
                        RoundedCornerShape(50),
                    )
                    .clickable {
                        state.activeRootId = root.id
                        state.openDirectory("")
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    Icons.Filled.Folder,
                    null,
                    tint = if (selected) MaterialTheme.colorScheme.primary else colors.textSecondary,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    root.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) MaterialTheme.colorScheme.primary else colors.textSecondary,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun FileListPane(state: AppState, modifier: Modifier) {
    val colors = LocalAppColors.current
    val page = state.currentPage

    Column(modifier.fillMaxHeight()) {
        // 面包屑
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TextButton(
                onClick = {
                    if (state.currentPath.isNotEmpty()) {
                        state.openDirectory(state.currentPath.substringBeforeLast('/', ""))
                    }
                },
                enabled = state.currentPath.isNotEmpty(),
            ) {
                Text("↑ 上级", style = MaterialTheme.typography.labelMedium)
            }
            Text(
                "/" + state.currentPath.trimStart('/'),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        HorizontalDivider(color = colors.hairline)

        if (state.filesError != null) {
            Text(
                state.filesError.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = colors.danger,
                modifier = Modifier.padding(14.dp),
            )
        }

        val entries = page?.entries.orEmpty()
        if (entries.isEmpty() && !state.filesBusy) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState("这个目录是空的", hint = "可以用右上角的按钮上传文件或新建目录。")
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(entries, key = { it.path }) { entry ->
                    FileRow(
                        entry = entry,
                        selected = state.selectedEntry?.path == entry.path,
                        onClick = {
                            if (entry.kind == "directory") state.openDirectory(entry.path)
                            else state.previewEntry(entry)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun FileRow(entry: FileEntry, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 1.dp)
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                RoundedCornerShape(Tokens.radiusSm),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            if (entry.kind == "directory") Icons.Filled.Folder else Icons.AutoMirrored.Filled.List,
            null,
            tint = if (entry.kind == "directory") MaterialTheme.colorScheme.primary else colors.textTertiary,
            modifier = Modifier.size(15.dp),
        )
        ElidedText(entry.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(
            if (entry.kind == "directory") "目录" else formatFileSize(entry.size),
            style = MaterialTheme.typography.labelSmall,
            color = colors.textTertiary,
        )
    }
}

@Composable
private fun PreviewPane(state: AppState, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    val entry = state.selectedEntry
    var moveOpen by remember { mutableStateOf(false) }
    val editing = state.fileDraft != null
    val extra = state.previewExtra
    // 内容还没回来时先按文件名判一次类型：徽标要"点开就知道是什么"，不能等读完再显示
    val type = extra?.type ?: entry?.let { classifyFile(it.name) }
    val kind = type?.kind

    Column(
        modifier
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 12.dp)) {
            ElidedText(entry?.name ?: "预览", style = MaterialTheme.typography.titleSmall)
            // 目录栏收起时，路径是唯一能说明"我在看哪个文件"的线索，不能省
            if (entry != null && !state.filesShowList) {
                Text(
                    "/" + entry.path.trimStart('/').substringBeforeLast('/', ""),
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = colors.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (entry != null && entry.kind != "directory") {
                Row(
                    Modifier.padding(top = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TypeBadge(type)
                    Text(
                        formatFileSize(entry.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textTertiary,
                    )
                    // 编码不是 UTF-8 才说，避免每份文件都挂一行噪音
                    extra?.charset?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = colors.warning)
                    }
                }
            }
        }

        // 动作条：只放当前这个文件真能做的事，不搞一排永远灰着的按钮
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (editing) {
                MiniAction("保存", MaterialTheme.colorScheme.primary, enabled = !state.filesBusy) { state.saveDraft() }
                MiniAction("取消", colors.textSecondary) { state.cancelEditing() }
            } else if (entry != null) {
                if (kind == FileKind.Markdown) {
                    MiniAction(
                        if (state.previewShowSource) "预览" else "源码",
                        MaterialTheme.colorScheme.primary,
                    ) { state.previewShowSource = !state.previewShowSource }
                }
                if (kind == FileKind.Text || kind == FileKind.Markdown) {
                    MiniAction("编辑", MaterialTheme.colorScheme.primary, enabled = state.filePreview != null) {
                        state.startEditing()
                    }
                }
                // 渲染不了的、和图片（想拿看图软件细看），都给这条出口
                if (kind == FileKind.Other || kind == FileKind.Image) {
                    MiniAction("用系统程序打开", MaterialTheme.colorScheme.primary, enabled = !state.filesBusy) {
                        state.openEntryExternally(entry)
                    }
                }
                MiniAction("移动", colors.textSecondary) { moveOpen = true }
                MiniAction("下载", colors.textSecondary) {
                    val chooser = JFileChooser()
                    chooser.selectedFile = File(entry.name)
                    if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                        chooser.selectedFile?.let { state.downloadEntry(entry, it) }
                    }
                }
                Spacer(Modifier.weight(1f))
                // 用文字而不是垃圾桶图标："删除"是破坏性操作，写清楚比画小图标更负责
                MiniAction("删除", colors.danger) { state.deleteEntry(entry) }
            }
        }
        HorizontalDivider(color = colors.hairline)

        when {
            entry == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    "选一个文件",
                    hint = "Markdown 会渲染成文档，图片直接显示；" +
                        "PDF、Office、压缩包这类交给系统程序打开。",
                )
            }

            entry.kind == "directory" -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState("这是一个目录", hint = "双击列表里的目录可以进去。")
            }

            // 编辑态：同一个位置换成可写的多行框，保存时带 ETag
            editing -> Column(Modifier.fillMaxSize()) {
                Text(
                    "编辑中 · 保存会带上读到这份内容时的 ETag，别人先改了会被拒（412），不会覆盖。",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textTertiary,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
                TextField(
                    value = state.fileDraft.orEmpty(),
                    onValueChange = { state.fileDraft = it },
                    textStyle = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                    ),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                    ),
                    modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                )
            }

            extra == null -> LoadingBlock("读取中…")

            else -> when (extra.type.kind) {
                FileKind.Other -> UnsupportedPane(extra, state.filesBusy) { state.openEntryExternally(entry) }
                FileKind.Image -> ImagePane(extra)
                FileKind.Markdown -> if (state.previewShowSource) {
                    SourcePane(state.filePreview.orEmpty(), state.filePreviewTruncated)
                } else {
                    MarkdownPreviewPane(state.filePreview.orEmpty(), state.filePreviewTruncated)
                }
                FileKind.Text -> SourcePane(state.filePreview.orEmpty(), state.filePreviewTruncated)
            }
        }
    }

    if (moveOpen && entry != null) {
        var target by remember(entry.path) { mutableStateOf(entry.path) }
        AlertDialog(
            onDismissRequest = { moveOpen = false },
            title = { Text("移动或重命名") },
            text = {
                Column {
                    OutlinedTextField(
                        value = target,
                        onValueChange = { target = it },
                        singleLine = true,
                        label = { Text("目标路径（相对授权根）") },
                        shape = RoundedCornerShape(Tokens.radiusSm),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "改文件名是重命名，改前半段是换目录 —— 同一个动作。目标已存在会被服务端拒绝。",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textTertiary,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    state.moveEntry(entry, target)
                    moveOpen = false
                }) { Text("移动") }
            },
            dismissButton = { TextButton(onClick = { moveOpen = false }) { Text("取消") } },
        )
    }
}

/** 类型徽标：Markdown / 图片 / 二进制 用不同底色，扫一眼就能分辨。 */
@Composable
private fun TypeBadge(type: FileType?) {
    if (type == null) return
    val tint = when (type.kind) {
        FileKind.Markdown, FileKind.Image -> MaterialTheme.colorScheme.primary
        FileKind.Text -> LocalAppColors.current.textSecondary
        FileKind.Other -> LocalAppColors.current.warning
    }
    Text(
        type.label,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
        color = tint,
        modifier = Modifier
            .background(tint.copy(alpha = 0.10f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** 动作条里的小按钮：纯文字 + 紧凑内边距，五个按钮才塞得进 430dp。 */
@Composable
private fun MiniAction(
    label: String,
    color: Color,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        color = if (enabled) color else color.copy(alpha = 0.35f),
        modifier = Modifier
            .clip(RoundedCornerShape(Tokens.radiusSm))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 6.dp),
    )
}

/**
 * Markdown 就地渲染。
 *
 * 渲染前先把 CRLF 归一：解析器按 `\n` 切行，行尾留着 `\r` 会让表格最后一格
 * 变成 `值\r|`，看起来像表格坏了。归一放在这一层而不是解码层，是为了不改
 * 编辑草稿的原始字节（Windows 文件保存回去仍是 CRLF）。
 */
@Composable
private fun MarkdownPreviewPane(text: String, truncated: Boolean) {
    Column(Modifier.fillMaxSize()) {
        if (truncated) TruncatedHint()
        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            MarkdownText(
                text.replace("\r\n", "\n").replace('\r', '\n'),
                Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 18.dp),
            )
        }
    }
}

/** 纯文本 / Markdown 源码：等宽、不做任何解释。 */
@Composable
private fun SourcePane(text: String, truncated: Boolean) {
    val colors = LocalAppColors.current
    Column(Modifier.fillMaxSize()) {
        if (truncated) TruncatedHint()
        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Text(
                text,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(14.dp),
            )
        }
    }
}

@Composable
private fun TruncatedHint() {
    Text(
        "文件超过 512 KiB，下面只显示前一段。完整内容请下载（也不提供编辑）。",
        style = MaterialTheme.typography.labelSmall,
        color = LocalAppColors.current.warning,
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

/**
 * 图片预览：按容器宽度等比缩放，不裁切。
 *
 * 解码放到 [Dispatchers.Default]：一张 8 MB 的 JPEG 解成位图要几百毫秒，
 * 在主线程做就是一次可见的卡顿。
 */
@Composable
private fun ImagePane(extra: PreviewExtra) {
    val colors = LocalAppColors.current
    val bytes = extra.imageBytes
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = bytes) {
        if (bytes == null) return@produceState
        value = runCatching {
            withContext(Dispatchers.Default) {
                org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap()
            }
        }.getOrNull()
    }

    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxWidth().weight(1f).padding(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            val image = bitmap
            if (image != null) {
                Image(
                    bitmap = image,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().clickable { ImagePreview.open(image) },
                )
            } else {
                LoadingBlock("解码中…")
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "${extra.imageFormat?.uppercase().orEmpty()} · ${bitmap?.width ?: 0}×${bitmap?.height ?: 0}",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textTertiary,
            )
            Text("点一下看原图", style = MaterialTheme.typography.labelSmall, color = colors.textTertiary)
        }
    }
}

/** 渲染不了的格式：说清原因，给一条明确的出口，不留"点了没反应"。 */
@Composable
private fun UnsupportedPane(extra: PreviewExtra, busy: Boolean, onOpen: () -> Unit) {
    val colors = LocalAppColors.current
    Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            TypeBadge(extra.type)
            Spacer(Modifier.height(12.dp))
            Text(
                extra.type.reason ?: "客户端不内嵌渲染这类文件。",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                if (busy) "正在下载…" else "用系统程序打开",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .clip(RoundedCornerShape(Tokens.radiusSm))
                    .background(if (busy) colors.textTertiary else MaterialTheme.colorScheme.primary)
                    .clickable(enabled = !busy, onClick = onOpen)
                    .padding(horizontal = 18.dp, vertical = 9.dp),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "会先下载到本机缓存目录，再交给系统默认程序；不会覆盖你已保存的文件。",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textTertiary,
            )
        }
    }
}
