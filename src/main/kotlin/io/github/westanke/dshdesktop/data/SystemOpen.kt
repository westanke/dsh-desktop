package io.github.westanke.dshdesktop.data

import java.awt.Desktop
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 把本地文件交给操作系统的默认程序。
 *
 * 客户端自己只渲染 Markdown / 文本 / 图片三类，其余（PDF、Word、压缩包、音视频……）
 * 一律"下载到本地 → 交给系统"。这样不用为了看一份 PDF 在客户端里塞一个 PDF 引擎，
 * 也不会因为某个格式没实现就变成"点了没反应"。
 *
 * 为什么要按平台列一串命令、而不是只调 `Desktop.open`：
 *
 * - **Linux / UOS**：`Desktop.isDesktopSupported()` 在有 X11 时返回 true，但
 *   `Desktop.open` 依赖 AWT 的 `gnome_url_show`/`gvfs` 桥，UOS 上经常静默失败；
 *   `xdg-open` 才是各桌面环境都认的入口，`gio open` 是它的兜底。
 * - **Windows**：`Desktop.open` 一般可用，但它对含空格/中文的路径偶尔会抛
 *   `IllegalArgumentException`；`rundll32 url.dll,FileProtocolHandler` 更稳，
 *   `cmd /c start` 是最后一层。
 *
 * 返回值统一成 [Result]：调用方要把失败原因说给人听，不能点了没反应。
 */
fun openWithSystem(file: File): Result<Unit> = runCatching {
    check(file.exists()) { "文件不在本地：${file.absolutePath}" }

    val attempts = mutableListOf<String>()
    for (command in openCommands(file)) {
        when (val outcome = runCommand(command)) {
            CommandOutcome.Started -> return@runCatching
            is CommandOutcome.Failed -> attempts += "${command.first()}：${outcome.why}"
        }
    }

    // 最后再试一次 AWT：命令行工具都没装的环境（精简系统）只剩这条路
    val desktopError = runCatching {
        check(Desktop.isDesktopSupported()) { "系统未提供 Desktop 接口" }
        val desktop = Desktop.getDesktop()
        check(desktop.isSupported(Desktop.Action.OPEN)) { "系统不支持用默认程序打开文件" }
        desktop.open(file)
    }.exceptionOrNull()
    if (desktopError == null) return@runCatching
    attempts += "Desktop.open：${desktopError.message ?: desktopError::class.simpleName}"

    error("没有可用的打开方式（${attempts.joinToString("；")}）")
}

/** 按平台给出候选命令，从最可靠的排到最兜底的。 */
private fun openCommands(file: File): List<List<String>> {
    val path = file.absolutePath
    return when {
        isWindows() -> listOf(
            listOf("rundll32.exe", "url.dll,FileProtocolHandler", path),
            listOf("cmd.exe", "/c", "start", "", path),
        )

        isMac() -> listOf(listOf("open", path))

        else -> listOf(
            listOf("xdg-open", path),
            listOf("gio", "open", path),
        )
    }
}

private sealed interface CommandOutcome {
    data object Started : CommandOutcome
    data class Failed(val why: String) : CommandOutcome
}

/**
 * 跑一条打开命令。
 *
 * 只等 2 秒：`xdg-open` 一般立刻返回，但有的桌面环境会一直挂着等程序退出。
 * 超时不当作失败 —— 那说明程序已经起来了，杀掉它反而会把用户刚开的窗口带走。
 */
private fun runCommand(command: List<String>): CommandOutcome = try {
    val process = ProcessBuilder(command)
        .redirectErrorStream(true)
        .start()
    val finished = process.waitFor(2, TimeUnit.SECONDS)
    if (!finished) {
        CommandOutcome.Started
    } else if (process.exitValue() == 0) {
        CommandOutcome.Started
    } else {
        val detail = runCatching {
            process.inputStream.bufferedReader().readText().trim().take(200)
        }.getOrDefault("")
        CommandOutcome.Failed(
            if (detail.isEmpty()) "退出码 ${process.exitValue()}" else detail,
        )
    }
} catch (e: Exception) {
    // 命令不存在、没有执行权限、被安全策略拦下都走这里
    CommandOutcome.Failed(e.message?.take(120) ?: e::class.simpleName.orEmpty())
}

private fun isWindows(): Boolean =
    System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)

private fun isMac(): Boolean =
    System.getProperty("os.name").orEmpty().contains("Mac", ignoreCase = true)
