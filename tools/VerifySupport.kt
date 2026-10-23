/**
 * 纯函数验证套件的公共断言库（第四批：路线 A，零新依赖）。
 *
 * 为什么要有这个文件：这个工程刻意不用 Gradle（本机 4GB 地址空间会打死它），
 * `lib/` 里也没有 junit / kotlin-test。但 Kotlin 纯函数的回归需要**同语言、
 * 可调试、结构化**的断言载体 —— 于是每个 `tools/Verify*.kt` 都是一个
 * `main` 函数 + 手写断言，由 `tools/verify-*.sh` 用已入库的 K2JVMCompiler
 * 直编直跑。鸿蒙那 1013 条 node 断言本质也是这种"无框架断言"。
 *
 * 原先四个套件各自带一份断言助手（措辞还不一样），新增套件要么再抄一遍、
 * 要么漂移。这份公共库把它们统一：新增纯函数回归时，写一个
 * `tools/VerifyXxx.kt`（`main` 里调 [vcheck] / [vexpect] / [vsection] /
 * [vsummary]），再加一行到 `tools/test.sh` 的套件清单，就完事。
 *
 * 两个断言入口的分工：**全绿的套件只打印失败**（[vcheck]，门禁里不刷屏），
 * **需要看覆盖了什么的套件每条都打印**（[vexpect]）。
 *
 * 纪律（与 AGENTS.md 一致）：data 层纯函数**禁 import Compose**；
 * 平台 IO（网络/加密/文件）走接口，测试里换替身。测试文件同样守 800 行预算。
 */

/** 已通过的断言数。套件结束时由 [vsummary] 打印。 */
internal var verifyPassed = 0
    private set

/** 失败的断言数。 */
internal var verifyFailed = 0
    private set

/**
 * 一条断言（简洁式）：通过不打印，失败才打印。
 *
 * 与 stdlib 的 `check()` 刻意不同：那个失败就抛异常中断，
 * 而回归测试要的是一次看全**哪些**用例红了。
 */
fun vcheck(name: String, ok: Boolean, detail: String = "") {
    if (ok) {
        verifyPassed++
    } else {
        verifyFailed++
        println("  ✗ $name" + if (detail.isEmpty()) "" else " — $detail")
    }
}

/** 一条断言（明细式）：通过也打印，适合要看清"覆盖了哪些形状"的套件。 */
fun vexpect(name: String, ok: Boolean, detail: String = "") {
    if (ok) {
        verifyPassed++
        println("  ✓ $name")
    } else {
        verifyFailed++
        println("  ✗ $name" + if (detail.isEmpty()) "" else " — $detail")
    }
}

/** 分节标题，让长输出的层次看得见。 */
fun vsection(title: String) {
    println("▸ $title")
}

/**
 * 收尾：打印统计，返回失败数（调用方决定退出码）。
 *
 * 各套件的退出码语义不同（0 通过；verify-markdown 用 4 表示内置用例失败、
 * 3 表示输入不可用；verify-history 用 2 表示回归），所以退出码留给调用方，
 * 这里只负责把人话说明白。
 */
fun vsummary(): Int {
    println()
    if (verifyFailed == 0) {
        println("✓ 全部通过（$verifyPassed 项）")
        return 0
    }
    println("✗ $verifyFailed 项失败（共 $verifyPassed 项通过）")
    return verifyFailed
}
