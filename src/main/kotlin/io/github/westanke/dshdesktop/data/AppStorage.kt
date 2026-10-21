package io.github.westanke.dshdesktop.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.PosixFilePermission
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

internal val appJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    prettyPrint = true
}

/** 连接配置。token 单独加密落在 `token.enc`，不写进这个文件。 */
@Serializable
data class ConnectionProfile(
    val endpoint: String = "",
    val deviceName: String = defaultDeviceName(),
    val deviceId: String = "",
    val scopes: List<String> = emptyList(),
    val rootIds: List<String> = emptyList(),
    val savedAt: Long = 0,
) {
    val isConfigured: Boolean get() = endpoint.isNotBlank()
}

@Serializable
data class UiPrefs(
    /**
     * 主题：`system` 或预设 id（sand / porcelain / moss / abyss / dusk）。
     * 旧版存的是 `light` / `dark`，读取时由 `resolvePalette()` 映射，不需要迁移脚本。
     */
    val theme: String = "sand",
    /** zh | en */
    val language: String = "zh",
    val lastSessionId: String? = null,
    val lastNav: String = "chat",
)

fun defaultDeviceName(): String {
    val os = System.getProperty("os.name").orEmpty()
    val host = runCatching { java.net.InetAddress.getLocalHost().hostName }.getOrNull()
    val base = when {
        os.contains("Windows", ignoreCase = true) -> "Windows"
        os.contains("Mac", ignoreCase = true) -> "macOS"
        else -> "UOS"
    }
    return if (host.isNullOrBlank()) "$base 桌面端" else "$base · $host"
}

/**
 * 本地配置存储。
 *
 * 目录遵守各平台约定：Windows 用 `%APPDATA%\dsh-desktop`，其余用
 * `$XDG_CONFIG_HOME/dsh-desktop`（缺省 `~/.config/dsh-desktop`）。
 *
 * 设备令牌用 AES-GCM 加密后落盘，密钥是同目录下 0600 的 `device.key`。
 * **它不是硬件密钥库**：能读到该用户主目录的程序同样能解得开。它的作用是
 * 让令牌不以明文出现在配置文件和备份里，挡住"随手一看"。对更高威胁模型，
 * 应把端点放到 HTTPS/Tailscale 后面并缩短令牌有效期。
 */
object AppStorage {

    private const val KEY_FILE = "device.key"
    private const val TOKEN_FILE = "token.enc"
    private const val PROFILE_FILE = "profile.json"
    private const val PREFS_FILE = "prefs.json"

    fun root(): Path {
        val os = System.getProperty("os.name").orEmpty()
        val base: String = if (os.contains("Windows", ignoreCase = true)) {
            System.getenv("APPDATA") ?: (System.getProperty("user.home") + "\\AppData\\Roaming")
        } else {
            System.getenv("XDG_CONFIG_HOME")?.takeIf { it.isNotBlank() }
                ?: (System.getProperty("user.home") + "/.config")
        }
        val dir = Paths.get(base, "dsh-desktop")
        Files.createDirectories(dir)
        return dir
    }

    /**
     * 缓存目录（可以整份删掉而不影响使用的那些）。
     *
     * 和 [root] 分开的理由：这里会落**下载下来准备交给系统程序打开的文件**，
     * 一份 PDF 可能几十兆。配置目录是用户会备份、会同步的地方，不该塞这种东西。
     * Windows 用 `%LOCALAPPDATA%`（不参与漫游），其余用 `$XDG_CACHE_HOME`。
     */
    fun cacheRoot(): Path {
        val os = System.getProperty("os.name").orEmpty()
        val base: String = if (os.contains("Windows", ignoreCase = true)) {
            System.getenv("LOCALAPPDATA")
                ?: (System.getProperty("user.home") + "\\AppData\\Local")
        } else {
            System.getenv("XDG_CACHE_HOME")?.takeIf { it.isNotBlank() }
                ?: (System.getProperty("user.home") + "/.cache")
        }
        val dir = Paths.get(base, "dsh-desktop")
        Files.createDirectories(dir)
        return dir
    }

    fun loadProfile(): ConnectionProfile {
        val file = root().resolve(PROFILE_FILE)
        if (!Files.exists(file)) return ConnectionProfile()
        return runCatching {
            appJson.decodeFromString(ConnectionProfile.serializer(), Files.readString(file))
        }.getOrElse { ConnectionProfile() }
    }

    /** 读回设备令牌；文件缺失或解密失败返回 null（调用方应回到配对流程）。 */
    fun loadToken(): String? {
        val file = root().resolve(TOKEN_FILE)
        if (!Files.exists(file)) return null
        return runCatching {
            val raw = Base64.getDecoder().decode(Files.readString(file).trim())
            if (raw.size <= 12) return null
            val nonce = raw.copyOfRange(0, 12)
            val body = raw.copyOfRange(12, raw.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, loadOrCreateKey(), GCMParameterSpec(128, nonce))
            String(cipher.doFinal(body), Charsets.UTF_8)
        }.getOrNull()
    }

    fun save(profile: ConnectionProfile, token: String?) {
        val dir = root()
        Files.writeString(dir.resolve(PROFILE_FILE), appJson.encodeToString(ConnectionProfile.serializer(), profile))
        restrict(dir.resolve(PROFILE_FILE))
        if (token == null) {
            runCatching { Files.deleteIfExists(dir.resolve(TOKEN_FILE)) }
            return
        }
        val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, loadOrCreateKey(), GCMParameterSpec(128, nonce))
        val encrypted = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        val combined = nonce + encrypted
        val file = dir.resolve(TOKEN_FILE)
        Files.writeString(file, Base64.getEncoder().encodeToString(combined))
        restrict(file)
    }

    fun clearToken() {
        runCatching { Files.deleteIfExists(root().resolve(TOKEN_FILE)) }
    }

    fun loadPrefs(): UiPrefs {
        val file = root().resolve(PREFS_FILE)
        if (!Files.exists(file)) return UiPrefs()
        return runCatching {
            appJson.decodeFromString(UiPrefs.serializer(), Files.readString(file))
        }.getOrElse { UiPrefs() }
    }

    fun savePrefs(prefs: UiPrefs) {
        runCatching {
            val file = root().resolve(PREFS_FILE)
            Files.writeString(file, appJson.encodeToString(UiPrefs.serializer(), prefs))
            restrict(file)
        }
    }

    private fun loadOrCreateKey(): SecretKeySpec {
        val file = root().resolve(KEY_FILE)
        if (Files.exists(file)) {
            val bytes = Base64.getDecoder().decode(Files.readString(file).trim())
            if (bytes.size == 32) return SecretKeySpec(bytes, "AES")
        }
        val fresh = ByteArray(32).also { SecureRandom().nextBytes(it) }
        Files.writeString(file, Base64.getEncoder().encodeToString(fresh))
        restrict(file)
        return SecretKeySpec(fresh, "AES")
    }

    /** Linux/macOS 上是 0600；Windows 没有 POSIX 权限位，静默跳过（依赖用户目录自身的 ACL）。 */
    private fun restrict(file: Path) {
        runCatching {
            Files.setPosixFilePermissions(
                file,
                setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
            )
        }
    }
}
