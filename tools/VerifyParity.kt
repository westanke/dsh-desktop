import io.github.westanke.dshdesktop.data.PermissionCatalog
import io.github.westanke.dshdesktop.data.PermissionOption
import io.github.westanke.dshdesktop.data.ReleaseAsset
import io.github.westanke.dshdesktop.data.UpdateChannel
import io.github.westanke.dshdesktop.data.UpdatePlatform
import io.github.westanke.dshdesktop.data.channelForLanguage
import io.github.westanke.dshdesktop.data.detectArch
import io.github.westanke.dshdesktop.data.detectPlatform
import io.github.westanke.dshdesktop.data.isNewerVersion
import io.github.westanke.dshdesktop.data.parseSha256Sums
import io.github.westanke.dshdesktop.data.pickUpdateAsset
import io.github.westanke.dshdesktop.data.resolveNewSessionPreset
import io.github.westanke.dshdesktop.data.runningLiteInstall
import io.github.westanke.dshdesktop.data.summarizeReleaseNotes

/**
 * 第二批对标（P2-1 schema 设置表单 / P2-2 检查更新 / P3 新建会话选档位）的纯函数用例。
 *
 * 为什么不用 JUnit：这个工程没有测试框架依赖（见 TECH-DEBT DEBT-001），
 * 而这几层都是纯函数，一个 main 函数就够了。跑法见 `tools/verify-parity.sh`。
 *
 * 规则：只收**会咬人的**边界——版本号逐段比较（`2.2.9 > 2.2.47` 是真实事故）、
 * 密钥判据、schema 摊不出来的降级、档位默认值语义。
 */
private var failed = 0
private var passed = 0

private fun check(name: String, ok: Boolean, detail: String = "") {
    if (ok) {
        passed++
    } else {
        failed++
        println("  ✗ $name${if (detail.isEmpty()) "" else " —— $detail"}")
    }
}

private fun catalog(vararg values: String, default: String = ""): PermissionCatalog = PermissionCatalog(
    options = values.map { PermissionOption(value = it, name = it) },
    defaultPreset = default,
)

// ── P3：新建会话的档位判定 ──────────────────────────────────────────────────

private fun verifyNewSessionPreset() {
    // 老桥 / 目录没到：什么都不切，也别画选择器
    check("目录为 null → 不切", resolveNewSessionPreset(null, "workspace-write") == null)
    // 只有一个档位：切来切去还是它
    check(
        "单档位目录 → 不切",
        resolveNewSessionPreset(catalog("read-only", default = "read-only"), "read-only") == null,
    )
    // 没选 = 部署默认，而默认本来就不必显式发命令
    check(
        "没选且默认在目录里 → 不切",
        resolveNewSessionPreset(
            catalog("read-only", "workspace-write", "danger-full-access", default = "workspace-write"),
            null,
        ) == null,
    )
    // 选了非默认：要切
    check(
        "选了非默认档 → 切过去",
        resolveNewSessionPreset(
            catalog("read-only", "workspace-write", "danger-full-access", default = "read-only"),
            "danger-full-access",
        ) == "danger-full-access",
    )
    // 选了默认档：等于没选，不多发一条命令
    check(
        "选了默认档 → 不切",
        resolveNewSessionPreset(
            catalog("read-only", "workspace-write", default = "read-only"),
            "read-only",
        ) == null,
    )
    // 选的值不在目录里（目录可能刚刷新过）：退回默认
    check(
        "选的值不在目录里 → 不切",
        resolveNewSessionPreset(
            catalog("read-only", "workspace-write", default = "read-only"),
            "bogus-preset",
        ) == null,
    )
    // 默认档不在目录里（服务端配错了）：不能拿一个不存在的档位去发命令
    check(
        "默认档不在目录里且没选 → 不切",
        resolveNewSessionPreset(catalog("read-only", "workspace-write", default = "ghost"), null) == null,
    )
    // 目录没给默认、用户选了一个：照切
    check(
        "无默认档 + 用户选了 → 切",
        resolveNewSessionPreset(catalog("read-only", "workspace-write"), "workspace-write") == "workspace-write",
    )
}


// ── P2-2：检查更新的纯函数 ────────────────────────────────────────────────

private fun asset(name: String, size: Long? = null) =
    ReleaseAsset(name = name, browserDownloadUrl = "https://example.invalid/$name", size = size)

