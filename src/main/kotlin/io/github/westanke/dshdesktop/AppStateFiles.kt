package io.github.westanke.dshdesktop

import io.github.westanke.dshdesktop.data.AppStorage
import io.github.westanke.dshdesktop.data.FileEntry
import io.github.westanke.dshdesktop.data.FileKind
import io.github.westanke.dshdesktop.data.FileType
import io.github.westanke.dshdesktop.data.MAX_IMAGE_BYTES
import io.github.westanke.dshdesktop.data.MAX_TEXT_PREVIEW_BYTES
import io.github.westanke.dshdesktop.data.PreviewExtra
import io.github.westanke.dshdesktop.data.WorkspaceApiException
import io.github.westanke.dshdesktop.data.classifyFile
import io.github.westanke.dshdesktop.data.decodeText
import io.github.westanke.dshdesktop.data.formatFileSize
import io.github.westanke.dshdesktop.data.imageFormatOf
import io.github.westanke.dshdesktop.data.openWithSystem
import io.github.westanke.dshdesktop.data.refineByContent
import io.github.westanke.dshdesktop.data.resolvePath
import io.github.westanke.dshdesktop.data.uploadEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Files

/**
 * 文件域动作（从 `AppState.kt` 按域拆出）。
 *
 * 拆分理由写在 `AppState.kt` 的构造参数注释里：Kotlin 没有 partial class，
 * 800 行预算是硬约束（`tools/docs-check.sh` 机检），只能靠同包扩展函数按域切。
 *
 * 这一批全是"调接口 → 落状态 → 报错说人话"的同构动作，与聊天域没有交集，
 * 唯独 [openAbsolutePath] 会被聊天页调用（点交付物卡片时定位文件）。
 */

fun AppState.refreshRoots() {
    scope.launch {
        runCatching { withContext(Dispatchers.IO) { client().listRoots() } }
            .onSuccess { list ->
                roots = list
                if (activeRootId == null || list.none { it.id == activeRootId }) {
                    activeRootId = list.firstOrNull()?.id
                    currentPath = ""
                    refreshDirectory()
                }
            }
            .onFailure { filesError = AppState.human(it) }
    }
}

fun AppState.refreshDirectory(path: String = currentPath) {
    val rootId = activeRootId ?: return
    filesBusy = true
    scope.launch {
        runCatching { withContext(Dispatchers.IO) { client().listEntries(rootId, path) } }
            .onSuccess {
                currentPage = it
                currentPath = it.path
                filesError = null
            }
            .onFailure { filesError = AppState.human(it) }
        filesBusy = false
    }
}

fun AppState.openDirectory(path: String) {
    selectedEntry = null
    filePreview = null
    previewExtra = null
    refreshDirectory(path)
}

/**
 * 点开一个文件：先按类型分流，再决定读多少、怎么读。
 *
 * 三个分支各有一条硬约束：
 * - **Markdown / 文本**只取前 `MAX_TEXT_PREVIEW_BYTES`（Range 请求），
 *   几十兆的日志不该因为"看一眼"就整份拉进内存；
 * - **图片**整份读，但超过 `MAX_IMAGE_BYTES` 直接不给内嵌，改走系统程序；
 * - **其他**根本不读内容 —— 反正渲染不了，读了也是白花带宽。
 */
fun AppState.previewEntry(entry: FileEntry) {
    selectedEntry = entry
    filePreview = null
    filePreviewTruncated = false
    filePreviewEtag = null
    fileDraft = null
    previewExtra = null
    previewShowSource = false
    val rootId = activeRootId ?: return
    if (entry.kind == "directory") return

    val declared = classifyFile(entry.name)
    scope.launch {
        runCatching { withContext(Dispatchers.IO) { loadPreview(rootId, entry, declared) } }
            .onSuccess {
                previewExtra = it.extra
                filePreview = it.text
                filePreviewTruncated = it.truncated
                filePreviewEtag = it.etag
            }
            .onFailure { filesError = AppState.human(it) }
    }
}

private class LoadedPreview(
    val extra: PreviewExtra,
    val text: String?,
    val truncated: Boolean,
    val etag: String?,
)

