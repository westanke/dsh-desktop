package io.github.westanke.dshdesktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.westanke.dshdesktop.AppState
import io.github.westanke.dshdesktop.createProvider
import io.github.westanke.dshdesktop.data.ProviderModel
import io.github.westanke.dshdesktop.data.ProviderPatch
import io.github.westanke.dshdesktop.data.ProviderView
import io.github.westanke.dshdesktop.deleteProvider
import io.github.westanke.dshdesktop.discoverModels
import io.github.westanke.dshdesktop.loadProviders
import io.github.westanke.dshdesktop.resetProviderModels
import io.github.westanke.dshdesktop.saveProvider

/**
 * 模型页：供应商、凭据、模型目录。
 *
 * 对齐安卓端的「模型」页。桌面端原来完全靠 WebUI 干这件事 —— 能看到会话在用什么模型，
 * 但改不了地址、填不了密钥、发现不了新模型。
 *
 * 一条刻意的限制：**密钥只能重设，不能查看**。服务端只回 `configured` 与来源，
 * 界面就只做"重设"这一个动作，不假装能读出来。
 */
@Composable
fun ModelsScreen(state: AppState) {
    val colors = LocalAppColors.current
    var editing by remember { mutableStateOf<ProviderView?>(null) }
    var creating by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { if (state.providerSettings == null) state.loadProviders() }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        PageHeader(
            title = "模型",
            subtitle = state.providerSettings?.let { "${it.providers.size} 个供应商" } ?: "供应商与凭据",
        ) {
            TextButton(onClick = { creating = true }) { Text("新建自定义", style = MaterialTheme.typography.labelMedium) }
            IconButton(onClick = { state.loadProviders() }, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Filled.Refresh, "重新载入", tint = colors.textSecondary, modifier = Modifier.size(16.dp))
            }
        }

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            state.providersError?.let { NoticeRow(it, danger = true) }

            val settings = state.providerSettings
            if (settings == null) {
                if (state.providersBusy) LoadingBlock("正在读取供应商设置…") else EmptyState(
                    title = "没有读到供应商设置",
                    hint = "点右上角刷新。服务端缺 `provider-settings-v1` 能力时也会空着。",
                )
                return@Column
            }
            if (!settings.writable) {
                NoticeRow("服务端把设置标成只读（`writable: false`），这里的改动会被拒。", danger = false)
            }

            settings.providers.forEach { provider ->
                ProviderCard(
                    provider = provider,
                    busy = state.providersBusy,
                    discovered = if (editing?.id == provider.id) state.discoveredModels else null,
                    onEdit = { editing = provider },
                    onDiscover = { state.discoverModels(provider.id) },
                    onResetModels = { state.resetProviderModels(provider.id) },
                    onDelete = { state.deleteProvider(provider.id) },
                )
            }

            Spacer(Modifier.size(4.dp))
            Text(
                "模型目录（全局）",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            val catalog = state.modelCatalog
            if (catalog == null) {
                Text(
                    "没读到 `settings/models`。这不影响用会话自己的模型列表。",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textTertiary,
                )
            } else {
                catalog.groups.forEach { group ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            group.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "${group.models.size} 个模型",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textTertiary,
                        )
                    }
                }
                catalog.failures.forEach { failure ->
                    Text(
                        "· ${failure.provider}：${failure.message}",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.warning,
                    )
                }
            }
        }
    }

    editing?.let { provider ->
        ProviderEditor(
            provider = provider,
            busy = state.providersBusy,
            discovered = state.discoveredModels,
            onDismiss = { editing = null },
            onSave = { patch ->
                state.saveProvider(provider.id, patch, provider.settingsNs)
                editing = null
            },
        )
    }

    if (creating) {
        CreateProviderDialog(
            protocols = state.providerSettings?.customProvider?.protocols.orEmpty(),
            busy = state.providersBusy,
            onDismiss = { creating = false },
            onCreate = { id, name, baseURL, api, key, modelId ->
                state.createProvider(id, name, baseURL, api, key, listOf(ProviderModel(modelId)))
                creating = false
            },
        )
    }
}