private fun verifyUpdateChecker() {
    // 版本比较：逐段比数字。字符串比较会让 2.2.9 > 2.2.47（真实事故）
    check("1.0.10 新于 1.0.2", isNewerVersion("1.0.10", "1.0.2"))
    check("2.2.47 新于 2.2.9", isNewerVersion("2.2.47", "2.2.9"))
    check("1.0.2 不新于 1.0.2", !isNewerVersion("1.0.2", "1.0.2"))
    check("v 前缀与分隔符忽略", isNewerVersion("v1.0.3", "1.0.2"))
    check("缺段按 0 算：1.0.2 新于 1.0", isNewerVersion("1.0.2", "1.0"))
    check("1.0 不新于 1.0.0", !isNewerVersion("1.0", "1.0.0"))
    check("预发布后缀不参与比较（判相等）", !isNewerVersion("1.0.2-beta", "1.0.2"))
    check("空版本号不判新", !isNewerVersion("", "1.0.2") && !isNewerVersion("1.0.2", ""))

    // 渠道：按界面语言
    check("zh → Gitee", channelForLanguage("zh") == UpdateChannel.Gitee)
    check("zh-CN → Gitee", channelForLanguage("zh-CN") == UpdateChannel.Gitee)
    check("ZH 大写也认", channelForLanguage("ZH") == UpdateChannel.Gitee)
    check("en → GitHub", channelForLanguage("en") == UpdateChannel.GitHub)
    check("空语言 → GitHub", channelForLanguage("") == UpdateChannel.GitHub)

    // 校验清单：SHA256SUMS 与单文件 .sha256 同一格式
    val sums = parseSha256Sums(
        "# 注释行\n" +
            "AAAABBBBCCCCDDDD" + "EEEEFFFF00001111" + "2222333344445555" + "6666777788889999  dsh-desktop_1.0.2-1_amd64.deb\n" +
            "aaaabbbbccccdddd" + "eeeeffff00001111" + "2222333344445555" + "6666777788889999 *lite.zip\n" +
            "认不出的一行\n" +
            "\n",
    )
    check("清单解析出两条", sums.size == 2)
    check("哈希转小写", sums["dsh-desktop_1.0.2-1_amd64.deb"] ==
        ("aaaabbbbccccdddd" + "eeeeffff00001111" + "2222333344445555" + "6666777788889999"))
    check("二进制标记 * 被剥掉", sums.containsKey("lite.zip"))

    // 资产挑选：按后缀，不按下标
    val assets = listOf(
        asset("dsh-desktop-runtime-1.0.2-linux-x64.tar.xz"),
        asset("dsh-desktop-1.0.2.msi"),
        asset("dsh-desktop-1.0.2-windows-x64.zip"),
        asset("dsh-desktop-1.0.2-windows-x64-lite.zip"),
        asset("dsh-desktop_1.0.2-1_amd64.deb"),
        asset("dsh-desktop-lite_1.0.2-1_amd64.deb"),
        asset("dsh-desktop_1.0.2-1_arm64.deb"),
        asset("dsh-desktop-lite_1.0.2-1_arm64.deb"),
    )
    check(
        "Linux amd64 完整包",
        pickUpdateAsset(assets, UpdatePlatform.Linux, "amd64", lite = false)
            ?.name == "dsh-desktop_1.0.2-1_amd64.deb",
    )
    check(
        "Linux amd64 轻量包",
        pickUpdateAsset(assets, UpdatePlatform.Linux, "amd64", lite = true)
            ?.name == "dsh-desktop-lite_1.0.2-1_amd64.deb",
    )
    check(
        "Linux arm64 完整包",
        pickUpdateAsset(assets, UpdatePlatform.Linux, "arm64", lite = false)
            ?.name == "dsh-desktop_1.0.2-1_arm64.deb",
    )
    check(
        "Windows 优先 msi",
        pickUpdateAsset(assets, UpdatePlatform.Windows, "amd64", lite = false)
            ?.name == "dsh-desktop-1.0.2.msi",
    )
    check(
        "Windows 轻量取 -lite.zip",
        pickUpdateAsset(assets, UpdatePlatform.Windows, "amd64", lite = true)
            ?.name == "dsh-desktop-1.0.2-windows-x64-lite.zip",
    )
    check(
        "架构认不出来 → null（不猜）",
        pickUpdateAsset(assets, UpdatePlatform.Linux, "", lite = false) == null,
    )
    check(
        "macOS 没有产物 → null",
        pickUpdateAsset(assets, UpdatePlatform.Mac, "arm64", lite = false) == null,
    )

    // 更新说明压平
    val flat = summarizeReleaseNotes(
        "**1.0.2** 发布\n- 修了 [一个很长很长的问题](https://example.invalid/x)\n" +
            "![示意图](https://example.invalid/y.png)\n\n第二条：消息流对齐、自由复制、对标第一批",
        max = 40,
    )
    check("说明压成一行且无 Markdown 痕迹", !flat.contains("**") && !flat.contains("](") && !flat.contains("\n"))
    check("超长补省略号", flat.endsWith("…"))

    // 环境探测：本仓 CI 与开发机都是 x86_64 Linux，这里钉住预期
    check("本机平台识别为 Linux", detectPlatform() == UpdatePlatform.Linux)
    check("本机架构归一为 amd64", detectArch() == "amd64")
    check("full/lite 判定返回布尔（不抛）", runningLiteInstall() in setOf(true, false))
}

fun main() {
    println("▸ 第二批对标纯函数用例")
    verifyNewSessionPreset()
    verifyUpdateChecker()
    println()
    if (failed > 0) {
        println("✗ $failed 项失败（共 $passed 项通过）")
        kotlin.system.exitProcess(1)
    }
    println("✓ 全部通过（$passed 项）")
}
