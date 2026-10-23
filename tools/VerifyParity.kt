import io.github.westanke.dshdesktop.data.PermissionCatalog
import io.github.westanke.dshdesktop.data.PermissionOption
import io.github.westanke.dshdesktop.data.resolveNewSessionPreset

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

fun main() {
    println("▸ 第二批对标纯函数用例")
    verifyNewSessionPreset()
    println()
    if (failed > 0) {
        println("✗ $failed 项失败（共 $passed 项通过）")
        kotlin.system.exitProcess(1)
    }
    println("✓ 全部通过（$passed 项）")
}
