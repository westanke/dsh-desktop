package io.github.westanke.dshdesktop.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl

/**
 * 桥接服务的补充契约。
 *
 * 基础 REST/WS 客户端原样复用 dsh-remote-bridge 的 kotlin-sdk（AGPL-3.0），
 * 这里只补 SDK 未覆盖、但桌面端确实要用的端点：会话列表、绝对路径反查、
 * Agent 提问。全部保持"字段可空 + 默认值"，因为服务端字段会随插件版本增减。
 */

@Serializable
data class ChatSession(
    val id: String,
    val rootId: String = "",
    val cwd: String = "",
    val updatedAt: Long = 0,
    val running: Boolean = false,
    val blank: Boolean = true,
    val title: String? = null,
    val workspaceId: String? = null,
    val workspaceTitle: String? = null,
    val agentPreset: String? = null,
    val pendingInteraction: String? = null,
    val cwdAbsolute: String? = null,
) {
    /** 会话标题为空时给一个稳定占位，避免列表出现空白行。 */
    val displayTitle: String
        get() = title?.takeIf { it.isNotBlank() }
            ?: cwd.substringAfterLast('/').takeIf { it.isNotBlank() }
            ?: "新会话"

    val hasPendingApproval: Boolean get() = pendingInteraction == "approval"
}

/** `GET /roots/resolve?path=<绝对路径>`：把会话事件里的绝对路径翻译成 rootId + 相对路径。 */
@Serializable
data class ResolvedPath(
    val rootId: String = "",
    val path: String = "",
    val kind: String = "",
    val size: Long? = null,
    val modifiedAt: Double = 0.0,
    val contentType: String? = null,
)

@Serializable
data class QuestionOption(val label: String, val description: String? = null)

@Serializable
data class AskQuestion(
    val id: String,
    val question: String,
    val header: String? = null,
    val detail: String? = null,
    val multiSelect: Boolean = false,
    val options: List<QuestionOption> = emptyList(),
)

@Serializable
data class QuestionCall(
    val callId: String,
    val state: String = "",
    val viaBridge: Boolean = false,
    val questions: List<AskQuestion> = emptyList(),
)

@Serializable
private data class SessionList(val items: List<ChatSession> = emptyList())

@Serializable
private data class QuestionList(val items: List<QuestionCall> = emptyList())

@Serializable
private data class AnswerItem(val id: String, val selected: List<String>, val custom: String? = null)

@Serializable
private data class AnswerRequest(val answers: List<AnswerItem>)

@Serializable
private data class SentAnswer(val ok: Boolean = true)

/** 归档会话用 `archived=true` 单独取；归档后默认列表就看不到它了。 */
fun DshWorkspaceClient.listChatSessions(archived: Boolean = false): List<ChatSession> {
    val url = endpoint("chat/sessions").newBuilder()
        .addQueryParameter("archived", archived.toString())
        .build()
    return executeJson(url, "GET", null, SessionList.serializer()).items
}

/**
 * 取一页历史。
 *
 * 服务端默认只给**最近**一段（实测一个会话返回 305 条、`hasMore: true`），
 * 想往回翻必须带 `beforeSeq`；`maxMessages` 控制页大小。
 */
fun DshWorkspaceClient.listMessagesPage(
    sessionId: String,
    beforeSeq: Int? = null,
    maxMessages: Int? = null,
): JsonElement {
    val url = endpoint("chat/sessions/$sessionId/messages").newBuilder().apply {
        beforeSeq?.let { addQueryParameter("beforeSeq", it.toString()) }
        maxMessages?.let { addQueryParameter("maxMessages", it.toString()) }
    }.build()
    return execute(url, "GET", null, true).use { response ->
        ensureSuccess(response)
        json.parseToJsonElement(response.body?.string() ?: "{}")
    }
}

/** 设备自身的授权根 id（配对响应里没有，要单独问一次，否则 `profile.rootIds` 永远是空）。 */
fun DshWorkspaceClient.selfDevice(): DeviceView = currentDevice()

fun DshWorkspaceClient.resolvePath(absolutePath: String): ResolvedPath {    val url = endpoint("roots/resolve").newBuilder()
        .addQueryParameter("path", absolutePath)
        .build()
    return executeJson(url, "GET", null, ResolvedPath.serializer())
}

fun DshWorkspaceClient.listSessionQuestions(sessionId: String): List<QuestionCall> =
    executeJson(
        endpoint("chat/sessions/$sessionId/questions"),
        "GET",
        null,
        QuestionList.serializer(),
    ).items

/**
 * 回答一组提问。[selected] 必须是问题自己的选项 label；自由文本走 [custom]。
 * 这个问题可能正把 agent 阻塞住，所以失败必须让调用方看见。
 */
