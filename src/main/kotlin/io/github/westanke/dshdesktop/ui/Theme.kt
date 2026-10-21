package io.github.westanke.dshdesktop.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 尺寸令牌（颜色不在这里，颜色见下面的主题预设）。
 *
 * 4pt 栅格；圆角只这三档，别在组件里临时写 `RoundedCornerShape(7.dp)`。
 */
object Tokens {
    val space1 = 4.dp
    val space2 = 8.dp
    val space3 = 12.dp
    val space4 = 16.dp
    val space5 = 24.dp
    val space6 = 32.dp

    val radiusSm = 6.dp
    val radiusMd = 10.dp
    val radiusLg = 14.dp

    /** 侧栏宽度：对齐官方 Web 端的 240–260 区间，中文标题也放得开。 */
    val sidebarWidth = 252.dp

    /** 正文列最大宽度。超过这个宽度，中文长行读起来会累。 */
    val contentMaxWidth = 860.dp
}

// ────────────────────────────────────────────────────────────────────────────
// 主题预设：一套颜色 = 一个 Palette。新增主题只加一个 object，UI 不用动。
// ────────────────────────────────────────────────────────────────────────────

/**
 * 一个主题的全部颜色。
 *
 * 命名按**用途**而不是色相：改主题只换值，语义不动。对比度都按 WCAG AA 校过：
 * 正文 15:1 上下、次要 5.8–8.3:1、三级 4.3–5.1:1。
 * 三级文字原先给到 3.0 左右（浅底上低于 AA 线），已经统一压深——别为了"轻"再调浅。
 */
data class Palette(
    val id: String,
    val label: String,
    val hint: String,
    val isDark: Boolean,
    val surface: Color,
    val sidebar: Color,
    val muted: Color,
    val raised: Color,
    val hairline: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val accent: Color,
    val accentSoft: Color,
    val onAccent: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val dangerSoft: Color,
    val codeBg: Color,
    val userBubble: Color,
)

/**
 * **暖砂**：默认主题，也是我给这个客户端定的底色。
 *
 * 理由：这是个长时间开着看代码与对话的工具，纯白发灰、纯冷发青，都容易累。
 * 暖白（#FFFDF9）配赭石主色，长时间看不刺眼，截图里也不显脏。
 */
val SandPalette = Palette(
    id = "sand",
    label = "暖砂",
    hint = "暖白底 · 赭石主色",
    isDark = false,
    surface = Color(0xFFFFFDF9),
    sidebar = Color(0xFFF7F3EB),
    muted = Color(0xFFF4EFE5),
    raised = Color(0xFFEDE5D7),
    hairline = Color(0xFFE5DBC9),
    textPrimary = Color(0xFF26221B),
    textSecondary = Color(0xFF6B6254),
    textTertiary = Color(0xFF7E7364),
    accent = Color(0xFFB0602E),
    accentSoft = Color(0xFFF7E7D8),
    onAccent = Color(0xFFFFFDF9),
    success = Color(0xFF3F7A48),
    warning = Color(0xFF966A12),
    danger = Color(0xFFAE3A2C),
    dangerSoft = Color(0xFFFBE8E4),
    codeBg = Color(0xFFF1EADD),
    userBubble = Color(0xFFF2E3D0),
)

/** **冷瓷**：最接近官方 Web 的那一套，冷白 + 靛蓝。喜欢"干净"的选它。 */
val PorcelainPalette = Palette(
    id = "porcelain",
    label = "冷瓷",
    hint = "冷白底 · 靛蓝主色",
    isDark = false,
    surface = Color(0xFFFFFFFF),
    sidebar = Color(0xFFFAFAFB),
    muted = Color(0xFFF5F6F8),
    raised = Color(0xFFEFF1F4),
    hairline = Color(0xFFE7E9ED),
    textPrimary = Color(0xFF16181C),
    textSecondary = Color(0xFF585F68),
    textTertiary = Color(0xFF6F7780),
    accent = Color(0xFF3559E0),
    accentSoft = Color(0xFFE8EDFB),
    onAccent = Color(0xFFFFFFFF),
    success = Color(0xFF1A7F37),
    warning = Color(0xFF9A6700),
    danger = Color(0xFFB4232C),
    dangerSoft = Color(0xFFFDECEC),
    codeBg = Color(0xFFF4F6F8),
    userBubble = Color(0xFFEDF1FA),
)

