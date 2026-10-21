package io.github.westanke.dshdesktop.data

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * 文件预览的"该怎么看"判定层。
 *
 * 全部是纯函数，不碰 UI、不碰网络 —— 这样 `tools/verify-files.sh` 能直接编译它们跑用例。
 * UI 只管按 [FileType.kind] 分流：Markdown 自己渲染、文本等宽显示、图片解码上屏、
 * 其余交给系统默认程序。
 *
 * 为什么判定要放在客户端而不是问服务端 `Content-Type`：
 * 服务端对未知扩展名一律给 `application/octet-stream`，靠它分不出 md 和 exe；
 * 而且"能不能就地看"是客户端自己的事（有没有对应的渲染器）。
 */

/** 预览分流结果。 */
enum class FileKind {
    /** 自己渲染成文档（Markdown）。 */
    Markdown,

    /** 等宽纯文本，可选编辑。 */
    Text,

    /** 解码成位图显示。 */
    Image,

    /** 渲染不了（PDF / Office / 压缩包 / 二进制），给"用系统程序打开"。 */
    Other,
}

/**
 * 一个文件的类型判定结果。
 *
 * @param kind 预览分流
 * @param label 徽标上的短名字（中文）
 * @param ext 小写扩展名，没有扩展名时为空串
 * @param reason 为什么不能内嵌渲染（[FileKind.Other] 时给人看）
 */
data class FileType(
    val kind: FileKind,
    val label: String,
    val ext: String,
    val reason: String? = null,
)

/** 文本预览上限。超过就只给前一段，完整内容请下载。 */
const val MAX_TEXT_PREVIEW_BYTES: Int = 512 * 1024

/**
 * 图片上限。比文本严得多：一张 4000×3000 的 PNG 解码成位图要 45 MB，
 * 而本应用的堆只有几百 MB，放开了会把界面拖垮。
 */
const val MAX_IMAGE_BYTES: Int = 8 * 1024 * 1024

/** 人话的字节数。文件列表和预览提示共用一份，两处各写一个迟早对不上。 */
fun formatFileSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
}

private val MARKDOWN_EXT = setOf("md", "markdown", "mdown", "mkd", "mkdn", "mdx", "rmd")

private val IMAGE_EXT = setOf("png", "jpg", "jpeg", "gif", "webp", "bmp")

private val TEXT_EXT = setOf(
    "txt", "text", "log", "csv", "tsv", "json", "jsonl", "ndjson", "json5",
    "yaml", "yml", "toml", "ini", "cfg", "conf", "properties", "env",
    "xml", "html", "htm", "xhtml", "css", "scss", "less", "sass",
    "js", "mjs", "cjs", "ts", "tsx", "jsx", "vue", "svelte", "astro",
    "kt", "kts", "java", "groovy", "gradle", "scala", "py", "rb", "go", "rs",
    "c", "h", "cc", "cpp", "hpp", "cs", "php", "swift", "m", "mm", "dart",
    "sh", "bash", "zsh", "fish", "bat", "cmd", "ps1", "psm1", "sql", "lua", "pl", "r",
    "gitignore", "gitattributes", "gitmodules", "editorconfig", "npmrc", "nvmrc",
    "dockerignore", "sqlite", "service", "desktop", "rules", "patch", "diff",
    "srt", "vtt", "tex", "bib", "proto", "graphql", "gql", "http", "rest",
    "lock", "sum", "mod", "targets", "tf", "tfvars", "hcl", "nix", "cmake", "mk",
)

