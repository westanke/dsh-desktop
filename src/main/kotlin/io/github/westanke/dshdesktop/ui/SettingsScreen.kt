package io.github.westanke.dshdesktop.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.westanke.dshdesktop.AppState
import io.github.westanke.dshdesktop.ConnectionState

/**
 * 关于页显示的版本号，兜底值。
 *
 * 优先取 jar manifest 的 `Implementation-Version`（`tools/build.sh` 打 jar 时写入，
 * 跟着 `version.txt` 走）；直接跑 `build/classes` 时读不到 manifest，才回落这个常量。
 * 常量由 `tools/docs-check.sh` 与 `version.txt` 对齐，漂了 CI 会红。
 */
private const val VERSION_FALLBACK = "1.0.1"

// 拿 manifest 得有一个类做锚点：`SettingsScreen` 是函数不是类，这里借 AppState（同一个 jar）
private val appVersion: String by lazy {
    AppState::class.java.`package`?.implementationVersion?.takeIf { it.isNotBlank() }
        ?: VERSION_FALLBACK
}

@Composable
fun SettingsScreen(state: AppState) {
    val colors = LocalAppColors.current
    val device = state.device

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        PageHeader("设置", "连接、权限与外观")
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ── 连接 ─────────────────────────────────────────────
            AppCard(Modifier.fillMaxWidth()) {
                SectionLabel("连接")
                Spacer(Modifier.height(10.dp))
                InfoRow("服务器", state.profile.endpoint.ifBlank { "—" }, mono = true)
                InfoRow(
                    "状态",
                    when (state.connection) {
                        ConnectionState.Connected -> "已连接"
                        ConnectionState.Connecting -> "连接中…"
                        ConnectionState.Failed -> "连接失败"
                        ConnectionState.Disconnected -> "未连接"
                    },
                    tone = when (state.connection) {
                        ConnectionState.Connected -> colors.success
                        ConnectionState.Failed -> colors.danger
                        else -> colors.textSecondary
                    },
                )
                InfoRow("设备令牌", if (state.token == null) "未保存" else "已加密保存（AES-GCM）")

                var deviceName by remember { mutableStateOf(state.profile.deviceName) }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = deviceName,
                    onValueChange = { deviceName = it },
                    label = { Text("本机名称（下次配对时上报）", style = MaterialTheme.typography.labelSmall) },
                    singleLine = true,
                    shape = RoundedCornerShape(Tokens.radiusSm),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            state.updateDeviceName(deviceName.trim())
                            state.showToast("已保存本机名称")
                        },
                        enabled = deviceName.isNotBlank() && deviceName != state.profile.deviceName,
                    ) { Text("保存名称") }
                    TextButton(onClick = { state.autoConnect() }) { Text("重新连接") }
                    TextButton(onClick = { state.disconnect(clearToken = false) }) { Text("断开") }
                    TextButton(onClick = { state.disconnect(clearToken = true) }) {
                        Text("忘记这台服务器", color = colors.danger)
                    }
                }
            }

            // ── 权限 ─────────────────────────────────────────────
            AppCard(Modifier.fillMaxWidth()) {
                SectionLabel("这台设备的权限")
                Spacer(Modifier.height(4.dp))
                Text(
                    "权限由电脑端的「手机设置 → 设备」分配，客户端只能查看，不能自升。",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textTertiary,
                )
                Spacer(Modifier.height(12.dp))
                val scopes = device?.scopes ?: state.profile.scopes
                if (scopes.isEmpty()) {
                    Text("尚未读到权限列表", style = MaterialTheme.typography.bodySmall, color = colors.textTertiary)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        scopes.chunked(2).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { scope ->
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(Tokens.radiusSm),
                                    ) {
                                        Text(
                                            scopeLabel(scope),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                InfoRow("已授权根目录", "${(device?.rootIds ?: state.profile.rootIds).size} 个")
                InfoRow("设备 ID", device?.id ?: state.profile.deviceId.ifBlank { "—" }, mono = true)
            }

            // ── 外观 ─────────────────────────────────────────────
            AppCard(Modifier.fillMaxWidth()) {
                SectionLabel("外观")
                Spacer(Modifier.height(4.dp))
                Text(
                    "主题会立刻生效，记在本地 prefs.json。亮色暗色不是开关而是两套完整配色，",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textTertiary,
                )
                Text(
                    "所以选「跟随系统」时，系统明暗变了会自动在暖砂与深海之间切。",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textTertiary,
                )
                Spacer(Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeChoices.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { (id, label, hint) ->
                                ThemeCard(
                                    label = label,
                                    hint = hint,
                                    id = id,
                                    selected = state.prefs.theme == id,
                                    modifier = Modifier.weight(1f),
                                    onClick = { state.savePrefs { it.copy(theme = id) } },
                                )
                            }
                        }
                    }
                }
            }

            // ── 会话 ─────────────────────────────────────────────
            AppCard(Modifier.fillMaxWidth()) {
                SectionLabel("会话")
                Spacer(Modifier.height(4.dp))
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "显示上下文注入",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            "记忆、系统提示这类注入内容默认不进消息流（官方 Web 也默认收起来）；开了会在对话里多一个「上下文注入」折叠块。",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textTertiary,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    androidx.compose.material3.Switch(
                        checked = state.prefs.showContextInjection,
                        onCheckedChange = { on ->
                            state.savePrefs { it.copy(showContextInjection = on) }
                        },
                    )
                }
            }

            // ── 关于 ─────────────────────────────────────────────
            AppCard(Modifier.fillMaxWidth()) {
                SectionLabel("关于")
                Spacer(Modifier.height(10.dp))
                InfoRow("客户端", "DSH Desktop $appVersion · Compose Desktop（Skia 原生渲染）")
                InfoRow("运行环境", "${System.getProperty("os.name")} · JDK ${System.getProperty("java.version")}")
                InfoRow("协议", "dsh-remote-bridge REST /api/v1 + WebSocket /api/v1/events")
                InfoRow("配置目录", io.github.westanke.dshdesktop.data.AppStorage.root().toString(), mono = true)
                Spacer(Modifier.height(10.dp))
                Text(
                    "网络层复用 dsh-remote-bridge 的 kotlin-sdk（AGPL-3.0）。本客户端同样以 AGPL-3.0 分发；" +
                        "它不渲染网页，界面由 Skia 直接绘制。",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textTertiary,
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, mono: Boolean = false, tone: Color? = null) {
    val colors = LocalAppColors.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
            modifier = Modifier.width(112.dp),
        )
        Text(
            value,
            style = if (mono) {
                MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            } else {
                MaterialTheme.typography.bodySmall
            },
            color = tone ?: MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ThemeCard(
    label: String,
    hint: String,
    id: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val palette = if (id == "system") null else AllPalettes.firstOrNull { it.id == id }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(Tokens.radiusMd),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = if (selected) {
            androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
        } else {
            androidx.compose.foundation.BorderStroke(1.dp, LocalAppColors.current.hairline)
        },
        modifier = modifier,
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ThemeSwatch(palette)
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) MaterialTheme.colorScheme.primary else LocalAppColors.current.textSecondary,
                    )
                    if (selected) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.Filled.Check,
                            null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(13.dp),
                        )
                    }
                }
                Text(
                    hint,
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalAppColors.current.textTertiary,
                    maxLines = 1,
                )
            }
        }
    }
}

