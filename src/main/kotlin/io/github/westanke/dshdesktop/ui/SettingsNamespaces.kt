package io.github.westanke.dshdesktop.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.westanke.dshdesktop.AppState
import io.github.westanke.dshdesktop.data.SettingsControl
import io.github.westanke.dshdesktop.data.SettingsFieldSpec
import io.github.westanke.dshdesktop.data.SettingsNamespace
import io.github.westanke.dshdesktop.data.SettingsOp
import io.github.westanke.dshdesktop.data.SettingsSchema
import io.github.westanke.dshdesktop.data.canWriteSettingsNamespaces
import io.github.westanke.dshdesktop.data.clampSettingsNumber
import io.github.westanke.dshdesktop.data.forNativeApp
import io.github.westanke.dshdesktop.data.humanizeFieldName
import io.github.westanke.dshdesktop.data.prettySettingsJson
import io.github.westanke.dshdesktop.refreshSettingsNamespaces
import io.github.westanke.dshdesktop.saveSettingsNamespace
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlin.math.roundToLong

/*
 * ============================================================================
 * 通用设置命名空间表单（P2-1，桥 v2.6.0）
 * ============================================================================
 *
 * 入口过滤（`forNativeApp()`，纯函数在 data 层）：**可写 + 有可编辑字段 +
 * 非纯 WebUI 键**才画。纯 WebUI 命名空间（`ui-theme`/`ui-chat`…）的消费者是
 * 浏览器端那几个前端包，桌面界面一个字都不读——显示它就是给一排拨了没反应的
 * 开关，比不给更糟。
 *
 * 三条降级（与 companion 对齐，理由见 `AppStateSettingsNamespaces.kt` 头注）：
 * - `settingsNamespaces == null`（老桥 / 缺 `settings.read`）→ 整块不画；
 * - `hasDocument == false`（内核还没建文档）→ 空态文案，不报错；
 * - 没有 `settings.write` → 表单照画，只提示"能看不能存"——配对默认五档
 *   （chat.read/chat.write/files.read/files.write/settings.read）里没有它，
 *   这是常态不是故障。
 *
 * 密钥位（`Secret`）只显示"已设置/未设置"，可重填；提交体里**只有用户真的
 * 输入了新值**才会带上那个 path，绝不回显、绝不明文提交。
 */

/**
 * 设置页里的「通用设置」卡。
 *
 * 懒加载由调用方（`SettingsScreen`）负责：`settingsNamespaces == null` 时调
 * `loadSettingsNamespaces()`——那是软失败通道，读不到就静默不画本卡。
 */
@Composable
fun SettingsNamespacesSection(state: AppState) {
    val list = state.settingsNamespaces ?: return
    val colors = LocalAppColors.current
    val canWrite = remember(list) {
        (state.device?.scopes ?: state.profile.scopes).canWriteSettingsNamespaces()
    }
    val visible = remember(list) { list.namespaces.forNativeApp() }
    // 有通道、内核也有文档，但一个能改的命名空间都没有（全是 WebUI 键或只读展览）
    // → 整块不画。用户原话：「不能在 App 上起到作用的都没必要显示」。
    if (visible.isEmpty() && list.hasDocument) return

    AppCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "通用设置",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "按插件设置文档自动渲染 · 保存带 revision 乐观锁",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textTertiary,
                )
            }
            if (state.settingsNamespacesBusy) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = colors.textSecondary)
                Spacer(Modifier.width(10.dp))
            }
            IconButton(
                onClick = { state.refreshSettingsNamespaces() },
                modifier = Modifier.size(30.dp),
            ) {
                Icon(Icons.Filled.Refresh, "刷新", tint = colors.textSecondary, modifier = Modifier.size(16.dp))
            }
        }

        if (!canWrite) {
            Spacer(Modifier.height(8.dp))
            NsNotice("设备没有 settings.write 授权：只能查看、不能保存（配对默认档位不含它，不是故障）")
        }

        if (!list.hasDocument) {
            Spacer(Modifier.height(10.dp))
            Text(
                "桥还没有生成设置文档。",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
            Text(
                "在任意端改过一项设置后，内核会建出这份文档；老桥则始终不显示本卡。",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textTertiary,
            )
            return@AppCard
        }

        visible.forEach { namespace ->
            Spacer(Modifier.height(6.dp))
            NamespaceCard(
                state = state,
                namespace = namespace,
                canWrite = canWrite,
                busy = state.settingsNamespacesBusy,
            )
        }
    }
}

