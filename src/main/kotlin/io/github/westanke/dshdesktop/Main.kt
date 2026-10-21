package io.github.westanke.dshdesktop

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.westanke.dshdesktop.ui.App
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.awt.Dimension
import java.io.File

/**
 * DSH Desktop 入口。
 *
 * 这是一个原生桌面应用：界面由 Compose 的 Skia 渲染管线直接绘制，不加载任何
 * HTML/WebView。网络侧只跟 dsh-remote-bridge 的公开 REST + WebSocket 说话。
 */
fun main() {
    ensureWritableDirs()
    ensureDesktopShortcut()
    runApp()
}

/** skiko 解压出来的 `libskiko-linux-x64.so` 约 30 MB，留足余量再判定"这个目录能用"。 */
private const val NATIVE_UNPACK_BYTES = 96L * 1024 * 1024

/**
 * 首次启动时补一份桌面图标。
 *
 * deb 的 postinst 已经试过一次，但安装那一刻用户往往还没登录图形会话，
 * `~/Desktop` 压根不存在，于是那次落空。这里以**用户身份**再补一次：
 * 系统菜单项在 `/usr/share/applications`，桌面图标是 UOS 上独立的一份拷贝。
 *
 * 只做一次（写完 `shortcut-installed` 标记就收手）——用户主动删掉的图标不能每次启动又长回来。
 */
private fun ensureDesktopShortcut() {
    if (!System.getProperty("os.name").orEmpty().contains("Linux", ignoreCase = true)) return
    val marker = io.github.westanke.dshdesktop.data.AppStorage.root().resolve("shortcut-installed")
    if (java.nio.file.Files.exists(marker)) return

    val systemEntry = File("/usr/share/applications/dsh-desktop.desktop")
    if (!systemEntry.isFile) return

    val home = System.getProperty("user.home").orEmpty()
    val desktop = desktopDir(home) ?: return
    val target = File(desktop, "dsh-desktop.desktop")

    runCatching {
        if (!target.exists()) systemEntry.copyTo(target)
        target.setExecutable(true, false)
        // 不标记 trusted，UOS 双击会弹"不信任的启动器"
        runCatching {
            ProcessBuilder("gio", "set", target.absolutePath, "metadata::trusted", "true")
                .redirectErrorStream(true)
                .start()
                .waitFor()
        }
        java.nio.file.Files.writeString(marker, "1")
    }
}

/** 依次看 `~/.config/user-dirs.dirs`、`~/Desktop`、`~/桌面`，取第一个真实存在的。 */
private fun desktopDir(home: String): File? {
    if (home.isBlank()) return null
    val dirsFile = File(home, ".config/user-dirs.dirs")
    val fromXdg = runCatching {
        dirsFile.readLines()
            .firstOrNull { it.trimStart().startsWith("XDG_DESKTOP_DIR=") }
            ?.substringAfter('=')
            ?.trim()
            ?.trim('"')
            ?.replace("\$HOME", home)
    }.getOrNull()
    return listOfNotNull(fromXdg, "$home/Desktop", "$home/桌面")
        .map(::File)
        .firstOrNull { it.isDirectory }
}

/**
 * 启动前把**两个**要落盘的位置都探一遍：skiko 的原生库目录和 JVM 的临时目录。
 *
 * skiko 启动时要把 jar 里的原生库解压出来再加载。它不认 `java.io.tmpdir`，
 * 而是用自己的 `skiko.data.path`，默认 `~/.skiko`。所以光探 tmpdir 是不够的：
 * 以服务/包方式启动（或 `sudo`）时 `user.home` 会落到 `/root` 这种不可写的位置，
 * 于是 `~/.skiko` 建不出来，skiko 抛 `ExceptionInInitializerError`，
 * 根因写着 `createTempFile … Permission denied`——看起来像权限问题，
 * 实际上跟 tmpdir 一点关系都没有，排查方向会被带偏。
 *
 * 两处都探，哪处不行就换到用户缓存目录；换完后原生库只需解压一次，之后启动直接命中。
 */
private fun ensureWritableDirs() {
    // 用户在启动参数里明确指定了就尊重，不去覆盖
    if (System.getProperty("skiko.data.path").isNullOrBlank()) {
        val home = System.getProperty("user.home").orEmpty()
        if (!usableDir(File(home, ".skiko"))) {
            nativeDirCandidates().firstOrNull(::usableDir)?.let {
                System.setProperty("skiko.data.path", it.absolutePath)
            }
        }
    }

    val current = File(System.getProperty("java.io.tmpdir").orEmpty())
    if (usableDir(current)) return
    tempDirCandidates().firstOrNull(::usableDir)?.let {
        System.setProperty("java.io.tmpdir", it.absolutePath)
    }
}