fun DshWorkspaceClient.answerQuestion(
    sessionId: String,
    callId: String,
    answers: List<Triple<String, List<String>, String?>>,
) {
    val body = json.encodeToString(
        AnswerRequest.serializer(),
        AnswerRequest(answers.map { AnswerItem(it.first, it.second, it.third) }),
    )
    executeJson(
        endpoint("chat/sessions/$sessionId/questions/$callId/answer"),
        "POST",
        body,
        SentAnswer.serializer(),
    )
}

/** 上传：与 `writeFile` 同一端点，语义是"整文件覆盖/新建"，这里给个更直白的名字。 */
fun DshWorkspaceClient.uploadEntry(
    rootId: String,
    path: String,
    bytes: ByteArray,
    etag: String? = null,
): String = writeFile(rootId, path, bytes, etag)

internal fun HttpUrl.Builder.query(key: String, value: String?): HttpUrl.Builder =
    apply { if (value != null) addQueryParameter(key, value) }

// ────────────────────────────────────────────────────────────────────────────
// 附件：图片内联、文件换 receiptId
//
// 协议事实（`src/host/router.ts` 的 `readPromptParts` + `attachmentsRoute`）：
// - `POST chat/sessions/{id}/messages` 同时接受旧的 `{text}` 与新的 `{content:[…]}`，
//   后者支持 `{type:text}` / `{type:image, mediaType, data(base64), name?}` /
//   `{type:file, receiptId}`。**只有图片允许内联 base64**，文件必须先上传换凭证。
// - `POST chat/sessions/{id}/attachments` body `{data(base64), name?}` → `{receiptId, name}`
// - `GET attachments/{attachmentId}?sessionId=…` → 原始字节（id 形如 `sha256:…`，含冒号，
//   必须 URL 编码）
// ────────────────────────────────────────────────────────────────────────────

/** 一次附件上传的凭证。`receiptId` 只在**同一次 prompt** 里有效，不能存下来复用。 */
@Serializable
data class AttachmentReceipt(val receiptId: String, val name: String? = null)

@Serializable
private data class AttachmentUpload(val data: String, val name: String? = null)

/** 发送内容片段。序列化成 wire 的 `content[]`。 */
sealed interface PromptPart {
    data class Text(val text: String) : PromptPart
    data class Image(val mediaType: String, val base64: String, val name: String? = null) : PromptPart
    data class FileRef(val receiptId: String) : PromptPart

    fun toJson(): JsonObject = when (this) {
        is Text -> buildJsonObject {
            put("type", "text")
            put("text", text)
        }
        is Image -> buildJsonObject {
            put("type", "image")
            put("mediaType", mediaType)
            put("data", base64)
            if (!name.isNullOrBlank()) put("name", name)
        }
        is FileRef -> buildJsonObject {
            put("type", "file")
            put("receiptId", receiptId)
        }
    }
}

/** 带内容片段发一条消息。`[steer]` 语义与文本版一致。 */
fun DshWorkspaceClient.sendMessageParts(
    sessionId: String,
    parts: List<PromptPart>,
    clientRequestId: String,
    steer: Boolean = false,
): JsonElement {
    require(parts.isNotEmpty()) { "At least one content part is required." }
    val body = buildJsonObject {
        put("content", buildJsonArray { parts.forEach { add(it.toJson()) } })
        put("mode", if (steer) "steer" else "queue")
        put("clientRequestId", clientRequestId)
    }.toString()
    return executeJsonElement("chat/sessions/$sessionId/messages", "POST", body)
}

/** 上传一个文件，换取本次 prompt 可引用的 `receiptId`（24 MB 上限由服务端把关）。 */
fun DshWorkspaceClient.uploadSessionAttachment(
    sessionId: String,
    base64: String,
    name: String? = null,
): AttachmentReceipt {
    val body = json.encodeToString(AttachmentUpload.serializer(), AttachmentUpload(base64, name))
    return executeJson(
        endpoint("chat/sessions/$sessionId/attachments"),
        "POST",
        body,
        AttachmentReceipt.serializer(),
    )
}

/**
 * 取回一张会话图片的字节。
 *
 * `attachmentId` 形如 `sha256:<hex>`，冒号必须编码，否则服务端路由匹配不到。
 * 返回 `(bytes, mediaType)`；失败抛出，由调用方决定是提示还是留空。
 */
fun DshWorkspaceClient.readAttachment(sessionId: String, attachmentId: String): Pair<ByteArray, String> {
    val url = endpoint("attachments/${java.net.URLEncoder.encode(attachmentId, "UTF-8")}")
        .newBuilder()
        .addQueryParameter("sessionId", sessionId)
        .build()
    return executeWith(url) { get() }.use { response ->
        ensureSuccess(response)
        (response.body?.bytes() ?: ByteArray(0)) to (response.header("Content-Type") ?: "application/octet-stream")
    }
}