/** 一个命名空间：默认折叠，展开才是表单。折叠态也显示 revision（变了用户能看出来）。 */
@Composable
private fun NamespaceCard(
    state: AppState,
    namespace: SettingsNamespace,
    canWrite: Boolean,
    busy: Boolean,
) {
    var expanded by remember(namespace.ns) { mutableStateOf(false) }
    val fields = remember(namespace) { SettingsSchema.fields(namespace) }
    // 草稿按 revision 键控：保存成功或 409 重拉后 revision 变，旧草稿自动清——
    // 否则用户看着刷新后的新值，保存发出的却是刷新前的旧草稿。
    var drafts by remember(namespace.ns, namespace.revision) { mutableStateOf(mapOf<String, JsonElement>()) }
    val colors = LocalAppColors.current

    Surface(
        shape = RoundedCornerShape(Tokens.radiusSm),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.hairline),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        namespace.ns,
                        style = MaterialTheme.typography.labelLarge,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    val subtitle = buildList {
                        namespace.applies?.takeIf { it.isNotBlank() }?.let { add(it) }
                        add("revision ${namespace.revision}")
                        namespace.reason?.let { add(reasonLabel(it)) }
                    }.joinToString(" · ")
                    Text(subtitle, style = MaterialTheme.typography.labelSmall, color = colors.textTertiary)
                }
                Icon(
                    if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (expanded) "收起" else "展开",
                    tint = colors.textSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }

            if (expanded) {
                Spacer(Modifier.height(8.dp))
                fields.forEach { spec ->
                    FieldRow(
                        spec = spec,
                        enabled = canWrite,
                        draft = drafts[spec.key],
                        onDraft = { key, value -> drafts = if (value == null) drafts - key else drafts + (key to value) },
                    )
                    Spacer(Modifier.height(4.dp))
                }
                if (canWrite) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TextButton(
                            onClick = {
                                val ops = drafts.map { (key, value) ->
                                    SettingsOp(path = listOf(key), value = value)
                                }
                                state.saveSettingsNamespace(namespace.ns, ops)
                                drafts = emptyMap()
                            },
                            enabled = drafts.isNotEmpty() && !busy,
                        ) { Text("保存") }
                        if (drafts.isNotEmpty()) {
                            Text(
                                "${drafts.size} 项未保存",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.warning,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 一个字段：标题/描述一行，编辑器按控件类型分派到下面各 Editor。 */
@Composable
private fun FieldRow(
    spec: SettingsFieldSpec,
    enabled: Boolean,
    draft: JsonElement?,
    onDraft: (String, JsonElement?) -> Unit,
) {
    val colors = LocalAppColors.current
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    spec.title?.takeIf { it.isNotBlank() } ?: humanizeFieldName(spec.key),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                spec.description?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textTertiary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (spec.control == SettingsControl.Boolean) {
                Spacer(Modifier.width(12.dp))
                BooleanEditor(spec = spec, draft = draft, enabled = enabled, onDraft = onDraft)
            }
        }
        when (spec.control) {
            SettingsControl.Enum -> EnumEditor(spec = spec, draft = draft, onDraft = onDraft)
            SettingsControl.Number -> NumberEditor(spec = spec, draft = draft, enabled = enabled, onDraft = onDraft)
            SettingsControl.Text -> TextEditor(spec = spec, draft = draft, enabled = enabled, onDraft = onDraft)
            SettingsControl.Secret -> SecretEditor(spec = spec, draft = draft, enabled = enabled, onDraft = onDraft)
            SettingsControl.ReadOnly -> ReadOnlyEditor(spec = spec)
            SettingsControl.Boolean -> Unit // 开关已画在标题行右侧
        }
    }
}

/** 枚举：一排可横向滚动的胶囊。当前值不在候选里时明说，不偷偷改用户的值。 */
@Composable
private fun EnumEditor(spec: SettingsFieldSpec, draft: JsonElement?, onDraft: (String, JsonElement?) -> Unit) {
    val colors = LocalAppColors.current
    val current = (draft ?: spec.currentValue) as? JsonPrimitive
    val currentText = current?.contentOrNull
    Spacer(Modifier.height(4.dp))
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        spec.enumValues.forEach { option ->
            val selected = option == currentText
            FilterChip(
                selected = selected,
                onClick = { onDraft(spec.key, JsonPrimitive(option)) },
                label = { Text(option, style = MaterialTheme.typography.labelSmall, maxLines = 1) },
            )
        }
    }
    if (currentText != null && currentText !in spec.enumValues) {
        Text(
            "当前值「$currentText」不在候选里，保存前先选一个",
            style = MaterialTheme.typography.labelSmall,
            color = colors.warning,
        )
    }
}

@Composable
private fun BooleanEditor(
    spec: SettingsFieldSpec,
    draft: JsonElement?,
    enabled: Boolean,
    onDraft: (String, JsonElement?) -> Unit,
) {
    val checked = (draft ?: spec.currentValue)?.let { (it as? JsonPrimitive)?.booleanOrNull } ?: false
    Switch(checked = checked, onCheckedChange = { onDraft(spec.key, JsonPrimitive(it)) }, enabled = enabled)
}

/**
 * 数字：夹取在编辑时就地做（`clampSettingsNumber`），脏 schema（min>max）原样放过。
 * 解析不出来的输入**清掉草稿**而不是留旧的——否则用户看着「12x」保存出 12。
 */