private fun AppState.loadPreview(rootId: String, entry: FileEntry, declared: FileType): LoadedPreview {
    if (declared.kind == FileKind.Other) {
        return LoadedPreview(PreviewExtra(declared), null, false, null)
    }

    if (declared.kind == FileKind.Image) {
        if (entry.size > MAX_IMAGE_BYTES) {
            val tooBig = declared.copy(
                kind = FileKind.Other,
                reason = "图片有 ${formatFileSize(entry.size)}，超过 " +
                    "${MAX_IMAGE_BYTES / 1024 / 1024} MB 的内嵌上限，已改为交给系统程序打开。",
            )
            return LoadedPreview(PreviewExtra(tooBig), null, false, null)
        }
        val content = client().readFile(rootId, entry.path)
        val format = imageFormatOf(content.bytes)
        if (format != null) {
            return LoadedPreview(
                PreviewExtra(declared, imageBytes = content.bytes, imageFormat = format),
                null, false, content.etag,
            )
        }
        // 扩展名说是图片、内容却不是（改过后缀、下载坏了）→ 按文本再判一次，二进制会自己被降级
        return textPreview(declared.copy(kind = FileKind.Text, label = "文本", reason = null), content.bytes, content.etag, entry)
    }

    // 文本 / Markdown：文件比上限大就只要前一段
    val truncatedBySize = entry.size > MAX_TEXT_PREVIEW_BYTES
    val content = client().readFile(
        rootId,
        entry.path,
        range = if (truncatedBySize) 0L..(MAX_TEXT_PREVIEW_BYTES - 1).toLong() else null,
    )
    return textPreview(declared, content.bytes, content.etag, entry)
}

private fun AppState.textPreview(
    declared: FileType,
    raw: ByteArray,
    etag: String?,
    entry: FileEntry,
): LoadedPreview {
    // 服务端可能不认 Range（照样 200 整份返回），所以本地再截一刀，别信它
    val bytes = if (raw.size > MAX_TEXT_PREVIEW_BYTES) raw.copyOf(MAX_TEXT_PREVIEW_BYTES) else raw
    val type = refineByContent(declared, bytes)
    if (type.kind == FileKind.Other) {
        // 二进制不给 etag：没有"编辑"这回事，免得上层误以为能保存
        return LoadedPreview(PreviewExtra(type), null, false, null)
    }
    val decoded = decodeText(bytes)
    val truncated = entry.size > MAX_TEXT_PREVIEW_BYTES
    return LoadedPreview(
        extra = PreviewExtra(type, charset = decoded.charset, note = decoded.note),
        text = decoded.text,
        truncated = truncated,
        // 截断过的内容不许编辑（改一个字再整份写回去 = 毁文件），所以也不给 etag
        etag = if (truncated) null else etag,
    )
}

/**
 * 下载到本地缓存，然后交给系统默认程序。
 *
 * 缓存目录按 `entry.path` 的哈希分子目录：同名不同目录的两个 `报告.pdf`
 * 不会互相覆盖，反复打开同一个文件又不用每次重新拉。
 */
fun AppState.openEntryExternally(entry: FileEntry) {
    val rootId = activeRootId ?: return
    scope.launch {
        filesBusy = true
        runCatching {
            withContext(Dispatchers.IO) {
                val bytes = client().readFile(rootId, entry.path).bytes
                val dir = AppStorage.cacheRoot()
                    .resolve("opened")
                    .resolve("%08x".format(entry.path.hashCode()))
                Files.createDirectories(dir)
                val target = dir.resolve(entry.name.substringAfterLast('/')).toFile()
                target.writeBytes(bytes)
                openWithSystem(target).getOrThrow()
                target
            }
        }.onSuccess { showToast("已交给系统打开：${it.name}") }
            .onFailure { filesError = "打不开「${entry.name}」：${it.message}" }
        filesBusy = false
    }
}

fun AppState.uploadFile(localFile: java.io.File) {
    val rootId = activeRootId ?: return
    val target = if (currentPath.isEmpty()) localFile.name else "$currentPath/${localFile.name}"
    scope.launch {
        runCatching {
            withContext(Dispatchers.IO) {
                client().uploadEntry(rootId, target, localFile.readBytes())
            }
        }.onSuccess {
            showToast("已上传 ${localFile.name}")
            refreshDirectory()
        }.onFailure { filesError = AppState.human(it) }
    }
}

fun AppState.downloadEntry(entry: FileEntry, destination: java.io.File) {
    val rootId = activeRootId ?: return
    scope.launch {
        runCatching {
            withContext(Dispatchers.IO) {
                val bytes = client().readFile(rootId, entry.path).bytes
                destination.writeBytes(bytes)
            }
        }.onSuccess { showToast("已保存到 ${destination.absolutePath}") }
            .onFailure { filesError = AppState.human(it) }
    }
}

