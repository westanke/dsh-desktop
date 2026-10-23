package io.github.westanke.dshdesktop.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 应用内检查更新（P2-2）。
 *
 * 边界写死：**只做检查 + 下载 + 校验 + 安装指引**，不做进程内热自更新、不自动提权。
 * 桌面端替换自己正在运行的程序本来就得重启，热更新只会多一种"半新半旧"的死法；
 * 而静默 `pkexec` 装 deb 在 UOS 上会弹一个用户看不懂的授权框。
 *
 * 三个渠道事实（v1.0.2 实测）：
 * - Gitee `releases/latest` **不需要令牌**，返回体字段集比 GitHub 少（asset 没有 `size`）；
 * - GitHub `releases/latest` **必须带 `User-Agent`**，否则 403；
 * - 两端字段集不同，所以两边都用 `ignoreUnknownKeys` 解析，不强类型化到某一端的形状。
 */

/** 检查更新的渠道：按界面语言分流（用户原话「中文 gitee，英文 github」）。 */
enum class UpdateChannel(val label: String) {
    Gitee("Gitee（国内优先）"),
    GitHub("GitHub"),
    ;

    val latestUrl: String
        get() = when (this) {
            Gitee -> "https://gitee.com/api/v5/repos/westanke/dsh-desktop/releases/latest"
            GitHub -> "https://api.github.com/repos/westanke/dsh-desktop/releases/latest"
        }
}

/** 界面语言 → 渠道。`zh*`（含 `zh-CN`）走 Gitee，其余走 GitHub。 */
fun channelForLanguage(language: String): UpdateChannel =
    if (language.lowercase().startsWith("zh")) UpdateChannel.Gitee else UpdateChannel.GitHub

/** Release 里的一个资产。Gitee 不给 `size`，所以它是可空的。 */
@Serializable
data class ReleaseAsset(
    val name: String = "",
    @SerialName("browser_download_url") val browserDownloadUrl: String = "",
    val size: Long? = null,
)

/** 一个 Release 的投影：只取界面用得上的字段。 */
@Serializable
data class ReleaseInfo(
    // 两端的字段名一致（都是 tag_name），但 Kotlin 默认按 tagName 找键 —— 不显式标注会
    // 静默解析成空串，然后"永远不是新版"。这个坑只在联调真接口时才看得见。
    @SerialName("tag_name") val tagName: String = "",
    val name: String? = null,
    val body: String? = null,
    val prerelease: Boolean = false,
    val assets: List<ReleaseAsset> = emptyList(),
)

/**
 * 逐段比数字判新旧。
 *
 * 字符串比较会让 `2.2.9 > 2.2.47`（"9" > "4"）——这是真实出过的事故，所以只认数字段：
 * 用 `\d+` 把两边的版本号抽成段，按位比，缺的段当 0。
 * 前导 `v` 与非数字分隔符都会被忽略；`1.0.2-beta` 与 `1.0.2` 判为相等（不含预发布语义）。
 */
fun isNewerVersion(remote: String, current: String): Boolean {
    val a = versionSegments(remote)
    val b = versionSegments(current)
    if (a.isEmpty() || b.isEmpty()) return false
    val n = maxOf(a.size, b.size)
    for (i in 0 until n) {
        val x = a.getOrElse(i) { 0 }
        val y = b.getOrElse(i) { 0 }
        if (x != y) return x > y
    }
    return false
}

private fun versionSegments(version: String): List<Int> =
    Regex("""\d+""").findAll(version).map { it.value.toIntOrNull() ?: 0 }.toList()

/**
 * 解析校验清单：`SHA256SUMS` 与单文件 `<包名>.sha256` 是同一格式
 * （`<64 位十六进制><空白><文件名>`，文件名可带 `*` 二进制标记）。
 * 返回 `文件名 → 小写哈希`；认不出的行跳过（清单里可能有注释或空行）。
 */
fun parseSha256Sums(text: String): Map<String, String> {
    val out = LinkedHashMap<String, String>()
    for (raw in text.lineSequence()) {
        val line = raw.trim()
        if (line.isEmpty() || line.startsWith("#")) continue
        val m = Regex("""^([0-9a-fA-F]{64})\s+[*]?(.+)$""").find(line) ?: continue
        val (sha, name) = m.destructured
        out[name.trim()] = sha.lowercase()
    }
    return out
}

/** 更新说明压平：去掉 Markdown 痕迹、压成一行、超长补省略号。 */
fun summarizeReleaseNotes(body: String, max: Int = 280): String {
    val flat = body
        .replace(Regex("""\r\n?"""), "\n")
        .replace(Regex("""!\[[^\]]*]\([^)]*\)"""), " ")   // 图片
        .replace(Regex("""\[([^\]]*)]\([^)]*\)"""), "$1")  // 链接留文字
        .replace(Regex("""[*_`>#]+"""), "")                // 强调/引用/标题符
        // 换行（含空行）一律压成段落记号：说明只有一行宽，硬换行只会被 Ellipsis 砍断
        .replace(Regex("""\n+"""), " ⏎ ")
        .replace(Regex("""[ \t]+"""), " ")
        .trim()
    return if (flat.length <= max) flat else flat.take(max).trimEnd() + "…"
}

/** 桌面平台。Mac 目前没有产物，列出来是为了把"找不到"和"不支持"说清楚。 */
enum class UpdatePlatform(val label: String) {
    Linux("Linux"),
    Windows("Windows"),
    Mac("macOS"),
    Other(""),
}

fun detectPlatform(): UpdatePlatform {
    val os = System.getProperty("os.name").orEmpty().lowercase()
    return when {
        os.contains("win") -> UpdatePlatform.Windows
        os.contains("mac") || os.contains("darwin") -> UpdatePlatform.Mac
        os.contains("nux") || os.contains("nix") -> UpdatePlatform.Linux
        else -> UpdatePlatform.Other
    }
}

