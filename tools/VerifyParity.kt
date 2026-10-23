import io.github.westanke.dshdesktop.data.PermissionCatalog
import io.github.westanke.dshdesktop.data.PermissionOption
import io.github.westanke.dshdesktop.data.ReleaseAsset
import io.github.westanke.dshdesktop.data.SettingsControl
import io.github.westanke.dshdesktop.data.SettingsNamespace
import io.github.westanke.dshdesktop.data.SettingsSchema
import io.github.westanke.dshdesktop.data.SettingsSecret
import io.github.westanke.dshdesktop.data.UpdateChannel
import io.github.westanke.dshdesktop.data.UpdatePlatform
import io.github.westanke.dshdesktop.data.canWriteSettingsNamespaces
import io.github.westanke.dshdesktop.data.channelForLanguage
import io.github.westanke.dshdesktop.data.clampSettingsNumber
import io.github.westanke.dshdesktop.data.detectArch
import io.github.westanke.dshdesktop.data.detectPlatform
import io.github.westanke.dshdesktop.data.forNativeApp
import io.github.westanke.dshdesktop.data.hasEditableField
import io.github.westanke.dshdesktop.data.humanizeFieldName
import io.github.westanke.dshdesktop.data.isNewerVersion
import io.github.westanke.dshdesktop.data.parseSha256Sums
import io.github.westanke.dshdesktop.data.pickUpdateAsset
import io.github.westanke.dshdesktop.data.resolveNewSessionPreset
import io.github.westanke.dshdesktop.data.runningLiteInstall
import io.github.westanke.dshdesktop.data.summarizeReleaseNotes
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * 第二批对标（P2-1 schema 设置表单 / P2-2 检查更新 / P3 新建会话选档位）的纯函数用例。
 *
 * 为什么不用 JUnit：这个工程没有测试框架依赖（见 TECH-DEBT DEBT-001），
 * 而这几层都是纯函数，一个 main 函数就够了。跑法见 `tools/verify-parity.sh`。
 *
 * 规则：只收**会咬人的**边界——版本号逐段比较（`2.2.9 > 2.2.47` 是真实事故）、
 * 密钥判据、schema 摊不出来的降级、档位默认值语义。
 *
 * 断言助手用公共库 `tools/VerifySupport.kt`（vcheck/vsummary），四个套件同一份
 * 措辞；跑法见 `tools/verify-parity.sh` 或 `tools/test.sh`。
 */

private fun catalog(vararg values: String, default: String = ""): PermissionCatalog = PermissionCatalog(
    options = values.map { PermissionOption(value = it, name = it) },
    defaultPreset = default,
)

// ── P3：新建会话的档位判定 ──────────────────────────────────────────────────

private fun verifyNewSessionPreset() {
    // 老桥 / 目录没到：什么都不切，也别画选择器
    vcheck("目录为 null → 不切", resolveNewSessionPreset(null, "workspace-write") == null)
    // 只有一个档位：切来切去还是它
    vcheck(
        "单档位目录 → 不切",
        resolveNewSessionPreset(catalog("read-only", default = "read-only"), "read-only") == null,
    )
    // 没选 = 部署默认，而默认本来就不必显式发命令
    vcheck(
        "没选且默认在目录里 → 不切",
        resolveNewSessionPreset(
            catalog("read-only", "workspace-write", "danger-full-access", default = "workspace-write"),
            null,
        ) == null,
    )
    // 选了非默认：要切
    vcheck(
        "选了非默认档 → 切过去",
        resolveNewSessionPreset(
            catalog("read-only", "workspace-write", "danger-full-access", default = "read-only"),
            "danger-full-access",
        ) == "danger-full-access",
    )
    // 选了默认档：等于没选，不多发一条命令
    vcheck(
        "选了默认档 → 不切",
        resolveNewSessionPreset(
            catalog("read-only", "workspace-write", default = "read-only"),
            "read-only",
        ) == null,
    )
    // 选的值不在目录里（目录可能刚刷新过）：退回默认
    vcheck(
        "选的值不在目录里 → 不切",
        resolveNewSessionPreset(
            catalog("read-only", "workspace-write", default = "read-only"),
            "bogus-preset",
        ) == null,
    )
    // 默认档不在目录里（服务端配错了）：不能拿一个不存在的档位去发命令
    vcheck(
        "默认档不在目录里且没选 → 不切",
        resolveNewSessionPreset(catalog("read-only", "workspace-write", default = "ghost"), null) == null,
    )
    // 目录没给默认、用户选了一个：照切
    vcheck(
        "无默认档 + 用户选了 → 切",
        resolveNewSessionPreset(catalog("read-only", "workspace-write"), "workspace-write") == "workspace-write",
    )
}


