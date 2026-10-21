package io.github.westanke.dshdesktop

import io.github.westanke.dshdesktop.data.CustomProviderCreate
import io.github.westanke.dshdesktop.data.ProviderModel
import io.github.westanke.dshdesktop.data.ProviderPatch
import io.github.westanke.dshdesktop.data.listPlugins
import io.github.westanke.dshdesktop.data.setPluginLoaded
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 设置域：Provider / 凭据 / 模型目录，以及插件清单与启停。
 *
 * 与安卓端「模型」页对齐。两条纪律：
 * 1. **写操作带 `expectedRevision`**。服务端用它做乐观并发控制，不带就等于"不管别人改过没"，
 *    会把另一台设备刚保存的 baseURL 静默盖掉。
 * 2. **密钥不回显**。服务端只给 `configured: true/false` 与来源，界面只能"重设"，不能"查看"。
 */

// ── Provider ────────────────────────────────────────────────────────────────

fun AppState.loadProviders() {
    if (providersBusy) return
    providersBusy = true
    providersError = null
    scope.launch {
        runCatching {
            withContext(Dispatchers.IO) {
                val settings = client().listProviderSettings()
                // 模型目录是锦上添花：拿不到不该把整页判成失败
                val catalog = runCatching { client().listModelCatalog() }.getOrNull()
                settings to catalog
            }
        }.onSuccess { (settings, catalog) ->
            providerSettings = settings
            modelCatalog = catalog
        }.onFailure { providersError = AppState.human(it) }
        providersBusy = false
    }
}

/**
 * 保存一个 provider。
 *
 * [expectedRevision] 传打开卡片那一刻的 `revisionByNamespace[settingsNs]`。
 * 服务端回 409 时**不能**自动重试覆盖 —— 那意味着别处改过了，
 * 直接重试等于替用户做"我的更对"的决定。这里只如实提示刷新。
 */
fun AppState.saveProvider(
    providerId: String,
    patch: ProviderPatch,
    settingsNs: String? = null,
) {
    val revision = settingsNs
        ?.let { ns -> providerSettings?.revisionByNamespace?.get(ns) }
        ?: providerSettings?.revisionByNamespace?.values?.firstOrNull()
    providersBusy = true
    scope.launch {
        runCatching {
            withContext(Dispatchers.IO) { client().updateProvider(providerId, patch.copy(expectedRevision = revision)) }
        }.onSuccess {
            providerSettings = it
            discoveredModels = null
            showToast("已保存")
        }.onFailure { providersError = AppState.human(it) }
        providersBusy = false
    }
}

fun AppState.resetProviderModels(providerId: String) {
    val revision = providerSettings?.revisionByNamespace?.values?.firstOrNull()
    providersBusy = true
    scope.launch {
        runCatching {
            withContext(Dispatchers.IO) { client().resetProviderModels(providerId, revision) }
        }.onSuccess {
            providerSettings = it
            showToast("模型目录已恢复为适配器默认")
        }.onFailure { providersError = AppState.human(it) }
        providersBusy = false
    }
}

fun AppState.deleteProvider(providerId: String) {
    val revision = providerSettings?.revisionByNamespace?.values?.firstOrNull()
    providersBusy = true
    scope.launch {
        runCatching { withContext(Dispatchers.IO) { client().deleteProvider(providerId, revision) } }
            .onSuccess {
                showToast("已删除自定义供应商")
                loadProviders()
            }
            .onFailure { providersError = AppState.human(it) }
        providersBusy = false
    }
}

/** 新建自定义 provider。目录必须至少一个模型，否则服务端会拒。 */
fun AppState.createProvider(
    id: String,
    displayName: String?,
    baseURL: String,
    api: String,
    apiKey: String?,
    models: List<ProviderModel>,
) {
    if (id.isBlank() || baseURL.isBlank() || api.isBlank()) {
        showToast("id、baseURL、api 协议都是必填")
        return
    }
    providersBusy = true
    scope.launch {
        runCatching {
            withContext(Dispatchers.IO) {
                client().createCustomProvider(
                    CustomProviderCreate(
                        id = id.trim(),
                        displayName = displayName?.takeIf { it.isNotBlank() },
                        baseURL = baseURL.trim(),
                        api = api.trim(),
                        apiKey = apiKey?.takeIf { it.isNotBlank() },
                        models = models,
                        expectedRevision = providerSettings?.customProvider?.revision,
                    ),
                )
            }
        }.onSuccess {
            providerSettings = it
            discoveredModels = null
            showToast("已创建 $id")
        }.onFailure { providersError = AppState.human(it) }
        providersBusy = false
    }
}

/**
 * 从供应商拉一次可用模型（`/discover`）。
 *
 * 结果只是**候选**：勾哪几个落进 `models` 由用户决定，
 * 所以这里不直接写库，只填 [AppState.discoveredModels]。
 */
fun AppState.discoverModels(
    providerId: String,
    baseURL: String? = null,
    api: String? = null,
    apiKey: String? = null,
) {
    providersBusy = true
    scope.launch {
        runCatching {
            withContext(Dispatchers.IO) { client().discoverProviderModels(providerId, baseURL, api, apiKey) }
        }.onSuccess {
            discoveredModels = it
            showToast(if (it.isEmpty()) "没有发现模型（检查地址与密钥）" else "发现 ${it.size} 个模型")
        }.onFailure { providersError = AppState.human(it) }
        providersBusy = false
    }
}

fun AppState.dismissDiscovered() {
    discoveredModels = null
}

// ── 插件 ────────────────────────────────────────────────────────────────────

/**
 * 取插件清单。
 *
 * [checkUpdates] 会去 npm 逐个查版本，慢且吃网络 —— 只有用户点「检查更新」时才传 true。
 * 平时打开页面用 false，先看见"装了哪些、启没启"。
 */
fun AppState.loadPlugins(checkUpdates: Boolean = false) {
    if (pluginsBusy && !checkUpdates) return
    pluginsChecking = checkUpdates
    pluginsBusy = true
    pluginsError = null
    scope.launch {
        runCatching { withContext(Dispatchers.IO) { client().listPlugins(checkUpdates) } }
            .onSuccess { plugins = it }
            .onFailure { pluginsError = AppState.human(it) }
        pluginsBusy = false
        pluginsChecking = false
    }
}

/**
 * 启用/停用插件。
 *
 * 只改 `dsh.profile.bundles`，**不装包也不删包**；bundle 只在 DSH 启动时读一次，
 * 所以返回的 `restartRequired` 必须如实转告 —— 不说的话用户会以为开关坏了。
 */
fun AppState.togglePlugin(name: String, loaded: Boolean) {
    scope.launch {
        runCatching {
            withContext(Dispatchers.IO) { client().setPluginLoaded(name, loaded) }
        }.onSuccess { result ->
            showToast(
                if (result.restartRequired) "已${if (loaded) "启用" else "停用"} $name，重启 DSH 后生效"
                else "已${if (loaded) "启用" else "停用"} $name",
            )
            loadPlugins()
        }.onFailure { showToast(AppState.human(it)) }
    }
}