fun AppState.deleteEntry(entry: FileEntry) {
    val rootId = activeRootId ?: return
    scope.launch {
        runCatching { withContext(Dispatchers.IO) { client().trashEntry(rootId, entry.path) } }
            .onSuccess {
                showToast("已移入回收站：${entry.name}")
                if (selectedEntry?.path == entry.path) {
                    selectedEntry = null
                    filePreview = null
                    previewExtra = null
                }
                refreshDirectory()
            }
            .onFailure { filesError = AppState.human(it) }
    }
}

fun AppState.createEntry(name: String, directory: Boolean) {
    val rootId = activeRootId ?: return
    val target = if (currentPath.isEmpty()) name else "$currentPath/$name"
    scope.launch {
        runCatching {
            withContext(Dispatchers.IO) {
                client().createEntry(rootId, target, if (directory) "directory" else "file")
            }
        }.onSuccess {
            showToast(if (directory) "已新建文件夹" else "已新建文件")
            refreshDirectory()
        }.onFailure { filesError = AppState.human(it) }
    }
}

fun AppState.refreshTrash() {
    scope.launch {
        runCatching { withContext(Dispatchers.IO) { client().listTrash() } }
            .onSuccess { trash = it }
            .onFailure { filesError = AppState.human(it) }
    }
}

fun AppState.restoreTrash(id: String) {
    scope.launch {
        runCatching { withContext(Dispatchers.IO) { client().restoreTrash(id) } }
            .onSuccess {
                showToast("已恢复")
                refreshTrash()
                refreshDirectory()
            }
            .onFailure { filesError = AppState.human(it) }
    }
}

/**
 * 按服务器绝对路径定位到文件页。
 *
 * 会话里出现的绝对路径（页头的 cwd、命令行 `-Ddsh.openPath=`）先交给服务端
 * `roots/resolve` 翻译成 rootId + 相对路径，再去文件页把目录列出来。
 * 目标是**定位**，不是打开 —— 要点开某个文件用 [openFileByPath]。
 *
 * @param preview 顺便把这个文件打开预览（只对能内嵌的类型有意义）。
 */
fun AppState.openAbsolutePath(absolutePath: String, preview: Boolean = false) {
    scope.launch {
        runCatching { withContext(Dispatchers.IO) { client().resolvePath(absolutePath) } }
            .onSuccess { resolved ->
                nav = Nav.Files
                activeRootId = resolved.rootId
                val parent = resolved.path.substringBeforeLast('/', "")
                if (preview) {
                    // 自己列一次目录：refreshDirectory 是发射出去就不管的异步动作，
                    // 紧跟着按路径找条目会落在"还没列完"的空目录上
                    val page = runCatching {
                        withContext(Dispatchers.IO) { client().listEntries(resolved.rootId, parent) }
                    }.getOrNull()
                    if (page == null) {
                        openDirectory(parent)
                    } else {
                        currentPage = page
                        currentPath = page.path
                        page.entries.firstOrNull { it.path == resolved.path }?.let { previewEntry(it) }
                    }
                } else {
                    openDirectory(parent)
                }
                showToast("已定位到 ${resolved.path.ifEmpty { "/" }}")
            }
            .onFailure { showToast(AppState.human(it)) }
    }
}

/**
 * 点交付物卡片：直接把**这个文件**打开。
 *
 * 与 [openAbsolutePath] 的分工：那个是"定位到目录"，这个是"打开这个文件"。
 * 卡片上写的是文件名，点下去就该看到内容，而不是被扔进文件列表再自己找一遍。
 *
 * 能不能在客户端里打开由 [classifyFile] 判（只看名字，与文件页预览同一套口径）：
 * - **Markdown / 文本 / 图片** → 文件页内嵌预览；
 * - **其余**（PDF / Office / 压缩包 / 二进制）→ [openEntryExternally] 下载到缓存后
 *   交给系统默认程序 —— 本客户端没有这些格式的渲染器，"不能打开就交给系统去判断"。
 * - 图片超过 [MAX_IMAGE_BYTES] 同样直接走系统：内嵌会被降级成"看不了"再让人点一次，
 *   不如一开始就交给系统，省一次无效跳转。
 *
 * 三个兜底：解析失败说人话；路径指向目录就退回 [openDirectory]；
 * 列父目录找不到这个条目（文件已被删/移走）也退回列目录并说明。
 */
