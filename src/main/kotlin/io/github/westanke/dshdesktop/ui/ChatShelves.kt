package io.github.westanke.dshdesktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.westanke.dshdesktop.AppState
import io.github.westanke.dshdesktop.data.TodoItem
import io.github.westanke.dshdesktop.data.TraceStep
import io.github.westanke.dshdesktop.data.TraceTurn

/**
 * 挂在会话标题下方的两块面板：**待办**与**轨迹**。
 *
 * 为什么从右侧抽屉挪下来：官方 Web 里这两样都是**贴着会话头**的，不是侧边栏——
 * 抽屉要把视线从正文挪到 300dp 外，而它们本来就是"这一轮正在干什么"的一部分，
 * 属于正文的注脚。默认折叠成一行，展开也不许超过 [ShelfMaxHeight]：
 * 注脚不该把正文挤出屏幕。
 */
private val ShelfMaxHeight = 220.dp

/**
 * 折叠条 + 可滚内容的公共外壳。
 *
 * 折叠时只有 36dp 一行（标题 + 摘要 + 箭头），点一下展开、再点收起。
 */
@Composable
private fun ShelfFrame(
    title: String,
    summary: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    accent: Color,
    content: @Composable () -> Unit,
) {
    val colors = LocalAppColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .background(colors.mutedSurface, RoundedCornerShape(Tokens.radiusMd))
            .border(1.dp, colors.hairline, RoundedCornerShape(Tokens.radiusMd)),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(6.dp)
                    .background(accent, CircleShape),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                color = colors.textSecondary,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                summary,
                style = MaterialTheme.typography.labelSmall,
                color = colors.textTertiary,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(6.dp))
            Icon(
                if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = if (expanded) "收起" else "展开",
                tint = colors.textTertiary,
                modifier = Modifier.size(15.dp),
            )
        }
        if (expanded) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.hairline))
            Box(Modifier.fillMaxWidth().heightIn(max = ShelfMaxHeight).verticalScroll(rememberScrollState())) {
                content()
            }
        }
    }
}

// ── 待办 ────────────────────────────────────────────────────────────────────

/**
 * 会话待办。内核在规划多步任务时写 `todos` 投影，一轮下来常有十几条，
 * 所以折叠时只报"几比几完成"，展开才列全。
 */
@Composable
fun TodoShelf(state: AppState) {
    val colors = LocalAppColors.current
    val items = state.todoItems
    if (items.isEmpty()) return
    val done = items.count { it.done }
    ShelfFrame(
        title = "待办",
        summary = "$done / ${items.size} 已完成",
        expanded = state.todosShelfOpen,
        onToggle = { state.todosShelfOpen = !state.todosShelfOpen },
        accent = MaterialTheme.colorScheme.primary,
    ) {
        Column(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items.forEach { item -> TodoRow(item) }
        }
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
                .background(if (item.done) tint else tint.copy(alpha = 0.25f), CircleShape),
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

// ── 轨迹（线性时间轴）────────────────────────────────────────────────────────

/**
 * 轨迹：**按 seq 顺序一行一条**的线性时间轴，对照官方的做法。
 *
 * 早先是"按轮次分组 + 事件类型×次数聚合"，那是给机器看的统计；
 * 官方给的是"这一步干了什么"——时间、类型、一句话摘要，竖线串起来。
 * 摘要由 `SessionProjections.stepLabel()` 生成；认不出的类型就只剩类型名。
 */
@Composable
fun TraceShelf(state: AppState) {
    val colors = LocalAppColors.current
    // 轮次只是分组依据；画时间轴要的是"一条接一条"，所以先摊平再按 seq 排
    val steps: List<TraceStep> = remember(state.trace) {
        state.trace.flatMap(TraceTurn::steps).sortedBy { it.seq }
    }
    if (steps.isEmpty()) return
    ShelfFrame(
        title = "轨迹",
        summary = "${steps.size} 个事件 · ${state.trace.size} 轮",
        expanded = state.traceShelfOpen,
        onToggle = { state.traceShelfOpen = !state.traceShelfOpen },
        accent = colors.textTertiary,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            steps.forEachIndexed { index, step ->
                TraceStepRow(step, isLast = index == steps.lastIndex)
            }
        }
    }
}

@Composable
private fun TraceStepRow(step: TraceStep, isLast: Boolean) {
    val colors = LocalAppColors.current
    var open by remember(step.seq, step.kind) { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth()) {
        // 左侧竖线 + 圆点：时间轴的"串"，最后一条不画尾巴
        Column(
            Modifier.width(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .padding(top = 5.dp)
                    .size(6.dp)
                    .background(colors.textTertiary, CircleShape),
            )
            if (!isLast) {
                Box(
                    Modifier
                        .width(1.dp)
                        .height(20.dp)
                        .background(colors.hairline),
                )
            }
        }
        Spacer(Modifier.width(6.dp))
        Column(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(Tokens.radiusSm))
                .clickable { open = !open }
                .padding(vertical = 3.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    formatTime(step.at),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.5.sp,
                    ),
                    color = colors.textTertiary,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    step.kind,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (step.label.isNotBlank()) {
                Text(
                    step.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    maxLines = if (open) 6 else 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (open) {
                Text(
                    "seq ${step.seq}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.5.sp,
                    ),
                    color = colors.textTertiary,
                )
            }
        }
    }
}
