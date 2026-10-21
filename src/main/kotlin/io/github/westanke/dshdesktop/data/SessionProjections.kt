package io.github.westanke.dshdesktop.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

/**
 * 会话投影与轨迹：**从历史响应里算出来的两样东西**。
 *
 * 桥接没有给"当前 TODO"和"按轮次时间轴"单独开端点 —— 上游 DSH 内核也没有。
 * 安卓端是从历史响应的 `projections.values` 和事件流里自己算的，这里照同一口径实现：
 * 同一份历史、同一套算法，两个客户端才不会对同一件事给出不同说法。
 *
 * 所有字段都按"缺了也能用"写：投影随内核版本变过形（`permissions` 从
 * `currentValue` 变成过 `{preset, sandbox, approval}`），严格解码会直接崩。
 */

/** 一条待办。`status` 是内核原样给的字符串，UI 只认 completed 与否。 */
data class TodoItem(val content: String, val status: String) {
    val done: Boolean get() = status == "completed" || status == "done"

    val statusLabel: String
        get() = when (status) {
            "completed", "done" -> "已完成"
            "in_progress", "active" -> "进行中"
            "cancelled", "canceled" -> "已取消"
            else -> "待办"
        }
}

/**
 * 会话当前的权限档位与可选项。
 *
 * **两个来源**（内核形状决定的，不是这里偷懒）：
 * - 当前值：会话投影 `permissions`，字段名按 `currentValue → preset → sandbox` 依次降级；
 * - 候选项：`GET /chat/permission-presets` 的目录，投影里通常没有。
 *
 * 候选项为空时**仍然返回**当前值 —— 显示"我不知道能切到哪"比整块消失诚实。
 */
data class PermissionState(
    val current: String,
    val options: List<PermissionOption>,
) {
    /**
     * 显示用的档位名。
     *
     * 目录里 `name` 常常就等于 `value`（服务端的 `normalizePermissionCatalog` 在名字缺失时
     * 拿 value 顶上），这时优先用内置中文名 —— 否则胶囊上会写 `danger-full-access`。
     */
    val currentLabel: String
        get() = options.firstOrNull { it.value == current }?.name
            ?.takeIf { it != current }
            ?: knownPresetLabel(current)

    val switchable: Boolean get() = options.size > 1
}

/**
 * 内核内置档位的显示名。
 *
 * 目录接口没到货（或服务端缺 `permission-presets` 能力）时用它兜底：
 * 把 `danger-full-access` 这种 slug 直接糊到胶囊上，用户得先学会读英文标识符。
 * 名字取自内核 `dsh-permission-presets` 的默认 `presets` 配置。
 */
private val KNOWN_PRESET_LABELS = mapOf(
    "read-only" to "只读",
    "workspace-write" to "工作区可写",
    "danger-full-access" to "完全访问",
)

private fun knownPresetLabel(value: String): String = KNOWN_PRESET_LABELS[value] ?: value

/** 轨迹里的一个事件节点。`kind` 是事件类型原文，UI 直接显示，不翻译成猜的名字。 */
data class TraceStep(val kind: String, val seq: Int, val at: Long)

/**
 * 一轮（turn）。`startedAt/endedAt` 来自 `turn/start`、`turn/end` 的 `time`；
 * 没有收口时 `endedAt` 为 null（正在跑，或历史被截断）。
 */
data class TraceTurn(
    val index: Int,
    val startedAt: Long,
    val endedAt: Long?,
    val steps: List<TraceStep>,
) {
    val toolCount: Int get() = steps.count { it.kind == "tool/call" }

    val durationMs: Long? get() = endedAt?.takeIf { it > 0 && startedAt > 0 }?.minus(startedAt)

    /** 这一轮里出现过的事件类型与次数，按次数降序。 */
    val kinds: List<Pair<String, Int>>
        get() = steps.groupingBy { it.kind }.eachCount().toList().sortedByDescending { it.second }
}

// ── 解析 ────────────────────────────────────────────────────────────────────

private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

