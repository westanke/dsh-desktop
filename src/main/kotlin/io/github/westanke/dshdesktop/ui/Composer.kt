package io.github.westanke.dshdesktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.westanke.dshdesktop.AppState
import io.github.westanke.dshdesktop.addAttachments
import io.github.westanke.dshdesktop.applyPermissionPreset
import io.github.westanke.dshdesktop.data.CommandDescriptor
import io.github.westanke.dshdesktop.data.PendingAttachment
import io.github.westanke.dshdesktop.removeAttachment
import io.github.westanke.dshdesktop.sendMessageWithAttachments
import io.github.westanke.dshdesktop.runCommand
import javax.swing.JFileChooser
import io.github.westanke.dshdesktop.stats

/**
 * 输入区：一张圆角卡片，里面是"输入框 + 工具行"，下面是统计一行。
 *
 * 三个桌面端特有的决定，都在官方 Web 端之外另有理由：
 * 1. **运行中发送 = 插话**。看得见执行过程的人想立刻纠偏，排队等这一轮跑完再发没意义，
 *    所以运行态按钮写「插话」，走 `mode: steer`。
 * 2. **`/` 起头给命令候选**。斜杠命令按会话返回，记不住有哪些；给候选比让人背文档靠谱。
 * 3. **权限胶囊是只读的**。权限由电脑端分配，客户端只能显示"当前拿到什么"，
 *    点一下给解释，不做"提权"的假开关。
 */
