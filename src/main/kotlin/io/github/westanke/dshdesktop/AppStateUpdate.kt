package io.github.westanke.dshdesktop

import io.github.westanke.dshdesktop.data.AppStorage
import io.github.westanke.dshdesktop.data.ReleaseAsset
import io.github.westanke.dshdesktop.data.ReleaseInfo
import io.github.westanke.dshdesktop.data.UpdateChannel
import io.github.westanke.dshdesktop.ui.openExternal
import io.github.westanke.dshdesktop.data.UpdateChecker
import io.github.westanke.dshdesktop.data.UpdateException
import io.github.westanke.dshdesktop.data.UpdatePlatform
import io.github.westanke.dshdesktop.data.channelForLanguage
import io.github.westanke.dshdesktop.data.detectArch
import io.github.westanke.dshdesktop.data.detectPlatform
import io.github.westanke.dshdesktop.data.isNewerVersion
import io.github.westanke.dshdesktop.data.parseSha256Sums
import io.github.westanke.dshdesktop.data.pickUpdateAsset
import io.github.westanke.dshdesktop.data.runningLiteInstall
import io.github.westanke.dshdesktop.data.sha256OfFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/**
 * 检查更新的状态机（P2-2，从 `AppState.kt` 按域拆出）。
 *
 * 边界：**只检查、下载、校验、给指引**。不热自更新、不自动提权——
 * 替换正在运行的程序必须重启，静默 `pkexec` 装 deb 在 UOS 上只会弹一个看不懂的授权框。
 * 手动触发为主（首版不做启动自动查，避免国内 Gitee API 频控和打扰）。
 */
sealed interface UpdateState {

    /** 没查过。 */
    data object Idle : UpdateState

    /** 正在查。 */
    data object Checking : UpdateState

    /** 已是最新。 */
    data class UpToDate(val current: String, val channel: UpdateChannel) : UpdateState

    /** 发现新版，等用户决定下不下载。 */
    data class Available(
        val info: ReleaseInfo,
        val asset: ReleaseAsset,
        val channel: UpdateChannel,
        val current: String,
    ) : UpdateState

    /**
     * 下载中。[total] 为 null = 渠道没给 Content-Length，界面显示"大小未知"而不是假 0%。
     * [offer] 留着是为了取消下载后能原样退回"有新版"那一态。
     */
    data class Downloading(
        val received: Long,
        val total: Long?,
        val file: File,
        val offer: Available,
    ) : UpdateState

    /** 下载完且哈希校验通过，等用户点"安装"。 */
    data class Ready(val file: File, val asset: ReleaseAsset, val info: ReleaseInfo) : UpdateState

    /** 失败。[hint] 是给用户的下一步（比如"去 Release 页手动下载"）。 */
    data class Failed(
        val message: String,
        val hint: String? = null,
        val channel: UpdateChannel? = null,
    ) : UpdateState
}

/** 存放待安装包的目录：配置目录下的 `updates/`，跨平台都落在用户主目录里。 */
fun AppState.updatesDir(): File = AppStorage.root().resolve("updates").toFile()

/**
 * 查最新版。
 *
 * [currentVersion] 由界面传进来（设置页的版本号读法：jar manifest 优先、
 * `VERSION_FALLBACK` 兜底）——更新检查不自己再读一遍 manifest，免得两处口径漂了。
 */
fun AppState.checkForUpdate(currentVersion: String) {
    if (updateState is UpdateState.Checking || updateState is UpdateState.Downloading) return
    updateState = UpdateState.Checking
    val channel = channelForLanguage(prefs.language)
    scope.launch {
        val result = withContext(Dispatchers.IO) { UpdateChecker().latest(channel) }
        result.onSuccess { info ->
            val current = currentVersion.ifBlank { "0" }
            val platform = detectPlatform()
            val arch = detectArch()
            val lite = runningLiteInstall()
            updateState = if (isNewerVersion(info.tagName, current)) {
                val asset = pickUpdateAsset(info.assets, platform, arch, lite)
                if (asset == null) {
                    UpdateState.Failed(
                        "${info.tagName} 已发布，但没有适合本机" +
                            "（${platform.label} · ${arch.ifEmpty { "未知架构" }}" +
                            "${if (lite) " · 轻量包" else ""}）的安装包",
                        "打开 Release 页手动挑选",
                        channel,
                    )
                } else {
                    UpdateState.Available(info, asset, channel, current)
                }
            } else {
                UpdateState.UpToDate(current, channel)
            }
        }.onFailure { e ->
            val message = when (e) {
                is UpdateException -> when (e.kind) {
                    UpdateException.Kind.NotFound -> "${channel.label} 上还没有 Release"
                    UpdateException.Kind.RateLimited -> "查询被限流，稍后再试"
                    else -> "查询失败：${e.message}"
                }
                else -> "查询失败：${e.message ?: e.javaClass.simpleName}"
            }
            updateState = UpdateState.Failed(message, "打开 Release 页手动查看", channel)
        }
    }
}

