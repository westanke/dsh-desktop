package io.github.westanke.dshdesktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import io.github.westanke.dshdesktop.AppState
import io.github.westanke.dshdesktop.ChatPanel
import io.github.westanke.dshdesktop.data.JobStatus
import io.github.westanke.dshdesktop.data.JobView
import io.github.westanke.dshdesktop.data.TodoItem
import io.github.westanke.dshdesktop.data.TraceTurn
import io.github.westanke.dshdesktop.killJob
import io.github.westanke.dshdesktop.loadJobOutput
import io.github.westanke.dshdesktop.refreshJobs
import io.github.westanke.dshdesktop.resyncHistory
import io.github.westanke.dshdesktop.seqGapHint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 会话右侧抽屉：待办 / 轨迹 / 任务 / 服务端。
 *
 * 为什么是抽屉而不是弹窗：这四样都是"边看正文边对照"的东西。安卓端做成底部弹层是因为
 * 手机屏幕就那么大；桌面竖着一条 300dp 的列，正文还能剩 900+ dp 可读，
 * 一边看执行一边看进度不用来回切。
 *
 * 宽度刻意不给 `fillMaxWidth`：抽屉是旁证，不是主角。正文列已经收窄到 860dp，
 * 抽屉再宽就会把它挤到换行。
 */
private val PanelWidth = 316.dp

@Composable
fun ChatSidePanel(state: AppState) {
    val colors = LocalAppColors.current
    val current = state.chatPanel ?: return
    Row(Modifier.fillMaxHeight()) {
        Box(Modifier.fillMaxHeight().width(1.dp).background(colors.hairline))
        Column(
            Modifier
                .width(PanelWidth)
                .fillMaxHeight()
                .background(colors.mutedSurface.copy(alpha = 0.5f)),
        ) {
            PanelHeader(state, current)
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.hairline))
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (current) {
                    ChatPanel.Todos -> TodosBody(state)
                    ChatPanel.Trace -> TraceBody(state)
                    ChatPanel.Jobs -> JobsBody(state)
                    ChatPanel.Server -> ServerBody(state)
                }
            }
        }
    }
}

@Composable
private fun PanelHeader(state: AppState, current: ChatPanel) {
    val colors = LocalAppColors.current
    Row(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            current.label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (current == ChatPanel.Jobs) {
            IconButton(onClick = { state.refreshJobs() }, modifier = Modifier.size(26.dp)) {
                Icon(Icons.Filled.Refresh, "刷新任务", tint = colors.textTertiary, modifier = Modifier.size(14.dp))
            }
        }
        IconButton(onClick = { state.chatPanel = null }, modifier = Modifier.size(26.dp)) {
            Icon(Icons.Filled.Close, "收起", tint = colors.textTertiary, modifier = Modifier.size(15.dp))
        }
    }
}

// ── 待办 ────────────────────────────────────────────────────────────────────

@Composable
private fun TodosBody(state: AppState) {
    val colors = LocalAppColors.current
    val items = state.todoItems
    if (items.isEmpty()) {
        PanelEmpty(
            title = "这个会话还没有待办",
            hint = "内核在规划多步任务时会写 `todos` 投影。只有一步、或者内核版本没接这个投影时，这里就是空的。",
        )
        return
    }
    val done = items.count { it.done }
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            "$done / ${items.size} 已完成",
            style = MaterialTheme.typography.labelSmall,
            color = colors.textTertiary,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        items.forEach { item -> TodoRow(item) }
    }
}

@Composable
private fun TodoRow(item: TodoItem) {
    val colors = LocalAppColors.current
    val tint = when {
        item.done -> colors.success
        item.status == "in_progress" || item.status == "active" -> MaterialTheme.colorScheme.primary
        item.status == "cancelled" || item.status == "canceled" -> colors.textTertiary
        else -> colors.textSecondary
    }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier
                .padding(top = 5.dp)
                .size(9.dp)
                .background(if (item.done) tint else Color.Transparent, CircleShape)
                .then(
                    if (item.done) Modifier
                    else Modifier.background(tint.copy(alpha = 0.25f), CircleShape),
                ),
        )
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.content,
                style = MaterialTheme.typography.bodySmall,
                color = if (item.done) colors.textTertiary else MaterialTheme.colorScheme.onSurface,
            )
            if (!item.done) {
                Text(item.statusLabel, style = MaterialTheme.typography.labelSmall, color = tint)
            }
        }
    }
}

// ── 轨迹 ────────────────────────────────────────────────────────────────────

@Composable
private fun TraceBody(state: AppState) {
    val colors = LocalAppColors.current
    val turns = state.trace
    val gap = state.seqGapHint()
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        if (gap != null) {
            // 断档说明放在轨迹顶部：轨迹本身就是"我漏了什么"的答案，先说清它自己不全
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(Tokens.radiusSm),
                modifier = Modifier.fillMaxWidth().padding(12.dp),
            ) {
                Column(Modifier.padding(10.dp)) {
                    Text(
                        gap,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(
                        "点这里重取",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.clickable { state.resyncHistory() },
                    )
                }
            }
        }
        if (turns.isEmpty()) {
            PanelEmpty(
                title = "没有事件可画",
                hint = "这份历史里没有 `turn/start` 之类的事件。空会话、或历史被截断到只剩正文时就是这样。",
            )
            return@Column
        }
        Column(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            turns.forEach { turn -> TraceTurnCard(turn, colors.textTertiary) }
        }
    }
}