@Composable
private fun ProviderCard(
    provider: ProviderView,
    busy: Boolean,
    discovered: List<ProviderModel>?,
    onEdit: () -> Unit,
    onDiscover: () -> Unit,
    onResetModels: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = LocalAppColors.current
    Surface(
        color = colors.mutedSurface,
        shape = RoundedCornerShape(Tokens.radiusMd),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    provider.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    provider.id,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = colors.textTertiary,
                )
                Spacer(Modifier.weight(1f))
                if (provider.active) Badge("使用中", MaterialTheme.colorScheme.primary)
                else if (!provider.configured) Badge("未配置", colors.warning)
            }
            val baseURL = provider.config.baseURL ?: provider.defaultBaseURL
            if (baseURL != null) {
                Text(
                    baseURL,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "凭据：" + when {
                        provider.credential.configured ->
                            "已配置" + (provider.credential.source?.let { "（$it）" } ?: "")
                        provider.nativeAuth?.oauth == true -> "未配置（支持 OAuth 登录）"
                        else -> "未配置"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (provider.credential.configured) colors.success else colors.warning,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "模型 ${provider.config.models.size} 个" + if (provider.config.modelsInherited) "（继承默认目录）" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textTertiary,
                )
            }
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (provider.configurable) {
                    ActionText("编辑", MaterialTheme.colorScheme.primary, !busy, onEdit)
                    ActionText("发现模型", MaterialTheme.colorScheme.primary, !busy, onDiscover)
                }
                if (provider.config.modelsInherited.not() && provider.configurable) {
                    ActionText("恢复默认目录", colors.textSecondary, !busy, onResetModels)
                }
                if (provider.removable) {
                    ActionText("删除", colors.danger, !busy, onDelete)
                }
            }
            if (discovered != null) {
                Spacer(Modifier.size(8.dp))
                Text(
                    if (discovered.isEmpty()) "没有发现模型" else "发现 ${discovered.size} 个候选（在编辑里挑要写进目录的）",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textTertiary,
                )
                discovered.take(8).forEach {
                    Text(
                        "· ${it.id}",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
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
 * 编辑供应商。
 *
 * 只放三个字段：显示名、baseURL、密钥。`api` 协议是适配器契约的一部分，
 * 改错会让整条链路失败而不只是"格式不对"，所以不给在界面上改 ——
 * 要换协议就新建一个自定义供应商。
 */
@Composable
private fun ProviderEditor(
    provider: ProviderView,
    busy: Boolean,
    discovered: List<ProviderModel>?,
    onDismiss: () -> Unit,
    onSave: (ProviderPatch) -> Unit,
) {
    var name by remember(provider.id) { mutableStateOf(provider.config.displayName.orEmpty()) }
    var baseURL by remember(provider.id) { mutableStateOf(provider.config.baseURL ?: provider.defaultBaseURL.orEmpty()) }
    var apiKey by remember(provider.id) { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑 ${provider.displayName}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("显示名") },
                    shape = RoundedCornerShape(Tokens.radiusSm),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = baseURL,
                    onValueChange = { baseURL = it },
                    singleLine = true,
                    label = { Text("Base URL") },
                    shape = RoundedCornerShape(Tokens.radiusSm),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    singleLine = true,
                    label = { Text(if (provider.credential.configured) "重设密钥（留空则不改）" else "API Key") },
                    visualTransformation = PasswordVisualTransformation(),
                    shape = RoundedCornerShape(Tokens.radiusSm),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "密钥只写不读：服务端只回「是否已配置」，读不回来。留空表示沿用现有值。",
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalAppColors.current.textTertiary,
                )
                if (discovered != null && discovered.isNotEmpty()) {
                    Text(
                        "发现 ${discovered.size} 个模型，保存时一并写入目录。",
                        style = MaterialTheme.typography.labelSmall,
                        color = LocalAppColors.current.textSecondary,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        ProviderPatch(
                            displayName = name.takeIf { it.isNotBlank() },
                            baseURL = baseURL.takeIf { it.isNotBlank() },
                            apiKey = apiKey.takeIf { it.isNotBlank() },
                            models = discovered?.takeIf { it.isNotEmpty() },
                        ),
                    )
                },
                enabled = !busy,
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun CreateProviderDialog(
    protocols: List<String>,
    busy: Boolean,
    onDismiss: () -> Unit,
    onCreate: (String, String, String, String, String, String) -> Unit,
) {
    var id by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var baseURL by remember { mutableStateOf("") }
    var api by remember { mutableStateOf(protocols.firstOrNull() ?: "openai-completions") }
    var key by remember { mutableStateOf("") }
    var modelId by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建自定义供应商") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = id,
                    onValueChange = { id = it },
                    singleLine = true,
                    label = { Text("id（唯一，字母数字）") },
                    shape = RoundedCornerShape(Tokens.radiusSm),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("显示名（可空）") },
                    shape = RoundedCornerShape(Tokens.radiusSm),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = baseURL,
                    onValueChange = { baseURL = it },
                    singleLine = true,
                    label = { Text("Base URL") },
                    shape = RoundedCornerShape(Tokens.radiusSm),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = api,
                    onValueChange = { api = it },
                    singleLine = true,
                    label = { Text("协议（如 openai-completions）") },
                    shape = RoundedCornerShape(Tokens.radiusSm),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (protocols.isNotEmpty()) {
                    Text(
                        "服务端支持：" + protocols.joinToString("、"),
                        style = MaterialTheme.typography.labelSmall,
                        color = LocalAppColors.current.textTertiary,
                    )
                }
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    singleLine = true,
                    label = { Text("API Key（可空）") },
                    visualTransformation = PasswordVisualTransformation(),
                    shape = RoundedCornerShape(Tokens.radiusSm),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = modelId,
                    onValueChange = { modelId = it },
                    singleLine = true,
                    label = { Text("首个模型 id（必填）") },
                    shape = RoundedCornerShape(Tokens.radiusSm),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(id, name, baseURL, api, key, modelId) },
                enabled = !busy && id.isNotBlank() && baseURL.isNotBlank() && modelId.isNotBlank(),
            ) { Text("创建") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

// ── 共用小件（本页私有） ────────────────────────────────────────────────────

@Composable
private fun Badge(text: String, color: androidx.compose.ui.graphics.Color) {
    Box(
        Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(50))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
private fun ActionText(label: String, color: androidx.compose.ui.graphics.Color, enabled: Boolean, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        color = if (enabled) color else LocalAppColors.current.textTertiary,
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
    )
}

@Composable
private fun NoticeRow(text: String, danger: Boolean) {
    val colors = LocalAppColors.current
    Surface(
        color = if (danger) MaterialTheme.colorScheme.errorContainer else colors.mutedSurface,
        shape = RoundedCornerShape(Tokens.radiusMd),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = if (danger) MaterialTheme.colorScheme.onErrorContainer else colors.textSecondary,
            modifier = Modifier.padding(12.dp),
        )
    }
}
