package io.github.westanke.dshdesktop.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import io.github.westanke.dshdesktop.AppState
import io.github.westanke.dshdesktop.ConnectionState
import io.github.westanke.dshdesktop.Nav
import kotlinx.coroutines.delay

@Composable
fun App(state: AppState, systemDark: Boolean) {
    val palette = resolvePalette(state.prefs.theme, systemDark)
    AppTheme(palette) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val needsPairing = state.connection == ConnectionState.Disconnected ||
                (state.connection == ConnectionState.Failed && state.token == null)

            Box(Modifier.fillMaxSize()) {
                if (needsPairing) {
                    ConnectScreen(state)
                } else {
                    MainShell(state)
                }
                ToastHost(state)
                ImagePreviewHost()
            }
        }
    }
}

/**
 * 主壳：一条侧栏 + 内容区。
 *
 * 侧栏放品牌、导航与会话列表（对齐官方 Web 端的单栏结构），内容区只放当前页。
 * 以前是 68dp 图标竖条 + 会话页自己再带一个左栏，两列并排时收窄后中间只剩一条缝，
 * 这也是改成单侧栏的直接原因。
 */
@Composable
private fun MainShell(state: AppState) {
    // 记住上次停在哪个页：下次开窗直接回到那儿，不用每次都从会话翻起
    LaunchedEffect(state.nav) {
        val name = when (state.nav) {
            Nav.Chat -> "chat"
            Nav.Files -> "files"
            Nav.Models -> "models"
            Nav.Plugins -> "plugins"
            Nav.Settings -> "settings"
        }
        if (state.prefs.lastNav != name) state.savePrefs { it.copy(lastNav = name) }
    }
    Row(Modifier.fillMaxSize()) {
        Sidebar(state)
        Box(
            Modifier
                .fillMaxHeight()
                .width(1.dp)
                .background(LocalAppColors.current.hairline),
        )
        Box(Modifier.weight(1f).fillMaxHeight()) {
            when (state.nav) {
                Nav.Chat -> ChatScreen(state)
                Nav.Files -> FilesScreen(state)
                Nav.Models -> ModelsScreen(state)
                Nav.Plugins -> PluginsScreen(state)
                Nav.Settings -> SettingsScreen(state)
            }
        }
    }
}

@Composable
private fun ToastHost(state: AppState) {
    val message = state.toast
    if (message != null) {
        LaunchedEffect(message) {
            delay(2600)
            state.dismissToast()
        }
    }
    if (message == null) return
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Surface(
            color = MaterialTheme.colorScheme.inverseSurface,
            shape = RoundedCornerShape(Tokens.radiusMd),
            modifier = Modifier.padding(bottom = 28.dp),
        ) {
            Text(
                message,
                color = MaterialTheme.colorScheme.inverseOnSurface,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}

/**
 * 内容区统一页头（文件页、设置页用；会话页有自己的面包屑头）。
 *
 * 只画一行标题 + 右侧动作，底部一条 hairline。不加背景色：内容区已经是 surface，
 * 再叠一层会让"卡片"和"页头"抢同一层。
 */
@Composable
fun PageHeader(title: String, subtitle: String? = null, actions: @Composable () -> Unit = {}) {
    val colors = LocalAppColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textTertiary,
                    maxLines = 1,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            actions()
        }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(colors.hairline),
    )
}


/**
 * 大图预览层。
 *
 * 放在壳的最外层而不是会话页里：以后别的页面（文件页的图片预览）也能复用，
 * 而且覆盖层不该被页面的裁剪边界切掉。
 */
@Composable
private fun ImagePreviewHost() {
    val image = ImagePreview.current ?: return
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.78f))
            .clickable { ImagePreview.close() },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            bitmap = image,
            contentDescription = "图片预览",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().padding(40.dp),
        )
    }
}
