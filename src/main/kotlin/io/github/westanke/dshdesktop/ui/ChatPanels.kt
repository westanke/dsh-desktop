package io.github.westanke.dshdesktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.westanke.dshdesktop.data.ChatStats
import io.github.westanke.dshdesktop.data.DeliverableFile
import io.github.westanke.dshdesktop.data.PendingApproval

/**
 * 审批卡片。
 *
 * 早先这里只有一句「有 N 个操作等待审批 — 请在电脑上处理（桌面端不代签）」——
 * 而用户手里的"电脑"就是这台机器，等于把活推给 WebUI。现在直接列出每一条，
 * 给出风险等级和理由，允许一次 / 拒绝。
 */
@Composable
fun ApprovalPanel(
    items: List<PendingApproval>,
    busy: Boolean,
    onDecide: (approvalId: String, allow: Boolean) -> Unit,
) {
    val colors = LocalAppColors.current
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(Tokens.radiusMd),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Warning,
                    null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "${items.size} 个操作等待批准",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            items.forEach { approval ->
                ApprovalRow(approval, busy, onDecide)
            }
        }
    }
}

@Composable
private fun ApprovalRow(
    approval: PendingApproval,
    busy: Boolean,
    onDecide: (String, Boolean) -> Unit,
) {
    val colors = LocalAppColors.current
    var showDetail by remember(approval.id) { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Tokens.radiusSm))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                approval.toolName.ifBlank { "未知工具" },
                style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                riskLabel(approval.risk),
                style = MaterialTheme.typography.labelSmall,
                color = when (approval.risk) {
                    "high", "critical" -> colors.danger
                    "medium" -> colors.warning
                    else -> colors.textTertiary
                },
            )
        }
        approval.reason?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
        }
        approval.detail?.takeIf { it.isNotBlank() }?.let { detail ->
            if (showDetail) {
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.5.sp,
                    ),
                    color = colors.textSecondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                        .background(colors.codeBackground, RoundedCornerShape(Tokens.radiusSm))
                        .padding(8.dp),
                )
            } else {
                Text(
                    "查看详情",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { showDetail = true },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(
                onClick = { onDecide(approval.id, true) },
                enabled = !busy,
                shape = RoundedCornerShape(Tokens.radiusSm),
                color = MaterialTheme.colorScheme.primary,
            ) {
                Text(
                    "允许一次",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
            TextButton(onClick = { onDecide(approval.id, false) }, enabled = !busy) {
                Text(
                    "拒绝",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.danger,
                )
            }
        }
    }
}

private fun riskLabel(risk: String): String = when (risk) {
    "high" -> "高风险"
    "critical" -> "极高风险"
    "medium" -> "中风险"
    "low" -> "低风险"
    else -> risk.ifBlank { "风险未知" }
}

/**
 * 交付物卡片。
 *
 * 一轮跑完端出来的文件，比正文更值得留一张卡片：点一下直接跳到文件页对着看。
 */
@Composable
fun DeliverableCard(files: List<DeliverableFile>, onOpen: (String) -> Unit) {
    val colors = LocalAppColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.mutedSurface, RoundedCornerShape(Tokens.radiusSm))
            .padding(vertical = 10.dp),
    ) {
        Text(
            "产出文件 · ${files.size}",
            style = MaterialTheme.typography.labelMedium,
            color = colors.textSecondary,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        Spacer(Modifier.size(6.dp))
        files.forEachIndexed { index, file ->
            if (index > 0) {
                HorizontalDivider(color = colors.hairline, modifier = Modifier.padding(horizontal = 12.dp))
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(file.path) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        file.name,
                        style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val sub = file.description?.takeIf { it.isNotBlank() } ?: file.directory
                    if (sub.isNotBlank()) {
                        Text(
                            sub,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Text("定位", style = MaterialTheme.typography.labelSmall, color = colors.textTertiary)
            }
        }
    }
}

/** 消息区顶部的一行量级：轮次、工具次数、失败数、产出数。没有内容时整行不占位。 */
@Composable
fun ChatStatsBar(stats: ChatStats, modifier: Modifier = Modifier) {
    if (stats.isEmpty) return
    Text(
        stats.summary(),
        style = MaterialTheme.typography.labelSmall,
        color = LocalAppColors.current.textTertiary,
        modifier = modifier.padding(horizontal = 2.dp),
    )
}