@Composable
fun Composer(state: AppState) {
    val colors = LocalAppColors.current
    var draft by remember(state.selectedSessionId) { mutableStateOf("") }
    var showCommands by remember { mutableStateOf(false) }
    val sessionId = state.selectedSessionId
    val running = state.running

    val suggestions = remember(state.sessionCommands, draft) {
        if (!draft.startsWith("/")) emptyList()
        else {
            val prefix = draft.removePrefix("/").substringBefore(' ')
            state.sessionCommands
                .filter { it.name.trimStart('/').startsWith(prefix, ignoreCase = true) }
                .take(6)
        }
    }

    fun submit(steer: Boolean) {
        if (sessionId == null) return
        if (draft.isBlank() && state.attachments.isEmpty()) return
        state.sendMessageWithAttachments(draft, steer)
        draft = ""
        // 自己说的话必须看得见：哪怕翻在旧消息上也回底
        state.requestScrollBottom()
    }

    // 消息气泡点「引用」：文本追加进输入框（换行衔接，别粘成一坨）
    LaunchedEffect(state.composerInsert) {
        state.composerInsert?.let { insert ->
            draft = if (draft.isBlank()) insert else draft.trimEnd() + "\n" + insert
            state.composerInsert = null
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 6.dp, bottom = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = Tokens.contentMaxWidth).fillMaxWidth()) {
            // 思考过程挂在输入框上方：它跟着"这一轮正在跑"走，不占消息流的位置。
            // 否则推理一长，正在生成的正文被顶出屏外，人反而看不到关键点。
            if (state.streamingReasoning.isNotBlank()) {
                ReasoningShelf(state.streamingReasoning)
                Spacer(Modifier.size(6.dp))
            }
            if (suggestions.isNotEmpty()) {
                CommandSuggestions(
                    items = suggestions,
                    onPick = { command ->
                        val withInput = command.input?.hint
                        if (withInput != null) {
                            draft = "/${command.name.trimStart('/')} "
                        } else {
                            draft = ""
                            state.runCommand("/${command.name.trimStart('/')}")
                        }
                    },
                )
                Spacer(Modifier.size(6.dp))
            }

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, colors.hairline),
                tonalElevation = 0.dp,
                shadowElevation = 1.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(start = 14.dp, end = 10.dp, top = 8.dp, bottom = 8.dp)) {
                    if (state.attachments.isNotEmpty()) {
                        AttachmentTray(
                            items = state.attachments,
                            onRemove = { state.removeAttachment(it) },
                        )
                        Spacer(Modifier.size(6.dp))
                    }
                    TextField(
                        value = draft,
                        onValueChange = { draft = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 44.dp, max = 168.dp)
                            .onPreviewKeyEvent { event ->
                                // Enter 发送，Shift+Enter 换行 —— 桌面用户的手感
                                if (event.type == KeyEventType.KeyDown && event.key == Key.Enter && !event.isShiftPressed) {
                                    submit(running)
                                    true
                                } else false
                            },
                        placeholder = {
                            Text(
                                when {
                                    sessionId == null -> "先选一个会话"
                                    running -> "正在执行 — 回车即插话，Shift+Enter 换行"
                                    else -> "发消息，Enter 发送 · Shift+Enter 换行 · / 看命令"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textTertiary,
                            )
                        },
                        textStyle = MaterialTheme.typography.bodyMedium,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(),
                        enabled = sessionId != null,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                        ),
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ToolPill(
                            icon = Icons.Filled.Add,
                            label = "命令",
                            active = showCommands || draft.startsWith("/"),
                            onClick = {
                                showCommands = !showCommands
                                draft = if (draft.startsWith("/")) "" else "/"
                            },
                        )
                        Spacer(Modifier.width(6.dp))
                        ToolPill(
                            icon = Icons.Filled.AttachFile,
                            label = if (state.attachments.isEmpty()) "附件" else "附件 ${state.attachments.size}",
                            active = state.attachments.isNotEmpty(),
                            onClick = { pickAttachments(state) },
                        )
                        Spacer(Modifier.width(6.dp))
                        PermissionPill(state)
                        Spacer(Modifier.weight(1f))
                        if (running) {
                            // 停止放在发送左边：运行中最常做的是"别跑了"
                            Surface(
                                onClick = { state.cancelRun() },
                                shape = CircleShape,
                                color = colors.mutedSurface,
                                modifier = Modifier.size(32.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Filled.Stop,
                                        "停止",
                                        tint = colors.danger,
                                        modifier = Modifier.size(15.dp),
                                    )
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                        }
                        val enabled = draft.isNotBlank() && sessionId != null
                        Surface(
                            onClick = { submit(running) },
                            shape = CircleShape,
                            color = if (enabled) MaterialTheme.colorScheme.primary else colors.mutedSurface,
                            modifier = Modifier.size(32.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Filled.ArrowUpward,
                                    if (running) "插话" else "发送",
                                    tint = if (enabled) MaterialTheme.colorScheme.onPrimary else colors.textTertiary,
                                    modifier = Modifier.size(17.dp),
                                )
                            }
                        }
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp, start = 4.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (running) "正在执行 · 回车即插话" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textTertiary,
                )
                // 统计居中（照官方 Web）：两侧等重，左边提示短时不偏
                Spacer(Modifier.weight(1f))
                ChatStatsBar(state.stats)
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

/**
 * 思考过程悬挂条：贴在输入框上方的那块。
 *
 * 三条来自官方 Web 的观察：
 * 1. **默认折叠成一行**（高 36dp）。推理往往比正文长得多，全摊开会盖住整个输入区；
 * 2. **展开也限高 132dp**，超出的自己滚——不让它把输入框顶到屏外；
 * 3. **跟着这一轮走**：这一轮结束它就消失，历史里的思考仍留在消息流的折叠块里。
 */
@Composable
private fun ReasoningShelf(text: String) {
    val colors = LocalAppColors.current
    var expanded by remember { mutableStateOf(false) }
    // 展开区自己滚到底：思考是流式的，新内容不断追加，停在顶部等于"什么都没显示"
    val scroll = rememberScrollState()
    LaunchedEffect(text.length) {
        if (expanded) scroll.animateScrollTo(scroll.maxValue)
    }
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.mutedSurface, RoundedCornerShape(Tokens.radiusMd))
            .border(
                width = 1.dp,
                color = colors.hairline,
                shape = RoundedCornerShape(Tokens.radiusMd),
            )
            .then(if (expanded) Modifier else Modifier.height(36.dp)),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusDot(MaterialTheme.colorScheme.primary, 5)
            Spacer(Modifier.width(8.dp))
            if (text.isBlank()) {
                Text(
                    "思考中",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textSecondary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
            } else {
                // 折叠态也把内容滚出来：只写"思考中 N 字"等于什么都没说。
                // 取**尾部**再让它横向滚动 —— 流式思考最有信息量的永远是最后一句
                // （Compose 没有 TextOverflow.StartEllipsis，所以自己截）。
                val flat = text.replace('\n', ' ')
                val tail = if (flat.length > 64) "…" + flat.takeLast(64) else flat
                Text(
                    tail,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.5.sp,
                    ),
                    color = colors.textSecondary,
                    modifier = Modifier
                        .weight(1f)
                        .basicMarquee(
                            iterations = Int.MAX_VALUE,
                            animationMode = MarqueeAnimationMode.Immediately,
                            velocity = 34.dp,
                            initialDelayMillis = 300,
                        ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(6.dp))
            Icon(
                if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = if (expanded) "收起思考" else "展开思考",
                tint = colors.textTertiary,
                modifier = Modifier.size(15.dp),
            )
        }
        if (expanded) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp, max = 132.dp)
                    .verticalScroll(scroll),
            ) {
                Text(
                    text,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.5.sp,
                        lineHeight = 17.sp,
                    ),
                    color = colors.textSecondary,
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun ToolPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalAppColors.current
    Row(
        Modifier
            .background(
                if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                RoundedCornerShape(50),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            label,
            tint = if (active) MaterialTheme.colorScheme.primary else colors.textSecondary,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (active) MaterialTheme.colorScheme.primary else colors.textSecondary,
        )
    }
}