/** 下载当前 [UpdateState.Available] 指到的那个包，下完就地验哈希。 */
fun AppState.downloadUpdate() {
    val offer = (updateState as? UpdateState.Available) ?: return
    val dest = File(updatesDir(), offer.asset.name)
    updateState = UpdateState.Downloading(0, offer.asset.size, dest, offer)
    updateDownloadJob = scope.launch {
        val downloaded = withContext(Dispatchers.IO) {
            UpdateChecker().download(offer.asset, dest) { received, total ->
                updateState = UpdateState.Downloading(received, total, dest, offer)
            }
        }
        downloaded.onFailure { e ->
            // 半截文件留着只会误导下一次校验，删掉重来
            runCatching { dest.delete() }
            updateState = UpdateState.Failed(
                "下载失败：${e.message ?: e.javaClass.simpleName}",
                "打开 Release 页手动下载",
                offer.channel,
            )
            return@launch
        }
        // 校验：先找 `<包名>.sha256`（单文件形式），找不到再退回整份 SHA256SUMS 按文件名查
        val expected = withContext(Dispatchers.IO) { expectedShaFor(offer) }
        if (expected == null) {
            // 没有清单可对：不装、不指引，明确说"没能校验"
            updateState = UpdateState.Failed(
                "下载完成，但 Release 里找不到 ${offer.asset.name} 的 SHA-256，不敢让你装",
                "去 Release 页核对后手动安装",
                offer.channel,
            )
            return@launch
        }
        val actual = withContext(Dispatchers.IO) { sha256OfFile(dest) }
        if (actual != expected) {
            runCatching { dest.delete() }
            updateState = UpdateState.Failed(
                "下载的文件哈希对不上（期望 $expected，实际 $actual），已删除",
                "重新下载，或去 Release 页手动核对",
                offer.channel,
            )
            return@launch
        }
        updateState = UpdateState.Ready(dest, offer.asset, offer.info)
    }
}

/** 取消下载：停任务、删半截文件、退回"有新版"。 */
fun AppState.cancelUpdateDownload() {
    val downloading = updateState as? UpdateState.Downloading ?: return
    updateDownloadJob?.cancel()
    updateDownloadJob = null
    runCatching { downloading.file.delete() }
    updateState = downloading.offer
}

/** 查这个包的期望哈希；查不到返回 null（调用方按"不敢装"处理）。 */
private suspend fun expectedShaFor(offer: UpdateState.Available): String? {
    val sumsUrls = offer.info.assets
        .filter { it.name == "${offer.asset.name}.sha256" || it.name == "SHA256SUMS" }
        .map { it.browserDownloadUrl }
        .filter { it.isNotBlank() }
    for (url in sumsUrls) {
        val text = withContext(Dispatchers.IO) {
            runCatching {
                OkHttpClient().newCall(
                    Request.Builder().url(url).header("User-Agent", "dsh-desktop").build(),
                ).execute().use { resp -> if (resp.isSuccessful) resp.body?.string() else null }
            }.getOrNull()
        } ?: continue
        parseSha256Sums(text)[offer.asset.name]?.let { return it }
    }
    return null
}

/** 供界面显示的一句话安装指引。 */
fun UpdateState.Ready.installHint(): String = when (detectPlatform()) {
    UpdatePlatform.Linux ->
        "已下载并校验通过：${file.name}\n" +
            "在终端执行  sudo dpkg -i ${file.absolutePath}  完成安装，" +
            "或点「打开安装」交给系统软件中心。"
    UpdatePlatform.Windows ->
        "已下载并校验通过：${file.name}\n点「打开安装」运行安装程序；zip 包则解压后覆盖原目录。"
    else -> "已下载并校验通过：${file.name}\n当前平台没有现成安装通路，请手动安装。"
}

/**
 * 把下载好的包交给系统（Linux 的 deb / Windows 的 msi）。
 *
 * 不自己提权、不静默替换：`Desktop.open` 会把 deb 交给软件中心、msi 交给安装程序，
 * 提不提权由用户在那个界面里决定。返回 false 时界面显示手动命令。
 */
fun AppState.openDownloadedPackage(file: File): Boolean = runCatching {
    if (java.awt.Desktop.isDesktopSupported()) {
        java.awt.Desktop.getDesktop().open(file)
        true
    } else {
        false
    }
}.getOrDefault(false)

/** Release 页兜底链接。 */
fun UpdateChannel.releasePageUrl(): String = when (this) {
    UpdateChannel.Gitee -> "https://gitee.com/westanke/dsh-desktop/releases"
    UpdateChannel.GitHub -> "https://github.com/westanke/dsh-desktop/releases"
}

/** 打开 Release 页（检查失败时的兜底）。 */
fun AppState.openReleasePage(channel: UpdateChannel) {
    val url = channel.releasePageUrl()
    if (!openExternal(url)) showToast("打不开浏览器，请手动访问 $url")
}
