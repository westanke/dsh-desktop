package io.github.westanke.dshdesktop.data

import java.util.Base64
import java.util.Locale

/**
 * 一条**已读进内存、还没发出去**的附件。
 *
 * 为什么不在选文件时就直接上传换 `receiptId`：凭证只在**同一次 prompt** 里有效
 * （见 `ApiExt.kt` 的协议说明）。用户挑完文件又改主意删掉、或者干脆不发，
 * 都会在服务端留下一条没人引用的上传记录。所以这里只持有字节，
 * 真正发送那一刻才决定"图片内联 / 文件换凭证"。
 */
data class PendingAttachment(
    val id: String,
    val name: String,
    val mediaType: String,
    val bytes: ByteArray,
) {
    val isImage: Boolean get() = mediaType.startsWith("image/")

    /** 图片内联走的 base64；文件走上传接口也是同一份编码，所以只算一次。 */
    val base64: String by lazy { Base64.getEncoder().encodeToString(bytes) }

    val sizeLabel: String
        get() = when {
            bytes.size >= 1024 * 1024 -> String.format(Locale.ROOT, "%.1f MB", bytes.size / 1048576.0)
            bytes.size >= 1024 -> "${bytes.size / 1024} KB"
            else -> "${bytes.size} B"
        }
}

/** 桥接单次附件上限 24 MB（服务端把关）；这里先拦一道，省得白传。 */
const val MAX_ATTACHMENT_BYTES = 24 * 1024 * 1024

/**
 * 按扩展名猜 MIME。
 *
 * 不用 `Files.probeContentType`：它在这台 UOS 上依赖 xdg 共享库，
 * 桌面环境裁剪过的机器上会返回 null；而猜错的代价只是"显示成通用文件图标"。
 * 图片必须猜准——是不是 image 前缀决定走内联还是走上传。
 */
fun guessMediaType(name: String): String = when (name.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
    "png" -> "image/png"
    "jpg", "jpeg" -> "image/jpeg"
    "gif" -> "image/gif"
    "webp" -> "image/webp"
    "bmp" -> "image/bmp"
    "svg" -> "image/svg+xml"
    "pdf" -> "application/pdf"
    "json" -> "application/json"
    "txt", "log", "md", "kt", "sh", "ts", "js", "py", "yml", "yaml", "toml" -> "text/plain"
    else -> "application/octet-stream"
}