/**
 * 权限胶囊：显示**会话当前档位**，点开可切。
 *
 * 这里和"设备 scope"是两件不同的事，早先混成一件了：
 * - **设备 scope**（`profile.scopes`）决定这台设备能调哪些接口，只能由服务端分配；
 * - **会话权限档位**（`permissions` 投影 + `/permission <preset>`）决定 agent 在当前会话里
 *   能做什么（只读 / 工作区可写 / 完全访问），是**用户自己能切的**。
 *
 * 早先的胶囊显示的是前者，于是点击只弹一句"由电脑端分配"——把后者也一起锁死了。
 * 现在优先显示档位；档位读不到时才退回 scope 的描述，并说明为什么读不到。
 */
@Composable
private fun PermissionPill(state: AppState) {
    val colors = LocalAppColors.current
    var expanded by remember { mutableStateOf(false) }
    val permission = state.permissionState
    val scopes = state.profile.scopes
    val full = scopes.any { it == "*" || it.contains("write") || it.contains("admin") }

    val label = permission?.currentLabel
        ?: when {
            scopes.isEmpty() -> "权限未读到"
            full -> "完全访问"
            else -> "受限访问"
        }
    val switchable = permission?.switchable == true

    Box {
        Row(
            Modifier
                .background(
                    if (expanded) MaterialTheme.colorScheme.primaryContainer else colors.mutedSurface,
                    RoundedCornerShape(50),
                )
                .clickable { expanded = true }
                .padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Lock,
                null,
                tint = if (expanded) MaterialTheme.colorScheme.primary else colors.textTertiary,
                modifier = Modifier.size(12.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = if (expanded) MaterialTheme.colorScheme.primary else colors.textSecondary,
            )
            Icon(
                Icons.Filled.KeyboardArrowDown,
                null,
                tint = colors.textTertiary,
                modifier = Modifier.size(13.dp),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (permission == null) {
                DropdownMenuItem(
                    text = {
                        Column {
                            Text("还没读到会话权限档位", style = MaterialTheme.typography.bodySmall)
                            Text(
                                "投影里没有 `permissions` 时就是这个样子：内核版本较旧，或这份历史被截断。",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textTertiary,
                            )
                        }
                    },
                    onClick = { expanded = false },
                )
            } else if (permission.options.isEmpty()) {
                DropdownMenuItem(
                    text = {
                        Column {
                            Text("当前：${permission.current}", style = MaterialTheme.typography.bodySmall)
                            Text(
                                "服务端没给候选档位清单，只能显示当前值（缺 permission-presets 能力）。",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textTertiary,
                            )
                        }
                    },
                    onClick = { expanded = false },
                )
            } else {
                permission.options.forEach { option ->
                    val on = option.value == permission.current
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(
                                    option.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (on) MaterialTheme.colorScheme.primary else Color.Unspecified,
                                )
                                option.description?.let {
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.textTertiary,
                                        maxLines = 2,
                                    )
                                }
                            }
                        },
                        onClick = {
                            expanded = false
                            if (!on) state.applyPermissionPreset(option.value)
                        },
                    )
                }
                if (!switchable) {
                    HorizontalDivider(color = colors.hairline)
                    Text(
                        "服务端只给了一个档位，切不了",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textTertiary,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

/**
 * 附件托盘：横向一条，图片带缩略预览。
 *
 * 缩略图**不**走服务端：这些字节还在本地内存里，用 Compose 的 `BitmapPainter` 直接画，
 * 省一次往返，也避免"发之前就先往服务端塞一遍"。
 */
@Composable
private fun AttachmentTray(items: List<PendingAttachment>, onRemove: (String) -> Unit) {
    val colors = LocalAppColors.current
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items.forEach { item ->
            Row(
                Modifier
                    .background(colors.mutedSurface, RoundedCornerShape(Tokens.radiusSm))
                    .padding(start = 6.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (item.isImage) {
                    ImageChip(item)
                    Spacer(Modifier.width(6.dp))
                }
                Column {
                    Text(
                        item.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 150.dp),
                    )
                    Text(item.sizeLabel, style = MaterialTheme.typography.labelSmall, color = colors.textTertiary)
                }
                IconButton(onClick = { onRemove(item.id) }, modifier = Modifier.size(22.dp)) {
                    Icon(Icons.Filled.Close, "移除", tint = colors.textTertiary, modifier = Modifier.size(12.dp))
                }
            }
        }
    }
}

/** 本地图片缩略图。解码失败就退回一个小方块 —— 一张读不出来的图不该让整条托盘消失。 */
@Composable
private fun ImageChip(item: PendingAttachment) {
    val colors = LocalAppColors.current
    val painter = remember(item.id) {
        runCatching {
            androidx.compose.ui.graphics.painter.BitmapPainter(
                org.jetbrains.skia.Image.makeFromEncoded(item.bytes).toComposeImageBitmap(),
            )
        }.getOrNull()
    }
    Box(
        Modifier
            .size(30.dp)
            .background(colors.raisedSurface, RoundedCornerShape(Tokens.radiusSm)),
        contentAlignment = Alignment.Center,
    ) {
        if (painter != null) {
            androidx.compose.foundation.Image(
                painter = painter,
                contentDescription = item.name,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.size(30.dp),
            )
        } else {
            Text("IMG", style = MaterialTheme.typography.labelSmall, color = colors.textTertiary)
        }
    }
}

/** 命令候选：只给名字和描述，选中即填或即执行。 */
@Composable
private fun CommandSuggestions(items: List<CommandDescriptor>, onPick: (CommandDescriptor) -> Unit) {
    val colors = LocalAppColors.current
    Surface(
        color = colors.mutedSurface,
        shape = RoundedCornerShape(Tokens.radiusMd),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(vertical = 4.dp)) {
            items.forEach { command ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onPick(command) }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "/" + command.name.trimStart('/'),
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        command.description.ifBlank { command.input?.hint.orEmpty() },
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}


/**
 * 选本地文件当附件。
 *
 * 允许多选：聊天里贴一批截图是常事，一次只能挑一张等于逼人重复四遍。
 * 选完只读进内存，真正上传在点发送那一刻（协议要求 `receiptId` 同一次 prompt 内有效）。
 */
private fun pickAttachments(state: AppState) {
    val chooser = JFileChooser().apply {
        isMultiSelectionEnabled = true
        dialogTitle = "选择要发送的附件"
    }
    if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        state.addAttachments(chooser.selectedFiles.toList())
    }
}