@Composable
private fun TraceTurnCard(turn: TraceTurn, tertiary: Color) {
    val colors = LocalAppColors.current
    var open by remember(turn.index, turn.steps.size) { mutableStateOf(turn.index == 0 && turn.steps.size <= 6) }
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Tokens.radiusSm))
            .clickable { open = !open }
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (turn.index == 0) "更早（无起始标记）" else "第 ${turn.index} 轮",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            turn.durationMs?.let {
                Text(formatDuration(it), style = MaterialTheme.typography.labelSmall, color = tertiary)
            }
        }
        Text(
            buildString {
                append("${turn.steps.size} 个事件")
                if (turn.toolCount > 0) append(" · ${turn.toolCount} 次工具")
                if (turn.endedAt == null && turn.index != 0) append(" · 未收口")
            },
            style = MaterialTheme.typography.labelSmall,
            color = tertiary,
            modifier = Modifier.padding(top = 1.dp),
        )
        if (open) {
            Spacer(Modifier.size(6.dp))
            // 按"类型 × 次数"聚合：一轮里常有几十条 delta，逐条列出来没人看得下去
            turn.kinds.forEach { (kind, count) ->
                Row(Modifier.padding(vertical = 1.dp)) {
                    Text(
                        if (count > 1) "$kind ×$count" else kind,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.5.sp,
                        ),
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

// ── 后台任务 ────────────────────────────────────────────────────────────────

@Composable
private fun JobsBody(state: AppState) {
    val colors = LocalAppColors.current
    val jobs = state.jobs
    val output = state.jobOutput
    if (jobs.isEmpty()) {
        PanelEmpty(
            title = "没有后台任务",
            hint = "agent 把命令挂到后台跑时会出现（`bash` 带 run_in_background）。" +
                "这里能看输出、能杀掉 —— 人不在电脑前时这是唯一的刹车。",
        )
        return
    }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(8.dp)) {
        jobs.forEach { job -> JobCard(job, state, colors.textTertiary) }
        output?.let { (jobId, text) ->
            Spacer(Modifier.size(8.dp))
            Text(
                "输出 · $jobId",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textTertiary,
            )
            Text(
                text.ifBlank { "（还没输出）" },
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.5.sp,
                ),
                color = colors.textSecondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 260.dp)
                    .padding(top = 4.dp)
                    .background(colors.codeBackground, RoundedCornerShape(Tokens.radiusSm))
                    .padding(8.dp)
                    .verticalScroll(rememberScrollState()),
            )
        }
    }
}

@Composable
private fun JobCard(job: JobView, state: AppState, tertiary: Color) {
    val colors = LocalAppColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Tokens.radiusSm))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                job.label.ifBlank { job.id },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                jobStatusLabel(job.status),
                style = MaterialTheme.typography.labelSmall,
                color = when (job.status) {
                    JobStatus.RUNNING, JobStatus.STOPPING -> MaterialTheme.colorScheme.primary
                    JobStatus.FAILED -> colors.danger
                    JobStatus.COMPLETED -> colors.success
                    JobStatus.KILLED -> tertiary
                },
            )
        }
        val sub = listOfNotNull(job.kind, job.progress, job.detail).filter { it.isNotBlank() }
        if (sub.isNotEmpty()) {
            Text(
                sub.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = tertiary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "看输出",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { state.loadJobOutput(job.id) },
            )
            if (job.status == JobStatus.RUNNING || job.status == JobStatus.STOPPING) {
                Text(
                    "终止",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.danger,
                    modifier = Modifier.clickable { state.killJob(job.id) },
                )
            }
        }
    }
}

// ── 服务端 ──────────────────────────────────────────────────────────────────

/**
 * 服务端能力面。
 *
 * 这一栏存在的理由很直接：出问题时第一个要回答的问题是"服务端到底支不支持"。
 * 有了它，"卡片不弹"和"本来没有"就分得开了 —— 这正是能力位当初要解决的问题。
 */
@Composable
private fun ServerBody(state: AppState) {
    val colors = LocalAppColors.current
    val caps = state.capabilities
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(12.dp)) {
        Text(
            "插件版本 · ${state.bridgeVersion ?: "未知"}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            "设备 · ${state.profile.deviceName}",
            style = MaterialTheme.typography.labelSmall,
            color = colors.textTertiary,
            modifier = Modifier.padding(top = 2.dp),
        )
        Spacer(Modifier.size(10.dp))
        if (caps.isEmpty()) {
            Text(
                "还没收到 `connection.ready`，能力清单为空。断线后重连会补上。",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textTertiary,
            )
            return@Column
        }
        Text(
            "${caps.size} 项能力已声明",
            style = MaterialTheme.typography.labelSmall,
            color = colors.textTertiary,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        caps.sorted().forEach { cap ->
            Text(
                "· $cap",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 10.5.sp),
                color = colors.textSecondary,
                modifier = Modifier.padding(vertical = 1.dp),
            )
        }
    }
}

// ── 共用 ────────────────────────────────────────────────────────────────────

@Composable
private fun PanelEmpty(title: String, hint: String) {
    val colors = LocalAppColors.current
    Column(Modifier.fillMaxWidth().padding(20.dp)) {
        Text(title, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
        Spacer(Modifier.size(6.dp))
        Text(hint, style = MaterialTheme.typography.labelSmall, color = colors.textTertiary)
    }
}

private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

internal fun formatTime(at: Long): String = if (at <= 0) "—" else timeFormat.format(Date(at))

private fun jobStatusLabel(status: JobStatus): String = when (status) {
    JobStatus.RUNNING -> "运行中"
    JobStatus.STOPPING -> "正在终止"
    JobStatus.COMPLETED -> "已完成"
    JobStatus.KILLED -> "已终止"
    JobStatus.FAILED -> "失败"
}

private fun formatDuration(ms: Long): String = when {
    ms < 1000 -> "${ms}ms"
    ms < 60_000 -> String.format(Locale.ROOT, "%.1fs", ms / 1000.0)
    else -> "${ms / 60_000}m${(ms % 60_000) / 1000}s"
}
