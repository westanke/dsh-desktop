import io.github.westanke.dshdesktop.data.ColumnAlign
import io.github.westanke.dshdesktop.data.MdBlock
import io.github.westanke.dshdesktop.data.classifyChatLink
import io.github.westanke.dshdesktop.data.normalizeRow
import io.github.westanke.dshdesktop.data.parseInline
import io.github.westanke.dshdesktop.data.parseMarkdown
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * Markdown 解析的回归验证：**先跑一组钉死的用例，再用真实会话正文过一遍**。
 *
 * 为什么要有它：表格是最容易"静默退化"的语法 —— 解析器少一层判定，
 * 整张表就摊成几十行竖线文本，而编译依然绿、界面也不报错。那种错只能靠比对形状发现。
 *
 * 退出码：0 通过 · 2 真实数据里表格形状不对 · 3 输入不可用 · 4 内置用例失败
 *
 * 断言助手用公共库 `tools/VerifySupport.kt`（vexpect 明细式：每条都打印）；
 * 跑法见 `tools/verify-markdown.sh` 或 `tools/test.sh`。
 */

fun main(args: Array<String>) {
    println("▸ 内置用例")
    runCases()
    if (verifyFailed > 0) {
        println()
        println("✗ 内置用例失败 $verifyFailed 项")
        kotlin.system.exitProcess(4)
    }

    if (args.isEmpty()) {
        println()
        println("（没有给 history.json，只跑了内置用例；用法：verify-markdown.sh <history.json>）")
        kotlin.system.exitProcess(0)
    }

    val file = File(args[0])
    if (!file.isFile) {
        System.err.println("找不到文件：${file.absolutePath}")
        kotlin.system.exitProcess(3)
    }
    val root = runCatching { Json.parseToJsonElement(file.readText()).jsonObject }.getOrElse {
        System.err.println("不是合法 JSON：${it.message}")
        kotlin.system.exitProcess(3)
    }

    val texts = assistantTexts(root)
    if (texts.isEmpty()) {
        System.err.println("这份响应里没有助手正文（取错端点？）")
        kotlin.system.exitProcess(3)
    }

    println()
    println("▸ 真实正文 ${texts.size} 段（${texts.fold(0) { acc, text -> acc + text.length }} 字符）")
    val counts = linkedMapOf<String, Int>()
    var tables = 0
    var badTable = 0
    var badRows = 0

    for ((index, text) in texts.withIndex()) {
        val blocks = parseMarkdown(text)
        blocks.forEach { block ->
            val key = block::class.simpleName ?: "?"
            counts[key] = (counts[key] ?: 0) + 1
            if (block is MdBlock.Table) {
                tables++
                if (block.headers.isEmpty() || block.rows.isEmpty()) {
                    badTable++
                    println("  ✗ 第 ${index + 1} 段有张空表（headers=${block.headers.size} rows=${block.rows.size}）")
                }
                block.rows.forEach { row ->
                    if (normalizeRow(row, block.columns).size != block.columns) badRows++
                }
            }
        }
    }

    println("  块分布：$counts")
    println("  表格 $tables 张，行数不齐 $badRows 行")
    vexpect("真实数据里解析出了表格", tables > 0, "一张都没有 ⇒ 分隔行判定失效了")
    vexpect("没有空表", badTable == 0)
    vexpect("每张表的行都能补齐到列数", badRows == 0)

    if (verifyFailed > 0) {
        println()
        println("✗ 真实数据验证失败 $verifyFailed 项")
        kotlin.system.exitProcess(2)
    }
    println()
    println("✓ 通过（表格 $tables 张）")
}

/** 从历史响应里取助手正文块。事件可能带外层 `{type:"event", event:{…}}` 包装。 */
private fun assistantTexts(root: JsonObject): List<String> {
    val events = (root["events"] as? JsonArray)?.toList().orEmpty()
    val out = mutableListOf<String>()
    for (element in events) {
        val outer = element as? JsonObject ?: continue
        val inner = (outer["event"] as? JsonObject) ?: outer
        if (inner["type"]?.jsonPrimitive?.content != "assistant/message") continue
        val data = inner["data"] as? JsonObject ?: continue
        val message = (data["message"] as? JsonObject) ?: data
        val content = message["content"]
        if (content is JsonArray) {
            for (block in content) {
                val value = block as? JsonObject ?: continue
                if (value["type"]?.jsonPrimitive?.content != "text") continue
                val text = value["text"]?.jsonPrimitive?.content ?: continue
                if (text.isNotBlank()) out += text
            }
        } else {
            content?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }?.let { out += it }
        }
    }
    return out
}