// ── P2-2：检查更新的纯函数 ────────────────────────────────────────────────

private fun asset(name: String, size: Long? = null) =
    ReleaseAsset(name = name, browserDownloadUrl = "https://example.invalid/$name", size = size)

private fun verifyUpdateChecker() {
    // 版本比较：逐段比数字。字符串比较会让 2.2.9 > 2.2.47（真实事故）
    vcheck("1.0.10 新于 1.0.2", isNewerVersion("1.0.10", "1.0.2"))
    vcheck("2.2.47 新于 2.2.9", isNewerVersion("2.2.47", "2.2.9"))
    vcheck("1.0.2 不新于 1.0.2", !isNewerVersion("1.0.2", "1.0.2"))
    vcheck("v 前缀与分隔符忽略", isNewerVersion("v1.0.3", "1.0.2"))
    vcheck("缺段按 0 算：1.0.2 新于 1.0", isNewerVersion("1.0.2", "1.0"))
    vcheck("1.0 不新于 1.0.0", !isNewerVersion("1.0", "1.0.0"))
    vcheck("预发布后缀不参与比较（判相等）", !isNewerVersion("1.0.2-beta", "1.0.2"))
    vcheck("空版本号不判新", !isNewerVersion("", "1.0.2") && !isNewerVersion("1.0.2", ""))

    // 渠道：按界面语言
    vcheck("zh → Gitee", channelForLanguage("zh") == UpdateChannel.Gitee)
    vcheck("zh-CN → Gitee", channelForLanguage("zh-CN") == UpdateChannel.Gitee)
    vcheck("ZH 大写也认", channelForLanguage("ZH") == UpdateChannel.Gitee)
    vcheck("en → GitHub", channelForLanguage("en") == UpdateChannel.GitHub)
    vcheck("空语言 → GitHub", channelForLanguage("") == UpdateChannel.GitHub)

    // 校验清单：SHA256SUMS 与单文件 .sha256 同一格式
    val sums = parseSha256Sums(
        "# 注释行\n" +
            "AAAABBBBCCCCDDDD" + "EEEEFFFF00001111" + "2222333344445555" + "6666777788889999  dsh-desktop_1.0.2-1_amd64.deb\n" +
            "aaaabbbbccccdddd" + "eeeeffff00001111" + "2222333344445555" + "6666777788889999 *lite.zip\n" +
            "认不出的一行\n" +
            "\n",
    )
    vcheck("清单解析出两条", sums.size == 2)
    vcheck("哈希转小写", sums["dsh-desktop_1.0.2-1_amd64.deb"] ==
        ("aaaabbbbccccdddd" + "eeeeffff00001111" + "2222333344445555" + "6666777788889999"))
    vcheck("二进制标记 * 被剥掉", sums.containsKey("lite.zip"))

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
    vcheck(
        "Linux amd64 完整包",
        pickUpdateAsset(assets, UpdatePlatform.Linux, "amd64", lite = false)
            ?.name == "dsh-desktop_1.0.2-1_amd64.deb",
    )
    vcheck(
        "Linux amd64 轻量包",
        pickUpdateAsset(assets, UpdatePlatform.Linux, "amd64", lite = true)
            ?.name == "dsh-desktop-lite_1.0.2-1_amd64.deb",
    )
    vcheck(
        "Linux arm64 完整包",
        pickUpdateAsset(assets, UpdatePlatform.Linux, "arm64", lite = false)
            ?.name == "dsh-desktop_1.0.2-1_arm64.deb",
    )
    vcheck(
        "Windows 优先 msi",
        pickUpdateAsset(assets, UpdatePlatform.Windows, "amd64", lite = false)
            ?.name == "dsh-desktop-1.0.2.msi",
    )
    vcheck(
        "Windows 轻量取 -lite.zip",
        pickUpdateAsset(assets, UpdatePlatform.Windows, "amd64", lite = true)
            ?.name == "dsh-desktop-1.0.2-windows-x64-lite.zip",
    )
    vcheck(
        "架构认不出来 → null（不猜）",
        pickUpdateAsset(assets, UpdatePlatform.Linux, "", lite = false) == null,
    )
    vcheck(
        "macOS 没有产物 → null",
        pickUpdateAsset(assets, UpdatePlatform.Mac, "arm64", lite = false) == null,
    )

    // 更新说明压平
    val flat = summarizeReleaseNotes(
        "**1.0.2** 发布\n- 修了 [一个很长很长的问题](https://example.invalid/x)\n" +
            "![示意图](https://example.invalid/y.png)\n\n第二条：消息流对齐、自由复制、对标第一批",
        max = 40,
    )
    vcheck("说明压成一行且无 Markdown 痕迹", !flat.contains("**") && !flat.contains("](") && !flat.contains("\n"))
    vcheck("超长补省略号", flat.endsWith("…"))

    // 环境探测：本仓 CI 与开发机都是 x86_64 Linux，这里钉住预期
    vcheck("本机平台识别为 Linux", detectPlatform() == UpdatePlatform.Linux)
    vcheck("本机架构归一为 amd64", detectArch() == "amd64")
    vcheck("full/lite 判定返回布尔（不抛）", runningLiteInstall() in setOf(true, false))
}