/**
 * 目录"能用"的判据：建得出文件，**并且**剩余空间装得下原生库。
 *
 * 只试建一个 0 字节文件是不够的 —— 有的环境 `/tmp` 是几 MB 的 tmpfs，
 * 探针过了，写 30 MB 的 `.so` 照样失败，报错还是那句 `Permission denied`。
 */
private fun usableDir(dir: File): Boolean = runCatching {
    if (!dir.isDirectory && !dir.mkdirs()) return false
    if (dir.usableSpace in 1 until NATIVE_UNPACK_BYTES) return false
    File.createTempFile("dsh-probe", ".tmp", dir).delete()
}.getOrDefault(false)

/** 原生库的退路：先用户数据目录，最后落到工作目录。 */
private fun nativeDirCandidates(): List<File> = buildList {
    System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() }?.let { add(File(it, "dsh-desktop/skiko")) }
    System.getenv("HOME")?.takeIf { it.isNotBlank() }?.let { add(File(it, ".local/share/dsh-desktop/skiko")) }
    System.getProperty("user.home").orEmpty().takeIf { it.isNotBlank() }?.let { add(File(it, ".skiko")) }
    add(File(System.getProperty("user.dir") ?: ".", ".dsh-desktop/skiko"))
}

/**
 * 临时目录的退路。注意不能只看 `user.home`：以服务/包方式启动时
 * `HOME` 可能为空，`user.home` 会落到 `/root` 这种不可写的位置。
 */
private fun tempDirCandidates(): List<File> = buildList {
    System.getenv("XDG_CACHE_HOME")?.takeIf { it.isNotBlank() }?.let { add(File(it, "dsh-desktop/tmp")) }
    System.getenv("HOME")?.takeIf { it.isNotBlank() }?.let { add(File(it, ".cache/dsh-desktop/tmp")) }
    System.getProperty("user.home").orEmpty().takeIf { it.isNotBlank() }?.let { add(File(it, ".cache/dsh-desktop/tmp")) }
    add(File(System.getProperty("user.dir") ?: ".", ".dsh-desktop-tmp"))
}

private fun runApp() = application {
    val scope = rememberCoroutineScope()
    val state = remember { AppState(scope) }
    val windowState = rememberWindowState(width = 1280.dp, height = 820.dp)

    LaunchedEffect(Unit) {
        // 部署预置：-Ddsh.endpoint=host:3090 -Ddsh.paircode=xxxx
        // 用于批量发机或联调，免去手工输入；两种属性都没有时走"恢复已保存凭据"。
        val preset = System.getProperty("dsh.endpoint")
        if (preset.isNullOrBlank()) state.autoConnect()
        else state.connect(preset, System.getProperty("dsh.paircode"))

        // 命令行直接打开某个文件：-Ddsh.openPath=/服务器/绝对路径
        // 两个用处：从终端直接调到某个文件；以及在无法合成鼠标事件的会话里做界面截图。
        val openPath = System.getProperty("dsh.openPath")
        if (!openPath.isNullOrBlank()) {
            scope.launch {
                repeat(100) {
                    if (state.connection == ConnectionState.Connected) {
                        state.openAbsolutePath(openPath, preview = true)
                        return@launch
                    }
                    delay(200)
                }
            }
        }
    }

    Window(
        onCloseRequest = {
            state.shutdown()
            exitApplication()
        },
        title = "DSH Desktop",
        state = windowState,
        // Ctrl+1/2/3 切换会话 / 文件 / 设置：桌面端的基本肌肉记忆，也让自动化测试可驱动
        onPreviewKeyEvent = { event ->
            if (event.type == KeyEventType.KeyDown && event.isCtrlPressed) {
                when (event.key) {
                    Key.One -> { state.nav = Nav.Chat; true }
                    Key.Two -> { state.nav = Nav.Files; true }
                    Key.Three -> { state.nav = Nav.Settings; true }
                    else -> false
                }
            } else false
        },
    ) {
        window.minimumSize = Dimension(1040, 660)
        // 尺寸兜底：dp → px 要过 Compose 的 density，而 density 是从 X 的 DPI 推的。
        // UOS 上缩放/DPI 配置一旦异常，1280.dp 会被算成 0，窗口就以 1×1 创建出来
        // ——用户看到的是"点了没反应"，连标题栏都没有（真机 2026-10-21 实测）。
        // 这里在组合后直接按像素兜一次底，不依赖 density。
        LaunchedEffect(Unit) {
            if (window.width < 400 || window.height < 300) {
                window.setSize(1280, 820)
                window.setLocationRelativeTo(null)
            }
        }
        App(state, isSystemInDarkTheme())
    }
}