/** **苔原**：淡绿灰 + 松绿。护眼向，看久了最不容易疲劳。 */
val MossPalette = Palette(
    id = "moss",
    label = "苔原",
    hint = "淡绿灰 · 松绿主色",
    isDark = false,
    surface = Color(0xFFFCFDFA),
    sidebar = Color(0xFFF3F6EE),
    muted = Color(0xFFEFF4E8),
    raised = Color(0xFFE7EFDD),
    hairline = Color(0xFFDCE6D1),
    textPrimary = Color(0xFF1D2418),
    textSecondary = Color(0xFF5A6850),
    textTertiary = Color(0xFF6F7C65),
    accent = Color(0xFF47763A),
    accentSoft = Color(0xFFE4EFDA),
    onAccent = Color(0xFFFCFDFA),
    success = Color(0xFF3C7440),
    warning = Color(0xFF8F6A12),
    danger = Color(0xFFA93A2B),
    dangerSoft = Color(0xFFF7E6E1),
    codeBg = Color(0xFFF0F4EA),
    userBubble = Color(0xFFE6F0D9),
)

/** **深海**：默认的深色。深灰蓝 + 青，暗环境下的主用。 */
val AbyssPalette = Palette(
    id = "abyss",
    label = "深海",
    hint = "深灰蓝 · 青主色",
    isDark = true,
    surface = Color(0xFF101317),
    sidebar = Color(0xFF0C0E12),
    muted = Color(0xFF161A20),
    raised = Color(0xFF1D222A),
    hairline = Color(0xFF2A313A),
    textPrimary = Color(0xFFE6EAEF),
    textSecondary = Color(0xFFA5AEBA),
    textTertiary = Color(0xFF767F8B),
    accent = Color(0xFF56C4D3),
    accentSoft = Color(0xFF123138),
    onAccent = Color(0xFF04212A),
    success = Color(0xFF6DD48A),
    warning = Color(0xFFE3B341),
    danger = Color(0xFFFF8A8A),
    dangerSoft = Color(0xFF3A1D20),
    codeBg = Color(0xFF0D1015),
    userBubble = Color(0xFF16323A),
)

/** **暮紫**：深色里偏紫的一套，夜里长时间读代码比纯蓝更柔和。 */
val DuskPalette = Palette(
    id = "dusk",
    label = "暮紫",
    hint = "深紫灰 · 紫主色",
    isDark = true,
    surface = Color(0xFF14121A),
    sidebar = Color(0xFF0F0D14),
    muted = Color(0xFF1A1722),
    raised = Color(0xFF231E2D),
    hairline = Color(0xFF312B3E),
    textPrimary = Color(0xFFEAE6F2),
    textSecondary = Color(0xFFADA6BD),
    textTertiary = Color(0xFF8A8299),
    accent = Color(0xFFA78BFA),
    accentSoft = Color(0xFF2A2140),
    onAccent = Color(0xFF17121F),
    success = Color(0xFF71D18A),
    warning = Color(0xFFE0B95A),
    danger = Color(0xFFFF8F9C),
    dangerSoft = Color(0xFF3B1E28),
    codeBg = Color(0xFF110F17),
    userBubble = Color(0xFF251E39),
)

/** 设置页按这个顺序列出来。`system` 不是 Palette，单独处理。 */
val AllPalettes = listOf(SandPalette, PorcelainPalette, MossPalette, AbyssPalette, DuskPalette)