@Composable
private fun NumberEditor(
    spec: SettingsFieldSpec,
    draft: JsonElement?,
    enabled: Boolean,
    onDraft: (String, JsonElement?) -> Unit,
) {
    val seed = (draft ?: spec.currentValue) as? JsonPrimitive
    var text by remember(spec.key, spec.currentValue) { mutableStateOf(seed?.contentOrNull ?: "") }
    OutlinedTextField(
        value = text,
        onValueChange = { raw ->
            text = raw
            val parsed = raw.trim().toDoubleOrNull()
            if (parsed == null) {
                onDraft(spec.key, null)
            } else {
                val clamped = clampSettingsNumber(parsed, spec.minimum, spec.maximum)
                val element = if (spec.integer) JsonPrimitive(clamped.roundToLong()) else JsonPrimitive(clamped)
                onDraft(spec.key, element)
            }
        },
        enabled = enabled,
        singleLine = true,
        label = { Text(numberLabel(spec), style = MaterialTheme.typography.labelSmall) },
        shape = RoundedCornerShape(Tokens.radiusSm),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun TextEditor(
    spec: SettingsFieldSpec,
    draft: JsonElement?,
    enabled: Boolean,
    onDraft: (String, JsonElement?) -> Unit,
) {
    val seed = (draft ?: spec.currentValue) as? JsonPrimitive
    var text by remember(spec.key, spec.currentValue) { mutableStateOf(seed?.contentOrNull ?: "") }
    OutlinedTextField(
        value = text,
        onValueChange = { raw ->
            text = raw
            onDraft(spec.key, JsonPrimitive(raw))
        },
        enabled = enabled,
        singleLine = true,
        label = { Text("文本", style = MaterialTheme.typography.labelSmall) },
        shape = RoundedCornerShape(Tokens.radiusSm),
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * 密钥：只显示「已设置 / 未设置」，可重填；**永远不回显值**。
 *
 * 只有用户真的输入了新值才会进草稿（空内容清草稿）——提交体里不该出现
 * 「把 *** 再写回去」这种 op，那既没用也可能触发脱敏校验。
 */
@Composable
private fun SecretEditor(
    spec: SettingsFieldSpec,
    draft: JsonElement?,
    enabled: Boolean,
    onDraft: (String, JsonElement?) -> Unit,
) {
    val colors = LocalAppColors.current
    var reentering by remember(spec.key) { mutableStateOf(false) }
    val set = spec.secretSet || draft != null
    if (!reentering) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Lock, null, tint = colors.textTertiary, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                if (set) "已设置" else "未设置",
                style = MaterialTheme.typography.labelMedium,
                color = colors.textSecondary,
            )
            if (enabled) {
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { reentering = true }) { Text(if (set) "重新输入" else "现在设置") }
            }
        }
    } else {
        var text by remember(spec.key) { mutableStateOf("") }
        OutlinedTextField(
            value = text,
            onValueChange = { raw ->
                text = raw
                onDraft(spec.key, raw.takeIf { it.isNotBlank() }?.let { JsonPrimitive(it) })
            },
            enabled = enabled,
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            label = { Text("新密钥（留空则不修改）", style = MaterialTheme.typography.labelSmall) },
            shape = RoundedCornerShape(Tokens.radiusSm),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** 只读字段（对象/数组/摊平失败）：折叠的 pretty JSON，给人看个结构，不给写入。 */
@Composable
private fun ReadOnlyEditor(spec: SettingsFieldSpec) {
    var open by remember(spec.key) { mutableStateOf(false) }
    val colors = LocalAppColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { open = !open }) { Text(if (open) "收起 JSON" else "查看 JSON") }
        Spacer(Modifier.width(8.dp))
        Text(
            "只读",
            style = MaterialTheme.typography.labelSmall,
            color = colors.textTertiary,
        )
    }
    if (open) {
        Text(
            prettySettingsJson(spec.currentValue ?: JsonPrimitive("null")),
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = colors.textSecondary,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** 顶部提示条（局部一份，不污染 ModelsScreen 的私有 NoticeRow）。 */
@Composable
private fun NsNotice(text: String) {
    val colors = LocalAppColors.current
    Surface(
        shape = RoundedCornerShape(Tokens.radiusSm),
        color = colors.mutedSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = colors.textSecondary,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
        )
    }
}

private fun reasonLabel(reason: String): String = when (reason) {
    "HOST_READONLY" -> "宿主设为只读"
    "HIGH_RISK_NAMESPACE" -> "高危命名空间"
    else -> reason
}

/** `整数 8 – 72` / `数值 ≥ 0`：schema 没给边界就只说类型。 */
private fun numberLabel(spec: SettingsFieldSpec): String {
    val kind = if (spec.integer) "整数" else "数值"
    val lo = spec.minimum?.let { formatBound(it) }
    val hi = spec.maximum?.let { formatBound(it) }
    return when {
        lo != null && hi != null -> "$kind $lo – $hi"
        lo != null -> "$kind ≥ $lo"
        hi != null -> "$kind ≤ $hi"
        else -> kind
    }
}

private fun formatBound(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