// ────────────────────────────────────────────────────────────────────────────
// 插件清单（`GET/PATCH settings/plugins`）
// ────────────────────────────────────────────────────────────────────────────

/** 一条插件记录。字段全是服务端裁剪过的，`available:false` 时整份清单不可信。 */
@Serializable
data class PluginEntry(
    val name: String,
    val declared: String? = null,
    val installed: String? = null,
    val loaded: Boolean = false,
    val official: Boolean = false,
    val state: String = "",
    val description: String? = null,
    val homepage: String? = null,
    val updateAvailable: Boolean? = null,
    val latestVersion: String? = null,
    val toggleable: Boolean = false,
)

@Serializable
data class PluginInventory(
    val profile: String? = null,
    val profilePath: String? = null,
    val available: Boolean = false,
    val reason: String? = null,
    val loadedCount: Int = 0,
    val problemCount: Int = 0,
    val items: List<PluginEntry> = emptyList(),
)

@Serializable
data class PluginToggleResult(val restartRequired: Boolean = false)

/** `checkUpdates=true` 会去 npm 查版本，慢；只在用户点「检查更新」时才传。 */
fun DshWorkspaceClient.listPlugins(checkUpdates: Boolean = false): PluginInventory {
    val url = endpoint("settings/plugins").newBuilder()
        .apply { if (checkUpdates) addQueryParameter("checkUpdates", "1") }
        .build()
    return executeJson(url, "GET", null, PluginInventory.serializer())
}

@Serializable
private data class PluginToggleRequest(val name: String, val loaded: Boolean)

/**
 * 启用/停用插件。**只改 `dsh.profile.bundles`，不装包、不删包**；
 * 且 bundle 只在 DSH 启动时读取一次，所以返回里带 `restartRequired`。
 */
fun DshWorkspaceClient.setPluginLoaded(name: String, loaded: Boolean): PluginToggleResult {
    val body = json.encodeToString(PluginToggleRequest.serializer(), PluginToggleRequest(name, loaded))
    return executeJson(endpoint("settings/plugins"), "PATCH", body, PluginToggleResult.serializer())
}

// ────────────────────────────────────────────────────────────────────────────
// 通用设置命名空间（`GET/PATCH settings/namespaces`，桥 v2.6.0）
//
// 协议事实（`dsh-remote-bridge/docs/api/openapi.yaml:582-643` + companion `691b5d5`）：
// - GET 需 scope `settings.read`；PATCH 需 `settings.write`，单次最多 32 条 op；
// - PATCH 成功返回**更新后的单个命名空间对象**（形状同 list 里一项），调用方
//   据此就地替换，不整表重拉；
// - 错误码以**代码字符串**判：409 `SETTINGS_CONFLICT`（早期需求口误成 412，
//   以 OpenAPI 为准）、403 `SETTINGS_NAMESPACE_READONLY`、400 `SETTINGS_OPS_INVALID`、
//   503 `SETTINGS_UNAVAILABLE`、403 `SCOPE_REQUIRED`。
// ────────────────────────────────────────────────────────────────────────────

/** 读命名空间列表。老桥没有这条路由时会抛 404，由 [isAbsentOnBridge] 判定降级。 */
fun DshWorkspaceClient.settingsNamespaces(): SettingsNamespaceList =
    executeJson(endpoint("settings/namespaces"), "GET", null, SettingsNamespaceList.serializer())

/**
 * 提交一个命名空间的修改（单次最多 32 条 op，服务端把关）。
 *
 * [expectedRevision] 传打开表单那一刻的 `revision`（乐观并发）：桥发现文档被别处
 * 改过会回 409 `SETTINGS_CONFLICT`，调用方应就地重拉而不是覆盖。
 */
fun DshWorkspaceClient.updateSettingsNamespace(
    ns: String,
    ops: List<SettingsOp>,
    expectedRevision: Int? = null,
): SettingsNamespace {
    val body = json.encodeToString(
        SettingsNamespaceUpdate.serializer(),
        SettingsNamespaceUpdate(ops, expectedRevision),
    )
    return executeJson(
        endpoint("settings/namespaces/$ns"),
        "PATCH",
        body,
        SettingsNamespace.serializer(),
    )
}

/**
 * 「桥没有这个端点/设备没有 scope」的判定：404 路由不存在，403 缺 scope。
 *
 * 刻意**不含** 503：服务端装了这条路由但暂时不可用，那是真的故障，该让用户知道。
 * 这条通道完全可选（老桥零变化），所以命中时界面只静默隐藏入口，不弹错误。
 */
fun WorkspaceApiException.isAbsentOnBridge(): Boolean =
    status == 404 || status == 403 || code in ABSENT_ON_BRIDGE_CODES

private val ABSENT_ON_BRIDGE_CODES = setOf("ROUTE_NOT_FOUND", "SCOPE_REQUIRED", "SCOPE_INVALID", "FORBIDDEN")