/** 渲染不了的文件给个人话名字，别只显示扩展名。 */
private val OTHER_LABELS = mapOf(
    "pdf" to "PDF 文档",
    "doc" to "Word 文档", "docx" to "Word 文档", "wps" to "WPS 文档", "rtf" to "RTF 文档", "odt" to "ODT 文档",
    "xls" to "Excel 表格", "xlsx" to "Excel 表格", "ods" to "ODS 表格",
    "ppt" to "演示文稿", "pptx" to "演示文稿", "odp" to "演示文稿",
    "zip" to "压缩包", "tar" to "压缩包", "gz" to "压缩包", "tgz" to "压缩包",
    "bz2" to "压缩包", "xz" to "压缩包", "7z" to "压缩包", "rar" to "压缩包", "zst" to "压缩包",
    "exe" to "可执行程序", "msi" to "安装包", "deb" to "安装包", "rpm" to "安装包",
    "appimage" to "安装包", "dmg" to "磁盘映像", "iso" to "光盘映像", "apk" to "安卓安装包",
    "mp3" to "音频", "wav" to "音频", "flac" to "音频", "aac" to "音频", "m4a" to "音频", "ogg" to "音频",
    "mp4" to "视频", "mkv" to "视频", "avi" to "视频", "mov" to "视频", "webm" to "视频", "flv" to "视频",
    "ttf" to "字体", "otf" to "字体", "woff" to "字体", "woff2" to "字体",
    "jar" to "Java 包", "class" to "Java 类文件", "so" to "动态库", "dll" to "动态库", "dylib" to "动态库",
    "o" to "目标文件", "a" to "静态库", "lib" to "静态库", "pdb" to "调试符号", "pyc" to "字节码",
    "svg" to "矢量图", "psd" to "PSD 图稿", "ai" to "AI 图稿", "sketch" to "设计稿", "fig" to "设计稿",
    "db" to "数据库", "sqlite3" to "数据库", "mdb" to "数据库",
    "cer" to "证书", "crt" to "证书", "pem" to "证书", "p12" to "证书", "pfx" to "证书", "key" to "密钥",
)

/**
 * 按文件名判类型。**只看名字**，不看内容 —— 内容层面的覆盖（二进制、图片魔数）
 * 由调用方在读到字节后用 [refineByContent] 补一刀。
 */
fun classifyFile(name: String): FileType {
    val bare = name.substringAfterLast('/').substringAfterLast('\\')
    val lower = bare.lowercase()
    val dot = lower.lastIndexOf('.')
    // dot > 0：`.gitignore` 这种"点开头"不算扩展名，它的整个名字才是标识
    val ext = if (dot > 0) lower.substring(dot + 1) else ""

    if (ext in MARKDOWN_EXT) return FileType(FileKind.Markdown, "Markdown", ext)
    if (ext in IMAGE_EXT) return FileType(FileKind.Image, "图片", ext)
    if (ext in TEXT_EXT) return FileType(FileKind.Text, "文本", ext)

    if (ext.isEmpty()) {
        // 无扩展名（Makefile / .gitignore / 编译出来的 a.out）：先一律当文本，
        // 真要是二进制，[refineByContent] 读到内容后会降级成 Other
        return FileType(FileKind.Text, "文本", "")
    }

    return FileType(
        FileKind.Other,
        OTHER_LABELS[ext] ?: "二进制文件",
        ext,
        reason = "客户端不内嵌渲染 ${OTHER_LABELS[ext] ?: "这类文件"}，可以交给系统默认程序打开。",
    )
}

/**
 * 读到内容后补判：名字说是文本、内容却是二进制（或反过来）时以内容为准。
 *
 * 典型场景：`.csv` 导出的实际是 xlsx 的二进制；`data.md` 里塞了个压缩包。
 */
fun refineByContent(type: FileType, bytes: ByteArray): FileType = when {
    type.kind == FileKind.Image -> type
    looksBinary(bytes) -> FileType(
        FileKind.Other,
        if (type.kind == FileKind.Markdown) "二进制（扩展名像 Markdown）" else "二进制文件",
        type.ext,
        reason = "内容里含 NUL 字节，不是文本，客户端不渲染。",
    )
    else -> type
}

/** 解码结果。`charset` 给 null 表示按 UTF-8 解、没别的可说。 */
data class DecodedText(
    val text: String,
    /** 实际用的字符集，用于界面提示（null = UTF-8） */
    val charset: String? = null,
    /** 编码回退的说明，给人看 */
    val note: String? = null,
)

/**
 * 一次预览的全部附加结果。
 *
 * 做成不可变、整体替换（而不是散成几个 `var`）：否则会出现"文本已换成新文件、
 * 图片还是上一张"这种半截状态，界面看着对、点开是错的。
 */
data class PreviewExtra(
    val type: FileType,
    /** 实际解码用的字符集，null = UTF-8 */
    val charset: String? = null,
    /** 需要提醒人的话（编码回退、体积截断之外的原因等） */
    val note: String? = null,
    /** 图片原始字节；非图片为 null。解码在 UI 层做，状态层只留数据 */
    val imageBytes: ByteArray? = null,
    /** 图片真实格式（按魔数认出来的） */
    val imageFormat: String? = null,
)