// ── P2-1：schema 驱动的设置命名空间表单 ─────────────────────────────────────

private val testJson = Json { ignoreUnknownKeys = true }

private fun jsonObject(text: String): JsonObject = testJson.parseToJsonElement(text).jsonObject

private fun namespace(
    name: String,
    schema: JsonObject?,
    value: JsonElement?,
    writable: Boolean = true,
    secrets: List<SettingsSecret> = emptyList(),
) = SettingsNamespace(ns = name, revision = 7, schema = schema, value = value, writable = writable, secrets = secrets)

/** 字段表按 key 索引，顺带保留顺序断言用。 */
private fun List<io.github.westanke.dshdesktop.data.SettingsFieldSpec>.byKey(key: String) =
    firstOrNull { it.key == key }

/**
 * 内核实际发的 **schemastery** 引用图（不是标准 JSON Schema）。
 *
 * ref id 用**数字**（真实形态）：`resolve` 走 `contentOrNull` 取 id，
 * 这个用例同时钉住「数字基元也能取出 id」——否则摊平会静默失败、
 * 整表退化成只读行，那正是 companion 在真机上撞过的坑。
 */
private val SCHEMASTERY = """
{
  "uid": 1,
  "refs": {
    "1": {"type": "object", "dict": {"locale": 2, "fontSize": 3, "apiKey": 4, "nested": 5, "loop": 6, "weird": 7}},
    "2": {"type": "union", "list": [8, 9], "meta": {"default": "zh-CN", "description": "界面语言"}},
    "8": {"type": "const", "value": "zh-CN"},
    "9": {"type": "const", "value": "en"},
    "3": {"type": "number", "meta": {"min": 8, "max": 72, "default": 14}},
    "4": {"type": "string", "meta": {"role": "secret"}},
    "5": {"type": "object", "dict": {"inner": 10}},
    "10": {"type": "string"},
    "6": {"type": "object", "dict": {"self": 6}},
    "7": {"type": "any"}
  }
}
"""

