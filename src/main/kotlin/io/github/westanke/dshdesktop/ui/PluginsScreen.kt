package io.github.westanke.dshdesktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.westanke.dshdesktop.AppState
import io.github.westanke.dshdesktop.data.PluginEntry
import io.github.westanke.dshdesktop.loadPlugins
import io.github.westanke.dshdesktop.togglePlugin

/**
 * 插件页：`dsh.profile.bundles` 的清单与启停。
 *
 * 这里有一条必须说清的边界：**开关只改 profile 里的 bundles 数组，不装包也不卸包**。
 * 而且 bundle 只在 DSH 启动时读一次，所以每次切换都会带 `restartRequired` ——
 * 界面照实转告，不然用户会以为开关坏了。
 *
 * `available: false` 时整份清单都不可信（profile 读不到、或格式不对），
 * 这时显示 `reason` 而不是一份假列表。
 */
@Composable
fun PluginsScreen(state: AppState) {
    val colors = LocalAppColors.current
    LaunchedEffect(Unit) { if (state.plugins == null) state.loadPlugins() }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        PageHeader(
            title = "插件",
            subtitle = state.plugins?.let { "已加载 ${it.loadedCount} · 异常 ${it.problemCount}" } ?: "profile 里的 bundle",
        ) {
            TextButton(
                onClick = { state.loadPlugins(checkUpdates = true) },
                enabled = !state.pluginsChecking,
            ) {
                Text(
                    if (state.pluginsChecking) "正在查版本…" else "检查更新",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            IconButton(onClick = { state.loadPlugins() }, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Filled.Refresh, "重新载入", tint = colors.textSecondary, modifier = Modifier.size(16.dp))
            }
        }

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.pluginsError?.let { error ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(Tokens.radiusMd),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }

            val inventory = state.plugins
            if (inventory == null) {
                if (state.pluginsBusy) LoadingBlock("正在读取插件清单…") else EmptyState(
                    title = "没有读到插件清单",
                    hint = "点右上角刷新。服务端缺 `plugin-inventory-v1` 能力时会空着。",
                )
                return@Column
            }

            if (!inventory.available) {
                Surface(
                    color = colors.mutedSurface,
                    shape = RoundedCornerShape(Tokens.radiusMd),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            "这份清单不可信",
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.warning,
                        )
                        Text(
                            inventory.reason ?: "服务端没给原因。通常是读不到 profile 文件，或格式不是预期的。",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        inventory.profilePath?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                color = colors.textTertiary,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
                return@Column
            }

            inventory.profile?.let { profile ->
                Text(
                    "profile · $profile",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = colors.textTertiary,
                )
            }

            inventory.items.forEach { entry ->
                PluginCard(entry, state)
            }

            if (inventory.items.isEmpty()) {
                EmptyState(
                    title = "没有声明任何插件",
                    hint = "profile 的 `bundles` 是空的。装上插件后这里才会出现条目。",
                )
            }

            Spacer(Modifier.size(6.dp))
            Text(
                "开关只改 profile 里的 `bundles`，不装包也不卸包；改完要重启 DSH 才生效。",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textTertiary,
            )
        }
    }
}

@Composable
private fun PluginCard(entry: PluginEntry, state: AppState) {
    val colors = LocalAppColors.current
    Surface(
        color = colors.mutedSurface,
        shape = RoundedCornerShape(Tokens.radiusMd),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        entry.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 420.dp),
                    )
                    if (entry.official) {
                        Spacer(Modifier.width(6.dp))
                        Tag("官方", colors.textTertiary)
                    }
                    if (entry.updateAvailable == true) {
                        Spacer(Modifier.width(6.dp))
                        Tag("可更新 ${entry.latestVersion ?: ""}".trim(), colors.warning)
                    }
                }
                val version = entry.installed ?: entry.declared
                val sub = buildString {
                    if (!version.isNullOrBlank()) append("v$version")
                    if (entry.state.isNotBlank() && entry.state != "ok") {
                        if (isNotEmpty()) append(" · ")
                        append(entry.state)
                    }
                    if (!entry.loaded) {
                        if (isNotEmpty()) append(" · ")
                        append("已停用")
                    }
                }
                if (sub.isNotEmpty()) {
                    Text(sub, style = MaterialTheme.typography.labelSmall, color = colors.textTertiary)
                }
                entry.description?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            if (entry.toggleable) {
                Switch(
                    checked = entry.loaded,
                    onCheckedChange = { state.togglePlugin(entry.name, it) },
                    modifier = Modifier.padding(start = 12.dp),
                )
            } else {
                Text(
                    "不可切换",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textTertiary,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun Tag(text: String, color: androidx.compose.ui.graphics.Color) {
    Box(
        Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(50))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = color)
    }
}
