package io.github.westanke.dshdesktop

import io.github.westanke.dshdesktop.data.ConnectionShare
import io.github.westanke.dshdesktop.data.ImportedConnection
import io.github.westanke.dshdesktop.data.toImported

/**
 * X9 阶段一：`DSH1:` 配置文本导入。
 *
 * 与多地址选路（阶段二）**完全解耦**：只取配置里的第一个地址，复用既有的
 * [connect] 通路做连通性验证、落盘与连接——不造第二套连接逻辑。
 * 编解码纯函数在 `data/ConnectionShare.kt`（可离线验证，见 `tools/verify-parity.sh`）。
 *
 * 安全：这段文本等同于设备令牌。导入成功后粘贴框由 UI 立即清空；
 * 失败原因只说结构问题（缺前缀/Base64 非法/无地址），**绝不回显令牌本身**。
 */
fun AppState.importDsh1Config(text: String, onEndpointFilled: (String) -> Unit) {
    when (val result = ConnectionShare.decode(text)) {
        is ConnectionShare.DecodeResult.Failure -> {
            // 具体原因，不静默：用户此刻已经离开电脑，一句"无效"等于让他重来一遍。
            connectionError = rejectMessage(result.reason, result.detail)
            return
        }

        is ConnectionShare.DecodeResult.Success -> {
            val imported = result.payload.toImported()
            connectionError = null
            onEndpointFilled(imported.baseUrl)
            if (imported.token == null) {
                // 没令牌的配置也有用：地址已经填进表单，用户补一个配对码就能连。
                // 刻意不落盘——scopes 依附于令牌，没有令牌存它没有意义。
                importNotice = importNoticeFor(imported)
                return
            }
            // 有令牌：走完整连接通路。health() 就是"连通性验证"，通过才落盘；
            // 令牌仍走 AppStorage 的 AES-GCM（token.enc），与配对流程同一套设施。
            connect(
                endpointRaw = imported.baseUrl,
                pairingCode = null,
                importedToken = imported.token,
                importNotice = importNoticeFor(imported),
            )
        }
    }
}

/** 拒绝原因的中文文案。detail 只可能是地址，不可能是令牌。 */
private fun rejectMessage(reason: ConnectionShare.Reason, detail: String): String = when (reason) {
    ConnectionShare.Reason.EMPTY -> "请先粘贴 DSH1 配置文本"
    ConnectionShare.Reason.NOT_BASE64 -> "这段文本解不出 Base64：可能复制漏了内容，或只粘到了一部分"
    ConnectionShare.Reason.NOT_JSON -> "解码成功，但内容不是有效的配置 JSON"
    ConnectionShare.Reason.NO_ENDPOINT -> "这份配置里没有地址（endpoints 为空）"
    ConnectionShare.Reason.UNUSABLE_URL ->
        "配置里的地址不可用（需要 http/https 且带主机名）：$detail"
}

/** 导入结果的常驻提示：导入成功了什么、还忽略了什么，都说明白。 */
private fun importNoticeFor(imported: ImportedConnection): String = buildString {
    val name = imported.displayName.ifBlank { "这台电脑" }
    append("已导入「$name」")
    if (imported.token == null) {
        append("，但这份配置不含设备令牌——请填写一次性配对码后点「连接」")
    }
    if (imported.extraEndpoints > 0) {
        append("；另有 ${imported.extraEndpoints} 个地址未启用（多地址选路尚未支持）")
    }
}