/**
 * `projections.values.todos` → 待办清单。
 *
 * 投影整体可能不存在（老服务端、或这一版内核没接这个投影），此时返回空表，
 * 由 UI 决定"没有待办"还是"这份历史没有投影"——两者不该混为一谈。
 */
fun parseTodos(projections: JsonElement?): List<TodoItem> {
    val values = (projections as? JsonObject)?.obj("values") ?: return emptyList()
    val array = values["todos"] as? JsonArray ?: return emptyList()
    return array.mapNotNull { raw ->
        val item = raw as? JsonObject ?: return@mapNotNull null
        val content = item.str("content")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        TodoItem(content, item.str("status").orEmpty())
    }
}

/** `projections.values.permissions` + 目录 → 当前档位；连当前值都读不到时返回 null。 */
fun parsePermission(projections: JsonElement?, catalog: PermissionCatalog?): PermissionState? {
    val values = (projections as? JsonObject)?.obj("values") ?: return null
    val raw = values.obj("permissions") ?: return null
    // 内核形状漂移：旧版 currentValue，新版 preset/sandbox。按可用性依次降级。
    val current = raw.str("currentValue")
        ?: raw.str("preset")
        ?: raw.str("sandbox")
        ?: return null
    val inline = (raw["options"] as? JsonArray).orEmpty().mapNotNull { entry ->
        val option = entry as? JsonObject ?: return@mapNotNull null
        val value = option.str("value") ?: return@mapNotNull null
        PermissionOption(value, option.str("name") ?: value, option.str("description"))
    }
    return PermissionState(current, inline.ifEmpty { catalog?.options.orEmpty() })
}

/** 历史响应里最大的持久事件 seq（用于断档检测的基线）。 */
fun parseMaxSeq(events: List<JsonElement>): Int? = events
    .mapNotNull { raw ->
        val outer = raw as? JsonObject ?: return@mapNotNull null
        val inner = outer.obj("event")
        val target = if (inner != null && outer.str("type") == "event") inner else outer
        (target["seq"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull()
    }
    .maxOrNull()

/**
 * 事件流 → 按轮次的时间轴。
 *
 * 口径与安卓端一致：`turn/start` 开一轮，`turn/end` 收口，其余事件归到当前轮。
 * **不认得的事件类型也保留**（`kind` 原文照抄）——轨迹的价值就在于"看见没被渲染的东西"，
 * 过滤掉未知事件等于把它最有用的部分删了。
 *
 * 历史是分页的，最早一轮可能没有 `turn/start`：那种"无主事件"归到 `index = 0` 的
 * 伪轮次里，并如实显示没有开始时间。
 */
fun buildTrace(events: List<JsonElement>): List<TraceTurn> {
    data class Builder(val index: Int, val startedAt: Long, val steps: MutableList<TraceStep>, var endedAt: Long?)

    val turns = mutableListOf<Builder>()
    var current: Builder? = null
    var index = 0
    val orphans = mutableListOf<TraceStep>()

    for (raw in events) {
        val outer = raw as? JsonObject ?: continue
        val inner = outer.obj("event")
        val event = if (inner != null && outer.str("type") == "event") inner else outer
        val type = event.str("type") ?: continue
        val seq = (event["seq"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: -1
        val time = (event["time"] as? JsonPrimitive)?.longOrNull
            ?: (event["time"] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()?.toLong()
            ?: 0L

        when (type) {
            "turn/start" -> {
                index += 1
                val builder = Builder(index, time, mutableListOf(), null)
                turns += builder
                current = builder
            }
            "turn/end" -> {
                current?.endedAt = time
                current?.steps?.add(TraceStep(type, seq, time))
                current = null
            }
            else -> {
                val step = TraceStep(type, seq, time)
                val target = current
                if (target != null) target.steps.add(step) else orphans += step
            }
        }
    }

    val result = turns.map { TraceTurn(it.index, it.startedAt, it.endedAt, it.steps) }.toMutableList()
    if (orphans.isNotEmpty()) {
        // 本轮之前的残留事件（历史被截断时常见）：单独一段，不伪造开始时间
        result.add(0, TraceTurn(0, orphans.firstOrNull { it.at > 0 }?.at ?: 0L, null, orphans))
    }
    return result
}