/**
 * 归一化架构。`x86_64/amd64/x64 → amd64`，`aarch64/arm64 → arm64`，
 * 认不出来返回空串——**空串会让资产挑选失败并给出明确提示**，比猜一个错的强。
 */
fun detectArch(): String {
    val arch = System.getProperty("os.arch").orEmpty().lowercase()
    return when {
        arch.contains("aarch64") || arch == "arm64" -> "arm64"
        arch == "x86_64" || arch == "amd64" || arch == "x64" -> "amd64"
        else -> ""
    }
}

/**
 * 当前是不是"轻量包"在跑。
 *
 * 判据是 `java.home`：完整包的 java 在 `<安装目录>/lib/runtime` 下（jpackage 布局），
 * 轻量包把运行时下载到 `~/.local/share/dsh-desktop/…`，路径里没有 `lib/runtime`。
 * 认不出时按完整包算——那会推荐完整 deb，体积大一点但不会错。
 */
fun runningLiteInstall(): Boolean {
    val javaHome = System.getProperty("java.home").orEmpty().replace('\\', '/')
    return !javaHome.contains("/lib/runtime")
}

/**
 * 按当前平台/架构/full-lite 挑资产，**按文件名后缀匹配，不按下标**。
 *
 * 命名来自 `tools/package.sh`：
 * - Linux `dsh-desktop_<ver>-1_<arch>.deb` / `dsh-desktop-lite_<ver>-1_<arch>.deb`
 * - Windows `dsh-desktop-<ver>-windows-x64.zip` / `…-windows-x64-lite.zip` / `dsh-desktop-<ver>.msi`
 */
fun pickUpdateAsset(
    assets: List<ReleaseAsset>,
    platform: UpdatePlatform,
    arch: String,
    lite: Boolean,
): ReleaseAsset? {
    val usable = assets.filter { it.name.isNotBlank() && it.browserDownloadUrl.isNotBlank() }
    return when (platform) {
        UpdatePlatform.Linux -> {
            if (arch.isEmpty()) null
            else usable.firstOrNull {
                it.name.endsWith("_$arch.deb") &&
                    it.name.startsWith(if (lite) "dsh-desktop-lite_" else "dsh-desktop_")
            }
        }
        UpdatePlatform.Windows -> if (lite) {
            usable.firstOrNull { it.name.endsWith("-windows-x64-lite.zip") }
        } else {
            usable.firstOrNull { it.name.endsWith(".msi") }
                ?: usable.firstOrNull { it.name.endsWith("-windows-x64.zip") }
        }
        UpdatePlatform.Mac, UpdatePlatform.Other -> null
    }
}

/** 取不到最新版的可读原因（给界面直接显示，不带堆栈）。 */
class UpdateException(val kind: Kind, message: String) : IOException(message) {
    enum class Kind { Network, NotFound, RateLimited, BadPayload }
}

/**
 * 检查更新的网络层。复用仓里现成的 OkHttp（4.12），不新开一套客户端；
 * 超时给得比 bridge 短：这是用户手动点的一下，卡住就该让他知道。
 */
class UpdateChecker(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(25, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun latest(channel: UpdateChannel): Result<ReleaseInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(channel.latestUrl).apply {
                header("Accept", "application/json")
                // GitHub 没 UA 直接 403（实测）；Gitee 无所谓，但带上没坏处
                header("User-Agent", USER_AGENT)
            }.build()
            http.newCall(request).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                when {
                    resp.isSuccessful -> json.decodeFromString(ReleaseInfo.serializer(), text)
                    resp.code == 404 -> throw UpdateException(
                        UpdateException.Kind.NotFound, "渠道上没有 Release（404）",
                    )
                    resp.code == 403 || resp.code == 429 -> throw UpdateException(
                        UpdateException.Kind.RateLimited,
                        "被限流了（${resp.code}），稍后再试；或去 Release 页手动下载",
                    )
                    else -> throw UpdateException(
                        UpdateException.Kind.Network, "HTTP ${resp.code} ${resp.message}",
                    )
                }
            }
        }
    }

    /**
     * 下载资产，边下边报进度。
     *
     * [onProgress] 的 total 可能为 null——Gitee 的资产响应不带 `Content-Length`，
     * 这时界面要显示"大小未知"而不是假的 0%。
     * 64 KiB 分块写盘，不整包进内存（完整包 70 MB）。
     */
    suspend fun download(
        asset: ReleaseAsset,
        dest: File,
        onProgress: (received: Long, total: Long?) -> Unit,
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            dest.parentFile?.mkdirs()
            val request = Request.Builder().url(asset.browserDownloadUrl)
                .header("User-Agent", USER_AGENT)
                .build()
            http.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw UpdateException(
                        UpdateException.Kind.Network,
                        "下载失败：HTTP ${resp.code} ${resp.message}",
                    )
                }
                val body = resp.body ?: throw UpdateException(
                    UpdateException.Kind.Network, "下载失败：响应没有内容",
                )
                val total = body.contentLength().takeIf { it > 0 } ?: asset.size?.takeIf { it > 0 }
                var received = 0L
                dest.outputStream().use { out ->
                    body.byteStream().use { input ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            received += n
                            onProgress(received, total)
                        }
                    }
                }
            }
            dest
        }
    }

    companion object {
        private const val USER_AGENT = "dsh-desktop"
    }
}

/** 分块算 SHA-256（64 KiB 缓冲），返回小写十六进制。 */
fun sha256OfFile(file: File): String {
    val digest = java.security.MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buf = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            digest.update(buf, 0, n)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
