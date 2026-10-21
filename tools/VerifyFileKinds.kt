import io.github.westanke.dshdesktop.data.FileKind
import io.github.westanke.dshdesktop.data.classifyFile
import io.github.westanke.dshdesktop.data.decodeText
import io.github.westanke.dshdesktop.data.formatFileSize
import io.github.westanke.dshdesktop.data.imageFormatOf
import io.github.westanke.dshdesktop.data.looksBinary
import io.github.westanke.dshdesktop.data.refineByContent
import java.nio.charset.Charset

/**
 * 文件类型判定与文本解码的内置用例。
 *
 * 为什么不用 JUnit：这个工程没有测试框架依赖（见 TECH-DEBT DEBT-001），
 * 而这两层是纯函数，一个 main 函数就够了。跑法见 `tools/verify-files.sh`。
 *
 * 覆盖的都是**真踩过的**坑：Windows 记事本的 GBK、Excel 导出带 BOM 的 CSV、
 * 改了后缀的二进制、`README.MD` 这种大写扩展名。
 */
private var failed = 0
private var passed = 0

private fun check(name: String, ok: Boolean, detail: String = "") {
    if (ok) {
        passed++
    } else {
        failed++
        println("  ✗ $name${if (detail.isEmpty()) "" else " —— $detail"}")
    }
}

private fun expectKind(name: String, file: String, kind: FileKind, ext: String? = null) {
    val type = classifyFile(file)
    check(
        "$name：$file → $kind",
        type.kind == kind && (ext == null || type.ext == ext),
        "实际 ${type.kind}/${type.ext}",
    )
}

private fun expectLabel(name: String, file: String, label: String) {
    val type = classifyFile(file)
    check("$name：$file 标签 = $label", type.label == label, "实际 ${type.label}")
}

