package io.github.westanke.dshdesktop.data

import java.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 「配置导入」的解码：把一段 `DSH1:` 短文本还原成一条连接配置。
 *
 * ## 线格式（与生成端一致，别单方面改）
 *
 * 生成端在 dsh-companion 仓（`tools/emit-config.mjs`）与安卓 `data/ConnectionShare.kt`，
 * 三端必须认同一种格式：
 *
 * ```
 * DSH1:<UTF-8 JSON 的 Base64 URL-safe 无填充编码>
 * ```
 *
 * JSON 结构（字段名即 [Payload] 的字段名）：
 *
 * ```json
 * {
 *   "displayName": "家里的电脑",
 *   "endpoints": [{ "label": "家里局域网", "baseUrl": "http://192.168.1.126:3090" }],
 *   "token": "设备令牌",
 *   "deviceName": "Pixel 9",
 *   "scopes": ["files.read"]
 * }
 * ```
 *
 * ## 为什么是文本而不是二维码
 *
 * 真实场景是「人已经出门在外，电脑留在家里」。扫码/念配对码都要求配对那一刻电脑在旁，
 * 看不到屏幕就扫不了码。文本可以由用户在电脑上复制、发给自己（微信文件传输助手/邮件/
 * 私密笔记），到目的地再粘贴。所以这条路径的容错要求高于普通解析：**用户粘贴时已经离开
 * 电脑，此刻失败就等于整个方案失败**。
 *
 * ## 安全边界（重要）
 *
 * 这段文本**本身就是凭据**（内含设备令牌），等价于一把钥匙；Base64 只是编码不是加密。
 * 导入后令牌仍走 `AppStorage` 的 AES-GCM 加密落盘（token.enc + device.key），
 * 与配对流程同一套设施。UI 不得把这段文本或解出的令牌打进日志、toast、错误详情；
 * 粘贴框用后即清。
 *
 * ## 桌面端阶段一的范围（X9，见 inbox 2026-10-23-batch3）
 *
 * **只做导入，不做生成**：生成端已有 emit-config.mjs，桌面作为接收端消费即可。
 * 多地址文本只取第一个地址（[toImported]），落进现有单条 `ConnectionProfile`，
 * 不碰选路核心；其余地址计数告知、不静默丢弃。[encode] 仅为往返测试和将来的导出端保留，
 * UI 不暴露——生成动作留给电脑侧工具。
 */
object ConnectionShare {

    const val PREFIX = "DSH1:"

    @Serializable
    data class Payload(
        val displayName: String = "",
        val endpoints: List<Endpoint> = emptyList(),
        val token: String? = null,
        val deviceName: String? = null,
        val scopes: List<String> = emptyList(),
    )

    /**
     * 分享文本里的一个地址。
     *
     * 刻意没有 `id` / `kind` / `enabled`：那些是本机状态（探活历史、选路标记），
     * 换一台设备就没有意义，写进去每个地址白花约 40 个 Base64 字符。
     * 对面若多写了（例如直接拿安卓 `Payload.serializer()` 输出的 JSON），
     * [decode] 靠 `ignoreUnknownKeys` 照读不误。
     */
    @Serializable
    data class Endpoint(
        val label: String = "",
        val baseUrl: String,
    )

    /** 解码失败的具体原因。中文文案由 UI 层映射——data 层不替界面说话。 */
    enum class Reason {
        /** 空白输入（连一个可解析字符都没有）。 */
        EMPTY,

        /** 有内容但 Base64 解不出（含长度 ≡ 1 mod 4 这种任何变体下都不可能的形状）。 */
        NOT_BASE64,

        /** Base64 解出了字节，但不是合法 JSON（花括号提取后再试也一样）。 */
        NOT_JSON,

        /** JSON 合法但 `endpoints` 为空。 */
        NO_ENDPOINT,

        /** 有地址但不可用：不是 http/https，或没有主机名。detail 带出问题的地址。 */
        UNUSABLE_URL,
    }

    sealed interface DecodeResult {
        data class Success(val payload: Payload) : DecodeResult
        data class Failure(val reason: Reason, val detail: String = "") : DecodeResult
    }

    /** `/api/v1` 是 [io.github.westanke.dshdesktop.data.DshApi] 的接口前缀，不是地址的一部分。 */
    private const val API_SUFFIX = "/api/v1"