// ── 内置用例 ─────────────────────────────────────────────────────────────────

private fun blocksOf(source: String) = parseMarkdown(source)

private fun runCases() {
    // 1. 标准管道表
    val t1 = blocksOf("| 仓库 | 目录 |\n|---|---|\n| a | b |\n| c | d |").filterIsInstance<MdBlock.Table>()
    vexpect("标准管道表被识别", t1.size == 1, "实际 ${t1.size}")
    vexpect("表头 2 列", t1.firstOrNull()?.headers == listOf("仓库", "目录"), "${t1.firstOrNull()?.headers}")
    vexpect("两行数据", t1.firstOrNull()?.rows?.size == 2)
    vexpect("单元格内容正确", t1.firstOrNull()?.rows?.get(0) == listOf("a", "b"))

    // 2. 省略首尾管道（GFM 合法）
    val t2 = blocksOf("a | b\n--|--\n1 | 2").filterIsInstance<MdBlock.Table>()
    vexpect("省略首尾管道也能识别", t2.size == 1, "实际 ${t2.size}")
    vexpect("省略管道后列数仍为 2", t2.firstOrNull()?.headers?.size == 2)

    // 3. 转义竖线属于内容
    val t3 = blocksOf("| a \\| b | c |\n|---|---|\n| 1 | 2 |").filterIsInstance<MdBlock.Table>()
    vexpect("转义竖线不切列", t3.firstOrNull()?.headers == listOf("a | b", "c"), "${t3.firstOrNull()?.headers}")

    // 4. 对齐标记
    val t4 = blocksOf("| 左 | 中 | 右 |\n|:--|:--:|--:|\n| 1 | 2 | 3 |").filterIsInstance<MdBlock.Table>()
    vexpect(
        "对齐解析成 Start/Center/End",
        t4.firstOrNull()?.aligns == listOf(ColumnAlign.Start, ColumnAlign.Center, ColumnAlign.End),
        "${t4.firstOrNull()?.aligns}",
    )

    // 5. `---` 是分隔线，不是单列表格
    val hr = blocksOf("上面\n\n---\n\n下面")
    vexpect("--- 仍按分隔线处理", hr.count { it is MdBlock.Rule } == 1 && hr.none { it is MdBlock.Table },
        "${hr.map { it::class.simpleName }}")

    // 6. 含竖线但没分隔行的普通段落不能被当表格
    val plain = blocksOf("端口 3090 | 3091 两个")
    vexpect("无分隔行的竖线文本保持段落", plain.size == 1 && plain[0] is MdBlock.Paragraph,
        "${plain.map { it::class.simpleName }}")

    // 7. 表格前的段落要保住
    val mixed = blocksOf("说明文字\n\n| a | b |\n|---|---|\n| 1 | 2 |")
    vexpect("表格前的段落不丢", mixed.firstOrNull() is MdBlock.Paragraph && mixed.any { it is MdBlock.Table },
        "${mixed.map { it::class.simpleName }}")

    // 8. 少格子/多格子的行能补齐
    val ragged = blocksOf("| a | b | c |\n|---|---|---|\n| 1 |\n| 1 | 2 | 3 | 4 |").filterIsInstance<MdBlock.Table>()
    val rows = ragged.firstOrNull()?.rows.orEmpty()
    vexpect("少格子补空", rows.getOrNull(0)?.size == 3)
    vexpect("多格子并入最后一列", rows.getOrNull(1)?.size == 3 && rows.getOrNull(1)?.get(2) == "3 | 4",
        "${rows.getOrNull(1)}")

    // 9. 行内标记
    val bold = parseInline("**粗** 与 `代码` 与 [标签](https://x.y)")
    vexpect("粗体被识别", bold.any { it.bold && it.text == "粗" })
    vexpect("行内代码被识别", bold.any { it.code && it.text == "代码" })
    vexpect("链接被识别", bold.any { it.url == "https://x.y" && it.text == "标签" })

    // 10. 未闭合标记按字面保留（不能吞内容）
    val raw = parseInline("**没闭合 的星号")
    vexpect("未闭合的 ** 原样保留", raw.joinToString("") { it.text } == "**没闭合 的星号",
        raw.joinToString("") { it.text })

    // 11. 代码块内的竖线不被当表格
    val fenced = blocksOf("```\n| a | b |\n|---|---|\n```")
    vexpect("围栏里的表格语法不外泄", fenced.size == 1 && fenced[0] is MdBlock.Code,
        "${fenced.map { it::class.simpleName }}")

    // 12. 列表与任务列表
    val bullets = blocksOf("- 一\n- [x] 二\n  - 三").filterIsInstance<MdBlock.Bullets>().firstOrNull()
    vexpect("列表 3 项", bullets?.items?.size == 3, "${bullets?.items?.size}")
    vexpect("任务项识别勾选", bullets?.items?.getOrNull(1)?.checked == true)
    vexpect("嵌套层级识别", bullets?.items?.getOrNull(2)?.level == 1, "${bullets?.items?.getOrNull(2)?.level}")

    // 13. 标题
    val headings = blocksOf("# 一\n## 二\n###### 六\n####### 七")
    vexpect("1/2/6 级标题都识别", headings.count { it is MdBlock.Heading } == 3,
        "${headings.map { it::class.simpleName }}")

    // 14. 独立图片（P1-1）：可渲染的出 Image 节点
    val img = blocksOf("![截图](/home/u/shot 1.png)").filterIsInstance<MdBlock.Image>()
    vexpect("独立图片出 Image 节点", img.size == 1, "${img.size}")
    vexpect("alt 与 target 正确", img.firstOrNull()?.alt == "截图" && img.firstOrNull()?.target == "/home/u/shot 1.png",
        "${img.firstOrNull()}")
    // 带尖括号与 title
    val img2 = blocksOf("![a](</p/b.png> \"标题\")").filterIsInstance<MdBlock.Image>()
    vexpect("尖括号与 title 被剥掉", img2.firstOrNull()?.target == "/p/b.png", "${img2.firstOrNull()?.target}")

    // 15. http 图 / 未知扩展名降级成段落（原文保留，不留空白）
    val http = blocksOf("![网图](https://x.y/a.png)")
    vexpect("http 图降级段落", http.size == 1 && http[0] is MdBlock.Paragraph, "${http.map { it::class.simpleName }}")
    val unknown = blocksOf("![日志](/var/log/a.log)")
    vexpect("非图片扩展名降级段落", unknown.size == 1 && unknown[0] is MdBlock.Paragraph,
        "${unknown.map { it::class.simpleName }}")

    // 16. 夹在正文里的行内图降级成链接（不吞原文）
    val inline = parseInline("看这张 ![图](/p/c.png) 就明白了")
    vexpect("行内图降级为链接", inline.any { it.url == "/p/c.png" }, "$inline")
    vexpect("行内图不吞原文", inline.joinToString("") { it.text }.contains("看这张"))

    // 17. 链接分类与行号后缀（P1-2）
    vexpect("绝对路径判为工作区文件", !classifyChatLink("/a/b/c.md").isExternal)
    val l1 = classifyChatLink("/a/b/c.md:24")
    vexpect("冒号行号", l1.lineStart == 24 && l1.lineEnd == 24 && l1.path == "/a/b/c.md", "$l1")
    val l2 = classifyChatLink("/a/b/c.md:24-30")
    vexpect("冒号行号区间", l2.lineStart == 24 && l2.lineEnd == 30, "$l2")
    val l3 = classifyChatLink("/a/b/c.md#L24")
    vexpect("#L 行号", l3.lineStart == 24 && l3.lineEnd == 24, "$l3")
    val l4 = classifyChatLink("/a/b/c.md#L24-L30")
    vexpect("#L 行号区间", l4.lineStart == 24 && l4.lineEnd == 30 && l4.path == "/a/b/c.md", "$l4")
    vexpect("http 仍判外链", classifyChatLink("https://x.y/a.png").isExternal)
    vexpect("相对路径判外链（没有 cwd 别猜）", classifyChatLink("docs/b.md").isExternal)
    vexpect("无后缀的路径行号为空", classifyChatLink("/a/b/c.md").lineStart == null)
}
