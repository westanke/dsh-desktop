package io.github.westanke.dshdesktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.westanke.dshdesktop.data.AgentPreset
import io.github.westanke.dshdesktop.data.AppStorage
import io.github.westanke.dshdesktop.data.ChatItem
import io.github.westanke.dshdesktop.data.ChatSession
import io.github.westanke.dshdesktop.data.ChatWorkspace
import io.github.westanke.dshdesktop.data.CommandDescriptor
import io.github.westanke.dshdesktop.data.ConnectionProfile
import io.github.westanke.dshdesktop.data.DeviceView
import io.github.westanke.dshdesktop.data.DeliverableFile
import io.github.westanke.dshdesktop.data.DirectoryPage
import io.github.westanke.dshdesktop.data.DshWorkspaceClient
import io.github.westanke.dshdesktop.data.FileEntry
import io.github.westanke.dshdesktop.data.HistoryPage
import io.github.westanke.dshdesktop.data.JobView
import io.github.westanke.dshdesktop.data.ModelSelection
import io.github.westanke.dshdesktop.data.ModelCatalog
import io.github.westanke.dshdesktop.data.PendingApproval
import io.github.westanke.dshdesktop.data.PendingAttachment
import io.github.westanke.dshdesktop.data.PermissionCatalog
import io.github.westanke.dshdesktop.data.PermissionState
import io.github.westanke.dshdesktop.data.PluginInventory
import io.github.westanke.dshdesktop.data.ProviderModel
import io.github.westanke.dshdesktop.data.ProviderSettings
import io.github.westanke.dshdesktop.data.RootView
import io.github.westanke.dshdesktop.data.SessionModels
import io.github.westanke.dshdesktop.data.TodoItem
import io.github.westanke.dshdesktop.data.TraceTurn
import io.github.westanke.dshdesktop.data.TrashEntry
import io.github.westanke.dshdesktop.data.UiPrefs
import io.github.westanke.dshdesktop.data.WorkspaceApiException
import io.github.westanke.dshdesktop.data.WorkspaceEvent
import io.github.westanke.dshdesktop.data.WorkspaceEventListener
import io.github.westanke.dshdesktop.data.EventSubscription
import io.github.westanke.dshdesktop.data.listChatSessions
import io.github.westanke.dshdesktop.data.listMessagesPage
import io.github.westanke.dshdesktop.data.deliverableFiles
import io.github.westanke.dshdesktop.data.deliverablesIn
import io.github.westanke.dshdesktop.data.parseHistory
import io.github.westanke.dshdesktop.data.parseMaxSeq
import io.github.westanke.dshdesktop.data.parseHistoryOldestSeq
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID

/**
 * 全局状态。
 *
 * 分工刻意保持简单：REST 负责"现在的真相"（历史、列表、文件），
 * WebSocket 只负责"刚刚发生了什么"（增量正文、提交、审批）。重连后一律重拉 REST，
 * 因为桥接明确不提供无界重放 —— 这与它的 asyncapi 描述一致。
 */