    /**
     * encode/decode 共用的 Json 配置。
     *
     * - `encodeDefaults = false`：默认值不写，省掉 `"scopes":[]` 这类字符；
     * - `explicitNulls = false`：`token` / `deviceName` 为 null 时连键都不写；
     * - `ignoreUnknownKeys = true`：对面多写字段（含 `id`、`kind`）也照读，向前兼容；
     * - `isLenient = true`：对轻微不规范的 JSON 宽容点，反正后面还有地址校验兜底。
     */
    private val json = Json {
        encodeDefaults = false
        explicitNulls = false
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * 把配置编码成可粘贴文本：`DSH1:` + URL-safe 无填充 Base64。
     *
     * **阶段一的 UI 不暴露它**（桌面只做导入）；保留给往返测试与将来的导出端。
     * 非法输入（无地址 / 地址不是 http/https）抛 [IllegalArgumentException] ——
     * 这是全文件唯一故意抛异常的地方：生成时人还在电脑前，能立刻看到并修正。
     */
    fun encode(payload: Payload): String {
        val wire = payload.toWire()
        require(wire.endpoints.isNotEmpty()) { "至少需要一个地址才能生成配置文本" }
        wire.endpoints.forEach { endpoint ->
            require(isUsableBaseUrl(endpoint.baseUrl)) {
                "地址必须是 http:// 或 https:// 开头（去掉 /api/v1 之后）：${endpoint.baseUrl}"
            }
        }
        val body = json.encodeToString(WirePayload.serializer(), wire)
        return PREFIX + Base64.getUrlEncoder().withoutPadding()
            .encodeToString(body.toByteArray(Charsets.UTF_8))
    }

    /**
     * 解析用户粘贴的文本。任何失败都返回 [DecodeResult.Failure]，**绝不抛异常** ——
     * 崩溃或误连都比明确说「这段文本无效」更糟。
     *
     * 容错清单（等价于：encode 的产物经过微信/邮件折腾后仍能还原）：
     * 首尾与内部的空白换行、零宽字符与 BOM、带或不带前缀（大小写不敏感）、
     * 前缀出现在整段文本任意位置（前面带「Pixel 9 的配置：」这类标签）、
     * URL-safe 与标准 Base64 两种字母表、缺失的 `=` 填充、结尾粘上的标点符号。
     */
    fun decode(text: String): DecodeResult {
        if (text.isBlank()) return DecodeResult.Failure(Reason.EMPTY)
        val body = stripPrefix(clean(text))
        val bytes = decodeBase64(body) ?: return DecodeResult.Failure(Reason.NOT_BASE64)
        val wire = parseWire(bytes.toString(Charsets.UTF_8))
            ?: return DecodeResult.Failure(Reason.NOT_JSON)
        val payload = wire.toPayload()
        if (payload.endpoints.isEmpty()) return DecodeResult.Failure(Reason.NO_ENDPOINT)
        val bad = payload.endpoints.firstOrNull { !isUsableBaseUrl(it.baseUrl) }
            ?: return DecodeResult.Success(payload)
        return DecodeResult.Failure(Reason.UNUSABLE_URL, bad.baseUrl)
    }

    // ------------------------------------------------------------------
    // 线格式用的私有表示
    //
    // 为什么不直接用 Payload.serializer()：contract 里字段无默认值时直接序列化
    // 会把本机专属值写进分享文本。这里用更窄的线格式表示，同时让解码端对多余字段宽容。
    // ------------------------------------------------------------------

    @Serializable
    private data class WirePayload(
        val displayName: String = "",
        val endpoints: List<WireEndpoint> = emptyList(),
        val token: String? = null,
        val deviceName: String? = null,
        val scopes: List<String> = emptyList(),
    )

    @Serializable
    private data class WireEndpoint(
        val label: String = "",
        val baseUrl: String,
    )

    private fun Payload.toWire() = WirePayload(
        displayName = displayName.trim(),
        endpoints = endpoints.map { endpoint ->
            WireEndpoint(label = endpoint.label.trim(), baseUrl = normalizeBaseUrl(endpoint.baseUrl))
        },
        token = token?.trim()?.takeIf(String::isNotEmpty),
        deviceName = deviceName?.trim()?.takeIf(String::isNotEmpty),
        scopes = scopes.map(String::trim).filter(String::isNotEmpty),
    )

    private fun WirePayload.toPayload() = Payload(
        displayName = displayName.trim(),
        endpoints = endpoints.map {
            Endpoint(label = it.label.trim(), baseUrl = normalizeBaseUrl(it.baseUrl))
        },
        token = token?.trim()?.takeIf(String::isNotEmpty),
        deviceName = deviceName?.trim()?.takeIf(String::isNotEmpty),
        scopes = scopes.map(String::trim).filter(String::isNotEmpty),
    )

    // ------------------------------------------------------------------
    // 地址校验与归一
    // ------------------------------------------------------------------

    /** 基础校验：http/https 且有主机名（`http://` 后面直接是端口或路径的不算）。 */
    private fun isUsableBaseUrl(raw: String): Boolean {
        val normalized = normalizeBaseUrl(raw)
        val schemeLength = when {
            normalized.startsWith("http://") -> 7
            normalized.startsWith("https://") -> 8
            else -> return false
        }
        return normalized.substring(schemeLength)
            .substringBefore('/')
            .substringBefore(':')
            .isNotBlank()
    }

    /**
     * 归一基地址：去首尾空白与尾斜杠、去掉 `/api/v1` 后缀、协议名统一小写。
     * 幂等 —— [encode] 与 [toPayload] 都会调用它。
     */
    private fun normalizeBaseUrl(raw: String): String {
        var value = raw.trim().trimEnd('/')
        if (value.endsWith(API_SUFFIX)) value = value.dropLast(API_SUFFIX.length).trimEnd('/')
        return when {
            value.regionMatches(0, "http://", 0, 7, ignoreCase = true) -> "http://" + value.substring(7)
            value.regionMatches(0, "https://", 0, 8, ignoreCase = true) -> "https://" + value.substring(8)
            else -> value
        }
    }

    // ------------------------------------------------------------------
    // 解码容错
    // ------------------------------------------------------------------

    /** 零宽字符、BOM 之类「看不见但不是空白」的字符，粘贴链路里很常见。 */
    private val INVISIBLE_CHARS = charArrayOf('\uFEFF', '\u200B', '\u200C', '\u200D', '\u2060')

    /** 去掉所有空白（含微信插入的换行、邮件折行、中文全角空格、nbsp）与零宽字符。 */
    private fun clean(text: String): String =
        text.filterNot { char -> char.isWhitespace() || char in INVISIBLE_CHARS }

    /**
     * 前缀可选、大小写不敏感，并且允许它出现在整段文本的任意位置。
     *
     * 因为空白已被清掉，「DS\nH1:」这种被拆开的写法也能命中。`DSH1:` 是刻意设计的哨兵，
     * 所以可以放心地在全文中找它 —— 于是「Pixel 9 Pro 的配置：DSH1:xxxx」这种带标签的
     * 粘贴也能救回来（标签里的 Latin 字母/数字本身就在 Base64 字母表内，靠过滤字符救不回来）。
     * 找不到哨兵就把整段当作纯 Base64 处理。
     */
    private fun stripPrefix(text: String): String {
        val at = text.indexOf(PREFIX, startIndex = 0, ignoreCase = true)
        return if (at >= 0) text.substring(at + PREFIX.length) else text
    }

    /**
     * 解 Base64：先剔掉字母表之外的字符（结尾粘上的句号、引号、标签文字），再补 `=` 填充，
     * 然后先按 URL-safe、再按标准字母表解 —— [encode] 用 URL-safe，但邮件/网页转码可能把它
     * 换成标准字母表，两种都得认。
     */
    private fun decodeBase64(body: String): ByteArray? {
        val alphabetOnly = body.filter(::isBase64Char)
        if (alphabetOnly.isEmpty()) return null
        val remainder = alphabetOnly.length % 4
        // 长度 ≡ 1 (mod 4) 在任何 Base64 变体下都不可能，直接判死，省得后面空转。
        if (remainder == 1) return null
        val padded = if (remainder == 0) alphabetOnly else alphabetOnly + "=".repeat(4 - remainder)
        runCatching { Base64.getUrlDecoder().decode(padded) }.getOrNull()?.let { return it }
        return runCatching { Base64.getDecoder().decode(padded) }.getOrNull()
    }

    private fun isBase64Char(char: Char): Boolean =
        char in 'A'..'Z' || char in 'a'..'z' || char in '0'..'9' ||
            char == '+' || char == '/' || char == '-' || char == '_' || char == '='

    /** 先按原样解析；失败则截出最外层 `{ ... }` 再试一次（解码结果里混了前后缀文字的情况）。 */
    private fun parseWire(raw: String): WirePayload? {
        runCatching { json.decodeFromString(WirePayload.serializer(), raw) }
            .getOrNull()?.let { return it }
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching {
            json.decodeFromString(WirePayload.serializer(), raw.substring(start, end + 1))
        }.getOrNull()
    }
}

/**
 * 导入结果里桌面真正会用的部分。
 *
 * X9 阶段一**只取第一个地址**（不碰多地址选路）：桌面是固定工位单地址模型，
 * 硬上选路只会多一地鸡毛。其余地址不静默丢弃——[extraEndpoints] 计数交给 UI 明说。
 */
data class ImportedConnection(
    /** 电脑侧显示名（payload 的 displayName），只用于提示，不落 profile。 */
    val displayName: String,
    /** 该地址的标签（payload 里没给就退回 baseUrl）。 */
    val label: String,
    /** 已归一的基地址（不含 `/api/v1`）。 */
    val baseUrl: String,
    /** 设备令牌；null 表示这份配置没带令牌，用户仍需配对码。 */
    val token: String?,
    /** 令牌的权限档位；没有令牌时为空。 */
    val scopes: List<String>,
    /** 配置里还有几个地址没被导入。 */
    val extraEndpoints: Int,
)

/**
 * 取配置里的第一个地址变成可导入的连接。
 *
 * **刻意不映射 `deviceName`**：payload 的 deviceName 是*分享端设备*的名字
 * （如「Pixel 9」），而桌面 `ConnectionProfile.deviceName` 是*本机*的名字
 * （配对时上报给服务端）。拿分享端的名字覆盖本机名，服务端看到的设备列表就串了。
 */
fun ConnectionShare.Payload.toImported(): ImportedConnection {
    val first = endpoints.first()
    return ImportedConnection(
        displayName = displayName.trim(),
        label = first.label.trim().ifEmpty { first.baseUrl },
        baseUrl = first.baseUrl,
        token = token?.trim()?.takeIf(String::isNotEmpty),
        scopes = scopes.map(String::trim).filter(String::isNotEmpty),
        extraEndpoints = (endpoints.size - 1).coerceAtLeast(0),
    )
}
