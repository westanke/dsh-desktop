package io.github.westanke.dshdesktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
 * 交付物卡片：**一个文件一张卡**，对齐官方 Web 的产出卡片。
 *
 * 官方那张卡的结构是「类型色块 + 主副两行 + 右侧箭头」，一眼就知道点下去是打开哪个文件。
 * 早先这里是一整块列表（一行标题 + 若干行），文件一多就退化成"一堆文件名"。
 *
 * 色块颜色按文件类型给**固定品牌色**（`.docx` 蓝、`.xlsx` 绿、`.pdf` 红……），
 * 不跟着主题变：这些颜色是用户对文件类型的既有认知，跟着主题走反而认不出。
 */
@Composable
fun DeliverableCard(files: List<DeliverableFile>, onOpen: (String) -> Unit) {
    val colors = LocalAppColors.current
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        files.forEach { file ->
            val ext = file.name.substringAfterLast('.', "").lowercase()
            val (badgeLabel, badgeColor) = fileBadge(ext)
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Tokens.radiusMd))
                    .border(1.dp, colors.hairline, RoundedCornerShape(Tokens.radiusMd))
                    .clickable { onOpen(file.path) }
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(40.dp)
                        .background(badgeColor, RoundedCornerShape(Tokens.radiusSm)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        badgeLabel,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        file.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
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
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "打开这个文件",
                    tint = colors.textTertiary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/** 文件类型色块：返回（块内文字，块底色）。扩展名在这里是内容的一部分，不是配色。 */
private fun fileBadge(ext: String): Pair<String, Color> = when (ext) {
    "doc", "docx" -> "DOC" to Color(0xFF2B579A)
    "xls", "xlsx" -> "XLS" to Color(0xFF217346)
    "ppt", "pptx" -> "PPT" to Color(0xFFD24726)
    "pdf" -> "PDF" to Color(0xFFC0392B)
    "md" -> "MD" to Color(0xFF4C5866)
    "txt", "log" -> "TXT" to Color(0xFF6B7785)
    "csv" -> "CSV" to Color(0xFF2F7D5B)
    "json", "yaml", "yml", "toml" -> "{}" to Color(0xFF5A6472)
    "png", "jpg", "jpeg", "gif", "webp", "bmp" -> "IMG" to Color(0xFF7A5AA8)
    "zip", "gz", "tar", "7z", "rar" -> "ZIP" to Color(0xFF8A6A3B)
    "mp4", "mov", "webm" -> "VID" to Color(0xFF4A6B8A)
    "mp3", "wav", "flac" -> "AUD" to Color(0xFF8A5A7A)
    else -> if (ext.isBlank()) "文件" to Color(0xFF6B7785) else ext.take(3).uppercase() to Color(0xFF6B7785)
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