class AppState(
    /**
     * 协程作用域与 [client] 对同包的扩展文件开放。
     *
     * 这不是随手放宽可见性：`AppState` 单文件已贴到 800 行预算（TECH-DEBT DEBT-003），
     * 按域把动作拆到 `AppStateChat.kt` / `AppStateFiles.kt` 是唯一拆法 ——
     * Kotlin 没有 partial class，而扩展函数必须用到这两样东西。
     */
    internal val scope: CoroutineScope,
) {

    var profile by mutableStateOf(AppStorage.loadProfile())
        private set
    var prefs by mutableStateOf(AppStorage.loadPrefs())
        private set
    var token by mutableStateOf(AppStorage.loadToken())
        private set

    var connection by mutableStateOf(if (token != null) ConnectionState.Connecting else ConnectionState.Disconnected)
        private set
    var connectionError by mutableStateOf<String?>(null)
        private set
    var device by mutableStateOf<DeviceView?>(null)

    /** 当前页。启动时按上次离开的页恢复（`prefs.lastNav`），换页由 `App` 回写。 */
    var nav by mutableStateOf(
        when (prefs.lastNav) {
            "files" -> Nav.Files
            "models" -> Nav.Models
            "plugins" -> Nav.Plugins
            "settings" -> Nav.Settings
            else -> Nav.Chat
        },
    )
    var sessions by mutableStateOf<List<ChatSession>>(emptyList())
    var sessionsError by mutableStateOf<String?>(null)
    var workspaces by mutableStateOf<List<ChatWorkspace>>(emptyList())
    var roots by mutableStateOf<List<RootView>>(emptyList())
    var trash by mutableStateOf<List<TrashEntry>>(emptyList())

    var selectedSessionId by mutableStateOf<String?>(prefs.lastSessionId)
    var chatItems by mutableStateOf<List<ChatItem>>(emptyList())
    var streamingText by mutableStateOf("")
    var streamingReasoning by mutableStateOf("")
    var running by mutableStateOf(false)
    var pendingApprovals by mutableStateOf(0)
    var chatError by mutableStateOf<String?>(null)
    var loadingHistory by mutableStateOf(false)

    /** 待审批条目本身（不只是计数）：桌面端要能代签，就必须先看见"批什么"。 */
    var approvals by mutableStateOf<List<PendingApproval>>(emptyList())
    var approvalsBusy by mutableStateOf(false)

    /** 这个会话可用的斜杠命令。切会话时重取，输入 `/` 时拿它做候选。 */
    var sessionCommands by mutableStateOf<List<CommandDescriptor>>(emptyList())

    /** 左栏是否在看「已归档」。归档是暗门：不切换就永远看不到自己归档过什么。 */
    var showArchived by mutableStateOf(false)
        internal set

    /** 历史还有更早的一页（服务端 `hasMore`）——决定顶部是否出现"加载更早"。 */
    var historyHasMore by mutableStateOf(false)
    var loadingOlder by mutableStateOf(false)
    private var oldestSeq: Int? = null

    /**
     * 标题下那两块悬挂面板（待办 / 轨迹）的展开与否。
     *
     * 它们以前是右侧抽屉的栏位，挪到标题下之后就不是"开一个面板"而是"展开一段"了
     * ——所以不复用 `chatPanel`：抽屉是"另起一列"，悬挂是"就地长高"。
     */
    var todosShelfOpen by mutableStateOf(false)
    var traceShelfOpen by mutableStateOf(false)

    /**
     * 这个会话历次端出来的**产出文件**，与消息流分开存。
     *
     * 为什么不能只靠消息流里的卡片：历史接口默认只给最近约 300 条，
     * 交付物事件常常落在更早的页里（真机：seq 580 的交付物在第 5 页）。
     * 于是"刚跑完有卡片、重新打开会话就没了"。这里由后台回溯补齐。
     */
    var sessionDeliverables by mutableStateOf<List<DeliverableFile>>(emptyList())
        internal set
    private var deliverableScan: Job? = null
    /** 后台为找卡片最多再往回翻几页。够覆盖 300×6 ≈ 1800 条，再长就该靠"加载更早"了。 */
    private val deliverableScanPages = 6

    var sessionModels by mutableStateOf<SessionModels?>(null)
    var modelError by mutableStateOf<String?>(null)

    // ── 会话投影（TODO / 权限 / 轨迹 / Agent Preset / 断档）────────────────
    // 这几样都不是独立端点：全从历史响应的 `projections` 与事件流里算出来，
    // 算法在 `data/SessionProjections.kt`，这里只存结果。

    var todoItems by mutableStateOf<List<TodoItem>>(emptyList())
        internal set
    var permissionState by mutableStateOf<PermissionState?>(null)
        internal set
    var permissionCatalog by mutableStateOf<PermissionCatalog?>(null)
        internal set
    var trace by mutableStateOf<List<TraceTurn>>(emptyList())
        internal set
    var agentPresets by mutableStateOf<List<AgentPreset>>(emptyList())
        internal set
    var agentPresetsError by mutableStateOf<String?>(null)

    /** 服务端自报的能力位（`connection.ready`）。空集 = 还没收到，按"未知"处理，不当作"不支持"。 */
    var capabilities by mutableStateOf<Set<String>>(emptySet())
        internal set
    var bridgeVersion by mutableStateOf<String?>(null)
        internal set

    /** 本地历史 seq 与订阅基线之间的缺口；>0 时界面提示"漏了一段"，可手动重取。 */
    var seqGap by mutableStateOf(0)
        internal set
    internal var lastSeenSeq: Int? = null
    internal var baselineCursor: Int? = null

    /** 上一份历史里的 `projections` 原文：权限目录晚到时要拿它重算一次档位。 */
    internal var lastProjections: JsonElement? = null

    /** 待发送附件（还没换成 wire 凭证，见 `PendingAttachment`）。 */
    var attachments by mutableStateOf<List<PendingAttachment>>(emptyList())

    /** 这个会话的后台任务（`jobs`）。只有真在看的时候才轮询，平时留空。 */
    var jobs by mutableStateOf<List<JobView>>(emptyList())
    var jobsBusy by mutableStateOf(false)
    var jobOutput by mutableStateOf<Pair<String, String>?>(null)

    /** 侧栏抽屉当前展示哪一栏；null = 收起。 */
    var chatPanel by mutableStateOf<ChatPanel?>(null)

    // 模型页（Provider / 凭据 / 自定义 provider）
    var providerSettings by mutableStateOf<ProviderSettings?>(null)
        internal set
    var providersBusy by mutableStateOf(false)
    var providersError by mutableStateOf<String?>(null)
    var discoveredModels by mutableStateOf<List<ProviderModel>?>(null)
    var modelCatalog by mutableStateOf<ModelCatalog?>(null)
        internal set

    // 插件页
    var plugins by mutableStateOf<PluginInventory?>(null)
        internal set
    var pluginsBusy by mutableStateOf(false)
    var pluginsError by mutableStateOf<String?>(null)
    var pluginsChecking by mutableStateOf(false)

    // 文件页
    var activeRootId by mutableStateOf<String?>(null)
    var currentPath by mutableStateOf("")
    var currentPage by mutableStateOf<DirectoryPage?>(null)
    var selectedEntry by mutableStateOf<FileEntry?>(null)
    var filePreview by mutableStateOf<String?>(null)
    var filePreviewTruncated by mutableStateOf(false)
    var filesError by mutableStateOf<String?>(null)
    var filesBusy by mutableStateOf(false)

    /** 预览时拿到的 ETag：保存要带它，服务端才敢确认"你改的就是我刚给的那份"。 */
    var filePreviewEtag by mutableStateOf<String?>(null)

    /** 编辑器里的草稿；null = 没在编辑（列表点开只是预览）。 */
    var fileDraft by mutableStateOf<String?>(null)

    /**
     * 预览的附加信息：类型判定（Markdown / 文本 / 图片 / 交给系统）、编码、图片字节。
     *
     * 收在一个字段里而不是散成四五个 `var`：散着写就会出现"文本换新文件了、
     * 图片还是上一张"的半截状态。整体替换最省心。
     */
    var previewExtra by mutableStateOf<io.github.westanke.dshdesktop.data.PreviewExtra?>(null)

    /** Markdown 预览时是否看源码（false = 渲染成文档）。 */
    var previewShowSource by mutableStateOf(false)

    var toast by mutableStateOf<String?>(null)

    private var subscription: EventSubscription? = null
    private var client: DshWorkspaceClient? = null

    val selectedSession: ChatSession?
        get() = sessions.firstOrNull { it.id == selectedSessionId }

    val isConfigured: Boolean get() = profile.endpoint.isNotBlank() && token != null

    fun savePrefs(transform: (UiPrefs) -> UiPrefs) {
        prefs = transform(prefs)
        AppStorage.savePrefs(prefs)
    }

    fun updateDeviceName(name: String) {
        if (name.isBlank()) return
        profile = profile.copy(deviceName = name)
        AppStorage.save(profile, token)
    }

    /** 窗口关闭：先断开 WebSocket，避免进程退出时留下半开连接。 */
    fun shutdown() {
        teardown()
    }

    fun showToast(message: String) {
        toast = message
    }

    fun dismissToast() {
        toast = null
    }

    // ── 连接 ────────────────────────────────────────────────────────────────

    /** 把用户输入的 `192.168.1.20:3090` / `http://host:3090/` 统一成 `http://host:3090/api/v1`。 */
    fun normalizeEndpoint(raw: String): String {
        var value = raw.trim()
        if (value.isEmpty()) return ""
        if (!value.startsWith("http://") && !value.startsWith("https://")) value = "http://$value"
        value = value.trimEnd('/')
        if (!value.endsWith("/api/v1")) value += "/api/v1"
        return value
    }

    fun connect(endpointRaw: String, pairingCode: String?) {
        val endpoint = normalizeEndpoint(endpointRaw)
        if (endpoint.isEmpty()) {
            connectionError = "请先填写服务器地址"
            return
        }
        connection = ConnectionState.Connecting
        connectionError = null
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val anonymous = DshWorkspaceClient(endpoint)
                    val health = anonymous.health()
                    val paired = if (!pairingCode.isNullOrBlank()) {
                        anonymous.exchangePairing(pairingCode.trim(), profile.deviceName)
                    } else null
                    Triple(health.pluginVersion ?: health.version, paired, endpoint)
                }
            }.onSuccess { (pluginVersion, paired, resolved) ->
                val effectiveToken = paired?.token ?: token
                if (effectiveToken == null) {
                    connection = ConnectionState.Failed
                    connectionError = "这台服务器还没有配对过，请填写一次性配对码"
                    return@onSuccess
                }
                profile = ConnectionProfile(
                    endpoint = resolved,
                    deviceName = profile.deviceName,
                    deviceId = paired?.device?.id ?: profile.deviceId,
                    scopes = paired?.device?.scopes ?: profile.scopes,
                    rootIds = paired?.device?.rootIds?.takeIf { it.isNotEmpty() } ?: profile.rootIds,
                    savedAt = System.currentTimeMillis(),
                )
                token = effectiveToken
                AppStorage.save(profile, effectiveToken)
                paired?.device?.let { adoptDevice(it) }
                connection = ConnectionState.Connected
                showToast("已连接 · 插件 $pluginVersion")
                bootstrap()
            }.onFailure { error ->
                connection = ConnectionState.Failed
                connectionError = human(error)
            }
        }
    }

    /** 已保存凭据时静默恢复会话。 */
    fun autoConnect() {
        if (!isConfigured) return
        connection = ConnectionState.Connecting
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val api = client()
                    api.health()
                    api.currentDevice()
                }
            }.onSuccess { current ->
                adoptDevice(current)
                connection = ConnectionState.Connected
                bootstrap()
            }.onFailure { error ->
                connection = ConnectionState.Failed
                connectionError = human(error)
            }
        }
    }

    fun disconnect(clearToken: Boolean) {
        teardown()
        if (clearToken) {
            AppStorage.clearToken()
            token = null
            profile = ConnectionProfile(deviceName = profile.deviceName)
            AppStorage.save(profile, null)
            sessions = emptyList()
            roots = emptyList()
            selectedSessionId = null
            chatItems = emptyList()
        }
        connection = ConnectionState.Disconnected
    }

    private fun teardown() {
        runCatching { subscription?.close() }
        subscription = null
        client = null
    }

    internal fun client(): DshWorkspaceClient {
        client?.let { return it }
        val api = DshWorkspaceClient(requireNotNull(profile.endpoint) { "未配置服务器地址" }, token)
        client = api
        return api
    }

    /**
     * 把服务端给的设备视图落到状态与本地配置。
     *
     * 配对响应里的 `device` 只带 id/name/scopes，**不带 rootIds**；早先直接拿它覆盖 profile，
     * 结果 `profile.rootIds` 存下来永远是空数组（真机上 `[]`），设置页看着像没授权。
     * 这里改成"有空就补、不拿空值覆盖已有的"。
     */
    private fun adoptDevice(view: DeviceView) {
        device = view
        val merged = profile.copy(
            deviceId = view.id.ifBlank { profile.deviceId },
            scopes = view.scopes.ifEmpty { profile.scopes },
            rootIds = view.rootIds.ifEmpty { profile.rootIds },
        )
        if (merged != profile) {
            profile = merged
            AppStorage.save(profile, token)
        }
    }

    private fun subscribe() {
        if (subscription != null) return
        val api = client()
        subscription = api.events(object : WorkspaceEventListener {
            override fun onEvent(event: WorkspaceEvent) {
                scope.launch { handleEvent(event) }
            }

            override fun onConnectionChanged(connected: Boolean) {
                if (!connected && connection == ConnectionState.Connected) {
                    connection = ConnectionState.Connecting
                } else if (connected && connection != ConnectionState.Connected) {
                    connection = ConnectionState.Connected
                    // 桥接不做无界重放，重连后必须自己重拉。
                    refreshSessions()
                    selectedSessionId?.let { loadHistory(it) }
                }
            }

            override fun onPermanentFailure(error: Throwable) {
                connection = ConnectionState.Failed
                connectionError = "设备令牌已失效，请重新配对"
            }
        })
    }

    fun bootstrap() {
        refreshSessions()
        refreshRoots()
        refreshDevice()
        subscribe()
    }

    /** 冷启动时把设备的授权根补齐（配对响应不带 rootIds，只能单独问）。 */
    private fun refreshDevice() {
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { client().currentDevice() } }
                .onSuccess { adoptDevice(it) }
        }
    }

    // ── 会话 ────────────────────────────────────────────────────────────────

    fun refreshSessions() {
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { client().listChatSessions(showArchived) } }
                .onSuccess { list ->
                    sessionsError = null
                    sessions = list
                    // 上次选中的会话可能已被归档/删除
                    val current = selectedSessionId
                    if (current != null && list.none { it.id == current }) selectedSessionId = null
                    when {
                        selectedSessionId == null -> list.firstOrNull()?.let { selectSession(it.id) }
                        // 冷启动时 lastSessionId 是从偏好里恢复的，此时正文与模型目录都还没拉过；
                        // 只恢复"选中态"而不拉数据，界面会是一片空白（踩过）。
                        chatItems.isEmpty() -> {
                            selectedSessionId?.let { id ->
                                loadHistory(id)
                                loadModels(id)
                                loadApprovals(id)
                                loadCommands(id)
                            }
                        }
                    }
                }
                // 以前这里是空的 onFailure。结果是：接口一失败，左栏就空着，
                // 用户看到"会话打开无任何显示"却拿不到任何线索（真机 2026-10-21 报障）。
                // 列表失败必须说话——它是"整页空白"最常见的那条路径。
                .onFailure { sessionsError = human(it) }
        }
    }

    /**
     * 在「活跃会话」与「已归档会话」之间切换。
     *
     * 归档后默认列表就看不到它了，如果客户端不给这个开关，用户会以为会话被删了。
     */
    fun showArchivedSessions(archived: Boolean) {
        if (showArchived == archived) return
        showArchived = archived
        selectedSessionId = null
        chatItems = emptyList()
        historyHasMore = false
        oldestSeq = null
        deliverableScan?.cancel()
        sessionDeliverables = emptyList()
        approvals = emptyList()
        pendingApprovals = 0
        sessionCommands = emptyList()
        refreshSessions()
    }

    fun selectSession(sessionId: String) {
        if (selectedSessionId == sessionId && chatItems.isNotEmpty()) return
        selectedSessionId = sessionId
        savePrefs { it.copy(lastSessionId = sessionId) }
        chatItems = emptyList()
        streamingText = ""
        streamingReasoning = ""
        approvals = emptyList()
        pendingApprovals = 0
        sessionCommands = emptyList()
        // 投影是按会话算的，换会话必须清干净：留着上一条的待办比空着更误导
        clearProjections()
        attachments = emptyList()
        jobs = emptyList()
        jobOutput = null
        chatPanel = null
        deliverableScan?.cancel()
        sessionDeliverables = emptyList()
        loadHistory(sessionId)
        loadModels(sessionId)
        loadApprovals(sessionId)
        loadCommands(sessionId)
    }

    fun loadHistory(sessionId: String) {
        loadingHistory = true
        chatError = null
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val element = client().listMessagesPage(sessionId)
                    val root = element.jsonObject
                    val events = (root["events"] as? JsonArray)?.toList().orEmpty()
                    HistoryPage(
                        items = parseHistory(events),
                        hasMore = (root["hasMore"] as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull() == true,
                        oldest = parseHistoryOldestSeq(events),
                        projections = root["projections"],
                        maxSeq = parseMaxSeq(events),
                        rawEvents = events,
                    )
                }
            }.onSuccess { page ->
                if (selectedSessionId == sessionId) {
                    chatItems = page.items
                    historyHasMore = page.hasMore
                    oldestSeq = page.oldest
                    sessionDeliverables = deliverablesIn(page.rawEvents)
                    applyProjections(sessionId, page)
                    // 首页只是"最近一段"，早期交付物不在这儿；后台往回翻几页把它们捞回来。
                    if (page.hasMore && page.oldest != null) scanDeliverables(sessionId, page.oldest!!)
                }
            }.onFailure {
                if (selectedSessionId == sessionId) chatError = human(it)
            }
            loadingHistory = false
        }
    }

    /** 往回翻一页更早的历史（服务端 `hasMore` 为真时才调用）。 */
    fun loadOlderHistory() {
        val sessionId = selectedSessionId ?: return
        val before = oldestSeq ?: return
        if (loadingOlder || !historyHasMore) return
        loadingOlder = true
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val element = client().listMessagesPage(sessionId, beforeSeq = before)
                    val root = element.jsonObject
                    val events = (root["events"] as? JsonArray)?.toList().orEmpty()
                    HistoryPage(
                        items = parseHistory(events),
                        hasMore = (root["hasMore"] as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull() == true,
                        oldest = parseHistoryOldestSeq(events),
                        projections = root["projections"],
                        maxSeq = parseMaxSeq(events),
                        rawEvents = events,
                    )
                }
            }.onSuccess { page ->
                if (selectedSessionId == sessionId) {
                    // 更早的一页拼在前面；用 id 去重，避免服务端把边界那条又给一次。
                    val existing = chatItems.map { it.id }.toHashSet()
                    chatItems = page.items.filterNot { it.id in existing } + chatItems
                    historyHasMore = page.hasMore
                    oldestSeq = page.oldest ?: before
                }
            }.onFailure { showToast(human(it)) }
            loadingOlder = false
        }
    }

    /**
     * 会话级产出里、**当前消息流没画到的**那些。
     *
     * 首页里就有交付物事件时，消息流自己会画卡片，这里再画一遍就是两张一样的卡。
     * 只有落在更早页（要回溯才捞得到）的部分才需要这块汇总区补上。
     */
    fun deliverablesOutsideStream(): List<DeliverableFile> {
        val inStream = chatItems
            .filterIsInstance<ChatItem.Deliverable>()
            .flatMap { it.files }
            .mapTo(HashSet()) { it.path }
        return sessionDeliverables.filterNot { it.path in inStream }
    }

    /**
     * 往回翻更早的历史，只为了把**产出文件**捞齐。
     *
     * 不动 `chatItems`，也不动 `oldestSeq`——它和"加载更早"是两回事：
     * 那一个是用户主动翻页，这一个是为了让卡片在打开会话时就出现。
     * 翻页失败静默收场：卡片少几张可以忍，弹一堆错误不行。
     */
    private fun scanDeliverables(sessionId: String, fromSeq: Int) {
        deliverableScan?.cancel()
        deliverableScan = scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val found = ArrayList(sessionDeliverables)
                    var before: Int? = fromSeq
                    var pages = 0
                    while (before != null && pages < deliverableScanPages) {
                        val root = client().listMessagesPage(sessionId, beforeSeq = before).jsonObject
                        val events = (root["events"] as? JsonArray)?.toList().orEmpty()
                        found += deliverablesIn(events)
                        pages++
                        val more = (root["hasMore"] as? JsonPrimitive)?.contentOrNull
                            ?.toBooleanStrictOrNull() == true
                        before = if (more) parseHistoryOldestSeq(events) else null
                    }
                    found
                }
            }.onSuccess { found ->
                if (selectedSessionId == sessionId) {
                    sessionDeliverables = found.distinctBy { it.path }
                }
            }
        }
    }

    fun loadModels(sessionId: String) {
        modelError = null
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { client().listSessionModels(sessionId) } }
                .onSuccess { if (selectedSessionId == sessionId) sessionModels = it }
                .onFailure { modelError = human(it) }
        }
    }

    fun loadApprovals(sessionId: String) {
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { client().listPendingApprovals(sessionId) } }
                .onSuccess { list ->
                    if (selectedSessionId != sessionId) return@onSuccess
                    approvals = list
                    pendingApprovals = list.size
                }
                .onFailure { }
        }
    }

    fun selectModel(selection: ModelSelection) {
        val sessionId = selectedSessionId ?: return
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { client().selectSessionModel(sessionId, selection) } }
                .onSuccess { selected -> sessionModels = sessionModels?.copy(current = selected) }
                .onFailure { showToast(human(it)) }
        }
    }

    /**
     * 发一条消息。
     *
     * [steer] 为真时用 `mode: steer`：插到**正在跑的这一轮**里，而不是排在后面等它跑完。
     * 桌面用户看得见执行过程，发现方向不对时想立刻纠偏，排队等一轮结束没有意义。
     */
    fun sendMessage(text: String, steer: Boolean = false) {
        val sessionId = selectedSessionId ?: return
        if (text.isBlank()) return
        chatError = null
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    client().sendMessage(sessionId, text, UUID.randomUUID().toString(), steer)
                }
            }.onSuccess {
                if (!steer) running = true
                streamingText = ""
                streamingReasoning = ""
                // 立刻把用户这句放到界面上，不等 committed 事件回来
                chatItems = chatItems + ChatItem.User(
                    id = "local-${UUID.randomUUID()}",
                    seq = Int.MAX_VALUE,
                    text = text,
                    time = System.currentTimeMillis(),
                )
            }.onFailure { chatError = human(it) }
        }
    }

    fun cancelRun() {
        val sessionId = selectedSessionId ?: return
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { client().cancel(sessionId) } }
                .onFailure { showToast(human(it)) }
        }
    }

    fun decideApproval(approvalId: String, allow: Boolean) {
        val sessionId = selectedSessionId ?: return
        if (approvalsBusy) return
        approvalsBusy = true
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) { client().decideApproval(sessionId, approvalId, allow) }
            }.onSuccess {
                showToast(if (allow) "已允许一次" else "已拒绝")
                // 先本地摘掉，列表不会在等 REST 回来的这段时间里还显示旧条目
                approvals = approvals.filterNot { it.id == approvalId }
                pendingApprovals = approvals.size
                loadApprovals(sessionId)
                loadHistory(sessionId)
            }.onFailure { showToast(human(it)) }
            approvalsBusy = false
        }
    }

    fun createSession(workspaceId: String) {
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    client().createSessionInWorkspace(workspaceId, UUID.randomUUID().toString())
                }
            }.onSuccess {
                refreshSessions()
                showToast("已新建会话")
            }.onFailure { showToast(human(it)) }
        }
    }

    fun renameSession(sessionId: String, title: String) {
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { client().renameSession(sessionId, title) } }
                .onSuccess { refreshSessions() }
                .onFailure { showToast(human(it)) }
        }
    }

    fun archiveSession(sessionId: String) {
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { client().archiveSession(sessionId) } }
                .onSuccess {
                    if (selectedSessionId == sessionId) selectedSessionId = null
                    refreshSessions()
                    showToast("会话已归档")
                }
                .onFailure { showToast(human(it)) }
        }
    }

    fun refreshWorkspaces() {
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { client().listChatWorkspaces() } }
                .onSuccess { workspaces = it }
                .onFailure { }
        }
    }

    // ── 事件 ────────────────────────────────────────────────────────────────

    private fun handleEvent(event: WorkspaceEvent) {
        val data = event.data as? JsonObject ?: return
        when (event.type) {
            "connection.ready" -> {
                connection = ConnectionState.Connected
                adoptCapabilities(data)
            }

            // 订阅基线：服务端告诉我们"从这条 seq 往后你都会收到"。
            // 本地历史落后于人，中间那段谁都没管 —— 这正是要主动补的缺口。
            "chat.session.snapshot" -> {
                val sessionId = data.str("sessionId") ?: return
                if (sessionId != selectedSessionId) {
                    refreshSessions()
                    return
                }
                val cursor = (data["cursor"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull()
                if (noteBaseline(cursor)) loadHistory(sessionId)
            }

            "chat.message.delta" -> {
                val sessionId = data.str("sessionId") ?: return
                if (sessionId != selectedSessionId) return
                val kind = data.str("kind") ?: "text"
                val text = data.str("text").orEmpty()
                if (kind == "reasoning") streamingReasoning += text else streamingText += text
                running = true
            }

            "chat.message.committed" -> {
                val sessionId = data.str("sessionId") ?: return
                if (sessionId != selectedSessionId) {
                    refreshSessions()
                    return
                }
                // 提交意味着 REST 历史已经有这条正文，流式缓冲可以清空
                streamingText = ""
                streamingReasoning = ""
                loadHistory(sessionId)
                refreshSessions()
            }

            "chat.session.status" -> {
                val sessionId = data.str("sessionId") ?: return
                val status = data.str("status")
                if (sessionId == selectedSessionId) {
                    running = status == "running"
                    if (status != "running") {
                        streamingText = ""
                        streamingReasoning = ""
                        loadHistory(sessionId)
                    }
                }
            }

            "chat.turn.failed" -> {
                val sessionId = data.str("sessionId") ?: return
                running = false
                val failure = data["failure"] as? JsonObject
                val message = failure?.str("message") ?: "本轮执行失败"
                if (sessionId == selectedSessionId) {
                    chatError = message
                    loadHistory(sessionId)
                }
            }

            "chat.approval.requested" -> {
                val sessionId = data.str("sessionId") ?: return
                if (sessionId == selectedSessionId) loadApprovals(sessionId)
            }

            "chat.approval.resolved" -> {
                val sessionId = data.str("sessionId") ?: return
                if (sessionId == selectedSessionId) loadApprovals(sessionId)
            }

            // 交付物。实时流里叫 `chat.deliverables.presented`（桥接规范化时加了前缀），
            // 历史里叫 `deliverables/presented` —— 两个名字都得认，漏一个就是
            // "新交付物要等下一次刷新才出现"。
            "chat.deliverables.presented", "deliverables/presented" -> {
                val sessionId = data.str("sessionId") ?: selectedSessionId ?: return
                val files = deliverableFiles(data)
                if (files.isEmpty()) return
                if (sessionId == selectedSessionId) {
                    sessionDeliverables = (files + sessionDeliverables).distinctBy { it.path }
                }
            }

            "file.changed", "file.trashed" -> {
                if (nav == Nav.Files) refreshDirectory()
            }
        }
    }

    companion object {
        /** 把异常翻成人话；服务端给了 code 就保留，便于对症。 */
        fun human(error: Throwable): String = when (error) {
            is WorkspaceApiException -> when (error.code) {
                "UNAUTHORIZED", "TOKEN_INVALID" -> "设备令牌无效或已撤销，请重新配对"
                "FORBIDDEN", "SCOPE_MISSING" -> "当前设备没有这项权限（${error.code}）"
                "PATH_INVALID" -> "路径不合法，服务器拒绝了这次操作"
                "CONFLICT" -> "文件已被改动，请刷新后重试"
                "NOT_FOUND" -> "对象不存在，可能已被删除"
                "RATE_LIMITED" -> "请求太频繁，稍后再试"
                else -> "${error.message}（${error.code}）"
            }
            is java.net.ConnectException -> "连不上服务器，检查地址、端口和防火墙"
            is java.net.SocketTimeoutException -> "连接超时，服务器没有响应"
            is java.net.UnknownHostException -> "解析不了这个主机名"
            is javax.net.ssl.SSLException -> "HTTPS 握手失败：${error.message}"
            else -> error.message ?: error::class.simpleName ?: "未知错误"
        }
    }
}

private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