fun AppState.openFileByPath(absolutePath: String) {
    scope.launch {
        val prepared = runCatching {
            withContext(Dispatchers.IO) {
                val resolved = client().resolvePath(absolutePath)
                val parent = resolved.path.substringBeforeLast('/', "")
                // 列一次父目录才能拿到 FileEntry（size / kind 只有条目里有）：
                // resolve 只给路径，预览和外呼都要那个条目
                val page = client().listEntries(resolved.rootId, parent)
                Triple(resolved, parent, page)
            }
        }.getOrElse {
            showToast(AppState.human(it))
            return@launch
        }
        val (resolved, parent, page) = prepared
        nav = Nav.Files
        activeRootId = resolved.rootId
        currentPage = page
        currentPath = page.path
        filesError = null

        val entry = page.entries.firstOrNull { it.path == resolved.path }
        if (entry == null) {
            openDirectory(parent)
            showToast("这个文件已经不在原位置了：${resolved.path}")
            return@launch
        }
        if (entry.kind == "directory") {
            openDirectory(entry.path)
            return@launch
        }

        val inline = when (classifyFile(entry.name).kind) {
            FileKind.Markdown, FileKind.Text -> true
            FileKind.Image -> entry.size <= MAX_IMAGE_BYTES
            FileKind.Other -> false
        }
        // 仍然停在文件页：外呼失败的话（openEntryExternally 只写 filesError），
        // 这里还能看到"打不开"的原因，以及"用系统程序打开 / 下载"两个按钮
        if (inline) previewEntry(entry) else openEntryExternally(entry)
    }
}


// ── 编辑与移动到 ────────────────────────────────────────────────────────────

/**
 * 进入编辑。
 *
 * **截断过的文件不给编辑**。预览只读了前 512 KiB，在这种内容上改一个字再整份写回去，
 * 等于把后面几十兆直接抹掉 —— 这是不可逆的数据损坏，比"不能编辑"糟得多。
 */
fun AppState.startEditing() {
    // 只有文本类能就地改。图片/二进制连编辑框都不该出现，这里是第二道闸
    val kind = previewExtra?.type?.kind
    if (kind != null && kind != FileKind.Text && kind != FileKind.Markdown) {
        showToast("这类文件不支持就地编辑，可以下载或交给系统程序")
        return
    }
    val text = filePreview ?: return
    if (filePreviewTruncated) {
        showToast("文件太大，客户端只读了前一段，不做编辑（会截断原文件）")
        return
    }
    fileDraft = text
}

fun AppState.cancelEditing() {
    fileDraft = null
}

fun AppState.saveDraft() {
    val rootId = activeRootId ?: return
    val entry = selectedEntry ?: return
    val text = fileDraft ?: return
    val etag = filePreviewEtag
    filesBusy = true
    scope.launch {
        runCatching {
            withContext(Dispatchers.IO) {
                client().writeFile(rootId, entry.path, text.toByteArray(Charsets.UTF_8), etag)
            }
        }.onSuccess { newEtag ->
            filePreviewEtag = newEtag
            filePreview = text
            fileDraft = null
            showToast("已保存 ${entry.name}")
            refreshDirectory()
        }.onFailure { error ->
            if (error is WorkspaceApiException && error.status == 412) {
                // 412 = 别人先改了。**不覆盖**：把最新内容读回来，草稿留在编辑器里，
                // 让人自己看一眼再决定 —— 替用户做"我的更对"的决定是最不该做的事。
                showToast("文件已被别处改动，已重新载入最新内容；你的编辑还在，请核对后再保存")
                previewEntry(entry)
                fileDraft = text
            } else {
                filesError = AppState.human(error)
            }
        }
        filesBusy = false
    }
}

/**
 * 移动 / 重命名。
 *
 * 合成一个动作：协议里 `move` 同时承担改名与挪目录（目标路径就是答案）。
 * 界面上也就只给一个"路径"框，改文件名和改位置不必学两套操作。
 */
fun AppState.moveEntry(entry: FileEntry, destination: String) {
    val rootId = activeRootId ?: return
    val target = destination.trim().trimStart('/')
    if (target.isEmpty() || target == entry.path) return
    scope.launch {
        runCatching { withContext(Dispatchers.IO) { client().moveEntry(rootId, entry.path, target) } }
            .onSuccess {
                showToast("已移动到 $target")
                if (selectedEntry?.path == entry.path) {
                    selectedEntry = null
                    filePreview = null
                    fileDraft = null
                    previewExtra = null
                }
                refreshDirectory()
            }
            .onFailure { filesError = AppState.human(it) }
    }
}
