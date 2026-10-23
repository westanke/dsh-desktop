package io.github.westanke.dshdesktop.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import io.github.westanke.dshdesktop.AppState
import io.github.westanke.dshdesktop.data.readAttachment
import io.github.westanke.dshdesktop.data.resolvePath
import io.github.westanke.dshdesktop.openFileByPath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 会话里的图片附件。
 *
 * 为什么不在解析历史时就把图全下来：一次历史返回 300 多条事件，里面可能有几十张图，
 * 其中大半根本不在首屏。按需在绘制时拉，配合一个小容量 LRU，滚动回去不用重下。
 *
 * 缓存上限刻意给得很小（24 张）：图片是给人看的，不是数据，占着内存不放没有意义；
 * 超出后最久未用的那张被挤掉，再滚回去会重新拉一次 —— 比把内存吃满好。
 */
private const val MAX_CACHED_IMAGES = 24

private val imageCache = object : LinkedHashMap<String, ImageBitmap>(MAX_CACHED_IMAGES, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?): Boolean =
        size > MAX_CACHED_IMAGES
}

/** 解码失败的结果也缓存：反复重试一张坏图只是白烧一趟网络。 */
private val failedIds = object : LinkedHashMap<String, Boolean>(MAX_CACHED_IMAGES, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean =
        size > MAX_CACHED_IMAGES
}

/** 清掉缓存（换设备/重新配对后旧 id 可能失效）。 */
internal fun clearImageCache() {
    imageCache.clear()
    failedIds.clear()
}

@Composable
fun RemoteImage(
    state: AppState,
    sessionId: String?,
    attachmentId: String,
    size: Int = 140,
) {
    val colors = LocalAppColors.current
    val cacheKey = "$sessionId|$attachmentId"
    val bitmap by produceState<ImageBitmap?>(initialValue = imageCache[cacheKey], key1 = cacheKey) {
        if (value != null || failedIds[cacheKey] == true || sessionId == null) return@produceState
        val loaded = runCatching {
            withContext(Dispatchers.IO) { state.client().readAttachment(sessionId, attachmentId).first }
        }.getOrNull()?.let { bytes ->
            runCatching { org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull()
        }
        if (loaded == null) failedIds[cacheKey] = true else imageCache[cacheKey] = loaded
        value = loaded
    }

    Box(
        Modifier
            .size(size.dp)
            .background(colors.raisedSurface, RoundedCornerShape(Tokens.radiusSm)),
        contentAlignment = Alignment.Center,
    ) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size.dp).clickable { ImagePreview.open(image) },
            )
        } else {
            Text(
                if (failedIds[cacheKey] == true) "取不到图" else "读取中…",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textTertiary,
            )
        }
    }
}

/** 一条消息里的若干张图：横排，超出换行由调用方决定。 */
@Composable
fun ImageStrip(state: AppState, sessionId: String?, ids: List<String>) {
    if (ids.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        ids.forEach { id -> RemoteImage(state, sessionId, id, size = 120) }
    }
}

/**
 * 正文 Markdown 里的图片（P1-1）：target 是**服务端绝对路径**。
 *
 * 通路：`roots/resolve` 翻译成 rootId + 相对路径 → `readFile` 取字节 → Skia 解码。
 * 与附件的 [RemoteImage] 共用同一份 LRU 缓存（key 加 `path|` 前缀区分），
 * 解码失败也缓存——反复重试一张坏图只是白烧请求。
 *
 * **不留空白**：加载中/失败都降级成一条可点的链接，跳文件页看。
 */
@Composable
fun PathImage(state: AppState, path: String, alt: String) {
    val colors = LocalAppColors.current
    val cacheKey = "path|$path"
    val bitmap by produceState<ImageBitmap?>(initialValue = imageCache[cacheKey], key1 = cacheKey) {
        if (value != null || failedIds[cacheKey] == true) return@produceState
        val loaded = runCatching {
            withContext(Dispatchers.IO) {
                val resolved = state.client().resolvePath(path)
                state.client().readFile(resolved.rootId, resolved.path).bytes
            }
        }.getOrNull()?.let { bytes ->
            runCatching { org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull()
        }
        if (loaded == null) failedIds[cacheKey] = true else imageCache[cacheKey] = loaded
        value = loaded
    }

    val image = bitmap
    if (image != null) {
        androidx.compose.foundation.layout.Column(Modifier.fillMaxWidth()) {
            Image(
                bitmap = image,
                contentDescription = alt.ifBlank { "会话图片" },
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 420.dp)
                    .clickable { ImagePreview.open(image) },
            )
        }
    } else {
        Text(
            if (failedIds[cacheKey] == true) "🖼 $alt（点开文件页查看）" else "🖼 $alt（读取中…）",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable { state.openFileByPath(path) },
        )
    }
}

/**
 * 全屏看图。
 *
 * 用一个进程级的小状态而不是弹窗：弹窗要处理焦点与返回键，
 * 而"点开大图看细节"这件事最轻的做法就是覆盖一层，再点一下关掉。
 */
object ImagePreview {
    internal var current by mutableStateOf<ImageBitmap?>(null)

    fun open(image: ImageBitmap) {
        current = image
    }

    fun close() {
        current = null
    }
}
