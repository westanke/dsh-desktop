import io.github.westanke.dshdesktop.data.ChatItem
import io.github.westanke.dshdesktop.data.ToolBlockView
import io.github.westanke.dshdesktop.data.chatStats
import io.github.westanke.dshdesktop.data.parseHistory
import io.github.westanke.dshdesktop.data.toolBlockView
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.io.File

/**
 * 用一份真实的历史响应验证投影，守护「会话整页空白」这条回归。
 *
 * 判据只有一条，但很硬：**事件数 > 0 而可渲染条目数 = 0 就是回归**。
 * 2026-10-21 真机那次就是 305 条事件全被过滤、界面全白，而编译是绿的。
 *
 * 顺带把新增的两套口径也量出来（只统计、不判定）：
 * - **工具专用块分布**：read/bash/edit/write 各有多少条走到了专用视图；
 * - **耗时可得率**：有多少条工具配上了 `tool/result.time`。
 * 这两项若全为 0/0%，说明工具名或事件形状变了 —— 打印 ⚠ 提示，不算失败。
 *
 * 退出码：0 正常 · 2 回归（有事件但一条都渲染不出）· 3 输入不可用
 */
fun main(args: Array<String>) {
    if (args.isEmpty()) {
        System.err.println("用法：VerifyHistoryKt <history.json>")
        kotlin.system.exitProcess(3)
    }
    val file = File(args[0])
    if (!file.isFile) {
        System.err.println("找不到文件：${file.absolutePath}")
        kotlin.system.exitProcess(3)
    }

    val root = runCatching {
        Json.parseToJsonElement(file.readText()).jsonObject
    }.getOrElse {
        System.err.println("不是合法 JSON：${it.message}")
        kotlin.system.exitProcess(3)
    }

    val events = (root["events"] as? kotlinx.serialization.json.JsonArray)?.toList().orEmpty()
    if (events.isEmpty()) {
        System.err.println("这份响应里没有 events（可能取错了端点）")
        kotlin.system.exitProcess(3)
    }

    val items = parseHistory(events)
    val dist = items.groupingBy { it::class.simpleName ?: "?" }.eachCount()

    println("事件 ${events.size} 条（hasMore=" + (root["hasMore"]?.toString() ?: "?") + "）")
    println("可渲染 ${items.size} 条：$dist")

    // ── 新增口径的量化 ────────────────────────────────────────────────────
    val tools = items.flatMap { item ->
        when (item) {
            is ChatItem.ToolGroup -> item.tools
            is ChatItem.Tool -> listOf(item)
            else -> emptyList()
        }
    }
    if (tools.isNotEmpty()) {
        val blocks = tools.groupingBy { toolBlockView(it.name, it.arguments, it.result)::class.simpleName ?: "?" }
            .eachCount()
        val specialized = blocks.filterKeys { it != "Generic" }.values.sum()
        println("工具专用块 $blocks（专用 $specialized/${tools.size}）")
        val withDuration = tools.count { it.durationMs != null }
        println("耗时可算 $withDuration/${tools.size}")
        val names = tools.map { it.name }.distinct().sorted()
        println("工具名：${names.joinToString("、")}")
        if (specialized == 0) {
            println("⚠ 全部走了通用块 —— 检查工具名是否已变更（见 data/ToolBlockView.kt 的分发表）")
        }
    }
    val stats = chatStats(items)
    println("统计：" + stats.summary().ifBlank { "（空）" })

    if (items.isEmpty()) {
        System.err.println()
        System.err.println("✗ 回归：有 ${events.size} 条事件，却一条都投影不出来。")
        System.err.println("  先看 PITFALLS.md §历史事件有外层包装 —— 历史每条是 {\"type\":\"event\",\"event\":{…}}。")
        kotlin.system.exitProcess(2)
    }

    println("--- 前 6 条 ---")
    items.take(6).forEach { item ->
        val preview = when (item) {
            is ChatItem.User -> item.text
            is ChatItem.Assistant -> item.text
            is ChatItem.Reasoning -> item.text
            is ChatItem.Tool -> "${item.name} ${item.arguments.take(50)}"
            is ChatItem.ToolGroup -> "${item.title} [${item.tools.size} 条]"
            is ChatItem.Deliverable -> item.files.joinToString("、") { it.name }
            is ChatItem.Notice -> "[${item.level}] ${item.text}"
        }.replace("\n", " ").take(64)
        println("  #${item.seq} ${item::class.simpleName}: $preview")
    }
    println()
    println("✓ 通过（${items.size} 条可渲染）")
}
