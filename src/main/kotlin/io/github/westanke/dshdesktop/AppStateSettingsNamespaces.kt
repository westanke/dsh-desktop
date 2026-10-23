package io.github.westanke.dshdesktop

import io.github.westanke.dshdesktop.data.SettingsNamespaceList
import io.github.westanke.dshdesktop.data.SettingsOp
import io.github.westanke.dshdesktop.data.WorkspaceApiException
import io.github.westanke.dshdesktop.data.canWriteSettingsNamespaces
import io.github.westanke.dshdesktop.data.isAbsentOnBridge
import io.github.westanke.dshdesktop.data.settingsNamespaces
import io.github.westanke.dshdesktop.data.updateSettingsNamespace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/*
 * ============================================================================
 * 通用设置命名空间（P2-1）：读取与提交，三层降级
 * ============================================================================
 *
 * 对标 companion `HarnessNamespacesViewModel.kt`（152 行）的同名三段式。
 * 这条通道是**先有对端、后有此页**：老桥（2.5.x 及更早）根本没有
 * `GET /settings/namespaces`。若照常抛异常，用户每次进设置页都会被吼一句
 * 「HTTP 404」——而那既不是他做错了什么，也不是他修得了的。
 *
 * 1. 数据层 [loadSettingsNamespaces]：任何失败都退化成 null（入口不画），
 *    读失败不能拖垮设置页其余部分；
 * 2. 状态层：`settingsNamespaces == null` 就不画入口（老桥零变化、零打扰）；
 * 3. 用户主动刷新 [refreshSettingsNamespaces]：404/403 静默隐藏，
 *    503 等真故障如实报错。
 */

/** 设备自己的 scope 集合（`devices/self` 补齐后的 profile 是合并值）。 */
private fun AppState.deviceScopes(): List<String> = device?.scopes ?: profile.scopes

/** 读侧门槛：没有 `settings.read` 时**直接不发请求**（发了也必然 403）。 */
private fun AppState.canReadSettingsNamespaces(): Boolean = "settings.read" in deviceScopes()

/**
 * 软加载命名空间列表（进设置页时调，失败不打扰用户）。
 *
 * 与插件/供应商那几页不同，这条通道**完全可选**：它挂了不影响聊天、文件、会话。
 * 若让它把错误抛到设置页，一个「设置表单没数据」会升级成「设置页一片红」，
 * 代价完全不对等。所以就地兜住，只区分一件事——
 * **503 是装了路由但服务不可用**，那是真故障，要说出来；其余（老桥 404、
 * 网络抖动）都按"这条通道不存在"处理，静默留空。
 */
fun AppState.loadSettingsNamespaces() {
    if (settingsNamespacesBusy) return
    if (!canReadSettingsNamespaces()) return
    settingsNamespacesBusy = true
    scope.launch {
        runCatching { withContext(Dispatchers.IO) { client().settingsNamespaces() } }
            .onSuccess { settingsNamespaces = it }
            .onFailure { error ->
                val unavailable = error is WorkspaceApiException && error.code == "SETTINGS_UNAVAILABLE"
                if (unavailable) showToast("桥的设置服务暂时不可用（503），稍后再试")
                // 其余失败：留着 null，入口不画。老桥与断网都不该在每次进设置页时弹提示。
            }
        settingsNamespacesBusy = false
    }
}

/**
 * 用户主动刷新（设置页里的刷新按钮）。
 *
 * 404 / 403 / 缺 scope → 静默隐藏入口 + 一句人话提示（这是**正常降级**，
 * 不是故障）；其余错误（含 503）如实报错，并保留旧数据不清空——
 * 网络抖动不该把已经能看的表单擦掉。
 */
fun AppState.refreshSettingsNamespaces() {
    if (settingsNamespacesBusy) return
    if (!canReadSettingsNamespaces()) {
        showToast("设备没有 settings.read 授权，读不到通用设置")
        return
    }
    settingsNamespacesBusy = true
    scope.launch {
        runCatching { withContext(Dispatchers.IO) { client().settingsNamespaces() } }
            .onSuccess { settingsNamespaces = it }
            .onFailure { error ->
                if (error is WorkspaceApiException && error.isAbsentOnBridge()) {
                    settingsNamespaces = null
                    showToast("当前桥不支持通用设置命名空间（老桥或未授权）")
                } else {
                    showToast(AppState.human(error))
                }
            }
        settingsNamespacesBusy = false
    }
}

/**
 * 提交一个命名空间的修改。
 *
 * 三件事照搬 companion：
 * - **没有 `settings.write` 就不发请求**：配对默认五档里没有它，
 *   「能看不能存」是常态不是故障，提前说清楚比让服务端 403 回来友好；
 * - **409 `SETTINGS_CONFLICT`**：文档被别处改过。不把错误码原文丢给用户，
 *   就地重拉最新值 + 提示「已在别处被修改，已刷新」——官方 `conflict` 的文案
 *   就是这个意思。绝不自动重试覆盖（那等于替用户做"我的更对"的决定）；
 * - **成功用 PATCH 返回的单对象替换列表里同 ns 那一项**，不整表重拉：
 *   既慢又会让用户当前展开的表单闪一下。
 */
fun AppState.saveSettingsNamespace(ns: String, ops: List<SettingsOp>) {
    if (ops.isEmpty()) return
    val current = settingsNamespaces?.namespaces?.firstOrNull { it.ns == ns } ?: return
    if (!deviceScopes().canWriteSettingsNamespaces()) {
        showToast("设备没有 settings.write 授权，只能查看、不能保存")
        return
    }
    settingsNamespacesBusy = true
    scope.launch {
        runCatching {
            withContext(Dispatchers.IO) {
                client().updateSettingsNamespace(
                    ns = ns,
                    ops = ops,
                    expectedRevision = current.revision,
                )
            }
        }.onSuccess { updated ->
            val list = settingsNamespaces
            settingsNamespaces = if (list == null) {
                SettingsNamespaceList(writable = true, namespaces = listOf(updated))
            } else {
                list.copy(namespaces = list.namespaces.map { if (it.ns == updated.ns) updated else it })
            }
            showToast("已保存")
        }.onFailure { error ->
            when {
                error is WorkspaceApiException && error.code == "SETTINGS_CONFLICT" -> {
                    val refreshed = runCatching {
                        withContext(Dispatchers.IO) { client().settingsNamespaces() }
                    }.getOrNull()
                    if (refreshed != null) settingsNamespaces = refreshed
                    showToast("设置已在别处被修改，已刷新")
                }
                error is WorkspaceApiException && error.code == "SETTINGS_NAMESPACE_READONLY" ->
                    showToast("这个命名空间是只读的：${error.message}")
                error is WorkspaceApiException && error.code == "SETTINGS_OPS_INVALID" ->
                    showToast("有字段不合法，未保存：${error.message}")
                else -> showToast(AppState.human(error))
            }
        }
        settingsNamespacesBusy = false
    }
}