fun main() {
    println("▸ 按文件名分类")

    expectKind("Markdown", "readme.md", FileKind.Markdown, "md")
    expectKind("大写扩展名", "README.MD", FileKind.Markdown, "md")
    expectKind("中文名", "接口说明.markdown", FileKind.Markdown)
    expectKind("mdx", "page.mdx", FileKind.Markdown)
    expectKind("图片 png", "shot.png", FileKind.Image, "png")
    expectKind("图片 JPEG 大写", "photo.JPEG", FileKind.Image, "jpeg")
    expectKind("图片 webp", "a.webp", FileKind.Image)
    expectKind("代码", "Main.kt", FileKind.Text, "kt")
    expectKind("配置", "docker-compose.yml", FileKind.Text, "yml")
    expectKind("无扩展名", "Makefile", FileKind.Text, "")
    expectKind("点开头", ".gitignore", FileKind.Text, "")
    expectKind("路径里带目录", "src/main/App.kt", FileKind.Text, "kt")
    expectKind("Windows 反斜杠路径", "src\\main\\App.kt", FileKind.Text, "kt")

    expectKind("PDF", "报告.pdf", FileKind.Other, "pdf")
    expectKind("Word", "合同.docx", FileKind.Other, "docx")
    expectKind("压缩包（双扩展名取最后一段）", "backup.tar.gz", FileKind.Other, "gz")
    expectKind("安卓包", "app.apk", FileKind.Other, "apk")
    expectKind("SVG（要光栅化，交给系统）", "logo.svg", FileKind.Other, "svg")

    expectLabel("PDF 说人话", "报告.pdf", "PDF 文档")
    expectLabel("Word 说人话", "合同.docx", "Word 文档")
    expectLabel("apk 说人话", "app.apk", "安卓安装包")

    println("▸ 内容覆盖名字")

    val binary = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x00, 0x00, 0x08)
    val liedAsText = refineByContent(classifyFile("data.csv"), binary)
    check("冒充 csv 的二进制被降级", liedAsText.kind == FileKind.Other, "实际 ${liedAsText.kind}")
    check("降级要说明原因", !liedAsText.reason.isNullOrBlank())

    val liedAsMd = refineByContent(classifyFile("readme.md"), binary)
    check("冒充 md 的二进制被降级", liedAsMd.kind == FileKind.Other, "实际 ${liedAsMd.kind}")

    val realText = refineByContent(classifyFile("a.txt"), "中文内容".toByteArray(Charsets.UTF_8))
    check("真文本保持文本", realText.kind == FileKind.Text, "实际 ${realText.kind}")

    // 图片里天然有 NUL（PNG 头就有），不能被"含 NUL 就是二进制"误降级
    val imageWithNul = refineByContent(classifyFile("x.png"), byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0, 0))
    check("图片不被 NUL 误降级", imageWithNul.kind == FileKind.Image, "实际 ${imageWithNul.kind}")

    println("▸ 二进制判定")

    check("NUL 字节算二进制", looksBinary(byteArrayOf(1, 2, 0, 3)))
    check("UTF-8 中文不算二进制", !looksBinary("中文测试，标点：。！".toByteArray(Charsets.UTF_8)))
    val utf16 = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + "中文".toByteArray(Charsets.UTF_16LE)
    check("带 BOM 的 UTF-16 不算二进制", !looksBinary(utf16))
    check("空文件不算二进制", !looksBinary(ByteArray(0)))

    println("▸ 文本解码")

    val plain = decodeText("中文测试".toByteArray(Charsets.UTF_8))
    check("UTF-8 原文", plain.text == "中文测试", "实际 ${plain.text}")
    check("UTF-8 不标 charset", plain.charset == null, "实际 ${plain.charset}")

    val bom = decodeText(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "带BOM".toByteArray(Charsets.UTF_8))
    check("UTF-8 BOM 被吃掉", bom.text == "带BOM", "实际 ${bom.text}")
    check("UTF-8 BOM 要标出来", bom.charset == "UTF-8 BOM", "实际 ${bom.charset}")

    val utf16Text = decodeText(utf16)
    check("UTF-16LE 解出中文", utf16Text.text == "中文", "实际 ${utf16Text.text}")
    check("UTF-16LE 标 charset", utf16Text.charset == "UTF-16LE", "实际 ${utf16Text.charset}")

    val gbkBytes = "中文测试".toByteArray(Charset.forName("GBK"))
    val gbk = decodeText(gbkBytes)
    check("GBK 回退成功", gbk.text == "中文测试", "实际 ${gbk.text}")
    check("GBK 要标出来", gbk.charset == "GBK", "实际 ${gbk.charset}")
    check("GBK 回退要留说明", !gbk.note.isNullOrBlank())

    // 换行不做归一：编辑草稿要保原样，否则 Windows 文件保存一次就被改成 LF
    val crlf = decodeText("a\r\nb\r\n".toByteArray(Charsets.UTF_8))
    check("CRLF 原样保留", crlf.text == "a\r\nb\r\n", "实际 ${crlf.text.replace("\r", "\\r").replace("\n", "\\n")}")

    println("▸ 图片魔数")

    check("PNG", imageFormatOf(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) == "png")
    check("JPEG", imageFormatOf(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())) == "jpeg")
    check("GIF", imageFormatOf("GIF89a".toByteArray()) == "gif")
    check("BMP", imageFormatOf(byteArrayOf(0x42, 0x4D, 0, 0)) == "bmp")
    val webp = "RIFF".toByteArray() + byteArrayOf(0, 0, 0, 0) + "WEBP".toByteArray()
    check("WEBP", imageFormatOf(webp) == "webp")
    check("文本不是图片", imageFormatOf("hello world".toByteArray()) == null)

    println("▸ 体积格式化")

    check("512 B", formatFileSize(512) == "512 B", formatFileSize(512))
    check("2.0 KB", formatFileSize(2048) == "2.0 KB", formatFileSize(2048))
    check("5.0 MB", formatFileSize(5L * 1024 * 1024) == "5.0 MB", formatFileSize(5L * 1024 * 1024))

    println()
    if (failed == 0) {
        println("✓ 全部通过（$passed 项）")
    } else {
        println("✗ $failed 项未通过，$passed 项通过")
        kotlin.system.exitProcess(1)
    }
}