/** 色卡：底 + 主色点 + 文字条。`system` 画成左右各一半（亮/暗）。 */
@Composable
private fun ThemeSwatch(palette: Palette?) {
    if (palette == null) {
        Row(Modifier.size(width = 34.dp, height = 24.dp)) {
            Box(
                Modifier.weight(1f).fillMaxHeight()
                    .background(SandPalette.surface, RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp)),
            )
            Box(
                Modifier.weight(1f).fillMaxHeight()
                    .background(AbyssPalette.surface, RoundedCornerShape(topEnd = 6.dp, bottomEnd = 6.dp)),
            )
        }
        return
    }
    Box(
        Modifier
            .size(width = 34.dp, height = 24.dp)
            .background(palette.surface, RoundedCornerShape(6.dp))
            .padding(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(palette.accent, CircleShape))
            Spacer(Modifier.width(4.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Box(Modifier.fillMaxWidth(0.9f).height(2.dp).background(palette.textSecondary, CircleShape))
                Box(Modifier.fillMaxWidth(0.6f).height(2.dp).background(palette.hairline, CircleShape))
            }
        }
    }
}

private fun scopeLabel(scope: String): String = when (scope) {
    "chat.read" -> "读会话"
    "chat.write" -> "写会话"
    "files.read" -> "读文件"
    "files.write" -> "写文件"
    "files.delete" -> "删文件"
    "settings.read" -> "读设置"
    "settings.write" -> "写设置"
    else -> scope
}