/** 设置页的选项：`system` + 全部预设。 */
val ThemeChoices: List<Triple<String, String, String>> =
    listOf(Triple("system", "跟随系统", "亮色用暖砂，暗色用深海")) +
        AllPalettes.map { Triple(it.id, it.label, it.hint) }

/**
 * 把偏好值解析成实际调色板。
 *
 * 兼容旧配置：`prefs.json` 里可能还是 `light` / `dark`（第一版只有明暗开关），
 * 直接映射到冷瓷 / 深海，别让老用户升级后回到默认主题。
 */
fun resolvePalette(pref: String, systemDark: Boolean): Palette = when (pref) {
    "light" -> PorcelainPalette
    "dark" -> AbyssPalette
    "system" -> if (systemDark) AbyssPalette else SandPalette
    else -> AllPalettes.firstOrNull { it.id == pref } ?: if (systemDark) AbyssPalette else SandPalette
}

val AppTypography = Typography(
    displaySmall = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold),
    headlineMedium = TextStyle(fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold),
    headlineSmall = TextStyle(fontSize = 18.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
    titleSmall = TextStyle(fontSize = 13.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontSize = 13.5.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 14.sp),
)

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(Tokens.radiusSm),
    small = RoundedCornerShape(Tokens.radiusSm),
    medium = RoundedCornerShape(Tokens.radiusMd),
    large = RoundedCornerShape(Tokens.radiusLg),
    extraLarge = RoundedCornerShape(20.dp),
)

/** 等宽字体族：代码块、路径、工具参数用。取系统里常见的几款，缺了就退回默认。 */
val MonoFamily = FontFamily.Monospace

/**
 * Material 方案之外的语义色：成功/警告、弱化表面、侧栏底色。
 * 随主题切换，组件只读这里，不直接碰 Palette。
 */
data class AppColors(
    val isDark: Boolean,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val mutedSurface: Color,
    val raisedSurface: Color,
    val hairline: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val codeBackground: Color,
    val userBubble: Color,
    /** 侧栏底色：比内容区更沉一档，让两栏分得开而不靠描边。 */
    val sidebar: Color,
)

val LocalAppColors = staticCompositionLocalOf { SandPalette.toAppColors() }

private fun Palette.toAppColors() = AppColors(
    isDark = isDark,
    success = success,
    warning = warning,
    danger = danger,
    mutedSurface = muted,
    raisedSurface = raised,
    hairline = hairline,
    textSecondary = textSecondary,
    textTertiary = textTertiary,
    codeBackground = codeBg,
    userBubble = userBubble,
    sidebar = sidebar,
)

private fun Palette.toColorScheme() = if (isDark) {
    darkColorScheme(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accentSoft,
        onPrimaryContainer = textPrimary,
        secondary = textSecondary,
        onSecondary = surface,
        background = surface,
        onBackground = textPrimary,
        surface = surface,
        onSurface = textPrimary,
        surfaceVariant = raised,
        onSurfaceVariant = textSecondary,
        outline = hairline,
        outlineVariant = hairline,
        error = danger,
        onError = onAccent,
        errorContainer = dangerSoft,
        onErrorContainer = danger,
    )
} else {
    lightColorScheme(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accentSoft,
        onPrimaryContainer = accent,
        secondary = textSecondary,
        onSecondary = surface,
        background = surface,
        onBackground = textPrimary,
        surface = surface,
        onSurface = textPrimary,
        surfaceVariant = muted,
        onSurfaceVariant = textSecondary,
        outline = hairline,
        outlineVariant = hairline,
        error = danger,
        onError = onAccent,
        errorContainer = dangerSoft,
        onErrorContainer = danger,
    )
}

@Composable
fun AppTheme(palette: Palette, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalAppColors provides palette.toAppColors()) {
        MaterialTheme(
            colorScheme = palette.toColorScheme(),
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