/**
 * 文本解码：BOM 优先，其次严格 UTF-8，失败退 GBK。
 *
 * 为什么非要 GBK：Windows 上大量中文文件是记事本/Excel 存的 GBK，
 * 严格 UTF-8 解出来是乱码或直接抛异常。Linux 默认 UTF-8，但也常碰到 GBK 老文件。
 *
 * **不做换行归一**：预览文本同时是编辑草稿的底稿，这里把 CRLF 改成 LF，
 * 用户在 Windows 上改一个字保存，整个文件就被悄悄换了行尾。归一留给渲染层。
 */
fun decodeText(bytes: ByteArray): DecodedText {
    bomDecoded(bytes)?.let { return it }

    strictDecode(bytes, Charsets.UTF_8)?.let { return DecodedText(it) }

    // GBK：JDK 各平台都带，Windows / Linux 一致
    val gbk = runCatching { Charset.forName("GBK") }.getOrNull()
    if (gbk != null) {
        strictDecode(bytes, gbk)?.let {
            return DecodedText(it, "GBK", "按 UTF-8 解不通，已回退 GBK。编辑保存会按原字节写回。")
        }
    }

    // 两种都解不开（多半是乱码的二进制碎片），按 UTF-8 宽容模式收拾，至少能显示
    return DecodedText(
        String(bytes, Charsets.UTF_8),
        "UTF-8（有损）",
        "既不是合法 UTF-8 也不是 GBK，按 UTF-8 有损显示，可能有乱码。",
    )
}

private fun bomDecoded(bytes: ByteArray): DecodedText? = when {
    bytes.startsWithBytes(0xEF, 0xBB, 0xBF) -> DecodedText(
        String(bytes, 3, bytes.size - 3, Charsets.UTF_8), "UTF-8 BOM",
    )
    bytes.startsWithBytes(0xFF, 0xFE) -> DecodedText(
        String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE), "UTF-16LE",
    )
    bytes.startsWithBytes(0xFE, 0xFF) -> DecodedText(
        String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE), "UTF-16BE",
    )
    else -> null
}

private fun strictDecode(bytes: ByteArray, charset: Charset): String? = runCatching {
    charset.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes))
        .toString()
}.getOrElse { if (it is CharacterCodingException) null else throw it }

/**
 * 是不是二进制。
 *
 * 判据是"前 8 KiB 里有 NUL 字节"。带 BOM 的 UTF-16 文本同样含大量 NUL，
 * 所以 **BOM 判定必须先于本函数**（[decodeText] 已经是这个顺序）。
 */
fun looksBinary(bytes: ByteArray): Boolean {
    if (bytes.startsWithBytes(0xEF, 0xBB, 0xBF) ||
        bytes.startsWithBytes(0xFF, 0xFE) ||
        bytes.startsWithBytes(0xFE, 0xFF)
    ) return false
    val n = minOf(bytes.size, 8 * 1024)
    for (i in 0 until n) if (bytes[i] == 0.toByte()) return true
    return false
}

/**
 * 图片容器的真实格式（按魔数），不认识就 null。
 *
 * 光看扩展名不可靠：`.png` 里可能是 JPEG，甚至是改了后缀的压缩包。
 * 交给 Skia 解码前先认一下，省得抛一堆看不懂的异常。
 */
fun imageFormatOf(bytes: ByteArray): String? = when {
    bytes.startsWithBytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) -> "png"
    bytes.startsWithBytes(0xFF, 0xD8, 0xFF) -> "jpeg"
    bytes.startsWithBytes(0x47, 0x49, 0x46, 0x38) -> "gif" // GIF8
    bytes.startsWithBytes(0x42, 0x4D) -> "bmp"
    bytes.size >= 12 &&
        bytes.startsWithBytes(0x52, 0x49, 0x46, 0x46) && // RIFF
        bytes[8] == 'W'.code.toByte() && bytes[9] == 'E'.code.toByte() &&
        bytes[10] == 'B'.code.toByte() && bytes[11] == 'P'.code.toByte() -> "webp"
    else -> null
}

private fun ByteArray.startsWithBytes(vararg prefix: Int): Boolean {
    if (size < prefix.size) return false
    for (i in prefix.indices) {
        if (this[i] != prefix[i].toByte()) return false
    }
    return true
}