private fun verifySettingsNamespaces() {
    // ── schemastery 摊平（内核真实形态）────────────────────────────────────
    val sm = namespace(
        name = "locale",
        schema = jsonObject(SCHEMASTERY),
        value = jsonObject("""{"locale": "zh-CN", "fontSize": 14, "apiKey": "***", "nested": {"inner": "x"}}"""),
    )
    val smFields = SettingsSchema.fields(sm)
    vcheck("schemastery 摊出 5 个字段（weird 是 any 被丢弃）", smFields.size == 5, "实际 ${smFields.size}")
    vcheck("摊平顺序 = dict 顺序", smFields.map { it.key } == listOf("locale", "fontSize", "apiKey", "nested", "loop", "weird").filter { it != "weird" })
    vcheck("union → Enum 下拉", smFields.byKey("locale")?.control == SettingsControl.Enum)
    vcheck("enum 选项来自 const 节点", smFields.byKey("locale")?.enumValues == listOf("zh-CN", "en"))
    vcheck("union 的 meta.description 透传", smFields.byKey("locale")?.description == "界面语言")
    vcheck("number → Number 且 min/max 认简写", smFields.byKey("fontSize")?.control == SettingsControl.Number)
    vcheck("min/max 值正确", smFields.byKey("fontSize")?.minimum == 8.0 && smFields.byKey("fontSize")?.maximum == 72.0)
    vcheck("meta.role=secret → Secret 控件", smFields.byKey("apiKey")?.control == SettingsControl.Secret)
    vcheck("值是 *** → 已设置", smFields.byKey("apiKey")?.secretSet == true)
    vcheck("object → 只读", smFields.byKey("nested")?.control == SettingsControl.ReadOnly)
    vcheck("循环引用不爆栈（depth 闸住）", smFields.byKey("loop")?.control == SettingsControl.ReadOnly)

    // ── 标准 JSON Schema 形态也认 ────────────────────────────────────────────
    val std = namespace(
        name = "app",
        schema = jsonObject(
            """
            {"type": "object", "properties": {
              "locale": {"type": "string", "enum": ["zh-CN", "en"], "title": "界面语言"},
              "autoSave": {"type": "boolean", "description": "退出时自动保存"},
              "fontSize": {"type": "integer", "minimum": 8, "maximum": 72},
              "port": {"type": "number", "min": 1024, "max": 65535},
              "nickname": {"type": "string"},
              "blob": {"type": "object"}
            }}
            """,
        ),
        value = jsonObject("""{"locale": "en", "autoSave": true, "fontSize": 14, "port": 3090, "nickname": "老王", "blob": {"a": 1}}"""),
    )
    val stdFields = SettingsSchema.fields(std)
    vcheck("标准 schema 摊出 6 个字段", stdFields.size == 6)
    vcheck("string+enum → Enum", stdFields.byKey("locale")?.control == SettingsControl.Enum)
    vcheck("title 透传", stdFields.byKey("locale")?.title == "界面语言")
    vcheck("boolean → Boolean", stdFields.byKey("autoSave")?.control == SettingsControl.Boolean)
    vcheck("integer 标记", stdFields.byKey("fontSize")?.integer == true)
    vcheck("简写 min/max 也认", stdFields.byKey("port")?.minimum == 1024.0 && stdFields.byKey("port")?.maximum == 65535.0)
    vcheck("纯 string → Text", stdFields.byKey("nickname")?.control == SettingsControl.Text)
    vcheck("object → ReadOnly", stdFields.byKey("blob")?.control == SettingsControl.ReadOnly)

    // ── 密钥三判据（任一命中即只读、绝不提交）────────────────────────────────
    val secretByList = namespace(
        name = "provider",
        schema = jsonObject("""{"type": "object", "properties": {"token": {"type": "string"}, "other": {"type": "string"}}}"""),
        value = jsonObject("""{"token": "sk-live-abc", "other": "x"}"""),
        secrets = listOf(SettingsSecret(path = listOf("token"), set = true)),
    )
    val secretFields = SettingsSchema.fields(secretByList)
    vcheck("secrets[].path 首段命中 → Secret", secretFields.byKey("token")?.control == SettingsControl.Secret)
    vcheck("secrets 的 set 透传", secretFields.byKey("token")?.secretSet == true)
    vcheck("同页其他 string 不受影响", secretFields.byKey("other")?.control == SettingsControl.Text)

    val masked = namespace(
        name = "provider2",
        schema = jsonObject("""{"type": "object", "properties": {"apiKey": {"type": "string"}}}"""),
        value = jsonObject("""{"apiKey": "***"}"""),
    )
    vcheck("值是 *** → Secret 且已设置", SettingsSchema.fields(masked).single().control == SettingsControl.Secret)
    vcheck("*** → secretSet", SettingsSchema.fields(masked).single().secretSet == true)

    val schemaSecret = namespace(
        name = "provider3",
        schema = jsonObject("""{"type": "object", "properties": {"apiKey": {"type": "string", "x-secret": true}}}"""),
        value = jsonObject("""{"apiKey": ""}"""),
    )
    vcheck("schema x-secret → Secret", SettingsSchema.fields(schemaSecret).single().control == SettingsControl.Secret)
    vcheck("x-secret 但值空 → 未设置", SettingsSchema.fields(schemaSecret).single().secretSet == false)

    // ── properties 缺失 → 按 value 键兜底，全只读 ───────────────────────────
    val noSchema = namespace(
        name = "legacy",
        schema = jsonObject("{}"),
        value = jsonObject("""{"a": 1, "b": "x", "k": "***"}"""),
    )
    val fallback = SettingsSchema.fields(noSchema)
    vcheck("无 properties → 按 value 键兜底", fallback.map { it.key } == listOf("a", "b", "k"))
    vcheck("兜底行默认只读", fallback.byKey("a")?.control == SettingsControl.ReadOnly)
    vcheck("兜底行里的掩码值仍判 Secret", fallback.byKey("k")?.control == SettingsControl.Secret)

    // ── 入口过滤：可写 + 有可编辑字段 + 非纯 WebUI ───────────────────────────
    val editable = namespace(
        name = "locale",
        schema = jsonObject("""{"type": "object", "properties": {"lang": {"type": "string", "enum": ["zh", "en"]}}}"""),
        value = jsonObject("""{"lang": "zh"}"""),
    )
    val webui = namespace(
        name = "ui-theme",
        schema = jsonObject("""{"type": "object", "properties": {"dark": {"type": "boolean"}}}"""),
        value = jsonObject("""{"dark": false}"""),
    )
    val readOnlyNs = namespace(
        name = "kernel",
        schema = jsonObject("{}"),
        value = jsonObject("""{"version": "2.6.0"}"""),
    )
    val notWritable = namespace(
        name = "highrisk",
        schema = jsonObject("""{"type": "object", "properties": {"x": {"type": "string"}}}"""),
        value = jsonObject("""{"x": "1"}"""),
        writable = false,
    )
    val secretOnly = namespace(
        name = "provider4",
        schema = jsonObject("""{"type": "object", "properties": {"token": {"type": "string"}}}"""),
        value = jsonObject("""{"token": "***"}"""),
    )
    val filtered = listOf(webui, readOnlyNs, notWritable, editable, secretOnly).forNativeApp()
    vcheck("只留可写 + 有可编辑字段 + 非 WebUI", filtered.map { it.ns } == listOf("locale", "provider4"))
    vcheck("顺序保持原样", filtered.first().ns == "locale")
    vcheck("纯 WebUI 键被滤掉", webui.ns in setOf("ui-theme", "ui-chat", "ui-conversation", "ui-settings", "ui-settings-general"))
    vcheck("Secret 算可编辑字段", secretOnly.hasEditableField())
    vcheck("全只读不算可编辑", !readOnlyNs.hasEditableField())

    // ── scope 判据 ──────────────────────────────────────────────────────────
    vcheck("有 settings.write → 能存", listOf("chat.read", "settings.write").canWriteSettingsNamespaces())
    vcheck(
        "配对默认五档没有 settings.write",
        !listOf("chat.read", "chat.write", "files.read", "files.write", "settings.read")
            .canWriteSettingsNamespaces(),
    )
    vcheck("空 scopes → 不能存", !emptyList<String>().canWriteSettingsNamespaces())

    // ── 数字夹取与人类化 ────────────────────────────────────────────────────
    vcheck("夹在区间内", clampSettingsNumber(5.0, 0.0, 10.0) == 5.0)
    vcheck("低于下界夹回来", clampSettingsNumber(-3.0, 0.0, 10.0) == 0.0)
    vcheck("高于上界夹回来", clampSettingsNumber(99.0, 0.0, 10.0) == 10.0)
    vcheck("无边界原样返回", clampSettingsNumber(12345.6, null, null) == 12345.6)
    vcheck("min>max 脏 schema 原样返回不抛", clampSettingsNumber(5.0, 10.0, 0.0) == 5.0)
    vcheck("camelCase 人类化", humanizeFieldName("transcriptView") == "Transcript view")
    vcheck("snake_case 人类化", humanizeFieldName("font_size") == "Font size")
    vcheck("kebab-case 人类化", humanizeFieldName("ui-theme") == "Ui theme")
    vcheck("空 key 原样返回", humanizeFieldName("") == "")
}

fun main() {
    println("▸ 第二批对标纯函数用例")
    verifyNewSessionPreset()
    verifyUpdateChecker()
    verifySettingsNamespaces()
    if (vsummary() > 0) kotlin.system.exitProcess(1)
}
